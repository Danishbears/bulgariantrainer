import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';

const base = process.env.AORIST_PRACTICE_BASE_URL;
const stateFile = new URL('../../../target/aorist-practice-http-state.json', import.meta.url);
const sourceFile = new URL('../../../learning-materials/aorist/bulgarian_aorist_40.json', import.meta.url);
const key = text => text.normalize('NFC').replace(/[\u0301\u0300]/gu, '').toLowerCase().trim().replace(/\s+/gu, ' ');

test('aorist practice: live typed/choice sessions, protected answers and JVM recovery', { skip: !base, timeout: 60000 }, async () => {
  const target = new URL(base);
  assert.equal(target.hostname, '127.0.0.1'); assert.ok(target.port && target.port !== '8080');
  const api = async (path, { method = 'GET', body, form, status = 200 } = {}) => {
    const response = await fetch(new URL(path, target), { method, signal: AbortSignal.timeout(15000),
      ...(form ? { body: form } : body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) });
    const data = await response.json(); assert.equal(response.status, status, JSON.stringify(data)); return data;
  };
  const post = (path, body, status = 200) => api(path, { method: 'POST', body, status });
  const route = (s, action) => `/api/aorist-practice/sessions/${s.id}/${action}`;
  const source = JSON.parse(await readFile(sourceFile, 'utf8'));
  const expected = s => source.entries.find(e => e.lemma === s.currentQuestion.prompt.lemma).forms[s.currentQuestion.prompt.person];
  const assertSafe = s => {
    assert.equal(s.currentQuestion.feedback, null);
    const serialized = JSON.stringify(s);
    for (const field of ['acceptedAnswers', 'correctOptionId', 'correctIndex', 'snapshot', 'contentHash', 'source', 'note', 'forms']) assert.ok(!serialized.includes(`"${field}"`));
    const q = s.currentQuestion;
    assert.equal(q.prompt.tense, 'aorist'); assert.ok(q.prompt.person && q.prompt.personLabel && q.prompt.russianMeaning);
    assert.ok(q.prompt.answerScope.includes('без подлежащего'));
    if (s.settings.exerciseType === 'TYPED') {
      assert.deepEqual(q.options, []);
      for (const answer of expected(s)) if (key(q.prompt.lemma) !== key(answer)) assert.ok(!serialized.includes(answer));
    } else {
      const accepted = new Set(expected(s).map(key));
      assert.equal(q.options.filter(o => accepted.has(key(o.text))).length, 1);
      assert.equal(new Set(q.options.map(o => key(o.text))).size, q.options.length);
      for (const option of q.options) assert.deepEqual(Object.keys(option).sort(), ['optionId', 'text']);
    }
  };
  const submission = s => s.settings.exerciseType === 'TYPED'
    ? { questionId: s.currentQuestion.id, answer: ` \t${expected(s)[0].toUpperCase().replace(/[АЪОУЕИЮЯ]/u, '$&\u0301')}\u00a0\n` }
    : { questionId: s.currentQuestion.id, optionId: s.currentQuestion.options.find(o => expected(s).map(key).includes(key(o.text))).optionId };
  const progress = () => api('/api/aorist-practice/progress');

  if (process.env.AORIST_PRACTICE_RESTART === '1') {
    const saved = JSON.parse(await readFile(stateFile, 'utf8'));
    assert.deepEqual(await progress(), saved.progress);
    for (const item of saved.sessions) {
      assert.deepEqual(await api(route(item.state, 'question')), item.state);
      assert.deepEqual(await post('/api/aorist-practice/sessions', item.request, 201), item.state);
      assert.deepEqual(await post(route(item.state, 'answers'), item.submission), item.state);
    }
    assert.deepEqual(await progress(), saved.progress, 'Replayed answers cannot update schedules again');
    for (const item of saved.sessions) {
      let s = await post(route(item.state, 'next'), { questionId: item.state.currentQuestion.id }); assertSafe(s);
      if (s.settings.exerciseType === 'TYPED') {
        const wrong = { questionId: s.currentQuestion.id, answer: 'не совпало' };
        const graded = await post(route(s, 'answers'), wrong);
        assert.equal(graded.currentQuestion.feedback.outcome, 'INCORRECT');
        assert.deepEqual(graded.currentQuestion.feedback.acceptedAnswers, expected(s), 'Reimport cannot change a saved question');
        assert.deepEqual(await post(route(s, 'answers'), wrong), graded);
        const results = await api(route(s, 'results')); assert.equal(results.mistakes.length, 1); assert.equal(results.accuracyPercent, 50);
        s = graded;
      } else {
        const reveal = { questionId: s.currentQuestion.id };
        const graded = await post(route(s, 'reveal'), reveal);
        assert.equal(graded.correctAnswers, 1); assert.equal(graded.independentRecallCorrect, 0);
        assert.deepEqual(graded.currentQuestion.feedback.acceptedAnswers, expected(s));
        assert.deepEqual(await post(route(s, 'reveal'), reveal), graded);
        await post(route(s, 'answers'), submission(s), 409);
        const results = await api(route(s, 'results')); assert.equal(results.accuracyPercent, 100); assert.equal(results.session.answeredQuestions, 1);
        s = graded;
      }
      assert.equal(s.status, 'COMPLETED');
      const ended = await post(route(s, 'next'), { questionId: s.currentQuestion.id }); assert.equal(ended.currentQuestion, null);
    }
    const rows = await progress();
    assert.equal(rows.reduce((n, p) => n + p.totalAttempts, 0), 4);
    assert.equal(rows.reduce((n, p) => n + p.correctAttempts, 0), 2);
    for (const p of rows) assert.equal(p.intervalDays, 1);
    const statistics = await api('/api/aorist-practice/statistics');
    const typed = statistics.exercises.find(mode => mode.exerciseType === 'TYPED');
    const choice = statistics.exercises.find(mode => mode.exerciseType === 'MULTIPLE_CHOICE');
    assert.deepEqual([typed.answered, typed.correct, typed.incorrect, typed.revealed], [2, 1, 1, 0]);
    assert.deepEqual([choice.answered, choice.correct, choice.incorrect, choice.revealed], [1, 1, 0, 1]);
    for (const mode of statistics.exercises) {
      assert.equal(mode.persons.length, 6);
      assert.equal(mode.persons.reduce((sum, row) => sum + row.answered, 0), mode.answered);
      assert.equal(mode.dueForReview, 0, 'All verbs were archived before restart');
    }
    assert.deepEqual(await api('/api/statistics'), saved.legacy);
    return;
  }

  assert.equal((await api('/api/aorist-verbs')).totalElements, 0, 'Use a fresh disposable database; no startup import');
  const legacy = await api('/api/statistics');
  const form = new FormData(); form.append('file', new File([JSON.stringify(source)], 'aorist.json', { type: 'application/json' }));
  const preview = await api('/api/aorist-imports/preview', { method: 'POST', form }); assert.equal(preview.valid, true);
  const summary = await post('/api/aorist-imports/confirm', { importId: preview.importId, requestId: randomUUID(), entries: preview.entries.map(e => ({ externalId: e.externalId, expectedVersion: e.expectedVersion })) });
  assert.equal(summary.created, 40);
  assert.equal((await api('/api/aorist-practice/availability')).eligibleQuestions, 40);
  assert.equal((await api('/api/aorist-practice/availability?persons=1sg,2sg,3sg,1pl,2pl,3pl')).eligibleQuestions, 240);
  await post('/api/aorist-practice/sessions', { requestId: randomUUID(), size: 100 }, 409);
  const shorter = await post('/api/aorist-practice/sessions', { requestId: randomUUID(), size: 100, allowShorter: true }, 201);
  assert.equal(shorter.totalQuestions, 40); assert.equal(shorter.settings.exerciseType, 'TYPED'); assert.deepEqual(shorter.settings.persons, ['1sg']);
  await post(route(shorter, 'complete'), {});
  const skipped = await api(route(shorter, 'results')); assert.ok(skipped.items.every(i => i.outcome === 'SKIPPED' && i.feedback === null));
  assert.deepEqual(await progress(), []);
  const sessions = [];
  for (const type of ['TYPED', 'MULTIPLE_CHOICE']) {
    const request = { requestId: randomUUID(), size: 2, exerciseType: type, persons: type === 'TYPED' ? ['1pl'] : ['2sg', '3sg'], scope: 'NEW', datasetId: source.datasetId };
    const initial = await post('/api/aorist-practice/sessions', request, 201); assertSafe(initial);
    await post(route(initial, 'next'), { questionId: initial.currentQuestion.id }, 409);
    const answer = submission(initial);
    const state = await post(route(initial, 'answers'), answer); assert.equal(state.correctAnswers, 1);
    assert.deepEqual(await post(route(initial, 'answers'), answer), state);
    assert.equal(state.currentPosition, 1, 'Feedback remains until explicit Next');
    assert.equal(state.independentRecallCorrect, type === 'TYPED' ? 1 : 0);
    sessions.push({ request, state, submission: answer });
  }
  const unfinished = await api('/api/aorist-practice/sessions'); assert.equal(unfinished.totalElements, 2);
  for (const summary of unfinished.content) assert.ok(!JSON.stringify(summary).includes('acceptedAnswers'));
  const beforeEdit = await progress();
  const changed = structuredClone(source); changed.datasetRevision = 2;
  for (const entry of changed.entries) {
    entry.note = 'Synthetic post-start edit: snapshots must stay unchanged';
    for (const person of Object.keys(entry.forms)) entry.forms[person] = [entry.reflexiveParticle ? `променена форма ${entry.reflexiveParticle}` : 'променена форма'];
  }
  const editedFile = new FormData(); editedFile.append('file', new File([JSON.stringify(changed)], 'edited.json', { type: 'application/json' }));
  const update = await api('/api/aorist-imports/preview', { method: 'POST', form: editedFile }); assert.equal(update.valid, true);
  const updated = await post('/api/aorist-imports/confirm', { importId: update.importId, requestId: randomUUID(), entries: update.entries.map(e => ({ externalId: e.externalId, expectedVersion: e.expectedVersion })) });
  assert.equal(updated.updated, 40); assert.deepEqual(await progress(), beforeEdit);
  const verbs = await api('/api/aorist-verbs?size=100');
  for (const verb of verbs.content) await api(`/api/aorist-verbs/${verb.id}/archive`, { method: 'PATCH', body: { version: verb.version, archived: true } });
  assert.equal((await api('/api/aorist-practice/availability')).eligibleQuestions, 0);
  assert.deepEqual(await progress(), beforeEdit, 'Archiving must preserve progress and unfinished sessions');
  assert.deepEqual(await api('/api/statistics'), legacy);
  await writeFile(stateFile, JSON.stringify({ sessions, progress: await progress(), legacy }, null, 2));
});
