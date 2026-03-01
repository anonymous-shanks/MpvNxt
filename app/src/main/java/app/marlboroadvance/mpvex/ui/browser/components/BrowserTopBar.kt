package app.marlboroadvance.mpvex.ui.browser.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.theme.DarkMode
import app.marlboroadvance.mpvex.ui.theme.LocalThemeTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserTopBar(
  title: String,
  isInSelectionMode: Boolean,
  selectedCount: Int,
  totalCount: Int,
  onCancelSelection: () -> Unit,
  modifier: Modifier = Modifier,
  onBackClick: (() -> Unit)? = null,
  onSortClick: (() -> Unit)? = null,
  onSearchClick: (() -> Unit)? = null,
  onSettingsClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  onRenameClick: (() -> Unit)? = null,
  isSingleSelection: Boolean = false,
  onInfoClick: (() -> Unit)? = null,
  onShareClick: (() -> Unit)? = null,
  onPlayClick: (() -> Unit)? = null,
  onBlacklistClick: (() -> Unit)? = null,
  onSelectAll: (() -> Unit)? = null,
  onInvertSelection: (() -> Unit)? = null,
  onDeselectAll: (() -> Unit)? = null,
  onPinClick: (() -> Unit)? = null,
  isPinned: Boolean = false,
  isPinEnabled: Boolean = true,
  onMoveUpClick: (() -> Unit)? = null,
  isMoveUpEnabled: Boolean = false,
  onMoveDownClick: (() -> Unit)? = null,
  isMoveDownEnabled: Boolean = false,
  onAddToPlaylistClick: (() -> Unit)? = null,
  additionalActions: @Composable RowScope.() -> Unit = { },
  onTitleLongPress: (() -> Unit)? = null,
  useRemoveIcon: Boolean = false,
) {
  if (isInSelectionMode) {
    SelectionTopBar(
      selectedCount = selectedCount,
      totalCount = totalCount,
      onCancel = onCancelSelection,
      onDelete = onDeleteClick,
      onRename = onRenameClick,
      isSingleSelection = isSingleSelection,
      onInfo = onInfoClick,
      onShare = onShareClick,
      onPlay = onPlayClick,
      onBlacklist = onBlacklistClick,
      onPinClick = onPinClick,
      isPinned = isPinned,
      isPinEnabled = isPinEnabled,
      onMoveUpClick = onMoveUpClick,
      isMoveUpEnabled = isMoveUpEnabled,
      onMoveDownClick = onMoveDownClick,
      isMoveDownEnabled = isMoveDownEnabled,
      onAddToPlaylistClick = onAddToPlaylistClick,
      onSelectAll = onSelectAll,
      onInvertSelection = onInvertSelection,
      onDeselectAll = onDeselectAll,
      modifier = modifier,
      useRemoveIcon = useRemoveIcon,
    )
  } else {
    NormalTopBar(
      title = title,
      onBackClick = onBackClick,
      onSortClick = onSortClick,
      onSearchClick = onSearchClick,
      onSettingsClick = onSettingsClick,
      additionalActions = additionalActions,
      modifier = modifier,
      onTitleLongPress = onTitleLongPress,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NormalTopBar(
  title: String,
  onBackClick: (() -> Unit)?,
  onSortClick: (() -> Unit)?,
  onSearchClick: (() -> Unit)?,
  onSettingsClick: (() -> Unit)?,
  additionalActions: @Composable RowScope.() -> Unit,
  modifier: Modifier = Modifier,
  onTitleLongPress: (() -> Unit)?,
) {
  val preferences = koinInject<AppearancePreferences>()
  val darkMode by preferences.darkMode.collectAsState()
  val darkTheme = isSystemInDarkTheme()
  val themeTransition = LocalThemeTransitionState.current
  val coroutineScope = rememberCoroutineScope()
  
  val titleBounds = remember { mutableStateOf(Rect.Zero) }
  
  fun toggleDarkMode() {
    when (darkMode) {
      DarkMode.System -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.Light)
      } else {
        preferences.darkMode.set(DarkMode.Dark)
      }
      DarkMode.Light -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.System)
      } else {
        preferences.darkMode.set(DarkMode.Dark)
      }
      DarkMode.Dark -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.Light)
      } else {
        preferences.darkMode.set(DarkMode.System)
      }
    }
  }

  TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = if (MaterialTheme.colorScheme.background == Color.Black) {
        Color.Black
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
    title = {
      val titleModifier = Modifier
        .onGloballyPositioned { coordinates ->
          titleBounds.value = coordinates.boundsInWindow()
        }
        .pointerInput(onTitleLongPress) {
          detectTapGestures(
            onTap = { localOffset ->
              if (themeTransition?.isAnimating == true) return@detectTapGestures
              
              val windowOffset = Offset(
                titleBounds.value.left + localOffset.x,
                titleBounds.value.top + localOffset.y
              )
              themeTransition?.startTransition(windowOffset)
              coroutineScope.launch {
                toggleDarkMode()
              }
            },
            onLongPress = if (onTitleLongPress != null) {
              { onTitleLongPress() }
            } else null
          )
        }

      Text(
        title,
        style =
          if (onBackClick == null) {
            MaterialTheme.typography.headlineMediumEmphasized
          } else {
            MaterialTheme.typography.headlineSmall
          },
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
          titleModifier.then(
            if (onBackClick == null) {
              Modifier.padding(start = 8.dp)
            } else {
              Modifier
            },
          ),
      )
    },
    navigationIcon = {
      if (onBackClick != null) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    },
    actions = {
      additionalActions()
      if (onSearchClick != null) {
        IconButton(
          onClick = onSearchClick,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.Search,
            contentDescription = "Search",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
      if (onSortClick != null) {
        IconButton(
          onClick = onSortClick,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Default.ViewComfy,
            contentDescription = stringResource(R.string.sort),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
      if (onSettingsClick != null) {
        IconButton(
          onClick = onSettingsClick,
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          Icon(
            Icons.Filled.Settings,
            contentDescription = "Settings",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    },
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionTopBar(
  selectedCount: Int,
  totalCount: Int,
  onCancel: () -> Unit,
  onDelete: (() -> Unit)?,
  onRename: (() -> Unit)?,
  isSingleSelection: Boolean,
  onInfo: (() -> Unit)?,
  onShare: (() -> Unit)?,
  onPlay: (() -> Unit)?,
  onBlacklist: (() -> Unit)?,
  onPinClick: (() -> Unit)?,
  isPinned: Boolean = false,
  isPinEnabled: Boolean = true,
  onMoveUpClick: (() -> Unit)? = null,
  isMoveUpEnabled: Boolean = false,
  onMoveDownClick: (() -> Unit)? = null,
  isMoveDownEnabled: Boolean = false,
  onAddToPlaylistClick: (() -> Unit)? = null,
  onSelectAll: (() -> Unit)?,
  onInvertSelection: (() -> Unit)?,
  onDeselectAll: (() -> Unit)?,
  modifier: Modifier = Modifier,
  useRemoveIcon: Boolean = false,
) {
  var showDropdown by remember { mutableStateOf(false) }
  var showOverflow by remember { mutableStateOf(false) }

  TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = if (MaterialTheme.colorScheme.background == Color.Black) {
        Color.Black
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { showDropdown = true },
      ) {
        Text(
          stringResource(R.string.selected_items, selectedCount, totalCount),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(
          Icons.Filled.ArrowDropDown,
          contentDescription = stringResource(R.string.selection_options),
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.primary,
        )

        DropdownMenu(
          expanded = showDropdown,
          onDismissRequest = { showDropdown = false },
        ) {
          if (onSelectAll != null) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.select_all)) },
              onClick = {
                onSelectAll()
                showDropdown = false
              },
            )
          }
          if (onInvertSelection != null) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.invert_selection)) },
              onClick = {
                onInvertSelection()
                showDropdown = false
              },
            )
          }
          if (onDeselectAll != null) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.deselect_all)) },
              onClick = {
                onDeselectAll()
                showDropdown = false
              },
            )
          }
        }
      }
    },
    navigationIcon = {
      IconButton(
        onClick = onCancel,
        modifier = Modifier.padding(horizontal = 2.dp),
      ) {
        Icon(
          Icons.Filled.Close,
          contentDescription = stringResource(R.string.generic_cancel),
          modifier = Modifier.size(28.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    },
    actions = {
      // 1. Play Button (Moved to the extreme left of actions)
      if (onPlay != null) {
        IconButton(onClick = onPlay) {
          Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      // 2. Move Up
      if (onMoveUpClick != null) {
        IconButton(
          onClick = onMoveUpClick,
          enabled = isMoveUpEnabled
        ) {
          Icon(
            Icons.Filled.ArrowUpward,
            contentDescription = "Move Up",
            modifier = Modifier.size(24.dp),
            tint = if (isMoveUpEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          )
        }
      }

      // 3. Move Down
      if (onMoveDownClick != null) {
        IconButton(
          onClick = onMoveDownClick,
          enabled = isMoveDownEnabled
        ) {
          Icon(
            Icons.Filled.ArrowDownward,
            contentDescription = "Move Down",
            modifier = Modifier.size(24.dp),
            tint = if (isMoveDownEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          )
        }
      }
      
      // 4. Pin / Unpin
      if (onPinClick != null) {
        IconButton(
          onClick = onPinClick,
          enabled = isPinEnabled
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Filled.PushPin,
              contentDescription = if (isPinned) "Unpin folder" else "Pin folder",
              modifier = Modifier.size(24.dp),
              tint = if (!isPinEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                     else if (isPinned) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.secondary,
            )
            if (isPinned && isPinEnabled) {
              val slashColor = MaterialTheme.colorScheme.error
              Canvas(modifier = Modifier.size(24.dp)) {
                drawLine(
                  color = slashColor,
                  start = Offset(size.width * 0.15f, size.height * 0.85f),
                  end = Offset(size.width * 0.85f, size.height * 0.15f),
                  strokeWidth = 2.5.dp.toPx()
                )
              }
            }
          }
        }
      }

      // 5. Rename
      if (onRename != null) {
        IconButton(
          onClick = onRename,
          enabled = isSingleSelection,
        ) {
          Icon(
            Icons.Filled.DriveFileRenameOutline,
            contentDescription = stringResource(R.string.rename),
            modifier = Modifier.size(24.dp),
            tint = if (isSingleSelection) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          )
        }
      }

      // 6. Overflow Menu (3-dot) for remaining actions
      if (onShare != null || onBlacklist != null || onInfo != null || onDelete != null || onAddToPlaylistClick != null) {
        IconButton(onClick = { showOverflow = true }) {
          Icon(
            Icons.Filled.MoreVert,
            contentDescription = "More options",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary
          )
          
          DropdownMenu(
            expanded = showOverflow,
            onDismissRequest = { showOverflow = false }
          ) {
            // Add to Playlist
            if (onAddToPlaylistClick != null) {
              DropdownMenuItem(
                text = { Text("Add to playlist") },
                onClick = {
                  showOverflow = false
                  onAddToPlaylistClick()
                },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) }
              )
            }

            // Info
            if (onInfo != null && isSingleSelection) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.info)) },
                onClick = {
                  showOverflow = false
                  onInfo()
                },
                leadingIcon = { Icon(Icons.Filled.Info, null) }
              )
            }

            // Share
            if (onShare != null) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.generic_share)) },
                onClick = {
                  showOverflow = false
                  onShare()
                },
                leadingIcon = { Icon(Icons.Filled.Share, null) }
              )
            }

            // Blacklist
            if (onBlacklist != null) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.pref_folders_blacklist)) },
                onClick = {
                  showOverflow = false
                  onBlacklist()
                },
                leadingIcon = { Icon(Icons.Filled.Block, null) }
              )
            }

            // Delete (with red error color)
            if (onDelete != null) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                  showOverflow = false
                  onDelete()
                },
                leadingIcon = { 
                  Icon(
                    imageVector = if (useRemoveIcon) Icons.Filled.RemoveCircle else Icons.Filled.Delete, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                  ) 
                }
              )
            }
          }
        }
      }
    },
    modifier = modifier.clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
  )
}
