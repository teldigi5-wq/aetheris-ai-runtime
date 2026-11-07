import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {join, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {test} from 'node:test';
import {verifyFrozenPublicModelReceipt} from './e39g_public_candidate_receipt.mjs';

const dir = dirname(fileURLToPath(import.meta.url));
const input = readFileSync(join(dir, 'fixtures/e39g_public_model_receipt_42329086279.json'));
const candidates = [
  {id: 'cityline-public', digest: '40353c2d23a2bde49fddafbf0fdacb075bd5e16d9bf5d0f9df8a7d62f6064e50',
    old: "output.innerHTML = 'Station: '+name;", next: "output.textContent = 'Station: '+name;"},
  {id: 'studynest-public', digest: '114705feff3479eabafeeccdc57e3eedfa60a5f03d48f352ea6d9a644f762cf5',
    old: 'min-width:960px;', next: 'min-width:100%;'}
];

test('exact four-case public receipt links both narrowly frozen candidate quotes', () => {
  const r = verifyFrozenPublicModelReceipt(input, candidates);
  assert.equal(r.allPublicAttemptsCounted, 4);
  assert.deepEqual(r.exactPublicCandidatesLinked, ['cityline-public', 'studynest-public']);
  assert.equal(r.heldOut, false);
  assert.equal(r.independentModelAttestation, false);
  assert.equal(r.modelCallsPerformedByReviewer, 0);
});

test('one mutated receipt byte refuses provenance credit', () => {
  const wrong = Buffer.from(input);
  wrong[30] ^= 1;
  assert.throws(() => verifyFrozenPublicModelReceipt(wrong, candidates), /artifact bytes differ/);
});

test('replayed answer under different candidate is denied', () => {
  const altered = candidates.map(x => ({...x}));
  altered[1].next = altered[0].next;
  assert.throws(() => verifyFrozenPublicModelReceipt(input, altered), /new quote differs/);
});

test('source hash drift is denied, even if the proposed edit is unchanged', () => {
  const altered = candidates.map(x => ({...x}));
  altered[0].digest = 'a'.repeat(64);
  assert.throws(() => verifyFrozenPublicModelReceipt(input, altered), /changed source/);
});

test('unsafe or missing candidate sets cannot grow claimed public success', () => {
  assert.throws(() => verifyFrozenPublicModelReceipt(input, candidates.slice(0, 1)), /two pre-reviewed/);
  const altered = [...candidates, { ...candidates[0], id: 'focus-public' }];
  assert.throws(() => verifyFrozenPublicModelReceipt(input, altered), /two pre-reviewed/);
  assert.throws(() => verifyFrozenPublicModelReceipt(input, []), /two pre-reviewed/);
});

test('model is never inferred, executed, or signed by the offline verifier', () => {
  const raw = readFileSync(join(dir, 'e39g_public_candidate_receipt.mjs'), 'utf8');
  for (const forbidden of ['child_process', 'eval(', 'fetch(', 'http.request(', 'spawn(', 'exec(']) {
    assert.ok(!raw.includes(forbidden), forbidden + ' is forbidden');
  }
});
