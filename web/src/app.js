import { bookById, books, pageAt, pageIndex, pages } from './books.js';
import { appendHighlighted, appendStyled, headingLabel, plainVerseText } from './format.js';
import { highlightPatternFor, normalizeVerseQuery } from './normalize.js';
import { loadChapter, searchHeadingText, searchNavigation, searchVerseText } from './repository.js';

const PREFS_KEY = 'kjv-web-prefs';
const MIN_SCALE = 0.5;
const MAX_SCALE = 2;

function icon(path, size = 24) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="${size}" height="${size}" aria-hidden="true"><path fill="currentColor" d="${path}"/></svg>`;
}

// Material Icons (filled), the same set the Android app uses.
const ICONS = {
  toc: icon('M3 9h14V7H3v2zm0 4h14v-2H3v2zm0 4h14v-2H3v2zm16 0h2v-2h-2v2zm0-10v2h2V7h-2zm0 6h2v-2h-2v2z', 26),
  search: icon('M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z'),
  close: icon('M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z', 22),
  back: icon('M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z'),
  moon: icon('M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z', 22),
  sun: icon('M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0 .39-.39.39-1.03 0-1.41l-1.06-1.06zm1.06-10.96c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z', 22)
};

const state = {
  bookId: 1,
  chapter: 1,
  highlightedVerse: null,
  scrollVerse: 1,
  pendingScrollVerse: null,
  textScale: 1,
  theme: 'system',
  query: '',
  searchFocused: false,
  fullPage: false,
  tocOpen: false,
  pickerBook: null,
  results: emptyResults(),
  searchToken: 0
};

let root;
let searchTimer = 0;
let scrollTimer = 0;
let overlayHistory = false;
let suppressPop = 0;
let ignoreVerseClick = false;
let readerStamp = '';
let scrollRequest = 0;
let suppressScrollSave = 0;

export function loadPrefs() {
  try {
    return JSON.parse(localStorage.getItem(PREFS_KEY) || '{}');
  } catch {
    return {};
  }
}

export function applyThemeClass(theme) {
  const dark = theme === 'dark' ||
    (theme !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  const color = dark ? '#121316' : '#FAF8F5';
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.content = color;
  return dark;
}

export function mountApp(mount) {
  root = mount;
  const prefs = loadPrefs();
  const book = bookById(prefs.bookId || 1);
  state.bookId = book.id;
  state.chapter = clampChapter(book, prefs.chapter || 1);
  state.scrollVerse = prefs.scrollVerse || 1;
  state.highlightedVerse = prefs.highlightedVerse || null;
  state.pendingScrollVerse = state.scrollVerse;
  state.textScale = clampScale(prefs.textScale || 1);
  state.theme = prefs.theme || 'system';
  applyThemeClass(state.theme);
  document.documentElement.style.setProperty('--scale', String(state.textScale));

  root.innerHTML = `
    <header class="topbar" id="topbar"></header>
    <div class="stage">
      <div class="reader" id="reader">
        <div class="pager" id="pager">
          <article class="page" data-slot="prev"></article>
          <article class="page" data-slot="current"></article>
          <article class="page" data-slot="next"></article>
        </div>
      </div>
      <div class="search-overlay hidden" id="search-overlay"><div class="search-card" id="search-card"></div></div>
      <div class="search-page hidden" id="search-page"></div>
      <div class="backdrop hidden" id="toc-backdrop"></div>
      <aside class="toc hidden" id="toc" aria-label="Table of contents">
        <header>
          ${ICONS.toc}
          <h2>Table of Contents</h2>
          <button class="icon-button" id="toc-close" type="button" aria-label="Close table of contents">${ICONS.close}</button>
        </header>
        <div class="toc-list" id="toc-list"></div>
      </aside>
    </div>
    <footer class="bottombar">
      <button class="toc-button" id="toc-bottom" type="button" aria-label="Table of contents">${ICONS.toc}</button>
      <form class="search-form" id="search-form" role="search">
        ${ICONS.search}
        <label class="search-field">
          <span class="placeholder" id="placeholder">Search</span>
          <input id="search-input" enterkeyhint="search" autocomplete="off" autocapitalize="off" aria-label="Search">
        </label>
        <button class="clear-button hidden" id="clear-search" type="button" aria-label="Clear search">${ICONS.close}</button>
      </form>
    </footer>
    <div class="dialog-backdrop hidden" id="picker"></div>
  `;

  renderTocList();
  bindChrome();
  bindReader();
  bindPinch(document.getElementById('search-page'));
  bindPinch(document.getElementById('search-card'));
  render();
  window.addEventListener('popstate', onPopState);
  window.addEventListener('keydown', onKeyDown);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') saveVisibleVerse();
  });
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (state.theme === 'system') {
      applyThemeClass(state.theme);
      render();
    }
  });
}

function emptyResults() {
  return { referenceMatch: null, matchingBooks: [], matchingVerses: [], matchingHeadings: [] };
}

function clampChapter(book, chapter) {
  return Math.min(book.chapterCount, Math.max(1, chapter));
}

function clampScale(scale) {
  if (!Number.isFinite(scale) || scale <= 0) return 1;
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
}

function setScale(next) {
  state.textScale = clampScale(next);
  document.documentElement.style.setProperty('--scale', String(state.textScale));
  const label = document.getElementById('scale-value');
  if (label) label.textContent = `${Math.round(state.textScale * 100)}%`;
  persist();
}

function isDark() {
  return document.documentElement.dataset.theme === 'dark';
}

function overlayOpen() {
  return Boolean(state.pickerBook || state.tocOpen || state.searchFocused || state.query.trim() || state.fullPage);
}

function ensureHistory() {
  if (overlayOpen() && !overlayHistory) {
    history.pushState({ kjvOverlay: true }, '');
    overlayHistory = true;
  }
}

function releaseHistory() {
  if (overlayHistory && history.state?.kjvOverlay) {
    suppressPop += 1;
    overlayHistory = false;
    history.back();
  } else {
    overlayHistory = false;
  }
}

function onPopState() {
  if (suppressPop > 0) {
    suppressPop -= 1;
    return;
  }
  overlayHistory = false;
  closeTopOverlay();
  render();
  if (overlayOpen()) {
    history.pushState({ kjvOverlay: true }, '');
    overlayHistory = true;
  }
}

function closeTopOverlay() {
  if (state.pickerBook) {
    state.pickerBook = null;
    return;
  }
  if (state.tocOpen) {
    state.tocOpen = false;
    return;
  }
  if (state.fullPage || state.searchFocused || state.query) dismissSearch();
}

function dismissFromUi() {
  closeTopOverlay();
  render();
  if (!overlayOpen()) releaseHistory();
}

function currentBook() {
  return bookById(state.bookId);
}

function render() {
  renderTopBar();
  renderReader();
  renderSearchChrome();
  renderSearchResults();
  renderToc();
  renderPicker();
}

function renderTopBar() {
  const bar = document.getElementById('topbar');
  const dark = isDark();
  if (state.fullPage) {
    bar.innerHTML = `
      <button class="icon-button" id="search-back" type="button" aria-label="Close search results">${ICONS.back}</button>
      <div class="top-title">Search results</div>
      ${scaleControls()}
    `;
  } else {
    const book = currentBook();
    bar.innerHTML = `
      <button class="toc-button" id="toc-top" type="button" aria-label="Table of contents">${ICONS.toc}</button>
      <button class="book-button" id="book-title" type="button">
        <strong>${escapeHtml(book.name)}</strong>
        <span>Chapter ${state.chapter}</span>
      </button>
      ${scaleControls()}
      <button class="theme-button" id="theme-toggle" type="button" aria-label="${dark ? 'Switch to light mode' : 'Switch to dark mode'}">${dark ? ICONS.sun : ICONS.moon}</button>
    `;
    document.getElementById('toc-top').addEventListener('click', toggleToc);
    document.getElementById('book-title').addEventListener('click', () => openBookPicker(currentBook(), false));
    document.getElementById('theme-toggle').addEventListener('click', () => {
      state.theme = dark ? 'light' : 'dark';
      applyThemeClass(state.theme);
      persist();
      renderTopBar();
      renderSearchResults();
    });
  }
  bindScaleControls();
  document.getElementById('search-back')?.addEventListener('click', dismissFromUi);
}

function scaleControls() {
  return `
    <div class="scale-controls">
      <button class="scale-button" id="scale-down" type="button" aria-label="Decrease text size">A-</button>
      <button class="scale-button scale-value" id="scale-value" type="button" aria-label="Reset text size">${Math.round(state.textScale * 100)}%</button>
      <button class="scale-button" id="scale-up" type="button" aria-label="Increase text size">A+</button>
    </div>
  `;
}

function bindScaleControls() {
  document.getElementById('scale-down').addEventListener('click', () => setScale(roundStep(state.textScale - 0.1)));
  document.getElementById('scale-up').addEventListener('click', () => setScale(roundStep(state.textScale + 0.1)));
  document.getElementById('scale-value').addEventListener('click', () => setScale(1));
}

function roundStep(value) {
  return Math.round(value * 10) / 10;
}

function renderReader() {
  const stamp = `${state.bookId}:${state.chapter}:${scrollRequest}`;
  if (stamp === readerStamp) {
    updateHighlights();
    return;
  }
  readerStamp = stamp;
  const index = pageIndex(state.bookId, state.chapter);
  fillPage(pageSlot('prev'), index > 0 ? pageAt(index - 1) : null, false);
  fillPage(pageSlot('current'), pageAt(index), true);
  fillPage(pageSlot('next'), index < pages.length - 1 ? pageAt(index + 1) : null, false);
  const current = pageSlot('current');
  const verse = state.pendingScrollVerse;
  state.pendingScrollVerse = null;
  document.getElementById('pager').style.transition = 'none';
  document.getElementById('pager').style.transform = 'translateX(-33.3333%)';
  pageSlot('prev').inert = true;
  pageSlot('current').inert = false;
  pageSlot('next').inert = true;
  suppressScrollSave += 1;
  requestAnimationFrame(() => {
    scrollPageToVerse(current, verse);
    setTimeout(() => {
      suppressScrollSave = Math.max(0, suppressScrollSave - 1);
    }, 50);
  });
}

function scrollPageToVerse(page, verse) {
  if (!verse || verse <= 1) {
    page.scrollTop = 0;
    return;
  }
  const target = page.querySelector(`[data-verse="${verse}"]`);
  if (!target) return;
  const pageRect = page.getBoundingClientRect();
  const targetRect = target.getBoundingClientRect();
  page.scrollTop += targetRect.top - pageRect.top - 48;
}

function pageSlot(slot) {
  return document.querySelector(`.page[data-slot="${slot}"]`);
}

function fillPage(element, location, interactive) {
  element.innerHTML = '';
  if (!location) return;
  const book = bookById(location.bookId);
  const column = document.createElement('div');
  column.className = 'column';
  const header = document.createElement('header');
  header.className = 'chapter-header';
  header.innerHTML = `
    <h1>${escapeHtml(book.name)}</h1>
    <p class="chapter-label">Chapter ${location.chapter}</p>
    <p class="hint">Swipe left or right to move between chapters. Pinch to resize text.</p>
  `;
  column.append(header);
  const verses = loadChapter(location.bookId, location.chapter);
  for (const verse of verses) {
    for (const heading of verse.headings) {
      const headingEl = document.createElement('p');
      headingEl.className = `heading ${heading.type === 'acrostic' ? 'acrostic' : 'psalm'}`;
      appendStyled(headingEl, headingLabel(heading));
      column.append(headingEl);
    }
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'verse';
    button.dataset.verse = String(verse.verse);
    if (interactive && state.highlightedVerse === verse.verse) button.classList.add('highlighted');
    const number = document.createElement('span');
    number.className = 'verse-number';
    number.textContent = String(verse.verse);
    button.append(number);
    appendStyled(button, verse.text);
    if (interactive) {
      button.addEventListener('click', () => {
        if (ignoreVerseClick) return;
        state.highlightedVerse = state.highlightedVerse === verse.verse ? null : verse.verse;
        updateHighlights();
        persist();
      });
    }
    column.append(button);
  }
  element.append(column);
}

function updateHighlights() {
  for (const button of pageSlot('current').querySelectorAll('.verse')) {
    button.classList.toggle('highlighted', Number(button.dataset.verse) === state.highlightedVerse);
  }
}

function renderSearchChrome() {
  const form = document.getElementById('search-form');
  const input = document.getElementById('search-input');
  const placeholder = document.getElementById('placeholder');
  const clear = document.getElementById('clear-search');
  const active = state.searchFocused || state.query.trim().length > 0;
  form.classList.toggle('active', active);
  placeholder.textContent = state.searchFocused ? 'Type verse, book, or word...' : 'Search';
  placeholder.classList.toggle('hidden', state.query.length > 0);
  clear.classList.toggle('hidden', state.query.length === 0);
  if (input.value !== state.query) input.value = state.query;
}

let renderedSearchQuery = null;

function renderSearchResults() {
  const overlay = document.getElementById('search-overlay');
  const page = document.getElementById('search-page');
  const card = document.getElementById('search-card');
  const active = state.searchFocused || state.query.trim().length > 0;
  const hasResults = Boolean(
    state.results.referenceMatch ||
    state.results.matchingBooks.length ||
    state.results.matchingHeadings.length ||
    state.results.matchingVerses.length
  );
  page.classList.toggle('hidden', !state.fullPage);
  overlay.classList.toggle('hidden', state.fullPage || !active || !hasResults);
  const scroller = state.fullPage ? page : card;
  const queryChanged = renderedSearchQuery !== state.query;
  const previousScroll = scroller.scrollTop;
  scroller.replaceChildren(buildResults(hasResults));
  scroller.scrollTop = queryChanged ? 0 : previousScroll;
  renderedSearchQuery = state.query;
}

function buildResults(hasResults) {
  const wrap = document.createElement('div');
  wrap.className = 'results';
  if (!hasResults) {
    const empty = document.createElement('p');
    empty.className = 'empty';
    empty.textContent = 'No results found.';
    wrap.append(empty);
    return wrap;
  }
  const pattern = matchPattern(state.query);
  if (state.results.referenceMatch) {
    wrap.append(section('Reference'));
    wrap.append(resultButton(state.results.referenceMatch.label, null, () => {
      const match = state.results.referenceMatch;
      openChapter(match.book, match.chapter, match.verse || 1);
    }));
  }
  if (state.results.matchingBooks.length) {
    wrap.append(section('Books'));
    for (const book of state.results.matchingBooks) {
      wrap.append(resultButton(book.name, null, () => openBookPicker(book, true)));
    }
  }
  if (state.results.matchingHeadings.length) {
    wrap.append(section('Headings'));
    for (const heading of state.results.matchingHeadings) {
      wrap.append(resultButton(
        `${bookById(heading.bookId).name} ${heading.chapter}:${heading.verse}`,
        headingLabel(heading),
        () => openChapter(bookById(heading.bookId), heading.chapter, heading.verse),
        pattern
      ));
    }
  }
  if (state.results.matchingVerses.length) {
    wrap.append(section('Verses'));
    for (const verse of state.results.matchingVerses) {
      wrap.append(resultButton(
        `${bookById(verse.bookId).name} ${verse.chapter}:${verse.verse}`,
        verse.text,
        () => openChapter(bookById(verse.bookId), verse.chapter, verse.verse),
        pattern
      ));
    }
  }
  return wrap;
}

function section(title) {
  const el = document.createElement('h3');
  el.className = 'section-title';
  el.textContent = title;
  return el;
}

function resultButton(title, subtitleRaw, onClick, pattern) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'result';
  const strong = document.createElement('strong');
  strong.textContent = title;
  button.append(strong);
  if (subtitleRaw) {
    const subtitle = document.createElement('p');
    appendHighlighted(subtitle, plainVerseText(subtitleRaw), pattern);
    button.append(subtitle);
  }
  // A press in the list must not blur the field first. Blur used to rebuild the
  // list and jump it back to the top, so the first click never landed.
  let origin = null;
  let handledByPointer = false;
  button.addEventListener('pointerdown', (event) => {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    origin = { x: event.clientX, y: event.clientY, type: event.pointerType };
    if (event.pointerType === 'mouse') event.preventDefault();
  });
  button.addEventListener('pointerup', (event) => {
    if (!origin || origin.type === 'mouse') return;
    const moved = Math.hypot(event.clientX - origin.x, event.clientY - origin.y);
    origin = null;
    if (moved > 12) return;
    handledByPointer = true;
    onClick();
  });
  button.addEventListener('pointercancel', () => {
    origin = null;
  });
  button.addEventListener('click', () => {
    if (handledByPointer) {
      handledByPointer = false;
      return;
    }
    onClick();
  });
  return button;
}

function matchPattern(query) {
  const tokens = [...new Set(normalizeVerseQuery(query).split(' ').filter(Boolean))]
    .sort((a, b) => b.length - a.length);
  if (!tokens.length) return null;
  return new RegExp(tokens.map(highlightPatternFor).join('|'), 'g');
}

function renderToc() {
  document.getElementById('toc-backdrop').classList.toggle('hidden', !state.tocOpen);
  document.getElementById('toc').classList.toggle('hidden', !state.tocOpen);
  for (const row of document.querySelectorAll('.book-row')) {
    const selected = Number(row.dataset.bookId) === state.bookId;
    row.classList.toggle('selected', selected);
    if (selected && state.tocOpen) row.scrollIntoView({ block: 'nearest' });
  }
}

function renderTocList() {
  const list = document.getElementById('toc-list');
  for (const book of books) {
    const row = document.createElement('button');
    row.type = 'button';
    row.className = 'book-row';
    row.dataset.bookId = String(book.id);
    const name = document.createElement('span');
    name.textContent = book.name;
    const count = document.createElement('span');
    count.className = 'count';
    count.textContent = `${book.chapterCount} ch`;
    row.append(name, count);
    row.addEventListener('click', () => {
      state.tocOpen = false;
      openBookPicker(book, false);
    });
    list.append(row);
  }
}

function renderPicker() {
  const picker = document.getElementById('picker');
  if (!state.pickerBook) {
    picker.classList.add('hidden');
    picker.innerHTML = '';
    return;
  }
  const book = state.pickerBook;
  picker.classList.remove('hidden');
  picker.innerHTML = `
    <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="picker-title">
      <header>
        <h2 id="picker-title">Choose chapter</h2>
        <button class="icon-button" id="picker-close" type="button" aria-label="Close chapter picker">${ICONS.close}</button>
      </header>
      <p class="book-name">${escapeHtml(book.name)}</p>
      <div class="chapter-grid" id="chapter-grid"></div>
    </div>
  `;
  const grid = document.getElementById('chapter-grid');
  for (let chapter = 1; chapter <= book.chapterCount; chapter += 1) {
    const cell = document.createElement('button');
    cell.type = 'button';
    cell.className = 'chapter-cell';
    cell.textContent = String(chapter);
    cell.addEventListener('click', () => openChapter(book, chapter, 1));
    grid.append(cell);
  }
  document.getElementById('picker-close').addEventListener('click', dismissFromUi);
}

function bindChrome() {
  document.getElementById('toc-bottom').addEventListener('click', toggleToc);
  document.getElementById('toc-close').addEventListener('click', dismissFromUi);
  document.getElementById('toc-backdrop').addEventListener('click', dismissFromUi);
  const input = document.getElementById('search-input');
  input.addEventListener('focus', () => {
    state.searchFocused = true;
    state.tocOpen = false;
    state.pickerBook = null;
    ensureHistory();
    renderSearchChrome();
    renderToc();
    renderPicker();
    scheduleSearch();
  });
  input.addEventListener('blur', () => {
    state.searchFocused = false;
    renderSearchChrome();
    const overlay = document.getElementById('search-overlay');
    const active = state.query.trim().length > 0;
    const hasResults = Boolean(
      state.results.referenceMatch ||
      state.results.matchingBooks.length ||
      state.results.matchingHeadings.length ||
      state.results.matchingVerses.length
    );
    overlay.classList.toggle('hidden', state.fullPage || !active || !hasResults);
  });
  input.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    document.getElementById('search-form').requestSubmit();
  });
  input.addEventListener('input', () => {
    state.query = input.value;
    state.results = searchNavigation(state.query);
    renderSearchChrome();
    renderSearchResults();
    scheduleSearch();
    ensureHistory();
  });
  document.getElementById('search-form').addEventListener('submit', (event) => {
    event.preventDefault();
    if (!state.query.trim()) return;
    state.fullPage = true;
    input.blur();
    ensureHistory();
    render();
  });
  document.getElementById('picker').addEventListener('click', (event) => {
    if (event.target.id === 'picker') dismissFromUi();
  });
  document.getElementById('clear-search').addEventListener('click', () => {
    dismissSearch();
    render();
    if (!overlayOpen()) releaseHistory();
    document.getElementById('search-input').focus();
  });
}

function scheduleSearch() {
  const token = state.searchToken + 1;
  state.searchToken = token;
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    if (token !== state.searchToken) return;
    const query = state.query;
    state.results = {
      ...searchNavigation(query),
      matchingVerses: searchVerseText(query),
      matchingHeadings: searchHeadingText(query)
    };
    renderSearchResults();
  }, 120);
}

function toggleToc() {
  document.getElementById('search-input').blur();
  state.tocOpen = !state.tocOpen;
  if (state.tocOpen) ensureHistory();
  renderToc();
  renderTopBar();
  if (!state.tocOpen && !overlayOpen()) releaseHistory();
}

function openBookPicker(book, fromSearch) {
  if (fromSearch) dismissSearch();
  if (book.chapterCount === 1) {
    openChapter(book, 1, 1);
    return;
  }
  state.pickerBook = book;
  ensureHistory();
  render();
}

function openChapter(book, chapter, verse) {
  scrollRequest += 1;
  state.bookId = book.id;
  state.chapter = clampChapter(book, chapter);
  state.highlightedVerse = verse;
  state.scrollVerse = verse;
  state.pendingScrollVerse = verse;
  state.pickerBook = null;
  state.tocOpen = false;
  dismissSearch();
  render();
  if (!overlayOpen()) releaseHistory();
  persist();
}

function dismissSearch() {
  state.query = '';
  state.searchFocused = false;
  state.fullPage = false;
  state.results = emptyResults();
  state.searchToken += 1;
  const input = document.getElementById('search-input');
  if (input && document.activeElement === input) input.blur();
}

function stepChapter(delta) {
  const index = pageIndex(state.bookId, state.chapter) + delta;
  if (index < 0 || index >= pages.length) return;
  scrollRequest += 1;
  const next = pageAt(index);
  state.bookId = next.bookId;
  state.chapter = next.chapter;
  state.highlightedVerse = null;
  state.scrollVerse = 1;
  state.pendingScrollVerse = 1;
  renderTopBar();
  renderReader();
  renderToc();
  persist();
}

function saveVisibleVerse() {
  if (suppressScrollSave) return;
  const page = pageSlot('current');
  if (!page) return;
  const pageTop = page.getBoundingClientRect().top + 8;
  let verse = 1;
  for (const el of page.querySelectorAll('.verse')) {
    if (el.getBoundingClientRect().bottom < pageTop) verse = Number(el.dataset.verse);
    else {
      verse = Number(el.dataset.verse);
      break;
    }
  }
  state.scrollVerse = verse;
  persist();
}

function bindReader() {
  const reader = document.getElementById('reader');
  const pager = document.getElementById('pager');
  const pointers = new Map();
  let drag = null;
  let pinch = null;

  reader.addEventListener('touchmove', (event) => {
    if (event.touches.length >= 2 || drag?.mode === 'x') event.preventDefault();
  }, { passive: false });

  reader.addEventListener('pointerdown', (event) => {
    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (pointers.size >= 2) {
      drag = null;
      pinch = pinchStart(pointers);
      return;
    }
    drag = { id: event.pointerId, x: event.clientX, y: event.clientY, dx: 0, mode: null };
  });

  reader.addEventListener('pointermove', (event) => {
    if (!pointers.has(event.pointerId)) return;
    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (pointers.size >= 2 && pinch) {
      const ratio = pinchDistance(pointers) / pinch.distance;
      if (Number.isFinite(ratio) && ratio > 0) setScale(pinch.scale * ratio);
      return;
    }
    if (!drag || drag.id !== event.pointerId || state.fullPage) return;
    drag.dx = event.clientX - drag.x;
    const dy = event.clientY - drag.y;
    if (!drag.mode) {
      if (Math.abs(drag.dx) < 8 && Math.abs(dy) < 8) return;
      drag.mode = Math.abs(drag.dx) > Math.abs(dy) ? 'x' : 'y';
    }
    if (drag.mode !== 'x') return;
    if (!reader.hasPointerCapture(event.pointerId)) reader.setPointerCapture(event.pointerId);
    event.preventDefault();
    const index = pageIndex(state.bookId, state.chapter);
    let dx = drag.dx;
    if ((index === 0 && dx > 0) || (index === pages.length - 1 && dx < 0)) dx *= 0.35;
    pager.style.transition = 'none';
    pager.style.transform = `translateX(calc(-33.3333% + ${dx}px))`;
  });

  const endPointer = (event) => {
    pointers.delete(event.pointerId);
    if (pointers.size < 2) pinch = null;
    if (!drag || drag.id !== event.pointerId) return;
    const { dx, mode } = drag;
    drag = null;
    if (mode !== 'x') {
      pager.style.transform = 'translateX(-33.3333%)';
      return;
    }
    ignoreVerseClick = true;
    setTimeout(() => {
      ignoreVerseClick = false;
    }, 0);
    const width = reader.clientWidth || 1;
    const index = pageIndex(state.bookId, state.chapter);
    let direction = 0;
    if (dx <= -Math.min(72, width * 0.18) && index < pages.length - 1) direction = 1;
    else if (dx >= Math.min(72, width * 0.18) && index > 0) direction = -1;
    if (!direction) {
      pager.style.transition = 'transform 160ms ease';
      pager.style.transform = 'translateX(-33.3333%)';
      return;
    }
    const destination = direction < 0 ? '0%' : '-66.6667%';
    pager.style.transition = 'transform 180ms ease';
    pager.style.transform = `translateX(${destination})`;
    let finished = false;
    const finish = () => {
      if (finished) return;
      finished = true;
      pager.removeEventListener('transitionend', finish);
      stepChapter(direction);
      pager.style.transition = 'none';
    };
    pager.addEventListener('transitionend', finish);
    setTimeout(finish, 220);
  };
  reader.addEventListener('pointerup', endPointer);
  reader.addEventListener('pointercancel', endPointer);

  pageSlot('current').addEventListener('scroll', () => {
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(saveVisibleVerse, 200);
  }, true);
}

function bindPinch(element) {
  const pointers = new Map();
  let pinch = null;
  element.addEventListener('touchmove', (event) => {
    if (event.touches.length >= 2) event.preventDefault();
  }, { passive: false });
  element.addEventListener('pointerdown', (event) => {
    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (pointers.size >= 2) pinch = pinchStart(pointers);
  });
  element.addEventListener('pointermove', (event) => {
    if (!pointers.has(event.pointerId)) return;
    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (pinch && pointers.size >= 2) {
      const ratio = pinchDistance(pointers) / pinch.distance;
      if (Number.isFinite(ratio) && ratio > 0) setScale(pinch.scale * ratio);
    }
  });
  const end = (event) => {
    pointers.delete(event.pointerId);
    if (pointers.size < 2) pinch = null;
  };
  element.addEventListener('pointerup', end);
  element.addEventListener('pointercancel', end);
}

function pinchStart(pointers) {
  return { distance: Math.max(1, pinchDistance(pointers)), scale: state.textScale };
}

function pinchDistance(pointers) {
  const [a, b] = [...pointers.values()];
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function onKeyDown(event) {
  if (event.key === 'Escape') {
    if (!overlayOpen()) return;
    event.preventDefault();
    dismissFromUi();
    return;
  }
  if (event.target.closest('input, textarea')) return;
    if (state.tocOpen || state.pickerBook || state.fullPage || state.query.trim()) return;
  if (event.key === 'ArrowRight') {
    event.preventDefault();
    stepChapter(1);
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault();
    stepChapter(-1);
  }
}

function persist() {
  const prefs = {
    bookId: state.bookId,
    chapter: state.chapter,
    scrollVerse: state.scrollVerse,
    highlightedVerse: state.highlightedVerse,
    textScale: state.textScale,
    theme: state.theme
  };
  localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
}

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  })[char]);
}
