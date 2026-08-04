import fs from 'fs';
import path from 'path';
import { randomUUID } from 'crypto';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.join(__dirname, 'data');
const ORDERS_FILE = path.join(DATA_DIR, 'orders.json');
const UNMATCHED_FILE = path.join(DATA_DIR, 'unmatched.json');

const ORDER_TTL_MS = 5 * 60 * 1000;
const AMOUNT_REUSE_WINDOW_MS = 5 * 60 * 1000;
/** 唯一金额优先在标价之下：例如标价 4.99 → 实付 4.79~4.98 */
const AMOUNT_BELOW_MIN_CENTS = 20;
const AMOUNT_BELOW_MAX_CENTS = 1;
/** 候选耗尽时后备：标价之上 +1~+10 分 */
const AMOUNT_ABOVE_MAX_CENTS = 10;

/** @typedef {'pending' | 'paid' | 'expired' | 'conflict'} OrderStatus */

/**
 * @typedef {Object} Order
 * @property {string} orderId
 * @property {OrderStatus} status
 * @property {string} displayPrice
 * @property {string} amount
 * @property {'single' | 'bundle'} productType
 * @property {string[]} moduleIds
 * @property {string} sessionId
 * @property {number} createdAt
 * @property {number} expiresAt
 * @property {number | null} paidAt
 * @property {object | null} paymentEvidence
 */

/** @type {Promise<void>} */
let writeChain = Promise.resolve();

function runSerializedWrite(task) {
  const result = writeChain.then(() => task());
  writeChain = result.then(
    () => undefined,
    () => undefined
  );
  return result;
}

function ensureDataDir() {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
}

function readJson(filePath, fallback) {
  ensureDataDir();
  if (!fs.existsSync(filePath)) {
    return fallback;
  }
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (_error) {
    return fallback;
  }
}

function writeJson(filePath, data) {
  ensureDataDir();
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2), 'utf8');
}

function loadOrders() {
  return readJson(ORDERS_FILE, []);
}

function saveOrders(orders) {
  return runSerializedWrite(() => {
    writeJson(ORDERS_FILE, orders);
  });
}

function loadUnmatched() {
  return readJson(UNMATCHED_FILE, []);
}

function saveUnmatched(items) {
  return runSerializedWrite(() => {
    writeJson(UNMATCHED_FILE, items);
  });
}

function toCents(price) {
  const value = Number.parseFloat(String(price));
  if (!Number.isFinite(value)) {
    throw new Error('Invalid displayPrice');
  }
  return Math.round(value * 100);
}

function formatAmount(cents) {
  return (cents / 100).toFixed(2);
}

function normalizeAmount(raw) {
  if (raw == null) {
    return '';
  }
  return String(raw).replace(/^[¥￥]/, '').trim();
}

function expireStaleOrders(orders, now = Date.now()) {
  let changed = false;
  for (const order of orders) {
    if (order.status === 'pending' && now > order.expiresAt) {
      order.status = 'expired';
      changed = true;
    }
  }
  return changed;
}

function getRecentlyUsedAmounts(orders, now = Date.now()) {
  const used = new Set();
  for (const order of orders) {
    const recent = now - order.createdAt <= AMOUNT_REUSE_WINDOW_MS;
    if (recent && (order.status === 'pending' || order.status === 'paid')) {
      used.add(order.amount);
    }
  }
  return used;
}

function collectAmountCandidates(baseCents, used, minOffset, maxOffset, belowDisplayOnly) {
  const candidates = [];
  for (let offset = minOffset; offset <= maxOffset; offset += 1) {
    const cents = baseCents + offset;
    if (cents <= 0) {
      continue;
    }
    if (belowDisplayOnly && cents >= baseCents) {
      continue;
    }
    const amount = formatAmount(cents);
    if (!used.has(amount)) {
      candidates.push(amount);
    }
  }
  return candidates;
}

