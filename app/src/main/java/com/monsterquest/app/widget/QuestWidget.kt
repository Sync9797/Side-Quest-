package com.monsterquest.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.monsterquest.app.MainActivity
import com.monsterquest.app.data.SaveRepository
import com.monsterquest.core.GameState
import com.monsterquest.core.HomeWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home-screen widget: today's reminders, upcoming calendar quests and the
 * to-do list. It follows Material 3 (and the wallpaper's colors on Android
 * 12+), resizes freely, and opens the game when tapped.
 */
class QuestWidget : GlanceAppWidget() {
    /** Small or short: the next few things as a list. About 2x2: the date card. Bigger: every section. */
    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL, SHORT, SQUARE, TALL, TALL_WIDE, WIDE, LARGE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { SaveRepository(context).load() }
        provideContent {
            GlanceTheme {
                WidgetBody(state)
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        /** Wider than tall but not very tall (like 2x1 or 3x1): the list, since the date card won't fit. */
        val SHORT = DpSize(160.dp, 110.dp)
        /** About 2x2 cells: the card with what's next and today's date. */
        val SQUARE = DpSize(160.dp, 160.dp)
        /** Taller-than-wide rectangles (like 2x3 or 2x4) get the list. */
        val TALL = DpSize(110.dp, 210.dp)
        val TALL_WIDE = DpSize(160.dp, 260.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)

        /** Redraws every placed widget (after the save changes). */
        suspend fun refresh(context: Context) = QuestWidget().updateAll(context)
    }
}

class QuestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuestWidget()
}

/** The date card needs a roughly square widget at least [DATE_CARD_MIN_HEIGHT] tall; everything else gets the list. */
internal val DATE_CARD_MIN_HEIGHT = 150.dp

internal fun isDateCardSize(size: DpSize): Boolean =
    size.height >= DATE_CARD_MIN_HEIGHT && size.width < 220.dp && size.height < 220.dp && size.height < size.width * 1.25f

private sealed interface Entry {
    data class Header(val text: String) : Entry
    data class Item(val line: HomeWidget.Line) : Entry
}

@Composable
private fun WidgetBody(state: GameState) {
    val size = LocalSize.current
    val summary = HomeWidget.summary(state, System.currentTimeMillis())
    // Around 2x2 (roughly square, and tall enough to fit it): a card with what's next at the
    // top and today's date at the bottom. Shorter or taller than that, the list.
    if (isDateCardSize(size)) {
        DateCard(summary.next)
        return
    }
    val compact = size.height < 180.dp || size.width < 200.dp
    val colors = GlanceTheme.colors
    // Small widgets get one combined list; bigger ones get a section per kind.
    val rows: List<Entry> = if (compact) {
        // As many lines as fit the height.
        val fits = ((size.height.value - 48f) / 26f).toInt().coerceIn(3, 10)
        (summary.reminders.filter { !it.done } + summary.calendar + summary.todos).take(fits).map { Entry.Item(it) }
    } else {
        buildList {
            if (summary.reminders.isNotEmpty()) {
                add(Entry.Header("Reminders"))
                summary.reminders.forEach { add(Entry.Item(it)) }
            }
            if (summary.calendar.isNotEmpty()) {
                add(Entry.Header("Calendar"))
                summary.calendar.forEach { add(Entry.Item(it)) }
            }
            if (summary.todos.isNotEmpty()) {
                add(Entry.Header("To-do"))
                summary.todos.forEach { add(Entry.Item(it)) }
            }
        }
    }
    Column(
        GlanceModifier
            .fillMaxSize()
            // The same frosted glass as the 2x2 card.
            .background(androidx.glance.ImageProvider(com.monsterquest.app.R.drawable.widget_glass))
            .cornerRadius(28.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        androidx.glance.layout.Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Side Quest",
                style = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(com.monsterquest.core.Stars.label(summary.heroLevel), style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (summary.isEmpty) {
            Text("All clear! Nothing due.", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp))
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(rows) { row ->
                    when (row) {
                        is Entry.Header -> Text(
                            row.text,
                            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                        is Entry.Item -> LineRow(row.line)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: HomeWidget.Line) {
    val colors = GlanceTheme.colors
    // Lines sit straight on the glass; urgent ones get a soft light band.
    val base = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
    androidx.glance.layout.Row(
        (if (line.urgent) base.background(androidx.glance.ImageProvider(com.monsterquest.app.R.drawable.widget_row_highlight)) else base)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        line.time?.let {
            Text(it, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            (if (line.done) "✓ " else "") + line.title,
            style = TextStyle(
                color = if (line.done) colors.onSurfaceVariant else colors.onSurface,
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
    }
}

/**
 * The 2x2 widget: the next event, task or reminder at the top (with when),
 * and today's date in large type at the bottom, on a wallpaper-tinted card.
 */
@Composable
private fun DateCard(next: HomeWidget.Line?) {
    val colors = GlanceTheme.colors
    // Scale the big date (and how many title lines fit) to the height, so nothing gets cut off.
    val h = LocalSize.current.height.value
    val dateSp = (h * 0.28f).coerceIn(36f, 52f)
    val titleLines = if (h >= 180f) 2 else 1
    val today = java.time.LocalDate.now()
    val locale = java.util.Locale.getDefault()
    Column(
        GlanceModifier
            .fillMaxSize()
            // Frosted, glossy glass tinted from the wallpaper (see drawable/widget_glass.xml).
            .background(androidx.glance.ImageProvider(com.monsterquest.app.R.drawable.widget_glass))
            .cornerRadius(28.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            next?.title ?: "All clear",
            style = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            maxLines = titleLines,
        )
        Text(
            next?.time ?: "Nothing planned",
            style = TextStyle(color = if (next?.urgent == true) colors.primary else colors.onSurfaceVariant, fontSize = 13.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            today.dayOfMonth.toString(),
            style = TextStyle(color = colors.onSurface, fontSize = dateSp.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            "${today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)}, " +
                today.month.getDisplayName(java.time.format.TextStyle.SHORT, locale),
            style = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}
