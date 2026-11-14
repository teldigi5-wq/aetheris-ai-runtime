"""Runtime actuator exposure is closed by default (same policy as the platform services).

The orchestrator exposes only health and info unless an operator explicitly opts in
with AETHERIS_RUNTIME_ACTUATOR_EXPOSURE. Metrics and internals (prometheus, metrics,
env, beans, heapdump, loggers, *) must never be part of the default.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "orchestrator-service/src/main/resources/application.yml"
PROPERTIES = ROOT / "orchestrator-service/src/main/resources/application.properties"


class RuntimeActuatorDefaultClosed(unittest.TestCase):
    def test_default_exposure_is_health_and_info_only(self):
        includes = re.findall(r"(?m)^\s+include:\s*(\S+)\s*$", CONFIG.read_text(encoding="utf-8"))
        self.assertEqual(["${AETHERIS_RUNTIME_ACTUATOR_EXPOSURE:health,info}"], includes)

    def test_no_other_config_reopens_actuator_internals(self):
        text = PROPERTIES.read_text(encoding="utf-8") if PROPERTIES.exists() else ""
        self.assertNotRegex(text, r"management\.endpoints\.web\.exposure\.include")
        for path in (ROOT / "orchestrator-service/src/main/resources").rglob("application*.y*ml"):
            if path == CONFIG:
                continue
            body = path.read_text(encoding="utf-8")
            for value in re.findall(r"(?m)^\s+include:\s*(\S+)", body):
                with self.subTest(path=path.name):
                    self.assertNotRegex(value, r"prometheus|metrics|env|beans|heapdump|loggers|\*")


if __name__ == "__main__":
    unittest.main()
