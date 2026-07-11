const $ = id => document.getElementById(id);

let config = null;
let currentUser = null;
let mode = 'login';
let newsCache = [];
let selPic = 'img/news1.svg';

const SOC_ICONS = {
  discord: '<svg viewBox="0 0 24 24"><path d="M19.3 5.3A16.9 16.9 0 0 0 15.1 4l-.5 1a15.6 15.6 0 0 0-5.2 0L8.9 4a16.9 16.9 0 0 0-4.2 1.3C2 9.2 1.3 13 1.6 16.7A17 17 0 0 0 6.8 19l1.1-1.8c-.6-.2-1.2-.5-1.7-.9l.4-.3a12.2 12.2 0 0 0 10.8 0l.4.3c-.5.4-1.1.7-1.7.9L17.2 19a17 17 0 0 0 5.2-2.3c.4-4.3-.7-8-3.1-11.4zM8.7 14.5c-1 0-1.8-.9-1.8-2s.8-2 1.8-2 1.8.9 1.8 2-.8 2-1.8 2zm6.6 0c-1 0-1.8-.9-1.8-2s.8-2 1.8-2 1.8.9 1.8 2-.8 2-1.8 2z"/></svg>',
  telegram: '<svg viewBox="0 0 24 24"><path d="M21.9 4.6 19 19.3c-.2 1-.8 1.2-1.6.8l-4.5-3.3-2.2 2.1c-.2.2-.4.4-.9.4l.3-4.6L18.6 7c.4-.3-.1-.5-.6-.2L7.7 13.2l-4.4-1.4c-1-.3-1-1 .2-1.4l17.2-6.6c.8-.3 1.5.2 1.2 1.4z"/></svg>',
  vk: '<svg viewBox="0 0 24 24"><path d="M12.8 17.5c-5.7 0-9-3.9-9.1-10.4h2.9c.1 4.8 2.2 6.8 3.9 7.2V7.1h2.7v4.1c1.6-.2 3.3-2.1 3.9-4.1h2.7c-.4 2.5-2.3 4.4-3.6 5.2 1.3.6 3.5 2.3 4.3 5.2h-3c-.6-2-2.2-3.5-4.3-3.7v3.7z"/></svg>',
  youtube: '<svg viewBox="0 0 24 24"><path d="M21.6 7.2a2.5 2.5 0 0 0-1.8-1.8C18.2 5 12 5 12 5s-6.2 0-7.8.4A2.5 2.5 0 0 0 2.4 7.2 26 26 0 0 0 2 12c0 1.6.1 3.2.4 4.8a2.5 2.5 0 0 0 1.8 1.8c1.6.4 7.8.4 7.8.4s6.2 0 7.8-.4a2.5 2.5 0 0 0 1.8-1.8c.3-1.6.4-3.2.4-4.8 0-1.6-.1-3.2-.4-4.8zM10 15.2V8.8l5.4 3.2z"/></svg>',
  site: '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm7.9 9h-3a15.6 15.6 0 0 0-1.2-5.5A8 8 0 0 1 19.9 11zM12 4c.9 1 2.2 3.2 2.5 7h-5C9.8 7.2 11.1 5 12 4zM8.3 5.5A15.6 15.6 0 0 0 7.1 11h-3a8 8 0 0 1 4.2-5.5zM4.1 13h3c.1 2 .5 3.9 1.2 5.5A8 8 0 0 1 4.1 13zM12 20c-.9-1-2.2-3.2-2.5-7h5c-.3 3.8-1.6 6-2.5 7zm3.7-1.5c.7-1.6 1.1-3.5 1.2-5.5h3a8 8 0 0 1-4.2 5.5z"/></svg>'
};
const PICS = ['img/news1.svg', 'img/news2.svg', 'img/news3.svg', 'img/media1.svg', 'img/media3.svg'];

