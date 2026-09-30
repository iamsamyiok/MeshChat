# 单页笔记本

一块纸、即时保存、光标处插入分隔线。同一 Tailscale 网址给各台设备用。

## 服务器（一台设备）

```bash
cd notebook
NOTEPAD_PASSWORD='可选密码' node server.js --host 0.0.0.0 --port 8765
```

打开 `http://<MagicDNS>:8765`。点「分隔」或 `Ctrl+/` 在光标处切开。正文存在 `data/note.md`，分隔符存成单独一行的 `---`。

建议 `--host 0.0.0.0`，否则 Tailscale 网卡进不来。共享 tailnet 时设置 `NOTEPAD_PASSWORD`。

```bash
tailscale serve --bg 8765
```

可得 `https://<MagicDNS>/`。

## 客户端自动连接

网页、APK、EXE 只填一次 Tailscale 网址和端口（默认 8765），之后自动 `GET/PUT /api/note`。点顶栏「服务器」可改地址。

同源打开（浏览器直接访问服务器）无需再填。APK/EXE 内嵌该页，用记住的主机/端口跨域请求；可选密码会换 Bearer token。
