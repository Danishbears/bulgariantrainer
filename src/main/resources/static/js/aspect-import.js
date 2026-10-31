import { $, api, status, element, content, load, save } from './aspect-common.js';

const storageKey = 'bulgariantrainer.aspect-pairs.import';
const classifications = { ADD: 'Новая запись', UPDATE: 'Изменение существующей записи', UNCHANGED: 'Без изменений' };
const outcomes = { CREATED: 'Добавлено', UPDATED: 'Обновлено', UNCHANGED: 'Без изменений', DESELECTED: 'Не выбрано' };
const fieldNames = { format: 'формат', formatVersion: 'версия формата', language: 'язык', translationLanguage: 'язык перевода',
  id: 'идентификатор', imperfective: 'несовершенный вид', perfective: 'совершенный вид', russianImperfective: 'русское значение НСВ', russianPerfective: 'русское значение СВ',
  acceptedImperfective: 'допустимые ответы НСВ', acceptedPerfective: 'допустимые ответы СВ', studyStage: 'этап', relationType: 'связь форм', topic: 'тема',
  note: 'пояснение', examples: 'примеры', provenance: 'происхождение', sourceId: 'код источника', tableRow: 'строка источника', entries: 'записи', sources: 'источники', bg: 'болгарский текст', ru: 'русский текст' };
const validationMessages = { UNSUPPORTED_FORMAT: 'Не поддерживается формат файла видовых пар.', UNSUPPORTED_FORMAT_VERSION: 'Поддерживается только версия формата 1.',
  INVALID_JSON: 'Некорректный JSON: проверьте синтаксис, повторяющиеся ключи и лишний текст после документа.', INVALID_UTF8: 'Файл должен быть в кодировке UTF-8.',
  DUPLICATE_EXTERNAL_ID: 'Повторяется идентификатор записи.', FILE_TOO_LARGE: 'Файл превышает 1 МБ.', INVALID_DOCUMENT: 'В корне файла ожидается объект JSON.' };
