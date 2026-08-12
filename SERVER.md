# Palmistry 服务器配置文档

> 最后更新：2026-08-11
> 服务器：`snappy-cat-1`（`104.225.236.196`）
> 域名：`palmistrygroup.com`（Cloudflare 托管）

---

## 1. 服务器概况

| 项目 | 值 |
|------|-----|
| 主机名 | snappy-cat-1 |
| 公网 IP | 104.225.236.196 |
| 操作系统 | Ubuntu 22.04.5 LTS |
| Nginx | 1.18.0 |
| Node 版本 | v24.19.0 |
| 进程管理 | PM2 |
| 域名 | palmistrygroup.com |
| DNS / CDN | Cloudflare（DNS 记录 + CDN 代理） |
| SSL 模式 | 完全（Full） |

> 服务器于 2026-08 重装系统，所有服务从零重新部署。

---

## 2. 目录结构

```
/var/www/palmistry/
├── server/              # 后端运行目录（PM2 跑这里）
│   ├── index.js         # 主程序（Express）
│   ├── paymentRoutes.js # 支付路由
│   ├── paymentStore.js  # 订单存储
│   ├── paymentListenerStore.js  # Android 心跳状态存储
│   ├── package.json
│   ├── package-lock.json
│   ├── .env             # 环境变量（含 API Key，不在 Git 中）
│   ├── node_modules/    # 依赖（不在 Git 中）
│   └── data/            # 订单数据持久化
├── server-src/          # Git 仓库（稀疏检出，只拉 server/ 目录）
│   ├── .git/
│   ├── server/          # 从 GitHub 拉取的源码
│   └── ...
├── frontend/            # 前端静态文件（HBuilderX 打包产物）
│   ├── index.html
│   ├── assets/
│   ├── static/
│   ├── robots.txt
│   └── sitemap.xml
└── backup/              # 部署脚本自动生成的备份
    └── frontend_backup_YYYYMMDD_HHMM/
```

### server-src 与 server 的关系

| 目录 | 用途 | Git 追踪 |
|------|------|----------|
| `server-src/` | Git 仓库，只用来 `git pull` 拉代码 | 是 |
| `server/` | 实际运行目录，PM2 跑这里 | 否（`.env` 和 `node_modules` 不进 Git） |

更新流程：`server-src/ git pull` → `cp 文件到 server/` → `pm2 重启`

---

## 3. 服务清单

| 服务 | 端口 | 进程管理 | 启动命令 |
|------|------|----------|----------|
| 后端 Node API | 3000 | PM2（`palmistry`） | `pm2 start index.js --name palmistry --cwd /var/www/palmistry/server` |
| Nginx（前端 + 反代） | 80 → 443 | systemd | `systemctl reload nginx` |

---

## 4. Nginx 配置

**主配置文件**：`/etc/nginx/sites-available/palmistry`
**已启用软链**：`/etc/nginx/sites-enabled/palmistry`
**默认站点**：已删除（`/etc/nginx/sites-enabled/default`）

核心配置要点：
- `80` 端口自动 `301` 跳转 `443`（HTTPS）
- `443` SSL 证书：Cloudflare 源证书（15 年免续期）
- 网站根目录：`root /var/www/palmistry/frontend`
- SPA 单页应用支持：`try_files $uri $uri/ /index.html`（刷新不 404）
- `/health` 和 `/api/` 反向代理到 `127.0.0.1:3000`
- `/api/` 超时 `120s`（手相/面相多模态分析 + 长输出预留）

**SSL 证书位置**：
- 证书：`/etc/nginx/ssl/palmistry.crt`
- 私钥：`/etc/nginx/ssl/palmistry.key`

---

## 5. 后端环境变量

**文件**：`/var/www/palmistry/server/.env`（不在 Git 中，手动维护）

| 变量 | 说明 | 当前值 |
|------|------|--------|
| `DASHSCOPE_API_KEY` | 通义千问 API Key | `sk-****`（已配置） |
| `PORT` | 后端端口 | `3000` |
| `ALLOWED_ORIGIN` | 允许跨域的前端域名 | `http://localhost:5173,https://palmistrygroup.com` |
| `PAYMENT_NOTIFY_SECRET` | Android 上报收款密钥 | `5b91****`（已配置） |
| `PAYMENT_TEST_MODE` | 测试模式（跳过验单） | `false` |
| `PAYMENT_LISTENER_HEARTBEAT_TTL_MS` | 心跳超此毫秒数视为离线 | `60000`（1 分钟） |
| `PAYMENT_LISTENER_LIVE_HEARTBEAT_MS` | 心跳在此毫秒数内视为实时在线 | `15000`（15 秒） |
| `ALIPAY_QR_SINGLE_URL` | 单模块收款码 | `/static/alipay-qr.png` |
| `ALIPAY_QR_BUNDLE_URL` | 打包收款码 | `/static/alipay-qr.png` |

> ⚠️ `.env` 不在 Git 中。新增变量时，参考 `server-src/server/.env.example`，手动添加到 `server/.env`。

---

