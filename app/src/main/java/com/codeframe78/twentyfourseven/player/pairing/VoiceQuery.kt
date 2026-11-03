package com.codeframe78.twentyfourseven.player.pairing

/** What a spoken phrase asks for: a field to search, the text to search for, and an artist to rank or narrow results by. */
internal data class VoiceQuery(val field: CatalogField, val text: String, val artist: String? = null)

/**
 * Turns what the recognizer heard into a search. "Peter Gunn by Henry Mancini" searches the song and
 * ranks the results by the artist; "artist Hans Zimmer", "album Godzilla" and "genre jazz" pick the field;
 * a leading "search for", "find", "request" or "play" is ignored. Anything else is a song search.
 */
internal fun parseVoiceQuery(heard: String): VoiceQuery? {
    var text = heard.trim().replace(Regex("\\s+"), " ")
    text = text.replace(Regex("^(?:please\\s+)?(?:search(?:\\s+for)?|find|look\\s+for|request|play)\\s+", RegexOption.IGNORE_CASE), "")
    var field = CatalogField.Title
    Regex("^(song|track|title|artist|album|genre)\\s+(?:called\\s+|named\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(text)?.let {
        field = when (it.groupValues[1].lowercase()) {
            "artist" -> CatalogField.Artist
            "album" -> CatalogField.Album
            "genre" -> CatalogField.Genre
            else -> CatalogField.Title
        }
        text = it.groupValues[2]
    }
    var artist: String? = null
    if (field == CatalogField.Title) {
        Regex("^(.+?)\\s+by\\s+(.+)$", RegexOption.IGNORE_CASE).find(text)?.let {
            text = it.groupValues[1]
            artist = it.groupValues[2].trim().takeIf(String::isNotEmpty)
        }
    }
    text = text.trim().trim('.', ',', '"', '“', '”').take(80)
    return if (text.length >= 2) VoiceQuery(field, text, artist) else null
}

/** Entries mentioning the artist first (keeping the station's order within each group). */
internal fun rankByArtist(entries: List<CatalogEntry>, artist: String?): List<CatalogEntry> {
    val wanted = artist?.lowercase()?.takeIf { it.isNotBlank() } ?: return entries
    fun mentions(e: CatalogEntry) = e.title.lowercase().contains(wanted) || e.subtitle?.lowercase()?.contains(wanted) == true
    return entries.filter(::mentions) + entries.filterNot(::mentions)
}
