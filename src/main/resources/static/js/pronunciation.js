const VOICE_PREFERENCE = 'bulgariantrainer.bulgarianVoice';
const messages = {
  unsupported: 'Браузер не поддерживает озвучивание.',
  loading: 'Загружаем список голосов…',
  missing: 'Болгарский голос не найден на этом устройстве.',
  speaking: 'Воспроизводим озвучку…',
  error: 'Не удалось воспроизвести озвучку.',
};

const isBulgarian = voice => /^bg(?:-|$)/i.test(voice?.lang || '');
const voiceKey = voice => JSON.stringify([voice.voiceURI || '', voice.name || '', voice.lang || '']);

export function flashcardBulgarianText(question) {
  if (!question) return null;
  if (question.direction === 'BG_TO_RU') return question.prompt?.[0] || null;
  return question.revealed && question.direction === 'RU_TO_BG' ? question.answer?.[0] || null : null;
}

export function typingBulgarianText(question) {
  if (!question) return null;
  if (question.direction === 'BG_TO_RU') return question.prompt?.[0] || null;
  return question.direction === 'RU_TO_BG' && question.feedback ? question.feedback.acceptedAnswers?.[0] || null : null;
}

export function sentenceBulgarianTexts(question) {
  return question?.feedback?.acceptedAnswers?.filter(text => typeof text === 'string' && text.trim()) || [];
}

export class BulgarianPronunciation {
  constructor({ browser = globalThis, storage, waitMs = 1800 } = {}) {
    this.browser = browser;
    this.synthesis = browser.speechSynthesis;
    this.Utterance = browser.SpeechSynthesisUtterance;
    this.supported = !!(this.synthesis && this.Utterance && typeof this.synthesis.getVoices === 'function'
      && typeof this.synthesis.speak === 'function' && typeof this.synthesis.cancel === 'function');
    this.storage = storage;
    if (this.storage === undefined) {
      try { this.storage = browser.localStorage; } catch { this.storage = null; }
    }
    try { this.savedKey = this.storage?.getItem(VOICE_PREFERENCE) || null; } catch { this.savedKey = null; }
    this.waitMs = waitMs;
    this.voices = [];
    this.selectedVoice = null;
    this.active = null;
    this.listeners = new Set();
    this.timer = null;
    this.status = this.supported ? 'loading' : 'unsupported';
    this.onVoicesChanged = () => this.refreshVoices();
    this.onPageHide = () => this.stop();
    if (this.supported) {
      this.synthesis.addEventListener?.('voiceschanged', this.onVoicesChanged);
      browser.addEventListener?.('pagehide', this.onPageHide);
      this.discover();
    }
  }

  snapshot() {
    return { status: this.status, message: messages[this.status] || '', voices: [...this.voices],
      selectedVoice: this.selectedVoice, activeOwner: this.active?.owner ?? null };
  }

  subscribe(listener) {
    this.listeners.add(listener);
    listener(this.snapshot());
    return () => this.listeners.delete(listener);
  }

  notify(status) {
    this.status = status;
    const state = this.snapshot();
    for (const listener of this.listeners) listener(state);
  }

  discover() {
    if (!this.supported) { this.notify('unsupported'); return; }
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.timer = null;
      this.refreshVoices();
      if (!this.voices.length && !this.active && this.status !== 'error') this.notify('missing');
    }, this.waitMs);
    this.notify('loading');
    this.refreshVoices();
  }

  refreshVoices() {
    if (!this.supported) return;
    let available;
    try { available = this.synthesis.getVoices(); }
    catch { clearTimeout(this.timer); this.timer = null; if (!this.active) this.notify('error'); return; }
    this.voices = Array.from(available || []).filter(isBulgarian);
    this.selectedVoice = this.voices.find(voice => voiceKey(voice) === this.savedKey) || this.voices[0] || null;
    if (this.voices.length) { clearTimeout(this.timer); this.timer = null; }
    if (!this.active) this.notify(this.voices.length ? 'ready' : this.timer ? 'loading' : 'missing');
    else this.notify('speaking');
  }

  selectVoice(key) {
    const voice = this.voices.find(candidate => voiceKey(candidate) === key);
    if (!voice) return false;
    this.selectedVoice = voice;
    this.savedKey = key;
    try { this.storage?.setItem(VOICE_PREFERENCE, key); } catch { /* Preference is optional. */ }
    this.notify(this.active ? 'speaking' : 'ready');
    return true;
  }

  stop() {
    this.active = null;
    if (this.supported) {
      try { this.synthesis.cancel(); }
      catch { this.notify('error'); return false; }
    }
    this.notify(!this.supported ? 'unsupported' : this.voices.length ? 'ready' : this.timer ? 'loading' : 'missing');
    return true;
  }

  speak(value, { slow = false, owner = null } = {}) {
    const text = typeof value === 'string' ? value.trim() : '';
    if (!text) { this.notify('error'); return false; }
    if (!this.supported) { this.notify('unsupported'); return false; }
    if (!this.selectedVoice) { this.discover(); return false; }
    if (!this.stop()) return false;
    let utterance;
    try {
      utterance = new this.Utterance(text);
      utterance.voice = this.selectedVoice;
      utterance.lang = this.selectedVoice.lang;
      utterance.rate = slow ? 0.5 : 1;
      const active = { utterance, owner };
      this.active = active;
      utterance.onstart = () => { if (this.active === active) this.notify('speaking'); };
      utterance.onend = () => { if (this.active === active) { this.active = null; this.notify(this.voices.length ? 'ready' : 'missing'); } };
      utterance.onerror = () => { if (this.active === active) { this.active = null; this.notify('error'); } };
      this.notify('speaking');
      this.synthesis.speak(utterance);
      return true;
    } catch {
      this.active = null;
      try { this.synthesis.cancel(); } catch { /* The failure is reported below. */ }
      this.notify('error');
      return false;
    }
  }

  destroy() {
    this.stop();
    clearTimeout(this.timer);
    this.synthesis?.removeEventListener?.('voiceschanged', this.onVoicesChanged);
    this.browser.removeEventListener?.('pagehide', this.onPageHide);
    this.listeners.clear();
  }
}

