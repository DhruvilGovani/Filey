package com.glimpse.files.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.files.data.AppSettings
import com.glimpse.files.data.ClipboardState
import com.glimpse.files.data.FileCategory
import com.glimpse.files.data.FileItem
import com.glimpse.files.data.FileRepository
import com.glimpse.files.data.SortMode
import com.glimpse.files.data.StorageInfo
import com.glimpse.files.data.ThemeMode
import com.glimpse.files.data.TrashItem
import com.glimpse.files.data.ZipEntryItem
import kotlinx.coroutines.launch

sealed interface OverlayScreen {
    data class CategoryList(val category: FileCategory) : OverlayScreen
    data object Search : OverlayScreen
    data object StorageAnalytics : OverlayScreen
    data object MoreTools : OverlayScreen
    data object RecycleBin : OverlayScreen
    data object FtpServer : OverlayScreen
    data object Settings : OverlayScreen
    data class ZipInspector(val item: FileItem) : OverlayScreen
}

class GlimpseViewModel(app: Application) : AndroidViewModel(app) {
    val repo = FileRepository(app)

    var hasAccess by mutableStateOf(repo.hasAllFilesAccess())
    var settings by mutableStateOf(repo.loadSettings())
    var activeTab by mutableStateOf(0) // 0 = Home, 1 = Browse

    var storageInfo by mutableStateOf<StorageInfo?>(null)
    var recentFiles by mutableStateOf<List<FileItem>>(emptyList())

    // Browse state
    var currentPath by mutableStateOf<List<String>>(emptyList())
    var browseItems by mutableStateOf<List<FileItem>>(emptyList())
    var isBrowseLoading by mutableStateOf(false)

    // Overlay stack
    val overlays = mutableStateListOf<OverlayScreen>()
    var overlayItems by mutableStateOf<List<FileItem>>(emptyList())
    var overlaySort by mutableStateOf(SortMode.DateAdded)
    var isOverlayLoading by mutableStateOf(false)

    // Search state
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<FileItem>>(emptyList())

    // Trash state
    var trashItems by mutableStateOf<List<TrashItem>>(emptyList())
    var isTrashEmptying by mutableStateOf(false)

    // Zip inspector state
    var zipEntries by mutableStateOf<List<ZipEntryItem>>(emptyList())

    // FTP state
    var ftpRunning by mutableStateOf(false)
    var ftpIp by mutableStateOf(repo.getLocalIpAddress())

    // Multi-select & Clipboard
    val selectedKeys = mutableStateMapOf<String, FileItem>()
    var clipboard by mutableStateOf<ClipboardState?>(null)

    // Active file action sheet & dialogs
    var actionTarget by mutableStateOf<FileItem?>(null)
    var showCreateDialog by mutableStateOf(false)
    var renameTarget by mutableStateOf<FileItem?>(null)
    var zipDialogTarget by mutableStateOf<Pair<List<String>, List<String>>?>(null) // (path, names)

    // Toast message
    var toastMessage by mutableStateOf<String?>(null)

    init {
        refreshAll()
    }

    fun showToast(msg: String) {
        toastMessage = msg
    }

    fun clearToast() {
        toastMessage = null
    }

    fun recheckAccess() {
        val granted = repo.hasAllFilesAccess()
        if (granted && !hasAccess) {
            hasAccess = true
            refreshAll()
        } else {
            hasAccess = granted
        }
    }

    fun refreshAll() {
        hasAccess = repo.hasAllFilesAccess()
        if (!hasAccess) return
        viewModelScope.launch {
            storageInfo = repo.getStorageInfo()
        }
        viewModelScope.launch {
            recentFiles = repo.getByCategory(FileCategory.Recent, 15)
        }
        loadBrowse(currentPath)
    }

    fun loadBrowse(path: List<String> = currentPath) {
        currentPath = path
        clearSelection()
        isBrowseLoading = true
        viewModelScope.launch {
            val raw = repo.listDirectory(path, settings.showHidden)
            browseItems = sortFiles(raw, settings.sortMode)
            isBrowseLoading = false
            val sizes = repo.calculateFolderSizes(path)
            if (currentPath == path && sizes.isNotEmpty()) {
                browseItems = browseItems.map { item ->
                    if (item.isDir && sizes.containsKey(item.name)) {
                        item.copy(computedDirSize = sizes[item.name])
                    } else item
                }
            }
        }
    }

    fun sortFiles(list: List<FileItem>, mode: SortMode): List<FileItem> {
        val dirs = list.filter { it.isDir }
        val files = list.filter { !it.isDir }
        val cmp: Comparator<FileItem> = when (mode) {
            SortMode.DateAdded -> compareByDescending { it.modifiedMs }
            SortMode.MostSize -> compareByDescending { it.computedDirSize ?: it.size }
            SortMode.NameAZ -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.NameZA -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.Oldest -> compareBy { it.modifiedMs }
        }
        return dirs.sortedWith(cmp) + files.sortedWith(cmp)
    }

