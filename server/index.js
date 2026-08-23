import cors from 'cors';
import dotenv from 'dotenv';
import express from 'express';
import rateLimit from 'express-rate-limit';
import fetch from 'node-fetch';
import { registerPaymentRoutes } from './paymentRoutes.js';

dotenv.config();

const DASHSCOPE_ENDPOINT =
  'https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation';
const PORT = Number(process.env.PORT || 3001);
const API_KEY = process.env.DASHSCOPE_API_KEY;
const PAYMENT_NOTIFY_SECRET = process.env.PAYMENT_NOTIFY_SECRET || 'dev-payment-secret';
const PAYMENT_TEST_MODE = process.env.PAYMENT_TEST_MODE === 'true';
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGIN || '')
  .split(',')
  .map((item) => item.trim())
  .filter(Boolean);

const app = express();

app.use(
  express.json({
    limit: '25mb'
  })
);

app.use(
  cors({
    origin(origin, callback) {
      if (!origin || ALLOWED_ORIGINS.includes(origin)) {
        callback(null, true);
        return;
      }
      callback(new Error(`CORS blocked for origin: ${origin}`));
    }
  })
);

const qwenRateLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 8,
  standardHeaders: true,
  legacyHeaders: false,
  handler(_req, res) {
    res.status(429).json({
      success: false,
      message: 'Too many requests. Please try again later.'
    });
  }
});

function ensureApiKeyConfigured(res) {
  if (API_KEY && API_KEY.length > 0 && API_KEY !== 'your_dashscope_api_key_here') {
    return true;
  }
  res.status(500).json({
    success: false,
    message: 'Server misconfigured: DASHSCOPE_API_KEY is not set'
  });
  return false;
}

app.all('/api/qwen', (req, res, next) => {
  if (req.method !== 'POST') {
    res.status(405).json({
      success: false,
      message: 'Method not allowed'
    });
    return;
  }
  next();
});

app.post('/api/qwen', qwenRateLimiter, async (req, res) => {
  if (!ensureApiKeyConfigured(res)) {
    return;
  }

  const analysisType = req.body?.analysis_type || 'unknown';
  const bodySize = JSON.stringify(req.body || {}).length;
  console.log(`[qwen] type=${analysisType} bodySize=${bodySize}`);

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 180000);

  const upstreamBody = {
    ...req.body,
    parameters: {
      ...(req.body?.parameters || {}),
      result_format: req.body?.parameters?.result_format || 'message',
      response_format: req.body?.parameters?.response_format || { type: 'json_object' }
    }
  };

  try {
    const upstream = await fetch(DASHSCOPE_ENDPOINT, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${API_KEY}`,
        'Content-Type': 'application/json',
        'X-DashScope-SSE': 'disable'
      },
      body: JSON.stringify(upstreamBody),
      signal: controller.signal
    });

    const rawText = await upstream.text();
    let payload = rawText;

    try {
      payload = rawText.length > 0 ? JSON.parse(rawText) : {};
    } catch (_error) {
      payload = {
        success: false,
        message: rawText || 'AI service temporarily unavailable'
      };
    }

    if (!upstream.ok) {
      const message =
        payload && typeof payload === 'object' && payload.message
          ? payload.message
          : 'AI service temporarily unavailable';
      res.status(upstream.status).json({
        success: false,
        message
      });
      return;
    }

    res.status(upstream.status).json(payload);
  } catch (error) {
    if (error && error.name === 'AbortError') {
      console.error('DashScope proxy timeout:', analysisType);
      res.status(504).json({
        success: false,
        message: 'AI 分析超时，请稍后重试'
      });
      return;
    }
    console.error('DashScope proxy error:', error);
    res.status(503).json({
      success: false,
      message: 'AI service temporarily unavailable'
    });
  } finally {
    clearTimeout(timeoutId);
  }
});

app.get('/health', (_req, res) => {
  res.json({
    ok: true,
    apiKeyConfigured: Boolean(
      API_KEY && API_KEY.length > 0 && API_KEY !== 'your_dashscope_api_key_here'
    ),
    paymentTestMode: PAYMENT_TEST_MODE,
    paymentNotifyConfigured: Boolean(PAYMENT_NOTIFY_SECRET)
  });
});

registerPaymentRoutes(app, {
  notifySecret: PAYMENT_NOTIFY_SECRET,
  alipayQrSingleUrl: process.env.ALIPAY_QR_SINGLE_URL || '/static/alipay-qr.png',
  alipayQrBundleUrl: process.env.ALIPAY_QR_BUNDLE_URL || '/static/alipay-qr.png'
});

app.use((error, _req, res, next) => {
  if (error && error.message && error.message.startsWith('CORS blocked')) {
    res.status(403).json({
      success: false,
      message: 'Origin not allowed'
    });
    return;
  }
  next(error);
});

app.listen(PORT, () => {
  console.log(`Palmistry API proxy listening on http://localhost:${PORT}`);
  if (!API_KEY || API_KEY.length === 0 || API_KEY === 'your_dashscope_api_key_here') {
    console.error(
      'WARNING: DASHSCOPE_API_KEY is not configured. Copy .env.example to .env and set your key.'
    );
  } else {
    console.log('DASHSCOPE_API_KEY is configured.');
  }
  if (ALLOWED_ORIGINS.length === 0) {
    console.warn('WARNING: ALLOWED_ORIGIN is empty. CORS will only allow requests without Origin.');
  } else {
    console.log('Allowed origins:', ALLOWED_ORIGINS.join(', '));
  }
});
