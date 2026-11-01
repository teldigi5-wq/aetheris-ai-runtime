/**
 * W2-B local fixed-fixture CLI. Deliberately no arbitrary input/output path or overwrite.
 * Does not use local models, external media, authentication, production APIs or deployment.
 */
import {readFile,lstat,mkdir,writeFile} from 'node:fs/promises';
import {dirname,join,relative,isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {compileCuratedW2Site} from './w2b_curated_compiler.mjs';

const here=dirname(fileURLToPath(import.meta.url));
const SITES=Object.freeze({
  morrowline:Object.freeze({fixture:'w2_demo_brief.json',preset:'mineral-editorial'}),
  arcloom:Object.freeze({fixture:'w2b_arcloom_brief.json',preset:'ink-monograph'})
});
async function main() {
  if(process.argv.length!==3 || !/^--site=(morrowline|arcloom)$/.test(process.argv[2]))
    throw new Error('W2B_FIXED_SITE_SELECTOR_REQUIRED');
  const site=process.argv[2].slice('--site='.length);
  const selection=SITES[site];
  const data=JSON.parse(await readFile(join(here,selection.fixture),'utf8'));
  const direction={version:1,preset:selection.preset};
  const {files,evidence}=compileCuratedW2Site(data.brief,data.content,direction);
  const parent=join(here,'target');
  try{
    const stat=await lstat(parent);
    if(!stat.isDirectory()||stat.isSymbolicLink())throw new Error('INVALID_TARGET_PARENT');
  }catch(e){
    if(e.code!=='ENOENT')throw e;
    await mkdir(parent,{recursive:false});
  }
  const output=join(parent,'e6-w2b-'+site);
  await mkdir(output,{recursive:false});
  for(const [name,bytes] of files){
    if(name.startsWith('/')||name.includes('..')||name.includes('\\'))throw new Error('UNSAFE_OUTPUT_NAME');
    const path=join(output,name), rel=relative(output,path);
    if(!rel||rel.startsWith('..')||isAbsolute(rel))throw new Error('OUTPUT_ESCAPE');
    await mkdir(dirname(path),{recursive:true});
    await writeFile(path,bytes,{flag:'wx',mode:0o644});
  }
  process.stdout.write(JSON.stringify({kind:'E6_W2B_LOCAL_DEMONSTRATION_CREATED',
    site,output,preset:evidence.preset,routes:evidence.routes,fileCount:files.size,
    modelCalled:false,releaseGo:false,stage45Go:false})+'\n');
}
main().catch(error=>{
  process.stderr.write('E6_W2B_GENERATION_BLOCKED '+String(error?.message||'UNKNOWN')+'\n');
  process.exitCode=1;
});