    fun updateSettings(newSettings: AppSettings) {
        val reloadNeeded = newSettings.showHidden != settings.showHidden || newSettings.sortMode != settings.sortMode
        settings = newSettings
        repo.saveSettings(newSettings)
        if (reloadNeeded) {
            loadBrowse(currentPath)
        }
    }

    fun setBrowseSort(mode: SortMode) {
        updateSettings(settings.copy(sortMode = mode))
    }

    fun toggleViewMode() {
        updateSettings(settings.copy(isGridView = !settings.isGridView))
    }

    // Overlay navigation
    fun openOverlay(screen: OverlayScreen) {
        clearSelection()
        overlays.add(screen)
        when (screen) {
            is OverlayScreen.CategoryList -> {
                overlaySort = if (screen.category == FileCategory.LargeFiles) SortMode.MostSize else SortMode.DateAdded
                overlayItems = emptyList()
                isOverlayLoading = true
                viewModelScope.launch {
                    val limit = if (screen.category == FileCategory.Recent) 250 else 400
                    val raw = repo.getByCategory(screen.category, limit)
                    overlayItems = sortFiles(raw, overlaySort)
                    isOverlayLoading = false
                }
            }
            OverlayScreen.Search -> {
                searchQuery = ""
                searchResults = emptyList()
            }
            OverlayScreen.RecycleBin -> {
                loadTrash()
            }
            OverlayScreen.MoreTools -> {
                loadTrash()
            }
            is OverlayScreen.ZipInspector -> {
                zipEntries = emptyList()
                viewModelScope.launch {
                    zipEntries = repo.inspectZip(screen.item.pathSegments, screen.item.name)
                }
            }
            else -> Unit
        }
    }

    fun popOverlay(): Boolean {
        if (selectedKeys.isNotEmpty()) {
            clearSelection()
            return true
        }
        if (overlays.isNotEmpty()) {
            overlays.removeAt(overlays.lastIndex)
            return true
        }
        return false
    }

    fun setOverlaySort(mode: SortMode) {
        overlaySort = mode
        overlayItems = sortFiles(overlayItems, mode)
    }

    fun onSearchQueryChange(q: String) {
        searchQuery = q
        if (q.trim().isEmpty()) {
            searchResults = emptyList()
            return
        }
        viewModelScope.launch {
            val res = repo.searchFiles(q, settings.showHidden)
            if (searchQuery == q) {
                searchResults = res
            }
        }
    }

    fun navigateToFolder(item: FileItem) {
        overlays.clear()
        clearSelection()
        activeTab = 1
        loadBrowse(item.pathSegments + item.name)
    }

    // Multi-select
    fun toggleSelection(item: FileItem) {
        val k = item.fullRelativePath
        if (selectedKeys.containsKey(k)) {
            selectedKeys.remove(k)
        } else {
            selectedKeys[k] = item
        }
    }

    fun selectAll(items: List<FileItem>) {
        items.forEach { selectedKeys[it.fullRelativePath] = it }
    }

    fun clearSelection() {
        selectedKeys.clear()
    }

    // File operations
    fun openOrToggle(item: FileItem) {
        if (selectedKeys.isNotEmpty()) {
            toggleSelection(item)
            return
        }
        if (item.isDir) {
            loadBrowse(item.pathSegments + item.name)
        } else {
            if (!repo.openFile(item)) {
                showToast("No app can open this file")
            }
        }
    }

    fun copySelected() {
        val items = selectedKeys.values.toList()
        clearSelection()
        val paths = items.map { it.pathSegments }.distinct()
        if (paths.size != 1) {
            showToast("Select items from one folder to copy")
            return
        }
        clipboard = ClipboardState(items, paths.first(), isMove = false)
        if (overlays.isNotEmpty()) {
            overlays.clear()
            activeTab = 1
        }
        showToast("${items.size} copied. Open a folder and tap Paste.")
    }

    fun moveSelected() {
        val items = selectedKeys.values.toList()
        clearSelection()
        val paths = items.map { it.pathSegments }.distinct()
        if (paths.size != 1) {
            showToast("Select items from one folder to move")
            return
        }
        clipboard = ClipboardState(items, paths.first(), isMove = true)
        if (overlays.isNotEmpty()) {
            overlays.clear()
            activeTab = 1
        }
        showToast("${items.size} ready to move. Open a folder and tap Paste.")
    }

