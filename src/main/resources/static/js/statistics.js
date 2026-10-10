const $ = id => document.getElementById(id);
const number = value => value.toLocaleString('ru-RU');
const dateTime = value => value ? new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'UTC',
}).format(new Date(value)) + ' UTC' : '—';
const shortDate = value => new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric', month: 'short', timeZone: 'UTC',
}).format(new Date(`${value}T12:00:00Z`));
const percent = rate => rate.accuracyPercent === null ? '—' : `${number(rate.accuracyPercent)} %`;
const modeNames = { FLASHCARD: 'Карточки', TYPING: 'Ввод перевода', SENTENCE: 'Предложения' };
const statusNames = { IN_PROGRESS: 'В процессе', COMPLETED: 'Завершено', ABANDONED: 'Завершено досрочно' };
const directionNames = { BG_TO_RU: 'Болгарский → русский', RU_TO_BG: 'Русский → болгарский' };
let overview = null;
let period = 7;
let busy = false;
let reviewWordId = null;

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}
async function api(url) {
  let response;
  try { response = await fetch(url, { headers: { Accept: 'application/json' }, cache: 'no-store', signal: AbortSignal.timeout(12000) }); }
  catch { throw new Error('Нет связи с приложением. Проверьте подключение и повторите загрузку.'); }
  if (!response.ok) throw new Error(response.status === 404 ? 'Слово больше не найдено.' : 'Не удалось загрузить статистику. Повторите попытку.');
  return response.json();
}
function setMetric(id, value) { $(id).textContent = number(value); }
function renderMode(name, description, performance) {
  const section = element('article', undefined, 'mode-result');
  section.append(element('h3', name), element('p', description));
  section.append(element('strong', percent(performance.total)));
  section.append(element('p', performance.total.attempts
    ? `${number(performance.total.correct)} правильных из ${number(performance.total.attempts)} ответов`
    : 'Оценённых ответов пока нет'));
  const bar = document.createElement('progress');
  bar.max = 100; bar.value = performance.total.accuracyPercent ?? 0;
  bar.setAttribute('aria-label', `${name}: ${percent(performance.total)}`);
  section.append(bar);
  return section;
}
function renderActivity() {
  const points = period === 7 ? overview.last7Days : overview.last30Days;
  const total = points.reduce((sum, day) => sum + day.reviewAttempts, 0);
  const correct = points.reduce((sum, day) => sum + day.correctAnswers, 0);
  const sessions = points.reduce((sum, day) => sum + day.completedSessions, 0);
  $('activity-summary').textContent = `За ${period} дней: ${number(total)} ответов, ${number(correct)} правильных, ${number(sessions)} завершённых занятий.`;
  $('activity-chart').setAttribute('aria-label', `Ответы за ${period} дней: ${points.map(day => `${shortDate(day.date)} — ${day.reviewAttempts}`).join('; ')}`);
  $('activity-chart').replaceChildren();
  $('activity-empty').hidden = total !== 0 || sessions !== 0;
  if (total === 0 && sessions === 0) return;
  const maximum = Math.max(1, ...points.map(day => day.reviewAttempts));
  points.forEach((day, index) => {
    const column = element('div', undefined, `activity-day ${period === 7 ? 'seven' : ''}`);
    const bar = element('span', undefined, 'activity-bar');
    bar.style.height = `${Math.max(2, Math.round(112 * day.reviewAttempts / maximum))}px`;
    if (!day.reviewAttempts) bar.style.visibility = 'hidden';
    bar.title = `${shortDate(day.date)}: ${day.reviewAttempts} ответов`;
    const label = element('span', period === 7 || index % 5 === 0 || index === points.length - 1 ? shortDate(day.date) : '', 'activity-label');
    column.append(bar, label); $('activity-chart').append(column);
  });
}
function renderDifficult(words) {
  $('difficult-words').replaceChildren();
  $('difficult-empty').hidden = words.length > 0;
  for (const word of words) {
    const row = element('li', undefined, 'difficult-word');
    const details = document.createElement('div');
    const name = element('strong', word.bulgarianText); name.lang = 'bg';
    details.append(name, element('p', `${word.russianTranslations.join('; ')}${word.archived ? ' · в архиве' : ''}`),
      element('p', `Неправильных ответов: ${number(word.incorrectAttempts)}`));
    const open = element('button', 'Разобрать', 'secondary'); open.type = 'button';
    open.addEventListener('click', () => reviewMistakes(word.wordId));
    row.append(details, open); $('difficult-words').append(row);
  }
}
function sessionUrl(session) {
  const page = { FLASHCARD: 'flashcards.html', TYPING: 'typing.html', SENTENCE: 'sentences.html' }[session.trainingMode];
  return page ? `/${page}?session=${session.id}` : null;
}
function renderSessions(sessions) {
  $('recent-sessions').replaceChildren();
  $('recent-empty').hidden = sessions.length > 0;
  for (const session of sessions) {
    const row = element('li');
    const url = sessionUrl(session);
    const title = url ? element('a', `${modeNames[session.trainingMode]} · ${dateTime(session.startedAt)}`)
      : element('strong', `${session.trainingMode} · ${dateTime(session.startedAt)}`);
    if (url) title.href = url;
    row.append(title, element('p', `${statusNames[session.status] || session.status} · ${number(session.answeredQuestions)} из ${number(session.totalQuestions)} ответов · правильных: ${number(session.correctAnswers)}${session.answeredQuestions ? ` (${percent({ accuracyPercent: session.accuracyPercent })})` : ''}`));
    $('recent-sessions').append(row);
  }
}
function render(data) {
  const words = data.vocabulary;
  setMetric('total-vocabulary', words.total);
  $('vocabulary-detail').textContent = `Активных: ${number(words.active)} · в архиве: ${number(words.archived)}`;
  setMetric('new-vocabulary', words.newItems);
  setMetric('due-vocabulary', words.dueItems);
  setMetric('mastered-vocabulary', words.masteredItems);
  setMetric('completed-sessions', data.completedSessions);
  setMetric('review-attempts', data.reviewAttempts);
  $('overall-accuracy').textContent = percent(data.overall.total);
  $('overall-detail').textContent = data.reviewAttempts
    ? `${number(data.overall.total.correct)} правильных из ${number(data.reviewAttempts)}` : 'Оценённых ответов пока нет';
  setMetric('study-streak', data.consecutiveStudyDays);
  setMetric('scheduled-today', data.reviewsScheduledToday);
  $('mode-results').replaceChildren(
    renderMode('Карточки', 'Самооценка узнавания', data.flashcards),
    renderMode('Болгарский → русский', 'Автоматическая проверка ввода', data.typingBgToRu),
    renderMode('Русский → болгарский', 'Автоматическая проверка ввода', data.typingRuToBg),
    renderMode('Перевод предложений', 'Автоматическая проверка и самооценка', data.sentences),
  );
  $('grading-detail').replaceChildren(
    element('h3', 'Как оценивались ответы'),
    element('p', `Автоматически: ${number(data.overall.automatic.attempts)} ответов · правильно ${number(data.overall.automatic.correct)} · ${percent(data.overall.automatic)}`),
    element('p', `Самостоятельно: ${number(data.overall.selfAssessed.attempts)} ответов · правильно ${number(data.overall.selfAssessed.correct)} · ${percent(data.overall.selfAssessed)}`),
  );
  if (data.overall.unclassified.attempts) $('grading-detail').append(element('p', `Без сохранённого способа оценки: ${number(data.overall.unclassified.attempts)} ответов.`));
  renderActivity(); renderDifficult(data.difficultWords); renderSessions(data.recentSessions);
}
async function load() {
  if (busy) return;
  busy = true; $('refresh').disabled = true; $('statistics-content').setAttribute('aria-busy', 'true');
  $('statistics-status').dataset.state = 'loading'; $('statistics-status').textContent = 'Загружаем статистику…';
  try {
    const data = await api('/api/statistics');
    overview = data; render(data);
    $('statistics-status').dataset.state = 'success';
    $('statistics-status').textContent = 'Статистика обновлена.';
  } catch (error) {
    $('statistics-status').dataset.state = 'error';
    $('statistics-status').textContent = `${error.message}${overview ? ' Показаны последние загруженные данные.' : ' Нажмите «Обновить» для повторной попытки.'}`;
  } finally {
    busy = false; $('refresh').disabled = false; $('statistics-content').setAttribute('aria-busy', 'false');
  }
}
async function reviewMistakes(wordId) {
  reviewWordId = wordId;
  $('mistake-review').hidden = false; $('mistake-content').replaceChildren(); $('mistake-retry').hidden = true;
  $('mistake-status').dataset.state = 'loading';
  $('mistake-status').textContent = 'Загружаем неправильные ответы…'; $('mistake-title').focus();
  try {
    const detail = await api(`/api/statistics/words/${wordId}/mistakes`);
    if (reviewWordId !== wordId) return;
    $('mistake-title').textContent = `Разбор: ${detail.bulgarianText}`;
    const Bulgarian = element('p', detail.bulgarianText); Bulgarian.lang = 'bg';
    const translations = element('p', `Текущие переводы: ${detail.currentRussianTranslations.join('; ')}`);
    const note = element('p', `Всего неправильных ответов: ${number(detail.incorrectAttempts)}. Показаны последние ${number(detail.recentMistakes.length)}.`);
    const list = document.createElement('ol');
    for (const mistake of detail.recentMistakes) {
      const mode = mistake.trainingMode === 'FLASHCARD' ? 'Карточка: «Не знаю»' : directionNames[mistake.direction];
      const answer = mistake.trainingMode === 'FLASHCARD' ? 'Самооценка узнавания' : `Ваш ответ: ${mistake.userAnswer || '—'}`;
      list.append(element('li', `${dateTime(mistake.createdAt)} · ${mode} · ${answer}`));
    }
    $('mistake-content').replaceChildren(Bulgarian, translations, note, list);
    $('mistake-status').dataset.state = 'success';
    $('mistake-status').textContent = detail.archived ? 'Это слово находится в архиве.' : 'Сравните свои ответы с текущими переводами слова.';
  } catch (error) {
    if (reviewWordId !== wordId) return;
    $('mistake-status').textContent = error.message;
    $('mistake-status').dataset.state = 'error'; $('mistake-retry').hidden = false;
  }
}
$('refresh').addEventListener('click', load);
$('period-7').addEventListener('click', () => { period = 7; $('period-7').setAttribute('aria-pressed', 'true'); $('period-30').setAttribute('aria-pressed', 'false'); if (overview) renderActivity(); });
$('period-30').addEventListener('click', () => { period = 30; $('period-7').setAttribute('aria-pressed', 'false'); $('period-30').setAttribute('aria-pressed', 'true'); if (overview) renderActivity(); });
$('mistake-retry').addEventListener('click', () => { if (reviewWordId !== null) reviewMistakes(reviewWordId); });
$('close-review').addEventListener('click', () => { reviewWordId = null; $('mistake-review').hidden = true; $('mistake-title').textContent = 'Разбор слова'; });
load();
