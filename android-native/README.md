# NRO Offline Native v0.2

This project is a **native Android Java** port in progress, NOT a completed
conversion of NRO. All original `../data/` files are packaged into the APK.

## Real resources now supported
- Both standard PNG and original XOR-obfuscated `cRBC` PNG with repeating
  key `EA 02 0C 04 05 02 F6`.
- Map byte format: uint8 width, uint8 height, then width*height uint8 tiles.
- Genuine map 39/40/41 are loaded from `data/mymap/` and drawn with original
  tileset `data/x1/t/1.png` (24px-wide tiles).
- A few extra maps 0/1/2 load from `data/nrolite/map/tile_map_data/`,
  but tile atlas mappings are still provisional.
- Local map-grid movement, jump collision and debug map cycling.

**Not yet ported:** original character sprites, AI, original tile collision
bitfields, NPCs, quests, inventory, skills, server-side game mechanics and
persistent save system. Player/enemy remain placeholders for now.

## Build
Commit to branch `offline-j2me` to trigger GitHub Actions.
The `main` branch is left untouched.