    fun pasteClipboard() {
        val clip = clipboard ?: return
        val dest = currentPath
        val destStr = dest.joinToString("/")
        if (clip.items.any { it.isDir && "$destStr/".startsWith("${it.fullRelativePath}/") }) {
            showToast("A folder cannot go inside itself")
            return
        }
        clipboard = null
        viewModelScope.launch {
            val ok = repo.transferItems(clip.sourcePath, clip.items.map { it.name }, dest, clip.isMove)
            if (ok) {
                loadBrowse(dest)
                recentFiles = repo.getByCategory(FileCategory.Recent, 15)
                showToast("${if (clip.isMove) "Moved" else "Pasted"} ${clip.items.size} item(s)")
            } else {
                showToast("Transfer failed")
            }
        }
    }

    fun shareItems(items: List<FileItem>) {
        clearSelection()
        if (!repo.shareFiles(items)) {
            showToast("Select files to share")
        }
    }

    fun deleteItems(items: List<FileItem>) {
        clearSelection()
        val grouped = items.groupBy { it.pathSegments }
        viewModelScope.launch {
            grouped.forEach { (seg, list) ->
                repo.moveToTrash(seg, list.map { it.name })
            }
            val deletedPaths = items.map { it.fullRelativePath }.toSet()
            overlayItems = overlayItems.filterNot { it.fullRelativePath in deletedPaths }
            loadBrowse(currentPath)
            recentFiles = repo.getByCategory(FileCategory.Recent, 15)
            loadTrash()
            showToast("${items.size} item(s) moved to Recycle Bin")
        }
    }

    fun createNew(name: String, isFolder: Boolean) {
        val clean = if (!isFolder && !name.contains('.')) "$name.txt" else name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val ok = if (isFolder) repo.createFolder(currentPath, clean) else repo.createFile(currentPath, clean)
            if (ok) {
                loadBrowse(currentPath)
                showToast("${if (isFolder) "Folder" else "File"} created")
            } else {
                showToast("Could not create $clean")
            }
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean == item.name) return
        viewModelScope.launch {
            if (repo.renameItem(item.pathSegments, item.name, clean)) {
                loadBrowse(currentPath)
                recentFiles = repo.getByCategory(FileCategory.Recent, 15)
                showToast("Renamed to $clean")
            } else {
                showToast("Rename failed")
            }
        }
    }

    fun compressItems(segments: List<String>, names: List<String>, outName: String) {
        showToast("Compressing...")
        viewModelScope.launch {
            val created = repo.createZip(segments, names, outName)
            if (created != null) {
                if (currentPath == segments) loadBrowse(currentPath)
                recentFiles = repo.getByCategory(FileCategory.Recent, 15)
                showToast("Created $created")
            } else {
                showToast("Compression failed")
            }
        }
    }

    fun extractArchive(item: FileItem, extractHere: Boolean) {
        val dest = if (extractHere) item.pathSegments else settings.extractDest.split('/').filter { it.isNotEmpty() }
        showToast("Extracting ${item.name}...")
        viewModelScope.launch {
            val folder = repo.extractZip(item.pathSegments, item.name, dest)
            if (folder != null) {
                if (currentPath == dest) loadBrowse(currentPath)
                recentFiles = repo.getByCategory(FileCategory.Recent, 15)
                showToast("Extracted to $folder")
            } else {
                showToast("Extraction failed (only .zip supported)")
            }
        }
    }

    // Recycle Bin
    fun loadTrash() {
        viewModelScope.launch {
            trashItems = repo.listTrash(settings.binRetentionDays)
        }
    }

    fun restoreTrashItem(item: TrashItem) {
        viewModelScope.launch {
            if (repo.restoreTrash(item.id)) {
                loadTrash()
                loadBrowse(currentPath)
                recentFiles = repo.getByCategory(FileCategory.Recent, 15)
                showToast("Restored ${item.name}")
            }
        }
    }

    fun wipeTrashItem(item: TrashItem) {
        viewModelScope.launch {
            if (repo.wipeTrash(item.id)) {
                loadTrash()
                showToast("Deleted forever")
            }
        }
    }

    fun emptyTrash() {
        isTrashEmptying = true
        trashItems = emptyList()
        viewModelScope.launch {
            repo.emptyTrash()
            isTrashEmptying = false
            showToast("Recycle Bin emptied")
        }
    }

    // FTP Server
    fun toggleFtpServer(enable: Boolean) {
        val res = repo.toggleFtp(enable, settings.ftpPort, settings.ftpUser, settings.ftpPass)
        res.onSuccess { ip ->
            ftpRunning = enable
            ftpIp = ip
            showToast(if (enable) "FTP server started" else "FTP server stopped")
        }.onFailure { err ->
            ftpRunning = false
            showToast("FTP: ${err.message ?: "failed"}")
        }
    }

    suspend fun getThumbnail(item: FileItem): Bitmap? = repo.loadThumbnail(item)
}
