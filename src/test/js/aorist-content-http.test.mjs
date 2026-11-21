import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';

const base = process.env.AORIST_HTTP_BASE_URL;
const stateFile = new URL('../../../target/aorist-http-state.json', import.meta.url);
const sourceFile = new URL('../../../learning-materials/aorist/bulgarian_aorist_40.json', import.meta.url);

test('aorist content: real HTTP import, management, export and durable replay', { skip: !base, timeout: 60000 }, async () => {
  const target = new URL(base);
  assert.equal(target.hostname, '127.0.0.1');
  assert.ok(target.port && target.port !== '8080', 'Use a separate disposable test server');
  const api = async (path, { method = 'GET', body, form, status = 200 } = {}) => {
    const response = await fetch(new URL(path, target), { method, signal: AbortSignal.timeout(12000),
      ...(form ? { body: form } : body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) });
    const content = await response.json();
    assert.equal(response.status, status, JSON.stringify(content));
    return content;
  };
  const preview = async bytes => {
    const form = new FormData(); form.append('file', new File([bytes], 'aorist.json', { type: 'application/json' }));
    return api('/api/aorist-imports/preview', { method: 'POST', form });
  };
  const selection = p => ({ importId: p.importId, requestId: randomUUID(),
    entries: p.entries.map(e => ({ externalId: e.externalId, expectedVersion: e.expectedVersion })), allowRevisionDowngrade: false });
  const confirm = (body, status = 200) => api('/api/aorist-imports/confirm', { method: 'POST', body, status });
  const legacy = () => api('/api/statistics');

  assert.equal((await api('/api/health')).status, 'UP');
  if (process.env.AORIST_HTTP_RESTART === '1') {
    const saved = JSON.parse(await readFile(stateFile, 'utf8'));
    assert.deepEqual(await confirm(saved.confirmation), saved.summary);
    assert.deepEqual(await api('/api/aorist-verbs?size=100'), saved.rows);
    assert.deepEqual(await legacy(), saved.legacy);
    assert.equal((await api('/api/aorist-verbs/export?datasetId=bg-aorist-core-40&archiveScope=all')).entries.length, 41);
    return;
  }

  assert.equal((await api('/api/aorist-verbs')).totalElements, 0, 'Fresh server must not import at startup');
  const oldStatistics = await legacy();
  const source = await readFile(sourceFile);
  const original = JSON.parse(source.toString('utf8'));
  const p = await preview(source);
  assert.equal(p.valid, true); assert.equal(p.entries.length, 40);
  assert.equal((await api('/api/aorist-verbs')).totalElements, 0, 'Preview cannot write content');
  const confirmation = selection(p), summary = await confirm(confirmation);
  assert.equal(summary.created, 40); assert.deepEqual(await confirm(confirmation), summary);
  assert.deepEqual(await api('/api/aorist-verbs/export?datasetId=bg-aorist-core-40'), original);
  const repeat = await preview(source);
  assert.ok(repeat.entries.every(e => e.classification === 'UNCHANGED'));
  assert.equal((await confirm(selection(repeat))).unchanged, 40);
  assert.equal((await api('/api/aorist-verbs?aspect=imperfective')).totalElements, 11);
  assert.equal((await api('/api/aorist-verbs?reflexiveParticle=%D1%81%D0%B5')).totalElements, 2);
  assert.equal((await api('/api/aorist-verbs?size=7&page=5')).content.length, 5);

  const changed = structuredClone(original); changed.datasetRevision = 2;
  changed.entries[0].note = 'Проверка явного обновления';
  const update = await preview(JSON.stringify(changed));
  assert.equal(update.entries[0].classification, 'UPDATE');
  const selected = selection(update); selected.entries = [selected.entries[0]];
  assert.equal((await confirm(selected)).updated, 1);
  const firstId = p.entries[0].externalId;
  let row = (await api('/api/aorist-verbs?size=100')).content.find(e => e.entry.id === firstId);
  assert.equal(row.id, summary.entries[0].id);
  assert.equal(row.entry.note, changed.entries[0].note);
  const entry = structuredClone(row.entry); entry.forms['1sg'].push('гледахме');
  row = await api(`/api/aorist-verbs/${row.id}`, { method: 'PUT', body: { datasetId: row.datasetId, version: row.version, entry } });
  assert.equal(row.entry.forms['1sg'].length, 2);
  const staleVersion = row.version;
  row = await api(`/api/aorist-verbs/${row.id}/archive`, { method: 'PATCH', body: { version: row.version, archived: true } });
  assert.equal((await api('/api/aorist-verbs')).totalElements, 39);
  assert.equal((await api('/api/aorist-verbs?archived=true')).totalElements, 1);
  await api(`/api/aorist-verbs/${row.id}/archive`, { method: 'PATCH', body: { version: staleVersion, archived: false }, status: 409 });
  row = await api(`/api/aorist-verbs/${row.id}/archive`, { method: 'PATCH', body: { version: row.version, archived: false } });
  const manual = structuredClone(row.entry); manual.id = 'manual-same-lemma';
  await api('/api/aorist-verbs', { method: 'POST', body: { datasetId: row.datasetId, entry: manual }, status: 201 });
  await api('/api/aorist-verbs', { method: 'POST', body: { datasetId: row.datasetId, entry: manual }, status: 409 });

  const beforeInvalid = await api('/api/aorist-verbs?size=100');
  const invalidVersion = structuredClone(original); invalidVersion.formatVersion = 2;
  const duplicate = structuredClone(original); duplicate.entries[39].id = duplicate.entries[0].id;
  for (const bytes of ['{', JSON.stringify(invalidVersion), JSON.stringify(duplicate)]) {
    const invalid = await preview(bytes); assert.equal(invalid.valid, false); assert.ok(invalid.errors.length);
    await confirm({ importId: invalid.importId, requestId: randomUUID(), entries: [{ externalId: firstId, expectedVersion: row.version }] }, 400);
    assert.deepEqual(await api('/api/aorist-verbs?size=100'), beforeInvalid);
  }
  const stale = await preview(JSON.stringify(changed));
  await api(`/api/aorist-verbs/${row.id}/archive`, { method: 'PATCH', body: { version: row.version, archived: true } });
  await confirm(selection(stale), 409);
  const archivedRow = await api(`/api/aorist-verbs/${row.id}`);
  await api(`/api/aorist-verbs/${row.id}/archive`, { method: 'PATCH', body: { version: archivedRow.version, archived: false } });
  assert.deepEqual(await legacy(), oldStatistics);
  assert.equal((await api('/api/words')).totalElements, 0);
  assert.equal((await api('/api/sentences')).totalElements, 0);
  const rows = await api('/api/aorist-verbs?size=100'); assert.equal(rows.totalElements, 41);
  await writeFile(stateFile, JSON.stringify({ confirmation, summary, rows, legacy: oldStatistics }, null, 2));
});
