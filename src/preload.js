const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('launcher', {
  minimize: () => ipcRenderer.send('win:minimize'),
  close: () => ipcRenderer.send('win:close'),
  openUrl: url => ipcRenderer.send('open:url', url),

  getConfig: () => ipcRenderer.invoke('config:get'),
  getSettings: () => ipcRenderer.invoke('settings:get'),
  setSettings: s => ipcRenderer.invoke('settings:set', s),

  register: (username, password) => ipcRenderer.invoke('auth:register', { username, password }),
  getSession: () => ipcRenderer.invoke('auth:session'),
  logout: () => ipcRenderer.invoke('auth:logout'),
  login: (username, password) => ipcRenderer.invoke('auth:login', { username, password }),

  launch: (username, ip, port) => ipcRenderer.invoke('game:launch', { username, ip, port }),
  serverStatus: (ip, port) => ipcRenderer.invoke('server:status', { ip, port }),

  getNews: () => ipcRenderer.invoke('news:get'),
  addNews: item => ipcRenderer.invoke('news:add', item),
  delNews: index => ipcRenderer.invoke('news:del', index),

  onStatus: cb => ipcRenderer.on('status', (_e, s) => cb(s)),
  onProgress: cb => ipcRenderer.on('progress', (_e, p) => cb(p)),
  onGameStarted: cb => ipcRenderer.on('game:started', cb),
  onGameClosed: cb => ipcRenderer.on('game:closed', (_e, c) => cb(c)),

  onUpdateAvailable: cb => ipcRenderer.on('update:available', cb),
  onUpdateProgress: cb => ipcRenderer.on('update:progress', (_e, p) => cb(p)),
  onUpdateReady: cb => ipcRenderer.on('update:ready', cb),
  installUpdate: () => ipcRenderer.send('update:install')
});
