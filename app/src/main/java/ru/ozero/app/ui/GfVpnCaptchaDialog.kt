package ru.ozero.app.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginegfvpn.GfVpnCaptchaState
import java.io.ByteArrayInputStream

@Composable
fun GfVpnCaptchaDialog() {
    val pending by GfVpnCaptchaState.challenge.collectAsStateWithLifecycle()
    val challenge = pending ?: return
    key(challenge) {
        Dialog(
            onDismissRequest = { GfVpnCaptchaState.cancel(challenge) },
            properties = DialogProperties(dismissOnClickOutside = false),
        ) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.gfvpn_captcha_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.gfvpn_captcha_summary))
                    AndroidView(
                        factory = { context -> WebView(context).apply { configureChallenge(challenge) } },
                        modifier = Modifier.fillMaxWidth().height(400.dp),
                        onRelease = { web -> web.stopLoading(); web.destroy() },
                    )
                    TextButton(onClick = { GfVpnCaptchaState.cancel(challenge) }) {
                        Text(stringResource(R.string.gfvpn_captcha_cancel))
                    }
                }
            }
        }
    }
}

// JavaScript is needed for the provider's manual challenge. No Android bridge,
// external navigation, file access, or certificate exceptions are permitted.
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureChallenge(challenge: GfVpnCaptchaState.Challenge) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_NO_CACHE
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !challenge.permits(request.url.toString())

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (challenge.permits(request.url.toString())) return null
            return WebResourceResponse(
                "text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)),
            )
        }
    }
    loadUrl(challenge.url)
}
