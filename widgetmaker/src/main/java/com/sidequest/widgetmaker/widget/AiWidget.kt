package com.sidequest.widgetmaker.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sidequest.widgetmaker.R
import com.sidequest.widgetmaker.data.DesignStore
import com.sidequest.widgetmaker.spec.ClockNode
import com.sidequest.widgetmaker.spec.Colors
import com.sidequest.widgetmaker.spec.ColumnNode
import com.sidequest.widgetmaker.spec.HAlign
import com.sidequest.widgetmaker.spec.IconName
import com.sidequest.widgetmaker.spec.IconNode
import com.sidequest.widgetmaker.spec.Node
import com.sidequest.widgetmaker.spec.ProgressNode
import com.sidequest.widgetmaker.spec.RowNode
import com.sidequest.widgetmaker.spec.SpacerNode
import com.sidequest.widgetmaker.spec.TextNode
import com.sidequest.widgetmaker.spec.Tokens
import com.sidequest.widgetmaker.spec.VAlign
import com.sidequest.widgetmaker.spec.Weight
import com.sidequest.widgetmaker.spec.WidgetDesign
import com.sidequest.widgetmaker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** The widget: draws whichever saved design was picked for it. Tapping it opens the app. */
class AiWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val saved = withContext(Dispatchers.IO) { DesignStore(context).designFor(appWidgetId) }
        val tokens = currentTokens(context)
        provideContent {
            val open = GlanceModifier.clickable(actionStartActivity<MainActivity>())
            if (saved == null) EmptyWidget(open) else DesignWidget(saved.design, tokens, open)
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) = AiWidget().updateAll(context)

        fun currentTokens(context: Context): Tokens {
            val battery = context.getSystemService(BatteryManager::class.java)
            val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return Tokens(
                now = LocalDateTime.now(),
                battery = level?.takeIf { it in 0..100 },
                charging = battery?.isCharging == true,
            )
        }
    }
}

class AiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AiWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = DesignStore(context)
        appWidgetIds.forEach { store.forget(it) }
    }
}

/** After "Add to home screen": links the new widget to the design it was added for. */
class PinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val designId = intent.getStringExtra(EXTRA_DESIGN_ID) ?: return
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        DesignStore(context).assign(appWidgetId, designId)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updateWidget(context, appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_DESIGN_ID = "design_id"
    }
}

suspend fun updateWidget(context: Context, appWidgetId: Int) {
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    AiWidget().update(context, glanceId)
}

@Composable
private fun EmptyWidget(modifier: GlanceModifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xE61E1E2E)).cornerRadius(24.dp).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Tap to design this widget",
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun DesignWidget(design: WidgetDesign, tokens: Tokens, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color(design.background))
            .cornerRadius(design.cornerRadius.dp)
            .padding(design.padding.dp),
    ) {
        GlanceNode(design.root, tokens, GlanceModifier.fillMaxSize())
    }
}

@Composable
private fun GlanceNode(node: Node, tokens: Tokens, modifier: GlanceModifier = GlanceModifier) {
    when (node) {
        is ColumnNode -> Column(
            modifier = container(modifier, node.background, node.cornerRadius, node.padding),
            horizontalAlignment = horizontal(node.align),
            verticalAlignment = vertical(node.verticalAlign),
        ) {
            node.children.forEach { child ->
                GlanceNode(child, tokens, if (child.grow) GlanceModifier.defaultWeight() else GlanceModifier)
            }
        }
        is RowNode -> Row(
            modifier = container(modifier, node.background, node.cornerRadius, node.padding),
            horizontalAlignment = horizontal(node.align),
            verticalAlignment = vertical(node.verticalAlign),
        ) {
            node.children.forEach { child ->
                GlanceNode(child, tokens, if (child.grow) GlanceModifier.defaultWeight() else GlanceModifier)
            }
        }
        is TextNode -> Text(
            text = tokens.fill(node.text),
            modifier = modifier,
            maxLines = node.maxLines,
            style = TextStyle(
                color = color(node.color),
                fontSize = node.size.sp,
                fontWeight = when (node.weight) {
                    Weight.bold -> FontWeight.Bold
                    Weight.medium -> FontWeight.Medium
                    else -> FontWeight.Normal
                },
                textAlign = when (node.align) {
                    HAlign.start -> TextAlign.Start
                    HAlign.center -> TextAlign.Center
                    HAlign.end -> TextAlign.End
                },
            ),
        )
        is ClockNode -> AndroidRemoteViews(remoteViews = clockViews(node), modifier = modifier)
        is IconNode -> Image(
            provider = ImageProvider(iconRes(node.name)),
            contentDescription = null,
            modifier = modifier.size(node.size.dp),
            colorFilter = ColorFilter.tint(color(node.color)),
        )
        is ProgressNode -> LinearProgressIndicator(
            progress = tokens.progress(node.value),
            modifier = modifier.fillMaxWidth().height(node.height.dp),
            color = color(node.color),
            backgroundColor = color(node.trackColor),
        )
        is SpacerNode -> Spacer(if (node.grow) modifier else modifier.size(node.size.dp))
    }
}

@Composable
private fun clockViews(node: ClockNode): RemoteViews {
    val context = androidx.glance.LocalContext.current
    val layout = when (node.weight) {
        Weight.bold, Weight.medium -> R.layout.clock_bold
        Weight.light -> R.layout.clock_light
        Weight.normal -> R.layout.clock_normal
    }
    return RemoteViews(context.packageName, layout).apply {
        // The same pattern whatever the phone's 12/24-hour setting: the design chose it.
        setCharSequence(R.id.clock, "setFormat12Hour", node.format)
        setCharSequence(R.id.clock, "setFormat24Hour", node.format)
        setTextColor(R.id.clock, Colors.argb(node.color))
        setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_SP, node.size.toFloat())
    }
}

private fun container(modifier: GlanceModifier, background: String?, radius: Int, padding: Int): GlanceModifier {
    var m = modifier
    if (background != null) m = m.background(color(background)).cornerRadius(radius.dp)
    if (padding > 0) m = m.padding(padding.dp)
    return m
}

private fun color(value: String) = ColorProvider(Color(Colors.argb(value)))

private fun horizontal(align: HAlign) = when (align) {
    HAlign.start -> Alignment.Horizontal.Start
    HAlign.center -> Alignment.Horizontal.CenterHorizontally
    HAlign.end -> Alignment.Horizontal.End
}

private fun vertical(align: VAlign) = when (align) {
    VAlign.top -> Alignment.Vertical.Top
    VAlign.center -> Alignment.Vertical.CenterVertically
    VAlign.bottom -> Alignment.Vertical.Bottom
}

fun iconRes(name: IconName) = when (name) {
    IconName.star -> R.drawable.icon_star
    IconName.heart -> R.drawable.icon_heart
    IconName.bolt -> R.drawable.icon_bolt
    IconName.moon -> R.drawable.icon_moon
    IconName.sun -> R.drawable.icon_sun
    IconName.battery -> R.drawable.icon_battery
    IconName.calendar -> R.drawable.icon_calendar
    IconName.music -> R.drawable.icon_music
}
