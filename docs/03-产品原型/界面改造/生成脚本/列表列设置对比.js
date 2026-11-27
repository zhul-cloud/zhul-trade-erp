// 列表列宽对比图（R15）：以销售订单列表为例（全站最宽，16 列 2546px），1366 宽表格可见 1043px。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const POP = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.24)' }, { type: 'shadow', offsetX: -20, offsetY: 20, blur: 40, spread: -4, color: 'rgba(145,158,171,0.24)' }];
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', FAINT = '#919EAB', LINE = '#E5E8EB', HEAD = '#F9FAFB', CANVAS = '#F4F6F8', PRIMARY = '#FDA92D', LINK = '#B76E00', CTRL = '#D3D5D8';
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color || SUB), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const sp = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1 });
const pill = (p, t, fg, bg) => { const b = H(p, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); T(b, t, { size: 12, weight: 600, color: fg }); };
const W = 1180, VIS = 1043;

// 真实数据（本机 SO20261118001）+ 第二行示意
const ROWS = [
  { no: 'SO20261118001', d: '2026-11-18 · 现货', c: 'PRIME CONTROLS LLC', cs: '新客户 · 美国', st: ['待采购', '#56636F', '#ECEEF0'], amt: 'USD 532.00', pay: [0, '待到账'], goods: [2, 30, '还有型号没下采购订单'], model: 'LC2D18B7C', ms: '共 2 个型号 · 30 件', brand: 'Schneider Electric、OMRON', cat: '接触器、传感器', gp: 'CNY 1,013.80', gpr: '26.65% · 未结算', buyer: '测试管理员', src: 'PI20261118001', sales: '测试管理员', cr: ['系统', '11-18 02:07'], up: ['测试管理员', '11-18 02:07'] },
  { no: 'SO20261117004', d: '2026-11-17 · 期货', c: 'Pacific Controls', cs: '老客户 · 澳大利亚', st: ['已下单', '#006C9C', '#D6F4F9'], amt: 'USD 1,280.00', pay: [50, '部分到账'], goods: [12, 20, '在途最早 11-28 到'], model: 'E3Z-D61', ms: '共 1 个型号 · 20 件', brand: 'OMRON', cat: '传感器', gp: 'CNY 2,104.60', gpr: '23.10% · 未结算', buyer: '测试管理员', src: 'PI20261116002', sales: '测试管理员', cr: ['测试管理员', '11-17 10:20'], up: ['测试管理员', '11-21 15:42'] },
];
const two = (td, a, b, o = {}) => { T(td, a, { size: 13, color: o.c || INK, weight: o.b ? 600 : 400 }); if (b) T(td, b, { size: 12, color: MUTE }); };
const COLS = {
  no: { h: '订单编号 · 销售日期', w: 170, r: (td, r) => two(td, r.no, r.d, { c: LINK }) },
  cust: { h: '客户', w: 200, r: (td, r) => two(td, r.c, r.cs, { b: true }) },
  st: { h: '订单状态', w: 110, r: (td, r) => pill(td, ...r.st) },
  amt: { h: '合计', w: 130, r: (td, r) => T(td, r.amt, { weight: 600, color: INK }) },
  pay: { h: '收款进度', w: 150, r: (td, r) => { const b = H(td, 6); const tr = H(b, 0, { width: 90, height: 6, cornerRadius: 3, fill: fill('#ECEEF0') }); if (r.pay[0]) I(tr, { type: 'rectangle', width: 90 * r.pay[0] / 100, height: 6, cornerRadius: 3, fill: fill(PRIMARY) }); T(b, `${r.pay[0]}%`, { size: 12, color: MUTE }); pill(td, r.pay[1], '#B76E00', '#FFF2D6'); } },
  goods: { h: '货物', w: 190, r: (td, r) => { T(td, `${r.goods[0]}/${r.goods[1]} 件`, { size: 13, color: INK }); T(td, r.goods[2], { size: 12, color: MUTE }); } },
  model: { h: '型号 · 数量', w: 190, r: (td, r) => two(td, r.model, r.ms) },
  brand: { h: '品牌', w: 170, r: (td, r) => T(td, r.brand, { color: INK }) },
  cat: { h: '品类', w: 170, r: (td, r) => T(td, r.cat, { color: INK }) },
  gp: { h: '毛利', w: 150, r: (td, r) => two(td, r.gp, r.gpr) },
  buyer: { h: '采购员', w: 140, r: (td, r) => T(td, r.buyer, { color: INK }) },
  src: { h: '来源单号', w: 200, r: (td, r) => T(td, r.src, { color: LINK }) },
  sales: { h: '业务员', w: 90, r: (td, r) => T(td, r.sales, { color: INK }) },
  cr: { h: '创建', w: 120, r: (td, r) => two(td, r.cr[0], r.cr[1]) },
  up: { h: '更新', w: 120, r: (td, r) => two(td, r.up[0], r.up[1]) },
  op: { h: '操作', w: 96, r: (td) => { const b = H(td, 12); icon(b, 'eye', 16, MUTE); icon(b, 'copy', 16, MUTE); } },
};

