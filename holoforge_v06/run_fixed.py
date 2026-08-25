#!/usr/bin/env python3
from pathlib import Path

script_path = Path(__file__).with_name("prepare_v06.py")
source = script_path.read_text()
start = source.index("    marker = '''")
end = source.index("    layout_path.write_text(layout)", start)
mode_row = '''
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
replacement = (
    "    status_pos = layout.index('android:id=\"@+id/axis_status\"')\n"
    "    insert_pos = layout.index('/>', status_pos) + 2\n"
    f"    mode_row = {mode_row!r}\n"
    "    layout = layout[:insert_pos] + mode_row + layout[insert_pos:]\n"
)
source = source[:start] + replacement + source[end:]
source = source.replace(
    "'      private void setAxisMode(AxisMode mode) {'",
    "'private void setAxisMode(AxisMode mode) {'",
)
namespace = {"__name__": "__main__", "__file__": str(script_path)}
exec(compile(source, str(script_path), "exec"), namespace)
