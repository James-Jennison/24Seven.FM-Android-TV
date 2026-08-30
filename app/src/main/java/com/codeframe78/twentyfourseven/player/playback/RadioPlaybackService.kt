package com.codeframe78.twentyfourseven.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.codeframe78.twentyfourseven.player.MainActivity
import com.codeframe78.twentyfourseven.player.RadioApplication
import com.codeframe78.twentyfourseven.player.domain.NowPlayingDetailsRepository
import com.codeframe78.twentyfourseven.player.domain.NowPlayingPublisher
import com.codeframe78.twentyfourseven.player.domain.NowPlayingState
import com.codeframe78.twentyfourseven.player.domain.StationId
import com.codeframe78.twentyfourseven.player.domain.normalizeTrailingTheArticle
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class RadioPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var stationSwitchCoordinator: StationSwitchCoordinator
    private val nowPlayingPublisher: NowPlayingPublisher by lazy {
        (application as RadioApplication).appContainer.nowPlayingPublisher
    }
    private val nowPlayingDetailsRepository: NowPlayingDetailsRepository by lazy {
        (application as RadioApplication).appContainer.nowPlayingDetailsRepository
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var detailsJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerButtonVisible = false
    private var activeNowPlaying: NowPlayingState? = null
    private var activeIcyTitle: String? = null
    private var activeMediaId: String? = null
    private var isShuttingDown = false
    private val cancelSleepTimerButton by lazy {
        CommandButton.Builder(CommandButton.ICON_MINUS_CIRCLE_FILLED)
            .setSessionCommand(SleepTimerSessionContract.cancelCommand)
            .setDisplayName("Cancel sleep timer")
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
    }
    private val sessionPlayer by lazy {
        object : ForwardingPlayer(player) {
            override fun setMediaItems(mediaItems: List<MediaItem>) {
                stationSwitchCoordinator.setMediaItems(mediaItems)
            }

            override fun setMediaItem(mediaItem: MediaItem) {
                stationSwitchCoordinator.setMediaItem(mediaItem)
            }

            override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
                stationSwitchCoordinator.setMediaItems(mediaItems, resetPosition)
            }

            override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
                stationSwitchCoordinator.setMediaItem(mediaItem, resetPosition)
            }

            override fun setMediaItems(
                mediaItems: List<MediaItem>,
                startIndex: Int,
                startPositionMs: Long,
            ) {
                stationSwitchCoordinator.setMediaItems(mediaItems, startIndex, startPositionMs)
            }

            override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
                stationSwitchCoordinator.setMediaItem(mediaItem, startPositionMs)
            }

            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands()
                .buildUpon()
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }
    }
    private val fallbackListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }
    private val metadataListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            onStationSwitchStarted(mediaItem?.mediaId)
        }

        override fun onMetadata(metadata: Metadata) {
            if (isShuttingDown) return
            val stationId = player.currentMediaItem?.stationId() ?: return
            val nowPlaying = metadata.toNowPlayingState(stationId) ?: return
            if (activeNowPlaying?.stationId == stationId && activeIcyTitle == nowPlaying.displayTitle) return
            detailsJob?.cancel()
            activeMediaId = player.currentMediaItem?.mediaId
            activeNowPlaying = nowPlaying
            activeIcyTitle = nowPlaying.displayTitle
            updateSessionMetadata(nowPlaying)
            nowPlayingPublisher.publish(nowPlaying)
            detailsJob = serviceScope.launch {
                val details = runCatching { nowPlayingDetailsRepository.fetchNowPlaying(stationId) }.getOrNull()
                    ?: return@launch
                if (
                    activeIcyTitle != nowPlaying.displayTitle ||
                    player.currentMediaItem?.stationId() != stationId ||
                    !details.isCurrentDetailFor(nowPlaying)
                ) {
                    return@launch
                }
                activeNowPlaying = details
                updateSessionMetadata(details)
                nowPlayingPublisher.publish(details)
            }
        }
    }
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val access = MediaSessionControllerPolicy.access(controller, packageName)
            if (access == ControllerAccess.Foreign) {
                return MediaSession.ConnectionResult.accept(
                    MediaSessionControllerPolicy.sessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                        access,
                    ),
                    MediaSessionControllerPolicy.playerCommands(session.player.availableCommands, access),
                )
            }
            val base = super.onConnect(session, controller)
            if (!base.isAccepted) return base
            return MediaSession.ConnectionResult.accept(
                MediaSessionControllerPolicy.sessionCommands(base.availableSessionCommands, access),
                MediaSessionControllerPolicy.playerCommands(base.availablePlayerCommands, access),
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ) = if (
            MediaSessionControllerPolicy.mayChangeMedia(
                MediaSessionControllerPolicy.access(controller, packageName),
            )
        ) {
            super.onAddMediaItems(mediaSession, controller, mediaItems)
        } else {
            Futures.immediateFailedFuture(SecurityException("Controller cannot change station media"))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ) = when (customCommand) {
            SleepTimerSessionContract.setCommand -> {
                val access = MediaSessionControllerPolicy.access(controller, packageName)
                if (!MediaSessionControllerPolicy.maySetSleepTimer(access)) {
                    Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                } else {
                    val durationMillis = SleepTimerSessionContract.durationMillis(args)
                    if (durationMillis == null) {
                        Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    } else {
                        startSleepTimer(durationMillis)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
            }

            SleepTimerSessionContract.cancelCommand -> {
                val access = MediaSessionControllerPolicy.access(controller, packageName)
                if (!MediaSessionControllerPolicy.mayCancelSleepTimer(access)) {
                    Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                } else {
                    cancelSleepTimer()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }

            else -> super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlayerInteractionFinished(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommands: Player.Commands,
        ) {
            if (playerCommands.contains(Player.COMMAND_STOP)) cancelSleepTimer()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val visualizationProcessor = AudioVisualizationProcessor(
            (application as RadioApplication).appContainer.audioVisualizationRepository,
        )
        player = ExoPlayer.Builder(this, AudioVisualizationRenderersFactory(this, visualizationProcessor))
            .setLoadControl(LiveIcecastLoadControl.create())
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        stationSwitchCoordinator = StationSwitchCoordinator(
            playlist = object : PlaylistPort {
                override fun setMediaItems(mediaItems: List<MediaItem>) {
                    player.setMediaItems(mediaItems)
                }

                override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
                    player.setMediaItems(mediaItems, resetPosition)
                }

                override fun setMediaItems(
                    mediaItems: List<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long,
                ) {
                    player.setMediaItems(mediaItems, startIndex, startPositionMs)
                }
            },
            onSwitchStarted = ::onStationSwitchStarted,
        )
        player.addListener(fallbackListener)
        player.addListener(metadataListener)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    private fun onStationSwitchStarted(incomingMediaId: String?) {
        if (isShuttingDown) return
        if (!shouldClearNowPlaying(activeMediaId, incomingMediaId)) return
        detailsJob?.cancel()
        activeNowPlaying = null
        activeIcyTitle = null
        activeMediaId = incomingMediaId
        val incomingStationId = incomingMediaId?.stationId()
        // ExoPlayer emits a transient null item while a controller replaces its media list.
        // It is not a user station change, so it must not erase the restored startup snapshot.
        if (incomingStationId == null) return
        // A normal process restart has a public snapshot for the same station. Keep it on
        // screen while the stream connects instead of flashing the generic app artwork.
        if (nowPlayingPublisher.current().stationId != incomingStationId) {
            nowPlayingPublisher.clear(incomingStationId)
        }
    }

    private fun updateSessionMetadata(nowPlaying: NowPlayingState) {
        val index = player.currentMediaItemIndex
        val currentItem = player.currentMediaItem ?: return
        val displayTitle = nowPlaying.displayTitle ?: return
        val artworkUri = nowPlaying.artworkUrl?.let(Uri::parse)
        if (currentItem.mediaMetadata.title == displayTitle && currentItem.mediaMetadata.artworkUri == artworkUri) return
        player.replaceMediaItem(
            index,
            currentItem.buildUpon()
                .setMediaMetadata(
                    currentItem.mediaMetadata.withNowPlayingTitle(displayTitle)
                        .buildUpon()
                        .setArtworkUri(artworkUri)
                        .build(),
                )
                .build(),
        )
    }

    private fun startSleepTimer(durationMillis: Long) {
        val elapsedRealtimeDeadline = SystemClock.elapsedRealtime() + durationMillis
        runSleepTimer(elapsedRealtimeDeadline, durationMillis)
    }

    private fun runSleepTimer(elapsedRealtimeDeadline: Long, initialRemainingMillis: Long) {
        sleepTimerJob?.cancel()
        publishSleepTimer(elapsedRealtimeDeadline, initialRemainingMillis)
        sleepTimerJob = serviceScope.launch {
            while (isActive) {
                val remaining = elapsedRealtimeDeadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    expireSleepTimer()
                    break
                }
                publishSleepTimer(elapsedRealtimeDeadline, remaining)
                delay(minOf(SLEEP_TIMER_UPDATE_INTERVAL_MILLIS, remaining))
            }
        }
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        publishSleepTimer(null, 0L)
    }

    private fun expireSleepTimer() {
        sleepTimerJob = null
        publishSleepTimer(null, 0L)
        player.stop()
        mediaSession.broadcastCustomCommand(SleepTimerSessionContract.expiredCommand, Bundle.EMPTY)
    }

    private fun publishSleepTimer(deadlineElapsedRealtimeMillis: Long?, remainingMillis: Long) {
        val isActive = deadlineElapsedRealtimeMillis != null && remainingMillis > 0L
        mediaSession.setSessionExtras(
            SleepTimerSessionContract.extras(deadlineElapsedRealtimeMillis, remainingMillis),
        )
        if (sleepTimerButtonVisible != isActive) {
            sleepTimerButtonVisible = isActive
            val buttons = if (isActive) listOf(cancelSleepTimerButton) else emptyList()
            mediaSession.setMediaButtonPreferences(buttons)
            mediaSession.setCustomLayout(buttons)
        }
    }

    override fun onDestroy() {
        isShuttingDown = true
        stationSwitchCoordinator.close()
        sleepTimerJob?.cancel()
        player.removeListener(fallbackListener)
        player.removeListener(metadataListener)
        serviceScope.cancel()
        nowPlayingPublisher.clear()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}

private const val SLEEP_TIMER_UPDATE_INTERVAL_MILLIS = 1_000L

private fun MediaItem.stationId(): StationId? = mediaId
    .substringBefore(':')
    .takeIf(String::isNotBlank)
    ?.let(::StationId)

private fun String.stationId(): StationId? = substringBefore(':')
    .takeIf(String::isNotBlank)
    ?.let(::StationId)

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun Metadata.toNowPlayingState(stationId: StationId): NowPlayingState? {
    val title = getFirstEntryOfType(IcyInfo::class.java)
        ?.title
        ?.trim()
        ?.normalizeLegacyIcyPunctuation()
        ?.normalizeIcyArtistSortArticle()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return NowPlayingState(stationId = stationId, displayTitle = title)
}

/**
 * Media3 exposes legacy ICY title bytes as ISO-8859-1 text. Some station catalog entries were authored with
 * Windows-1252 punctuation, whose bytes otherwise become C1 control characters such as U+0092. Map only those
 * defined code points so unrelated Unicode metadata remains untouched.
 */
internal fun String.normalizeLegacyIcyPunctuation(): String = map { character ->
    when (character) {
        '\u0080' -> '€'
        '\u0082' -> '‚'
        '\u0083' -> 'ƒ'
        '\u0084' -> '„'
        '\u0085' -> '…'
        '\u0086' -> '†'
        '\u0087' -> '‡'
        '\u0088' -> 'ˆ'
        '\u0089' -> '‰'
        '\u008A' -> 'Š'
        '\u008B' -> '‹'
        '\u008C' -> 'Œ'
        '\u008E' -> 'Ž'
        '\u0091' -> '‘'
        '\u0092' -> '’'
        '\u0093' -> '“'
        '\u0094' -> '”'
        '\u0095' -> '•'
        '\u0096' -> '–'
        '\u0097' -> '—'
        '\u0098' -> '˜'
        '\u0099' -> '™'
        '\u009A' -> 'š'
        '\u009B' -> '›'
        '\u009C' -> 'œ'
        '\u009E' -> 'ž'
        '\u009F' -> 'Ÿ'
        else -> character
    }
}.joinToString("")

internal fun MediaMetadata.withNowPlayingTitle(displayTitle: String): MediaMetadata {
    val stationName = albumTitle ?: title
    return buildUpon()
        .setTitle(displayTitle)
        .setArtist(stationName)
        .setAlbumTitle(stationName)
        .setSubtitle("24seven.FM")
        .build()
}

internal fun String.normalizeIcyArtistSortArticle(): String {
    val separator = indexOf(" - ")
    if (separator <= 0) return this
    val artist = substring(0, separator).normalizeTrailingTheArticle()
    return artist + substring(separator)
}

internal fun shouldClearNowPlaying(activeMediaId: String?, incomingMediaId: String?): Boolean =
    activeMediaId == null || incomingMediaId == null || activeMediaId != incomingMediaId

/**
 * An ICY title is sometimes shortened by the stream metadata block. A public station response is
 * authoritative only when it identifies the same station and expands (or exactly matches) that
 * currently-live title; otherwise it may describe the preceding track.
 */
internal fun NowPlayingState.isCurrentDetailFor(icyNowPlaying: NowPlayingState): Boolean {
    if (stationId == null || stationId != icyNowPlaying.stationId) return false
    val fullTitle = displayTitle?.trim()?.takeIf(String::isNotBlank) ?: return false
    val icyTitle = icyNowPlaying.displayTitle?.trim()?.takeIf(String::isNotBlank) ?: return false
    return fullTitle.startsWith(icyTitle, ignoreCase = true) || icyTitle.startsWith(fullTitle, ignoreCase = true)
}
