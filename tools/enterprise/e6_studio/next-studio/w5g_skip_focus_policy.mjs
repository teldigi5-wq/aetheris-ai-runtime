/** E6 W5-G: static fragment-target acceptance.
 * This supplements real browser focus tests; regex checks do not prove WCAG.
 */
const fail=s=>{throw Error('E6_W5G_'+s);};
export function checkSkipTargetMarkup(html){
  if(typeof html!=='string'||html.length<1000)fail('INVALID_DOCUMENT');
  const links=html.match(/<a\b[^>]*class="skip"[^>]*>/g)||[];
  const main=html.match(/<main\b[^>]*>/g)||[];
  if(links.length!==1||main.length!==1)fail('MISSING_OR_DUPLICATE_SKIP_TARGET');
  if(!/\bhref="#main"/.test(links[0])||
     !/\bid="main"/.test(main[0])||
     !/\btabindex="-1"/.test(main[0])||
     /\bhidden\b|\bstyle=/.test(main[0]))
    fail('SKIP_TARGET_NOT_FOCUSABLE');
  const ids=(html.match(/\bid="main"/g)||[]).length;
  if(ids!==1)fail('SKIP_TARGET_DUPLICATE_ID');
  if((html.match(/<script\b/gi)||[]).length!==0)
    fail('AUTHOR_SCRIPTS_PRESENT');
  return {fragmentTargetPresent:true,mainTabindexMinusOne:true,
    extraSequentialTabstopAdded:false,
    realBrowserFocusVerified:false,wcagCertified:false,
    ownerApproved:false,releaseGo:false};
}
export function checkNoJsFocusProof(x){
  if(!x||x.browserJavaScriptEnabled!==false||
    x.hasExactlyOneMain!==true||x.hasExactlyOneSkipLink!==true||
    x.skipWasFirstTabStop!==true||x.skipFocusIndicatorVisible!==true||
    x.fragmentNavigatedToMain!==true||x.activeElementIsMain!==true||
    x.mainHasTabindexMinusOne!==true||
    x.nextTabDidNotReturnToSkip!==true||
    x.horizontalOverflowPx>2||!Number.isFinite(x.horizontalOverflowPx)||
    x.outsideRequests!==0||x.browserErrors!==0||
    x.ownerDeviceMeasured!==false||x.realModelUsed!==false||
    x.wcagCertified!==false||x.releaseGo!==false)
    fail('NOT_AN_ACTUAL_NO_JS_FOCUS_PROOF');
  return true;
}
