# Side Quest

An Android RPG to-do list. Your real-life tasks, walks and phone-free time
power a hero who explores the real world with a team of monsters: clearing
dungeons, taming monsters in their dens and facing the thugs on your streets.

## Download

Every push to `main` builds the app and publishes it as the
[`latest` release](../../releases/tag/latest); each new version is also kept as
its own release.

1. On your Android phone, open the release page and download the APK.
2. Open the file and allow installing from your browser if Android asks.

Updates install over the previous version and keep your save.

## The game

### Real life
- **Quests:** your calendar events and your own to-dos. Completing one gives
  XP and gold. Rewards are fair-use limited: a to-do pays 15 minutes after it's
  added, and only so many quests pay in full each day.
- **Phone-free sessions:** put the phone down for 15 to 120 minutes to win a
  loot box; long sessions give the best rewards.
- **Walking:** every 1,000 steps lights a **torch**, and you get 3 each morning.
  Torches open dungeons and pay for gym training. Walking also heals your team.
- **Weekly bounty:** walk 40,000 steps, finish two 60-minute phone-free
  sessions and complete 10 quests in a week for a guaranteed Rare or Epic piece
  of gear.
- **Sleep:** 8+ hours makes the next day's fights easier;
  under 7 makes them harder.

### The map
The map is built from OpenStreetMap data and real places host the game:
- **Dungeons** in coffee shops, doctor's offices and restaurants (one per
  block, a different place each day).
- **Traders** at grocery stores, **dens** with a strong monster to beat and
  tame, and **thug crews** on the streets (and outside bars at night).
- **Services:** pharmacies heal, hospitals revive, gyms train your team and
  schools are academies where your hero studies magic.
- **Shrines** at landmarks give items, and Silver and Gold shrines hold bosses.

The map tilts as you zoom in, follows you every second and can turn with your
phone. The real weather favors some types in battle (rain helps Water and
Electric, for one). With the **battery saver** on, wild monsters find you as you walk; food
set out draws the ones that eat it.

### Battles and dungeons
- Turn-based battles ordered by Speed. Your hero always fights with at least
  one monster; the rest wait on the bench and can swap in, and a fight is only
  lost when nobody is left standing.
- **Dungeons** are rated 1 to 5 stars. Pick a door in each room (monsters,
  traps, treasure, fountains) and beat the champion at the end for a permanent
  stat point and a roll on the reward pool.

### Your hero
- **Classes** at level 5: Warrior, Mage, Rogue, or stay an Adventurer, each
  with three subclasses at level 15.
- **Spells:** learn them with spell pages and rank them up to 5.
- **Gear:** weapons and armor in four rarities with bonus effects; forge new
  gear from parts.
- **Star ranks:** every 50 levels earns a star and a star reward box.

### Monsters
98 species in 39 evolution lines and 7 types. Each type is strong against two
and weak to two:

| Type | Strong against | Weak to |
|---|---|---|
| Water | Fire, Gas | Earth, Electric |
| Fire | Corrupt, Earth | Water, Flight |
| Corrupt | Gas, Flight | Fire, Electric |
| Gas | Earth, Electric | Water, Corrupt |
| Earth | Flight, Water | Fire, Gas |
| Flight | Electric, Fire | Corrupt, Earth |
| Electric | Water, Corrupt | Gas, Flight |

Every monster rolls its own genes and personality, learns moves as it levels,
gains a skill at level 30 and evolves when you choose to. Monsters can't get
more than 5 levels ahead of your hero.

Battles and walks build each monster's **bond** (up to 5 hearts, a little
stronger each time, and at the top it survives one knockout a fight). Pairing
types in your team, such as Water with Electric, gives the whole team a
**synergy** boost.

### Also
- A home-screen widget with your next quests and the date.
- Foldable support: two-pane layouts when unfolded, tabletop and book postures.
- Light and dark themes.

The in-game Help screen covers everything in detail.

## Project layout

- `core/`: the game engine in plain Kotlin (battles, monsters, dungeons, the
  world, rewards), with unit tests. It builds without the Android SDK:
  `CORE_ONLY=1 ./gradlew :core:test`.
- `app/`: the Android app (Jetpack Compose, MapLibre map, widget, step counter,
  phone-free service).
- `widgetmaker/`: **AI Widget Maker**, a separate app that designs home-screen
  and lock-screen widgets with Claude. See [`widgetmaker/README.md`](widgetmaker/README.md).
- `tools/beasts/`: the source for the evolved monsters' art (`BeastArt.kt`).
- `docs/`: ideas for later.

Map data © OpenStreetMap contributors, map tiles by OpenFreeMap and OpenMapTiles.
