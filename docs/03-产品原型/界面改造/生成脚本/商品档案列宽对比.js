// 商品档案列宽对比图（R12）：现状 / 推荐 / 备选，1366 宽下表格可见 1043px。单独用 op design 生成。
const C = {
  ink: '#212B36', sub: '#3E4750', mute: '#637381', faint: '#919EAB', line: '#E5E8EB', head: '#F9FAFB', inset: '#F7F8FA',
  card: '#FFFFFF', canvas: '#F4F6F8', primary: '#FDA92D', primarySoft: '#FFF5E6', link: '#B76E00', track: '#E9ECEF',
  orange: '#9A5B00', orangeSoft: '#FFF5E0', green: '#0D7A4A', greenSoft: '#E3F6EA', red: '#B71D18', redSoft: '#FFEBE6', redMain: '#C4302B',
  info: '#006C9C', infoSoft: '#E0F6FA', neutralSoft: '#ECEEF0', control: '#D3D5D8', bar: '#C4CDD5',
};
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color || C.sub), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const pill = (p, text, fg, bg, ic) => { const b = H(p, 4, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); if (ic) icon(b, ic, 12, fg); T(b, text, { size: 12, weight: 600, color: fg }); return b; };
const sp = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1 });
const hr = (p) => I(p, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) });
const btn = (p, text, o = {}) => { const b = H(p, 4, { height: 30, padding: [0, 12], cornerRadius: 6, fill: fill(o.solid ? C.primary : C.card), stroke: o.solid ? undefined : { thickness: 1, fill: fill(C.control), align: 'inside' } }); if (o.ic) icon(b, o.ic, 13, o.solid ? C.ink : C.sub); T(b, text, { size: 13, color: C.ink }); if (o.caret) icon(b, 'chevron-down', 12, C.mute); return b; };
const box = (p) => I(p, { type: 'rectangle', width: 16, height: 16, cornerRadius: 4, fill: fill(C.card), stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } });
const W = 1180;


const VIS = 1043; // 1366 宽下表格可见宽度（实测）
const ROWS = [
  { m: 'ATV320U15N4B', n: '变频器 ATV320 1.5kW 三相 380V', b: 'Schneider Electric', c: '变频器与驱动', q: '0', d: '0', done: 2, cr: ['系统', '11-17 20:00'], up: ['platform-test', '11-24 16:56'] },
  { m: 'E3Z-D61', n: '光电传感器 漫反射型', b: 'OMRON', c: '传感器', q: '0', d: '0', done: 8, cr: ['系统', '11-17 20:00'], up: ['platform-test', '11-24 16:56'] },
  { m: '6ES7214-1AG40-0XB0', n: 'S7-1200 CPU 1214C DC/DC/DC', b: 'Siemens', c: 'PLC 与控制器', q: '0', d: '0', done: 1, cr: ['系统', '11-17 20:00'], up: ['platform-test', '11-24 16:56'] },
];
const root = I(null, { type: 'frame', name: '商品档案列宽对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '商品档案列宽：现状 / 推荐 / 备选（浅色，1366 宽，表格可见 1043px）', { size: 20, weight: 700, color: C.ink });
const variant = (title, note, good) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: C.ink }); if (good) pill(h, '推荐', C.green, C.greenSoft);
  T(col, note, { size: 13, color: C.sub, w: W });
  return V(col, 0, { width: W, padding: 24, cornerRadius: 12, fill: fill(C.canvas) });
};
const bar = (p, done, w) => {
  const g = H(p, 6, { width: w });
  const segs = H(g, 2, { width: 'fill_container' });
  // 只用主题色（琥珀），完成度越高越实：35% 起，补齐为原色（D48）
  const a = 0.35 + 0.65 * done / 10;
  const mixc = (hex, al) => '#' + [1, 3, 5].map((k) => Math.round(parseInt(hex.slice(k, k + 2), 16) * al + 255 * (1 - al)).toString(16).padStart(2, '0')).join('');
  for (let i = 0; i < 10; i++) I(segs, { type: 'rectangle', width: 'fill_container', height: 6, cornerRadius: 3, fill: fill(i < done ? mixc(C.primary, a) : C.neutralSoft) });
  T(g, `${done}/10`, { size: 12, weight: 600, color: done >= 8 ? C.ink : C.sub });
};
const table = (pg, cols) => {
  const card = V(pg, 0, { width: VIS + 40, padding: [16, 20], cornerRadius: 10, fill: fill(C.card), effects: SH });
  const qb = H(card, 8, { width: 'fill_container', padding: [0, 0, 12, 0] }); T(qb, '共 3 条', { color: C.sub });
  ['缺图片 3', '缺物流 2', '缺海关 2', '缺参考价 2', '品类未细分 3'].forEach((t) => { const b = H(qb, 0, { height: 26, padding: [0, 10], cornerRadius: 13, fill: fill(C.neutralSoft) }); T(b, t, { size: 12, color: C.sub }); });
  const clip = V(card, 0, { width: VIS, clipContent: true });
  const total = cols.reduce((s, c) => s + c.w, 0);
  const inner = V(clip, 0, { width: Math.max(total, VIS) });
  const hr0 = H(inner, 0, { width: 'fill_container', height: 40, fill: fill(C.head) });
  cols.forEach((c) => { const th = H(hr0, 4, { width: c.w, height: 40, padding: [0, 10] }); T(th, c.h, { size: 13, weight: 600, color: C.sub }); if (c.sort) icon(th, 'chevrons-up-down', 12, C.faint); });
  ROWS.forEach((r) => {
    I(inner, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) });
    const tr = H(inner, 0, { width: 'fill_container', height: 56 });
    cols.forEach((c) => { const td = V(tr, 2, { width: c.w, height: 56, padding: [0, 10], justifyContent: 'center' }); c.r(td, r); });
  });
  if (total > VIS) {
    const sb = H(card, 0, { width: VIS, height: 8, padding: [2, 0], fill: fill(C.head) });
    I(sb, { type: 'rectangle', width: Math.round(VIS * VIS / total), height: 4, cornerRadius: 2, fill: fill(C.bar) });
    T(card, `表格内容 ${total}px，看见 ${VIS}px；右边 ${total - VIS}px 要横向滚动才能看到`, { size: 12, color: C.red });
  }
};
const tx = (p, s, o = {}) => T(p, s, { size: 13, color: C.ink, ...o });
const sub = (p, s) => T(p, s, { size: 12, color: C.mute });
const two = (p, a, b, bold) => { tx(p, a, bold ? { weight: 700 } : {}); sub(p, b); };
const lc = (p) => pill(p, '在产', C.green, C.greenSoft);
// 列表行里的状态开关：公共 StatusSwitch，小号 28×16，跟主题色（D48）
const sw = (p) => { const t = I(p, { type: 'frame', layout: 'horizontal', width: 28, height: 16, cornerRadius: 8, padding: 2, justifyContent: 'end', fill: fill(C.primary) }); I(t, { type: 'ellipse', width: 12, height: 12, fill: fill('#FFFFFF') }); };
const audit = (k) => ({ h: k === 'cr' ? '创建' : '更新', w: 120, r: (td, r) => two(td, r[k][0], r[k][1]) });

