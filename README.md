# Cosmic Abilities 2.7 (Paper 1.21.11)

## Build the jar
Upload this whole folder (including `.github`) to your GitHub repo. GitHub builds it automatically:
**Actions** tab -> latest run -> **Artifacts** -> **CosmicAbilities** (jar + resource pack).
Or on a PC with Java 21: `gradlew.bat build` -> `build/libs/CosmicAbilities-2.7.0.jar`.

## Install
1. Delete the old CosmicAbilities jar from `plugins/`, add `CosmicAbilities-2.7.0.jar`, restart.
   Your existing config is kept and the new settings are added to it automatically. Player data carries over.
2. Upload `CosmicAbilities-ResourcePack.zip` (direct download link, e.g. mc-packs.net), put the link and
   its SHA-1 in `config.yml` under `resource-pack`, then `/cosmic admin reload` and rejoin.

## New in 2.7 - Money SMP
- **Teams**: `/team create <name>`, `/team invite <player>`, `/team accept`, `leave`, `kick`, `disband`, `deposit`, `info`, `top`.
  Teammates can't hurt each other (hits or abilities). Every kill pays `economy.kill-reward` into the killer's **team bank**.
- **Seasons** (`season.days`, default 14): the team with the most money in its bank wins, and every member gets a
  rare boss/dragon ability orb. Then every bank resets. End it early in /adminpanel -> Teams & Season.
- **Daily events** (/adminpanel -> Daily Events): 2x kill money, 2x AFK tokens, Ability Frenzy (half cooldowns),
  Supply Drop Rain, Boss Fight. Choose how long each runs.
- **Auction house** purchases by a team leader come out of the team bank (lowers their season score).
- **Exclusive abilities**: Dragon's Wrath (only the Ender Dragon drops it), and one per boss: Void Grasp, Titan's Fist,
  Glacial Maw, Nebula Wings, Star Overdrive (`ability-drops.boss-exclusive-chance`). Never rolled on the wheel.
- Every cooldown is 3 minutes; change each one (or all at once) in /adminpanel -> Ability Cooldowns.
  Abilities can't one-shot combo: `abilities.combo-damage-cap` limits ability damage taken in a short window.
- **Bosses** are stronger and bigger (`bosses.health-multiplier`, `damage-multiplier`, `size-multiplier`, also in
  /adminpanel -> Boss Strength) and have new models; the Solar Titan is now a walking magma golem.
- **Admin RPG** (admins only): a rocket that blows a giant crater (`rpg.*`). **Supply drops**: `/supplydrop`
  or /adminpanel. **Custom tab list** (`tablist.*`). **Player Stats** page: refresh or reset kills / cash.
- **/afk**: you hover in place and nothing can hurt or move you, even if the ground is dug out. Only pressing a
  movement key (or attacking / casting) ends it.

## How it works
- **Slot 1** = new players spin the **cosmic wheel** (a slot-machine menu of every ability) and keep what it lands on.
- **Slot 2** = a second ability, only from **boss fight orbs** (right-click to absorb).
  If another player kills you, your slot 2 ability drops as an orb that only your killer can pick up for 60 seconds.
- **Cosmic Reroll** (craftable, recipe editable): right-click to spin the wheel again and change your slot 1 ability.
  Announced to the whole server. Hover it in `/cosmic` -> Cosmic Items to see the recipe (until you own one).
- **Cosmic Pickaxe** (netherite, mines 3x3) and **Cosmic Axe** (netherite, fells whole trees). Shift + right-click toggles.
  Default recipe: diamond blocks in the corners, amethyst shards on the sides, the netherite tool in the middle.
- **One upgrade per ability**: 30% shorter cooldown (`/cosmic upgrade 1|2`), with a camera cutscene.
  Dying removes the upgrade (`death.lose-upgrade`).
- Every cast gives a short PvP buff (Strength I max / Speed / Resistance / Regeneration / Absorption).
- Ability damage is magic (`abilities.armor-piercing`): armor points don't block it, Protection still does.
  Balanced for Sharp V netherite / Prot IV diamond: a big ability hit takes ~2-3 hearts. Every ability's cooldown,
  damage and cast effects can be changed separately in `/cconfig`.
- Ability hits show floating damage numbers and a hitmarker sound.

## Abilities
| Ability | What it does | Signature bonus |
|---|---|---|
| Singularity | A 3D black hole with a tilted, spinning accretion disk that drags enemies into its core | Event Horizon: collapses and detonates |
| Supernova | A sun ignites above you, charges, then locks a beam of sunlight onto every nearby enemy | Stellar Cataclysm: a second volley of beams |
| Meteor | Comet dash through enemies; rock walls burst from the ground on both sides (visual only) | Comet Breaker: crater slam at the end |
| Solar | Shield: -30% damage, reflects damage back | Solar Ascension: reflects 100%, burns attackers |
| Cryostasis | Frost nova that freezes enemies in ice | Absolute Zero: longer freeze, ice shatters for damage |
| Voidwalker | Blink; a void rift explodes behind you | Dimensional Rift: rift at your landing too |
| Pulsar | Instant beam, pierces 2 | Quasar: 3 beams |
| Nebula | Cloud: allies Speed + Regen, enemies weakened | Cosmic Veil: allies resist, mobs lose them |
| Orbital | The Sun, Earth, Mars and Saturn share one orbit around you; everything inside the orbit takes damage | Planetary Orbit: planets home in at the end |
| Celestial | A beam of light pours down from the sky and heals you | Astral Blessing: heals allies, cleanses |
| Paradox | Inverts enemy controls (W walks backwards, A/D swapped) + nausea | Time Fracture: longer, spins them around |
| Entropy | Scrambles enemy hotbars for 5 seconds (then they snap back) | Total Collapse: knocks out their offhand (totem) |
| Magnetosphere | 15s dome of blue hexagon panels. Whoever is inside is trapped, nobody outside can get in, nobody else can place blocks/webs/liquids | Repulsor: deflects projectiles, shocks enemies who hit the wall |
| Umbra | Burrow underground, hidden from all players; a trail of crumbling ground follows you, then you erupt | Shadow Eruption: longer, harder hit + blind |

