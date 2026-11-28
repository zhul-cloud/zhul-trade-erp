// 全站表面分层对比图（R6），2026-11-24。
const BASE = { ink: '#212B36', sub: '#3E4750', mute: '#637381', faint: '#919EAB', line: '#E5E8EB', card: '#FFFFFF', control: '#D3D5D8',
  orange: '#9A5B00', orangeSoft: '#FFF5E0', red: '#B71D18', redSoft: '#FFEBE6', info: '#006C9C', infoSoft: '#E0F6FA', green: '#0D7A4A', greenSoft: '#E5F8EC', primary: '#FDA92D', onPrimary: '#212B36' };
const NOW = { ...BASE, canvas: '#F9FAFB', head: '#F4F6F8', inset: '#F1F3F5', track: '#F9FAFB', hover: '#F6F7F8' };
const NEW = { ...BASE, canvas: '#F4F6F8', head: '#F9FAFB', inset: '#F7F8FA', track: '#E9ECEF', hover: '#F6F7F8' };
const F = 'Public Sans', MONO = 'JetBrains Mono';
const fill = (c) => [{ type: 'solid', color: c }];
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: o.font || F, fontSize: o.size || 14, fontWeight: o.weight || 400, lineHeight: o.size === 12 ? 1.5 : 1.57, fill: fill(o.color) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const pill = (p, text, fg, bg) => { const b = H(p, 4, { height: 24, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); T(b, text, { size: 12, weight: 600, color: fg }); };
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const W = 640;

const pageOf = (parent, C, title, desc) => {
  const col = V(parent, 10, { width: W + 40 });
  T(col, title, { size: 16, weight: 700, color: C.ink });
  I(col, { type: 'text', content: desc, fontFamily: F, fontSize: 13, lineHeight: 1.5, fill: fill(C.mute), width: W + 40, textGrowth: 'fixed-width' });
  const pg = V(col, 16, { width: W + 40, padding: 20, fill: fill(C.canvas), cornerRadius: 12 });
  // 页头 + 页签（分段控件直接放在页面上）
  const hd = H(pg, 12, { width: 'fill_container' }); T(hd, '客户管理', { size: 20, weight: 700, color: C.ink }); I(hd, { type: 'frame', width: 'fill_container', height: 1 });
  const btn = H(hd, 6, { height: 36, padding: [0, 14], cornerRadius: 8, fill: fill(C.primary) }); T(btn, '新增客户', { weight: 600, color: C.onPrimary });
  const seg = H(pg, 2, { padding: 3, cornerRadius: 8, fill: fill(C.track) });
  ['全部客户 2', '档案补全', '疑似重复', '公海客户'].forEach((t, i) => { const s = H(seg, 0, { height: 30, padding: [0, 12], cornerRadius: 6, fill: i === 0 ? fill(C.card) : undefined, effects: i === 0 ? [{ type: 'shadow', offsetX: 0, offsetY: 1, blur: 2, spread: 0, color: 'rgba(0,0,0,0.08)' }] : undefined }); T(s, t, { size: 13, weight: i === 0 ? 600 : 400, color: i === 0 ? C.ink : C.sub }); });
  // 数字卡：卡内凹格
  const mc = H(pg, 12, { width: 'fill_container', padding: 16, cornerRadius: 10, fill: fill(C.card), effects: SH });
  [['本月实收', 'CNY 386,420.00'], ['进行中', '18'], ['未指定采购员', '3']].forEach(([a, b]) => { const c = V(mc, 4, { width: 'fill_container', padding: [10, 12], cornerRadius: 8, fill: fill(C.inset) }); T(c, a, { size: 12, color: C.mute }); T(c, b, { size: 18, weight: 700, color: C.ink }); });
  // 主卡 + 表格（收进卡内）
  const main = V(pg, 12, { width: 'fill_container', padding: 16, cornerRadius: 10, fill: fill(C.card), effects: SH });
  T(main, '共 128 条', { color: C.sub });
  const tb = V(main, 0, { width: 'fill_container', cornerRadius: 8, clipContent: true });
  const th = H(tb, 0, { width: 'fill_container', height: 44, fill: fill(C.head) });
  [['客户编码', 140], ['客户名称', 'fill_container'], ['国家/地区', 110]].forEach(([c, w]) => { const x = H(th, 0, { width: w, padding: [0, 12] }); T(x, c, { size: 13, color: C.mute }); });
  [['ACC1118AM', 'PRIME CONTROLS LLC', '美国'], ['ACC1117A', 'Pacific Controls', '澳大利亚']].forEach((r, i) => { I(tb, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) }); const tr = H(tb, 0, { width: 'fill_container', height: 52, fill: fill(i === 1 ? C.hover : C.card) }); [[r[0], 140, MONO], [r[1], 'fill_container'], [r[2], 110]].forEach(([t, w, f]) => { const x = H(tr, 0, { width: w, padding: [0, 12] }); T(x, t, { color: C.ink, font: f, size: f ? 13 : 14 }); }); });
  T(main, '第二行为鼠标悬停', { size: 12, color: C.faint });
  // 订单分组卡：表头贴边（最容易融色的写法）
  const g = V(pg, 0, { width: 'fill_container', cornerRadius: 10, clipContent: true, fill: fill(C.card), effects: SH });
  const gh = H(g, 14, { width: 'fill_container', padding: [14, 16] }); icon(gh, 'chevron-down', 16, C.mute); const a = V(gh, 0, { width: 150 }); T(a, 'SO20261118001', { font: MONO, size: 13, weight: 600, color: C.ink }); T(a, '销售 2026-11-18 · 现货', { size: 12, color: C.mute }); T(gh, 'PRIME CONTROLS LLC', { weight: 600, color: C.ink }); pill(gh, '草稿中 1', C.orange, C.orangeSoft);
  I(g, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) });
  const gth = H(g, 0, { width: 'fill_container', height: 44, fill: fill(C.head) }); [['型号 · 品牌 · 品类', 200], ['描述', 'fill_container']].forEach(([c, w]) => { const x = H(gth, 0, { width: w, padding: [0, 16] }); T(x, c, { size: 13, color: C.mute }); });
  I(g, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) });
  const gr = H(g, 0, { width: 'fill_container', height: 63 }); const c1 = V(gr, 0, { width: 200, padding: [0, 16] }); T(c1, 'E3Z-D61', { font: MONO, size: 13, weight: 600, color: C.ink }); T(c1, 'OMRON · 传感器', { size: 12, color: C.mute }); const c2 = V(gr, 0, { width: 'fill_container', padding: [0, 16] }); T(c2, '光电开关，漫反射 100mm，NPN', { color: C.sub }); T(c2, '备注：NPN 输出，线长 2 米', { size: 12, color: C.orange });
};

const root = I(null, { type: 'frame', name: '表面分层对比', layout: 'vertical', gap: 20, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '全站表面分层：现状 / 调整后（浅色）', { size: 20, weight: 700, color: BASE.ink });
I(root, { type: 'text', content: '规则：页面底最深 → 卡内浅灰（表头、凹格）居中 → 白卡最亮；卡内的灰永远比页面浅，贴到卡边也不会像「露出页面」。参照 Ant Design：页面 #f5f5f5、卡片 #ffffff、表头 #fafafa。页面上直接放的分段控件，轨道用比页面深一档的灰。', fontFamily: F, fontSize: 13, lineHeight: 1.5, fill: fill(BASE.mute), width: 1400, textGrowth: 'fixed-width' });
const row = H(root, 40, { alignItems: 'start' });
pageOf(row, NOW, '现状', '页面底 #F9FAFB；表头 #F4F6F8、凹格 #F1F3F5 都比页面深；分段控件轨道就是页面底色。');
pageOf(row, NEW, '调整后（推荐）', '页面底 #F4F6F8；表头 #F9FAFB、凹格 #F7F8FA 介于页面和白卡之间；分段控件轨道 #E9ECEF。页面上的次要文字对比度 4.51。');
