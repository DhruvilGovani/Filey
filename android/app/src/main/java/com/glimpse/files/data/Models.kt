package com.glimpse.files.data

import java.io.File

enum class FileCategory(val key: String, val label: String) {
    Images("img", "Images"),
    Videos("vid", "Videos"),
    Apks("apk", "APKs"),
    Archives("arc", "Archives"),
    Downloads("dl", "Downloads"),
    Documents("doc", "Documents"),
    Audio("aud", "Audio"),
    LargeFiles("big", "Large files"),
    ThisWeek("week", "Last 7 days"),
    Recent("recent", "Recent")
}

enum class SortMode(val key: String, val label: String) {
    DateAdded("date", "Date added"),
    MostSize("size", "Most size"),
    NameAZ("az", "A-Z"),
    NameZA("za", "Z-A"),
    Oldest("old", "Oldest")
}

enum class ThemeMode(val key: String, val label: String) {
    Auto("auto", "Auto"),
    Light("light", "Light"),
    Dark("dark", "Dark")
}

data class FileItem(
    val name: String,
    val pathSegments: List<String>,
    val isDir: Boolean,
    val size: Long,
    val modifiedMs: Long,
    val itemCount: Int = 0,
    val computedDirSize: Long? = null
) {
    val fullRelativePath: String
        get() = (pathSegments + name).joinToString("/")

    val ext: String
        get() = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
}

data class StorageSlice(
    val name: String,
    val bytes: Long,
    val colorHex: Long
)

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val slices: List<StorageSlice>
) {
    val freeBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)
    val usedFraction: Float get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class TrashItem(
    val id: String,
    val name: String,
    val fromRelPath: String,
    val size: Long,
    val isDir: Boolean,
    val deletedAtMs: Long
)

data class ZipEntryItem(
    val name: String,
    val size: Long,
    val isDir: Boolean
)

data class ClipboardState(
    val items: List<FileItem>,
    val sourcePath: List<String>,
    val isMove: Boolean
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.Auto,
    val accentHue: Float = 200f,
    val showHidden: Boolean = false,
    val sortMode: SortMode = SortMode.DateAdded,
    val isGridView: Boolean = false,
    val binRetentionDays: Int = 14,
    val ftpPort: Int = 2121,
    val ftpUser: String = "admin",
    val ftpPass: String = "files123",
    val extractDest: String = "Download/Extracted"
)
