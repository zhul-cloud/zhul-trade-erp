// 主题色对比板；令牌与组件函数与销售订单详情.js 相同
const CN = 'Noto Sans SC';
const NUM = 'Public Sans';
const MONO = 'Roboto Mono';
const solid = (c) => [{ type: 'solid', color: c }];
const P = '#FDA92D';
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 3, blur: 1, spread: -2, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 2, blur: 2, spread: 0, color: 'rgba(145,158,171,0.14)' }, { type: 'shadow', offsetX: 0, offsetY: 1, blur: 5, spread: 0, color: 'rgba(145,158,171,0.12)' }];
const LIGHT = { key: 'light', page: '#F9FAFB', card: '#FFFFFF', head: '#F4F6F8', line: '#E5E8EB', field: '#D4D6D9', ink: '#212B36', body: '#3E4750', sub: '#637381', faint: '#919EAB', navOn: '#EDEEEF', soft: '#FEF5E6', tonal: '#F1F3F5', shadow: SH, stroke: null,
  st: { ok: ['#22C55E', '#E5F8EC'], warn: ['#FFAB00', '#FFF5E0'], info: ['#00B8D9', '#E0F6FA'], err: ['#FF5630', '#FFEBE6'], neu: ['#637381', '#ECEEF0'] } };
const DARK = { key: 'dark', page: '#0A0A0A', card: '#1A1A1A', head: '#222222', line: '#2A2A2A', field: '#4A4A4A', ink: '#F5F5F5', body: '#E0E0E0', sub: '#A0A0A0', faint: '#6E6E6E', navOn: '#2C2C2C', soft: '#3E311D', tonal: '#232323', shadow: [], stroke: { thickness: 1, fill: solid('#2A2A2A') },
  st: { ok: ['#22C55E', '#1B3525'], warn: ['#FFAB00', '#3F3116'], info: ['#00B8D9', '#163339'], err: ['#FF5630', '#3F241E'], neu: ['#A0A0A0', '#2F2F2F'] } };
// 货物进度条：图表系列色（2026-11-20 用户确认）
const G = { shipped: '#2E9A62', handed: '#2F9E99', inWh: '#3B82F6', transit: '#B87A14', pendShip: '#E8664A', pendBuy: '#7A8794' };

function mkIcon(p, n, s, c, inline) {
  const node = { type: 'icon_font', name: 'fill=' + c, iconFontName: n, width: s, height: s, fill: solid(c) };
  if (inline === false) return I(p, node);
  const w = I(p, { type: 'frame', name: 'icon', width: s, height: s + 2, layout: 'vertical', justifyContent: 'end' });
  return I(w, node);
}

