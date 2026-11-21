import { $, api, element, status, persons, types } from './aorist-common.js';

export function mountStatistics() {
  let busy = false, loaded = false;
  const number = value => value.toLocaleString('ru-RU');
  async function refresh(event) {
    event?.preventDefault();
    if (busy) return;
    busy = true; $('as-refresh').disabled = true; $('as-dataset').disabled = true;
    $('as-content').setAttribute('aria-busy', 'true'); status('as-message', 'Загружаем статистику аориста…');
    const dataset = $('as-dataset').value.trim();
    try {
      const data = await api(`/api/aorist-practice/statistics${dataset ? `?datasetId=${encodeURIComponent(dataset)}` : ''}`);
      $('as-content').replaceChildren(...data.exercises.map(mode => {
        const section = element('section', undefined, 'panel'); section.append(element('h3', types[mode.exerciseType]));
        section.append(element('p', `Ответов: ${number(mode.answered)}. Верно: ${number(mode.correct)}. Не совпало: ${number(mode.incorrect)}. Раскрыто: ${number(mode.revealed)}.`));
        section.append(element('p', `Точность: ${mode.answered ? `${Math.round(mode.correct * 100 / mode.answered)} %` : 'пока нет ответов'}. К повторению: ${number(mode.dueForReview)} вопросов, ${number(mode.dueVerbs)} глаголов.`));
        const table = element('table', undefined, 'aorist-table'); table.append(element('caption', 'Результаты по лицам'));
        const head = element('thead'), header = element('tr');
        for (const title of ['Лицо', 'Результаты']) { const cell = element('th', title); cell.scope = 'col'; header.append(cell); }
        head.append(header); table.append(head); const body = element('tbody');
        for (const row of mode.persons) {
          const tr = element('tr'), label = element('th', persons[row.person]); label.scope = 'row';
          tr.append(label, element('td', `Ответов: ${number(row.answered)}; верно: ${number(row.correct)}; не совпало: ${number(row.incorrect)}; раскрыто: ${number(row.revealed)}; к повторению: ${number(row.dueForReview)}.`)); body.append(tr);
        }
        table.append(body); section.append(table, element('p', 'Срок учитывается только для активных глаголов. Один глагол может иметь несколько вопросов по лицам; расписания типов упражнений независимы.', 'note')); return section;
      }));
      loaded = true;
      status('as-message', `${dataset ? `Набор: ${dataset}.` : 'Все наборы.'} ${data.exercises.some(mode => mode.answered || mode.revealed) ? 'Статистика обновлена.' : 'Попыток пока нет. Начните практику.'} Данные на ${new Date(data.asOf).toLocaleString('ru-RU')}.`);
    } catch (error) { status('as-message', `${error.message} ${loaded ? 'Показаны последние загруженные данные; фильтр к ним не применён.' : 'Нажмите «Обновить» для повторной попытки.'}`, true); }
    finally { busy = false; $('as-refresh').disabled = false; $('as-dataset').disabled = false; $('as-content').setAttribute('aria-busy', 'false'); }
  }
  $('as-filter').addEventListener('submit', refresh);
  return { show: refresh };
}
