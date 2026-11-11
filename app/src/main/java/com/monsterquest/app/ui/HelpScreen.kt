package com.monsterquest.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.IconText

/** A short section of help: a title and a few points. */
private class HelpSection(val title: String, val points: List<String>)

/** A help category: its icon, title and sections. */
private class HelpTopic(val icon: GameIcon, val title: String, val sections: List<HelpSection>)

private fun section(title: String, vararg points: String) = HelpSection(title, points.toList())

/** One section per class path: what the class does, its weapons, and its three subclasses. Built from the game's own data. */
private fun classPaths(): List<HelpSection> = listOf(
    com.monsterquest.core.HeroClass.WARRIOR,
    com.monsterquest.core.HeroClass.MAGE,
    com.monsterquest.core.HeroClass.ROGUE,
    com.monsterquest.core.HeroClass.NONE,
).map { cls ->
    val weapons = com.monsterquest.core.WeaponType.entries.filter { cls in it.classes }.joinToString(" and ") { it.label.lowercase() + "s" }
    val what = if (cls == com.monsterquest.core.HeroClass.NONE) "No class: no strengths or weaknesses, and any weapon works" else cls.summary
    HelpSection(
        "${cls.label} path",
        listOf("$what." + if (weapons.isNotEmpty()) " Best with $weapons." else "") +
            com.monsterquest.core.Subclass.entries.filter { it.parent == cls }.map { "${it.label}: ${it.summary}." },
    )
}

