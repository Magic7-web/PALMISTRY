# Palmistry API Proxy

最小 DashScope 代理服务，用于隐藏大模型 API Key。前端统一请求 `/api/qwen`，由本服务转发到通义千问官方接口。

## 环境变量

复制示例配置：

```bash
cd server
cp .env.example .env
```

在 `.env` 中填写：

| 变量 | 说明 |
|------|------|
| `DASHSCOPE_API_KEY` | 通义千问 DashScope API Key（仅保存在服务端） |
| `PORT` | 本地服务端口，默认 `3001` |
| `ALLOWED_ORIGIN` | 允许跨域的前端域名，逗号分隔 |

示例：

```env
DASHSCOPE_API_KEY=your_real_key_here
PORT=3001
ALLOWED_ORIGIN=http://localhost:5173,https://your-domain.com
```

## 本地开发

1. 安装依赖：

```bash
cd server
npm install
```

2. 配置 `.env`（见上）。

3. 启动后端：

```bash
npm run dev
```

或：

```bash
npm start
```

4. 启动前端 H5 开发服务（项目根目录）。`vite.config.js` 会把 `/api/qwen` 代理到 `http://localhost:3001`。

5. 健康检查：

```bash
curl http://localhost:3001/health
```

## 接口说明

### `POST /api/qwen`

- 仅允许 `POST`
- 请求体：与 DashScope multimodal-generation 接口一致（前端原样透传）
- 成功：返回 DashScope 原始 JSON
- 失败：返回统一结构，例如：

```json
{
  "success": false,
  "message": "AI service temporarily unavailable"
}
```

### 安全限制

- 请求体上限：`10mb`
- CORS：仅 `ALLOWED_ORIGIN` 中配置的域名
- 限流：每个 IP 每分钟最多 8 次

## 生产部署

1. 在服务器上部署本目录（Node.js >= 18）。
2. 配置 `.env`，填入真实 `DASHSCOPE_API_KEY` 和正式前端域名。
3. 使用进程管理器启动，例如：

```bash
npm install --production
npm start
```

4. 在 Nginx（或其他反向代理）中，将前端的 `/api/qwen` 转发到本服务，例如：

```nginx
location /api/qwen {
  proxy_pass http://127.0.0.1:3001/api/qwen;
  proxy_http_version 1.1;
  proxy_set_header Host $host;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  client_max_body_size 10m;
}
```

5. 确保 `.env` 不被提交到 Git，且构建后的前端产物中不包含 API Key。

## 常见问题

- **未配置 API Key**：`/api/qwen` 返回 `500`，message 提示 `DASHSCOPE_API_KEY is not set`。
- **请求过于频繁**：返回 `429`，请稍后重试。
- **CORS 错误**：检查 `ALLOWED_ORIGIN` 是否包含当前前端访问地址。
