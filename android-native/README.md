# NRO Offline Android Native — Gameplay foundation v0.4

**Only the `offline-j2me` development branch is changed.** The `main`
branch and original `data.zip` are unchanged.

## Original NRO data verified and actually consumed by the runtime
- `nrolite/mobs.bin`: all 119 original monster IDs, names, type bytes and
  max HP values. The two trailing bytes in each record have not been
  interpreted, to avoid inventing field names.
- `nrolite/map/item_bg_map_data/{39,40,41}`: 10, 13, 8 original
  map decorations, with sprite IDs and cell coordinates, drawn using the
  original `nrolite/item_bg_temp/x1/<id>.png` textures.
- Existing `m/part`, `tile_set_info`, `mymap` and `cRBC` decoders.
- The complete assets directory is embedded locally in the APK.

## New playable OFFLINE prototype mechanics
- Fixed 60 Hz simulation steps decoupled from display frame rate.
- Move / gravity / land / jump / camera.
- Three locally spawned monsters for each verified test map.
- A monster notices nearby players, approaches, attacks with cooldown,
  dies, respawns and returns to its spawn when the player leaves.
- Hit detection is time-delayed to align damage with the punch animation.
- Melee button `ĐẤM`; KI projectile `KI` with resource cost and cooldown;
  `HỒI` trades KI for HP.
- HP / KI display, nearby monster name and actual source-template HP.
- Gold drops, XP, level progression, collection on proximity.
- Save/load HP, KI, XP, gold and kills with Android SharedPreferences.
- Real NRO map object decoration sprites as well as tile geometry.

## Critical limits — this is NOT yet original NRO gameplay
- Enemy spawn locations per map are **test placements**, not the original
  spawn tables. Grounded map tiles are verified only for 39/40/41.
- Movement tuning, combat damage, cooldowns, target range, KI consumption,
  gold and EXP rewards, and monster AI are **locally authored prototype
  rules**; original server implementation has not been recovered.
- Enemy visuals use NRO humanoid body-part sprites as stand-ins. The
  real monster animation data `nrolite/mob/x1/<id>` is still undecoded.
- Original NRO character animation transitions, quests, inventory,
  player attributes/skills, NPC interaction, map travel, items and original
  server simulation remain to be ported.
- The visual renderer is Android Java, not a J2ME emulator.

This update addresses practical missing gameplay and map fidelity without
misrepresenting the prototype as the original NRO client. Full rule parity
requires decompiling and porting client state machines plus any server-side
rules that are not present in the JAR.

## Build
Workflow: https://github.com/KhanhLyNek/nro-client/actions/workflows/android-native.yml
Artifact: `NRO-Offline-Native-v0.4-debug`, contains `app-debug.apk`.
