package com.monsterquest.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import com.monsterquest.core.WeaponType
import com.monsterquest.core.ItemKind
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Gear
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Armor
import com.monsterquest.core.GameEngine
import com.monsterquest.core.Item
import com.monsterquest.core.MAX_ITEMS_PER_BATTLE
import com.monsterquest.core.Spells
import com.monsterquest.core.Hero
import com.monsterquest.core.PROFICIENCY_BATTLES
import com.monsterquest.core.EXOTIC_MASTERY_BATTLES
import com.monsterquest.core.SMELT_COST
import com.monsterquest.core.Trait
import com.monsterquest.core.PARTS_TO_FORGE
import com.monsterquest.core.smeltParts

@Composable
fun BagScreen(vm: GameViewModel) {
    val s = vm.state
    var using by remember { mutableStateOf<Item?>(null) }
    val itemRow: @Composable (Item) -> Unit = { item ->
        GameCard(Modifier.padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(item.icon(), Modifier.size(28.dp), item.color())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${item.label} x${s.count(item)}", fontWeight = FontWeight.SemiBold)
                    Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.kind == ItemKind.HEAL_HP) SmallButton("Use", { using = item }, outlined = true)
                if (item.kind == ItemKind.HEAL_MP) {
                    SmallButton("Use", { vm.usePotion(item, com.monsterquest.core.Combatant.HERO_KEY) }, outlined = true, enabled = s.heroEnergyUsed > 0)
                }
                if (item.kind == ItemKind.FOOD) SmallButton("Set out", { vm.useFood(item) }, outlined = true)
                TextButton(onClick = { vm.discard(item) }) { Text("Drop") }
            }
        }
    }
    val bag: @Composable () -> Unit = {
        LootBoxSection(vm)
        EggsSection(vm)
        SectionTitle("Bag ${s.bagCount}/${s.bagCapacity}")
        LabeledBar("Space", s.bagCount, s.bagCapacity, if (s.bagFull) HpRed else MpBlue)
        if (s.bagCount > s.bagCapacity) {
            // Level-up boxes can overfill the bag; nothing is lost, but nothing more fits until there's room.
            Text(
                "Your bag is over full (${s.bagCount}/${s.bagCapacity}). You keep everything, but you can't pick up or buy " +
                    "anything until you use, sell or drop ${s.bagCount - s.bagCapacity + 1} item(s).",
                fontSize = 12.sp,
                color = HpRed,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            "You can use up to $MAX_ITEMS_PER_BATTLE items per battle (Binding Crystals and berries don't count). Bag size grows with upgrades every 5 levels.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        val owned = Item.entries.filter { s.count(it) > 0 }
        if (owned.isEmpty()) Text("Your bag is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Items by kind, each in a section that folds away.
        BAG_GROUPS.forEach { group ->
            val items = owned.filter { it.kind in group.kinds }
            if (items.isEmpty()) return@forEach
            BagFold(group.title, group.icon, group.tint, "${items.sumOf { s.count(it) }} • ${items.size} kind${if (items.size == 1) "" else "s"}", startOpen = group.startOpen) {
                items.forEach { item -> itemRow(item) }
            }
        }
    }
    val gear: @Composable () -> Unit = {
        val klepto = s.hero.rankOf(Trait.KLEPTOMANIAC) > 0
        BagFold(
            "Weapons", GameIcon.SWORD, Gold,
            (s.equippedGear?.let { "${it.name} equipped • " } ?: "") + "${s.gear.size} spare",
            startOpen = true,
        ) {
        Text(
            "Any class can use any weapon. Your class's own types (Warrior: sword, broadsword • Rogue: dagger, sword • " +
                "Mage: staff, wand) get +20% power and +2/4/6/8 to all stats by rarity. Others take that much off until " +
                "you've fought $PROFICIENCY_BATTLES battles with that weapon.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // What materials turn up at this level, and the next milestone.
        val tier = com.monsterquest.core.Armory.materialTier(s.hero.level)
        val nextTier = com.monsterquest.core.Armory.nextMaterialTier(s.hero.level)
        Text(
            "Gear you find now: ${tier.note.lowercase()}." + (nextTier?.let { " At level ${it.fromLevel}: ${it.note.lowercase()}." } ?: ""),
            fontSize = 12.sp,
            color = Gold,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        s.equippedGear?.let { w ->
            GearCard(w, s.hero, equipped = true) {
                TextButton(onClick = vm::unequip) { Text("Remove") }
            }
        }
        s.offhandGear?.let { w ->
            GearCard(w, s.hero, equipped = true, where = "off hand") {
                TextButton(onClick = vm::unequipOffhand) { Text("Remove") }
            }
        }
        if (s.gear.isEmpty() && s.equippedGear == null) {
            Text("No weapons yet. Item shrines, villains and boxes drop them, and parts can be forged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.gear.sortedWith(compareByDescending<Gear> { it.rarity }.thenByDescending { it.level }).forEach { w ->
            GearCard(w, s.hero, equipped = false) {
                TextButton(onClick = { vm.dropWeapon(w.id) }) { Text("Drop") }
                if (klepto) SmallButton("Off hand", { vm.equipOffhand(w.id) }, outlined = true)
                Spacer(Modifier.width(6.dp))
                SmallButton("Equip", { vm.equip(w.id) })
            }
        }
        }
        BagFold("Armor", GameIcon.PLATE, Gold, "${s.wornArmor.size} worn • ${s.armor.size} spare") {
        Text(
            "Wear one piece in each slot: body (Plate for Warriors, Leather for Rogues, Robes for Mages), " +
                "a hood, a cloak, pants and boots. Each has its own buffs; Rare and better add more. Anyone can wear anything.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.wornArmor.forEach { a ->
            ArmorCard(a, s.hero.heroClass, equipped = true) {
                TextButton(onClick = { vm.unequipArmor(a.type.slot) }) { Text("Remove") }
            }
        }
        if (s.armor.isEmpty() && s.wornArmor.isEmpty()) {
            Text("No armor yet. Shrines, villain camps and loot boxes drop it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.armor.sortedWith(compareByDescending<Armor> { it.rarity }.thenByDescending { it.level }).forEach { a ->
            ArmorCard(a, s.hero.heroClass, equipped = false) {
                TextButton(onClick = { vm.dropArmor(a.id) }) { Text("Drop") }
                SmallButton("Wear", { vm.equipArmor(a.id) })
            }
        }
        }
        // All candy, folded into one line until opened.
        val candies = s.candy.filterValues { it > 0 }
        if (candies.isNotEmpty()) {
            BagFold("Candy", GameIcon.CANDY, Gold, "${candies.values.sum()} • ${candies.size} line${if (candies.size == 1) "" else "s"}") {
                Text("Doesn't take bag space. Spend it on a monster's page.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                candies.entries.sortedByDescending { it.value }.forEach { (family, n) ->
                    val first = com.monsterquest.core.Bestiary.all.first { it.family == family }
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.monsterquest.app.ui.art.MonsterArt(first, Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(GameEngine.candyName(first), modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Text("x$n", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
        val partsTotal = WeaponType.entries.sumOf { s.parts[it] ?: 0 }
        val partsReady = WeaponType.entries.count { (s.parts[it] ?: 0) >= PARTS_TO_FORGE }
        BagFold("Weapon parts", GameIcon.PARTS, SilverColor, "$partsTotal" + if (partsReady > 0) " • $partsReady ready to forge" else "", startOpen = partsReady > 0) {
        WeaponType.entries.forEach { type ->
            val n = s.parts[type] ?: 0
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(type.icon(), Modifier.size(20.dp), SilverColor)
                Spacer(Modifier.width(8.dp))
                Text("${type.label} parts $n/${PARTS_TO_FORGE}", modifier = Modifier.weight(1f))
                SmallButton("Forge", { vm.forge(type) }, enabled = n >= PARTS_TO_FORGE && !s.bagFull, outlined = true)
            }
        }
        }
        // Armor scrap: from thugs, villains and smelting armor.
        val scrapTotal = com.monsterquest.core.ArmorType.entries.sumOf { s.armorParts[it] ?: 0 }
        val scrapReady = com.monsterquest.core.ArmorType.entries.count { (s.armorParts[it] ?: 0) >= PARTS_TO_FORGE }
        BagFold("Armor scrap", GameIcon.PLATE, SilverColor, "$scrapTotal" + if (scrapReady > 0) " • $scrapReady ready to forge" else "", startOpen = scrapReady > 0) {
        com.monsterquest.core.ArmorType.entries.forEach { type ->
            val n = s.armorParts[type] ?: 0
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(type.icon(), Modifier.size(20.dp), SilverColor)
                Spacer(Modifier.width(8.dp))
                Text("${type.label} scrap $n/${PARTS_TO_FORGE}", modifier = Modifier.weight(1f))
                SmallButton("Forge", { vm.forgeArmor(type) }, enabled = n >= PARTS_TO_FORGE && !s.bagFull, outlined = true)
            }
        }
        }
        val pages = s.spellPages.filterValues { it > 0 }
        if (pages.isNotEmpty()) {
            BagFold("Spell sheets", GameIcon.PAGE, Gold, "${pages.values.sum()} pages • ${pages.size} spell${if (pages.size == 1) "" else "s"}") {
            pages.forEach { (id, n) ->
                val sp = Spells.byId(id) ?: return@forEach
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(GameIcon.PAGE, Modifier.size(18.dp), Gold)
                    Spacer(Modifier.width(6.dp))
                    GIcon(sp.spellIcon(), Modifier.size(16.dp), sp.spellColor())
                    Spacer(Modifier.width(6.dp))
                    val rank = s.hero.spellRank(id)
                    Text(
                        if (rank == 0) "${sp.name}: $n/${Spells.PAGES_TO_LEARN} pages to learn"
                        else "${sp.name}: $n pages (rank up on the Hero tab)",
                    )
                }
            }
            }
        }
    }
    AdaptivePanes(
        compact = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                bag()
                gear()
                Spacer(Modifier.height(24.dp))
            }
        },
        first = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp)) {
                bag()
                Spacer(Modifier.height(24.dp))
            }
        },
        second = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 16.dp)) {
                gear()
                Spacer(Modifier.height(24.dp))
            }
        },
    )
    using?.let { item -> PotionTargetDialog(vm, item) { using = null } }
}

/** The Store: storage expansions and items to buy, and the counter that buys back what you don't need. */
@Composable
fun StoreScreen(vm: GameViewModel) {
    val s = vm.state
    val buy: @Composable () -> Unit = {
        SectionTitle("Store  •  ${s.gold} gold")
        StorageShop(vm)
        Item.entries.filter { it.price > 0 && it.kind != ItemKind.FOOD && it.kind != ItemKind.LURE }.forEach { item ->
            GameCard(Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(item.icon(), Modifier.size(28.dp), item.color())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, fontWeight = FontWeight.SemiBold)
                        Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MarkupNote(s, item)
                    }
                    val price = com.monsterquest.core.Prices.unitPrice(s, item, System.currentTimeMillis())
                    if (s.hero.level < item.minLevel) {
                        // Locked until the hero is strong enough.
                        Button(onClick = {}, enabled = false) { IconText(GameIcon.LOCK, "Lv ${item.minLevel}") }
                    } else {
                        Button(onClick = { vm.buy(item) }, enabled = s.gold >= price && !s.bagFull) {
                            IconText(GameIcon.COIN, "%,d".format(price))
                        }
                    }
                }
            }
        }
    }

    val food: @Composable () -> Unit = {
        SectionTitle("Food")
        Text(
            "Set food out from the Bag, turn on the battery saver and go for a walk: for 30 minutes the monsters that find you are far likelier to be ones that eat it.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        com.monsterquest.core.Foods.all.forEach { FoodStoreCard(vm, it) }
    }
    AdaptivePanes(
        compact = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                buy()
                food()
                SellSection(vm)
                Spacer(Modifier.height(24.dp))
            }
        },
        first = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp)) {
                buy()
                food()
                Spacer(Modifier.height(24.dp))
            }
        },
        second = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 16.dp)) {
                SellSection(vm)
                Spacer(Modifier.height(24.dp))
            }
        },
    )
}

