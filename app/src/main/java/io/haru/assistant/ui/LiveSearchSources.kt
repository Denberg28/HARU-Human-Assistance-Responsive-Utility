package io.haru.assistant.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.haru.assistant.onlineai.LiveSearchPolicy
import io.haru.assistant.onlineai.WebSource
import java.io.ByteArrayInputStream

@Composable
internal fun LiveSearchSources(sources: List<WebSource>, suggestionsHtml: String) {
    val context = LocalContext.current
    fun open(url: String) {
        if (LiveSearchPolicy.safeUrl(url)) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }
    var expanded by remember(sources) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text("Sources · ${sources.size}  ${if (expanded) "▴" else "▾"}")
        }
        if (expanded) sources.forEach { source ->
            TextButton(onClick = { open(source.url) }) { Text(source.title) }
        }
        if (suggestionsHtml.isNotBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (request.isForMainFrame && request.hasGesture()) open(request.url.toString())
                                return true
                            }
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val uri = request.url
                                return if (uri.scheme == "https" && uri.host in setOf("www.gstatic.com", "www.google.com") && request.method == "GET" && !request.isForMainFrame) null
                                else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }
                        }
                    }
                },
                update = { view ->
                    if (view.tag != suggestionsHtml) {
                        view.tag = suggestionsHtml
                        val csp = "default-src 'none'; style-src 'unsafe-inline'; img-src data: https://www.gstatic.com https://www.google.com; base-uri 'none'; form-action 'none'"
                        view.loadDataWithBaseURL("https://www.google.com/", "<meta http-equiv=\"Content-Security-Policy\" content=\"$csp\">$suggestionsHtml", "text/html", "UTF-8", null)
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
            )
        }
    }
}
