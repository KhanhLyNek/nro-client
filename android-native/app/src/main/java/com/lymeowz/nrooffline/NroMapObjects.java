package com.lymeowz.nrooffline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Source format: ushort objectCount, then (ushort spriteId,
 * ushort column, ushort row) repeated. These are original NRO map props.
 */
public final class NroMapObjects {
    private final Item[] items;
    private final OfflineAssets assets;
    private static final class Item {
        final int id, x, y;
        Item(int id,int x,int y) {this.id=id;this.x=x;this.y=y;}
    }
    public NroMapObjects(OfflineAssets assets,int mapId) throws IOException {
        this.assets=assets;
        byte[] data=assets.bytes("nrolite/map/item_bg_map_data/"+mapId);
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(data));
        int n=in.readUnsignedShort();
        if(n>500||data.length!=2+n*6)throw new IOException("Bad NRO map object data");
        items=new Item[n];
        for(int i=0;i<n;i++)
            items[i]=new Item(in.readUnsignedShort(),in.readUnsignedShort(),in.readUnsignedShort());
    }
    public void draw(Canvas c,Paint p,float cameraX,float worldViewWidth) {
        RectF dst=new RectF();
        p.setColor(android.graphics.Color.WHITE);
        p.setAlpha(255);
        for(Item obj:items) {
            Bitmap image=assets.png("nrolite/item_bg_temp/x1/"+obj.id+".png");
            if(image==null)continue;
            float x=obj.x*LegacyMap.TILE-cameraX;
            float y=obj.y*LegacyMap.TILE;
            if(x+image.getWidth()<0||x>worldViewWidth)continue;
            dst.set(x,y,x+image.getWidth(),y+image.getHeight());
            c.drawBitmap(image,null,dst,p);
        }
    }
    public int count(){return items.length;}
}
