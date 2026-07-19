#!/usr/bin/env python3
from pathlib import Path

script_path = Path(__file__).with_name("prepare_v06.py")
source = script_path.read_text()
start = source.index("    marker = '''")
end = source.index("    layout = replace_once", start)
replacement = '''    marker = """          <View\n              android:layout_width=\\"38dp\\"""\n    mode_row = """            <LinearLayout\n                android:layout_width=\\"match_parent\\"\n                android:layout_height=\\"42dp\\"\n                android:layout_marginTop=\\"10dp\\"\n                android:orientation=\\"horizontal\\">\n              <TextView\n                  android:id=\\"@+id/mode_touch\\"\n                  style=\\"@style/HoloAxisButton\\"\n                  android:text=\\"TACTILE\\" />\n              <TextView\n                  android:id=\\"@+id/mode_one_hand\\"\n                  style=\\"@style/HoloAxisButton\\"\n                  android:text=\\"UNE MAIN\\" />\n            </LinearLayout>\n\n          <View\n              android:layout_width=\\"38dp\\"""\n'''
source = source[:start] + replacement + source[end:]
namespace = {"__name__": "__main__", "__file__": str(script_path)}
exec(compile(source, str(script_path), "exec"), namespace)
