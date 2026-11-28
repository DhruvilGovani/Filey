package com.glimpse.files.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glimpse.files.data.FileCategory
import com.glimpse.files.data.FileItem
import com.glimpse.files.data.ThemeMode
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun OverlayShell(
    title: String,
    vm: GlimpseViewModel,
    isSelecting: Boolean = false,
    onSelectAll: (() -> Unit)? = null,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val p = LocalGlimpsePalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.overlayBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isSelecting) {
                GlassCircleButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Cancel selection",
                    size = 40.dp,
                    onClick = { vm.clearSelection() }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${vm.selectedKeys.size} selected",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.ink
                )
                Spacer(Modifier.weight(1f))
                if (onSelectAll != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onSelectAll)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Select all", fontWeight = FontWeight.SemiBold, color = p.accent)
                    }
                }
            } else {
                GlassCircleButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    size = 40.dp,
                    onClick = onBack
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun CategoryOverlayScreen(
    category: FileCategory,
    vm: GlimpseViewModel
) {
    val p = LocalGlimpsePalette.current
    val isSelecting = vm.selectedKeys.isNotEmpty()

    OverlayShell(
        title = category.label,
        vm = vm,
        isSelecting = isSelecting,
        onSelectAll = { vm.selectAll(vm.overlayItems) },
        onBack = { vm.popOverlay() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FilterAndViewBar(
                currentSort = vm.overlaySort,
                isGridView = vm.settings.isGridView,
                onSelectSort = { vm.setOverlaySort(it) },
                onToggleView = { vm.toggleViewMode() }
            )
            Spacer(Modifier.height(10.dp))

            if (vm.isOverlayLoading && vm.overlayItems.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Loading...", color = p.muted)
                }
            } else if (vm.overlayItems.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Nothing here yet.", color = p.muted)
                }
            } else if (vm.settings.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = if (isSelecting) 96.dp else 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(vm.overlayItems, key = { it.fullRelativePath }) { item ->
                        val selected = isSelecting && vm.selectedKeys.containsKey(item.fullRelativePath)
                        FileGridItemCard(
                            item = item,
                            vm = vm,
                            isSelecting = isSelecting,
                            isSelected = selected,
                            onClick = { vm.openOrToggle(item) },
                            onLongClick = { vm.toggleSelection(item) },
                            onMoreClick = { vm.actionTarget = item }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = if (isSelecting) 96.dp else 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(vm.overlayItems, key = { it.fullRelativePath }) { item ->
                        val selected = isSelecting && vm.selectedKeys.containsKey(item.fullRelativePath)
                        FileListItemRow(
                            item = item,
                            vm = vm,
                            isSelecting = isSelecting,
                            isSelected = selected,
                            onClick = { vm.openOrToggle(item) },
                            onLongClick = { vm.toggleSelection(item) },
                            onMoreClick = { vm.actionTarget = item }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlayScreen(vm: GlimpseViewModel) {
    val p = LocalGlimpsePalette.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    val matchingCategories = remember(vm.searchQuery) {
        val q = vm.searchQuery.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else FileCategory.entries.filter {
            it != FileCategory.Recent && it.label.lowercase().contains(q)
        }
    }

    OverlayShell(
        title = "Search",
        vm = vm,
        onBack = { vm.popOverlay() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = p.muted)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = vm.searchQuery,
                        onValueChange = { vm.onSearchQueryChange(it) },
                        placeholder = { Text("Search files, folders, categories", color = p.muted) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                    if (vm.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = p.muted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (vm.searchQuery.trim().isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        "Type to search every file, folder and category.",
                        color = p.muted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (matchingCategories.isNotEmpty()) {
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                matchingCategories.forEach { cat ->
                                    val c = categoryColor(cat)
                                    GlassCard(
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.clickable { vm.openOverlay(OverlayScreen.CategoryList(cat)) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(cat.label, fontWeight = FontWeight.SemiBold, color = p.ink)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (vm.searchResults.isEmpty() && matchingCategories.isEmpty()) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                            ) {
                                Text("No matches. Try a shorter name.", color = p.muted)
                            }
                        }
                    } else {
                        items(vm.searchResults, key = { it.fullRelativePath }) { item ->
                            FileListItemRow(
                                item = item,
                                vm = vm,
                                isSelecting = false,
                                isSelected = false,
                                onClick = {
                                    if (item.isDir) vm.navigateToFolder(item)
                                    else vm.openOrToggle(item)
                                },
                                onLongClick = { vm.actionTarget = item },
                                onMoreClick = { vm.actionTarget = item }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageAnalyticsOverlayScreen(vm: GlimpseViewModel) {
    val p = LocalGlimpsePalette.current
    val info = vm.storageInfo
    val slices = remember(info) {
        info?.slices?.filter { it.bytes > 0L } ?: emptyList()
    }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val totalBytes = slices.sumOf { it.bytes }.coerceAtLeast(1L)

    val sweepProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "donutSweep"
    )

    OverlayShell(
        title = "Storage analytics",
        vm = vm,
        onBack = { vm.popOverlay() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(240.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(slices) {
                            detectTapGestures { offset ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val dx = offset.x - cx
                                val dy = offset.y - cy
                                val dist = hypot(dx, dy)
                                val outerR = minOf(size.width, size.height) / 2f
                                val innerR = outerR * 0.45f
                                if (dist in innerR..outerR && slices.isNotEmpty()) {
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                    if (angle < 0f) angle += 360f
                                    var acc = 0f
                                    for (i in slices.indices) {
                                        val sweep = slices[i].bytes.toFloat() / totalBytes.toFloat() * 360f
                                        if (angle in acc..(acc + sweep)) {
                                            selectedIndex = if (selectedIndex == i) -1 else i
                                            break
                                        }
                                        acc += sweep
                                    }
                                }
                            }
                        }
                ) {
                    var startAngle = -90f
                    slices.forEachIndexed { i, slice ->
                        val sweep = (slice.bytes.toFloat() / totalBytes.toFloat()) * 360f * sweepProgress
                        val isPicked = selectedIndex == i
                        val strokeWidth = if (isPicked) 38.dp.toPx() else 28.dp.toPx()
                        val alpha = if (selectedIndex < 0 || isPicked) 1f else 0.48f
                        val pad = strokeWidth / 2f + 6.dp.toPx()
                        drawArc(
                            color = Color(slice.colorHex).copy(alpha = alpha),
                            startAngle = startAngle,
                            sweepAngle = (sweep - 2.2f).coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = Size(size.width - pad * 2f, size.height - pad * 2f),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val activeSlice = slices.getOrNull(selectedIndex)
                    val displayGb = (activeSlice?.bytes ?: totalBytes) / 1_000_000_000.0
                    Text(
                        text = String.format("%.1f", displayGb),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = p.ink
                    )
                    Text(
                        text = if (activeSlice != null) "GB, ${activeSlice.name}" else "GB used",
                        fontSize = 13.sp,
                        color = p.muted
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            slices.forEachIndexed { i, slice ->
                val isPicked = selectedIndex == i
                val c = Color(slice.colorHex)
                GlassCard(
                    shape = RoundedCornerShape(18.dp),
                    selected = isPicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { selectedIndex = if (selectedIndex == i) -1 else i }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(c)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = slice.name,
                            fontWeight = FontWeight.Bold,
                            color = p.ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%.1f GB", slice.bytes / 1_000_000_000.0),
                            color = p.ink
                        )
                    }
                }
            }
        }
    }
}

private data class ToolTileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun MoreToolsOverlayScreen(vm: GlimpseViewModel) {
    val p = LocalGlimpsePalette.current
    val binCount = vm.trashItems.size
    val tools = listOf(
        ToolTileSpec(
            "Recycle Bin",
            "$binCount item${if (binCount == 1) "" else "s"}",
            Icons.Rounded.Delete,
            Color(0xFFE8590C)
        ) { vm.openOverlay(OverlayScreen.RecycleBin) },
        ToolTileSpec(
            "FTP server",
            if (vm.ftpRunning) "Running" else "Stopped",
            Icons.Rounded.Wifi,
            Color(0xFF17B88A)
        ) { vm.openOverlay(OverlayScreen.FtpServer) },
        ToolTileSpec(
            "Archives",
            "Inspect and extract",
            Icons.Rounded.Archive,
            Color(0xFFE8960F)
        ) { vm.openOverlay(OverlayScreen.CategoryList(FileCategory.Archives)) },
        ToolTileSpec(
            "Compress",
            "Create .zip archive",
            Icons.Rounded.Folder,
            Color(0xFF7B61FF)
        ) { vm.zipDialogTarget = vm.currentPath to emptyList() },
        ToolTileSpec(
            "Large files",
            "Over 50 MB",
            Icons.Rounded.Storage,
            Color(0xFFF0457F)
        ) { vm.openOverlay(OverlayScreen.CategoryList(FileCategory.LargeFiles)) },
        ToolTileSpec(
            "This week",
            "Changed in 7 days",
            Icons.Rounded.Schedule,
            Color(0xFF2A8CF0)
        ) { vm.openOverlay(OverlayScreen.CategoryList(FileCategory.ThisWeek)) }
    )

    OverlayShell(title = "More", vm = vm, onBack = { vm.popOverlay() }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tools) { tool ->
                val interaction = remember { MutableInteractionSource() }
                GlassCard(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .springPress(interaction, 0.95f)
                        .clickable(interactionSource = interaction, indication = null, onClick = tool.onClick)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(tool.icon, contentDescription = null, tint = tool.color, modifier = Modifier.size(30.dp))
                        Text(tool.title, fontWeight = FontWeight.Bold, color = p.ink)
                        Text(tool.subtitle, fontSize = 12.sp, color = p.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun RecycleBinOverlayScreen(vm: GlimpseViewModel) {
    val p = LocalGlimpsePalette.current
    OverlayShell(title = "Recycle Bin", vm = vm, onBack = { vm.popOverlay() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Items are removed for good after ${vm.settings.binRetentionDays} days.",
                fontSize = 13.sp,
                color = p.muted
            )
            Spacer(Modifier.height(12.dp))

            if (vm.isTrashEmptying) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Emptying Recycle Bin...", color = p.muted)
                }
            } else if (vm.trashItems.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        "The Recycle Bin is empty. Deleted files wait here until you restore or wipe them.",
                        color = p.muted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(vm.trashItems, key = { it.id }) { item ->
                        GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(p.accent.copy(alpha = 0.20f))
                                ) {
                                    Icon(
                                        imageVector = if (item.isDir) Icons.Rounded.Folder else Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = p.accent
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.SemiBold,
                                        color = p.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatBytes(item.size)}, deleted ${formatRelativeTime(item.deletedAtMs)}",
                                        fontSize = 12.sp,
                                        color = p.muted
                                    )
                                }
                                GlassCircleButton(
                                    icon = Icons.Rounded.Restore,
                                    contentDescription = "Restore",
                                    size = 38.dp,
                                    onClick = { vm.restoreTrashItem(item) }
                                )
                                GlassCircleButton(
                                    icon = Icons.Rounded.Close,
                                    contentDescription = "Delete forever",
                                    size = 38.dp,
                                    tint = Color(0xFFE5484D),
                                    onClick = { vm.wipeTrashItem(item) }
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(p.accent.copy(alpha = 0.34f))
                                .clickable { vm.emptyTrash() }
                                .padding(vertical = 14.dp)
                        ) {
                            Text("Empty Recycle Bin", fontWeight = FontWeight.Bold, color = p.ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FtpServerOverlayScreen(vm: GlimpseViewModel) {
    val p = LocalGlimpsePalette.current
    OverlayShell(title = "FTP server", vm = vm, onBack = { vm.popOverlay() }) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
            GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wireless transfer", fontWeight = FontWeight.Bold, color = p.ink)
                        Text(
                            text = if (vm.ftpRunning) "Server is running" else "Server is stopped",
                            fontSize = 12.sp,
                            color = p.muted
                        )
                    }
                    Switch(
                        checked = vm.ftpRunning,
                        onCheckedChange = { vm.toggleFtpServer(it) }
                    )
                }
            }

            if (vm.ftpRunning) {
                val url = "ftp://${vm.ftpIp}:${vm.settings.ftpPort}"
                GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(20.dp)
                    ) {
                        Text(url, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                        Text(
                            "User ${vm.settings.ftpUser}, password ${vm.settings.ftpPass}",
                            fontSize = 13.sp,
                            color = p.muted
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Type this address into a PC file manager on the same Wi‑Fi network.",
                            fontSize = 12.sp,
                            color = p.muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(top = 36.dp)) {
                    Text(
                        "Turn the server on to share Internal storage over your Wi‑Fi network.",
                        color = p.muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsOverlayScreen(vm: GlimpseViewModel) {
    val p = LocalGlimpsePalette.current
    val s = vm.settings
    val hues = listOf(200f, 265f, 330f, 25f, 150f)

    OverlayShell(title = "Settings", vm = vm, onBack = { vm.popOverlay() }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            // Appearance
            GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                    Text("Appearance", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.muted)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Theme", color = p.ink, modifier = Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x28808080))
                                .padding(3.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val active = s.theme == mode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (active) p.cardFill else Color.Transparent)
                                        .clickable { vm.updateSettings(s.copy(theme = mode)) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        mode.label,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (active) p.accent else p.ink,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Accent tint", color = p.ink, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            hues.forEach { h ->
                                val active = s.accentHue == h
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.hsl(h, 0.90f, 0.55f))
                                        .border(
                                            width = if (active) 3.dp else 0.dp,
                                            color = if (active) p.ink else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { vm.updateSettings(s.copy(accentHue = h)) }
                                )
                            }
                        }
                    }
                }
            }

            // Storage & Behavior
            GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                    Text("Storage and behavior", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.muted)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show hidden files", color = p.ink, modifier = Modifier.weight(1f))
                        Switch(
                            checked = s.showHidden,
                            onCheckedChange = { vm.updateSettings(s.copy(showHidden = it)) }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Empty bin after", color = p.ink, modifier = Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x28808080))
                                .padding(3.dp)
                        ) {
                            listOf(7, 14, 30).forEach { days ->
                                val active = s.binRetentionDays == days
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (active) p.cardFill else Color.Transparent)
                                        .clickable { vm.updateSettings(s.copy(binRetentionDays = days)) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        "$days d",
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (active) p.accent else p.ink,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Power Tools & Server
            GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    Text("Power tools and server", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.muted)

                    OutlinedTextField(
                        value = s.ftpPort.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { vm.updateSettings(s.copy(ftpPort = it)) } },
                        label = { Text("FTP port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = s.ftpUser,
                        onValueChange = { vm.updateSettings(s.copy(ftpUser = it)) },
                        label = { Text("FTP user") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = s.ftpPass,
                        onValueChange = { vm.updateSettings(s.copy(ftpPass = it)) },
                        label = { Text("FTP password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = s.extractDest,
                        onValueChange = { vm.updateSettings(s.copy(extractDest = it)) },
                        label = { Text("Extract to") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Permissions
            GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("All files access", fontWeight = FontWeight.SemiBold, color = p.ink)
                        Text("MANAGE_EXTERNAL_STORAGE", fontSize = 12.sp, color = p.muted)
                    }
                    Text(
                        text = if (vm.hasAccess) "Granted" else "Required",
                        fontWeight = FontWeight.Bold,
                        color = if (vm.hasAccess) Color(0xFF17B88A) else Color(0xFFE5484D)
                    )
                }
            }
        }
    }
}

@Composable
fun ZipInspectorOverlayScreen(item: FileItem, vm: GlimpseViewModel) {
    val p = LocalGlimpsePalette.current
    OverlayShell(title = item.name, vm = vm, onBack = { vm.popOverlay() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "${vm.zipEntries.size} items, ${formatBytes(item.size)} compressed",
                fontSize = 13.sp,
                color = p.muted
            )
            Spacer(Modifier.height(10.dp))

            if (vm.zipEntries.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text("Empty or unsupported archive format (supports .zip).", color = p.muted, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(vm.zipEntries) { entry ->
                        GlassCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (entry.isDir) Icons.Rounded.Folder else Icons.Rounded.Archive,
                                    contentDescription = null,
                                    tint = p.accent
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, fontWeight = FontWeight.SemiBold, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (entry.isDir) "Folder" else formatBytes(entry.size), fontSize = 12.sp, color = p.muted)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x2E808080))
                        .clickable {
                            vm.popOverlay()
                            vm.extractArchive(item, extractHere = true)
                        }
                        .padding(vertical = 13.dp)
                ) {
                    Text("Extract here", fontWeight = FontWeight.Bold, color = p.ink)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.accent.copy(alpha = 0.36f))
                        .clickable {
                            vm.popOverlay()
                            vm.extractArchive(item, extractHere = false)
                        }
                        .padding(vertical = 13.dp)
                ) {
                    Text("Extract to ${vm.settings.extractDest}", fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
