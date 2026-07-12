const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const crypto = require('crypto');
const net = require('net');
const { Client, Authenticator } = require('minecraft-launcher-core');
let autoUpdater = null;
try { autoUpdater = require('electron-updater').autoUpdater; } catch { /* dev без npm install */ }

const CONFIG = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'config.json'), 'utf8'));
// Forge/1.16.5 падает на путях с кириллицей (C:\Users\Алиса\...) —
// таким пользователям кладём игру в C:\HiveRP
const APPDATA = app.getPath('appData');
const DATA_DIR = (process.platform === 'win32' && /[^\x00-\x7F]/.test(APPDATA))
  ? 'C:\\HiveRP'
  : path.join(APPDATA, '.rp-launcher');
const GAME_DIR = path.join(DATA_DIR, 'minecraft');
const USERS_FILE = path.join(DATA_DIR, 'users.json');
const SETTINGS_FILE = path.join(DATA_DIR, 'settings.json');
const SESSION_FILE = path.join(DATA_DIR, 'session.json');
const NEWS_FILE = path.join(DATA_DIR, 'news.json');

fs.mkdirSync(GAME_DIR, { recursive: true });

const LOG_FILE = path.join(DATA_DIR, 'launcher.log');
function log(line) {
  try { fs.appendFileSync(LOG_FILE, new Date().toISOString() + ' ' + line + '\n'); } catch {}
}
log('=== запуск лаунчера, папка игры: ' + GAME_DIR + ' ===');

let win;

function createWindow() {
  win = new BrowserWindow({
    width: 1200,
    height: 740,
    minWidth: 1000,
    minHeight: 640,
    frame: false,
    resizable: true,
    backgroundColor: '#0b0714',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

app.whenReady().then(() => {
  createWindow();
  setupAutoUpdates();
});

/* ---------- автообновление через GitHub Releases ---------- */
function setupAutoUpdates() {
  if (!autoUpdater || !app.isPackaged) return;
  autoUpdater.autoDownload = true;
  autoUpdater.on('update-available', () => send('update:available'));
  autoUpdater.on('download-progress', p => send('update:progress', { percent: p.percent }));
  autoUpdater.on('update-downloaded', () => send('update:ready'));
  const check = () => autoUpdater.checkForUpdates().catch(() => {});
  check();
  setInterval(check, 30 * 60 * 1000);
}
ipcMain.on('update:install', () => { if (autoUpdater) autoUpdater.quitAndInstall(); });
app.on('window-all-closed', () => app.quit());

/* ---------- утилиты ---------- */

function readJson(file, fallback) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return fallback; }
}
function writeJson(file, data) {
  fs.writeFileSync(file, JSON.stringify(data, null, 2));
}
function sha256(s) {
  return crypto.createHash('sha256').update(s).digest('hex');
}

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https') ? https : http;
    lib.get(url, { headers: { 'User-Agent': 'RP-Launcher' } }, res => {
      if (res.statusCode >= 300 && res.headers.location) {
        return fetchJson(res.headers.location).then(resolve, reject);
      }
      let data = '';
      res.on('data', c => (data += c));
      res.on('end', () => {
        try { resolve(JSON.parse(data)); } catch (e) { reject(e); }
      });
    }).on('error', reject);
  });
}

function fetchText(url) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, { timeout: 5000 }, res => {
      let d = ''; res.on('data', c => d += c); res.on('end', () => resolve(d));
    });
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    req.on('error', reject);
  });
}

function downloadFile(url, dest, onProgress) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https') ? https : http;
    lib.get(url, { headers: { 'User-Agent': 'RP-Launcher' } }, res => {
      if (res.statusCode >= 300 && res.headers.location) {
        return downloadFile(res.headers.location, dest, onProgress).then(resolve, reject);
      }
      if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode));
      const total = parseInt(res.headers['content-length'] || '0', 10);
      let done = 0;
      const file = fs.createWriteStream(dest);
      res.on('data', c => { done += c.length; if (onProgress && total) onProgress(done / total); });
      res.pipe(file);
      file.on('finish', () => file.close(resolve));
      file.on('error', reject);
    }).on('error', reject);
  });
}

