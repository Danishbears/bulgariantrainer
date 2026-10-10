const status = document.querySelector('#connection-status');
const button = document.querySelector('#check-connection');

async function checkConnection() {
    button.disabled = true;
    status.dataset.state = 'loading';
    status.textContent = 'Проверяем соединение…';
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);

    try {
        const response = await fetch('/api/health', {
            headers: { Accept: 'application/json' },
            cache: 'no-store',
            signal: controller.signal,
        });
        if (!response.ok) throw new Error('Health request failed');
        const health = await response.json();
        if (health.status !== 'UP' || health.application !== 'bulgariantrainer') {
            throw new Error('Unexpected health response');
        }
        status.dataset.state = 'success';
        status.textContent = 'Соединение установлено. Приложение работает.';
    } catch {
        status.dataset.state = 'error';
        status.textContent = 'Нет связи с приложением. Убедитесь, что оно запущено, и повторите проверку.';
    } finally {
        clearTimeout(timeout);
        button.disabled = false;
    }
}

button.addEventListener('click', checkConnection);
checkConnection();

const overview = document.querySelector('#overview');
const overviewStatus = document.querySelector('#overview-status');
const refresh = document.querySelector('#refresh-overview');

async function statistics() {
    const response = await fetch('/api/statistics', {
        headers: { Accept: 'application/json' }, cache: 'no-store', signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) throw new Error('Statistics request failed');
    const data = await response.json();
    if (!data.vocabulary || !Number.isSafeInteger(data.vocabulary.active) || !Number.isSafeInteger(data.vocabulary.dueItems)) throw new Error('Invalid statistics');
    return data.vocabulary;
}

async function loadOverview() {
    refresh.disabled = true;
    overview.setAttribute('aria-busy', 'true');
    overviewStatus.dataset.state = 'loading';
    overviewStatus.textContent = 'Загружаем данные словаря…';
    document.querySelector('#active-count').textContent = '—';
    document.querySelector('#archive-count').textContent = '—';
    document.querySelector('#due-count').textContent = '—';
    document.querySelector('#dictionary-empty').hidden = true;
    try {
        const { active, archived, dueItems } = await statistics();
        document.querySelector('#active-count').textContent = active.toLocaleString('ru-RU');
        document.querySelector('#archive-count').textContent = archived.toLocaleString('ru-RU');
        document.querySelector('#due-count').textContent = dueItems.toLocaleString('ru-RU');
        overviewStatus.dataset.state = 'success';
        overviewStatus.textContent = 'Данные словаря обновлены.';
        if (active === 0) {
            document.querySelector('#dictionary-empty').hidden = false;
            document.querySelector('#empty-title').textContent = archived ? 'Активных слов пока нет' : 'Начните с первого слова';
            document.querySelector('#empty-description').textContent = archived
                ? 'В архиве есть сохранённые записи. Откройте словарь, выберите архив и восстановите нужные слова или добавьте новые.'
                : 'Добавьте слово вручную или перенесите записи из вашего документа. Всё начнётся с вашего собственного словаря.';
        }
    } catch {
        overviewStatus.dataset.state = 'error';
        overviewStatus.textContent = 'Не удалось загрузить данные. Проверьте связь с приложением и нажмите «Обновить». Вместо недоступных данных показано тире.';
    } finally {
        overview.setAttribute('aria-busy', 'false');
        refresh.disabled = false;
    }
}
refresh.addEventListener('click', loadOverview);
loadOverview();
