package com.lymeowz.nrooffline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Original NRO part table (m/part). Count ushort, then type byte and
 * 3 (head), 17 (body), 14 (legs) {iconId short, dx byte, dy byte} frames.
 * The reader validates exact length, rather than guessing offsets.
 */
public final class NroParts {
    private static final class Frame {
        final int icon, dx, dy;
        Frame(int icon, int dx, int dy) {
            this.icon = icon; this.dx = dx; this.dy = dy;
        }
    }
    private static final class Part {
        final int type;
        final Frame[] frames;
        Part(int type, Frame[] frames) {
            this.type = type; this.frames = frames;
        }
    }

    private final Part[] parts;
    private final OfflineAssets assets;
    private final RectF dst = new RectF();

    public NroParts(OfflineAssets assets) throws IOException {
        this.assets = assets;
        byte[] bytes = assets.bytes("nrolite/m/part");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int count = in.readUnsignedShort();
        if (count < 3 || count > 5000) throw new IOException("Invalid NRO part count");
        parts = new Part[count];
        for (int i = 0; i < count; i++) {
            int type = in.readUnsignedByte();
            int frameCount = type == 0 ? 3 : type == 1 ? 17 : type == 2 ? 14 : -1;
            if (frameCount < 0) throw new IOException("Unknown part type " + type);
            Frame[] frames = new Frame[frameCount];
            for (int j = 0; j < frameCount; j++)
                frames[j] = new Frame(in.readUnsignedShort(), in.readByte(), in.readByte());
            parts[i] = new Part(type, frames);
        }
        if (in.available() != 0) throw new IOException("Extra bytes in NRO part table");
        if (parts[0].type != 0 || parts[1].type != 1 || parts[2].type != 2)
            throw new IOException("Missing initial NRO character sprite set");
    }

    private void render(Canvas c, Paint p, int part, int frame,
                        float x, float y, boolean reverse) {
        if (part < 0 || part >= parts.length) return;
        Frame[] frames = parts[part].frames;
        Frame f = frames[Math.min(Math.max(frame, 0), frames.length - 1)];
        if (f.icon == 0) return;
        Bitmap b = assets.png("nrolite/icon/x1/" + f.icon + ".png");
        if (b == null) return;
        float left = x - b.getWidth() / 2f + f.dx;
        float top = y + f.dy;
        if (reverse) {
            int state = c.save();
            c.scale(-1f, 1f, x, 0f);
            dst.set(left, top, left + b.getWidth(), top + b.getHeight());
            p.setColor(android.graphics.Color.WHITE);
            c.drawBitmap(b, null, dst, p);
            c.restoreToCount(state);
        } else {
            dst.set(left, top, left + b.getWidth(), top + b.getHeight());
            p.setColor(android.graphics.Color.WHITE);
            c.drawBitmap(b, null, dst, p);
        }
    }

    /** Renders 100% original source sprite parts; offsets are initial
     * alignment only; original NRO's pose tables still need porting.
     */
    public void drawHuman(Canvas c, Paint p, float x, float feet,
                          int headPart, int bodyPart, int legPart,
                          boolean reverse, int tick, boolean walking,
                          boolean attacking) {
        int frame = attacking ? 8 + (tick % 3) :
                    walking ? 2 + (tick % 5) : 1;
        // NRO sprite draw order: legs, torso, head.
        render(c, p, legPart, frame, x, feet - 13f, reverse);
        render(c, p, bodyPart, frame, x, feet - 25f, reverse);
        render(c, p, headPart, walking ? tick % 2 : 0, x, feet - 45f, reverse);
    }

    public int partCount() { return parts.length; }
}
