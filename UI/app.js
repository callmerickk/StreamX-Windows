'use strict';

const $ = id => document.getElementById(id);
const state = {
  page: 'Home', shelves: [], featured: [], activeIndex: 0, currentItem: null,
  list: safeParse(localStorage.getItem('streamx-my-list'), []),
  profile: localStorage.getItem('streamx-profile') || 'Rakesh',
  requestNo: 0, pending: new Map(), lastInput: Date.now(), searchTimer: null,
  homeBusy: false, sources: [],
};

function safeParse(s, fallback) { try { return s ? JSON.parse(s) : fallback; } catch { return fallback; } }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function setImage(el, url, fallback = '') {
  if (!el) return;
  const valid = typeof url === 'string' && /^https?:\/\//i.test(url.trim());
  if (valid) { el.style.backgroundImage = `url("${url.replace(/["\\]/g, '\\$&')}")`; el.classList.remove('no-art'); }
  else { el.style.backgroundImage = fallback; el.classList.add('no-art'); }
}
function setImageElement(el, url) {
  if (!el) return;
  if (typeof url === 'string' && /^https?:\/\//i.test(url.trim())) {
    el.src = url; el.classList.remove('image-missing');
    el.onerror = () => { el.classList.add('image-missing'); el.removeAttribute('src'); };
  } else { el.classList.add('image-missing'); el.removeAttribute('src'); }
}
function touch() { state.lastInput = Date.now(); }
function toast(message) {
  const el = $('toast'); el.textContent = message; el.classList.add('show');
  clearTimeout(toast.timer); toast.timer = setTimeout(() => el.classList.remove('show'), 2700);
}
function bridgeCall(action, payload = {}) {
  if (!window.chrome?.webview) return Promise.reject(new Error('The StreamX Windows bridge is unavailable. Launch StreamX.Windows.exe instead of opening UI/index.html in a browser.'));
  const id = String(++state.requestNo);
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { state.pending.delete(id); reject(new Error('StreamX request timed out. Check your internet connection and addon URL.')); }, 25000);
    state.pending.set(id, { resolve, reject, timer });
    window.chrome.webview.postMessage({ id, action, payload });
  });
}
if (window.chrome?.webview) {
  window.chrome.webview.addEventListener('message', event => {
    const message = event.data || {};
    const item = state.pending.get(String(message.id));
    if (!item) return;
    clearTimeout(item.timer); state.pending.delete(String(message.id));
    if (message.ok) item.resolve(message.data);
    else item.reject(new Error(message.error || 'The request failed.'));
  });
}

function metaLine(item) {
  const bits = [];
  if (item?.imdbRating) bits.push(`<span class="rating">★ ${escapeHtml(item.imdbRating)}</span>`);
  if (item?.releaseInfo) bits.push(`<span>${escapeHtml(String(item.releaseInfo).slice(0, 4))}</span>`);
  if (item?.runtime) bits.push(`<span>${escapeHtml(item.runtime)}</span>`);
  if (item?.genres?.length) bits.push(`<span>${escapeHtml(item.genres.slice(0, 2).join(' · '))}</span>`);
  bits.push(`<span class="pill">${String(item?.type || 'movie').toUpperCase() === 'SERIES' ? 'SERIES' : 'MOVIE'}</span>`);
  return bits.join('');
}
function itemDescription(item) { return item?.description?.trim() || 'No description was supplied by this catalogue.'; }
function addToList(item) {
  if (!item?.id) return;
  const key = `${item.type}/${item.id}`;
  const idx = state.list.findIndex(x => `${x.type}/${x.id}` === key);
  if (idx >= 0) { state.list.splice(idx, 1); toast('Removed from My List'); }
  else { state.list.push(item); toast('Added to My List'); }
  localStorage.setItem('streamx-my-list', JSON.stringify(state.list));
  renderList(); updateHeroListButton();
  if (! $('detailModal').classList.contains('hidden')) updateDetailListButton();
}
function isInList(item) { return !!item && state.list.some(x => x.type === item.type && x.id === item.id); }
function updateHeroListButton() { $('heroList').textContent = isInList(state.featured[state.activeIndex]) ? '✓' : '＋'; }

