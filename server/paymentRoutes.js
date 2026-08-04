import { createOrder, getOrderById, notifyPayment } from './paymentStore.js';

export function registerPaymentRoutes(app, config) {
  app.post('/api/payment/orders', async (req, res) => {
    try {
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

    res.json({
      success: true,
      ...result
    });
  });
}
