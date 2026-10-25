package com.example.dmsonly

import android.os.Bundle
import android.webkit.WebView
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.example.dmsonly.ui.theme.DeReelTheme
import com.example.dmsonly.web.InstagramBackNavigation
import com.example.dmsonly.web.InstagramWebView
import com.example.dmsonly.web.clearInstagramSession

private const val WEBVIEW_STATE_KEY = "webview_state"

class MainActivity : ComponentActivity() {

    private var activeWebView: WebView? = null
    private var restoredWebViewState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredWebViewState = savedInstanceState?.getBundle(WEBVIEW_STATE_KEY)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val webView = activeWebView
                    if (webView == null) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        return
                    }

                    if (InstagramBackNavigation.goBack(webView)) {
                        return
                    }

                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )

        setContent {
            DeReelTheme {
                val darkTheme = isSystemInDarkTheme()
                val systemBarColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    window.statusBarColor = systemBarColor
                    val supportsLightNavigationBar = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                    window.navigationBarColor =
                        if (supportsLightNavigationBar) systemBarColor else Color.BLACK
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = supportsLightNavigationBar && !darkTheme
                    }
                }
                DeReelApp(
                    initialWebViewState = restoredWebViewState,
                    onWebViewReady = { activeWebView = it }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        activeWebView?.let { webView ->
            val webViewState = Bundle()
            webView.saveState(webViewState)
            outState.putBundle(WEBVIEW_STATE_KEY, webViewState)
        }
        super.onSaveInstanceState(outState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeReelApp(
    initialWebViewState: Bundle?,
    onWebViewReady: (WebView) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DeReel") },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = {
                                    showMenu = false
                                    errorMessage = null
                                    reloadToken++
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Instagram session") },
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                InstagramWebView(
                    modifier = Modifier.fillMaxSize(),
                    initialWebViewState = initialWebViewState,
                    reloadToken = reloadToken,
                    onWebViewReady = {
                        webView = it
                        onWebViewReady(it)
                    },
                    onLoadingChanged = { isLoading = it },
                    onError = { errorMessage = it },
                    onRecovered = { errorMessage = null }
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                errorMessage?.let { message ->
                    ErrorOverlay(
                        message = message,
                        onRetry = {
                            errorMessage = null
                            reloadToken++
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Instagram session?") },
            text = {
                Text(
                    "This clears this app's WebView cookies, cache, and history. " +
                        "It does not change your Instagram account."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        clearInstagramSession(webView) {
                            errorMessage = null
                            isLoading = true
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message)
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
