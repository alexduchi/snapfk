#!/usr/bin/env python3
from pathlib import Path

script_path = Path(__file__).with_name("prepare_v06_clean.py")
source = script_path.read_text()
source = source.replace(
    "        '      private void setAxisMode(AxisMode mode) {',",
    "        'private void setAxisMode(AxisMode mode) {',",
    1,
)
namespace = {"__name__": "__main__", "__file__": str(script_path)}
exec(compile(source, str(script_path), "exec"), namespace)