private val topics = listOf(
    HelpTopic(
        GameIcon.STAR, "Getting started",
        listOf(
            section(
                "What is Side Quest?",
                "An adventure RPG powered by your real life. Your tasks and calendar are your quests; walking, sleeping well and time off your phone make you stronger.",
                "Get things done for XP and gold, walk for torches, and use torches to explore dungeons where monsters live. Tame them, raise them and take on champions.",
            ),
            section(
                "Getting around",
                "The menu at the bottom: Quests (Home), Map, Bag and Store, with the Hero button in the middle (tap or hold it for your Hero or your Monsters).",
                "The top bar shows your level, gold and torches. The battery button turns on the battery saver (see Walking).",
            ),
            section(
                "First steps, goals and achievements",
                "New here? The First steps checklist on Home walks you through the basics, with a reward for each step. Hide it any time.",
                "Goals on Home: three for today and three for the week. Claim each for gold and an item; finish a whole set for a bonus. Achievements on the Hero tab track lifetime milestones.",
                "Weekly bounty (under Goals): the same three every week, from Sunday to Saturday: walk 40,000 steps, finish two phone-free sessions of 60+ minutes and complete 10 quests. Clear all three for 600 gold, a Resonant Crystal and a guaranteed Rare or Epic weapon or armor piece.",
                "Mystery of the week: a silhouette on Home with clues. Tame one for 500 gold, 10 candy and a Golden Berry. Solved ones move to Mysteries at the bottom of Home.",
            ),
        ),
    ),
    HelpTopic(
        GameIcon.QUESTS, "Quests and focus",
        listOf(
            section(
                "Quests",
                "Your phone's calendar events become quests automatically; use + Task for things without a date. Tap a quest to change its priority.",
                "Completing a quest gives XP, gold and a hunt token. Rewards are capped to keep things fair: a new task pays after 15 minutes (its card shows Reward in X min, and marking it done sooner asks first), the first 8 quests a day pay in full and the next 4 half, and only 3 Hard quests a day pay the Hard rate.",
                "Keeping up with your weekday quests opens the Silver (Tue/Thu) and Gold (weekend) shrines.",
            ),
            section(
                "Phone-free sessions",
                "Pick 15 to 120 minutes and put your phone down. Finish to win a loot box (rarer for longer sessions). Leaving for another app fails it; calls never count.",
                "Long sessions pay extra: party training, Scholar's Tomes (spell pages you assign), Energy, Mood Crystals (60+ minutes) and, at two hours, 5 torches and a rare visitor: an Epic monster that finds you on your next battery-saver walk within 2 hours, and is easier to tame.",
                "Loot boxes open by walking: 1, 3, 5 or 10 miles depending on the box.",
            ),
        ),
    ),
    HelpTopic(
        GameIcon.WALK, "Walking, sleep and health",
        listOf(
            section(
                "Walking and torches",
                "Every 1,000 steps lights a torch. You can hold 10 to start, 5 more every 10 levels, up to 30 at level 40; once full, walking and the morning gift stop adding them until you spend some (chests and other rewards can still take you up to 5 over). You also get 3 every morning. Torches let you into dungeons.",
                "Walking heals: every 100 steps restores 10 HP for your hero and battle party and 2 Energy. It also opens loot boxes, warms eggs and earns buddy candy.",
                "Choose where steps come from in Settings: the phone's step counter, Samsung Health, Health Connect or GPS.",
            ),
            section(
                "Battery saver and wild encounters",
                "Wild monsters don't roam the map any more. Turn on the battery saver (the battery button at the top, or hold the phone upside down on the map) and walk: now and then a monster, or a thug and their crew, finds you. The phone buzzes three times and the fight starts. The battery saver comes back on afterwards.",
                "Food: buy it in the Store and set it out from the Bag. For 30 minutes the monsters that eat it are far likelier to be the ones that find you on battery-saver walks; Fine and Gourmet food make their rarer eaters likelier too. While it's out, its icon and time left show under the compass on the map (beside the map on a foldable).",
                "Turn these encounters off with Ambushes in Settings.",
            ),
            section(
                "Sleep and rest",
                "Sleep syncs from Health Connect (or log it by hand on the Health card). A full night (7+ hours) refills your Energy, once a day.",
                "8 hours or more: you're Well rested, so your hero and monsters fight 10% better all day. Under 7 hours: Tired, 10% weaker for the day.",
            ),
            section(
                "Buddy, eggs and water",
                "Your first party slot is your buddy: it finds its line's candy as you walk (every mile for Common monsters, up to 10 for Legendary).",
                "Eggs: a male and a female of the same line (level 20+) can make one; adventures and nomads bring some too. Carry 5, but walking warms only 3 at a time (tap Warm to swap one in). An Incubator (800 gold) halves an egg's distance once.",
                "The Health card shows steps by hour, water (just for you, no rewards) and sleep. Each morning a recap shows yesterday.",
                "Water history: the History tab under the water tracker lists each day's glasses against that day's goal, with your current and best streak of goal days. The calendar on Home marks goal days with a blue dot, and the water streak above it opens the history. Changing your goal never rewrites past days.",
                "Everyone rests on their own: 5% HP an hour; fainted monsters wake after about 2 hours.",
            ),
        ),
    ),
    HelpTopic(
        GameIcon.MAP, "The map",
        listOf(
            section(
                "Getting around the map",
                "The map follows you. Pinch or double-tap to zoom (out to about a quarter mile), and it tilts toward the horizon as you zoom in. Your position updates every second and your hero walks smoothly between fixes. Drag around yourself or twist two fingers to turn the map. Tap the compass to have the map turn with your phone, so what's in front of you is up the screen (a gold ring shows it's on); tap it again, or turn the map yourself, for north up. It never moves more than a mile away. Coffee shops, doctors, restaurants, groceries, pharmacies, gyms, parks and bars come from the map itself, so they show up even where place lookups are slow. Tap Walk here on anything for directions.",
                "The weather badge shows the real weather. It changes which types find you on battery-saver walks (tap the badge to see how), and rain and heat carry into battles (Rain or Drought). Storms, heatwaves, fog and snow are events: their type surges, and once a day each sends a rare visitor of that type to find you on your next battery-saver walk.",
                "The weather also picks favorites: in battle their monsters (yours and theirs) get +10% Attack and Speed, and the types it holds back get −10% Attack. Clear skies favor Fire and Flight, heat Fire and Earth, cloud and fog Gas and Corrupt, rain and storms Water and Electric, snow Water and Earth, and wind Flight. Rain holds back Fire, wind Gas, and so on: the battle log says who's helped.",
            ),
            section(
                "Item shrines",
                "Blue crystal beacons at real places. Walk within 60 m and tap one for gold, potions, crystals, berries, spell pages and gear. It recharges for 30 minutes.",
                "Shrines you keep visiting bond with you: Familiar (5 visits), Trusted (15) and Bonded (40), each giving more loot and gold and recharging faster.",
            ),
            section(
                "Dens",
                "Rocky caves with glowing eyes. Each day a den takes over some of the map's dungeon and trader spots (at that exact place). Each holds one big monster a few levels above you with great stats. Beat it, then get 5 Binding casts (plus 1 for each of your monsters still standing) to tame it. Dens move every day.",
            ),
            section(
                "Dungeons",
                "Stone archways with torches. Each block (about 300 m) has one, in a coffee shop, doctor's office or restaurant picked fresh each day, so busy downtowns aren't packed and a different café opens tomorrow. Where there are no such places, scattered ones fill in. Rated 1 to 5 stars (stars above the arch); every neighbourhood has a couple of easy ones and at most one 5-star. Tap one to see its level, rooms, the monsters inside, the champion and the reward pool with odds. Entering costs 1 to 3 torches.",
                "Each room offers two doors: monster lairs (tameable, 1 to 4 monsters; easy dungeons send at most 3), Alpha dens, cursed guardians (can't be tamed), traps (you see your odds; fail and everyone takes damage, but you go on), treasuries, fairy fountains (full heal) and quiet rooms. Some doors are hidden. Harder dungeons have more rooms, fights and traps.",
                "Loot: if your bag has room when you go in, everything you find in a dungeon comes home, even past your bag's size (the same for adventures). Once your bag is over its size, nothing more fits until you make room. Go in with a full bag and you can't keep what you find.",
                "At the end waits a champion: a Warrior, Mage or Rogue with their monsters. Beat them for +1 Attack, Magic or Speed for good (once a day, up to 10 per stat for each star rank) and a roll on the reward pool.",
                "Leave whenever you like and keep what you found. If your team falls, you lose half of it.",
            ),
            section(
                "Silver and Gold shrines",
                "Silver shrines open Tue and Thu when you've done 50% of this week's quests (at least 2); Gold shrines on weekends at 70% of your weekday quests (at least 5). Gold ones hold legendaries.",
                "On those days a glowing shrine of that tier also rises within about 0.2 mile of you, and follows you until you use one.",
                "Bosses act twice per round. Defeat a Gold guardian once, then come back to tame it.",
            ),
            section(
                "Services",
                "Pharmacies (green cross): an infirmary that heals your hero and every standing monster to full, free once a day.",
                "Hospitals (red cross): a healing hall that wakes every fainted monster, once a day.",
                "Schools, colleges and universities (purple book): an academy. Universities have 3 study spots around campus and colleges 2; for primary and high schools the spot is on a nearby street, off the school grounds, so you never need to go onto a school campus. Spots by a school are locked from 7 AM to 4 PM on weekdays (a lock shows on the map). Study once a day for a Scholar's Tome of 2 pages for any spell you choose, to learn a new one or rank one up.",
                "Gyms (dumbbell): a training ground. Spend a torch and every party monster gains part of a level: 30%, then 20%, then 10%, for up to 3 sessions a day.",
                "Star ranks: every 50 levels earns a star. Level 51 shows as ★1 · Lv 1, level 101 as ★2 · Lv 1, and your star shows by your name and on your map marker. Nothing drops: your hero keeps all their strength and the world keeps up. Each new star opens a star reward box: an Epic weapon, Epic armor, 3 Prismatic Crystals, 2 Restore Potions and 2 Phoenix Feathers.",
                "Levels stay fair: monsters can't get more than 5 levels above your hero (extra XP is saved and pays out as your hero levels up), and beating foes far below your level gives much less XP.",
                "Libraries and bookstores: their shrines hold extra spell pages.",
                "Parks and gardens host dens of Earth, Flight and Gas monsters; fountains host Water dens.",
                "Bars and nightclubs draw thugs and villains at night (8 PM to 4 AM); by day the streets around them are clear.",
            ),
            section(
                "People you meet",
                "Thugs and villains (dark banners) stand on the streets, changing daily: cursed monsters you can't tame. Beat the whole team for loot, then try a Restore Potion to free one (75%).",
                "Traders (green glow) wait by a grocery store in each neighbourhood (about 400 m), beside its Supply Cache, and sell gear and supplies 20-35% off. Nomads (blue glow) fight alongside two monsters with two swaps each; beat one for gold, Greater Potions and an egg with 3 perfect stats.",
            ),
        ),
    ),
    HelpTopic(
        GameIcon.FIGHT, "Battles",
        listOf(
            section(
                "The basics",
                "Turn-based, in Speed order (the strip at the top shows who's next). Your side matches theirs: one monster means your hero alone, a pack of 3 means your hero and 2 monsters. Dens bring your whole team.",
                "Swap sends a monster in from the bench; your hero can step back and let a monster fight. Every fight is at least your hero and one monster; against a lone foe you pick which monster fights beside you (the rest wait on the bench). If everyone fighting is knocked out (the hero too), it isn't over while someone on the bench can still fight: you pick who steps in. You only lose when nobody is left. Defend blocks all damage until your next turn (not twice in a row).",
                "Battles happen in a painted scene: the dungeon you're in, or the outdoors at this time of day.",
            ),
            section(
                "Moves and types",
                "Each monster has 4 moves with limited uses (each rests back a use every turn it isn't used). Hold a move to see its details.",
                "Seven types, each strong against two and weak to two (the Type chart on the Monsters tab). Super-effective hits do 1.5x. A move of the monster's own type (STAB) also hits 1.5x.",
                "Statuses show on the fighter's picture: sleep (Zz), shock (bolt), poison, burn and frostbite.",
            ),
            section(
                "Losing and healing",
                "Knocked-out monsters wake after about 2 hours, or right away with a Restore Potion. Losing a battle costs 10% of your gold.",
                "Store prices rise if you keep buying the same item (after 3, each costs 15% more, up to 2.5x) and settle back over hours. Walking heals for free.",
            ),
        ),
    ),
    HelpTopic(
        GameIcon.ORB, "Monsters",
        listOf(
            section(
                "Taming",
                "Get a monster below 50% HP, press Tame, pick a Binding Crystal and trace its glyph in one stroke. Your trace is graded: Fizzle (0.3x odds), Faint (0.65x), Good, Strong (1.3x) or Perfect (1.6x). It counts how much of the glyph you cover, how close you stay to the line and how quickly you finish: a fair attempt is Good, a small wobble Strong, and Perfect takes a steady hand and a quick stroke. Scribbling fizzles.",
                "Snap Bind: on the very first turn of a wild fight you can gamble a crystal on a strong catch, even at full health. Land it and the rest scatter; miss and they double down.",
                "Berries (Sweet 1.5x, Golden 2.5x) before a cast make it likelier. Every cast has a 2% critical catch.",
            ),
            section(
                "Candy, evolving and levels",
                "Taming gives candy for the line (5 for first forms, 3 otherwise). Spend it on a monster's page to level up, or to evolve once it reaches its evolution level.",
                "Levelling is slow: it takes a while to raise a monster, so every battle counts.",
                "Alphas: big first forms with 3 perfect stats and a strong move. They wait in dungeons' Alpha dens.",
            ),
            section(
                "Bonds",
                "Every monster builds a bond with you: +1 for each battle it fights in and wins, and +1 for every 1,000 steps it spends in your team (party or swaps). Bond hearts show on its page.",
                "Each bond level (at 15, 40, 80, 140 and 220 points) adds 2% to its HP, Attack, Defense and Speed. At bond 5 it's Steadfast: once a battle, a blow that would knock it out leaves it at 1 HP instead.",
            ),
            section(
                "Team synergy",
                "Some types fight better together. With both types somewhere in your party or swap slots, every monster of yours gets a boost: Storm Front (Water + Electric, +5% Speed), Magma Core (Fire + Earth, +5% Defense), Miasma (Gas + Corrupt, +5% Attack), Wildfire (Fire + Flight, +5% Attack) and Tailwind (Flight + Gas, +5% Speed). Your synergies show above the party on the Monsters tab.",
            ),
            section(
                "Skills, genders and the Journal",
                "Monsters gain a skill at level 30, rolled from a few that suit their kind. Every monster is male or female except legendaries and mythics.",
                "The Journal records every monster you've seen and caught, with lore, max stats and a star rating for its line. Pin a line to your Wanted list there.",
            ),
        ),
    ),
    HelpTopic(
        GameIcon.HERO, "Your hero",
        listOf(
            section(
                "Classes and stats",
                "Each level gives 3 attribute points. At level 5 choose Warrior, Mage or Rogue; at 15 a subclass. Dungeon champions raise a stat for good.",
                "Skills open at levels 30, 45 and 60 and rank up every 10 levels.",
            ),
            section(
                "Choosing your path",
                "At level ${com.monsterquest.core.CLASS_UNLOCK_LEVEL} pick a class on the Hero tab: Warrior, Mage, Rogue, or stay an Adventurer. The first pick is free and comes with a Rare weapon for that path.",
                "At level ${com.monsterquest.core.SUBCLASS_UNLOCK_LEVEL} pick one of your class's three subclasses (free the first time).",
                "You can change later: a new class costs ${com.monsterquest.core.CLASS_CHANGE_COST} gold and clears your subclass; a new subclass costs ${com.monsterquest.core.SUBCLASS_CHANGE_COST} gold.",
                "Weapons and armor made for your class get +20%. Below are the four paths.",
            ),
            *classPaths().toTypedArray(),
            section(
                "Spells",
                "Learn a spell with 3 spell pages (from shrines, chests, champions and Scholar's Tomes), then rank it up on the Hero tab with more pages and gold. Rank 2 opens at hero level 10, rank 3 at 20, rank 4 at 30 and rank 5 at 40.",
                "Spell paths: at rank 5 every spell can follow one of two paths, for example a bolt that splits to hit every foe or one that hits harder and burns, shocks or poisons; Mend can cure status or turn extra healing into a shield; Aegis can be bigger or cover the whole team. Each path has 5 mastery ranks (pages and gold) that open from level 45 to 65. Switching paths costs 1,000 gold and keeps your mastery.",
                "Attack spells grow with you: a Mage who puts most points into Magic hits a little harder than a first-form monster of their level with a rank-3 bolt, and about as hard as a fully evolved one at rank 5. That power costs Energy: bolts take 10 and Mud Sling 7, and Mages pay ${com.monsterquest.core.Spells.MAGE_ATTACK_DISCOUNT_PCT}% less for attack spells (8 and 5).",
                "In battle, healing spells show how much they'll heal (with your path and mastery), and the target list shows each ally's HP bar filling.",
                "Bolts of every type, Mud Sling, and support magic: Mend (heal), Aegis (a shield worth half your max HP, more as it ranks up), Fortify (Defense), Haste (Speed), Empower (Attack), Cleanse (cures status and lowered stats) and Healing Circle (heals the whole team).",
                "Energy comes back slowly, faster by walking, at once with a Mana Draught, or with a full night's sleep.",
            ),
            section(
                "Gear",
                "Armor comes in five slots, with at least two kinds each: body (Plate for Warriors, Leather for Rogues, Robe for Mages), head (Hood or Helm), back (Cloak or Mantle), legs (Greaves or Leggings) and feet (Boots or Sandals). Each kind suits certain classes, which get +20% from it.",
                "Weapons and armor of your class get +20% and extra stats by rarity. Better materials turn up as you level; diamond is always rare. Smelt a weapon for its parts and forge new gear from parts.",
                "Adventures (Monsters tab) send a team to real places for gold, items and sometimes eggs while you're away.",
            ),
        ),
    ),
)

