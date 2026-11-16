package com.monsterquest.app.ui.art

import android.graphics.Canvas

/**
 * Small 24x24 icons drawn from path data. FILL and LINE parts use the tint;
 * CUT parts are drawn in the background color to carve detail out of fills.
 */
enum class GameIcon(internal vararg val parts: IconPart) {
    // Navigation
    QUESTS(line("M6 3h11v15a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v-1h11"), line("M9 8h5M9 12h5")),
    HERO(line("M12 3C8 6 6 9 6 14l1.5 6h9L18 14c0-5-2-8-6-11z"), line("M9.5 13.5q2.5-3 5 0")),
    MONSTERS(
        fill("M7.5 17a4.5 3.8 0 1 0 9 0a4.5 3.8 0 1 0 -9 0"),
        fill("M3.5 11a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"), fill("M7.5 6.5a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"),
        fill("M12.5 6.5a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"), fill("M16.5 11a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"),
    ),
    MAP(line("M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z"), line("M9 4v14M15 6v14")),
    BAG(line("M5 9h14l-1 11H6z"), line("M9 9V7a3 3 0 0 1 6 0v2"), line("M9 13h6")),
    STORE(line("M4 9l1.5-5h13L20 9"), line("M4 9a2.67 2.67 0 0 0 5.33 0a2.67 2.67 0 0 0 5.34 0a2.67 2.67 0 0 0 5.33 0"), line("M5.5 11.5V20h13v-8.5"), line("M10 20v-5h4v5")),

    // Resources & actions
    COIN(fill("M12 3a9 9 0 1 0 .01 0z"), cut("M12 7.5l3.2 4.5L12 16.5 8.8 12z")),
    TARGET(line("M12 4a8 8 0 1 0 .01 0z"), line("M12 8.5a3.5 3.5 0 1 0 .01 0z"), fill("M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0")),
    PLUS(line("M12 5v14M5 12h14")),
    CHECK(line("M5 12.5l4.5 4.5L19 7")),
    CLOSE(line("M6 6l12 12M18 6L6 18")),
    EDIT(fill("M4 20l1-4.5L16 4.5l3.5 3.5L8.5 19z")),
    TRASH(line("M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13")),
    CALENDAR(line("M4 5h16v15H4z"), line("M4 9.5h16M8 3v4M16 3v4"), fill("M7 12h3v3H7z")),
    PHONE_OFF(line("M8 2.5h8a1 1 0 0 1 1 1v17a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1v-17a1 1 0 0 1 1-1z"), line("M3 3l18 18")),
    COMPASS(line("M12 3a9 9 0 1 0 .01 0z"), fill("M15.5 8.5l-2 5.5-5.5 2 2-5.5z")),
    WALK(fill("M12.5 2.5a2 2 0 1 0 .01 0z"), line("M12 8l-2.5 5 3 2.5V21M12 8l4 4M9.5 13L6.5 17")),
    STAR(fill("M12 2.5l2.8 6.2 6.7.6-5.1 4.4 1.6 6.6L12 16.8l-6 3.5 1.6-6.6-5.1-4.4 6.7-.6z")),
    SHIELD(fill("M12 2l8 3v6c0 5-3.5 9-8 11-4.5-2-8-6-8-11V5z")),
    SPARK(fill("M12 2l2 8 8 2-8 2-2 8-2-8-8-2 8-2z")),
    FIGHT(line("M4 4l10 10M20 4L10 14M5 19l3-3M19 19l-3-3M7 14l3 3M17 14l-3 3")),
    PIN(fill("M12 22s7-7 7-12a7 7 0 0 0-14 0c0 5 7 12 7 12z"), cut("M12 7.5a2.5 2.5 0 1 0 .01 0z")),
    LOCK(fill("M6 11h12v10H6z"), line("M8.5 11V8a3.5 3.5 0 0 1 7 0v3")),
    HOURGLASS(line("M6 3h12M6 21h12"), line("M7.5 3c0 6 9 6 9 9s-9 3-9 9M16.5 3c0 6-9 6-9 9s9 3 9 9")),
    SLEEP(line("M4 6h6l-6 7h6M13 11h6l-6 7h6")),
    SKULL(fill("M12 3c-5 0-8 3-8 7 0 3 2 5 4 5.5V20h8v-4.5c2-.5 4-2.5 4-5.5 0-4-3-7-8-7z"), cut("M8.5 10a1.8 1.8 0 1 0 .01 0zM15.5 10a1.8 1.8 0 1 0 .01 0z")),
    GEAR(fill("M10.3 2h3.4l.5 2.6 1.9.8 2.2-1.5 2.4 2.4-1.5 2.2.8 1.9 2.6.5v3.4l-2.6.5-.8 1.9 1.5 2.2-2.4 2.4-2.2-1.5-1.9.8-.5 2.6h-3.4l-.5-2.6-1.9-.8-2.2 1.5-2.4-2.4 1.5-2.2-.8-1.9L2 13.7v-3.4l2.6-.5.8-1.9-1.5-2.2 2.4-2.4 2.2 1.5 1.9-.8z"), cut("M12 8.5a3.5 3.5 0 1 0 .01 0z")),

