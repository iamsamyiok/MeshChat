const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const os = require('os');
const http = require('http');
const { spawn } = require('child_process');

const PORT = Number(process.env.NOTEPAD_PORT || 8765);
let serverProc = null;

function localAddresses() {
  const rows = [];
  const ifs = os.networkInterfaces();
  for (const [name, addrs] of Object.entries(ifs)) {
    for (const a of addrs || []) {
      if (a.family === 'IPv4' && !a.internal) rows.push({ name: name, addr: a.address });
    }
  }
  rows.sort((x, y) => (x.addr.startsWith('100.') ? 0 : 1) - (y.addr.startsWith('100.') ? 0 : 1));
  return rows;
}

function health() {
  return new Promise((resolve) => {
    const req = http.get({ host: '127.0.0.1', port: PORT, path: '/api/health', timeout: 1500 }, (res) => {
      res.resume();
      resolve(res.statusCode === 200);
    });
    req.on('error', () => resolve(false));
    req.on('timeout', () => { req.destroy(); resolve(false); });
  });
}

async function waitForHealth(ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (await health()) return true;
    await new Promise((r) => setTimeout(r, 400));
  }
  return false;
}

function serverEntry() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'app.asar.unpacked', 'server', 'server.js');
  }
  return path.join(__dirname, 'server', 'server.js');
}

async function startServer() {
  if (serverProc || (await health())) {
    return { running: true, port: PORT, addresses: localAddresses() };
  }
  const dataDir = path.join(app.getPath('userData'), 'notepad-data');
  serverProc = spawn(process.execPath, [serverEntry(), '--host', '0.0.0.0', '--port', String(PORT)], {
    env: Object.assign({}, process.env, {
      ELECTRON_RUN_AS_NODE: '1',
      NOTEPAD_DATA: dataDir
    }),
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true
  });
  serverProc.stdout.on('data', () => {});
  serverProc.stderr.on('data', () => {});
  serverProc.on('exit', () => { serverProc = null; });
  const ok = await waitForHealth(15000);
  if (!ok) {
    return { running: false, port: PORT, error: '启动失败：端口 ' + PORT + ' 可能被占用' };
  }
  return { running: true, port: PORT, addresses: localAddresses() };
}

ipcMain.handle('meshchat:start-server', () => startServer());
ipcMain.handle('meshchat:server-status', async () => ({
  running: serverProc != null || (await health()),
  port: PORT
}));

function createWindow() {
  const win = new BrowserWindow({
    width: 920,
    height: 740,
    backgroundColor: '#f3efe6',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      webSecurity: false
    }
  });
  win.loadFile(path.join(__dirname, 'index.html'));
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => { app.quit(); });
app.on('before-quit', () => { if (serverProc) serverProc.kill(); });
