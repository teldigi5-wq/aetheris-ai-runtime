/**
 * Exact-byte, offline linkage for PUBLIC Qwen candidate browser-review fixtures.
 * This is not signed model provenance, held-out evidence, or patch permission.
 * Source: historical Actions #42329086279 untrusted model-receipt artifact.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';

const sha256 = value => createHash('sha256').update(value).digest('hex');
const RECEIPT_SHA256 = 'a8a2dc8c8aaee525551b7dabda645b1ac3654144ba3d307ec4d4e846612eec54';
const SOURCE_BUNDLE_SHA256 = 'b31ac6df6f91fafbf3eb39c32d70a85e26f1db60090c0fc37f8a731ce672b957';
const EXPECTED_IDS = ['cityline-public', 'focus-public', 'motion-public', 'studynest-public'];
const APPROVED_PUBLIC_IDS = ['cityline-public', 'studynest-public'];
const RESPONSE_KEYS = ['fault', 'new', 'old', 'operation', 'site', 'sourceSha256'];
const TRIAL_KEYS = ['caseId', 'rawModelResponse', 'rawResponseSha256', 'sourceSha256'];
const RECEIPT_KEYS = ['evidenceClass', 'goldenReferenceAvailable', 'modelCallsSelfReported',
  'modelOutputExecuted', 'modelTag', 'ownerApprovalGranted', 'sourceBundleSha256', 'trials'];

const exactKeys = (actual, expected, label) => {
  assert.ok(actual && typeof actual === 'object' && !Array.isArray(actual), label + ' must be object');
  assert.deepEqual(Object.keys(actual).sort(), [...expected].sort(), label + ' key drift');
};

/**
 * Throws on any receipt, declared edit, or source pin mismatch; never applies a patch.
 * The reference file is intentionally already-public research data.
 */
export function verifyFrozenPublicModelReceipt(rawBytes, browserCandidates) {
  assert.ok(Buffer.isBuffer(rawBytes), 'receipt must be raw bytes');
  assert.equal(sha256(rawBytes), RECEIPT_SHA256, 'frozen artifact bytes differ');
  assert.ok(Array.isArray(browserCandidates), 'candidates must be an array');
  assert.deepEqual(browserCandidates.map(x => x.id).sort(), APPROVED_PUBLIC_IDS,
    'only the two pre-reviewed PUBLIC browser cases may be considered');

  const receipt = JSON.parse(rawBytes.toString('utf8'));
  exactKeys(receipt, RECEIPT_KEYS, 'receipt');
  assert.equal(receipt.evidenceClass, 'E39G12B4H_UNTRUSTED_MODEL_PROPOSALS');
  assert.equal(receipt.sourceBundleSha256, SOURCE_BUNDLE_SHA256);
  assert.equal(receipt.modelTag, 'qwen2.5-coder:7b');
  assert.equal(receipt.modelCallsSelfReported, 4); // not independent model attestation
  for (const field of ['ownerApprovalGranted', 'modelOutputExecuted', 'goldenReferenceAvailable']) {
    assert.equal(receipt[field], false, field + ' cannot grant authority');
  }
  assert.ok(Array.isArray(receipt.trials));
  assert.equal(receipt.trials.length, 4, 'all four attempts must remain in evidence');
  assert.deepEqual(receipt.trials.map(x => x.caseId), EXPECTED_IDS,
    'model attempts must be complete and in canonical order');
  const attempts = new Map();
  for (const t of receipt.trials) {
    exactKeys(t, TRIAL_KEYS, 'trial');
    assert.equal(typeof t.rawModelResponse, 'string');
    assert.equal(sha256(Buffer.from(t.rawModelResponse, 'utf8')), t.rawResponseSha256,
      t.caseId + ' raw model answer hash mismatch');
    assert.match(t.sourceSha256, /^[a-f0-9]{64}$/);
    const suggestion = JSON.parse(t.rawModelResponse);
    exactKeys(suggestion, RESPONSE_KEYS, 'model proposal');
    assert.equal(suggestion.operation, 'replace_one');
    for (const value of Object.values(suggestion)) assert.equal(typeof value, 'string');
    assert.equal(suggestion.sourceSha256, t.sourceSha256,
      t.caseId + ' broken-source digest mismatch');
    attempts.set(t.caseId, suggestion);
  }
  const linked = [];
  for (const c of browserCandidates) {
    assert.equal(typeof c.id, 'string');
    assert.match(c.digest, /^[a-f0-9]{64}$/);
    const model = attempts.get(c.id);
    assert.ok(model, c.id + ' missing model answer');
    assert.equal(c.digest, model.sourceSha256, c.id + ' changed source');
    assert.equal(c.old, model.old, c.id + ' old quote differs from raw model answer');
    assert.equal(c.next, model.new, c.id + ' new quote differs from raw model answer');
    const expectedSite = c.id === 'cityline-public' ? 'cityline' : 'studynest';
    const expectedFault = c.id === 'cityline-public' ? 'defensive-html' : 'mobile-overflow';
    assert.equal(model.site, expectedSite);
    assert.equal(model.fault, expectedFault);
    linked.push(c.id);
  }
  // Focus and motion remain model attempts, but are intentionally NOT patch-admitted.
  assert.equal(attempts.get('focus-public').new, '.skip:focus{top:0;}');
  assert.match(attempts.get('motion-public').new, /\n/);
  return Object.freeze({
    modelRunId: 42329086279,
    modelReceiptSha256: RECEIPT_SHA256,
    sourceBundleSha256: SOURCE_BUNDLE_SHA256,
    allPublicAttemptsCounted: 4,
    exactPublicCandidatesLinked: linked,
    declinedPublicCases: ['focus-public', 'motion-public'],
    heldOut: false,
    independentModelAttestation: false,
    modelCallsPerformedByReviewer: 0,
  });
}
