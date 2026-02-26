package app.marlboroadvance.mpvex.utils.sort

import app.marlboroadvance.mpvex.domain.browser.FileSystemItem
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.FolderSortType
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.VideoSortType

object SortUtils {
  
  fun sortVideos(videos: List<Video>, sortType: VideoSortType, sortOrder: SortOrder): List<Video> {
    val sorted = when (sortType) {
      VideoSortType.Title -> videos.sortedWith { t1, t2 -> NaturalOrderComparator.DEFAULT.compare(t1.displayName, t2.displayName) }
      VideoSortType.Duration -> videos.sortedBy { it.duration }
      VideoSortType.Date -> videos.sortedBy { it.dateModified }
      VideoSortType.Size -> videos.sortedBy { it.size }
    }
    return if (sortOrder.isAscending) sorted else sorted.reversed()
  }

  fun sortFolders(
    folders: List<VideoFolder>,
    sortType: FolderSortType,
    sortOrder: SortOrder,
    pinnedFolders: List<String> = emptyList() // Changed to List to preserve exact order
  ): List<VideoFolder> {
    val sorted = when (sortType) {
      FolderSortType.Title -> folders.sortedWith { t1, t2 -> NaturalOrderComparator.DEFAULT.compare(t1.name, t2.name) }
      FolderSortType.Date -> folders.sortedByDescending { it.lastModified }
      FolderSortType.Size -> folders.sortedByDescending { it.totalSize }
      FolderSortType.VideoCount -> folders.sortedByDescending { it.videoCount }
    }
      
    val orderedFolders = if (sortOrder.isAscending) sorted else sorted.reversed()

    if (pinnedFolders.isEmpty()) return orderedFolders

    // Map exactly according to the pinnedFolders list order
    val pinned = pinnedFolders.mapNotNull { path -> orderedFolders.find { it.path == path } }
    val unpinned = orderedFolders.filterNot { pinnedFolders.contains(it.path) }

    return pinned + unpinned
  }

  fun sortFileSystemItems(items: List<FileSystemItem>, sortType: FolderSortType, sortOrder: SortOrder): List<FileSystemItem> {
    val folders = items.filterIsInstance<FileSystemItem.Folder>()
    val videos = items.filterIsInstance<FileSystemItem.VideoFile>()

    val sortedFolders = when (sortType) {
      FolderSortType.Title -> folders.sortedWith { t1, t2 -> NaturalOrderComparator.DEFAULT.compare(t1.name, t2.name) }
      FolderSortType.Date -> folders.sortedBy { it.lastModified }
      FolderSortType.Size -> folders.sortedBy { it.totalSize }
      FolderSortType.VideoCount -> folders.sortedBy { it.videoCount }
    }

    val sortedVideos = when (sortType) {
      FolderSortType.Title -> videos.sortedWith { t1, t2 -> NaturalOrderComparator.DEFAULT.compare(t1.name, t2.name) }
      FolderSortType.Date -> videos.sortedBy { it.lastModified }
      FolderSortType.Size -> videos.sortedBy { it.video.size }
      FolderSortType.VideoCount -> videos.sortedBy { it.video.duration } 
    }

    val orderedFolders = if (sortOrder.isAscending) sortedFolders else sortedFolders.reversed()
    val orderedVideos = if (sortOrder.isAscending) sortedVideos else sortedVideos.reversed()

    return orderedFolders + orderedVideos
  }

  class NaturalOrderComparator(private val ignoreCase: Boolean, private val shouldSkip: (Char) -> Boolean) : Comparator<String> {
    companion object { val DEFAULT = NaturalOrderComparator(ignoreCase = true, shouldSkip = { it.isWhitespace() }) }
    override fun compare(a: String, b: String): Int {
      var ia = 0; var ib = 0
      while (true) {
        while (ia < a.length && shouldSkip(a[ia])) ia++
        while (ib < b.length && shouldSkip(b[ib])) ib++
        if (ia >= a.length || ib >= b.length) return when { ia >= a.length && ib >= b.length -> 0; ia >= a.length -> -1; else -> 1 }
        val numA = parseNumber(a, ia); val numB = parseNumber(b, ib)
        when {
          numA != null && numB != null -> { val cmp = numA.value.compareTo(numB.value); if (cmp != 0) return cmp; ia = numA.exclusiveEndIndex; ib = numB.exclusiveEndIndex }
          else -> { val ca = if (ignoreCase) a[ia].lowercaseChar() else a[ia]; val cb = if (ignoreCase) b[ib].lowercaseChar() else b[ib]; val cmp = ca.compareTo(cb); if (cmp != 0) return cmp; ia++; ib++ }
        }
      }
    }
    private data class ParsedNumber(val value: Int, val exclusiveEndIndex: Int)
    private fun parseNumber(s: String, start: Int): ParsedNumber? {
      var i = start; var hasDigit = false
      while (i < s.length) { val c = s[i]; if (c.isDigit()) { hasDigit = true; i++ } else break }
      if (!hasDigit) return null
      return try { ParsedNumber(s.substring(start, i).toInt(), i) } catch (_: Exception) { null }
    }
  }
}
