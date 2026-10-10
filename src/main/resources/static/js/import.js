const $ = (selector) => document.querySelector(selector);
const fileInput = $('#vocabulary-file');
$('#choose-file').addEventListener('click', () => fileInput.click());
fileInput.addEventListener('change', () => {
    $('#file-name').textContent = fileInput.files[0]?.name || 'Файл не выбран';
});
const warningLabels = {
    EMPTY_RECORD: 'Пустая запись.',
    NO_SEPARATOR: 'Не найдена пара. Возможно, это заголовок или фраза без перевода.',
    MISSING_VALUE: 'Не хватает слова или перевода.',
    VALUE_TOO_LONG: 'Слово или перевод длиннее 255 символов либо часть речи длиннее 100.',
    MULTIPLE_SEPARATORS: 'Несколько разделителей: проверьте, не объединены ли разные пары.',
    LANGUAGE_REVIEW: 'Проверьте языки и смешение алфавитов. Стороны не переставлялись.',
    ALTERNATIVES: 'Есть варианты через косую черту. Они оставлены одним выражением.',
    COMMA_OR_LIST: 'Есть запятые или перечисление. Текст оставлен целиком.',
    ANNOTATION: 'В тексте могут быть примечания. Проверьте пару.',
    MULTILINE_VALUE: 'Поле занимает несколько строк. Проверьте объединённый текст.',
    CONTROL_CHARACTER: 'Недопустимый управляющий символ. Исправьте текст.',
    CSV_HEADER: 'Заголовок CSV, не словарная запись.',
    UNSUPPORTED_CSV_HEADER: 'Не распознаны заголовки CSV. Нужны bulgarian,russian и, при необходимости, partOfSpeech.',
    CSV_SYNTAX: 'Некорректные кавычки в CSV. Исходная запись сохранена для проверки.',
    CSV_COLUMN_COUNT: 'Количество полей не совпадает с заголовком. Проверьте кавычки вокруг запятых.',
    DUPLICATE_IN_FILE: 'Это слово повторяется в документе.',
    DUPLICATE_IN_DATABASE: 'Такое слово уже есть в словаре.',
};
const errorLabels = {
    INVALID_UTF8: 'Не удалось прочитать кодировку. Сохраните документ в UTF-8 и загрузите заново.',
    FILE_TOO_LARGE: 'Файл слишком большой. Максимальный размер — 1 МБ.',
    TOO_MANY_ENTRIES: 'В документе больше 2000 записей. Разделите его на несколько файлов.',
    UNSUPPORTED_FILE: 'Выберите файл с расширением .txt или .csv.',
    INVALID_UPLOAD: 'Не удалось прочитать файл. Выберите его повторно.',
    PREVIEW_EXPIRED: 'Предпросмотр устарел. Загрузите документ повторно. Если связь прервалась при сохранении, проверьте повторы в новом предпросмотре.',
    IMPORT_ALREADY_CONFIRMED: 'Этот предпросмотр уже подтверждён. Для новых изменений загрузите документ снова.',
    IMPORT_TARGET_CHANGED: 'Слово для добавления перевода изменилось. Откройте новый предпросмотр.',
    WORD_NOT_FOUND: 'Слово для добавления перевода больше не найдено. Откройте новый предпросмотр.',
    INVALID_IMPORT_ROW: 'Исправьте выбранные пары или снимите с них отметки. Ничего не сохранено.',
    VALIDATION_FAILED: 'Проверьте выбранные строки и длину полей. Ничего не сохранено.',
    INVALID_REQUEST: 'Не удалось сохранить записи. Проверьте поля; у слова может быть не более 50 переводов.',
    DATA_CONFLICT: 'Словарь изменился или возник конфликт. Ничего не сохранено; откройте новый предпросмотр.',
};
const outcomeLabels = {
    CREATED: 'Слово создано',
    TRANSLATION_ADDED: 'Перевод добавлен',
    DUPLICATE_SKIPPED: 'Повтор пропущен',
    TRANSLATION_EXISTS_SKIPPED: 'Перевод уже существует, запись пропущена',
    DESELECTED: 'Запись не выбрана или пропущена',
};
let preview = null;
let rows = [];
let page = 0;
let busy = false;
let completed = false;
let pendingConfirmation = null;
const pageSize = 25;

function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
}

function message(text, error = false) {
    $('#message').textContent = text;
    $('#message').dataset.state = error ? 'error' : 'success';
}

function key(text) {
    return text.normalize('NFC').replace(/\s+/gu, ' ').trim().toLowerCase();
}

