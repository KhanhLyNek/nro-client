package com.lymeowz.nrooffline;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline combat prototype using ORIGINAL NRO mob templates (names and HP),
 * but locally designed hit timing/AI. It does not claim server-rule parity.
 *
 * All timers use real delta-seconds, so combat is framerate independent.
 */
public final class OfflineBattle {
    public static final class Enemy {
        public final NroMobTemplates.Mob template;
        public final float origin;
        public float x, y, hp, attackWait, defeatedFor, noticeTime;
        public boolean left;
        Enemy(NroMobTemplates.Mob template,float x,float floor) {
            this.template=template;origin=x;this.x=x;y=floor;hp=template.maxHp;
        }
        public boolean alive(){return hp>0;}
    }
    public static final class Loot {
        public float x,y,age;
        public final int amount;
        Loot(float x,float y,int amount){this.x=x;this.y=y;this.amount=amount;}
    }
    public static final class Projectile {
        public float x,y;
        public final Enemy target;
        public final int damage;
        Projectile(float x,float y,Enemy target,int damage){
            this.x=x;this.y=y;this.target=target;this.damage=damage;
        }
    }

    public final ArrayList<Enemy> enemies=new ArrayList<>();
    public final ArrayList<Loot> loot=new ArrayList<>();
    public final ArrayList<Projectile> shots=new ArrayList<>();
    public int hp=100,maxHp=100,ki=100,maxKi=100,exp=0,gold=0,kills=0;
    public float punchCd,beamCd,punchVisual,beamVisual,hurtVisual,deathDelay;
    public int lastHit=0,lastDamage=0;
    public float floatTextTime=0f;
    public Enemy selected;
    private int pendingPunch;
    private Enemy pendingTarget;
    private float punchImpact;
    private float kiRegen;
    private float noticeTime;
    private final NroMobTemplates templates;

    public OfflineBattle(NroMobTemplates templates){this.templates=templates;}
    public int level(){return 1+exp/150;}
    public void load(int hp,int ki,int exp,int gold,int kills) {
        this.exp=Math.max(0,exp);this.gold=Math.max(0,gold);
        this.kills=Math.max(0,kills);
        this.hp=Math.max(1,Math.min(maxHp,hp));
        this.ki=Math.max(0,Math.min(maxKi,ki));
    }

    public void resetEnemies(int mapId,LegacyMap map) {
        enemies.clear();loot.clear();shots.clear();
        if(map==null)return;
        // Spawn choices are debug scenarios, not recovered NRO map spawn tables.
        int templateId=mapId==39?0:mapId==40?1:2;
        for(int i=0;i<3;i++) {
            float x=Math.min(map.widthPixels()-35f, 145f + i*205f);
            x=Math.max(35f,x);
            enemies.add(new Enemy(templates.get(templateId),x,map.floorAt(x)));
        }
        selected=null;
    }

    public Enemy closest(float playerX,float playerY,float range) {
        Enemy best=null;float d=range*range;
        for(Enemy e:enemies){
            if(!e.alive())continue;
            float dx=e.x-playerX,dy=(e.y-playerY)*0.8f;
            float dd=dx*dx+dy*dy;
            if(dd<d){best=e;d=dd;}
        }
        selected=best;
        return best;
    }

    public boolean punch(float playerX,float playerY,boolean faceRight) {
        if(deathDelay>0f||punchCd>0f)return false;
        punchCd=0.48f;
        punchVisual=0.27f;
        Enemy e=closest(playerX,playerY,70f);
        if(e!=null && (faceRight?(e.x>=playerX-17):(e.x<=playerX+17))) {
            pendingTarget=e;
            pendingPunch=7+level()*2;
            punchImpact=0.12f; // Damage at contact frame, not on button press.
        }
        return true;
    }

    public boolean beam(float px,float py,boolean faceRight) {
        if(deathDelay>0f||beamCd>0f||ki<18)return false;
        beamCd=1.35f;
        beamVisual=0.40f;
        ki-=18;
        Enemy target=closest(px,py,215f);
        if(target!=null)shots.add(new Projectile(px,py-28f,target,23+level()*5));
        return true;
    }

