// 采购需求分组卡（R5）与全站表面分层（R6）对比图，2026-11-24 用户按推荐选定 B3 与调整后的分层；单独用 op design 生成。
const C = {
  ink: '#212B36', sub: '#3E4750', mute: '#637381', faint: '#919EAB', line: '#E5E8EB', head: '#F4F6F8', hover: '#F6F7F8',
  card: '#FFFFFF', canvas: '#F9FAFB', orange: '#9A5B00', orangeSoft: '#FFF5E0', red: '#B71D18', redSoft: '#FFEBE6',
  info: '#006C9C', infoSoft: '#E0F6FA', neutralSoft: '#ECEEF0', link: '#B76E00', primarySoft: '#FFF5E6', primaryLine: '#FEDDAB', control: '#D3D5D8',
};
const F = 'Public Sans', MONO = 'JetBrains Mono';
const fill = (c) => [{ type: 'solid', color: c }];
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: o.font || F, fontSize: o.size || 14, fontWeight: o.weight || 400, lineHeight: o.size === 12 ? 1.5 : 1.57, fill: fill(o.color || C.sub) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const pill = (p, text, fg, bg, ic) => { const b = H(p, 4, { height: 24, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); if (ic) I(b, { type: 'icon_font', iconFontName: ic, width: 12, height: 12, fill: fill(fg) }); T(b, text, { size: 12, weight: 600, color: fg }); };
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const box = (p) => I(p, { type: 'rectangle', width: 16, height: 16, cornerRadius: 4, fill: fill(C.card), stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } });
const W = 600;

const ORDERS = [
  { so: 'SO20261118001', d: '销售 2026-11-18 · 现货', cu: 'PRIME CONTROLS LLC', cty: 'United States', over: '1 个超时', st: '草稿中 1', owner: '业务员 测试管理员',
    lines: [['E3Z-D61', '超时 4 天', 'OMRON · 传感器', '光电开关，漫反射 100mm，NPN', '备注：NPN 输出，线长 2 米', '10', '草稿中 10']] },
  { so: 'SO20261115004', d: '销售 2026-11-15 · 期货', cu: 'Arabian Automation LLC', cty: 'United Arab Emirates', st: '待下单 2', owner: '业务员 Kevin',
    lines: [['6ES7214-1AG40-0XB0', '', '西门子 · PLC', 'CPU 1214C DC/DC/DC', '', '3', '已下单 0'], ['6EP1334-3BA10', '', '西门子 · 电源', 'SITOP PSU100S 24V/10A', '', '5', '已下单 0']] },
];

