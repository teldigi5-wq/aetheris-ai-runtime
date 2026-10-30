/* Unit-test the exact compiled TypeScript camera timeline, without browser or GPU. */
import assert from 'node:assert/strict';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';

const require=createRequire(import.meta.url);
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../../');
const {CAMERA_KEYFRAMES,sampleCamera}=require(path.join(root,'target/e6-studio/camera-compiled/cameraTimeline.js'));

assert.equal(CAMERA_KEYFRAMES.length,3);
assert.deepEqual(CAMERA_KEYFRAMES.map(x=>x.name),['arrival','material','precision']);
assert.deepEqual(CAMERA_KEYFRAMES.map(x=>x.at),[0,0.5,1]);
assert.deepEqual(CAMERA_KEYFRAMES.map(x=>x.fov),[38,34,30]);
for(const key of CAMERA_KEYFRAMES){
 const actual=sampleCamera(key.at);
 assert.equal(actual.shot,key.name);
 assert.equal(actual.fov,key.fov);
 assert.deepEqual(actual.position,key.position);
}
for(const value of [-Infinity,-50,NaN,Infinity]) {
 const s=sampleCamera(value);
 assert.deepEqual(s,sampleCamera(0),'Invalid/out-of-range input must fail closed to arrival');
}
assert.deepEqual(sampleCamera(-1),sampleCamera(0));
assert.deepEqual(sampleCamera(2),sampleCamera(1));
for(let i=0;i<=100;i++){
 const progress=i/100;
 const s=sampleCamera(progress);
 assert.equal(s.progress,progress);
 assert(s.fov<=38&&s.fov>=30);
 assert(s.position.every(Number.isFinite));
 if(i>0){
  const prior=sampleCamera((i-1)/100);
  assert(s.fov<=prior.fov+1e-9,'FOV should tighten monotonically through acts');
  assert(Math.hypot(...s.position.map((v,k)=>v-prior.position[k]))<0.25,'Discontinuous camera jump');
 }
 // Deterministic reversible composition with no clock dependence
 assert.deepEqual(s,sampleCamera(progress));
}
assert.deepEqual(sampleCamera(0.5),sampleCamera(0.25+0.25));
assert.equal(sampleCamera(0.2).shot,'arrival');
assert.equal(sampleCamera(0.5).shot,'material');
assert.equal(sampleCamera(0.9).shot,'precision');
console.log('E6_AUTHORED_TIMELINE_PURE_101_SAMPLES_PASS stage45=NO_GO');
