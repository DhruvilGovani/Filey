package com.glimpse.files.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glimpse.files.data.FileCategory
import com.glimpse.files.data.FileItem

private data class ShortcutTileSpec(
    val category: FileCategory?,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTabContent(
    vm: GlimpseViewModel,
    modifier: Modifier = Modifier
) {
    val p = LocalGlimpsePalette.current
    val info = vm.storageInfo
    val usedGb = info?.let { it.usedBytes / 1_000_000_000.0 } ?: 0.0
    val freeGb = info?.let { it.freeBytes / 1_000_000_000.0 } ?: 0.0
    val totalGb = info?.let { Math.round(it.totalBytes / 1_000_000_000.0) } ?: 128L

    val animatedFraction by animateFloatAsState(
        targetValue = info?.usedFraction ?: 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "storageBar"
    )

    val shortcuts = remember {
        listOf(
            ShortcutTileSpec(FileCategory.Images, "Images", Icons.Rounded.Image, Color(0xFF17B88A)),
            ShortcutTileSpec(FileCategory.Videos, "Videos", Icons.Rounded.VideoFile, Color(0xFFF0457F)),
            ShortcutTileSpec(FileCategory.Apks, "APKs", Icons.Rounded.Android, Color(0xFF7B61FF)),
            ShortcutTileSpec(FileCategory.Archives, "Archives", Icons.Rounded.Archive, Color(0xFFE8960F)),
            ShortcutTileSpec(FileCategory.Downloads, "Downloads", Icons.Rounded.Download, Color(0xFF2A8CF0)),
            ShortcutTileSpec(FileCategory.Documents, "Documents", Icons.Rounded.Description, Color(0xFF9B5DE5)),
            ShortcutTileSpec(FileCategory.Audio, "Audio", Icons.Rounded.AudioFile, Color(0xFF12A8C4)),
            ShortcutTileSpec(null, "More", Icons.Rounded.MoreHoriz, Color(0xFF6B7A90))
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp)
    ) {
        // 1. Storage Card
        val storageInteraction = remember { MutableInteractionSource() }
        GlassCard(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp)
                .springPress(storageInteraction, 0.98f)
                .clickable(interactionSource = storageInteraction, indication = null) {
                    vm.openOverlay(OverlayScreen.StorageAnalytics)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Column {
                        Text("Internal storage", fontSize = 12.sp, color = p.muted)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = String.format("%.1f GB used", usedGb),
                            fontSize = 27.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            color = p.ink
                        )
                        Text(
                            text = String.format("%.1f GB free of %d GB", freeGb, totalGb),
                            fontSize = 12.sp,
                            color = p.muted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 56.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0x36808080))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            p.accent,
                                            Color.hsl((vm.settings.accentHue + 60f) % 360f, 0.92f, 0.62f)
                                        )
                                    )
                                )
                        )
                    }
                }

                val pieInteraction = remember { MutableInteractionSource() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(44.dp)
                        .springPress(pieInteraction, 0.90f)
                        .shadow(8.dp, RoundedCornerShape(15.dp), ambientColor = p.shadowColor, spotColor = p.shadowColor)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(p.pillFillTop, p.pillFillBottom)))
                        .border(1.dp, p.pillBorder, RoundedCornerShape(15.dp))
                        .clickable(interactionSource = pieInteraction, indication = null) {
                            vm.openOverlay(OverlayScreen.StorageAnalytics)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PieChart,
                        contentDescription = "Storage analytics",
                        tint = p.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 2. 4x2 Category Shortcuts Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shortcuts.chunked(4).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowItems.forEach { spec ->
                        val tileInteraction = remember { MutableInteractionSource() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(82.dp)
                                .springPress(tileInteraction, 0.93f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(interactionSource = tileInteraction, indication = null) {
                                    if (spec.category != null) {
                                        vm.openOverlay(OverlayScreen.CategoryList(spec.category))
                                    } else {
                                        vm.openOverlay(OverlayScreen.MoreTools)
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = spec.icon,
                                contentDescription = spec.label,
                                tint = spec.color,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = spec.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = p.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 3. Recent Header + Edge-to-Edge 2-Card Carousel
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { vm.openOverlay(OverlayScreen.CategoryList(FileCategory.Recent)) }
                    .padding(vertical = 2.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = "Recent",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.ink
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "View all recent",
                    tint = p.muted,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (vm.recentFiles.isEmpty()) {
                GlassCard(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("No recent files found.", color = p.muted, fontSize = 13.sp)
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cardWidth = (maxWidth - 12.dp) / 2f
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(vm.recentFiles, key = { it.fullRelativePath }) { item ->
                            val cardInteraction = remember { MutableInteractionSource() }
                            GlassCard(
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .width(cardWidth)
                                    .height(142.dp)
                                    .springPress(cardInteraction, 0.96f)
                                    .combinedClickable(
                                        interactionSource = cardInteraction,
                                        indication = null,
                                        onClick = { vm.openOrToggle(item) },
                                        onLongClick = { vm.actionTarget = item }
                                    )
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp)
                                ) {
                                    FileThumbnailBadge(
                                        item = item,
                                        vm = vm,
                                        modifier = Modifier.size(52.dp),
                                        cornerRadius = 16.dp,
                                        iconSize = 26.dp
                                    )
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = item.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = p.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${formatRelativeTime(item.modifiedMs)} · ${formatBytes(item.size)}",
                                            fontSize = 12.sp,
                                            color = p.muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrowseTabContent(
    vm: GlimpseViewModel,
    modifier: Modifier = Modifier
) {
    val p = LocalGlimpsePalette.current
    val isSelecting = vm.selectedKeys.isNotEmpty() && vm.overlays.isEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Breadcrumb or Selection Header Bar
        GlassCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSelecting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    GlassCircleButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Cancel selection",
                        size = 36.dp,
                        onClick = { vm.clearSelection() }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${vm.selectedKeys.size} selected",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = p.ink
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.selectAll(vm.browseItems) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Select all", fontWeight = FontWeight.SemiBold, color = p.accent)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    LazyRow(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            val isRoot = vm.currentPath.isEmpty()
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isRoot) p.accent.copy(alpha = 0.24f) else Color.Transparent)
                                    .clickable { vm.loadBrowse(emptyList()) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Internal", fontWeight = FontWeight.SemiBold, color = p.ink)
                            }
                        }
                        items(vm.currentPath.size) { index ->
                            val seg = vm.currentPath[index]
                            val isLast = index == vm.currentPath.lastIndex
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = p.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isLast) p.accent.copy(alpha = 0.24f) else Color.Transparent)
                                        .clickable { vm.loadBrowse(vm.currentPath.take(index + 1)) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(seg, fontWeight = FontWeight.SemiBold, color = p.ink)
                                }
                            }
                        }
                    }

                    if (vm.clipboard != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.pasteClipboard() }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Paste here", fontWeight = FontWeight.SemiBold, color = p.accent)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val folderName = vm.currentPath.lastOrNull() ?: "archive"
                                vm.zipDialogTarget = vm.currentPath to emptyList()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Compress", fontWeight = FontWeight.SemiBold, color = p.accent)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Filter Pill + Grid/List Switcher
        FilterAndViewBar(
            currentSort = vm.settings.sortMode,
            isGridView = vm.settings.isGridView,
            onSelectSort = { vm.setBrowseSort(it) },
            onToggleView = { vm.toggleViewMode() }
        )

        Spacer(Modifier.height(10.dp))

        if (vm.isBrowseLoading && vm.browseItems.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Loading...", color = p.muted)
            }
        } else if (vm.browseItems.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Nothing here yet.", color = p.muted)
            }
        } else if (vm.settings.isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(vm.browseItems, key = { it.fullRelativePath }) { item ->
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
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(vm.browseItems, key = { it.fullRelativePath }) { item ->
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

@Composable
fun FloatingLiquidNavPill(
    activeTab: Int,
    isSelecting: Boolean,
    onSelectTab: (Int) -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onZip: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalGlimpsePalette.current
    val pillWidth by animateDpAsState(
        targetValue = if (isSelecting) 340.dp else 224.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navWidth"
    )

    Box(
        modifier = modifier
            .width(pillWidth)
            .height(58.dp)
            .shadow(14.dp, CircleShape, ambientColor = p.shadowColor, spotColor = p.shadowColor)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(p.pillFillTop, p.pillFillBottom)))
            .border(1.dp, p.pillBorder, CircleShape)
            .padding(6.dp)
    ) {
        if (isSelecting) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                NavActionBtn(Icons.Rounded.ContentCopy, "Copy", p.ink, onCopy, Modifier.weight(1f))
                NavActionBtn(Icons.Rounded.DriveFileMove, "Move", p.ink, onMove, Modifier.weight(1f))
                NavActionBtn(Icons.Rounded.Archive, "Zip", p.ink, onZip, Modifier.weight(1f))
                NavActionBtn(Icons.Rounded.Share, "Share", p.ink, onShare, Modifier.weight(1f))
                NavActionBtn(Icons.Rounded.Delete, "Delete", Color(0xFFE5484D), onDelete, Modifier.weight(1f))
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val halfWidth = maxWidth / 2f
                val indicatorOffset by animateDpAsState(
                    targetValue = if (activeTab == 0) 0.dp else halfWidth,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "navIndicator"
                )
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(halfWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = if (p.isDark) 0.22f else 0.78f),
                                    p.accent.copy(alpha = 0.36f)
                                )
                            )
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    NavTabBtn(
                        icon = Icons.Rounded.Home,
                        label = "Home",
                        ink = p.ink,
                        onClick = { onSelectTab(0) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    NavTabBtn(
                        icon = Icons.Rounded.Folder,
                        label = "Browse",
                        ink = p.ink,
                        onClick = { onSelectTab(1) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabBtn(
    icon: ImageVector,
    label: String,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@Composable
private fun NavActionBtn(
    icon: ImageVector,
    label: String,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = ink, modifier = Modifier.size(20.dp))
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ink, textAlign = TextAlign.Center)
    }
}
