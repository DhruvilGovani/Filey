package com.glimpse.files.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileRepository(private val ctx: Context) {
    val root: File = Environment.getExternalStorageDirectory()
    private val trashDir = File(root, ".glimpse_trash")
    private val prefs = ctx.getSharedPreferences("glimpse_prefs", Context.MODE_PRIVATE)
    private var ftpServer: FtpServer? = null

    private val thumbCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ctx.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun loadSettings(): AppSettings {
        val themeKey = prefs.getString("theme", ThemeMode.Auto.key) ?: ThemeMode.Auto.key
        val sortKey = prefs.getString("sort", SortMode.DateAdded.key) ?: SortMode.DateAdded.key
        return AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.key == themeKey } ?: ThemeMode.Auto,
            accentHue = prefs.getFloat("hue", 200f),
            showHidden = prefs.getBoolean("hidden", false),
            sortMode = SortMode.entries.firstOrNull { it.key == sortKey } ?: SortMode.DateAdded,
            isGridView = prefs.getBoolean("grid", false),
            binRetentionDays = prefs.getInt("bin", 14),
            ftpPort = prefs.getInt("port", 2121),
            ftpUser = prefs.getString("user", "admin") ?: "admin",
            ftpPass = prefs.getString("pass", "files123") ?: "files123",
            extractDest = prefs.getString("ext", "Download/Extracted") ?: "Download/Extracted"
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("theme", s.theme.key)
            .putFloat("hue", s.accentHue)
            .putBoolean("hidden", s.showHidden)
            .putString("sort", s.sortMode.key)
            .putBoolean("grid", s.isGridView)
            .putInt("bin", s.binRetentionDays)
            .putInt("port", s.ftpPort)
            .putString("user", s.ftpUser)
            .putString("pass", s.ftpPass)
            .putString("ext", s.extractDest)
            .apply()
    }

    private fun safeName(n: String) {
        require(n.isNotEmpty() && n != ".." && n != "." && !n.contains('/')) { "Invalid name" }
    }

    fun resolveDir(segments: List<String>): File {
        var f = root
        for (seg in segments) {
            safeName(seg)
            f = File(f, seg)
        }
        return f
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var idx = 1
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        while (candidate.exists()) {
            candidate = File(dir, "$base ($idx)$ext")
            idx++
        }
        return candidate
    }

    private fun computeDirSize(d: File, depth: Int = 0): Long {
        if (depth > 12) return 0L
        val kids = d.listFiles() ?: return 0L
        var total = 0L
        for (k in kids) {
            total += if (k.isDirectory) computeDirSize(k, depth + 1) else k.length()
        }
        return total
    }

    private fun advertisedCapacity(rawTotalBytes: Long): Long {
        val tiers = longArrayOf(
            16_000_000_000L, 32_000_000_000L, 64_000_000_000L, 128_000_000_000L,
            256_000_000_000L, 512_000_000_000L, 1_000_000_000_000L, 2_000_000_000_000L
        )
        return tiers.firstOrNull { rawTotalBytes <= it && rawTotalBytes >= (it * 0.72).toLong() } ?: rawTotalBytes
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun computeAppsBytes(): Long {
        val pm = ctx.packageManager
        val apps = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        if (Build.VERSION.SDK_INT >= 26 && hasUsageStatsPermission()) {
            try {
                val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val seenUids = HashSet<Int>()
                var total = 0L
                for (ai in apps) {
                    if (!seenUids.add(ai.uid)) continue
                    val stats = ssm.queryStatsForUid(StorageManager.UUID_DEFAULT, ai.uid)
                    total += stats.appBytes + stats.dataBytes + stats.cacheBytes
                }
                if (total > 0L) return total
            } catch (_: Exception) {}
        }

        var fallback = 0L
        for (ai in apps) {
            try {
                ai.sourceDir?.let { File(it).takeIf { f -> f.isFile }?.let { f -> fallback += f.length() } }
                ai.splitSourceDirs?.forEach { p ->
                    File(p).takeIf { f -> f.isFile }?.let { f -> fallback += f.length() }
                }
            } catch (_: Exception) {}
        }
        val obbRoot = File(root, "Android/obb")
        if (obbRoot.isDirectory) fallback += computeDirSize(obbRoot, 0)
        val mediaRoot = File(root, "Android/media")
        if (mediaRoot.isDirectory) fallback += computeDirSize(mediaRoot, 0)
        return fallback
    }

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val st = StatFs(root.path)
        val total = advertisedCapacity(st.totalBytes)
        val used = maxOf(0L, total - st.availableBytes)

        var img = 0L; var vid = 0L; var aud = 0L; var arc = 0L; var doc = 0L; var oth = 0L
        try {
            ctx.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(FileColumns.SIZE, FileColumns.MEDIA_TYPE, FileColumns.MIME_TYPE, FileColumns.DATA),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val s = c.getLong(0)
                    val t = c.getInt(1)
                    val m = c.getString(2) ?: ""
                    val path = c.getString(3) ?: ""
                    val ext = path.substringAfterLast('.', "").lowercase()
                    when {
                        t == FileColumns.MEDIA_TYPE_IMAGE -> img += s
                        t == FileColumns.MEDIA_TYPE_VIDEO -> vid += s
                        t == FileColumns.MEDIA_TYPE_AUDIO || m.startsWith("audio/") || ext in setOf("mp3", "flac", "m4a", "wav", "ogg", "aac", "opus") -> aud += s
                        m.contains("zip") || m.contains("rar") || m.contains("7z") || m.contains("tar") || ext in setOf("zip", "rar", "7z", "tar", "gz") -> arc += s
                        m.contains("pdf") || m.contains("word") || m.contains("sheet") || m.contains("presentation") || m.startsWith("text/") || ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv") -> doc += s
                        else -> oth += s
                    }
                }
            }
        } catch (_: Exception) {}

        val rawApps = computeAppsBytes()
        val maxApps = maxOf(0L, used - img - vid - aud - arc - doc - oth)
        val apps = minOf(rawApps, maxApps)
        val sys = maxOf(0L, used - apps - img - vid - aud - arc - doc - oth)

        StorageInfo(
            totalBytes = total,
            usedBytes = used,
            slices = listOf(
                StorageSlice("Apps", apps, 0xFF5B8CFF),
                StorageSlice("Images", img, 0xFF29D3A3),
                StorageSlice("Videos", vid, 0xFFFF5D8F),
                StorageSlice("Audio", aud, 0xFF12A8C4),
                StorageSlice("Archives", arc, 0xFFFFB84D),
                StorageSlice("Documents", doc, 0xFFB57BFF),
                StorageSlice("Other", oth, 0xFF3EC6FF),
                StorageSlice("System", sys, 0xFF8A94A8)
            )
        )
    }

    suspend fun listDirectory(segments: List<String>, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val d = resolveDir(segments)
            val kids = d.listFiles() ?: return@withContext emptyList()
            kids.mapNotNull { f ->
                val name = f.name
                if (!showHidden && name.startsWith(".")) return@mapNotNull null
                val isDir = f.isDirectory
                FileItem(
                    name = name,
                    pathSegments = segments,
                    isDir = isDir,
                    size = if (isDir) 0L else f.length(),
                    modifiedMs = f.lastModified(),
                    itemCount = if (isDir) (f.list()?.size ?: 0) else 0
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun calculateFolderSizes(segments: List<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        try {
            val res = HashMap<String, Long>()
            resolveDir(segments).listFiles()?.forEach { f ->
                if (f.isDirectory && !f.name.startsWith(".")) {
                    res[f.name] = computeDirSize(f)
                }
            }
            res
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun scanDocsDirect(): List<FileItem> {
        val docExts = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "rtf", "odt", "ods", "odp", "epub", "json", "xml")
        val out = ArrayList<FileItem>()
        val seen = HashSet<String>()

        fun walk(d: File, depth: Int) {
            if (depth > 8 || out.size >= 400) return
            val kids = d.listFiles() ?: return
            for (f in kids) {
                if (out.size >= 400) break
                val name = f.name
                if (name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (depth == 0 && name == "Android") continue
                    walk(f, depth + 1)
                } else if (f.isFile && f.length() > 0L) {
                    val ext = f.extension.lowercase()
                    if (ext in docExts && seen.add(f.absolutePath)) {
                        val rel = f.relativeTo(root).path
                        val slashIdx = rel.lastIndexOf('/')
                        val parentSegs = if (slashIdx > 0) rel.substring(0, slashIdx).split('/') else emptyList()
                        out.add(
                            FileItem(
                                name = name,
                                pathSegments = parentSegs,
                                isDir = false,
                                size = f.length(),
                                modifiedMs = f.lastModified()
                            )
                        )
                    }
                }
            }
        }
        walk(root, 0)
        return out.sortedByDescending { it.modifiedMs }
    }

    suspend fun getByCategory(cat: FileCategory, limit: Int = 400): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            if (cat == FileCategory.Documents) {
                return@withContext scanDocsDirect()
            }
            val out = ArrayList<FileItem>()
            val rootPath = root.absolutePath.trimEnd('/') + "/"
            val uri = MediaStore.Files.getContentUri("external")
            val proj = arrayOf(FileColumns.DATA, FileColumns.SIZE, FileColumns.DATE_MODIFIED)
            val weekSec = (System.currentTimeMillis() - 7L * 86400_000L) / 1000L

            val whereBuilder = StringBuilder("${FileColumns.SIZE} > 0 AND ${FileColumns.DATA} LIKE ? AND ${FileColumns.DATA} NOT LIKE ? AND ${FileColumns.DATA} NOT LIKE ?")
            val argsList = ArrayList<String>().apply {
                add("$rootPath%")
                add("${rootPath}Android/%")
                add("${rootPath}.glimpse_trash/%")
            }

            when (cat) {
                FileCategory.Images -> whereBuilder.append(" AND ${FileColumns.MEDIA_TYPE} = ${FileColumns.MEDIA_TYPE_IMAGE}")
                FileCategory.Videos -> whereBuilder.append(" AND ${FileColumns.MEDIA_TYPE} = ${FileColumns.MEDIA_TYPE_VIDEO}")
                FileCategory.Audio -> whereBuilder.append(" AND ${FileColumns.MEDIA_TYPE} = ${FileColumns.MEDIA_TYPE_AUDIO}")
                FileCategory.Downloads -> {
                    whereBuilder.append(" AND ${FileColumns.DATA} LIKE ?")
                    argsList.add("${rootPath}Download/%")
                }
                FileCategory.LargeFiles -> whereBuilder.append(" AND ${FileColumns.SIZE} > 52428800")
                FileCategory.ThisWeek -> whereBuilder.append(" AND ${FileColumns.DATE_MODIFIED} >= $weekSec")
                FileCategory.Apks -> whereBuilder.append(" AND (LOWER(${FileColumns.DATA}) LIKE '%.apk' OR LOWER(${FileColumns.DATA}) LIKE '%.apks' OR LOWER(${FileColumns.DATA}) LIKE '%.xapk')")
                FileCategory.Archives -> whereBuilder.append(" AND (LOWER(${FileColumns.DATA}) LIKE '%.zip' OR LOWER(${FileColumns.DATA}) LIKE '%.rar' OR LOWER(${FileColumns.DATA}) LIKE '%.7z' OR LOWER(${FileColumns.DATA}) LIKE '%.tar' OR LOWER(${FileColumns.DATA}) LIKE '%.gz')")
                FileCategory.Recent, FileCategory.Documents -> Unit
            }

            val sort = if (cat == FileCategory.LargeFiles) "${FileColumns.SIZE} DESC" else "${FileColumns.DATE_MODIFIED} DESC"

            ctx.contentResolver.query(uri, proj, whereBuilder.toString(), argsList.toTypedArray(), sort)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val path = c.getString(0) ?: continue
                    val rel = path.removePrefix(rootPath)
                    val slashIdx = rel.lastIndexOf('/')
                    val name = if (slashIdx >= 0) rel.substring(slashIdx + 1) else rel
                    if (name.isEmpty() || name.startsWith(".")) continue
                    val size = c.getLong(1)
                    val modMs = c.getLong(2) * 1000L
                    val parentSegs = if (slashIdx > 0) rel.substring(0, slashIdx).split('/') else emptyList()
                    out.add(
                        FileItem(
                            name = name,
                            pathSegments = parentSegs,
                            isDir = false,
                            size = size,
                            modifiedMs = modMs
                        )
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchFiles(query: String, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return@withContext emptyList()
            val out = ArrayList<FileItem>()
            val rootPath = root.absolutePath.trimEnd('/') + "/"
            val seenDirs = HashSet<String>()
            val uri = MediaStore.Files.getContentUri("external")
            val proj = arrayOf(FileColumns.DATA)
            val sel = "${FileColumns.DATA} LIKE ?"
            val args = arrayOf("%$q%")
            val sort = "${FileColumns.DATE_MODIFIED} DESC"
            ctx.contentResolver.query(uri, proj, sel, args, sort)?.use { c ->
                while (c.moveToNext() && out.size < 100) {
                    val path = c.getString(0) ?: continue
                    if (!path.startsWith(rootPath)) continue
                    val rel = path.removePrefix(rootPath)
                    if (rel.startsWith(".glimpse_trash/") || rel.startsWith("Android/")) continue
                    val f = File(path)
                    if (!f.exists()) continue
                    if (!showHidden && f.name.startsWith(".")) continue

                    if (f.name.lowercase().contains(q)) {
                        val slashIdx = rel.lastIndexOf('/')
                        val parentSegs = if (slashIdx > 0) rel.substring(0, slashIdx).split('/') else emptyList()
                        if (f.isDirectory) {
                            if (seenDirs.add(f.absolutePath)) {
                                out.add(FileItem(f.name, parentSegs, true, 0L, f.lastModified(), f.list()?.size ?: 0))
                            }
                        } else if (f.isFile) {
                            out.add(FileItem(f.name, parentSegs, false, f.length(), f.lastModified()))
                        }
                    }

                    var curParent = f.parentFile
                    while (curParent != null && curParent.absolutePath.startsWith(rootPath) && curParent.absolutePath != root.absolutePath) {
                        val dn = curParent.name
                        if ((showHidden || !dn.startsWith(".")) && dn.lowercase().contains(q) && seenDirs.add(curParent.absolutePath)) {
                            val dRel = curParent.absolutePath.removePrefix(rootPath)
                            val slashIdx = dRel.lastIndexOf('/')
                            val dParentSegs = if (slashIdx > 0) dRel.substring(0, slashIdx).split('/') else emptyList()
                            out.add(FileItem(dn, dParentSegs, true, 0L, curParent.lastModified(), curParent.list()?.size ?: 0))
                        }
                        curParent = curParent.parentFile
                    }
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun decodeCroppedBitmap(decodeFn: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeFn(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 320 && bounds.outHeight / (sample * 2) >= 320) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = decodeFn(opts) ?: return null
        val size = 320
        val minSide = minOf(raw.width, raw.height).coerceAtLeast(1)
        val x = (raw.width - minSide) / 2
        val y = (raw.height - minSide) / 2
        val cropped = if (raw.width == raw.height) raw else Bitmap.createBitmap(raw, x, y, minSide, minSide)
        if (cropped !== raw) raw.recycle()
        val scaled = if (cropped.width == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    suspend fun loadThumbnail(item: FileItem): Bitmap? = withContext(Dispatchers.IO) {
        if (item.isDir) return@withContext null
        val ext = item.ext
        if (ext !in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "mp4", "mkv", "avi", "mov", "webm", "3gp", "mp3", "flac", "m4a", "wav", "ogg", "aac", "opus", "apk")) {
            return@withContext null
        }
        try {
            val f = File(resolveDir(item.pathSegments), item.name)
            if (!f.isFile) return@withContext null
            val key = "${f.canonicalPath}|${f.lastModified()}"
            thumbCache.get(key)?.let { if (!it.isRecycled) return@withContext it }

            val bmp: Bitmap? = when (ext) {
                "jpg", "jpeg", "png", "webp", "gif", "heic", "bmp" -> {
                    decodeCroppedBitmap { opts -> BitmapFactory.decodeFile(f.absolutePath, opts) }
                }
                "mp4", "mkv", "avi", "mov", "webm", "3gp" -> {
                    val mmr = MediaMetadataRetriever()
                    val frame: Bitmap? = try {
                        mmr.setDataSource(f.absolutePath)
                        mmr.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: mmr.frameAtTime
                    } catch (_: Exception) {
                        null
                    } finally {
                        try { mmr.release() } catch (_: Exception) {}
                    }
                    if (frame != null) {
                        val size = 320
                        val minSide = minOf(frame.width, frame.height).coerceAtLeast(1)
                        val x = (frame.width - minSide) / 2
                        val y = (frame.height - minSide) / 2
                        val cropped = if (frame.width == frame.height) frame else Bitmap.createBitmap(frame, x, y, minSide, minSide)
                        if (cropped !== frame) frame.recycle()
                        val scaled = if (cropped.width == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
                        if (scaled !== cropped) cropped.recycle()
                        scaled
                    } else null
                }
                "mp3", "flac", "m4a", "wav", "ogg", "aac", "opus" -> {
                    val mmr = MediaMetadataRetriever()
                    val pic: ByteArray? = try {
                        mmr.setDataSource(f.absolutePath)
                        mmr.embeddedPicture
                    } catch (_: Exception) {
                        null
                    } finally {
                        try { mmr.release() } catch (_: Exception) {}
                    }
                    if (pic != null && pic.isNotEmpty()) {
                        decodeCroppedBitmap { opts -> BitmapFactory.decodeByteArray(pic, 0, pic.size, opts) }
                    } else null
                }
                "apk" -> {
                    try {
                        val pm = ctx.packageManager
                        val pi = if (Build.VERSION.SDK_INT >= 33) {
                            pm.getPackageArchiveInfo(f.absolutePath, PackageManager.PackageInfoFlags.of(0L))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageArchiveInfo(f.absolutePath, 0)
                        }
                        val appInfo = pi?.applicationInfo?.apply {
                            sourceDir = f.absolutePath
                            publicSourceDir = f.absolutePath
                        }
                        val drw = appInfo?.loadIcon(pm)
                        if (drw != null) {
                            val size = 256
                            if (drw is BitmapDrawable && drw.bitmap != null) {
                                Bitmap.createScaledBitmap(drw.bitmap, size, size, true)
                            } else {
                                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { canvasBmp ->
                                    val c = Canvas(canvasBmp)
                                    drw.setBounds(0, 0, size, size)
                                    drw.draw(c)
                                }
                            }
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                else -> null
            }
            if (bmp != null) {
                thumbCache.put(key, bmp)
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun openFile(item: FileItem): Boolean {
        val f = File(resolveDir(item.pathSegments), item.name)
        if (!f.isFile) return false
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.ext) ?: "*/*"
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        i.clipData = ClipData.newRawUri("", uri)
        return try {
            ctx.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareFiles(items: List<FileItem>): Boolean {
        val uris = ArrayList<Uri>()
        var singleMime: String? = null
        for (item in items) {
            val f = File(resolveDir(item.pathSegments), item.name)
            if (f.isFile) {
                uris.add(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
                if (items.size == 1) {
                    singleMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.ext) ?: "*/*"
                }
            }
        }
        if (uris.isEmpty()) return false
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = singleMime ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                clipData = ClipData.newRawUri("", uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return try {
            ctx.startActivity(Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun createFolder(segments: List<String>, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            safeName(name)
            File(resolveDir(segments), name).mkdirs()
        } catch (_: Exception) { false }
    }

    suspend fun createFile(segments: List<String>, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            safeName(name)
            File(resolveDir(segments), name).createNewFile()
        } catch (_: Exception) { false }
    }

    suspend fun renameItem(segments: List<String>, oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            safeName(newName)
            val d = resolveDir(segments)
            File(d, oldName).renameTo(File(d, newName))
        } catch (_: Exception) { false }
    }

    suspend fun transferItems(srcSegments: List<String>, names: List<String>, destSegments: List<String>, isMove: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val sd = resolveDir(srcSegments)
            val dd = resolveDir(destSegments).apply { mkdirs() }
            for (n in names) {
                val src = File(sd, n)
                if (!src.exists()) continue
                if (isMove && sd.canonicalPath == dd.canonicalPath) continue
                if (src.isDirectory && (dd.canonicalPath + "/").startsWith(src.canonicalPath + "/")) continue
                val target = uniqueFile(dd, n)
                if (isMove) {
                    if (!src.renameTo(target)) {
                        src.copyRecursively(target, true)
                        src.deleteRecursively()
                    }
                } else {
                    src.copyRecursively(target, true)
                }
            }
            true
        } catch (_: Exception) { false }
    }

    suspend fun moveToTrash(segments: List<String>, names: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val d = resolveDir(segments)
            trashDir.mkdirs()
            val idxFile = File(trashDir, "index.json")
            val arr = if (idxFile.exists()) JSONArray(idxFile.readText()) else JSONArray()
            for (n in names) {
                val src = File(d, n)
                if (!src.exists()) continue
                val size = if (src.isDirectory) computeDirSize(src) else src.length()
                val id = "${System.currentTimeMillis()}_${Math.abs(n.hashCode())}"
                val dst = File(trashDir, id)
                if (!src.renameTo(dst)) {
                    src.copyRecursively(dst, true)
                    src.deleteRecursively()
                }
                arr.put(
                    JSONObject()
                        .put("id", id)
                        .put("name", n)
                        .put("from", d.relativeTo(root).path)
                        .put("s", size)
                        .put("dir", dst.isDirectory)
                        .put("t", System.currentTimeMillis())
                )
            }
            idxFile.writeText(arr.toString())
            true
        } catch (_: Exception) { false }
    }

    suspend fun listTrash(maxDays: Int): List<TrashItem> = withContext(Dispatchers.IO) {
        try {
            val idxFile = File(trashDir, "index.json")
            if (!idxFile.exists()) return@withContext emptyList()
            val arr = JSONArray(idxFile.readText())
            val out = ArrayList<TrashItem>()
            val kept = JSONArray()
            val cut = System.currentTimeMillis() - maxDays.toLong() * 86400_000L
            var dirty = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val f = File(trashDir, id)
                val t = o.optLong("t", 0L)
                if (!f.exists()) { dirty = true; continue }
                if (maxDays > 0 && t > 0L && t < cut) {
                    f.deleteRecursively()
                    dirty = true
                    continue
                }
                val size = o.optLong("s", if (f.isDirectory) computeDirSize(f) else f.length())
                o.put("s", size).put("dir", f.isDirectory)
                kept.put(o)
                out.add(
                    TrashItem(
                        id = id,
                        name = o.optString("name", "Unknown"),
                        fromRelPath = o.optString("from", ""),
                        size = size,
                        isDir = f.isDirectory,
                        deletedAtMs = t
                    )
                )
            }
            if (dirty) idxFile.writeText(kept.toString())
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun restoreTrash(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val idxFile = File(trashDir, "index.json")
            if (!idxFile.exists()) return@withContext false
            val arr = JSONArray(idxFile.readText())
            val kept = JSONArray()
            var restored = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) {
                    val src = File(trashDir, id)
                    val fromRel = o.optString("from", "")
                    val name = o.optString("name", "Restored")
                    val destDir = if (fromRel.isEmpty()) root else File(root, fromRel).apply { mkdirs() }
                    val target = uniqueFile(destDir, name)
                    if (src.exists()) {
                        if (!src.renameTo(target)) {
                            src.copyRecursively(target, true)
                            src.deleteRecursively()
                        }
                        restored = true
                    }
                } else {
                    kept.put(o)
                }
            }
            idxFile.writeText(kept.toString())
            restored
        } catch (_: Exception) { false }
    }

    suspend fun wipeTrash(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val idxFile = File(trashDir, "index.json")
            if (!idxFile.exists()) return@withContext false
            val arr = JSONArray(idxFile.readText())
            val kept = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) {
                    File(trashDir, id).deleteRecursively()
                } else {
                    kept.put(o)
                }
            }
            idxFile.writeText(kept.toString())
            true
        } catch (_: Exception) { false }
    }

    suspend fun emptyTrash(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (trashDir.exists()) {
                trashDir.listFiles()?.forEach { it.deleteRecursively() }
            }
            true
        } catch (_: Exception) { false }
    }

    suspend fun inspectZip(segments: List<String>, name: String): List<ZipEntryItem> = withContext(Dispatchers.IO) {
        try {
            val f = File(resolveDir(segments), name)
            val list = ArrayList<ZipEntryItem>()
            if (f.isFile) {
                ZipInputStream(BufferedInputStream(FileInputStream(f))).use { zis ->
                    var e: ZipEntry? = zis.nextEntry
                    var count = 0
                    while (e != null && count < 500) {
                        list.add(ZipEntryItem(e.name, maxOf(0L, e.size), e.isDirectory))
                        zis.closeEntry()
                        e = zis.nextEntry
                        count++
                    }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun createZip(segments: List<String>, targetNames: List<String>, outName: String): String? = withContext(Dispatchers.IO) {
        try {
            val baseDir = resolveDir(segments)
            val targets = targetNames.ifEmpty { baseDir.listFiles()?.map { it.name } ?: emptyList() }
            val cleanName = if (outName.endsWith(".zip", true)) outName else "$outName.zip"
            safeName(cleanName)
            val outFile = uniqueFile(baseDir, cleanName)
            val buf = ByteArray(16384)

            fun addEntry(zos: ZipOutputStream, file: File, relPath: String) {
                if (file.canonicalPath == outFile.canonicalPath) return
                if (file.isDirectory) {
                    val entryName = if (relPath.endsWith("/")) relPath else "$relPath/"
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.closeEntry()
                    file.listFiles()?.forEach { child ->
                        addEntry(zos, child, "$relPath/${child.name}")
                    }
                } else if (file.isFile) {
                    val ze = ZipEntry(relPath).apply { time = file.lastModified() }
                    zos.putNextEntry(ze)
                    BufferedInputStream(FileInputStream(file)).use { bis ->
                        var len: Int
                        while (bis.read(buf).also { len = it } > 0) {
                            zos.write(buf, 0, len)
                        }
                    }
                    zos.closeEntry()
                }
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
                for (item in targets) {
                    val f = File(baseDir, item)
                    if (f.exists()) addEntry(zos, f, f.name)
                }
            }
            outFile.name
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extractZip(segments: List<String>, name: String, destSegments: List<String>): String? = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(resolveDir(segments), name)
            if (!srcFile.isFile) return@withContext null
            val baseDest = resolveDir(destSegments).apply { mkdirs() }
            val folderName = name.substringBeforeLast('.', name).ifEmpty { "Extracted" }
            val targetDir = uniqueFile(baseDest, folderName).apply { mkdirs() }
            val targetCanon = targetDir.canonicalPath + File.separator
            val buf = ByteArray(16384)

            ZipInputStream(BufferedInputStream(FileInputStream(srcFile))).use { zis ->
                var e: ZipEntry? = zis.nextEntry
                while (e != null) {
                    val outF = File(targetDir, e.name)
                    if (!outF.canonicalPath.startsWith(targetCanon) && outF.canonicalPath != targetDir.canonicalPath) {
                        throw SecurityException("Invalid zip entry")
                    }
                    if (e.isDirectory) {
                        outF.mkdirs()
                    } else {
                        outF.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(outF)).use { bos ->
                            var len: Int
                            while (zis.read(buf).also { len = it } > 0) {
                                bos.write(buf, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
            targetDir.name
        } catch (_: Exception) {
            null
        }
    }

    fun getLocalIpAddress(): String = try {
        val all = NetworkInterface.getNetworkInterfaces().toList()
        val pref = all.filter { it.name.startsWith("wlan") || it.name.startsWith("ap") }.ifEmpty { all }
        pref.flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress ?: "0.0.0.0"
    } catch (_: Exception) {
        "0.0.0.0"
    }

    fun toggleFtp(start: Boolean, port: Int, user: String, pass: String): Result<String> {
        return try {
            ftpServer?.stop()
            ftpServer = null
            if (start) {
                val fs = FtpServerFactory()
                val lf = ListenerFactory().apply { this.port = port }
                fs.addListener("default", lf.createListener())
                val um = PropertiesUserManagerFactory().createUserManager()
                val u = BaseUser().apply {
                    this.name = user
                    this.password = pass
                    this.homeDirectory = root.absolutePath
                    this.authorities = listOf<Authority>(WritePermission())
                }
                um.save(u)
                fs.userManager = um
                ftpServer = fs.createServer().also { it.start() }
            }
            Result.success(getLocalIpAddress())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
