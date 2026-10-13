"""Offline fail-closed registry for flagship design/library candidates."""
import json
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ALLOWED={
  "maplibre":("maplibre/maplibre-gl-js","maps","NOASSERTION"),
  "react-map-gl":("visgl/react-map-gl","maps","NOASSERTION"),
  "deck-gl":("visgl/deck.gl","geospatial","MIT"),
  "react-globe":("vasturiano/react-globe.gl","globe","MIT"),
  "r3f":("pmndrs/react-three-fiber","globe","MIT"),
  "motion":("motiondivision/motion","motion","MIT"),
  "gsap":("greensock/GSAP","motion","NOASSERTION"),
  "dnd-kit":("clauderic/dnd-kit","interaction","MIT"),
  "zod":("colinhacks/zod","validation","MIT"),
  "tanstack-query":("TanStack/query","data-fetch","MIT"),
  "playwright":("microsoft/playwright","testing","Apache-2.0"),
  "axe-core":("dequelabs/axe-core","accessibility","MPL-2.0")
}
ROOT_FIELDS={"schemaVersion","catalogStatus","physicalPcStatus","sourceDate",
"automaticPackageInstallation","automaticRuntimeExecution","liveAPIDataClaimed",
"sourcePinned","licenseApproved","projectDeploymentChanged","projects"}
ENTRY_FIELDS={"id","repository","url","category","reportedLicense","purpose",
"installed","enabled","licenseReviewed","verifiedCommitSha","executionAllowed"}

def validate(root=ROOT):
    errors=[]
    f=root/"experience-design"/"flagship-sources.json"
    if f.is_symlink() or not f.is_file() or f.stat().st_size>50000:return ["missing or oversized flagship catalog"]
    try: d=json.loads(f.read_text(encoding="utf-8"))
    except (OSError,ValueError,UnicodeError):return ["unreadable flagship catalog"]
    if not isinstance(d,dict) or set(d)!=ROOT_FIELDS:return ["invalid top-level source schema"]
    if type(d["schemaVersion"]) is not int or d["schemaVersion"]!=1 or d["catalogStatus"]!="REVIEW_ONLY":
        errors.append("schema/research status mismatch")
    if d["physicalPcStatus"]!="BLOCKED_PENDING_HARDWARE":errors.append("hardware trust drift")
    for key in ("automaticPackageInstallation","automaticRuntimeExecution","liveAPIDataClaimed",
                "sourcePinned","licenseApproved","projectDeploymentChanged"):
        if d[key] is not False:errors.append("source catalog attempted to promote "+key)
    if not isinstance(d["projects"],list) or len(d["projects"])!=len(ALLOWED):
        return errors+["catalog must match 12 exact approved GitHub candidates"]
    seen=set()
    for e in d["projects"]:
        if not isinstance(e,dict) or set(e)!=ENTRY_FIELDS:
            errors.append("bad project schema");continue
        n=e["id"]
        if not isinstance(n,str) or n not in ALLOWED:
            errors.append("unapproved project identity");continue
        if n in seen:errors.append("duplicate project")
        seen.add(n)
        repo,kind,lic=ALLOWED[n]
        if (e["repository"],e["category"],e["reportedLicense"])!=(repo,kind,lic):
            errors.append(f"{n}: pinned identity or reported license modified")
        if e["url"]!="https://github.com/"+repo:
            errors.append(f"{n}: URL substitution")
        if e["verifiedCommitSha"] is not None or e["licenseReviewed"] is not False:
            errors.append(f"{n}: unreviewed code/rights promoted")
        if e["installed"] is not False or e["enabled"] is not False or e["executionAllowed"] is not False:
            errors.append(f"{n}: execution/installation escalation")
        if not isinstance(e["purpose"],str) or not 10<=len(e["purpose"])<=240:
            errors.append(f"{n}: malformed purpose")
    if seen!=set(ALLOWED):errors.append("missing expected project source")
    for path in (root/"experience-design").rglob("*"):
        if path.is_symlink():errors.append("symlink in design reference")
        elif path.is_file() and path.relative_to(root/"experience-design").as_posix() not in {
            "README.md","flagship-sources.json","connected-journey.mjs","connected-journey.contract.json"
        }:errors.append("unreviewed executable path in experience reference")
    return errors

if __name__=="__main__":
    errors=validate()
    if errors:
        for e in errors:print("FAIL:",e,file=sys.stderr)
        sys.exit(1)
    print("12 design-library references validated, none installed or executable")