function send(channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}

/* ---------- окно ---------- */

ipcMain.on('win:minimize', () => win.minimize());
ipcMain.on('win:close', () => win.close());
ipcMain.on('open:url', (_e, url) => shell.openExternal(url));

ipcMain.handle('config:get', () => CONFIG);

/* ---------- настройки (RAM и т.п.) ---------- */

ipcMain.handle('settings:get', () => readJson(SETTINGS_FILE, { ramMb: CONFIG.defaultRamMb }));
ipcMain.handle('settings:set', (_e, s) => { writeJson(SETTINGS_FILE, s); return true; });

/* ---------- аккаунты (серверная регистрация через LauncherGuard) ---------- */

const GUARD_ERR = {
  nick_taken: 'Ник уже занят',
  wrong_password: 'Неверный пароль',
  not_found: 'Аккаунт не найден. Зарегистрируйся',
  bad_nick: 'Ник: 3–16 символов, латиница, цифры, _',
  bad_password: 'Пароль: от 4 до 64 символов',
  password_required: 'Обнови лаунчер',
  bad_secret: 'Ошибка конфигурации лаунчера',
  bad_nick_case: 'Неверный регистр ника'
};

let creds = null; // { username, password } на время сессии

function guardApi(endpoint, params) {
  const qs = Object.entries(params)
    .map(([k, v]) => k + '=' + encodeURIComponent(v)).join('&');
  const url = `http://${CONFIG.serverIp}:${CONFIG.guardPort}/${endpoint}?${qs}&secret=${encodeURIComponent(CONFIG.guardSecret)}`;
  return new Promise((resolve, reject) => {
    const req = http.get(url, { timeout: 8000 }, res => {
      let d = ''; res.on('data', c => d += c);
      res.on('end', () => { try { resolve(JSON.parse(d)); } catch { reject(new Error('bad_response')); } });
    });
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    req.on('error', reject);
  });
}

function saveSession(username, password) {
  writeJson(SESSION_FILE, { username, p: Buffer.from(password, 'utf8').toString('base64') });
}
function loadSession() {
  const s = readJson(SESSION_FILE, null);
  if (!s || !s.username || !s.p) return null;
  return { username: s.username, password: Buffer.from(s.p, 'base64').toString('utf8') };
}

ipcMain.handle('auth:register', async (_e, { username, password }) => {
  if (!/^[A-Za-z0-9_]{3,16}$/.test(username))
    return { ok: false, error: GUARD_ERR.bad_nick };
  if (password.length < 6)
    return { ok: false, error: 'Пароль минимум 6 символов' };
  try {
    const r = await guardApi('register', { nick: username, pass: password });
    if (!r.ok) return { ok: false, error: GUARD_ERR[r.error] || 'Ошибка сервера' };
  } catch (e) {
    return { ok: false, error: 'Сервер недоступен, попробуй позже' };
  }
  creds = { username, password };
  saveSession(username, password);
  return { ok: true, username };
});

ipcMain.handle('auth:login', async (_e, { username, password }) => {
  try {
    const r = await guardApi('login', { nick: username, pass: password });
    if (!r.ok) return { ok: false, error: GUARD_ERR[r.error] || 'Ошибка сервера' };
    username = r.nick || username;
  } catch (e) {
    return { ok: false, error: 'Сервер недоступен, попробуй позже' };
  }
  creds = { username, password };
  saveSession(username, password);
  return { ok: true, username };
});

ipcMain.handle('auth:session', async () => {
  const s = loadSession();
  if (!s) return null;
  creds = s;
  return { username: s.username };
});

ipcMain.handle('auth:logout', async () => {
  creds = null;
  try { fs.unlinkSync(SESSION_FILE); } catch {}
  return true;
});

/* ---------- моды ----------
   modsManifestUrl должен отдавать JSON:
   { "mods": [ { "name": "some-mod.jar", "url": "https://...", "sha1": "..." } ] }
   Лишние jar из папки mods удаляются — сборка всегда как на сервере. */

