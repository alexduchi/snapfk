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


def replace_once(path: Path, old: str, new: str) -> None:
    data = path.read_text(encoding="utf-8")
    if old not in data:
        raise RuntimeError(f"Pattern not found in {path}: {old[:120]!r}")
    path.write_text(data.replace(old, new, 1), encoding="utf-8")


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", binascii.crc32(kind + payload) & 0xFFFFFFFF)
    )


def write_solid_png(path: Path, rgba: tuple[int, int, int, int]) -> None:
    signature = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0)
    raw = b"\x00" + bytes(rgba)
    path.write_bytes(
        signature
        + png_chunk(b"IHDR", ihdr)
        + png_chunk(b"IDAT", zlib.compress(raw, 9))
        + png_chunk(b"IEND", b"")
    )


def main() -> None:
    if SDK_DIR.exists():
        shutil.rmtree(SDK_DIR)
    if BUILD_DIR.exists():
        shutil.rmtree(BUILD_DIR)
    SDK_DIR.parent.mkdir(parents=True, exist_ok=True)

    run(
        "git",
        "clone",
        "--depth",
        "1",
        "--branch",
        "1.54.0",
        "https://github.com/google-ar/arcore-android-sdk.git",
        str(SDK_DIR),
    )
    shutil.copytree(SDK_DIR / "samples" / "hello_ar_java", BUILD_DIR)

    app_gradle = BUILD_DIR / "app" / "build.gradle"
    gradle = app_gradle.read_text(encoding="utf-8")
    gradle = gradle.replace(
        'applicationId "com.google.ar.core.examples.java.helloar"',
        'applicationId "com.neo.holoforge.ar"',
    )
    gradle = gradle.replace("versionCode 1", "versionCode 4")
    gradle = gradle.replace("versionName '1.0'", "versionName '0.4.0'")
    app_gradle.write_text(gradle, encoding="utf-8")

    strings = BUILD_DIR / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    strings.write_text(
        textwrap.dedent(
            """\
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
              <string translatable="false" name="app_name">HoloForge AR</string>
              <string translatable="false" name="depth_use_explanation">La profondeur permet aux objets holographiques de passer derrière les objets réels.</string>
              <string translatable="false" name="button_text_enable_depth">Activer</string>
              <string translatable="false" name="button_text_disable_depth">Plus tard</string>
              <string translatable="false" name="options_title_with_depth">Profondeur ARCore disponible</string>
              <string translatable="false" name="options_title_without_depth">Profondeur indisponible</string>
              <string translatable="false" name="done">Valider</string>
              <string-array translatable="false" name="depth_options_array">
                <item>Occultation par profondeur</item>
                <item>Afficher la carte de profondeur</item>
              </string-array>
              <string translatable="false" name="options_title_instant_placement">Placement instantané</string>
              <string-array translatable="false" name="instant_placement_options_array">
                <item>Activer le placement instantané</item>
              </string-array>
            </resources>
            """
        ),
        encoding="utf-8",
    )

    layout = BUILD_DIR / "app" / "src" / "main" / "res" / "layout" / "activity_main.xml"
    layout.write_text(
        textwrap.dedent(
            """\
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
                  android:layout_marginStart="18dp"
                  android:layout_marginTop="22dp"
                  android:layout_marginEnd="18dp"
                  android:background="@drawable/hud_panel"
                  android:elevation="8dp"
                  android:orientation="vertical"
                  android:paddingStart="18dp"
                  android:paddingTop="14dp"
                  android:paddingEnd="18dp"
                  android:paddingBottom="14dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="HoloForge AR"
                    android:textColor="#F4FBFF"
                    android:textSize="20sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/status_text"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="Initialisation d’ARCore…"
                    android:textColor="#B8CBD6"
                    android:textSize="13sp" />
              </LinearLayout>

              <View
                  android:layout_width="34dp"
                  android:layout_height="34dp"
                  android:layout_gravity="center"
                  android:background="@drawable/reticle" />

              <LinearLayout
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:layout_gravity="bottom"
                  android:layout_marginStart="18dp"
                  android:layout_marginEnd="18dp"
                  android:layout_marginBottom="24dp"
                  android:background="@drawable/hud_panel"
                  android:elevation="10dp"
                  android:gravity="center_vertical"
                  android:orientation="horizontal"
                  android:padding="8dp">

                <TextView
                    android:id="@+id/undo_button"
                    android:layout_width="0dp"
                    android:layout_height="52dp"
                    android:layout_weight="1"
                    android:background="@drawable/button_dark"
                    android:gravity="center"
                    android:text="Annuler"
                    android:textColor="#F4FBFF"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/object_count"
                    android:layout_width="0dp"
                    android:layout_height="52dp"
                    android:layout_marginStart="8dp"
                    android:layout_marginEnd="8dp"
                    android:layout_weight="1.18"
                    android:background="@drawable/button_accent"
                    android:gravity="center"
                    android:text="Touchez pour placer"
                    android:textColor="#031218"
                    android:textSize="13sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/clear_button"
                    android:layout_width="0dp"
                    android:layout_height="52dp"
                    android:layout_weight="1"
                    android:background="@drawable/button_dark"
                    android:gravity="center"
                    android:text="Effacer"
                    android:textColor="#F4FBFF"
                    android:textSize="14sp"
                    android:textStyle="bold" />

                <ImageButton
                    android:id="@+id/settings_button"
                    android:layout_width="52dp"
                    android:layout_height="52dp"
                    android:layout_marginStart="8dp"
                    android:background="@drawable/button_dark"
                    android:contentDescription="Réglages AR"
                    android:padding="14dp"
                    android:src="@drawable/ic_settings"
                    android:tint="#F4FBFF" />
              </LinearLayout>
            </FrameLayout>
            """
        ),
        encoding="utf-8",
    )

    drawable = BUILD_DIR / "app" / "src" / "main" / "res" / "drawable"
    (drawable / "hud_panel.xml").write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
  <solid android:color="#D9142530" />
  <stroke android:width="1dp" android:color="#554DDFF5" />
  <corners android:radius="24dp" />
