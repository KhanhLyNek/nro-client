package com.lymeowz.nrooffline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * An Android-native playable prototype, NOT a claim of finished NRO gameplay.
 * Renders real PNG resources bundled into APK assets, and temporary vector
 * player/enemy graphics until original cRBC sprite data can be decoded.
 */
public final class NativeGameView extends View {
    private static final float VW = 960f;
    private static final float VH = 540f;
    private static final float FLOOR = 406f;
    private static final float WORLD_LENGTH = 3200f;
    private static final int WHITE = Color.WHITE;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final OfflineAssets resources;
    private final RectF rect = new RectF();
    private LegacyMap map;
    private int mapId = 39;
    private String mapMessage = "";

    private float playerX = 220f;
    private float playerY = FLOOR;
    private float vy = 0f;
    private float cameraX = 0f;
    private float enemyX = 770f;
    private float enemyHp = 100f;
    private int playerHp = 100;
    private int hits = 0;
    private boolean facingRight = true;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean jumpPressed;
    private boolean attackPressed;
    private boolean frozen;
    private long lastFrameNanos;
    private float enemyTimer;
    private float attackAnim;
    private float hitCooldown;
    private float respawnTimer;

    public NativeGameView(Context context) {
        super(context);
        resources = new OfflineAssets(context.getAssets());
        openMap(39);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
    }

    public void pause() {
        frozen = true;
        leftHeld = false;
        rightHeld = false;
        lastFrameNanos = 0L;
    }

