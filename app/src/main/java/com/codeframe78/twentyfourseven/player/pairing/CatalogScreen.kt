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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    clock: RequestClockUi? = null,
    requested: Set<String> = emptySet(),
) {
    val ui by browser.ui.collectAsStateWithLifecycle()
    val back = { if (!browser.back()) onExit() }
    BackHandler { back() }
    PairingBackdrop {
        Box(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 32.dp)) {
            when (val state = ui) {
                is CatalogUi.Query -> QueryStep(station, state, browser, onExit, stationChoices, onStation, clock)
                is CatalogUi.Keys -> KeysStep(state, browser, back)
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
                    items = state.tracks.map { if ("$station:${it.songId}" in requested) it.copy(eligible = false) else it },
                    onBack = back,
                    key = { it.songId.ifBlank { "${it.title}|${it.artist}" } },
                    title = { it.title },
                    subtitle = { listOfNotNull(it.artist, it.duration).joinToString(" · ") },
                    cover = { coverUrl(station, it.albumId) },
                    detail = { track, _ -> TrackDetail(station, track, "$station:${track.songId}" in requested) },
                    onClick = { if (it.eligible) onPick(it) },
                    accent = { if (it.eligible) null else PairingPalette.Red },
                )
            }
        }
    }
}

@Composable
private fun QueryStep(station: String, state: CatalogUi.Query, browser: CatalogBrowser, onExit: () -> Unit, stationChoices: List<String>, onStation: (String) -> Unit, clock: RequestClockUi?) {
    val waits by browser.waits.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val input = remember { FocusRequester() }
    val firstChip = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Start on the Song chip, not in the text field, so the on-screen keyboard stays down until the listener goes to the field.
    LaunchedEffect(Unit) { runCatching { firstChip.requestFocus() } }
    // One screen, no scrolling: header with the request clock, station row, a search panel, the station's own picks.
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Request a song", style = MaterialTheme.typography.headlineLarge)
                Text(stationName(station), style = MaterialTheme.typography.titleLarge, color = PairingPalette.Lavender)
            }
            RequestClockBadge(clock, waits)
        }
        if (stationChoices.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Station", style = MaterialTheme.typography.labelLarge, color = PairingPalette.Muted, modifier = Modifier.width(96.dp))
                stationChoices.forEach { id -> Pill(stationShortName(id), selected = id == station, onClick = { onStation(id) }) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth()
                .background(PairingPalette.Charcoal.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                .border(1.dp, PairingPalette.Violet.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Search by", style = MaterialTheme.typography.labelLarge, color = PairingPalette.Muted, modifier = Modifier.width(96.dp))
                CatalogField.entries.forEach { field ->
                    Pill(
                        field.label,
                        selected = field == state.field,
                        onClick = { browser.setField(field) },
                        modifier = if (field == CatalogField.entries.first()) Modifier.focusRequester(firstChip) else Modifier,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Browse", style = MaterialTheme.typography.labelLarge, color = PairingPalette.Muted, modifier = Modifier.width(96.dp))
                BrowseKind.entries.forEach { kind -> Pill(kind.label, onClick = { browser.openBrowse(kind) }) }
                Pill("Surprise me", onClick = { browser.suggest(SuggestionMode.Random) })
                Pill("Rare find", onClick = { browser.suggest(SuggestionMode.LeastPlayed) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            if (text.isEmpty()) Text("Type a ${state.field.label.lowercase()}, or use the mic on the keyboard", color = PairingPalette.Muted.copy(alpha = 0.7f), style = MaterialTheme.typography.titleLarge)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(input)
                        // A text field can swallow D-pad Down; hand it on explicitly so the buttons and cards below stay reachable.
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                                focusManager.moveFocus(FocusDirection.Down); true
                            } else false
                        }
                        .background(PairingPalette.Night.copy(alpha = 0.8f), RoundedCornerShape(50))
                        .border(2.dp, PairingPalette.Violet.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                )
                Pill("Search", filled = true, onClick = { browser.search(text) })
            }
            state.notice?.let { Text(it, color = PairingPalette.Amber) }
        }
        Spacer(Modifier.height(20.dp))
        Pill("Back", onClick = onExit)
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
                // Two rows can share a target (the same artist listed twice); the index keeps every key unique.
                itemsIndexed(items, key = { index, item -> "$index|${key(item)}" }) { index, item ->
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
private fun TrackDetail(station: String, track: CatalogTrack, requestedFromThisTv: Boolean) {
    CoverArt(coverUrl(station, track.albumId), 300.dp, track.title)
    Spacer(Modifier.height(20.dp))
    Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
    track.artist?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = PairingPalette.Muted, maxLines = 1) }
    track.albumTitle?.let { Text(it, color = PairingPalette.Muted, maxLines = 1) }
    Spacer(Modifier.height(10.dp))
    val status = classifyTrackStatus(track, requestedFromThisTv)
    Chip(status.label, if (status.canRequest) PairingPalette.Green else PairingPalette.Red)
    // The station's own words (when it gave a reason) stay visible under the label.
    if (!status.canRequest && status != TrackStatus.RequestedHere) track.note?.let { Text(it, color = PairingPalette.Muted, maxLines = 3) }
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
            androidx.compose.ui.text.buildAnnotatedString {
                // The marker always takes its space so a selected pill never changes size.
                withStyle(androidx.compose.ui.text.SpanStyle(color = if (selected) PairingPalette.Lavender else Color.Transparent)) { append("● ") }
                append(text)
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
        )
    }
}

/** The station's request clock as a compact status card: ready, or a live countdown; the station decides eligibility. */
@Composable
private fun RequestClockBadge(clock: RequestClockUi?, waits: List<WaitCard>) {
    val value = clock?.clock
    if (value == null) { WaitsOnly(waits); return }
    var now by remember(clock) { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(clock) { while (true) { kotlinx.coroutines.delay(1000); now = android.os.SystemClock.elapsedRealtime() } }
    val passed = ((now - clock.readAtMillis) / 1000).toInt()
    val next = value.secondsUntilNextRequest?.let { (it - passed).coerceAtLeast(0) }
    val queued = value.secondsUntilQueuedRequestPlays?.takeIf { it > 0 }?.let { (it - passed).coerceAtLeast(0) }
    if (next == null && queued == null) return
    Column(
        Modifier.background(PairingPalette.Charcoal.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .border(1.dp, PairingPalette.Violet.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (next != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).background(if (next == 0) PairingPalette.Green else PairingPalette.Amber, androidx.compose.foundation.shape.CircleShape))
                Text("Next request", style = MaterialTheme.typography.labelLarge, color = PairingPalette.Muted)
                Text(
                    if (next == 0) "Ready" else formatWait(next),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (next == 0) PairingPalette.Green else PairingPalette.Amber,
                )
            }
        }
        if (queued != null && queued > 0) {
            Text("Your request plays in about ${formatWait(queued)}", style = MaterialTheme.typography.bodyMedium, color = PairingPalette.Muted)
        }
        WaitsLine(waits)
    }
}

@Composable
private fun WaitsLine(waits: List<WaitCard>) {
    if (waits.isEmpty()) return
    Text(waits.joinToString("  ·  ") { "${it.label.removeSuffix(" Wait")} wait ${it.value}" }, style = MaterialTheme.typography.bodyMedium, color = PairingPalette.Muted)
}

@Composable
private fun WaitsOnly(waits: List<WaitCard>) {
    if (waits.isEmpty()) return
    Column(
        Modifier.background(PairingPalette.Charcoal.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .border(1.dp, PairingPalette.Violet.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) { WaitsLine(waits) }
}

/** Letters (# and A–Z) or years of one Browse menu; picking one lists that part of the library. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun KeysStep(state: CatalogUi.Keys, browser: CatalogBrowser, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(state.kind) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(if (state.kind == BrowseKind.Year) "Browse by year" else "${state.kind.label} A–Z", style = MaterialTheme.typography.displaySmall)
        Text(if (state.kind == BrowseKind.Year) "Pick a year" else "Pick the first letter", style = MaterialTheme.typography.titleLarge, color = PairingPalette.Lavender)
        Spacer(Modifier.height(28.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.keys.forEachIndexed { index, key ->
                Pill(key.first, onClick = { browser.browse(state.kind, key) }, modifier = if (index == 0) Modifier.focusRequester(first) else Modifier)
            }
        }
        Spacer(Modifier.height(28.dp))
        Pill("Back", onClick = onBack)
    }
}

internal fun formatWait(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