function allocateUniqueAmount(displayPrice, orders) {
  const baseCents = toCents(displayPrice);
  const used = getRecentlyUsedAmounts(orders);

  let candidates = collectAmountCandidates(
    baseCents,
    used,
    -AMOUNT_BELOW_MIN_CENTS,
    -AMOUNT_BELOW_MAX_CENTS,
    true
  );

  if (candidates.length === 0) {
    candidates = collectAmountCandidates(baseCents, used, 1, AMOUNT_ABOVE_MAX_CENTS, false);
  }

  if (candidates.length === 0) {
    throw new Error('No unique amount available in pool');
  }

  return candidates[Math.floor(Math.random() * candidates.length)];
}

export async function createOrder(input, config) {
  const orders = loadOrders();
  expireStaleOrders(orders);
  const now = Date.now();
  const displayPrice = normalizeAmount(input.displayPrice);
  const amount = allocateUniqueAmount(displayPrice, orders);
  const productType = input.productType === 'bundle' ? 'bundle' : 'single';
  const qrCodeUrl =
    productType === 'bundle' ? config.alipayQrBundleUrl : config.alipayQrSingleUrl;

  /** @type {Order} */
  const order = {
    orderId: randomUUID(),
    status: 'pending',
    displayPrice,
    amount,
    productType,
    moduleIds: Array.isArray(input.moduleIds) ? input.moduleIds.map(String) : [],
    sessionId: input.sessionId ? String(input.sessionId) : '',
    createdAt: now,
    expiresAt: now + ORDER_TTL_MS,
    paidAt: null,
    paymentEvidence: null
  };

  orders.unshift(order);
  await saveOrders(orders);

  return {
    orderId: order.orderId,
    amount: order.amount,
    displayPrice: order.displayPrice,
    expiresAt: order.expiresAt,
    qrCodeUrl
  };
}

export async function getOrderById(orderId) {
  const orders = loadOrders();
  if (expireStaleOrders(orders)) {
    await saveOrders(orders);
  }
  const order = orders.find((item) => item.orderId === orderId);
  if (!order) {
    return null;
  }
  return {
    orderId: order.orderId,
    status: order.status,
    amount: order.amount,
    displayPrice: order.displayPrice,
    expiresAt: order.expiresAt,
    paidAt: order.paidAt,
    moduleIds: order.moduleIds,
    sessionId: order.sessionId
  };
}

export async function notifyPayment(payload) {
  const orders = loadOrders();
  if (expireStaleOrders(orders)) {
    await saveOrders(orders);
  }

  const amount = normalizeAmount(payload.amount);
  const now = Date.now();
  const pendingMatches = orders.filter(
    (order) =>
      order.status === 'pending' &&
      order.amount === amount &&
      now <= order.expiresAt
  );

  const evidence = {
    amount,
    title: payload.title ? String(payload.title) : '',
    body: payload.body ? String(payload.body) : '',
    postedAt: Number(payload.postedAt) || now,
    receivedAt: now
  };

  if (pendingMatches.length === 0) {
    const unmatched = loadUnmatched();
    unmatched.unshift({
      id: randomUUID(),
      reason: 'no_pending_order',
      ...evidence
    });
    await saveUnmatched(unmatched.slice(0, 200));
    return { matched: false, reason: 'no_pending_order' };
  }

  pendingMatches.sort((a, b) => a.createdAt - b.createdAt);
  const order = pendingMatches[0];

  if (pendingMatches.length > 1) {
    for (let i = 1; i < pendingMatches.length; i += 1) {
      pendingMatches[i].status = 'conflict';
    }
  }

  order.status = 'paid';
  order.paidAt = now;
  order.paymentEvidence = evidence;
  await saveOrders(orders);

  return {
    matched: true,
    orderId: order.orderId,
    status: 'paid'
  };
}

export function createPaymentRouter(config) {
  return {
    createOrder,
    getOrderById,
    notifyPayment,
    config
  };
}