Turn the signature bonuses off with `abilities.bonus-mechanics: false`.

## Commands
| Command | Who | |
|---|---|---|
| `/ability 1`, `/ability 2` | all | cast |
| `/keybinds` | all | choose how each slot casts (Sneak+F, F, Sneak+left-click, double-tap sneak, Sneak+Q, command only) |
| `/abilities` | all | what every ability does |
| `/abilityholders` | all | player heads; hover to see their abilities |
| `/trust <player>`, `/untrust <player>`, `/trust list` | all | your abilities never affect players you trust |
| `/cosmic` | all | the Cosmic menu: your abilities + upgrades, every other menu, cosmic items and recipes |
| `/cosmic upgrade [1\|2]`, `/cosmic info` | all | upgrade / your abilities in chat |
| `/afk`, `/afkshop` | all | earn 1 token per 30 min AFK, spend them |
| `/bossfight` | admin | start a boss fight: pick the boss, countdown and how many players must be there before it starts |
| `/cconfig` | admin | edit the config in game: global settings, and per ability its cooldown, damage and cast effects |
| `/creload` | admin | instantly reload config.yml and afkshop.yml after editing the files |
| `/adminpanel` | admin | **everything**: abilities, boss fights, config, AFK shop editor, recipes, players (spin/set/upgrade/orbs/tokens), give items, announce, reload, unban, tags |
| `/announce <msg>` or `/announce <title> \| <subtitle>` | admin | message on every screen |
| `/tag admin\|owner\|off` | admin / OP | `[ADMIN] Name` or `[OWNER] Name` |
| `/unban <player>` | admin | undo a Ban Pistol ban |
| `/afkshop edit` | admin | AFK shop editor: click items in your inventory to add, left/right-click to change price, shift-right-click to remove, Q to move |
| `/cosmic admin set\|reroll\|spin\|settier\|give\|giveorb\|givereroll\|tokens\|boss\|resetcd\|reload` | admin | |

Bosses can only be damaged when `bosses.min-players-to-damage` players (default 3) are nearby.
To test alone, set it to 1.

## Ban Pistol
3D pistol held in your fist. **Right-click to aim** (sights centred, slight zoom), **left-click to fire** while aiming.
The slide kicks back on every shot; after 7 shots the magazine drops out and you reload (Q reloads early).
Any player the bullet hits is banned (staff can't be). Animals and mobs take damage and get a bleeding bullet hole.
`/unban <player>` lifts a ban. Settings under `ban-pistol` in config.

## Cash and coinflips
Everyone starts with **$10,000** and earns **$1,000 per player kill** (change it in `/adminpanel`).
`/cf` opens the coinflip menu: click the sign to put up a bet (min $1,000, no max; the money is held until someone
accepts or you cancel). Click someone's head to accept - the coin flips between both heads and lands on the winner,
who takes the pot. `/balance`, `/pay <player> <amount>`; admins `/eco give|take|set <player> <amount>`.

## Discord chat
Discord: channel settings -> Integrations -> Webhooks -> New Webhook -> Copy Webhook URL.
In game: `/discordbridge set <url>`. Public chat, joins/leaves and deaths go to that channel; /msg never does.

## Crafting (`/recipes`)
Cosmic Shard (glowstone, amethyst, ender pearl -> 2 shards), Cosmic Pickaxe / Axe (diamond blocks, Cosmic Shards,
netherite tool), Upgrade Token (Cosmic Shards, diamonds, nether star), Cosmic Reroll. All editable in
`/adminpanel` -> Recipes. Hover an item in `/recipes` to see the real items in the crafting grid.
Upgrading an ability uses an **Upgrade Token**: right-click it and pick the ability.

## Bosses
Every boss has its own animated 3D model (Void Emperor, Solar Titan, Frost Leviathan, Nebula Dragon, Cosmic
Destroyer). `bosses.custom-models: false` brings back the plain mobs.

## Ranks (Tebex)
Starlight < Cosmos < Supernova. In each Tebex package add the command (run when the player is online):
`cosmicrank give {username} supernova` - the buyer gets an enchanted rank box; placing it unlocks the rank.
Ranks show `[SUPERNOVA] Name` and give a bigger ender chest (36 / 45 / 54 slots; normal players 27).
Test them in `/adminpanel` -> Ranks. `/cosmicrank set <player> <rank|none>` sets one directly.

## Auction house
`/ah` to browse and buy, `/ah sell <price>` to list the item in your hand. Unsold items return after 48h (Collect).

## Sidebar
The COSMIC panel (name, kills, cash, ping, players online). `/sidebar` toggles it.

## AFK Tokens
AFK Tokens are now items (amethyst clusters) - spend them in `/afkshop`.
