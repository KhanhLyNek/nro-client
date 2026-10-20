package com.lymeowz.nrooffline;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Verified decoder for original NRO nrolite/mobs.bin.
 * 119 entries: ushort id, ubyte type, UTF name, int hp, 2 unknown bytes.
 * Unknown bytes are intentionally NOT claimed to be attack damage/speed.
 */
public final class NroMobTemplates {
    public static final class Mob {
        public final int id, kind, maxHp, rawA, rawB;
        public final String name;
        private Mob(int id, int kind, String name, int maxHp, int rawA, int rawB) {
            this.id=id;this.kind=kind;this.name=name;this.maxHp=maxHp;
            this.rawA=rawA;this.rawB=rawB;
        }
    }
    private final Mob[] templates;
    public NroMobTemplates(OfflineAssets assets) throws IOException {
        byte[] data = assets.bytes("nrolite/mobs.bin");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int count = in.readUnsignedShort();
        if (count < 1 || count > 500) throw new IOException("Invalid mob count");
        templates = new Mob[count];
        for (int i=0;i<count;i++) {
            int id = in.readUnsignedShort();
            int kind = in.readUnsignedByte();
            String name = in.readUTF();
            int hp = in.readInt();
            int a = in.readUnsignedByte(), b = in.readUnsignedByte();
            if (id != i || hp < 1)
                throw new IOException("Bad NRO mob entry " + i);
            templates[i] = new Mob(id,kind,name.isEmpty() ? "Quái #" + id : name,hp,a,b);
        }
        if (in.available() != 0) throw new IOException("Unparsed mob bytes");
    }
    public Mob get(int id) {
        return templates[Math.max(0,Math.min(id,templates.length-1))];
    }
    public int size() {return templates.length;}
}