export function mountImport(changed) {
  let preview = null, selected = new Set(), page = 0, busy = false, pending = load(storageKey)?.pending || null;
  if (!pending?.requestId || !pending?.importId || !Array.isArray(pending.entries)) pending = null;
  const perPage = 10;
  const remember = () => save(storageKey, { pending });
  function controls() {
    const frozen = busy || !!pending;
    $('aspect-upload-fields').disabled = frozen; $('aspect-preview-fields').disabled = frozen;
    $('aspect-confirm').disabled = frozen || !preview?.valid || !selected.size;
    $('aspect-import-retry').hidden = !pending; $('aspect-import-retry').disabled = busy;
    $('aspect-preview-previous').disabled = frozen || page === 0;
    $('aspect-preview-next').disabled = frozen || !preview || (page + 1) * perPage >= preview.entries.length;
    $('aspect-preview').setAttribute('aria-busy', String(busy));
  }
  function selectionCount() {
    $('aspect-preview-count').textContent = `Записей: ${preview.entries.length}. Новых: ${preview.entries.filter(r => r.classification === 'ADD').length}. Изменений: ${preview.entries.filter(r => r.classification === 'UPDATE').length}. Без изменений: ${preview.entries.filter(r => r.classification === 'UNCHANGED').length}. Выбрано: ${selected.size}.`;
    controls();
  }
  function render() {
    if (!preview) return;
    $('aspect-preview-rows').replaceChildren();
    const pages = Math.ceil(preview.entries.length / perPage); page = Math.min(page, Math.max(0, pages - 1));
    $('aspect-preview-page').textContent = `Страница ${pages ? page + 1 : 0} из ${pages}`;
    for (const row of preview.entries.slice(page * perPage, (page + 1) * perPage)) {
      const card = element('article');
      card.append(element('h4', `${row.externalId} · ${classifications[row.classification] || 'Проверьте запись'}`));
      const check = document.createElement('input'); check.type = 'checkbox'; check.checked = selected.has(row.externalId); check.disabled = !preview.valid;
      check.addEventListener('change', () => { check.checked ? selected.add(row.externalId) : selected.delete(row.externalId); selectionCount(); });
      const label = element('label', undefined, 'aspect-selection'); label.append(check, document.createTextNode(`Сохранить ${row.externalId}`)); card.append(label);
      if (row.classification === 'UPDATE') {
        const diff = element('div', undefined, 'aspect-diff');
        for (const [title, e] of [['Было', row.before.entry], ['Будет', row.after]]) {
          const column = element('div'); column.append(element('strong', title)); content(column, e); diff.append(column);
        }
        card.append(diff);
      } else {
        card.append(element('p', `${row.after.imperfective} → ${row.after.perfective} · ${row.after.russianImperfective} → ${row.after.russianPerfective}`));
        const detail = element('details'); detail.append(element('summary', 'Все учебные поля')); const full = element('div'); content(full, row.after); detail.append(full); card.append(detail);
      }
      $('aspect-preview-rows').append(card);
    }
    selectionCount();
  }
  function showPreview(data) {
    preview = data; page = 0;
    selected = new Set(data.valid ? data.entries.filter(row => row.classification === 'ADD').map(row => row.externalId) : []);
    $('aspect-preview').hidden = false; $('aspect-import-result').hidden = true;
    $('aspect-editorial-note').textContent = data.metadata?.editorialStatus || '';
    $('aspect-import-errors').replaceChildren();
    if (data.errors.length) {
      $('aspect-import-errors').append(element('h4', 'В файле есть ошибки. Ни одна запись не будет сохранена.'));
      const list = element('ul');
      for (const error of data.errors) {
        const index = /entries\[(\d+)\]/.exec(error.path);
        const names = (error.path.match(/[A-Za-z]+/g) || []).filter(name => fieldNames[name]).map(name => fieldNames[name]);
        list.append(element('li', `${index ? `Запись ${Number(index[1]) + 1}: ` : ''}${names.join(' → ')}. ${validationMessages[error.code] || 'Проверьте структуру, заполнение и допустимые значения этого поля.'}`));
      }
      $('aspect-import-errors').append(list);
    }
    render(); status('aspect-import-message', data.valid ? 'Предпросмотр готов. Обновления выберите явно после сравнения.' : 'Документ не прошёл проверку. Исправьте исходный файл и выберите его снова.', !data.valid);
  }
  async function confirm() {
    if (busy || (!pending && (!preview?.valid || !selected.size))) return;
    if (!pending) pending = { importId: preview.importId, requestId: crypto.randomUUID(), entries: preview.entries.filter(row => selected.has(row.externalId))
      .map(row => ({ externalId: row.externalId, expectedVersion: row.expectedVersion })) };
    remember(); busy = true; controls(); status('aspect-import-message', 'Сохраняем выбранные записи…');
    try {
      const result = await api('/api/aspect-pair-imports/confirm', { method: 'POST', body: pending });
      pending = null; remember(); preview = null; selected.clear();
      $('aspect-preview').hidden = true; $('aspect-preview-rows').replaceChildren(); $('aspect-import-result').hidden = false;
      $('aspect-import-summary').textContent = `Добавлено: ${result.created}. Обновлено: ${result.updated}. Без изменений: ${result.unchanged}. Не выбрано: ${result.deselected}.`;
      $('aspect-import-outcomes').replaceChildren(...result.entries.map(row => element('li', `${row.externalId}: ${outcomes[row.outcome] || 'Обработано'}`)));
      status('aspect-import-message', 'Импорт завершён. Пары доступны в списке и для занятий.'); changed();
    } catch (error) {
      if (error.definite) {
        pending = null; remember();
        if (preview && ['STALE_IMPORT_PREVIEW', 'PREVIEW_EXPIRED', 'IMPORT_ALREADY_CONFIRMED', 'INVALID_IMPORT_SELECTION'].includes(error.code)) {
          preview.valid = false; selected.clear(); render();
        }
      }
      status('aspect-import-message', error.definite ? error.message : 'Подтверждение отправлено, но ответ сервера не получен. Выбор заблокирован: повторите то же подтверждение, чтобы узнать результат.', true);
      $('aspect-import-message').focus();
    } finally { busy = false; controls(); }
  }
  $('aspect-choose-file').addEventListener('click', () => $('aspect-file').click());
  $('aspect-file').addEventListener('change', () => { $('aspect-file-name').textContent = $('aspect-file').files[0]?.name || 'Файл не выбран'; });
  $('aspect-upload-form').addEventListener('submit', async event => {
    event.preventDefault(); if (busy || pending) return;
    const file = $('aspect-file').files[0];
    if (!file) { status('aspect-import-message', 'Выберите JSON-файл.', true); return; }
    busy = true; controls(); status('aspect-import-message', 'Проверяем весь документ без сохранения…');
    try {
      const form = new FormData(); form.append('namespace', $('aspect-import-namespace').value); form.append('file', file);
      showPreview(await api('/api/aspect-pair-imports/preview', { method: 'POST', form }));
    } catch (error) { status('aspect-import-message', error.message, true); }
    finally { busy = false; controls(); }
  });
  $('aspect-preview-previous').addEventListener('click', () => { if (!busy && !pending && page > 0) { page--; render(); } });
  $('aspect-preview-next').addEventListener('click', () => { if (!busy && !pending && preview && (page + 1) * perPage < preview.entries.length) { page++; render(); } });
  $('aspect-select-new').addEventListener('click', () => { if (!busy && !pending && preview?.valid) { selected = new Set(preview.entries.filter(row => row.classification === 'ADD').map(row => row.externalId)); render(); } });
  $('aspect-select-none').addEventListener('click', () => { if (!busy && !pending) { selected.clear(); render(); } });
  $('aspect-confirm').addEventListener('click', confirm); $('aspect-import-retry').addEventListener('click', confirm);
  if (pending) status('aspect-import-message', 'Сохранилось незавершённое подтверждение. Нажмите «Повторить подтверждение», чтобы безопасно узнать результат.', true);
  controls(); return { pending: () => !!pending };
}
