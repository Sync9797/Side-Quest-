package com.sidequest.widgetmaker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidequest.widgetmaker.spec.ClockNode
import com.sidequest.widgetmaker.spec.Colors
import com.sidequest.widgetmaker.spec.ColumnNode
import com.sidequest.widgetmaker.spec.HAlign
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
import com.sidequest.widgetmaker.widget.AiWidget
import com.sidequest.widgetmaker.widget.iconRes
import kotlinx.coroutines.delay
import java.util.Calendar

/** Home-screen sizes to preview a design at (about 2x1, 2x2 and 4x2 cells). */
enum class PreviewSize(val label: String, val width: Dp, val height: Dp) {
    Small("2×1", 160.dp, 80.dp),
    Square("2×2", 160.dp, 160.dp),
    Wide("4×2", 320.dp, 160.dp),
}

/**
 * The design drawn in the app, matching what the widget draws (same layout
 * rules, same live values), so you can see it before adding it.
 */
@Composable
fun DesignPreview(design: WidgetDesign, size: PreviewSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tokens by remember { mutableStateOf(AiWidget.currentTokens(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tokens = AiWidget.currentTokens(context)
        }
    }
    val shape = RoundedCornerShape(design.cornerRadius.dp)
    Box(
        modifier
            .size(size.width, size.height)
            .clip(shape)
            .background(color(design.background))
            .padding(design.padding.dp),
    ) {
        PreviewNode(design.root, tokens, Modifier.fillMaxSize())
    }
}

@Composable
private fun PreviewNode(node: Node, tokens: Tokens, modifier: Modifier) {
    when (node) {
        is ColumnNode -> Column(
            modifier = container(modifier, node.background, node.cornerRadius, node.padding),
            horizontalAlignment = when (node.align) {
                HAlign.start -> Alignment.Start
                HAlign.center -> Alignment.CenterHorizontally
                HAlign.end -> Alignment.End
            },
            verticalArrangement = when (node.verticalAlign) {
                VAlign.top -> Arrangement.Top
                VAlign.center -> Arrangement.Center
                VAlign.bottom -> Arrangement.Bottom
            },
        ) { node.children.forEach { ColumnChild(it, tokens) } }
        is RowNode -> Row(
            modifier = container(modifier, node.background, node.cornerRadius, node.padding),
            horizontalArrangement = when (node.align) {
                HAlign.start -> Arrangement.Start
                HAlign.center -> Arrangement.Center
                HAlign.end -> Arrangement.End
            },
            verticalAlignment = when (node.verticalAlign) {
                VAlign.top -> Alignment.Top
                VAlign.center -> Alignment.CenterVertically
                VAlign.bottom -> Alignment.Bottom
            },
        ) { node.children.forEach { RowChild(it, tokens) } }
        is TextNode -> Text(
            text = tokens.fill(node.text),
            modifier = modifier,
            color = color(node.color),
            fontSize = node.size.sp,
            lineHeight = (node.size * 1.2f).sp,
            fontWeight = weight(node.weight),
            textAlign = when (node.align) {
                HAlign.start -> TextAlign.Start
                HAlign.center -> TextAlign.Center
                HAlign.end -> TextAlign.End
            },
            maxLines = node.maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        is ClockNode -> Text(
            text = android.text.format.DateFormat.format(node.format, Calendar.getInstance()).toString(),
            modifier = modifier,
            color = color(node.color),
            fontSize = node.size.sp,
            lineHeight = node.size.sp,
            fontWeight = weight(node.weight),
            maxLines = 1,
        )
        is IconNode -> Image(
            painter = painterResource(iconRes(node.name)),
            contentDescription = null,
            modifier = modifier.size(node.size.dp),
            colorFilter = ColorFilter.tint(color(node.color)),
        )
        is ProgressNode -> LinearProgressIndicator(
            progress = { tokens.progress(node.value) },
            modifier = modifier.fillMaxWidth().height(node.height.dp).clip(RoundedCornerShape(node.height.dp)),
            color = color(node.color),
            trackColor = color(node.trackColor),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        is SpacerNode -> Spacer(if (node.grow) modifier else modifier.size(node.size.dp))
    }
}

@Composable
private fun ColumnScope.ColumnChild(node: Node, tokens: Tokens) =
    PreviewNode(node, tokens, if (node.grow) Modifier.weight(1f) else Modifier)

@Composable
private fun RowScope.RowChild(node: Node, tokens: Tokens) =
    PreviewNode(node, tokens, if (node.grow) Modifier.weight(1f) else Modifier)

private fun container(modifier: Modifier, background: String?, radius: Int, padding: Int): Modifier {
    var m = modifier
    if (background != null) m = m.clip(RoundedCornerShape(radius.dp)).background(color(background))
    if (padding > 0) m = m.padding(padding.dp)
    return m
}

private fun color(value: String) = Color(Colors.argb(value))

private fun weight(weight: Weight) = when (weight) {
    Weight.light -> FontWeight.Light
    Weight.normal -> FontWeight.Normal
    Weight.medium -> FontWeight.Medium
    Weight.bold -> FontWeight.Bold
}
