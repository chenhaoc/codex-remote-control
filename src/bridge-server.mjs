import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { EventEmitter } from 'node:events';
import { randomUUID } from 'node:crypto';
import { WebSocket, WebSocketServer } from 'ws';
import { createError, createResponse, createEvent, nowIso } from './protocol.mjs';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

const SANDBOX_POLICY_TYPES = new Set(['readOnly', 'workspaceWrite', 'dangerFullAccess', 'externalSandbox']);
const SANDBOX_MODE_TYPES = new Set(['readOnly', 'workspaceWrite', 'dangerFullAccess']);

function firstNonEmptyString(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value;
    }
  }
  return '';
}

function diffStatsLabel(diff) {
  const text = typeof diff === 'string' ? diff : '';
  if (!text.trim()) return '';
  let additions = 0;
  let deletions = 0;
  for (const line of text.replace(/\r\n/g, '\n').split('\n')) {
    if (line.startsWith('+') && !line.startsWith('+++')) additions += 1;
    if (line.startsWith('-') && !line.startsWith('---')) deletions += 1;
  }
  if (additions > 0 && deletions > 0) return `+${additions} / -${deletions}`;
  if (additions > 0) return `+${additions}`;
  if (deletions > 0) return `-${deletions}`;
  return '';
}

function mergeSessionSummary(summary, stored) {
  if (!stored) return clone(summary);
  const merged = {
    ...clone(summary),
    session_id: summary.session_id ?? stored.session_id,
    thread_id: summary.thread_id ?? stored.thread_id ?? summary.session_id,
    title: firstNonEmptyString(summary.title, stored.title),
    cwd: firstNonEmptyString(summary.cwd, stored.cwd),
    model: firstNonEmptyString(summary.model, stored.model),
    preview: firstNonEmptyString(summary.preview, stored.preview),
    backend: firstNonEmptyString(summary.backend, stored.backend),
    createdAt: firstNonEmptyString(summary.createdAt, stored.createdAt),
    updatedAt: firstNonEmptyString(summary.updatedAt, stored.updatedAt),
    active: summary.active ?? stored.active ?? true,
    thread: clone(summary.thread ?? stored.thread ?? null),
  };

  if (summary.approvalPolicy != null && !(typeof summary.approvalPolicy === 'string' && summary.approvalPolicy.trim() === '')) {
    merged.approvalPolicy = clone(summary.approvalPolicy);
  } else if (stored.approvalPolicy != null) {
    merged.approvalPolicy = clone(stored.approvalPolicy);
  }

  if (summary.permissions != null) {
    merged.permissions = clone(summary.permissions);
  } else if (stored.permissions != null) {
    merged.permissions = clone(stored.permissions);
  }

  if (summary.sandbox != null) {
    merged.sandbox = stored.sandboxOverride && stored.sandbox != null
      ? clone(stored.sandbox)
      : clone(summary.sandbox);
  } else if (stored.sandbox != null) {
    merged.sandbox = clone(stored.sandbox);
  }
  if (stored.sandboxOverride) {
    merged.sandboxOverride = true;
  }

  if (Number.isFinite(summary.contextWindow) && summary.contextWindow > 0) {
    merged.contextWindow = summary.contextWindow;
  } else if (Number.isFinite(stored.contextWindow) && stored.contextWindow > 0) {
    merged.contextWindow = stored.contextWindow;
  }

  if (summary.lastTokenUsage != null) {
    merged.lastTokenUsage = clone(summary.lastTokenUsage);
  } else if (stored.lastTokenUsage != null) {
    merged.lastTokenUsage = clone(stored.lastTokenUsage);
  }

  if (summary.totalTokenUsage != null) {
    merged.totalTokenUsage = clone(summary.totalTokenUsage);
  } else if (stored.totalTokenUsage != null) {
    merged.totalTokenUsage = clone(stored.totalTokenUsage);
  }

  if (summary.reasoningEffort != null && !(typeof summary.reasoningEffort === 'string' && summary.reasoningEffort.trim() === '')) {
    merged.reasoningEffort = clone(summary.reasoningEffort);
  } else if (stored.reasoningEffort != null) {
    merged.reasoningEffort = clone(stored.reasoningEffort);
  }

  return merged;
}

function canonicalizeSandboxType(value) {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  const candidate = trimmed.includes('-')
    ? trimmed.split('-').map((part, index) => (index === 0 ? part : `${part.slice(0, 1).toUpperCase()}${part.slice(1)}`)).join('')
    : trimmed;
  return SANDBOX_POLICY_TYPES.has(candidate) ? candidate : null;
}

function toSandboxMode(value) {
  const type = typeof value === 'string'
    ? canonicalizeSandboxType(value)
    : canonicalizeSandboxType(value?.type);
  if (!type || !SANDBOX_MODE_TYPES.has(type)) return null;
  return type.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
}

function normalizeSandboxPolicy(value) {
  if (value == null) return null;

  if (typeof value === 'string') {
    switch (canonicalizeSandboxType(value)) {
      case 'dangerFullAccess':
        return { type: 'dangerFullAccess' };
      case 'readOnly':
        return { type: 'readOnly', networkAccess: false };
      case 'workspaceWrite':
        return {
          type: 'workspaceWrite',
          writableRoots: [],
          networkAccess: false,
          excludeTmpdirEnvVar: false,
          excludeSlashTmp: false,
        };
      default:
        return null;
    }
  }

  if (typeof value !== 'object') return null;
  const type = canonicalizeSandboxType(value.type);
  if (!type) return null;

  const next = clone(value);
  next.type = type;

  switch (type) {
    case 'dangerFullAccess':
      return { type };
    case 'readOnly':
      return {
        ...next,
        networkAccess: Boolean(next.networkAccess),
      };
    case 'workspaceWrite':
      return {
        ...next,
        writableRoots: Array.isArray(next.writableRoots) ? clone(next.writableRoots) : [],
        networkAccess: Boolean(next.networkAccess),
        excludeTmpdirEnvVar: Boolean(next.excludeTmpdirEnvVar),
        excludeSlashTmp: Boolean(next.excludeSlashTmp),
      };
    case 'externalSandbox':
      return next.networkAccess != null ? next : null;
    default:
      return null;
  }
}

function normalizePermissionSelection(value) {
  if (!value || typeof value !== 'object') return null;
  const type = typeof value.type === 'string' ? value.type.trim() : '';
  if (type && type !== 'profile') return null;
  const id = typeof value.id === 'string' ? value.id.trim() : '';
  if (!id) return null;
  const modifications = Array.isArray(value.modifications)
    ? value.modifications.filter(Boolean)
    : [];
  return {
    type: 'profile',
    id,
    ...(modifications.length > 0 ? { modifications: clone(modifications) } : {}),
  };
}

function normalizeThreadSummary(thread, backend = 'mock') {
  if (!thread) return null;
  const envelope = thread ?? {};
  const raw = envelope.thread ?? envelope;
  const id = raw.id ?? raw.threadId ?? raw.sessionId;
  const approvalPolicy = envelope.approvalPolicy ?? raw.approvalPolicy;
  const permissions = normalizePermissionSelection(envelope.activePermissionProfile ?? raw.activePermissionProfile);
  const sandbox = normalizeSandboxPolicy(envelope.sandbox ?? raw.sandbox ?? raw.sandboxPolicy);
  const tokenUsage = envelope.tokenUsage ?? raw.tokenUsage ?? null;
  const reasoningEffort = envelope.reasoningEffort ?? raw.reasoningEffort ?? null;
  const historyUpdatedAt = inferThreadHistoryUpdatedAt(raw);
  return {
    session_id: id,
    thread_id: id,
    title: raw.name ?? raw.title ?? raw.preview ?? '',
    cwd: envelope.cwd ?? raw.cwd ?? raw.path ?? '',
    model: envelope.model ?? raw.model ?? '',
    preview: raw.preview ?? '',
    active: raw.status ? String(raw.status) === 'active' || String(raw.status) === 'running' : true,
    backend,
    createdAt: raw.createdAt ? new Date(raw.createdAt * 1000).toISOString() : nowIso(),
    updatedAt: historyUpdatedAt ?? (raw.updatedAt ? new Date(raw.updatedAt * 1000).toISOString() : nowIso()),
    ...(approvalPolicy != null ? { approvalPolicy } : {}),
    ...(permissions != null ? { permissions } : {}),
    ...(sandbox != null ? { sandbox } : {}),
    ...(tokenUsage?.modelContextWindow != null ? { contextWindow: tokenUsage.modelContextWindow } : {}),
    ...(tokenUsage?.last ? { lastTokenUsage: clone(tokenUsage.last) } : {}),
    ...(tokenUsage?.total ? { totalTokenUsage: clone(tokenUsage.total) } : {}),
    ...(firstNonEmptyString(reasoningEffort ?? '') ? { reasoningEffort } : {}),
    thread: clone(raw),
  };
}

