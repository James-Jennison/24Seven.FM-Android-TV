package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.MusicReactiveState
import com.codeframe78.twentyfourseven.player.domain.ReactiveStatus
import com.codeframe78.twentyfourseven.player.ui.visualization.EmeraldMusicMapper
import com.codeframe78.twentyfourseven.player.ui.visualization.NeonMusicMapper
import org.junit.Assert.*
import org.junit.Test

class MusicAudioMapperTest {
    private val music = MusicReactiveState(epoch = 1, sequence = 3, measuredAtElapsedRealtimeMs = 1000,
        status = ReactiveStatus.Active, amplitude = .6f, energy = .7f, bass = .8f, mid = .4f, treble = .2f, onset = .5f)

    @Test fun sameSignalsProduceDistinctVideoAndGeometryControls() {
        val neon = NeonMusicMapper.map(music, 1010, false)
        val video = EmeraldMusicMapper.map(music, 1010, false)
        assertEquals(.8f, neon.bass, 0f)
        assertEquals(.5f, neon.impact, 0f)
        assertTrue(video.scaleX > video.scaleY && video.shear > 0f && video.moving)
        assertTrue(EmeraldMusicMapper.map(music.copy(bass = 0f), 1010, false).scaleX < video.scaleX)
        assertTrue(EmeraldMusicMapper.map(music.copy(mid = 0f), 1010, false).shear < video.shear)
        assertTrue(EmeraldMusicMapper.map(music, 1010, true).scaleX < video.scaleX)
        assertTrue(NeonMusicMapper.map(music, 1010, true).impact < neon.impact)
    }

    @Test fun unavailableFutureAndStaleStatesCannotDriveEitherRenderer() {
        listOf(music.copy(status = ReactiveStatus.Unavailable), music.copy(measuredAtElapsedRealtimeMs = 2000),
            music.copy(measuredAtElapsedRealtimeMs = 1)).forEach {
            assertEquals(0f, NeonMusicMapper.map(it, 1010, false).energy, 0f)
            assertFalse(EmeraldMusicMapper.map(it, 1010, false).moving)
        }
        assertFalse(EmeraldMusicMapper.map(music.copy(status = ReactiveStatus.Silence), 1010, false).moving)
    }

    @Test fun invalidControlsRemainFiniteAndBounded() {
        val bad = music.copy(amplitude = Float.NaN, energy = Float.POSITIVE_INFINITY,
            bass = -1f, mid = 10f, treble = Float.NaN, onset = Float.NaN)
        val video = EmeraldMusicMapper.map(bad, 1010, false)
        listOf(video.scaleX, video.scaleY, video.alpha, video.shear, video.speed).forEach { assertTrue(it.isFinite()) }
        assertEquals(0f, NeonMusicMapper.map(bad, 1010, false).impact, 0f)
    }
}
