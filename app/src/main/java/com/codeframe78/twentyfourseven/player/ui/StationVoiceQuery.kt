package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.StationId
import java.util.Locale

/**
 * Maps what a listener said to a station for "play ... on 24Seven.FM" (Android's MEDIA_PLAY_FROM_SEARCH).
 * Null means no station was named; the caller then resumes the current station instead of guessing one.
 */
internal fun stationFromVoiceQuery(query: String?): StationId? {
    val text = query?.lowercase(Locale.ROOT)?.replace(Regex("[^a-z0-9 ]"), " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    if (text.isEmpty()) return null
    val words = " $text "
    fun has(vararg phrases: String) = phrases.any { words.contains(" $it ") }
    return when {
        has("1980s", "1980", "80s", "eighties", "nineteen eighties", "nineteen eighty", "1980s fm") -> StationId("1980s")
        has("adagio", "adagio fm") -> StationId("afm")
        has("death", "death fm", "death dot fm") -> StationId("dfm")
        has("entranced", "entranced fm") -> StationId("efm")
        has("sst", "streaming soundtracks", "streamingsoundtracks", "soundtracks", "streaming soundtracks com") -> StationId("sst")
        else -> null
    }
}
