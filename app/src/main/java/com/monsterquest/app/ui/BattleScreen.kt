package com.monsterquest.app.ui

import androidx.activity.compose.BackHandler
import com.monsterquest.core.Bestiary
import com.monsterquest.app.ui.art.HeroArt
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.monsterquest.core.MoveEffect
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.ThugArt
import com.monsterquest.core.HeroClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.BattleView
import com.monsterquest.app.FighterView
import com.monsterquest.core.BattleAction
import com.monsterquest.core.BattleOutcome
import com.monsterquest.core.Element
import com.monsterquest.core.EncounterType
import com.monsterquest.core.Item
import com.monsterquest.core.ItemKind
import com.monsterquest.core.Move
import com.monsterquest.core.Skill
import com.monsterquest.core.SUPPORT_KINDS
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import com.monsterquest.core.LevelUp
import com.monsterquest.core.Weather

private sealed interface Menu {
    data object Main : Menu
    data object Magic : Menu
    data object Items : Menu
    data object Tame : Menu
    /** Pick an ally for a heal spell or item. */
    data class Target(val spell: Skill? = null, val item: Item? = null) : Menu
    /** Pick who comes in from the swap slots ([inKey] null), then whose place they take. */
    data class Swap(val inKey: String? = null) : Menu
}

@Composable
fun BattleScreen(
    b: BattleView,
    onAction: (BattleAction) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit = {},
    outfit: HeroOutfit? = null,
    onTarget: (String) -> Unit = {},
    /** Send a swap monster in for one that just fainted (incoming uid, fallen key). */
    onReplace: (String, String) -> Unit = { _, _ -> },
    /** A monster learns a new move in place of one it knows (monster uid, new move id, replaced move id). */
    onLearn: (String, String, String) -> Unit = { _, _, _ -> },
    /** Animation speed: 1 normal, 0.5 fast, 0 instant. */
    speed: Float = 1f,
    /** Where the fight happens, for the painted background (null: plain). */
    scene: Scene? = null,
) {
    CompositionLocalProvider(LocalOutfit provides outfit, LocalScene provides scene) { BattleContent(b, onAction, onClose, onNext, onTarget, onReplace, onLearn, speed) }
}

/** The battle's scene, for its backdrop. */
private val LocalScene = staticCompositionLocalOf<Scene?> { null }

/** What the hero is wearing, so their battle sprite matches their equipment. */
private val LocalOutfit = staticCompositionLocalOf<HeroOutfit?> { null }

@Composable
private fun BattleContent(
    b: BattleView,
    onAction: (BattleAction) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onTarget: (String) -> Unit,
    onReplace: (String, String) -> Unit,
    onLearn: (String, String, String) -> Unit,
    speed: Float,
) {
    var menu by remember { mutableStateOf<Menu>(Menu.Main) }
    // After a Binding cast, the crystal shakes before the result shows.
    var sealSeen by remember { mutableIntStateOf(b.castId) }
    val sealing = b.castRoll != null && b.castId != sealSeen
    b.castRoll?.takeIf { sealing }?.let { roll -> BindingSeal(roll, Gold) { sealSeen = b.castId } }
    // When a monster faints and someone waits in a swap slot, offer to send them in (once per faint).
    val asked = remember { mutableStateListOf<String>() }
    // With everyone out front down it isn't over: someone from the bench must step in.
    val forced = b.mustSwap && b.outcome == BattleOutcome.ONGOING
    val fallen = if (forced) b.allies.firstOrNull { !it.alive } else b.allies.firstOrNull { !it.alive && it.key !in asked }
    if (fallen != null && b.outcome == BattleOutcome.ONGOING && b.swaps.any { it.alive }) {
        AlertDialog(
            onDismissRequest = { if (!forced) asked += fallen.key },
            title = { Text(if (fallen.isHero) "${fallen.name} is down!" else "${fallen.name} fainted!") },
            text = {
                Column {
                    Text(
                        if (forced) "It's not over: send in someone from the bench to keep fighting."
                        else "Send in someone from the bench? It won't use a turn.",
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    b.swaps.filter { it.alive }.forEach { w ->
                        OutlinedButton(onClick = { asked += fallen.key; onReplace(w.key, fallen.key) }, modifier = Modifier.fillMaxWidth()) {
                            FighterArt(w, Modifier.size(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${w.name}  Lv ${w.level}  ${w.hp}/${w.maxHp} HP")
                        }
                    }
                }
            },
            confirmButton = { if (!forced) TextButton(onClick = { asked += fallen.key }) { Text("Not now") } },
        )
    }
    val fx = rememberBattleFx(b, speed)
    // Back closes a sub-menu (Magic, Items, Tame...). It never quits a fight in
    // progress (use Flee), and leaves the result screen once the fight is over.
    BackHandler {
        when {
            b.outcome != BattleOutcome.ONGOING -> onClose()
            menu != Menu.Main -> menu = Menu.Main
        }
    }
    // Return to the main menu whenever the turn passes to someone else.
    LaunchedEffect(b.current?.key, b.round, b.log.size) { menu = Menu.Main }

    // Enemy + log on top / left; party + commands on bottom / right. On a
    // half-folded phone in tabletop posture the controls sit on the lower half.
    val top: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().clipToBounds()) {
            EnemyPanel(b, onTarget)
            Spacer(Modifier.height(8.dp))
            BattleLog(b.log, Modifier.weight(1f))
        }
    }
    val team: @Composable () -> Unit = {
        TurnOrderStrip(b) { f, m -> FighterArt(f, m) }
        AllyRow(b)
    }
    val commands: @Composable () -> Unit = {
        if (sealing) {
            // The crystal is still shaking: no result or commands yet.
            Spacer(Modifier.height(1.dp))
        } else if (b.outcome == BattleOutcome.ONGOING) {
            val actor = b.current
            if (actor != null) {
                ActionPanel(b, actor, menu, onMenu = { menu = it }, onAction = onAction)
            }
        } else {
            LevelUpGate(b, onClose, onNext, onLearn) { close, next -> ResultPanel(b, close, next) }
        }
    }
    val controls: @Composable () -> Unit = {
        Column {
            team()
            Spacer(Modifier.height(8.dp))
            commands()
        }
    }
    // Half-open phone (tabletop): the lower half is short and wide, so put your
    // team on the left and the moves on the right instead of stacking them.
    val fold = LocalFold.current
    val tabletop = fold != null && fold.isSeparating && !fold.isVertical

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CompositionLocalProvider(LocalBattleFx provides fx) {
            Box(Modifier.fillMaxSize()) {
                // The place the fight happens, faintly behind everything.
                LocalScene.current?.let { Backdrop(it, Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f }) }
                AdaptivePanes(
                    modifier = Modifier.safeDrawingPadding().padding(12.dp),
                    compact = {
                        // Your team and the moves come first: they may use up to ~68% of the
                        // height so the moves fit without scrolling; the log shrinks instead.
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val controlsMax = maxHeight * 0.68f
                            Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { top() }
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().heightIn(max = controlsMax)) { controls() }
                            }
                        }
                    },
                    first = top,
                    second = {
                        if (tabletop) {
                            Row(Modifier.fillMaxSize()) {
                                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { team() }
                                Spacer(Modifier.width(12.dp))
                                // The action panel scrolls by itself; give it the full height of the half.
                                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.Center) { commands() }
                            }
                        } else {
                            Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.BottomCenter) { controls() }
                        }
                    },
                )
                // An ambush: a warning flashes over the fight as it starts.
                if (b.ambush) AmbushOverlay(b)
                // Projectiles, slashes and damage numbers fly over everything.
                BattleFxOverlay()
            }
        }
    }
}

