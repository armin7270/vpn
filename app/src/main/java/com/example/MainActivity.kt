package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.MainLayout
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge to Edge immersive layout activation
        enableEdgeToEdge()
        
        setContent {
            // Read viewmodel preference state or fallback to system dark theme
            val appThemePref by viewModel.appTheme.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()
            val systemTheme = isSystemInDarkTheme()
            
            var isDarkThemeCustom by remember(appThemePref) {
                mutableStateOf(
                    when (appThemePref) {
                        "dark" -> true
                        "light" -> false
                        else -> systemTheme
                    }
                )
            }

            val context = LocalContext.current
            val locale = remember(appLanguage) { Locale(appLanguage) }
            val localizedContext = remember(locale) {
                val config = android.content.res.Configuration(context.resources.configuration)
                config.setLocale(locale)
                context.createConfigurationContext(config)
            }
            val layoutDirection = remember(appLanguage) {
                if (appLanguage == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalLayoutDirection provides layoutDirection
            ) {
                MyApplicationTheme(darkTheme = isDarkThemeCustom) {
                    MainLayout(
                        viewModel = viewModel,
                        isDarkThemeCustom = isDarkThemeCustom,
                        onToggleTheme = { dark ->
                            isDarkThemeCustom = dark
                            viewModel.updateTheme(if (dark) "dark" else "light")
                        }
                    )
                }
            }
        }
    }
}
