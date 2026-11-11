package com.sidequest.widgetmaker

import com.sidequest.widgetmaker.ai.Prompts
import com.sidequest.widgetmaker.spec.ClockNode
import com.sidequest.widgetmaker.spec.ColumnNode
import com.sidequest.widgetmaker.spec.DesignParser
import com.sidequest.widgetmaker.spec.IconName
import com.sidequest.widgetmaker.spec.IconNode
import com.sidequest.widgetmaker.spec.RowNode
import com.sidequest.widgetmaker.spec.TextNode
import com.sidequest.widgetmaker.spec.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class DesignParserTest {
    @Test
    fun parsesThePromptsOwnExample() {
        val example = Prompts.SYSTEM.substringAfter("Example (a calm clock widget):")
        val design = DesignParser.parse(example)
        assertEquals("Calm clock", design.name)
        val root = design.root as ColumnNode
        assertTrue(root.children[1] is ClockNode)
        val row = root.children[2] as RowNode
        assertEquals(IconName.battery, (row.children[1] as IconNode).name)
    }

    @Test
    fun findsJsonInsideAFenceAndText() {
        val reply = "Here you go:\n```json\n{\"root\":{\"type\":\"text\",\"text\":\"Hi\"}}\n```"
        val design = DesignParser.parse(reply)
        assertEquals("Hi", (design.root as TextNode).text)
    }

    @Test
    fun clampsSizesAndFixesBadColors() {
        val design = DesignParser.parse(
            """{"padding":500,"background":"blue","root":{"type":"text","text":"x","size":999,"color":"#12"}}""",
        )
        assertEquals(32, design.padding)
        assertEquals("#E61E1E2E", design.background)
        val text = design.root as TextNode
        assertEquals(72, text.size)
        assertEquals("#FFFFFFFF", text.color)
    }

    @Test
    fun unknownEnumValuesFallBackToDefaults() {
        val design = DesignParser.parse("""{"root":{"type":"icon","name":"rocket"}}""")
        assertEquals(IconName.star, (design.root as IconNode).name)
    }

    @Test
    fun rejectsTooManyElements() {
        val children = (1..60).joinToString(",") { """{"type":"text","text":"$it"}""" }
        assertThrows(DesignParser.InvalidDesign::class.java) {
            DesignParser.parse("""{"root":{"type":"column","children":[$children]}}""")
        }
    }

    @Test
    fun rejectsReplyWithoutJson() {
        assertThrows(DesignParser.InvalidDesign::class.java) { DesignParser.parse("Sorry, no.") }
    }

    @Test
    fun roundTrips() {
        val design = DesignParser.parse(Prompts.SYSTEM.substringAfter("Example (a calm clock widget):"))
        assertEquals(design, DesignParser.parse(DesignParser.encode(design)))
    }

    @Test
    fun fillsTokens() {
        val tokens = Tokens(LocalDateTime.of(2026, 3, 4, 8, 15), battery = 76, charging = true, locale = Locale.UK)
        assertEquals("Good morning, Wednesday 76% Charging {nope}", tokens.fill("{greeting}, {day} {battery}% {charging} {nope}"))
        assertEquals(0.76f, tokens.progress("{battery}"), 0.001f)
        assertEquals(1f, tokens.progress("250"), 0.001f)
    }
}
