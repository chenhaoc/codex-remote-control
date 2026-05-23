import fs from 'node:fs/promises';
import path from 'node:path';

export async function readSessionFile(store, sessionId, requestedPath, maxBytes) {
  const session = sessionId ? store.getSession(sessionId) : null;
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

export function clampMaxBytes(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 200_000;
  return Math.max(8_192, Math.min(500_000, Math.trunc(parsed)));
}
