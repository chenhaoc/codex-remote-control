import { firstNonEmptyString, normalizeTurnStatusValue } from './common.mjs';
import { diffStatsLabel } from './sync-log.mjs';

export function buildActiveTurns(store, sessionId, entries = []) {
  const events = store.syncEvents(sessionId, 0);
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
      const detail = activeTurnDetailFromEvent(event);
      const outputCharsDelta = detail.output_chars_delta ?? 0;
      delete detail.output_chars_delta;
      turns.set(turnId, {
        ...turns.get(turnId),
        last_event: event.event,
        last_item_type: activeTurnItemTypeFromEvent(event) ?? turns.get(turnId)?.last_item_type ?? null,
        ...detail,
        output_chars: (turns.get(turnId)?.output_chars ?? 0) + outputCharsDelta,
      });
    }
  }

  for (const entry of entries) {
    const turnId = entry?.turn_id ?? '';
    const status = entry?.type === 'turn_status' ? normalizeTurnStatusValue(entry.status) : '';
    if (turnId && status && status !== 'inProgress') {
      turns.delete(turnId);
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

function activeTurnItemTypeFromEvent(event) {
  if (event?.payload?.item?.type) return event.payload.item.type;
  if (event?.event === 'item/agentMessage/delta') return 'agentMessage';
  if (event?.event === 'item/fileChange/patchUpdated' || event?.event === 'turn/diff/updated') return 'fileChange';
  return null;
}

function activeTurnDetailFromEvent(event) {
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
