#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import sys
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
V05_OUT = ROOT / "holoforge_v05_build"
OUT = ROOT / "holoforge_v06_build"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Missing patch target: {label}")
    return text.replace(old, new, 1)


def main() -> None:
    shutil.rmtree(V05_OUT, ignore_errors=True)
    shutil.rmtree(OUT, ignore_errors=True)
    subprocess.run([sys.executable, str(ROOT / "holoforge_v05/prepare_v05.py")], check=True)
    V05_OUT.rename(OUT)

    gradle_path = OUT / "app/build.gradle"
    gradle = gradle_path.read_text()
    gradle = gradle.replace('applicationId "com.neo.holoforge.ar.v05"', 'applicationId "com.neo.holoforge.ar.v06"')
    gradle = gradle.replace("versionCode 5", "versionCode 6")
    gradle = gradle.replace("versionName '0.5.0'", "versionName '0.6.0'")
    gradle_path.write_text(gradle)

    layout_path = OUT / "app/src/main/res/layout/activity_main.xml"
    layout = layout_path.read_text()
    status_block = '''            <TextView
                android:id="@+id/axis_status"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="5dp"
                android:text="Scanne la pièce puis touche une surface"
                android:textColor="#B5CAD5"
                android:textSize="13sp" />'''
    mode_row = status_block + '''

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="42dp"
                android:layout_marginTop="10dp"
                android:orientation="horizontal">
              <TextView
                  android:id="@+id/mode_touch"
                  style="@style/HoloAxisButton"
                  android:text="TACTILE" />
              <TextView
                  android:id="@+id/mode_one_hand"
                  style="@style/HoloAxisButton"
                  android:text="UNE MAIN" />
            </LinearLayout>'''
    layout = replace_once(layout, status_block, mode_row, "manual control mode row")
    layout_path.write_text(layout)

    shader_path = OUT / "app/src/main/assets/shaders/hologram.frag"
    shader_path.write_text(textwrap.dedent('''\
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
          vec3 normal = normalize(v_ViewNormal);
          vec3 eyeDir = normalize(-v_ViewPosition);
          float fresnel = pow(1.0 - abs(dot(eyeDir, normal)), 2.0);
          float scan = 0.5 + 0.5 * sin(v_ViewPosition.y * 95.0 + u_Time * 5.0);
          float gx = smoothstep(0.91, 1.0, abs(sin(v_TexCoord.x * 25.1327)));
          float gy = smoothstep(0.91, 1.0, abs(sin(v_TexCoord.y * 25.1327)));
          float grid = max(gx, gy);
          float pulse = u_Selected ? (0.88 + 0.12 * sin(u_Time * 4.2)) : 0.92;

          vec3 base = max(u_Color.rgb, vec3(0.06));
          vec3 color = base * (0.72 + 0.28 * scan);
          color += vec3(0.32, 0.95, 1.0) * fresnel * 0.80;
          color += base * grid * 0.38;
          color *= pulse;
          color = clamp(color, 0.0, 1.0);

          // Opaque output on purpose: this is the reliable visibility pass for v0.6.
          o_FragColor = vec4(color, 1.0);
        }
    '''))

    activity_path = OUT / "app/src/main/java/com/google/ar/core/examples/java/helloar/HelloArActivity.java"
    source = activity_path.read_text()

    source = replace_once(
        source,
        '  private enum AxisMode { X, Y, Z, ROTATE, SCALE }',
        '  private enum AxisMode { X, Y, Z, ROTATE, SCALE }\n'
        '  private enum ControlMode { TOUCH, ONE_HAND }',
        "control mode enum",
    )
    source = replace_once(
        source,
        '  private volatile AxisMode axisMode = AxisMode.X;\n'
        '  private volatile int selectedIndex = -1;',
        '  private volatile AxisMode axisMode = AxisMode.X;\n'
        '  private volatile ControlMode controlMode = ControlMode.TOUCH;\n'
        '  private volatile int selectedIndex = -1;',
        "control mode field",
    )

    source = replace_once(
        source,
        '    findViewById(R.id.clear_objects).setOnClickListener(v -> surfaceView.queueEvent(this::clearAllObjects));\n'
        '    setAxisMode(AxisMode.X);\n'
        '    updateHud();',
        '    findViewById(R.id.clear_objects).setOnClickListener(v -> surfaceView.queueEvent(this::clearAllObjects));\n'
        '    findViewById(R.id.mode_touch).setOnClickListener(v -> setControlMode(ControlMode.TOUCH));\n'
        '    findViewById(R.id.mode_one_hand).setOnClickListener(v -> setControlMode(ControlMode.ONE_HAND));\n'
        '    setControlMode(ControlMode.TOUCH);\n'
        '    setAxisMode(AxisMode.X);\n'
        '    updateHud();',
        "mode button listeners",
    )

    source = replace_once(
        source,
        '      private void setAxisMode(AxisMode mode) {',
        '''      private void setControlMode(ControlMode mode) {
        controlMode = mode;
        runOnUiThread(() -> {
          findViewById(R.id.mode_touch).setSelected(mode == ControlMode.TOUCH);
          findViewById(R.id.mode_one_hand).setSelected(mode == ControlMode.ONE_HAND);
          updateHud();
        });
      }

      private String controlLabel() {
        return controlMode == ControlMode.ONE_HAND ? "Mode une main" : "Mode tactile";
      }

      private void setAxisMode(AxisMode mode) {''',
        "control mode helpers",
    )

    source = source.replace(
        ': axisLabel() + " · objet " + (selectedIndex + 1) + "/" + count);',
        ': controlLabel() + " · " + axisLabel() + " · objet " + (selectedIndex + 1) + "/" + count);',
    )
    source = replace_once(
        source,
        '          float dx = drag[0];\n'
        '          float dy = drag[1];',
        '          float sensitivity = controlMode == ControlMode.ONE_HAND ? 0.62f : 1.0f;\n'
        '          float dx = drag[0] * sensitivity;\n'
        '          float dy = drag[1] * sensitivity;',
        "one hand drag sensitivity",
    )

    source = source.replace(
        '.setDepthWrite(false)\n'
        '              .setCullFace(false)\n'
        '              .setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA);',
        '.setDepthWrite(true)\n'
        '              .setCullFace(false);',
    )

    source = source.replace('float centerY = object.offsetY + 0.10f * object.scale;',
                            'float centerY = object.offsetY + 0.175f * object.scale;')
    source = source.replace('0.20f * object.scale, 0.20f * object.scale, 0.20f * object.scale,',
                            '0.35f * object.scale, 0.35f * object.scale, 0.35f * object.scale,')
    source = source.replace(
        'selected ? new float[] {0.12f, 1.00f, 1.00f, 0.90f} : new float[] {0.05f, 0.78f, 1.00f, 0.68f},',
        'selected ? new float[] {0.02f, 1.00f, 1.00f, 1.00f} : new float[] {0.02f, 0.62f, 1.00f, 1.00f},',
    )
    source = source.replace('0.42f, 0.026f, 0.026f,', '0.52f, 0.035f, 0.035f,')
    source = source.replace('0.026f, 0.42f, 0.026f,', '0.035f, 0.52f, 0.035f,')
    source = source.replace('0.026f, 0.026f, 0.42f,', '0.035f, 0.035f, 0.52f,')

    # Disable depth occlusion in the reliability build. It remains available later,
    # but cannot hide the first visible object in v0.6.
    source = source.replace(
        'backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());',
        'backgroundRenderer.setUseOcclusion(render, false);',
    )

    # Make placement confirmation explicit on screen.
    source = source.replace(
        '          updateHud();\n'
        '          // For devices that support the Depth API, shows a dialog to suggest enabling',
        '          updateHud();\n'
        '          runOnUiThread(() -> axisStatusText.setText("Hologramme placé · " + controlLabel() + " · " + axisLabel()));\n'
        '          // For devices that support the Depth API, shows a dialog to suggest enabling',
    )

    activity_path.write_text(source)
    print("Prepared HoloForge v0.6 at", OUT)


if __name__ == "__main__":
    main()
