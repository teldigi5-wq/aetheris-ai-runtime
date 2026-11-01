/** E6 preflight for complex original websites. A plan, NOT a website generator. No I/O. */
export const EXPERIENCE_CONTRACT_KIND = 'E6_ORIGINAL_WEB_EXPERIENCE_CONTRACT_V1';
const siteProfiles = new Set(['creative-studio', 'agency', 'fashion', 'product', 'portfolio']);
const sectionKinds = new Set([
  'hero', 'editorial', 'services', 'case-studies', 'gallery', 'process',
  'scene3d', 'contact', 'footer', 'team'
]);
const motions = new Set(['none', 'micro', 'scroll', 'scene']);
const fallbacks = new Set(['none', 'static', 'text']);
const assetSources = new Set(['none', 'owner-original', 'license-pending', 'unverified']);
const simpleSlug = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;
const pathPattern = /^\/(?:[a-z0-9]+(?:-[a-z0-9]+)*(?:\/[a-z0-9]+(?:-[a-z0-9]+)*)*)?$/;
const fail = (code) => ({kind:EXPERIENCE_CONTRACT_KIND,valid:false,reasons:[code],plan:null,stage45Go:false,releaseGo:false});

// Avoid getters, inherited properties and unexpected keys on an untrusted in-memory object.
function plainData(value, keys) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  if (Object.getPrototypeOf(value) !== Object.prototype && Object.getPrototypeOf(value) !== null) return false;
  const own = Reflect.ownKeys(value);
  if (own.some(k => typeof k !== 'string' || !keys.includes(k))) return false;
  return own.every(k => Object.getOwnPropertyDescriptor(value,k)?.get === undefined &&
    Object.getOwnPropertyDescriptor(value,k)?.set === undefined &&
    Object.hasOwn(value,k));
}
function boundedSlug(value) {
  return typeof value === 'string' && value.length <= 48 && simpleSlug.test(value);
}
function safePath(value) {
  return typeof value === 'string' && value.length <= 100 && pathPattern.test(value);
}

/**
 * The caller must build a plain data object from an explicitly approved owner brief.
 * No free-form source code, URLs, images, scripts, network, model or filesystem actions.
 */
export function evaluateOriginalExperienceContract(input) {
  try {
    if (!plainData(input,['version','projectSlug','siteProfile','pages'])) return fail('INVALID_ROOT_SHAPE');
    if (input.version !== 1 || !boundedSlug(input.projectSlug) || !siteProfiles.has(input.siteProfile))
      return fail('INVALID_IDENTITY_OR_VERSION');
    if (!Array.isArray(input.pages) || input.pages.length < 1 || input.pages.length > 12)
      return fail('INVALID_PAGE_COUNT');

    const paths = new Set();
    const pages = [];
    let totalSections = 0;
    let totalScenes = 0;
    const stack = new Set(['semantic-html','css']);
    const risks = new Set();
    for (const page of input.pages) {
      if (!plainData(page,['path','sections']) || !safePath(page.path) || paths.has(page.path))
        return fail('INVALID_OR_DUPLICATE_PAGE_PATH');
      paths.add(page.path);
      if (!Array.isArray(page.sections) || page.sections.length < 1 || page.sections.length > 16)
        return fail('INVALID_SECTION_COUNT');
      const sectionIds = new Set();
      const sections = [];
      let pageScenes = 0;
      for (const section of page.sections) {
        if (!plainData(section,['id','kind','motion','fallback','assetSource']) ||
          !boundedSlug(section.id) || sectionIds.has(section.id) ||
          !sectionKinds.has(section.kind) || !motions.has(section.motion) ||
          !fallbacks.has(section.fallback) || !assetSources.has(section.assetSource))
          return fail('INVALID_OR_DUPLICATE_SECTION');
        sectionIds.add(section.id);
        if (section.motion === 'none' && section.fallback !== 'none')
          return fail('NON_MOTION_CANNOT_DECLARE_MOTION_FALLBACK');
        if (section.motion !== 'none' && section.fallback === 'none')
          return fail('MOTION_REQUIRES_NON_ANIMATED_FALLBACK');
        if (section.kind === 'scene3d') {
          if (section.motion !== 'scene' || section.fallback !== 'static' ||
              section.assetSource === 'none') return fail('SCENE3D_REQUIRES_STATIC_FALLBACK_AND_ASSET');
          ++pageScenes;
          ++totalScenes;
          stack.add('three-r3f-opt-in');
          risks.add('PHYSICAL_GPU_AND_WEBGL_FALLBACK_UNVERIFIED');
        } else if (section.motion === 'scene') {
          return fail('SCENE_MOTION_REQUIRES_SCENE3D');
        }
        if (section.motion === 'scroll') stack.add('gsap-scrolltrigger-conditional');
        if (section.motion === 'micro') stack.add('css-waapi');
        if (section.assetSource !== 'none' && section.assetSource !== 'owner-original') {
          risks.add('ASSET_RIGHTS_REVIEW_REQUIRED');
        }
        if (section.kind === 'case-studies' || section.kind === 'team')
          risks.add('CLIENT_AND_PERSON_CLAIMS_REQUIRE_SOURCE_REVIEW');
        sections.push(Object.freeze({
          id:section.id, kind:section.kind, motion:section.motion,
          reducedMotionFallback:section.fallback, assetSource:section.assetSource
        }));
        ++totalSections;
      }
      if (pageScenes > 4 || totalScenes > 12) return fail('SCENE_COUNT_REQUIRES_REARCHITECTURE');
      pages.push(Object.freeze({path:page.path,sections:Object.freeze(sections)}));
    }
    if (!paths.has('/')) return fail('ROOT_PAGE_REQUIRED');
    const reasons = ['ORIGINAL_CREATIVE_AND_CONTENT_REVIEW_REQUIRED',
      'BROWSER_RESPONSIVE_AND_ACCESSIBILITY_QA_REQUIRED',
      'MEASURED_WEB_VITALS_AND_RESOURCE_BUDGETS_REQUIRED',
      'SECURITY_AND_SUPPLY_CHAIN_REVIEW_REQUIRED',
      ...[...risks].sort(),
      'EXPLICIT_OWNER_RELEASE_APPROVAL_REQUIRED'];
    const plan = Object.freeze({
      projectSlug:input.projectSlug, siteProfile:input.siteProfile,
      pageCount:pages.length, sectionCount:totalSections, sceneCount:totalScenes,
      pages:Object.freeze(pages),
      suggestedStack:Object.freeze([...stack].sort()),
      constraints:Object.freeze({
        maximumSimultaneousWebglContexts:1,
        noMandatoryPaidServices:true,
        assetsMustHaveProvenance:true,
        contentAndNavigationIndependentOfWebgl:true,
        reducedMotionMandatory:true,
        normalScrollDefault:true,
        targetLcpMs:2500,targetInpMs:200,targetCls:0.1,
        metricsAreUnmeasuredTargets:true
      }),
      requiredEvidence:Object.freeze(reasons),
      // Never infer authorization or production viability from a valid contract.
      genuineModelAuthorshipProven:false,
      runnableWebsiteBuilt:false,
      browserTested:false,
      physicalGpuMeasured:false,
      humanReviewed:false,
      ownerApproved:false,
      deploymentAuthorized:false,
      stage45Go:false
    });
    return Object.freeze({kind:EXPERIENCE_CONTRACT_KIND,valid:true,reasons:[],plan,stage45Go:false,releaseGo:false});
  } catch {
    return fail('INVALID_DATA_ACCESS');
  }
}
