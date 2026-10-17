import { BulgarianPronunciation, createPlaybackControls, flashcardBulgarianText, mountVoiceSettings } from './pronunciation.js';

const $ = id => document.getElementById(id);
const pronunciation = new BulgarianPronunciation();
const audio = createPlaybackControls(pronunciation);
$('pronunciation-controls').append(audio.element);
mountVoiceSettings(pronunciation, $('voice-settings'));
const base = '/api/flashcards/sessions';
const storageKey = 'bulgariantrainer.flashcards';
const errors = {
  EMPTY_DICTIONARY: 'Нет активных слов с переводами. Добавьте слова или восстановите их из архива.',
  EMPTY_REVIEW_QUEUE: 'Сейчас нет слов для повторения. Выберите новые слова или смешанное занятие.',
  EMPTY_NEW_QUEUE: 'Новых слов нет. Выберите повторение или смешанное занятие.',
  SESSION_NOT_FOUND: 'Занятие не найдено. Возможно, используется другая база данных. Начните новое занятие.',
  SESSION_CLOSED: 'Это занятие уже завершено. Обновите состояние, чтобы увидеть результат.',
  STALE_QUESTION: 'Текущая карточка изменилась. Обновите состояние занятия.',
  RATING_CONFLICT: 'Для этой карточки уже сохранена другая оценка. Обновите состояние занятия.',
  ANSWER_HIDDEN: 'Сначала откройте ответ.',
  START_REQUEST_CHANGED: 'Настройки запроса изменились. Начните новое занятие.',
  VALIDATION_FAILED: 'Проверьте настройки: количество слов должно быть от 1 до 100.',
  INVALID_REQUEST: 'Не удалось прочитать запрос. Проверьте настройки занятия.',
  DATA_CONFLICT: 'Другой запрос изменяет занятие. Повторите запрос — оценка не будет учтена дважды.',
};
let state = null, busy = false, pending = null, sessionId = null, previousQuestion = null;

