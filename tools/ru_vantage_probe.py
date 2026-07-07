#!/usr/bin/env python3
import importlib.util
import sys
from pathlib import Path

module_path = Path(__file__).with_name("ru-vantage-probe.py")
spec = importlib.util.spec_from_file_location(__name__, module_path)
if spec is None or spec.loader is None:
    raise ImportError(f"Cannot load {module_path}")
module = importlib.util.module_from_spec(spec)
sys.modules[__name__] = module
spec.loader.exec_module(module)
