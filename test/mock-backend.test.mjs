import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { EventEmitter, once } from 'node:events';
import WebSocket from 'ws';
import { StateStore } from '../src/store.mjs';
import { MockBackend } from '../src/backends/mock.mjs';
import { BridgeServer } from '../src/bridge-server.mjs';

test('mock backend produces events and approval flow', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'codex-bridge-test-'));
  const store = new StateStore(path.join(dir, 'state.json'));
  const backend = new MockBackend();
  const bridge = new BridgeServer({ backend, store, host: '127.0.0.1', port: 0, token: 'test-token' });
  await bridge.start();
  const { port } = bridge.address();

  const ws = new WebSocket(`ws://127.0.0.1:${port}/ws?token=test-token`);
  await once(ws, 'open');

  const messages = [];
  ws.on('message', (buffer) => {
    messages.push(JSON.parse(buffer.toString('utf8')));
  });

  ws.send(JSON.stringify({ id: '0', type: 'model.list', payload: { includeHidden: false } }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '0'));
  const modelList = messages.find((m) => m.type === 'response' && m.id === '0');
  assert.equal(modelList.ok, true);
  assert.ok(modelList.payload.data.length >= 1);
  const chosenModel = modelList.payload.data[0].model;

  ws.send(JSON.stringify({ id: '1', type: 'session.start', payload: { title: 'Test session', model: chosenModel } }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '1'));
  const sessionStart = messages.find((m) => m.type === 'response' && m.id === '1');
  assert.equal(sessionStart.ok, true);
  assert.ok(sessionStart.payload.session.session_id);
  assert.equal(sessionStart.payload.session.model, chosenModel);

  const sessionId = sessionStart.payload.session.session_id;
  ws.send(JSON.stringify({ id: '1b', type: 'session.list', payload: {} }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '1b'));
  const sessionList = messages.find((m) => m.type === 'response' && m.id === '1b');
  assert.equal(sessionList.ok, true);
  assert.equal(sessionList.payload.sessions[0].model, chosenModel);

  ws.send(JSON.stringify({ id: '2', type: 'turn.send', payload: { session_id: sessionId, text: 'please request approval' } }));
  await waitFor(() => messages.find((m) => m.type === 'event' && m.event === 'item/commandExecution/requestApproval'));

  const approvalEvent = messages.find((m) => m.type === 'event' && m.event === 'item/commandExecution/requestApproval');
  ws.send(JSON.stringify({
    id: '3',
    type: 'approval.response',
    payload: {
      session_id: sessionId,
      request_id: approvalEvent.request_id,
      decision: 'accept',
    },
  }));

  await waitFor(() => messages.find((m) => m.type === 'event' && m.event === 'turn/completed'));
  const turnCompleted = messages.find((m) => m.type === 'event' && m.event === 'turn/completed');
  assert.equal(turnCompleted.session_id, sessionId);
  ws.close();
  await once(ws, 'close');
  await bridge.stop();
  await fs.rm(dir, { recursive: true, force: true });
});

