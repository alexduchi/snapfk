#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / ".build" / "arcore-android-sdk-v05"
OUT = ROOT / "holoforge_v05_build"


def run(*args: str) -> None:
    print("+", " ".join(args), flush=True)
    subprocess.run(args, check=True)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Missing patch target: {label}")
    return text.replace(old, new, 1)


def main() -> None:
    shutil.rmtree(SDK, ignore_errors=True)
    shutil.rmtree(OUT, ignore_errors=True)
    SDK.parent.mkdir(parents=True, exist_ok=True)
    run(
        "git", "clone", "--depth", "1", "--branch", "1.54.0",
        "https://github.com/google-ar/arcore-android-sdk.git", str(SDK)
    )
    shutil.copytree(SDK / "samples" / "hello_ar_java", OUT)

    app_gradle = OUT / "app/build.gradle"
    gradle = app_gradle.read_text()
    gradle = gradle.replace(
        'applicationId "com.google.ar.core.examples.java.helloar"',
        'applicationId "com.neo.holoforge.ar.v05"'
    )
    gradle = gradle.replace("versionCode 1", "versionCode 5")
    gradle = gradle.replace("versionName '1.0'", "versionName '0.5.0'")
    app_gradle.write_text(gradle)

    manifest = OUT / "app/src/main/AndroidManifest.xml"
    m = manifest.read_text()
    m = m.replace('\n    package="com.google.ar.core.examples.java.helloar">', '>')
    m = m.replace('android:screenOrientation="locked"', 'android:screenOrientation="portrait"')
    manifest.write_text(m)

    strings = OUT / "app/src/main/res/values/strings.xml"
    s = strings.read_text().replace("HelloAR Java", "HoloForge AR v0.5")
    s = s.replace(
        "When Depth is enabled on supported devices, virtual objects adapt to the environment by blending in with the real world.",
        "La profondeur permet aux hologrammes de passer derrière les objets réels."
    )
    strings.write_text(s)

    layout = OUT / "app/src/main/res/layout/activity_main.xml"
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
              android:layout_marginStart="16dp"
              android:layout_marginTop="20dp"
              android:layout_marginEnd="16dp"
              android:background="@drawable/holo_panel"
              android:orientation="vertical"
              android:paddingStart="17dp"
              android:paddingTop="13dp"
              android:paddingEnd="17dp"
              android:paddingBottom="13dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">
              <TextView
                  android:layout_width="0dp"
                  android:layout_height="wrap_content"
                  android:layout_weight="1"
                  android:text="HoloForge AR"
                  android:textColor="#F2FCFF"
                  android:textSize="20sp"
                  android:textStyle="bold" />
              <TextView
                  android:id="@+id/object_status"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:background="@drawable/holo_chip"
                  android:paddingStart="12dp"
                  android:paddingTop="6dp"
                  android:paddingEnd="12dp"
                  android:paddingBottom="6dp"
                  android:text="Aucun objet"
                  android:textColor="#66ECFF"
                  android:textSize="12sp"
                  android:textStyle="bold" />
            </LinearLayout>

            <TextView
                android:id="@+id/axis_status"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="5dp"
                android:text="Scanne la pièce puis touche une surface"
                android:textColor="#B5CAD5"
                android:textSize="13sp" />
          </LinearLayout>

          <View
              android:layout_width="38dp"
              android:layout_height="38dp"
              android:layout_gravity="center"
              android:background="@drawable/holo_reticle" />

          <LinearLayout
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:layout_gravity="bottom"
              android:layout_marginStart="12dp"
              android:layout_marginEnd="12dp"
              android:layout_marginBottom="20dp"
              android:background="@drawable/holo_panel"
              android:orientation="vertical"
              android:padding="8dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="48dp"
                android:gravity="center"
                android:orientation="horizontal">
              <TextView android:id="@+id/axis_x" style="@style/HoloAxisButton" android:text="X" android:textColor="#FF5863" />
              <TextView android:id="@+id/axis_y" style="@style/HoloAxisButton" android:text="Y" android:textColor="#61F29A" />
              <TextView android:id="@+id/axis_z" style="@style/HoloAxisButton" android:text="Z" android:textColor="#52A7FF" />
              <TextView android:id="@+id/mode_rotate" style="@style/HoloAxisButton" android:text="ROT" />
              <TextView android:id="@+id/mode_scale" style="@style/HoloAxisButton" android:text="TAILLE" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="46dp"
                android:layout_marginTop="6dp"
                android:gravity="center"
                android:orientation="horizontal">
              <TextView android:id="@+id/next_object" style="@style/HoloUtilityButton" android:text="Objet suivant" />
              <TextView android:id="@+id/reset_object" style="@style/HoloUtilityButton" android:text="Réinitialiser" />
              <TextView android:id="@+id/clear_objects" style="@style/HoloUtilityButton" android:text="Effacer" />
              <ImageButton
                  android:id="@+id/settings_button"
                  android:layout_width="48dp"
                  android:layout_height="match_parent"
                  android:layout_marginStart="6dp"
                  android:background="@drawable/holo_button"
                  android:contentDescription="Réglages AR"
                  android:padding="13dp"
                  android:src="@drawable/ic_settings"
                  android:tint="#EAFBFF" />
            </LinearLayout>
          </LinearLayout>
        </FrameLayout>
    '''))

    values = OUT / "app/src/main/res/values"
    (values / "holo_styles.xml").write_text(textwrap.dedent('''\
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
          <style name="HoloAxisButton">
            <item name="android:layout_width">0dp</item>
            <item name="android:layout_height">match_parent</item>
            <item name="android:layout_weight">1</item>
            <item name="android:layout_marginStart">3dp</item>
            <item name="android:layout_marginEnd">3dp</item>
            <item name="android:background">@drawable/holo_button</item>
            <item name="android:gravity">center</item>
            <item name="android:textColor">#EAFBFF</item>
            <item name="android:textSize">13sp</item>
            <item name="android:textStyle">bold</item>
          </style>
          <style name="HoloUtilityButton">
            <item name="android:layout_width">0dp</item>
            <item name="android:layout_height">match_parent</item>
            <item name="android:layout_weight">1</item>
            <item name="android:layout_marginStart">3dp</item>
            <item name="android:layout_marginEnd">3dp</item>
            <item name="android:background">@drawable/holo_button</item>
            <item name="android:gravity">center</item>
            <item name="android:textColor">#EAFBFF</item>
            <item name="android:textSize">11sp</item>
            <item name="android:textStyle">bold</item>
          </style>
        </resources>
    '''))

    drawable = OUT / "app/src/main/res/drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / "holo_panel.xml").write_text(textwrap.dedent('''\
        <?xml version="1.0" encoding="utf-8"?>
        <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
          <gradient android:angle="90" android:startColor="#E812222C" android:endColor="#D91A303A" />
          <stroke android:width="1dp" android:color="#6B53E7FA" />
          <corners android:radius="23dp" />
        </shape>
    '''))
    (drawable / "holo_button.xml").write_text(textwrap.dedent('''\
        <?xml version="1.0" encoding="utf-8"?>
        <selector xmlns:android="http://schemas.android.com/apk/res/android">
          <item android:state_selected="true"><shape><solid android:color="#704DE4F7"/><stroke android:width="2dp" android:color="#9D64F3FF"/><corners android:radius="16dp"/></shape></item>
          <item android:state_pressed="true"><shape><solid android:color="#6146CDE0"/><corners android:radius="16dp"/></shape></item>
          <item><shape><solid android:color="#B8253A46"/><stroke android:width="1dp" android:color="#4C65DDEC"/><corners android:radius="16dp"/></shape></item>
        </selector>
    '''))
    (drawable / "holo_chip.xml").write_text(textwrap.dedent('''\
        <?xml version="1.0" encoding="utf-8"?>
        <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
          <solid android:color="#4434C9DE" />
          <stroke android:width="1dp" android:color="#7859E9FB" />
          <corners android:radius="14dp" />
        </shape>
    '''))
    (drawable / "holo_reticle.xml").write_text(textwrap.dedent('''\
        <?xml version="1.0" encoding="utf-8"?>
        <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
          <item android:left="17dp" android:right="17dp"><shape><solid android:color="#DFFFFFFF"/><corners android:radius="2dp"/></shape></item>
          <item android:top="17dp" android:bottom="17dp"><shape><solid android:color="#DFFFFFFF"/><corners android:radius="2dp"/></shape></item>
        </layer-list>
    '''))

    shaders = OUT / "app/src/main/assets/shaders"
    (shaders / "hologram.vert").write_text(textwrap.dedent('''\
        #version 300 es
        uniform mat4 u_ModelView;
        uniform mat4 u_ModelViewProjection;
        layout(location = 0) in vec4 a_Position;
        layout(location = 1) in vec2 a_TexCoord;
        layout(location = 2) in vec3 a_Normal;
        out vec3 v_ViewPosition;
        out vec3 v_ViewNormal;
        out vec2 v_TexCoord;
        void main() {
          v_ViewPosition = (u_ModelView * a_Position).xyz;
          v_ViewNormal = normalize((u_ModelView * vec4(a_Normal, 0.0)).xyz);
          v_TexCoord = a_TexCoord;
          gl_Position = u_ModelViewProjection * a_Position;
        }
    '''))
    (shaders / "hologram.frag").write_text(textwrap.dedent('''\
        #version 300 es
        precision highp float;
        uniform vec4 u_Color;
        uniform float u_Time;
        uniform bool u_Selected;
        in vec3 v_ViewPosition;
        in vec3 v_ViewNormal;
        in vec2 v_TexCoord;
        out vec4 o_FragColor;
        void main() {
          vec3 eyeDir = normalize(-v_ViewPosition);
          float fresnel = pow(1.0 - abs(dot(eyeDir, normalize(v_ViewNormal))), 2.2);
          float scan = 0.5 + 0.5 * sin(v_ViewPosition.y * 125.0 + u_Time * 5.5);
          float gx = smoothstep(0.93, 1.0, abs(sin(v_TexCoord.x * 31.4159)));
          float gy = smoothstep(0.93, 1.0, abs(sin(v_TexCoord.y * 31.4159)));
          float grid = max(gx, gy);
          float pulse = u_Selected ? (0.84 + 0.16 * sin(u_Time * 4.0)) : 0.78;
          vec3 glow = u_Color.rgb * (0.75 + 1.7 * fresnel + 0.24 * scan + 0.30 * grid);
          float alpha = u_Color.a * pulse * (0.28 + 0.48 * fresnel + 0.10 * scan + 0.16 * grid);
          o_FragColor = vec4(glow, clamp(alpha, 0.10, 0.95));
        }
    '''))

    models = OUT / "app/src/main/assets/models"
    (models / "pawn.obj").write_text(textwrap.dedent('''\
        o HoloCube
        v -0.5 -0.5  0.5
        v  0.5 -0.5  0.5
        v  0.5  0.5  0.5
        v -0.5  0.5  0.5
        v  0.5 -0.5 -0.5
        v -0.5 -0.5 -0.5
        v -0.5  0.5 -0.5
        v  0.5  0.5 -0.5
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
    '''))

    tap = OUT / "app/src/main/java/com/google/ar/core/examples/java/common/helpers/TapHelper.java"
    tap.write_text(textwrap.dedent('''\
        package com.google.ar.core.examples.java.common.helpers;

        import android.content.Context;
        import android.view.GestureDetector;
        import android.view.MotionEvent;
        import android.view.View;
        import android.view.View.OnTouchListener;
        import java.util.concurrent.ArrayBlockingQueue;
        import java.util.concurrent.BlockingQueue;

        public final class TapHelper implements OnTouchListener {
          private final GestureDetector gestureDetector;
          private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);
          private final BlockingQueue<float[]> queuedDrags = new ArrayBlockingQueue<>(64);

          public TapHelper(Context context) {
            gestureDetector = new GestureDetector(
                context,
                new GestureDetector.SimpleOnGestureListener() {
                  @Override
                  public boolean onSingleTapUp(MotionEvent e) {
                    queuedSingleTaps.offer(MotionEvent.obtain(e));
                    return true;
                  }

                  @Override
                  public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    queuedDrags.offer(new float[] {-distanceX, -distanceY});
                    return true;
                  }

                  @Override
                  public boolean onDown(MotionEvent e) {
                    return true;
                  }
                });
          }

          public MotionEvent poll() {
            return queuedSingleTaps.poll();
          }

          public float[] pollDrag() {
            return queuedDrags.poll();
          }

          @Override
          public boolean onTouch(View view, MotionEvent motionEvent) {
            return gestureDetector.onTouchEvent(motionEvent);
          }
        }
    '''))

    activity = OUT / "app/src/main/java/com/google/ar/core/examples/java/helloar/HelloArActivity.java"
    a = activity.read_text()
    a = a.replace('import android.widget.Toast;', 'import android.widget.Toast;\nimport android.widget.TextView;')
    a = a.replace(
        'private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";',
        'private static final String SEARCHING_PLANE_MESSAGE = "Déplace le téléphone pour scanner la pièce";'
    )
    a = a.replace(
        'private static final String WAITING_FOR_TAP_MESSAGE = "Tap on a surface to place an object.";',
        'private static final String WAITING_FOR_TAP_MESSAGE = "Touche une surface pour créer un hologramme";'
    )
    a = replace_once(
        a,
        'public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {',
        'public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {\n\n'
        '  private enum AxisMode { X, Y, Z, ROTATE, SCALE }',
        'axis enum'
    )
    a = replace_once(
        a,
        '  private SampleRender render;\n',
        '  private SampleRender render;\n'
        '  private TextView axisStatusText;\n'
        '  private TextView objectStatusText;\n'
        '  private volatile AxisMode axisMode = AxisMode.X;\n'
        '  private volatile int selectedIndex = -1;\n',
        'hud fields'
    )
    a = replace_once(
        a,
        '  private final float[] modelMatrix = new float[16];\n',
        '  private final float[] modelMatrix = new float[16];\n'
        '  private final float[] anchorMatrix = new float[16];\n',
        'anchor matrix'
    )

    oncreate_tail = '''        });
  }

  /** Menu button to launch feature specific settings. */'''
    oncreate_new = '''        });

    axisStatusText = findViewById(R.id.axis_status);
    objectStatusText = findViewById(R.id.object_status);
    findViewById(R.id.axis_x).setOnClickListener(v -> setAxisMode(AxisMode.X));
    findViewById(R.id.axis_y).setOnClickListener(v -> setAxisMode(AxisMode.Y));
    findViewById(R.id.axis_z).setOnClickListener(v -> setAxisMode(AxisMode.Z));
    findViewById(R.id.mode_rotate).setOnClickListener(v -> setAxisMode(AxisMode.ROTATE));
    findViewById(R.id.mode_scale).setOnClickListener(v -> setAxisMode(AxisMode.SCALE));
    findViewById(R.id.next_object).setOnClickListener(v -> surfaceView.queueEvent(this::selectNextObject));
    findViewById(R.id.reset_object).setOnClickListener(v -> surfaceView.queueEvent(this::resetSelectedObject));
    findViewById(R.id.clear_objects).setOnClickListener(v -> surfaceView.queueEvent(this::clearAllObjects));
    setAxisMode(AxisMode.X);
    updateHud();
  }

  /** Menu button to launch feature specific settings. */'''
    a = replace_once(a, oncreate_tail, oncreate_new, 'onCreate controls')

    helper_marker = '  /** Menu button to launch feature specific settings. */'
    helpers = textwrap.dedent('''
      private void setAxisMode(AxisMode mode) {
        axisMode = mode;
        runOnUiThread(() -> {
          findViewById(R.id.axis_x).setSelected(mode == AxisMode.X);
          findViewById(R.id.axis_y).setSelected(mode == AxisMode.Y);
          findViewById(R.id.axis_z).setSelected(mode == AxisMode.Z);
          findViewById(R.id.mode_rotate).setSelected(mode == AxisMode.ROTATE);
          findViewById(R.id.mode_scale).setSelected(mode == AxisMode.SCALE);
          updateHud();
        });
      }

      private String axisLabel() {
        switch (axisMode) {
          case X: return "Axe X · glisse gauche/droite";
          case Y: return "Axe Y · glisse haut/bas";
          case Z: return "Axe Z · glisse haut/bas";
          case ROTATE: return "Rotation Y · glisse gauche/droite";
          case SCALE: return "Taille · glisse haut/bas";
          default: return "Manipulation";
        }
      }

      private void updateHud() {
        runOnUiThread(() -> {
          int count = wrappedAnchors.size();
          if (axisStatusText != null) {
            axisStatusText.setText(count == 0
                ? "Scanne la pièce puis touche une surface"
                : axisLabel() + " · objet " + (selectedIndex + 1) + "/" + count);
          }
          if (objectStatusText != null) {
            objectStatusText.setText(count == 0 ? "Aucun objet" : count + " hologramme" + (count > 1 ? "s" : ""));
          }
        });
      }

      private void selectNextObject() {
        if (wrappedAnchors.isEmpty()) {
          selectedIndex = -1;
        } else {
          selectedIndex = (selectedIndex + 1) % wrappedAnchors.size();
        }
        updateHud();
      }

      private void resetSelectedObject() {
        if (selectedIndex < 0 || selectedIndex >= wrappedAnchors.size()) return;
        WrappedAnchor object = wrappedAnchors.get(selectedIndex);
        object.offsetX = object.offsetY = object.offsetZ = 0f;
        object.rotationY = 0f;
        object.scale = 1f;
        updateHud();
      }

      private void clearAllObjects() {
        for (WrappedAnchor object : wrappedAnchors) object.getAnchor().detach();
        wrappedAnchors.clear();
        selectedIndex = -1;
        updateHud();
      }

      private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
      }

      private void handleAxisDrag() {
        float[] drag;
        while ((drag = tapHelper.pollDrag()) != null) {
          if (selectedIndex < 0 || selectedIndex >= wrappedAnchors.size()) continue;
          WrappedAnchor object = wrappedAnchors.get(selectedIndex);
          float dx = drag[0];
          float dy = drag[1];
          switch (axisMode) {
            case X:
              object.offsetX = clamp(object.offsetX + dx * 0.0018f, -1.5f, 1.5f);
              break;
            case Y:
              object.offsetY = clamp(object.offsetY - dy * 0.0018f, -1.0f, 1.5f);
              break;
            case Z:
              object.offsetZ = clamp(object.offsetZ - dy * 0.0018f, -1.5f, 1.5f);
              break;
            case ROTATE:
              object.rotationY += dx * 0.35f;
              break;
            case SCALE:
              object.scale = clamp(object.scale - dy * 0.004f, 0.35f, 3.0f);
              break;
          }
        }
      }

      private void drawHoloPart(
          SampleRender render,
          float[] base,
          float tx, float ty, float tz,
          float rx, float ry, float rz,
          float sx, float sy, float sz,
          float[] color,
          boolean selected) {
        System.arraycopy(base, 0, modelMatrix, 0, 16);
        Matrix.translateM(modelMatrix, 0, tx, ty, tz);
        if (rx != 0f) Matrix.rotateM(modelMatrix, 0, rx, 1f, 0f, 0f);
        if (ry != 0f) Matrix.rotateM(modelMatrix, 0, ry, 0f, 1f, 0f);
        if (rz != 0f) Matrix.rotateM(modelMatrix, 0, rz, 0f, 0f, 1f);
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz);
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
        virtualObjectShader
            .setMat4("u_ModelView", modelViewMatrix)
            .setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
            .setVec4("u_Color", color)
            .setBool("u_Selected", selected)
            .setFloat("u_Time", System.nanoTime() * 0.000000001f);
        render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
      }

    ''')
    a = replace_once(a, helper_marker, helpers + helper_marker, 'helper methods')

    old_object_block = '''      // Virtual object to render (ARCore pawn)
      virtualObjectAlbedoTexture =
          Texture.createFromAsset(
              render,
              "models/pawn_albedo.png",
              Texture.WrapMode.CLAMP_TO_EDGE,
              Texture.ColorFormat.SRGB);
      virtualObjectAlbedoInstantPlacementTexture =
          Texture.createFromAsset(
              render,
              "models/pawn_albedo_instant_placement.png",
              Texture.WrapMode.CLAMP_TO_EDGE,
              Texture.ColorFormat.SRGB);
      Texture virtualObjectPbrTexture =
          Texture.createFromAsset(
              render,
              "models/pawn_roughness_metallic_ao.png",
              Texture.WrapMode.CLAMP_TO_EDGE,
              Texture.ColorFormat.LINEAR);

      virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
      virtualObjectShader =
          Shader.createFromAssets(
                  render,
                  "shaders/environmental_hdr.vert",
                  "shaders/environmental_hdr.frag",
                  /* defines= */ new HashMap<String, String>() {
                    {
                      put(
                          "NUMBER_OF_MIPMAP_LEVELS",
                          Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                    }
                  })
              .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
              .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
              .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
              .setTexture("u_DfgTexture", dfgTexture);'''
    new_object_block = '''      // HoloForge hologram object and axis gizmo use the same lightweight mesh/shader.
      virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
      virtualObjectShader =
          Shader.createFromAssets(render, "shaders/hologram.vert", "shaders/hologram.frag", null)
              .setDepthTest(true)
              .setDepthWrite(false)
              .setCullFace(false)
              .setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA);'''
    a = replace_once(a, old_object_block, new_object_block, 'hologram shader block')

    a = replace_once(
        a,
        '    // Handle one tap per frame.\n    handleTap(frame, camera);',
        '    // Handle placement taps and one-dimensional manipulation drags.\n    handleTap(frame, camera);\n    handleAxisDrag();',
        'drag frame hook'
    )

    old_render = '''    // Update lighting parameters in the shader
    updateLightEstimation(frame.getLightEstimate(), viewMatrix);

    // Visualize anchors created by touch.
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
    for (WrappedAnchor wrappedAnchor : wrappedAnchors) {
      Anchor anchor = wrappedAnchor.getAnchor();
      Trackable trackable = wrappedAnchor.getTrackable();
      if (anchor.getTrackingState() != TrackingState.TRACKING) {
        continue;
      }

      // Get the current pose of an Anchor in world space. The Anchor pose is updated
      // during calls to session.update() as ARCore refines its estimate of the world.
      anchor.getPose().toMatrix(modelMatrix, 0);

      // Calculate model/view/projection matrices
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

      // Update shader properties and draw
      virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
      virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

      if (trackable instanceof InstantPlacementPoint
          && ((InstantPlacementPoint) trackable).getTrackingMethod()
              == InstantPlacementPoint.TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE) {
        virtualObjectShader.setTexture(
            "u_AlbedoTexture", virtualObjectAlbedoInstantPlacementTexture);
      } else {
        virtualObjectShader.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture);
      }

      render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
    }'''
    new_render = '''    // Draw transparent holograms and the selected object's axis gizmo.
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
    for (int index = 0; index < wrappedAnchors.size(); index++) {
      WrappedAnchor object = wrappedAnchors.get(index);
      Anchor anchor = object.getAnchor();
      if (anchor.getTrackingState() != TrackingState.TRACKING) continue;
      anchor.getPose().toMatrix(anchorMatrix, 0);
      boolean selected = index == selectedIndex;
      float centerY = object.offsetY + 0.10f * object.scale;
      drawHoloPart(
          render, anchorMatrix,
          object.offsetX, centerY, object.offsetZ,
          0f, object.rotationY, 0f,
          0.20f * object.scale, 0.20f * object.scale, 0.20f * object.scale,
          selected ? new float[] {0.12f, 1.00f, 1.00f, 0.90f} : new float[] {0.05f, 0.78f, 1.00f, 0.68f},
          selected);

      if (selected) {
        drawHoloPart(render, anchorMatrix, object.offsetX + 0.30f, centerY, object.offsetZ,
            0f, 0f, 0f, 0.42f, 0.026f, 0.026f,
            new float[] {1.00f, 0.13f, 0.19f, 0.95f}, axisMode == AxisMode.X);
        drawHoloPart(render, anchorMatrix, object.offsetX, centerY + 0.30f, object.offsetZ,
            0f, 0f, 0f, 0.026f, 0.42f, 0.026f,
            new float[] {0.15f, 1.00f, 0.42f, 0.95f}, axisMode == AxisMode.Y);
        drawHoloPart(render, anchorMatrix, object.offsetX, centerY, object.offsetZ + 0.30f,
            0f, 0f, 0f, 0.026f, 0.026f, 0.42f,
            new float[] {0.12f, 0.49f, 1.00f, 0.95f}, axisMode == AxisMode.Z);
      }
    }'''
    a = replace_once(a, old_render, new_render, 'render loop')

    a = replace_once(
        a,
        '          wrappedAnchors.add(new WrappedAnchor(hit.createAnchor(), trackable));',
        '          wrappedAnchors.add(new WrappedAnchor(hit.createAnchor(), trackable));\n'
        '          selectedIndex = wrappedAnchors.size() - 1;\n'
        '          updateHud();',
        'selection after placement'
    )

    old_wrapped = '''class WrappedAnchor {
  private Anchor anchor;
  private Trackable trackable;

  public WrappedAnchor(Anchor anchor, Trackable trackable) {
    this.anchor = anchor;
    this.trackable = trackable;
  }

  public Anchor getAnchor() {
    return anchor;
  }

  public Trackable getTrackable() {
    return trackable;
  }
}'''
    new_wrapped = '''class WrappedAnchor {
  private final Anchor anchor;
  private final Trackable trackable;
  float offsetX;
  float offsetY;
  float offsetZ;
  float rotationY;
  float scale = 1f;

  public WrappedAnchor(Anchor anchor, Trackable trackable) {
    this.anchor = anchor;
    this.trackable = trackable;
  }

  public Anchor getAnchor() {
    return anchor;
  }

  public Trackable getTrackable() {
    return trackable;
  }
}'''
    a = replace_once(a, old_wrapped, new_wrapped, 'wrapped anchor data')
    activity.write_text(a)

    print("Prepared HoloForge v0.5 at", OUT)


if __name__ == "__main__":
    main()