@Composable
private fun EnemyPanel(b: BattleView, onTarget: (String) -> Unit) {
    val e = b.enemy
    if (b.weather != Weather.CLEAR || b.reversedOrder) {
        Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            b.weather.icon()?.let { IconText(it, b.weather.label, tint = MpBlue, textColor = MpBlue, fontSize = 13.sp) }
            if (b.reversedOrder) IconText(GameIcon.WING, "Slowest move first", tint = MpBlue, textColor = MpBlue, fontSize = 13.sp)
        }
    }
    if (b.isPack) {
        PackPanel(b, onTarget)
        return
    }
    GameCard(border = if (b.isBoss) BossRed else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FoeArt(e, 96)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (b.encounter == EncounterType.DEN) {
                    Text("DEN BEAST", color = HpYellow, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                if (b.isBoss) {
                    val silver = b.encounter == EncounterType.SILVER_BOSS
                    Text(
                        if (silver) "SILVER GUARDIAN" else "BOSS",
                        color = if (silver) SilverColor else BossRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${e.name}  Lv ${e.level}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (b.alreadyCaught(e)) CaughtMark(Modifier.padding(start = 6.dp).size(18.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconText(e.element.icon(), e.element.label, tint = e.element.color(), fontSize = 13.sp, size = 14.dp)
                    e.element2?.let { t ->
                        Text(" / ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconText(t.icon(), t.label, tint = t.color(), fontSize = 13.sp, size = 14.dp)
                    }
                    Text(" type", fontSize = 13.sp)
                }
                if (!b.fullTeam) {
                    // Outside dens your side matches theirs: one on one here, the rest on the bench.
                    Text(
                        if (b.swaps.isNotEmpty()) "One on one • Swap to send a monster in" else "One on one",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (b.encounter == EncounterType.WILD) {
                    val reward = "%.2g".format(com.monsterquest.core.TeamScaling.reward(b.teamSize))
                    Text(
                        if (b.teamSize > 1) "Stronger vs your team of ${b.teamSize} • Rewards x$reward"
                        else "Weaker vs a lone hero • Rewards x$reward",
                        fontSize = 11.sp,
                        color = if (b.teamSize > 1) HpYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Bar(e.hpFraction, hpColor(e.hpFraction), Modifier.fillMaxWidth(), heightDp = 10)
                Text("${e.hp} / ${e.maxHp} HP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.tags.isNotEmpty()) {
                    Text(e.tags.joinToString("  "), fontSize = 12.sp, color = HpYellow, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A pack of wild monsters: tap one to aim your attacks, spells and orbs at it. */
@Composable
private fun PackPanel(b: BattleView, onTarget: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PACK OF ${b.enemies.size}", color = BossRed, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            val reward = "%.2g".format(com.monsterquest.core.TeamScaling.reward(b.teamSize) * com.monsterquest.core.TeamScaling.packReward(b.enemies.size))
            Text("Tap a monster to target it • Rewards x$reward", fontSize = 11.sp, color = HpYellow)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            b.enemies.forEach { f ->
                val targeted = f.key == b.enemy.key && f.alive
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(if (targeted) 2.dp else 1.dp, if (targeted) Gold else Line, RoundedCornerShape(10.dp))
                        .clickable(enabled = f.alive) { onTarget(f.key) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FoeArt(f, 56)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (targeted) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f, fill = false))
                        if (b.alreadyCaught(f)) CaughtMark(Modifier.padding(start = 3.dp).size(11.dp))
                    }
                    IconText(f.element.icon(), "Lv ${f.level}", tint = f.element.color(), fontSize = 10.sp, size = 11.dp)
                    Bar(f.hpFraction, hpColor(f.hpFraction), Modifier.fillMaxWidth(), heightDp = 6)
                    Text(
                        when {
                            f.alive -> "${f.hp}/${f.maxHp}"
                            f.tamed -> "Tamed!"
                            f.gone != null -> f.gone
                            else -> "Defeated"
                        },
                        fontSize = 10.sp,
                        fontWeight = if (f.tamed) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            f.alive -> MaterialTheme.colorScheme.onSurface
                            f.tamed -> XpPurple
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (targeted) Text("Target", fontSize = 9.sp, color = Gold, fontWeight = FontWeight.Bold)
                    f.tags.firstOrNull()?.let { Text(it, fontSize = 9.sp, color = HpYellow, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun BattleLog(log: List<String>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1) }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Compact lines: the newest stay bright and it scrolls to them.
        itemsIndexed(log) { i, line ->
            Text(
                line,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = if (i >= log.size - 3) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun AllyRow(b: BattleView) {
    // Up to 4 fighters share one row; a bigger team (up to 6) splits into rows of 3.
    val rows = if (b.allies.size <= 4) listOf(b.allies) else b.allies.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { a ->
                    val up = if (b.outcome != BattleOutcome.ONGOING) b.levelUps.find { it.key == a.key } else null
                    AllyCard(a, active = a.key == b.current?.key, modifier = Modifier.weight(1f), levelUp = up)
                }
                repeat((if (rows.size > 1) 3 else row.size) - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AllyCard(a: FighterView, active: Boolean, modifier: Modifier, levelUp: LevelUp? = null) {
    Box(modifier) {
        AllyCardBody(a, active, Modifier.fillMaxWidth())
        if (levelUp != null) LevelUpBanner(levelUp, Modifier.align(Alignment.TopCenter))
    }
}

/** A gold "LEVEL UP!" ribbon over a fighter's card, popping in and then gently pulsing. */
@Composable
private fun LevelUpBanner(up: LevelUp, modifier: Modifier) {
    val pop = remember(up) { Animatable(0f) }
    LaunchedEffect(up) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    val pulse by rememberInfiniteTransition(label = "lvl").animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "lvlPulse",
    )
    Row(
        modifier
            .offset(y = (-11).dp)
            .graphicsLayer {
                val k = pop.value * pulse
                scaleX = k
                scaleY = k
                alpha = pop.value.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF5DA93), FixedGold)))
            .border(1.dp, Color(0xFF6B5126), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (up.evolvedFrom != null) "EVOLVED!" else "LEVEL UP!", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A1A08), maxLines = 1)
        Text(" ${up.toLevel}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B3A08), maxLines = 1)
    }
}

/**
 * After a fight, Continue (or Next opponent) first steps through a pop-up for
 * each fighter that levelled up, like a classic RPG, then carries on.
 */
@Composable
private fun LevelUpGate(
    b: BattleView,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onLearn: (String, String, String) -> Unit,
    content: @Composable (close: () -> Unit, next: () -> Unit) -> Unit,
) {
    var showing by remember(b.levelUps) { mutableStateOf(-1) }
    var then by remember(b.levelUps) { mutableStateOf<(() -> Unit)?>(null) }
    var seen by remember(b.levelUps) { mutableStateOf(false) }
    fun gated(action: () -> Unit): () -> Unit = {
        if (!seen && b.levelUps.isNotEmpty()) {
            then = action
            showing = 0
        } else {
            action()
        }
    }
    content(gated(onClose), gated(onNext))
    val up = b.levelUps.getOrNull(showing)
    if (up != null) {
        LevelUpDialog(up, b.allies.find { it.key == up.key }, showing, b.levelUps.size, onLearn) {
            if (showing + 1 < b.levelUps.size) {
                showing++
            } else {
                showing = -1
                seen = true
                then?.invoke()
            }
        }
    }
}

/** One fighter's level-up: new level, every stat before → after, moves learned, evolution. */
@Composable
internal fun LevelUpDialog(
    up: LevelUp,
    fighter: FighterView?,
    index: Int,
    total: Int,
    onLearn: (String, String, String) -> Unit,
    onNext: () -> Unit,
) {
    // Moves it wants to learn: decided one at a time, right here.
    var loadout by remember(up) { mutableStateOf(up.loadout) }
    var decided by remember(up) { mutableStateOf(emptyMap<String, String?>()) }
    val pending = up.canLearnIds.firstOrNull { it !in decided }
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sp = up.speciesId?.let { Bestiary.get(it) }
                when {
                    sp != null -> MonsterArt(sp, Modifier.size(64.dp), shiny = fighter?.shiny == true)
                    fighter != null -> FighterArt(fighter, Modifier.size(64.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("LEVEL UP!", color = Gold, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text("${up.name} grew to Lv ${up.toLevel}!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (up.evolvedFrom != null) {
                    IconText(GameIcon.SPARK, "${up.evolvedFrom} evolved into ${up.speciesId?.let { Bestiary.get(it).name } ?: up.name}!", tint = Gold, textColor = Gold, bold = true, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                }
                up.stats.forEach { st ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(st.label, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text("${st.before}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("  →  ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${st.after}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (st.gain > 0) "  +${st.gain}" else "",
                            fontSize = 14.sp,
                            color = HpGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 44.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
                up.learned.forEach {
                    Spacer(Modifier.height(4.dp))
                    IconText(GameIcon.BOOK, "Learned $it!", tint = MpBlue, textColor = MpBlue, bold = true, fontSize = 14.sp)
                }
                // What was decided so far.
                decided.forEach { (newId, replaced) ->
                    Spacer(Modifier.height(4.dp))
                    val mv = com.monsterquest.core.Moves.get(newId)
                    if (replaced != null) {
                        IconText(GameIcon.BOOK, "Learned ${mv.name} in place of ${com.monsterquest.core.Moves.get(replaced).name}!", tint = MpBlue, textColor = MpBlue, bold = true, fontSize = 14.sp)
                    } else {
                        Text("Skipped ${mv.name} for now: swap it in later on its page.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                pending?.let { newId ->
                    LearnMovePrompt(
                        name = up.name,
                        newMove = com.monsterquest.core.Moves.get(newId),
                        current = loadout.mapNotNull { com.monsterquest.core.Moves.find(it) },
                        ownTypes = up.speciesId?.let { Bestiary.get(it).types }.orEmpty(),
                        onReplace = { old ->
                            onLearn(up.key, newId, old.id)
                            loadout = loadout.map { if (it == old.id) newId else it }
                            decided = decided + (newId to old.id)
                        },
                        onSkip = { decided = decided + (newId to null) },
                    )
                }
                up.notes.forEach {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontSize = 13.sp, color = Gold)
                }
            }
        },
        confirmButton = {
            // Leaving with a move undecided skips it (it can be swapped in later on the monster's page).
            Button(onClick = onNext) { Text(if (index + 1 < total) "Next (${index + 1}/$total)" else "OK") }
        },
    )
}

/**
 * A new move to learn, compared side by side with the 4 it knows: pick one to
 * forget, or skip it for now.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LearnMovePrompt(
    name: String,
    newMove: Move,
    current: List<Move>,
    ownTypes: List<Element>,
    onReplace: (Move) -> Unit,
    onSkip: () -> Unit,
) {
    // Hold any move (the new one or one you know) for its full details.
    var details by remember { mutableStateOf<Move?>(null) }
    details?.let { mv -> MoveDetailsDialog(mv, emptyList(), ownTypes) { details = null } }
    Column(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, typeColor(newMove.type), RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        Text("$name wants to learn ${newMove.name}!", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Gold)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onLongClickLabel = "Move details", onLongClick = { details = newMove }, onClick = { details = newMove })
                .padding(vertical = 2.dp),
        ) {
            LearnMoveLine(newMove, ownTypes, highlight = true)
            Text(newMove.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text("Forget one to make room (hold a move for its details):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        current.forEach { old ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(onLongClickLabel = "Move details", onLongClick = { details = old }, onClick = { onReplace(old) }),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = Color.Transparent,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LearnMoveLine(old, ownTypes, highlight = false)
                    // How the new move compares with this one.
                    val diff = newMove.power - old.power
                    if (newMove.isDamaging && old.isDamaging && diff != 0) {
                        Text(
                            "${newMove.name} is ${if (diff > 0) "+$diff" else "$diff"} power" +
                                if (newMove.accuracy != old.accuracy) ", ${newMove.accuracy - old.accuracy}% accuracy" else "",
                            fontSize = 11.sp,
                            color = if (diff > 0) HpGreen else HpRed,
                        )
                    }
                }
            }
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
    }
}

/** One move in the comparison: type, name, power, accuracy, role and uses. */
@Composable
private fun LearnMoveLine(mv: Move, ownTypes: List<Element>, highlight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(mv.type.icon(), Modifier.size(16.dp), typeColor(mv.type))
        Spacer(Modifier.width(6.dp))
        Text(mv.name, fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            (if (mv.isDamaging) "Pow ${mv.power}" else "Status") +
                (if (MoveEffect.SureHit in mv.effects) " • Sure hit" else " • Acc ${mv.accuracy}") +
                (if (mv.isDamaging && mv.type != Element.NEUTRAL && mv.type in ownTypes) " • STAB" else ""),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text("${mv.role.label} • ${mv.maxPp} uses", fontSize = 11.sp, color = typeColor(mv.type))
}

@Composable
private fun AllyCardBody(a: FighterView, active: Boolean, modifier: Modifier) {
    val bg = if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (active) 2.dp else 0.dp, if (active) Gold else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp)) {
            FighterArt(a, Modifier.size(44.dp).fighterFx(a.key, a.alive))
            if (a.alive) a.status?.let { StatusOverlay(it, 44, Modifier.align(Alignment.TopEnd)) }
        }
        Text(
            a.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Gold else MaterialTheme.colorScheme.onSurface,
        )
        if (active) Text("Your move", fontSize = 9.sp, color = Gold, fontWeight = FontWeight.Bold)
        Bar(a.hpFraction, hpColor(a.hpFraction), Modifier.fillMaxWidth(), heightDp = 6)
        Text("${a.hp}/${a.maxHp}", fontSize = 10.sp)
        a.tags.firstOrNull()?.let { Text(it, fontSize = 9.sp, color = HpYellow, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (a.isHero) {
            Bar(a.mp.toFloat() / a.maxMp.coerceAtLeast(1), MpBlue, Modifier.fillMaxWidth(), heightDp = 4)
            Text("${a.mp}/${a.maxMp} EN", fontSize = 10.sp, color = MpBlue)
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null,
    icon: GameIcon? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        colors = if (color != null) ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFFF5F2EA)) else ButtonDefaults.buttonColors(),
    ) {
        if (icon != null) {
            GIcon(icon, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The hero or a monster, as drawn art. Fallen fighters are grayed out. */
@Composable
private fun FighterArt(f: FighterView, modifier: Modifier) {
    val cls = f.heroClass
    val sp = f.speciesId?.let { Bestiary.get(it) }
    when {
        // A thug: an evil look of their own for each class.
        f.thug -> ThugArt(cls ?: HeroClass.ROGUE, modifier)
        cls != null -> HeroArt(cls, modifier, portrait = false, outfit = LocalOutfit.current ?: HeroOutfit.classic(cls))
        sp != null -> MonsterArt(sp, modifier, dim = !f.alive, shiny = f.shiny)
    }
}

/**
 * A foe's picture that shows how it left the fight: a beaten one fades out
 * (grayed), a tamed one fades back behind a glowing crystal and a "Tamed"
 * tag, and one that ran off fades almost away.
 */
@Composable
private fun FoeArt(f: FighterView, sizeDp: Int) {
    val target = when {
        f.alive -> 1f
        f.tamed -> 0.45f
        f.gone != null -> 0.15f
        else -> 0.3f
    }
    val fade by androidx.compose.animation.core.animateFloatAsState(target, tween(600), label = "foeFade")
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        FighterArt(f, Modifier.size(sizeDp.dp).fighterFx(f.key, f.alive).graphicsLayer { alpha = fade })
        if (f.alive) f.status?.let { StatusOverlay(it, sizeDp, Modifier.align(Alignment.TopEnd)) }
        if (f.tamed) {
            GIcon(GameIcon.CRYSTAL, Modifier.size((sizeDp * 0.45f).dp), XpPurple)
            Text(
                "TAMED",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(XpPurple, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        } else if (!f.alive) {
            Text(
                if (f.gone != null) f.gone.uppercase() else "DEFEATED",
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color(0xCC4A4F55), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * A status shown on the fighter's picture, animated: drifting "Zz" for sleep,
 * a flickering bolt for shock, bubbling poison, a dancing flame for a burn and
 * a slowly turning snowflake for frostbite.
 */
@Composable
private fun StatusOverlay(status: com.monsterquest.core.Status, sizeDp: Int, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "status")
    val cycle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "cycle")
    val pulse by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "pulse")
    val icon = (sizeDp * 0.42f).coerceAtLeast(14f).dp
    Box(modifier.size((sizeDp * 0.55f).coerceAtLeast(20f).dp), contentAlignment = Alignment.Center) {
        when (status) {
            com.monsterquest.core.Status.SLEEP -> {
                // Two Zs drifting up and fading, one after the other.
                listOf(0f, 0.5f).forEachIndexed { i, offset ->
                    val p = (cycle + offset) % 1f
                    Text(
                        "z",
                        fontSize = (11 + i * 4).sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF9CBFE4),
                        modifier = Modifier.graphicsLayer {
                            translationY = -p * sizeDp * 0.5f
                            translationX = (i * 6 - 3).toFloat() + p * 4
                            alpha = 1f - p
                        },
                    )
                }
            }
            com.monsterquest.core.Status.SHOCK ->
                GIcon(GameIcon.BOLT, Modifier.size(icon).graphicsLayer { alpha = pulse; rotationZ = if (pulse > 0.8f) 8f else -8f }, Color(0xFFF2D24A))
            com.monsterquest.core.Status.POISON ->
                GIcon(GameIcon.GAS, Modifier.size(icon).graphicsLayer { translationY = -kotlin.math.sin(cycle * 6.283f) * 4f; alpha = 0.75f + 0.25f * pulse }, Color(0xFFB57FE0))
            com.monsterquest.core.Status.BURN ->
                GIcon(GameIcon.FLAME, Modifier.size(icon).graphicsLayer { scaleX = 0.85f + 0.2f * pulse; scaleY = 0.9f + 0.25f * pulse }, Color(0xFFF2803A))
            com.monsterquest.core.Status.FROSTBITE ->
                GIcon(GameIcon.SNOW, Modifier.size(icon).graphicsLayer { rotationZ = cycle * 360f }, Color(0xFF9FE3F2))
        }
    }
}

/** A small taming orb next to a foe's name: you've already caught one of these. */
@Composable
private fun CaughtMark(modifier: Modifier) {
    GIcon(GameIcon.ORB, modifier.semantics { contentDescription = "Already caught" }, Gold)
}

/** A move button: type-colored, with power and a hint when it's super effective. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MoveButton(
    mv: Move,
    enemyTypes: List<Element>,
    modifier: Modifier,
    ppLeft: Int? = null,
    ownTypes: List<Element> = emptyList(),
    onClick: () -> Unit,
) {
    val mult = if (mv.isDamaging) mv.type.multiplierAgainst(enemyTypes) else 1.0
    val spent = ppLeft != null && ppLeft <= 0
    val stab = mv.isDamaging && mv.type != Element.NEUTRAL && mv.type in ownTypes
    // Hold a move to see its accuracy and side effects.
    var details by remember { mutableStateOf(false) }
    if (details) MoveDetailsDialog(mv, enemyTypes, ownTypes) { details = false }
    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(50))
            .combinedClickable(onLongClickLabel = "Move details", onLongClick = { details = true }, onClick = { if (!spent) onClick() }),
        shape = RoundedCornerShape(50),
        // Out of uses: greyed out until it rests a turn.
        color = if (spent) Color(0xFF4A4F55) else typeColor(mv.type),
        contentColor = Color(0xFFF5F2EA),
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(mv.type.icon(), Modifier.size(14.dp), Color.White)
                Spacer(Modifier.width(4.dp))
                Text(
                    mv.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                )
            }
            Text(
                when {
                    !mv.isDamaging -> "Status"
                    mult > 1.0 -> "${mv.power} • Super effective!"
                    mult < 1.0 -> "${mv.power} • Weak"
                    else -> "Power ${mv.power}"
                } + (if (stab) " • STAB" else "") + (ppLeft?.let { "  •  $it/${mv.maxPp}" } ?: ""),
                fontSize = 11.sp,
                maxLines = 1,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun MoveStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/** A move's numbers and side effects, shown when its button is held. */
@Composable
fun MoveDetailsDialog(mv: Move, enemyTypes: List<Element>, ownTypes: List<Element> = emptyList(), onDismiss: () -> Unit) {
    val mult = if (mv.isDamaging) mv.type.multiplierAgainst(enemyTypes) else 1.0
    val stab = mv.isDamaging && mv.type != Element.NEUTRAL && mv.type in ownTypes
    val sureHit = MoveEffect.SureHit in mv.effects
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(mv.type.icon(), Modifier.size(22.dp), typeColor(mv.type))
                Spacer(Modifier.width(8.dp))
                Text(mv.name)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MoveStat("Type", mv.type.label)
                MoveStat("Power", if (mv.isDamaging) mv.power.toString() else "— (status move)")
                MoveStat("Accuracy", if (sureHit) "Never misses" else "${mv.accuracy}%")
                MoveStat("Role", mv.role.label)
                MoveStat("Uses per battle", "${mv.maxPp} (+1 back each turn it rests)")
                if (mv.isDamaging && ownTypes.isNotEmpty()) MoveStat(
                    "Same type (STAB)",
                    if (stab) "Yes: ×${com.monsterquest.core.SAME_TYPE_BONUS} power" else "No",
                )
                if (mv.isDamaging && enemyTypes.isNotEmpty()) MoveStat(
                    "Vs this foe",
                    when {
                        mult > 1.0 -> "Super effective (×${"%.1f".format(mult)})"
                        mult < 1.0 -> "Weak (×${"%.1f".format(mult)})"
                        else -> "Normal"
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text("Effect", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(mv.description)
            }
        },
    )
}

@Composable
private fun ActionPanel(
    b: BattleView,
    actor: FighterView,
    menu: Menu,
    onMenu: (Menu) -> Unit,
    onAction: (BattleAction) -> Unit,
) {
    // A Binding cast in progress: the crystal chosen on the Tame menu.
    var casting by remember { mutableStateOf<com.monsterquest.app.OrbOption?>(null) }
    casting?.let { o ->
        val species = b.enemy.speciesId?.let { com.monsterquest.core.Bestiary.get(it) }
        if (species == null) {
            casting = null
        } else {
            val glyph = remember(o.item, species.id) { com.monsterquest.core.Binding.glyphFor(species, o.item) }
            BindingCast(
                title = "Binding with a ${o.item.label}",
                glyph = glyph,
                gradeOdds = o.gradeOdds.mapValues { (it.value * 100).toInt() },
                onCancel = { casting = null },
            ) { grade ->
                casting = null
                onAction(BattleAction.Capture(o.item, grade))
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (!actor.alive) {
            // A fainted monster's turn: send someone in from the swap slots, or let it pass.
            Text("${actor.name} has fainted.", fontWeight = FontWeight.Bold, color = HpRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            if (menu is Menu.Swap) {
                Text("Who takes ${actor.name}'s place?", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                b.swaps.filter { it.alive }.forEach { w ->
                    OutlinedButton(onClick = { onAction(BattleAction.Swap(w.key, actor.key)) }, modifier = Modifier.fillMaxWidth()) {
                        FighterArt(w, Modifier.size(24.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${w.name}  Lv ${w.level}  ${w.hp}/${w.maxHp} HP")
                    }
                }
                BackButton(onMenu)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton("Swap", Modifier.weight(1f), icon = GameIcon.PAW) { onMenu(Menu.Swap()) }
                    if (!b.mustSwap) ActionButton("Skip", Modifier.weight(1f), color = Color(0xFF3A4E50)) { onAction(BattleAction.Skip) }
                }
            }
            return@Column
        }
        Text("What will ${actor.name} do?", fontWeight = FontWeight.Bold, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        when (menu) {
            Menu.Main -> {
                if (actor.isHero) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActionButton("Attack", Modifier.weight(1f), icon = GameIcon.FIGHT) { onAction(BattleAction.Attack) }
                        ActionButton("Magic", Modifier.weight(1f), enabled = b.spells.isNotEmpty(), icon = GameIcon.SPARK) { onMenu(Menu.Magic) }
                        ActionButton("Defend", Modifier.weight(1f), icon = GameIcon.SHIELD) { onAction(BattleAction.Defend) }
                    }
                } else {
                    // A monster's 4 moves, two per row, with the uses each has left.
                    if (b.moves.isNotEmpty() && b.moves.all { (b.movePp[it.id] ?: 1) <= 0 }) {
                        Text("Out of moves! Rest one a turn to get a use back, or:", fontSize = 12.sp, color = HpYellow)
                        MoveButton(com.monsterquest.core.Moves.STRUGGLE, b.enemy.types, Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            onAction(BattleAction.UseMove(com.monsterquest.core.Moves.STRUGGLE.id))
                        }
                    }
                    b.moves.chunked(2).forEach { row ->
                        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { mv ->
                                MoveButton(mv, b.enemy.types, Modifier.weight(1f), b.movePp[mv.id], ownTypes = actor.types) { onAction(BattleAction.UseMove(mv.id)) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton(
                        "Items (${b.itemsLeft})",
                        Modifier.weight(1f),
                        enabled = b.itemsLeft > 0 && b.items.isNotEmpty(),
                    ) { onMenu(Menu.Items) }
                    // Your best odds right now, so you can see when it's worth a throw.
                    val best = if (b.captureBlocked == null) b.orbs.maxOfOrNull { it.odds } else null
                    val snap = b.snapOdds.maxOfOrNull { it.third }
                    ActionButton(
                        when {
                            snap != null -> "Snap ${(snap * 100).toInt()}%"
                            best != null -> "Tame ${(best * 100).toInt()}%"
                            else -> "Tame"
                        },
                        Modifier.weight(1f),
                        color = if (snap != null) Color(0xFF8A3F7A) else if (b.captureBlocked == null) Color(0xFF5B4580) else null,

                    ) { onMenu(Menu.Tame) }
                    // Swap: send in a monster from the swap slots.
                    ActionButton(
                        "Swap",
                        Modifier.weight(1f),
                        enabled = b.swaps.any { it.alive },
                        color = Color(0xFF3A4E50),
                    ) { onMenu(Menu.Swap()) }
                    ActionButton("Flee", Modifier.weight(1f), color = Color(0xFF3A4E50)) { onAction(BattleAction.Flee) }
                }
            }
            Menu.Magic -> {
                b.spells.forEach { sp ->
                    val affordable = actor.mp >= sp.mpCost
                    OutlinedButton(
                        onClick = {
                            if (sp.kind in SUPPORT_KINDS) onMenu(Menu.Target(spell = sp))
                            else onAction(BattleAction.CastSpell(sp.id))
                        },
                        enabled = affordable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GIcon(sp.spellIcon(), Modifier.size(16.dp), sp.spellColor())
                        Spacer(Modifier.width(6.dp))
                        // Healing spells say how much they restore right now (path and mastery included).
                        val effect = b.spellHeals[sp.id]?.let { n ->
                            when {
                                sp.id in b.spellHealsAll -> (if (sp.kind == com.monsterquest.core.SkillKind.CLEANSE) "cures and " else "") + "heals everyone $n HP"
                                sp.kind == com.monsterquest.core.SkillKind.CLEANSE -> "cures, heals $n HP"
                                else -> "heals $n HP"
                            }
                        } ?: sp.spellEffect()
                        Text("${sp.name}  •  ${sp.mpCost} energy  •  $effect")
                    }
                }
                BackButton(onMenu)
            }
            Menu.Items -> {
                Text("${b.itemsLeft} item use(s) left this battle", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                b.items.forEach { (item, count) ->
                    OutlinedButton(onClick = { onMenu(Menu.Target(item = item)) }, modifier = Modifier.fillMaxWidth()) {
                        GIcon(item.icon(), Modifier.size(18.dp), item.color())
                        Spacer(Modifier.width(6.dp))
                        Text("${item.label} x$count")
                    }
                }
                BackButton(onMenu)
            }
            Menu.Tame -> {
                val blocked = b.captureBlocked
                // Berries first: feed one, then throw (it lasts one throw).
                if (b.bait > 1.0) {
                    Text("It ate a berry: your next cast is ${b.bait}x as likely.", fontSize = 12.sp, color = Gold)
                }
                // Snap Bind: the first-turn gamble.
                if (b.snapOdds.isNotEmpty()) {
                    Text("SNAP BIND (first turn only)", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFFE08AD0))
                    Text(
                        "Catch it off guard, whatever its health. Land it and it's tamed" +
                            (if (b.enemies.size > 1) " while the rest of the pack scatters (they'll run from you on the map)" else "") +
                            ". Miss and ${if (b.enemies.size > 1) "they double down" else "it doubles down"}: all stats up and half the taming odds for the rest of the fight.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    b.snapOdds.forEach { (orb, count, odds) ->
                        Button(
                            onClick = { onAction(BattleAction.SnapBind(orb)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A3F7A), contentColor = Color.White),
                        ) {
                            GIcon(orb.icon(), Modifier.size(18.dp), Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Snap Bind: ${orb.label} x$count  •  ${(odds * 100).toInt()}%", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                b.berries.forEach { (berry, count) ->
                    OutlinedButton(onClick = { onAction(BattleAction.UseItem(berry, actor.key)) }, modifier = Modifier.fillMaxWidth()) {
                        GIcon(berry.icon(), Modifier.size(18.dp), berry.color())
                        Spacer(Modifier.width(6.dp))
                        Text("Feed ${berry.label} x$count  •  next cast ${berry.power}x")
                    }
                }
                if (blocked != null) {
                    Text(blocked, color = HpYellow)
                } else {
                    // Binding Tome: pick a crystal, then trace the glyph (or one tap with Simple casting).
                    Text("Cast Binding: choose a crystal, then trace the glyph", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    b.orbs.forEach { o ->
                        OutlinedButton(
                            onClick = {
                                if (b.simpleCasting || !com.monsterquest.core.Binding.needsTrace(o.item)) onAction(BattleAction.Capture(o.item))
                                else casting = o
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            GIcon(o.item.icon(), Modifier.size(18.dp), o.item.color())
                            Spacer(Modifier.width(6.dp))
                            val perfect = o.gradeOdds[com.monsterquest.core.TraceGrade.PERFECT]
                            Text(
                                "${o.item.label} x${o.count}  •  ${(o.odds * 100).toInt()}%" +
                                    if (perfect != null && perfect > o.odds && !b.simpleCasting) " (Perfect ${(perfect * 100).toInt()}%)" else "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                BackButton(onMenu)
            }
            is Menu.Swap -> {
                val incoming = menu.inKey?.let { k -> b.swaps.find { it.key == k } }
                if (incoming == null) {
                    Text(if (b.fullTeam) "Send in from the swap slots" else "Send in from the bench", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    b.swaps.forEach { w ->
                        OutlinedButton(onClick = { onMenu(Menu.Swap(w.key)) }, enabled = w.alive, modifier = Modifier.fillMaxWidth()) {
                            FighterArt(w, Modifier.size(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${w.name}  Lv ${w.level}  ${w.hp}/${w.maxHp} HP")
                        }
                    }
                } else {
                    Text("${incoming.name} takes whose place?", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // The hero can step back too (while standing), and a monster fights in their place.
                    val mons = b.allies.filter { !it.isHero || it.alive }
                    // The fallen first: that's what the swap is for.
                    mons.sortedBy { it.alive }.forEach { a ->
                        OutlinedButton(onClick = { onAction(BattleAction.Swap(incoming.key, a.key)) }, modifier = Modifier.fillMaxWidth()) {
                            FighterArt(a, Modifier.size(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (a.alive) "${a.name}  ${a.hp}/${a.maxHp} HP" else "${a.name}  (fainted)")
                        }
                    }
                    if (b.allies.size < b.slots) {
                        OutlinedButton(onClick = { onAction(BattleAction.Swap(incoming.key, null)) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Join the fight (free spot)")
                        }
                    }
                }
                BackButton(onMenu)
            }
            is Menu.Target -> {
                val item = menu.item
                val spell = menu.spell
                val healsAll = spell != null && spell.id in b.spellHealsAll
                Text(
                    if (healsAll) "Heals your whole team: pick anyone to cast it" else "Choose a target",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                b.allies.forEach { a ->
                    val valid = when {
                        item?.kind == ItemKind.REVIVE -> !a.alive
                        item?.kind == ItemKind.HEAL_MP -> a.isHero && a.alive
                        else -> a.alive
                    }
                    OutlinedButton(
                        onClick = {
                            if (spell != null) onAction(BattleAction.CastSpell(spell.id, a.key))
                            else if (item != null) onAction(BattleAction.UseItem(item, a.key))
                        },
                        enabled = valid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FighterArt(a, Modifier.size(24.dp))
                        Spacer(Modifier.width(6.dp))
                        // How far a healing potion or spell would fill their HP bar.
                        val healAmount = when {
                            item?.kind == ItemKind.HEAL_HP -> com.monsterquest.core.GameEngine.potionHeal(item, a.maxHp)
                            spell != null -> b.spellHeals[spell.id]
                            else -> null
                        }
                        if (healAmount != null && a.alive) {
                            val after = minOf(a.maxHp, a.hp + healAmount)
                            // Overflow turns healing past full HP into a shield.
                            val shield = spell?.let { b.spellOverflow[it.id] }?.let { share -> ((a.hp + healAmount - a.maxHp).coerceAtLeast(0) * share).toInt() } ?: 0
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.name + (if (after > a.hp) "  ${a.hp} → $after HP (+${after - a.hp})" else "  Full HP") +
                                        if (shield > 0) "  +$shield shield" else "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                HealPreviewBar(a.hp, after, a.maxHp)
                            }
                        } else {
                            Text("${a.name}  ${a.hp}/${a.maxHp} HP" + if (a.isHero) "  ${a.mp}/${a.maxMp} EN" else "")
                        }
                    }
                }
                BackButton(onMenu)
            }
        }
    }
}

@Composable
private fun BackButton(onMenu: (Menu) -> Unit) {
    OutlinedButton(onClick = { onMenu(Menu.Main) }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}

@Composable
private fun ResultPanel(b: BattleView, onClose: () -> Unit, onNext: () -> Unit) {
    val (title, color) = when (b.outcome) {
        BattleOutcome.WON -> "Victory!" to Gold
        BattleOutcome.CAPTURED -> "Tamed!" to XpPurple
        BattleOutcome.LOST -> "Defeat" to HpRed
        BattleOutcome.FLED -> "Escaped" to MaterialTheme.colorScheme.onSurfaceVariant
        BattleOutcome.ONGOING -> "" to Color.Unspecified
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            b.resultText?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            val next = b.nextOpponent
            if (next != null) {
                // Villain teams are fought back to back; damage carries over.
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next opponent: $next") }
                TextButton(onClick = onClose) { Text("Retreat") }
            } else {
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }
        }
    }
}

/**
 * "AMBUSH!" flashed over the battle as it opens, so it's clear the foes struck
 * first. It doesn't block the screen and fades by itself.
 */
@Composable
private fun AmbushOverlay(b: BattleView) {
    var shown by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, tween(500), label = "ambushFade")
    val pop = remember { Animatable(1.6f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
        kotlinx.coroutines.delay(1600)
        shown = false
    }
    if (alpha <= 0.01f) return
    val thug = b.enemies.firstOrNull { it.thug }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0x88400A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GIcon(GameIcon.SKULL, Modifier.size(56.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }, Color(0xFFFF6B5E))
            Text(
                "AMBUSH!",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFE3DC),
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            )
            Text(
                when {
                    thug != null -> "${thug.name} jumps you! They strike first."
                    b.enemies.size > 1 -> "${b.enemies.size} wild monsters strike first!"
                    else -> "A wild ${b.enemy.name} strikes first!"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
