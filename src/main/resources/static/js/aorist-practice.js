import { $, api, status, element, button, table, source, persons, personCodes, types, outcomes, load, save, positiveId } from './aorist-common.js';
import { BulgarianPronunciation, createPlaybackControls, mountVoiceSettings } from './pronunciation.js';

const base = '/api/aorist-practice', storageKey = 'bulgariantrainer.aorist.practice';
const actions = new Set(['availability', 'start', 'load', 'answers', 'reveal', 'next', 'complete', 'results']);
export function mountPractice() {
  const stored = load(storageKey) || {}, requested = positiveId(new URLSearchParams(location.search).get('session'));
  let sessionId = requested || positiveId(stored.sessionId), state = null, busy = false, availability = null, sessionsBusy = false;
  let draft = stored.draft || null, pending = actions.has(stored.pending?.action) && (!requested || requested === positiveId(stored.sessionId)) ? stored.pending : null;
  const pronunciation = new BulgarianPronunciation(); mountVoiceSettings(pronunciation, $('aorist-voice-settings'));
  let audio = [];
  const clearAudio = () => { audio.forEach(c => c.destroy()); audio = []; pronunciation.stop(); };
  const remember = () => save(storageKey, { sessionId, draft, pending });
  const addAudio = (root, text, label) => { const c = createPlaybackControls(pronunciation, { text, label }); audio.push(c); root.append(c.element); };
  function controls() {
    const frozen = busy || !!pending, q = state?.currentQuestion;
    $('ap-option-fields').disabled = frozen; $('ap-start').disabled = frozen || !availability?.offeredSize;
    $('ap-answer').disabled = frozen; $('ap-answer').readOnly = !!q?.feedback;
    $('ap-choices').disabled = frozen || !!q?.feedback;
    $('ap-submit').disabled = $('ap-reveal').disabled = frozen || !q || !!q.feedback;
    $('ap-next').disabled = frozen; $('ap-end').disabled = frozen || state?.status !== 'IN_PROGRESS'; $('ap-new').disabled = frozen;
    $('ap-retry').hidden = !pending; $('ap-retry').disabled = busy; $('ap-resume-last').disabled = frozen;
    $('ap-resume-last').hidden = !sessionId; $('ap-sessions-refresh').disabled = frozen || sessionsBusy;
    $('ap-sessions').querySelectorAll('button').forEach(b => { b.disabled = frozen || sessionsBusy; });
    $('ap-training').setAttribute('aria-busy', String(busy));
    $('ap-person-fields').hidden = $('ap-person-mode').value !== 'SELECTED';
  }
  function settings() {
    const mode = $('ap-person-mode').value;
    const selected = mode === 'AZ' ? ['1sg'] : mode === 'ALL' ? personCodes : [...document.querySelectorAll('[name=ap-person]:checked')].map(n => n.value);
    if (!selected.length) throw new Error('Выберите хотя бы одно лицо.');
    const size = Number($('ap-size').value); if (!Number.isInteger(size) || size < 1 || size > 100) throw new Error('Выберите от 1 до 100 вопросов.');
    return { exerciseType: $('ap-type').value, persons: selected, size, aspect: $('ap-aspect').value || null, studyGroup: $('ap-group').value || null, datasetId: $('ap-dataset').value || null, scope: $('ap-scope').value };
  }
  function restoreOptions(s) {
    if (!s) return;
    $('ap-type').value = s.exerciseType || 'TYPED'; $('ap-size').value = s.size || 20; $('ap-aspect').value = s.aspect || ''; $('ap-group').value = s.studyGroup || ''; $('ap-dataset').value = s.datasetId || ''; $('ap-scope').value = s.scope || 'MIXED';
    const selected = s.persons || ['1sg']; $('ap-person-mode').value = selected.length === 6 ? 'ALL' : selected.length === 1 && selected[0] === '1sg' ? 'AZ' : 'SELECTED';
    document.querySelectorAll('[name=ap-person]').forEach(e => { e.checked = selected.includes(e.value); }); controls();
  }
  const availabilityUrl = options => { const q = new URLSearchParams(); for (const [k, v] of Object.entries(options)) if (Array.isArray(v)) v.forEach(x => q.append(k, x)); else if (v != null) q.set(k, v); return `${base}/availability?${q}`; };
  function showAvailability(data) {
    availability = data;
    $('ap-availability').textContent = data.offeredSize ? `Доступно вопросов: ${data.eligibleQuestions}, глаголов: ${data.eligibleVerbs}. ${data.shorterRequired ? 'Предлагаем сокращённое занятие: ' : 'В занятии: '}${data.offeredSize} вопросов без повторов глагола и лица.` : 'Для этих параметров нет доступных вопросов. Измените фильтры, очередь или импортируйте набор.';
    if (data.choiceUnavailableQuestions || data.reducedOptionQuestions) $('ap-availability').append(` Вопросов с 2–3 вариантами: ${data.reducedOptionQuestions}; без достаточного выбора: ${data.choiceUnavailableQuestions}. Можно явно выбрать ввод ответа (доступно: ${data.typedAlternativeQuestions}).`);
    $('ap-start').textContent = data.shorterRequired ? `Начать сокращённое занятие (${data.offeredSize})` : `Начать занятие (${data.offeredSize})`;
    status('ap-message', data.offeredSize ? 'Доступность проверена. Можно начать занятие.' : 'Пока нет вопросов для этих параметров.');
  }
  async function explanation(root, id, questionId) {
    root.dataset.questionId = questionId; root.replaceChildren(element('p', 'Загружаем пояснение…', 'note'));
    try {
      const data = await api(`${base}/sessions/${id}/questions/${questionId}/explanation`);
      if (!root.isConnected || root.dataset.questionId !== String(questionId)) return;
      root.replaceChildren();
      if (!data.available) { root.append(element('p', data.notice, 'note')); return; }
      if (data.note) root.append(element('p', data.note, 'aorist-note'));
      const details = element('details'); details.append(element('summary', 'Таблица всех шести лиц'), table(data.forms)); root.append(details); source(root, data.source);
    } catch (error) {
      if (!root.isConnected || root.dataset.questionId !== String(questionId)) return;
      root.replaceChildren(element('p', error.message, 'note'), button('Повторить загрузку пояснения', () => explanation(root, id, questionId)));
    }
  }
  function showQuestion(data) {
    state = data; sessionId = data.id; remember(); restoreOptions(data.settings); clearAudio();
    history.replaceState(null, '', `/aorist.html?session=${data.id}${location.hash || '#practice'}`);
    $('ap-settings').hidden = true; $('ap-results').hidden = true;
    $('ap-details').replaceChildren(); $('ap-details').dataset.questionId = ''; $('ap-source-audio').replaceChildren(); $('ap-answer-audio').replaceChildren();
    const q = data.currentQuestion; $('ap-training').hidden = !q; if (!q) return;
    $('ap-position').textContent = `Вопрос ${q.position} из ${data.totalQuestions} · Верно: ${data.correctAnswers} · Раскрыто: ${data.revealedQuestions}`;
    $('ap-progress').max = data.totalQuestions; $('ap-progress').value = data.answeredQuestions + data.revealedQuestions;
    $('ap-mode').textContent = types[data.settings.exerciseType]; $('ap-lemma').textContent = q.prompt.lemma; $('ap-meaning').textContent = q.prompt.russianMeaning;
    $('ap-tense').textContent = q.prompt.tenseLabel; $('ap-person').textContent = q.prompt.personLabel; $('ap-answer-scope').textContent = q.prompt.answerScope;
    addAudio($('ap-source-audio'), q.prompt.lemma, 'Словарная форма');
    const typed = data.settings.exerciseType === 'TYPED'; $('ap-typed').hidden = !typed; $('ap-choices').hidden = typed; $('ap-answer').required = typed && !q.feedback;
    $('ap-answer').value = q.feedback ? q.feedback.userAnswer || '' : draft?.questionId === q.id ? draft.text || '' : '';
    $('ap-choice-options').replaceChildren();
    const optionId = q.feedback?.selectedOptionId || (draft?.questionId === q.id ? draft.optionId : null);
    for (const [index, o] of q.options.entries()) {
      const label = element('label', undefined, 'aorist-choice'), input = element('input'); input.type = 'radio'; input.name = 'ap-option'; input.id = `ap-option-${q.id}-${index}`; input.value = o.optionId; input.required = !q.feedback; input.checked = o.optionId === optionId;
      input.addEventListener('change', () => { draft = { questionId: q.id, optionId: input.value }; remember(); });
      const text = element('span', o.text); text.lang = 'bg'; label.append(input, text); $('ap-choice-options').append(label);
    }
    $('ap-choice-notice').textContent = q.choiceNotice || '';
    $('ap-feedback').hidden = !q.feedback; $('ap-submit').hidden = $('ap-reveal').hidden = $('ap-reveal-help').hidden = !!q.feedback;
    if (q.feedback) {
      draft = null; remember(); $('ap-feedback').dataset.outcome = q.feedback.outcome; $('ap-feedback-title').textContent = q.feedback.message;
      const submitted = q.feedback.userAnswer ?? q.feedback.selectedOptionText;
      $('ap-submitted').textContent = submitted == null ? 'Ответ раскрыт без самостоятельной попытки.' : `Ваш ответ: ${submitted}`;
      $('ap-correct').textContent = `Правильная форма: ${q.feedback.acceptedAnswers[0]}`;
      $('ap-alternatives').textContent = q.feedback.acceptedAnswers.length > 1 ? `Также принимаются: ${q.feedback.acceptedAnswers.slice(1).join('; ')}` : 'Дополнительных вариантов в карточке нет.';
      $('ap-explanation').textContent = q.feedback.explanation; addAudio($('ap-answer-audio'), q.feedback.acceptedAnswers[0], 'Проверенный ответ');
      explanation($('ap-details'), data.id, q.id); $('ap-next').textContent = data.status === 'COMPLETED' ? 'Дальше — результаты' : 'Дальше';
    }
    status('ap-message', q.feedback ? 'Ответ сохранён. Прочитайте пояснение и нажмите «Дальше».' : typed ? 'Введите изолированную форму на болгарском.' : 'Выберите вариант и нажмите «Проверить».');
  }
  function reviewItem(item, id) {
    const card = element('article', undefined, 'aorist-review');
    card.append(element('h4', `${item.position}. ${item.prompt.lemma} — ${item.prompt.russianMeaning}`), element('p', `Аорист · Лицо: ${item.prompt.personLabel}`), element('strong', outcomes[item.outcome] || 'Результат'));
    if (item.feedback) {
      const answer = item.feedback.userAnswer ?? item.feedback.selectedOptionText;
      card.append(element('p', answer == null ? 'Ответ был раскрыт.' : `Ваш ответ: ${answer}`), element('p', `Принимаются: ${item.feedback.acceptedAnswers.join('; ')}`), element('p', item.feedback.explanation, 'note'));
      const details = element('details'); details.append(element('summary', 'Пояснение и таблица')); const root = element('div'); details.append(root);
      let loaded = false; details.addEventListener('toggle', () => { if (details.open && !loaded) { loaded = true; explanation(root, id, item.questionId); } }); card.append(details);
    } else card.append(element('p', 'Ответ пропущен и не раскрывается. Расписание не изменено.'));
    return card;
  }
  function showResults(result) {
    state = result.session; sessionId = state.id; draft = null; remember(); restoreOptions(state.settings); clearAudio();
    $('ap-source-audio').replaceChildren(); $('ap-answer-audio').replaceChildren(); $('ap-details').replaceChildren(); $('ap-details').dataset.questionId = '';
    $('ap-settings').hidden = $('ap-training').hidden = true; $('ap-results').hidden = false;
    $('ap-results-title').textContent = state.status === 'ABANDONED' ? 'Занятие завершено досрочно' : 'Занятие завершено';
    $('ap-result-counts').textContent = `${types[state.settings.exerciseType]}. Вопросов: ${state.totalQuestions}. Ответов: ${state.answeredQuestions}. Верно: ${state.correctAnswers}. Не совпало: ${state.incorrectAnswers}. Раскрыто: ${state.revealedQuestions}. Пропущено: ${state.remainingQuestions}.`;
    const label = state.settings.exerciseType === 'TYPED' ? 'Точность самостоятельного ввода' : 'Точность выбора (не самостоятельный ввод)';
    $('ap-result-accuracy').textContent = result.accuracyPercent == null ? `${label}: —. Проверенных ответов нет.` : `${label}: ${result.accuracyPercent}%. Раскрытия и пропуски не входят в точность.`;
    const mistakes = result.items.filter(i => ['INCORRECT', 'REVEALED'].includes(i.outcome));
    $('ap-mistakes').replaceChildren(...(mistakes.length ? mistakes.map(i => reviewItem(i, state.id)) : [element('p', 'В сохранённых ответах нет ошибок или раскрытий.')]));
    $('ap-all-results').replaceChildren(...result.items.map(i => reviewItem(i, state.id))); status('ap-message', 'Занятие и результаты сохранены на сервере.');
  }
  async function run(op) {
    if (busy) return; pending = op; busy = true; remember(); controls(); status('ap-message', 'Загружаем…'); let focus = null;
    try {
      let url, method = 'GET';
      if (op.action === 'availability') url = availabilityUrl(op.options);
      else if (op.action === 'start') { url = `${base}/sessions`; method = 'POST'; }
      else { url = `${base}/sessions/${op.sessionId}/${op.action === 'load' ? 'question' : op.action}`; if (!['load', 'results'].includes(op.action)) method = 'POST'; }
      const data = await api(url, { method, body: op.body }); pending = null; remember();
      if (op.action === 'availability') showAvailability(data);
      else if (op.action === 'results') { showResults(data); focus = $('ap-results-title'); }
      else {
        showQuestion(data);
        if (!data.currentQuestion) {
          pending = { action: 'results', sessionId: data.id }; remember();
          const result = await api(`${base}/sessions/${data.id}/results`); pending = null; remember(); showResults(result); focus = $('ap-results-title');
        } else focus = data.currentQuestion.feedback ? $('ap-next') : data.settings.exerciseType === 'TYPED' ? $('ap-answer') : $('ap-choice-options').querySelector('input');
      }
    } catch (error) {
      if (error.definite) {
        if (error.code === 'SESSION_NOT_FOUND') { sessionId = null; state = null; pending = null; clearAudio(); $('ap-training').hidden = true; $('ap-settings').hidden = false; history.replaceState(null, '', '/aorist.html#practice'); }
        else if (['STALE_QUESTION', 'ANSWER_CONFLICT', 'ANSWER_REVEALED', 'ANSWER_ALREADY_SUBMITTED', 'SESSION_CLOSED'].includes(error.code)) pending = { action: 'load', sessionId: op.sessionId };
        else { pending = null; if (op.action === 'start') { availability = null; $('ap-availability').textContent = 'Проверьте доступность ещё раз.'; } }
      }
      remember(); status('ap-message', `${error.message}${pending ? ' Нажмите «Повторить запрос», чтобы безопасно восстановить результат.' : ''}`, true); focus = $('ap-message');
    } finally { busy = false; controls(); focus?.focus(); }
  }
  async function loadSessions() {
    if (sessionsBusy) return; sessionsBusy = true; controls(); status('ap-sessions-message', 'Загружаем незавершённые занятия…');
    try {
      const data = await api(`${base}/sessions?size=100`); $('ap-sessions').replaceChildren();
      for (const s of data.content) { const row = element('p'); const resume = button(`Продолжить: ${types[s.settings.exerciseType]}, вопрос ${s.currentPosition} из ${s.totalQuestions}`, () => { if (!busy && !pending) run({ action: 'load', sessionId: s.id }); }); row.append(resume); $('ap-sessions').append(row); }
      status('ap-sessions-message', data.totalElements ? `Незавершённых занятий: ${data.totalElements}.${data.totalElements > data.content.length ? ' Показаны последние 100.' : ''}` : 'Незавершённых занятий пока нет.');
    } catch (error) { status('ap-sessions-message', error.message, true); }
    finally { sessionsBusy = false; controls(); }
  }
  $('ap-options').addEventListener('submit', event => { event.preventDefault(); if (!busy && !pending) try { run({ action: 'availability', options: settings() }); } catch (error) { status('ap-message', error.message, true); } });
  $('ap-options').addEventListener('input', () => { availability = null; $('ap-availability').textContent = 'Параметры изменены. Проверьте доступность.'; controls(); });
  $('ap-person-mode').addEventListener('change', controls);
  $('ap-start').addEventListener('click', () => { if (busy || pending || !availability?.offeredSize) return; try { run({ action: 'start', body: { requestId: crypto.randomUUID(), ...settings(), allowShorter: availability.shorterRequired } }); } catch (error) { status('ap-message', error.message, true); } });
  $('ap-answer').addEventListener('input', () => { if (state?.currentQuestion && !state.currentQuestion.feedback) { draft = { questionId: state.currentQuestion.id, text: $('ap-answer').value }; remember(); } });
  $('ap-answer-form').addEventListener('submit', event => {
    event.preventDefault(); if (busy || pending || !state?.currentQuestion || state.currentQuestion.feedback) return;
    const body = { questionId: state.currentQuestion.id };
    if (state.settings.exerciseType === 'TYPED') { body.answer = $('ap-answer').value; if (!body.answer.trim()) { status('ap-message', 'Введите форму на болгарском.', true); return; } }
    else { body.optionId = $('ap-choice-options').querySelector('input:checked')?.value; if (!body.optionId) { status('ap-message', 'Выберите один вариант.', true); return; } }
    run({ action: 'answers', sessionId, body });
  });
  $('ap-training').addEventListener('keydown', event => {
    if (event.key !== 'Enter') return;
    if (event.repeat || event.isComposing) { event.preventDefault(); return; }
    if (state?.currentQuestion?.feedback && (event.target === $('ap-answer') || event.target.name === 'ap-option')) { event.preventDefault(); if (!busy && !pending) run({ action: 'next', sessionId, body: { questionId: state.currentQuestion.id } }); }
  });
  $('ap-reveal').addEventListener('click', () => { if (!busy && !pending && state?.currentQuestion && !state.currentQuestion.feedback) run({ action: 'reveal', sessionId, body: { questionId: state.currentQuestion.id } }); });
  $('ap-next').addEventListener('click', () => { if (!busy && !pending && state?.currentQuestion?.feedback) run({ action: 'next', sessionId, body: { questionId: state.currentQuestion.id } }); });
  $('ap-end').addEventListener('click', () => { if (!busy && !pending && state?.status === 'IN_PROGRESS') run({ action: 'complete', sessionId }); });
  $('ap-retry').addEventListener('click', () => { if (pending) run(pending); }); $('ap-resume-last').addEventListener('click', () => { if (!busy && !pending && sessionId) run({ action: 'load', sessionId }); }); $('ap-sessions-refresh').addEventListener('click', loadSessions);
  $('ap-new').addEventListener('click', () => { if (busy || pending) return; state = null; draft = null; availability = null; remember(); clearAudio(); $('ap-settings').hidden = false; $('ap-results').hidden = $('ap-training').hidden = true; $('ap-details').replaceChildren(); $('ap-mistakes').replaceChildren(); $('ap-all-results').replaceChildren(); history.replaceState(null, '', '/aorist.html#practice'); status('ap-message', 'Выберите параметры нового занятия.'); $('ap-availability').textContent = 'Проверьте доступность нового занятия.'; controls(); loadSessions(); $('ap-type').focus(); });
  if (pending) { if (pending.action === 'start') restoreOptions(pending.body); status('ap-message', 'Остался незавершённый запрос. Повторите его для безопасного восстановления.', true); }
  else if (requested) run({ action: 'load', sessionId: requested });
  controls();
  return { active: () => !!requested || !!pending, show() { if (!state && !pending) loadSessions(); }, hide() { pronunciation.stop(); }, refreshAvailability() { availability = null; controls(); } };
}
