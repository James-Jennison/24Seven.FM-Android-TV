package com.codeframe78.twentyfourseven.player.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Browse the paired station's catalog and pick a track to propose to the phone. Every step works with the D-pad. */
@Composable
internal fun CatalogScreen(browser: CatalogBrowser, onPick: (CatalogTrack) -> Unit, onExit: () -> Unit) {
    val ui by browser.ui.collectAsStateWithLifecycle()
    BackHandler { if (!browser.back()) onExit() }
    Column(Modifier.fillMaxWidth()) {
        when (val state = ui) {
            is CatalogUi.Query -> QueryStep(state, browser, onExit)
            is CatalogUi.Loading -> Text(state.what, style = MaterialTheme.typography.headlineMedium)
            is CatalogUi.Message -> Step(state.text, "Back") { if (!browser.back()) onExit() }
            is CatalogUi.Entries -> ListStep(state.heading, state.items, key = { "${it.target}" }, onBack = { if (!browser.back()) onExit() },
                title = { it.title }, detail = { listOfNotNull(it.subtitle, it.year).joinToString(" · ") }, onClick = browser::open)
            is CatalogUi.Tracks -> ListStep(state.heading, state.tracks, key = { it.songId }, onBack = { if (!browser.back()) onExit() },
                title = { it.title }, detail = { listOfNotNull(it.artist, it.duration, if (it.eligible) null else "Can't request now: ${it.note}").joinToString(" · ") },
                onClick = { if (it.eligible) onPick(it) })
        }
    }
}

@Composable
private fun Step(text: String, label: String, onClick: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(text) { runCatching { focus.requestFocus() } }
    Text(text, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onClick, modifier = Modifier.focusRequester(focus)) { Text(label) }
}

@Composable
private fun QueryStep(state: CatalogUi.Query, browser: CatalogBrowser, onExit: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val input = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { input.requestFocus() } }
    Text("Find a song to request", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CatalogField.entries.forEach { field ->
            Button(onClick = { browser.setField(field) }) { Text(if (field == state.field) "● ${field.label}" else field.label) }
        }
    }
    Spacer(Modifier.height(16.dp))
    BasicTextField(
        value = text,
        onValueChange = { text = it.take(80) },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { browser.search(text) }),
        modifier = Modifier.fillMaxWidth().focusRequester(input)
            .border(2.dp, MaterialTheme.colorScheme.border, RoundedCornerShape(12.dp)).padding(16.dp),
    )
    state.notice?.let { Spacer(Modifier.height(8.dp)); Text(it) }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { browser.search(text) }) { Text("Search") }
        Button(onClick = onExit) { Text("Back") }
    }
}

@Composable
private fun <T> androidx.compose.foundation.layout.ColumnScope.ListStep(
    heading: String,
    items: List<T>,
    key: (T) -> Any,
    onBack: () -> Unit,
    title: (T) -> String,
    detail: (T) -> String,
    onClick: (T) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(heading) { runCatching { first.requestFocus() } }
    Text(heading, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = key) { item ->
            Button(
                onClick = { onClick(item) },
                modifier = Modifier.fillMaxWidth().then(if (item === items.first()) Modifier.focusRequester(first) else Modifier),
            ) {
                Column {
                    Text(title(item), style = MaterialTheme.typography.titleLarge)
                    detail(item).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onBack) { Text("Back") }
}
