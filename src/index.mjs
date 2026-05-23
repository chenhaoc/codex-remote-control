import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { StateStore } from './store.mjs';
import { MockBackend } from './backends/mock.mjs';
import { CodexBackend } from './backends/codex.mjs';
import { BridgeServer } from './bridge-server.mjs';

function parseArgs(argv) {
  const out = {
    backend: 'mock',
    listen: '127.0.0.1:8787',
    state: path.join(process.cwd(), 'data', 'bridge-state.json'),
    tokenFile: path.join(process.cwd(), 'data', 'bridge-token.txt'),
    syncLog: null,
    protocolLog: null,
    codexBin: 'codex',
    codexArgs: [],
    codexConfigs: [],
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--backend') out.backend = argv[++i];
    else if (arg === '--listen') out.listen = argv[++i];
    else if (arg === '--state') out.state = argv[++i];
    else if (arg === '--token-file') out.tokenFile = argv[++i];
    else if (arg === '--sync-log') out.syncLog = argv[++i];
    else if (arg === '--protocol-log') out.protocolLog = argv[++i];
    else if (arg === '--codex-bin') out.codexBin = argv[++i];
    else if (arg === '--codex-arg') out.codexArgs.push(argv[++i]);
    else if (arg === '--codex-config') out.codexConfigs.push(argv[++i]);
    else if (arg === '--help' || arg === '-h') {
      out.help = true;
    }
  }
  return out;
}

function parseListen(listen) {
  const [host, portRaw] = listen.includes(':') ? listen.split(/:(?=[^:]*$)/) : [listen, '8787'];
  return { host, port: Number(portRaw) || 8787 };
}

async function loadOrCreateToken(tokenFile) {
  await fs.mkdir(path.dirname(tokenFile), { recursive: true });
  try {
    const token = (await fs.readFile(tokenFile, 'utf8')).trim();
    if (token) return token;
  } catch {}
  const token = cryptoRandomToken();
  await fs.writeFile(tokenFile, `${token}\n`, 'utf8');
  return token;
}

function cryptoRandomToken() {
  return [...crypto.getRandomValues(new Uint8Array(24))]
    .map((n) => n.toString(16).padStart(2, '0'))
    .join('');
}

const crypto = globalThis.crypto ?? (await import('node:crypto')).webcrypto;

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log(`Usage: node src/index.mjs [options]

Options:
  --backend mock|codex
  --listen HOST:PORT
  --state PATH
  --token-file PATH
  --sync-log PATH        enable sync debug logging to a file
  --protocol-log PATH    capture raw bridge<->codex protocol lines
  --codex-bin PATH
  --codex-arg VALUE      repeatable
  --codex-config KEY=VALUE repeatable
`);
    process.exit(0);
  }

  const { host, port } = parseListen(args.listen);
  const token = await loadOrCreateToken(args.tokenFile);
  const store = new StateStore(args.state);
  const backend = args.backend === 'codex'
    ? new CodexBackend({
        codexBin: args.codexBin,
        codexArgs: args.codexArgs,
        codexConfigs: args.codexConfigs,
        protocolLogPath: args.protocolLog,
      })
    : new MockBackend();
  const bridge = new BridgeServer({ backend, store, host, port, token, syncLogPath: args.syncLog });

  await bridge.start();
  const address = bridge.address();
  console.log(`Codex bridge ready on ${address.host}:${address.port}`);
  console.log(`Token file: ${args.tokenFile}`);
  console.log(`State file: ${args.state}`);
  if (args.syncLog) {
    console.log(`Sync log: ${args.syncLog}`);
  }
  if (args.protocolLog) {
    console.log(`Protocol log: ${args.protocolLog}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
