# Palmistry 项目配置与更新文档

> 最后更新：2026-08-11
> 仓库：`git@github.com:Magic7-web/PALMISTRY.git`
> 部署分支：`main`
> 线上地址：`https://palmistrygroup.com`

---

## 1. 项目概述

Palmistry 是一个手相/面相分析的 H5 应用，包含以下模块：

| 模块 | 目录 | 技术栈 | 说明 |
|------|------|--------|------|
| 前端 | `frontend/` | uni-app (HBuilderX) | H5 打包产物部署到 Nginx |
| 后端 | `server/` | Node.js + Express | API 代理 + 支付闭环 |
| Android 监听 | `android-payment-listener/` | Kotlin | 监听支付宝到账通知，上报后端 |

---

## 2. 目录结构

```
palmistry/
├── .gitignore              # 屏蔽 .env / node_modules / 打包产物
├── SERVER.md               # 服务器配置文档
├── PALMISTRY_PROJECT.md    # 本文档
├── update-palmistry.sh     # 前端一键部署脚本
├── frontend/               # 前端源码（uni-app）
│   ├── pages/              # 页面（camera、face、match 等）
│   ├── utils/              # 工具函数（paymentApi、locale 等）
│   ├── static/            # 静态资源
│   └── unpackage/          # 打包产物（被 .gitignore 屏蔽）
├── server/                 # 后端源码
│   ├── index.js            # 主程序
│   ├── paymentRoutes.js    # 支付路由
│   ├── paymentStore.js     # 订单存储
│   ├── paymentListenerStore.js  # Android 心跳状态
│   ├── package.json
│   ├── .env.example        # 环境变量模板
│   └── data/               # 订单数据
└── android-payment-listener/  # Android 监听 App
```

---

## 3. .gitignore 配置

以下文件不提交到 Git：

```
.env              # 含密钥
node_modules/     # 依赖
.DS_Store         # macOS 系统文件
frontend/dist/    # 前端打包产物
frontend/unpackage/
```

> ⚠️ 服务器上的 `.env` 和 `node_modules` 只在服务器本地维护，不进 Git。

---

## 4. 后端环境变量

**模板文件**：`server/.env.example`
**服务器实际文件**：`/var/www/palmistry/server/.env`（不在 Git 中）

| 变量 | 说明 | 生产值 |
|------|------|--------|
| `DASHSCOPE_API_KEY` | 通义千问 API Key | `sk-****` |
| `PORT` | 后端端口 | `3000` |
| `ALLOWED_ORIGIN` | 允许跨域的域名 | `http://localhost:5173,https://palmistrygroup.com` |
| `PAYMENT_NOTIFY_SECRET` | Android 上报密钥 | `5b91****` |
| `PAYMENT_TEST_MODE` | 测试模式 | `false` |
| `PAYMENT_LISTENER_HEARTBEAT_TTL_MS` | 心跳离线阈值 | `60000` |
| `PAYMENT_LISTENER_LIVE_HEARTBEAT_MS` | 心跳在线阈值 | `15000` |
| `ALIPAY_QR_SINGLE_URL` | 单模块收款码 | `/static/alipay-qr.png` |
| `ALIPAY_QR_BUNDLE_URL` | 打包收款码 | `/static/alipay-qr.png` |

---

## 5. 服务器目录结构

```
/var/www/palmistry/
├── server/              # 运行目录（PM2 跑这里）
├── server-src/          # Git 仓库（稀疏检出，只拉 server/）
├── frontend/            # 前端静态文件
└── backup/              # 备份
```

---

## 6. 更新流程

### 6.1 后端更新

#### 第一步：本地提交并推送

```bash
cd /Users/wangliqun/Desktop/SoftWare/Mine/palmistry
git add -A
git commit -m "你的提交说明"
git push origin main
```

#### 第二步：服务器拉取并同步

```bash
ssh root@104.225.236.196
cd /var/www/palmistry/server-src

# 1. 拉取最新代码
git pull origin main

# 2. 同步源码到运行目录（不碰 .env 和 node_modules）
cp server/*.js server/*.json /var/www/palmistry/server/

# 3. 如果 .env.example 有新增变量，更新 .env
diff /var/www/palmistry/server/.env server/.env.example
# 有新变量就手动追加到 /var/www/palmistry/server/.env

# 4. 如果 package.json 有新依赖，装依赖
cd /var/www/palmistry/server
npm install

# 5. PM2 重启（改了 .env 必须 delete+start）
pm2 delete palmistry
pm2 start index.js --name palmistry --cwd /var/www/palmistry/server
pm2 save

# 6. 验证
curl http://127.0.0.1:3000/health
curl http://127.0.0.1:3000/api/payment/listener/status
```