test('bridge resumes notLoaded threads before send and hydrates history', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'codex-bridge-resume-test-'));
  const rolloutPath = path.join(dir, 'resume-thread.jsonl');
  await fs.writeFile(rolloutPath, [
    JSON.stringify({ timestamp: '2026-01-01T00:00:00.000Z', type: 'event_msg', payload: { type: 'task_started', turn_id: 'turn_old' } }),
    JSON.stringify({ timestamp: '2026-01-01T00:00:00.100Z', type: 'event_msg', payload: { type: 'user_message', message: '旧消息' } }),
    JSON.stringify({ timestamp: '2026-01-01T00:00:00.200Z', type: 'event_msg', payload: { type: 'agent_message', message: '旧回复' } }),
    JSON.stringify({ timestamp: '2026-01-01T00:00:00.300Z', type: 'event_msg', payload: { type: 'task_complete', turn_id: 'turn_old' } }),
    '',
  ].join('\n'), 'utf8');

  class ResumeBackend extends EventEmitter {
    constructor() {
      super();
      this.resumeCalls = 0;
    }

    async start() {}

    async stop() {}

    async listThreads() {
      return [this.#thread(false)];
    }

    async resumeThread(threadId) {
      this.resumeCalls += 1;
      return {
        thread: this.#thread(true, threadId),
      };
    }

    async startTurn(threadId) {
      if (this.resumeCalls === 0) {
        throw new Error(`thread not found: ${threadId}`);
      }
      return {
        id: 'turn_live',
        items: [],
        itemsView: 'summary',
        status: 'inProgress',
        error: null,
        startedAt: 10,
        completedAt: null,
        durationMs: null,
      };
    }

    async interruptTurn() {}

    async respondRequest() {}

    #thread(loaded, threadId = 'resume_thread_1') {
      return {
        id: threadId,
        sessionId: threadId,
        forkedFromId: null,
        preview: '历史会话',
        ephemeral: false,
        model: 'gpt-5',
        modelProvider: 'custom',
        createdAt: 1,
        updatedAt: 2,
        status: loaded ? { type: 'idle' } : { type: 'notLoaded' },
        path: rolloutPath,
        cwd: '/tmp',
        cliVersion: 'mock',
        source: 'app-server',
        threadSource: null,
        agentNickname: null,
        agentRole: null,
        gitInfo: null,
        name: '历史会话',
        turns: loaded
          ? [{
            id: 'turn_old',
            items: [
              {
                type: 'userMessage',
                id: 'item_user',
                content: [{ type: 'text', text: '旧消息', text_elements: [] }],
              },
              {
                type: 'agentMessage',
                id: 'item_agent',
                text: '旧回复',
                phase: null,
                memoryCitation: null,
              },
            ],
            itemsView: 'full',
            status: 'completed',
            error: null,
            startedAt: 1,
            completedAt: 2,
            durationMs: 1,
          }]
          : [],
      };
    }
  }

  const store = new StateStore(path.join(dir, 'state.json'));
  const backend = new ResumeBackend();
  const bridge = new BridgeServer({ backend, store, host: '127.0.0.1', port: 0, token: 'test-token' });
  await bridge.start();
  const { port } = bridge.address();

  const ws = new WebSocket(`ws://127.0.0.1:${port}/ws?token=test-token`);
  await once(ws, 'open');

  const messages = [];
  ws.on('message', (buffer) => {
    messages.push(JSON.parse(buffer.toString('utf8')));
  });

  ws.send(JSON.stringify({ id: '1', type: 'session.list', payload: {} }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '1'));
  const sessionList = messages.find((m) => m.type === 'response' && m.id === '1');
  const sessionId = sessionList.payload.sessions[0].session_id;

  ws.send(JSON.stringify({ id: '2', type: 'turn.send', payload: { session_id: sessionId, text: '继续' } }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '2'));
  const sendResponse = messages.find((m) => m.type === 'response' && m.id === '2');
  assert.equal(sendResponse.ok, true);
  assert.equal(backend.resumeCalls, 1);

  ws.send(JSON.stringify({ id: '3', type: 'session.sync', payload: { session_id: sessionId, since_seq: 0 } }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '3'));
  const syncResponse = messages.find((m) => m.type === 'response' && m.id === '3');
  assert.equal(syncResponse.ok, true);
  assert.ok(syncResponse.payload.events.some((event) => event.event === 'turn/input' && event.payload.text === '旧消息'));
  assert.ok(syncResponse.payload.events.some((event) => event.event === 'item/agentMessage/delta' && event.payload.delta === '旧回复'));

  ws.close();
  await once(ws, 'close');
  await bridge.stop();
  await fs.rm(dir, { recursive: true, force: true });
});

test('bridge keeps stored assistant deltas instead of replacing them with hydrated summaries', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'codex-bridge-stored-test-'));
  const rolloutPath = path.join(dir, 'stored-thread.jsonl');
  await fs.writeFile(rolloutPath, '', 'utf8');

  class StoredEventBackend extends EventEmitter {
    async start() {}

    async stop() {}

    async listThreads() {
      return [this.#thread()];
    }

    async resumeThread() {
      throw new Error('resume should not be called when stored events exist');
    }

    #thread() {
      return {
        id: 'stored_thread_1',
        sessionId: 'stored_thread_1',
        forkedFromId: null,
        preview: '已存历史',
        ephemeral: false,
        model: 'gpt-5',
        modelProvider: 'custom',
        createdAt: 1,
        updatedAt: 2,
        status: { type: 'idle' },
        path: rolloutPath,
        cwd: '/tmp',
        cliVersion: 'mock',
        source: 'app-server',
        threadSource: null,
        agentNickname: null,
        agentRole: null,
        gitInfo: null,
        name: '已存历史',
        turns: [{
          id: 'turn_live',
          items: [{
            type: 'agentMessage',
            id: 'item_summary',
            text: '折叠后的摘要',
            phase: null,
            memoryCitation: null,
          }],
          itemsView: 'summary',
          status: 'completed',
          error: null,
          startedAt: 1,
          completedAt: 2,
          durationMs: 1,
        }],
      };
    }
  }

  const store = new StateStore(path.join(dir, 'state.json'));
  await store.upsertSession({
    session_id: 'stored_thread_1',
    thread_id: 'stored_thread_1',
    title: '已存历史',
    cwd: '/tmp',
    model: 'gpt-5',
    backend: 'mock',
    preview: '已存历史',
    active: true,
    thread: {
      id: 'stored_thread_1',
      sessionId: 'stored_thread_1',
      status: { type: 'idle' },
      path: rolloutPath,
      turns: [],
    },
  });
  await store.appendEvent('stored_thread_1', {
    type: 'event',
    event: 'item/agentMessage/delta',
    session_id: 'stored_thread_1',
    thread_id: 'stored_thread_1',
    turn_id: 'turn_live',
    payload: {
      itemId: 'item_live',
      delta: '第一段',
      text: '第一段',
      message: '第一段',
    },
  });

  const backend = new StoredEventBackend();
  const bridge = new BridgeServer({ backend, store, host: '127.0.0.1', port: 0, token: 'test-token' });
  await bridge.start();
  const { port } = bridge.address();

  const ws = new WebSocket(`ws://127.0.0.1:${port}/ws?token=test-token`);
  await once(ws, 'open');

  const messages = [];
  ws.on('message', (buffer) => {
    messages.push(JSON.parse(buffer.toString('utf8')));
  });

  ws.send(JSON.stringify({ id: '1', type: 'session.list', payload: {} }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '1'));

  ws.send(JSON.stringify({ id: '2', type: 'session.sync', payload: { session_id: 'stored_thread_1', since_seq: 0 } }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '2'));

  const syncResponse = messages.find((m) => m.type === 'response' && m.id === '2');
  assert.equal(syncResponse.ok, true);
  assert.equal(syncResponse.payload.events.length, 1);
  assert.equal(syncResponse.payload.events[0].payload.delta, '第一段');
  assert.notEqual(syncResponse.payload.events[0].payload.delta, '折叠后的摘要');

  ws.close();
  await once(ws, 'close');
  await bridge.stop();
  await fs.rm(dir, { recursive: true, force: true });
});