function message(text, error = false) {
  $('flash-message').textContent = text;
  $('flash-message').dataset.state = error ? 'error' : 'success';
}
function remember() {
  try { localStorage.setItem(storageKey, JSON.stringify({ sessionId, pending })); } catch { /* URL and server state still allow session recovery. */ }
}
function controls() {
  const frozen = busy || pending !== null;
  $('settings-fields').disabled = frozen;
  $('new-session').disabled = frozen;
  $('reveal').disabled = frozen || !state?.currentQuestion || state.currentQuestion.revealed;
  $('known').disabled = frozen || !state?.currentQuestion?.revealed;
  $('unknown').disabled = $('known').disabled;
  $('end-session').disabled = frozen || !state?.currentQuestion;
  $('retry-action').hidden = pending === null;
  $('retry-action').disabled = busy;
  $('training').setAttribute('aria-busy', String(busy));
}
async function api(url, options = {}) {
  let response;
  try {
    response = await fetch(url, { ...options, headers: { 'Content-Type': 'application/json' }, cache: 'no-store', signal: AbortSignal.timeout(12000) });
  } catch { throw new Error('Нет связи с сервером. Нажмите «Повторить запрос». Повтор безопасен и не удвоит оценку.'); }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const error = new Error(errors[body.code] || 'Не удалось выполнить запрос. Повторите попытку.');
    error.code = body.code; error.definite = response.status >= 400 && response.status < 500 && body.code !== 'DATA_CONFLICT';
    throw error;
  }
  return response.json();
}
function lines(target, values) {
  target.replaceChildren();
  for (const value of values) { const line = document.createElement('p'); line.textContent = value; target.append(line); }
}
async function display(data) {
  state = data; sessionId = data.id;
  history.replaceState(null, '', `/flashcards.html?session=${sessionId}`);
  remember();
  $('settings').hidden = true; $('empty-dictionary').hidden = true; $('summary').hidden = true;
  $('training').hidden = data.status !== 'IN_PROGRESS';
  $('new-session').hidden = data.status === 'IN_PROGRESS';
  if (data.status !== 'IN_PROGRESS') {
    pronunciation.stop(); audio.setText(null);
    message('Загружаем результаты…');
    const result = await api(`${base}/${sessionId}/results`);
    $('summary-title').textContent = result.session.status === 'ABANDONED' ? 'Занятие завершено досрочно' : 'Занятие завершено';
    $('summary-description').textContent = `Оценено карточек: ${result.session.answeredQuestions} из ${result.session.totalQuestions}. Результаты сохранены.`;
    $('known-count').textContent = result.session.knownCount;
    $('unknown-count').textContent = result.session.unknownCount;
    $('unrated-count').textContent = result.session.remainingQuestions;
    $('result-list').replaceChildren();
    const ratings = { CORRECT: 'Знаю', INCORRECT: 'Не знаю', SKIPPED: 'Без оценки' };
    for (const item of result.items) {
      const li = document.createElement('li');
      li.textContent = `${item.bulgarianText} — ${item.russianTexts.join('; ')} · ${ratings[item.result]}`;
      $('result-list').append(li);
    }
    $('summary').hidden = false; message('Занятие сохранено.'); $('summary-title').focus();
    return;
  }
  const question = data.currentQuestion;
  $('session-progress').textContent = `Карточка ${question.position} из ${data.totalQuestions} · Оценено: ${data.answeredQuestions}`;
  $('progress').max = data.totalQuestions; $('progress').value = data.answeredQuestions;
  $('card-direction').textContent = question.direction === 'BG_TO_RU' ? 'Болгарский → русский' : 'Русский → болгарский';
  $('prompt').lang = question.direction === 'BG_TO_RU' ? 'bg' : 'ru';
  $('answer').lang = question.direction === 'BG_TO_RU' ? 'ru' : 'bg';
  const changed = previousQuestion !== question.id;
  if (changed) pronunciation.stop();
  if (changed) $('flashcard').classList.add('resetting');
  lines($('prompt'), question.prompt); lines($('answer'), question.answer);
  $('flashcard').classList.toggle('revealed', question.revealed);
  $('card-front').setAttribute('aria-hidden', String(question.revealed));
  $('card-back').setAttribute('aria-hidden', String(!question.revealed));
  $('reveal').hidden = question.revealed;
  if (changed) { void $('flashcard').offsetWidth; $('flashcard').classList.remove('resetting'); }
  previousQuestion = question.id;
  audio.setText(flashcardBulgarianText(question));
  message(question.revealed ? 'Оцените, удалось ли вспомнить перевод.' : 'Вспомните перевод и откройте ответ.');
}
async function execute(operation) {
  if (busy) return;
  pending = operation; remember(); busy = true; controls();
  message(operation.action === 'start' ? 'Готовим карточки…' : 'Загружаем состояние занятия…');
  try {
    const url = operation.action === 'start' ? base : `${base}/${operation.sessionId}/${operation.action === 'load' ? 'question' : operation.action}`;
    const data = await api(url, { method: operation.action === 'load' ? 'GET' : 'POST', ...(operation.body ? { body: JSON.stringify(operation.body) } : {}) });
    pending = null; remember();
    await display(data);
  } catch (error) {
    if (error.definite) {
      pending = null;
      if (error.code === 'EMPTY_DICTIONARY') $('empty-dictionary').hidden = false;
      else if (sessionId && error.code !== 'SESSION_NOT_FOUND') pending = { action: 'load', sessionId };
      if (error.code === 'SESSION_NOT_FOUND') {
        state = null; sessionId = null; $('training').hidden = true; $('settings').hidden = false;
        history.replaceState(null, '', '/flashcards.html');
      }
    } else if (pending === null) pending = { action: 'load', sessionId };
    message(error.message, true); remember(); $('flash-message').focus();
  } finally {
    busy = false; controls();
    if (!pending && state?.status === 'IN_PROGRESS') (state.currentQuestion.revealed ? $('unknown') : $('reveal')).focus();
  }
}
$('start-form').addEventListener('submit', event => {
  event.preventDefault(); if (busy || pending) return;
  execute({ action: 'start', body: { requestId: crypto.randomUUID(), size: Number($('session-size').value), direction: $('direction').value, scope: $('study-scope').value } });
});
function reveal() {
  if (busy || pending || !state?.currentQuestion || state.currentQuestion.revealed) return;
  execute({ action: 'reveal', sessionId, body: { questionId: state.currentQuestion.id } });
}
function rate(known) {
  if (busy || pending || !state?.currentQuestion?.revealed) return;
  pronunciation.stop();
  execute({ action: 'ratings', sessionId, body: { questionId: state.currentQuestion.id, known } });
}
$('reveal').addEventListener('click', reveal);
$('unknown').addEventListener('click', () => rate(false));
$('known').addEventListener('click', () => rate(true));
$('end-session').addEventListener('click', () => { if (!busy && !pending) { pronunciation.stop(); execute({ action: 'complete', sessionId }); } });
$('retry-action').addEventListener('click', () => { if (pending) execute(pending); });
$('new-session').addEventListener('click', () => {
  pronunciation.stop(); audio.setText(null);
  state = null; sessionId = null; pending = null; previousQuestion = null; remember();
  history.replaceState(null, '', '/flashcards.html');
  $('summary').hidden = true; $('new-session').hidden = true; $('settings').hidden = false;
  message(''); controls(); $('direction').focus();
});
document.addEventListener('keydown', event => {
  if ($('training').hidden || busy || pending || event.altKey || event.ctrlKey || event.metaKey || event.target.closest('button,input,select,textarea,a,[contenteditable=true],summary,.site-sidebar')) return;
  if (event.code === 'Space' || event.key === '1' || event.key === '2') {
    event.preventDefault();
    if (event.repeat || busy || pending) return;
    if (event.code === 'Space') reveal();
    else rate(event.key === '2');
  }
});
let stored = null;
try { stored = JSON.parse(localStorage.getItem(storageKey)); } catch { /* Invalid or disabled storage is optional. */ }
const requestedSession = new URLSearchParams(location.search).get('session');
if (requestedSession && /^\d+$/.test(requestedSession) && Number(requestedSession) > 0) sessionId = Number(requestedSession);
else if (Number.isSafeInteger(stored?.sessionId) && stored.sessionId > 0) sessionId = stored.sessionId;
if (stored?.pending && (!requestedSession || String(stored.sessionId) === requestedSession)
    && ['start','load','reveal','ratings','complete'].includes(stored.pending.action)) {
  pending = stored.pending;
  $('settings').hidden = sessionId !== null;
  message('Остался незавершённый запрос. Нажмите «Повторить запрос», чтобы безопасно восстановить состояние.', true);
  controls();
} else if (sessionId) execute({ action: 'load', sessionId });
else controls();
