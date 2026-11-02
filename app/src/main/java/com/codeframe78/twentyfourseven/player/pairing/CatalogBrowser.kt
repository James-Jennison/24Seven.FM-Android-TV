package com.codeframe78.twentyfourseven.player.pairing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch

internal sealed interface CatalogUi {
    data class Query(val field: CatalogField, val notice: String? = null) : CatalogUi
    data class Loading(val what: String) : CatalogUi
    data class Entries(val heading: String, val items: List<CatalogEntry>) : CatalogUi
    data class Tracks(val heading: String, val tracks: List<CatalogTrack>) : CatalogUi
    /** The search had nothing to show, or the page could not be read. */
    data class Message(val text: String) : CatalogUi
}

/**
 * Search-and-browse flow for one paired station: query, entries (artists or albums), then the album's
 * tracks. Holds only what is on screen; at most one load runs, and a newer action cancels it, so a slow
 * earlier page can never replace a later one. Back walks up the stack and reports false at the root.
 */
internal class CatalogBrowser(
    private val catalog: RequestCatalog,
    private val station: String,
    private val scope: CoroutineScope,
) {
    private val stack = ArrayDeque<CatalogUi>().apply { addLast(CatalogUi.Query(CatalogField.Title)) }
    private val _ui = MutableStateFlow<CatalogUi>(stack.last())
    val ui: StateFlow<CatalogUi> = _ui
    private var job: Job? = null

    fun setField(field: CatalogField) {
        val top = stack.last()
        if (top is CatalogUi.Query) replaceTop(top.copy(field = field, notice = null))
    }

    fun search(query: String) {
        val top = stack.last() as? CatalogUi.Query ?: return
        val text = query.trim()
        if (text.length < 2) { replaceTop(top.copy(notice = "Type at least two letters.")); return }
        load("Searching…", empty = "Nothing found for “$text”.") {
            CatalogUi.Entries("${top.field.label}: $text", catalog.search(station, text, top.field))
        }
    }

    fun open(entry: CatalogEntry) = when (val target = entry.target) {
        is CatalogTarget.Album -> load("Loading album…", empty = "That album has no tracks to show.") {
            val album = catalog.album(station, target.albumId)
            CatalogUi.Tracks(album.title ?: entry.title, album.tracks)
        }
        is CatalogTarget.Artist -> load("Loading albums…", empty = "No albums found for ${entry.title}.") {
            CatalogUi.Entries(entry.title, catalog.albumsBy(station, target.name))
        }
    }

    fun suggest(mode: SuggestionMode) = load("Picking a track…", empty = "The station has no suggestion right now.") {
        val album = catalog.suggest(station, mode)
        CatalogUi.Tracks(if (mode == SuggestionMode.Random) "Surprise pick" else "Rarely played pick", album.tracks)
    }

    /** True when it moved up one level; false when already at the first screen. */
    fun back(): Boolean {
        job?.cancel()
        if (stack.size <= 1) return false
        stack.removeLast()
        _ui.value = stack.last()
        return true
    }

    private fun replaceTop(next: CatalogUi) {
        stack.removeLast(); stack.addLast(next); _ui.value = next
    }

    private fun load(what: String, empty: String, block: suspend () -> CatalogUi) {
        job?.cancel()
        _ui.value = CatalogUi.Loading(what)
        job = scope.launch {
            val result = try {
                block().let { if (it.isEmpty()) CatalogUi.Message(empty) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.w("PairingCatalog", "load failed: ${e.javaClass.simpleName}: ${e.message}") }
                CatalogUi.Message("The station's request page could not be read. Check the connection and try again.")
            }
            coroutineContext.ensureActive() // a result whose load was cancelled by Back must not become a stale level
            // Every outcome, including a message, is a level of its own, so Back returns to the screen before it.
            stack.addLast(result)
            _ui.value = result
        }
    }

    private fun CatalogUi.isEmpty() = (this is CatalogUi.Entries && items.isEmpty()) || (this is CatalogUi.Tracks && tracks.isEmpty())
}
