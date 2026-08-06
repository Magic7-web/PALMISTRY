# Palmistry 服务器部署文档

> 最后更新：2026-08-05
> 服务器：`snappy-cat-1`（`104.225.236.196`）
> 域名：`palmistrygroup.com`（Cloudflare 托管）

## 1. 服务器概况

| 项目 | 值 |
|------|-----|
| 主机名 | snappy-cat-1 |
| 公网 IP | 104.225.236.196 |
| 操作系统 | Ubuntu（Nginx 1.18.0） |
| Node 版本 | v24.19.0 |
| 域名 | palmistrygroup.com |
| DNS / CDN | Cloudflare（DNS 记录 + CDN 代理） |
| SSL 模式 | 完全（Full） |

> 服务器于 2026-08 重装系统，所有服务从零重新部署。

## 2. 目录结构

```
/var/www/palmistry/
├── server/              # 后端 Node 服务（API 代理 + 支付闭环）
│   ├── index.js         # 主程序（Express）
│   ├── paymentRoutes.js # 支付路由
│   ├── paymentStore.js  # 订单存储
│   ├── .env             # 环境变量（含 API Key，勿提交 Git）
│   └── node_modules/
├── frontend/            # 前端静态文件（HBuilderX 打包产物）
│   ├── index.html
│   ├── assets/
│   └── static/
└── backup/              # 部署脚本自动生成的备份
    └── frontend_backup_YYYYMMDD_HHMM/
```

## 3. 服务清单

| 服务 | 端口 | 进程管理 | 启动命令 |
|------|------|----------|----------|
| 后端 Node API | 3000 | PM2（`palmistry`） | `pm2 start index.js --name palmistry --cwd /var/www/palmistry/server` |
| Nginx（前端 + 反代） | 80 → 443 | systemd | `systemctl reload nginx` |

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
- `/api/` 超时 `120s`（手相多模态分析 + 长输出预留）

**SSL 证书位置**：
- 证书：`/etc/nginx/ssl/palmistry.crt`
- 私钥：`/etc/nginx/ssl/palmistry.key`

## 5. 后端环境变量

**文件**：`/var/www/palmistry/server/.env`

| 变量 | 说明 | 当前值 |
|------|------|--------|
| `DASHSCOPE_API_KEY` | 通义千问 API Key | `sk-****`（已配置，须有效） |
| `PORT` | 后端端口 | `3000` |
| `ALLOWED_ORIGIN` | 允许跨域的前端域名 | `http://localhost:5173,https://palmistrygroup.com` |
| `PAYMENT_NOTIFY_SECRET` | Android 上报收款密钥 | `dev-payment-secret` |
| `PAYMENT_TEST_MODE` | 测试模式（跳过验单） | `false` |
| `ALIPAY_QR_SINGLE_URL` | 单模块收款码 | `/static/alipay-qr.png` |
| `ALIPAY_QR_BUNDLE_URL` | 打包收款码 | `/static/alipay-qr.png` |

> 注意：`index.js` 中 `dotenv.config({ override: true })`，确保 `.env` 覆盖任何已存在的环境变量。

## 6. 接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查，返回 `{ok, apiKeyConfigured, paymentTestMode, paymentNotifyConfigured}` |
| POST | `/api/qwen` | 通义千问代理（透传到 DashScope），限流 8 次/分钟/IP |
| POST | `/api/payment/orders` | 创建支付订单（参数：`displayPrice`） |
| GET | `/api/payment/orders/:orderId` | 查询订单状态（`pending` / `paid` / `expired`） |
| POST | `/api/payment/notify` | Android 上报收款（请求头 `x-payment-secret`） |

## 7. 常用运维命令

### 7.1 PM2 后端管理

```bash
# 查看状态
pm2 list
pm2 logs palmistry --lines 20

# 重启（注意：改了 .env 必须用 delete+start，不要用 restart）
pm2 delete palmistry
cd /var/www/palmistry/server
pm2 start index.js --name palmistry --cwd /var/www/palmistry/server
pm2 save

# 停止 / 删除
pm2 stop palmistry
pm2 delete palmistry
```

> ⚠️ **重要**：`pm2 restart` 不会重新加载 `.env`（PM2 缓存旧环境变量）。改完 `.env` 后必须 `pm2 delete` + `pm2 start`，或用 `pm2 restart palmistry --update-env`。

### 7.2 Nginx 管理

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

### 7.3 健康检查

```bash
# 后端直连
curl http://127.0.0.1:3000/health
# 应返回：{"ok":true,"apiKeyConfigured":true,...}

# 后端直连带 Origin（测 CORS）
curl -i -X POST http://127.0.0.1:3000/api/qwen \
  -H "Content-Type: application/json" \
  -H "Origin: https://palmistrygroup.com" \
  -d '{"model":"qwen3.6-flash-2026-04-16","input":{"messages":[{"role":"user","content":"hello"}]}}'

# 走 Nginx（本机）
curl -k https://127.0.0.1/health

# 外网（从本地 Mac）
curl https://palmistrygroup.com/health
curl -i https://palmistrygroup.com/
```

## 8. 前端更新流程

### 8.1 打包

在 HBuilderX 中打开 `frontend/` 项目 → 发行 → 网站-PC Web/H5，产物输出到：

