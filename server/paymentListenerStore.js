/** Android 收款监听心跳状态（内存，单实例后端） */

const DEFAULT_TTL_MS = Number(process.env.PAYMENT_LISTENER_HEARTBEAT_TTL_MS || 60000);
/** 支付探测：心跳必须在此毫秒数内才算实时在线（默认 15000，约为 Android 10s 心跳的 1.5 倍） */
const LIVE_HEARTBEAT_MS = Number(process.env.PAYMENT_LISTENER_LIVE_HEARTBEAT_MS || 15000);

/** @type {{ lastHeartbeatAt: number | null, listenerConnected: boolean, notificationListenerEnabled: boolean | null, deviceInfo: string | null, source: string | null }} */
let listenerState = {
  lastHeartbeatAt: null,
  listenerConnected: false,
  notificationListenerEnabled: null,
  deviceInfo: null,
  source: null
};

/**
 * @param {{ listenerConnected?: boolean, notificationListenerEnabled?: boolean, deviceInfo?: string, source?: string }} payload
 */
export function recordListenerHeartbeat(payload = {}) {
  listenerState = {
    lastHeartbeatAt: Date.now(),
    listenerConnected: payload.listenerConnected !== false,
    notificationListenerEnabled:
      typeof payload.notificationListenerEnabled === 'boolean'
        ? payload.notificationListenerEnabled
        : listenerState.notificationListenerEnabled,
    deviceInfo: payload.deviceInfo != null ? String(payload.deviceInfo) : listenerState.deviceInfo,
    source: payload.source != null ? String(payload.source) : 'heartbeat'
  };
}

export function getListenerStatus(ttlMs = DEFAULT_TTL_MS) {
  const now = Date.now();
  const lastAt = listenerState.lastHeartbeatAt;
  const fresh = lastAt != null && now - lastAt <= ttlMs;
  const available = fresh && listenerState.listenerConnected === true;

  let reason = 'ok';
  if (lastAt == null) {
    reason = 'no_heartbeat';
  } else if (!fresh) {
    reason = 'heartbeat_stale';
  } else if (!listenerState.listenerConnected) {
    reason = 'listener_disconnected';
  }

  return {
    available,
    listenerConnected: listenerState.listenerConnected,
    notificationListenerEnabled: listenerState.notificationListenerEnabled,
    lastHeartbeatAt: lastAt,
    heartbeatAgeMs: lastAt != null ? now - lastAt : null,
    heartbeatTtlMs: ttlMs,
    liveHeartbeatMs: LIVE_HEARTBEAT_MS,
    deviceInfo: listenerState.deviceInfo,
    source: listenerState.source,
    reason
  };
}

export function isListenerAvailableForPayment(ttlMs = DEFAULT_TTL_MS) {
  return getListenerStatus(ttlMs).available;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 支付前探测：等待 Android 上报「新鲜」心跳，避免沿用改错配置前的旧心跳。
 * @param {{ timeoutMs?: number }} options
 */
export async function waitForListenerReady(options = {}) {
  const timeoutMs = Math.min(Number(options.timeoutMs) || 6000, 15000);
  const waitStartAt = Date.now();
  const baselineLastAt = listenerState.lastHeartbeatAt;
  const deadline = waitStartAt + timeoutMs;

  while (Date.now() < deadline) {
    const status = getListenerStatus();
    const lastAt = listenerState.lastHeartbeatAt;
    const connected = listenerState.listenerConnected === true;

    if (lastAt != null && connected) {
      const ageMs = Date.now() - lastAt;
      const isLive = ageMs <= LIVE_HEARTBEAT_MS && lastAt >= waitStartAt - LIVE_HEARTBEAT_MS;
      const isNewSinceProbe =
        baselineLastAt == null
          ? lastAt >= waitStartAt
          : lastAt > baselineLastAt;

      if (isLive || isNewSinceProbe) {
        return {
          ...status,
          available: true,
          reason: isLive ? 'live_heartbeat' : 'fresh_heartbeat',
          waitedMs: Date.now() - waitStartAt
        };
      }
    }

    await sleep(400);
  }

  return {
    ...getListenerStatus(),
    available: false,
    reason: 'probe_timeout',
    waitedMs: timeoutMs
  };
}
