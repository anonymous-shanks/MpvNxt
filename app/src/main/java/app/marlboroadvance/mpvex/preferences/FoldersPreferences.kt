package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore

/**
 * Preferences for folder management
 */
class FoldersPreferences(
  preferenceStore: PreferenceStore,
) {
  // Set of folder paths that should be hidden from the folder list
  val blacklistedFolders = preferenceStore.getStringSet("blacklisted_folders", emptySet())

  // Ordered list of pinned folder paths, stored as a single string separated by |||
  // This allows us to keep the custom user-defined order (Move Up / Move Down)
  val pinnedFoldersList = preferenceStore.getString("pinned_folders_list", "")
}