function button(text, label) {
  const control = document.createElement('button');
  control.type = 'button'; control.className = 'secondary'; control.textContent = text;
  control.setAttribute('aria-label', label);
  return control;
}

export function createPlaybackControls(pronunciation, { text = null, label = 'Болгарский текст' } = {}) {
  const owner = Symbol('pronunciation-control');
  const element = document.createElement('div'); element.className = 'pronunciation-controls';
  const listen = button('Прослушать', `Прослушать: ${label}`);
  const slow = button('Медленно', `Медленно прослушать: ${label}`);
  const stop = button('Остановить', `Остановить озвучивание: ${label}`);
  const status = document.createElement('span'); status.className = 'pronunciation-status';
  status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
  element.append(listen, slow, stop, status);
  let currentText = null;
  let attempted = false;
  const update = state => {
    const ownPlayback = state.status === 'speaking' && state.activeOwner === owner;
    listen.disabled = slow.disabled = !currentText || state.status === 'unsupported';
    stop.hidden = !ownPlayback;
    status.textContent = ownPlayback ? state.message : attempted && ['loading', 'missing', 'error', 'unsupported'].includes(state.status)
      ? state.message : state.status === 'unsupported' && currentText ? state.message : '';
  };
  const unsubscribe = pronunciation.subscribe(update);
  const play = slowMode => event => {
    event.stopPropagation(); attempted = true;
    pronunciation.speak(currentText, { slow: slowMode, owner });
  };
  listen.addEventListener('click', play(false));
  slow.addEventListener('click', play(true));
  stop.addEventListener('click', event => { event.stopPropagation(); pronunciation.stop(); });
  function setText(next) {
    const cleaned = typeof next === 'string' ? next.trim() : '';
    if (cleaned !== currentText && pronunciation.active?.owner === owner) pronunciation.stop();
    currentText = cleaned || null;
    attempted = false;
    element.hidden = !currentText;
    update(pronunciation.snapshot());
  }
  setText(text);
  return { element, setText, destroy() { if (pronunciation.active?.owner === owner) pronunciation.stop(); unsubscribe(); element.remove(); } };
}

export function mountVoiceSettings(pronunciation, container) {
  const details = document.createElement('details'); details.className = 'pronunciation-settings';
  const summary = document.createElement('summary'); summary.textContent = 'Настройки произношения';
  const label = document.createElement('label'); label.textContent = 'Болгарский голос';
  const select = document.createElement('select'); select.setAttribute('aria-label', 'Болгарский голос');
  label.append(select);
  const retry = button('Повторить поиск голосов', 'Повторить поиск болгарских голосов');
  const status = document.createElement('p'); status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
  details.append(summary, label, retry, status); container.append(details);
  select.addEventListener('change', () => pronunciation.selectVoice(select.value));
  retry.addEventListener('click', () => pronunciation.discover());
  const unsubscribe = pronunciation.subscribe(state => {
    const selected = state.selectedVoice ? voiceKey(state.selectedVoice) : '';
    select.replaceChildren(...state.voices.map(voice => new Option(`${voice.name || 'Болгарский голос'} (${voice.lang})`, voiceKey(voice))));
    select.value = selected;
    label.hidden = state.voices.length < 2;
    status.textContent = state.status === 'ready' && state.voices.length === 1
      ? `Выбран болгарский голос: ${state.voices[0].name || state.voices[0].lang}.`
      : state.status === 'ready' ? 'Выберите болгарский голос.' : state.message;
  });
  return { element: details, destroy() { unsubscribe(); details.remove(); } };
}
