package com.glimpse.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glimpse.files.data.ClipboardState
import com.glimpse.files.data.FileItem

private data class SheetActionSpec(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionBottomSheet(
    item: FileItem,
    vm: GlimpseViewModel,
    onDismiss: () -> Unit
) {
    val p = LocalGlimpsePalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isArchive = item.ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2")

    val actions = buildList {
        add(SheetActionSpec(Icons.Rounded.ContentCopy, "Copy") {
            onDismiss()
            vm.clipboard = ClipboardState(listOf(item), item.pathSegments, isMove = false)
            if (vm.overlays.isNotEmpty()) {
                vm.overlays.clear()
                vm.activeTab = 1
            }
            vm.showToast("Copied. Open a folder and tap Paste.")
        })
        add(SheetActionSpec(Icons.Rounded.DriveFileMove, "Move") {
            onDismiss()
            vm.clipboard = ClipboardState(listOf(item), item.pathSegments, isMove = true)
            if (vm.overlays.isNotEmpty()) {
                vm.overlays.clear()
                vm.activeTab = 1
            }
            vm.showToast("Ready to move. Open a folder and tap Paste.")
        })
        add(SheetActionSpec(Icons.Rounded.Edit, "Rename") {
            onDismiss()
            vm.renameTarget = item
        })
        if (!item.isDir) {
            add(SheetActionSpec(Icons.Rounded.Share, "Share") {
                onDismiss()
                vm.shareItems(listOf(item))
            })
        }
        add(SheetActionSpec(Icons.Rounded.Delete, "Delete", Color(0xFFE5484D)) {
            onDismiss()
            vm.deleteItems(listOf(item))
        })
        if (isArchive) {
            add(SheetActionSpec(Icons.Rounded.Archive, "Inspect") {
                onDismiss()
                vm.openOverlay(OverlayScreen.ZipInspector(item))
            })
            add(SheetActionSpec(Icons.Rounded.Download, "Extract") {
                onDismiss()
                vm.extractArchive(item, extractHere = false)
            })
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.overlayBg,
        contentColor = p.ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = item.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val loc = item.pathSegments.joinToString("/").ifEmpty { "Internal storage" }
            val sizeStr = if (item.isDir) "${item.itemCount} items" else formatBytes(item.size)
            Text(
                text = "$sizeStr, $loc",
                fontSize = 13.sp,
                color = p.muted
            )
            Spacer(Modifier.height(14.dp))

            actions.chunked(3).forEach { rowActions ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    rowActions.forEach { act ->
                        GlassCard(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { act.onClick() }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = act.icon,
                                    contentDescription = act.label,
                                    tint = act.tint ?: p.ink,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = act.label,
                                    fontSize = 12.sp,
                                    color = act.tint ?: p.ink,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    repeat(3 - rowActions.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun CreateNewItemDialog(
    folderLabel: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, isFolder: Boolean) -> Unit
) {
    val p = LocalGlimpsePalette.current
    var isFolder by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("New folder") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.overlayBg,
        titleContentColor = p.ink,
        textContentColor = p.ink,
        title = { Text("Create new", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("In $folderLabel", fontSize = 13.sp, color = p.muted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x28808080))
                        .padding(3.dp)
                ) {
                    listOf(true to "Folder", false to "File").forEach { (folderOpt, label) ->
                        val active = isFolder == folderOpt
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (active) p.cardFill else Color.Transparent)
                                .clickable {
                                    isFolder = folderOpt
                                    name = if (folderOpt) "New folder" else "New file.txt"
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                label,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) p.accent else p.ink
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCreate(name, isFolder)
                onDismiss()
            }) {
                Text("Create", fontWeight = FontWeight.Bold, color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = p.muted)
            }
        }
    )
}

@Composable
fun RenameItemDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    val p = LocalGlimpsePalette.current
    var name by remember(item) { mutableStateOf(item.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.overlayBg,
        titleContentColor = p.ink,
        textContentColor = p.ink,
        title = { Text("Rename", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onRename(name)
                onDismiss()
            }) {
                Text("Save", fontWeight = FontWeight.Bold, color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = p.muted)
            }
        }
    )
}

@Composable
fun CompressZipDialog(
    defaultName: String,
    itemCount: Int,
    onDismiss: () -> Unit,
    onCompress: (String) -> Unit
) {
    val p = LocalGlimpsePalette.current
    var name by remember(defaultName) { mutableStateOf(defaultName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.overlayBg,
        titleContentColor = p.ink,
        textContentColor = p.ink,
        title = { Text("Create .zip archive", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (itemCount > 0) "$itemCount selected item(s)" else "Compress current folder",
                    fontSize = 13.sp,
                    color = p.muted
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCompress(name)
                onDismiss()
            }) {
                Text("Compress", fontWeight = FontWeight.Bold, color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = p.muted)
            }
        }
    )
}