test('bridge hydrates full turn items when turn summaries are incomplete', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'codex-bridge-items-test-'));

  class TurnItemsBackend extends EventEmitter {
    async start() {}

    async stop() {}

    async listThreads() {
      return [this.#thread()];
    }

    async listTurnItems(threadId, turnId) {
      assert.equal(threadId, 'items_thread_1');
      assert.equal(turnId, 'turn_full');
      return {
        data: [
          {
            type: 'userMessage',
            id: 'item_user',
            content: [{ type: 'text', text: '你好', text_elements: [] }],
          },
          {
            type: 'agentMessage',
            id: 'item_part_1',
            text: '第一段',
            phase: null,
            memoryCitation: null,
          },
          {
            type: 'agentMessage',
            id: 'item_part_2',
            text: '第二段',
            phase: null,
            memoryCitation: null,
          },
        ],
        nextCursor: null,
        backwardsCursor: null,
      };
    }

    #thread() {
      return {
        id: 'items_thread_1',
        sessionId: 'items_thread_1',
        forkedFromId: null,
        preview: '完整 items',
        ephemeral: false,
        model: 'gpt-5',
        modelProvider: 'custom',
        createdAt: 1,
        updatedAt: 2,
        status: { type: 'idle' },
        path: null,
        cwd: '/tmp',
        cliVersion: 'mock',
        source: 'app-server',
        threadSource: null,
        agentNickname: null,
        agentRole: null,
        gitInfo: null,
        name: '完整 items',
        turns: [{
          id: 'turn_full',
          items: [{
            type: 'agentMessage',
            id: 'item_summary',
            text: '折叠摘要',
            phase: null,
            memoryCitation: null,
          }],
          itemsView: 'summary',
          status: 'completed',
          error: null,
          startedAt: 1,
          completedAt: 2,
          durationMs: 1,
        }],
      };
    }
  }

  const store = new StateStore(path.join(dir, 'state.json'));
  const backend = new TurnItemsBackend();
  const bridge = new BridgeServer({ backend, store, host: '127.0.0.1', port: 0, token: 'test-token' });
  await bridge.start();
  const { port } = bridge.address();

  const ws = new WebSocket(`ws://127.0.0.1:${port}/ws?token=test-token`);
  await once(ws, 'open');

  const messages = [];
  ws.on('message', (buffer) => {
    messages.push(JSON.parse(buffer.toString('utf8')));
  });

  ws.send(JSON.stringify({ id: '1', type: 'session.list', payload: {} }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '1'));

  ws.send(JSON.stringify({ id: '2', type: 'session.sync', payload: { session_id: 'items_thread_1', since_seq: 0 } }));
  await waitFor(() => messages.find((m) => m.type === 'response' && m.id === '2'));

  const syncResponse = messages.find((m) => m.type === 'response' && m.id === '2');
  assert.equal(syncResponse.ok, true);
  assert.ok(syncResponse.payload.events.some((event) => event.event === 'turn/input' && event.payload.text === '你好'));
  assert.ok(syncResponse.payload.events.some((event) => event.event === 'item/agentMessage/delta' && event.payload.itemId === 'item_part_1' && event.payload.delta === '第一段'));
  assert.ok(syncResponse.payload.events.some((event) => event.event === 'item/agentMessage/delta' && event.payload.itemId === 'item_part_2' && event.payload.delta === '第二段'));

  ws.close();
  await once(ws, 'close');
  await bridge.stop();
  await fs.rm(dir, { recursive: true, force: true });
});

async function waitFor(predicate, timeoutMs = 8000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (predicate()) return;
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  throw new Error('timed out');
}
