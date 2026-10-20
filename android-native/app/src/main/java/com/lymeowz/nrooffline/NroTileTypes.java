package com.lymeowz.nrooffline;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Reads the original NRO tile_set_info: tileset count (ubyte);
 * for each tileset: group count (ubyte) and repeated (int flag, ubyte
 * number of tiles, tileIds[]). The old renderer treated every nonzero
 * tile as solid, ignoring the original collision flags.
 */
public final class NroTileTypes {
    private final int[][] flags;

    public NroTileTypes(OfflineAssets assets) throws IOException {
        byte[] b = assets.bytes("nrolite/map/tile_set_info");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
        int count = in.readUnsignedByte();
        if (count < 1 || count > 128) throw new IOException("Bad tile set count");
        flags = new int[count][256];
        for (int t = 0; t < count; t++) {
            int groups = in.readUnsignedByte();
            for (int g = 0; g < groups; g++) {
                int flag = in.readInt();
                int n = in.readUnsignedByte();
                for (int i = 0; i < n; i++)
                    flags[t][in.readUnsignedByte()] |= flag;
            }
        }
        if (in.available() != 0) throw new IOException("Corrupt tile flags");
    }

    public int flags(int setIndex, int tileId) {
        if (setIndex < 0 || setIndex >= flags.length || tileId < 0 || tileId > 255)
            return 0;
        return flags[setIndex][tileId];
    }

    public boolean top(int setIndex, int tileId) {
        return (flags(setIndex, tileId) & (2 | 0x2000)) != 0;
    }
}
