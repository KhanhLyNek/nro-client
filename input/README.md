# Drop your original Java game JAR here

Upload **NRO-Offline-1.0.2.jar** into this `input/` folder **on branch `decompile-nro-jar`**.

On GitHub (web):
1. Switch to the `decompile-nro-jar` branch.
2. Open `input/`, click **Add file** > **Upload files**.
3. Select your original executable JAR (not `data.zip`), then commit to this branch.

The workflow runs on the new JAR commit. Then:
- Read `decompiled/NRO-Offline-1.0.2/src/` for approximate recovered Java sources.
- Read `decompiled/NRO-Offline-1.0.2/metadata/REPORT.txt` for class counts.
- Or open **Actions > Reverse engineer NRO JAR > latest run > Artifacts**
  and download `nro-decompiled-java`.

Resources in `data/` alone cannot recreate Java classes. A runnable JAR containing
`.class` files is required. CFR reconstructs approximate Java code, not
byte-identical original source. Building a playable Java ME client still needs
MIDP/CLDC libraries and likely code fixes.

**Important:** This repository is public. Only upload JARs you have permission
to inspect and publish; do not upload private or secret files.
