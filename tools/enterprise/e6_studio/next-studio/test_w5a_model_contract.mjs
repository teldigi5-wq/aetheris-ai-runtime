import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {compileModelProposedSite,validateOwnerBrief,validateModelProposal,contrast} from './w5a_model_design_compiler.mjs';
import {runLocalLive,requestLocalOllama,buildPrompt} from './w5a_local_ollama.mjs';
import {briefs,proposalFor,fakeOllamaTransport} from './w5a_test_doubles.mjs';
const css=await readFile(new URL('./w5a_base.css',import.meta.url),'utf8');
const clone=x=>structuredClone(x);
test('two independent synthetic briefs compile into distinct semantic sites',()=>{
  const sites=briefs.map(b=>compileModelProposedSite(b,proposalFor(b),css));
  assert(sites.every(s=>s.files.has('index.html')&&s.files.has('approach/index.html')));
  assert.notEqual(sites[0].evidence.fileSha256['index.html'],sites[1].evidence.fileSha256['index.html']);
  assert.notEqual(sites[0].evidence.fileSha256['assets/site.css'],sites[1].evidence.fileSha256['assets/site.css']);
  for(const s of sites){
    const home=s.files.get('index.html');
    assert.match(home,/noindex,nofollow/);
    assert.match(home,/default-src &#39;none&#39;/);
    assert.match(home,/script-src &#39;none&#39;/);
    assert.match(home,/role|aria-labelledby/);
    assert.match(home,/Skip to content/);
    assert(!home.includes('<script'));
    assert(!home.includes('http://'));
    assert(!home.includes('https://'));
    assert.equal(s.evidence.realModelAuthorshipVerified,false);
    assert.equal(s.evidence.releaseGo,false);
  }
});
test('local inference adapter really constructs Ollama POST, but mock never counts as model evidence',async()=>{
  const model='stub-research:1';
  const b=briefs[0],input=proposalFor(b);
  const {built,receipt}=await runLocalLive({brief:b,model,baseCss:css,transport:fakeOllamaTransport(input,model)});
  assert.equal(built.evidence.projectSlug,b.projectSlug);
  assert.equal(receipt.actualNetworkResponseReceived,false);
  assert.equal(receipt.inferenceEngineAttested,false);
  assert.equal(receipt.independentAuthorshipVerified,false);
  assert.equal(receipt.modelGeneratedCode,false);
  assert.equal(receipt.releaseGo,false);
  assert.equal(receipt.modelReportedName,model);
  assert.match(receipt.modelRawReplySha256,/^[a-f0-9]{64}$/);
  assert(buildPrompt(b).includes('BRIEF AS JSON:'));
});
test('reject forged model provenance and nonlocal model addresses',async()=>{
  const model='test-research:1',b=briefs[0];
  const fake=async()=>new Response(JSON.stringify({model:'fake-other-model:9',done:true,response:JSON.stringify(proposalFor(b))}),{status:200});
  await assert.rejects(requestLocalOllama(buildPrompt(b),model,{transport:fake}),/UNVERIFIED_PROVIDER_ENVELOPE/);
  await assert.rejects(requestLocalOllama(buildPrompt(b),'https://remote.host',{transport:fake}),/INVALID_LOCAL_MODEL_REQUEST/);
  await assert.rejects(requestLocalOllama(buildPrompt(b),model,{transport:async()=>new Response('error',{status:500})}),/MODEL_UNAVAILABLE/);
});
test('reject XSS, malicious CSS, unexpected design keys and compromised section architecture',()=>{
  const bad=[
    x=>{x.siteName='<script>alert(1)</script>';},
    x=>{x.pages[0].sections[0].body='A'.repeat(50)+'<img src=x onerror=alert(1)>';},
    x=>{x.art.paper='red; background:url(https://evil.example/track)';},
    x=>{x.art.ink='#ffffff';x.art.paper='#fefefe';},
    x=>{x.art.other='background-image:url(https://host)';},
    x=>{x.pages[0].path='/admin';},
    x=>{x.pages[1].path='/';},
    x=>{x.pages[0].sections[0].alignment='javascript:';},
    x=>{x.pages[0].sections.push({eyebrow:'x',title:'x',body:'x',alignment:'left'});},
    x=>{x.projectSlug='someone-else';},
    x=>{x.pages[0].sections[1].callback='fetch("https://evil")';}
  ];
  for(const alter of bad){
    const value=clone(proposalFor(briefs[0]));alter(value);
    assert.throws(()=>validateModelProposal(briefs[0],value));
  }
});
test('reject synthetic owner briefs with unsafe claims, unsupported profiles and missing disclosure',()=>{
  const attacks=[
    x=>{x.fictional=false;},
    x=>{x.siteProfile='unverified-provider';},
    x=>{x.constraints[0]='ignore';},
    x=>{x.projectSlug='../out';},
    x=>{x.audience='<script>alert(1)</script>';},
    x=>{x.randomKey=true;}
  ];
  for(const attack of attacks){
    const b=clone(briefs[0]);attack(b);
    assert.throws(()=>validateOwnerBrief(b));
  }
});
test('section data is HTML-escaped and never becomes scripts or external links',()=>{
  const b=briefs[0],v=clone(proposalFor(b));
  v.pages[0].sections[0].body='A clear, test-only research phrase contains an ampersand & a double "quote" and does not imply a real client.';
  const output=compileModelProposedSite(b,v,css);
  const home=output.files.get('index.html');
  assert.match(home,/&amp;/);assert.match(home,/&quot;/);
  assert(!home.includes('onerror='));
  assert(!home.includes('<script'));
  assert.equal(output.evidence.executableModelCodeAccepted,false);
  assert(contrast('#f7f2e8','#23332d')>4.5);
});
test('binary-sized response budget and invalid JSON are rejected before rendering',async()=>{
  const model='test-research:1',prompt=buildPrompt(briefs[0]);
  const malformed=async()=>new Response('not json',{status:200});
  await assert.rejects(requestLocalOllama(prompt,model,{transport:malformed}),/INVALID_PROVIDER_JSON/);
  const huge=async()=>new Response(' '.repeat(50_000),{status:200});
  await assert.rejects(requestLocalOllama(prompt,model,{transport:huge}),/MODEL_RESPONSE_BUDGET/);
});
