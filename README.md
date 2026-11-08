# Cosmic Abilities 2.1 (Paper 1.21.11)

## Build the jar
Upload this whole folder (including `.github`) to your GitHub repo. GitHub builds it automatically:
**Actions** tab -> latest run -> **Artifacts** -> **CosmicAbilities** (jar + resource pack).
Or on a PC with Java 21: `gradlew.bat build` -> `build/libs/CosmicAbilities-2.1.0.jar`.

## Install
1. Delete the old CosmicAbilities jar from `plugins/`, add `CosmicAbilities-2.1.0.jar`, restart.
   Your existing config is kept and the new settings are added to it automatically. Player data carries over.
2. Upload `CosmicAbilities-ResourcePack.zip` (direct download link, e.g. mc-packs.net), put the link and
   its SHA-1 in `config.yml` under `resource-pack`, then `/cosmic admin reload` and rejoin.

## How it works
- **Slot 1** = the random ability you join with. **You keep it forever.**
- **Slot 2** = a gained ability. Bosses drop a random **ability orb** (right-click to absorb).
  If another player kills you, your slot 2 ability drops as an orb that only your killer can pick up for 60 seconds.
- **Cosmic Reroll** (craftable: diamond corners, eye of ender sides, totem of undying centre):
  right-click to get back the ability you lost. Announced to the whole server. Also sold in the AFK shop.
- **One upgrade per ability**: 30% shorter cooldown (`/cosmic upgrade 1|2`), with a camera cutscene.
  Dying removes the upgrade (`death.lose-upgrade`).
- Every cast gives a short PvP buff (Strength / Speed / Resistance / Regeneration / Absorption).
- Ability hits show floating damage numbers and a hitmarker sound.

## Abilities
| Ability | What it does | Signature bonus |
|---|---|---|
| Singularity | Black hole that drags enemies into its core | Event Horizon: collapses and detonates |
| Supernova | A sun ignites above you, charges, then fires solar beams into nearby enemies | Stellar Cataclysm: second, wider blast |
| Meteor | Comet dash through enemies; the ground tears open behind you (visual only) | Comet Breaker: crater slam at the end |
| Solar | Shield: -30% damage, reflects damage back | Solar Ascension: reflects 100%, burns attackers |
| Cryostasis | Frost nova that freezes enemies in ice | Absolute Zero: longer freeze, ice shatters for damage |
| Voidwalker | Blink; a void rift explodes behind you | Dimensional Rift: rift at your landing too |
| Pulsar | Instant beam, pierces 2 | Quasar: 3 beams |
| Nebula | Cloud: allies Speed + Regen, enemies weakened | Cosmic Veil: allies resist, mobs lose them |
| Orbital | The Sun, Earth, Mars and Saturn orbit you and smash enemies | Planetary Orbit: planets home in at the end |
| Celestial | A beam of light pours down from the sky and heals you | Astral Blessing: heals allies, cleanses |
| Paradox | Inverts enemy controls (W walks backwards, A/D swapped) + nausea | Time Fracture: longer, spins them around |
| Entropy | Scrambles enemy hotbars | Total Collapse: knocks out their offhand (totem) |
| Magnetosphere | 15s forcefield (visible to other players, not the caster): nobody else can place blocks/webs/liquids inside | Repulsor: pushes enemies out, deflects projectiles |
| Umbra | Burrow underground, hidden from all players, then erupt | Shadow Eruption: longer, harder hit + blind |

Turn the signature bonuses off with `abilities.bonus-mechanics: false`.

## Commands
| Command | Who | |
|---|---|---|
| `/ability 1`, `/ability 2` | all | cast |
| `/keybinds` | all | choose how each slot casts (Sneak+F, F, Sneak+left-click, double-tap sneak, Sneak+Q, command only) |
| `/abilities` | all | what every ability does |
| `/abilityholders` | all | player heads; hover to see their abilities |
| `/cosmic`, `/cosmic upgrade [1\|2]` | all | your abilities / upgrade |
| `/afk`, `/afkshop` | all | earn 1 token per 30 min AFK, spend them |
| `/bossfight` | admin | start a boss fight: pick the boss, countdown and how many players must be there before it starts |
| `/cconfig` | admin | edit the config in game: global settings, and per ability its cooldown, damage and cast effects |
| `/creload` | admin | instantly reload config.yml and afkshop.yml after editing the files |
| `/adminpanel` | admin | every ability, bosses, materials, Ban Pistol, tags |
| `/announce <msg>` or `/announce <title> \| <subtitle>` | admin | message on every screen |
| `/tag admin\|owner\|off` | admin / OP | `[ADMIN] Name` or `[OWNER] Name` |
| `/unban <player>` | admin | undo a Ban Pistol ban |
| `/afkshop add <price> [slot]` | admin | sell the item in your hand (shift-right-click in shop to remove) |
| `/cosmic admin set\|reroll\|settier\|give\|giveorb\|givereroll\|tokens\|boss\|resetcd\|reload` | admin | |

Bosses can only be damaged when `bosses.min-players-to-damage` players (default 3) are nearby.
To test alone, set it to 1.

## Ban Pistol
3D pistol, loud gunshot, fires a visible bullet with a red tracer. Anyone it hits is banned
(staff can't be). `/unban <player>` lifts it. Settings under `ban-pistol` in config.
