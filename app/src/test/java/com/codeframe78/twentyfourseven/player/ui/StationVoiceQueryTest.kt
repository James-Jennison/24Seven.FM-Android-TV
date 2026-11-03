package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.StationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationVoiceQueryTest {
    @Test fun stationsByTheirNames() {
        assertEquals(StationId("1980s"), stationFromVoiceQuery("1980s.FM"))
        assertEquals(StationId("1980s"), stationFromVoiceQuery("the eighties station"))
        assertEquals(StationId("afm"), stationFromVoiceQuery("Adagio FM"))
        assertEquals(StationId("dfm"), stationFromVoiceQuery("death.fm"))
        assertEquals(StationId("efm"), stationFromVoiceQuery("entranced"))
        assertEquals(StationId("sst"), stationFromVoiceQuery("Streaming Soundtracks"))
        assertEquals(StationId("sst"), stationFromVoiceQuery("SST"))
    }
    @Test fun noStationNamedMeansNoStation() {
        assertNull(stationFromVoiceQuery(null))
        assertNull(stationFromVoiceQuery(""))
        assertNull(stationFromVoiceQuery("24Seven FM"))
        assertNull(stationFromVoiceQuery("some song"))
    }
}
