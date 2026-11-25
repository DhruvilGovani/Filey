package com.glimpse.files

import android.content.ClipData
import android.content.Intent
import android.os.Environment
import android.os.StatFs
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
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

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

    @JavascriptInterface fun hasAccess(): Boolean = act.granted()
    @JavascriptInterface fun requestAccess() { act.runOnUiThread { act.askAccess() } }

    @JavascriptInterface fun list(p: String): String = try {
        val a = JSONArray()
        dir(p).listFiles()?.forEach {
            val isDir = it.isDirectory
            var cnt = 0
            var size = 0L
            if (isDir) {
                val kids = it.listFiles()
                if (kids != null) {
                    cnt = kids.size
                    for (k in kids) if (k.isFile) size += k.length()
                }
            } else {
                size = it.length()
            }
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
        val st = StatFs(root.path); val total = st.totalBytes; val used = total - st.availableBytes
        var img = 0L; var vid = 0L; var arc = 0L; var doc = 0L; var oth = 0L
        try {
            act.contentResolver.query(MediaStore.Files.getContentUri("external"), arrayOf(FileColumns.SIZE, FileColumns.MEDIA_TYPE, FileColumns.MIME_TYPE), null, null, null)?.use {
                while (it.moveToNext()) {
                    val s = it.getLong(0); val t = it.getInt(1); val m = it.getString(2) ?: ""
                    when {
                        t == FileColumns.MEDIA_TYPE_IMAGE -> img += s
                        t == FileColumns.MEDIA_TYPE_VIDEO -> vid += s
                        m.contains("zip") || m.contains("rar") || m.contains("7z") || m.contains("tar") -> arc += s
                        m.contains("pdf") || m.contains("word") || m.contains("sheet") || m.contains("presentation") || m.startsWith("text/") -> doc += s
                        else -> oth += s
                    }
                }
            }
        } catch (_: Exception) {}
        val sys = maxOf(0L, used - img - vid - arc - doc - oth)
        fun b(n: String, v: Long) = JSONObject().put("name", n).put("bytes", v)
        val br = JSONArray().put(b("Images", img)).put(b("Videos", vid)).put(b("Archives", arc)).put(b("Documents", doc)).put(b("Other", oth)).put(b("System", sys))
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

    @JavascriptInterface fun trash(p: String, ns: String) {
        val d = dir(p); trashDir.mkdirs()
        val idx = File(trashDir, "index.json"); val arr = if (idx.exists()) JSONArray(idx.readText()) else JSONArray()
        for (n in names(ns)) {
            val src = File(d, n); if (!src.exists()) continue
            val id = System.currentTimeMillis().toString() + "_" + Math.abs(n.hashCode()); val dst = File(trashDir, id)
            if (!src.renameTo(dst)) { src.copyRecursively(dst, true); src.deleteRecursively() }
            arr.put(JSONObject().put("id", id).put("name", n).put("from", d.relativeTo(root).path).put("t", System.currentTimeMillis()))
        }
        idx.writeText(arr.toString())
    }

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