// 按实测列宽（现状）覆盖默认宽度
const wd = (k, w) => ({ ...COLS[k], w });
const root = I(null, { type: 'frame', name: '列表列设置对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '列表列宽：现状 / 推荐 / 备选（以销售订单为例，1366 宽，表格可见 1043px；第二行为示意数据）', { size: 20, weight: 700, color: INK });
const variant = (title, note, good) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK }); if (good) pill(h, '推荐', '#118D57', '#DCF6E5');
  T(col, note, { size: 13, color: SUB, w: W });
  return V(col, 0, { width: W, padding: 24, cornerRadius: 12, fill: fill(CANVAS) });
};

const table = (pg, keys, opts = {}) => {
  const card = V(pg, 0, { width: VIS + 40, padding: [16, 20], cornerRadius: 10, fill: fill('#FFFFFF'), effects: SH });
  const tb = H(card, 8, { width: 'fill_container', padding: [0, 0, 12, 0] }); T(tb, '共 2 条', { color: SUB });
  const q = H(tb, 0, { height: 26, padding: [0, 10], cornerRadius: 13, fill: fill('#ECEEF0') }); T(q, '毛利为负', { size: 12, color: SUB });
  sp(tb);
  if (opts.settings) { const b = H(tb, 6, { height: 32, padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: fill(CTRL), align: 'inside' }, fill: fill('#FFFFFF') }); icon(b, 'columns-3', 14, SUB); T(b, '列', { color: INK }); T(b, opts.settings, { size: 12, color: MUTE }); }
  const cols = keys.map((k) => (typeof k === 'string' ? COLS[k] : k));
  const total = cols.reduce((s, c) => s + c.w, 0);
  const clip = V(card, 0, { width: VIS, clipContent: true });
  const inner = V(clip, 0, { width: Math.max(total, VIS) });
  const stretch = total < VIS ? VIS / total : 1;
  const hr0 = H(inner, 0, { width: 'fill_container', height: 40, fill: fill(HEAD) });
  cols.forEach((c) => { const th = H(hr0, 4, { width: Math.round(c.w * stretch), height: 40, padding: [0, 10] }); T(th, c.h, { size: 13, weight: 600, color: MUTE }); });
  ROWS.forEach((r) => {
    I(inner, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(LINE) });
    const tr = H(inner, 0, { width: 'fill_container', height: 64 });
    cols.forEach((c) => { const td = V(tr, 2, { width: Math.round(c.w * stretch), height: 64, padding: [0, 10], justifyContent: 'center' }); c.r(td, r); });
  });
  if (total > VIS) {
    const sb = H(card, 0, { width: VIS, height: 8, padding: [2, 0], fill: fill(HEAD) });
    I(sb, { type: 'rectangle', width: Math.round(VIS * VIS / total), height: 4, cornerRadius: 2, fill: fill('#C4CDD5') });
    T(card, `表格内容 ${total}px，看见 ${VIS}px；右边 ${total - VIS}px 要横向滚动`, { size: 12, color: '#B71D18' });
  } else T(card, `表格内容 ${total}px，一屏放下`, { size: 12, color: MUTE });
  return card;
};

