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
    private NroParts nroParts;
    private NroTileTypes tileTypes;
    private float animationTime;
    private static final float PIXEL_ZOOM = 2.0f;
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
        try {
            nroParts = new NroParts(resources);
            tileTypes = new NroTileTypes(resources);
        } catch (java.io.IOException e) {
            android.util.Log.e("NroNative", "Original part or tile table failed", e);
        }
        LegacyMap.setTileTypes(tileTypes);
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
        animationTime += dt;
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

        cameraX = clamp(playerX - (VW / PIXEL_ZOOM) * 0.43f,
                0f, Math.max(0f, maxX - VW / PIXEL_ZOOM));
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
        // Real resource-pack background: no synthetic hills or mock scenery.
        Bitmap sky = resources.png("nrolite/res/x1/b03-3");
        if (sky != null) drawBitmap(c, sky, 0f, 0f, VW, VH);
        else {
            setColor(Color.rgb(210, 230, 227));
            c.drawRect(0f, 0f, VW, VH, p);
        }

        Bitmap distance = resources.png("nrolite/res/x1/b00-2");
        if (distance != null) {
            for (int i = -1; i < 9; i++) {
                float x = i * 190f - (cameraX * PIXEL_ZOOM * 0.2f) % 190f;
                drawBitmap(c, distance, x, 150f, 190f, 98f);
            }
        }

        final float worldViewWidth = VW / PIXEL_ZOOM;
        final float worldViewHeight = VH / PIXEL_ZOOM;
        float cameraY = 0f;
        if (map != null) {
            cameraY = clamp(playerY - worldViewHeight * 0.67f, 0f,
                    Math.max(0f, map.height * 24 - worldViewHeight));
        }
        int save = c.save();
        c.scale(PIXEL_ZOOM, PIXEL_ZOOM);
        c.translate(0f, -cameraY);
        if (map != null) {
            Bitmap tiles = resources.png("x1/t/" + map.tilesetId + ".png");
            map.draw(c, tiles, p, cameraX, 0f, worldViewWidth);
        }

        int animationTick = (int) (animationTime * 7f);
        if (enemyHp > 0f) {
            float ground = map == null ? FLOOR : map.floorAt(enemyX);
            if (nroParts != null) {
                // Initial Namek character parts 6/7/8 from original part pack.
                nroParts.drawHuman(c, p, enemyX - cameraX, ground,
                        6, 7, 8, true, animationTick, true, false);
            }
            setColor(Color.rgb(150, 34, 43));
            c.drawRect(enemyX - cameraX - 22f, ground - 57f,
                    enemyX - cameraX - 22f + 44f*enemyHp/100f,
                    ground - 55f,p);
        }

        if (nroParts != null) {
            // Genuine NRO head/body/leg sprite set, not Android vector art.
            nroParts.drawHuman(c, p, playerX - cameraX, playerY,
                    0, 1, 2, !facingRight, animationTick,
                    leftHeld || rightHeld, attackAnim > 0f);
        }
        if (attackAnim > 0f) {
            setColor(Color.argb((int)(160f*attackAnim/0.25f),250,246,108));
            float x = playerX - cameraX + (facingRight ? 25f : -25f);
            c.drawCircle(x, playerY - 25f, 12f + attackAnim*55f, p);
        }
        c.restoreToCount(save);
    }

    private void drawHud(Canvas c) {
        setColor(Color.argb(175, 16, 38, 53));
        c.drawRoundRect(14f, 12f, 365f, 101f, 15f, 15f, p);
        Bitmap logo = resources.png("x1/mainImage/logo1.png");
        if (logo != null) drawBitmap(c, logo, 24f, 17f, 163f, 52f);
        text(c, "ANDROID THUẦN  •  KHÔNG SERVER",
                195f, 40f, 15f, WHITE, false);
        text(c, "v0.3 • SPRITE NRO THẬT • MAP 2x",
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
