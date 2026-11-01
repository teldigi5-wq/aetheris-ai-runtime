/* W4-D: evidence schema and deterministic acceptance rules.
 * Soft-browser measurements are observations, NOT owner physical-GPU proof.
 */
import assert from 'node:assert/strict';

export const BUDGETS=Object.freeze({
  bootstrapMaxBytes:12_000,
  optionalSceneMaxBytes:780_000,
  modelMaxBytes:2_000_000,
  noHorizontalOverflowPx:2
});
export const EXPECTED_PROFILES=Object.freeze([
  'desktop-software','mobile-software','compact-software',
  'reduced-motion-static','javascript-disabled-static'
]);
export function bounded(value,name,min=0,max=Number.MAX_SAFE_INTEGER){
  assert(typeof value==='number'&&Number.isFinite(value)&&value>=min&&value<=max,
    name+' must be a finite number in supported range');
  return value;
}
export function validateReport(report){
  assert.equal(report.kind,'E6_W4D_SW_BROWSER_EVIDENCE_V1');
  assert.equal(report.executionEnvironment,'GITHUB_ACTIONS_UBUNTU_CHROMIUM_SWIFTSHADER');
  assert.equal(report.physicalGpuMeasured,false);
  assert.equal(report.ownerDeviceVerified,false);
  assert.equal(report.independentVisualApproval,false);
  assert.equal(report.wcagCertified,false);
  assert.equal(report.releaseGo,false);
  assert.equal(report.stage45Go,false);
  assert.equal(report.productionReady,false);
  assert.equal(report.aiModelAuthored,false);
  assert.match(report.parentModelSha256,/^[a-f0-9]{64}$/);
  assert.match(report.refinedStillSha256,/^[a-f0-9]{64}$/);
  assert.match(report.initialBootstrapSha256,/^[a-f0-9]{64}$/);
  assert.match(report.optionalSceneSha256,/^[a-f0-9]{64}$/);
  assert.equal(report.profiles.length,EXPECTED_PROFILES.length);
  assert.deepEqual(report.profiles.map(p=>p.profile),EXPECTED_PROFILES);
  bounded(report.initialBootstrapBytes,'initialBootstrapBytes',1,BUDGETS.bootstrapMaxBytes);
  bounded(report.optionalSceneBytes,'optionalSceneBytes',1,BUDGETS.optionalSceneMaxBytes);
  bounded(report.modelBytes,'modelBytes',1,BUDGETS.modelMaxBytes);
  for(const p of report.profiles){
    assert.equal(p.pageStatus,200);
    assert.equal(p.externalRequests,0);
    assert.equal(p.uncaughtBrowserExceptions,0);
    bounded(p.horizontalOverflowPx,'horizontalOverflowPx',0,BUDGETS.noHorizontalOverflowPx);
    bounded(p.documentLoadMs,'documentLoadMs',0,300_000);
    bounded(p.lcpCandidateMs,'lcpCandidateMs',0,300_000);
    bounded(p.clsObserved,'clsObserved',0,10);
    assert.equal(p.contentRoutesAccessible,true);
    assert.equal(p.staticBlenderStillVisible,true);
    assert.equal(p.staticScreenshotSha256.length,64);
    if(p.profile==='reduced-motion-static'||p.profile==='javascript-disabled-static'){
      assert.equal(p.initial3DEngineRequests,0);
      assert.equal(p.initialGlbRequests,0);
      assert.equal(p.activationAttempted,false);
      assert.equal(p.renderedWebglCanvas,false);
    }else{
      assert.equal(p.initial3DEngineRequests,0);
      assert.equal(p.initialGlbRequests,0);
      assert.equal(p.activationAttempted,true);
      assert.equal(p.engineRequestsAfterActivation,1);
      assert.equal(p.glbRequestsAfterActivation,1);
      assert.equal(p.renderedWebglCanvas,true);
      assert.equal(p.sourceVerifiedModel,true);
      assert.equal(p.cameraPositionsObserved,3);
      assert.equal(p.nonIdenticalCameraFrames,true);
      assert(p.scrollToDrawMs.length===3);
      for(const delta of p.scrollToDrawMs)bounded(delta,'scrollToDrawMs',0,30_000);
    }
    // Do NOT turn software CI load timings into unsupported production CWV claims.
    assert.equal(p.coreWebVitalsCertified,false);
    assert.equal(p.physicalGpuFrameTimeMeasured,false);
  }
  return true;
}
