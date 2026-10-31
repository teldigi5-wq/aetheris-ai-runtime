#!/usr/bin/env node
/** E6 Task 8 local independent-acceptance inventory verifier.
 * Reads only local owner-provided files, never uploads, and never approves release.
 */
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {evaluateAcceptanceInventory} from './independent_acceptance_policy.mjs';
const app=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(app,'../../../../');
const defaultCase=path.join(root,'target/e6-studio/independent-acceptance');
const args=process.argv.slice(2);
if(args.length!==0&&!(args.length===2&&args[0]==='--case-dir')){
 throw Error('E6_USAGE: node independent_acceptance_cli.mjs [--case-dir /local/evidence/directory]');
}
const dir=args.length===2?path.resolve(args[1]):defaultCase;
let manifest=null,missing=false;
try {
 const raw=await readFile(path.join(dir,'manifest.json'),'utf8');
 if(Buffer.byteLength(raw)>256*1024)throw Error('E6_OVERSIZE_MANIFEST');
 manifest=JSON.parse(raw);
} catch(e) {
 if(e?.code==='ENOENT'){missing=true;}
 else throw e;
}
const report=await evaluateAcceptanceInventory(manifest,dir);
if(missing)report.problems.unshift('E6_LOCAL_MANIFEST_NOT_PROVIDED');
console.log(JSON.stringify(report,null,2));
if(!report.readyForIndependentHumanReview)process.exitCode=2;