    public void resume() {
        frozen = false;
        lastFrameNanos = 0L;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.016f :
                Math.min(0.035f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        if (!frozen) update(dt);

        float scale = Math.min(getWidth() / VW, getHeight() / VH);
        float startX = (getWidth() - VW * scale) * 0.5f;
        float startY = (getHeight() - VH * scale) * 0.5f;
        canvas.drawColor(Color.rgb(9, 27, 42));
        int save = canvas.save();
        canvas.translate(startX, startY);
        canvas.scale(scale, scale);
        drawWorld(canvas);
        drawHud(canvas);
        drawControls(canvas);
        canvas.restoreToCount(save);
        if (!frozen) postInvalidateDelayed(16L);
    }

    /** Switch only between maps existing in the checked-in resource pack. */
    private void openMap(int id) {
        try {
            LegacyMap loaded = LegacyMap.read(resources, id);
            if (resources.png("x1/t/" + loaded.tilesetId + ".png") == null)
                throw new java.io.IOException("Cannot read original tileset");
            map = loaded;
            mapId = id;
            mapMessage = "";
            playerX = Math.min(220f, loaded.widthPixels() * 0.33f);
            playerY = loaded.floorAt(playerX);
            vy = 0f;
            enemyX = Math.min(loaded.widthPixels() - 55f, playerX + 270f);
            enemyHp = 100f;
            playerHp = 100;
            cameraX = 0f;
        } catch (java.io.IOException e) {
            mapMessage = e.getMessage() == null ? "Map missing" : e.getMessage();
        }
    }

    private void nextMap() {
        int[] ids = {39, 40, 41, 0, 1, 2};
        int i = 0;
        for (int n = 0; n < ids.length; n++)
            if (ids[n] == mapId) { i = n; break; }
        openMap(ids[(i + 1) % ids.length]);
    }

    private float worldOffset() {
        return map == null ? 0f : Math.max(0f, (VW - map.widthPixels()) * 0.5f);
    }

    private void update(float dt) {
        float maxX = map == null ? WORLD_LENGTH : map.widthPixels();
        float velocity = (rightHeld ? 1f : 0f) - (leftHeld ? 1f : 0f);
        playerX = clamp(playerX + velocity * 170f * dt,
                25f, Math.max(26f, maxX - 27f));
        if (velocity != 0f) facingRight = velocity > 0f;
        float ground = map == null ? FLOOR : map.floorAt(playerX);
        if (jumpPressed && Math.abs(playerY - ground) < 1f && vy >= 0f)
            vy = -380f;
        jumpPressed = false;

        float previousY = playerY;
        vy += 980f * dt;
        float newY = playerY + vy * dt;
        if (map != null && vy >= 0f) {
            float collisionY = map.landing(playerX, previousY, newY);
            if (!Float.isNaN(collisionY)) {
                newY = collisionY;
                vy = 0f;
            }
        } else if (map == null && newY >= FLOOR) {
            newY = FLOOR;
            vy = 0f;
        }
        playerY = newY;
        if (playerY > (map == null ? 900f : map.height * 24 + 80f)) {
            playerY = ground;
            vy = 0f;
        }

        cameraX = clamp(playerX - VW * 0.4f, 0f, Math.max(0f, maxX - VW));
        enemyTimer += dt;
        enemyX = Math.min(maxX - 40f, Math.max(50f,
                maxX * 0.66f + (float) Math.sin(enemyTimer) * 48f));
        float enemyFloor = map == null ? FLOOR : map.floorAt(enemyX);
        attackAnim = Math.max(0f, attackAnim - dt);
        hitCooldown = Math.max(0f, hitCooldown - dt);

        if (attackPressed) {
            attackAnim = 0.25f;
            if (enemyHp > 0f && Math.abs(playerX - enemyX) < 78f
                    && Math.abs(playerY - enemyFloor) < 58f) {
                enemyHp = Math.max(0f, enemyHp - 25f);
                hits++;
                if (enemyHp == 0f) respawnTimer = 3f;
            }
        }
        attackPressed = false;
        if (enemyHp > 0f && Math.abs(playerX - enemyX) < 35f
                && Math.abs(playerY - enemyFloor) < 48f
                && hitCooldown == 0f) {
            playerHp = Math.max(0, playerHp - 5);
            hitCooldown = 1.3f;
        }
        if (enemyHp == 0f) {
            respawnTimer -= dt;
            if (respawnTimer <= 0f) enemyHp = 100f;
        }
        if (playerHp <= 0) {
            playerHp = 100;
            playerX = Math.min(220f, maxX / 3f);
            playerY = map == null ? FLOOR : map.floorAt(playerX);
            vy = 0f;
        }
    }

    private void drawWorld(Canvas c) {
        p.setShader(new LinearGradient(0f, 0f, 0f, FLOOR,
                Color.rgb(77, 164, 216), Color.rgb(203, 239, 238),
                Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, VW, VH, p);
        p.setShader(null);
        setColor(Color.rgb(254, 238, 174));
        c.drawCircle(780f - cameraX * 0.03f, 100f, 38f, p);

        // Decorative original PNG background; the foreground is the real
        // binary tile map, not a procedurally painted landscape.
        Bitmap backdrop = resources.png("x1/mapBackGround/0.png");
        if (backdrop != null) {
            for (int i = 0; i < 9; i++) {
                float x = i * 175f - cameraX * 0.16f;
                drawBitmap(c, backdrop, x, 230f, 121f, 125f);
            }
        }
        if (map != null) {
            Bitmap atlas = resources.png("x1/t/" + map.tilesetId + ".png");
            map.draw(c, atlas, p, cameraX, worldOffset(), VW);
        }

        float enemyFloor = map == null ? FLOOR : map.floorAt(enemyX);
        if (enemyHp > 0f)
            drawEnemy(c, enemyX - cameraX + worldOffset(), enemyFloor);
        drawPlayer(c, playerX - cameraX + worldOffset(), playerY);
        if (attackAnim > 0f) {
            setColor(Color.argb((int) (190f * attackAnim / 0.25f),
                    255, 250, 180));
            float x = playerX - cameraX + worldOffset()
                    + (facingRight ? 35f : -35f);
            c.drawCircle(x, playerY - 49f,
                    25f + (0.25f - attackAnim) * 90f, p);
        }
    }

    private void drawEnemy(Canvas c, float x, float y) {
        setColor(Color.rgb(43, 55, 65));
        c.drawOval(x - 24f, y - 5f, x + 24f, y + 4f, p);
        setColor(Color.rgb(80, 80, 190));
        c.drawOval(x - 22f, y - 54f, x + 22f, y - 8f, p);
        setColor(Color.rgb(183, 188, 251));
        c.drawCircle(x, y - 60f, 22f, p);
        setColor(Color.rgb(255, 80, 80));
        c.drawCircle(x - 8f, y - 63f, 4f, p);
        c.drawCircle(x + 8f, y - 63f, 4f, p);
        setColor(Color.DKGRAY);
        c.drawRoundRect(x - 28f, y - 98f, x + 28f, y - 89f, 4f, 4f, p);
        setColor(Color.rgb(239, 81, 82));
        c.drawRoundRect(x - 27f, y - 97f,
                x - 27f + 54f * enemyHp / 100f, y - 90f, 3f, 3f, p);
    }

    private void drawPlayer(Canvas c, float x, float y) {
        // Character is a vector placeholder, not the original NRO sprite.
        setColor(Color.argb(85, 20, 20, 20));
        c.drawOval(x - 27f, y - 6f, x + 27f, y + 3f, p);
        setColor(Color.rgb(23, 38, 87));
        c.drawRect(x - 15f, y - 29f, x - 3f, y, p);
        c.drawRect(x + 4f, y - 29f, x + 16f, y, p);
        setColor(Color.rgb(238, 123, 27));
        c.drawRoundRect(x - 22f, y - 71f, x + 22f, y - 27f, 8f, 8f, p);
        setColor(Color.rgb(28, 62, 146));
        c.drawRect(x - 21f, y - 36f, x + 21f, y - 30f, p);
        setColor(Color.rgb(247, 195, 134));
        c.drawCircle(x - 23f, y - 58f, 8f, p);
        c.drawCircle(x + 23f, y - 58f, 8f, p);
        c.drawCircle(x, y - 89f, 23f, p);
        setColor(Color.rgb(29, 31, 43));
        c.drawOval(x - 24f, y - 116f, x + 24f, y - 90f, p);
        setColor(Color.BLACK);
        c.drawCircle(x + (facingRight ? 8f : -8f), y - 89f, 2.8f, p);
    }

    private void drawHud(Canvas c) {
        setColor(Color.argb(175, 16, 38, 53));
        c.drawRoundRect(14f, 12f, 365f, 101f, 15f, 15f, p);
        Bitmap logo = resources.png("x1/mainImage/logo1.png");
        if (logo != null) drawBitmap(c, logo, 24f, 17f, 163f, 52f);
        text(c, "ANDROID THUẦN  •  KHÔNG SERVER",
                195f, 40f, 15f, WHITE, false);
        text(c, "v0.2 • MAP NRO THẬT, NHÂN VẬT THỬ",
                195f, 62f, 13f, Color.rgb(255, 221, 137), false);
        setColor(Color.DKGRAY);
        c.drawRoundRect(195f, 76f, 335f, 89f, 4f, 4f, p);
        setColor(Color.rgb(233, 81, 78));
        c.drawRoundRect(196f, 77f,
                196f + 138f * playerHp / 100f, 88f, 4f, 4f, p);
        setColor(Color.argb(170, 17, 34, 53));
        c.drawRoundRect(745f, 14f, 946f, 73f, 13f, 13f, p);
        text(c, "MAP " + mapId + "  •  ĐỔI MAP >",
                757f, 37f, 16f, WHITE, true);
        text(c, map == null ? mapMessage : "Đánh trúng: " + hits,
                757f, 60f, 14f, Color.rgb(255, 222, 147), true);
    }

    private void drawControls(Canvas c) {
        drawControl(c, 73f, 466f, 47f, "◀", leftHeld);
        drawControl(c, 188f, 466f, 47f, "▶", rightHeld);
        drawControl(c, 783f, 466f, 48f, "NHẢY", false);
        drawControl(c, 896f, 466f, 48f, "ĐẤM", attackAnim > 0f);
        text(c, "Trái/phải • Nhảy • Đấm • Đổi map góc phải",
                VW * 0.5f, 515f, 16f, Color.rgb(255, 245, 225),
                true, true);
    }

    private void drawControl(Canvas c, float x, float y, float radius,
                             String name, boolean selected) {
        setColor(selected ? Color.argb(218, 254, 165, 69)
                : Color.argb(164, 26, 50, 71));
        c.drawCircle(x, y, radius, p);
        setColor(Color.argb(224, 237, 241, 235));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        c.drawCircle(x, y, radius - 2f, p);
        p.setStyle(Paint.Style.FILL);
        text(c, name, x, y + 8f,
                name.length() > 1 ? 19f : 32f, WHITE, true, true);
    }

    private void drawBitmap(Canvas c, Bitmap b, float x, float y,
                            float w, float h) {
        p.setColor(Color.WHITE);
        p.setAlpha(255);
        rect.set(x, y, x + w, y + h);
        c.drawBitmap(b, null, rect, p);
    }

    private void setColor(int color) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
    }

    private void text(Canvas c, String message, float x, float y,
                      float size, int color, boolean bold) {
        text(c, message, x, y, size, color, bold, false);
    }

    private void text(Canvas c, String message, float x, float y,
                      float size, int color, boolean bold, boolean centered) {
        setColor(color);
        p.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD
                : android.graphics.Typeface.DEFAULT);
        p.setTextSize(size);
        p.setTextAlign(centered ? Paint.Align.CENTER : Paint.Align.LEFT);
        c.drawText(message, x, y, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float[] toVirtual(float x, float y) {
        float scale = Math.min(getWidth() / VW, getHeight() / VH);
        if (scale <= 0f) return new float[] {-1f, -1f};
        return new float[] {
                (x - (getWidth() - VW * scale) * 0.5f) / scale,
                (y - (getHeight() - VH * scale) * 0.5f) / scale
        };
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            leftHeld = rightHeld = false;
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            float[] pos = toVirtual(event.getX(index), event.getY(index));
            if (pos[1] < 90f && pos[0] > 740f) nextMap();
            if (pos[1] > 402f) {
                if (pos[0] > 725f && pos[0] < 840f) jumpPressed = true;
                if (pos[0] >= 841f) attackPressed = true;
            }
        }
        leftHeld = false;
        rightHeld = false;
        int upIndex = (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP)
                ? event.getActionIndex() : -1;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == upIndex) continue;
            float[] pos = toVirtual(event.getX(i), event.getY(i));
            if (pos[1] > 407f) {
                if (pos[0] < 126f) leftHeld = true;
                else if (pos[0] < 244f) rightHeld = true;
            }
        }
        invalidate();
        return true;
    }
}