    // Weather
    CLOUD(fill("M7 19a4 4 0 0 1-.6-7.96A5.5 5.5 0 0 1 17 10a4.5 4.5 0 0 1 0 9z")),
    STORM(fill("M7 14a4 4 0 0 1-.6-7.96A5.5 5.5 0 0 1 17 5a4.5 4.5 0 0 1 0 9z"), fill("M12.5 15l-3 4h2.2l-1 3.5 3.8-5h-2.2l1-2.5z")),
    SNOW(fill("M7 14a4 4 0 0 1-.6-7.96A5.5 5.5 0 0 1 17 5a4.5 4.5 0 0 1 0 9z"), fill("M8 17.5a1.1 1.1 0 1 0 .01 0zM12 19.5a1.1 1.1 0 1 0 .01 0zM16 17.5a1.1 1.1 0 1 0 .01 0z")),
    FOG(line("M4 7h16M3 11h18M5 15h14M7 19h10")),
    WIND(line("M3 8h11a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h8")),

    // Types
    FLAME(fill("M12 2c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 2-5 3-6 0 2 1 3 2 3 0-3-1-6 1-9z")),
    BOLT(fill("M13 2L4 14h6l-1 8 9-12h-6z")),
    WING(fill("M2 15C8 15 14 10 17 3c2.5 5 1.5 11-3 14.5-4 3-9 2-12-2.5z"), cut("M6 15.5c3 0 6-2 8-5")),
    DROP(fill("M12 2c4 6 7 9 7 13a7 7 0 0 1-14 0c0-4 3-7 7-13z")),
    BERRY(fill("M12 9a5.6 5.6 0 1 0 .01 0z"), fill("M12 9.2c0-3.2 1.6-5.4 5-6.2-.9 3-2.5 5.2-5 6.2z"), cut("M9.2 12.6a1.2 1.2 0 1 0 .01 0z")),
    HEART(fill("M12 20.5C6.5 16.6 3 13.4 3 9.3 3 6.4 5.2 4.3 7.8 4.3c1.7 0 3.2.9 4.2 2.3 1-1.4 2.5-2.3 4.2-2.3 2.6 0 4.8 2.1 4.8 5 0 4.1-3.5 7.3-9 11.2z")),
    GAS(fill("M7 19a4.5 4.5 0 0 1-.6-9 5.5 5.5 0 0 1 10.6-1A4.5 4.5 0 0 1 17 19z"), cut("M8 15c2-2 5-2 8 0")),
    MOON(fill("M15 3a9 9 0 1 0 6 15A7 7 0 0 1 15 3z")),
    LEAF(fill("M5 19C5 10 11 4 20 4c0 9-6 15-15 15z"), cut("M6 18L15 9")),
    RING(line("M12 5a7 7 0 1 0 .01 0z")),
    RAIN(fill("M7 13a4 4 0 0 1-.5-8 5 5 0 0 1 9.5-1A4 4 0 0 1 17 13z"), line("M8 16l-1 3M12 16l-1 3M16 16l-1 3")),
    SUN(fill("M12 7.5a4.5 4.5 0 1 0 .01 0z"), line("M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8")),

