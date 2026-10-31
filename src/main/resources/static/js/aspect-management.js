import { $, api, status, element, button, content, relations, load, save, equalContent } from './aspect-common.js';

const base = '/api/aspect-pairs';
const storageKey = 'bulgariantrainer.aspect-pairs.management';
const fields = { imperfective: 'pair-imperfective', perfective: 'pair-perfective', russianImperfective: 'pair-russian-imperfective',
  russianPerfective: 'pair-russian-perfective', topic: 'pair-topic', note: 'pair-note' };
export function mountManagement(changed) {
  let busy = false, page = 0, totalPages = 0, editing = null, visible = false;
  let pending = load(storageKey)?.pending || null;
  if (!['save', 'archive'].includes(pending?.kind)) pending = null;
  const remember = () => save(storageKey, { pending });
  function controls() {
    const frozen = busy || !!pending;
    $('pair-editor-fields').disabled = frozen; $('pair-filter-fields').disabled = frozen; $('pair-create').disabled = frozen;
    $('pairs-previous').disabled = frozen || page <= 0; $('pairs-next').disabled = frozen || page + 1 >= totalPages;
    $('pair-list').querySelectorAll('button').forEach(node => { node.disabled = frozen; });
    $('pair-list').setAttribute('aria-busy', String(busy));
    $('pairs-retry').disabled = busy; $('pair-save-retry').disabled = busy;
    $('pair-save-retry').hidden = pending?.kind !== 'save';
    $('pairs-retry').hidden = pending?.kind === 'save' || (!pending && !$('pairs-retry').dataset.failed);
    $('pairs-retry').textContent = pending ? 'Проверить архивное состояние и повторить' : 'Повторить загрузку';
  }
  function filters() {
    const query = new URLSearchParams({ page, size: $('pair-filter-size').value, archived: $('pair-filter-archive').value });
    for (const [name, id] of [['namespace', 'pair-filter-namespace'], ['q', 'pair-query'], ['topic', 'pair-filter-topic'], ['studyStage', 'pair-filter-stage'], ['relationType', 'pair-filter-relation']])
      if ($(id).value) query.set(name, $(id).value);
    return query;
  }
  async function list() {
    if (busy || pending) return;
    busy = true; controls(); status('pairs-message', 'Загружаем пары…');
    try {
      let data = await api(`${base}?${filters()}`);
      if (data.totalPages && page >= data.totalPages) { page = data.totalPages - 1; data = await api(`${base}?${filters()}`); }
      totalPages = data.totalPages;
      $('pairs-page').textContent = `Страница ${data.totalPages ? page + 1 : 0} из ${data.totalPages}`;
      $('pair-list').replaceChildren();
      for (const pair of data.content) {
        const e = pair.entry, card = element('article', undefined, 'panel aspect-card');
        const title = element('h3', `${e.imperfective} → ${e.perfective}`); title.lang = 'bg';
        card.append(title, element('p', `${e.russianImperfective} → ${e.russianPerfective}`),
          element('p', `${e.topic} · Этап ${e.studyStage} · ${relations[e.relationType]}`, 'note'));
        const details = element('details'); details.append(element('summary', 'Ответы, пояснение и примеры'));
        const full = element('div'); content(full, e); details.append(full); card.append(details);
        const toolbar = element('div', undefined, 'toolbar');
        const edit = button('Изменить', () => open(pair.id)); edit.setAttribute('aria-label', `Изменить пару ${e.imperfective} — ${e.perfective}`);
        const archive = button(pair.archived ? 'Восстановить' : 'В архив', () => archivePair(pair));
        archive.setAttribute('aria-label', `${pair.archived ? 'Восстановить' : 'В архив'}: ${e.imperfective} — ${e.perfective}`);
        toolbar.append(edit, archive); card.append(toolbar); $('pair-list').append(card);
      }
      if (!data.content.length) {
        const empty = element('div', undefined, 'empty-state'); empty.append(element('h3', 'Пары не найдены'), element('p', 'Измените поиск и фильтры, добавьте пару или импортируйте JSON.'));
        $('pair-list').append(empty);
      }
      delete $('pairs-retry').dataset.failed;
      status('pairs-message', `Найдено записей: ${data.totalElements}.`);
    } catch (error) { status('pairs-message', error.message, true); $('pairs-retry').dataset.failed = 'true'; }
    finally { busy = false; controls(); }
  }
  function sourceFields() {
    const presentation = $('pair-provenance').value === 'presentation';
    $('pair-source').disabled = !presentation; $('pair-source-row').disabled = !presentation;
    $('pair-source').required = presentation; $('pair-source-row').required = presentation;
  }
  function fill(pair) {
    editing = pair?.id ? pair : null;
    const e = pair?.entry || {};
    $('pair-form').reset(); $('pair-editor').hidden = false;
    $('pair-editor-title').textContent = editing ? 'Редактирование пары' : 'Новая пара';
    $('pair-namespace').value = pair?.namespace || 'bulgarian-aspect-pairs-100';
    $('pair-external-id').value = e.id || `manual-${crypto.randomUUID()}`;
    $('pair-namespace').readOnly = !!editing; $('pair-external-id').readOnly = !!editing;
    for (const [field, id] of Object.entries(fields)) $(id).value = e[field] || '';
    $('pair-accepted-imperfective').value = (e.acceptedImperfective || []).join('\n');
    $('pair-accepted-perfective').value = (e.acceptedPerfective || []).join('\n');
    $('pair-stage').value = e.studyStage || 1; $('pair-relation').value = e.relationType || 'core';
    $('pair-provenance').value = e.provenance?.kind || 'added';
    $('pair-source').value = e.provenance?.sourceId || ''; $('pair-source-row').value = e.provenance?.tableRow || '';
    for (const side of ['imperfective', 'perfective']) for (const lang of ['bg', 'ru']) $(`pair-example-${side}-${lang}`).value = e.examples?.[side]?.[lang] || '';
    sourceFields(); $('pair-reload').hidden = true; status('pair-editor-message', '');
  }
  async function open(id) {
    if (busy || pending) return;
    busy = true; controls(); let opened = false;
    try { fill(await api(`${base}/${id}`)); opened = true; }
    catch (error) { status('pairs-message', error.message, true); }
    finally { busy = false; controls(); if (opened) $('pair-imperfective').focus(); }
  }
  function build() {
    const entry = { ...(editing?.entry || {}), id: $('pair-external-id').value };
    for (const [field, id] of Object.entries(fields)) entry[field] = $(id).value;
    for (const side of ['imperfective', 'perfective']) {
      const field = side === 'imperfective' ? 'acceptedImperfective' : 'acceptedPerfective';
      const value = $(`pair-accepted-${side}`).value;
      entry[field] = editing?.entry[field]?.join('\n') === value ? editing.entry[field] : value.split(/\r?\n/).filter(line => line.trim());
      if (!entry[field].length || entry[field].length > 50 || entry[field].some(line => line.length > 255)) throw new Error('Для каждого вида укажите от 1 до 50 ответов длиной до 255 символов.');
    }
    entry.studyStage = Number($('pair-stage').value); entry.relationType = $('pair-relation').value;
    entry.provenance = $('pair-provenance').value === 'added' ? { kind: 'added' } : { kind: 'presentation', sourceId: $('pair-source').value, tableRow: Number($('pair-source-row').value) };
    const examples = Object.fromEntries(['imperfective', 'perfective'].map(side => [side, Object.fromEntries(['bg', 'ru'].map(lang => [lang, $(`pair-example-${side}-${lang}`).value]))]));
    const values = Object.values(examples).flatMap(example => Object.values(example));
    if (values.some(value => value.trim()) && values.some(value => !value.trim())) throw new Error('Заполните оба примера с обоими переводами или оставьте все четыре поля пустыми.');
    entry.examples = values.some(value => value.trim()) ? examples : null;
    return { namespace: $('pair-namespace').value, ...(editing ? { version: editing.version } : {}), entry };
  }
  async function done(message) {
    const kind = pending.kind; pending = null; remember();
    if (kind === 'save') { $('pair-editor').hidden = true; editing = null; status('pair-editor-message', ''); }
    busy = false; status('pairs-message', message); controls(); changed();
    if (visible) { await list(); status('pairs-message', message); $('pairs-message').focus(); }
  }
  async function sendMutation() {
    busy = true; controls();
    const op = pending;
    const id = op.kind === 'save' ? 'pair-editor-message' : 'pairs-message'; status(id, 'Сохраняем…');
    try {
      await api(op.kind === 'archive' ? `${base}/${op.id}/archive` : op.id ? `${base}/${op.id}` : base,
        { method: op.kind === 'archive' ? 'PATCH' : op.id ? 'PUT' : 'POST', body: op.body });
      await done(op.kind === 'save' ? 'Пара сохранена.' : op.body.archived ? 'Пара перенесена в архив. История сохранена.' : 'Пара восстановлена.');
    } catch (error) {
      if (error.definite) {
        pending = null; remember();
        if (op.kind === 'save' && op.id && error.code === 'STALE_ASPECT_PAIR') $('pair-reload').hidden = false;
        if (op.kind === 'archive') $('pairs-retry').dataset.failed = 'true';
      }
      status(id, error.definite ? error.message : 'Ответ сервера не получен. Данные сохранены на странице. Нажмите кнопку проверки сохранения, прежде чем менять запись.', true);
    } finally { busy = false; controls(); }
  }
  async function recover() {
    if (busy || !pending) return;
    busy = true; controls(); const op = pending;
    try {
      let current;
      if (op.id) current = await api(`${base}/${op.id}`);
      else for (const archived of [false, true]) {
        let searchPage = 0, pages = 1;
        while (!current && searchPage < pages) {
          const result = await api(`${base}?${new URLSearchParams({ namespace: op.body.namespace, q: op.body.entry.id, size: 100, archived, page: searchPage })}`);
          current = result.content.find(pair => pair.entry.id === op.body.entry.id);
          pages = result.totalPages; searchPage++;
        }
        if (current) break;
      }
      if (op.kind === 'archive' ? current?.archived === op.body.archived : current && equalContent(current.entry, op.body.entry)) {
        await done('Сохранение подтверждено. Повторной записи не было.'); return;
      }
      if (current && (!op.id || current.version !== op.body.version)) {
        pending = null; remember();
        if (op.kind === 'save' && op.id) $('pair-reload').hidden = false;
        status(op.kind === 'save' ? 'pair-editor-message' : 'pairs-message', 'Запись уже отличается от вашей версии. Введённый текст сохранён на странице; загрузите актуальную запись перед новой правкой.', true);
      } else { busy = false; await sendMutation(); }
    } catch (error) { status(op.kind === 'save' ? 'pair-editor-message' : 'pairs-message', error.message, true); }
    finally { busy = false; controls(); }
  }
  function archivePair(pair) {
    if (busy || pending) return;
    pending = { kind: 'archive', id: pair.id, body: { version: pair.version, archived: !pair.archived } }; remember(); sendMutation();
  }
  $('pair-form').addEventListener('submit', event => {
    event.preventDefault(); if (busy || pending) return;
    try { pending = { kind: 'save', id: editing?.id || null, body: build() }; remember(); sendMutation(); }
    catch (error) { status('pair-editor-message', error.message, true); }
  });
  $('pair-create').addEventListener('click', () => { if (!busy && !pending) { fill(null); $('pair-imperfective').focus(); } });
  $('pair-cancel').addEventListener('click', () => { if (!busy && !pending) { $('pair-editor').hidden = true; editing = null; $('pair-create').focus(); } });
  $('pair-provenance').addEventListener('change', sourceFields);
  for (const side of ['imperfective', 'perfective']) $(`pair-${side}`).addEventListener('blur', () => { if (!$(`pair-accepted-${side}`).value) $(`pair-accepted-${side}`).value = $(`pair-${side}`).value; });
  $('pair-save-retry').addEventListener('click', recover);
  $('pair-reload').addEventListener('click', () => { if (editing) open(editing.id); });
  $('pair-filters').addEventListener('submit', event => { event.preventDefault(); if (!busy && !pending) { page = 0; list(); } });
  $('pairs-previous').addEventListener('click', () => { if (!busy && !pending && page > 0) { page--; list(); } });
  $('pairs-next').addEventListener('click', () => { if (!busy && !pending && page + 1 < totalPages) { page++; list(); } });
  $('pairs-retry').addEventListener('click', () => pending ? recover() : list());
  if (pending?.kind === 'save') { fill({ id: pending.id, ...pending.body }); status('pair-editor-message', 'Осталось неподтверждённое сохранение. Проверьте его результат.', true); }
  controls();
  return { show: () => { visible = true; if (!pending) list(); }, hide: () => { visible = false; }, pending: () => !!pending };
}
