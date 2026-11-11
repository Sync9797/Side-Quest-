package com.sidequest.widgetmaker.spec

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads and checks the designs Claude writes, and keeps them within what a widget can draw. */
object DesignParser {
    val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    /** Widgets get slow and launchers refuse them past a few dozen views. */
    const val MAX_NODES = 40
    const val MAX_DEPTH = 6

    class InvalidDesign(message: String) : Exception(message)

    /**
     * Pulls the JSON object out of a model reply (which may wrap it in a code
     * fence or a sentence), parses it and clamps sizes to sane ranges.
     */
    fun parse(reply: String): WidgetDesign {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw InvalidDesign("The reply had no JSON object in it.")
        val design = try {
            json.decodeFromString(WidgetDesign.serializer(), reply.substring(start, end + 1))
        } catch (e: SerializationException) {
            throw InvalidDesign("The JSON didn't match the widget format: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw InvalidDesign("The JSON didn't match the widget format: ${e.message}")
        }
        val count = countNodes(design.root)
        if (count > MAX_NODES) throw InvalidDesign("The design has $count elements; the limit is $MAX_NODES.")
        val depth = depth(design.root)
        if (depth > MAX_DEPTH) throw InvalidDesign("The design nests $depth levels deep; the limit is $MAX_DEPTH.")
        return sanitize(design)
    }

    fun encode(design: WidgetDesign): String = json.encodeToString(WidgetDesign.serializer(), design)

    private fun countNodes(node: Node): Int = 1 + children(node).sumOf { countNodes(it) }

    private fun depth(node: Node): Int = 1 + (children(node).maxOfOrNull { depth(it) } ?: 0)

    private fun children(node: Node): List<Node> = when (node) {
        is ColumnNode -> node.children
        is RowNode -> node.children
        else -> emptyList()
    }

    private fun sanitize(design: WidgetDesign) = design.copy(
        name = design.name.take(40).ifBlank { "My widget" },
        background = Colors.orDefault(design.background, "#E61E1E2E"),
        cornerRadius = design.cornerRadius.coerceIn(0, 40),
        padding = design.padding.coerceIn(0, 32),
        root = sanitize(design.root),
    )

    private fun sanitize(node: Node): Node = when (node) {
        is ColumnNode -> node.copy(
            children = node.children.map { sanitize(it) },
            background = node.background?.let { Colors.parseOrNull(it)?.let { _ -> it } },
            cornerRadius = node.cornerRadius.coerceIn(0, 40),
            padding = node.padding.coerceIn(0, 32),
        )
        is RowNode -> node.copy(
            children = node.children.map { sanitize(it) },
            background = node.background?.let { Colors.parseOrNull(it)?.let { _ -> it } },
            cornerRadius = node.cornerRadius.coerceIn(0, 40),
            padding = node.padding.coerceIn(0, 32),
        )
        is TextNode -> node.copy(
            text = node.text.take(200),
            size = node.size.coerceIn(8, 72),
            color = Colors.orDefault(node.color, "#FFFFFFFF"),
            maxLines = node.maxLines.coerceIn(1, 6),
        )
        is ClockNode -> node.copy(
            format = node.format.take(30).ifBlank { "h:mm" },
            size = node.size.coerceIn(10, 96),
            color = Colors.orDefault(node.color, "#FFFFFFFF"),
        )
        is IconNode -> node.copy(
            size = node.size.coerceIn(10, 64),
            color = Colors.orDefault(node.color, "#FFFFFFFF"),
        )
        is ProgressNode -> node.copy(
            color = Colors.orDefault(node.color, "#FF7CF29C"),
            trackColor = Colors.orDefault(node.trackColor, "#33FFFFFF"),
            height = node.height.coerceIn(2, 24),
        )
        is SpacerNode -> node.copy(size = node.size.coerceIn(0, 64))
    }
}

/** "#RGB", "#RRGGBB" or "#AARRGGBB" to an ARGB int. */
object Colors {
    fun parseOrNull(value: String): Int? {
        val hex = value.trim().removePrefix("#")
        val full = when (hex.length) {
            3 -> "FF" + hex.map { "$it$it" }.joinToString("")
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
        return full.toLongOrNull(16)?.toInt()
    }

    fun orDefault(value: String, fallback: String): String = if (parseOrNull(value) != null) value else fallback

    fun argb(value: String?, fallback: Int = 0xFFFFFFFF.toInt()): Int = value?.let { parseOrNull(it) } ?: fallback
}
