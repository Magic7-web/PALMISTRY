# 支付宝唯一金额支付闭环（最小版）

## 流程

```
H5 创建订单 → 展示唯一金额 + 收款码 → 用户扫码支付
      ↓                                      ↓
  轮询订单状态                          支付宝通知
      ↑                                      ↓
  订单变 paid ← 后端匹配金额 ← Android 上报 notify
      ↓
  H5 自动解锁内容
```

## 快速开始

### 1. 后端

```bash
cd server
cp .env.example .env
# 编辑 .env：PAYMENT_NOTIFY_SECRET=dev-payment-secret
npm install
npm run dev
```

### 2. 前端

```bash
# 项目根目录
npm run dev
```

`utils/paymentConfig.uts`：

- `testMode: false` — 正式闭环（默认）
- `testMode: true` — 本地跳过验单，显示「测试模式直接解锁」

### 3. Android 监听 App

```bash
cd android-payment-listener
# Android Studio Run 到收款手机
```

App 内配置：

- 后端地址：`http://192.168.x.x:3001`（电脑局域网 IP）
- 上报密钥：`dev-payment-secret`

### 4. 联调步骤

1. 电脑、手机同一 Wi-Fi
2. 收款手机登录 **收款支付宝账号**，安装监听 App 并开启通知使用权
3. 浏览器打开 H5，完成手相分析后，点击解锁追问模块
4. 选择 **支付宝**，等待创建订单
5. 页面显示：
   - 商品标价 ¥4.99
   - **请支付唯一金额** ¥4.97（示例）
6. 用 **另一支付宝** 扫码，支付 **4.97 元**（必须完全一致）
7. 约 2 秒内 H5 应显示「支付已确认，正在解锁…」并生成深度解读

### 5. 验证后端

```bash
# 查看订单
cat server/data/orders.json

# 查看未匹配通知
cat server/data/unmatched.json
```

## 注意事项

- 已移除「我已支付，继续分析」按钮（`testMode: false` 时）
- 订单有效期 5 分钟
- 唯一金额池：标价位下方 0.01~0.20 元（如标价 4.99 → 实付 4.79~4.98）；耗尽时后备 +0.01~+0.10 元
- 金额错误或过期不会解锁

## 改动文件索引

| 模块 | 文件 |
|------|------|
| 后端 | `server/paymentStore.js`, `server/paymentRoutes.js`, `server/index.js` |
| 前端 | `utils/paymentApi.uts`, `utils/paymentConfig.uts`, `pages/camera/camera.uvue`, `vite.config.js` |
| Android | `PaymentNotifyClient.java`, `NotifyConfig.java`, `AlipayNotificationListenerService.java` |
