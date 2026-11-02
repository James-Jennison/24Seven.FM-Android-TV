package com.codeframe78.twentyfourseven.player.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val CardShape = RoundedCornerShape(18.dp)

/** Browse the station's catalog and pick a track. Every step works with the D-pad. */
@Composable
internal fun CatalogScreen(
    station: String,
    browser: CatalogBrowser,
    onPick: (CatalogTrack) -> Unit,
    onExit: () -> Unit,
    stationChoices: List<String> = emptyList(),
    onStation: (String) -> Unit = {},
) {
    val ui by browser.ui.collectAsStateWithLifecycle()
    val back = { if (!browser.back()) onExit() }
    BackHandler { back() }
    PairingBackdrop {
        Box(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 32.dp)) {
            when (val state = ui) {
                is CatalogUi.Query -> QueryStep(station, state, browser, onExit, stationChoices, onStation)
                is CatalogUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.what, style = MaterialTheme.typography.headlineMedium, color = PairingPalette.Lavender)
                }
                is CatalogUi.Message -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(state.text, style = MaterialTheme.typography.headlineSmall)
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(state) { runCatching { focus.requestFocus() } }
                        Pill("Back", onClick = back, modifier = Modifier.focusRequester(focus))
                    }
                }
                is CatalogUi.Entries -> BrowseStep(
                    heading = state.heading,
                    items = state.items,
                    onBack = back,
                    key = { "${it.target}" },
                    title = { it.title },
                    subtitle = { listOfNotNull(it.subtitle, it.year).joinToString(" · ") },
                    cover = { (it.target as? CatalogTarget.Album)?.let { t -> coverUrl(station, t.albumId) } },
                    detail = { entry, _ -> EntryDetail(station, entry) },
                    onClick = browser::open,
                )
                is CatalogUi.Tracks -> BrowseStep(
                    heading = state.heading,
                    items = state.tracks,
                    onBack = back,
                    key = { it.songId.ifBlank { "${it.title}|${it.artist}" } },
                    title = { it.title },
                    subtitle = { listOfNotNull(it.artist, it.duration).joinToString(" · ") },
                    cover = { coverUrl(station, it.albumId) },
                    detail = { track, _ -> TrackDetail(station, track) },
                    onClick = { if (it.eligible) onPick(it) },
                    accent = { if (it.eligible) null else PairingPalette.Red },
                )
            }
        }
    }
}

@Composable
private fun QueryStep(station: String, state: CatalogUi.Query, browser: CatalogBrowser, onExit: () -> Unit, stationChoices: List<String>, onStation: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val input = remember { FocusRequester() }
    val firstChip = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Start on the Song chip, not in the text field, so the on-screen keyboard stays down until the listener goes to the field.
    LaunchedEffect(Unit) { runCatching { firstChip.requestFocus() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Find a song", style = MaterialTheme.typography.displaySmall)
        Text("Request a track on ${stationName(station)}", style = MaterialTheme.typography.titleLarge, color = PairingPalette.Muted)
        if (stationChoices.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                stationChoices.forEach { id -> Pill(stationName(id), selected = id == station, onClick = { onStation(id) }) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CatalogField.entries.forEach { field ->
                Pill(
                    field.label,
                    selected = field == state.field,
                    onClick = { browser.setField(field) },
                    modifier = if (field == CatalogField.entries.first()) Modifier.focusRequester(firstChip) else Modifier,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(80) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = PairingPalette.WarmWhite),
            cursorBrush = SolidColor(PairingPalette.Lavender),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { browser.search(text) }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Type ${state.field.label.lowercase()} name, or use the mic on the keyboard", color = PairingPalette.Muted.copy(alpha = 0.7f), style = MaterialTheme.typography.titleLarge)
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(input)
                // A text field can swallow D-pad Down; hand it on explicitly so the buttons and cards below stay reachable.
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                        focusManager.moveFocus(FocusDirection.Down); true
                    } else false
                }
                .background(PairingPalette.Charcoal, RoundedCornerShape(50))
                .border(2.dp, PairingPalette.Violet.copy(alpha = 0.6f), RoundedCornerShape(50))
                .padding(horizontal = 28.dp, vertical = 18.dp),
        )
        state.notice?.let { Spacer(Modifier.height(10.dp)); Text(it, color = PairingPalette.Amber) }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("Search", filled = true, onClick = { browser.search(text) })
            Pill("Back", onClick = onExit)
        }
        Spacer(Modifier.height(36.dp))
        Text("Or let the station pick", style = MaterialTheme.typography.titleMedium, color = PairingPalette.Muted)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IdeaCard("Surprise me", "A random track from the library", Modifier.weight(1f)) { browser.suggest(SuggestionMode.Random) }
            IdeaCard("Hidden gem", "Something that rarely gets played", Modifier.weight(1f)) { browser.suggest(SuggestionMode.LeastPlayed) }
        }
    }
}

