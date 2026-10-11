package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SearchMode
import com.example.overlay.ProcessTextActivity
import com.example.ui.screens.MainAppContent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FactCheckViewModel
import com.example.widget.KhojSearchWidgetProvider

class MainActivity : ComponentActivity() {
    private val viewModel: FactCheckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
            val renderingLanguage by viewModel.renderingLanguage.collectAsStateWithLifecycle()
            val bengaliFontPreference by viewModel.bengaliFontPreference.collectAsStateWithLifecycle()
            val isBn = renderingLanguage == "bn"
            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (themePreference) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }

            MyApplicationTheme(
                darkTheme = darkTheme,
                isBn = isBn,
                bengaliFont = bengaliFontPreference
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // Handle overlay fact-check from ProcessTextActivity (text selection in other apps)
        if (intent.action == ProcessTextActivity.ACTION_OVERLAY_FACT_CHECK) {
            val claimText = intent.getStringExtra(ProcessTextActivity.EXTRA_CLAIM_TEXT)
            val imageUriString = intent.getStringExtra("extra_image_uri")
            when {
                !claimText.isNullOrBlank() -> viewModel.setPendingClaim(claimText)
                !imageUriString.isNullOrBlank() -> {
                    val uri = android.net.Uri.parse(imageUriString)
                    viewModel.setPendingImageUri(uri)
                }
            }
            return
        }

        val isWidgetLaunch = intent.getBooleanExtra(KhojSearchWidgetProvider.EXTRA_WIDGET_LAUNCH, false)
        val modeExtra = intent.getStringExtra(KhojSearchWidgetProvider.EXTRA_SEARCH_MODE)
        val queryExtra = intent.getStringExtra(KhojSearchWidgetProvider.EXTRA_SEARCH_QUERY)
        val autoFocus = intent.getBooleanExtra(KhojSearchWidgetProvider.EXTRA_AUTO_FOCUS, true)

        if (isWidgetLaunch || modeExtra != null) {
            val mode = SearchMode.fromIntentExtra(modeExtra)
            viewModel.handleIncomingWidgetIntent(mode, queryExtra, autoFocus)
            intent.removeExtra(KhojSearchWidgetProvider.EXTRA_WIDGET_LAUNCH)
        }
    }
}
