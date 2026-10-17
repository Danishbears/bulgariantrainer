import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../../main/resources/static/js/pronunciation.js', import.meta.url));
const { BulgarianPronunciation, createPlaybackControls, flashcardBulgarianText, typingBulgarianText, sentenceBulgarianTexts } =
  await import(`data:text/javascript;base64,${source.toString('base64')}`);

const bg = (name, lang = 'bg-BG') => ({ name, lang, voiceURI: name });
function fixture(voices = [], waitMs = 20) {
  const events = new Map();
  const pageEvents = new Map();
  const calls = { cancel: 0, spoken: [] };
  const synthesis = {
    getVoices: () => voices,
    addEventListener: (name, callback) => events.set(name, callback),
    removeEventListener: name => events.delete(name),
    cancel: () => { calls.cancel++; },
    speak: utterance => { calls.spoken.push(utterance); utterance.onstart?.(); },
  };
  class Utterance { constructor(text) { this.text = text; } }
  const saved = new Map();
  const browser = { speechSynthesis: synthesis, SpeechSynthesisUtterance: Utterance,
    addEventListener: (name, callback) => pageEvents.set(name, callback),
    removeEventListener: name => pageEvents.delete(name),
    localStorage: { getItem: key => saved.get(key), setItem: (key, value) => saved.set(key, value) } };
  const pronunciation = new BulgarianPronunciation({ browser, waitMs });
  return { pronunciation, browser, synthesis, events, pageEvents, calls, saved,
    setVoices: next => { voices = next; events.get('voiceschanged')?.(); } };
}

function fakeDocument() {
  return { createElement(tag) {
    return { tag, children: [], listeners: new Map(), attributes: new Map(), hidden: false, disabled: false,
      append(...children) { this.children.push(...children); },
      setAttribute(name, value) { this.attributes.set(name, value); },
      addEventListener(name, listener) { this.listeners.set(name, listener); },
      click() { this.listeners.get('click')?.({ stopPropagation() { this.stopped = true; } }); },
      remove() { this.removed = true; } };
  } };
}

test('selects only Bulgarian voices and preserves the saved selection', () => {
  const f = fixture([bg('English', 'en-US'), bg('Bulgarian One', 'BG'), bg('Bulgarian Two')]);
  assert.equal(f.pronunciation.snapshot().voices.length, 2);
  const key = JSON.stringify(['Bulgarian Two', 'Bulgarian Two', 'bg-BG']);
  assert.equal(f.pronunciation.selectVoice(key), true);
  assert.equal(f.pronunciation.speak('  Добър ден!  '), true);
  assert.equal(f.calls.spoken[0].text, 'Добър ден!');
  assert.equal(f.calls.spoken[0].voice.name, 'Bulgarian Two');
  assert.equal(f.calls.spoken[0].lang, 'bg-BG');
  assert.equal(f.calls.spoken[0].rate, 1);
  f.pronunciation.destroy();
  const restored = new BulgarianPronunciation({ browser: f.browser });
  assert.equal(restored.snapshot().selectedVoice.name, 'Bulgarian Two');
  f.setVoices([bg('Replacement')]);
  restored.refreshVoices();
  assert.equal(restored.snapshot().selectedVoice.name, 'Replacement');
  restored.destroy();
});

test('waits for delayed voices, then reports missing and supports retry', async () => {
  const f = fixture([], 15);
  assert.equal(f.pronunciation.snapshot().status, 'loading');
  f.setVoices([bg('Български')]);
  assert.equal(f.pronunciation.snapshot().status, 'ready');
  f.setVoices([]);
  f.pronunciation.discover();
  await new Promise(resolve => setTimeout(resolve, 25));
  assert.equal(f.pronunciation.snapshot().status, 'missing');
  assert.equal(f.pronunciation.speak('дума'), false);
  assert.equal(f.calls.spoken.length, 0);
  f.setVoices([bg('Нов глас')]);
  assert.equal(f.pronunciation.snapshot().status, 'ready');
  f.pronunciation.destroy();
});

test('never falls back to a non-Bulgarian voice or unsupported browser', async () => {
  const f = fixture([bg('Russian', 'ru-RU')], 10);
  await new Promise(resolve => setTimeout(resolve, 20));
  assert.equal(f.pronunciation.snapshot().status, 'missing');
  assert.equal(f.pronunciation.speak('котка'), false);
  assert.equal(f.calls.spoken.length, 0);
  f.pronunciation.destroy();
  const unsupported = new BulgarianPronunciation({ browser: {} });
  assert.equal(unsupported.snapshot().status, 'unsupported');
  assert.equal(unsupported.speak('котка'), false);
});

