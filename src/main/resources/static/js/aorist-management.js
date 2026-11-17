import { $, api, status, element, button, content, persons, personCodes, aspects, load, save, equal } from './aorist-common.js';

const base = '/api/aorist-verbs', storageKey = 'bulgariantrainer.aorist.management';
const fields = ['dataset', 'external', 'lemma', 'russian', 'aspect', 'particle', 'group', 'note', 'source-title', 'source-url', 'source-section', 'source-date', 'revision', 'created', 'root-notes'];
export function mountManagement(changed) {
  for (const p of personCodes) { const label = element('label', persons[p]); const area = element('textarea'); area.id = `av-form-${p}`; area.lang = 'bg'; area.rows = 2; area.required = true; area.spellcheck = false; label.append(area); $('av-forms').append(label); }
  const saved = load(storageKey) || {};
  let editing = saved.editing || null, pending = ['save', 'archive'].includes(saved.pending?.kind) ? saved.pending : null;
  let busy = false, visible = false, page = 0, pages = 0, failed = false;
  const values = () => ({ ...Object.fromEntries(fields.map(f => [f, $(`av-${f}`).value])), forms: Object.fromEntries(personCodes.map(p => [p, $(`av-form-${p}`).value])), newDataset: $('av-new-dataset').checked });
  function remember() { save(storageKey, { pending, editing, draft: $('av-editor').hidden ? null : values() }); }
  function controls() {
    const frozen = busy || !!pending;
    $('av-editor-fields').disabled = frozen; $('av-filter-fields').disabled = frozen; $('av-create').disabled = frozen;
    $('av-list').querySelectorAll('button').forEach(b => { b.disabled = frozen; }); $('av-list').setAttribute('aria-busy', String(busy));
    $('av-previous').disabled = frozen || failed || page <= 0; $('av-next').disabled = frozen || failed || page + 1 >= pages;
    $('av-save-retry').hidden = pending?.kind !== 'save'; $('av-save-retry').disabled = busy;
    $('av-retry').hidden = pending?.kind === 'save' || (!pending && !failed); $('av-retry').disabled = busy;
    $('av-retry').textContent = pending ? 'Проверить архивное состояние и повторить' : 'Повторить загрузку';
    $('av-new-dataset').disabled = frozen || !!editing?.id;
    $('av-metadata').hidden = !$('av-new-dataset').checked;
    $('av-revision').required = $('av-created').required = $('av-new-dataset').checked;
  }
  function filters() {
    const q = new URLSearchParams({ page, size: $('av-filter-size').value, archived: $('av-filter-archive').value });
    for (const [name, id] of [['q', 'av-query'], ['datasetId', 'av-filter-dataset'], ['studyGroup', 'av-filter-group'], ['aspect', 'av-filter-aspect'], ['reflexiveParticle', 'av-filter-particle']]) if ($(id).value) q.set(name, $(id).value);
    return q;
  }
  async function list() {
    if (busy || pending) return; busy = true; controls(); status('av-message', 'Загружаем глаголы…');
    try {
      let data = await api(`${base}?${filters()}`);
      if (data.totalPages && page >= data.totalPages) { page = data.totalPages - 1; data = await api(`${base}?${filters()}`); }
      pages = data.totalPages; failed = false; $('av-list').replaceChildren();
      for (const row of data.content) {
        const card = element('article', undefined, 'panel'); card.dataset.verbId = row.id;
        const title = element('h3', row.entry.lemma); title.lang = 'bg'; card.append(title, element('p', row.entry.russian), element('p', `${aspects[row.entry.aspect]} · Группа: ${row.entry.studyGroup}`, 'note'));
        const details = element('details'); details.append(element('summary', 'Спряжение, пояснение и источник')); const full = element('div'); content(full, row.entry); details.append(full); card.append(details);
        const actions = element('div', undefined, 'toolbar'); const edit = button('Изменить', () => open(row.id)); edit.setAttribute('aria-label', `Изменить глагол ${row.entry.lemma}`);
        const archive = button(row.archived ? 'Восстановить' : 'В архив', () => { if (busy || pending) return; pending = { kind: 'archive', id: row.id, body: { version: row.version, archived: !row.archived } }; remember(); send(); });
        archive.setAttribute('aria-label', `${row.archived ? 'Восстановить' : 'В архив'}: ${row.entry.lemma}`); actions.append(edit, archive); card.append(actions); $('av-list').append(card);
      }
      if (!data.content.length) { const empty = element('div', undefined, 'empty-state'); empty.append(element('h3', 'Глаголы не найдены'), element('p', 'Измените фильтры, добавьте глагол или импортируйте JSON.')); const link = element('a', 'Импортировать набор'); link.href = '#import'; empty.append(link); $('av-list').append(empty); }
      $('av-page').textContent = `Страница ${pages ? page + 1 : 0} из ${pages}`; status('av-message', `Найдено записей: ${data.totalElements}.`);
    } catch (error) { failed = true; status('av-message', error.message, true); }
    finally { busy = false; controls(); }
  }
  function fill(row, draft) {
    editing = row?.id ? row : null; $('av-form').reset(); $('av-editor').hidden = false;
    $('av-editor-title').textContent = editing ? 'Редактирование глагола' : 'Новый глагол';
    const e = row?.entry || {};
    const v = { dataset: row?.datasetId || 'bg-aorist-core-40', external: e.id || `manual-${crypto.randomUUID()}`, lemma: e.lemma || '', russian: e.russian || '', aspect: e.aspect || 'imperfective', particle: e.reflexiveParticle || '', group: e.studyGroup || '', note: e.note || '',
      'source-title': e.source?.title || '', 'source-url': e.source?.url || '', 'source-section': e.source?.section || 'Минало свършено време', 'source-date': e.source?.checkedOn || '', revision: 1, created: new Date().toLocaleDateString('sv-SE'), 'root-notes': '', ...draft };
    for (const f of fields) $(`av-${f}`).value = v[f];
    for (const p of personCodes) $(`av-form-${p}`).value = draft?.forms?.[p] ?? (e.forms?.[p] || []).join('\n');
    $('av-new-dataset').checked = !!draft?.newDataset; $('av-dataset').readOnly = $('av-external').readOnly = !!editing;
    $('av-reload').hidden = true; status('av-editor-message', ''); controls();
  }
  async function open(id) {
    if (busy || pending) return; busy = true; controls();
    try { fill(await api(`${base}/${id}`)); remember(); } catch (error) { status('av-message', error.message, true); }
    finally { busy = false; controls(); if (!$('av-editor').hidden) $('av-lemma').focus(); }
  }
  function build() {
    const v = values(), forms = {};
    for (const p of personCodes) {
      forms[p] = editing?.entry.forms[p]?.join('\n') === v.forms[p] ? editing.entry.forms[p] : v.forms[p].split(/\r?\n/).filter(s => s.trim());
      if (!forms[p].length || forms[p].length > 50 || forms[p].some(s => s.length > 255)) throw new Error(`Для ${persons[p]} укажите от 1 до 50 ответов длиной до 255 символов.`);
    }
    const entry = { id: v.external, lemma: v.lemma, russian: v.russian, aspect: v.aspect, tense: 'aorist', reflexiveParticle: v.particle || null, studyGroup: v.group, note: v.note, forms,
      source: { title: v['source-title'], url: v['source-url'], section: v['source-section'], checkedOn: v['source-date'] } };
    const metadata = v.newDataset ? { format: 'bulgariantrainer.aorist', formatVersion: 1, datasetId: v.dataset, datasetRevision: Number(v.revision), createdOn: v.created, language: 'bg', translationLanguage: 'ru', tense: 'aorist', entryForm: 'first_person_singular_present_dictionary_form', notes: v['root-notes'].split(/\r?\n/).filter(s => s.trim()) } : undefined;
    return { datasetId: v.dataset, entry, ...(editing ? { version: editing.version } : {}), ...(metadata ? { metadata } : {}) };
  }
  async function done(message) {
    const kind = pending.kind; pending = null;
    if (kind === 'save') { $('av-editor').hidden = true; editing = null; }
    remember(); busy = false; controls(); changed();
    if (visible) await list(); status('av-message', message); $('av-message').focus();
  }
  async function send() {
    if (busy || !pending) return; busy = true; controls(); const op = pending, target = op.kind === 'save' ? 'av-editor-message' : 'av-message'; status(target, 'Сохраняем…');
    try { await api(op.kind === 'archive' ? `${base}/${op.id}/archive` : op.id ? `${base}/${op.id}` : base, { method: op.kind === 'archive' ? 'PATCH' : op.id ? 'PUT' : 'POST', body: op.body }); await done(op.kind === 'save' ? 'Глагол сохранён.' : op.body.archived ? 'Глагол перенесён в архив. История сохранена.' : 'Глагол восстановлен.'); }
    catch (error) {
      if (error.definite) { pending = null; if (op.id && op.kind === 'save' && error.code === 'STALE_AORIST_VERB') $('av-reload').hidden = false; if (op.kind === 'archive') failed = true; remember(); }
      status(target, error.definite ? error.message : 'Ответ сервера не получен. Ввод сохранён; проверьте сохранение перед новой правкой.', true);
    } finally { busy = false; controls(); }
  }
  async function recover() {
    if (busy || !pending) return; busy = true; controls(); const op = pending;
    try {
      let current;
      if (op.id) current = await api(`${base}/${op.id}`);
      else for (const archived of [false, true]) {
        let n = 0, total = 1;
        while (!current && n < total) { const rows = await api(`${base}?${new URLSearchParams({ datasetId: op.body.datasetId, q: op.body.entry.id, archived, page: n++, size: 100 })}`); current = rows.content.find(r => r.entry.id === op.body.entry.id); total = rows.totalPages; }
        if (current) break;
      }
      if (op.kind === 'archive' ? current?.archived === op.body.archived : current && equal(current.entry, op.body.entry)) { await done('Сохранение подтверждено. Повторной записи не было.'); return; }
      if (current && (!op.id || current.version !== op.body.version)) { pending = null; remember(); if (op.kind === 'save' && op.id) $('av-reload').hidden = false; status(op.kind === 'save' ? 'av-editor-message' : 'av-message', 'Запись уже отличается от вашей версии. Ввод сохранён; загрузите актуальную запись перед новой правкой.', true); }
      else { busy = false; await send(); }
    } catch (error) { status(op.kind === 'save' ? 'av-editor-message' : 'av-message', error.message, true); }
    finally { busy = false; controls(); }
  }
  $('av-form').addEventListener('input', () => { controls(); remember(); });
  $('av-form').addEventListener('submit', event => { event.preventDefault(); if (busy || pending) return; try { pending = { kind: 'save', id: editing?.id || null, body: build() }; remember(); send(); } catch (error) { status('av-editor-message', error.message, true); } });
  $('av-create').addEventListener('click', () => { if (!busy && !pending) { fill(null); remember(); $('av-lemma').focus(); } });
  $('av-cancel').addEventListener('click', () => { if (!busy && !pending) { $('av-editor').hidden = true; editing = null; remember(); $('av-create').focus(); } });
  $('av-reload').addEventListener('click', () => { if (editing && !busy && !pending) open(editing.id); });
  $('av-save-retry').addEventListener('click', recover); $('av-retry').addEventListener('click', () => pending ? recover() : list());
  $('av-filters').addEventListener('submit', event => { event.preventDefault(); if (!busy && !pending) { page = 0; list(); } });
  $('av-previous').addEventListener('click', () => { if (!busy && !pending && page > 0) { page--; list(); } }); $('av-next').addEventListener('click', () => { if (!busy && !pending && page + 1 < pages) { page++; list(); } });
  if (saved.draft || pending?.kind === 'save') { fill(saved.editing || (pending?.id ? { id: pending.id, ...pending.body } : null), saved.draft); if (pending) status('av-editor-message', 'Осталось неподтверждённое сохранение. Проверьте его результат.', true); }
  controls(); return { show() { visible = true; if (!pending) list(); }, hide() { visible = false; }, pending: () => !!pending || !$('av-editor').hidden };
}
