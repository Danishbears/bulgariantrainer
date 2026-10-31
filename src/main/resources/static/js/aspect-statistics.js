import { $, api, status, element } from './aspect-common.js';

export function mountStatistics() {
  let busy = false, loaded = false;
  const number = value => value.toLocaleString('ru-RU');
  async function refresh() {
    if (busy) return;
    busy = true; $('aspect-statistics-refresh').disabled = true;
    $('aspect-statistics-content').setAttribute('aria-busy', 'true'); status('aspect-statistics-message', 'Загружаем статистику видовых пар…');
    try {
      const data = await api('/api/aspect-pair-practice/statistics');
      for (const [field, id] of [['answered', 'answered'], ['correct', 'correct'], ['incorrect', 'incorrect'], ['dueForReview', 'due']])
        $(`aspect-stat-${id}`).textContent = number(data[field]);
      $('aspect-stat-revealed').textContent = `Раскрыто: ${number(data.revealed)}. Эти исходы не засчитаны как самостоятельные ответы.`;
      $('aspect-stat-due-detail').textContent = `К повторению: ${number(data.dueForReview)} активных пар, ${number(data.dueDirections)} направлений. Одна пара учитывается один раз, даже если наступил срок обоих направлений.`;
      $('aspect-stat-directions').replaceChildren(...data.directions.map(row => {
        const section = element('article');
        section.append(element('h4', row.direction === 'IMPERFECTIVE_TO_PERFECTIVE' ? 'НСВ → СВ' : 'СВ → НСВ'),
          element('p', `Ответов: ${number(row.answered)}. Верно: ${number(row.correct)}. Не совпало: ${number(row.incorrect)}. Раскрыто: ${number(row.revealed)}. К повторению: ${number(row.dueForReview)}.`));
        return section;
      }));
      loaded = true; status('aspect-statistics-message', data.answered || data.revealed ? 'Статистика видовых пар обновлена.' : 'Попыток пока нет. Начните практику, чтобы появились результаты.');
    } catch (error) {
      status('aspect-statistics-message', `${error.message}${loaded ? ' Показаны последние загруженные данные.' : ' Нажмите «Обновить» для повторной попытки.'}`, true);
    } finally {
      busy = false; $('aspect-statistics-refresh').disabled = false; $('aspect-statistics-content').setAttribute('aria-busy', 'false');
    }
  }
  $('aspect-statistics-refresh').addEventListener('click', refresh);
  return { show: refresh };
}
