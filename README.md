# MeshChat

单页笔记本客户端。一台设备跑服务器，其余设备用 APK / EXE 填 Tailscale 网址和端口后自动连接。

## 服务器

在其中一台已加入同一 Tailscale 的机器上：

```bash
cd notebook
NOTEPAD_PASSWORD='可选密码' node server.js --host 0.0.0.0 --port 8765
```

浏览器打开 `http://<MagicDNS>:8765` 即可写。正文在 `data/note.md`。

## Android APK

首次打开内嵌笔记页，填 MagicDNS（或 `100.x`）和端口 `8765`。之后自动连。点「服务器」可改地址。

CI 推送到 `master` 后会把 `apk/MeshChat.apk` 写回仓库。

本机构建：

```bash
gradle assembleDebug --no-daemon
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## Windows EXE

```bash
cd desktop
npm install
npm run dist
```

产物：`desktop/dist/MeshChat*.exe`。CI 同样会写回 `exe/MeshChat.exe`。

首次打开同样只填网址和端口。
