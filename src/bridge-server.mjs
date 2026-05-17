import http from 'node:http';
import { EventEmitter } from 'node:events';
import { randomUUID } from 'node:crypto';
import { WebSocket, WebSocketServer } from 'ws';
import { createError, createResponse, createEvent, nowIso } from './protocol.mjs';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function normalizeThreadSummary(thread, backend = 'mock') {
  if (!thread) return null;
  const raw = thread.thread ?? thread;
  const id = raw.id ?? raw.threadId ?? raw.sessionId;
  return {
    session_id: id,
    thread_id: id,
    title: raw.name ?? raw.title ?? raw.preview ?? '',
    cwd: raw.cwd ?? raw.path ?? '',
    model: raw.model ?? '',
    preview: raw.preview ?? '',
    active: raw.status ? String(raw.status) === 'active' || String(raw.status) === 'running' : true,
    backend,
    createdAt: raw.createdAt ? new Date(raw.createdAt * 1000).toISOString() : nowIso(),
    updatedAt: raw.updatedAt ? new Date(raw.updatedAt * 1000).toISOString() : nowIso(),
    thread: clone(raw),
  };
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
          return summary;
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
          permissions: payload.permissions ?? null,
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
        await this.store.upsertSession(session);
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
          permissions: payload.permissions ?? null,
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
      case 'turn.send': {
        const sessionId = payload.session_id ?? payload.thread_id;
        const inputEvent = createEvent('turn/input', {
          session_id: sessionId,
          thread_id: sessionId,
          request_id: id,
          payload: {
            text: payload.text ?? '',
            input: payload.input ?? null,
          },
        });
        const session = this.store.getSession(sessionId);
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
        }
        await this.store.appendEvent(sessionId, inputEvent);
        this.#broadcast(inputEvent);
        const turn = await this.backend.startTurn(sessionId, {
          text: payload.text ?? '',
          input: payload.input ?? null,
          responsesapiClientMetadata: payload.responsesapiClientMetadata ?? null,
          environments: payload.environments ?? null,
          cwd: payload.cwd ?? null,
          approvalPolicy: payload.approvalPolicy ?? null,
          approvalsReviewer: payload.approvalsReviewer ?? null,
          sandboxPolicy: payload.sandboxPolicy ?? null,
          permissions: payload.permissions ?? null,
          model: payload.model ?? null,
          serviceTier: payload.serviceTier ?? null,
          effort: payload.effort ?? null,
          summary: payload.summary ?? null,
          personality: payload.personality ?? null,
          outputSchema: payload.outputSchema ?? null,
          collaborationMode: payload.collaborationMode ?? null,
          turnId: payload.turn_id ?? null,
        });
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
        ws.send(JSON.stringify(createResponse(id, { ok: true })));
        return;
      }
      case 'session.sync': {
        const sessionId = payload.session_id ?? payload.thread_id;
        const events = this.store.syncEvents(sessionId, payload.since_seq ?? 0);
        ws.send(JSON.stringify(createResponse(id, { session_id: sessionId, events })));
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
      if (sessionId) {
        const session = this.store.getSession(sessionId);
        if (session) {
          await this.store.appendEvent(sessionId, event);
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
          });
          await this.store.appendEvent(sessionId, event);
        }
      }
      this.#broadcast(event);
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
        await this.store.appendEvent(threadId, event);
      }
      this.#broadcast(event);
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
}

function safeJson(text) {
  try {
    return { ok: true, value: JSON.parse(text) };
  } catch (error) {
    return { ok: false, error };
  }
}
