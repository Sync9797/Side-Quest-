package com.sidequest.widgetmaker.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sidequest.widgetmaker.spec.SavedDesign

class MainActivity : ComponentActivity() {
    private val viewModel: MakerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MakerTheme { MakerScreen(viewModel) } }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshWidgets()
    }
}

@Composable
fun MakerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private val IDEAS = listOf(
    "A big minimalist clock with the date underneath",
    "Retro terminal: green text on black, time, date and battery",
    "Pastel sunset gradient vibe with a greeting and the time",
    "A battery widget with a glowing progress bar",
    "Cozy night mode: moon icon, time and day of the week",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MakerScreen(viewModel: MakerViewModel) {
    val state by viewModel.state.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Widget Maker") },
                actions = { TextButton(onClick = { showSettings = true }) { Text("Settings") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.hasApiKey) {
                InfoCard(
                    title = "Connect Claude",
                    body = "This app uses Claude to design widgets. Add an Anthropic API key " +
                        "(from console.anthropic.com) in Settings to get started.",
                    action = "Open Settings" to { showSettings = true },
                )
            }

            val draft = state.draft
            if (draft == null) {
                DescribeCard(busy = state.busy, onGenerate = viewModel::generate)
            } else {
                DraftCard(
                    draft = draft,
                    busy = state.busy,
                    onRefine = viewModel::refine,
                    onSave = { viewModel.saveDraft(thenPin = false) },
                    onSaveAndAdd = { viewModel.saveDraft(thenPin = true) },
                    onDiscard = viewModel::discardDraft,
                )
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            if (state.saved.isNotEmpty()) {
                Text("Your widgets", style = MaterialTheme.typography.titleLarge)
                state.saved.forEach { saved ->
                    SavedCard(
                        saved = saved,
                        onAdd = { viewModel.pin(saved) },
                        onEdit = { viewModel.edit(saved) },
                        onDelete = { viewModel.delete(saved) },
                    )
                }
            }

            InfoCard(
                title = "On your lock screen",
                body = "The widget also offers itself to lock screens. On a Samsung phone: long-press " +
                    "the lock screen, tap Widgets, and look for AI Widget Maker. If it isn't listed, " +
                    "your One UI version only allows Samsung's own lock screen widgets for now; " +
                    "it will still work on your home screen.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSettings) {
        SettingsDialog(
            current = viewModel.apiKey,
            onSave = {
                viewModel.setApiKey(it)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun DescribeCard(busy: Boolean, onGenerate: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Describe a widget", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("A dark glassy clock with today's date and my battery…") },
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IDEAS.forEach { idea -> SuggestionChip(onClick = { text = idea }, label = { Text(idea) }) }
            }
            BusyButton("Design it", busy = busy, enabled = text.isNotBlank()) { onGenerate(text) }
        }
    }
}

@Composable
private fun DraftCard(
    draft: Draft,
    busy: Boolean,
    onRefine: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAndAdd: () -> Unit,
    onDiscard: () -> Unit,
) {
    var change by remember(draft) { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf(PreviewSize.Wide) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(draft.design.name, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewSize.entries.forEach { s ->
                    FilterChip(selected = s == size, onClick = { size = s }, label = { Text(s.label) })
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DesignPreview(draft.design, size)
            }
            OutlinedTextField(
                value = change,
                onValueChange = { change = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Change something") },
                placeholder = { Text("Make the clock bigger and the background purple") },
            )
            BusyButton("Change it", busy = busy, enabled = change.isNotBlank()) { onRefine(change) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveAndAdd, enabled = !busy) { Text("Save & add") }
                OutlinedButton(onClick = onSave, enabled = !busy) { Text("Save") }
                TextButton(onClick = onDiscard, enabled = !busy) { Text("Discard") }
            }
        }
    }
}

@Composable
private fun SavedCard(saved: SavedDesign, onAdd: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(saved.design.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DesignPreview(saved.design, PreviewSize.Wide)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text("Add to home screen") }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${saved.design.name}”?") },
            text = { Text("Widgets showing it will switch to your newest design.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
    }
}

@Composable
private fun BusyButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.widthIn(min = 140.dp)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text("Designing…")
        } else {
            Text(label)
        }
    }
}

@Composable
private fun SettingsDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Anthropic API key. It's stored only on this phone and sent only to Anthropic.")
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    placeholder = { Text("sk-ant-…") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