## 6. 接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/api/qwen` | 通义千问代理（透传到 DashScope），限流 8 次/分钟/IP |
| POST | `/api/payment/orders` | 创建支付订单（参数：`displayPrice`） |
| GET | `/api/payment/orders/:orderId` | 查询订单状态（`pending` / `paid` / `expired`） |
| POST | `/api/payment/notify` | Android 上报收款（请求头 `x-payment-secret`） |
| POST | `/api/payment/listener/heartbeat` | Android 定时心跳上报（请求头 `x-payment-secret`） |
| GET | `/api/payment/listener/status` | H5 查询 Android 监听是否在线（无需密钥） |

---

## 7. PM2 后端管理

```bash
# 查看状态
pm2 list
pm2 logs palmistry --lines 20

# 重启（改了 .env 必须用 delete+start，不要用 restart）
pm2 delete palmistry
cd /var/www/palmistry/server
pm2 start index.js --name palmistry --cwd /var/www/palmistry/server
pm2 save

# 停止 / 删除
pm2 stop palmistry
pm2 delete palmistry
```

> ⚠️ **重要**：`pm2 restart` 不会重新加载 `.env`（PM2 缓存旧环境变量）。改完 `.env` 后必须 `pm2 delete` + `pm2 start`。

---

## 8. Nginx 管理

```bash
# 测试配置
nginx -t

# 重载配置（不中断服务）
systemctl reload nginx

# 查看状态
systemctl status nginx

# 查看错误日志
tail -f /var/log/nginx/error.log
```

---

## 9. 健康检查

```bash
# 后端直连
curl http://127.0.0.1:3000/health

# 新接口测试（心跳状态，无需密钥）
curl http://127.0.0.1:3000/api/payment/listener/status

# 走 Nginx（本机）
curl -k https://127.0.0.1/health

# 外网（从本地 Mac）
curl https://palmistrygroup.com/health
curl -i https://palmistrygroup.com/
```

---

## 10. Cloudflare 配置要点

| 项目 | 配置 |
|------|------|
| DNS A 记录 | `palmistrygroup.com` → `104.225.236.196`（橙云代理开启） |
| SSL/TLS 模式 | **完全（Full）** |
| 源服务器证书 | CF 后台生成，安装到 `/etc/nginx/ssl/`，15 年免续期 |

> 如重装服务器后证书丢失：CF 后台 → SSL/TLS → 源服务器 → 创建证书 → 下载 cert+key 到 `/etc/nginx/ssl/palmistry.crt` 和 `palmistry.key`。

---

## 11. 故障排查

### 常见错误对照表

| 现象 | 原因 | 解决 |
|------|------|------|
| 外网 521 | Nginx 443 没监听或证书未装 | 检查 `systemctl status nginx` + 证书文件 |
| 403 `Origin not allowed` | `ALLOWED_ORIGIN` 不含前端域名 | 改 `.env` 加上 `https://palmistrygroup.com`，`pm2 delete` + `pm2 start` |
| 401 `Invalid API-key provided` | DashScope Key 失效 | 去 [DashScope 控制台](https://dashscope.console.aliyun.com/apiKey) 换新 key |
| 500 `DASHSCOPE_API_KEY is not set` | `.env` 没配 key 或进程没读到 | 确认 `.env`，用 `pm2 delete` + `pm2 start` 重启 |
| `EADDRINUSE :::3000` | 端口被旧进程占用 | `fuser -k 3000/tcp` + `pkill -f "node.*index.js"` |
| `ERR_MODULE_NOT_FOUND` | `node_modules` 不完整 | `rm -rf node_modules package-lock.json && npm install` |
| 前端显示 `palmistry backend ok` | Nginx `location /` 没配 `try_files` | 确认 `root /var/www/palmistry/frontend` + `try_files $uri $uri/ /index.html` |

### 端口被占用排查

```bash
# 1. 查看谁占用了 3000
ss -tlnp | grep :3000

# 2. 杀掉非 PM2 的 node 进程
fuser -k 3000/tcp
pkill -f "node.*index.js"

# 3. PM2 重新创建进程
pm2 delete palmistry
cd /var/www/palmistry/server
pm2 start index.js --name palmistry --cwd /var/www/palmistry/server
pm2 save

# 4. 验证
curl http://127.0.0.1:3000/health
```

### npm install 报 EALLOWREMOTE

```bash
# 方案1：加参数
npm install --allow-remote

# 方案2：删除 lock 文件重新安装
rm -rf node_modules package-lock.json
npm install
```

---

## 12. 多项目共存规范

在同一台服务器上部署其他项目时，遵循以下约定避免冲突：

1. **目录隔离**：每个项目一个独立目录 `/var/www/<项目名>/`
2. **端口隔离**：palmistry → 3000，新项目 → 3001、3002 …
3. **PM2 进程名隔离**：`pm2 start index.js --name <项目名>`
4. **Nginx server_name 隔离**：每个项目一个独立域名
5. **更新脚本隔离**：每个项目一个独立的 `update-<项目名>.sh`
