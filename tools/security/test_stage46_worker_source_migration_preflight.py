"""Stage46.25 adversarial frozen worker-wire and actual JVM ClassRef evidence."""
from __future__ import annotations
import unittest
from stage46_worker_source_migration_preflight import review,CONSTANTS

POOL_START="Classfile /tmp/fixed.class\n  Last modified ...\nConstant pool:\n"
NO_REF=POOL_START+"  #1 = Class              #2  // java/lang/Object\n"
BANNED=POOL_START+"  #1 = Class              #2  // io/aetheris/orchestrator/voice/SyntraSyntheticPcmWorkerMain\n"

def fixture():
    source={
        "io/aetheris/orchestrator/voice/SyntraSyntheticHostLimits.java":
            'final class SyntraSyntheticHostLimits {\n'
            'static final String FIXED_PCM_WORKER_CLASS =\n'
            '"io.aetheris.orchestrator.voice.SyntraSyntheticPcmWorkerMain";\n'
            '}\n',
        "io/aetheris/orchestrator/voice/SyntraSyntheticProcessSpeechBoundary.java":
            'public final class SyntraSyntheticProcessSpeechBoundary {\n'
            'static final int FIXED_PCM_MAGIC = 0x53463431;\n'
            'static final String FIXED_PCM_PREFIX = "SYNTRA46_10_V1|";\n'
            '}\n',
    }
    return source,{p:NO_REF for p in CONSTANTS}

def inspect(s=None,v=None,mains=0):
    ss,vv=fixture()
    return review(ss if s is None else s,vv if v is None else v,mains)

class Stage4625WorkerSymbolDecouplingTest(unittest.TestCase):
    def no_authority(self,r):
        self.assertEqual(r["decision"],"NO_GO")
        for key in ("worker_source_moved_out_of_orchestrator",
                    "module_release_approved","voice_microphone_authorized",
                    "network_egress_certified","physical_owner_pc_pilot_authorized",
                    "production_authorized"):
            self.assertIs(r[key],False,key)

    def test_exact_frozen_protocol_and_no_worker_classrefs(self):
        r=inspect()
        self.assertTrue(r["frozen_wire_values_verified"])
        self.assertTrue(r["synthetic_worker_symbol_link_free"])
        self.assertTrue(r["compiled_jvm_constant_pool_checked"])
        self.assertTrue(r["regular_jar_worker_entrypoints_excluded"])
        self.no_authority(r)

    def test_class_literal_in_producer_is_denied(self):
        s,v=fixture()
        key=next(iter(s))
        s[key]+="SyntraSyntheticPcmWorkerMain.class"
        self.assertFalse(inspect(s=s)["synthetic_worker_symbol_link_free"])

    def test_protocol_constant_link_in_producer_denied(self):
        s,v=fixture()
        key=list(s)[1]
        s[key]+="SyntraSyntheticPcmWorkerMain.MAGIC"
        self.assertFalse(inspect(s=s)["frozen_wire_values_verified"])

    def test_prefix_link_in_producer_denied(self):
        s,v=fixture()
        key=list(s)[1]
        s[key]+="SyntraSyntheticPcmWorkerMain.PREFIX"
        self.assertFalse(inspect(s=s)["synthetic_worker_symbol_link_free"])

    def test_job_probe_classref_in_producer_denied(self):
        s,v=fixture()
        v[list(v)[0]]=POOL_START+"#20 = Class #19 // io/aetheris/orchestrator/voice/SyntraSyntheticJobProbeMain"
        self.assertFalse(inspect(v=v)["synthetic_worker_symbol_link_free"])

    def test_real_jvm_worker_classref_denied_even_with_correct_source(self):
        s,v=fixture()
        v[list(v)[0]]=BANNED
        r=inspect(v=v)
        self.assertTrue(r["frozen_wire_values_verified"])
        self.assertFalse(r["synthetic_worker_symbol_link_free"])
        self.no_authority(r)

    def test_missing_javap_output_never_counts_as_verified(self):
        s,v=fixture()
        v[list(v)[1]]=""
        self.assertFalse(inspect(v=v)["compiled_jvm_constant_pool_checked"])

    def test_missing_actual_constant_pool_never_counts_as_verified(self):
        s,v=fixture()
        v[list(v)[1]]="Classfile foo\nno data"
        self.assertFalse(inspect(v=v)["compiled_jvm_constant_pool_checked"])

    def test_missing_actual_classfile_marker_never_counts_as_verified(self):
        s,v=fixture()
        v[list(v)[1]]="Constant pool: only"
        self.assertFalse(inspect(v=v)["compiled_jvm_constant_pool_checked"])

    def test_wrong_worker_class_literal_fails(self):
        s,v=fixture()
        key=list(s)[0]
        s[key]=s[key].replace('SyntraSyntheticPcmWorkerMain"','ArbitraryMain"')
        self.assertFalse(inspect(s=s)["frozen_wire_values_verified"])

    def test_wrong_magic_value_fails(self):
        s,v=fixture()
        key=list(s)[1]
        s[key]=s[key].replace("0x53463431","0x53463432")
        self.assertFalse(inspect(s=s)["frozen_wire_values_verified"])

    def test_wrong_prefix_fails(self):
        s,v=fixture()
        key=list(s)[1]
        s[key]=s[key].replace("SYNTRA46_10_V1|","SYNTRA_FAKE|")
        self.assertFalse(inspect(s=s)["frozen_wire_values_verified"])

    def test_duplicate_protocol_anchor_fails(self):
        s,v=fixture()
        key=list(s)[1]
        s[key]+='static final int FIXED_PCM_MAGIC = 0x53463431;'
        self.assertFalse(inspect(s=s)["frozen_wire_values_verified"])

    def test_worker_main_returning_to_production_jar_rejected(self):
        r=inspect(mains=1)
        self.assertFalse(r["regular_jar_worker_entrypoints_excluded"])
        self.no_authority(r)

    def test_boolean_main_count_is_not_integer(self):
        r=inspect(mains=False)
        self.assertFalse(r["frozen_wire_values_verified"])
        self.no_authority(r)

    def test_malformed_source_schema_is_denied(self):
        for bad in ({},[],None,"untrusted"):
            r=review(bad,fixture()[1],0)
            self.assertFalse(r["frozen_wire_values_verified"])
            self.no_authority(r)

    def test_extra_unreviewed_source_class_fails(self):
        s,v=fixture()
        s["Other.java"]="Injected"
        self.assertFalse(inspect(s=s)["frozen_wire_values_verified"])

    def test_extra_fabricated_bytecode_result_fails(self):
        s,v=fixture()
        v["Other.java"]=NO_REF
        self.assertFalse(inspect(v=v)["compiled_jvm_constant_pool_checked"])

    def test_no_release_even_if_sources_are_consistent(self):
        r=inspect()
        self.assertTrue(r["synthetic_worker_symbol_link_free"])
        self.no_authority(r)

if __name__=="__main__":
    unittest.main()
