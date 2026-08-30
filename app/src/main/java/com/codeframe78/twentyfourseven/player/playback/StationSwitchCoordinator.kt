package com.codeframe78.twentyfourseven.player.playback

import androidx.media3.common.MediaItem

/**
 * Serializes playlist replacement requested through the MediaSession.
 *
 * This coordinator is intentionally owned by [RadioPlaybackService], rather than a UI controller.
 * Every request runs on Media3's application looper, clears metadata work for the outgoing station
 * before its replacement reaches the player, and becomes inert during service teardown.
 */
internal class StationSwitchCoordinator(
    private val playlist: PlaylistPort,
    private val onSwitchStarted: (String?) -> Unit,
) : AutoCloseable {
    private var closed = false

    fun setMediaItems(mediaItems: List<MediaItem>) {
        begin(mediaItems.firstOrNull()?.mediaId)
        if (!closed) playlist.setMediaItems(mediaItems)
    }

    fun setMediaItem(mediaItem: MediaItem) = setMediaItems(listOf(mediaItem))

    fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        begin(mediaItems.firstOrNull()?.mediaId)
        if (!closed) playlist.setMediaItems(mediaItems, resetPosition)
    }

    fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) =
        setMediaItems(listOf(mediaItem), resetPosition)

    fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        begin(mediaItems.getOrNull(startIndex)?.mediaId)
        if (!closed) playlist.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) =
        setMediaItems(listOf(mediaItem), 0, startPositionMs)

    private fun begin(incomingMediaId: String?) {
        if (!closed) onSwitchStarted(incomingMediaId)
    }

    override fun close() {
        closed = true
    }
}

internal interface PlaylistPort {
    fun setMediaItems(mediaItems: List<MediaItem>)

    fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean)

    fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long)
}