function kit(T) {
  const tx = (p, content, o) => { const n = Object.assign({ type: 'text', content, fontSize: 14, fontFamily: CN, fill: solid(T.body) }, o || {}); n.name = 'fill=' + n.fill[0].color; return I(p, n); };
  const box = (p, o) => I(p, Object.assign({ type: 'frame', width: 'fit_content', height: 'fit_content' }, o || {}));
  const icon = (p, n, s, c, inline) => mkIcon(p, n, s, c, inline);
  const hr = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
  const card = (p, o) => box(p, Object.assign({ fill: solid(T.card), cornerRadius: 10, effects: T.shadow }, T.stroke ? { stroke: T.stroke } : {}, o));
  const label = (p, t, tone, ic) => { const b = box(p, { layout: 'horizontal', gap: 4, alignItems: 'center', padding: [3, 8], cornerRadius: 4, fill: solid(tone[1]) }); if (ic) icon(b, ic, 12, tone[0]); tx(b, t, { fontSize: 12, fill: solid(tone[0]) }); return b; };
  const primaryBtn = (p, t, ic) => { const b = box(p, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 36, padding: [0, 16], cornerRadius: 8, fill: solid(P), effects: T.key === 'light' ? SH : [] }); if (ic) icon(b, ic, 18, '#FFFFFF'); tx(b, t, { fontWeight: 500, fill: solid('#FFFFFF') }); return b; };
  // 次要按钮：PrimeDash 文字按钮；disabled 用禁用灰
  const textBtn = (p, t, ic, c) => { const col = c || P; const b = box(p, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 36, padding: [0, 10], cornerRadius: 8 }); if (ic) icon(b, ic, 18, col); tx(b, t, { fontWeight: 500, fill: solid(col) }); return b; };
  // 卡片标题：18/500，可带帮助图标、标签、右侧内容
  const cardHead = (p, title, o) => {
    o = o || {};
    const h = box(p, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' });
    tx(h, title, { fontSize: 18, fontWeight: 500, fill: solid(T.ink) });
    if (o.help) icon(h, 'info', 14, T.sub);
    for (const l of o.labels || []) label(h, l[0], l[1], l[2]);
    const r = box(h, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', alignItems: 'center', gap: 8 });
    return r;
  };
  // 「标签：数值」行：PrimeDash Price & Taxes，行高 52、14px、底部分隔线
  const kv = (p, k, v, o) => {
    o = o || {};
    const r = box(p, { width: 'fill_container', height: o.h || 44, layout: 'horizontal', alignItems: 'center', gap: 12, padding: [0, 4] });
    tx(r, k, { fill: solid(T.sub), width: o.kw || 96 });
    const vv = box(r, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center', justifyContent: o.right ? 'end' : 'start' });
    if (typeof v === 'string') tx(vv, v, Object.assign({ fontFamily: o.num ? NUM : CN, fill: solid(T.ink) }, o.v || {}));
    else v(vv);
    if (!o.last) hr(p);
    return vv;
  };
  return { tx, box, icon, hr, card, label, primaryBtn, textBtn, cardHead, kv };
}


// ———— 主题色对比板：6 种预设（PrimeDash 色相）在浅色、深色下各一张；取值见 design.md 第 2 节预设表
const PRESETS = [
  { n: '琥珀', c: '#FDA92D', def: 1, soft: '#FFF5E6', dl: '#FDA92D', ds: '#3E311D', note: '与警告色相近，警告靠图标和浅底标签区分' },
  { n: '青绿', c: '#0D9394', soft: '#E2F2F2', dl: '#0D9394', ds: '#182D2E', note: '与成功色同为绿系，成功标签靠浅底区分' },
  { n: '天蓝', c: '#078DEE', soft: '#E1F1FD', dl: '#078DEE', ds: '#172C3C', note: '与信息色同为蓝绿系，信息标签靠浅底区分' },
  { n: '紫', c: '#7635DC', soft: '#EFE7FB', dl: '#9969E5', ds: '#291E39', note: '与各状态色都分得开' },
  { n: '宝蓝', c: '#2065D1', soft: '#E4EDF9', dl: '#4482E2', ds: '#1B2637', note: '与各状态色都分得开' },
  { n: '红', c: '#FF3030', soft: '#FFE6E6', dl: '#FF5050', ds: '#3F1E1E', note: '与错误色相近，危险操作靠描边按钮和确认框区分' },
];

function presetCell(T, parent, pr) {
  const { tx, box, icon, card, label } = kit(T);
  const C = pr.c; const L = T.key === 'light' ? pr.c : pr.dl; const S = T.key === 'light' ? pr.soft : pr.ds;
  const c = card(parent, { width: 'fill_container', layout: 'vertical', gap: 14, padding: [18, 20] });
  // 标题：预设名 + 色值
  const h = box(c, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' });
  I(h, { type: 'ellipse', width: 14, height: 14, fill: solid(C) });
  tx(h, pr.n, { fontSize: 16, fontWeight: 600, fill: solid(T.ink) });
  tx(h, C, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
  if (pr.def) label(h, '默认', [T.sub, T.st.neu[1]]);
  // Logo 与侧栏选中
  const r1 = box(c, { width: 'fill_container', layout: 'horizontal', gap: 16, alignItems: 'start' });
  const nav = box(r1, { width: 190, layout: 'vertical', gap: 2 });
  const brand = box(nav, { layout: 'horizontal', gap: 8, alignItems: 'center', padding: [0, 6, 8, 6] });
  const lg = box(brand, { width: 24, height: 24, cornerRadius: 6, fill: solid(C), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }); tx(lg, '烛', { fontSize: 12, fontWeight: 700, fill: solid('#FFFFFF') });
  tx(brand, '烛龙ERP', { fontSize: 15, fontWeight: 700, fill: solid(L) });
  const g = box(nav, { width: 'fill_container', height: 32, layout: 'horizontal', gap: 8, alignItems: 'center', padding: [0, 8], cornerRadius: 6, fill: solid(T.navOn) }); icon(g, 'briefcase', 16, L); tx(g, '销售管理', { fontSize: 13, fill: solid(L), width: 'fill_container' }); icon(g, 'chevron-down', 14, L);
  const on = box(nav, { width: 'fill_container', height: 32, layout: 'horizontal', gap: 8, alignItems: 'center', padding: [0, 8, 0, 12], cornerRadius: 6, fill: solid(S) }); icon(on, 'circle-dot', 10, L); tx(on, '销售订单', { fontSize: 13, fill: solid(L) });
  const off = box(nav, { width: 'fill_container', height: 32, layout: 'horizontal', gap: 8, alignItems: 'center', padding: [0, 8, 0, 12] }); icon(off, 'circle', 10, T.faint); tx(off, '售后管理', { fontSize: 13, fill: solid(T.body) });
  // 页签、按钮、链接、快捷筛选
  const rt = box(r1, { width: 'fill_container', layout: 'vertical', gap: 12 });
  const tabs = box(rt, { layout: 'horizontal', gap: 4 });
  for (const [t, a] of [['工作台', 0], ['销售订单', 1]]) { const tb = box(tabs, { layout: 'vertical', gap: 6 }); tx(tb, t, { fontSize: 13, fontWeight: 500, fill: solid(a ? L : T.body) }); I(tb, { type: 'frame', width: 'fill_container', height: 2, fill: solid(a ? L : T.card) }); box(tabs, { width: 8 }); }
  const bt = box(rt, { layout: 'horizontal', gap: 6, alignItems: 'center' });
  const pb = box(bt, { layout: 'horizontal', gap: 4, alignItems: 'center', height: 32, padding: [0, 12], cornerRadius: 8, fill: solid(C), effects: T.key === 'light' ? SH : [] }); icon(pb, 'send', 14, '#FFFFFF'); tx(pb, '发货通知', { fontSize: 13, fontWeight: 500, fill: solid('#FFFFFF') });
  const tbn = box(bt, { layout: 'horizontal', gap: 4, alignItems: 'center', height: 32, padding: [0, 6] }); icon(tbn, 'sliders-horizontal', 14, L); tx(tbn, '更多筛选', { fontSize: 13, fontWeight: 500, fill: solid(L) });
  const lk = box(rt, { layout: 'horizontal', gap: 10, alignItems: 'center' });
  tx(lk, 'SO20261119004', { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(L) });
  const chip = box(lk, { layout: 'horizontal', gap: 4, alignItems: 'center', height: 26, padding: [0, 10, 0, 6], cornerRadius: 999, fill: solid(S) }); icon(chip, 'check', 12, L); tx(chip, '毛利为负', { fontSize: 12, fontWeight: 500, fill: solid(L) });
  // 步骤条与收款进度
  const r2 = box(c, { width: 'fill_container', layout: 'horizontal', gap: 16, alignItems: 'center' });
  const st = box(r2, { layout: 'horizontal', gap: 6, alignItems: 'center' });
  [1, 2, 3].forEach((n, i) => { if (i) I(st, { type: 'frame', width: 18, height: 1, fill: solid(T.line) }); const d = box(st, { width: 22, height: 22, cornerRadius: 11, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(i < 2 ? C : (T.key === 'light' ? '#EFF0F1' : '#2C2C2C')) }); tx(d, String(n), { fontSize: 11, fontFamily: NUM, fill: solid(i < 2 ? '#FFFFFF' : T.sub) }); });
  const pg = box(r2, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' });
  const bar = box(pg, { width: 'fill_container', height: 6, cornerRadius: 3, fill: solid(T.line), layout: 'horizontal', clipContent: true }); I(bar, { type: 'frame', width: 50, height: 6, fill: solid(C) });
  tx(pg, '30%', { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
  // 状态标签（不随主题色变化）
  const r3 = box(c, { width: 'fill_container', layout: 'horizontal', gap: 6, alignItems: 'center' });
  tx(r3, '状态色不变', { fontSize: 12, fill: solid(T.sub) });
  label(r3, '已到账', T.st.ok); label(r3, '待到账', T.st.warn, 'triangle-alert'); label(r3, '已下单', T.st.info); label(r3, '已过期', T.st.err);
  if (pr.note) { const nt = box(c, { layout: 'horizontal', gap: 6, alignItems: 'center' }); icon(nt, 'info', 12, T.sub); tx(nt, pr.note, { fontSize: 12, fill: solid(T.sub) }); }
}

function presetBoard(T, name) {
  const root = I(null, { type: 'frame', name, width: 1440, height: 790, layout: 'vertical', gap: 20, padding: [24, 24], fill: solid(T.page) });
  const { tx, box } = kit(T);
  tx(root, T.key === 'light' ? '主题色对比 · 浅色' : '主题色对比 · 深色', { fontSize: 20, fontWeight: 600, fill: solid(T.ink) });
  for (let r = 0; r < 2; r++) {
    const row = box(root, { width: 'fill_container', layout: 'horizontal', gap: 20 });
    for (const pr of PRESETS.slice(r * 3, r * 3 + 3)) presetCell(T, row, pr);
  }
}

presetBoard(LIGHT, 'E1 主题色对比 · 浅色');
presetBoard(DARK, 'E2 主题色对比 · 深色');