function sha1File(file) {
  return new Promise((resolve, reject) => {
    const h = crypto.createHash('sha1');
    fs.createReadStream(file).on('data', d => h.update(d))
      .on('end', () => resolve(h.digest('hex'))).on('error', reject);
  });
}

async function syncMods() {
  if (!CONFIG.modsManifestUrl) return;
  send('status', 'Проверка модов...');
  const manifest = await fetchJson(CONFIG.modsManifestUrl);
  const modsDir = path.join(GAME_DIR, 'mods');
  fs.mkdirSync(modsDir, { recursive: true });

  const wanted = new Set(manifest.mods.map(m => m.name));
  for (const f of fs.readdirSync(modsDir)) {
    if (f.endsWith('.jar') && !wanted.has(f)) fs.unlinkSync(path.join(modsDir, f));
  }

  let i = 0;
  for (const mod of manifest.mods) {
    i++;
    const dest = path.join(modsDir, mod.name);
    let needs = true;
    if (fs.existsSync(dest)) {
      needs = mod.sha1 ? (await sha1File(dest)) !== mod.sha1.toLowerCase() : false;
    }
    if (needs) {
      send('status', `Скачивание модов (${i}/${manifest.mods.length}): ${mod.name}`);
      await downloadFile(mod.url, dest, p =>
        send('progress', { value: (i - 1 + p) / manifest.mods.length }));
    }
    send('progress', { value: i / manifest.mods.length });
  }
}



/* ---------- предзагруженная карта (JourneyMap) ----------
   map.zip = запакованная папка journeymap (полностью отрисованный город).
   Качается один раз; чтобы обновить у всех — подними mapPackVersion в config.json. */
async function installMapPack() {
  if (!CONFIG.mapPackUrl) return;
  const marker = path.join(GAME_DIR, '.mappack');
  const have = readJson(marker, null);
  if (have && have.version === CONFIG.mapPackVersion) return;
  try {
    send('status', 'Скачивание карты города...');
    const zipPath = path.join(DATA_DIR, 'map.zip');
    await downloadFile(CONFIG.mapPackUrl, zipPath, p => send('progress', { value: p }));
    send('status', 'Распаковка карты...');
    await new Promise((resolve, reject) => {
      const { execFile } = require('child_process');
      execFile('powershell', ['-NoProfile', '-Command',
        `Expand-Archive -LiteralPath "${zipPath}" -DestinationPath "${GAME_DIR}" -Force`],
        err => err ? reject(err) : resolve());
    });
    fs.unlinkSync(zipPath);
    writeJson(marker, { version: CONFIG.mapPackVersion, at: Date.now() });
    log('карта города установлена, версия ' + CONFIG.mapPackVersion);
  } catch (e) {
    log('карта города не установилась: ' + e.message);
    /* не критично — JourneyMap отрисует сам */
  }
}

/* ---------- поиск Java 8 (нужна для Forge 1.16.5) ---------- */
function findJava8() {
  if (CONFIG.javaPath) return CONFIG.javaPath;
  const roots = [
    'C:\\Program Files\\Eclipse Adoptium',
    'C:\\Program Files\\Eclipse Foundation',
    'C:\\Program Files\\AdoptOpenJDK',
    'C:\\Program Files\\Java',
    'C:\\Program Files (x86)\\Java',
    'C:\\Program Files\\Zulu',
    'C:\\Program Files\\Amazon Corretto'
  ];
  for (const root of roots) {
    let dirs = [];
    try { dirs = fs.readdirSync(root); } catch { continue; }
    for (const dir of dirs) {
      if (/(jdk|jre)[-_]?8u?|1\.8\.0/i.test(dir)) {
        for (const exe of ['javaw.exe', 'java.exe']) {
          const p = path.join(root, dir, 'bin', exe);
          if (fs.existsSync(p)) return p;
        }
      }
    }
  }
  return undefined; // возьмётся java из PATH
}

