import { $, api, status, content, element, outcomes, load, save, positiveId } from './aspect-common.js';

const base = '/api/aspect-pair-practice';
const key = 'bulgariantrainer.aspect-pairs.practice';
const allowed = new Set(['availability', 'start', 'load', 'answers', 'reveal', 'next', 'complete', 'results']);
export function mountPractice() {
  const saved = load(key) || {};
  const requested = positiveId(new URLSearchParams(location.search).get('session'));
  let sessionId = requested || positiveId(saved.sessionId), state = null, busy = false, availability = null;
  let draft = saved.draft || null;
  let pending = saved.pending && allowed.has(saved.pending.action) && (!requested || requested === positiveId(saved.sessionId)) ? saved.pending : null;
  const remember = () => save(key, { sessionId, pending, draft });
  function controls() {
    const frozen = busy || !!pending;
    $('practice-option-fields').disabled = frozen;
    $('practice-start').disabled = frozen || !availability?.offeredSize;
    $('aspect-answer').disabled = frozen; $('aspect-answer').readOnly = !!state?.currentQuestion?.feedback;
    $('practice-submit').disabled = frozen || !state?.currentQuestion || !!state.currentQuestion.feedback;
    $('practice-reveal').disabled = $('practice-submit').disabled;
    $('practice-next').disabled = frozen; $('practice-new').disabled = frozen;
    $('practice-end').disabled = frozen || state?.status !== 'IN_PROGRESS';
    $('practice-retry').hidden = !pending; $('practice-retry').disabled = busy;
    $('aspect-resume-button').disabled = frozen;
    $('aspect-training').setAttribute('aria-busy', String(busy));
  }
  function settings() {
    const stages = [...document.querySelectorAll('[name=practice-stage]:checked')].map(node => Number(node.value));
    if (!stages.length) throw new Error('Выберите хотя бы один этап изучения.');
    const size = Number($('practice-size').value);
    if (!Number.isInteger(size) || size < 1 || size > 100) throw new Error('Выберите от 1 до 100 вопросов.');
    return { size, studyStages: stages, topic: $('practice-topic').value || null,
      namespace: $('practice-namespace').value || null, direction: $('practice-direction').value, scope: $('practice-scope').value };
  }
  function availabilityUrl(options) {
    const query = new URLSearchParams();
    for (const [field, value] of Object.entries(options)) {
      if (Array.isArray(value)) value.forEach(item => query.append(field, item));
      else if (value != null) query.set(field, value);
    }
    return `${base}/availability?${query}`;
  }
  function showAvailability(data) {
    availability = data;
    $('practice-availability').textContent = !data.offeredSize ? 'В выбранной очереди нет доступных пар. Измените фильтры или импортируйте набор.'
      : data.shorterRequired ? `Доступно ${data.eligiblePairs} пар. Предлагаем сокращённое занятие: ${data.offeredSize} вопросов без повторов.`
        : `Доступно ${data.eligiblePairs} пар. В занятии будет ${data.offeredSize} вопросов без повторов.`;
    $('practice-start').textContent = data.shorterRequired ? `Начать сокращённое занятие (${data.offeredSize})` : `Начать занятие (${data.offeredSize})`;
    status('practice-message', data.offeredSize ? 'Доступность проверена. Можно начать занятие.' : 'Пока нет пар для этих параметров.');
  }
  function restoreOptions(options) {
    $('practice-size').value = options.size; $('practice-namespace').value = options.namespace || '';
    $('practice-topic').value = options.topic || ''; $('practice-direction').value = options.direction; $('practice-scope').value = options.scope;
    document.querySelectorAll('[name=practice-stage]').forEach(node => { node.checked = options.studyStages.includes(Number(node.value)); });
  }
  function showQuestion(data) {
    state = data; sessionId = data.id; remember();
    restoreOptions(data.settings);
    history.replaceState(null, '', `/aspect-pairs.html?session=${sessionId}${location.hash || '#practice'}`);
    $('aspect-resume').hidden = true; $('practice-settings').hidden = true; $('practice-results').hidden = true;
    const q = data.currentQuestion;
    $('aspect-training').hidden = !q;
    $('practice-explanation').replaceChildren();
    if (!q) return;
    $('practice-position').textContent = `Вопрос ${q.position} из ${data.totalQuestions} · Верно: ${data.correctAnswers} · Раскрыто: ${data.revealedQuestions}`;
    $('practice-progress').max = data.totalQuestions; $('practice-progress').value = data.answeredQuestions + data.revealedQuestions;
    $('practice-aspect').textContent = q.direction === 'IMPERFECTIVE_TO_PERFECTIVE' ? 'Требуется совершенный вид · СВ' : 'Требуется несовершенный вид · НСВ';
    $('practice-verb').textContent = q.prompt.bulgarian; $('practice-meaning').textContent = q.prompt.russianMeaning;
    $('practice-instruction').textContent = `${q.prompt.instruction} со значением «${q.prompt.targetRussianMeaning}».`;
    $('aspect-answer').value = q.feedback ? q.feedback.userAnswer || '' : draft?.questionId === q.id ? draft.text : '';
    $('practice-feedback').hidden = !q.feedback;
    $('practice-submit').hidden = !!q.feedback; $('practice-reveal').hidden = !!q.feedback; $('practice-reveal-note').hidden = !!q.feedback;
    if (q.feedback) {
      draft = null; remember();
      $('practice-feedback').dataset.outcome = q.feedback.outcome;
      $('practice-feedback-title').textContent = q.feedback.message;
      $('practice-user-answer').textContent = q.feedback.userAnswer == null ? 'Ответ был раскрыт без самостоятельной попытки.' : `Ваш ответ: ${q.feedback.userAnswer}`;
      $('practice-accepted').textContent = `Принимаются: ${q.feedback.acceptedAnswers.join('; ')}`;
      content($('practice-explanation'), q.feedback.content);
      $('practice-next').textContent = data.status === 'COMPLETED' ? 'Дальше — результаты' : 'Дальше';
    }
    status('practice-message', q.feedback ? 'Результат сохранён. Прочитайте пояснение и нажмите «Дальше».' : 'Введите одну форму на болгарском.');
  }
  function reviewItem(item) {
    const card = element('article', undefined, 'aspect-review');
    const title = element('h4', `${item.position}. ${item.prompt.bulgarian} — ${item.prompt.russianMeaning}`); title.lang = 'ru';
    card.append(title, element('p', `${item.prompt.instruction} со значением «${item.prompt.targetRussianMeaning}».`), element('strong', outcomes[item.outcome] || 'Результат'));
    if (item.feedback) {
      if (item.feedback.userAnswer != null) card.append(element('p', `Ваш ответ: ${item.feedback.userAnswer}`));
      content(card, item.feedback.content);
    } else card.append(element('p', 'Вопрос пропущен. Ответ не раскрывался.'));
    return card;
  }
  function showResults(result) {
    state = result.session; remember();
    restoreOptions(state.settings);
    $('aspect-training').hidden = true; $('practice-settings').hidden = true; $('aspect-resume').hidden = true; $('practice-results').hidden = false;
    $('practice-explanation').replaceChildren();
    $('practice-results-title').textContent = state.status === 'ABANDONED' ? 'Занятие завершено досрочно' : 'Занятие завершено';
    $('practice-result-counts').textContent = `Вопросов: ${state.totalQuestions}. Самостоятельных ответов: ${state.answeredQuestions}. Верно: ${state.correctAnswers}. Не совпало: ${state.incorrectAnswers}. Раскрыто: ${state.revealedQuestions}. Пропущено: ${state.remainingQuestions}.`;
    $('practice-result-accuracy').textContent = result.accuracyPercent == null ? 'Точность: —. Самостоятельных ответов пока нет.' : `Точность самостоятельных ответов: ${result.accuracyPercent}%. Раскрытия не входят в точность.`;
    const mistakes = result.items.filter(item => ['INCORRECT', 'REVEALED'].includes(item.outcome));
    $('practice-mistakes').replaceChildren(...mistakes.map(reviewItem)); $('practice-no-mistakes').hidden = mistakes.length > 0;
    $('practice-all-results').replaceChildren(...result.items.map(reviewItem));
    status('practice-message', 'Занятие и результаты сохранены на сервере.');
  }
  async function run(operation) {
    if (busy) return;
    pending = operation; busy = true; remember(); controls(); status('practice-message', 'Загружаем…');
    let focus = null;
    try {
      let url, method = 'GET';
      if (operation.action === 'availability') url = availabilityUrl(operation.options);
      else if (operation.action === 'start') { url = `${base}/sessions`; method = 'POST'; }
      else { url = `${base}/sessions/${operation.sessionId}/${operation.action === 'load' ? 'question' : operation.action}`; if (!['load', 'results'].includes(operation.action)) method = 'POST'; }
      const data = await api(url, { method, body: operation.body });
      pending = null; remember();
      if (operation.action === 'availability') showAvailability(data);
      else if (operation.action === 'results') { showResults(data); focus = $('practice-results-title'); }
      else {
        showQuestion(data);
        if (!data.currentQuestion) {
          pending = { action: 'results', sessionId: data.id }; remember();
          const results = await api(`${base}/sessions/${data.id}/results`);
          pending = null; remember(); showResults(results); focus = $('practice-results-title');
        } else focus = data.currentQuestion.feedback ? $('practice-next') : $('aspect-answer');
      }
    } catch (error) {
      if (error.definite) {
        if (error.code === 'SESSION_NOT_FOUND') {
          sessionId = null; state = null; pending = null; $('aspect-training').hidden = true; $('practice-settings').hidden = false;
          history.replaceState(null, '', '/aspect-pairs.html#practice');
        } else if (['STALE_QUESTION', 'ANSWER_CONFLICT', 'ANSWER_REVEALED', 'ANSWER_ALREADY_SUBMITTED', 'SESSION_CLOSED'].includes(error.code)) pending = { action: 'load', sessionId: operation.sessionId };
        else { pending = null; if (operation.action === 'start') { availability = null; $('practice-availability').textContent = 'Проверьте доступность ещё раз.'; } }
      }
      status('practice-message', `${error.message}${pending ? ' Нажмите «Повторить запрос»: будет отправлен тот же запрос или восстановлено состояние.' : ''}`, true);
      remember(); focus = $('practice-message');
    } finally { busy = false; controls(); focus?.focus(); }
  }
  $('practice-options').addEventListener('submit', event => {
    event.preventDefault(); if (busy || pending) return;
    try { run({ action: 'availability', options: settings() }); } catch (error) { status('practice-message', error.message, true); }
  });
  $('practice-options').addEventListener('input', () => { availability = null; $('practice-availability').textContent = 'Параметры изменены. Проверьте доступность.'; controls(); });
  $('practice-start').addEventListener('click', () => {
    if (busy || pending || !availability?.offeredSize) return;
    try { run({ action: 'start', body: { requestId: crypto.randomUUID(), ...settings(), allowShorter: availability.shorterRequired } }); }
    catch (error) { status('practice-message', error.message, true); }
  });
  $('aspect-answer').addEventListener('input', () => { if (state?.currentQuestion && !state.currentQuestion.feedback) { draft = { questionId: state.currentQuestion.id, text: $('aspect-answer').value }; remember(); } });
  $('aspect-answer-form').addEventListener('submit', event => {
    event.preventDefault(); if (busy || pending || !state?.currentQuestion || state.currentQuestion.feedback) return;
    run({ action: 'answers', sessionId, body: { questionId: state.currentQuestion.id, answer: $('aspect-answer').value } });
  });
  $('aspect-training').addEventListener('keydown', event => {
    if (event.key !== 'Enter') return;
    if (event.repeat || event.isComposing) { event.preventDefault(); return; }
    if (state?.currentQuestion?.feedback && event.target === $('aspect-answer')) {
      event.preventDefault();
      if (!busy && !pending) run({ action: 'next', sessionId, body: { questionId: state.currentQuestion.id } });
    }
  });
  $('practice-reveal').addEventListener('click', () => { if (!busy && !pending && state?.currentQuestion && !state.currentQuestion.feedback) run({ action: 'reveal', sessionId, body: { questionId: state.currentQuestion.id } }); });
  $('practice-next').addEventListener('click', () => { if (!busy && !pending && state?.currentQuestion?.feedback) run({ action: 'next', sessionId, body: { questionId: state.currentQuestion.id } }); });
  $('practice-end').addEventListener('click', () => { if (!busy && !pending && state?.status === 'IN_PROGRESS') run({ action: 'complete', sessionId }); });
  $('practice-retry').addEventListener('click', () => { if (pending) run(pending); });
  $('aspect-resume-button').addEventListener('click', () => { if (!busy && !pending && sessionId) run({ action: 'load', sessionId }); });
  $('practice-new').addEventListener('click', () => {
    if (busy || pending) return;
    state = null; sessionId = null; draft = null; availability = null; remember();
    history.replaceState(null, '', '/aspect-pairs.html#practice');
    $('practice-settings').hidden = false; $('practice-results').hidden = true; $('aspect-training').hidden = true;
    $('practice-availability').textContent = 'Проверьте доступность нового занятия.';
    $('practice-mistakes').replaceChildren(); $('practice-all-results').replaceChildren(); $('practice-explanation').replaceChildren();
    status('practice-message', 'Выберите параметры нового занятия.'); controls(); $('practice-size').focus();
  });
  if (pending) status('practice-message', 'Остался незавершённый запрос. Нажмите «Повторить запрос», чтобы безопасно восстановить занятие.', true);
  else if (requested) run({ action: 'load', sessionId: requested });
  else $('aspect-resume').hidden = !sessionId;
  controls();
  return { active: () => !!requested || !!pending, refreshAvailability: () => { availability = null; controls(); } };
}