function renderDots() {
  const host = $('deckDots'); host.replaceChildren();
  state.featured.slice(0, 10).forEach((item, idx) => {
    const dot = document.createElement('button'); dot.className = 'deck-dot' + (idx === state.activeIndex ? ' active' : '');
    dot.title = `Featured title ${idx + 1}`; dot.setAttribute('aria-label', `Show featured title ${idx + 1}`);
    dot.addEventListener('click', () => { touch(); showHero(idx, true); }); host.appendChild(dot);
  });
}
function peekArt(el, item) { setImage(el, item?.background || item?.poster, 'linear-gradient(140deg,#19131a,#0d1015)'); }
function renderHeroContent(item) {
  const safeItem = item || {};
  setImage($('heroImage'), safeItem.background || safeItem.poster);
  $('heroKicker').textContent = safeItem.type ? `${safeItem.type} · FEATURED` : 'STREAMX';
  const logo = $('heroLogo');
  if (safeItem.logo && /^https?:\/\//i.test(safeItem.logo)) {
    setImageElement(logo, safeItem.logo); logo.classList.remove('hidden'); logo.onerror = () => { logo.classList.add('hidden'); $('heroTitle').classList.remove('hidden'); }; $('heroTitle').classList.add('hidden');
  } else { logo.classList.add('hidden'); $('heroTitle').classList.remove('hidden'); $('heroTitle').textContent = safeItem.name || 'Your catalogue'; }
  $('heroMeta').innerHTML = metaLine(safeItem);
  $('heroDescription').textContent = itemDescription(safeItem);
  $('heroBadge').textContent = safeItem.imdbRating && Number(safeItem.imdbRating) >= 7.5 ? `★ ${safeItem.imdbRating}` : (safeItem.type || 'FEATURED').toUpperCase();
  $('heroPlay').disabled = !safeItem.id;
  $('heroInfo').disabled = !safeItem.id;
  $('heroList').disabled = !safeItem.id;
  updateHeroListButton();
}
function showHero(index, animate = false) {
  if (!state.featured.length) return;
  state.activeIndex = (index + state.featured.length) % state.featured.length;
  const item = state.featured[state.activeIndex];
  const hero = $('heroCard');
  hero.classList.remove('switching-in');
  if (animate) hero.classList.add('switching-out');
  const delay = animate ? 165 : 0;
  window.setTimeout(() => {
    renderHeroContent(item);
    peekArt($('peekFront'), state.featured[(state.activeIndex + 1) % state.featured.length]);
    peekArt($('peekBack'), state.featured[(state.activeIndex + 2) % state.featured.length]);
    renderDots();
    hero.classList.remove('switching-out');
    if (animate) {
      hero.classList.add('switching-in');
      requestAnimationFrame(() => requestAnimationFrame(() => hero.classList.remove('switching-in')));
    }
  }, delay);
}