function esc(s) {
  return String(s).replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function mainServer() { return config.servers.find(s => !s.comingSoon) || config.servers[0]; }
function isAdmin() { return !!(currentUser && (config.adminUsers || []).some(a => a.toLowerCase() === currentUser.toLowerCase())); }

/* ---------- инициализация ---------- */
(async () => {
  config = await window.launcher.getConfig();
  $('brand-name').innerHTML = esc(config.serverName).replace(/\s*RP$/i, ' <b>RP</b>');
  const ms = mainServer();
  $('sh-name').textContent = ms.name;
  $('sh-name2').textContent = ms.name;
  document.querySelectorAll('.sh-bg').forEach(el => el.style.backgroundImage = `url('${ms.image}')`);

  const saved = localStorage.getItem('rp_user');
  if (saved) setUser(saved);

  renderServers();
  renderSocials();
  renderMedia();
  loadNews();
  loadSettings();
  updateOnline();
  setInterval(updateOnline, 30000);
})();

/* ---------- окно ---------- */
$('btn-min').onclick = () => window.launcher.minimize();
$('btn-close').onclick = () => window.launcher.close();

/* ---------- навигация ---------- */
function openPage(page) {
  document.querySelectorAll('.nav-item').forEach(b =>
    b.classList.toggle('active', b.dataset.page === page));
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  $('page-' + page).classList.add('active');
  $('btn-back').classList.toggle('hidden', page === 'home');
  $('tb-online-wrap').classList.toggle('hidden', page !== 'home' && page !== 'servers');
  $('content').scrollTop = 0;
}
document.querySelectorAll('.nav-item[data-page]').forEach(btn => {
  btn.onclick = () => openPage(btn.dataset.page);
});
$('btn-back').onclick = () => openPage('home');
$('btn-more-news').onclick = () => openPage('news');
$('chip-discord').onclick = () => window.launcher.openUrl(config.discordUrl);
$('chip-site').onclick = () => window.launcher.openUrl(config.siteUrl);

/* ---------- модалки ---------- */
document.querySelectorAll('.modal-x').forEach(b => {
  b.onclick = () => $(b.dataset.close).classList.add('hidden');
});
$('btn-settings').onclick = () => $('modal-settings').classList.remove('hidden');
$('btn-login-open').onclick = () => $('modal-auth').classList.remove('hidden');
document.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !$('modal-auth').classList.contains('hidden')) doAuth();
  if (e.key === 'Escape') document.querySelectorAll('.modal-wrap').forEach(m => m.classList.add('hidden'));
});

/* ---------- аккаунт ---------- */
function setMode(m) {
  mode = m;
  $('tab-login').classList.toggle('active', m === 'login');
  $('tab-register').classList.toggle('active', m === 'register');
  $('auth-password2').classList.toggle('hidden', m === 'login');
  $('auth-title').textContent = m === 'login' ? 'Вход в аккаунт' : 'Регистрация';
  $('btn-auth').textContent = m === 'login' ? 'ВОЙТИ' : 'СОЗДАТЬ АККАУНТ';
  $('auth-error').textContent = '';
}
$('tab-login').onclick = () => setMode('login');
$('tab-register').onclick = () => setMode('register');
$('btn-auth').onclick = doAuth;

async function doAuth() {
  const u = $('auth-username').value.trim();
  const p = $('auth-password').value;
  const err = $('auth-error');
  err.textContent = '';
  if (!u || !p) { err.textContent = 'Заполни все поля'; return; }
  if (mode === 'register' && p !== $('auth-password2').value) {
    err.textContent = 'Пароли не совпадают'; return;
  }
  $('btn-auth').disabled = true;
  const res = mode === 'login'
    ? await window.launcher.login(u, p)
    : await window.launcher.register(u, p);
  $('btn-auth').disabled = false;
  if (!res.ok) { err.textContent = res.error; return; }
  localStorage.setItem('rp_user', res.username);
  $('modal-auth').classList.add('hidden');
  $('auth-password').value = '';
  setUser(res.username);
}

function setUser(username) {
  currentUser = username;
  $('side-nick').textContent = username;
  $('avatar').textContent = username[0].toUpperCase();
  $('side-user').classList.remove('hidden');
  $('btn-login-open').classList.add('hidden');
  $('btn-add-news').classList.toggle('hidden', !isAdmin());
  loadNews();
}
$('btn-logout').onclick = () => {
  localStorage.removeItem('rp_user');
  currentUser = null;
  $('side-user').classList.add('hidden');
  $('btn-login-open').classList.remove('hidden');
  $('btn-add-news').classList.add('hidden');
  loadNews();
};

