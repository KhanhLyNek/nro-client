#!/usr/bin/env python3
"""Tests resource-backed game mechanics inputs: original mob templates and map props."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]/"data"
raw=(root/"nrolite/mobs.bin").read_bytes()
count=int.from_bytes(raw[:2],"big")
assert count==119,count
p=2
names=[]
for i in range(count):
    identifier=int.from_bytes(raw[p:p+2],"big");p+=2
    kind=raw[p];p+=1
    n=int.from_bytes(raw[p:p+2],"big");p+=2
    name=raw[p:p+n].decode("utf8");p+=n
    hp=int.from_bytes(raw[p:p+4],"big");p+=4
    raw_a,raw_b=raw[p:p+2];p+=2
    assert identifier==i and hp>0 and name
    names.append((name,hp))
assert p==len(raw),(p,len(raw))
assert names[0]==("Mộc nhân",20) and names[1]==("Khủng long",200)
print("Original mob templates:",len(names),"sample:",names[:3])
for mid,expected in ((39,10),(40,13),(41,8)):
    b=(root/"nrolite/map/item_bg_map_data"/str(mid)).read_bytes()
    n=int.from_bytes(b[:2],"big")
    assert n==expected and len(b)==2+6*n,(mid,n,len(b))
    for pos in range(2,len(b),6):
        id=int.from_bytes(b[pos:pos+2],"big")
        assert (root/"nrolite/item_bg_temp/x1"/f"{id}.png").is_file()
    print("Original map",mid,"decorations:",n)