function inferThreadHistoryUpdatedAt(thread) {
  const turns = Array.isArray(thread?.turns) ? thread.turns : [];
  let latestSeconds = 0;
  for (const turn of turns) {
    const candidates = [turn?.completedAt, turn?.startedAt, turn?.createdAt, turn?.updatedAt];
    for (const value of candidates) {
      if (Number.isFinite(value) && value > latestSeconds) {
        latestSeconds = value;
      }
    }
  }
  return latestSeconds > 0 ? new Date(latestSeconds * 1000).toISOString() : null;
}

function getThreadStatusType(thread) {
  const status = thread?.status;
  if (!status) return null;
  return typeof status === 'string' ? status : status.type ?? null;
}

function hasMeaningfulSessionEvents(session) {
  const events = Array.isArray(session?.events) ? session.events : [];
  return events.some((event) => {
    const name = String(event?.event ?? '');
    return name !== 'thread/started'
      && name !== 'session/changed'
      && name !== 'thread/tokenUsage/updated'
      && name !== 'thread/status/changed'
      && name !== 'warning'
      && name !== 'error';
  });
}

function extractUserMessageText(content = []) {
  return content
    .filter((item) => item?.type === 'text' && typeof item.text === 'string')
    .map((item) => item.text.trim())
    .filter(Boolean)
    .join('\n');
}

function safeJsonLineParse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function isThreadNotFoundError(error) {
  const message = String(error?.message ?? '');
  return /thread not found/i.test(message);
}

function normalizeTokenUsageSnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== 'object') return null;

  const normalized = {
    totalTokens: Number(snapshot.totalTokens ?? snapshot.total_tokens ?? 0),
    inputTokens: Number(snapshot.inputTokens ?? snapshot.input_tokens ?? 0),
    cachedInputTokens: Number(snapshot.cachedInputTokens ?? snapshot.cached_input_tokens ?? 0),
    outputTokens: Number(snapshot.outputTokens ?? snapshot.output_tokens ?? 0),
    reasoningOutputTokens: Number(snapshot.reasoningOutputTokens ?? snapshot.reasoning_output_tokens ?? 0),
  };

  if (!Object.values(normalized).some((value) => Number.isFinite(value) && value > 0)) {
    return null;
  }

  return normalized;
}

function hasMissingSessionMetadata(session) {
  if (!session) return false;
  return !firstNonEmptyString(session.model)
    || !firstNonEmptyString(session.approvalPolicy)
    || session.sandbox == null
    || !Number.isFinite(session.contextWindow)
    || session.contextWindow <= 0;
}

function mergeMetadataIntoSession(session, metadata = {}) {
  if (!session) return null;
  const next = {
    ...clone(session),
  };

  if (firstNonEmptyString(metadata.model) && !firstNonEmptyString(next.model)) {
    next.model = metadata.model;
  }

  if (firstNonEmptyString(metadata.approvalPolicy) && !firstNonEmptyString(next.approvalPolicy)) {
    next.approvalPolicy = metadata.approvalPolicy;
  }

  if (metadata.permissions != null && next.permissions == null) {
    next.permissions = clone(metadata.permissions);
  }

  if (metadata.sandbox != null && next.sandbox == null) {
    next.sandbox = clone(metadata.sandbox);
  }

  if (Number.isFinite(metadata.contextWindow) && metadata.contextWindow > 0 && (!Number.isFinite(next.contextWindow) || next.contextWindow <= 0)) {
    next.contextWindow = metadata.contextWindow;
  }

  if (metadata.lastTokenUsage != null && next.lastTokenUsage == null) {
    next.lastTokenUsage = clone(metadata.lastTokenUsage);
  }

  if (metadata.totalTokenUsage != null && next.totalTokenUsage == null) {
    next.totalTokenUsage = clone(metadata.totalTokenUsage);
  }

  if (firstNonEmptyString(metadata.reasoningEffort) && !firstNonEmptyString(next.reasoningEffort)) {
    next.reasoningEffort = metadata.reasoningEffort;
  }

  return next;
}

function getPayloadItemId(payload = {}) {
  return firstNonEmptyString(payload.itemId, payload.item_id);
}

function getTurnInputItemId({ payload = {}, requestId = '', turnId = '', sessionId = '', seq = '' } = {}) {
  return firstNonEmptyString(
    getPayloadItemId(payload),
    requestId ? `input_${requestId}` : '',
    turnId ? `stored_${turnId}_user_${seq}` : '',
    sessionId ? `stored_${sessionId}_user_${seq}` : '',
  );
}

function defaultSandboxPolicy() {
  return { type: 'dangerFullAccess' };
}

export class BridgeServer extends EventEmitter {
  constructor({
    backend,
    store,
    host = '127.0.0.1',
    port = 8787,
    token = null,
    name = 'codex-remote-control',
    version = '0.1.0',
  }) {
    super();
    this.backend = backend;
    this.store = store;
    this.host = host;
    this.port = port;
    this.token = token;
    this.name = name;
    this.version = version;
    this.httpServer = null;
    this.wsServer = null;
    this.connections = new Set();
    this.backendListener = (message) => this.#handleBackendMessage(message);
  }