// 分组头
const head = (p, o, bg, pad = [12, 16]) => {
  const h = V(p, 4, { width: 'fill_container', padding: pad, fill: bg ? fill(bg) : undefined });
  const r = H(h, 14, { width: 'fill_container' });
  icon(r, 'chevron-down', 16, C.mute);
  const a = V(r, 0, { width: 150 }); T(a, o.so, { weight: 600, color: C.ink, font: MONO, size: 13 }); T(a, o.d, { size: 12, color: C.mute });
  const b = V(r, 0, { width: 'fill_container' }); const bl = H(b, 6); T(bl, o.cu, { weight: 600, color: C.ink }); pill(bl, '新客户', C.info, C.infoSoft); T(b, o.cty, { size: 12, color: C.mute });
  if (o.over) pill(r, o.over, C.red, C.redSoft, 'clock');
  pill(r, o.st, C.orange, C.orangeSoft);
  T(h, o.owner, { size: 12, color: C.mute });
};
// 表格（贴边或带内衬）
const table = (p, o, opts = {}) => {
  const t = V(p, 0, { width: 'fill_container', fill: fill(C.card), cornerRadius: opts.radius || 0, clipContent: true, stroke: opts.border ? { thickness: 1, fill: fill(C.line), align: 'inside' } : undefined });
  const hd = H(t, 0, { width: 'fill_container', height: 44, fill: fill(opts.headBg || C.head) });
  const cols = [['', 44], ['型号 · 品牌 · 品类', 170], ['描述', 'fill_container'], ['数量', 80]];
  for (const [c, w] of cols) { const x = H(hd, 0, { width: w, padding: [0, 12] }); if (c) T(x, c, { size: 13, color: C.mute }); else box(x); }
  for (const l of o.lines) {
    I(t, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) });
    const r = H(t, 0, { width: 'fill_container', height: 63, fill: fill(C.card) });
    const c0 = H(r, 0, { width: 44, padding: [0, 12] }); box(c0);
    const c1 = V(r, 0, { width: 170, padding: [0, 12] }); const m = H(c1, 6); T(m, l[0], { font: MONO, size: 13, weight: 600, color: C.ink }); if (l[1]) { const ov = H(m, 3); icon(ov, 'clock', 12, C.red); T(ov, l[1], { size: 12, weight: 600, color: C.red }); } T(c1, l[2], { size: 12, color: C.mute });
    const c2 = V(r, 0, { width: 'fill_container', padding: [0, 12] }); T(c2, l[3]); if (l[4]) T(c2, l[4], { size: 12, color: C.orange });
    const c3 = V(r, 0, { width: 80, padding: [0, 12] }); T(c3, l[5], { color: C.ink }); T(c3, l[6], { size: 12, color: C.mute });
  }
};
const toolbar = (p) => { const r = H(p, 12, { width: 'fill_container', padding: [16, 16, 12, 16] }); T(r, '共 2 张订单', { color: C.sub }); pill(r, '超时未下单 · 1', C.sub, C.neutralSoft); };


const root = I(null, { type: 'frame', name: '分组卡表头对比', layout: 'vertical', gap: 24, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '方案 B 的表头和页面底色融在一起：三种修法', { size: 20, weight: 700, color: C.ink });
T(root, '问题：表头浅灰 #F4F6F8 铺到卡片边缘，卡片浅色下无边框、阴影很淡，表头这一条看不出卡片边界。', { size: 13, color: C.mute });
const row = H(root, 32, { alignItems: 'start' });
const col = (title, desc) => { const c = V(row, 10, { width: W + 32 }); T(c, title, { size: 16, weight: 700, color: C.ink }); I(c, { type: 'text', content: desc, fontFamily: F, fontSize: 13, lineHeight: 1.5, fill: fill(C.mute), width: W + 32, textGrowth: 'fixed-width' }); return V(c, 16, { width: W + 32, padding: 16, fill: fill(C.canvas), cornerRadius: 12 }); };
const o = ORDERS[0];
{
  const page = col('B1 表头改白底', '卡片内整块白色，表头不铺灰，只用次要色小字和下面一条分隔线区分；卡片边界在任何高度都清楚。和主列表的区别：主列表表头仍是浅灰圆角条。');
  const g = V(page, 0, { width: 'fill_container', fill: fill(C.card), cornerRadius: 10, clipContent: true, effects: SH }); head(g, o, C.card, [14, 16]); I(g, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) }); table(g, o, { headBg: C.card });
}
{
  const page = col('B2 卡片加细边框', '保留浅灰表头，卡片加 1px 浅灰边框和阴影，把边界画出来。浅色下全站卡片原本不用边框，这里会和别的卡不一致。');
  const g = V(page, 0, { width: 'fill_container', fill: fill(C.card), cornerRadius: 10, clipContent: true, effects: SH, stroke: { thickness: 1, fill: fill(C.line), align: 'inside' } }); head(g, o, C.card, [14, 16]); I(g, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) }); table(g, o);
}
{
  const page = col('B3 表格收进卡内留白（推荐）', '和主列表一样：表格左右留 16，表头是浅灰圆角条，四周都有白边包住。全站表格只有一种样子，边界也清楚；代价是每张订单卡左右、底部各多 16。');
  const g = V(page, 0, { width: 'fill_container', fill: fill(C.card), cornerRadius: 10, clipContent: true, effects: SH }); head(g, o, C.card, [14, 16]); const w = V(g, 0, { width: 'fill_container', padding: [0, 16, 16, 16] }); table(w, o, { radius: 8 });
}
