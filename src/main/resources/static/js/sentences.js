import { BulgarianPronunciation, createPlaybackControls, mountVoiceSettings, sentenceBulgarianTexts } from './pronunciation.js';

const $ = id => document.getElementById(id);
const pronunciation = new BulgarianPronunciation();
const sentenceAudio = [];
mountVoiceSettings(pronunciation, $('voice-settings'));
function clearSentenceAudio() {
  pronunciation.stop();
  for (const control of sentenceAudio.splice(0)) control.destroy();
  $('sentence-pronunciation').replaceChildren();
}
function showSentenceAudio(question) {
  clearSentenceAudio();
  sentenceBulgarianTexts(question).forEach((text, index) => {
    const row = document.createElement('div'); row.className = 'pronunciation-variant';
    const description = document.createElement('p'); description.lang = 'bg'; description.textContent = text;
    const control = createPlaybackControls(pronunciation, { text, label: index === 0 ? 'Основной перевод' : `Вариант ${index}` });
    sentenceAudio.push(control); row.append(description, control.element); $('sentence-pronunciation').append(row);
  });
}
const base = '/api/sentence-practice/sessions';
const storageKey = 'bulgariantrainer.sentences';
const errorText = {
  EMPTY_SENTENCES: 'Нет одобренных предложений. Добавьте предложение или подтвердите импорт CSV.',
  EMPTY_REVIEW_QUEUE: 'Сейчас нет предложений для повторения. Выберите новые предложения или смешанное занятие.',
  EMPTY_NEW_QUEUE: 'Новых предложений нет. Выберите повторение или смешанное занятие.',
  SESSION_NOT_FOUND: 'Занятие не найдено. Начните новое занятие.',
  SESSION_CLOSED: 'Занятие завершено. Обновите состояние.',
  STALE_QUESTION: 'Вопрос изменился. Обновите занятие.',
  ANSWER_CONFLICT: 'Другой ответ уже сохранён. Обновите занятие.',
  EVALUATION_CONFLICT: 'Оценка уже сохранена. Обновите занятие.',
  EVALUATION_REQUIRED: 'Сначала завершите самооценку.',
  DUPLICATE_SENTENCE: 'Такое русское предложение уже есть. Измените существующую запись.',
  VALIDATION_FAILED: 'Проверьте обязательные поля и длину текста.',
  INVALID_SENTENCE: 'Проверьте предложение, варианты перевода и связанные слова.',
};
async function api(url, options = {}) {
  let response;
  try { response = await fetch(url, { ...options, cache: 'no-store', headers: { 'Content-Type': 'application/json' }, signal: AbortSignal.timeout(12000) }); }
  catch { throw new Error('Нет связи с сервером. Повторите запрос, когда связь восстановится.'); }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const error = new Error(errorText[body.code] || 'Не удалось выполнить запрос. Повторите попытку.');
    error.definite = response.status < 500 && body.code !== 'DATA_CONFLICT';
    error.code = body.code;
    throw error;
  }
  return response.json();
}
function status(id, text, error = false) { $(id).textContent = text; $(id).dataset.state = error ? 'error' : 'success'; }
function button(text, onClick, secondary = true) { const b = document.createElement('button'); b.type = 'button'; b.textContent = text; if (secondary) b.className = 'secondary'; b.addEventListener('click', onClick); return b; }
let state = null, sessionId = null, pending = null, busy = false;
function remember() { try { localStorage.setItem(storageKey, JSON.stringify({ sessionId, pending })); } catch { /* The URL still allows recovery. */ } }
function controls() {
  const frozen = busy || !!pending;
  $('start-form').querySelector('button').disabled = frozen;
  $('session-size').disabled = frozen;
  $('study-scope').disabled = frozen;
  $('answer-input').disabled = frozen || !!state?.currentQuestion?.feedback;
  $('submit').disabled = frozen || !!state?.currentQuestion?.feedback;
  $('end').disabled = frozen || state?.status !== 'IN_PROGRESS';
  $('next-question').disabled = frozen;
  for (const b of $('self-rating').querySelectorAll('button')) b.disabled = frozen;
  $('retry').hidden = !pending; $('retry').disabled = busy;
  $('training').setAttribute('aria-busy', String(busy));
}
function resetSession() {
  clearSentenceAudio();
  state = null; sessionId = null; pending = null; remember();
  history.replaceState(null, '', '/sentences.html');
  $('training').hidden = true; $('summary').hidden = true; $('settings').hidden = false;
  $('manage').hidden = false; $('editor').hidden = false;
  void loadList();
  status('sentence-message', 'Выберите размер занятия.'); controls(); $('session-size').focus();
}
async function display(data) {
  const previous = state?.currentQuestion?.id;
  state = data; sessionId = data.id; remember();
  history.replaceState(null, '', `/sentences.html?session=${sessionId}`);
  $('settings').hidden = true; $('summary').hidden = true;
  const q = data.currentQuestion;
  $('training').hidden = !q;
  $('manage').hidden = !!q; $('editor').hidden = !!q;
  if (q) $('sentence-list').replaceChildren();
  if (!q) {
    clearSentenceAudio();
    const result = await api(`${base}/${sessionId}/results`);
    $('summary-title').textContent = data.status === 'ABANDONED' ? 'Занятие завершено досрочно' : 'Занятие завершено';
    $('summary-counts').textContent = `Оценено: ${data.answeredQuestions} из ${data.totalQuestions}. Правильно: ${data.correctAnswers}. Частично: ${data.partialAnswers}.`;
    $('review-list').replaceChildren();
    for (const item of result.items) {
      const li = document.createElement('li');
      const rating = { CORRECT: 'Правильно', PARTIAL: 'Частично', INCORRECT: 'Неправильно', PENDING: 'Без самооценки', SKIPPED: 'Пропущено' }[item.result];
      li.textContent = `${item.russianText} — ${rating}${item.evaluationSource === 'SELF' ? ' (самооценка)' : ''}. ${item.userAnswer ? `Ваш ответ: ${item.userAnswer}. ` : ''}Одобренные варианты: ${item.acceptedAnswers.join('; ')}.${item.explanation ? ` Пояснение: ${item.explanation}` : ''}`;
      $('review-list').append(li);
    }
    $('summary').hidden = false; status('sentence-message', 'Результаты сохранены.'); $('summary-title').focus();
    void loadList();
    return;
  }
  $('question-count').textContent = `Предложение ${q.position} из ${data.totalQuestions} · Оценено: ${data.answeredQuestions}`;
  $('question-progress').max = data.totalQuestions; $('question-progress').value = data.answeredQuestions;
  $('russian-prompt').textContent = q.russianText;
  if (previous !== q.id) $('answer-input').value = '';
  $('feedback').hidden = !q.feedback; $('submit').hidden = !!q.feedback;
  if (previous !== q.id || !q.feedback) clearSentenceAudio();
  if (q.feedback) {
    const feedback = q.feedback;
    $('answer-input').value = feedback.userAnswer;
    $('feedback').dataset.result = feedback.result;
    $('feedback-heading').textContent = feedback.needsSelfAssessment ? 'Проверьте свой вариант' : feedback.result === 'CORRECT' ? 'Совпало с одобренным переводом' : feedback.result === 'PARTIAL' ? 'Частично правильно — ваша самооценка' : 'Неправильно — ваша самооценка';
    $('user-answer').textContent = `Ваш ответ: ${feedback.userAnswer}`;
    $('approved').textContent = `Одобренные варианты: ${feedback.acceptedAnswers.join('; ')}`;
    showSentenceAudio(q);
    $('explanation').textContent = feedback.explanation ? `Пояснение: ${feedback.explanation}` : '';
    $('self-rating').hidden = !feedback.needsSelfAssessment;
    $('next-question').hidden = feedback.needsSelfAssessment;
    $('next-question').textContent = data.status === 'COMPLETED' ? 'Посмотреть результаты' : 'Следующее предложение';
  }
  status('sentence-message', q.feedback ? q.feedback.needsSelfAssessment ? 'Текст не совпал дословно. Сравните варианты и оцените свой ответ самостоятельно.' : 'Ответ сохранён.' : 'Переведите предложение на болгарский.');
}
async function execute(operation) {
  if (busy) return;
  pending = operation; remember(); busy = true; controls(); status('sentence-message', 'Загружаем…');
  try {
    const url = operation.action === 'start' ? base : `${base}/${operation.sessionId}/${operation.action === 'load' ? 'question' : operation.action}`;
    const data = await api(url, { method: operation.action === 'load' ? 'GET' : 'POST', ...(operation.body ? { body: JSON.stringify(operation.body) } : {}) });
    pending = null; await display(data); remember();
  } catch (error) {
    if (error.definite) {
      pending = sessionId ? { action: 'load', sessionId } : null;
      if (error.code === 'SESSION_NOT_FOUND') resetSession();
    } else if (!pending) pending = { action: 'load', sessionId };
    status('sentence-message', error.message, true); remember(); $('sentence-message').focus();
  } finally {
    busy = false; controls();
    if (!pending && state?.currentQuestion) (state.currentQuestion.feedback ? state.currentQuestion.feedback.needsSelfAssessment ? $('self-rating').querySelector('button') : $('next-question') : $('answer-input')).focus();
  }
}
$('start-form').addEventListener('submit', event => { event.preventDefault(); if (busy || pending) return; execute({ action: 'start', body: { requestId: crypto.randomUUID(), size: Number($('session-size').value), scope: $('study-scope').value } }); });
$('answer-form').addEventListener('submit', event => { event.preventDefault(); if (busy || pending || !state?.currentQuestion || state.currentQuestion.feedback) return; execute({ action: 'answers', sessionId, body: { questionId: state.currentQuestion.id, answer: $('answer-input').value } }); });
$('self-rating').addEventListener('click', event => { const rating = event.target.closest('[data-rating]'); if (rating && !busy && !pending) execute({ action: 'self-rating', sessionId, body: { questionId: state.currentQuestion.id, result: rating.dataset.rating } }); });
$('next-question').addEventListener('click', () => { if (!busy && !pending) { clearSentenceAudio(); execute({ action: 'next', sessionId, body: { questionId: state.currentQuestion.id } }); } });
$('end').addEventListener('click', () => { if (!busy && !pending) { clearSentenceAudio(); execute({ action: 'complete', sessionId }); } });
$('new-session').addEventListener('click', resetSession);
$('retry').addEventListener('click', () => { if (pending) execute(pending); });
let saved = null; try { saved = JSON.parse(localStorage.getItem(storageKey)); } catch { /* Optional storage. */ }
const requested = new URLSearchParams(location.search).get('session');
if (requested && /^\d+$/.test(requested) && Number(requested) > 0) sessionId = Number(requested);
else if (Number.isSafeInteger(saved?.sessionId) && saved.sessionId > 0) sessionId = saved.sessionId;
if (sessionId) { $('manage').hidden = true; $('editor').hidden = true; }
if (saved?.pending && (!requested || String(saved.sessionId) === requested) && ['start', 'load', 'answers', 'self-rating', 'next', 'complete'].includes(saved.pending.action)) {
  pending = saved.pending; $('settings').hidden = !!sessionId;
  status('sentence-message', 'Незавершённый запрос. Нажмите «Повторить запрос» для безопасного восстановления.', true); controls();
} else if (sessionId) execute({ action: 'load', sessionId }); else { status('sentence-message', 'Выберите размер занятия.'); controls(); }

