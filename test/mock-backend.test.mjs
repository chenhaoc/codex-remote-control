import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
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

async function waitFor(predicate, timeoutMs = 8000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (predicate()) return;
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  throw new Error('timed out');
}
