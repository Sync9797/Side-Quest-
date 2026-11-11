package com.sidequest.widgetmaker.ui

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.widgetmaker.ai.WidgetDesigner
import com.sidequest.widgetmaker.data.DesignStore
import com.sidequest.widgetmaker.spec.SavedDesign
import com.sidequest.widgetmaker.spec.WidgetDesign
import com.sidequest.widgetmaker.widget.AiWidget
import com.sidequest.widgetmaker.widget.AiWidgetReceiver
import com.sidequest.widgetmaker.widget.PinnedReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class Draft(
    /** Set when editing a saved design, so saving replaces it. */
    val id: String?,
    val prompt: String,
    val design: WidgetDesign,
)

data class MakerState(
    val draft: Draft? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val saved: List<SavedDesign> = emptyList(),
    val hasApiKey: Boolean = false,
)

class MakerViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DesignStore(app)
    private val _state = MutableStateFlow(MakerState())
    val state: StateFlow<MakerState> = _state

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.all() }
            _state.update { it.copy(saved = saved, hasApiKey = store.apiKey.isNotBlank()) }
        }
    }

    val apiKey: String get() = store.apiKey

    fun setApiKey(key: String) {
        store.apiKey = key
        _state.update { it.copy(hasApiKey = key.isNotBlank(), error = null) }
    }

    /** A new design from a description. */
    fun generate(description: String) = run(description, previous = null)

    /** Changes the current draft as described. */
    fun refine(change: String) {
        val draft = _state.value.draft ?: return
        run(change, previous = draft)
    }

    private fun run(text: String, previous: Draft?) {
        if (text.isBlank() || _state.value.busy) return
        val key = store.apiKey
        if (key.isBlank()) {
            _state.update { it.copy(error = "Add your Anthropic API key in Settings first.") }
            return
        }
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val design = WidgetDesigner(key).design(text, previous?.design)
                val prompt = if (previous == null) text else previous.prompt + "\n→ " + text
                _state.update { it.copy(busy = false, draft = Draft(previous?.id, prompt, design)) }
            } catch (e: WidgetDesigner.DesignFailed) {
                _state.update { it.copy(busy = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "Something went wrong: ${e.message}") }
            }
        }
    }

    fun edit(saved: SavedDesign) {
        _state.update { it.copy(draft = Draft(saved.id, saved.prompt, saved.design), error = null, message = null) }
    }

    fun discardDraft() = _state.update { it.copy(draft = null, error = null) }

    fun dismissMessage() = _state.update { it.copy(message = null, error = null) }

    /** Saves the draft (replacing the design it was opened from) and redraws placed widgets. */
    fun saveDraft(thenPin: Boolean) {
        val draft = _state.value.draft ?: return
        val saved = SavedDesign(
            id = draft.id ?: UUID.randomUUID().toString(),
            prompt = draft.prompt,
            design = draft.design,
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.save(saved) }
            AiWidget.refreshAll(getApplication())
            _state.update { it.copy(draft = null, message = "Saved “${saved.design.name}”.") }
            reload()
            if (thenPin) pin(saved)
        }
    }

    fun delete(saved: SavedDesign) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(saved.id) }
            AiWidget.refreshAll(getApplication())
            reload()
        }
    }

    /** Asks the launcher to add a widget showing [saved] (Android 8+, most launchers including Samsung's). */
    fun pin(saved: SavedDesign) {
        val app = getApplication<Application>()
        val manager = AppWidgetManager.getInstance(app)
        if (!manager.isRequestPinAppWidgetSupported) {
            _state.update {
                it.copy(message = "Your launcher can't add widgets from apps. Long-press the home screen, tap Widgets, and find AI Widget Maker.")
            }
            return
        }
        val callback = PendingIntent.getBroadcast(
            app,
            saved.id.hashCode(),
            Intent(app, PinnedReceiver::class.java).putExtra(PinnedReceiver.EXTRA_DESIGN_ID, saved.id),
            // Mutable: the launcher adds the new widget's id to this intent.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        manager.requestPinAppWidget(ComponentName(app, AiWidgetReceiver::class.java), null, callback)
    }

    /** Called when the app comes to the front: redraw widgets so dates and battery are fresh. */
    fun refreshWidgets() {
        viewModelScope.launch { AiWidget.refreshAll(getApplication()) }
    }
}
