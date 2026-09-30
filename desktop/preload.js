const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('meshchat', {
  startServer: () => ipcRenderer.invoke('meshchat:start-server'),
  serverStatus: () => ipcRenderer.invoke('meshchat:server-status')
});