/** How to play, in short topics you can open one at a time. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<Int?>(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Column(Modifier.widthIn(max = 640.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconText(GameIcon.BOOK, "How to play", tint = Gold, textColor = Gold, size = 22.dp, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Back") }
            }
            Text(
                "An adventure RPG powered by your real life: get things done, get outside and rest well to grow stronger.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            topics.forEachIndexed { i, t ->
                val expanded = open == i
                GameCard(
                    Modifier.padding(vertical = 4.dp).animateContentSize(),
                    border = if (expanded) Gold else null,
                ) {
                    Row(Modifier.clickable { open = if (expanded) null else i }, verticalAlignment = Alignment.CenterVertically) {
                        GIcon(t.icon, Modifier.size(22.dp), Gold)
                        Spacer(Modifier.width(10.dp))
                        Text(t.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (expanded) "-" else "+", color = Gold, fontSize = 18.sp)
                    }
                    if (expanded) {
                        // Subsections, each folding on its own.
                        t.sections.forEach { sec -> HelpSectionRow(sec) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpSectionRow(sec: HelpSection) {
    var open by rememberSaveable(sec.title) { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp).animateContentSize()) {
        Row(Modifier.clickable { open = !open }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(sec.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
            Text(if (open) "\u25B2" else "\u25BC", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            sec.points.forEach { p ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•  ", color = Gold, fontSize = 13.sp)
                    Text(p, fontSize = 13.sp)
                }
            }
        }
    }
}