> ⚠️ `pm2 restart` 不会重载 `.env`，改了环境变量必须用 `delete` + `start`。

---

### 6.2 前端更新

#### 第一步：HBuilderX 打包

1. 打开 HBuilderX → 加载 `frontend/` 项目
2. 菜单：发行 → 网站-PC Web/H5
3. 产物输出到 `frontend/unpackage/dist/build/web/`

#### 第二步：一键部署

```bash
cd /Users/wangliqun/Desktop/SoftWare/Mine/palmistry
./update-palmistry.sh
```

脚本执行流程：
1. 备份服务器当前 `frontend` 到 `backup/`
2. 清空服务器 `frontend` 目录
3. 上传本地打包产物
4. 上传 `robots.txt` 和 `sitemap.xml`
5. 重载 Nginx

#### 第三步：验证

浏览器打开 `https://palmistrygroup.com` → `Cmd+Shift+R` 强制刷新

---

### 6.3 Android 监听 App 更新

Android App 的代码在 `android-payment-listener/`，需要用 Android Studio 打包 APK，安装到服务器旁的 Android 设备上。

更新后需同步检查：
- `PAYMENT_NOTIFY_SECRET` 与后端 `.env` 一致
- 心跳上报地址指向 `https://palmistrygroup.com/api/payment/listener/heartbeat`

---

## 7. 从零部署（参考）

### 7.1 后端首次部署

```bash
ssh root@104.225.236.196

# 1. 创建目录
mkdir -p /var/www/palmistry

# 2. 稀疏检出仓库
cd /var/www/palmistry
git clone --no-checkout https://github.com/Magic7-web/PALMISTRY.git server-src
cd server-src
git sparse-checkout init --cone
git sparse-checkout set server
git checkout main

# 3. 复制源码到运行目录
cd /var/www/palmistry
mkdir -p server
cp server-src/server/*.js server-src/server/*.json server/

# 4. 配置 .env
cp server/.env.example server/.env
nano server/.env  # 填入真实 API Key 和密钥

# 5. 装依赖
cd server
npm install

# 6. PM2 启动
pm2 start index.js --name palmistry --cwd /var/www/palmistry/server
pm2 save
pm2 startup  # 设置开机自启

# 7. 验证
curl http://127.0.0.1:3000/health
```

### 7.2 前端首次部署

```bash
# 1. HBuilderX 打包后，创建服务器目录
ssh root@104.225.236.196 "mkdir -p /var/www/palmistry/frontend"

# 2. 上传打包产物
scp -r frontend/unpackage/dist/build/web/* root@104.225.236.196:/var/www/palmistry/frontend/

# 3. 配置 Nginx（参考 SERVER.md 第 4 节）
```

### 7.3 Nginx 配置

```nginx
server {
    listen 80;
    server_name palmistrygroup.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name palmistrygroup.com;

    ssl_certificate /etc/nginx/ssl/palmistry.crt;
    ssl_certificate_key /etc/nginx/ssl/palmistry.key;

    root /var/www/palmistry/frontend;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /health {
        proxy_pass http://127.0.0.1:3000;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 120s;
        client_max_body_size 25m;
    }
}
```

---

## 8. 安全注意事项

1. **`.env` 不进 Git** — 含 API Key 和支付密钥，已在 `.gitignore` 中屏蔽
2. **轮换密钥** — 如 `.env` 曾误提交到 Git，上线后轮换：
   - DashScope 控制台重新生成 API Key
   - 用 `openssl rand -hex 16` 生成新的 `PAYMENT_NOTIFY_SECRET`
   - 同步更新 Android App 中的密钥
3. **Cloudflare 代理** — 所有流量经过 CF，隐藏源站 IP
4. **SSL 证书** — 使用 Cloudflare 源证书，15 年免续期

---

## 9. 快速排障

| 问题 | 排查命令 |
|------|----------|
| 后端没响应 | `pm2 logs palmistry --lines 30` |
| 端口被占 | `ss -tlnp | grep :3000` |
| 前端 404 | 检查 Nginx `try_files` 配置 |
| CORS 报错 | 检查 `.env` 的 `ALLOWED_ORIGIN` |
| API Key 无效 | 去 DashScope 控制台检查 Key 状态 |
| npm install 失败 | `rm -rf node_modules package-lock.json && npm install` |

详细排障见 [SERVER.md](./SERVER.md) 第 11 节。
