package com.example.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import android.content.Context
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.database.VpnServerEntity
import com.example.service.ConnectionState
import com.example.service.VpnServiceState
import com.example.ui.components.ConnectionGlowButton
import com.example.ui.components.GlassCard
import com.example.ui.components.TipsTopLogo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(
    viewModel: MainViewModel,
    isDarkThemeCustom: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val allServers by viewModel.allServers.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    
    // Add Node dialog state
    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showClipboardImportDialog by remember { mutableStateOf(false) }

    // Edge-to-edge gradient canvas supporting professional polish atmosphere
    val backgroundBrush = if (isDarkThemeCustom) {
        Brush.linearGradient(
            listOf(
                Color(0xFF080B14),
                Color(0xFF0B1224),
                Color(0xFF05070D)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFF8FAFC),
                Color(0xFFF1F5F9),
                Color(0xFFE2E8F0)
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Transparent,
        bottomBar = {
            // Elegant Apple iOS Floating Translucent Menu
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    cornerRadius = 28.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabItem(
                            tab = MainViewModel.Tab.DASHBOARD,
                            currentTab = selectedTab,
                            icon = Icons.Outlined.Home,
                            selectedIcon = Icons.Filled.Home,
                            label = stringResource(R.string.home),
                            onClick = { viewModel.selectedTab.value = MainViewModel.Tab.DASHBOARD }
                        )
                        TabItem(
                            tab = MainViewModel.Tab.SERVERS,
                            currentTab = selectedTab,
                            icon = Icons.Outlined.Dns,
                            selectedIcon = Icons.Filled.Dns,
                            label = stringResource(R.string.servers),
                            onClick = { 
                                viewModel.selectedTab.value = MainViewModel.Tab.SERVERS 
                                viewModel.runActiveLatencyCheck() 
                            }
                        )
                        TabItem(
                            tab = MainViewModel.Tab.SUBSCRIPTIONS,
                            currentTab = selectedTab,
                            icon = Icons.Outlined.Link,
                            selectedIcon = Icons.Filled.Link,
                            label = stringResource(R.string.subscriptions),
                            onClick = { viewModel.selectedTab.value = MainViewModel.Tab.SUBSCRIPTIONS }
                        )
                        TabItem(
                            tab = MainViewModel.Tab.SETTINGS,
                            currentTab = selectedTab,
                            icon = Icons.Outlined.Settings,
                            selectedIcon = Icons.Filled.Settings,
                            label = stringResource(R.string.settings),
                            onClick = { viewModel.selectedTab.value = MainViewModel.Tab.SETTINGS }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Atmospheric glass blurry shapes
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .offset(x = (-80).dp, y = (-40).dp)
                    .background(
                        color = if (isDarkThemeCustom) Color(0x332563EB) else Color(0x103B82F6),
                        shape = CircleShape
                    )
                    .blur(100.dp)
            )
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 100.dp, y = 140.dp)
                    .background(
                        color = if (isDarkThemeCustom) Color(0x1A4F46E5) else Color(0x08EC4899),
                        shape = CircleShape
                    )
                    .blur(110.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Top Header Banner
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TipsTopLogo(size = 46.dp, showGrayBorder = true)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TipsTop",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color(0xFF335DF7),
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Network",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = if (isDarkThemeCustom) Color.White else Color(0xFF1F2937),
                                    letterSpacing = (-0.5).sp
                                )
                            }
                            Text(
                                text = "XRAY-CORE STABLE",
                                fontSize = 10.sp,
                                color = if (isDarkThemeCustom) Color(0x80FFFFFF) else Color(0xFF64748B),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Connection Status Pill
                    ConnectionStatusPill(state = vpnState.state, isDark = isDarkThemeCustom)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Screens Routing with sleek slide / crossfade transitions
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                        },
                        label = "ScreenTransition"
                    ) { tab ->
                        when (tab) {
                            MainViewModel.Tab.DASHBOARD -> DashboardScreen(
                                viewModel = viewModel,
                                vpnState = vpnState,
                                activeServer = activeServer,
                                isDark = isDarkThemeCustom
                            )
                            MainViewModel.Tab.SERVERS -> ServersScreen(
                                viewModel = viewModel,
                                servers = allServers,
                                activeServer = activeServer,
                                isDark = isDarkThemeCustom,
                                onOpenAddNode = { showClipboardImportDialog = true }
                            )
                            MainViewModel.Tab.SUBSCRIPTIONS -> SubscriptionsScreen(
                                viewModel = viewModel,
                                subs = subscriptions,
                                isDark = isDarkThemeCustom
                            )
                            MainViewModel.Tab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                isDark = isDarkThemeCustom,
                                onToggleTheme = onToggleTheme
                            )
                        }
                    }
                }
            }
        }

        if (showAddNodeDialog) {
            AddNodeManualDialog(
                onDismiss = { showAddNodeDialog = false },
                onAddNode = { name, protocol, host, port, secret, sni, pbk, security ->
                    viewModel.addManualXrayNode(name, protocol, host, port, secret, sni, pbk, security)
                    showAddNodeDialog = false
                    Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                },
                isDark = isDarkThemeCustom
            )
        }

        if (showClipboardImportDialog) {
            ClipboardImportPopupDialog(
                onDismiss = { showClipboardImportDialog = false },
                onLaunchManualAdd = { 
                    showClipboardImportDialog = false
                    showAddNodeDialog = true 
                },
                viewModel = viewModel,
                isDark = isDarkThemeCustom
            )
        }
    }
}