{
  const pg = variant('A 现状：11 列 1530px', '型号、品牌、品类、生命周期、询盘次数、成交次数、档案完整度、维护方、状态、创建、更新各占一列，询盘、成交两列都是 120 宽却只放一个数字。1366 宽要横向滚动 487px，完整度只露出一半，维护方、状态、创建、更新都在屏幕外。', false);
  table(pg, [
    { h: '商品', w: 280, r: (td, r) => two(td, r.m, r.n, true) },
    { h: '品牌', w: 150, r: (td, r) => tx(td, r.b) },
    { h: '品类', w: 130, r: (td, r) => tx(td, r.c) },
    { h: '生命周期', w: 120, r: (td) => lc(td) },
    { h: '询盘次数', w: 120, sort: true, r: (td, r) => tx(td, r.q, { color: C.mute }) },
    { h: '成交次数', w: 120, sort: true, r: (td, r) => tx(td, r.d, { color: C.mute }) },
    { h: '档案完整度', w: 190, sort: true, r: (td, r) => bar(td, r.done, 170) },
    { h: '维护方', w: 90, r: (td) => pill(td, '平台', C.sub, C.neutralSoft) },
    { h: '状态', w: 90, r: (td) => sw(td) },
    audit('cr'), audit('up'),
  ]);
}
{
  const pg = variant('B 推荐：合并相关列，1366 宽一屏放下', '品牌、品类合成一列两行（品牌上、品类下）；生命周期标签放到型号后面（同商品详情页头）；询盘、成交合成「询盘 / 成交」一列，表头可切换按哪个排序；完整度条缩到 120 宽。共 8 列约 1030px，1366 宽不出现横向滚动，创建、更新两列（全站规范必显）也在屏内；更宽的屏幕上各列按比例放宽。', true);
  table(pg, [
    { h: '商品', w: 250, r: (td, r) => { const l = H(td, 6); tx(l, r.m, { weight: 700 }); pill(l, '在产', C.green, C.greenSoft); sub(td, r.n); } },
    { h: '品牌 / 品类', w: 160, r: (td, r) => two(td, r.b, r.c) },
    { h: '询盘 / 成交', w: 100, sort: true, r: (td, r) => tx(td, `${r.q} / ${r.d}`, { color: C.mute }) },
    { h: '完整度', w: 120, sort: true, r: (td, r) => bar(td, r.done, 100) },
    { h: '维护方', w: 80, r: (td) => pill(td, '平台', C.sub, C.neutralSoft) },
    { h: '状态', w: 80, r: (td) => sw(td) },
    audit('cr'), audit('up'),
  ]);
}
{
  const pg = variant('C 备选：列不变，收窄并冻结商品列', '保留 11 列，只把窄内容的列收窄（询盘、成交各 80，完整度 130），完整度挪到商品后面，商品列冻结在左侧。仍要横向滚动约 220px，创建、更新、状态在屏外；改动最小，但「一屏看全」的问题没解决。', false);
  table(pg, [
    { h: '商品', w: 240, r: (td, r) => two(td, r.m, r.n, true) },
    { h: '档案完整度', w: 130, sort: true, r: (td, r) => bar(td, r.done, 110) },
    { h: '品牌', w: 130, r: (td, r) => tx(td, r.b) },
    { h: '品类', w: 110, r: (td, r) => tx(td, r.c) },
    { h: '生命周期', w: 90, r: (td) => lc(td) },
    { h: '询盘次数', w: 80, sort: true, r: (td, r) => tx(td, r.q, { color: C.mute }) },
    { h: '成交次数', w: 80, sort: true, r: (td, r) => tx(td, r.d, { color: C.mute }) },
    { h: '维护方', w: 80, r: (td) => pill(td, '平台', C.sub, C.neutralSoft) },
    { h: '状态', w: 80, r: (td) => sw(td) },
    audit('cr'), audit('up'),
  ]);
}
