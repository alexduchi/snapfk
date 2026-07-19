#!/usr/bin/env python3
from __future__ import annotations

import binascii
import shutil
import struct
import subprocess
import textwrap
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SDK_DIR = ROOT / ".build" / "arcore-android-sdk"
BUILD_DIR = ROOT / "holoforge_v04_build"


def run(*args: str) -> None:
    print("+", " ".join(args), flush=True)
    subprocess.run(args, check=True)


def chunk(kind: bytes, payload: bytes) -> bytes:
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", binascii.crc32(kind + payload) & 0xFFFFFFFF)


def solid_png(path: Path, rgba: tuple[int, int, int, int]) -> None:
    raw = b"\x00" + bytes(rgba)
    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9))
    data += chunk(b"IEND", b"")
    path.write_bytes(data)


def main() -> None:
    shutil.rmtree(SDK_DIR, ignore_errors=True)
    shutil.rmtree(BUILD_DIR, ignore_errors=True)
    SDK_DIR.parent.mkdir(parents=True, exist_ok=True)
    run("git", "clone", "--depth", "1", "--branch", "1.54.0", "https://github.com/google-ar/arcore-android-sdk.git", str(SDK_DIR))
    shutil.copytree(SDK_DIR / "samples" / "hello_ar_java", BUILD_DIR)

    app_gradle = BUILD_DIR / "app" / "build.gradle"
    gradle = app_gradle.read_text(encoding="utf-8")
    gradle = gradle.replace('applicationId "com.google.ar.core.examples.java.helloar"', 'applicationId "com.neo.holoforge.ar"')
    gradle = gradle.replace("versionCode 1", "versionCode 4")
    gradle = gradle.replace("versionName '1.0'", "versionName '0.4.0'")
    app_gradle.write_text(gradle, encoding="utf-8")

    strings = BUILD_DIR / "app/src/main/res/values/strings.xml"
    text = strings.read_text(encoding="utf-8")
    text = text.replace("HelloAR Java", "HoloForge AR")
    strings.write_text(text, encoding="utf-8")

    layout = BUILD_DIR / "app/src/main/res/layout/activity_main.xml"
    layout.write_text(textwrap.dedent('''\
        <?xml version="1.0" encoding="utf-8"?>
        <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="#000000">

          <android.opengl.GLSurfaceView
              android:id="@+id/surfaceview"
              android:layout_width="match_parent"
              android:layout_height="match_parent" />

          <LinearLayout
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:layout_gravity="top"
              android:layout_margin="18dp"
              android:background="@drawable/hud_panel"
              android:orientation="vertical"
              android:padding="16dp">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="HoloForge AR"
                android:textColor="#F4FBFF"
                android:textSize="21sp"
                android:textStyle="bold" />
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="Déplace le téléphone, puis touche une surface"
                android:textColor="#B8CBD6"
                android:textSize="13sp" />
          </LinearLayout>

          <View
              android:layout_width="34dp"
              android:layout_height="34dp"
              android:layout_gravity="center"
              android:background="@drawable/reticle" />

          <TextView
              android:layout_width="wrap_content"
              android:layout_height="48dp"
              android:layout_gravity="bottom|center_horizontal"
              android:layout_marginBottom="25dp"
              android:background="@drawable/button_accent"
              android:gravity="center"
              android:paddingStart="28dp"
              android:paddingEnd="28dp"
              android:text="Touchez pour placer un cube"
              android:textColor="#031218"
              android:textSize="14sp"
              android:textStyle="bold" />

          <ImageButton
              android:id="@+id/settings_button"
              android:layout_width="52dp"
              android:layout_height="52dp"
              android:layout_gravity="top|end"
              android:layout_marginTop="104dp"
              android:layout_marginEnd="20dp"
              android:background="@drawable/button_dark"
              android:contentDescription="Réglages AR"
              android:padding="14dp"
              android:src="@drawable/ic_settings"
              android:tint="#F4FBFF" />
        </FrameLayout>
    '''), encoding="utf-8")

    drawable = BUILD_DIR / "app/src/main/res/drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / "hud_panel.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
  <solid android:color="#D9142530" />
  <stroke android:width="1dp" android:color="#554DDFF5" />
  <corners android:radius="24dp" />
</shape>''', encoding="utf-8")
    (drawable / "button_dark.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
  <solid android:color="#DD253945" />
  <stroke android:width="1dp" android:color="#665EEAFA" />
  <corners android:radius="18dp" />
</shape>''', encoding="utf-8")
    (drawable / "button_accent.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
  <gradient android:angle="0" android:startColor="#4EDDF4" android:endColor="#63F2FF" />
  <corners android:radius="18dp" />
</shape>''', encoding="utf-8")
    (drawable / "reticle.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
  <item android:left="14dp" android:right="14dp"><shape><solid android:color="#EFFFFFFF"/><corners android:radius="2dp"/></shape></item>
  <item android:top="14dp" android:bottom="14dp"><shape><solid android:color="#EFFFFFFF"/><corners android:radius="2dp"/></shape></item>
</layer-list>''', encoding="utf-8")

    models = BUILD_DIR / "app/src/main/assets/models"
    models.mkdir(parents=True, exist_ok=True)
    (models / "pawn.obj").write_text(textwrap.dedent('''\
        o HoloCube
        v -0.05 0.00  0.05
        v  0.05 0.00  0.05
        v  0.05 0.10  0.05
        v -0.05 0.10  0.05
        v  0.05 0.00 -0.05
        v -0.05 0.00 -0.05
        v -0.05 0.10 -0.05
        v  0.05 0.10 -0.05
        vt 0 0
        vt 1 0
        vt 1 1
        vt 0 1
        vn 0 0 1
        vn 0 0 -1
        vn 1 0 0
        vn -1 0 0
        vn 0 1 0
        vn 0 -1 0
        f 1/1/1 2/2/1 3/3/1
        f 1/1/1 3/3/1 4/4/1
        f 5/1/2 6/2/2 7/3/2
        f 5/1/2 7/3/2 8/4/2
        f 2/1/3 5/2/3 8/3/3
        f 2/1/3 8/3/3 3/4/3
        f 6/1/4 1/2/4 4/3/4
        f 6/1/4 4/3/4 7/4/4
        f 4/1/5 3/2/5 8/3/5
        f 4/1/5 8/3/5 7/4/5
        f 6/1/6 5/2/6 2/3/6
        f 6/1/6 2/3/6 1/4/6
    '''), encoding="utf-8")
    solid_png(models / "pawn_albedo.png", (28, 215, 245, 255))
    solid_png(models / "pawn_albedo_instant_placement.png", (255, 184, 52, 255))

    manifest = BUILD_DIR / "app/src/main/AndroidManifest.xml"
    m = manifest.read_text(encoding="utf-8").replace('android:screenOrientation="locked"', 'android:screenOrientation="portrait"')
    manifest.write_text(m, encoding="utf-8")
    print("Prepared safe official ARCore project", BUILD_DIR)


if __name__ == "__main__":
    main()