/* ---------- запуск игры ---------- */

let launching = false;

ipcMain.handle('game:launch', async (_e, { username, ip, port }) => {
  if (launching) return { ok: false, error: 'Игра уже запускается' };
  launching = true;
  try {
    try { await syncMods(); }
    catch (e) { send('status', 'Список модов недоступен, запускаем без проверки...'); }
    await installMapPack();

    // авторизация на сервере: пропуск выдаётся только с верным паролем
    if (CONFIG.guardSecret) {
      send('status', 'Авторизация на сервере...');
      if (creds && creds.username === username) {
        try {
          const r = await guardApi('allow', { nick: username, pass: creds.password });
          if (!r.ok) {
            launching = false;
            return { ok: false, error: GUARD_ERR[r.error] || 'Сервер отклонил вход' };
          }
        } catch (e) { /* сервер недоступен — пробуем зайти, кикнет с понятным сообщением */ }
      } else {
        try {
          await fetchText(`http://${ip || CONFIG.serverIp}:${CONFIG.guardPort || 8777}` +
            `/allow?nick=${encodeURIComponent(username)}&secret=${encodeURIComponent(CONFIG.guardSecret)}`);
        } catch (e) {}
      }
    }

    const settings = readJson(SETTINGS_FILE, { ramMb: CONFIG.defaultRamMb });
    send('status', 'Подготовка игры...');

    const launcher = new Client();
    const opts = {
      // тип входа 'mojang' — иначе 1.16.5 считает аккаунт Microsoft,
      // не может его проверить и отключает мультиплеер
      authorization: Object.assign(Authenticator.getAuth(username), {
        meta: { type: 'mojang', demo: false }
      }),
      root: GAME_DIR,
      version: { number: CONFIG.minecraftVersion, type: 'release' },
      memory: { max: settings.ramMb + 'M', min: '1024M' },
      // фикс бага 1.16.x: при офлайн-входе игра получает пустой ответ от серверов
      // Mojang и блокирует мультиплеер; уводим проверку на несуществующий адрес
      customArgs: [
        '-Dminecraft.api.auth.host=https://nope.invalid',
        '-Dminecraft.api.account.host=https://nope.invalid',
        '-Dminecraft.api.session.host=https://nope.invalid',
        '-Dminecraft.api.services.host=https://nope.invalid'
      ],
      javaPath: findJava8(),
      quickPlay: {
        // до 1.20 автоподключение работает через старые аргументы (legacy)
        type: parseInt(CONFIG.minecraftVersion.split('.')[1], 10) >= 20 ? 'multiplayer' : 'legacy',
        identifier: (ip || CONFIG.serverIp) + ':' + (port || CONFIG.serverPort)
      }
    };

    if (CONFIG.loader === 'forge' && CONFIG.forgeInstallerUrl) {
      const forgeJar = path.join(DATA_DIR, 'forge-installer.jar');
      if (!fs.existsSync(forgeJar)) {
        send('status', 'Скачивание Forge...');
        await downloadFile(CONFIG.forgeInstallerUrl, forgeJar, p => send('progress', { value: p }));
      }
      opts.forge = forgeJar;
    }

    launcher.on('progress', e => {
      if (e.total) send('progress', { value: e.task / e.total });
      send('status', `Загрузка: ${e.type} (${e.task}/${e.total})`);
    });
    launcher.on('download-status', e => {
      if (e.total) send('progress', { value: e.current / e.total });
    });
    launcher.on('debug', d => log('[debug] ' + String(d).trim()));
    launcher.on('data', d => { send('game:started'); log('[game] ' + String(d).trim()); });
    launcher.on('close', code => { launching = false; log('[game] закрылась, код ' + code); send('game:closed', code); });

    send('status', 'Запуск Minecraft...');
    await launcher.launch(opts);
    return { ok: true };
  } catch (e) {
    launching = false;
    return { ok: false, error: e.message };
  }
});

/* ---------- онлайн сервера и новости ---------- */

