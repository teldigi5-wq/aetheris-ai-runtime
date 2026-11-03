/**
 * E6 W5-H — ONE bounded, sanitized retry for validator-rejected REAL model proposals.
 *
 * The model never sees its previously rejected freeform response, stack traces,
 * external content, paths, secrets, URLs, or authority. Only an allowlisted
 * deterministic failure code enters one follow-up prompt. If a second output
 * still fails, the case is REJECTED and never becomes HTML/CSS.
 *
 * Offline fake transports exercise this in CI; no model runs there.
 */
import {buildPrompt,requestLocalOllama} from './w5a_local_ollama.mjs';
import {createHash} from 'node:crypto';
const sha=s=>createHash('sha256').update(s,'utf8').digest('hex');
import {validateModelProposal} from './w5a_model_design_compiler.mjs';
import {buildCssCodePrompt,parseCodeProposal} from './w5c_local_code_trial.mjs';
import {compileRestrictedCss} from './w5c_restricted_css_compiler.mjs';
const fail=s=>{throw Error('E6_W5H_'+s);};
const designErrors=new Set([
  'E6_W5A_MODEL_SCHEMA_OR_CONTRAST_REJECTED',
  'E6_W5A_INVALID_PAGE_ARCHITECTURE',
  'E6_W5A_UNTRUSTED_PAGE_CONTENT',
  'E6_W5A_INCOMPLETE_PAGE_IDENTITY'
]);
const cssErrors=new Set([
  'E6_W5C_INVALID_MODEL_CODE_ENVELOPE',
  'E6_W5C_INVALID_OR_UNSAFE_SOURCE',
  'E6_W5C_UNSUPPORTED_CSS_GRAMMAR',
  'E6_W5C_SELECTOR_DENIED_OR_DUPLICATED',
  'E6_W5C_DECLARATION_COUNT_DENIED',
  'E6_W5C_MALFORMED_DECLARATION',
  'E6_W5C_PROPERTY_OR_VALUE_DENIED',
  'E6_W5C_RULE_COUNT_DENIED',
  'E6_W5C_TOO_FEW_RULES'
]);
const repairText=(original,code)=>{
  if(typeof original!=='string'||original.length>7000||
     typeof code!=='string'||!/^E6_W5[AC]_[A-Z0-9_]+$/.test(code))
    fail('INVALID_REPAIR_PROMPT');
  const suffix=[
    '',
    'PREVIOUS CANDIDATE REJECTED BY A DETERMINISTIC SECURITY/SCHEMA VALIDATOR.',
    'Failure category: '+code+'.',
    'Do not request, quote, reconstruct, reuse, or include the previous model reply.',
    'Return a completely new FULL JSON object satisfying the ORIGINAL constraints.',
    'Do not add keys, websites, scripts, tools, file paths, external links or commentary.',
    'This is the one and only allowed retry; validation will never be relaxed.'
  ].join('\n');
  const prompt=original+suffix;
  if(prompt.length>8000)fail('REPAIR_PROMPT_BUDGET');
  return prompt;
};
// Expose the exact production retry prompt only for deterministic, offline
// comparison of captured response metadata. No model call occurs here.
export function buildDesignRepairPrompt(brief,code){
 if(!designErrors.has(code))fail('UNAPPROVED_DESIGN_REPAIR_CODE');
 return repairText(buildPrompt(brief),code);
}
async function tryValidated({basePrompt,model,transport,validator,allowed,phase}){
  if(typeof basePrompt!=='string'||!basePrompt||
     typeof validator!=='function'||!(allowed instanceof Set)||
     !['design','css'].includes(phase))
    fail('INVALID_ATTEMPT_CONTRACT');
  const failureCodes=[];
  for(let attempt=1;attempt<=2;attempt++){
    // Provider-level errors (HTTP, malformed JSON, size/timeout, wrong model)
    // are NEVER retried. Only deterministic validator failures qualify.
    const prompt=attempt===1?basePrompt:repairText(basePrompt,failureCodes[0]);
    const response=await requestLocalOllama(prompt,model,{transport,phase});
    try{
      const accepted=validator(response.design);
      return {value:accepted,rawReplySha256:response.rawReplySha256,
        attempts:attempt,repairAttempted:attempt===2,
        attemptedPromptSha256:sha(prompt),initialPromptSha256:sha(basePrompt),
        providerReportedStopReason:response.providerReportedStopReason,
        providerStopReasonVerified:response.providerStopReasonVerified,
        providerReportedOutputTokens:response.providerReportedOutputTokens,
        inferencePhase:response.inferencePhase,
        maximumOutputTokens:response.maximumOutputTokens,
        firstRejectedValidationCode:failureCodes[0]||null,
        retryBudgetExhausted:false};
    }catch(e){
      const code=String(e?.message||'');
      if(!allowed.has(code))throw e;
      failureCodes.push(code);
      if(attempt===2)fail('VALIDATION_REJECTED_AFTER_ONE_SAFE_RETRY');
    }
  }
  fail('UNREACHABLE');
}
export async function requestValidatedDesign(brief,model,{transport=fetch}={}){
  const basePrompt=buildPrompt(brief);
  return tryValidated({basePrompt,model,transport,phase:'design',allowed:designErrors,
    validator:x=>{
      validateModelProposal(brief,x);
      return x;
    }});
}
export async function requestValidatedCss(brief,proposal,model,{transport=fetch}={}){
  // Already validated design required; caller must not pass unvalidated data.
  validateModelProposal(brief,proposal);
  const basePrompt=buildCssCodePrompt(brief,proposal);
  return tryValidated({basePrompt,model,transport,phase:'css',allowed:cssErrors,
    validator:x=>{
      const source=parseCodeProposal(x,brief.projectSlug);
      compileRestrictedCss(source);
      return source;
    }});
}