/** The shop buys anything that isn't worn: items at half price, gear by rarity and level. Weapons can be smelted. */
@Composable
private fun SellSection(vm: GameViewModel) {
    val s = vm.state
    SectionTitle("Sell")
    Text(
        "The Store pays half price for items; rarer and higher-level gear pays more. Smelt a weapon or armor for $SMELT_COST gold " +
            "to get parts or scrap of its type (1 to 4 by rarity). Take gear off to sell it.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val owned = Item.entries.filter { s.count(it) > 0 }
    if (owned.isEmpty() && s.gear.isEmpty() && s.armor.isEmpty()) {
        Text("Nothing to sell.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    owned.forEach { item ->
        SellRow(item.icon(), item.color(), "${item.label} x${s.count(item)}", null) {
            SmallButton("Sell ${item.sellPrice}", { vm.sell(item) }, outlined = true)
        }
    }
    s.gear.sortedWith(compareByDescending<Gear> { it.rarity }.thenByDescending { it.level }).forEach { w ->
        SellRow(w.type.icon(), w.rarity.color(), "${w.rarity.label} ${w.name}", "Lv ${w.level} ${w.type.label}") {
            SmallButton("Smelt +${w.rarity.smeltParts}", { vm.smelt(w.id) }, enabled = s.gold >= SMELT_COST, outlined = true)
            Spacer(Modifier.width(6.dp))
            SmallButton("Sell ${w.sellPrice}", { vm.sellWeapon(w.id) }, outlined = true)
        }
    }
    s.armor.sortedWith(compareByDescending<Armor> { it.rarity }.thenByDescending { it.level }).forEach { a ->
        SellRow(a.type.icon(), a.rarity.color(), "${a.rarity.label} ${a.name}", "Lv ${a.level} ${a.type.label}") {
            SmallButton("Smelt +${a.rarity.smeltParts}", { vm.smeltArmor(a.id) }, enabled = s.gold >= SMELT_COST, outlined = true)
            Spacer(Modifier.width(6.dp))
            SmallButton("Sell ${a.sellPrice}", { vm.sellArmor(a.id) }, outlined = true)
        }
    }
}

@Composable
private fun SellRow(icon: GameIcon, tint: androidx.compose.ui.graphics.Color, title: String, detail: String?, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, Modifier.size(24.dp), tint)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = tint, fontSize = 14.sp)
            detail?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        actions()
    }
}