/* ---------- сервера ---------- */
function renderServers() {
  $('servers-grid').innerHTML = config.servers.map((s, i) => `
    <div class="srv-card${s.comingSoon ? ' soon' : ''}" data-i="${i}">
      <div class="sv-bg" style="background-image:url('${esc(s.image || '')}')"></div>
      <div class="sv-top">${esc(s.num)} ${s.emoji || ''}</div>
      <div class="sv-name">${esc(s.name)}</div>
      <div class="sv-sub">${s.comingSoon ? 'открытие скоро' : 'сейчас играет'}</div>
      ${s.comingSoon ? '<div class="sv-soon">СКОРО</div>'
        : `<div class="sv-count" id="sv-count-${i}">—</div><button class="sv-playbtn">▶</button>`}
    </div>`).join('');
  document.querySelectorAll('.srv-card:not(.soon)').forEach(c => {
    c.onclick = () => play(config.servers[+c.dataset.i]);
  });
}

/* ---------- онлайн ---------- */
async function updateOnline() {
  let total = 0, mainStat = null;
  for (let i = 0; i < config.servers.length; i++) {
    const s = config.servers[i];
    if (s.comingSoon) continue;
    const st = await window.launcher.serverStatus(s.ip, s.port);
    if (st.online) total += st.players;
    if (!mainStat) mainStat = st;
    const el = $('sv-count-' + i);
    if (el) el.textContent = st.online ? st.players : 'офф';
  }
  const st = mainStat || { online: false, players: 0 };
  $('online-now').textContent = st.online ? total.toLocaleString('ru') : '—';
  $('sh-count').textContent = st.online ? st.players : '—';
  $('sh-count2').textContent = st.online ? st.players : '—';
  $('sh-online').textContent = st.online ? st.players : 'офф';
  $('sh-online2').textContent = st.online ? st.players : 'офф';
}

/* ---------- новости ---------- */
function newsCardHtml(n, i, del) {
  const img = n.image || PICS[i % PICS.length];
  return `
    <div class="news-card" data-i="${i}">
      ${del ? `<button class="nc-del" data-del="${i}" title="Удалить">✕</button>` : ''}
      <div class="nc-img" style="background-image:url('${esc(img)}')"></div>
      <div class="nc-title">${esc(n.title)}</div>
      <div class="nc-date"><span>${esc(n.date || '')}</span><span class="nc-arrow">⬊</span></div>
    </div>`;
}
function renderNews() {
  const admin = isAdmin();
  $('news-grid').innerHTML = newsCache.slice(0, 3).map((n, i) => newsCardHtml(n, i, false)).join('');
  $('news-grid-full').innerHTML = newsCache.map((n, i) => newsCardHtml(n, i, admin)).join('');
  document.querySelectorAll('.news-card').forEach(c => {
    c.onclick = e => {
      if (e.target.dataset.del !== undefined) return;
      openArticle(+c.dataset.i);
    };
  });
  document.querySelectorAll('.nc-del').forEach(b => {
    b.onclick = async e => {
      e.stopPropagation();
      newsCache = await window.launcher.delNews(+b.dataset.del);
      renderNews();
    };
  });
}
async function loadNews() {
  newsCache = await window.launcher.getNews();
  renderNews();
}
function openArticle(i) {
  const n = newsCache[i];
  if (!n) return;
  $('art-img').style.backgroundImage = `url('${esc(n.image || PICS[i % PICS.length])}')`;
  $('art-title').textContent = n.title;
  $('art-date').textContent = n.date || '';
  $('art-text').textContent = n.text || '';
  openPage('article');
}