function varInt(n) {
  const b = [];
  while (true) {
    if ((n & ~0x7F) === 0) { b.push(n); break; }
    b.push((n & 0x7F) | 0x80); n >>>= 7;
  }
  return Buffer.from(b);
}

// Прямой пинг сервера по протоколу Minecraft (Server List Ping)
function pingServer(host, port) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host, port, timeout: 5000 });
    let buf = Buffer.alloc(0);
    const fail = e => { socket.destroy(); reject(e); };
    socket.on('timeout', () => fail(new Error('timeout')));
    socket.on('error', fail);
    socket.on('connect', () => {
      const hostBuf = Buffer.from(host, 'utf8');
      const data = Buffer.concat([
        Buffer.from([0x00]), varInt(754), varInt(hostBuf.length), hostBuf,
        Buffer.from([(port >> 8) & 0xff, port & 0xff]), Buffer.from([0x01])
      ]);
      socket.write(Buffer.concat([varInt(data.length), data]));
      socket.write(Buffer.from([0x01, 0x00]));
    });
    socket.on('data', d => {
      buf = Buffer.concat([buf, d]);
      let offset = 0;
      const readVarInt = () => {
        let num = 0, cnt = 0;
        while (true) {
          if (offset >= buf.length) throw new Error('incomplete');
          const b = buf[offset++];
          num |= (b & 0x7F) << (7 * cnt++);
          if (!(b & 0x80)) break;
        }
        return num;
      };
      try {
        const len = readVarInt();
        if (buf.length - offset < len) return;
        readVarInt();
        const strLen = readVarInt();
        const json = JSON.parse(buf.slice(offset, offset + strLen).toString('utf8'));
        socket.destroy();
        const p = json.players || {};
        resolve({ online: true, players: p.online || 0, max: p.max || 0 });
      } catch (e) { /* ждём остаток пакета */ }
    });
  });
}

ipcMain.handle('server:status', async (_e, srv) => {
  const ip = (srv && srv.ip) || CONFIG.serverIp;
  const port = (srv && srv.port) || CONFIG.serverPort;
  try { return await pingServer(ip, port); }
  catch { return { online: false, players: 0, max: 0 }; }
});

function defaultNews() {
  return [
    { title: 'Грандиозное открытие HIVE RP!', date: 'Сегодня', image: 'img/news1.svg',
      text: 'Сервер AURORA официально открыт! Регистрируйся, заходи и начинай свою историю: работы, бизнесы, недвижимость, фракции и своя банда. Первые 100 игроков получат стартовый бонус.' },
    { title: 'Обновление сборки', date: 'Недавно', image: 'img/news2.svg',
      text: 'Лаунчер теперь сам скачивает игру и моды — просто нажми «Играть». Если что-то не работает, пиши в Discord, поможем.' },
    { title: 'Розыгрыш в Discord', date: '', image: 'img/news3.svg',
      text: 'Вступай в наш Discord — каждую неделю разыгрываем привилегии и игровую валюту среди участников.' }
  ];
}

ipcMain.handle('news:get', async () => {
  if (CONFIG.newsUrl) {
    try { return await fetchJson(CONFIG.newsUrl); } catch { /* локальные ниже */ }
  }
  let list = readJson(NEWS_FILE, null);
  if (!Array.isArray(list)) { list = defaultNews(); writeJson(NEWS_FILE, list); }
  return list;
});

ipcMain.handle('news:add', (_e, item) => {
  const list = readJson(NEWS_FILE, defaultNews());
  list.unshift({
    title: String(item.title || '').slice(0, 120),
    text: String(item.text || '').slice(0, 4000),
    image: String(item.image || ''),
    date: new Date().toLocaleDateString('ru-RU')
  });
  writeJson(NEWS_FILE, list);
  return list;
});

ipcMain.handle('news:del', (_e, index) => {
  const list = readJson(NEWS_FILE, defaultNews());
  if (index >= 0 && index < list.length) list.splice(index, 1);
  writeJson(NEWS_FILE, list);
  return list;
});
