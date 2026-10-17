import { BulgarianPronunciation, createPlaybackControls, mountVoiceSettings, typingBulgarianText } from './pronunciation.js';

const $ = id => document.getElementById(id);
const pronunciation = new BulgarianPronunciation();
const audio = createPlaybackControls(pronunciation);
$('pronunciation-controls').append(audio.element);
mountVoiceSettings(pronunciation, $('voice-settings'));
const base = '/api/typing';
const storageKey = 'bulgariantrainer.typing';
const errors = {
  EMPTY_DICTIONARY: 'Нет активных слов с переводами. Добавьте слова в словарь.',
  EMPTY_REVIEW_QUEUE: 'Сейчас нет слов для повторения. Выберите новые слова или смешанное занятие.',
  EMPTY_NEW_QUEUE: 'Новых слов нет. Выберите повторение или смешанное занятие.',
  SHORTER_SESSION_REQUIRED: 'Словарь изменился. Обновите список и выберите сокращённое занятие.',
  SESSION_NOT_FOUND: 'Занятие не найдено. Начните новое занятие.',
  SESSION_CLOSED: 'Занятие завершено. Обновите состояние, чтобы увидеть результаты.',
  STALE_QUESTION: 'Вопрос изменился. Обновите состояние занятия.',
  ANSWER_CONFLICT: 'На этот вопрос уже сохранён другой ответ. Обновите состояние занятия.',
  ANSWER_REQUIRED: 'Сначала введите и проверьте ответ.',
  START_REQUEST_CHANGED: 'Настройки запроса изменились. Начните новое занятие.',
  VALIDATION_FAILED: 'Введите непустой ответ длиной до 4000 символов.',
  INVALID_REQUEST: 'Проверьте введённый ответ.',
  DATA_CONFLICT: 'Данные изменяются другим запросом. Безопасно повторите запрос.',
};
let state = null, sessionId = null, pending = null, busy = false, availability = null;
function message(text, error = false) {
  $('typing-message').textContent = text;
  $('typing-message').dataset.state = error ? 'error' : 'success';
}
function remember() {
  try { localStorage.setItem(storageKey, JSON.stringify({ sessionId, pending })); } catch { /* Recovery remains available through the session URL. */ }
}
function controls() {
  const frozen = busy || pending !== null;
  $('start').disabled = frozen || !availability?.offeredSize;
  $('answer-input').disabled = frozen || !!state?.currentQuestion?.feedback;
  $('submit').disabled = frozen || !state?.currentQuestion || !!state.currentQuestion.feedback;
  $('next').disabled = frozen;
  $('end').disabled = frozen || state?.status !== 'IN_PROGRESS';
  $('new').disabled = frozen;
  $('retry').hidden = !pending; $('retry').disabled = busy;
  $('training').setAttribute('aria-busy', String(busy));
}
async function api(url, options = {}) {
  let response;
  try { response = await fetch(url, { ...options, headers: { 'Content-Type': 'application/json' }, cache: 'no-store', signal: AbortSignal.timeout(12000) }); }
  catch { throw new Error('Нет связи с сервером. Нажмите «Повторить запрос»: ответ не будет учтён дважды.'); }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const error = new Error(errors[body.code] || 'Не удалось выполнить запрос. Повторите попытку.');
    error.code = body.code; error.definite = response.status < 500 && body.code !== 'DATA_CONFLICT'; throw error;
  }
  return response.json();
}
function showAvailability(data) {
  pronunciation.stop(); audio.setText(null);
  availability = data;
  $('settings').hidden = false; $('summary').hidden = true; $('training').hidden = true;
  $('availability').textContent = data.eligibleWords === 0 ? 'В выбранном режиме пока нет доступных слов.'
    : data.shorterRequired ? `Доступно слов: ${data.eligibleWords}. Предлагаем сокращённое занятие: ${data.offeredSize} вопросов без повторов, по ${Math.floor(data.offeredSize / 2)} и ${Math.ceil(data.offeredSize / 2)} в каждом направлении.`
      : `Доступно слов: ${data.eligibleWords}. В занятии будет 30 вопросов.`;
  $('start').textContent = data.shorterRequired ? `Начать сокращённое занятие (${data.offeredSize})` : 'Начать занятие (30)';
  message(data.eligibleWords ? 'Выберите занятие, когда будете готовы.' : 'Добавьте слова, чтобы начать.');
}
async function display(data) {
  const previousId = state?.currentQuestion?.id;
  state = data; sessionId = data.id; remember();
  history.replaceState(null, '', `/typing.html?session=${sessionId}`);
  $('settings').hidden = true; $('summary').hidden = true;
  const question = data.currentQuestion;
  $('training').hidden = !question;
  if (!question) {
    pronunciation.stop(); audio.setText(null);
    const result = await api(`${base}/sessions/${sessionId}/results`);
    $('summary-title').textContent = data.status === 'ABANDONED' ? 'Занятие завершено досрочно' : 'Занятие завершено';
    $('summary-counts').textContent = `Ответов: ${data.answeredQuestions} из ${data.totalQuestions}. Верно: ${data.correctAnswers}. Ошибок: ${data.answeredQuestions - data.correctAnswers}. Без ответа: ${data.totalQuestions - data.answeredQuestions}.`;
    $('mistakes').replaceChildren(); $('no-mistakes').hidden = result.mistakes.length > 0;
    for (const item of result.mistakes) {
      const li = document.createElement('li');
      li.textContent = `Вопрос ${item.position}. ${direction(item.direction)}: ${item.prompt.join('; ')}. Ваш ответ: ${item.userAnswer}. Принимаются: ${item.acceptedAnswers.join('; ')}.`;
      $('mistakes').append(li);
    }
    $('summary').hidden = false; message('Ответы и результаты сохранены.'); $('summary-title').focus(); return;
  }
  $('question-number').textContent = `Вопрос ${question.position} из ${data.totalQuestions} · Ответов: ${data.answeredQuestions}`;
  $('progress').max = data.totalQuestions; $('progress').value = data.answeredQuestions;
  $('direction').textContent = direction(question.direction);
  $('prompt').textContent = question.prompt.join('; ');
  $('prompt').lang = question.direction === 'BG_TO_RU' ? 'bg' : 'ru';
  $('answer-input').lang = question.direction === 'BG_TO_RU' ? 'ru' : 'bg';
  if (previousId !== question.id) { pronunciation.stop(); $('answer-input').value = ''; }
  audio.setText(typingBulgarianText(question));
  $('feedback').hidden = !question.feedback; $('submit').hidden = !!question.feedback;
  if (question.feedback) {
    $('answer-input').value = question.feedback.userAnswer;
    $('feedback').dataset.result = question.feedback.result;
    $('feedback-title').textContent = question.feedback.result === 'CORRECT' ? 'Верно!' : 'Неверно — запомните перевод';
    $('submitted-answer').textContent = `Ваш ответ: ${question.feedback.userAnswer}`;
    $('accepted').textContent = `Принимаются: ${question.feedback.acceptedAnswers.join('; ')}`;
    $('next').textContent = data.status === 'COMPLETED' ? 'Посмотреть результаты' : 'Следующий вопрос';
  }
  message(question.feedback ? 'Ответ сохранён. Продолжите, когда будете готовы.' : 'Введите перевод из вашего словаря.');
}
function direction(value) { return value === 'BG_TO_RU' ? 'Болгарский → русский' : 'Русский → болгарский'; }
async function execute(operation) {
  if (busy) return;
  pending = operation; remember(); busy = true; controls(); message('Загружаем…');
  try {
    const read = ['availability', 'load'].includes(operation.action);
    const url = operation.action === 'availability' ? `${base}/availability?scope=${encodeURIComponent(operation.scope || $('study-scope').value)}` : operation.action === 'start' ? `${base}/sessions`
      : `${base}/sessions/${operation.sessionId}/${operation.action === 'load' ? 'question' : operation.action}`;
    const data = await api(url, { method: read ? 'GET' : 'POST', ...(operation.body ? { body: JSON.stringify(operation.body) } : {}) });
    pending = null;
    if (operation.action === 'availability') showAvailability(data); else await display(data);
    remember();
  } catch (error) {
    if (error.definite) {
      pending = sessionId ? { action: 'load', sessionId } : { action: 'availability' };
      if (error.code === 'SESSION_NOT_FOUND') {
        state = null; sessionId = null; pending = { action: 'availability' };
        history.replaceState(null, '', '/typing.html'); $('training').hidden = true;
      }
    } else if (!pending) pending = { action: 'load', sessionId };
    message(error.message, true); remember(); $('typing-message').focus();
  } finally {
    busy = false; controls();
    if (!pending && state?.currentQuestion) (state.currentQuestion.feedback ? $('next') : $('answer-input')).focus();
  }
}
$('start').addEventListener('click', () => {
  if (!busy && !pending && availability?.offeredSize) execute({ action: 'start', body: { requestId: crypto.randomUUID(), allowShorter: availability.shorterRequired, scope: $('study-scope').value } });
});
$('study-scope').addEventListener('change', () => { if (!busy && !pending) execute({ action: 'availability', scope: $('study-scope').value }); });
$('answer-form').addEventListener('submit', event => {
  event.preventDefault();
  if (busy || pending || !state?.currentQuestion || state.currentQuestion.feedback) return;
  execute({ action: 'answers', sessionId, body: { questionId: state.currentQuestion.id, answer: $('answer-input').value } });
});
$('next').addEventListener('click', () => { if (!busy && !pending) { pronunciation.stop(); execute({ action: 'next', sessionId, body: { questionId: state.currentQuestion.id } }); } });
$('end').addEventListener('click', () => { if (!busy && !pending) { pronunciation.stop(); execute({ action: 'complete', sessionId }); } });
$('retry').addEventListener('click', () => { if (pending) execute(pending); });
$('new').addEventListener('click', () => {
  pronunciation.stop(); audio.setText(null);
  state = null; sessionId = null; pending = null; remember(); history.replaceState(null, '', '/typing.html'); execute({ action: 'availability' });
});
let saved = null;
try { saved = JSON.parse(localStorage.getItem(storageKey)); } catch { /* Storage is optional. */ }
const requested = new URLSearchParams(location.search).get('session');
if (requested && /^\d+$/.test(requested) && Number.isSafeInteger(Number(requested)) && Number(requested) > 0) sessionId = Number(requested);
else if (Number.isSafeInteger(saved?.sessionId) && saved.sessionId > 0) sessionId = saved.sessionId;
if (saved?.pending && (!requested || String(saved.sessionId) === requested)
  && ['availability','start','load','answers','next','complete'].includes(saved.pending.action)) {
  pending = saved.pending; $('settings').hidden = !!sessionId;
  message('Остался незавершённый запрос. Нажмите «Повторить запрос», чтобы восстановить занятие.', true); controls();
} else execute(sessionId ? { action: 'load', sessionId } : { action: 'availability' });