    // Items
    POTION(fill("M10 2h4v4.5l4.5 6.5v5a4 4 0 0 1-4 4h-5a4 4 0 0 1-4-4v-5L10 6.5z"), cut("M7 14h10")),
    GREATER_POTION(fill("M10 2h4v6l3 3v9a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-9l3-3z"), cut("M8.5 14h7")),
    CRYSTAL(fill("M12 2l6 7-6 13-6-13z"), cut("M6 9h12M12 2v20")),
    FEATHER(fill("M20 3C12 4 6 10 5 19l-1 2 2-1c9-1 14-7 14-17z"), cut("M6 18l9-9")),
    RESTORE(fill("M12 21l-8.2-8.2a5 5 0 0 1 8.2-6 5 5 0 0 1 8.2 6z"), cut("M12 9v7M8.5 12.5h7")),
    // A crystal taming orb held in a small claw cradle, marked with a rune.
    ORB(fill("M12 2.5a7.5 7.5 0 1 0 .01 0z"), cut("M8 7.5a5 5 0 0 1 3.5-2.4"), cut("M12 7v6.5M9.6 9.2l4.8 2.3M14.4 9.2l-4.8 2.3"), fill("M5.5 16.5L8 21.5h8l2.5-5-3.5 1.6h-6z")),
    TREAT(fill("M15 3a6 6 0 0 1 0 12h-1.5l-4 4a2 2 0 1 1-2.5-2.5l4-4V11A6 6 0 0 1 15 3z")),
    CHEST(fill("M3 11h18v10H3z"), fill("M3.5 10Q12 2 20.5 10z"), cut("M3 13.5h18"), cut("M10.5 12h3v4h-3z")),
    PARTS(fill("M12 3l2 2.5 3-.5.5 3L20 10l-1.5 2.5L20 15l-2.5 2-.5 3-3-.5L12 21l-2-2.5-3 .5-.5-3L4 15l1.5-2.5L4 10l2.5-2 .5-3 3 .5z"), cut("M12 9a3 3 0 1 0 .01 0z")),
    CANDY(fill("M12 7a5 5 0 1 0 .01 0z"), fill("M7.5 10.5L2 7v10l5.5-3.5z"), fill("M16.5 10.5L22 7v10l-5.5-3.5z"), cut("M9.5 10l5 4")),
    PAGE(fill("M6 2h9l4 4v16H6z"), cut("M9 10h7M9 13.5h7M9 17h5")),
    EGG(fill("M12 2.5c-3.9 0-7 5.8-7 10.8a7 7 0 0 0 14 0c0-5-3.1-10.8-7-10.8z"), cut("M9 11.5a1.3 1.3 0 1 0 .01 0z"), cut("M14.5 15a1.1 1.1 0 1 0 .01 0z"), cut("M11 17.5a.9.9 0 1 0 .01 0z")),

    // Weapons
    SWORD(fill("M20 2.5l-9.5 9.5 1.5 1.5L21.5 4V2.5z"), fill("M6.5 12.5l5 5-1.2 1.2-5-5z"), fill("M7 16l1.8 1.8-3.6 3.6-1.8-1.8z")),
    STAFF(line("M5 21L16 8"), fill("M18 3a3.2 3.2 0 1 0 .01 0z")),
    BROADSWORD(fill("M18 2h4v4L11 17l-4-4z"), fill("M5.5 11.5l7 7-1.5 1.5-7-7z"), fill("M6 17l1.5 1.5-3.5 3.5L2.5 20.5z")),
    WAND(line("M4 20L14 10"), fill("M17 3l1.2 2.8L21 7l-2.8 1.2L17 11l-1.2-2.8L13 7l2.8-1.2z")),
    DAGGER(fill("M15 3l3 3-7.5 7.5-3-3z"), line("M8.5 15.5L4 20")),
    AXE(line("M12 9v13"), fill("M11 4Q7 4 4 2Q2.5 7 4 12Q7 10 11 10z"), fill("M13 4Q17 4 20 2Q21.5 7 20 12Q17 10 13 10z"), fill("M10.5 2h3v9h-3z")),
    BOW(line("M7 3q11 9 0 18"), line("M7 3v18"), line("M3 12h15"), fill("M17.5 10l4 2-4 2z")),
    SCYTHE(line("M9 21L14.5 3"), fill("M14.5 3q-8-1.5-11.5 6 5-3.5 10.8-2z")),

