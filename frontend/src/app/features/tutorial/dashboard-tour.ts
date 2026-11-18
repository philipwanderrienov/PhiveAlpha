export const DASHBOARD_STEPS = [
  { target: 'welcome', view: 'dashboard', title: 'Mengenal CoinMind-AI', text: 'CoinMind-AI membantu membaca kondisi pasar kripto. Tutorial ini hanya menjelaskan tampilan; tidak menjalankan AI, trading, atau mengubah konfigurasi.' },
  { target: 'connection', view: 'dashboard', title: 'Status koneksi server dan market', text: 'Server connected berarti server dapat dijangkau. Market aktif berarti data mengalir. Jika terlambat atau offline, periksa koneksi dan kesegaran data sebelum mengambil keputusan.' },
  { target: 'symbols', view: 'dashboard', title: 'Memilih BTC, ETH, atau SOL', text: 'Kartu BTC, ETH, dan SOL memilih pasar yang ingin dipantau. Setelah tutorial, pilih kartu untuk memperbarui konteks analisis sesuai aset.' },
  { target: 'ticker', view: 'dashboard', title: 'Harga, perubahan, dan volume', text: 'Harga menunjukkan harga terakhir dalam USDT. Persentase menunjukkan perubahan 24 jam, sedangkan volume menunjukkan aktivitas perdagangan. Data dapat belum tersedia saat koneksi terputus.' },
  { target: 'navigation', view: 'dashboard', menu: true, title: 'Navigasi menu aplikasi', text: 'Sidebar membuka Dashboard, Trade Decision, Simulasi & Risiko, News, AI & Usage, AI Billing, dan Tutorial. Pada mobile, buka sidebar melalui tombol menu.' },
  { target: 'sentiment', view: 'news', title: 'Memahami News Sentiment', text: 'News Sentiment merangkum jumlah berita positif, netral, dan negatif untuk aset terpilih. Sentimen adalah sinyal pendukung, bukan jaminan arah harga. Tutorial membuka tampilan News secara otomatis.' },
  { target: 'news-article', view: 'news', title: 'Sumber dan ringkasan berita', text: 'Kartu berita memuat sumber, waktu publikasi, judul, ringkasan bila tersedia, dan tautan artikel asli. Jika berita kosong, daftar ini tetap menjadi tempat berita akan tampil.' },
  { target: 'news-ai', view: 'news', title: 'Fungsi tombol Analisis dengan AI', text: 'Tombol ini meminta penilaian dampak berita dengan AI jika layanan tersedia. Permintaan dapat menggunakan kuota. Dalam tutorial tombol tidak dapat ditekan dan tidak ada permintaan Gemini yang dijalankan.' },
  { target: 'trade-menu', view: 'dashboard', menu: true, title: 'Mengenal menu Trade Decision', text: 'Menu Trade Decision menampilkan rekomendasi, area entry, risiko, target, dan alasan keputusan. Informasi ini membantu evaluasi; tutorial tidak membuka atau menjalankan transaksi.' },
  { target: 'welcome', view: 'dashboard', title: 'Tutorial selesai', text: 'Anda telah mengenal Dashboard! Gunakan Selesai untuk kembali menjelajah, atau Ulangi untuk belajar dari awal. Tutorial dapat dibuka lagi melalui Tutorial Center.' }
] as const;

export function tourIndex(index: number): number {
  return Math.max(0, Math.min(DASHBOARD_STEPS.length - 1, index));
}

export function spotlight(rect: { left: number; top: number; right: number; bottom: number }, width: number, height: number) {
  const left = Math.min(width - 4, Math.max(4, rect.left - 5));
  const top = Math.min(height - 4, Math.max(4, rect.top - 5));
  return { left, top, width: Math.max(0, Math.min(width - 4, rect.right + 5) - left), height: Math.max(0, Math.min(height - 4, rect.bottom + 5) - top) };
}

/** Prefer a fully visible adjacent placement, then clamp the best available space. */
export function popoverPosition(box: {left:number;top:number;width:number;height:number}, panel: {width:number;height:number}, width:number, height:number) {
  const gap = 14, margin = 12;
  const candidates = [
    {placement:'bottom', left:box.left + (box.width-panel.width)/2, top:box.top+box.height+gap},
    {placement:'top', left:box.left + (box.width-panel.width)/2, top:box.top-panel.height-gap},
    {placement:'right', left:box.left+box.width+gap, top:box.top+(box.height-panel.height)/2},
    {placement:'left', left:box.left-panel.width-gap, top:box.top+(box.height-panel.height)/2}
  ];
  const overflow = (p:typeof candidates[number]) => Math.max(0,margin-p.left)+Math.max(0,p.left+panel.width-width+margin)+Math.max(0,margin-p.top)+Math.max(0,p.top+panel.height-height+margin);
  const best = candidates.reduce((a,b) => overflow(b)<overflow(a) ? b : a);
  return {...best, left:Math.max(margin,Math.min(width-panel.width-margin,best.left)), top:Math.max(margin,Math.min(height-panel.height-margin,best.top))};
}