function makePoster(item) {
  const card = document.createElement('button'); card.type = 'button'; card.className = 'poster-card';
  card.setAttribute('aria-label', `${item.name || 'Title'} details`);
  const poster = document.createElement('img'); poster.className = 'poster-img'; poster.alt = ''; poster.loading = 'lazy'; setImageElement(poster, item.poster || item.background);
  const backdrop = document.createElement('img'); backdrop.className = 'poster-backdrop'; backdrop.alt = ''; backdrop.loading = 'lazy'; setImageElement(backdrop, item.background || item.poster);
  const shade = document.createElement('span'); shade.className = 'poster-shade';
  const stamp = document.createElement('span'); stamp.className = 'poster-stamp'; stamp.textContent = item.name || 'Untitled';
  const info = document.createElement('span'); info.className = 'poster-info';
  const title = document.createElement('span'); title.className = 'poster-title'; title.textContent = item.name || 'Untitled';
  const details = document.createElement('span'); details.className = 'poster-meta'; details.innerHTML = metaLine(item);
  const desc = document.createElement('span'); desc.className = 'poster-desc'; desc.textContent = itemDescription(item);
  info.append(title, details, desc); card.append(poster, backdrop, shade, stamp, info);
  card.addEventListener('mouseenter', () => card.classList.add('expanded'));
  card.addEventListener('mouseleave', () => card.classList.remove('expanded'));
  card.addEventListener('focus', () => card.classList.add('expanded'));
  card.addEventListener('blur', () => card.classList.remove('expanded'));
  card.addEventListener('click', () => { touch(); openDetail(item); });
  card.addEventListener('keydown', e => {
    const list = Array.from(card.parentElement.querySelectorAll('.poster-card'));
    const i = list.indexOf(card);
    if (e.key === 'ArrowRight' && list[i + 1]) { e.preventDefault(); list[i + 1].focus(); }
    if (e.key === 'ArrowLeft' && list[i - 1]) { e.preventDefault(); list[i - 1].focus(); }
  });
  return card;
}
function renderShelves() {
  const host = $('shelves'); host.replaceChildren();
  if (!state.shelves.length) {
    const empty = document.createElement('div'); empty.className = 'no-catalog';
    empty.innerHTML = '<strong>Your catalogue has not loaded yet.</strong>StreamX is waiting for addon catalogues. Check your internet connection, then open Settings → Catalogue addons to add or refresh a source.';
    const button = document.createElement('button'); button.className = 'settings-button'; button.textContent = 'Open addon settings'; button.addEventListener('click', () => showPage('Settings'));
    empty.appendChild(button); host.appendChild(empty); return;
  }
  state.shelves.forEach(shelf => {
    if (!shelf.items?.length) return;
    const section = document.createElement('section'); section.className = 'shelf';
    const heading = document.createElement('div'); heading.className = 'shelf-head';
    const h = document.createElement('h2'); h.textContent = shelf.title || 'Catalogue';
    const see = document.createElement('button'); see.className = 'see-all'; see.type = 'button'; see.innerHTML = 'See all <span>›</span>';
    see.addEventListener('click', () => { showPage('Search'); $('searchInput').value = ''; $('searchStatus').textContent = `Searching installed addons can show more titles than this ${shelf.title} shelf.`; $('searchInput').focus(); });
    heading.append(h, see);
    const row = document.createElement('div'); row.className = 'shelf-row';
    shelf.items.slice(0, 40).forEach(item => row.appendChild(makePoster(item)));
    section.append(heading, row); host.appendChild(section);
  });
}
function showHomeStatus(message, error = false, loading = false) {
  const el = $('homeStatus'); el.replaceChildren(); el.classList.toggle('error', error);
  if (loading) { const spin = document.createElement('span'); spin.className = 'loading-ring'; el.appendChild(spin); }
  const text = document.createElement('span'); text.textContent = message; el.appendChild(text);
}
async function loadHome() {
  if (state.homeBusy) return; state.homeBusy = true; showHomeStatus('Loading catalogues from installed addons…', false, true);
  try {
    const data = await bridgeCall('home');
    state.shelves = data?.shelves || [];
    state.featured = data?.featured || state.shelves.flatMap(s => s.items || []).filter(m => m.background || m.poster).slice(0, 10);
    if (state.featured.length) {
      $('deckStage').classList.remove('no-featured'); showHero(0, false);
      showHomeStatus(`${state.shelves.length} catalogue shelves loaded · ${state.featured.length} featured titles`);
      $('statusText').textContent = `${state.shelves.length} catalogue shelves ready`;
    } else {
      $('heroKicker').textContent = 'CATALOGUE'; $('heroTitle').textContent = 'No titles returned'; $('heroDescription').textContent = 'The connected addons did not return a catalogue. Open Settings to check your connection or add another addon.'; $('heroMeta').replaceChildren();
      $('heroPlay').disabled = $('heroInfo').disabled = $('heroList').disabled = true;
      showHomeStatus('No catalogue results. Open Settings → Catalogue addons to check your connection.', true);
      $('statusText').textContent = 'No catalogue results';
    }
    renderShelves();
  } catch (error) {
    state.shelves = []; state.featured = []; renderShelves();
    $('heroTitle').textContent = 'Connect your catalogue'; $('heroDescription').textContent = 'StreamX could not load the live catalogue. Check the connection, then retry in Settings.'; $('heroMeta').replaceChildren();
    $('heroPlay').disabled = $('heroInfo').disabled = $('heroList').disabled = true;
    showHomeStatus(error.message, true); $('statusText').textContent = 'Catalogue not connected';
  } finally { state.homeBusy = false; }
}