</shape>
""",
        encoding="utf-8",
    )
    (drawable / "button_dark.xml").write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
  <item android:state_pressed="true"><shape><solid android:color="#405B6A"/><corners android:radius="18dp"/></shape></item>
  <item><shape><solid android:color="#253945"/><stroke android:width="1dp" android:color="#445EEAFA"/><corners android:radius="18dp"/></shape></item>
</selector>
""",
        encoding="utf-8",
    )
    (drawable / "button_accent.xml").write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
  <item android:state_pressed="true"><shape><solid android:color="#7AF0FF"/><corners android:radius="18dp"/></shape></item>
  <item><shape><gradient android:angle="0" android:startColor="#4EDDF4" android:endColor="#63F2FF"/><corners android:radius="18dp"/></shape></item>
</selector>
""",
        encoding="utf-8",
    )
    (drawable / "reticle.xml").write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
  <item android:left="14dp" android:right="14dp"><shape><solid android:color="#DFFFFFFF"/><corners android:radius="2dp"/></shape></item>
  <item android:top="14dp" android:bottom="14dp"><shape><solid android:color="#DFFFFFFF"/><corners android:radius="2dp"/></shape></item>
</layer-list>
""",
        encoding="utf-8",
    )

    models = BUILD_DIR / "app" / "src" / "main" / "assets" / "models"
    (models / "holocube.obj").write_text(
        textwrap.dedent(
            """\
            o HoloCube
            v -0.050 -0.050  0.050
            v  0.050 -0.050  0.050
            v  0.050  0.050  0.050
            v -0.050  0.050  0.050
            v  0.050 -0.050 -0.050
            v -0.050 -0.050 -0.050
            v -0.050  0.050 -0.050
            v  0.050  0.050 -0.050
            vt 0.0 0.0
            vt 1.0 0.0
            vt 1.0 1.0
            vt 0.0 1.0
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
            """
        ),
        encoding="utf-8",
    )
    write_solid_png(models / "holocube_albedo.png", (28, 215, 245, 255))
    write_solid_png(models / "holocube_albedo_instant.png", (255, 188, 60, 255))

    activity = (
        BUILD_DIR
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "google"
        / "ar"
        / "core"
        / "examples"
        / "java"
        / "helloar"
        / "HelloArActivity.java"
    )
    source = activity.read_text(encoding="utf-8")
    source = source.replace(
        'private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";',
        'private static final String SEARCHING_PLANE_MESSAGE = "Déplace le téléphone pour détecter les surfaces";',
    )
    source = source.replace(
        'private static final String WAITING_FOR_TAP_MESSAGE = "Tap on a surface to place an object.";',
        'private static final String WAITING_FOR_TAP_MESSAGE = "Touchez une surface pour placer un cube";',
    )
    source = source.replace(
        "import android.widget.Toast;",
        "import android.widget.Toast;\nimport android.widget.TextView;",
    )
    source = source.replace(
        "private SampleRender render;",
        "private SampleRender render;\n  private TextView statusText;\n  private TextView objectCountText;\n  private String lastHudMessage = \"\";",
    )
    source = source.replace(
        "    settingsButton.setOnClickListener(\n        new View.OnClickListener() {",
        "    statusText = findViewById(R.id.status_text);\n"
        "    objectCountText = findViewById(R.id.object_count);\n"
        "    findViewById(R.id.undo_button).setOnClickListener(v -> undoLastObject());\n"
        "    findViewById(R.id.clear_button).setOnClickListener(v -> clearAllObjects());\n"
        "    objectCountText.setOnClickListener(v ->\n"
        "        Toast.makeText(this, \"Touchez directement une surface détectée\", Toast.LENGTH_SHORT).show());\n\n"
        "    settingsButton.setOnClickListener(\n        new View.OnClickListener() {",
    )
    marker = "  /** Menu button to launch feature specific settings. */"
    helpers = textwrap.dedent(
        """
          private void undoLastObject() {
            surfaceView.queueEvent(
                () -> {
                  if (!wrappedAnchors.isEmpty()) {
                    WrappedAnchor removed = wrappedAnchors.remove(wrappedAnchors.size() - 1);
                    removed.getAnchor().detach();
                    updateObjectCounter();
                  }
                });
          }

          private void clearAllObjects() {
            surfaceView.queueEvent(
                () -> {
                  for (WrappedAnchor wrappedAnchor : wrappedAnchors) {
                    wrappedAnchor.getAnchor().detach();
                  }
                  wrappedAnchors.clear();
                  updateObjectCounter();
                });
          }

          private void updateObjectCounter() {
            final int count = wrappedAnchors.size();
            runOnUiThread(
                () -> {
                  if (objectCountText != null) {
                    objectCountText.setText(count == 0 ? "Touchez pour placer" : count + " objet" + (count > 1 ? "s" : ""));
                  }
                });
          }

          private void updateHudMessage(String message) {
            if (message == null || message.equals(lastHudMessage)) {
              return;
            }
            lastHudMessage = message;
            runOnUiThread(() -> {
              if (statusText != null) {
                statusText.setText(message);
              }
            });
          }

        """
    )
    if marker not in source:
        raise RuntimeError("Unable to locate settings marker in HelloArActivity")
    source = source.replace(marker, helpers + marker, 1)
    source = source.replace(
        '"models/pawn_albedo.png"',
        '"models/holocube_albedo.png"',
    )
    source = source.replace(
        '"models/pawn_albedo_instant_placement.png"',
        '"models/holocube_albedo_instant.png"',
    )
    source = source.replace(
        'virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");',
        'virtualObjectMesh = Mesh.createFromAsset(render, "models/holocube.obj");',
    )
    source = source.replace(
        "    if (message == null) {\n      messageSnackbarHelper.hide(this);",
        "    updateHudMessage(message == null ? \"Suivi actif · touchez une surface\" : message);\n"
        "    if (message == null) {\n      messageSnackbarHelper.hide(this);",
    )
    source = source.replace(
        "          wrappedAnchors.add(new WrappedAnchor(hit.createAnchor(), trackable));",
        "          wrappedAnchors.add(new WrappedAnchor(hit.createAnchor(), trackable));\n"
        "          updateObjectCounter();",
    )
    activity.write_text(source, encoding="utf-8")

    manifest = BUILD_DIR / "app" / "src" / "main" / "AndroidManifest.xml"
    manifest_text = manifest.read_text(encoding="utf-8")
    manifest_text = manifest_text.replace(
        'android:screenOrientation="locked"',
        'android:screenOrientation="portrait"',
    )
    manifest.write_text(manifest_text, encoding="utf-8")

    print(f"Prepared official ARCore project at {BUILD_DIR}")


if __name__ == "__main__":
    main()
