package com.lymeowz.nrooffline;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Reads original PNG and protected cRBC resources bundled in the APK. */
public final class OfflineAssets {
    private static final byte[] KEY = {(byte) 0xea, 2, 12, 4, 5, 2, (byte) 0xf6};
    private final AssetManager assets;
    private final Map<String, Bitmap> cache = new HashMap<>();

    public OfflineAssets(AssetManager manager) { assets = manager; }

    public byte[] bytes(String path) throws IOException {
        try (InputStream in = assets.open(path, AssetManager.ACCESS_STREAMING);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > 16 * 1024 * 1024) throw new IOException("Asset too large");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    public static byte[] decodePng(byte[] data) {
        if (data.length >= 8 && data[0] == 'c' && data[1] == 'R'
                && data[2] == 'B' && data[3] == 'C') {
            for (int i = 0; i < data.length; i++)
                data[i] = (byte) (data[i] ^ KEY[i % KEY.length]);
        }
        return data;
    }

    public Bitmap png(String path) {
        if (cache.containsKey(path)) return cache.get(path);
        Bitmap result = null;
        try {
            byte[] data = decodePng(bytes(path));
            if (data.length > 24 && (data[0] & 0xff) == 0x89
                    && data[1] == 'P' && data[2] == 'N' && data[3] == 'G')
                result = BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (IOException | OutOfMemoryError ignored) {
            // Missing/unsupported files are displayed as absent, not a crash.
        }
        cache.put(path, result);
        return result;
    }

    public void clear() {
        for (Bitmap b : cache.values())
            if (b != null && !b.isRecycled()) b.recycle();
        cache.clear();
    }
}