function lockControls() {
    $('#upload-fields').disabled = busy || pendingConfirmation !== null;
    $('#preview-fields').disabled = busy || completed || pendingConfirmation !== null;
    $('#confirm-import').disabled = busy || completed || !rows.some(row => row.selected);
    $('#confirm-import').textContent = pendingConfirmation && !busy
        ? 'Повторить подтверждение' : busy ? 'Пожалуйста, подождите…' : 'Подтвердить импорт';
}

function updateCount() {
    $('#selection-count').textContent = 'Выбрано: ' + rows.filter(row => row.selected).length + ' из ' + rows.length + '.';
    lockControls();
}

function visibleRows() {
    const filter = $('#row-filter').value;
    return rows.filter(row => filter === 'all' || (filter === 'selected' ? row.selected : row.original.status !== 'READY'));
}

function renderRows() {
    const filtered = visibleRows();
    const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
    page = Math.min(page, pages - 1);
    $('#preview-rows').replaceChildren();
    for (const row of filtered.slice(page * pageSize, (page + 1) * pageSize)) {
        const original = row.original;
        const tr = element('tr');
        tr.dataset.status = original.status;
        const pickCell = element('td');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.checked = row.selected;
        checkbox.setAttribute('aria-label', 'Выбрать строку ' + original.lineNumber);
        checkbox.addEventListener('change', () => {
            row.selected = checkbox.checked;
            updateCount();
        });
        pickCell.append(checkbox);
        const sourceCell = element('td', 'Строка ' + original.lineNumber
            + (original.endLineNumber !== original.lineNumber ? '–' + original.endLineNumber : ''));
        const details = element('details');
        details.append(element('summary', 'Исходный текст'), element('pre', original.sourceLine || '(пустая строка)'));
        sourceCell.append(details);
        tr.append(pickCell, sourceCell);
        for (const [field, label] of [['bulgarianText', 'Болгарский'], ['russianText', 'Русский'], ['partOfSpeech', 'Часть речи']]) {
            const cell = element('td');
            const input = document.createElement(field === 'partOfSpeech' ? 'input' : 'textarea');
            if (field === 'partOfSpeech') input.type = 'text';
            input.value = row[field] || '';
            input.setAttribute('aria-label', label + ', строка ' + original.lineNumber);
            input.addEventListener('input', () => { row[field] = input.value; });
            cell.append(input);
            tr.append(cell);
        }
        const actionCell = element('td');
        const warnings = element('ul', undefined, 'warnings');
        for (const code of original.warnings) warnings.append(element('li', warningLabels[code] || 'Проверьте запись.'));
        if (original.duplicateOfRowId !== null) {
            const first = rows.find(item => item.original.rowId === original.duplicateOfRowId);
            warnings.append(element('li', 'Первое появление: строка ' + first.original.lineNumber + '.'));
        }
        actionCell.append(warnings);
        if (original.existingWordId !== null) {
            actionCell.append(element('p', 'В словаре: «' + original.bulgarianText + '» — '
                + original.existingTranslations.join('; '), 'existing'));
        }
        const action = element('select');
        action.setAttribute('aria-label', 'Действие, строка ' + original.lineNumber);
        for (const [value, label] of [['CREATE', 'Создать, если слова ещё нет'], ['SKIP', 'Пропустить запись']]) {
            const option = element('option', label); option.value = value; action.append(option);
        }
        if (original.existingWordId !== null) {
            const option = element('option', 'Добавить перевод к «' + original.bulgarianText + '»');
            option.value = 'ADD_TRANSLATION';
            action.append(option);
        }
        action.value = row.action;
        action.addEventListener('change', () => { row.action = action.value; });
        actionCell.append(action);
        tr.append(actionCell);
        $('#preview-rows').append(tr);
    }
    $('#page-label').textContent = 'Страница ' + (page + 1) + ' из ' + pages + ' · Записей: ' + filtered.length;
    $('#previous-page').disabled = page === 0;
    $('#next-page').disabled = page >= pages - 1;
    updateCount();
}

async function request(url, options) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000);
    try {
        const response = await fetch(url, { ...options, signal: controller.signal });
        const data = await response.json();
        if (!response.ok) {
            const error = new Error(errorLabels[data.code] || 'Ошибка приложения. Повторите попытку.');
            error.definite = true;
            const rowNumber = data.code === 'INVALID_IMPORT_ROW' && data.message?.match(/row (\d+)/);
            if (rowNumber) error.message += ' Номер записи: ' + rowNumber[1] + '.';
            throw error;
        }
        return data;
    } finally {
        clearTimeout(timer);
    }
}

fileInput.addEventListener('change', () => {
    preview = null; rows = []; completed = false; pendingConfirmation = null;
    $('#preview').hidden = true;
    $('#result').hidden = true;
    message('');
    lockControls();
});

