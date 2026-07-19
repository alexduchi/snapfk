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
    layout = layout.replace('android:id="@+id/next_object" style="@style/HoloUtilityButton" android:text="Objet suivant"',
                            'android:id="@+id/next_object" style="@style/HoloUtilityButton" android:text="MODE : TACTILE"')
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
          float pulse = u_Selected ? (0.90 + 0.10 * sin(u_Time * 4.2)) : 0.95;
          vec3 base = max(u_Color.rgb, vec3(0.08));
          vec3 color = base * (0.78 + 0.22 * scan);
          color += vec3(0.35, 0.95, 1.0) * fresnel * 0.9;
          color += base * grid * 0.42;
          o_FragColor = vec4(clamp(color * pulse, 0.0, 1.0), 1.0);
        }
    '''))

    activity_path = OUT / "app/src/main/java/com/google/ar/core/examples/java/helloar/HelloArActivity.java"
    source = activity_path.read_text()
    source = replace_once(source,
        '  private enum AxisMode { X, Y, Z, ROTATE, SCALE }',
        '  private enum AxisMode { X, Y, Z, ROTATE, SCALE }\n  private enum ControlMode { TOUCH, ONE_HAND }',
        'control enum')
    source = replace_once(source,
        '  private volatile AxisMode axisMode = AxisMode.X;\n  private volatile int selectedIndex = -1;',
        '  private volatile AxisMode axisMode = AxisMode.X;\n  private volatile ControlMode controlMode = ControlMode.TOUCH;\n  private volatile int selectedIndex = -1;',
        'control field')
    source = replace_once(source,
        '    findViewById(R.id.next_object).setOnClickListener(v -> surfaceView.queueEvent(this::selectNextObject));',
        '    findViewById(R.id.next_object).setOnClickListener(v -> toggleControlMode());',
        'mode button listener')
    source = replace_once(source,
        '      private void setAxisMode(AxisMode mode) {',
        '''      private void toggleControlMode() {
        controlMode = controlMode == ControlMode.TOUCH ? ControlMode.ONE_HAND : ControlMode.TOUCH;
        runOnUiThread(() -> {
          TextView button = findViewById(R.id.next_object);
          button.setText(controlMode == ControlMode.ONE_HAND ? "MODE : UNE MAIN" : "MODE : TACTILE");
          updateHud();
        });
      }

      private String controlLabel() {
        return controlMode == ControlMode.ONE_HAND ? "Mode une main" : "Mode tactile";
      }

      private void setAxisMode(AxisMode mode) {''',
        'control methods')
    source = source.replace(
        ': axisLabel() + " · objet " + (selectedIndex + 1) + "/" + count);',
        ': controlLabel() + " · " + axisLabel() + " · objet " + (selectedIndex + 1) + "/" + count);')
    source = replace_once(source,
        '          float dx = drag[0];\n          float dy = drag[1];',
        '          float sensitivity = controlMode == ControlMode.ONE_HAND ? 0.62f : 1.0f;\n          float dx = drag[0] * sensitivity;\n          float dy = drag[1] * sensitivity;',
        'one hand sensitivity')
    source = source.replace(
        '.setDepthWrite(false)\n              .setCullFace(false)\n              .setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA);',
        '.setDepthWrite(true)\n              .setCullFace(false);')
    source = source.replace('float centerY = object.offsetY + 0.10f * object.scale;',
                            'float centerY = object.offsetY + 0.20f * object.scale;')
    source = source.replace('0.20f * object.scale, 0.20f * object.scale, 0.20f * object.scale,',
                            '0.40f * object.scale, 0.40f * object.scale, 0.40f * object.scale,')
    source = source.replace(
        'selected ? new float[] {0.12f, 1.00f, 1.00f, 0.90f} : new float[] {0.05f, 0.78f, 1.00f, 0.68f},',
        'selected ? new float[] {0.02f, 1.00f, 1.00f, 1.00f} : new float[] {0.02f, 0.62f, 1.00f, 1.00f},')
    source = source.replace('0.42f, 0.026f, 0.026f,', '0.58f, 0.045f, 0.045f,')
    source = source.replace('0.026f, 0.42f, 0.026f,', '0.045f, 0.58f, 0.045f,')
    source = source.replace('0.026f, 0.026f, 0.42f,', '0.045f, 0.045f, 0.58f,')
    source = source.replace(
        'backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());',
        'backgroundRenderer.setUseOcclusion(render, false);')
    source = source.replace(
        '          updateHud();\n          // For devices that support the Depth API, shows a dialog to suggest enabling',
        '          updateHud();\n          runOnUiThread(() -> axisStatusText.setText("Hologramme visible placé · " + controlLabel() + " · " + axisLabel()));\n          // For devices that support the Depth API, shows a dialog to suggest enabling')
    activity_path.write_text(source)
    print("Prepared clean HoloForge v0.6 at", OUT)


if __name__ == "__main__":
    main()
