#!/usr/bin/env python3
"""Validate original protected tilesets and original binary map layout."""
from pathlib import Path
import struct
root = Path(__file__).resolve().parents[2] / "data"
key = bytes.fromhex("ea 02 0c 04 05 02 f6")
def png_dims(path):
    b = path.read_bytes()
    if b[:4] == b"cRBC":
        b = bytes(x ^ key[i%7] for i,x in enumerate(b))
    assert b[:8] == bytes.fromhex("89504e470d0a1a0a"),path
    assert b[12:16] == b"IHDR", path
    return struct.unpack(">II",b[16:24])
w,h = png_dims(root / "x1/t/1.png")
assert w == 24 and h//24 >= 39,(w,h)
print("Original NRO tile atlas:",w,h)
for mid in (39,40,41):
    data = (root/"mymap"/str(mid)).read_bytes()
    x,y = data[:2]
    assert len(data) == 2+x*y and max(data[2:]) <= h//24
    print("Original map",mid,x,"x",y)
count = 0
for folder in ("x1/t","x1/bg","x1/mainImage"):
    for image in (root/folder).glob("*.png"):
        png_dims(image)
        count += 1
print("Verified",count,"original PNG textures")

# Validate exact original part encoding (NRO head/body/legs).
raw = (root / "nrolite/m/part").read_bytes()
count = int.from_bytes(raw[:2],"big")
assert count == 2099,count
pos = 2
types=[]
for part in range(count):
    kind = raw[pos]
    types.append(kind)
    pos += 1
    assert kind in (0,1,2), (part,kind)
    pos += {0:3,1:17,2:14}[kind]*4
assert pos == len(raw),(pos,len(raw))
assert types[:3] == [0,1,2]
print("Validated",count,"original head/body/leg parts")

# Validate original tile collision flags for 39 tilesets.
flags = (root / "nrolite/map/tile_set_info").read_bytes()
p = 0
count_set = flags[p];p+=1
assert count_set==39
for _ in range(count_set):
    n=flags[p];p+=1
    for _ in range(n):
        p+=4
        q=flags[p];p+=1+q
assert p == len(flags),(p,len(flags))
print("Validated",count_set,"original tile collision sets")
