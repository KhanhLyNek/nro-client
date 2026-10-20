package com.lymeowz.nrooffline;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Read original image assets from the APK. cRBC-encoded .png files are
 * detected as unsupported for now, not passed to BitmapFactory.
 */
public final class OfflineAssets {
    private final AssetManager assets;
    private final Map<String, Bitmap> cache = new HashMap<>();

    public OfflineAssets(AssetManager assets) {
        this.assets = assets;
    }

    public Bitmap png(String path) {
        if (cache.containsKey(path)) return cache.get(path);
        Bitmap bitmap = null;
        try (InputStream stream = assets.open(path, AssetManager.ACCESS_STREAMING)) {
            byte[] magic = new byte[8];
            if (stream.read(magic) == magic.length
                    && (magic[0] & 0xff) == 0x89
                    && magic[1] == 'P' && magic[2] == 'N' && magic[3] == 'G') {
                try (InputStream original = assets.open(path, AssetManager.ACCESS_STREAMING)) {
                    bitmap = BitmapFactory.decodeStream(original);
                }
            }
        } catch (IOException ignored) {
            // Encoded / absent resources should never crash the renderer.
        }
        cache.put(path, bitmap);
        return bitmap;
    }

    public void clear() {
        for (Bitmap bitmap : cache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        cache.clear();
    }
}
