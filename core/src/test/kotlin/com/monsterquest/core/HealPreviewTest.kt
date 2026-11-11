package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a healing spell's preview says is what it heals. */
class HealPreviewTest {
    private fun battle(spell: String, path: Int?): Battle {
        val base = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val hero = base.hero.copy(
            level = 60,
            knownSpells = Spells.all.map { it.id }.toSet(),
            spellRanks = Spells.all.associate { it.id to Spells.MAX_RANK },
            spellPaths = if (path == null) emptyMap() else mapOf(spell to path),
            spellMastery = if (path == null) emptyMap() else mapOf(spell to 3),
            attributes = base.hero.attributes + (HeroAttribute.ENERGY to 60),
        )
        // Everyone hurt, so nothing caps at full HP.
        val s = base.copy(hero = hero, heroDamage = 100_000, monsters = base.monsters.map { it.copy(level = 60, damage = 100_000) })
        return GameEngine.newBattle(s, Monster.create(Bestiary.get("zapmouse"), 3, Random(2)), EncounterType.WILD, rng = Random(3))
    }

    @Test
    fun previewsMatchTheHealing() {
        for (id in listOf("mend", "cleanse", "healing_circle")) for (path in listOf(null, 1, 2)) {
            val b = battle(id, path)
            var guard = 0
            while (b.current?.isHero != true && guard++ < 10) b.act(BattleAction.Defend)
            val hero = assertNotNull(b.current, "$id $path")
            val skill = b.spellsFor(hero).single { it.id == id }
            val preview = assertNotNull(b.healPreview(hero, skill), "$id $path")
            val ally = b.allies.first { !it.isHero }
            val before = b.allies.associate { it.key to it.hp }
            b.act(BattleAction.CastSpell(id, hero.key))
            assertEquals(before.getValue(hero.key) + preview, b.allies.first { it.isHero }.hp, "$id path $path heals the target")
            val all = b.healsEveryone(hero, skill)
            assertEquals(if (all) before.getValue(ally.key) + preview else before.getValue(ally.key), ally.hp, "$id path $path: everyone? $all")
        }
        // Spells that don't heal have no preview.
        val b = battle("mend", null)
        val hero = b.allies.first { it.isHero }
        assertNull(b.healPreview(hero, b.spellsFor(hero).first { it.kind == SkillKind.MAGICAL }))
    }
}