let listPage = 0, listPages = 0, editingId = null, selectedWords = new Map(), listBusy = false;
async function loadList() {
  if (state?.currentQuestion) return;
  if (listBusy) return; listBusy = true; $('sentence-list').setAttribute('aria-busy', 'true');
  status('list-message', 'Загружаем предложения…');
  try {
    const data = await api(`/api/sentences?archived=${$('archive-filter').value}&page=${listPage}&size=10`);
    if (state?.currentQuestion) { $('sentence-list').replaceChildren(); return; }
    if (listPage >= data.totalPages && listPage > 0) { listPage = Math.max(0, data.totalPages - 1); listBusy = false; return loadList(); }
    listPages = data.totalPages; $('sentence-list').replaceChildren();
    if (!data.content.length) { const p = document.createElement('p'); p.textContent = $('archive-filter').value === 'true' ? 'Архив предложений пуст.' : 'Предложений пока нет. Добавьте их или импортируйте CSV.'; $('sentence-list').append(p); }
    for (const sentence of data.content) {
      const card = document.createElement('article'); card.className = 'sentence-card';
      const title = document.createElement('h3'); title.textContent = sentence.russianText;
      const main = document.createElement('p'); main.textContent = `Болгарский: ${sentence.bulgarianText}`;
      const details = document.createElement('p'); details.textContent = `Вариантов: ${sentence.alternativeTranslations.length}. Сложность: ${sentence.difficulty ?? 'не указана'}. ${sentence.topic ? `Тема: ${sentence.topic}.` : ''} Связанных слов: ${sentence.wordIds.length}.`;
      const actions = document.createElement('div'); actions.className = 'toolbar';
      actions.append(button('Изменить', () => editSentence(sentence)), button(sentence.archived ? 'Восстановить' : 'В архив', () => archiveSentence(sentence)));
      card.append(title, main, details, actions); $('sentence-list').append(card);
    }
    $('page-label').textContent = listPages ? `Страница ${listPage + 1} из ${listPages}` : 'Нет записей';
    $('previous-page').disabled = listPage === 0; $('next-page').disabled = listPage + 1 >= listPages;
    status('list-message', `Всего предложений: ${data.totalElements}.`);
  } catch (error) { status('list-message', error.message, true); }
  finally { listBusy = false; $('sentence-list').setAttribute('aria-busy', 'false'); }
}
async function archiveSentence(sentence) {
  try { await api(`/api/sentences/${sentence.id}/archive`, { method: 'PATCH', body: JSON.stringify({ archived: !sentence.archived }) }); await loadList(); status('list-message', sentence.archived ? 'Предложение восстановлено.' : 'Предложение перенесено в архив.'); }
  catch (error) { status('list-message', error.message, true); }
}
function renderSelectedWords() {
  $('selected-words').replaceChildren();
  for (const [id, text] of selectedWords) {
    const chip = document.createElement('span'); chip.className = 'word-chip'; chip.append(document.createTextNode(text), button('×', () => { selectedWords.delete(id); renderSelectedWords(); }));
    $('selected-words').append(chip);
  }
  if (!selectedWords.size) $('selected-words').textContent = 'Связанные слова не выбраны.';
}
async function editSentence(sentence) {
  editingId = sentence.id; $('editor-title').textContent = 'Изменить предложение'; $('cancel-edit').hidden = false;
  $('russian-text').value = sentence.russianText; $('bulgarian-text').value = sentence.bulgarianText;
  $('alternatives').value = sentence.alternativeTranslations.join('\n'); $('grammar').value = sentence.explanation || '';
  $('difficulty').value = sentence.difficulty ?? ''; $('topic').value = sentence.topic || '';
  selectedWords = new Map(sentence.wordIds.map(id => [id, `Слово №${id}`])); renderSelectedWords();
  for (const id of sentence.wordIds) { try { const word = await api(`/api/words/${id}`); selectedWords.set(id, word.bulgarianText); renderSelectedWords(); } catch { /* Keep the known ID. */ } }
  $('editor').scrollIntoView({ behavior: 'smooth' }); $('russian-text').focus();
}
function resetEditor() {
  editingId = null; $('sentence-form').reset(); $('editor-title').textContent = 'Добавить предложение'; $('cancel-edit').hidden = true;
  selectedWords.clear(); renderSelectedWords(); status('editor-message', '');
}
$('sentence-form').addEventListener('submit', async event => {
  event.preventDefault(); if ($('editor-fields').disabled) return;
  const request = { russianText: $('russian-text').value, bulgarianText: $('bulgarian-text').value,
    alternativeTranslations: $('alternatives').value.split(/\r?\n/).map(x => x.trim()).filter(Boolean),
    explanation: $('grammar').value || null, difficulty: $('difficulty').value ? Number($('difficulty').value) : null,
    topic: $('topic').value || null, wordIds: [...selectedWords.keys()] };
  $('editor-fields').disabled = true; status('editor-message', 'Сохраняем предложение…');
  try {
    await api(editingId ? `/api/sentences/${editingId}` : '/api/sentences', { method: editingId ? 'PUT' : 'POST', body: JSON.stringify(request) });
    resetEditor(); status('editor-message', 'Предложение сохранено.'); await loadList();
  } catch (error) { status('editor-message', error.message, true); }
  finally { $('editor-fields').disabled = false; }
});
$('cancel-edit').addEventListener('click', resetEditor);
$('search-words').addEventListener('click', async () => {
  $('word-matches').textContent = 'Ищем слова…';
  const query = $('word-search').value.trim();
  try {
    const data = await api(query ? `/api/words/search?q=${encodeURIComponent(query)}&size=20` : '/api/words?size=20');
    $('word-matches').replaceChildren();
    for (const word of data.content) {
      const row = document.createElement('div'); row.className = 'word-match';
      row.append(button('Добавить', () => { selectedWords.set(word.id, word.bulgarianText); renderSelectedWords(); }), document.createTextNode(`${word.bulgarianText} — ${word.translations.map(t => t.russianText).join('; ')}`));
      $('word-matches').append(row);
    }
    if (!data.content.length) $('word-matches').textContent = 'Слова не найдены.';
  } catch (error) { $('word-matches').textContent = error.message; }
});
$('word-search').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); $('search-words').click(); } });
$('archive-filter').addEventListener('change', () => { listPage = 0; loadList(); });
$('reload-list').addEventListener('click', loadList);
$('previous-page').addEventListener('click', () => { if (listPage > 0) { listPage--; loadList(); } });
$('next-page').addEventListener('click', () => { if (listPage + 1 < listPages) { listPage++; loadList(); } });
renderSelectedWords(); if (!sessionId) loadList();
