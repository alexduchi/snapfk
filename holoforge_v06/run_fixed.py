#!/usr/bin/env python3
from pathlib import Path

script_path = Path(__file__).with_name("prepare_v06.py")
source = script_path.read_text()
start = source.index("    marker = '''")
end = source.index("    layout = replace_once", start)
marker = '          <View\n              android:layout_width="38dp"'
mode_row = '''            <LinearLayout
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
            </LinearLayout>

          <View
              android:layout_width="38dp"'''
replacement = f"    marker = {marker!r}\n    mode_row = {mode_row!r}\n"
source = source[:start] + replacement + source[end:]
namespace = {"__name__": "__main__", "__file__": str(script_path)}
exec(compile(source, str(script_path), "exec"), namespace)
