// Run only after the verification server has stopped and restarted on the same
// disposable database created by aspect-pairs-browser.test.mjs.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const base = process.env.ASPECT_BROWSER_BASE_URL;
assert.match(base || '', /^http:\/\/127\.0\.0\.1:\d+$/);
const saved = JSON.parse(await readFile('target/aspect-final-state.json', 'utf8'));
async function request(path, body, expected = 200) {
  const response = await fetch(`${base}${path}`, body === undefined ? {} : {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  });
  assert.equal(response.status, expected, path); return response.json();
}
const path = `/api/aspect-pair-practice/sessions/${saved.unfinished.id}`;
assert.deepEqual(await request(`${path}/question`), saved.unfinished, 'Actual process restart restores feedback and position');
assert.deepEqual(await request('/api/aspect-pair-practice/sessions', saved.startRequest, 201), saved.unfinished, 'Start UUID survives process restart');
assert.deepEqual(await request(`${path}/answers`, saved.submission), saved.unfinished, 'Submission retry cannot duplicate progress');
let statistics = await request('/api/aspect-pair-practice/statistics');
for (const field of ['answered', 'correct', 'incorrect', 'revealed', 'dueForReview', 'dueDirections', 'directions'])
  assert.deepEqual(statistics[field], saved.statistics[field]);
const second = await request(`${path}/next`, { questionId: saved.submission.questionId });
assert.equal(second.currentPosition, 2); assert.equal(second.currentQuestion.feedback, null);
assert.doesNotMatch(JSON.stringify(second), /"купя"|acceptedPerfective|snapshot|examples/);
const revealed = await request(`${path}/reveal`, { questionId: second.currentQuestion.id });
assert.equal(revealed.status, 'COMPLETED'); assert.equal(revealed.correctAnswers, 1);
assert.deepEqual(await request(`${path}/reveal`, { questionId: second.currentQuestion.id }), revealed);
assert.equal((await request(`${path}/answers`, { questionId: second.currentQuestion.id, answer: 'купя' }, 409)).code, 'ANSWER_REVEALED');
await request(`${path}/next`, { questionId: second.currentQuestion.id });
const results = await request(`${path}/results`);
assert.deepEqual(results.items.map(item => item.outcome), ['CORRECT', 'REVEALED']);
statistics = await request('/api/aspect-pair-practice/statistics');
for (const field of ['answered', 'correct', 'incorrect', 'dueForReview', 'dueDirections']) assert.equal(statistics[field], saved.statistics[field]);
assert.equal(statistics.revealed, saved.statistics.revealed + 1);
const progress = await request('/api/aspect-pair-practice/progress?namespace=restart-check');
assert.equal(progress.length, 2); assert.ok(progress.every(row => row.totalAttempts === 1 && row.intervalDays === 1));
assert.equal(progress.reduce((sum, row) => sum + row.correctAttempts, 0), 1);
assert.deepEqual(await request('/api/statistics'), saved.legacy);
console.log('PASS: actual JVM restart, queue/feedback recovery, exact retries, hidden answers, reveal scheduling, separate statistics and legacy isolation.');