/** Left: the list. Right: artwork and details for whatever has focus, so the choice is visible, not just read. */
@Composable
private fun <T> BrowseStep(
    heading: String,
    items: List<T>,
    onBack: () -> Unit,
    key: (T) -> Any,
    title: (T) -> String,
    subtitle: (T) -> String,
    cover: (T) -> String?,
    detail: @Composable (T, Int) -> Unit,
    onClick: (T) -> Unit,
    accent: (T) -> Color? = { null },
) {
    var focused by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(heading) { runCatching { first.requestFocus() } }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1.25f).fillMaxHeight()) {
            Text(heading, style = MaterialTheme.typography.headlineLarge, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                    ResultCard(
                        title = title(item),
                        subtitle = subtitle(item),
                        cover = cover(item),
                        accent = accent(item),
                        modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { if (it.isFocused) focused = index },
                        onClick = { onClick(item) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Pill("Back", onClick = onBack)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            items.getOrNull(focused)?.let { detail(it, focused) }
        }
    }
}

@Composable
private fun EntryDetail(station: String, entry: CatalogEntry) {
    val art = (entry.target as? CatalogTarget.Album)?.let { coverUrl(station, it.albumId) }
    CoverArt(art, 300.dp, entry.title)
    Spacer(Modifier.height(20.dp))
    Text(entry.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
    entry.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = PairingPalette.Muted, maxLines = 2) }
    entry.year?.let { Text(it, color = PairingPalette.Muted) }
    Spacer(Modifier.height(10.dp))
    Chip(if (entry.target is CatalogTarget.Artist) "Artist · see albums" else "Album · see tracks", PairingPalette.Lavender)
}

@Composable
private fun TrackDetail(station: String, track: CatalogTrack) {
    CoverArt(coverUrl(station, track.albumId), 300.dp, track.title)
    Spacer(Modifier.height(20.dp))
    Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
    track.artist?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = PairingPalette.Muted, maxLines = 1) }
    track.albumTitle?.let { Text(it, color = PairingPalette.Muted, maxLines = 1) }
    Spacer(Modifier.height(10.dp))
    if (track.eligible) Chip("Available to request", PairingPalette.Green)
    else Chip(track.note ?: "Not available right now", PairingPalette.Red)
}

@Composable
private fun ResultCard(
    title: String,
    subtitle: String,
    cover: String?,
    accent: Color?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PairingPalette.Charcoal,
            contentColor = PairingPalette.WarmWhite,
            focusedContainerColor = PairingPalette.Violet,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverArt(cover, 64.dp, title)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = LocalSubtitle())
            }
            if (accent != null) Box(Modifier.width(10.dp).height(10.dp).background(accent, RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun LocalSubtitle(): Color = PairingPalette.Muted

@Composable
internal fun IdeaCard(title: String, caption: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PairingPalette.Charcoal,
            contentColor = PairingPalette.WarmWhite,
            focusedContainerColor = PairingPalette.Violet,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(caption, style = MaterialTheme.typography.bodyLarge, color = PairingPalette.Muted)
        }
    }
}

/** A rounded pill button: outlined by default, filled for the primary action, tinted when selected. */
@Composable
internal fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when { filled -> PairingPalette.Violet; selected -> PairingPalette.Lavender.copy(alpha = 0.28f); else -> PairingPalette.Charcoal },
            contentColor = PairingPalette.WarmWhite,
            focusedContainerColor = PairingPalette.Lavender,
            focusedContentColor = Color(0xFF1C1424),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(
            (if (selected) "● " else "") + text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
        )
    }
}
