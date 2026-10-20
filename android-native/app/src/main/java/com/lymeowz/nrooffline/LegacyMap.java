package com.lymeowz.nrooffline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.IOException;

/** Original NRO tile-grid resource; ID 0 is empty, all other IDs index the tile atlas. */
public final class LegacyMap {
    public static final int TILE = 24;
    public final int id;
    public final int width, height;
    public final int tilesetId;
    private final byte[] tileData;
    private static NroTileTypes types;

    public static void setTileTypes(NroTileTypes newTypes) { types = newTypes; }

    private LegacyMap(int id, byte[] bytes) throws IOException {
        if (bytes.length < 2) throw new IOException("No map header");
        int w = bytes[0] & 255, h = bytes[1] & 255;
        if (w < 2 || w > 240 || h < 2 || h > 150 || bytes.length != 2 + w*h)
            throw new IOException("Invalid map " + id + " " + w + "x" + h);
        this.id = id;
        width = w;
        height = h;
        tileData = bytes;
        tilesetId = 1; // Verified atlas for maps 39-41. Other maps provisional.
    }

    public static LegacyMap read(OfflineAssets assets, int id) throws IOException {
        String path = id >= 39 && id <= 41 ? "mymap/" + id
                : "nrolite/map/tile_map_data/" + id;
        return new LegacyMap(id, assets.bytes(path));
    }

    public int widthPixels() { return width * TILE; }

    public int tile(int col, int row) {
        if (col < 0 || col >= width || row < 0 || row >= height) return 0;
        return tileData[2 + row * width + col] & 255;
    }

    public boolean solid(int col, int row) {
        // Approximation; original collision flags in tile_set_info still to port.
        int id = tile(col,row);
        if (id == 0) return false;
        // Original 0x2 top and 0x2000 boundary flags, plus fallback for
        // surfaced tiles whose original flags only encode left/right edges.
        return types == null || types.top(0,id) ||
                (tile(col,row-1) == 0) ||
                ((types.flags(0,id) & (4|8)) != 0);
    }

    public float floorAt(float x) {
        int col = Math.max(0, Math.min(width-1, (int) (x / TILE)));
        for (int row = 7; row < height; row++)
            if (solid(col,row)) return row * TILE;
        return (height - 1) * TILE;
    }

    /** Floor contact for a falling character crossing tile-row boundaries. */
    public float landing(float x, float oldY, float newY) {
        if (newY < oldY) return Float.NaN;
        int minRow = Math.max(0, (int) Math.floor(oldY/TILE));
        int maxRow = Math.min(height-1, (int) Math.floor(newY/TILE));
        for (int row = minRow; row <= maxRow; row++) {
            int top = row * TILE;
            if (top + 0.1f < oldY || top > newY + 0.1f) continue;
            int a = (int) Math.floor((x - 8f) / TILE);
            int b = (int) Math.floor((x + 8f) / TILE);
            if (solid(a, row) || solid(b, row)) return top;
        }
        return Float.NaN;
    }

    public void draw(Canvas c, Bitmap atlas, Paint p,
                     float cameraX, float offsetX, float viewportWidth) {
        if (atlas == null || atlas.getWidth() < TILE) return;
        int count = atlas.getHeight()/TILE;
        int start = Math.max(0, (int) ((cameraX-offsetX)/TILE)-1);
        int end = Math.min(width-1, (int) ((cameraX+viewportWidth-offsetX)/TILE)+1);
        Rect src = new Rect();
        RectF dst = new RectF();
        p.setColor(android.graphics.Color.WHITE);
        p.setAlpha(255);
        for (int row = 0; row < height; row++)
            for (int col = start; col <= end; col++) {
                int tile = tile(col,row);
                if (tile < 1 || tile > count) continue;
                float x = col * TILE + offsetX - cameraX;
                int sy = (tile-1) * TILE;
                src.set(0,sy,TILE,sy+TILE);
                dst.set(x,row*TILE,x+TILE,(row+1)*TILE);
                c.drawBitmap(atlas,src,dst,p);
            }
    }
}
