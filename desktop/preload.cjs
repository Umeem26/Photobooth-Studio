'use strict';
// Mengekspos alamat sidecar dan token ke UI sebagai window.booth (contextIsolation aktif).

const { contextBridge, ipcRenderer } = require('electron');

const env = ipcRenderer.sendSync('vandebooth:env');
if (env && env.apiBase) {
  contextBridge.exposeInMainWorld('booth', Object.freeze({ apiBase: env.apiBase, token: env.token }));
}
