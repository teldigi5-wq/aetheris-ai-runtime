package io.aetheris.orchestrator.stage9;

import io.aetheris.orchestrator.evaluation.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class BenchmarkService {
    private final EvaluationService evaluations;
    private final Map<String,BenchmarkPack> packs;
    public BenchmarkService(EvaluationService evaluations){this.evaluations=evaluations;Map<String,BenchmarkPack> m=new LinkedHashMap<>();add(m,new BenchmarkPack("coding-core","Software Engineering","Implementation, debugging and verification",List.of("implement-feature","debug-regression","write-tests","review-security")));add(m,new BenchmarkPack("research-grounding","Research","Evidence quality and contradiction handling",List.of("source-synthesis","contradiction-check","uncertainty-report")));add(m,new BenchmarkPack("university-tutor","Syntra Academy","Teaching quality and mastery support",List.of("explain-concept","worked-example","exam-practice")));add(m,new BenchmarkPack("pc-operations","Computer Operations","Safe diagnostics and reversible maintenance",List.of("diagnose","safe-remediation","rollback-plan")));add(m,new BenchmarkPack("multi-agent","Orchestration","Planning, delegation, verification and recovery",List.of("plan-delegate","cross-review","recover-failure")));this.packs=Collections.unmodifiableMap(m);}
    public List<BenchmarkPack> packs(){return List.copyOf(packs.values());}
    public BenchmarkRunResult record(BenchmarkRunRequest r){BenchmarkPack pack=packs.get(r.packId());if(pack==null)throw new NoSuchElementException("Unknown benchmark pack: "+r.packId());if(!pack.scenarios().contains(r.scenario()))throw new IllegalArgumentException("Scenario is not part of benchmark pack");EvaluationRunEntity run=evaluations.record(new EvaluationRequest(r.subjectType(),r.subjectId(),pack.id()+":"+r.scenario(),r.success(),r.corrections(),r.latencyMs(),r.costUsd(),r.evidenceScore(),r.hallucinationPenalty()));String verdict=run.getOverallScore()>=80?"PASS_STRONG":run.getOverallScore()>=65?"PASS":run.getOverallScore()>=50?"NEEDS_REVIEW":"FAIL";return new BenchmarkRunResult(pack,run,verdict);}
    private void add(Map<String,BenchmarkPack> map,BenchmarkPack pack){map.put(pack.id(),pack);}
}