/* ---------- добавление новости (админ) ---------- */
$('btn-add-news').onclick = () => {
  $('nn-title').value = ''; $('nn-text').value = ''; $('nn-error').textContent = '';
  selPic = PICS[0];
  $('nn-pics').innerHTML = PICS.slice(0, 4).map(p =>
    `<div class="nn-pic${p === selPic ? ' sel' : ''}" data-p="${p}" style="background-image:url('${p}')"></div>`).join('');
  document.querySelectorAll('.nn-pic').forEach(el => {
    el.onclick = () => {
      selPic = el.dataset.p;
      document.querySelectorAll('.nn-pic').forEach(x => x.classList.toggle('sel', x === el));
    };
  });
  $('modal-news').classList.remove('hidden');
};
$('btn-news-save').onclick = async () => {
  const title = $('nn-title').value.trim();
  const text = $('nn-text').value.trim();
  if (!title || !text) { $('nn-error').textContent = 'Заполни заголовок и текст'; return; }
  newsCache = await window.launcher.addNews({ title, text, image: selPic });
  $('modal-news').classList.add('hidden');
  renderNews();
  openPage('news');
};

/* ---------- сообщества ---------- */
function renderSocials() {
  $('soc-grid').innerHTML = (config.socials || []).map(s => `
    <div class="soc-card" data-url="${esc(s.url)}">
      <div class="soc-ico">${SOC_ICONS[s.type] || SOC_ICONS.site}</div>
      <div class="soc-title">${esc(s.title)}</div>
    </div>`).join('');
  document.querySelectorAll('.soc-card').forEach(c => {
    c.onclick = () => window.launcher.openUrl(c.dataset.url);
  });
}

/* ---------- играй и смотри ---------- */
function renderMedia() {
  $('media-strip').innerHTML = (config.media || []).map(m => `
    <div class="media-card" data-url="${esc(m.url)}" style="background-image:url('${esc(m.thumb || '')}')">
      <div class="mc-t">${esc(m.title)}</div>
    </div>`).join('');
  document.querySelectorAll('.media-card').forEach(c => {
    c.onclick = () => window.launcher.openUrl(c.dataset.url);
  });
}

/* ---------- настройки ---------- */
async function loadSettings() {
  const s = await window.launcher.getSettings();
  $('ram-slider').value = s.ramMb;
  $('ram-label').textContent = s.ramMb + ' МБ';
}
$('ram-slider').oninput = e => $('ram-label').textContent = e.target.value + ' МБ';
$('btn-save-settings').onclick = async () => {
  await window.launcher.setSettings({ ramMb: parseInt($('ram-slider').value, 10) });
  $('settings-saved').textContent = 'Сохранено ✓';
  setTimeout(() => $('settings-saved').textContent = '', 2000);
};

/* ---------- запуск ---------- */
window.launcher.onStatus(s => $('status-text').textContent = s);
window.launcher.onProgress(p => {
  $('progress-fill').style.width = Math.round(Math.min(1, p.value) * 100) + '%';
});
window.launcher.onGameStarted(() => $('status-text').textContent = 'Игра запущена. Приятной игры!');
window.launcher.onGameClosed(() => {
  setPlayState(false);
  $('progress-wrap').classList.add('hidden');
});

function setPlayState(running) {
  [$('btn-play'), $('btn-play2')].forEach(b => {
    b.disabled = running;
    b.textContent = running ? 'Запуск...' : 'Играть';
  });
}
async function play(server) {
  if (!currentUser) { $('modal-auth').classList.remove('hidden'); return; }
  const s = server && server.ip ? server : mainServer();
  openPage('home');
  setPlayState(true);
  $('progress-wrap').classList.remove('hidden');
  $('progress-fill').style.width = '0%';
  const res = await window.launcher.launch(currentUser, s.ip, s.port);
  if (!res.ok) {
    $('status-text').textContent = 'Ошибка: ' + res.error;
    setPlayState(false);
  }
}
$('btn-play').onclick = () => play();
$('btn-play2').onclick = () => play();

/* ---------- автообновление ---------- */
let updateReady = false;
window.launcher.onUpdateAvailable(() => {
  const b = $('update-banner');
  b.classList.remove('hidden');
  b.textContent = 'Скачивание обновления...';
});
window.launcher.onUpdateProgress(p => {
  if (!updateReady) $('update-banner').textContent = 'Скачивание: ' + Math.round(p.percent) + '%';
});
window.launcher.onUpdateReady(() => {
  updateReady = true;
  const b = $('update-banner');
  b.classList.remove('hidden');
  b.innerHTML = 'Доступно обновление <span>⭳</span>';
});
$('update-banner').onclick = () => { if (updateReady) window.launcher.installUpdate(); };
