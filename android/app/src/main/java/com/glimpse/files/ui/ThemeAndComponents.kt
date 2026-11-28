package com.glimpse.files.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glimpse.files.data.FileCategory
import com.glimpse.files.data.FileItem
import com.glimpse.files.data.SortMode
import com.glimpse.files.data.ThemeMode
import kotlin.math.abs

data class GlimpsePalette(
    val isDark: Boolean,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val bg1: Color,
    val bg2: Color,
    val bg3: Color,
    val cardFill: Color,
    val cardBorder: Color,
    val pillFillTop: Color,
    val pillFillBottom: Color,
    val pillBorder: Color,
    val overlayBg: Color,
    val shadowColor: Color
)

val LocalGlimpsePalette = staticCompositionLocalOf {
    buildGlimpsePalette(isDark = true, hue = 200f)
}

fun buildGlimpsePalette(isDark: Boolean, hue: Float): GlimpsePalette {
    val h = ((hue % 360f) + 360f) % 360f
    return if (isDark) {
        val acc = Color.hsl(h, 0.90f, 0.62f)
        GlimpsePalette(
            isDark = true,
            ink = Color(0xFFEEF4FF),
            muted = Color(0xA6EEF4FF),
            accent = acc,
            accentSoft = acc.copy(alpha = 0.24f),
            bg1 = Color(0xFF0E1F4A),
            bg2 = Color(0xFF3A1450),
            bg3 = Color(0xFF07404A),
            cardFill = Color(0x18FFFFFF),
            cardBorder = Color(0x28FFFFFF),
            pillFillTop = Color(0x2EFFFFFF),
            pillFillBottom = Color(0x12FFFFFF),
            pillBorder = Color(0x99FFFFFF),
            overlayBg = Color(0xF0111938),
            shadowColor = Color(0x66000000)
        )
    } else {
        val acc = Color.hsl(h, 0.88f, 0.43f)
        GlimpsePalette(
            isDark = false,
            ink = Color(0xFF14202E),
            muted = Color(0x9914202E),
            accent = acc,
            accentSoft = acc.copy(alpha = 0.18f),
            bg1 = Color(0xFFCFE9FF),
            bg2 = Color(0xFFFFC9E4),
            bg3 = Color(0xFFC9FFE3),
            cardFill = Color(0xBDFFFFFF),
            cardBorder = Color(0xF0FFFFFF),
            pillFillTop = Color(0xA6FFFFFF),
            pillFillBottom = Color(0x5CFFFFFF),
            pillBorder = Color(0xF5FFFFFF),
            overlayBg = Color(0xF2E8F2FF),
            shadowColor = Color(0x1F1C305C)
        )
    }
}

@Composable
fun GlimpseTheme(
    themeMode: ThemeMode,
    accentHue: Float,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.Auto -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val palette = remember(isDark, accentHue) { buildGlimpsePalette(isDark, accentHue) }
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.accent,
            background = palette.bg1,
            surface = palette.overlayBg,
            onBackground = palette.ink,
            onSurface = palette.ink
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = palette.bg1,
            surface = palette.overlayBg,
            onBackground = palette.ink,
            onSurface = palette.ink
        )
    }
    CompositionLocalProvider(LocalGlimpsePalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

@Composable
fun LiquidWallpaper(modifier: Modifier = Modifier) {
    val p = LocalGlimpsePalette.current
    val accentGlow = remember(p.accent, p.isDark) {
        p.accent.copy(alpha = if (p.isDark) 0.42f else 0.34f)
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(p.bg1, p.bg2.copy(alpha = 0.55f), p.bg1),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentGlow, Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.08f),
                radius = size.maxDimension * 0.55f
            ),
            radius = size.maxDimension * 0.55f,
            center = Offset(size.width * 0.12f, size.height * 0.08f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(p.bg2.copy(alpha = 0.75f), Color.Transparent),
                center = Offset(size.width * 0.94f, size.height * 0.18f),
                radius = size.maxDimension * 0.5f
            ),
            radius = size.maxDimension * 0.5f,
            center = Offset(size.width * 0.94f, size.height * 0.18f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(p.bg3.copy(alpha = 0.72f), Color.Transparent),
                center = Offset(size.width * 0.78f, size.height * 0.92f),
                radius = size.maxDimension * 0.58f
            ),
            radius = size.maxDimension * 0.58f,
            center = Offset(size.width * 0.78f, size.height * 0.92f)
        )
    }
}

