export const SESSION_SYNC_EVENT_NAMES = new Set([
  'turn/input',
  'turn/started',
  'turn/completed',
  'item/completed',
  'item/fileChange/patchUpdated',
  'turn/diff/updated',
  'item/commandExecution/requestApproval',
  'item/fileChange/requestApproval',
  'item/permissions/requestApproval',
  'applyPatchApproval',
  'execCommandApproval',
]);

export const SYNC_LOG_EVENT_LIMIT = 12;

export function nowLocalIso() {
  const date = new Date();
  const pad = (value, length = 2) => String(value).padStart(length, '0');
  const offsetMinutes = -date.getTimezoneOffset();
  const offsetSign = offsetMinutes >= 0 ? '+' : '-';
  const absOffsetMinutes = Math.abs(offsetMinutes);
  const offsetHours = Math.floor(absOffsetMinutes / 60);
  const offsetRemainderMinutes = absOffsetMinutes % 60;
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`
    + `${offsetSign}${pad(offsetHours)}:${pad(offsetRemainderMinutes)}`;
}

export function normalizeSeq(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0;
}

export function summarizeSyncEntries(entries = []) {
  return entries.slice(-SYNC_LOG_EVENT_LIMIT).map((entry) => ({
    type: entry?.type ?? '',
    turn_id: entry?.turn_id ?? '',
    item_type: entry?.item?.type ?? '',
    item_id: entry?.item?.id ?? '',
    text_len: String(entry?.item?.text ?? entry?.item?.content?.[0]?.text ?? '').length,
    status: entry?.status ?? '',
  }));
}

export function summarizeSyncEvents(events = []) {
  return events.slice(-SYNC_LOG_EVENT_LIMIT).map((event) => ({
    seq: event?.seq ?? null,
    event: event?.event ?? '',
    turn_id: event?.turn_id ?? '',
    item_type: event?.payload?.item?.type ?? '',
    item_id: event?.payload?.item?.id ?? '',
    text_len: String(event?.payload?.item?.text ?? event?.payload?.text ?? event?.payload?.delta ?? '').length,
  }));
}

export function syncItemTypeCounts(entries = []) {
  const counts = {};
  for (const entry of entries) {
    const key = entry?.item?.type ?? entry?.type ?? 'unknown';
    counts[key] = (counts[key] ?? 0) + 1;
  }
  return counts;
}

export function diffStatsLabel(diff) {
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
