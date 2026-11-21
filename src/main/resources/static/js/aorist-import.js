import { $, api, request, status, element, content, load, save } from './aorist-common.js';

const storageKey = 'bulgariantrainer.aorist.import';
const classifications = { ADD: 'Новая запись', UPDATE: 'Обновление', UNCHANGED: 'Без изменений' };
const outcomes = { CREATED: 'Добавлено', UPDATED: 'Обновлено', UNCHANGED: 'Без изменений', DESELECTED: 'Не выбрано' };
const fieldNames = { entries: 'Записи', id: 'Идентификатор', lemma: 'Словарная форма', russian: 'Русское значение', aspect: 'Вид', tense: 'Время', reflexiveParticle: 'Частица', forms: 'Допустимые формы', source: 'Источник', title: 'Название', url: 'Ссылка', section: 'Раздел', checkedOn: 'Дата проверки', note: 'Пояснение', studyGroup: 'Учебная группа', format: 'Формат', formatVersion: 'Версия формата', datasetId: 'Код набора', datasetRevision: 'Ревизия набора', createdOn: 'Дата набора', language: 'Язык', translationLanguage: 'Язык перевода', entryForm: 'Словарная запись', notes: 'Примечания набора', '1sg': 'аз', '2sg': 'ти', '3sg': 'той/тя/то', '1pl': 'ние', '2pl': 'вие', '3pl': 'те' };
const errors = { INVALID_JSON: 'Неверный JSON: проверьте синтаксис, повторяющиеся ключи и лишний текст после документа.', INVALID_UTF8: 'Нужен файл UTF-8.', UNSUPPORTED_FORMAT: 'Нужен отдельный формат аориста.', UNSUPPORTED_FORMAT_VERSION: 'Поддерживается только целая версия формата 1.', DUPLICATE_EXTERNAL_ID: 'Повторяется идентификатор записи.', INVALID_DOCUMENT: 'В корне файла нужен объект JSON.', FILE_TOO_LARGE: 'Файл превышает 1 МБ.' };
const warnings = { GROUP_REVIEW: 'В исходном наборе у вземам группа 2_ih не соответствует показанным формам вземах. Метка сохранена; это повод проверить материал, а не правило спряжения.', REVISION_DOWNGRADE: 'Показана более ранняя ревизия источника. Для её импорта требуется отдельное согласие.', SAME_REVISION_DIFFERENT_CONTENT: 'Ревизия источника та же, но файл отличается. Проверьте изменения и выберите обновления явно.' };
const pathLabel = path => String(path).replace(/^\$\.?/, '').split('.').map(part => { const [field, index] = part.split('['); return `${fieldNames[field] || 'Поле'}${index ? ` № ${Number(index.replace(']', '')) + 1}` : ''}`; }).filter(Boolean).join(' → ');
export function mountImport(changed) {
  let preview = null, selected = new Set(), page = 0, busy = false, exporting = false;
  let pending = load(storageKey)?.pending || null;
  if (!pending?.importId || !pending?.requestId || !Array.isArray(pending.entries)) pending = null;
  const remember = () => save(storageKey, { pending });
  function controls() {
    const frozen = busy || !!pending;
    $('ai-upload-fields').disabled = frozen; $('ai-preview-fields').disabled = frozen;
    $('ai-confirm').disabled = frozen || !preview?.valid || !selected.size || (preview.warnings.some(w => w.code === 'REVISION_DOWNGRADE') && !$('ai-downgrade').checked);
    $('ai-retry').hidden = !pending; $('ai-retry').disabled = busy;
    $('ai-previous').disabled = frozen || page === 0; $('ai-next').disabled = frozen || !preview || (page + 1) * 10 >= preview.entries.length;
    $('ai-preview').setAttribute('aria-busy', String(busy)); $('ae-fields').disabled = exporting;
  }
  function render() {
    if (!preview) return;
    const pages = Math.ceil(preview.entries.length / 10); page = Math.min(page, Math.max(0, pages - 1));
    $('ai-page').textContent = `Страница ${pages ? page + 1 : 0} из ${pages}`;
    $('ai-count').textContent = `Записей: ${preview.entries.length}. Новых: ${preview.entries.filter(r => r.classification === 'ADD').length}. Обновлений: ${preview.entries.filter(r => r.classification === 'UPDATE').length}. Без изменений: ${preview.entries.filter(r => r.classification === 'UNCHANGED').length}. Выбрано: ${selected.size}.`;
    $('ai-rows').replaceChildren();
    for (const row of preview.entries.slice(page * 10, (page + 1) * 10)) {
      const card = element('article'); card.append(element('h4', `${row.externalId} · ${classifications[row.classification] || 'Проверьте запись'}`));
      const label = element('label', undefined, 'aorist-check'), check = element('input'); check.type = 'checkbox'; check.checked = selected.has(row.externalId); check.disabled = !preview.valid;
      check.addEventListener('change', () => { check.checked ? selected.add(row.externalId) : selected.delete(row.externalId); $('ai-count').textContent = $('ai-count').textContent.replace(/Выбрано: \d+\./, `Выбрано: ${selected.size}.`); controls(); });
      label.append(check, document.createTextNode(`Сохранить ${row.externalId}`)); card.append(label);
      if (row.classification === 'UPDATE') {
        const diff = element('div', undefined, 'aorist-diff');
        for (const [title, entry] of [['Было', row.before.entry], ['Будет', row.after]]) { const column = element('div'); column.append(element('strong', title)); content(column, entry); diff.append(column); }
        card.append(diff);
      } else { card.append(element('p', `${row.after.lemma} — ${row.after.russian}`)); const details = element('details'); details.append(element('summary', 'Все формы и источник')); const full = element('div'); content(full, row.after); details.append(full); card.append(details); }
      $('ai-rows').append(card);
    }
    controls();
  }
  function showPreview(data) {
    preview = data; page = 0; selected = new Set(data.valid ? data.entries.filter(r => r.classification === 'ADD').map(r => r.externalId) : []);
    $('ai-preview').hidden = false; $('ai-result').hidden = true; $('ai-downgrade').checked = false;
    $('ai-metadata').textContent = data.metadata ? `Набор: ${typeof data.metadata.datasetId === 'string' ? data.metadata.datasetId : 'не указан'}. Ревизия источника: ${Number.isInteger(data.metadata.datasetRevision) ? data.metadata.datasetRevision : 'не указана'}. Версия формата: ${Number.isInteger(data.metadata.formatVersion) ? data.metadata.formatVersion : 'не указана'}.` : 'Документ не прочитан.';
    const notes = Array.isArray(data.metadata?.notes) ? data.metadata.notes.filter(x => typeof x === 'string') : [];
    $('ai-root-notes').replaceChildren(...(notes.length ? [element('h4', 'Примечания исходного файла'), ...notes.map(n => element('p', n, 'note'))] : []));
    $('ai-errors').replaceChildren(...data.errors.map(e => element('p', `${pathLabel(e.path) || 'Документ'}: ${errors[e.code] || 'Проверьте обязательное поле, тип, длину и допустимые значения. Для форм нужны все шесть непустых списков.'}`)));
    $('ai-warnings').replaceChildren(...data.warnings.map(w => element('p', warnings[w.code] || 'В материале есть замечание. Проверьте запись.', 'note')));
    $('ai-downgrade-label').hidden = !data.warnings.some(w => w.code === 'REVISION_DOWNGRADE');
    render(); status('ai-message', data.valid ? 'Предпросмотр готов. Проверьте выбор перед подтверждением.' : 'Документ не прошёл проверку. Ни одна запись не будет сохранена; исправьте файл и загрузите снова.', !data.valid);
  }
  async function confirm() {
    if (busy || (!pending && (!preview?.valid || !selected.size))) return;
    if (!pending) pending = { importId: preview.importId, requestId: crypto.randomUUID(), entries: preview.entries.filter(r => selected.has(r.externalId)).map(r => ({ externalId: r.externalId, expectedVersion: r.expectedVersion })), allowRevisionDowngrade: $('ai-downgrade').checked };
    remember(); busy = true; controls(); status('ai-message', 'Сохраняем выбранные записи…');
    try {
      const result = await api('/api/aorist-imports/confirm', { method: 'POST', body: pending });
      pending = null; remember(); preview = null; selected.clear(); $('ai-preview').hidden = true; $('ai-rows').replaceChildren(); $('ai-result').hidden = false;
      $('ai-summary').textContent = `Добавлено: ${result.created}. Обновлено: ${result.updated}. Без изменений: ${result.unchanged}. Не выбрано: ${result.deselected}.`;
      $('ai-outcomes').replaceChildren(...result.entries.map(r => element('li', `${r.externalId}: ${outcomes[r.outcome] || 'Обработано'}`)));
      status('ai-message', 'Импорт завершён. Данные доступны в списке и практике.'); changed();
    } catch (error) {
      if (error.definite) { pending = null; remember(); if (preview && ['STALE_IMPORT_PREVIEW', 'PREVIEW_EXPIRED', 'IMPORT_ALREADY_CONFIRMED', 'INVALID_IMPORT_SELECTION'].includes(error.code)) { preview.valid = false; selected.clear(); render(); } }
      status('ai-message', error.definite ? error.message : 'Подтверждение могло сохраниться. Выбор заблокирован; повторите то же подтверждение, чтобы узнать результат.', true); $('ai-message').focus();
    } finally { busy = false; controls(); }
  }
  $('ai-upload').addEventListener('submit', async event => {
    event.preventDefault(); if (busy || pending) return; const file = $('ai-file').files[0];
    if (!file) { status('ai-message', 'Выберите JSON-файл.', true); return; }
    busy = true; controls(); status('ai-message', 'Проверяем весь документ без сохранения…');
    try { const form = new FormData(); form.append('file', file); showPreview(await api('/api/aorist-imports/preview', { method: 'POST', form })); }
    catch (error) { status('ai-message', error.message, true); }
    finally { busy = false; controls(); }
  });
  $('ai-previous').addEventListener('click', () => { if (!busy && !pending && page > 0) { page--; render(); } }); $('ai-next').addEventListener('click', () => { if (!busy && !pending && preview && (page + 1) * 10 < preview.entries.length) { page++; render(); } });
  for (const [id, classification] of [['ai-select-new', 'ADD'], ['ai-select-unchanged', 'UNCHANGED'], ['ai-select-none', null]]) $(id).addEventListener('click', () => { if (!busy && !pending && preview?.valid) { selected = new Set(preview.entries.filter(r => r.classification === classification).map(r => r.externalId)); render(); } });
  $('ai-downgrade').addEventListener('change', controls); $('ai-confirm').addEventListener('click', confirm); $('ai-retry').addEventListener('click', confirm);
  $('ae-form').addEventListener('submit', async event => {
    event.preventDefault(); if (exporting) return; exporting = true; controls(); status('ae-message', 'Готовим учебный JSON…');
    try {
      const query = new URLSearchParams({ datasetId: $('ae-dataset').value }); const scope = $('ae-scope').value;
      query.set(scope === 'all' ? 'archiveScope' : 'archived', scope === 'all' ? 'all' : String(scope === 'archived'));
      const response = await request(`/api/aorist-verbs/export?${query}`); const blob = await response.blob();
      const document = JSON.parse(await blob.text()); const url = URL.createObjectURL(blob), a = element('a'); a.href = url; a.download = `${document.datasetId}-aorist.json`; a.hidden = true; window.document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
      status('ae-message', `Файл подготовлен: ${document.entries.length} записей. Сохранён только учебный материал; история и архивный статус не экспортируются.`);
    } catch (error) { status('ae-message', error.code ? error.message : 'Не удалось получить весь файл. Параметры сохранены; повторите скачивание.', true); }
    finally { exporting = false; controls(); }
  });
  if (pending) status('ai-message', 'Осталось неподтверждённое сохранение. Повторите подтверждение, чтобы безопасно узнать результат.', true);
  controls(); return { pending: () => !!pending };
}