fun Modifier.springPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "springPress"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalGlimpsePalette.current
    val fill = if (selected) p.accentSoft else p.cardFill
    val borderColor = if (selected) p.accent else p.cardBorder
    val borderWidth = if (selected) 1.5.dp else 1.dp
    Box(
        modifier = modifier
            .shadow(elevation = if (p.isDark) 6.dp else 8.dp, shape = shape, ambientColor = p.shadowColor, spotColor = p.shadowColor)
            .clip(shape)
            .background(fill)
            .border(borderWidth, borderColor, shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color? = null
) {
    val p = LocalGlimpsePalette.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .springPress(interaction, 0.92f)
            .shadow(10.dp, CircleShape, ambientColor = p.shadowColor, spotColor = p.shadowColor)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(p.pillFillTop, p.pillFillBottom)))
            .border(1.dp, p.pillBorder, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: p.ink,
            modifier = Modifier.size(if (size >= 46.dp) 23.dp else 20.dp)
        )
    }
}

data class FileVisualSpec(
    val color: Color,
    val icon: ImageVector,
    val supportsPreview: Boolean
)

fun fileVisualSpec(item: FileItem, accent: Color): FileVisualSpec {
    if (item.isDir) {
        return FileVisualSpec(accent, Icons.Rounded.Folder, false)
    }
    return when (item.ext) {
        "jpg", "jpeg", "png", "webp", "gif", "heic", "bmp" ->
            FileVisualSpec(Color(0xFF17B88A), Icons.Rounded.Image, true)
        "mp4", "mkv", "avi", "mov", "webm", "3gp" ->
            FileVisualSpec(Color(0xFFF0457F), Icons.Rounded.VideoFile, true)
        "apk", "apks", "xapk" ->
            FileVisualSpec(Color(0xFF7B61FF), Icons.Rounded.Android, item.ext == "apk")
        "zip", "rar", "7z", "tar", "gz", "bz2" ->
            FileVisualSpec(Color(0xFFE8960F), Icons.Rounded.Archive, false)
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "rtf", "epub" ->
            FileVisualSpec(Color(0xFF9B5DE5), Icons.Rounded.Description, false)
        "mp3", "flac", "m4a", "wav", "ogg", "aac", "opus" ->
            FileVisualSpec(Color(0xFF12A8C4), Icons.Rounded.AudioFile, true)
        else ->
            FileVisualSpec(Color(0xFF6B7A90), Icons.Rounded.InsertDriveFile, false)
    }
}

fun categoryColor(cat: FileCategory): Color = when (cat) {
    FileCategory.Images -> Color(0xFF17B88A)
    FileCategory.Videos -> Color(0xFFF0457F)
    FileCategory.Apks -> Color(0xFF7B61FF)
    FileCategory.Archives -> Color(0xFFE8960F)
    FileCategory.Downloads -> Color(0xFF2A8CF0)
    FileCategory.Documents -> Color(0xFF9B5DE5)
    FileCategory.Audio -> Color(0xFF12A8C4)
    FileCategory.LargeFiles -> Color(0xFFF0457F)
    FileCategory.ThisWeek -> Color(0xFF2A8CF0)
    FileCategory.Recent -> Color(0xFF2A8CF0)
}

