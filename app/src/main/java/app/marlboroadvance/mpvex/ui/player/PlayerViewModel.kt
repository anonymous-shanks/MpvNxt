package app.marlboroadvance.mpvex.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import app.marlboroadvance.mpvex.repository.wyzie.WyzieSearchRepository
import app.marlboroadvance.mpvex.repository.wyzie.WyzieSubtitle
import app.marlboroadvance.mpvex.utils.media.MediaInfoParser
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import app.marlboroadvance.mpvex.ui.preferences.CustomButton
import java.io.File
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


enum class RepeatMode {
  OFF,      // No repeat
  ONE,      // Repeat current file
  ALL       // Repeat all (playlist)
}

class PlayerViewModelProviderFactory(
  private val host: PlayerHost,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(
    modelClass: Class<T>,
    extras: CreationExtras,
  ): T {
    if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return PlayerViewModel(host) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}

@Suppress("TooManyFunctions")
class PlayerViewModel(
  private val host: PlayerHost,
) : ViewModel(),
  KoinComponent {
  private val playerPreferences: PlayerPreferences by inject()
  private val appearancePreferences: AppearancePreferences by inject()
  private val gesturePreferences: GesturePreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val json: Json by inject()
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val wyzieRepository: WyzieSearchRepository by inject()

  // Playlist items for the playlist sheet
  private val _playlistItems = kotlinx.coroutines.flow.MutableStateFlow<List<app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem>>(emptyList())
  val playlistItems: kotlinx.coroutines.flow.StateFlow<List<app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem>> = _playlistItems.asStateFlow()

  // Wyzie Search Results
  private val _wyzieSearchResults = MutableStateFlow<List<WyzieSubtitle>>(emptyList())
  val wyzieSearchResults: StateFlow<List<WyzieSubtitle>> = _wyzieSearchResults.asStateFlow()

  private val _isDownloadingSub = MutableStateFlow(false)
  val isDownloadingSub: StateFlow<Boolean> = _isDownloadingSub.asStateFlow()

  private val _isSearchingSub = MutableStateFlow(false)
  val isSearchingSub: StateFlow<Boolean> = _isSearchingSub.asStateFlow()

  private val _isOnlineSectionExpanded = MutableStateFlow(true)
  val isOnlineSectionExpanded: StateFlow<Boolean> = _isOnlineSectionExpanded.asStateFlow()

  // Media Search / Autocomplete
  private val _mediaSearchResults = MutableStateFlow<List<app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult>>(emptyList())
  val mediaSearchResults: StateFlow<List<app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult>> = _mediaSearchResults.asStateFlow()

  private val _isSearchingMedia = MutableStateFlow(false)
  val isSearchingMedia: StateFlow<Boolean> = _isSearchingMedia.asStateFlow()

  // TV Show Details
  private val _selectedTvShow = MutableStateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieTvShowDetails?>(null)
  val selectedTvShow: StateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieTvShowDetails?> = _selectedTvShow.asStateFlow()

  private val _isFetchingTvDetails = MutableStateFlow(false)
  val isFetchingTvDetails: StateFlow<Boolean> = _isFetchingTvDetails.asStateFlow()

  // Season / Episode
  private val _selectedSeason = MutableStateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason?>(null)
  val selectedSeason: StateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason?> = _selectedSeason.asStateFlow()

  private val _seasonEpisodes = MutableStateFlow<List<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode>>(emptyList())
  val seasonEpisodes: StateFlow<List<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode>> = _seasonEpisodes.asStateFlow()

  private val _isFetchingEpisodes = MutableStateFlow(false)
  val isFetchingEpisodes: StateFlow<Boolean> = _isFetchingEpisodes.asStateFlow()

  private val _selectedEpisode = MutableStateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode?>(null)
  val selectedEpisode: StateFlow<app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode?> = _selectedEpisode.asStateFlow()

  fun toggleOnlineSection() {
      _isOnlineSectionExpanded.value = !_isOnlineSectionExpanded.value
  }

  // Cache for video metadata (Duration, Resolution, isNew flag)
  private val metadataCache = object : android.util.LruCache<String, Triple<String, String, Boolean>>(100) {}

  private fun updateMetadataCache(key: String, value: Triple<String, String, Boolean>) {
    metadataCache.put(key, value)
  }

  // MPV properties with efficient collection
  val paused by MPVLib.propBoolean["pause"].collectAsState(viewModelScope)
  val pos by MPVLib.propInt["time-pos"].collectAsState(viewModelScope)
  val duration by MPVLib.propInt["duration"].collectAsState(viewModelScope)

  // High-precision position and duration for smooth seekbar
  private val _precisePosition = MutableStateFlow(0f)
  val precisePosition = _precisePosition.asStateFlow()

  private val _preciseDuration = MutableStateFlow(0f)
  val preciseDuration = _preciseDuration.asStateFlow()

  // Audio state
  val currentVolume = MutableStateFlow(host.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
  private val volumeBoostCap by MPVLib.propInt["volume-max"].collectAsState(viewModelScope)

  init {
    // Poll precise position only when playing
    viewModelScope.launch {
      while (isActive) {
        val time = MPVLib.getPropertyDouble("time-pos")
        if (time != null) {
          _precisePosition.value = time.toFloat()
        }
        delay(16) // ~60fps updates
      }
    }

    // Update precise duration when the integer duration changes (avoid polling)
    viewModelScope.launch {
      MPVLib.propInt["duration"].collect { _ ->
        val dur = MPVLib.getPropertyDouble("duration")
        if (dur != null && dur > 0) {
            _preciseDuration.value = dur.toFloat()
        }
      }
    }
  }
  val maxVolume = host.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

  val subtitleTracks: StateFlow<List<TrackNode>> =
    MPVLib.propNode["track-list"]
      .map { node ->
        node?.toObject<List<TrackNode>>(json)?.filter { it.isSubtitle }?.toImmutableList()
          ?: persistentListOf()
      }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  val audioTracks: StateFlow<List<TrackNode>> =
    MPVLib.propNode["track-list"]
      .map { node ->
        node?.toObject<List<TrackNode>>(json)?.filter { it.isAudio }?.toImmutableList()
          ?: persistentListOf()
      }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  val chapters: StateFlow<List<dev.vivvvek.seeker.Segment>> =
    MPVLib.propNode["chapter-list"]
      .map { node ->
        node?.toObject<List<ChapterNode>>(json)?.map { it.toSegment() }?.toImmutableList()
          ?: persistentListOf()
      }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

  // UI state
  private val _controlsShown = MutableStateFlow(false)
  val controlsShown: StateFlow<Boolean> = _controlsShown.asStateFlow()

  private val _seekBarShown = MutableStateFlow(false)
  val seekBarShown: StateFlow<Boolean> = _seekBarShown.asStateFlow()

  private val _areControlsLocked = MutableStateFlow(false)
  val areControlsLocked: StateFlow<Boolean> = _areControlsLocked.asStateFlow()

  val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
  val isBrightnessSliderShown = MutableStateFlow(false)
  val isVolumeSliderShown = MutableStateFlow(false)
  val volumeSliderTimestamp = MutableStateFlow(0L)
  val brightnessSliderTimestamp = MutableStateFlow(0L)
  val currentBrightness =
    MutableStateFlow(
      runCatching {
        Settings.System
          .getFloat(host.hostContentResolver, Settings.System.SCREEN_BRIGHTNESS)
          .normalize(0f, 255f, 0f, 1f)
      }.getOrElse { 0f },
    )

  val sheetShown = MutableStateFlow(Sheets.None)
  val panelShown = MutableStateFlow(Panels.None)

  // Seek state
  private val _seekText = MutableStateFlow<String?>(null)
  val seekText: StateFlow<String?> = _seekText.asStateFlow()

  private val _doubleTapSeekAmount = MutableStateFlow(0)
  val doubleTapSeekAmount: StateFlow<Int> = _doubleTapSeekAmount.asStateFlow()

  private val _isSeekingForwards = MutableStateFlow(false)
  val isSeekingForwards: StateFlow<Boolean> = _isSeekingForwards.asStateFlow()

  // Frame navigation
  private val _currentFrame = MutableStateFlow(0)
  val currentFrame: StateFlow<Int> = _currentFrame.asStateFlow()

  private val _totalFrames = MutableStateFlow(0)
  val totalFrames: StateFlow<Int> = _totalFrames.asStateFlow()

  private val _isFrameNavigationExpanded = MutableStateFlow(false)
  val isFrameNavigationExpanded: StateFlow<Boolean> = _isFrameNavigationExpanded.asStateFlow()

  private val _isSnapshotLoading = MutableStateFlow(false)
  val isSnapshotLoading: StateFlow<Boolean> = _isSnapshotLoading.asStateFlow()

  // Video zoom
  private val _videoZoom = MutableStateFlow(0f)
  val videoZoom: StateFlow<Float> = _videoZoom.asStateFlow()

  // Timer
  private var timerJob: Job? = null
  private val _remainingTime = MutableStateFlow(0)
  val remainingTime: StateFlow<Int> = _remainingTime.asStateFlow()

  // Media title for subtitle association
  var currentMediaTitle: String = ""
  private var lastAutoSelectedMediaTitle: String? = null

  // External subtitle tracking
  private val _externalSubtitles = mutableListOf<String>()
  val externalSubtitles: List<String> get() = _externalSubtitles.toList()
  
  // Mapping from mpv internal path/URI to the original source URI (resolves deletion issues)
  private val mpvPathToUriMap = mutableMapOf<String, String>()

  // Repeat and Shuffle state
  private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
  val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

  private val _shuffleEnabled = MutableStateFlow(false)
  val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

  // A-B Loop state
  private val _abLoopA = MutableStateFlow<Double?>(null)
  val abLoopA: StateFlow<Double?> = _abLoopA.asStateFlow()

  private val _abLoopB = MutableStateFlow<Double?>(null)
  val abLoopB: StateFlow<Double?> = _abLoopB.asStateFlow()

  private val _isABLoopExpanded = MutableStateFlow(false)
  val isABLoopExpanded: StateFlow<Boolean> = _isABLoopExpanded.asStateFlow()

  // Mirroring state
  private val _isMirrored = MutableStateFlow(false)
  val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

  // Vertical flip state
  private val _isVerticalFlipped = MutableStateFlow(false)
  val isVerticalFlipped: StateFlow<Boolean> = _isVerticalFlipped.asStateFlow()

  init {
    // Restore repeat mode and shuffle state from preferences
    _repeatMode.value = playerPreferences.repeatMode.get()
    _shuffleEnabled.value = playerPreferences.shuffleEnabled.get()

    // Observe volume boost cap changes
    viewModelScope.launch {
      audioPreferences.volumeBoostCap.changes().collect { cap ->
        val maxVol = 100 + cap
        MPVLib.setPropertyString("volume-max", maxVol.toString())
        
        val currentMpvVol = MPVLib.getPropertyInt("volume") ?: 100
        if (currentMpvVol > maxVol) {
          MPVLib.setPropertyInt("volume", maxVol)
        }
      }
    }

    // Monitor duration and AB loop changes
    viewModelScope.launch {
      combine(
        MPVLib.propInt["duration"],
        abLoopA,
        abLoopB
      ) { duration, loopA, loopB ->
        Triple(duration, loopA, loopB)
      }.collect { (duration, loopA, loopB) ->
        val videoDuration = duration ?: 0
        val isLoopActive = loopA != null || loopB != null
        val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || videoDuration < 120 || isLoopActive
        
        MPVLib.setPropertyString("hr-seek", if (shouldUsePreciseSeeking) "yes" else "no")
        MPVLib.setPropertyString("hr-seek-framedrop", if (shouldUsePreciseSeeking) "no" else "yes")
      }
    }
    
    // Refresh custom buttons
    viewModelScope.launch {
      combine(
        advancedPreferences.enableLuaScripts.changes().drop(1),
        playerPreferences.customButtons.changes().drop(1)
      ) { _, _ -> }.collect {
        setupCustomButtons()
      }
    }

    setupCustomButtons()
  }

  // ==================== Custom Buttons ====================

  data class CustomButtonState(
    val id: String,
    val label: String,
    val isLeft: Boolean,
  )

  private val _customButtons = MutableStateFlow<List<CustomButtonState>>(emptyList())
  val customButtons: StateFlow<List<CustomButtonState>> = _customButtons.asStateFlow()

  private fun setupCustomButtons() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val buttons = mutableListOf<CustomButtonState>()
        if (!advancedPreferences.enableLuaScripts.get()) {
            _customButtons.value = buttons
            return@launch
        }

        val scriptContent = buildString {
          val jsonString = playerPreferences.customButtons.get()
          if (jsonString.isNotBlank()) {
            try {
               val slotsData = json.decodeFromString<app.marlboroadvance.mpvex.ui.preferences.CustomButtonSlots>(jsonString)
               slotsData.slots.forEachIndexed { index, btn ->
                 if (btn != null) {
                   val safeId = btn.id.replace("-", "_")
                   val isLeft = index < 4 
                   processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, buttons)
                 }
               }
            } catch (e: Exception) {
               try {
                 val customButtonsList = json.decodeFromString<List<app.marlboroadvance.mpvex.ui.preferences.CustomButton>>(jsonString)
                 customButtonsList.forEachIndexed { index, btn ->
                   val safeId = btn.id.replace("-", "_")
                   val isLeft = index < 4 
                   processButton(btn.id, safeId, btn.title, btn.content, btn.longPressContent, btn.onStartup, isLeft, buttons)
                 }
               } catch (e2: Exception) {
                 e2.printStackTrace()
               }
            }
          }
        }

        _customButtons.value = buttons

        if (scriptContent.isNotEmpty()) {
          val scriptsDir = File(host.context.filesDir, "scripts")
          if (!scriptsDir.exists()) scriptsDir.mkdirs()
          
          val file = File(scriptsDir, "custombuttons.lua")
          file.writeText(scriptContent)
          MPVLib.command("load-script", file.absolutePath)
        }
      } catch (e: Exception) {
        android.util.Log.e("PlayerViewModel", "Error setting up custom buttons", e)
      }
    }
  }

  fun callCustomButton(id: String) {
    val safeId = id.replace("-", "_")
    MPVLib.command("script-message", "call_button_$safeId")
  }
  
  fun callCustomButtonLongPress(id: String) {
    val safeId = id.replace("-", "_")
    MPVLib.command("script-message", "call_button_long_$safeId")
  }

  private fun StringBuilder.processButton(
    originalId: String,
    safeId: String,
    label: String,
    command: String,
    longPressCommand: String,
    onStartup: String,
    isLeft: Boolean,
    uiList: MutableList<CustomButtonState>
  ) {
    if (label.isNotBlank()) {
      uiList.add(CustomButtonState(originalId, label, isLeft))
      
      if (onStartup.isNotBlank()) {
          append(onStartup)
          append("\n")
      }

      if (command.isNotBlank()) {
        append(
          """
          function button_${safeId}()
              ${command}
          end
          mp.register_script_message('call_button_${safeId}', button_${safeId})
          """.trimIndent()
        )
        append("\n")
      }
      
      if (longPressCommand.isNotBlank()) {
        append(
          """
          function button_long_${safeId}()
              ${longPressCommand}
          end
          mp.register_script_message('call_button_long_${safeId}', button_long_${safeId})
          """.trimIndent()
        )
        append("\n")
      }
    }
  }

  private val doubleTapToSeekDuration by lazy { gesturePreferences.doubleTapToSeekDuration.get() }
  private val inputMethodManager by lazy {
    host.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  }

  private var pendingSeekOffset: Int = 0
  private var seekCoalesceJob: Job? = null

  private companion object {
    const val TAG = "PlayerViewModel"
    const val SEEK_COALESCE_DELAY_MS = 60L
    val VALID_SUBTITLE_EXTENSIONS =
      setOf("srt", "ass", "ssa", "sub", "idx", "vtt", "sup", "txt", "pgs")
  }

  // ==================== Timer ====================

  fun startTimer(seconds: Int) {
    timerJob?.cancel()
    _remainingTime.value = seconds
    if (seconds < 1) return

    timerJob =
      viewModelScope.launch {
        for (time in seconds downTo 0) {
          _remainingTime.value = time
          delay(1000)
        }
        MPVLib.setPropertyBoolean("pause", true)
        showToast(host.context.getString(R.string.toast_sleep_timer_ended))
      }
  }

  // ==================== Audio/Subtitle Management ====================

  fun addAudio(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val path =
          uri.resolveUri(host.context)
            ?: return@launch withContext(Dispatchers.Main) {
              showToast("Failed to load audio file: Invalid URI")
            }

        MPVLib.command("audio-add", path, "cached")
        withContext(Dispatchers.Main) {
          showToast("Audio track added")
        }
      }.onFailure { e ->
        withContext(Dispatchers.Main) {
          showToast("Failed to load audio: ${e.message}")
        }
      }
    }
  }

  fun addSubtitle(uri: Uri, select: Boolean = true, silent: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      val uriString = uri.toString()
      if (_externalSubtitles.contains(uriString)) return@launch

      runCatching {
        val fileName = getFileNameFromUri(uri) ?: "subtitle.srt"

        if (!isValidSubtitleFile(fileName)) {
          return@launch withContext(Dispatchers.Main) {
            showToast("Invalid subtitle file format")
          }
        }

        if (uri.scheme == "content") {
          try {
            host.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
          } catch (e: SecurityException) { /* Handled */ }
        }

        val mpvPath = uri.resolveUri(host.context) ?: uri.toString()
        val mode = if (select) "select" else "auto"
        mpvPathToUriMap[mpvPath] = uri.toString()
        MPVLib.command("sub-add", mpvPath, mode)
        
        if (!_externalSubtitles.contains(uriString)) _externalSubtitles.add(uriString)

        val displayName = fileName.take(30).let { if (fileName.length > 30) "$it..." else it }
        if (!silent) withContext(Dispatchers.Main) { showToast("$displayName added") }
      }.onFailure {
        if (!silent) withContext(Dispatchers.Main) { showToast("Failed to load subtitle") }
      }
    }
  }

  private fun scanLocalSubtitles(mediaTitle: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val saveFolderUri = subtitlesPreferences.subtitleSaveFolder.get()
      if (saveFolderUri.isBlank()) return@launch
      try {
        val sanitizedTitle = MediaInfoParser.parse(mediaTitle).title
        val parentDir = DocumentFile.fromTreeUri(host.context, Uri.parse(saveFolderUri)) ?: return@launch
        val movieDir = parentDir.findFile(sanitizedTitle) ?: return@launch
        if (movieDir.isDirectory) {
          movieDir.listFiles().forEach { file ->
            if (file.isFile && isValidSubtitleFile(file.name ?: "")) {
              withContext(Dispatchers.Main) { addSubtitle(file.uri, select = false, silent = true) }
            }
          }
        }
      } catch (e: Exception) { Log.e(TAG, "Error scanning local subtitles", e) }
    }
  }

  fun setMediaTitle(mediaTitle: String) {
    if (currentMediaTitle != mediaTitle) {
      currentMediaTitle = mediaTitle
      lastAutoSelectedMediaTitle = null
      _externalSubtitles.clear()
      scanLocalSubtitles(mediaTitle)
    }
  }

  fun removeSubtitle(id: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      val tracks = subtitleTracks.value
      val trackToRemove = tracks.firstOrNull { it.id == id }
      if (trackToRemove?.external == true && trackToRemove.externalFilename != null) {
        val mpvPath = trackToRemove.externalFilename
        val originalUriString = mpvPathToUriMap[mpvPath] ?: mpvPath
        if (wyzieRepository.deleteSubtitleFile(Uri.parse(originalUriString))) {
          _externalSubtitles.remove(originalUriString)
          mpvPathToUriMap.remove(mpvPath)
          withContext(Dispatchers.Main) { showToast("Subtitle deleted") }
        }
      }
      MPVLib.command("sub-remove", id.toString())
    }
  }

  private var mediaSearchJob: Job? = null

  fun searchMedia(query: String) {
    mediaSearchJob?.cancel()
    if (query.isBlank()) { _mediaSearchResults.value = emptyList(); return }
    mediaSearchJob = viewModelScope.launch {
      delay(300)
      _isSearchingMedia.value = true
      wyzieRepository.searchMedia(query).onSuccess { _mediaSearchResults.value = it }
      _isSearchingMedia.value = false
    }
  }

  fun selectMedia(result: app.marlboroadvance.mpvex.repository.wyzie.WyzieTmdbResult) {
    _mediaSearchResults.value = emptyList()
    _wyzieSearchResults.value = emptyList()
    if (result.mediaType == "tv") fetchTvShowDetails(result.id) else searchSubtitles(result.title)
  }

  private fun fetchTvShowDetails(id: Int) {
    viewModelScope.launch {
      _isFetchingTvDetails.value = true
      wyzieRepository.getTvShowDetails(id).onSuccess { details ->
          val validSeasons = details.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }
          _selectedTvShow.value = details.copy(seasons = validSeasons)
          _selectedSeason.value = null
          _seasonEpisodes.value = emptyList()
      }.onFailure { showToast("Failed to load details: ${it.message}") }
      _isFetchingTvDetails.value = false
    }
  }

  fun selectSeason(season: app.marlboroadvance.mpvex.repository.wyzie.WyzieSeason) {
    val tvShowId = _selectedTvShow.value?.id ?: return
    _selectedSeason.value = season
    viewModelScope.launch {
      _isFetchingEpisodes.value = true
      wyzieRepository.getSeasonEpisodes(tvShowId, season.season_number).onSuccess { episodes ->
          val validEpisodes = episodes.filter { it.episode_number > 0 }.sortedBy { it.episode_number }
          _seasonEpisodes.value = validEpisodes
          _selectedEpisode.value = null
      }.onFailure { showToast("Failed to load episodes: ${it.message}") }
      _isFetchingEpisodes.value = false
    }
  }

  fun selectEpisode(episode: app.marlboroadvance.mpvex.repository.wyzie.WyzieEpisode) {
    _selectedEpisode.value = episode
    searchSubtitles(_selectedTvShow.value?.name ?: currentMediaTitle, episode.season_number, episode.episode_number)
  }

  fun clearMediaSelection() {
    _selectedTvShow.value = null; _selectedSeason.value = null; _seasonEpisodes.value = emptyList(); _selectedEpisode.value = null; _mediaSearchResults.value = emptyList()
  }

  fun searchSubtitles(query: String, season: Int? = null, episode: Int? = null) {
     viewModelScope.launch {
         _isSearchingSub.value = true
         wyzieRepository.search(query, season, episode).onSuccess { _wyzieSearchResults.value = it }
             .onFailure { showToast("Search failed: ${it.message}") }
         _isSearchingSub.value = false
     }
  }

  fun downloadSubtitle(subtitle: WyzieSubtitle) {
      viewModelScope.launch {
          _isDownloadingSub.value = true
          wyzieRepository.download(subtitle, currentMediaTitle).onSuccess { addSubtitle(it) }
              .onFailure { showToast("Download failed: ${it.message}") }
          _isDownloadingSub.value = false
      }
  }

  fun toggleSubtitle(id: Int) {
    val primarySid = MPVLib.getPropertyInt("sid") ?: 0
    val secondarySid = MPVLib.getPropertyInt("secondary-sid") ?: 0
    when {
      id == primarySid -> MPVLib.setPropertyString("sid", "no")
      id == secondarySid -> MPVLib.setPropertyString("secondary-sid", "no")
      primarySid <= 0 -> MPVLib.setPropertyInt("sid", id)
      secondarySid <= 0 -> MPVLib.setPropertyInt("secondary-sid", id)
      else -> MPVLib.setPropertyInt("sid", id)
    }
  }

  fun isSubtitleSelected(id: Int): Boolean {
    val pSid = MPVLib.getPropertyInt("sid") ?: 0
    val sSid = MPVLib.getPropertyInt("secondary-sid") ?: 0
    return (id == pSid && pSid > 0) || (id == sSid && sSid > 0)
  }

  private fun getFileNameFromUri(uri: Uri): String? = when (uri.scheme) {
    "content" -> host.context.contentResolver.query(uri, null, null, null, null)?.use {
      val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (idx >= 0 && it.moveToFirst()) it.getString(idx) else null
    }
    else -> uri.lastPathSegment
  }

  private fun isValidSubtitleFile(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in VALID_SUBTITLE_EXTENSIONS

  // ==================== UI/Control ====================

  fun pauseUnpause() {
    viewModelScope.launch(Dispatchers.IO) {
      if (MPVLib.getPropertyBoolean("pause") == true) {
        withContext(Dispatchers.Main) { host.requestAudioFocus() }
        MPVLib.setPropertyBoolean("pause", false)
      } else {
        MPVLib.setPropertyBoolean("pause", true)
        withContext(Dispatchers.Main) { host.abandonAudioFocus() }
      }
    }
  }

  fun showControls() {
    if (sheetShown.value != Sheets.None || panelShown.value != Panels.None) return
    try {
      if (playerPreferences.showSystemStatusBar.get()) host.windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
      if (playerPreferences.showSystemNavigationBar.get()) host.windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
    } catch (e: Exception) { Log.e(TAG, "Bars show error", e) }
    _controlsShown.value = true
  }

  fun hideControls() {
    try {
      if (playerPreferences.showSystemStatusBar.get()) host.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
      if (playerPreferences.showSystemNavigationBar.get()) host.windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    } catch (e: Exception) { Log.e(TAG, "Bars hide error", e) }
    _controlsShown.value = false; _seekBarShown.value = false
  }

  fun seekTo(position: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      val maxDur = MPVLib.getPropertyInt("duration") ?: 0
      val clampedPos = position.coerceIn(0, maxDur)
      seekCoalesceJob?.cancel(); pendingSeekOffset = 0
      val mode = if (playerPreferences.usePreciseSeeking.get() || maxDur < 120) "absolute+exact" else "absolute+keyframes"
      MPVLib.command("seek", clampedPos.toString(), mode)
    }
  }

  // ==================== Brightness/Volume ====================

  fun changeBrightnessTo(brightness: Float) {
    val b = brightness.coerceIn(0f, 1f)
    host.hostWindow.attributes = host.hostWindow.attributes.apply { screenBrightness = b }
    currentBrightness.value = b
    if (playerPreferences.rememberBrightness.get()) playerPreferences.defaultBrightness.set(b)
  }

  fun changeVolumeTo(volume: Int) {
    val v = volume.coerceIn(0..maxVolume)
    host.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
    currentVolume.value = v
  }

  // ==================== Playlist & New Label Logic ====================

  fun getPlaylistData(): List<app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem>? {
    val activity = host as? PlayerActivity ?: return null
    if (activity.playlist.isEmpty()) return null
    val cPos = pos ?: 0; val cDur = duration ?: 0
    val cProg = if (cDur > 0) ((cPos.toFloat() / cDur.toFloat()) * 100f).coerceIn(0f, 100f) else 0f

    return activity.playlist.mapIndexed { index, uri ->
      val title = activity.getPlaylistItemTitle(uri)
      val isPlayingItem = index == activity.playlistIndex
      val (durStr, resStr, isNewC) = synchronized(metadataCache) { metadataCache[uri.toString()] } ?: Triple("", "", false)
      app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem(
        uri = uri, title = title, index = index, isPlaying = isPlayingItem, path = uri.toString(),
        progressPercent = if (isPlayingItem) cProg else 0f, isWatched = isPlayingItem && cProg >= 95f,
        isNew = isNewC, duration = durStr, resolution = resStr
      )
    }
  }

  private suspend fun checkIsNewVideo(uri: Uri): Boolean {
    if (!appearancePreferences.showUnplayedOldVideoLabel.get()) return false
    val thresholdMillis = appearancePreferences.unplayedOldVideoDays.get() * 24L * 60 * 60 * 1000L
    var dateMillis = 0L
    try {
      if (uri.scheme == "file") {
        val f = File(uri.path ?: return false); if (f.exists()) dateMillis = f.lastModified()
      } else if (uri.scheme == "content") {
        host.context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use {
          if (it.moveToFirst()) dateMillis = it.getLong(0) * 1000L
        }
      }
    } catch (e: Exception) { Log.e(TAG, "Date error", e) }

    if (dateMillis == 0L || (System.currentTimeMillis() - dateMillis) > thresholdMillis) return false
    
    // Identifier check logic
    val playbackState = playbackStateRepository.getVideoDataByTitle(uri.toString())
    return playbackState == null || playbackState.lastPosition <= 0
  }

  fun refreshPlaylistItems() {
    viewModelScope.launch(Dispatchers.IO) {
      val items = getPlaylistData() ?: return@launch
      _playlistItems.value = items
      loadPlaylistMetadataAsync(items)
    }
  }

  private fun loadPlaylistMetadataAsync(items: List<app.marlboroadvance.mpvex.ui.player.controls.components.sheets.PlaylistItem>) {
    viewModelScope.launch(Dispatchers.IO) {
      if ((host as? PlayerActivity)?.isCurrentPlaylistM3U() == true) return@launch
      items.chunked(5).forEach { batch ->
        val updates = mutableMapOf<String, Triple<String, String, Boolean>>()
        batch.forEach { item ->
          val key = item.uri.toString()
          if (metadataCache.get(key) == null) {
            val meta = getVideoMetadata(item.uri)
            val isN = checkIsNewVideo(item.uri)
            updateMetadataCache(key, Triple(meta.first, meta.second, isN))
            updates[key] = Triple(meta.first, meta.second, isN)
          }
        }
        if (updates.isNotEmpty()) {
          _playlistItems.value = _playlistItems.value.map { current ->
            val u = updates[current.uri.toString()] ?: return@map current
            current.copy(duration = u.first, resolution = u.second, isNew = u.third)
          }
        }
      }
    }
  }

  private fun getVideoMetadata(uri: Uri): Pair<String, String> {
    if (uri.scheme?.startsWith("http") == true) return "" to ""
    val retriever = android.media.MediaMetadataRetriever()
    return try {
      if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(host.context, uri)
      val dMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
      val dStr = if (dMs != null) {
        val s = dMs.toLong() / 1000; "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
      } else ""
      val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
      val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
      dStr to (if (w != null) "${w}x${h}" else "")
    } catch (e: Exception) { "" to "" } finally { retriever.release() }
  }

  fun showToast(m: String) { Toast.makeText(host.context, m, Toast.LENGTH_SHORT).show() }
}

fun Float.normalize(im: Float, iM: Float, om: Float, oM: Float): Float = (this - im) * (oM - om) / (iM - im) + om
fun <T> Flow<T>.collectAsState(scope: CoroutineScope, initial: T? = null) = object : ReadOnlyProperty<Any?, T?> {
  private var value: T? = initial
  init { scope.launch { collect { value = it } } }
  override fun getValue(thisRef: Any?, property: KProperty<*>) = value
}