  async start() {
    await this.store.load();
    await this.backend.start();
    this.backend.on('message', this.backendListener);

    this.httpServer = http.createServer((req, res) => this.#handleHttp(req, res));
    this.wsServer = new WebSocketServer({ server: this.httpServer });
    this.wsServer.on('connection', (ws, req) => this.#handleConnection(ws, req));

    await new Promise((resolve) => {
      this.httpServer.listen(this.port, this.host, resolve);
    });

    return this;
  }

  async stop() {
    this.backend.off('message', this.backendListener);
    for (const ws of this.connections) {
      try {
        ws.close(1001, 'server shutting down');
      } catch {}
    }
    this.connections.clear();
    if (this.wsServer) {
      await new Promise((resolve) => this.wsServer.close(resolve));
    }
    if (this.httpServer) {
      await new Promise((resolve) => this.httpServer.close(resolve));
    }
    await this.backend.stop();
  }

  address() {
    const addr = this.httpServer?.address();
    if (typeof addr === 'object' && addr) {
      return { host: addr.address, port: addr.port };
    }
    return { host: this.host, port: this.port };
  }

  #handleHttp(req, res) {
    if (req.url === '/healthz') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ ok: true, name: this.name, version: this.version }));
      return;
    }
    if (req.url === '/v1/sessions' && req.method === 'GET') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ sessions: this.store.listSessions() }));
      return;
    }
    res.writeHead(404, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ ok: false, error: 'not_found' }));
  }

  async #handleConnection(ws, req) {
    const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
    const providedToken = url.searchParams.get('token') ?? req.headers['x-bridge-token'];
    if (this.token && providedToken !== this.token) {
      ws.close(1008, 'unauthorized');
      return;
    }

    this.connections.add(ws);
    ws.send(JSON.stringify({
      type: 'hello',
      ok: true,
      payload: {
        name: this.name,
        version: this.version,
        server_time: nowIso(),
      },
    }));

    ws.on('message', async (buffer) => {
      const raw = buffer.toString('utf8');
      const parsed = safeJson(raw);
      if (!parsed.ok) {
        ws.send(JSON.stringify(createError(null, 'bad_json', 'invalid JSON', { raw })));
        return;
      }
      try {
        await this.#handleClientMessage(ws, parsed.value);
      } catch (error) {
        ws.send(JSON.stringify(createError(parsed.value?.id ?? null, 'internal_error', error?.message ?? 'internal error')));
      }
    });

    ws.on('close', () => {
      this.connections.delete(ws);
    });
  }

  async #handleClientMessage(ws, message) {
    const type = message?.type;
    const id = message?.id ?? randomUUID();
    const payload = message?.payload ?? {};

    switch (type) {
      case 'ping':
        ws.send(JSON.stringify(createResponse(id, { pong: true }, 'pong')));
        return;
      case 'session.list': {
        const backendThreads = await this.backend.listThreads(payload);
        const sessions = backendThreads.map((thread) => {
          const summary = normalizeThreadSummary(thread, this.backend.constructor.name.replace('Backend', '').toLowerCase() || 'mock');
          const stored = summary?.session_id ? this.store.getSession(summary.session_id) : null;
          return mergeSessionSummary(summary, stored);
        });
        for (const session of sessions) {
          await this.store.upsertSession(session);
        }
        ws.send(JSON.stringify(createResponse(id, { sessions })));
        return;
      }
      case 'model.list': {
        const listed = typeof this.backend.listModels === 'function'
          ? await this.backend.listModels(payload)
          : { data: [], nextCursor: null };
        const data = Array.isArray(listed) ? listed : listed?.data ?? [];
        const nextCursor = Array.isArray(listed) ? null : listed?.nextCursor ?? null;
        ws.send(JSON.stringify(createResponse(id, { data, nextCursor })));
        return;
      }
      case 'session.start': {
        const started = await this.backend.startThread({
          cwd: payload.cwd ?? null,
          model: payload.model ?? null,
          approvalPolicy: payload.approvalPolicy ?? null,
          approvalsReviewer: payload.approvalsReviewer ?? null,
          sandbox: payload.sandbox ?? null,
          permissions: normalizePermissionSelection(payload.permissions),
          config: payload.config ?? null,
          serviceName: payload.serviceName ?? null,
          baseInstructions: payload.baseInstructions ?? null,
          developerInstructions: payload.developerInstructions ?? null,
          personality: payload.personality ?? null,
          ephemeral: payload.ephemeral ?? null,
          sessionStartSource: payload.sessionStartSource ?? null,
          threadSource: payload.threadSource ?? null,
          environments: payload.environments ?? null,
          dynamicTools: payload.dynamicTools ?? null,
          mockExperimentalField: payload.mockExperimentalField ?? null,
          experimentalRawEvents: payload.experimentalRawEvents ?? true,
          persistExtendedHistory: payload.persistExtendedHistory ?? false,
        });
        const session = normalizeThreadSummary(started, this.backend.constructor.name.replace('Backend', '').toLowerCase() || 'mock');
        await this.store.upsertSession(session, { touch: true });
        ws.send(JSON.stringify(createResponse(id, { session })));
        return;
      }
      case 'session.resume': {
        const resumed = await this.backend.resumeThread(payload.session_id ?? payload.thread_id, {
          threadId: payload.thread_id ?? payload.session_id,
          history: payload.history ?? null,
          path: payload.path ?? null,
          model: payload.model ?? null,
          modelProvider: payload.modelProvider ?? null,
          serviceTier: payload.serviceTier ?? null,
          cwd: payload.cwd ?? null,
          approvalPolicy: payload.approvalPolicy ?? null,
          approvalsReviewer: payload.approvalsReviewer ?? null,
          sandbox: payload.sandbox ?? null,
          permissions: normalizePermissionSelection(payload.permissions),
          config: payload.config ?? null,
          baseInstructions: payload.baseInstructions ?? null,
          developerInstructions: payload.developerInstructions ?? null,
          personality: payload.personality ?? null,
          excludeTurns: payload.excludeTurns ?? null,
          persistExtendedHistory: payload.persistExtendedHistory ?? false,
        });
        const session = normalizeThreadSummary(resumed, this.backend.constructor.name.replace('Backend', '').toLowerCase() || 'mock');
        await this.store.upsertSession(session);
        ws.send(JSON.stringify(createResponse(id, { session })));
        return;
      }
      case 'session.update': {
        const sessionId = payload.session_id ?? payload.thread_id;
        const current = this.store.getSession(sessionId);
        if (!current) {
          ws.send(JSON.stringify(createError(id, 'not_found', `unknown session: ${sessionId}`)));
          return;
        }
        const patch = {
          session_id: current.session_id,
        };
        if (Object.prototype.hasOwnProperty.call(payload, 'approvalPolicy')) {
          patch.approvalPolicy = payload.approvalPolicy;
        }
        if (Object.prototype.hasOwnProperty.call(payload, 'permissions')) {
          patch.permissions = normalizePermissionSelection(payload.permissions);
        }
        if (Object.prototype.hasOwnProperty.call(payload, 'sandbox') || Object.prototype.hasOwnProperty.call(payload, 'sandboxPolicy')) {
          const normalizedSandbox = normalizeSandboxPolicy(
            Object.prototype.hasOwnProperty.call(payload, 'sandbox') ? payload.sandbox : payload.sandboxPolicy,
          );
          if (normalizedSandbox == null) {
            ws.send(JSON.stringify(createError(id, 'invalid_request', 'invalid sandbox')));
            return;
          }
          patch.sandbox = normalizedSandbox;
          patch.sandboxOverride = true;
        }
        const session = await this.store.upsertSession(patch, { touch: true });
        this.#emitSessionChanged(sessionId);
        ws.send(JSON.stringify(createResponse(id, { session })));
        return;
      }
      case 'turn.send': {
        const sessionId = payload.session_id ?? payload.thread_id;
        const inputItemId = getTurnInputItemId({ payload, requestId: id, sessionId });
        const inputEvent = createEvent('turn/input', {
          session_id: sessionId,
          thread_id: sessionId,
          request_id: id,
          payload: {
            itemId: inputItemId,
            item_id: inputItemId,
            text: payload.text ?? '',
            input: payload.input ?? null,
          },
        });
        let session = this.store.getSession(sessionId);
        if (!session) {
          await this.store.upsertSession({
            session_id: sessionId,
            thread_id: sessionId,
            title: payload.title ?? '',
            cwd: payload.cwd ?? '',
            model: payload.model ?? '',
            backend: this.backend.constructor.name.replace('Backend', '').toLowerCase() || 'mock',
            preview: payload.text ?? '',
            active: true,
          });
          session = this.store.getSession(sessionId);
        }
        const storedInputEvent = await this.store.appendEvent(sessionId, inputEvent);
        this.#broadcast(storedInputEvent);
        const requestedPermissions = normalizePermissionSelection(payload.permissions);
        const requestedSandboxPolicy = Object.prototype.hasOwnProperty.call(payload, 'sandboxPolicy')
          ? normalizeSandboxPolicy(payload.sandboxPolicy)
          : normalizeSandboxPolicy(session?.sandbox ?? null) ?? defaultSandboxPolicy();
        const turnParams = {
          text: payload.text ?? '',
          input: payload.input ?? null,
          responsesapiClientMetadata: payload.responsesapiClientMetadata ?? null,
          environments: payload.environments ?? null,
          cwd: payload.cwd ?? null,
          approvalPolicy: payload.approvalPolicy ?? null,
          approvalsReviewer: payload.approvalsReviewer ?? null,
          sandboxPolicy: requestedSandboxPolicy,
          permissions: requestedPermissions,
          model: payload.model ?? null,
          serviceTier: payload.serviceTier ?? null,
          effort: payload.effort ?? null,
          summary: payload.summary ?? null,
          personality: payload.personality ?? null,
          outputSchema: payload.outputSchema ?? null,
          collaborationMode: payload.collaborationMode ?? null,
          turnId: payload.turn_id ?? null,
        };
        let ensured = await this.#ensureSessionLoaded(sessionId, {
          model: turnParams.model,
          cwd: turnParams.cwd,
          approvalPolicy: turnParams.approvalPolicy,
          approvalsReviewer: turnParams.approvalsReviewer,
          sandbox: requestedSandboxPolicy,
          permissions: turnParams.permissions,
          personality: turnParams.personality,
          excludeTurns: true,
        });
        let targetThreadId = ensured?.session?.thread_id ?? session?.thread_id ?? sessionId;
        let turn;
        try {
          turn = await this.backend.startTurn(targetThreadId, turnParams);
        } catch (error) {
          if (!isThreadNotFoundError(error)) throw error;
          ensured = await this.#resumeStoredSession(sessionId, {
            model: turnParams.model,
            cwd: turnParams.cwd,
            approvalPolicy: turnParams.approvalPolicy,
            approvalsReviewer: turnParams.approvalsReviewer,
            sandbox: requestedSandboxPolicy,
            permissions: turnParams.permissions,
            personality: turnParams.personality,
            excludeTurns: true,
          });
          targetThreadId = ensured?.session?.thread_id ?? targetThreadId;
          turn = await this.backend.startTurn(targetThreadId, turnParams);
        }
        if (turn?.id) {
          await this.store.attachTurnIdToRequestEvent(sessionId, id, turn.id);
        }
        this.#emitSessionChanged(sessionId);
        ws.send(JSON.stringify(createResponse(id, { turn, session_id: sessionId })));
        return;
      }
      case 'turn.interrupt': {
        await this.backend.interruptTurn(payload.session_id ?? payload.thread_id, payload.turn_id ?? payload.turnId);
        ws.send(JSON.stringify(createResponse(id, { ok: true })));
        return;
      }
      case 'approval.response': {
        await this.backend.respondRequest(
          payload.request_id ?? payload.requestId,
          payload.result ?? this.#compileApprovalResult(payload),
          payload.error ?? null,
        );
        await this.store.resolveApproval(payload.session_id ?? payload.thread_id, payload.request_id ?? payload.requestId);
        this.#emitSessionChanged(payload.session_id ?? payload.thread_id);
        ws.send(JSON.stringify(createResponse(id, { ok: true })));
        return;
      }
      case 'session.sync': {
        const sessionId = payload.session_id ?? payload.thread_id;
        const sinceSeq = payload.since_seq ?? 0;
        if (sinceSeq > 0) {
          ws.send(JSON.stringify(createResponse(id, {
            session_id: sessionId,
            events: this.#filterIncrementalSessionEvents(sessionId, sinceSeq),
            last_seq: this.store.getLastSeq(sessionId),
          })));
          return;
        }

        const refreshed = await this.#refreshSessionHistory(sessionId);
        ws.send(JSON.stringify(createResponse(id, {
          session_id: sessionId,
          events: refreshed.events,
          last_seq: refreshed.lastSeq,
        })));
        return;
      }
      case 'session.content': {
        const sessionId = payload.session_id ?? payload.thread_id;
        const content = await this.#buildSessionContent(sessionId);
        ws.send(JSON.stringify(createResponse(id, content)));
        return;
      }
      case 'file.read': {
        const sessionId = payload.session_id ?? payload.thread_id;
        const requestedPath = String(payload.path ?? '').trim();
        const maxBytes = clampMaxBytes(payload.max_bytes);
        if (!requestedPath) {
          ws.send(JSON.stringify(createError(id, 'invalid_path', 'path is required')));
          return;
        }
        const result = await this.#readSessionFile(sessionId, requestedPath, maxBytes);
        ws.send(JSON.stringify(createResponse(id, {
          session_id: sessionId,
          path: requestedPath,
          resolved_path: result.resolvedPath,
          content: result.content,
          truncated: result.truncated,
          bytes: result.bytes,
        })));
        return;
      }
      default:
        ws.send(JSON.stringify(createError(id, 'unknown_message', `unknown message type: ${type}`)));
    }
  }

  async #handleBackendMessage(message) {
    if (message?.type === 'notification') {
      const payload = message.params ?? {};
      const threadId = payload.threadId ?? payload.thread_id ?? payload.thread?.id ?? payload.thread?.threadId ?? payload.thread?.sessionId ?? null;
      const turnId = payload.turnId ?? payload.turn_id ?? payload.turn?.id ?? null;
      const sessionId = threadId ?? payload.sessionId ?? payload.session_id ?? null;
      const event = createEvent(message.method, {
        session_id: sessionId,
        thread_id: threadId,
        turn_id: turnId,
        payload,
      });
      let broadcastEvent = event;
      if (sessionId) {
        let session = this.store.getSession(sessionId);
        if (session) {
          if (message.method === 'thread/tokenUsage/updated' && payload.tokenUsage) {
            session = await this.store.upsertSession({
              ...session,
              contextWindow: payload.tokenUsage.modelContextWindow ?? session.contextWindow,
              lastTokenUsage: payload.tokenUsage.last ?? session.lastTokenUsage ?? null,
              totalTokenUsage: payload.tokenUsage.total ?? session.totalTokenUsage ?? null,
            });
          }
          broadcastEvent = await this.store.appendEvent(sessionId, event);
        } else {
          await this.store.upsertSession({
            session_id: sessionId,
            thread_id: threadId ?? sessionId,
            title: payload.thread?.name ?? payload.thread?.preview ?? '',
            cwd: payload.thread?.cwd ?? '',
            model: payload.thread?.model ?? '',
            backend: this.backend.constructor.name.replace('Backend', '').toLowerCase() || 'mock',
            preview: payload.thread?.preview ?? '',
            active: true,
            thread: payload.thread ?? null,
          }, { touch: true });
          broadcastEvent = await this.store.appendEvent(sessionId, event);
        }
      }
      this.#broadcast(broadcastEvent);
      if (this.#shouldEmitSessionChanged(message.method, payload)) {
        this.#emitSessionChanged(sessionId);
      }
      return;
    }

    if (message?.type === 'request') {
      const payload = message.params ?? {};
      const threadId = payload.threadId ?? payload.thread_id ?? null;
      const turnId = payload.turnId ?? payload.turn_id ?? null;
      const event = createEvent(message.method, {
        session_id: threadId,
        thread_id: threadId,
        turn_id: turnId,
        request_id: message.id,
        payload,
      });
      let broadcastEvent = event;
      if (threadId) {
        const session = this.store.getSession(threadId);
        if (!session) {
          await this.store.upsertSession({
            session_id: threadId,
            thread_id: threadId,
            title: payload.thread?.name ?? '',
            cwd: payload.cwd?.path ?? payload.cwd ?? '',
            model: '',
            backend: this.backend.constructor.name.replace('Backend', '').toLowerCase() || 'mock',
            preview: '',
            active: true,
          });
        }
        await this.store.recordPendingApproval(threadId, {
          request_id: message.id,
          request_method: message.method,
          thread_id: threadId,
          turn_id: turnId,
          payload,
          at: nowIso(),
        });
        broadcastEvent = await this.store.appendEvent(threadId, event);
      }
      this.#broadcast(broadcastEvent);
      if (this.#shouldEmitSessionChanged(message.method, payload)) {
        this.#emitSessionChanged(threadId);
      }
      return;
    }

    if (message?.type === 'response') {
      return;
    }
  }

  #broadcast(message) {
    const data = JSON.stringify(message);
    for (const ws of this.connections) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(data);
      }
    }
  }

  #emitSessionChanged(sessionId) {
    if (!sessionId) return;
    const session = this.store.getSession(sessionId);
    this.#broadcast(createEvent('session/changed', {
      session_id: sessionId,
      thread_id: session?.thread_id ?? sessionId,
      payload: {
        last_seq: this.store.getLastSeq(sessionId),
        updated_at: session?.updatedAt ?? nowIso(),
      },
    }));
  }

  #shouldEmitSessionChanged(eventName, payload = {}) {
    if (eventName === 'item/completed') {
      const itemType = payload?.item?.type ?? '';
      return itemType === 'agentMessage' || itemType === 'fileChange';
    }
    return eventName === 'thread/started'
      || eventName === 'turn/started'
      || eventName === 'turn/completed'
      || eventName === 'item/commandExecution/requestApproval'
      || eventName === 'item/fileChange/requestApproval'
      || eventName === 'item/permissions/requestApproval'
      || eventName === 'applyPatchApproval'
      || eventName === 'execCommandApproval';
  }

  #compileApprovalResult(payload) {
    if (payload.decision !== undefined || payload.permissions !== undefined) {
      const result = {};
      if (payload.decision !== undefined) result.decision = payload.decision;
      if (payload.permissions !== undefined) result.permissions = payload.permissions;
      if (payload.scope !== undefined) result.scope = payload.scope;
      if (payload.strictAutoReview !== undefined) result.strictAutoReview = payload.strictAutoReview;
      return result;
    }
    return payload.result ?? payload;
  }

  async #ensureSessionLoaded(sessionId, params = {}) {
    const session = this.store.getSession(sessionId);
    if (!session) return null;
    if (getThreadStatusType(session.thread) !== 'notLoaded') {
      return { session, resumed: null };
    }
    return this.#resumeStoredSession(sessionId, params);
  }

  async #resumeStoredSession(sessionId, params = {}) {
    const session = this.store.getSession(sessionId);
    if (!session) return null;
    const explicitPath = Object.prototype.hasOwnProperty.call(params, 'path') ? params.path : null;
    const requestedSandbox = Object.prototype.hasOwnProperty.call(params, 'sandbox')
      ? params.sandbox
      : session.sandbox ?? defaultSandboxPolicy();
    const resumed = await this.backend.resumeThread(session.thread_id ?? sessionId, {
      threadId: session.thread_id ?? sessionId,
      path: explicitPath,
      model: params.model ?? session.model ?? null,
      cwd: params.cwd ?? session.cwd ?? null,
      approvalPolicy: params.approvalPolicy ?? null,
      approvalsReviewer: params.approvalsReviewer ?? null,
      sandbox: toSandboxMode(requestedSandbox),
      permissions: normalizePermissionSelection(params.permissions),
      personality: params.personality ?? null,
      excludeTurns: params.excludeTurns ?? null,
      persistExtendedHistory: false,
    });
    const normalized = normalizeThreadSummary(resumed, this.backend.constructor.name.replace('Backend', '').toLowerCase() || 'mock');
    await this.store.upsertSession(normalized);
    return { session: normalized, resumed };
  }

  async #refreshSessionHistory(sessionId) {
    const session = this.store.getSession(sessionId);
    if (!session) {
      return { events: [], lastSeq: 0 };
    }

    const storedEvents = this.store.syncEvents(sessionId, 0);
    try {
      const hydratedEvents = await this.#hydrateSessionHistory(sessionId);
      const effectiveHydratedEvents = this.#pruneHydratedEvents(hydratedEvents, storedEvents);
      if (effectiveHydratedEvents.length === 0) {
        return {
          events: storedEvents,
          lastSeq: this.store.getLastSeq(sessionId),
        };
      }

      const seen = new Set(storedEvents.map((event) => this.#eventSignature(event)));
      for (const event of effectiveHydratedEvents) {
        const signature = this.#eventSignature(event);
        if (seen.has(signature)) continue;
        const stored = await this.store.appendEvent(sessionId, event);
        storedEvents.push(stored);
        seen.add(signature);
      }

      return {
        events: this.#mergeInitialSessionEvents(effectiveHydratedEvents, storedEvents),
        lastSeq: this.store.getLastSeq(sessionId),
      };
    } catch {
      return {
        events: storedEvents,
        lastSeq: this.store.getLastSeq(sessionId),
      };
    }
  }

  async #backfillSessionMetadataFromLocalState(session) {
    if (!session || !hasMissingSessionMetadata(session)) {
      return session;
    }

    let next = mergeMetadataIntoSession(session, this.#extractSessionMetadataFromEvents(session));
    next = mergeMetadataIntoSession(next, await this.#extractSessionMetadataFromRollout(next));

    if (JSON.stringify(next) === JSON.stringify(session)) {
      return session;
    }

    return this.store.upsertSession(next);
  }

  #extractSessionMetadataFromEvents(session) {
    const metadata = {};
    const events = Array.isArray(session?.events) ? session.events : [];

    for (let index = events.length - 1; index >= 0; index -= 1) {
      const event = events[index];
      if (event?.event !== 'thread/tokenUsage/updated') continue;
      const tokenUsage = event?.payload?.tokenUsage;
      if (!tokenUsage || typeof tokenUsage !== 'object') continue;
      if (tokenUsage.modelContextWindow != null) {
        metadata.contextWindow = tokenUsage.modelContextWindow;
      }
      if (tokenUsage.last != null) {
        metadata.lastTokenUsage = tokenUsage.last;
      }
      if (tokenUsage.total != null) {
        metadata.totalTokenUsage = tokenUsage.total;
      }
      break;
    }

    return metadata;
  }

  async #extractSessionMetadataFromRollout(session) {
    const rolloutPath = session?.thread?.path;
    if (typeof rolloutPath !== 'string' || !rolloutPath.trim()) {
      return {};
    }

    try {
      const raw = await fs.readFile(rolloutPath, 'utf8');
      const metadata = {};
      const lines = raw.split('\n').map((line) => line.trim()).filter(Boolean);

      for (const line of lines) {
        const record = safeJsonLineParse(line);
        if (!record || typeof record !== 'object') continue;
        const payload = record.payload ?? {};

        if (record.type === 'session_meta' && firstNonEmptyString(payload.model)) {
          metadata.model = payload.model;
        }

        if (record.type === 'turn_context') {
          if (firstNonEmptyString(payload.model) && !firstNonEmptyString(metadata.model)) {
            metadata.model = payload.model;
          }
          if (firstNonEmptyString(payload.effort)) {
            metadata.reasoningEffort = payload.effort;
          }
          if (firstNonEmptyString(payload.approval_policy)) {
            metadata.approvalPolicy = payload.approval_policy;
          }
          if (payload.sandbox_policy != null) {
            metadata.sandbox = clone(payload.sandbox_policy);
          }
          if (payload.permission_profile != null) {
            metadata.permissions = clone(payload.permission_profile);
          }
          if (Number.isFinite(payload.model_context_window) && payload.model_context_window > 0) {
            metadata.contextWindow = payload.model_context_window;
          }
        }

        if (record.type === 'event_msg' && payload.type === 'task_started') {
          if (Number.isFinite(payload.model_context_window) && payload.model_context_window > 0) {
            metadata.contextWindow = payload.model_context_window;
          }
        }

        if (record.type === 'event_msg' && payload.type === 'token_count') {
          const info = payload.info ?? {};
          if (Number.isFinite(info.model_context_window) && info.model_context_window > 0) {
            metadata.contextWindow = info.model_context_window;
          }
          const lastTokenUsage = normalizeTokenUsageSnapshot(info.last_token_usage ?? info.lastTokenUsage ?? null);
          if (lastTokenUsage != null) {
            metadata.lastTokenUsage = lastTokenUsage;
          }
          const totalTokenUsage = normalizeTokenUsageSnapshot(info.total_token_usage ?? info.totalTokenUsage ?? null);
          if (totalTokenUsage != null) {
            metadata.totalTokenUsage = totalTokenUsage;
          }
        }
      }

      return metadata;
    } catch {
      return {};
    }
  }

  async #buildSessionContent(sessionId) {
    let session = this.store.getSession(sessionId);
    if (!session) {
      return {
        session_id: sessionId,
        thread_id: sessionId,
        entries: [],
        pending_approvals: [],
      };
    }

    session = await this.#backfillSessionMetadataFromLocalState(session);

    let thread = null;
    try {
      thread = await this.#loadHydrationThread(sessionId, session);
      session = this.store.getSession(sessionId) ?? session;
      session = await this.#backfillSessionMetadataFromLocalState(session);
    } catch {
      thread = session.thread ?? null;
    }

    let entries = await this.#buildContentEntriesFromThread(sessionId, thread);
    if (entries.length === 0) {
      entries = this.#buildContentEntriesFromStoredEvents(sessionId);
    }
    if (entries.length === 0 && session.thread?.path) {
      try {
        entries = await this.#buildContentEntriesFromRolloutFile(sessionId, session.thread.path, session.thread_id ?? sessionId);
      } catch {}
    }

    return {
      session_id: sessionId,
      thread_id: session.thread_id ?? sessionId,
      session,
      entries,
      active_turns: this.#buildActiveTurns(sessionId, entries),
      pending_approvals: this.store.getPendingApprovals(sessionId),
    };
  }

  #buildActiveTurns(sessionId, entries = []) {
    const events = this.store.syncEvents(sessionId, 0);
    const turns = new Map();
    for (const event of events) {
      const turnId = event?.turn_id ?? '';
      if (!turnId) continue;
      if (event.event === 'turn/started') {
        turns.set(turnId, {
          turn_id: turnId,
          thread_id: event.thread_id ?? '',
          started_at: event.at ?? null,
          last_event: event.event,
          output_chars: 0,
        });
      } else if (event.event === 'turn/completed') {
        turns.delete(turnId);
      } else if (turns.has(turnId)) {
        const detail = this.#activeTurnDetailFromEvent(event);
        const outputCharsDelta = detail.output_chars_delta ?? 0;
        delete detail.output_chars_delta;
        turns.set(turnId, {
          ...turns.get(turnId),
          last_event: event.event,
          last_item_type: this.#activeTurnItemTypeFromEvent(event) ?? turns.get(turnId)?.last_item_type ?? null,
          ...detail,
          output_chars: (turns.get(turnId)?.output_chars ?? 0) + outputCharsDelta,
        });
      }
    }

    const lastItemsByTurn = new Map();
    for (const entry of entries) {
      const turnId = entry?.turn_id ?? '';
      if (turnId && entry?.item?.type) {
        lastItemsByTurn.set(turnId, entry.item.type);
      }
    }

    return Array.from(turns.values()).map((turn) => ({
      turn_id: turn.turn_id,
      thread_id: turn.thread_id,
      started_at: turn.started_at,
      last_event: turn.last_event,
      last_item_type: turn.last_item_type ?? lastItemsByTurn.get(turn.turn_id) ?? null,
      detail: turn.detail ?? null,
      file_count: turn.file_count ?? null,
      diff_stats: turn.diff_stats ?? null,
      approval_type: turn.approval_type ?? null,
      output_chars: turn.output_chars ?? null,
    }));
  }

  #activeTurnItemTypeFromEvent(event) {
    if (event?.payload?.item?.type) return event.payload.item.type;
    if (event?.event === 'item/agentMessage/delta') return 'agentMessage';
    if (event?.event === 'item/fileChange/patchUpdated' || event?.event === 'turn/diff/updated') return 'fileChange';
    return null;
  }

  #activeTurnDetailFromEvent(event) {
    const payload = event?.payload ?? {};
    if (event?.event === 'item/agentMessage/delta') {
      return { output_chars_delta: String(payload.delta ?? payload.text ?? payload.message ?? '').length };
    }
    if (event?.event === 'item/commandExecution/requestApproval') {
      return {
        approval_type: 'command',
        detail: firstNonEmptyString(payload.command, payload.reason),
      };
    }
    if (event?.event === 'item/fileChange/requestApproval' || event?.event === 'applyPatchApproval') {
      const changes = payload.fileChanges?.changes ?? payload.fileChanges ?? null;
      const fileCount = Array.isArray(changes) ? changes.length : null;
      return {
        approval_type: 'fileChange',
        detail: fileCount ? `${fileCount} 个文件` : firstNonEmptyString(payload.reason),
        file_count: fileCount,
      };
    }
    if (event?.event === 'item/permissions/requestApproval') {
      return {
        approval_type: 'permission',
        detail: firstNonEmptyString(payload.grantRoot, payload.cwd, payload.reason),
      };
    }
    if (event?.event === 'execCommandApproval') {
      return {
        approval_type: 'command',
        detail: firstNonEmptyString(payload.command, payload.reason),
      };
    }
    if (event?.event === 'item/fileChange/patchUpdated') {
      const changes = Array.isArray(payload.changes) ? payload.changes : [];
      return {
        detail: changes.length ? `${changes.length} 个文件` : '',
        file_count: changes.length || null,
      };
    }
    if (event?.event === 'turn/diff/updated') {
      return {
        diff_stats: diffStatsLabel(payload.diff ?? ''),
      };
    }
    const item = payload.item ?? {};
    switch (item.type) {
      case 'commandExecution':
        return { detail: firstNonEmptyString(item.command, item.source) };
      case 'fileChange': {
        const changes = Array.isArray(item.changes) ? item.changes : [];
        return {
          detail: changes.length ? `${changes.length} 个文件` : '',
          file_count: changes.length || null,
        };
      }
      case 'mcpToolCall':
        return { detail: firstNonEmptyString(item.tool, item.server) };
      case 'dynamicToolCall':
        return { detail: [item.namespace, item.tool].filter(Boolean).join('.') };
      case 'collabAgentToolCall':
        return { detail: firstNonEmptyString(item.tool, item.prompt) };
      default:
        return {};
    }
  }

  #filterIncrementalSessionEvents(sessionId, sinceSeq) {
    const session = this.store.getSession(sessionId);
    if (!session) return [];

    const events = (session.events ?? []).slice().sort((left, right) => (left.seq ?? 0) - (right.seq ?? 0));
    if (events.length === 0) return [];

    const openTurnIds = new Set();
    for (const event of events) {
      if ((event.seq ?? 0) > sinceSeq) break;
      const turnId = event?.turn_id ?? '';
      if (!turnId) continue;
      if (event.event === 'turn/started') {
        openTurnIds.add(turnId);
      } else if (event.event === 'turn/completed') {
        openTurnIds.delete(turnId);
      }
    }

    const recentTurnIds = new Set();
    for (const event of events) {
      if ((event.seq ?? 0) <= sinceSeq) continue;
      if (event.event === 'turn/started' && event.turn_id) {
        recentTurnIds.add(event.turn_id);
      }
    }

    return events.filter((event) => {
      if ((event.seq ?? 0) <= sinceSeq) return false;
      const turnId = event?.turn_id ?? '';
      if (!turnId) return true;
      if (recentTurnIds.has(turnId)) return true;
      return openTurnIds.has(turnId);
    });
  }

  async #hydrateSessionHistory(sessionId) {
    const session = this.store.getSession(sessionId);
    if (!session) return [];

    try {
      const thread = await this.#loadHydrationThread(sessionId, session);
      const hydratedEvents = await this.#buildEventsFromThread(sessionId, thread);
      if (hydratedEvents.length > 0) {
        return hydratedEvents;
      }

      if (session.thread?.path) {
        const fromRollout = await this.#buildEventsFromRolloutFile(sessionId, session.thread.path, session.thread_id ?? sessionId);
        if (fromRollout.length > 0) {
          return fromRollout;
        }
      }

      return clone(session.events ?? []);
    } catch {
      return clone(session.events ?? []);
    }
  }

  async #buildEventsFromRolloutFile(sessionId, filePath, threadId = sessionId) {
    const raw = await fs.readFile(filePath, 'utf8');
    const lines = raw.split('\n').map((line) => line.trim()).filter(Boolean);
    const events = [];
    let currentTurnId = null;
    let currentItemSeq = 0;

    for (const line of lines) {
      const record = safeJsonLineParse(line);
      if (!record || typeof record !== 'object') continue;
      const payload = record.payload ?? {};
      if (record.type === 'event_msg') {
        switch (payload.type) {
          case 'task_started': {
            currentTurnId = payload.turn_id ?? currentTurnId;
            currentItemSeq = 0;
            if (currentTurnId) {
              events.push(createEvent('turn/started', {
                session_id: sessionId,
                thread_id: threadId,
                turn_id: currentTurnId,
                payload: { turn: { id: currentTurnId } },
              }));
            }
            break;
          }
          case 'user_message': {
            const text = String(payload.message ?? '').trim();
            if (text) {
              const itemId = currentTurnId ? `rollout_${currentTurnId}_user` : `rollout_${sessionId}_user`;
              events.push(createEvent('turn/input', {
                session_id: sessionId,
                thread_id: threadId,
                turn_id: currentTurnId,
                payload: {
                  itemId,
                  item_id: itemId,
                  text,
                  input: null,
                },
              }));
            }
            break;
          }
          case 'agent_message': {
            const text = String(payload.message ?? '').trim();
            if (text) {
              events.push(createEvent('item/completed', {
                session_id: sessionId,
                thread_id: threadId,
                turn_id: currentTurnId,
                payload: {
                  item: {
                    type: 'agentMessage',
                    id: currentTurnId ? `rollout_${currentTurnId}_${String(++currentItemSeq).padStart(4, '0')}` : `rollout_${sessionId}_${String(++currentItemSeq).padStart(4, '0')}`,
                    text,
                    phase: null,
                    memoryCitation: null,
                  },
                },
              }));
            }
            break;
          }
          case 'task_complete': {
            const completedTurnId = payload.turn_id ?? currentTurnId;
            if (completedTurnId) {
              events.push(createEvent('turn/completed', {
                session_id: sessionId,
                thread_id: threadId,
                turn_id: completedTurnId,
                payload: {
                  status: 'completed',
                  turn: {
                    id: completedTurnId,
                    status: 'completed',
                  },
                },
              }));
            }
            currentTurnId = null;
            break;
          }
          default:
            break;
        }
      }
    }

    return events;
  }

  async #buildContentEntriesFromRolloutFile(sessionId, filePath, threadId = sessionId) {
    const raw = await fs.readFile(filePath, 'utf8');
    const lines = raw.split('\n').map((line) => line.trim()).filter(Boolean);
    const entries = [];
    let currentTurnId = null;
    let currentItemSeq = 0;

    for (const line of lines) {
      const record = safeJsonLineParse(line);
      if (!record || typeof record !== 'object') continue;
      const payload = record.payload ?? {};
      if (record.type !== 'event_msg') continue;

      switch (payload.type) {
        case 'task_started':
          currentTurnId = payload.turn_id ?? currentTurnId;
          currentItemSeq = 0;
          break;
        case 'user_message': {
          const text = String(payload.message ?? '').trim();
          if (!text) break;
          entries.push({
            type: 'item',
            session_id: sessionId,
            thread_id: threadId,
            turn_id: currentTurnId,
            item: {
              type: 'userMessage',
              id: currentTurnId ? `rollout_${currentTurnId}_user` : `rollout_${sessionId}_user`,
              content: [{ type: 'text', text }],
            },
          });
          break;
        }
        case 'agent_message': {
          const text = String(payload.message ?? '').trim();
          if (!text) break;
          entries.push({
            type: 'item',
            session_id: sessionId,
            thread_id: threadId,
            turn_id: currentTurnId,
            item: {
              type: 'agentMessage',
              id: currentTurnId ? `rollout_${currentTurnId}_${String(++currentItemSeq).padStart(4, '0')}` : `rollout_${sessionId}_${String(++currentItemSeq).padStart(4, '0')}`,
              text,
              phase: null,
              memoryCitation: null,
            },
          });
          break;
        }
        default:
          break;
      }
    }

    return entries;
  }

  #buildContentEntriesFromStoredEvents(sessionId) {
    const session = this.store.getSession(sessionId);
    if (!session) return [];

    const events = this.store.syncEvents(sessionId, 0);
    const entries = [];
    const seenItemIds = new Set();
    const completedUserMessageSignatures = new Set();

    for (const event of events) {
      if (event?.event !== 'item/completed') continue;
      const item = event?.payload?.item;
      if (item?.type !== 'userMessage') continue;
      const text = extractUserMessageText(item.content ?? []);
      if (!text) continue;
      completedUserMessageSignatures.add(`${event.turn_id ?? ''}|${text}`);
    }

    for (const event of events) {
      if (event?.event === 'turn/input') {
        const text = String(event?.payload?.text ?? '').trim();
        if (!text) continue;
        if (completedUserMessageSignatures.has(`${event.turn_id ?? ''}|${text}`)) {
          continue;
        }
        const itemId = getTurnInputItemId({
          payload: event?.payload,
          requestId: event?.request_id ?? '',
          turnId: event?.turn_id ?? '',
          sessionId,
          seq: event?.seq ?? entries.length,
        });
        entries.push({
          type: 'item',
          session_id: sessionId,
          thread_id: session.thread_id ?? sessionId,
          turn_id: event.turn_id ?? null,
          item: {
            type: 'userMessage',
            id: itemId,
            content: [{ type: 'text', text }],
          },
        });
        continue;
      }

      if (event?.event !== 'item/completed') continue;
      const item = event?.payload?.item;
      if (!item?.type) continue;
      if (item.type !== 'userMessage' && item.type !== 'agentMessage' && item.type !== 'fileChange') continue;

      const itemId = item.id ?? `stored_item_${event.seq ?? entries.length}`;
      if (seenItemIds.has(itemId)) continue;
      seenItemIds.add(itemId);
      entries.push({
        type: 'item',
        session_id: sessionId,
        thread_id: session.thread_id ?? sessionId,
        turn_id: event.turn_id ?? null,
        item: {
          ...item,
          id: itemId,
        },
      });
    }

    return entries;
  }

  async #loadHydrationThread(sessionId, session) {
    const thread = session.thread ?? null;
    const rawThread = thread?.thread ?? thread;
    const status = getThreadStatusType(thread);
    const inlineTurns = Array.isArray(rawThread?.turns) ? rawThread.turns : [];
    const hasMeaningfulEvents = hasMeaningfulSessionEvents(session);
    const shouldSkipHydrationRead = thread && status !== 'notLoaded' && inlineTurns.length === 0 && !hasMeaningfulEvents;

    if (shouldSkipHydrationRead) {
      return thread;
    }

    if (typeof this.backend.readThread === 'function') {
      try {
        const read = await this.backend.readThread(session.thread_id ?? sessionId, {
          includeTurns: true,
        });
        const thread = read?.thread ?? read ?? null;
        if (thread) {
          const normalized = normalizeThreadSummary(thread, this.backend.constructor.name.replace('Backend', '').toLowerCase() || 'mock');
          if (normalized) {
            await this.store.upsertSession(mergeSessionSummary(normalized, this.store.getSession(sessionId)));
          }
          return thread;
        }
      } catch {}
    }

    if (thread && status !== 'notLoaded') {
      return thread;
    }

    const resumed = await this.#resumeStoredSession(sessionId, {
      model: session.model ?? null,
      cwd: session.cwd ?? null,
      excludeTurns: false,
    });
    return resumed?.resumed?.thread ?? resumed?.session?.thread ?? thread ?? null;
  }

  async #buildEventsFromThread(sessionId, thread) {
    const rawThread = thread?.thread ?? thread;
    const turns = this.#sortTurnsForRendering(Array.isArray(rawThread?.turns) ? rawThread.turns : []);
    const threadId = rawThread?.id ?? rawThread?.threadId ?? rawThread?.sessionId ?? sessionId;
    const storedUserInputItemIds = this.#buildStoredTurnInputItemIdMap(sessionId);
    const events = [];

    for (const turn of turns) {
      if (!turn?.id) continue;
      events.push(createEvent('turn/started', {
        session_id: sessionId,
        thread_id: threadId,
        turn_id: turn.id,
        payload: { turn: { id: turn.id } },
      }));

      const items = await this.#loadTurnItems(threadId, turn);
      let turnItemSeq = 0;
      for (const item of items) {
        if (item?.type === 'userMessage') {
          const text = extractUserMessageText(item.content ?? []);
          if (text) {
            const fallbackSeq = item.id ? null : String(++turnItemSeq).padStart(4, '0');
            const itemId = storedUserInputItemIds.get(`${turn.id}|${text}`)
              ?? item.id
              ?? `turn_${turn.id}_user_${fallbackSeq}`;
            events.push(createEvent('turn/input', {
              session_id: sessionId,
              thread_id: threadId,
              turn_id: turn.id,
              payload: {
                itemId,
                item_id: itemId,
                text,
                input: item.content ?? null,
              },
            }));
          }
          continue;
        }

        if (item?.type) {
          const itemId = item.id ?? `turn_${turn.id}_item_${String(++turnItemSeq).padStart(4, '0')}`;
          events.push(createEvent('item/completed', {
            session_id: sessionId,
            thread_id: threadId,
            turn_id: turn.id,
            payload: {
              item: {
                ...item,
                id: itemId,
              },
            },
          }));
        }
      }

      if (turn.status && turn.status !== 'inProgress') {
        events.push(createEvent('turn/completed', {
          session_id: sessionId,
          thread_id: threadId,
          turn_id: turn.id,
          payload: {
            status: turn.status,
            turn: {
              id: turn.id,
              status: turn.status,
            },
          },
        }));
      }
    }

    return events;
  }

  async #buildContentEntriesFromThread(sessionId, thread) {
    const rawThread = thread?.thread ?? thread;
    const turns = this.#sortTurnsForRendering(Array.isArray(rawThread?.turns) ? rawThread.turns : []);
    const threadId = rawThread?.id ?? rawThread?.threadId ?? rawThread?.sessionId ?? sessionId;
    const storedUserInputItemIds = this.#buildStoredTurnInputItemIdMap(sessionId);
    const entries = [];

    for (const turn of turns) {
      if (!turn?.id) continue;
      const items = await this.#loadTurnItems(threadId, turn);
      let turnItemSeq = 0;
      for (const item of items) {
        if (!item?.type) continue;
        if (item.type !== 'userMessage' && item.type !== 'agentMessage' && item.type !== 'fileChange') {
          continue;
        }
        const text = item.type === 'userMessage' ? extractUserMessageText(item.content ?? []) : '';
        const fallbackSeq = item.id ? null : String(++turnItemSeq).padStart(4, '0');
        const itemId = item.type === 'userMessage'
          ? storedUserInputItemIds.get(`${turn.id}|${text}`) ?? item.id ?? `turn_${turn.id}_user_${fallbackSeq}`
          : item.id ?? `turn_${turn.id}_item_${fallbackSeq}`;
        entries.push({
          type: 'item',
          session_id: sessionId,
          thread_id: threadId,
          turn_id: turn.id,
          item: {
            ...item,
            id: itemId,
          },
        });
      }

      if (turn.status && turn.status !== 'completed' && turn.status !== 'inProgress') {
        entries.push({
          type: 'turn_status',
          session_id: sessionId,
          thread_id: threadId,
          turn_id: turn.id,
          status: turn.status,
          error: turn.error ?? null,
        });
      }
    }

    return entries;
  }

  #sortTurnsForRendering(turns) {
    return turns.slice().sort((left, right) => {
      const leftStarted = Number(left?.startedAt ?? left?.createdAt ?? 0);
      const rightStarted = Number(right?.startedAt ?? right?.createdAt ?? 0);
      if (leftStarted !== rightStarted) return leftStarted - rightStarted;

      const leftCompleted = Number(left?.completedAt ?? 0);
      const rightCompleted = Number(right?.completedAt ?? 0);
      if (leftCompleted !== rightCompleted) return leftCompleted - rightCompleted;

      return String(left?.id ?? '').localeCompare(String(right?.id ?? ''));
    });
  }

  #buildStoredTurnInputItemIdMap(sessionId) {
    const session = this.store.getSession(sessionId);
    const events = session?.events ?? [];
    const itemIds = new Map();
    for (const event of events) {
      if (event?.event !== 'turn/input') continue;
      const turnId = event.turn_id ?? '';
      const text = String(event?.payload?.text ?? '').trim();
      const itemId = getTurnInputItemId({ payload: event?.payload, requestId: event?.request_id ?? '' });
      if (turnId && text && itemId) {
        itemIds.set(`${turnId}|${text}`, itemId);
      }
    }
    return itemIds;
  }

  async #loadTurnItems(threadId, turn) {
    const inlineItems = Array.isArray(turn?.items) ? turn.items : [];
    const canListTurnItems = typeof this.backend.listTurnItems === 'function';
    const shouldLoadRemoteItems = canListTurnItems && turn?.id && threadId && (turn?.itemsView !== 'full' || inlineItems.length === 0);
    if (!shouldLoadRemoteItems) {
      return inlineItems;
    }

    try {
      const loaded = [];
      let cursor = null;
      do {
        const page = await this.backend.listTurnItems(threadId, turn.id, {
          cursor,
          limit: 200,
          sortDirection: 'asc',
        });
        const data = Array.isArray(page) ? page : page?.data ?? [];
        loaded.push(...data);
        cursor = Array.isArray(page) ? null : page?.nextCursor ?? null;
      } while (cursor);

      return loaded.length > 0 ? loaded : inlineItems;
    } catch {
      return inlineItems;
    }
  }

  async #readSessionFile(sessionId, requestedPath, maxBytes) {
    const session = sessionId ? this.store.getSession(sessionId) : null;
    const cwd = session?.cwd || session?.thread?.cwd || null;
    const resolvedPath = path.isAbsolute(requestedPath)
      ? requestedPath
      : cwd
        ? path.resolve(cwd, requestedPath)
        : null;
    if (!resolvedPath) {
      throw new Error(`cannot resolve relative path without session cwd: ${requestedPath}`);
    }

    const raw = await fs.readFile(resolvedPath);
    const truncated = raw.byteLength > maxBytes;
    const bytes = truncated ? raw.subarray(0, maxBytes) : raw;
    return {
      resolvedPath,
      content: bytes.toString('utf8'),
      truncated,
      bytes: bytes.byteLength,
    };
  }

  #mergeInitialSessionEvents(hydratedEvents, storedEvents) {
    if (hydratedEvents.length === 0) {
      return clone(storedEvents);
    }

    const merged = [];
    const seen = new Set();
    const finalizedAssistantItems = new Set(
      hydratedEvents
        .filter((event) => event?.event === 'item/completed' && event?.payload?.item?.type === 'agentMessage')
        .map((event) => `${event?.turn_id ?? ''}|${event?.payload?.item?.id ?? ''}`),
    );

    for (const event of hydratedEvents) {
      const signature = this.#eventSignature(event);
      if (seen.has(signature)) continue;
      seen.add(signature);
      merged.push(event);
    }

    for (const event of storedEvents) {
      const signature = this.#eventSignature(event);
      if (seen.has(signature)) continue;
      if (event?.event === 'item/agentMessage/delta') {
        const itemId = event?.payload?.itemId ?? event?.payload?.item_id ?? '';
        const key = `${event?.turn_id ?? ''}|${itemId}`;
        if (itemId && finalizedAssistantItems.has(key)) {
          continue;
        }
      }
      seen.add(signature);
      merged.push(event);
    }

    return merged;
  }

  #pruneHydratedEvents(hydratedEvents, storedEvents) {
    if (hydratedEvents.length === 0) {
      return [];
    }

    const storedAssistantDeltaTurns = new Set(
      storedEvents
        .filter((event) => event?.event === 'item/agentMessage/delta')
        .map((event) => event?.turn_id ?? '')
        .filter(Boolean),
    );
    const hydratedAssistantCounts = new Map();
    for (const event of hydratedEvents) {
      if (event?.event !== 'item/completed' || event?.payload?.item?.type !== 'agentMessage') {
        continue;
      }
      const turnId = event?.turn_id ?? '';
      if (!turnId) continue;
      hydratedAssistantCounts.set(turnId, (hydratedAssistantCounts.get(turnId) ?? 0) + 1);
    }

    return hydratedEvents.filter((event) => {
      if (event?.event !== 'item/completed' || event?.payload?.item?.type !== 'agentMessage') {
        return true;
      }
      const turnId = event?.turn_id ?? '';
      if (!turnId || !storedAssistantDeltaTurns.has(turnId)) {
        return true;
      }
      return (hydratedAssistantCounts.get(turnId) ?? 0) > 1;
    });
  }

  #eventSignature(event) {
    const payload = event?.payload ?? {};
    if (event?.event === 'item/completed' || event?.event === 'item/started') {
      const item = this.#canonicalizeItemForSignature(payload?.item ?? {});
      return [
        event?.event ?? '',
        event?.turn_id ?? '',
        item?.id ?? '',
        item?.type ?? '',
        JSON.stringify(item),
      ].join('|');
    }
    if (event?.event === 'item/commandExecution/requestApproval') {
      return [
        event?.event ?? '',
        event?.turn_id ?? '',
        event?.request_id ?? '',
        payload?.itemId ?? '',
      ].join('|');
    }
    if (event?.event === 'turn/completed') {
      return [
        event?.event ?? '',
        event?.turn_id ?? '',
        payload?.status ?? payload?.turn?.status ?? '',
        payload?.turn?.error?.message ?? payload?.error?.message ?? '',
      ].join('|');
    }
    return [
      event?.event ?? '',
      event?.turn_id ?? '',
      payload?.itemId ?? '',
      payload?.text ?? '',
      payload?.delta ?? '',
      payload?.status ?? '',
      payload?.message ?? '',
      payload?.error?.message ?? '',
      payload?.item?.id ?? '',
      payload?.item?.type ?? '',
    ].join('|');
  }

  #canonicalizeItemForSignature(item) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      return item;
    }
    const normalized = clone(item);
    delete normalized.phase;
    delete normalized.memoryCitation;
    return normalized;
  }
}

function safeJson(text) {
  try {
    return { ok: true, value: JSON.parse(text) };
  } catch (error) {
    return { ok: false, error };
  }
}

function clampMaxBytes(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 200_000;
  return Math.max(8_192, Math.min(500_000, Math.trunc(parsed)));
}