@Composable
fun TabItem(
    tab: MainViewModel.Tab,
    currentTab: MainViewModel.Tab,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val isSelected = tab == currentTab
    val activeColor = Color(0xFF007AFF) // Cupertino standard Blue
    val inactiveColor = if (currentTab.hashCode() % 2 == 0) Color(0x60FFFFFF) else Color(0xFF64748B)

    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeColor else inactiveColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ConnectionStatusPill(state: ConnectionState, isDark: Boolean) {
    val (textRes, bgColor, textColor) = when (state) {
        ConnectionState.CONNECTED -> Triple(R.string.status_connected, Color(0x3034C759), Color(0xFF34C759))
        ConnectionState.CONNECTING -> Triple(R.string.status_connecting, Color(0x30FFCC00), Color(0xFFFFCC00))
        ConnectionState.RECONNECTING -> Triple(R.string.status_reconnecting, Color(0x30FF9500), Color(0xFFFF9500))
        ConnectionState.DISCONNECTED -> Triple(R.string.status_disconnected, if (isDark) Color(0x30FFFFFF) else Color(0x15000000), if (isDark) Color(0x80FFFFFF) else Color(0xFF64748B))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = stringResource(textRes),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    vpnState: VpnServiceState,
    activeServer: VpnServerEntity?,
    isDark: Boolean
) {
    val context = LocalContext.current

    val vpnPrepareLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.toggleVpnConnection(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Giant Connected Button with visual breathing waves
        ConnectionGlowButton(
            isConnected = vpnState.state == ConnectionState.CONNECTED,
            isConnecting = vpnState.state == ConnectionState.CONNECTING || vpnState.state == ConnectionState.RECONNECTING,
            onClick = {
                val currentState = vpnState.state
                if (currentState == ConnectionState.CONNECTED || currentState == ConnectionState.CONNECTING || currentState == ConnectionState.RECONNECTING) {
                    viewModel.toggleVpnConnection(context)
                } else {
                    val intent = android.net.VpnService.prepare(context)
                    if (intent != null) {
                        vpnPrepareLauncher.launch(intent)
                    } else {
                        viewModel.toggleVpnConnection(context)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (vpnState.state == ConnectionState.CONNECTED) stringResource(R.string.status_connected).uppercase() else stringResource(R.string.connect).uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (vpnState.state == ConnectionState.CONNECTED) Color(0xFF34C759) else if (isDark) Color(0x70FFFFFF) else Color(0xFF64748B),
            letterSpacing = 1.5.sp,
            modifier = Modifier.testTag("tap_connection_status")
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Live Upload/Download Speed Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Download Speed
            Box(modifier = Modifier.weight(1f)) {
                GlassCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x2034C759)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.download),
                                fontSize = 11.sp,
                                color = if (isDark) Color(0x80FFFFFF) else Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatSpeedText(vpnState.downloadSpeed),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }

            // Upload Speed
            Box(modifier = Modifier.weight(1f)) {
                GlassCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x20007AFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = Color(0xFF007AFF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.upload),
                                fontSize = 11.sp,
                                color = if (isDark) Color(0x80FFFFFF) else Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatSpeedText(vpnState.uploadSpeed),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large Detailed Node connection Info
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "SELECTED SECURED TUNNEL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0x70FFFFFF) else Color(0xFF64748B),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dynamic circular icon depending on selected protocol
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E1E2F) else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (activeServer?.protocol == "VLESS") Icons.Default.Security else Icons.Default.Public,
                        contentDescription = null,
                        tint = if (isDark) Color.White else Color(0xFF1E293B),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeServer?.name ?: stringResource(R.string.auto_select),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeServer?.address ?: "No host"} : ${activeServer?.port ?: "No port"}",
                        fontSize = 12.sp,
                        color = if (isDark) Color(0x70FFFFFF) else Color(0xFF64748B)
                    )
                }

                // Protocol badge and Latency (ping) Info
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0x30FFFFFF) else Color(0x10000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activeServer?.protocol ?: "VLESS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else Color(0xFF1E293B)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (activeServer?.ping != null && activeServer.ping > 0) "${activeServer.ping} ms" else "Offline",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeServer?.ping != null && activeServer.ping in 1..200) Color(0xFF34C759) else Color(0xFFFF453A)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (activeServer?.ping != null && activeServer.ping > 0) Color(0xFF34C759) else Color(0xFFFF453A))
                        )
                    }
                }
            }

            // Divider Line
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (isDark) Color(0x15FFFFFF) else Color(0x08000000))
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Usage Stats list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.traffic_used),
                        fontSize = 11.sp,
                        color = if (isDark) Color(0x60FFFFFF) else Color(0xFF64748B)
                    )
                    Text(
                        text = formatBytesText(vpnState.totalDownload + vpnState.totalUpload),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF1E293B)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "DURATION Connected",
                        fontSize = 11.sp,
                        color = if (isDark) Color(0x60FFFFFF) else Color(0xFF64748B)
                    )
                    Text(
                        text = formatDurationText(vpnState.connectionDuration),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF1E293B)
                    )
                }
            }
        }
    }
}

