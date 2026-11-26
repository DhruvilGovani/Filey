package com.glimpse.files

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
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

/** Paths from the page are JSON arrays of folder names under the storage root. Runs on the WebView's JavaBridge thread. */
class Bridge(private val act: MainActivity) {
    private val root: File = Environment.getExternalStorageDirectory()
    private val trashDir = File(root, ".glimpse_trash")
    private var ftp: FtpServer? = null

    private fun safe(n: String) = require(n.isNotEmpty() && n != ".." && n != "." && !n.contains('/')) { "bad name" }
    private fun dir(p: String): File {
        var f = root; val a = JSONArray(p)
        for (i in 0 until a.length()) { val n = a.getString(i); safe(n); f = File(f, n) }
        return f
    }
    private fun names(s: String): List<String> = JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } }
    private fun uniq(d: File, n: String): File {
        var f = File(d, n); var i = 1
        val b = n.substringBeforeLast('.', n)
        val e = n.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        while (f.exists()) { f = File(d, "$b ($i)$e"); i++ }
        return f
    }

    private fun dirSize(d: File, depth: Int = 0): Long {
        if (depth > 12) return 0L
        val kids = d.listFiles() ?: return 0L
        var total = 0L
        for (k in kids) {
            total += if (k.isDirectory) dirSize(k, depth + 1) else k.length()
        }
        return total
    }

    private fun advertisedCapacity(rawTotalBytes: Long): Long {
        val tiers = longArrayOf(
            16_000_000_000L,
            32_000_000_000L,
            64_000_000_000L,
            128_000_000_000L,
            256_000_000_000L,
            512_000_000_000L,
            1_000_000_000_000L,
            2_000_000_000_000L
        )
        return tiers.firstOrNull { rawTotalBytes <= it && rawTotalBytes >= (it * 0.72).toLong() } ?: rawTotalBytes
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = act.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), act.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), act.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun computeAppsBytes(): Long {
        val pm = act.packageManager
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
                val ssm = act.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
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
        if (obbRoot.isDirectory) fallback += dirSize(obbRoot, 0)
        val mediaRoot = File(root, "Android/media")
        if (mediaRoot.isDirectory) fallback += dirSize(mediaRoot, 0)
        return fallback
    }

    @JavascriptInterface fun hasAccess(): Boolean = act.granted()
    @JavascriptInterface fun requestAccess() { act.runOnUiThread { act.askAccess() } }

    @JavascriptInterface fun list(p: String): String = try {
        val a = JSONArray()
        dir(p).listFiles()?.forEach {
            val isDir = it.isDirectory
            val cnt = if (isDir) (it.listFiles()?.size ?: 0) else 0
            val size = if (isDir) 0L else it.length()
            a.put(
                JSONObject()
                    .put("n", it.name)
                    .put("dir", isDir)
                    .put("s", size)
                    .put("cnt", cnt)
                    .put("d", it.lastModified())
            )
        }
            a.toString()
        } catch (e: Exception) { "[]" }
    }

    @JavascriptInterface fun calcSizes(p: String) {
        Thread {
            try {
                val res = JSONObject()
                dir(p).listFiles()?.forEach {
                    if (it.isDirectory && !it.name.startsWith(".")) {
                        res.put(it.name, dirSize(it))
                    }
                }
                val js = "window.__onSizes&&window.__onSizes(${JSONObject.quote(p)},$res)"
                act.runOnUiThread { act.web.evaluateJavascript(js, null) }
            } catch (_: Exception) {}
        }.start()
    }

    @JavascriptInterface fun recent(limit: Int): String = try {
        val a = JSONArray()
        val rootPath = root.absolutePath.trimEnd('/') + "/"
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(FileColumns.DATA, FileColumns.SIZE, FileColumns.DATE_MODIFIED)
        val sel = "${FileColumns.SIZE} > 0"
        val sort = "${FileColumns.DATE_MODIFIED} DESC"
        act.contentResolver.query(uri, proj, sel, null, sort)?.use { c ->
            while (c.moveToNext() && a.length() < limit) {
                val path = c.getString(0) ?: continue
                if (!path.startsWith(rootPath)) continue
                val rel = path.removePrefix(rootPath)
                if (rel.startsWith(".glimpse_trash/") || rel.startsWith("Android/")) continue
                val f = File(path)
                if (!f.isFile || f.name.startsWith(".")) continue
                val parts = rel.split('/')
                val parentArr = JSONArray()
                for (i in 0 until parts.size - 1) parentArr.put(parts[i])
                a.put(
                    JSONObject()
                        .put("n", f.name)
                        .put("p", parentArr)
                        .put("s", f.length())
                        .put("d", f.lastModified())
                )
            }
        }
        a.toString()
    } catch (e: Exception) { "[]" }

    @JavascriptInterface fun search(query: String, showHidden: Boolean): String {
        return try {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return "[]"
        val a = JSONArray()
        val rootPath = root.absolutePath.trimEnd('/') + "/"
        val seenDirs = HashSet<String>()
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(FileColumns.DATA)
        val sel = "${FileColumns.DATA} LIKE ?"
        val args = arrayOf("%$q%")
        val sort = "${FileColumns.DATE_MODIFIED} DESC"
        act.contentResolver.query(uri, proj, sel, args, sort)?.use { c ->
            while (c.moveToNext() && a.length() < 80) {
                val path = c.getString(0) ?: continue
                if (!path.startsWith(rootPath)) continue
                val rel = path.removePrefix(rootPath)
                if (rel.startsWith(".glimpse_trash/") || rel.startsWith("Android/")) continue
                val f = File(path)
                if (!f.exists()) continue
                if (!showHidden && f.name.startsWith(".")) continue

                if (f.name.lowercase().contains(q)) {
                    val parts = rel.split('/')
                    val parentArr = JSONArray()
                    for (i in 0 until parts.size - 1) parentArr.put(parts[i])
                    if (f.isDirectory) {
                        if (seenDirs.add(f.absolutePath)) {
                            a.put(
                                JSONObject()
                                    .put("n", f.name)
                                    .put("p", parentArr)
                                    .put("dir", true)
                                    .put("s", 0L)
                                    .put("d", f.lastModified())
                            )
                        }
                    } else if (f.isFile) {
                        a.put(
                            JSONObject()
                                .put("n", f.name)
                                .put("p", parentArr)
                                .put("dir", false)
                                .put("s", f.length())
                                .put("d", f.lastModified())
                        )
                    }
                }

                // Also check matching parent folder names in the path
                var curParent = f.parentFile
                while (curParent != null && curParent.absolutePath.startsWith(rootPath) && curParent.absolutePath != root.absolutePath) {
                    val dn = curParent.name
                    if ((showHidden || !dn.startsWith(".")) && dn.lowercase().contains(q) && seenDirs.add(curParent.absolutePath)) {
                        val dRel = curParent.absolutePath.removePrefix(rootPath)
                        val dParts = dRel.split('/')
                        val dParentArr = JSONArray()
                        for (i in 0 until dParts.size - 1) dParentArr.put(dParts[i])
                        a.put(
                            JSONObject()
                                .put("n", dn)
                                .put("p", dParentArr)
                                .put("dir", true)
                                .put("s", 0L)
                                .put("d", curParent.lastModified())
                        )
                    }
                    curParent = curParent.parentFile
                }
            }
        }
        a.toString()
    } catch (e: Exception) { "[]" }

    @JavascriptInterface fun byCategory(cat: String): String = try {
        val a = JSONArray()
        val rootPath = root.absolutePath.trimEnd('/') + "/"
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(FileColumns.DATA, FileColumns.SIZE, FileColumns.DATE_MODIFIED)
        val sort = "${FileColumns.DATE_MODIFIED} DESC"
        val extMap = mapOf(
            "img" to setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp"),
            "vid" to setOf("mp4", "mkv", "avi", "mov", "webm", "3gp"),
            "apk" to setOf("apk", "apks", "xapk"),
            "arc" to setOf("zip", "rar", "7z", "tar", "gz", "bz2"),
            "doc" to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md"),
            "aud" to setOf("mp3", "flac", "m4a", "wav", "ogg", "aac", "opus")
        )
        val now = System.currentTimeMillis()
        val weekCut = now - 7L * 86400_000L
        act.contentResolver.query(uri, proj, "${FileColumns.SIZE} > 0", null, sort)?.use { c ->
            while (c.moveToNext() && a.length() < 300) {
                val path = c.getString(0) ?: continue
                if (!path.startsWith(rootPath)) continue
                val rel = path.removePrefix(rootPath)
                if (rel.startsWith(".glimpse_trash/") || rel.startsWith("Android/")) continue
                val f = File(path)
                if (!f.isFile || f.name.startsWith(".")) continue
                val ext = f.extension.lowercase()
                val match = when (cat) {
                    "dl" -> rel.startsWith("Download/")
                    "big" -> f.length() > 50L * 1024L * 1024L
                    "week" -> f.lastModified() >= weekCut
                    else -> extMap[cat]?.contains(ext) == true
                }
                if (!match) continue
                val parts = rel.split('/')
                val parentArr = JSONArray()
                for (i in 0 until parts.size - 1) parentArr.put(parts[i])
                a.put(
                    JSONObject()
                        .put("n", f.name)
                        .put("p", parentArr)
                        .put("s", f.length())
                        .put("d", f.lastModified())
                )
            }
        }
        a.toString()
    } catch (e: Exception) { "[]" }

    @JavascriptInterface fun info(): String {
        val st = StatFs(root.path)
        val dataTotal = st.totalBytes
        val avail = st.availableBytes
        val total = advertisedCapacity(dataTotal)
        val used = maxOf(0L, total - avail)

        var img = 0L; var vid = 0L; var aud = 0L; var arc = 0L; var doc = 0L; var oth = 0L
        try {
            act.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(FileColumns.SIZE, FileColumns.MEDIA_TYPE, FileColumns.MIME_TYPE, FileColumns.DATA),
                null, null, null
            )?.use {
                while (it.moveToNext()) {
                    val s = it.getLong(0)
                    val t = it.getInt(1)
                    val m = it.getString(2) ?: ""
                    val path = it.getString(3) ?: ""
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

        fun b(n: String, v: Long) = JSONObject().put("name", n).put("bytes", v)
        val br = JSONArray()
            .put(b("Apps", apps))
            .put(b("Images", img))
            .put(b("Videos", vid))
            .put(b("Audio", aud))
            .put(b("Archives", arc))
            .put(b("Documents", doc))
            .put(b("Other", oth))
            .put(b("System", sys))
        return JSONObject().put("total", total).put("used", used).put("breakdown", br).toString()
    }

    @JavascriptInterface fun open(p: String, n: String) {
        val f = File(dir(p), n); if (!f.isFile) return
        val uri = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("", uri)
        act.runOnUiThread {
            try { act.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            catch (e: Exception) { act.toast("No app can open this file") }
        }
    }

    @JavascriptInterface fun share(p: String, ns: String) {
        val d = dir(p)
        val list = names(ns)
        val uris = ArrayList<Uri>()
        var singleMime: String? = null
        for (n in list) {
            val f = File(d, n)
            if (f.isFile) {
                uris.add(FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", f))
                if (list.size == 1) {
                    singleMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
                }
            }
        }
        if (uris.isEmpty()) {
            act.toast("Select files to share")
            return
        }
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
        act.runOnUiThread {
            try {
                act.startActivity(Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (e: Exception) {
                act.toast("No app available to share")
            }
        }
    }

    @JavascriptInterface fun inspectZip(p: String, n: String): String = try {
        val f = File(dir(p), n)
        val arr = JSONArray()
        if (f.isFile) {
            ZipInputStream(BufferedInputStream(FileInputStream(f))).use { zis ->
                var e: ZipEntry? = zis.nextEntry
                var count = 0
                while (e != null && count < 500) {
                    arr.put(
                        JSONObject()
                            .put("n", e.name)
                            .put("s", maxOf(0L, e.size))
                            .put("dir", e.isDirectory)
                    )
                    zis.closeEntry()
                    e = zis.nextEntry
                    count++
                }
            }
        }
        arr.toString()
    } catch (e: Exception) { "[]" }

    @JavascriptInterface fun zip(p: String, ns: String, outName: String) {
        Thread {
            try {
                val baseDir = dir(p)
                val targetNames = names(ns).ifEmpty {
                    baseDir.listFiles()?.map { it.name } ?: emptyList()
                }
                val cleanName = if (outName.endsWith(".zip", true)) outName else "$outName.zip"
                safe(cleanName)
                val outFile = uniq(baseDir, cleanName)
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
                    for (item in targetNames) {
                        val f = File(baseDir, item)
                        if (f.exists()) addEntry(zos, f, f.name)
                    }
                }
                val js = "window.__onZipDone&&window.__onZipDone('zip',${JSONObject.quote(p)},${JSONObject.quote(outFile.name)},true)"
                act.runOnUiThread { act.web.evaluateJavascript(js, null) }
            } catch (e: Exception) {
                val js = "window.__onZipDone&&window.__onZipDone('zip',${JSONObject.quote(p)},'',false)"
                act.runOnUiThread { act.web.evaluateJavascript(js, null) }
            }
        }.start()
    }

    @JavascriptInterface fun extractZip(p: String, n: String, destPath: String) {
        Thread {
            try {
                val srcFile = File(dir(p), n)
                if (!srcFile.isFile) throw IllegalArgumentException("Missing file")
                val baseDest = dir(destPath).apply { mkdirs() }
                val folderName = n.substringBeforeLast('.', n).ifEmpty { "Extracted" }
                val targetDir = uniq(baseDest, folderName).apply { mkdirs() }
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
                val js = "window.__onZipDone&&window.__onZipDone('extract',${JSONObject.quote(destPath)},${JSONObject.quote(targetDir.name)},true)"
                act.runOnUiThread { act.web.evaluateJavascript(js, null) }
            } catch (e: Exception) {
                val js = "window.__onZipDone&&window.__onZipDone('extract',${JSONObject.quote(destPath)},'',false)"
                act.runOnUiThread { act.web.evaluateJavascript(js, null) }
            }
        }.start()
    }

    @JavascriptInterface fun trash(p: String, ns: String) {
        val d = dir(p); trashDir.mkdirs()
        val idx = File(trashDir, "index.json"); val arr = if (idx.exists()) JSONArray(idx.readText()) else JSONArray()
        for (n in names(ns)) {
            val src = File(d, n); if (!src.exists()) continue
            val size = if (src.isDirectory) dirSize(src) else src.length()
            val id = System.currentTimeMillis().toString() + "_" + Math.abs(n.hashCode()); val dst = File(trashDir, id)
            if (!src.renameTo(dst)) { src.copyRecursively(dst, true); src.deleteRecursively() }
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
        idx.writeText(arr.toString())
    }

    @JavascriptInterface fun listTrash(maxDays: Int): String {
        return try {
        val idx = File(trashDir, "index.json")
        if (!idx.exists()) return "[]"
        val arr = JSONArray(idx.readText())
        val out = JSONArray()
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
            val size = o.optLong("s", if (f.isDirectory) dirSize(f) else f.length())
            o.put("s", size).put("dir", f.isDirectory)
            kept.put(o)
            out.put(o)
        }
        if (dirty) idx.writeText(kept.toString())
            out.toString()
        } catch (e: Exception) { "[]" }
    }

    @JavascriptInterface fun restoreTrash(id: String): Boolean {
        return try {
        val idx = File(trashDir, "index.json")
        if (!idx.exists()) return false
        val arr = JSONArray(idx.readText())
        val kept = JSONArray()
        var restored = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) {
                val src = File(trashDir, id)
                val fromRel = o.optString("from", "")
                val name = o.optString("name", "Restored")
                val destDir = if (fromRel.isEmpty()) root else File(root, fromRel).apply { mkdirs() }
                val target = uniq(destDir, name)
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
        idx.writeText(kept.toString())
            restored
        } catch (e: Exception) { false }
    }

    @JavascriptInterface fun wipeTrash(id: String): Boolean {
        return try {
        val idx = File(trashDir, "index.json")
        if (!idx.exists()) return false
        val arr = JSONArray(idx.readText())
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) {
                File(trashDir, id).deleteRecursively()
            } else {
                kept.put(o)
            }
        }
        idx.writeText(kept.toString())
            true
        } catch (e: Exception) { false }
    }

    @JavascriptInterface fun emptyTrash(): Boolean = try {
        if (trashDir.exists()) {
            trashDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        true
    } catch (e: Exception) { false }

    @JavascriptInterface fun rename(p: String, old: String, new: String) { safe(new); File(dir(p), old).renameTo(File(dir(p), new)) }
    @JavascriptInterface fun mkdir(p: String, n: String) { safe(n); File(dir(p), n).mkdirs() }
    @JavascriptInterface fun createFile(p: String, n: String) { safe(n); File(dir(p), n).createNewFile() }
    @JavascriptInterface fun move(s: String, ns: String, d: String) = xfer(s, ns, d, true)
    @JavascriptInterface fun copy(s: String, ns: String, d: String) = xfer(s, ns, d, false)

    private fun xfer(s: String, ns: String, d: String, mv: Boolean) {
        val sd = dir(s); val dd = dir(d); dd.mkdirs()
        for (n in names(ns)) {
            val src = File(sd, n); if (!src.exists()) continue
            if (mv && sd.canonicalPath == dd.canonicalPath) continue
            if (src.isDirectory && (dd.canonicalPath + "/").startsWith(src.canonicalPath + "/")) continue
            val t = uniq(dd, n)
            if (mv) { if (!src.renameTo(t)) { src.copyRecursively(t); src.deleteRecursively() } } else src.copyRecursively(t)
        }
    }

    private fun ip(): String = try {
        val all = NetworkInterface.getNetworkInterfaces().toList()
        val pref = all.filter { it.name.startsWith("wlan") || it.name.startsWith("ap") }.ifEmpty { all }
        pref.flatMap { it.inetAddresses.toList() }.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress ?: "0.0.0.0"
    } catch (e: Exception) { "0.0.0.0" }

    @JavascriptInterface fun ftp(action: String, opts: String): String {
        try {
            ftp?.stop(); ftp = null
            if (action == "start") {
                val o = JSONObject(opts)
                val fs = FtpServerFactory(); val lf = ListenerFactory(); lf.port = o.optInt("port", 2121)
                fs.addListener("default", lf.createListener())
                val um = PropertiesUserManagerFactory().createUserManager()
                val u = BaseUser(); u.name = o.optString("user", "admin"); u.password = o.optString("pass", "files123")
                u.homeDirectory = root.absolutePath; u.setAuthorities(listOf<Authority>(WritePermission())); um.save(u)
                fs.userManager = um
                ftp = fs.createServer().also { it.start() }
            }
        } catch (e: Exception) { act.toast("FTP: " + (e.message ?: "failed")); return JSONObject().put("ip", ip()).put("error", e.message ?: "failed").toString() }
        return JSONObject().put("ip", ip()).toString()
    }
}
