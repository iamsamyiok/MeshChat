#!/usr/bin/env node
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { URL } = require('url');

const ROOT = __dirname;
const PUBLIC_DIR = path.join(ROOT, 'public');
const DATA_DIR = process.env.NOTEPAD_DATA || path.join(ROOT, 'data');
const NOTE_PATH = path.join(DATA_DIR, 'note.md');

const args = process.argv.slice(2);
function arg(name, fallback) {
  const i = args.indexOf('--' + name);
  if (i >= 0 && args[i + 1]) return args[i + 1];
  return fallback;
}

const HOST = arg('host', process.env.NOTEPAD_HOST || '0.0.0.0');
const PORT = Number(arg('port', process.env.NOTEPAD_PORT || '8765'));
const PASSWORD = String(process.env.NOTEPAD_PASSWORD || '').trim();
const TOKEN_SECRET = process.env.NOTEPAD_TOKEN_SECRET || (PASSWORD || 'local-notepad');

fs.mkdirSync(DATA_DIR, { recursive: true });

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8'
};

function corsHeaders(req) {
  const origin = req.headers.origin;
  return {
    'Access-Control-Allow-Origin': !origin || origin === 'null' ? '*' : origin,
    'Access-Control-Allow-Methods': 'GET,PUT,POST,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Max-Age': '86400'
  };
}

function json(res, req, code, data) {
  res.writeHead(code, Object.assign({
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store'
  }, corsHeaders(req)));
  res.end(JSON.stringify(data));
}

function readBody(req, limit) {
  const max = limit || 2 * 1024 * 1024;
  return new Promise((resolve, reject) => {
    let buf = '';
    req.on('data', (c) => {
      buf += c;
      if (buf.length > max) {
        req.destroy();
        reject(new Error('payload too large'));
      }
    });
    req.on('end', () => {
      try { resolve(JSON.parse(buf || '{}')); }
      catch { reject(new Error('invalid json')); }
    });
    req.on('error', reject);
  });
}

function atomicWrite(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const tmp = filePath + '.' + process.pid + '.' + Date.now() + '.tmp';
  fs.writeFileSync(tmp, content, 'utf8');
  fs.renameSync(tmp, filePath);
}

function loadNote() {
  try {
    return fs.readFileSync(NOTE_PATH, 'utf8');
  } catch {
    return '';
  }
}

function tokenFor() {
  return crypto.createHmac('sha256', TOKEN_SECRET).update('notepad-ok').digest('hex').slice(0, 32);
}

function parseCookies(req) {
  const out = {};
  String(req.headers.cookie || '').split(';').forEach((part) => {
    const i = part.indexOf('=');
    if (i < 0) return;
    out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  });
  return out;
}

function isAuthed(req) {
  if (!PASSWORD) return true;
  if (parseCookies(req).np_auth === tokenFor()) return true;
  if (String(req.headers.authorization || '') === 'Bearer ' + tokenFor()) return true;
  return false;
}

function serveStatic(res, filePath) {
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404);
      res.end('Not Found');
      return;
    }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(filePath)] || 'application/octet-stream',
      'Cache-Control': 'no-store'
    });
    res.end(data);
  });
}

if (!fs.existsSync(NOTE_PATH)) {
  atomicWrite(NOTE_PATH, '在这里写。点「分隔」或按 Ctrl+/ 在光标处插入一条分隔线。\n');
}

const server = http.createServer(async (req, res) => {
  try {
    const u = new URL(req.url, 'http://' + (req.headers.host || 'localhost'));
    const p = u.pathname;

    if (req.method === 'OPTIONS') {
      res.writeHead(204, corsHeaders(req));
      res.end();
      return;
    }

    if (p === '/api/health' && req.method === 'GET') {
      json(res, req, 200, { ok: true, auth: !!PASSWORD });
      return;
    }

    if (p === '/api/login' && req.method === 'POST') {
      const body = await readBody(req, 4096);
      if (!PASSWORD) { json(res, req, 200, { ok: true, auth: false, token: '' }); return; }
      if (String(body.password || '') !== PASSWORD) {
        json(res, req, 401, { ok: false, error: '密码错误' });
        return;
      }
      const token = tokenFor();
      res.setHeader('Set-Cookie', 'np_auth=' + token + '; HttpOnly; SameSite=Lax; Path=/; Max-Age=' + (30 * 86400));
      json(res, req, 200, { ok: true, token: token });
      return;
    }

    if (p === '/login' || p === '/login.html') {
      serveStatic(res, path.join(PUBLIC_DIR, 'login.html'));
      return;
    }

    if (PASSWORD && !isAuthed(req) && p.startsWith('/api/')) {
      json(res, req, 401, { ok: false, error: '未登录', authRequired: true });
      return;
    }

    if (PASSWORD && !isAuthed(req) && (p === '/' || p === '/index.html')) {
      res.writeHead(302, { Location: '/login' });
      res.end();
      return;
    }

    if (p === '/api/note' && req.method === 'GET') {
      const st = fs.existsSync(NOTE_PATH) ? fs.statSync(NOTE_PATH) : null;
      json(res, req, 200, { ok: true, body: loadNote(), updatedAt: st ? st.mtime.toISOString() : null });
      return;
    }

    if (p === '/api/note' && req.method === 'PUT') {
      const body = await readBody(req);
      const text = typeof body.body === 'string' ? body.body : '';
      atomicWrite(NOTE_PATH, text);
      json(res, req, 200, { ok: true, updatedAt: new Date().toISOString() });
      return;
    }

    if (req.method === 'GET' && (p === '/' || p === '/index.html')) {
      serveStatic(res, path.join(PUBLIC_DIR, 'index.html'));
      return;
    }

    res.writeHead(404);
    res.end('Not Found');
  } catch (err) {
    json(res, req, 500, { ok: false, error: String(err && err.message || err) });
  }
});

server.listen(PORT, HOST, () => {
  console.log('Notepad listening on http://' + HOST + ':' + PORT);
  console.log('Note file: ' + NOTE_PATH);
  if (PASSWORD) console.log('Password auth enabled');
  else console.log('No password set. Set NOTEPAD_PASSWORD if the tailnet is shared.');
});
