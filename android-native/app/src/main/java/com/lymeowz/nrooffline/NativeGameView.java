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
    private NroMapObjects mapProps;
    private OfflineBattle battle;
    private final android.content.SharedPreferences saveData;
    private float saveTimer;
    private boolean grounded=true, beamPressed, healPressed;
    private float animationTime;
    private float fixedAccumulator;
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
        saveData=context.getSharedPreferences("nro_native_progress_v04",Context.MODE_PRIVATE);
        try {
            nroParts = new NroParts(resources);
            tileTypes = new NroTileTypes(resources);
            battle = new OfflineBattle(new NroMobTemplates(resources));
            battle.load(saveData.getInt("hp",100),saveData.getInt("ki",100),
                    saveData.getInt("exp",0),saveData.getInt("gold",0),
                    saveData.getInt("kills",0));
        } catch (java.io.IOException e) {
            android.util.Log.e("NroNative", "Original part or tile table failed", e);
        }
        LegacyMap.setTileTypes(tileTypes);
        openMap(39);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
    }

    public void pause() {
        saveProgress();
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
        if (!frozen) {
            fixedAccumulator=Math.min(0.12f,fixedAccumulator+dt);
            int frames=0;
            while(fixedAccumulator>=1f/60f && frames++<7){
                update(1f/60f);
                fixedAccumulator-=1f/60f;
            }
        }

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
            try {mapProps=new NroMapObjects(resources,id);}
            catch(java.io.IOException e){mapProps=null;}
            if(battle!=null)battle.resetEnemies(id,loaded);
            mapId = id;
            mapMessage = "";
            playerX = Math.min(220f, loaded.widthPixels() * 0.33f);
            playerY = loaded.floorAt(playerX);
            vy = 0f;grounded=true;
            enemyX = Math.min(loaded.widthPixels() - 55f, playerX + 270f);
            enemyHp = 100f;
            playerHp = 100;
            cameraX = 0f;
        } catch (java.io.IOException e) {
            mapMessage = e.getMessage() == null ? "Map missing" : e.getMessage();
        }
    }

    private void nextMap() {
        // Only the 3 verified maps: other maps need the correct tileset mapping.
        int[] ids = {39, 40, 41};
        int i = 0;
        for (int n = 0; n < ids.length; n++)
            if (ids[n] == mapId) { i = n; break; }
        openMap(ids[(i + 1) % ids.length]);
    }

    private float worldOffset() {
        return map == null ? 0f : Math.max(0f, (VW - map.widthPixels()) * 0.5f);
    }

    private void saveProgress(){
        if(battle==null)return;
        saveData.edit().putInt("hp",battle.hp).putInt("ki",battle.ki)
                .putInt("exp",battle.exp).putInt("gold",battle.gold)
                .putInt("kills",battle.kills).apply();
    }

    private void update(float dt) {
        animationTime += dt;
        if(map==null)return;
        float maxX=map.widthPixels();
        float speed=(rightHeld?1f:0f)-(leftHeld?1f:0f);
        if(battle!=null&&battle.deathDelay>0f)speed=0f;
        float step=speed*165f*dt;
        float attempted=clamp(playerX+step,12f,maxX-12f);
        if(!map.solid((int)(attempted/24f),(int)((playerY-22f)/24f))) {
            playerX=attempted;
        }
        if(speed!=0f)facingRight=speed>0f;
        if(jumpPressed&&grounded&& (battle==null||battle.deathDelay<=0f)) {
            vy=-365f;grounded=false;
        }
        jumpPressed=false;
        float oldY=playerY;
        if(!grounded || vy<0f) {
            vy=Math.min(680f,vy+1020f*dt);
            float targetY=playerY+vy*dt;
            if(vy>=0f) {
                float land=map.landing(playerX,oldY,targetY);
                if(!Float.isNaN(land)){
                    playerY=land;
                    vy=0;grounded=true;
                }else{playerY=targetY;grounded=false;}
            }else{playerY=targetY;}
        }else{
            // Fall from ledges; unlike v0.3, no teleport to upper platforms.
            float below=map.landing(playerX,playerY,playerY+3f);
            if(Float.isNaN(below))grounded=false;
        }
        if(playerY>map.height*24+40f){
            playerY=map.floorAt(playerX);vy=0;grounded=true;
        }
        cameraX=clamp(playerX-(VW/PIXEL_ZOOM)*0.43f,0f,
                Math.max(0f,maxX-VW/PIXEL_ZOOM));

        if(battle!=null){
            if(attackPressed)battle.punch(playerX,playerY,facingRight);
            if(beamPressed)battle.beam(playerX,playerY,facingRight);
            if(healPressed)battle.heal();
            battle.tick(dt,playerX,playerY,map);
            saveTimer+=dt;
            if(saveTimer>8f){saveTimer=0;saveProgress();}
            if(battle.deathDelay>0f&&battle.hp==0) {
                leftHeld=rightHeld=false;
            }
        }
        attackPressed=beamPressed=healPressed=false;
    }

    private void drawWorld(Canvas c) {
        // Real NRO background PNGs; protected assets go through OfflineAssets.
        Bitmap sky=resources.png("x1/bg/b00.png");
        if(sky!=null){
            for(int i=0;i<9;i++){
                float x=i*128f-(cameraX*0.12f)%128f;
                drawBitmap(c,sky,x,0f,128f,VH);
            }
        }else{
            setColor(Color.rgb(166,215,216));c.drawRect(0f,0f,VW,VH,p);
        }
        float worldViewWidth=VW/PIXEL_ZOOM;
        float worldViewHeight=VH/PIXEL_ZOOM;
        float cameraY=map==null?0f:clamp(playerY-worldViewHeight*0.65f,
                0f,Math.max(0f,map.height*24-worldViewHeight));
        int state=c.save();
        c.scale(PIXEL_ZOOM,PIXEL_ZOOM);
        c.translate(0f,-cameraY);
        if(map!=null){
            Bitmap atlas=resources.png("x1/t/"+map.tilesetId+".png");
            map.draw(c,atlas,p,cameraX,0f,worldViewWidth);
        }
        // Source nrolite/map/item_bg_map_data: original placement + textures.
        if(mapProps!=null)mapProps.draw(c,p,cameraX,worldViewWidth);
        int tick=(int)(animationTime*7f);
        if(battle!=null){
            for(OfflineBattle.Loot coin:battle.loot){
                float x=coin.x-cameraX;
                setColor(Color.rgb(255,210,47));
                c.drawCircle(x,coin.y-6f+(float)Math.sin(coin.age*5)*3f,6f,p);
                setColor(Color.rgb(116,85,13));p.setTextSize(8);
                c.drawText("+"+coin.amount,x+7,coin.y-7,p);
            }
            for(OfflineBattle.Enemy e:battle.enemies){
                if(!e.alive())continue;
                float ex=e.x-cameraX;
                if(ex < -90f ||ex > worldViewWidth+90f)continue;
                if(nroParts!=null)nroParts.drawHuman(c,p,ex,e.y,
                        6,7,8,e.left,tick,e.noticeTime>0f,false);
                float percent=Math.max(0,e.hp/e.template.maxHp);
                setColor(Color.argb(180,44,26,22));
                c.drawRect(ex-25,e.y-70f,ex+25,e.y-66f,p);
                setColor(Color.rgb(223,57,58));
                c.drawRect(ex-25,e.y-70f,ex-25+50f*percent,e.y-66f,p);
                text(c,e.template.name,ex,e.y-75f,9f,Color.WHITE,true,true);
            }
            for(OfflineBattle.Projectile shot:battle.shots){
                setColor(Color.rgb(250,241,92));
                c.drawCircle(shot.x-cameraX,shot.y,8f,p);
                setColor(Color.argb(100,246,239,70));
                c.drawCircle(shot.x-cameraX,shot.y,14f,p);
            }
        }
        if(nroParts!=null){
            boolean attacking=battle!=null&&
                    (battle.punchVisual>0f||battle.beamVisual>0f);
            nroParts.drawHuman(c,p,playerX-cameraX,playerY,
                    0,1,2,!facingRight,tick,leftHeld||rightHeld,attacking);
        }
        if(battle!=null&&battle.punchVisual>0f){
            setColor(Color.argb(155,253,245,124));
            c.drawCircle(playerX-cameraX+(facingRight?23:-23),playerY-35f,
                    6f+12f*battle.punchVisual/0.27f,p);
        }
        if(battle!=null&&battle.floatTextTime>0f&&battle.selected!=null){
            float x=battle.selected.x-cameraX;
            text(c,"-"+battle.lastDamage,x,battle.selected.y-92f-
                    (0.56f-battle.floatTextTime)*25f,13f,
                    Color.rgb(255,251,104),true,true);
        }
        c.restoreToCount(state);
    }

    private void drawHud(Canvas c) {
        setColor(Color.argb(190,11,34,46));
        c.drawRoundRect(10f,10f,460f,116f,12f,12f,p);
        Bitmap logo=resources.png("x1/mainImage/logo1.png");
        if(logo!=null)drawBitmap(c,logo,18f,17f,146f,52f);
        text(c,"NRO Offline • Gameplay thử nghiệm",168f,32f,15f,
                Color.WHITE,true);
        if(battle!=null){
            text(c,"Cấp "+battle.level()+"   EXP "+battle.exp+
                    "   Vàng "+battle.gold,168f,52f,14f,
                    Color.rgb(255,229,124),false);
            setColor(Color.rgb(65,70,78));c.drawRect(168f,61f,419f,75f,p);
            setColor(Color.rgb(220,66,65));
            c.drawRect(168f,61f,168f+251f*battle.hp/battle.maxHp,75f,p);
            text(c,"HP "+battle.hp+"/"+battle.maxHp,176f,73f,12f,
                    Color.WHITE,true);
            setColor(Color.rgb(64,72,83));c.drawRect(168f,80f,419f,94f,p);
            setColor(Color.rgb(48,146,228));
            c.drawRect(168f,80f,168f+251f*battle.ki/battle.maxKi,94f,p);
            text(c,"KI "+battle.ki+"/"+battle.maxKi,176f,92f,12f,
                    Color.WHITE,true);
            text(c,"Diệt quái: "+battle.kills,168f,109f,12f,Color.WHITE,false);
        }
        setColor(Color.argb(190,14,32,46));
        c.drawRoundRect(710f,10f,947f,75f,12f,12f,p);
        text(c,"MAP "+mapId+"   •   ĐỔI MAP >",723f,33f,17f,Color.WHITE,true);
        String notice=mapMessage;
        if(battle!=null) {
            OfflineBattle.Enemy closest=battle.selected;
            notice=closest==null?"Đấm / Chưởng KI / Nhặt vàng":
                    closest.template.name+" "+Math.round(closest.hp)+
                    "/"+closest.template.maxHp;
        }
        text(c,notice,722f,59f,13f,Color.rgb(250,226,155),false);
    }
    private void drawControls(Canvas c) {
        drawControl(c,73f,466f,47f,"◀",leftHeld);
        drawControl(c,188f,466f,47f,"▶",rightHeld);
        drawControl(c,565f,466f,40f,"HỒI",false);
        drawControl(c,675f,466f,43f,"KI",
                battle!=null&&battle.beamVisual>0);
        drawControl(c,786f,466f,45f,"NHẢY",false);
        drawControl(c,897f,466f,46f,"ĐẤM",
                battle!=null&&battle.punchVisual>0);
        text(c,"Quái + HP từ NRO gốc • Quy tắc chiến đấu offline đang thử",
                VW/2f,518f,12f,Color.WHITE,true,true);
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
            if (pos[1] > 406f) {
                if (pos[0] > 525f && pos[0] < 610f) healPressed=true;
                else if(pos[0]>=610f&&pos[0]<731f)beamPressed=true;
                else if (pos[0]>=731f&&pos[0]<841f)jumpPressed=true;
                else if(pos[0]>=841f)attackPressed=true;
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
