/**
 * E6 W5-C research: turn model-authored CSS *source text* into a bounded static
 * website. The CSS is parsed, explicitly allowlisted and RE-EMITTED canonically.
 * No eval, model JavaScript, external styles, imports, URL, arbitrary selectors,
 * browser-origin permissions, dynamic HTML, filesystem access or runtime tools.
 *
 * A successful mock is NOT evidence of real model authorship or visual quality.
 */
import {createHash} from 'node:crypto';
import {compileModelProposedSite,validateModelProposal} from './w5a_model_design_compiler.mjs';
const sha=x=>createHash('sha256').update(x,'utf8').digest('hex');
const fail=x=>{throw Error('E6_W5C_'+x);};
const SELECTORS=new Set([
  '.hero h1','.hero__figure','.hero__figure:before','.hero__type',
  '.eyebrow','.lede','.section__content','.section__text',
  '.section h2','.section:nth-child(even)'
]);
const selectorProperties={
  '.hero h1':['letter-spacing','max-width','font-style'],
  '.hero__figure':['border-radius','background-color','border'],
  '.hero__figure:before':['transform','border-radius'],
  '.hero__type':['text-align'],
  '.eyebrow':['letter-spacing','font-weight'],
  '.lede':['max-width','font-style'],
  '.section__content':['gap','border-radius'],
  '.section__text':['max-width','text-align'],
  '.section h2':['font-style','letter-spacing'],
  '.section:nth-child(even)':['background-color']
};
const MATCHERS={
  'letter-spacing':/^(?:-0\.(?:0[0-9]|[1-7][0-9])|0(?:\.[0-9]{1,2})?)em$/,
  'max-width':/^(?:[2-7][0-9]|80)ch$/,
  'font-style':/^(?:normal|italic)$/,
  'border-radius':/^(?:0|(?:[1-9]|1[0-9]|2[0-4])px)$/,
  'background-color':/^var\(--(?:paper|surface)\)$/,
  'border':/^1px solid var\(--(?:accent|ink)\)$/,
  'transform':/^rotate\(-?(?:[0-9]|[1-2][0-9]|30)deg\)$/,
  'text-align':/^(?:left|center|right)$/,
  'font-weight':/^(?:500|600|700|800)$/,
  'gap':/^(?:[8-9]|[1-3][0-9]|4[0-8])px$/
};
export function compileRestrictedCss(code){
  if(typeof code!=='string'||code.length<80||code.length>2400||
     !/^[\x09\x0a\x0d\x20-\x7e]+$/.test(code)||
     /[@\\<>"'\u0060]|\/\*|\*\/|\/\/|\$\{|(?:https?|url|data|expression|javascript|import)\s*[:(]/i.test(code))
    fail('INVALID_OR_UNSAFE_SOURCE');
  let rest=code.trim(),rules=[],seen=new Set();
  while(rest){
    const match=/^([.#a-zA-Z0-9:()_-]+(?:\s+[a-zA-Z0-9]+)?)\s*\{([^{}]*)\}/.exec(rest);
    if(!match)fail('UNSUPPORTED_CSS_GRAMMAR');
    const selector=match[1],body=match[2];
    if(!SELECTORS.has(selector)||seen.has(selector))fail('SELECTOR_DENIED_OR_DUPLICATED');
    seen.add(selector);
    const declarations=body.split(';').map(x=>x.trim()).filter(Boolean);
    if(declarations.length<1||declarations.length>3)fail('DECLARATION_COUNT_DENIED');
    const props=new Set(),safe=[];
    for(const declaration of declarations){
      const pair=/^([a-z-]+)\s*:\s*(.+)$/.exec(declaration);
      if(!pair)fail('MALFORMED_DECLARATION');
      const prop=pair[1],val=pair[2].trim();
      if(!selectorProperties[selector].includes(prop)||props.has(prop)||
         !MATCHERS[prop]?.test(val))
        fail('PROPERTY_OR_VALUE_DENIED');
      props.add(prop);
      safe.push(prop+': '+val+';');
    }
    rules.push(selector+' { '+safe.join(' ')+' }');
    rest=rest.slice(match[0].length).trim();
    if(rules.length>8)fail('RULE_COUNT_DENIED');
  }
  if(rules.length<3)fail('TOO_FEW_RULES');
  const normalized='/* E6 W5-C validated model-source CSS; no arbitrary code execution */\n'+
    rules.join('\n')+'\n';
  return {sourceSha256:sha(code),acceptedSelectors:[...seen],
    ruleCount:rules.length,css:normalized,cssSha256:sha(normalized)};
}
export function compileAuthoredSite(brief,proposal,code,baseCss){
  validateModelProposal(brief,proposal);
  const parsed=compileRestrictedCss(code);
  const built=compileModelProposedSite(brief,proposal,baseCss);
  const css=built.files.get('assets/site.css')+'\n'+parsed.css;
  if(css.length>22_000)fail('COMPILED_CSS_SIZE_BUDGET');
  built.files.set('assets/site.css',css);
  const fileHashes=Object.fromEntries([...built.files].filter(([p])=>p!=='build-evidence.json')
    .map(([path,content])=>[path,sha(content)]).sort((a,b)=>a[0].localeCompare(b[0])));
  const evidence={
    kind:'E6_W5C_BOUNDED_MODEL_CSS_SOURCE_V1',
    projectSlug:brief.projectSlug,proposalSha256:built.evidence.proposalSha256,
    authoredCssSourceSha256:parsed.sourceSha256,
    normalizedCssSha256:parsed.cssSha256,
    compiledCssFileSha256:sha(css),
    acceptedSelectors:parsed.acceptedSelectors,
    acceptedCssRules:parsed.ruleCount,fileSha256:fileHashes,
    modelSourceLanguage:'RESTRICTED_CSS_ONLY',
    noUntrustedJavascript:true,compiledScriptCount:0,
    realModelInferenceVerified:false,modelWeightsAttested:false,
    humanAuthorshipExcluded:false,independentRightsReview:false,
    browserQualityAccepted:false,ownerApproved:false,stage45Go:false,releaseGo:false
  };
  built.files.set('build-evidence.json',JSON.stringify(evidence,null,2)+'\n');
  return {files:built.files,evidence};
}
