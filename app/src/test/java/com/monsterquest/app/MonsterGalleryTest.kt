package com.monsterquest.app

import android.graphics.Bitmap
import android.graphics.Canvas
import com.monsterquest.app.ui.art.drawMonster
import com.monsterquest.core.Bestiary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every species' art to its own PNG (gallery_<id>.png), for reviewing the roster. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonsterGalleryTest {
    @Test fun renderEverySpecies() {
        val dir = File("build/screenshots").apply { mkdirs() }
        for (sp in Bestiary.everything) {
            val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            drawMonster(Canvas(bmp), 160f, 160f, sp)
            File(dir, "gallery_${sp.id}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        check(Bestiary.all.isNotEmpty())
    }
}