@Composable
fun FileThumbnailBadge(
    item: FileItem,
    vm: GlimpseViewModel,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    iconSize: Dp = 24.dp
) {
    val p = LocalGlimpsePalette.current
    val spec = remember(item.name, item.isDir, p.accent) { fileVisualSpec(item, p.accent) }
    var bitmap by remember(item.fullRelativePath, item.modifiedMs) { mutableStateOf<Bitmap?>(null) }

    if (spec.supportsPreview) {
        LaunchedEffect(item.fullRelativePath, item.modifiedMs) {
            bitmap = vm.getThumbnail(item)
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val bgBrush = remember(item.name, spec) {
        if (item.ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "mp4", "mkv", "avi", "mov", "webm", "3gp")) {
            val h = abs(item.name.hashCode() % 360).toFloat()
            Brush.linearGradient(
                listOf(Color.hsl(h, 0.78f, 0.58f), Color.hsl((h + 50f) % 360f, 0.78f, 0.48f))
            )
        } else {
            Brush.linearGradient(listOf(spec.color.copy(alpha = 0.22f), spec.color.copy(alpha = 0.22f)))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
    ) {
        val currentBmp = bitmap
        if (currentBmp != null && !currentBmp.isRecycled) {
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = null,
                contentScale = if (item.ext == "apk") ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (item.ext == "apk") 6.dp else 0.dp)
            )
        } else {
            val iconTint = if (item.ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "mp4", "mkv", "avi", "mov", "webm", "3gp")) {
                Color.White
            } else {
                spec.color
            }
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun FilterAndViewBar(
    currentSort: SortMode,
    isGridView: Boolean,
    onSelectSort: (SortMode) -> Unit,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalGlimpsePalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        GlassCard(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(1f)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(SortMode.entries) { mode ->
                    val active = mode == currentSort
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (active) p.accent.copy(alpha = 0.28f) else Color.Transparent)
                            .clickable { onSelectSort(mode) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) p.accent else p.ink
                        )
                    }
                }
            }
        }

        val toggleInteraction = remember { MutableInteractionSource() }
        GlassCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .size(40.dp)
                .springPress(toggleInteraction, 0.92f)
                .clickable(interactionSource = toggleInteraction, indication = null, onClick = onToggleView)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = "Switch view",
                    tint = p.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItemRow(
    item: FileItem,
    vm: GlimpseViewModel,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalGlimpsePalette.current
    val interaction = remember { MutableInteractionSource() }
    GlassCard(
        shape = RoundedCornerShape(20.dp),
        selected = isSelected,
        modifier = modifier
            .fillMaxWidth()
            .springPress(interaction, 0.97f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            FileThumbnailBadge(
                item = item,
                vm = vm,
                modifier = Modifier.size(44.dp),
                cornerRadius = 14.dp,
                iconSize = 23.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val subtitle = if (item.isDir) {
                    val szPart = item.computedDirSize?.takeIf { it > 0L }?.let { ", ${formatBytes(it)}" } ?: ""
                    "${item.itemCount} items$szPart"
                } else {
                    "${formatBytes(item.size)}, ${formatRelativeTime(item.modifiedMs)}"
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelecting) {
                SelectionCircle(checked = isSelected)
            } else if (item.isDir) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = p.muted,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "Options for ${item.name}",
                        tint = p.muted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItemCard(
    item: FileItem,
    vm: GlimpseViewModel,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalGlimpsePalette.current
    val interaction = remember { MutableInteractionSource() }
    GlassCard(
        shape = RoundedCornerShape(22.dp),
        selected = isSelected,
        modifier = modifier
            .fillMaxWidth()
            .springPress(interaction, 0.96f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 13.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(152.dp)) {
                FileThumbnailBadge(
                    item = item,
                    vm = vm,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 16.dp,
                    iconSize = 42.dp
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) p.accent else Color(0x880E1224))
                        .clickable { if (isSelecting) onClick() else onMoreClick() }
                ) {
                    if (isSelecting) {
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Box(Modifier.size(16.dp).border(1.8.dp, Color.White.copy(alpha = 0.85f), CircleShape))
                        }
                    } else {
                        Icon(
                            imageVector = if (item.isDir) Icons.Rounded.ChevronRight else Icons.Rounded.MoreHoriz,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
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
                val sub = if (item.isDir) {
                    "${item.itemCount} items"
                } else {
                    "${formatBytes(item.size)}, ${formatRelativeTime(item.modifiedMs)}"
                }
                Text(
                    text = sub,
                    fontSize = 12.sp,
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SelectionCircle(checked: Boolean) {
    val p = LocalGlimpsePalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (checked) p.accent else Color.Transparent)
            .border(2.dp, if (checked) p.accent else p.muted, CircleShape)
    ) {
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

fun formatBytes(bytes: Long): String {
    val gb = 1_073_741_824.0
    val mb = 1_048_576.0
    return when {
        bytes >= gb -> String.format("%.1f GB", bytes / gb)
        bytes >= mb -> if (bytes >= 100 * mb) String.format("%.0f MB", bytes / mb) else String.format("%.1f MB", bytes / mb)
        else -> "${maxOf(1L, Math.round(bytes / 1024.0))} KB"
    }
}

fun formatRelativeTime(timestampMs: Long): String {
    val mins = ((System.currentTimeMillis() - timestampMs) / 60_000L).coerceAtLeast(1L)
    return when {
        mins < 60L -> "$mins min ago"
        mins < 1440L -> "${mins / 60L} h ago"
        else -> "${mins / 1440L} d ago"
    }
}
