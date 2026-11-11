package com.sidequest.widgetmaker.spec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A widget design: what Claude writes and what the widget renders.
 *
 * Android widgets can't run downloaded code, so a design is a small tree of
 * building blocks (rows, columns, text, a live clock, icons, progress bars)
 * that the app knows how to draw on the home screen and the lock screen.
 */
@Serializable
data class WidgetDesign(
    val name: String = "My widget",
    /** Background color of the whole widget, "#RRGGBB" or "#AARRGGBB". */
    val background: String = "#E61E1E2E",
    val cornerRadius: Int = 24,
    val padding: Int = 14,
    val root: Node,
)

/** A saved design plus what it was made from. */
@Serializable
data class SavedDesign(
    val id: String,
    val prompt: String,
    val design: WidgetDesign,
    val createdAt: Long,
)

enum class HAlign { start, center, end }
enum class VAlign { top, center, bottom }
enum class Weight { light, normal, medium, bold }

/** Icons the widget can draw (each is a drawable in the app). */
enum class IconName { star, heart, bolt, moon, sun, battery, calendar, music }

@Serializable
sealed interface Node {
    /** In a row or column: take up the leftover space. */
    val grow: Boolean
}

@Serializable
@SerialName("column")
data class ColumnNode(
    val children: List<Node> = emptyList(),
    val align: HAlign = HAlign.start,
    val verticalAlign: VAlign = VAlign.top,
    val background: String? = null,
    val cornerRadius: Int = 0,
    val padding: Int = 0,
    override val grow: Boolean = false,
) : Node

@Serializable
@SerialName("row")
data class RowNode(
    val children: List<Node> = emptyList(),
    val align: HAlign = HAlign.start,
    val verticalAlign: VAlign = VAlign.center,
    val background: String? = null,
    val cornerRadius: Int = 0,
    val padding: Int = 0,
    override val grow: Boolean = false,
) : Node

/** Text. May contain tokens like {date} or {battery}; see [Tokens]. */
@Serializable
@SerialName("text")
data class TextNode(
    val text: String,
    val size: Int = 14,
    val color: String = "#FFFFFFFF",
    val weight: Weight = Weight.normal,
    val align: HAlign = HAlign.start,
    val maxLines: Int = 2,
    override val grow: Boolean = false,
) : Node

/**
 * A live clock or date that the system keeps up to date every minute.
 * [format] is an Android date pattern, like "h:mm", "HH:mm" or "EEE d MMM".
 */
@Serializable
@SerialName("clock")
data class ClockNode(
    val format: String = "h:mm",
    val size: Int = 40,
    val color: String = "#FFFFFFFF",
    val weight: Weight = Weight.light,
    override val grow: Boolean = false,
) : Node

@Serializable
@SerialName("icon")
data class IconNode(
    val name: IconName = IconName.star,
    val size: Int = 20,
    val color: String = "#FFFFFFFF",
    override val grow: Boolean = false,
) : Node

/** A bar from 0 to 100. [value] is a number or "{battery}". */
@Serializable
@SerialName("progress")
data class ProgressNode(
    val value: String = "{battery}",
    val color: String = "#FF7CF29C",
    val trackColor: String = "#33FFFFFF",
    val height: Int = 6,
    override val grow: Boolean = false,
) : Node

/** Empty space: [size] dp, or all the leftover space when [grow] is set. */
@Serializable
@SerialName("spacer")
data class SpacerNode(
    val size: Int = 8,
    override val grow: Boolean = false,
) : Node
