# Cosmic Abilities (Paper 1.21.11)

Every player spawns with one of 10 random cosmic abilities. Defeat cosmic bosses for materials and Mastery, upgrade to level 10, pick a specialization at level 5, and evolve at level 10.

## Build the jar

**Easiest: GitHub (no setup on your PC).** Create a free GitHub repo, upload this whole folder (including the
`.github` folder), and GitHub builds the jar automatically. Open the repo's **Actions** tab -> latest run ->
**Artifacts** -> download **CosmicAbilities** (contains the jar and the resource pack).

**Or on your PC** with **Java 21** installed:

```
./gradlew build          # macOS / Linux
gradlew.bat build        # Windows
```

The jar appears at `build/libs/CosmicAbilities-1.0.0.jar`.
Or open the folder in IntelliJ IDEA → Gradle panel → `build` → `build`.

## Install on Folium

1. Upload `CosmicAbilities-1.0.0.jar` to your server's `plugins/` folder.
2. Restart the server (avoid `/reload`).
3. Edit `plugins/CosmicAbilities/config.yml` to tune damage, cooldowns, costs, and boss health, then `/cosmic admin reload`.

## Resource pack (ability icons, ban pistol model, ADMIN/OWNER badges)

1. Upload `CosmicAbilities-ResourcePack.zip` to a host that gives a direct download link (e.g. mc-packs.net).
2. In `plugins/CosmicAbilities/config.yml` set `resource-pack.url` to that link and `resource-pack.sha1` to the zip's SHA-1 (mc-packs.net shows it).
3. `/cosmic admin reload` and rejoin. The plugin sends the pack on join, layered on top of your server's own pack, so your Luminara pack keeps working.

Without the pack, set `hud.use-icons: false` to show ability names as text instead of icons.

## Playing

| Action | How |
|---|---|
| Cast slot 1 | `/ability 1` or **Sneak + F** |
| Cast slot 2 | `/ability 2` or **Sneak + left-click air** (empty hand) |
| See your ability, level, next cost | `/cosmic` |
| Level up | `/cosmic upgrade [1\|2]` |
| Choose specialization (level 5, permanent) | `/cosmic path <a\|b> [1\|2]` |
| List all abilities | `/cosmic abilities` |

## Abilities, materials, paths, evolutions

| Ability | Role | Material | Path A | Path B | Level 10 |
|---|---|---|---|---|---|
| Singularity | Control / Damage | Void Shard | Crushing Core | Gravity Well | Event Horizon |
| Supernova | Burst Damage | Solar Core | Flare | Shockwave | Stellar Cataclysm |
| Meteor | Offense / Mobility | Astral Fragment | Impact | Trailblazer | Comet Breaker |
| Solar | Defence / Support | Solar Core | Radiance | Aegis | Solar Ascension |
| Cryostasis | Control | Frozen Stardust | Glacier | Frostbite | Absolute Zero |
| Voidwalker | Mobility / Escape | Void Shard | Phase | Rupture | Dimensional Rift |
| Pulsar | Ranged Damage | Astral Fragment | Lance | Burst | Quasar |
| Nebula | Support / Utility | Nebula Essence | Drift | Shroud | Cosmic Veil |
| Orbital | Area Damage | Astral Fragment | Bastion | Ring | Planetary Orbit |
| Celestial | Recovery | Nebula Essence | Mender | Stride | Astral Blessing |

Upgrading uses your ability's material plus a Mastery threshold (Mastery is earned from boss kills, never spent). Level 10 also costs Astral Fragments. Cooldowns drop 1.5 s at levels 4 and 8.

## Bosses

Spawn with `/cosmic admin boss <type>` at your location (e.g. from an arena command block or an event).

| Boss | Mob | Drops | Special attack |
|---|---|---|---|
| `void_emperor` | Giant Wither Skeleton | Void Shards | Pulls players in, Darkness, teleports behind a target |
| `solar_titan` | Giant Blaze | Solar Cores | Fire rings that burn nearby players |
| `frost_leviathan` | Giant Stray | Frozen Stardust | Freezing nova with heavy Slowness |
| `nebula_serpent` | Giant Witch | Nebula Essence | Heals itself, Nausea / Weakness / Poison |
| `cosmic_destroyer` | Giant Ravager | Astral Fragments | Shockwave that launches players |

Every player who dealt at least 5% of the damage gets their own materials and Mastery; the top damage dealer gets bonus Mastery. Non-Destroyer bosses have a 25% chance to also give Astral Fragments.

Every player awakens one ability in slot 1. Slot 2 is filled by admins (admin panel or `/cosmic admin set <player> <ability> 2`).
Ability icons sit above the hotbar; they dim and show a countdown while recharging.
Each equipped ability also gives a permanent passive effect (Infuse-style), set in the `passives` section of config:

| Ability | Passive |
|---|---|
| Singularity | Night Vision |
| Supernova | Strength I |
| Meteor | Jump Boost II |
| Solar | Fire Resistance |
| Cryostasis | Water Breathing |
| Voidwalker | Speed I |
| Pulsar | Haste II |
| Nebula | Luck |
| Orbital | Resistance I |
| Celestial | Health Boost II (+4 hearts) |

## Staff tools

- **`/adminpanel`** (admins only): all 10 abilities (left-click = slot 1, right-click = slot 2, shift-click = test cast), summon any boss, grab materials, Ban Pistol, name tags, reset cooldowns, max levels.
- **Ban Pistol** (3D model): right-click to fire. Any player hit is banned (permanent by default; see `ban-pistol` in config). Staff can't be banned by it. Non-admins can't fire it, and it deletes itself if they try or if they try to pick it up.
- **`/unban <player>`** (admins, or console): lifts the ban.
- **`/tag admin`** (admin or OP), **`/tag owner`** (OP only), **`/tag off`**: shows `[OWNER] YourName` / `[ADMIN] YourName` above your head, in tab and in chat. Players can only tag themselves.

The pistol model is `assets/cosmic/models/item/ban_pistol.json` in the resource pack. It opens in Blockbench
(Java Item Model) if you want to tweak its shape or the in-hand angles (Display tab).

## Admin commands (`cosmicabilities.admin`, op by default)

```
/cosmic admin set <player> <ability> [slot]   change ability (keeps level, resets path)
/cosmic admin reroll <player>             random new ability, back to level 1
/cosmic admin setlevel <player> <1-10>
/cosmic admin setpath <player> <a|b|none>
/cosmic admin mastery <player> <amount>
/cosmic admin give <player> <material> [amount]
/cosmic admin boss <type>
/cosmic admin resetcd <player>
/cosmic admin reload
```

Player data is saved in `plugins/CosmicAbilities/players/<uuid>.yml`.
