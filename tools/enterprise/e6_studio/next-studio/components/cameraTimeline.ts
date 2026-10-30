/** E6 three-shot hand-authored camera path; one scroll playhead, no second animation driver.
 * All positions are local world units, tested with bounded fov and fixed origin.
 * Research only; no physical-GPU or color-calibrated design certification.
 */
export const CAMERA_KEYFRAMES = [
  {at:0,   name:"arrival",   position:[-2.25,1.10,4.65] as const, fov:38},
  {at:0.5, name:"material",  position:[2.10,0.90,3.90] as const,  fov:34},
  {at:1,   name:"precision", position:[0.95,0.70,2.85] as const, fov:30},
] as const;
export type CameraShot = (typeof CAMERA_KEYFRAMES)[number]["name"];
export type CameraSample = {
  progress:number;
  shot:CameraShot;
  position:readonly [number,number,number];
  fov:number;
};

const ease=(t:number)=>t*t*(3-2*t);
const between=(a:number,b:number,t:number)=>a+(b-a)*t;

/** Pure, reversible mapping. Invalid playheads resolve to the safe arrival frame. */
export function sampleCamera(progress:number):CameraSample {
  const p=Number.isFinite(progress)?Math.min(1,Math.max(0,progress)):0;
  const left=p<=0.5?CAMERA_KEYFRAMES[0]:CAMERA_KEYFRAMES[1];
  const right=p<=0.5?CAMERA_KEYFRAMES[1]:CAMERA_KEYFRAMES[2];
  const t=ease(Math.min(1,Math.max(0,(p-left.at)/(right.at-left.at))));
  const position:[number,number,number]=[
    between(left.position[0],right.position[0],t),
    between(left.position[1],right.position[1],t),
    between(left.position[2],right.position[2],t),
  ];
  const shot:CameraShot=p<1/3?"arrival":p<2/3?"material":"precision";
  return {progress:p,shot,position,fov:between(left.fov,right.fov,t)};
}
