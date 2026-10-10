const $ = id => document.getElementById(id);
const labels = { noun: 'Существительное', verb: 'Глагол', adjective: 'Прилагательное', adverb: 'Наречие', pronoun: 'Местоимение', preposition: 'Предлог', conjunction: 'Союз', interjection: 'Междометие', numeral: 'Числительное', masculine: 'Мужской', feminine: 'Женский', neuter: 'Средний' };
const display = text => labels[text?.toLowerCase()] || text;
let page = 0, totalPages = 0, editingId = null, requestNumber = 0, saving = false;
let filters = { query: '', part: '', archived: 'false', size: '20' };

function message(id, text, error = false) {
  $(id).textContent = text;
  $(id).dataset.state = error ? 'error' : 'success';
}
async function api(url, options = {}) {
  let response;
  try { response = await fetch(url, { ...options, headers: { 'Content-Type': 'application/json', ...options.headers }, signal: AbortSignal.timeout(15000) }); }
  catch { throw new Error('Нет связи с сервером. Проверьте подключение и повторите попытку. Введённые данные сохранены на странице.'); }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const errors = {
      DUPLICATE_WORD: 'Такое болгарское слово уже есть. Проверьте также архив и измените существующую запись.',
      WORD_NOT_FOUND: 'Слово больше не найдено. Обновите список.',
      INVALID_REQUEST: 'Проверьте заполнение полей: переводы не должны повторяться, основной перевод должен быть один.',
      VALIDATION_FAILED: 'Проверьте обязательные поля и длину текста.',
      DATA_CONFLICT: 'Запись изменилась или уже существует. Обновите список и повторите попытку.'
    };
    throw new Error(errors[body.code] || 'Не удалось выполнить запрос. Повторите попытку.');
  }
  return response.json();
}
function button(text, action, secondary = true) {
  const element = document.createElement('button');
  element.type = 'button'; element.textContent = text;
  if (secondary) element.className = 'secondary';
  element.addEventListener('click', action);
  return element;
}
function paragraph(text, className = '') {
  const element = document.createElement('p'); element.textContent = text; element.className = className; return element;
}
async function loadParts() {
  try {
    const parts = await api('/api/words/parts-of-speech');
    const selected = $('part-filter').value;
    $('part-filter').replaceChildren(new Option('Все части речи', ''));
    for (const part of parts) $('part-filter').add(new Option(display(part), part));
    $('part-filter').value = selected;
  } catch { message('message', 'Не удалось загрузить части речи. Повторите загрузку.', true); $('retry').hidden = false; }
}
async function loadWords(notice = '') {
  const version = ++requestNumber;
  $('results').setAttribute('aria-busy', 'true');
  $('results').replaceChildren();
  $('previous').disabled = true; $('next').disabled = true; $('retry').hidden = true;
  message('message', 'Загрузка слов…');
  const params = new URLSearchParams({ page, size: filters.size, archived: filters.archived });
  if (filters.part) params.set('partOfSpeech', filters.part);
  if (filters.query) params.set('q', filters.query);
  try {
    const data = await api(`/api/words${filters.query ? '/search' : ''}?${params}`);
    if (version !== requestNumber) return;
    if (page > 0 && page >= data.totalPages) { page = Math.max(0, data.totalPages - 1); return loadWords(notice); }
    totalPages = data.totalPages;
    for (const word of data.content) renderWord(word);
    if (!data.totalElements) {
      const empty = document.createElement('div'); empty.className = 'empty-state';
      const heading = document.createElement('h2'); heading.textContent = filters.query || filters.part ? 'Нет совпадений' : filters.archived === 'true' ? 'Архив пока пуст' : 'Здесь появятся ваши слова';
      empty.append(heading, paragraph(filters.query || filters.part ? 'Попробуйте другой запрос или выберите все части речи.' : filters.archived === 'true' ? 'Перенесённые в архив слова сохранят переводы и историю обучения.' : 'Добавьте первое слово вручную или импортируйте свой документ.'));
      if (!filters.query && !filters.part && filters.archived === 'false') {
        const link = document.createElement('a'); link.href = '/import.html'; link.className = 'button secondary'; link.textContent = 'Импортировать слова'; empty.append(link);
      }
      $('results').append(empty);
    }
    message('message', [notice, data.totalElements ? `Найдено записей: ${data.totalElements}.` : 'Слова не найдены. Измените фильтры или добавьте новое слово.'].filter(Boolean).join(' '));
    $('page-info').textContent = totalPages ? `Страница ${page + 1} из ${totalPages}` : 'Нет записей';
    $('previous').disabled = page === 0; $('next').disabled = page + 1 >= totalPages;
  } catch (error) {
    if (version !== requestNumber) return;
    message('message', error.message, true); $('retry').hidden = false; $('page-info').textContent = '';
  } finally { if (version === requestNumber) $('results').setAttribute('aria-busy', 'false'); }
}
function renderWord(word) {
  const card = document.createElement('article'); card.className = 'word-card';
  const title = document.createElement('h2'); title.textContent = word.bulgarianText; title.lang = 'bg'; card.append(title);
  const translations = document.createElement('ul');
  for (const translation of word.translations) {
    const item = document.createElement('li'); item.textContent = translation.russianText + (translation.isPrimary ? ' — основной' : ''); translations.append(item);
  }
  card.append(translations);
  const metadata = [display(word.partOfSpeech), display(word.grammaticalGender)].filter(Boolean).join(' · ');
  if (metadata) card.append(paragraph(metadata, 'note'));
  if (word.notes) card.append(paragraph(word.notes));
  const actions = document.createElement('div'); actions.className = 'toolbar';
  actions.append(button('Редактировать', async event => {
    if (saving) return;
    event.target.disabled = true;
    try { openEditor(await api(`/api/words/${word.id}`)); }
    catch (error) { message('message', error.message, true); }
    finally { event.target.disabled = false; }
  }));
  actions.append(button(word.archived ? 'Восстановить' : 'В архив', async event => {
    event.target.disabled = true;
    try {
      await api(`/api/words/${word.id}/archive`, { method: 'PATCH', body: JSON.stringify({ archived: !word.archived }) });
      await loadWords(word.archived ? 'Слово восстановлено.' : 'Слово перемещено в архив.');
    } catch (error) { message('message', error.message, true); event.target.disabled = false; }
  }));
  card.append(actions); $('results').append(card);
}
function addTranslation(text = '', primary = false) {
  const container = $('translations');
  if (container.children.length >= 50) return;
  const row = document.createElement('div'); row.className = 'translation-row';
  const label = document.createElement('label'); label.append('Русский перевод');
  const input = document.createElement('input'); input.required = true; input.maxLength = 255; input.value = text; input.className = 'translation-text'; label.append(input);
  const primaryLabel = document.createElement('label'); primaryLabel.className = 'primary-label';
  const radio = document.createElement('input'); radio.type = 'radio'; radio.name = 'primary'; radio.checked = primary; primaryLabel.append(radio, 'Основной');
  row.append(label, primaryLabel, button('Убрать', () => {
    if (container.children.length === 1) { message('editor-message', 'Нужен хотя бы один перевод.', true); return; }
    row.remove();
    if (!container.querySelector(':checked')) container.querySelector('[type=radio]').checked = true;
    $('add-translation').disabled = false;
  }));
  container.append(row); $('add-translation').disabled = container.children.length >= 50;
  return input;
}
function openEditor(word = null) {
  editingId = word?.id ?? null;
  $('editor-title').textContent = word ? 'Редактирование слова' : 'Новое слово';
  $('bulgarian').value = word?.bulgarianText ?? '';
  $('word-part').value = word?.partOfSpeech ?? '';
  $('gender').value = word?.grammaticalGender ?? '';
  $('notes').value = word?.notes ?? '';
  $('translations').replaceChildren();
  for (const translation of word?.translations ?? [{ russianText: '', isPrimary: true }]) addTranslation(translation.russianText, translation.isPrimary);
  message('editor-message', ''); $('editor').hidden = false; $('bulgarian').focus();
}
$('new-word').addEventListener('click', () => { if (!saving) openEditor(); });
$('cancel-edit').addEventListener('click', () => { $('editor').hidden = true; $('new-word').focus(); });
$('add-translation').addEventListener('click', () => addTranslation()?.focus());
$('word-form').addEventListener('submit', async event => {
  event.preventDefault(); if (saving) return;
  const translations = [...$('translations').children].map(row => ({ russianText: row.querySelector('.translation-text').value.trim(), isPrimary: row.querySelector('[type=radio]').checked }));
  if (!$('bulgarian').value.trim() || translations.some(item => !item.russianText)) { message('editor-message', 'Заполните болгарское слово и все переводы.', true); return; }
  saving = true; $('editor-fields').disabled = true; $('new-word').disabled = true;
  message('editor-message', 'Сохранение…');
  try {
    await api(editingId ? `/api/words/${editingId}` : '/api/words', { method: editingId ? 'PUT' : 'POST', body: JSON.stringify({ bulgarianText: $('bulgarian').value, partOfSpeech: $('word-part').value, grammaticalGender: $('gender').value, notes: $('notes').value, translations }) });
    $('editor').hidden = true; $('new-word').focus();
    await loadWords('Слово сохранено.'); await loadParts();
  } catch (error) { message('editor-message', error.message, true); }
  finally {
    saving = false; $('editor-fields').disabled = false; $('new-word').disabled = false;
    if ($('editor').hidden) $('new-word').focus();
  }
});
$('filters').addEventListener('submit', event => {
  event.preventDefault(); page = 0;
  filters = { query: $('query').value.trim(), part: $('part-filter').value, archived: $('archive-filter').value, size: $('page-size').value };
  loadWords();
});
$('previous').addEventListener('click', () => { if (page > 0) { page--; loadWords(); } });
$('next').addEventListener('click', () => { if (page + 1 < totalPages) { page++; loadWords(); } });
$('retry').addEventListener('click', async () => { await loadWords(); await loadParts(); });
await loadWords();
await loadParts();
