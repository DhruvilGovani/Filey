package com.glimpse.files

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.*
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : Activity() {
    lateinit var web: WebView
    private var asked = false
    private val origin = "https://appassets.androidplatform.net"

    fun toast(s: String) = runOnUiThread { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }

    fun granted() =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun askAccess() {
        asked = true
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
                catch (e: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        } catch (e: Exception) { toast("Open Settings > Apps > Special app access > All files access and allow Glimpse") }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = 0xFF0A1230.toInt(); window.navigationBarColor = 0xFF0A1230.toInt()
        web = WebView(this); web.setBackgroundColor(0xFF0A1230.toInt()); setContentView(web)
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = false; allowContentAccess = false }
        val docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) = loader.shouldInterceptRequest(r.url)
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = !r.url.toString().startsWith(origin)
            override fun onPageStarted(v: WebView, u: String?, f: android.graphics.Bitmap?) { if (!docStart) v.evaluateJavascript(SHIM, null) }
        }
        web.addJavascriptInterface(Bridge(this), "GlimpseAndroid")
        if (docStart) WebViewCompat.addDocumentStartJavaScript(web, SHIM, setOf(origin))
        if (!granted()) askAccess()
        web.loadUrl("$origin/assets/index.html")
    }

    override fun onResume() { super.onResume(); if (asked && granted()) { asked = false; web.reload() } }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { if (granted()) web.reload() }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() { web.evaluateJavascript(BACK) { if (it != "\"h\"") finish() } }

    companion object {
        // Gives the page the window.GlimpseNative object it expects (promises + arrays) on top of the string-only Java bridge.
        const val SHIM = """(function(){var A=window.GlimpseAndroid,S=JSON.stringify,P=JSON.parse;window.GlimpseNative={
list:function(p){return Promise.resolve(P(A.list(S(p))))},info:function(){return Promise.resolve(P(A.info()))},
calcSizes:function(p){A.calcSizes(S(p))},
recent:function(l){return Promise.resolve(P(A.recent(l||15)))},byCategory:function(c){return Promise.resolve(P(A.byCategory(c)))},
inspectZip:function(p,n){return Promise.resolve(P(A.inspectZip(S(p),n)))},
zip:function(p,n,o){A.zip(S(p),S(n),o)},extractZip:function(p,n,d){A.extractZip(S(p),n,S(d))},
open:function(p,n){A.open(S(p),n)},trash:function(p,n){A.trash(S(p),S(n))},rename:function(p,o,n){A.rename(S(p),o,n)},
move:function(s,n,d){A.move(S(s),S(n),S(d))},copy:function(s,n,d){A.copy(S(s),S(n),S(d))},
mkdir:function(p,n){A.mkdir(S(p),n)},createFile:function(p,n){A.createFile(S(p),n)},
access:function(){return A.hasAccess()},requestAccess:function(){A.requestAccess()},
ftp:function(a,o){var r=P(A.ftp(a,S(o)));window.FIP=r.ip;return r}}})();"""
        const val BACK = """(function(){var o=[].slice.call(document.querySelectorAll('.ov.on')).pop();if(o){o.querySelector('[data-x]').click();return 'h'}
var s=document.querySelector('.scrim.on');if(s){s.click();return 'h'}
if(sel){sel=null;browse();return 'h'}
if(document.getElementById('nav').dataset.v==='browse'&&cur.length){cur.pop();browse();return 'h'}return 'x'})()"""
    }
}
