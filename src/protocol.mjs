import { randomUUID } from 'node:crypto';

export function nowIso() {
  return new Date().toISOString();
}

export function nowMs() {
  return Date.now();
}

export function makeId(prefix = 'msg') {
  return `${prefix}_${randomUUID()}`;
}

export function safeJsonParse(text) {
  try {
    return { ok: true, value: JSON.parse(text) };
  } catch (error) {
    return { ok: false, error };
  }
}

export function createResponse(id, payload, type = 'response') {
  return { type, id, ok: true, payload };
}

export function createError(id, code, message, details = undefined, type = 'error') {
  return { type, id, ok: false, error: { code, message, details } };
}

export function createEvent(event, fields = {}) {
  return { type: 'event', event, ...fields };
}

export function normalizeSessionKey(value) {
  return value ?? '';
}

export function pickDefined(source, keys) {
  const out = {};
  for (const key of keys) {
    if (source[key] !== undefined) out[key] = source[key];
  }
  return out;
}