function showPage(page) {
  touch(); state.page = page;
  document.querySelectorAll('.nav-item[data-page]').forEach(btn => btn.classList.toggle('active', btn.dataset.page === page));
  for (const [id, p] of [['homePage','Home'],['searchPage','Search'],['libraryPage','Library'],['spacePage','My Space'],['settingsPage','Settings']]) $(id).classList.toggle('hidden', page !== p);
  if (page === 'Library') renderList();
  if (page === 'My Space') syncProfile();
  if (page === 'Settings') loadAddons();
  if (page === 'Home') { $('main').scrollTop = 0; }
}
function renderList() {
  const host = $('listCards'); host.replaceChildren();
  state.list.forEach(item => host.appendChild(makePoster(item)));
  $('listEmpty').classList.toggle('hidden', state.list.length > 0);
}
function syncProfile() {
  $('profileName').textContent = state.profile; $('avatar').textContent = (state.profile.trim()[0] || 'S').toUpperCase();
  $('bigAvatar').textContent = (state.profile.trim()[0] || 'S').toUpperCase(); $('profileInput').value = state.profile;
}

async function runSearch(query) {
  query = query.trim(); $('searchResults').replaceChildren();
  if (query.length < 2) { $('searchStatus').textContent = 'Type at least two characters to search.'; $('searchBusy').classList.add('hidden'); return; }
  $('searchBusy').classList.remove('hidden'); $('searchStatus').textContent = 'Searching installed addons…';
  try {
    const result = await bridgeCall('search', { query }); const items = result?.results || [];
    items.forEach(item => $('searchResults').appendChild(makePoster(item)));
    $('searchStatus').textContent = result?.message || (items.length ? `${items.length} results` : 'No results.');
  } catch (error) { $('searchStatus').textContent = error.message; }
  finally { $('searchBusy').classList.add('hidden'); }
}