@Composable
fun ServersScreen(
    viewModel: MainViewModel,
    servers: List<VpnServerEntity>,
    activeServer: VpnServerEntity?,
    isDark: Boolean,
    onOpenAddNode: () -> Unit
) {
    var qrInputText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Node Importing Widget
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.import_qr),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = qrInputText,
                onValueChange = { qrInputText = it },
                placeholder = { Text("vless://... OR trojan://... OR vmess://...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qr_import_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF007AFF),
                    unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x20000000)
                ),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (qrInputText.trim().isNotEmpty()) {
                                val success = viewModel.importConfigUri(qrInputText)
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                                    qrInputText = ""
                                } else {
                                    Toast.makeText(context, context.getString(R.string.unsupported_qr), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add node", tint = Color(0xFF007AFF))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Row of actions: Auto select node and Custom Manual Adding dialog
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.triggerAutoServerSelection() },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("auto_select_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.auto_select), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpenAddNode,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("add_server_button"),
                border = BorderStroke(1.dp, Color(0xFF007AFF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Create, contentDescription = null, tint = Color(0xFF007AFF))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.add_server), fontSize = 12.sp, color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Nodes listing
        Text(
            text = "AVAILABLE SECURED NETWORKS (${servers.size})",
            fontSize = 10.sp,
            color = if (isDark) Color(0x60FFFFFF) else Color(0xFF64748B),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No connections configured yet.\nPaste a subscription link or add nodes manually.",
                    textAlign = TextAlign.Center,
                    color = if (isDark) Color(0x40FFFFFF) else Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(servers, key = { it.id }) { server ->
                    val isActive = server.id == activeServer?.id
                    ServerRowItem(
                        server = server,
                        isActive = isActive,
                        isDark = isDark,
                        onSelect = { viewModel.selectServer(server.id) },
                        onDelete = { viewModel.deleteServer(server.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun ServerRowItem(
    server: VpnServerEntity,
    isActive: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("vpn_node_card_${server.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0x30007AFF) else if (isDark) Color(0x18FFFFFF) else Color(0x40FFFFFF)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) Color(0xFF007AFF) else if (isDark) Color(0x0Fffffff) else Color(0x08000000)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Color(0xFF007AFF) else if (isDark) Color(0xFF1E1E2F) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (server.protocol.uppercase()) {
                        "VLESS" -> Icons.Default.Security
                        "TROJAN" -> Icons.Default.Lock
                        else -> Icons.Default.Public
                    },
                    tint = if (isActive) Color.White else if (isDark) Color.White else Color(0xFF1E293B),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isDark) Color.White else Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${server.protocol} • ${server.address}:${server.port}",
                    fontSize = 11.sp,
                    color = if (isDark) Color(0x70FFFFFF) else Color(0xFF64748B)
                )
            }

            // Ping and delete button group
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Latency indicator
                Text(
                    text = if (server.ping > 0) "${server.ping} ms" else "Idle",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (server.ping in 1..200) Color(0xFF34C759) else if (server.ping > 200) Color(0xFFFFCC00) else if (isDark) Color(0x40FFFFFF) else Color(0xFF94A3B8)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("delete_node_button_${server.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove server",
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionsScreen(
    viewModel: MainViewModel,
    subs: List<com.example.data.database.VpnSubscriptionEntity>,
    isDark: Boolean
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val isUpdating by viewModel.isUpdating.collectAsStateWithLifecycle()
    val syncError by viewModel.syncErrorMessage.collectAsStateWithLifecycle()
    
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Add Subscription Form
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.add_subscription).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0x70FFFFFF) else Color(0xFF64748B),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Provider Title", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sub_title_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF007AFF)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Resource URL (Base64)", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sub_url_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF007AFF)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    if (title.trim().isNotEmpty() && url.trim().isNotEmpty()) {
                        viewModel.addSubscriptionLink(title, url)
                        title = ""
                        url = ""
                    } else {
                        Toast.makeText(context, "Fill inputs first", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isUpdating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("add_sub_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(text = "Register Subscription Link", fontWeight = FontWeight.Bold)
                }
            }

            syncError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Sync Error: $it", color = Color(0xFFFF3B30), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subscription Title with Global refresh action button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MANAGED CHANNELS (${subs.size})",
                fontSize = 10.sp,
                color = if (isDark) Color(0x60FFFFFF) else Color(0xFF64748B),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            IconButton(
                onClick = { viewModel.syncAllSubscriptions() },
                enabled = !isUpdating && subs.isNotEmpty(),
                modifier = Modifier.testTag("sync_all_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync subs",
                    tint = Color(0xFF007AFF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (subs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No subscription links registered.",
                    color = if (isDark) Color(0x40FFFFFF) else Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(subs, key = { it.url }) { sub ->
                    SubscriptionRowItem(
                        sub = sub,
                        isDark = isDark,
                        onDelete = { viewModel.deleteSubLink(sub.url) }
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionRowItem(
    sub: com.example.data.database.VpnSubscriptionEntity,
    isDark: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0x18FFFFFF) else Color(0x40FFFFFF)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = if (isDark) Color.White else Color(0xFF1E293B),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isDark) Color.White else Color(0xFF1E293B)
                )
                Text(
                    text = sub.url,
                    fontSize = 11.sp,
                    color = if (isDark) Color(0x60FFFFFF) else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.last_updated, formatTime(sub.lastUpdated)),
                    fontSize = 10.sp,
                    color = if (isDark) Color(0x40FFFFFF) else Color(0xFF94A3B8)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove subscription",
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    isDark: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val dnsText by viewModel.dnsServer.collectAsStateWithLifecycle()
    val currentMode by viewModel.splitTunnelMode.collectAsStateWithLifecycle()
    val appsList by viewModel.deviceApps.collectAsStateWithLifecycle()
    val chosenApps by viewModel.splitTunnelApps.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    
    var localDnsInput by remember { mutableStateOf(TextFieldValue(dnsText)) }
    var searchAppText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Aesthetic light/dark toggles
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.theme_mode).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0x70FFFFFF) else Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (isDark) R.string.theme_dark else R.string.theme_light),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.theme_desc),
                            fontSize = 11.sp,
                            color = if (isDark) Color(0x50FFFFFF) else Color(0xFF94A3B8)
                        )
                    }
                    Switch(
                        checked = isDark,
                        onCheckedChange = { onToggleTheme(it) },
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }
            }
        }

        // Language Select Options Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.language_select).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0x70FFFFFF) else Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.updateLanguage("en") },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (appLanguage == "en") Color(0xFF007AFF) else if (isDark) Color(0x10FFFFFF) else Color(0x10000000)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "English",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (appLanguage == "en") Color.White else if (isDark) Color.White else Color(0xFF1E293B)
                        )
                    }

                    Button(
                        onClick = { viewModel.updateLanguage("fa") },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (appLanguage == "fa") Color(0xFF007AFF) else if (isDark) Color(0x10FFFFFF) else Color(0x10000000)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "فارسی",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (appLanguage == "fa") Color.White else if (isDark) Color.White else Color(0xFF1E293B)
                        )
                    }
                }
            }
        }

        // Secure DNS / DoH Config
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.doh_dns).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0x70FFFFFF) else Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = localDnsInput,
                    onValueChange = { 
                        localDnsInput = it
                        viewModel.updateDns(it.text)
                    },
                    placeholder = { Text(stringResource(R.string.dns_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dns_address_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF007AFF)
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dns-over-HTTPS secures query lookups dynamically against ISP leak monitoring.",
                    fontSize = 10.sp,
                    color = if (isDark) Color(0x50FFFFFF) else Color(0xFF94A3B8)
                )
            }
        }

        // Split Tunneling Configuration Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.split_tunneling).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0x70FFFFFF) else Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Mode switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.updateSplitTunnelMode("bypass") },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentMode == "bypass") Color(0xFF007AFF) else if (isDark) Color(0x10FFFFFF) else Color(0x10000000)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Bypass Selected",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentMode == "bypass") Color.White else if (isDark) Color.White else Color(0xFF1E293B)
                        )
                    }

                    Button(
                        onClick = { viewModel.updateSplitTunnelMode("allow") },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentMode == "allow") Color(0xFF007AFF) else if (isDark) Color(0x10FFFFFF) else Color(0x10000000)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Only Selected",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentMode == "allow") Color.White else if (isDark) Color.White else Color(0xFF1E293B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // App Search Text Input
                OutlinedTextField(
                    value = searchAppText,
                    onValueChange = { searchAppText = it },
                    placeholder = { Text(stringResource(R.string.search_apps), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("search_apps_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF007AFF)
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = if (isDark) Color(0x40FFFFFF) else Color(0xFF64748B))
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable container to control device app toggles
                val filteredApps = appsList.filter { 
                    it.name.contains(searchAppText, ignoreCase = true) || it.packageName.contains(searchAppText, ignoreCase = true)
                }.take(15) // limit list overhead for extreme low cpu usage

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    if (filteredApps.isEmpty()) {
                        Text(
                            text = "No applications found.",
                            color = if (isDark) Color(0x40FFFFFF) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredApps) { app ->
                                val isChecked = chosenApps.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleAppInSplitTunnel(app.packageName) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color.White else Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 10.sp,
                                            color = if (isDark) Color(0x40FFFFFF) else Color(0xFF94A3B8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { viewModel.toggleAppInSplitTunnel(app.packageName) },
                                        modifier = Modifier.testTag("app_checkbox_${app.packageName}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun AddNodeManualDialog(
    onDismiss: () -> Unit,
    onAddNode: (String, String, String, Int, String, String, String, String) -> Unit,
    isDark: Boolean
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("VLESS") } // VLESS, TROJAN, VMESS
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") } // UUID or password
    var sni by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var security by remember { mutableStateOf("none") } // none, tls, reality

    val protocols = listOf("VLESS", "TROJAN", "VMESS")
    val securities = listOf("none", "tls", "reality")

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configure Custom Tunnel Node",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF1E293B)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Friendly Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_node_title_input")
                )

                // Protocol Picker
                Text(text = "Tunnel Protocol", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF64748B))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    protocols.forEach { p ->
                        Button(
                            onClick = { protocol = p },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (protocol == p) Color(0xFF007AFF) else if (isDark) Color(0x18FFFFFF) else Color(0x12000000)
                            )
                        ) {
                            Text(
                                p, 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold,
                                color = if (protocol == p) Color.White else if (isDark) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host Domain / IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_node_host_input")
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_node_port_input")
                )

                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text(if (protocol == "TROJAN") "Password" else "UUID Secret Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_node_secret_input")
                )

                // Security Type Selector
                Text(text = "Security Layer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF64748B))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    securities.forEach { s ->
                        Button(
                            onClick = { security = s },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (security == s) Color(0xFF007AFF) else if (isDark) Color(0x18FFFFFF) else Color(0x12000000)
                            )
                        ) {
                            Text(
                                s, 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold,
                                color = if (security == s) Color.White else if (isDark) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }
                }

                if (security == "tls" || security == "reality") {
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { sni = it },
                        label = { Text("SNI Domain (e.g. google.com)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_node_sni_input")
                    )
                }

                if (security == "reality") {
                    OutlinedTextField(
                        value = publicKey,
                        onValueChange = { publicKey = it },
                        label = { Text("Reality public key (pbk)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_node_pbk_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pInt = portText.toIntOrNull() ?: 443
                    if (name.isNotEmpty() && host.isNotEmpty() && keyText.isNotEmpty()) {
                        onAddNode(name, protocol, host, pInt, keyText, sni, publicKey, security)
                    } else {
                        Toast.makeText(context, "Fill required fields first", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("confirm_add_node_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Save Node")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFFF3B30))
            }
        }
    )
}

// Format utilities with beautiful human readable results
fun formatSpeedText(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format("%.1f MB/s", mb)
    } else {
        String.format("%.1f KB/s", kb)
    }
}

fun formatBytesText(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.1f KB", kb)
    }
}

fun formatDurationText(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

fun formatTime(timestamp: Long): String {
    val format = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
    return format.format(java.util.Date(timestamp))
}

@Composable
fun ClipboardImportPopupDialog(
    onDismiss: () -> Unit,
    onLaunchManualAdd: () -> Unit,
    viewModel: MainViewModel,
    isDark: Boolean
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    
    // Read Clipboard on open
    var clipboardText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val primaryClip = clipboard.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val text = primaryClip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotBlank()) {
                    clipboardText = text.trim()
                }
            }
        } catch (e: Exception) {
            // Clipboard access exceptions in sandbox runtime
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "انصراف",
                    color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = Color(0xFF335DF7),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "افزودن سرور یا اشتراک جدید",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // If clipboard contains interesting content, highlight it!
                if (clipboardText.isNotEmpty()) {
                    val isVpnConfig = clipboardText.startsWith("vless://") || 
                                     clipboardText.startsWith("vmess://") || 
                                     clipboardText.startsWith("trojan://")
                    val isSubWebLink = clipboardText.startsWith("http://") || 
                                      clipboardText.startsWith("https://")
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isDark) Color(0x1A335DF7) else Color(0x0D335DF7),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                color = Color(0xFF335DF7).copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                inputText = clipboardText
                            }
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = Color(0xFF335DF7),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "محتوای شناسایی شده در کلیپ‌بورد:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF335DF7)
                                )
                            }
                            Text(
                                text = if (isVpnConfig) "کانفیگ تک کاربره (VLESS/VMess/Trojan)" 
                                       else if (isSubWebLink) "لینک ساب / اشتراک شبکه" 
                                       else "متن کپی شده عمومی",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF1E293B)
                            )
                            Text(
                                text = if (clipboardText.length > 60) clipboardText.take(57) + "..." else clipboardText,
                                fontSize = 10.sp,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    inputText = clipboardText
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF335DF7)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.End).height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("جایگذاری خودکار", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Regular manual text field to enter config / subscription link
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "آدرس کانفیگ یا لینک ساب را وارد کنید:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth().testTag("clipboard_pop_input"),
                        placeholder = { 
                            Text(
                                text = "vless://... یا vmess://... یا https://...",
                                fontSize = 11.sp,
                                color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                            ) 
                        },
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            color = if (isDark) Color.White else Color(0xFF0F172A)
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF335DF7),
                            unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x20000000)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4,
                        trailingIcon = {
                            if (inputText.isNotEmpty()) {
                                IconButton(onClick = { inputText = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Import Buttons Side-by-Side
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val uri = inputText.trim()
                            if (uri.isEmpty()) {
                                Toast.makeText(context, "لطفا ابتدا مقداری وارد کنید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val success = viewModel.importConfigUri(uri)
                            if (success) {
                                Toast.makeText(context, "سرور با موفقیت به لیست اضافه شد", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "فرمت کانفیگ ناشناخته یا نامعتبر است", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF335DF7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "وارد کردن به عنوان کانفیگ تکی", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val url = inputText.trim()
                            if (url.isEmpty()) {
                                Toast.makeText(context, "لطفا ابتدا مقداری وارد کنید", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                Toast.makeText(context, "لینک ساب باید با http:// یا https:// شروع شود", Toast.LENGTH_LONG).show()
                                return@OutlinedButton
                            }
                            viewModel.addSubscriptionLink("اشتراک جدید", url)
                            Toast.makeText(context, "لینک اشتراک با موفقیت ثبت و همگام‌سازی شد", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF335DF7)),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF335DF7))
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "ثبت به عنوان لینک اشتراک (ساب)", fontSize = 12.sp, color = Color(0xFF335DF7), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer layout to launch manual nodes customizer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchManualAdd() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ساخت یا ویرایش دستی مشخصات سرور ⚙️",
                        color = if (isDark) Color(0xFFA5B4FC) else Color(0xFF4F46E5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = if (isDark) Color(0xFF0F1322) else Color(0xFFFFFFFF)
    )
}

