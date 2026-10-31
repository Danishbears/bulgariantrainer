export const $ = id => document.getElementById(id);
export const relations = { core: 'Основная пара', resultative: 'Результат действия', bounded: 'Ограниченное действие' };
export const outcomes = { CORRECT: 'Верно', INCORRECT: 'Не совпало с ответами этой карточки', REVEALED: 'Ответ раскрыт', SKIPPED: 'Без ответа' };
const errors = {
  INVALID_NAMESPACE: 'Код набора: до 100 строчных латинских букв, цифр, точек, дефисов или подчёркиваний. Начните с буквы или цифры.',
  INVALID_ASPECT_PAIR: 'Проверьте поля пары. Основные формы должны входить в свои списки ответов; примеры заполняются для обоих видов с переводами.',
  DUPLICATE_ASPECT_PAIR: 'В этом наборе уже есть запись с таким идентификатором, возможно в архиве.',
  STALE_ASPECT_PAIR: 'Запись изменилась. Загрузите актуальную версию перед повторным сохранением.',
  ASPECT_PAIR_NOT_FOUND: 'Пара не найдена. Обновите список.',
  INVALID_REQUEST: 'Проверьте заполнение полей, этапы 1–3 и число вопросов от 1 до 100.',
  VALIDATION_FAILED: 'Проверьте обязательные поля, длину текста и допустимые значения.',
  UNSUPPORTED_FILE: 'Выберите файл JSON с видовыми парами.', FILE_TOO_LARGE: 'Файл превышает 1 МБ.',
  INVALID_UPLOAD: 'Не удалось прочитать файл. Выберите его снова.',
  INVALID_IMPORT_DOCUMENT: 'В документе есть ошибки. Исправьте исходный файл и создайте новый предпросмотр.',
  INVALID_IMPORT_SELECTION: 'Выбор записей не соответствует предпросмотру. Загрузите файл снова.',
  STALE_IMPORT_PREVIEW: 'Пары изменились после предпросмотра. Загрузите файл снова и проверьте изменения.',
  PREVIEW_EXPIRED: 'Предпросмотр истёк или сервер перезапущен. Загрузите файл снова.',
  IMPORT_ALREADY_CONFIRMED: 'Этот предпросмотр уже подтверждён. Обновите список пар.',
  IMPORT_REQUEST_CHANGED: 'Это подтверждение уже отправлялось с другим выбором. Загрузите файл снова.',
  EMPTY_ASPECT_PAIRS: 'Нет доступных пар. Импортируйте набор или измените фильтры.',
  EMPTY_NEW_QUEUE: 'В выбранном направлении нет новых пар. Измените очередь или фильтры.',
  EMPTY_REVIEW_QUEUE: 'Сейчас нет пар с наступившим сроком повторения. Выберите другую очередь.',
  SHORTER_SESSION_REQUIRED: 'Доступных пар стало меньше. Ещё раз проверьте доступность и выберите сокращённое занятие.',
  SESSION_NOT_FOUND: 'Занятие не найдено. Можно начать новое.', SESSION_CLOSED: 'Занятие завершено. Восстановите его состояние для просмотра результатов.',
  START_REQUEST_CHANGED: 'Параметры уже отправленного занятия изменились. Проверьте доступность для нового занятия.',
  ANSWER_CONFLICT: 'На этот вопрос сохранён другой ответ. Восстановите состояние занятия.',
  ANSWER_REVEALED: 'Ответ уже раскрыт и не может быть засчитан как самостоятельный.',
  ANSWER_ALREADY_SUBMITTED: 'Ответ уже сохранён. Восстановите обратную связь.',
  STALE_QUESTION: 'Текущий вопрос изменился. Восстановите занятие.', ANSWER_REQUIRED: 'Сначала проверьте или раскройте ответ.',
  SESSION_NOT_COMPLETED: 'Сначала завершите занятие.', DATA_CONFLICT: 'Данные меняются другим запросом. Повторите проверку.',
};
export async function api(url, { body, method = 'GET', form } = {}) {
  let response;
  try {
    response = await fetch(url, { method, cache: 'no-store', signal: AbortSignal.timeout(12000),
      ...(form ? { body: form } : body !== undefined ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}) });
  } catch {
    throw new Error('Нет связи с сервером. Введённые данные сохранены на странице. Повторите запрос.');
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const error = new Error(errors[data.code] || 'Не удалось выполнить запрос. Проверьте связь и повторите попытку.');
    error.code = data.code; error.definite = response.status < 500 && data.code !== 'DATA_CONFLICT';
    throw error;
  }
  return response.json();
}
export function status(id, text, error = false) {
  $(id).textContent = text; $(id).dataset.state = error ? 'error' : 'success';
}
export function element(tag, text, className) {
  const node = document.createElement(tag); if (text !== undefined) node.textContent = text;
  if (className) node.className = className; return node;
}
export function button(text, action) {
  const node = element('button', text, 'secondary'); node.type = 'button'; node.addEventListener('click', action); return node;
}
export function content(root, entry) {
  root.classList.add('aspect-content');
  const bg = element('p', `${entry.imperfective} → ${entry.perfective}`); bg.lang = 'bg'; root.append(bg);
  root.append(element('p', `НСВ: ${entry.russianImperfective}. СВ: ${entry.russianPerfective}.`),
    element('p', `Допустимые НСВ: ${entry.acceptedImperfective.join('; ')}`),
    element('p', `Допустимые СВ: ${entry.acceptedPerfective.join('; ')}`),
    element('p', `${entry.topic} · Этап ${entry.studyStage} · ${relations[entry.relationType] || 'Связь форм'}`, 'note'));
  if (entry.note) root.append(element('p', entry.note));
  if (entry.examples) for (const [side, label] of [['imperfective', 'Пример НСВ'], ['perfective', 'Пример СВ']]) {
    const example = entry.examples[side]; if (!example) continue;
    const box = element('div', undefined, 'aspect-example'); const text = element('p', example.bg); text.lang = 'bg';
    box.append(element('strong', label), text, element('p', example.ru)); root.append(box);
  }
}
export function load(key) { try { return JSON.parse(localStorage.getItem(key)); } catch { return null; } }
export function save(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { $('aspect-storage-note').hidden = false; }
}
export function equalContent(a, b) {
  const canonical = value => Array.isArray(value) ? value.map(canonical) : value && typeof value === 'object'
    ? Object.fromEntries(Object.keys(value).sort().filter(key => value[key] != null).map(key => [key, canonical(value[key])])) : value;
  return JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));
}
export function positiveId(value) { const n = Number(value); return /^\d+$/.test(String(value)) && Number.isSafeInteger(n) && n > 0 ? n : null; }
