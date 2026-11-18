export function aiStatusLabel(state?: string): string {
  if (state === 'DISABLED' || state === 'MISSING_API_KEY') return 'AI Belum Diaktifkan';
  if (state === 'READY') return 'AI Aktif';
  if (state === 'ERROR' || state === 'RATE_LIMITED') return 'AI Sedang Mengalami Gangguan';
  return 'AI Tidak Tersedia';
}