async function openDetail(item) {
  state.currentItem = item; state.sources = [];
  $('detailTitle').textContent = item.name || 'Untitled'; $('detailKicker').textContent = `${(item.type || 'movie').toUpperCase()} · TITLE DETAILS`;
  $('detailMeta').innerHTML = metaLine(item); $('detailDescription').textContent = itemDescription(item);
  setImage($('detailArtwork'), item.background || item.poster, 'linear-gradient(140deg,#26161b,#101115)');
  $('sourcesList').replaceChildren(); $('sourcesList').innerHTML = '<div class="empty-message"><span class="loading-ring"></span> Checking installed addon sources…</div>';
  $('sourceCount').textContent = ''; updateDetailListButton(); $('detailModal').classList.remove('hidden');
  bridgeCall('meta', {type:item.type || 'movie',id:item.id}).then(res => {
    if (state.currentItem?.id !== item.id || !res?.meta) return;
    state.currentItem = {...item, ...res.meta}; $('detailMeta').innerHTML = metaLine(state.currentItem);
    $('detailDescription').textContent = itemDescription(state.currentItem); setImage($('detailArtwork'), state.currentItem.background || state.currentItem.poster);
  }).catch(() => {});
  await loadSources();
}
function updateDetailListButton() { $('detailList').textContent = isInList(state.currentItem) ? '✓ In My List' : '＋ My List'; }
async function loadSources() {
  const item = state.currentItem; if (!item) return;
  $('sourcesList').replaceChildren(); $('sourcesList').innerHTML = '<div class="empty-message">Checking sources from addons…</div>';
  try {
    const result = await bridgeCall('streams', {type:item.type || 'movie',id:item.id});
    if (state.currentItem?.id !== item.id) return;
    state.sources = result?.sources || []; $('sourceCount').textContent = result?.count ? `${result.count}` : '';
    renderSources(state.sources);
  } catch (error) { $('sourcesList').innerHTML = `<div class="empty-message">${escapeHtml(error.message)}</div>`; }
}
function renderSources(sources) {
  const host = $('sourcesList'); host.replaceChildren();
  if (!sources.length) { const empty = document.createElement('div'); empty.className = 'empty-message'; empty.textContent = 'No streams were returned by your installed addons for this title. Add or configure addons in Settings. Some addons only return torrent hashes or external resolver links, which this early Windows build does not play yet.'; host.appendChild(empty); return; }
  sources.forEach(source => {
    const row = document.createElement('div'); row.className = 'source-row';
    const main = document.createElement('div'); main.className = 'source-main';
    const strong = document.createElement('strong'); strong.textContent = [source.name, source.title].filter(Boolean).join(' · ') || source.addonName || 'Stream source';
    const small = document.createElement('small'); small.textContent = [source.addonName, source.description, source.filename, source.infoHash ? 'Torrent source' : ''].filter(Boolean).join(' — ');
    main.append(strong, small);
    const btn = document.createElement('button'); btn.className = 'source-open'; btn.textContent = source.canOpenDirectly ? 'Try play' : (source.externalUrl ? 'Open link' : 'Not playable yet');
    btn.disabled = !source.canOpenDirectly && !source.externalUrl;
    btn.addEventListener('click', async () => {
      if (source.canOpenDirectly && source.url) openPlayer(source);
      else if (source.externalUrl) { try { await bridgeCall('openExternal', {url:source.externalUrl}); } catch (e) { toast(e.message); } }
    });
    row.append(main, btn); host.appendChild(row);
  });
}
async function openPlayer(source) {
  try {
    await bridgeCall('playStream', { url: source.url, title: state.currentItem?.name || source.title || source.name || 'StreamX', headers: source.headers || {} });
    toast('Opening the StreamX video player…');
  } catch (error) { toast(error.message || 'Could not open this stream.'); }
}
function closeOverlay(id) { $(id).classList.add('hidden'); }

async function loadAddons() {
  $('addonList').innerHTML = '<div class="empty-message">Loading addon manifests…</div>';
  try {
    const data = await bridgeCall('addons'); const host = $('addonList'); host.replaceChildren();
    (data?.addons || []).forEach(addon => {
      const row = document.createElement('div'); row.className = 'addon-row';
      const detail = document.createElement('div'); detail.className = 'addon-details';
      const name = document.createElement('strong'); name.textContent = addon.name || addon.url;
      const url = document.createElement('small'); url.textContent = `${addon.url} · ${addon.catalogCount || 0} catalogue(s)`;
      detail.append(name, url); row.appendChild(detail);
      if (addon.isDefault) { const tag = document.createElement('span'); tag.className = 'addon-badge'; tag.textContent = 'DEFAULT'; row.appendChild(tag); }
      else { const remove = document.createElement('button'); remove.className = 'remove-addon'; remove.textContent = 'Remove'; remove.addEventListener('click', async () => { try { await bridgeCall('removeAddon',{url:addon.url}); toast('Addon removed'); await loadAddons(); await loadHome(); } catch (e) { toast(e.message); } }); row.appendChild(remove); }
      host.appendChild(row);
    });
    const errors = data?.errors || [];
    if (errors.length) { const error = document.createElement('div'); error.className = 'small-status'; error.textContent = errors.map(x => `${x.url}: ${x.error}`).join('\n'); host.appendChild(error); }
    $('addonStatus').textContent = `${(data?.addons || []).length} addon manifest(s) available. Addons and catalogs are saved on this Windows device.`;
  } catch (error) { $('addonList').replaceChildren(); $('addonStatus').textContent = error.message; }
}

