package com.sidequest.widgetmaker.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sidequest.widgetmaker.data.DesignStore
import com.sidequest.widgetmaker.widget.updateWidget
import kotlinx.coroutines.launch

/**
 * Shown when a widget is added from the widget list, and from the widget's
 * settings later: pick which saved design it shows.
 */
class ChooseDesignActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Keep the widget even if nothing is picked: it shows the newest design (or a "tap to design" note).
        setResult(RESULT_OK, result)

        val store = DesignStore(this)
        val designs = store.all()
        setContent {
            MakerTheme {
                Surface {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Pick a design", style = MaterialTheme.typography.titleLarge)
                        if (designs.isEmpty()) {
                            Text("You haven't made one yet. Describe a widget in the app and it will show up here.")
                            Button(onClick = {
                                startActivity(Intent(this@ChooseDesignActivity, MainActivity::class.java))
                                finish()
                            }) { Text("Open AI Widget Maker") }
                        }
                        designs.forEach { saved ->
                            Column(
                                Modifier.clickable {
                                    store.assign(appWidgetId, saved.id)
                                    lifecycleScope.launch {
                                        updateWidget(this@ChooseDesignActivity, appWidgetId)
                                        finish()
                                    }
                                },
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(saved.design.name, style = MaterialTheme.typography.titleMedium)
                                DesignPreview(saved.design, PreviewSize.Square)
                            }
                        }
                    }
                }
            }
        }
    }
}
