export function marketEventFresh(eventMs: number, nowMs: number): boolean {
  return Number.isFinite(eventMs) && eventMs >= nowMs - 30_000 && eventMs <= nowMs + 5_000;
}
