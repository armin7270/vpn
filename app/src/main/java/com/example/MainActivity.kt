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

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import android.net.VpnService
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var showVpnDeniedDialog by mutableStateOf(false)

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.toggleVpnConnection(this)
        } else {
            showVpnDeniedDialog = true
        }
    }

    fun startVpnPreparation() {
        try {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                vpnPrepareLauncher.launch(intent)
            } else {
                viewModel.toggleVpnConnection(this)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error preparing VpnService", e)
        }
    }

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
                LocalLayoutDirection provides layoutDirection,
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                MyApplicationTheme(darkTheme = isDarkThemeCustom) {
                    if (showVpnDeniedDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showVpnDeniedDialog = false },
                            title = {
                                androidx.compose.material3.Text(
                                    text = androidx.compose.ui.res.stringResource(R.string.vpn_permission_denied_title),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (isDarkThemeCustom) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1E293B)
                                )
                            },
                            text = {
                                androidx.compose.material3.Text(
                                    text = androidx.compose.ui.res.stringResource(R.string.vpn_permission_denied_desc),
                                    fontSize = 14.sp,
                                    color = if (isDarkThemeCustom) androidx.compose.ui.graphics.Color(0xCCFFFFFF) else androidx.compose.ui.graphics.Color(0xFF475569)
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.Button(
                                    onClick = { showVpnDeniedDialog = false },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = androidx.compose.ui.graphics.Color(0xFF007AFF)
                                    )
                                ) {
                                    androidx.compose.material3.Text(
                                        text = androidx.compose.ui.res.stringResource(R.string.vpn_permission_denied_ok),
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                        )
                    }

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
