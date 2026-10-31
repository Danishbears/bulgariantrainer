// Optional real-Chrome integration test. Run against a disposable application database.
// Uses Node's built-in CDP/WebSocket support; no frontend dependency is required.
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFile, writeFile, mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const base = process.env.ASPECT_BROWSER_BASE_URL;
test('aspect pairs: real browser management, import, practice, recovery and phone layouts', { skip: !base, timeout: 180000 }, async () => {
  assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
  const chromePath = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  const profile = await mkdtemp(join(tmpdir(), 'bulgariantrainer-chrome-'));
  const chrome = spawn(chromePath, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--disable-background-networking', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
  let ws;
  try {
    let port;
    for (let i = 0; i < 150; i++) {
      try { port = (await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; break; } catch { await new Promise(r => setTimeout(r, 100)); }
    }
    assert.ok(port, 'Chrome did not expose its isolated debugging port');
    const tabs = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    ws = new WebSocket(tabs.find(tab => tab.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
    let sequence = 0;
    const waiting = new Map(), exceptions = [];
    let loseNextSaveResponse = false;
    ws.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text);
      if (message.method === 'Fetch.requestPaused') {
        const params = message.params;
        if (loseNextSaveResponse && params.responseStatusCode) {
          loseNextSaveResponse = false;
          call('Fetch.failRequest', { requestId: params.requestId, errorReason: 'ConnectionClosed' })
            .then(() => call('Fetch.disable')).catch(error => exceptions.push(error.message));
        } else call('Fetch.continueRequest', { requestId: params.requestId }).catch(error => exceptions.push(error.message));
      }
      const pending = waiting.get(message.id);
      if (pending) { waiting.delete(message.id); clearTimeout(pending.timer); message.error ? pending.reject(new Error(JSON.stringify(message.error))) : pending.resolve(message.result); }
    };
    const call = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++sequence;
      const timer = setTimeout(() => { waiting.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 18000);
      waiting.set(id, { resolve, reject, timer }); ws.send(JSON.stringify({ id, method, params }));
    });
    const evaluate = async expression => {
      const result = await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
      return result.result.value;
    };
    const wait = async (expression, label = expression) => {
      for (let i = 0; i < 180; i++) { if (await evaluate(expression)) return; await new Promise(r => setTimeout(r, 70)); }
      const screenshot = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
      await writeFile(resolve('target/aspect-pairs-browser-failure.png'), Buffer.from(screenshot.data, 'base64'));
      const debug = await evaluate(`({active:document.activeElement?.id || document.activeElement?.tagName,inputs:[...document.querySelectorAll('#practice-options input, #practice-options select')].map(e=>({id:e.id,value:e.value,valid:e.validity.valid,disabled:e.disabled})),width:innerWidth,height:innerHeight})`);
      throw new Error(`Browser wait failed: ${label}\n${JSON.stringify(debug)}\n${await evaluate('document.body.innerText')}`);
    };
    const text = selector => evaluate(`document.querySelector(${JSON.stringify(selector)})?.innerText || ''`);
    const actionable = selector => wait(`(() => {const e=document.querySelector(${JSON.stringify(selector)}); return !!e && e.getClientRects().length>0 && !e.matches(':disabled');})()`, `Visible enabled control: ${selector}`);
    const fill = async (selector, value) => {
      await actionable(selector);
      return evaluate(`{ const e=document.querySelector(${JSON.stringify(selector)}); e.focus(); e.value=${JSON.stringify(value)}; e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }`);
    };
    const click = async selector => {
      await actionable(selector);
      const point = await evaluate(`{ const e=document.querySelector(${JSON.stringify(selector)}); if(!e || e.disabled) throw new Error('Missing/disabled: '+${JSON.stringify(selector)}); e.scrollIntoView({block:'center'}); const r=e.getBoundingClientRect(); ({x:r.x+r.width/2,y:r.y+r.height/2}); }`);
      await call('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point });
      await call('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point });
      await call('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point });
    };
    const key = async (key, autoRepeat = false) => {
      const windowsVirtualKeyCode = { Enter: 13, Escape: 27, Tab: 9 }[key];
      await call('Input.dispatchKeyEvent', { type: 'keyDown', key, code: key, windowsVirtualKeyCode, autoRepeat,
        ...(key === 'Enter' ? { text: '\r', unmodifiedText: '\r' } : {}) });
      await call('Input.dispatchKeyEvent', { type: 'keyUp', key, code: key, windowsVirtualKeyCode });
    };
    const file = async path => {
      const { root } = await call('DOM.getDocument');
      const { nodeId } = await call('DOM.querySelector', { nodeId: root.nodeId, selector: '#aspect-file' });
      await call('DOM.setFileInputFiles', { nodeId, files: [resolve(path)] });
    };
    const navigate = async path => {
      const previous = await evaluate('performance.timeOrigin');
      await call('Page.navigate', { url: `${base}${path}` });
      await wait(`performance.timeOrigin!==${previous} && document.readyState==='complete' && !!document.querySelector('#aspect-confirm')`);
    };
    const reload = async () => {
      const previous = await evaluate('performance.timeOrigin');
      await call('Page.reload');
      await wait(`performance.timeOrigin!==${previous} && document.readyState==='complete' && !!document.querySelector('#aspect-confirm')`);
    };
    const request = async path => { const response = await fetch(`${base}${path}`); assert.equal(response.status, 200); return response.json(); };
    await call('Page.enable'); await call('Runtime.enable'); await call('Network.enable');
    const legacy = await request('/api/statistics');
    assert.equal((await request('/api/aspect-pairs')).totalElements, 0, 'Use a fresh disposable database');

    await navigate('/aspect-pairs.html#import');
    await file('learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json');
    await click('#aspect-upload-form button[type=submit]');
    await wait("document.querySelector('#aspect-preview-count').textContent.includes('Выбрано: 100')");
    assert.equal((await request('/api/aspect-pairs')).totalElements, 0, 'Preview must not import');
    await call('Network.emulateNetworkConditions', { offline: true, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
    await click('#aspect-confirm');
    await wait("!document.querySelector('#aspect-import-retry').hidden");
    assert.equal(await evaluate("document.querySelector('#aspect-preview-fields').disabled"), true, 'Uncertain confirmations freeze selections');
    await call('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
    await reload();
    await wait("document.readyState==='complete' && !document.querySelector('#aspect-import-retry').hidden");
    await click('#aspect-import-retry');
    await wait("!document.querySelector('#aspect-import-result').hidden");
    assert.match(await text('#aspect-import-summary'), /Добавлено: 100/);
    await click('#aspect-import-result a');
    await wait("document.querySelector('#pairs-message').textContent.includes('Найдено записей: 100')");
    await fill('#pair-filter-size', '10'); await click('#pair-filters button[type=submit]');
    await wait("document.querySelector('#pairs-page').textContent==='Страница 1 из 10'");
    await click('#pairs-next'); await wait("document.querySelector('#pairs-page').textContent==='Страница 2 из 10'");
    await click('#pairs-previous'); await wait("document.querySelector('#pairs-page').textContent==='Страница 1 из 10'");
    await fill('#pair-query', 'купувам'); await click('#pair-filters button[type=submit]');
    await wait("document.querySelector('#pairs-message').textContent==='Найдено записей: 1.'");
    await click('#pair-list .toolbar button:first-child');
    await wait("!document.querySelector('#pair-editor').hidden && !document.querySelector('#pair-editor-fields').disabled");
    await fill('#pair-accepted-imperfective', 'купувам\nзакупувам'); await fill('#pair-note', 'Изменено в проверке браузера.');
    await click('#pair-form button[type=submit]');
    await wait("document.querySelector('#pair-editor').hidden && document.querySelector('#pairs-message').textContent==='Пара сохранена.'");
    assert.match(await text('#pair-list'), /купувам/);
    await click('#pair-list .toolbar button:last-child');
    await wait("document.querySelector('#pairs-message').textContent.includes('перенесена в архив')");
    await fill('#pair-filter-archive', 'true'); await click('#pair-filters button[type=submit]');
    await wait("document.querySelector('#pair-list button:last-child')?.textContent==='Восстановить'");
    await click('#pair-list .toolbar button:last-child');
    await wait("document.querySelector('#pairs-message').textContent==='Пара восстановлена.'");

    await click('#pair-create');
    for (const [selector, value] of Object.entries({ '#pair-external-id': 'bgasp', '#pair-imperfective': 'купувам', '#pair-perfective': 'купя',
      '#pair-russian-imperfective': 'покупать', '#pair-russian-perfective': 'купить', '#pair-accepted-imperfective': 'купувам\nзакупувам', '#pair-accepted-perfective': 'купя', '#pair-topic': 'Ручная проверка', '#pair-note': 'Учебное пояснение, видимое после ответа.' })) await fill(selector, value);
    await click('#pair-editor details summary');
    for (const [selector, value] of Object.entries({ '#pair-example-imperfective-bg': 'Купувам хляб.', '#pair-example-imperfective-ru': 'Покупаю хлеб.', '#pair-example-perfective-bg': 'Ще купя хляб.', '#pair-example-perfective-ru': 'Куплю хлеб.' })) await fill(selector, value);
    // The save commits, but its response is lost. The exact external ID is on
    // search page 2 because all 100 imported external IDs also match "bgasp".
    loseNextSaveResponse = true;
    await call('Fetch.enable', { patterns: [{ urlPattern: `${base}/api/aspect-pairs`, requestStage: 'Response' }] });
    await click('#pair-form button[type=submit]');
    await wait("!document.querySelector('#pair-save-retry').hidden && !document.querySelector('#pair-save-retry').disabled");
    assert.equal(await evaluate("document.querySelector('#pair-imperfective').value"), 'купувам', 'Failed saves retain editor input');
    assert.equal((await request('/api/aspect-pairs')).totalElements, 101, 'The uncertain save already committed');
    await click('#pair-save-retry');
    await wait("document.querySelector('#pair-editor').hidden && document.querySelector('#pairs-message').textContent==='Сохранение подтверждено. Повторной записи не было.'");
    assert.equal((await request('/api/aspect-pairs')).totalElements, 101);

    await click('[data-view=import]');
    await file('learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json');
    await click('#aspect-upload-form button[type=submit]');
    await wait("document.querySelector('#aspect-preview-count').textContent.includes('Изменений: 1')");
    assert.equal(await evaluate("document.querySelector('#aspect-confirm').disabled"), true, 'Updates require explicit selection');
    for (let i = 0; i < 5; i++) await click('#aspect-preview-next');
    await click('#aspect-preview-rows article:nth-child(7) input[type=checkbox]');
    await click('#aspect-confirm');
    await wait("!document.querySelector('#aspect-import-result').hidden && document.querySelector('#aspect-import-summary').textContent.includes('Обновлено: 1')");
    assert.equal((await request('/api/aspect-pairs')).totalElements, 101, 'Reimport preserves manually created pairs');
    const invalid = JSON.parse(await readFile(resolve('learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json'), 'utf8'));
    invalid.formatVersion = 2; invalid.entries = invalid.entries.slice(0, 2);
    await writeFile(resolve('target/aspect-invalid-version.json'), JSON.stringify(invalid));
    await file('target/aspect-invalid-version.json'); await click('#aspect-upload-form button[type=submit]');
    await wait("document.querySelector('#aspect-import-errors').textContent.includes('версия формата 1')");
    assert.equal(await evaluate("document.querySelector('#aspect-confirm').disabled"), true);
    assert.equal((await request('/api/aspect-pairs')).totalElements, 101, 'Invalid preview must not change pairs');

    // Practice an actual imported pair, with a topic isolating it for this browser test.
    await click('[data-view=pairs]'); await fill('#pair-filter-archive', 'false'); await click('#pair-filters button[type=submit]');
    await wait("document.querySelector('#pairs-message').textContent==='Найдено записей: 2.'");
    await click('#pair-list .toolbar button:first-child');
    await wait("!document.querySelector('#pair-editor').hidden && !document.querySelector('#pair-editor-fields').disabled");
    assert.equal(await evaluate("document.querySelector('#pair-external-id').value"), 'bgasp-057');
    await fill('#pair-topic', 'Проверка'); await fill('#pair-note', 'Учебное пояснение, видимое после ответа.');
    await click('#pair-form button[type=submit]');
    await wait("document.querySelector('#pair-editor').hidden && document.querySelector('#pairs-message').textContent==='Пара сохранена.'");

    await click('[data-view=practice]');
    assert.equal(await evaluate("[...document.querySelectorAll('[name=practice-stage]:checked')].map(n=>n.value).join(',')"), '1');
    await fill('#practice-size', '2'); await fill('#practice-topic', 'Проверка'); await fill('#practice-direction', 'IMPERFECTIVE_TO_PERFECTIVE');
    const launch = async () => {
      await click('#practice-options button[type=submit]');
      await wait("!document.querySelector('#practice-start').disabled"); await click('#practice-start');
      await wait("!document.querySelector('#aspect-training').hidden && !document.querySelector('#aspect-answer').disabled");
    };
    await launch();
    assert.equal(await text('#practice-verb'), 'купувам'); assert.match(await text('#practice-instruction'), /совершенный вид со значением «купить»/);
    assert.doesNotMatch(await text('#aspect-training'), /купя|Учебное пояснение|Ще купя/);
    await fill('#aspect-answer', 'купя');
    await call('Network.emulateNetworkConditions', { offline: true, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
    await key('Enter');
    await wait("document.querySelector('#practice-message').dataset.state==='error'");
    assert.equal(await evaluate("document.querySelector('#aspect-answer').value"), 'купя');
    await call('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
    await reload();
    await wait("document.readyState==='complete' && !document.querySelector('#practice-retry').hidden");
    await click('#practice-retry');
    await wait("!document.querySelector('#practice-feedback').hidden && !document.querySelector('#practice-next').disabled");
    assert.match(await text('#practice-feedback'), /Учебное пояснение/);
    assert.equal(await evaluate("document.querySelectorAll('#practice-explanation .aspect-example').length"), 2);
    await key('Enter', true);
    assert.equal(await evaluate("document.querySelector('#practice-results').hidden"), true, 'Held Enter must not advance feedback');
    await reload();
    await wait("document.readyState==='complete' && !document.querySelector('#practice-feedback').hidden && !document.querySelector('#practice-next').disabled");
    await navigate('/aspect-pairs.html#practice');
    await wait("!document.querySelector('#aspect-resume').hidden"); await click('#aspect-resume-button');
    await wait("!document.querySelector('#practice-feedback').hidden && !document.querySelector('#practice-next').disabled");
    await key('Enter'); await wait("!document.querySelector('#practice-results').hidden");
    assert.match(await text('#practice-result-counts'), /Верно: 1/);
    await click('#practice-new'); await fill('#practice-size', '1'); await launch();
    await fill('#aspect-answer', 'куп'); await key('Enter');
    await wait("document.querySelector('#practice-feedback-title').textContent==='Не совпало с ответами этой карточки'");
    await evaluate("document.querySelector('#aspect-answer').focus()");
    await key('Enter'); await wait("!document.querySelector('#practice-results').hidden");
    assert.match(await text('#practice-mistakes'), /Ваш ответ: куп/);
    await click('#practice-new'); await launch(); await click('#practice-reveal');
    await wait("!document.querySelector('#practice-feedback').hidden && !document.querySelector('#practice-next').disabled");
    assert.match(await text('#practice-feedback-title'), /Самостоятельное воспроизведение не засчитано/);
    await key('Enter'); await wait("!document.querySelector('#practice-results').hidden");
    assert.match(await text('#practice-result-counts'), /Верно: 0.*Раскрыто: 1/);

    await click('#practice-new'); await fill('#practice-topic', 'Общение');
    await click('[name=practice-stage][value="1"]'); await click('[name=practice-stage][value="3"]');
    await launch();
    assert.equal(await text('#practice-verb'), 'говоря'); assert.match(await text('#practice-instruction'), /со значением «поговорить»/);
    assert.doesNotMatch(await text('#aspect-training'), /поговоря/);
    await click('#practice-reveal'); await wait("!document.querySelector('#practice-feedback').hidden");

    for (const width of [360, 320]) {
      await call('Emulation.setDeviceMetricsOverride', { width, height: 800, deviceScaleFactor: 1, mobile: true });
      for (const view of ['practice', 'pairs', 'import', 'statistics', 'help']) {
        await click(`[data-view=${view}]`);
        await wait(`!document.querySelector('[data-panel="${view}"]').hidden`);
        if (view === 'import' && width === 360) {
          await file('learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json');
          await click('#aspect-upload-form button[type=submit]');
          await wait("!document.querySelector('#aspect-preview').hidden && !document.querySelector('#aspect-preview-fields').disabled");
          for (let i = 0; i < 5; i++) await click('#aspect-preview-next');
          await wait("document.querySelector('#aspect-preview-page').textContent==='Страница 6 из 10'");
        }
        if (view === 'statistics') {
          await wait("document.querySelector('#aspect-statistics-message').textContent==='Статистика видовых пар обновлена.'");
          assert.equal(await text('#aspect-stat-answered'), '2');assert.equal(await text('#aspect-stat-correct'), '1');
          assert.equal(await text('#aspect-stat-incorrect'), '1');assert.equal(await text('#aspect-stat-due'), '0');
          assert.match(await text('#aspect-stat-revealed'), /Раскрыто: 2/);
          if (width === 360) {
            await call('Network.emulateNetworkConditions', { offline: true, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
            await click('#aspect-statistics-refresh');
            await wait("document.querySelector('#aspect-statistics-message').dataset.state==='error'");
            assert.equal(await text('#aspect-stat-answered'), '2', 'A failed refresh retains the last confirmed statistics');
            await call('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
            await click('#aspect-statistics-refresh');
            await wait("document.querySelector('#aspect-statistics-message').textContent==='Статистика видовых пар обновлена.'");
            await evaluate("document.querySelector('#aspect-statistics-content').scrollIntoView({block:'start'}); new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))");
            const statisticsImage = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
            await writeFile(resolve('target/aspect-statistics-phone.png'), Buffer.from(statisticsImage.data, 'base64'));
          }
        }
        const dimensions = await evaluate('({ width:innerWidth, scroll:document.documentElement.scrollWidth })');
        assert.ok(dimensions.scroll <= dimensions.width, `${view} overflows at ${width}px: ${JSON.stringify(dimensions)}`);
      }
    }
    await evaluate("document.querySelector('.menu-toggle').focus()"); await key('Enter');
    await wait("document.querySelector('.menu-toggle').getAttribute('aria-expanded')==='true'");
    await key('Escape'); await wait("document.querySelector('.menu-toggle').getAttribute('aria-expanded')==='false'");
    await click('[data-view=practice]');
    await wait("!document.querySelector('[data-panel=practice]').hidden");
    await call('Emulation.setDeviceMetricsOverride', { width: 360, height: 800, deviceScaleFactor: 1, mobile: true });
    await evaluate("document.querySelector('#aspect-prompt').scrollIntoView({block:'center'}); new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))");
    const screenshot = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    await writeFile(resolve('target/aspect-pairs-phone.png'), Buffer.from(screenshot.data, 'base64'));
    await call('Page.addScriptToEvaluateOnNewDocument', { source: "window.__aspectStorageUnavailable=true; Storage.prototype.setItem = function(){ throw new DOMException('Storage unavailable','QuotaExceededError'); };" });
    await reload();
    await wait("window.__aspectStorageUnavailable===true && !document.querySelector('#practice-feedback').hidden && !document.querySelector('#practice-next').disabled");
    assert.equal(await evaluate("document.querySelector('#aspect-storage-note').hidden"), false, 'URL recovery works when local storage fails');
    const pair = (await request('/api/aspect-pairs?q=bgasp-057')).content.find(row => row.entry.id === 'bgasp-057');
    const progress = (await request('/api/aspect-pair-practice/progress')).filter(row => row.pairId === pair.id);
    assert.equal(progress.reduce((sum, row) => sum + row.totalAttempts, 0), 3, 'Retries must not create extra attempts');
    assert.equal(progress.reduce((sum, row) => sum + row.correctAttempts, 0), 1);
    assert.deepEqual(await request('/api/statistics'), legacy);
    // Existing modes still work over real HTTP, and their attempts cannot enter
    // the separate aspect statistics (or vice versa).
    const beforeLegacyPractice = await request('/api/aspect-pair-practice/statistics');
    const post = async (path, body, expected = 200) => {
      const response = await fetch(`${base}${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      assert.equal(response.status, expected, path); return response.json();
    };
    const word = await post('/api/words', { bulgarianText: 'проверявам', translations: [{ russianText: 'проверять', isPrimary: true }] }, 201);
    const cards = await post('/api/flashcards/sessions', { requestId: crypto.randomUUID(), size: 1, direction: 'BG_TO_RU' }, 201);
    await post(`/api/flashcards/sessions/${cards.id}/reveal`, { questionId: cards.currentQuestion.id });
    assert.equal((await post(`/api/flashcards/sessions/${cards.id}/ratings`, { questionId: cards.currentQuestion.id, known: true })).status, 'COMPLETED');
    const typing = await post('/api/typing/sessions', { requestId: crypto.randomUUID(), allowShorter: true }, 201);
    const typed = await post(`/api/typing/sessions/${typing.id}/answers`, { questionId: typing.currentQuestion.id,
      answer: typing.currentQuestion.direction === 'BG_TO_RU' ? 'проверять' : 'проверявам' });
    assert.equal(typed.currentQuestion.feedback.result, 'CORRECT');
    await post('/api/sentences', { russianText: 'Я проверяю.', bulgarianText: 'Проверявам.', alternativeTranslations: [], explanation: '', difficulty: 1, topic: 'Проверка', wordIds: [word.id] }, 201);
    const sentence = await post('/api/sentence-practice/sessions', { requestId: crypto.randomUUID(), size: 1 }, 201);
    assert.equal((await post(`/api/sentence-practice/sessions/${sentence.id}/answers`, { questionId: sentence.currentQuestion.id, answer: 'Проверявам.' })).currentQuestion.feedback.result, 'CORRECT');
    const afterLegacyPractice = await request('/api/aspect-pair-practice/statistics');
    for (const field of ['answered', 'correct', 'incorrect', 'revealed', 'dueForReview', 'dueDirections', 'directions'])
      assert.deepEqual(afterLegacyPractice[field], beforeLegacyPractice[field], `Legacy mode changed aspect ${field}`);
    assert.equal((await request('/api/statistics')).reviewAttempts, 3);
    // Leave an unfinished session for the optional fresh-JVM restart check.
    const original = JSON.parse(await readFile(resolve('learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json'), 'utf8')).entries.find(entry => entry.id === 'bgasp-057');
    for (const id of ['one', 'two']) await post('/api/aspect-pairs', { namespace: 'restart-check', entry: { ...original, id } }, 201);
    const startRequest = { requestId: crypto.randomUUID(), size: 2, studyStages: [1], namespace: 'restart-check', direction: 'IMPERFECTIVE_TO_PERFECTIVE' };
    const started = await post('/api/aspect-pair-practice/sessions', startRequest, 201);
    const submission = { questionId: started.currentQuestion.id, answer: 'купя' };
    const unfinished = await post(`/api/aspect-pair-practice/sessions/${started.id}/answers`, submission);
    assert.equal(unfinished.status, 'IN_PROGRESS');
    await writeFile(resolve('target/aspect-final-state.json'), JSON.stringify({ startRequest, submission, unfinished,
      statistics: await request('/api/aspect-pair-practice/statistics'), legacy: await request('/api/statistics') }));
    assert.deepEqual(exceptions, [], 'No uncaught browser exceptions');
  } finally {
    ws?.close(); chrome.kill();
  }
});
