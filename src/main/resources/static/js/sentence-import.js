const $ = id => document.getElementById(id);
let preview = null, rows = [], page = 0, busy = false, pendingConfirm = null;
const perPage = 10;
$('choose-sentence-file').addEventListener('click', () => $('sentence-file').click());
$('sentence-file').addEventListener('change', () => { $('selected-file-name').textContent = $('sentence-file').files[0]?.name || 'Файл не выбран'; });
const warningNames = {
  CSV_SYNTAX: 'Ошибка CSV', CSV_COLUMN_COUNT: 'Неверное число столбцов', INVALID_ALTERNATIVES_JSON: 'Неверный JSON с вариантами',
  INVALID_DIFFICULTY: 'Неверная сложность', MISSING_TEXT: 'Нет обязательного текста', MISSING_SOURCE_ID: 'Нет идентификатора источника',
  VALUE_TOO_LONG: 'Текст слишком длинный', DUPLICATE_IN_FILE: 'Повтор в файле', DUPLICATE_IN_DATABASE: 'Уже есть в базе', EMPTY_RECORD: 'Пустая строка',
};
function status(text, error = false) { $('import-message').textContent = text; $('import-message').dataset.state = error ? 'error' : 'success'; }
async function api(url, options = {}) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(15000) }); }
  catch { throw new Error('Нет связи с сервером. Проверьте подключение и повторите запрос.'); }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const map = { PREVIEW_EXPIRED: 'Предпросмотр истёк. Загрузите файл снова.', INVALID_HEADER: 'Заголовок CSV не совпадает с ожидаемыми семью столбцами.', INVALID_UTF8: 'Файл должен быть в кодировке UTF-8.', INVALID_CONFIRMATION: 'Проверьте выбранные записи и исправления.', VALIDATION_FAILED: 'Проверьте длину и заполнение полей.', DUPLICATE_SENTENCE: 'В файле или базе уже есть такое предложение.' };
    const error = new Error(map[body.code] || 'Не удалось выполнить запрос. Проверьте данные и повторите попытку.');
    error.definite = response.status < 500 && body.code !== 'DATA_CONFLICT'; throw error;
  }
  return response.json();
}
function field(label, value, onInput, multiline = false, type = 'text') {
  const wrapper = document.createElement('label'); wrapper.textContent = label;
  const control = multiline ? document.createElement('textarea') : document.createElement('input');
  if (multiline) control.rows = 2; else control.type = type;
  control.value = value ?? ''; control.addEventListener('input', () => onInput(control.value));
  wrapper.append(control); return wrapper;
}
function render() {
  $('import-rows').replaceChildren();
  const count = Math.ceil(rows.length / perPage); page = Math.min(page, Math.max(0, count - 1));
  $('import-page-label').textContent = `Страница ${page + 1} из ${Math.max(1, count)}`;
  $('previous-import-page').disabled = page === 0; $('next-import-page').disabled = page + 1 >= count;
  $('preview-count').textContent = `Найдено строк: ${rows.length}. Выбрано: ${rows.filter(r => r.selected).length}.`;
  for (const row of rows.slice(page * perPage, (page + 1) * perPage)) {
    const entry = row.entry, draft = row.draft;
    const card = document.createElement('article'); card.className = 'sentence-card';
    const heading = document.createElement('h3'); heading.textContent = `Строка ${entry.lineNumber}${entry.endLineNumber !== entry.lineNumber ? `–${entry.endLineNumber}` : ''}, ID источника: ${entry.sourceId || 'не указан'}`;
    const check = document.createElement('input'); check.type = 'checkbox'; check.checked = row.selected; check.disabled = ['HEADER','EMPTY'].includes(entry.status) || busy;
    check.addEventListener('change', () => { row.selected = check.checked; $('preview-count').textContent = `Найдено строк: ${rows.length}. Выбрано: ${rows.filter(r => r.selected).length}.`; });
    const choose = document.createElement('label'); choose.append(check, document.createTextNode(' Сохранить эту запись'));
    const flags = document.createElement('p'); flags.textContent = entry.warnings.map(w => warningNames[w] || w).join(' · ') || (entry.status === 'HEADER' ? 'Заголовок файла' : 'Готово к импорту');
    if (entry.existingSentenceId) flags.textContent += ` · Предложение №${entry.existingSentenceId}`;
    const source = document.createElement('details'); const caption = document.createElement('summary'); caption.textContent = 'Исходная строка CSV'; const raw = document.createElement('pre'); raw.textContent = entry.sourceLine; source.append(caption, raw);
    const grid = document.createElement('div'); grid.className = 'sentence-fields';
    grid.append(field('Русский текст', draft.russianText, v => draft.russianText = v, true),
      field('Болгарский перевод', draft.bulgarianText, v => draft.bulgarianText = v, true),
      field('Другие одобренные переводы, по одному на строку', draft.alternatives, v => draft.alternatives = v, true),
      field('Грамматическое пояснение', draft.explanation, v => draft.explanation = v, true),
      field('Сложность: 1 — базовая, 2 — средняя', draft.difficulty, v => draft.difficulty = v, false, 'number'),
      field('Тема', draft.topic, v => draft.topic = v));
    card.append(heading, choose, flags, grid, source); $('import-rows').append(card);
  }
}
function showPreview(data) {
  preview = data;
  rows = preview.entries.map(entry => ({ entry, selected: entry.status === 'READY', draft: {
    russianText: entry.russianText, bulgarianText: entry.bulgarianText,
    alternatives: entry.alternativeTranslations.join('\n'), explanation: entry.explanation || '',
    difficulty: entry.difficulty ?? '', topic: entry.topic || '' } }));
  page = 0; $('preview').hidden = false; $('import-result').hidden = true; render();
  status(`Предпросмотр готов: ${rows.length} строк. Проверьте записи перед сохранением.`);
}
$('upload-form').addEventListener('submit', async event => {
  event.preventDefault(); if (busy) return;
  const file = $('sentence-file').files[0]; if (!file) { status('Выберите файл CSV.', true); return; }
  busy = true; $('upload-form').querySelector('button').disabled = true; status('Читаем файл и проверяем повторы…');
  try {
    const form = new FormData(); form.append('file', file);
    showPreview(await api('/api/sentence-imports/preview', { method: 'POST', body: form }));
  } catch (error) { status(error.message, true); }
  finally { busy = false; $('upload-form').querySelector('button').disabled = false; }
});
$('preview-project-file').addEventListener('click', async () => {
  if (busy) return;
  busy = true; $('preview-project-file').disabled = true; status('Читаем файл проекта…');
  try { showPreview(await api('/api/sentence-imports/preview-initial', { method: 'POST' })); }
  catch (error) { status(error.message, true); }
  finally { busy = false; $('preview-project-file').disabled = false; }
});
$('previous-import-page').addEventListener('click', () => { if (page > 0) { page--; render(); } });
$('next-import-page').addEventListener('click', () => { if ((page + 1) * perPage < rows.length) { page++; render(); } });
function buildConfirm() {
  return { importId: preview.importId, entries: rows.map(row => ({ rowId: row.entry.rowId, selected: row.selected,
    sentence: row.selected ? { russianText: row.draft.russianText, bulgarianText: row.draft.bulgarianText,
      alternativeTranslations: row.draft.alternatives.split(/\r?\n/).map(x => x.trim()).filter(Boolean),
      explanation: row.draft.explanation || null, difficulty: row.draft.difficulty === '' ? null : Number(row.draft.difficulty),
      topic: row.draft.topic || null, wordIds: [] } : null })) };
}
async function confirm() {
  if (busy || !preview) return;
  if (!pendingConfirm) pendingConfirm = buildConfirm();
  busy = true; $('confirm-import').disabled = true; $('retry-import').hidden = true; status('Сохраняем выбранные предложения…');
  try {
    const result = await api('/api/sentence-imports/confirm', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(pendingConfirm) });
    pendingConfirm = null; $('preview').hidden = true; $('import-result').hidden = false;
    $('import-summary').textContent = `Создано: ${result.created}. Повторы пропущены: ${result.duplicatesSkipped}. Не выбрано: ${result.deselected}.`;
    status('Импорт завершён. Предложения доступны для занятия.');
  } catch (error) {
    if (error.definite) pendingConfirm = null;
    else $('retry-import').hidden = false;
    status(error.message, true); $('import-message').focus();
  } finally { busy = false; $('confirm-import').disabled = false; }
}
$('confirm-import').addEventListener('click', confirm);
$('retry-import').addEventListener('click', confirm);