{
  const pg = variant('A 现状：16 列全部默认显示，2546px', '每个字段默认一列，再加规范要求的创建、更新两列。1366 宽只看得到前 6 列，型号、品牌、品类、毛利、采购员、来源单号、业务员、创建、更新都要横向滚动才能看到；全站 34 个列表里约 26 个是这种情况，最宽的 2556px。', false);
  table(pg, ['no', wd('cust', 240), wd('st', 130), wd('amt', 150), wd('pay', 180), wd('goods', 230), 'model', 'brand', 'cat', 'gp', 'buyer', 'src', 'sales', 'cr', 'up', 'op']);
}
{
  const pg = variant('B 推荐：默认只显示核心列，其余放进「列设置」', '默认 7 列（单号、客户、状态、合计、收款、货物、操作），1366 宽一屏放下；其余 9 列在工具栏右侧「列」里勾选打开，按页面、按用户记住。创建、更新两列默认收进列设置（规范改为「必须能打开」，排序仍按更新时间倒序）。参照 Linear、Notion、Odoo、NetSuite 的做法：默认视图只放判断和操作需要的列，研究型的列由用户自己加。', true);
  const card = table(pg, ['no', wd('cust', 196), 'st', 'amt', wd('pay', 150), wd('goods', 186), 'op'], { settings: '7/16' });
  // 列设置弹层
  const pop = V(pg, 8, { width: 300, padding: [12, 16], cornerRadius: 12, fill: fill('#FFFFFF'), effects: POP });
  const ph = H(pop, 8, { width: 'fill_container' }); T(ph, '显示的列', { weight: 600, color: INK }); sp(ph); T(ph, '恢复默认', { size: 12, color: LINK });
  const box = (p, on, dis) => { const b = H(p, 0, { width: 16, height: 16, cornerRadius: 4, justifyContent: 'center', fill: fill(on ? (dis ? '#C4CDD5' : PRIMARY) : '#FFFFFF'), stroke: on ? undefined : { thickness: 1, fill: fill(CTRL), align: 'inside' } }); if (on) icon(b, 'check', 12, '#212B36'); };
  [['订单编号 · 销售日期', true, true], ['客户', true], ['订单状态', true], ['合计', true], ['收款进度', true], ['货物', true]].forEach(([t, on, dis]) => { const r = H(pop, 8); icon(r, 'grip-vertical', 14, FAINT); box(r, on, dis); T(r, t, { color: dis ? MUTE : INK }); });
  I(pop, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(LINE) });
  ['型号 · 数量', '品牌', '品类', '毛利', '采购员', '来源单号', '业务员', '创建', '更新'].forEach((t) => { const r = H(pop, 8); icon(r, 'grip-vertical', 14, FAINT); box(r, false); T(r, t, { color: INK }); });
  T(pop, '只对你自己生效，下次打开还在', { size: 12, color: MUTE });
}
{
  const pg = variant('C 备选：不加列设置，合并成两行单元格', '全部信息仍默认显示，相关字段合进同一格两行：型号下面写品牌、品类，合计下面写毛利，业务员和采购员一列，创建、更新合成一列。11 列约 1756px，仍要横向滚动约 710px，每格变成两行，信息更密、更难扫。', false);
  table(pg, ['no', 'cust', 'st',
    { h: '合计 · 毛利', w: 150, r: (td, r) => two(td, r.amt, `毛利 ${r.gp}`) },
    'pay', 'goods',
    { h: '型号 · 品牌 · 品类', w: 220, r: (td, r) => two(td, r.model, `${r.brand} · ${r.cat}`) },
    { h: '业务员 / 采购员', w: 130, r: (td, r) => two(td, r.sales, r.buyer) },
    'src',
    { h: '创建 / 更新', w: 140, r: (td, r) => two(td, `${r.cr[0]} ${r.cr[1]}`, `${r.up[0]} ${r.up[1]}`) },
    'op']);
}