```
frontend/unpackage/dist/build/web/
├── index.html
├── assets/
└── static/
```

### 8.2 一键部署脚本

**本地脚本**：`/Users/wangliqun/update-palmistry.sh`
**仓库副本**：项目根目录 `update-palmistry.sh`

```bash
# 在本地 Mac 项目根目录执行
./update-palmistry.sh
```

脚本执行流程：
1. 备份服务器当前 `/var/www/palmistry/frontend` 到 `/var/www/palmistry/backup/`
2. 清空服务器 `frontend` 目录（含隐藏文件）
3. 上传本地 `frontend/unpackage/dist/build/web/*` 到服务器
4. 重载 Nginx

> 如服务器路径或 IP 变更，编辑脚本里的 `root@104.225.236.196` 和目标路径即可。

### 8.3 手动更新（不用脚本）

```bash
# 本地 Mac
scp -r frontend/unpackage/dist/build/web/* root@104.225.236.196:/var/www/palmistry/frontend/
```

## 9. 后端更新流程

```bash
# 本地 Mac 上传代码
scp -r server/* root@104.225.236.196:/var/www/palmistry/server/

# 服务器上重启（改了 .env 用 delete+start）
ssh root@104.225.236.196
cd /var/www/palmistry/server
npm install          # 如有新依赖
pm2 delete palmistry
pm2 start index.js --name palmistry --cwd /var/www/palmistry/server
pm2 save
curl http://127.0.0.1:3000/health
```

## 10. 故障排查

### 10.1 常见错误对照表

| 现象 | 原因 | 解决 |
|------|------|------|
| 外网 521 | Nginx 443 没监听或证书未装 | 检查 `systemctl status nginx` + 证书文件 |
| 403 `Origin not allowed` | `ALLOWED_ORIGIN` 不含前端域名 | 改 `.env` 加上 `https://palmistrygroup.com`，`pm2 delete` + `pm2 start` |
| 401 `Invalid API-key provided` | DashScope Key 失效 | 去 [DashScope 控制台](https://dashscope.console.aliyun.com/apiKey) 换新 key |
| 500 `DASHSCOPE_API_KEY is not set` | `.env` 没配 key 或进程没读到 | 确认 `.env`，用 `pm2 delete` + `pm2 start` 重启 |
| `EADDRINUSE :::3000` | 端口被旧进程占用 | `fuser -k 3000/tcp` + `pkill -f "node.*index.js"` |
| 前端显示 `palmistry backend ok` | Nginx `location /` 没配 `try_files` | 确认 `root /var/www/palmistry/frontend` + `try_files $uri $uri/ /index.html` |

### 10.2 "改了 .env 不生效" 排查清单

```bash
# 1. 确认 .env 内容
cat /var/www/palmistry/server/.env | grep ALLOWED_ORIGIN

# 2. 确认没有裸 node 进程占端口（PM2 之外的）
ss -tlnp | grep :3000
ps aux | grep -E "node.*index.js" | grep -v grep

# 3. 如有非 PM2 的 node 进程，杀掉
fuser -k 3000/tcp
pkill -f "node.*index.js"

# 4. PM2 重新创建进程（不要用 restart）
pm2 delete palmistry
cd /var/www/palmistry/server
pm2 start index.js --name palmistry --cwd /var/www/palmistry/server
pm2 save

# 5. 验证
curl http://127.0.0.1:3000/health
```

### 10.3 "521" 排查

```bash
# Nginx 在跑吗
systemctl status nginx

# 443 监听了吗
ss -tlnp | grep :443

# 本机走 443 能到后端吗
curl -k https://127.0.0.1/health

# 证书文件在吗
ls -la /etc/nginx/ssl/palmistry.*

# CF DNS 的 A 记录指向这台服务器 IP 吗
dig palmistrygroup.com +short
```

## 11. Cloudflare 配置要点

| 项目 | 配置 |
|------|------|
| DNS A 记录 | `palmistrygroup.com` → `104.225.236.196`（橙云代理开启） |
| SSL/TLS 模式 | **完全（Full）** |
| 源服务器证书 | CF 后台生成，安装到 `/etc/nginx/ssl/`，15 年免续期 |

> 如重装服务器后证书丢失：CF 后台 → SSL/TLS → 源服务器 → 创建证书 → 下载 cert+key 到 `/etc/nginx/ssl/palmistry.crt` 和 `palmistry.key`。

## 12. 多项目共存规范（避免冲突）

在同一台服务器上部署其他项目时，遵循以下约定避免冲突：

1. **目录隔离**：每个项目一个独立目录
   ```
   /var/www/<项目名>/
   ├── server/        # 后端（如有）
   ├── frontend/      # 前端静态文件
   └── backup/        # 备份
   ```

2. **端口隔离**：每个后端分配独立端口
   - palmistry → 3000
   - 新项目 → 3001、3002 …（避免复用）

3. **PM2 进程名隔离**：`pm2 start index.js --name <项目名>`

4. **Nginx server_name 隔离**：每个项目一个独立域名 / 子域名，各自一个 `/etc/nginx/sites-available/<项目名>` 配置文件

5. **更新脚本隔离**：每个项目一个独立的 `update-<项目名>.sh`，路径和端口互不重叠
