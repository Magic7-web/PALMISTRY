import { createOrder, getOrderById, notifyPayment } from './paymentStore.js';
import {
  getListenerStatus,
  isListenerAvailableForPayment,
  recordListenerHeartbeat,
  waitForListenerReady
} from './paymentListenerStore.js';

export function registerPaymentRoutes(app, config) {
  app.post('/api/payment/orders', async (req, res) => {
    try {
      if (!config.testMode && !isListenerAvailableForPayment()) {
        res.status(503).json({
          success: false,
          listenerUnavailable: true,
          message: 'Payment listener is offline',
          ...getListenerStatus()
        });
        return;
      }

      const displayPrice = req.body?.displayPrice;
      if (!displayPrice) {
        res.status(400).json({
          success: false,
          message: 'displayPrice is required'
        });
        return;
      }

      const order = await createOrder(
        {
          displayPrice,
          productType: req.body?.productType,
          moduleIds: req.body?.moduleIds,
          sessionId: req.body?.sessionId
        },
        config
      );

      res.json({
        success: true,
        ...order
      });
    } catch (error) {
      console.error('Create payment order failed:', error);
      res.status(500).json({
        success: false,
        message: error instanceof Error ? error.message : 'Failed to create order'
      });
    }
  });

  app.get('/api/payment/orders/:orderId', async (req, res) => {
    const order = await getOrderById(req.params.orderId);
    if (!order) {
      res.status(404).json({
        success: false,
        message: 'Order not found'
      });
      return;
    }

    res.json({
      success: true,
      ...order
    });
  });

  app.post('/api/payment/notify', async (req, res) => {
    const secret = req.header('x-payment-secret');
    if (!config.notifySecret || secret !== config.notifySecret) {
      res.status(401).json({
        success: false,
        message: 'Invalid payment notify secret'
      });
      return;
    }

    const amount = req.body?.amount;
    if (!amount) {
      res.status(400).json({
        success: false,
        message: 'amount is required'
      });
      return;
    }

    const result = await notifyPayment({
      amount,
      title: req.body?.title,
      body: req.body?.body,
      postedAt: req.body?.postedAt
    });

    recordListenerHeartbeat({
      listenerConnected: true,
      source: 'notify'
    });

    res.json({
      success: true,
      ...result
    });
  });

  /** Android 诊断 App 定时上报，供 H5 支付前检查收款通道是否可用 */
  app.post('/api/payment/listener/heartbeat', async (req, res) => {
    const secret = req.header('x-payment-secret');
    if (!config.notifySecret || secret !== config.notifySecret) {
      res.status(401).json({
        success: false,
        message: 'Invalid payment notify secret'
      });
      return;
    }

    recordListenerHeartbeat({
      listenerConnected: req.body?.listenerConnected !== false,
      notificationListenerEnabled:
        typeof req.body?.notificationListenerEnabled === 'boolean'
          ? req.body.notificationListenerEnabled
          : undefined,
      deviceInfo: req.body?.deviceInfo,
      source: 'heartbeat'
    });

    res.json({
      success: true,
      ...getListenerStatus()
    });
  });

  /** H5 支付前检查 Android 监听是否在线（无需密钥） */
  app.get('/api/payment/listener/status', (_req, res) => {
    res.json({
      success: true,
      ...getListenerStatus()
    });
  });

  /**
   * H5 支付前探测：等待新鲜心跳，避免 Android 改错配置后仍沿用旧心跳。
   * body: { timeoutMs?: number }
   */
  app.post('/api/payment/listener/wait-ready', async (req, res) => {
    const timeoutMs = Math.min(Number(req.body?.timeoutMs) || 6000, 15000);
    const result = await waitForListenerReady({ timeoutMs });
    res.json({
      success: true,
      ...result
    });
  });
}
