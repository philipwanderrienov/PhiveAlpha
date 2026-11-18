/** Display only API source metadata; never infer the publisher from article text or AI. */
export function newsSourceLabel(source: unknown): string {
  if (typeof source !== 'string') return 'Sumber tidak tersedia';
  const label = source.replace(/[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/g, '').trim().replace(/\s+/g, ' ');
  if (!label || /[<>]/.test(label)) return 'Sumber tidak tersedia';
  switch (label.toLowerCase()) {
    case 'www.coindesk.com':
    case 'coindesk.com': return 'CoinDesk';
    case 'www.cointelegraph.com':
    case 'cointelegraph.com': return 'Cointelegraph';
    default: return label.slice(0, 100);
  }
}

/** Accept absolute HTTP(S) links only, without credentials or hidden control characters. */
export function safeNewsUrl(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const url = value.trim();
  if (!/^https?:\/\//i.test(url) || /[\s\u0000-\u001f\u007f-\u009f\\]/.test(url)) return null;
  try {
    const parsed = new URL(url);
    if (!['http:', 'https:'].includes(parsed.protocol) || !parsed.hostname || parsed.username || parsed.password) return null;
    return parsed.href;
  } catch {
    return null;
  }
}
