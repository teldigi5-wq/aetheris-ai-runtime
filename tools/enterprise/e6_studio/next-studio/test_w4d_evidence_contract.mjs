import test from 'node:test';
import assert from 'node:assert/strict';
import {validateReport,EXPECTED_PROFILES,BUDGETS,bounded} from './w4d_evidence_contract.mjs';
const digest='a'.repeat(64);
const fixture=()=>({
  kind:'E6_W4D_SW_BROWSER_EVIDENCE_V1',
  executionEnvironment:'GITHUB_ACTIONS_UBUNTU_CHROMIUM_SWIFTSHADER',
  parentModelSha256:digest,refinedStillSha256:digest,
  initialBootstrapSha256:digest,optionalSceneSha256:digest,
  initialBootstrapBytes:1139,optionalSceneBytes:738079,modelBytes:72724,
  physicalGpuMeasured:false,ownerDeviceVerified:false,
  independentVisualApproval:false,wcagCertified:false,
  aiModelAuthored:false,productionReady:false,releaseGo:false,stage45Go:false,
  profiles:EXPECTED_PROFILES.map(profile=>({
    profile,pageStatus:200,externalRequests:0,
    uncaughtBrowserExceptions:0,horizontalOverflowPx:0,
    documentLoadMs:50,lcpCandidateMs:40,clsObserved:0,
    contentRoutesAccessible:true,staticBlenderStillVisible:true,
    staticScreenshotSha256:digest,
    initial3DEngineRequests:0,initialGlbRequests:0,
    activationAttempted:!profile.endsWith('static'),
    engineRequestsAfterActivation:profile.endsWith('static')?0:1,
    glbRequestsAfterActivation:profile.endsWith('static')?0:1,
    renderedWebglCanvas:!profile.endsWith('static'),
    sourceVerifiedModel:!profile.endsWith('static'),
    cameraPositionsObserved:profile.endsWith('static')?0:3,
    nonIdenticalCameraFrames:!profile.endsWith('static'),
    scrollToDrawMs:profile.endsWith('static')?[]:[25,28,33],
    coreWebVitalsCertified:false,physicalGpuFrameTimeMeasured:false
  }))
});
test('contract accepts bounded evidence without claiming physical device acceptance',()=>{
  assert.equal(validateReport(fixture()),true);
  assert.equal(BUDGETS.bootstrapMaxBytes,12_000);
});
test('fails closed on forged physical-GPU and production-approval claims',()=>{
  for(const key of ['physicalGpuMeasured','ownerDeviceVerified',
    'independentVisualApproval','wcagCertified','aiModelAuthored',
    'productionReady','releaseGo','stage45Go']){
    const x=fixture();x[key]=true;
    assert.throws(()=>validateReport(x),key);
  }
});
test('fails closed on eager scene or GLB request and non-deterministic remote fetch',()=>{
  for(const key of ['initial3DEngineRequests','initialGlbRequests','externalRequests']){
    const x=fixture();x.profiles[0][key]=1;
    assert.throws(()=>validateReport(x),key);
  }
});
test('requires genuine click-driven scene proof and distinct screenshots',()=>{
  const x=fixture();x.profiles[0].nonIdenticalCameraFrames=false;
  assert.throws(()=>validateReport(x));
  const y=fixture();y.profiles[0].engineRequestsAfterActivation=0;
  assert.throws(()=>validateReport(y));
  const z=fixture();z.profiles[0].scrollToDrawMs=[];
  assert.throws(()=>validateReport(z));
});
test('rejects overflow, missing routes and source/asset budget failures',()=>{
  const cases=[
    x=>{x.profiles[2].horizontalOverflowPx=4;},
    x=>{x.profiles[3].contentRoutesAccessible=false;},
    x=>{x.initialBootstrapBytes=12_001;},
    x=>{x.optionalSceneBytes=780_001;},
    x=>{x.modelBytes=2_000_001;}
  ];
  for(const alter of cases){const x=fixture();alter(x);assert.throws(()=>validateReport(x));}
});
test('rejects synthetic timing NaN/negative and inflated unsupported certification',()=>{
  const x=fixture();x.profiles[0].clsObserved=Number.NaN;
  assert.throws(()=>validateReport(x));
  const y=fixture();y.profiles[1].documentLoadMs=-1;
  assert.throws(()=>validateReport(y));
  const z=fixture();z.profiles[0].coreWebVitalsCertified=true;
  assert.throws(()=>validateReport(z));
  assert.throws(()=>bounded(Infinity,'time'));
});
