# NRO Offline Native v0.3 — real character resource milestone

This is a work-in-progress Android port on branch `offline-j2me` only.

- Parses all **2099** original NRO parts in `data/nrolite/m/part`.
- Renders true original character PNG sprites from `nrolite/icon/x1/`:
  part 0=head, 1=body, 2=legs; NPC test uses parts 6/7/8.
- Motion is a **temporary animation-frame schedule**; original NRO pose
  tables have not been recovered yet (do not mistake it for gameplay parity).
- Parses original `nrolite/map/tile_set_info` into bit flags and uses
  them for collision checks (still with boundary fallbacks).
- Displays original NRO resource-pack backgrounds and 2x game-world zoom.
- Uses actual 24px tile graphics for maps 39/40/41. Mapping other
  maps to the correct original tilesets remains pending.
- Retains resource-pack XOR cRBC decoding; all data is inside APK.
- No network permission, no J2ME emulator, no server.

**Not yet ported:** character's complete directional pose tables, actual
NRO server-side rules, NPC dialogs, items, quests, save files and skill trees.
This build is for testing asset fidelity and Android rendering.

To build: GitHub Actions in `offline-j2me` runs on each commit,
artifact `NRO-Offline-Native-v0.3-debug`.