    public void heal() {
        if(deathDelay>0||ki<20||hp>=maxHp)return;
        ki-=20;
        hp=Math.min(maxHp,hp+30);
    }

    private void hit(Enemy e,int damage) {
        if(!e.alive())return;
        e.hp=Math.max(0,e.hp-damage);
        lastDamage=damage;floatTextTime=0.56f;
        e.noticeTime=2f;
        if(!e.alive()){
            kills++;
            exp+=Math.max(1,Math.min(50,e.template.maxHp/20));
            loot.add(new Loot(e.x,e.y-18f,Math.max(1,Math.min(40,e.template.maxHp/25))));
            e.defeatedFor=4.5f;
            if(selected==e)selected=null;
        }
    }

    public void tick(float dt,float px,float py,LegacyMap map) {
        if(map==null)return;
        punchCd=Math.max(0,punchCd-dt);
        beamCd=Math.max(0,beamCd-dt);
        punchVisual=Math.max(0,punchVisual-dt);
        beamVisual=Math.max(0,beamVisual-dt);
        hurtVisual=Math.max(0,hurtVisual-dt);
        floatTextTime=Math.max(0,floatTextTime-dt);
        if(punchImpact>0) {
            punchImpact-=dt;
            if(punchImpact<=0&&pendingTarget!=null){
                if(pendingTarget.alive() && Math.abs(px-pendingTarget.x)<79
                        && Math.abs(py-pendingTarget.y)<47)hit(pendingTarget,pendingPunch);
                pendingTarget=null;
            }
        }
        kiRegen+=dt;
        if(kiRegen>=0.45f){ki=Math.min(maxKi,ki+(int)(kiRegen/0.45f));kiRegen%=0.45f;}
        for(int i=shots.size()-1;i>=0;i--){
            Projectile s=shots.get(i);
            if(!s.target.alive()){shots.remove(i);continue;}
            float delta=s.target.x-s.x;
            float increment=Math.signum(delta)*340f*dt;
            if(Math.abs(delta)<=Math.abs(increment)+8f){
                hit(s.target,s.damage);
                shots.remove(i);
            }else{s.x+=increment;s.y+=(s.target.y-35f-s.y)*Math.min(1f,dt*6f);}
        }

        for(Enemy e:enemies) {
            if(!e.alive()){
                e.defeatedFor-=dt;
                if(e.defeatedFor<=0){e.hp=e.template.maxHp;e.x=e.origin;}
                continue;
            }
            e.attackWait=Math.max(0f,e.attackWait-dt);
            e.noticeTime=Math.max(0f,e.noticeTime-dt);
            float delta=px-e.x;
            float distance=Math.abs(delta);
            if(deathDelay<=0f && distance<130f && Math.abs(py-e.y)<58f) {
                e.noticeTime=1f;
                e.left=delta<0;
                if(distance>30f) {
                    float nx=e.x+Math.signum(delta)*Math.min(46f*dt,distance-30f);
                    if(!map.solid((int)(nx/24),(int)((e.y-12f)/24)))
                        e.x=nx;
                }else if(e.attackWait==0f && hurtVisual==0f) {
                    int damage=Math.max(1,Math.min(5,level()+1));
                    hp=Math.max(0,hp-damage);
                    lastHit=damage;
                    hurtVisual=0.33f;
                    e.attackWait=1.65f;
                    if(hp==0)deathDelay=2.8f;
                }
            }else if(distance>180f && Math.abs(e.x-e.origin)>2f){
                e.x+=Math.signum(e.origin-e.x)*Math.min(dt*27f,Math.abs(e.origin-e.x));
            }
            e.y=map.floorAt(e.x);
        }
        for(int i=loot.size()-1;i>=0;i--){
            Loot drop=loot.get(i);
            drop.age+=dt;
            if(Math.abs(px-drop.x)<30f && Math.abs(py-drop.y)<55f){
                gold+=drop.amount;loot.remove(i);
            }else if(drop.age>25f)loot.remove(i);
        }
        if(deathDelay>0f){
            deathDelay-=dt;
            if(deathDelay<=0f){hp=maxHp;ki=Math.max(ki,40);}
        }
    }
}
