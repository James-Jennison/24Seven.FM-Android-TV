package com.codeframe78.twentyfourseven.player.playback

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StationSwitchCoordinatorTest {
    @Test
    fun `starts the metadata barrier before replacing a playlist`() {
        val events = mutableListOf<String>()
        val coordinator = StationSwitchCoordinator(
            playlist = RecordingPlaylistPort(events),
            onSwitchStarted = { mediaId -> events += "barrier:$mediaId" },
        )

        coordinator.setMediaItems(listOf(mediaItem("adagio:0")), 0, 0L)

        assertEquals(listOf("barrier:adagio:0", "replace:adagio:0:0:0"), events)
    }

    @Test
    fun `uses the requested start item for the metadata barrier`() {
        val events = mutableListOf<String>()
        val coordinator = StationSwitchCoordinator(
            playlist = RecordingPlaylistPort(events),
            onSwitchStarted = { mediaId -> events += "barrier:$mediaId" },
        )

        coordinator.setMediaItems(listOf(mediaItem("adagio:0"), mediaItem("adagio:1")), 1, 0L)

        assertEquals(listOf("barrier:adagio:1", "replace:adagio:0:1:0"), events)
    }

    @Test
    fun `routes singular media replacement through the metadata barrier`() {
        val events = mutableListOf<String>()
        val coordinator = StationSwitchCoordinator(
            playlist = RecordingPlaylistPort(events),
            onSwitchStarted = { mediaId -> events += "barrier:$mediaId" },
        )

        coordinator.setMediaItem(mediaItem("death:0"))

        assertEquals(listOf("barrier:death:0", "replace:death:0"), events)
    }

    @Test
    fun `does not forward requests after service teardown`() {
        val events = mutableListOf<String>()
        val coordinator = StationSwitchCoordinator(
            playlist = RecordingPlaylistPort(events),
            onSwitchStarted = { mediaId -> events += "barrier:$mediaId" },
        )
        coordinator.close()

        coordinator.setMediaItems(listOf(mediaItem("adagio:0")))

        assertEquals(emptyList<String>(), events)
    }

    private fun mediaItem(mediaId: String) = MediaItem.Builder().setMediaId(mediaId).build()
}

private class RecordingPlaylistPort(
    private val events: MutableList<String>,
) : PlaylistPort {
    override fun setMediaItems(mediaItems: List<MediaItem>) {
        events += "replace:${mediaItems.firstOrNull()?.mediaId}"
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        events += "replace:${mediaItems.firstOrNull()?.mediaId}:$resetPosition"
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        events += "replace:${mediaItems.firstOrNull()?.mediaId}:$startIndex:$startPositionMs"
    }
}