// All interactive controls, with keyboard equivalents for the TV's focus-based navigation.
document.querySelectorAll('.nav-item[data-page]').forEach(btn => btn.addEventListener('click', () => showPage(btn.dataset.page)));
$('deckPrev').addEventListener('click', () => { touch(); showHero(state.activeIndex - 1, true); });
$('deckNext').addEventListener('click', () => { touch(); showHero(state.activeIndex + 1, true); });
$('heroInfo').addEventListener('click', () => { const item = state.featured[state.activeIndex]; if (item) openDetail(item); });
$('heroPlay').addEventListener('click', () => { const item = state.featured[state.activeIndex]; if (item) openDetail(item); });
$('heroList').addEventListener('click', () => { const item = state.featured[state.activeIndex]; if (item) addToList(item); });
$('detailPlay').addEventListener('click', loadSources);
$('detailList').addEventListener('click', () => { if (state.currentItem) addToList(state.currentItem); });
document.querySelectorAll('[data-close]').forEach(btn => btn.addEventListener('click', () => closeOverlay(btn.dataset.close)));
$('searchInput').addEventListener('input', e => { clearTimeout(state.searchTimer); state.searchTimer = setTimeout(() => runSearch(e.target.value), 360); });
$('addonForm').addEventListener('submit', async e => {
  e.preventDefault(); const url = $('addonUrl').value.trim(); if (!url) return;
  const button = $('addonForm').querySelector('button'); button.disabled = true; button.textContent = 'Adding…'; $('addonStatus').textContent = 'Checking addon manifest…';
  try { await bridgeCall('addAddon', {url}); $('addonUrl').value = ''; toast('Addon added'); await loadAddons(); await loadHome(); }
  catch (error) { $('addonStatus').textContent = error.message; }
  finally { button.disabled = false; button.textContent = 'Add addon'; }
});
$('refreshAddons').addEventListener('click', async () => { try { await loadAddons(); await loadHome(); toast('Catalogue refreshed'); } catch (e) { toast(e.message); } });
$('profileButton').addEventListener('click', () => showPage('My Space'));
$('saveProfile').addEventListener('click', () => {
  const name = $('profileInput').value.trim().slice(0,32) || 'Profile'; state.profile = name; localStorage.setItem('streamx-profile', name); syncProfile(); toast('Profile name saved on this device');
});
document.addEventListener('pointerdown', touch, {passive:true});
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    if (!$('detailModal').classList.contains('hidden')) closeOverlay('detailModal');
  }
  if (e.key === 'ArrowRight' && document.activeElement === document.body && state.page === 'Home') { touch(); showHero(state.activeIndex + 1, true); }
  if (e.key === 'ArrowLeft' && document.activeElement === document.body && state.page === 'Home') { touch(); showHero(state.activeIndex - 1, true); }
});
// Match the TV deck's eight-second idle advance; do not rotate during a modal, another page, or active input.
setInterval(() => {
  if (state.page !== 'Home' || !$('detailModal').classList.contains('hidden') || Date.now() - state.lastInput < 7500) return;
  const top = $('main').scrollTop;
  if (top > 80 || !state.featured.length) return;
  showHero(state.activeIndex + 1, true);
}, 8000);
window.addEventListener('error', e => { if (e?.message) console.error('StreamX UI:', e.message); });

syncProfile();
loadHome();
// The source TvStart.kt uses staggered glow/mark/word/X animations and exits after roughly three seconds.
setTimeout(() => $('splash')?.remove(), 3400);
