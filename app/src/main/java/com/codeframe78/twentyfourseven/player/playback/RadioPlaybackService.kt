package com.codeframe78.twentyfourseven.player.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class RadioPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val nowPlayingPublisher: NowPlayingPublisher by lazy {
        (application as RadioApplication).appContainer.nowPlayingPublisher
    }
    private val nowPlayingDetailsRepository: NowPlayingDetailsRepository by lazy {
        (application as RadioApplication).appContainer.nowPlayingDetailsRepository
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var detailsJob: Job? = null
    private var sessionArtworkJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerButtonVisible = false
    private var activeNowPlaying: NowPlayingState? = null
    private var activeIcyTitle: String? = null
    private var activeMediaId: String? = null
    private val sleepTimerPreferences by lazy {
        getSharedPreferences(SLEEP_TIMER_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val cancelSleepTimerButton by lazy {
        CommandButton.Builder(CommandButton.ICON_MINUS_CIRCLE_FILLED)
            .setSessionCommand(SleepTimerSessionContract.cancelCommand)
            .setDisplayName("Cancel sleep timer")
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
    }
    private val sessionPlayer by lazy {
        object : ForwardingPlayer(player) {
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
            val incomingMediaId = mediaItem?.mediaId
            if (!shouldClearNowPlaying(activeMediaId, incomingMediaId)) return
            detailsJob?.cancel()
            activeNowPlaying = null
            activeIcyTitle = null
            activeMediaId = incomingMediaId
            val incomingStationId = mediaItem?.stationId()
            // ExoPlayer emits a transient null item while a controller replaces its media list.
            // It is not a user station change, so it must not erase the restored startup snapshot.
            if (incomingStationId == null) return
            // A normal process restart has a public snapshot for the same station. Keep it on
            // screen while the stream connects instead of flashing the generic app artwork.
            if (nowPlayingPublisher.current().stationId != incomingStationId) {
                nowPlayingPublisher.clear(incomingStationId)
            }
        }

        override fun onMetadata(metadata: Metadata) {
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
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
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
        restoreSleepTimer()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    private fun updateSessionMetadata(nowPlaying: NowPlayingState) {
        val index = player.currentMediaItemIndex
        val currentItem = player.currentMediaItem ?: return
        val displayTitle = nowPlaying.displayTitle ?: return
        val artworkUri = nowPlaying.artworkUrl?.let(Uri::parse)
        if (currentItem.mediaMetadata.title != displayTitle || currentItem.mediaMetadata.artworkUri != artworkUri) {
            player.replaceMediaItem(
                index,
                currentItem.buildUpon()
                    .setMediaMetadata(
                        currentItem.mediaMetadata.withNowPlayingTitle(displayTitle)
                            .buildUpon()
                            .setArtworkUri(artworkUri)
                            .setArtworkData(null, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build(),
                    )
                    .build(),
            )
        }
        loadSessionArtwork(artworkUri, player.currentMediaItem?.mediaId)
    }

    /**
     * Some TV launchers render a rich Now Playing card only when the active MediaSession includes
     * compact artwork data. The URI stays available to controllers that can load a larger image.
     */
    private fun loadSessionArtwork(artworkUri: Uri?, mediaId: String?) {
        sessionArtworkJob?.cancel()
        if (artworkUri?.scheme != "https" || mediaId == null) return
        sessionArtworkJob = serviceScope.launch {
            val artworkData = withContext(Dispatchers.IO) { artworkUri.loadSessionArtwork() } ?: return@launch
            val currentItem = player.currentMediaItem ?: return@launch
            if (currentItem.mediaId != mediaId || currentItem.mediaMetadata.artworkUri != artworkUri) return@launch
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentItem.buildUpon()
                    .setMediaMetadata(
                        currentItem.mediaMetadata.buildUpon()
                            .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build(),
                    )
                    .build(),
            )
        }
    }

    private fun startSleepTimer(durationMillis: Long) {
        val elapsedRealtimeDeadline = SystemClock.elapsedRealtime() + durationMillis
        val epochDeadline = System.currentTimeMillis() + durationMillis
        sleepTimerPreferences.edit()
            .putLong(KEY_ELAPSED_REALTIME_DEADLINE, elapsedRealtimeDeadline)
            .putLong(KEY_EPOCH_DEADLINE, epochDeadline)
            .apply()
        runSleepTimer(elapsedRealtimeDeadline, durationMillis)
    }

    private fun restoreSleepTimer() {
        val elapsedDeadline = sleepTimerPreferences.getLong(KEY_ELAPSED_REALTIME_DEADLINE, 0L)
        val epochDeadline = sleepTimerPreferences.getLong(KEY_EPOCH_DEADLINE, 0L)
        if (elapsedDeadline <= 0L || epochDeadline <= 0L) {
            clearPersistedSleepTimer()
            publishSleepTimer(null, 0L)
            return
        }
        val remaining = PersistedSleepTimer(elapsedDeadline, epochDeadline).restoredRemainingMillis(
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            epochMillis = System.currentTimeMillis(),
        )
        when {
            remaining == null -> cancelSleepTimer()
            remaining <= 0L -> expireSleepTimer()
            else -> runSleepTimer(elapsedDeadline, remaining)
        }
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
        clearPersistedSleepTimer()
        publishSleepTimer(null, 0L)
    }

    private fun expireSleepTimer() {
        sleepTimerJob = null
        clearPersistedSleepTimer()
        publishSleepTimer(null, 0L)
        player.stop()
        mediaSession.broadcastCustomCommand(SleepTimerSessionContract.expiredCommand, Bundle.EMPTY)
    }

    private fun clearPersistedSleepTimer() {
        sleepTimerPreferences.edit()
            .remove(KEY_ELAPSED_REALTIME_DEADLINE)
            .remove(KEY_EPOCH_DEADLINE)
            .apply()
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
        sessionArtworkJob?.cancel()
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

internal const val SLEEP_TIMER_PREFERENCES_NAME = "playback_sleep_timer"
private const val KEY_ELAPSED_REALTIME_DEADLINE = "elapsed_realtime_deadline"
private const val KEY_EPOCH_DEADLINE = "epoch_deadline"
private const val SLEEP_TIMER_UPDATE_INTERVAL_MILLIS = 1_000L
private const val MAX_SESSION_ARTWORK_DOWNLOAD_BYTES = 2 * 1024 * 1024
private const val MAX_SESSION_ARTWORK_DECODE_DIMENSION = 1_024
private const val MAX_SESSION_ARTWORK_DIMENSION = 512
private const val SESSION_ARTWORK_JPEG_QUALITY = 85

private fun Uri.loadSessionArtwork(): ByteArray? {
    val connection = (URL(toString()).openConnection() as? HttpURLConnection) ?: return null
    return try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        connection.inputStream.use { input ->
            val source = input.readBounded(MAX_SESSION_ARTWORK_DOWNLOAD_BYTES) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val bitmap = BitmapFactory.decodeByteArray(
                source,
                0,
                source.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sessionArtworkSampleSize(bounds.outWidth, bounds.outHeight)
                },
            ) ?: return null
            val scaled = bitmap.scaleForSessionArtwork()
            try {
                ByteArrayOutputStream().use { output ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, SESSION_ARTWORK_JPEG_QUALITY, output)) return null
                    output.toByteArray()
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > maxBytes) return null
        output.write(buffer, 0, count)
    }
}

private fun Bitmap.scaleForSessionArtwork(): Bitmap {
    val largestDimension = maxOf(width, height)
    if (largestDimension <= MAX_SESSION_ARTWORK_DIMENSION) return this
    val scale = MAX_SESSION_ARTWORK_DIMENSION.toFloat() / largestDimension
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}

private fun sessionArtworkSampleSize(width: Int, height: Int): Int {
    var sampleSize = 1
    var largestDimension = maxOf(width, height)
    while (largestDimension > MAX_SESSION_ARTWORK_DECODE_DIMENSION) {
        sampleSize *= 2
        largestDimension = (largestDimension + 1) / 2
    }
    return sampleSize
}

private fun MediaItem.stationId(): StationId? = mediaId
    .substringBefore(':')
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
        .setDisplayTitle(displayTitle)
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
