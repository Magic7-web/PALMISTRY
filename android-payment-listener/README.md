# 支付宝通知诊断版

监听支付宝收款通知，本地保存记录，并将 **支付匹配** 通知上报后端用于订单核销。

## 功能

- 仅监听 `com.eg.android.AlipayGphone`
- 收款语义匹配 + 上下文金额提取
- 本地保存最近 30 条通知
- 支付匹配成功后 POST 到后端 `/api/payment/notify`
- 主界面可配置后端地址与 `PAYMENT_NOTIFY_SECRET`

## 构建

见前文 Gradle 说明，或 Android Studio Open 本目录后 Run。

## 配置后端上报

1. 打开 App，在 **后端上报配置** 填写：
   - **后端地址**：电脑局域网 IP，例如 `http://192.168.1.100:3001`
   - **上报密钥**：与 server `.env` 中 `PAYMENT_NOTIFY_SECRET` 一致（默认 `dev-payment-secret`）
2. 点 **保存上报配置**
3. 手机与电脑需在同一 Wi-Fi

> 本地 HTTP 已开启 `usesCleartextTraffic`，仅用于开发。

## 支付闭环联调

1. 启动后端：`cd server && npm run dev`
2. 启动前端 H5：`npm run dev`（项目根目录）
3. 安装本 App 到 **收款手机**（登录支付宝收款账号）
4. 配置上报地址为电脑 IP:3001
5. 浏览器打开 H5，解锁追问模块，选择支付宝
6. 记下页面显示的 **唯一金额**（例如 ¥4.97）
7. 用另一支付宝账号向收款码支付该 **唯一金额**
8. 观察：
   - App 出现 `[支付匹配]` 记录
   - App 底部「最近上报」显示成功
   - H5 支付弹窗自动变为「支付已确认，正在解锁…」

## vivo / OriginOS

- 开启通知使用权
- 电池 → 不限制（App 内可点「加入电池优化白名单」）
- 允许自启动 / 后台运行
- 安装后会在通知栏显示「支付通知监听运行中」常驻通知（前台 Service 保活）
- 熄屏/后台每 10 秒上报心跳（前台 Service 内 Handler）
- 进程被杀后 START_STICKY 自动重启；开机自启恢复

## 保活机制（v1.1.0+）

- **前台 Service + WakeLock**：维持进程，熄屏仍心跳
- **START_STICKY**：内存不足被杀后系统自动重启 Service
- **开机自启**：`BOOT_COMPLETED` 后自动恢复（需已保存后端配置）
- **电池白名单**：App 内一键申请忽略电池优化

## 项目结构

```
app/src/main/java/com/palmistry/paymentdiagnostic/
├── MainActivity.java
├── AlipayNotificationListenerService.java
├── ForegroundKeepAliveService.java
├── KeepAliveManager.java
├── BootReceiver.java
├── NotificationStorage.java
├── PaymentNotifyClient.java
├── NotifyConfig.java
└── RecordUpdateNotifier.java
```

## 说明

本 App **不会** 自动解锁 H5，只负责读取通知并上报后端。解锁由 H5 轮询订单状态完成。
