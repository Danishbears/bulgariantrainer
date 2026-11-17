export const $ = id => document.getElementById(id);
export const persons = { '1sg': 'аз — я', '2sg': 'ти — ты', '3sg': 'той/тя/то — он/она/оно', '1pl': 'ние — мы', '2pl': 'вие — вы', '3pl': 'те — они' };
export const personCodes = ['1sg', '2sg', '3sg', '1pl', '2pl', '3pl'];
export const aspects = { imperfective: 'Несовершенный вид', perfective: 'Совершенный вид' };
export const types = { TYPED: 'Ввод ответа', MULTIPLE_CHOICE: 'Выбор варианта' };
export const outcomes = { CORRECT: 'Верно', INCORRECT: 'Не совпало', REVEALED: 'Ответ раскрыт', SKIPPED: 'Пропущено' };
const errors = {
  INVALID_DATASET_ID: 'Код набора: 1–100 строчных латинских букв, цифр, точек, дефисов или подчёркиваний; начните с буквы или цифры.',
  INVALID_AORIST_ENTRY: 'Проверьте все шесть списков ответов, обязательные поля и источник. Частица должна сохраняться в словарной форме и каждом ответе.',
  INVALID_AORIST_METADATA: 'Проверьте код, ревизию, дату и примечания нового набора.',
  INVALID_AORIST_REQUEST: 'Проверьте поля, неизменяемые идентификаторы, фильтры и текущую версию записи.',
  INVALID_AORIST_PRACTICE: 'Проверьте параметры занятия и ответ. Выберите хотя бы одно лицо и от 1 до 100 вопросов.',
  VALIDATION_FAILED: 'Проверьте обязательные поля и допустимую длину текста.', INVALID_REQUEST: 'Проверьте поля запроса и параметры.',
  DUPLICATE_AORIST_VERB: 'В этом наборе уже есть запись с таким идентификатором, возможно в архиве.',
  STALE_AORIST_VERB: 'Запись изменилась. Загрузите актуальную версию перед новой правкой.', AORIST_NOT_FOUND: 'Глагол или набор не найден.',
  UNSUPPORTED_FILE: 'Выберите JSON-файл аориста.', FILE_TOO_LARGE: 'Файл превышает 1 МБ.', INVALID_UPLOAD: 'Не удалось прочитать файл. Выберите его снова.',
  INVALID_IMPORT_DOCUMENT: 'В документе есть ошибки. Исправьте файл и создайте новый предпросмотр.', INVALID_IMPORT_SELECTION: 'Выбор не соответствует предпросмотру. Загрузите файл снова.',
  STALE_IMPORT_PREVIEW: 'Данные изменились после предпросмотра. Загрузите файл снова и сравните изменения.', PREVIEW_EXPIRED: 'Предпросмотр истёк или сервер перезапущен. Загрузите файл снова.',
  IMPORT_ALREADY_CONFIRMED: 'Этот предпросмотр уже подтверждён. Обновите список.', IMPORT_REQUEST_CHANGED: 'Подтверждение с этим кодом уже отправлялось с другим выбором.',
  REVISION_DOWNGRADE_CONFIRMATION_REQUIRED: 'Для более ранней ревизии отметьте отдельное согласие на показанное понижение.',
  EMPTY_AORIST_EXPORT: 'В выбранном наборе и архивном состоянии нет записей для экспорта.', EXPORT_TOO_LARGE: 'Набор слишком велик для одного поддерживаемого JSON-файла.',
  EMPTY_AORIST_QUEUE: 'Нет доступных вопросов. Импортируйте набор или измените фильтры.', EMPTY_REVIEW_QUEUE: 'Сейчас нет вопросов с наступившим сроком повторения.', EMPTY_NEW_QUEUE: 'В этом типе упражнения нет новых вопросов для выбранных фильтров.',
  CHOICE_OPTIONS_UNAVAILABLE: 'Не хватает разных вариантов для выбора. Переключитесь на ввод ответа или измените фильтры.', SHORTER_SESSION_REQUIRED: 'Доступность изменилась. Проверьте очередь и примите сокращённое занятие.',
  SESSION_NOT_FOUND: 'Занятие не найдено. Можно начать новое.', SESSION_CLOSED: 'Занятие завершено. Восстановите результаты.', START_REQUEST_CHANGED: 'Для новых параметров нужно новое занятие.',
  ANSWER_CONFLICT: 'На вопрос уже сохранён другой ответ. Восстановите состояние.', ANSWER_REVEALED: 'Ответ уже раскрыт и не может получить самостоятельный зачёт.', ANSWER_ALREADY_SUBMITTED: 'Ответ уже сохранён. Восстановите обратную связь.',
  STALE_QUESTION: 'Текущий вопрос изменился. Восстановите занятие.', ANSWER_REQUIRED: 'Сначала проверьте или раскройте ответ.', SESSION_NOT_COMPLETED: 'Сначала завершите занятие.', DATA_CONFLICT: 'Данные меняются другим запросом. Проверьте сохранение и повторите.',
};
export async function request(url, { body, method = 'GET', form } = {}) {
  let response;
  try { response = await fetch(url, { method, cache: 'no-store', signal: AbortSignal.timeout(12000),
    ...(form ? { body: form } : body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) }); }
  catch { throw new Error('Нет связи с сервером. Введённые данные сохранены. Повторите запрос.'); }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const error = new Error(errors[data.code] || 'Не удалось выполнить запрос. Проверьте связь и повторите попытку.');
    error.code = data.code; error.definite = response.status < 500 && data.code !== 'DATA_CONFLICT'; throw error;
  }
  return response;
}
export async function api(url, options) {
  const response = await request(url, options);
  try { return await response.json(); } catch { throw new Error('Ответ сервера получен не полностью. Повторите тот же запрос.'); }
}
export function element(tag, text, className) { const e = document.createElement(tag); if (text !== undefined) e.textContent = text; if (className) e.className = className; return e; }
export function button(text, action) { const b = element('button', text, 'secondary'); b.type = 'button'; b.addEventListener('click', action); return b; }
export function status(id, text, error = false) { $(id).textContent = text; $(id).dataset.state = error ? 'error' : 'success'; }
export function load(key) { try { return JSON.parse(localStorage.getItem(key)); } catch { return null; } }
export function save(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { $('aorist-storage-note').hidden = false; } }
export function positiveId(value) { const n = Number(value); return /^\d+$/.test(String(value)) && Number.isSafeInteger(n) && n > 0 ? n : null; }
export function equal(a, b) { const canonical = x => Array.isArray(x) ? x.map(canonical) : x && typeof x === 'object' ? Object.fromEntries(Object.keys(x).sort().filter(k => x[k] != null).map(k => [k, canonical(x[k])])) : x; return JSON.stringify(canonical(a)) === JSON.stringify(canonical(b)); }
export function table(forms, caption = 'Аорист — допустимые формы') {
  const t = element('table', undefined, 'aorist-table'); t.append(element('caption', caption));
  const head = element('thead'), tr = element('tr'); for (const title of ['Лицо', 'Допустимые ответы']) { const th = element('th', title); th.scope = 'col'; tr.append(th); } head.append(tr); t.append(head);
  const body = element('tbody'); for (const p of personCodes) {
    const row = element('tr'), label = element('th', persons[p]); label.scope = 'row';
    const answers = element('td', (forms?.[p] || []).join('; ')); answers.lang = 'bg'; row.append(label, answers); body.append(row);
  } t.append(body); return t;
}
export function source(root, value) {
  if (!value) return; const p = element('p', undefined, 'note');
  let url; try { url = new URL(value.url); } catch { url = null; }
  if (url && ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password) { const link = element('a', value.title); link.href = url.href; link.target = '_blank'; link.rel = 'noopener noreferrer'; p.append('Источник: ', link); }
  else p.append(`Источник: ${value.title || ''}`);
  p.append(`. Раздел: ${value.section || ''}. Дата в материале: ${value.checkedOn || ''}.`); root.append(p);
}
export function content(root, entry) {
  const title = element('p', entry.lemma); title.lang = 'bg'; root.append(title, element('p', entry.russian), element('p', `${aspects[entry.aspect] || 'Вид указан в наборе'} · Группа: ${entry.studyGroup}`, 'note'), table(entry.forms));
  if (entry.note) root.append(element('p', entry.note, 'aorist-note')); source(root, entry.source);
}