    // Armor
    PLATE(fill("M5 5l4-2h6l4 2v6c0 5-3 9-7 11-4-2-7-6-7-11z"), cut("M12 4v17M7 9h10")),
    LEATHER(fill("M7 3h10l3 5-3 2v11H7V10L4 8z"), cut("M8 6l8 12")),
    ROBE(fill("M9 2h6l1 6 4 14H4L8 8z"), cut("M9.5 3L12 8l2.5-5M12 9v12")),
    HOOD(fill("M12 2C7 4 5 9 5 14v7h14v-7c0-5-2-10-7-12z"), cut("M8.5 17v-4c1-4 6-4 7 0v4z")),
    CLOAK(fill("M8 3h8l5 18H3z"), cut("M12 6v14M9 3l3 3 3-3")),
    PANTS(fill("M6 3h12l1 18h-5l-2-11-2 11H5z"), cut("M6 6h12")),
    BOOTS(fill("M7 3h6v10l6 3v4H5v-3l2-2z"), cut("M7 8h6")),
    HELM(fill("M4 15a8 8 0 0 1 16 0v3H4z"), fill("M11 3h2v5h-2z"), cut("M7 13.5h10v1.5H7z")),
    SANDALS(fill("M4 17h16v3H4z"), line("M8 17l2-6 2 6M12 11l3 6")),

    // Exploration
    BATTERY(line("M3 7h15v10H3z"), line("M18 10h3v4h-3"), fill("M5 9h4v6H5z")),
    LURE(fill("M12 2c3 4 5 6 5 10a5 5 0 0 1-10 0c0-4 2-6 5-10z"), line("M5 20q7-4 14 0"), cut("M12 9v6")),
    BOOK(fill("M4 4h7a2 2 0 0 1 1 1v16a3 3 0 0 0-3-2H4zM20 4h-7a2 2 0 0 0-1 1v16a3 3 0 0 1 3-2h5z")),
    PAW(fill("M12 12.5c3 0 5.5 3 5.5 5.5 0 2-2 2.5-3 2.5-1.2 0-1.5-.6-2.5-.6s-1.3.6-2.5.6c-1 0-3-.5-3-2.5 0-2.5 2.5-5.5 5.5-5.5z"), fill("M5 9a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"), fill("M8.5 5a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"), fill("M11.5 5a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0"), fill("M15 9a2 2.4 0 1 0 4 0a2 2.4 0 1 0 -4 0")),
    EYE(fill("M2 12c3-5 6.5-7 10-7s7 2 10 7c-3 5-6.5 7-10 7s-7-2-10-7z"), cut("M12 9a3 3 0 1 0 .01 0z")),
    FIST(fill("M6 10h12v6a5 5 0 0 1-5 5h-2a5 5 0 0 1-5-5z"), cut("M9 10v4M12 10v4M15 10v4")),

    // Shrines
    ITEM_SHRINE(fill("M12 2l8 8-8 12-8-12z"), cut("M4 10h16M8 10l4 12 4-12")),
    SILVER_SHRINE(fill("M8 22V9l4-7 4 7v13z"), cut("M10 13h4M10 17h4")),
    GOLD_SHRINE(fill("M4 22V12l4-3V5l4-3 4 3v4l4 3v10z"), cut("M10.5 22v-5h3v5M12 7.5a1 1 0 1 0 .01 0z")),
}

internal enum class PartKind { FILL, LINE, CUT }

class IconPart internal constructor(internal val d: String, internal val kind: PartKind)

private fun fill(d: String) = IconPart(d, PartKind.FILL)
private fun line(d: String) = IconPart(d, PartKind.LINE)
private fun cut(d: String) = IconPart(d, PartKind.CUT)

/** Draws [icon] in [tint] into a [w]x[h] area; CUT parts use [cutColor]. */
internal fun drawIcon(c: Canvas, w: Float, h: Float, icon: GameIcon, tint: Int, cutColor: Int) {
    c.save()
    ArtPainter.fit(c, w, h, 24f, 24f)
    for (p in icon.parts) {
        when (p.kind) {
            PartKind.FILL -> ArtPainter.fill(c, p.d, tint)
            PartKind.LINE -> ArtPainter.stroke(c, p.d, tint, 2f)
            PartKind.CUT -> ArtPainter.stroke(c, p.d, cutColor, 1.6f)
        }
    }
    c.restore()
}