/** One weapon: rarity-colored name, level and type, stats for your class, and its effects. */
@Composable
private fun GearCard(w: Gear, hero: Hero, equipped: Boolean, where: String = "equipped", actions: @Composable () -> Unit) {
    val cls = hero.heroClass
    GameCard(Modifier.padding(vertical = 3.dp), border = if (equipped) Gold else w.rarity.color().copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(w.type.icon(), Modifier.size(30.dp), w.rarity.color())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${w.rarity.label} ${w.name}" + if (equipped) " ($where)" else "", fontWeight = FontWeight.SemiBold, color = w.rarity.color())
                Text("Lv ${w.level} ${w.type.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                weaponClassNote(w, hero)?.let { (text, color) -> Text(text, fontSize = 12.sp, color = color) }
                Text(w.summary(cls), fontSize = 12.sp)
                // A relic's line of lore, and an exotic weapon's trade-off.
                w.relic?.let { Text("“${it.lore}”", fontSize = 12.sp, color = Gold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
                if (w.type.exotic) Text("Exotic: ${w.type.drawback}. ${EXOTIC_MASTERY_BATTLES} battles to master.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                w.curse?.let { c ->
                    Text(
                        if (w.cursed) "Cursed (${w.cursePct}% left) • ${c.label}: ${c.weaponText}" else "${c.label}: ${c.weaponText}",
                        fontSize = 11.sp,
                        color = if (w.cursed) XpPurple else HpGreen,
                    )
                }
                w.effects.forEach { fx ->
                    Text("${fx.label}: ${fx.description}", fontSize = 11.sp, color = XpPurple)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
private fun ArmorCard(a: Armor, cls: HeroClass, equipped: Boolean, actions: @Composable () -> Unit) {
    GameCard(Modifier.padding(vertical = 3.dp), border = if (equipped) Gold else a.rarity.color().copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(a.type.icon(), Modifier.size(30.dp), a.rarity.color())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${a.rarity.label} ${a.name}" + if (equipped) " (worn)" else "", fontWeight = FontWeight.SemiBold, color = a.rarity.color())
                Text(
                    "Lv ${a.level} ${a.type.label} (${a.type.slot.label.lowercase()})" + if (a.matches(cls)) " • Class bonus" else "",
                    fontSize = 12.sp,
                    color = if (a.matches(cls)) HpGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(a.summary(cls), fontSize = 12.sp)
                a.curse?.let { c ->
                    Text(
                        if (a.cursed) "Cursed (${a.cursePct}% left) • ${c.label}: ${c.armorText}" else "${c.label}: ${c.armorText}",
                        fontSize = 11.sp,
                        color = if (a.cursed) XpPurple else HpGreen,
                    )
                }
                a.effects.forEach { fx ->
                    Text("${fx.label}: ${fx.description}", fontSize = 11.sp, color = XpPurple)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

/** Pick who drinks a healing potion outside battle. */
@Composable
fun PotionTargetDialog(vm: GameViewModel, item: Item, onDone: () -> Unit) {
    val s = vm.state
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Use ${item.label} on…") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val heroHp = s.hero.maxHp - s.heroDamage
                TargetRow("You", heroHp, s.hero.maxHp, com.monsterquest.core.GameEngine.potionHeal(item, s.hero.maxHp)) {
                    vm.usePotion(item, com.monsterquest.core.Combatant.HERO_KEY)
                    onDone()
                }
                // Hurt monsters first, most hurt at the top; those at full HP after.
                s.monsters.filter { !it.fainted }
                    .sortedWith(compareBy<com.monsterquest.core.Monster> { it.damage <= 0 }.thenBy { it.currentHp.toFloat() / it.stats.hp })
                    .forEach { m ->
                        TargetRow(m.displayName, m.currentHp, m.stats.hp, com.monsterquest.core.GameEngine.potionHeal(item, m.stats.hp)) {
                            vm.usePotion(item, m.uid)
                            onDone()
                        }
                    }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/**
 * One potion target: name, an HP bar and, if hurt, how far this potion would
 * fill it (the lighter part of the bar) and the HP it would end on.
 */
@Composable
private fun TargetRow(name: String, hp: Int, maxHp: Int, heal: Int, onPick: () -> Unit) {
    val hurt = hp < maxHp
    val after = minOf(maxHp, hp + heal)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(
                if (hurt) "$hp → $after HP (+${after - hp})" else "$hp/$maxHp HP",
                fontSize = 12.sp,
                color = if (hurt) HpGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            HealPreviewBar(hp, after, maxHp)
        }
        SmallButton(if (hurt) "Heal" else "Full HP", onPick, enabled = hurt)
    }
}

/**
 * Storage in the shop: claim the free upgrade earned every few levels, or buy
 * more room. Bought expansions add a little and get pricier each time, so
 * storage grows slowly.
 */
@Composable
private fun StorageShop(vm: GameViewModel) {
    val s = vm.state
    GameCard(Modifier.padding(vertical = 3.dp), border = if (s.pendingUpgrades > 0) Gold else null) {
        Text("Storage", fontWeight = FontWeight.SemiBold)
        Text(
            "Bag ${s.bagCount}/${s.bagCapacity}   •   Monsters ${s.monsters.size}/${s.monsterCapacity}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (s.pendingUpgrades > 0) {
            Text("Free upgrade earned! (${s.pendingUpgrades} to claim)", color = Gold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            SmallButton(
                "Claim: bag +${com.monsterquest.core.BAG_UPGRADE_AMOUNT} and monsters +${com.monsterquest.core.STORAGE_UPGRADE_AMOUNT}",
                vm::upgrade,
                Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            val next = (s.hero.level / com.monsterquest.core.UPGRADE_LEVEL_INTERVAL + 1) * com.monsterquest.core.UPGRADE_LEVEL_INTERVAL
            Text(
                "Next free upgrade at level $next.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    listOf(true, false).forEach { bag ->
        val cost = com.monsterquest.core.GameEngine.expansionCost(s, bag)
        GameCard(Modifier.padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(if (bag) GameIcon.BAG else GameIcon.MONSTERS, Modifier.size(28.dp), Gold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (bag) "Bag expansion" else "Monster storage expansion", fontWeight = FontWeight.SemiBold)
                    Text(
                        "+${com.monsterquest.core.PAID_UPGRADE_AMOUNT} ${if (bag) "bag slots" else "monster spaces"}. Each one costs more than the last.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { vm.buyStorage(bag) }, enabled = s.gold >= cost) {
                    IconText(GameIcon.COIN, "%,d".format(cost))
                }
            }
        }
    }
}

/** Who a food draws: the lines you've met by name, the rest as a count. */
internal fun foodEatersText(s: com.monsterquest.core.GameState, food: Item): String {
    val lines = com.monsterquest.core.Foods.eaters(food).map { com.monsterquest.core.Bestiary.lineOf(it).first() }.distinctBy { it.family }
    val known = lines.filter { sp -> com.monsterquest.core.Bestiary.lineOf(sp).any { it.id in s.seen } }
    val unknown = lines.size - known.size
    return "Draws: " + (known.map { it.name } + listOfNotNull(if (unknown > 0) "$unknown line${if (unknown == 1) "" else "s"} you haven't met" else null)).joinToString() +
        " (30 min, ${com.monsterquest.core.Foods.BOOST.toInt()}x as often)"
}

/** One food in the Store with its three tiers to buy (locked until their level). */
@Composable
private fun FoodStoreCard(vm: GameViewModel, food: Item) {
    val s = vm.state
    GameCard(Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(food.icon(), Modifier.size(28.dp), food.color())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(food.label, fontWeight = FontWeight.SemiBold)
                Text(food.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(foodEatersText(s, food), fontSize = 11.sp, color = Gold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.monsterquest.core.Foods.tiers(food).forEach { tier ->
                val label = com.monsterquest.core.Foods.TIER_LABELS[com.monsterquest.core.Foods.tier(tier) - 1]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (s.hero.level < tier.minLevel) {
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { IconText(GameIcon.LOCK, "Lv ${tier.minLevel}", fontSize = 12.sp) }
                    } else {
                        val price = com.monsterquest.core.Prices.unitPrice(s, tier, System.currentTimeMillis())
                        Button(onClick = { vm.buy(tier) }, enabled = s.gold >= price && !s.bagFull, modifier = Modifier.fillMaxWidth()) {
                            IconText(GameIcon.COIN, "%,d".format(price), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** A kind of item in the Bag, shown in its own folding section. */
private class BagGroup(val title: String, val icon: GameIcon, val tint: androidx.compose.ui.graphics.Color, val kinds: Set<ItemKind>, val startOpen: Boolean = false)

private val BAG_GROUPS = listOf(
    BagGroup("Healing", GameIcon.POTION, androidx.compose.ui.graphics.Color(0xFF9CD2BD), setOf(ItemKind.HEAL_HP, ItemKind.HEAL_MP, ItemKind.REVIVE, ItemKind.RESTORE), startOpen = true),
    BagGroup("Binding Crystals", GameIcon.CRYSTAL, androidx.compose.ui.graphics.Color(0xFFC3A6F0), setOf(ItemKind.CAPTURE)),
    BagGroup("Baits & berries", GameIcon.BERRY, androidx.compose.ui.graphics.Color(0xFFE5739A), setOf(ItemKind.BAIT)),
    BagGroup("Food", GameIcon.PAW, androidx.compose.ui.graphics.Color(0xFFE07A6E), setOf(ItemKind.FOOD)),
    BagGroup("Treats", GameIcon.LURE, androidx.compose.ui.graphics.Color(0xFFF2A198), setOf(ItemKind.LURE, ItemKind.TREAT, ItemKind.PERSONALITY, ItemKind.INCUBATOR)),
)

/**
 * A Bag section that folds to one line (title and a short summary) and opens
 * with a tap. Remembers whether it's open while you move around the app.
 */
@Composable
private fun BagFold(
    title: String,
    icon: GameIcon,
    tint: androidx.compose.ui.graphics.Color,
    summary: String,
    startOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(startOpen) }
    GameCard(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            GIcon(icon, Modifier.size(20.dp), tint)
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$summary  ${if (open) "\u25B2" else "\u25BC"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (open) {
            Column(Modifier.padding(top = 4.dp)) { content() }
        }
    }
}

/** "Selling fast: +30%" on an item you've been buying a lot, with when it eases. */
@Composable
private fun MarkupNote(s: com.monsterquest.core.GameState, item: Item) {
    val m = com.monsterquest.core.Prices.multiplier(s, item, System.currentTimeMillis())
    if (m <= 1.0) return
    Text(
        "Selling fast: +${Math.round((m - 1) * 100)}%. Prices ease each hour and reset tomorrow. Walking heals for free!",
        fontSize = 11.sp,
        color = HpYellow,
    )
}
