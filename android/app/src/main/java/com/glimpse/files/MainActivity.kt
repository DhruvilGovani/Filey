package com.glimpse.files

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glimpse.files.ui.BrowseTabContent
import com.glimpse.files.ui.CategoryOverlayScreen
import com.glimpse.files.ui.CompressZipDialog
import com.glimpse.files.ui.CreateNewItemDialog
import com.glimpse.files.ui.FileActionBottomSheet
import com.glimpse.files.ui.FloatingLiquidNavPill
import com.glimpse.files.ui.FtpServerOverlayScreen
import com.glimpse.files.ui.GlassCard
import com.glimpse.files.ui.GlassCircleButton
import com.glimpse.files.ui.GlimpseTheme
import com.glimpse.files.ui.GlimpseViewModel
import com.glimpse.files.ui.HomeTabContent
import com.glimpse.files.ui.LiquidWallpaper
import com.glimpse.files.ui.LocalGlimpsePalette
import com.glimpse.files.ui.MoreToolsOverlayScreen
import com.glimpse.files.ui.OverlayScreen
import com.glimpse.files.ui.RecycleBinOverlayScreen
import com.glimpse.files.ui.RenameItemDialog
import com.glimpse.files.ui.SearchOverlayScreen
import com.glimpse.files.ui.SettingsOverlayScreen
import com.glimpse.files.ui.StorageAnalyticsOverlayScreen
import com.glimpse.files.ui.ZipInspectorOverlayScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var vmRef: GlimpseViewModel? = null
    private val legacyPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vmRef?.recheckAccess() }

    private fun enableHighRefreshRate() {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            if (Build.VERSION.SDK_INT >= 23) {
                val disp = if (Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
                val bestMode = disp?.supportedModes?.maxWithOrNull(
                    compareBy<android.view.Display.Mode> { it.refreshRate }
                        .thenBy { it.physicalWidth * it.physicalHeight }
                )
                if (bestMode != null) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = bestMode.modeId
                    if (Build.VERSION.SDK_INT >= 30) {
                        lp.preferredRefreshRate = bestMode.refreshRate
                    }
                    window.attributes = lp
                }
            }
        } catch (_: Exception) {}
    }

    fun requestAllFilesAccess() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                legacyPermLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Open Settings > Apps > Special app access > All files access and allow Glimpse", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()

        setContent {
            val vm: GlimpseViewModel = viewModel()
            vmRef = vm

            GlimpseTheme(
                themeMode = vm.settings.theme,
                accentHue = vm.settings.accentHue
            ) {
                GlimpseAppRoot(
                    vm = vm,
                    onRequestAccess = { requestAllFilesAccess() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableHighRefreshRate()
        vmRef?.recheckAccess()
    }
}

@Composable
fun GlimpseAppRoot(
    vm: GlimpseViewModel,
    onRequestAccess: () -> Unit
) {
    val p = LocalGlimpsePalette.current
    val isSelecting = vm.selectedKeys.isNotEmpty()
    val topOverlay = vm.overlays.lastOrNull()

    BackHandler(enabled = isSelecting || topOverlay != null || (vm.activeTab == 1 && vm.currentPath.isNotEmpty())) {
        when {
            isSelecting -> vm.clearSelection()
            topOverlay != null -> vm.popOverlay()
            vm.activeTab == 1 && vm.currentPath.isNotEmpty() -> vm.loadBrowse(vm.currentPath.dropLast(1))
        }
    }

    LaunchedEffect(vm.toastMessage) {
        if (vm.toastMessage != null) {
            delay(2400L)
            vm.clearToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidWallpaper()

        // Main Home / Browse Shell
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                GlassCircleButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    onClick = { vm.openOverlay(OverlayScreen.Settings) }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vm.activeTab == 1) {
                        GlassCircleButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = "New folder or file",
                            onClick = { vm.showCreateDialog = true }
                        )
                    }
                    GlassCircleButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search",
                        onClick = { vm.openOverlay(OverlayScreen.Search) }
                    )
                }
            }

            if (!vm.hasAccess) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Allow access to your files", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.ink)
                            Text(
                                "Glimpse needs All files access permission to manage your storage.",
                                fontSize = 13.sp,
                                color = p.muted,
                                textAlign = TextAlign.Center
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(p.accent.copy(alpha = 0.36f))
                                    .clickable(onClick = onRequestAccess)
                                    .padding(vertical = 13.dp)
                            ) {
                                Text("Open settings", fontWeight = FontWeight.Bold, color = p.ink)
                            }
                        }
                    }
                }
            } else {
                AnimatedContent(
                    targetState = vm.activeTab,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.97f)) togetherWith
                            (fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) + scaleOut(targetScale = 0.97f))
                    },
                    label = "tabSwitch",
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { tab ->
                    if (tab == 0) {
                        HomeTabContent(vm = vm)
                    } else {
                        BrowseTabContent(vm = vm)
                    }
                }
            }
        }

        // Overlay Stack (Category, Search, Storage Analytics, More, Recycle Bin, FTP, Settings, Zip Inspector)
        AnimatedVisibility(
            visible = topOverlay != null,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 14 },
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it / 14 }
        ) {
            when (val screen = topOverlay) {
                is OverlayScreen.CategoryList -> CategoryOverlayScreen(category = screen.category, vm = vm)
                OverlayScreen.Search -> SearchOverlayScreen(vm = vm)
                OverlayScreen.StorageAnalytics -> StorageAnalyticsOverlayScreen(vm = vm)
                OverlayScreen.MoreTools -> MoreToolsOverlayScreen(vm = vm)
                OverlayScreen.RecycleBin -> RecycleBinOverlayScreen(vm = vm)
                OverlayScreen.FtpServer -> FtpServerOverlayScreen(vm = vm)
                OverlayScreen.Settings -> SettingsOverlayScreen(vm = vm)
                is OverlayScreen.ZipInspector -> ZipInspectorOverlayScreen(item = screen.item, vm = vm)
                null -> Unit
            }
        }

        // Floating Liquid-Glass Nav Pill / Multi-Select Action Bar
        val showNavPill = vm.hasAccess && (topOverlay == null || isSelecting)
        AnimatedVisibility(
            visible = showNavPill,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            FloatingLiquidNavPill(
                activeTab = vm.activeTab,
                isSelecting = isSelecting,
                onSelectTab = { tab ->
                    vm.clearSelection()
                    vm.activeTab = tab
                    if (tab == 1) vm.loadBrowse(vm.currentPath)
                },
                onCopy = { vm.copySelected() },
                onMove = { vm.moveSelected() },
                onZip = {
                    val items = vm.selectedKeys.values.toList()
                    vm.clearSelection()
                    val paths = items.map { it.pathSegments }.distinct()
                    if (paths.size != 1) {
                        vm.showToast("Select items from one folder to zip together")
                    } else {
                        vm.zipDialogTarget = paths.first() to items.map { it.name }
                    }
                },
                onShare = { vm.shareItems(vm.selectedKeys.values.toList()) },
                onDelete = { vm.deleteItems(vm.selectedKeys.values.toList()) }
            )
        }

        // Toast Pill
        AnimatedVisibility(
            visible = vm.toastMessage != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp, start = 24.dp, end = 24.dp)
        ) {
            vm.toastMessage?.let { msg ->
                GlassCard(shape = RoundedCornerShape(20.dp)) {
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = p.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Bottom Sheet & Dialogs
        vm.actionTarget?.let { target ->
            FileActionBottomSheet(
                item = target,
                vm = vm,
                onDismiss = { vm.actionTarget = null }
            )
        }

        if (vm.showCreateDialog) {
            CreateNewItemDialog(
                folderLabel = vm.currentPath.lastOrNull() ?: "Internal storage",
                onDismiss = { vm.showCreateDialog = false },
                onCreate = { name, isFolder -> vm.createNew(name, isFolder) }
            )
        }

        vm.renameTarget?.let { target ->
            RenameItemDialog(
                item = target,
                onDismiss = { vm.renameTarget = null },
                onRename = { newName -> vm.renameItem(target, newName) }
            )
        }

        vm.zipDialogTarget?.let { (seg, names) ->
            val defName = if (names.size == 1) names.first().substringBeforeLast('.', names.first())
            else seg.lastOrNull() ?: "archive"
            CompressZipDialog(
                defaultName = defName,
                itemCount = names.size,
                onDismiss = { vm.zipDialogTarget = null },
                onCompress = { outName -> vm.compressItems(seg, names, outName) }
            )
        }
    }
}
