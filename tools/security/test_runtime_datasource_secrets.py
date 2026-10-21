"""Production/runtime datasource must never default to published developer password.

The dedicated test H2 profile intentionally uses an empty local in-memory DB
password, which does NOT establish a production secret or public listener.
"""
from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[2]
APP=ROOT/"orchestrator-service/src/main/resources/application.properties"
TEST=ROOT/"orchestrator-service/src/test/resources/application.properties"


class RuntimeDatasourceSecurity(unittest.TestCase):
    def test_production_requires_explicit_secret(self):
        source=APP.read_text("utf-8")
        match=re.search(r"(?m)^spring\.datasource\.password=(.+)$",source)
        self.assertIsNotNone(match)
        self.assertEqual(match.group(1),"\${SPRING_DATASOURCE_PASSWORD}".replace("\\",""))
        self.assertNotIn("SPRING_DATASOURCE_PASSWORD:aetheris",source)
        self.assertNotRegex(source,r"(?m)^spring\.datasource\.password=(?:aetheris|postgres|password)$")

    def test_h2_test_profile_is_explicitly_isolated(self):
        source=TEST.read_text("utf-8")
        self.assertIn("jdbc:h2:mem:orchestrator;",source)
        self.assertIn("spring.datasource.password=\n",source)
        self.assertIn("spring.jpa.hibernate.ddl-auto=create-drop",source)

    def test_write_privileges_and_owner_runtime_remain_opt_in(self):
        source=APP.read_text("utf-8")
        self.assertIn("aetheris.execution.workspace-write-enabled=\${AETHERIS_WORKSPACE_WRITE_ENABLED:false}".replace("\\",""),source)
        self.assertIn("aetheris.syntra.owner-runtime.enabled=\${AETHERIS_SYNTRA_OWNER_RUNTIME_ENABLED:false}".replace("\\",""),source)
        self.assertIn("aetheris.host.simulation-only=\${AETHERIS_HOST_SIMULATION_ONLY:true}".replace("\\",""),source)


if __name__=="__main__":
    unittest.main()