$('#upload-form').addEventListener('submit', async event => {
    event.preventDefault();
    const file = fileInput.files[0];
    if (!file) { message('Выберите документ со словами перед предпросмотром.', true); return; }
    if (file.size > 1024 * 1024) { message(errorLabels.FILE_TOO_LARGE, true); return; }
    if (!/\.(txt|csv)$/i.test(file.name)) { message(errorLabels.UNSUPPORTED_FILE, true); return; }
    busy = true; completed = false; pendingConfirmation = null;
    $('#preview').hidden = true; $('#result').hidden = true;
    lockControls();
    message('Читаем документ. Слова пока не сохраняются…');
    try {
        const form = new FormData();
        form.append('file', file);
        preview = await request('/api/imports/preview', { method: 'POST', body: form });
        rows = preview.entries.map(original => ({
            original, selected: original.suggestedSelected,
            bulgarianText: original.bulgarianText, russianText: original.russianText,
            partOfSpeech: original.partOfSpeech || '', action: original.existingWordId !== null ? 'SKIP' : 'CREATE',
        }));
        page = 0;
        $('#row-filter').value = 'all';
        const warnings = rows.filter(row => row.original.status === 'WARNING').length;
        const invalid = rows.filter(row => row.original.status === 'INVALID').length;
        const empty = rows.filter(row => row.original.status === 'EMPTY').length;
        $('#preview-description').textContent = preview.filename + ' · Записей: ' + rows.length
            + '. С предупреждениями: ' + warnings + '. Некорректных: ' + invalid + '. Пустых: ' + empty + '.';
        $('#preview').hidden = false;
        renderRows();
        message('Предпросмотр готов. Проверьте языки и предупреждения перед сохранением.');
    } catch (error) {
        message(error.definite ? error.message : 'Нет связи с приложением. Проверьте, что оно запущено, и повторите предпросмотр.', true);
    } finally {
        busy = false; lockControls();
    }
});

$('#row-filter').addEventListener('change', () => { page = 0; renderRows(); });
$('#previous-page').addEventListener('click', () => { page--; renderRows(); });
$('#next-page').addEventListener('click', () => { page++; renderRows(); });
$('#select-safe').addEventListener('click', () => {
    rows.forEach(row => { row.selected = row.original.suggestedSelected; });
    renderRows();
});
$('#deselect-all').addEventListener('click', () => {
    rows.forEach(row => { row.selected = false; }); renderRows();
});

function invalidRow(row) {
    if (!row.selected || row.action === 'SKIP') return false;
    if (!key(row.bulgarianText) || !key(row.russianText) || row.bulgarianText.length > 255
        || row.russianText.length > 255 || row.partOfSpeech.length > 100) return true;
    return row.action === 'ADD_TRANSLATION' && key(row.bulgarianText) !== key(row.original.bulgarianText);
}

$('#confirm-import').addEventListener('click', async () => {
    if (!preview || busy || completed) return;
    if (!pendingConfirmation) {
        const invalid = rows.find(invalidRow);
        if (invalid) {
            $('#row-filter').value = 'all';
            page = Math.floor(rows.indexOf(invalid) / pageSize); renderRows();
            message('Исправьте строку ' + invalid.original.lineNumber
                + ' или снимите отметку. Для добавления перевода болгарское слово должно совпадать с найденным.', true);
            return;
        }
        pendingConfirmation = JSON.stringify({
            importId: preview.importId,
            entries: rows.map(row => ({
                rowId: row.original.rowId, selected: row.selected, action: row.action,
                bulgarianText: row.bulgarianText, russianText: row.russianText,
                partOfSpeech: row.partOfSpeech || null, existingWordId: row.original.existingWordId,
            })),
        });
    }
    busy = true; lockControls();
    message('Сохраняем выбранные записи…');
    try {
        const summary = await request('/api/imports/confirm', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: pendingConfirmation,
        });
        completed = true; pendingConfirmation = null;
        $('#result-summary').textContent = 'Создано слов: ' + summary.createdWords
            + '. Добавлено переводов: ' + summary.addedTranslations + '. Пропущено записей: ' + summary.skippedEntries + '.';
        $('#result-rows').replaceChildren();
        for (const item of summary.entries) {
            const li = element('li', 'Строка ' + item.lineNumber + ': ' + outcomeLabels[item.outcome] + '.');
            li.append(element('pre', item.sourceLine || '(пустая строка)'));
            $('#result-rows').append(li);
        }
        $('#result').hidden = false;
        message('Импорт завершён. Существующие слова и история обучения сохранены.');
        $('#result').scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
    } catch (error) {
        if (error.definite) {
            pendingConfirmation = null;
            message(error.message, true);
        } else {
            message('Связь прервалась. Результат сохранения пока неизвестен. Нажмите «Повторить подтверждение»: повторный запрос безопасен. Поля временно заблокированы.', true);
        }
    } finally {
        busy = false; lockControls();
    }
});