test('rejects empty text and cancels old playback without stale event damage', () => {
  const f = fixture([bg('Bulgarian')]);
  assert.equal(f.pronunciation.speak('  '), false);
  assert.equal(f.calls.spoken.length, 0);
  f.pronunciation.speak('първи');
  const old = f.calls.spoken[0];
  f.pronunciation.speak('втори', { slow: true });
  assert.ok(f.calls.cancel >= 2);
  assert.equal(f.calls.spoken[1].rate, 0.5);
  old.onerror?.(); old.onend?.();
  assert.equal(f.pronunciation.snapshot().status, 'speaking');
  f.calls.spoken[1].onerror?.();
  assert.equal(f.pronunciation.snapshot().status, 'error');
  assert.equal(f.pronunciation.speak('повторно'), true);
  f.pronunciation.stop();
  assert.equal(f.pronunciation.snapshot().status, 'ready');
  f.pronunciation.destroy();
});

test('rapid replay, slow playback, and stop use one utterance at a time', () => {
  const previousDocument = globalThis.document;
  globalThis.document = fakeDocument();
  try {
    const f = fixture([bg('Bulgarian')]);
    const controls = createPlaybackControls(f.pronunciation, { text: '  Добър ден!  ' });
    const [listen, slow, stop, status] = controls.element.children;
    assert.equal(listen.type, 'button');
    assert.equal(slow.type, 'button');
    assert.equal(stop.type, 'button');
    listen.click(); listen.click(); slow.click();
    assert.deepEqual(f.calls.spoken.map(utterance => utterance.text), ['Добър ден!', 'Добър ден!', 'Добър ден!']);
    assert.deepEqual(f.calls.spoken.map(utterance => utterance.rate), [1, 1, 0.5]);
    assert.equal(f.calls.cancel, 3);
    assert.equal(f.pronunciation.active.utterance, f.calls.spoken[2]);
    assert.equal(status.textContent, 'Воспроизводим озвучку…');
    stop.click();
    assert.equal(f.pronunciation.active, null);
    assert.equal(stop.hidden, true);
    controls.destroy(); f.pronunciation.destroy();
  } finally { globalThis.document = previousDocument; }
});

test('failed cancellation does not enqueue another utterance and playback can recover', () => {
  const f = fixture([bg('Bulgarian')]);
  f.pronunciation.speak('първи');
  f.synthesis.cancel = () => { throw new Error('audio device failure'); };
  assert.equal(f.pronunciation.speak('втори'), false);
  assert.equal(f.calls.spoken.length, 1);
  assert.equal(f.pronunciation.snapshot().status, 'error');
  f.synthesis.cancel = () => { f.calls.cancel++; };
  assert.equal(f.pronunciation.speak('втори'), true);
  f.pageEvents.get('pagehide')?.();
  assert.equal(f.pronunciation.active, null);
  f.pronunciation.destroy();
});

test('a speech engine failure reports an error without blocking a later replay', () => {
  const f = fixture([bg('Bulgarian')]);
  f.synthesis.speak = () => { throw new Error('playback failed'); };
  assert.equal(f.pronunciation.speak('куче'), false);
  assert.equal(f.pronunciation.snapshot().status, 'error');
  f.synthesis.speak = utterance => { f.calls.spoken.push(utterance); utterance.onstart?.(); };
  assert.equal(f.pronunciation.speak('куче'), true);
  assert.equal(f.calls.spoken.length, 1);
  f.pronunciation.destroy();
});

test('destroying paginated controls removes subscriptions and stops their playback', () => {
  const previousDocument = globalThis.document;
  globalThis.document = fakeDocument();
  try {
    const f = fixture([bg('Bulgarian')]);
    for (let page = 0; page < 4; page++) {
      const controls = Array.from({ length: 20 }, (_, index) => createPlaybackControls(f.pronunciation, { text: `дума ${page}-${index}` }));
      assert.equal(f.pronunciation.listeners.size, 20);
      controls[0].element.children[0].click();
      controls.forEach(control => control.destroy());
      assert.equal(f.pronunciation.listeners.size, 0);
      assert.equal(f.pronunciation.active, null);
    }
    f.pronunciation.destroy();
  } finally { globalThis.document = previousDocument; }
});

test('only exposes Bulgarian text at the correct stage in each mode', () => {
  assert.equal(flashcardBulgarianText({ direction: 'RU_TO_BG', answer: ['куче'], revealed: false }), null);
  assert.equal(flashcardBulgarianText({ direction: 'RU_TO_BG', answer: ['куче'], revealed: true }), 'куче');
  assert.equal(flashcardBulgarianText({ direction: 'BG_TO_RU', prompt: ['котка'] }), 'котка');
  assert.equal(typingBulgarianText({ direction: 'RU_TO_BG', feedback: null }), null);
  assert.equal(typingBulgarianText({ direction: 'RU_TO_BG', feedback: { acceptedAnswers: ['къща'] } }), 'къща');
  assert.equal(typingBulgarianText({ direction: 'BG_TO_RU', prompt: ['добър ден'] }), 'добър ден');
  assert.deepEqual(sentenceBulgarianTexts({ feedback: null }), []);
  assert.deepEqual(sentenceBulgarianTexts({ feedback: { acceptedAnswers: ['Това е чай.', 'Това е чаят.'] } }), ['Това е чай.', 'Това е чаят.']);
});
