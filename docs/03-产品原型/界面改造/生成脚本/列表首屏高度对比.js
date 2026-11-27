// 列表首屏能看几行（R35，UI-117）：1366×768 下销售订单列表表头落在 y=408，按两行行高 64 只看得到 4 行，
// 规范要求不少于 5 行；8 个付费模板实测表头多在 252–338。单独用 op design 生成，尺寸按本机实测（侧栏 260、顶栏到 80）。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const INK = '#212B36', SUB = '#454F5B', MUTE = '#919EAB', HEAD_C = '#637381', LINE = '#EDEFF2', HEAD = '#F9FAFB', CANVAS = '#F4F6F8', CARD = '#FFFFFF', RED = '#B71D18';
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const W = 1074; // 1366 - 侧栏 260 - 左右外边距 16
const VIEW = 688; // 768 - 顶栏 80：内容区可见高度

const METRICS = [
  ['本月实收人民币', 'CNY 386,420.00', '线上 CNY 120,800.00 · 线下 CNY 265,620.00'],
  ['进行中', '18', ''],
  ['本月订单', '26', ''],
  ['未指定采购员', '3', ''],
];
const ROWS = [
  ['SO20261118001', '2026-11-18 · 现货', 'PRIME CONTROLS LLC', '老客户 · 美国 · Mike', '待采购', 'USD 532.00', '0% · 待到账', '2/30 件'],
  ['SO20261117003', '2026-11-17 · 期货', 'Nordic Automation AB', '新客户 · 瑞典 · Erik', '采购中', 'USD 4,860.00', '30% · 已收定金', '0/12 件'],
  ['SO20261116002', '2026-11-16 · 现货', 'Al Noor Trading LLC', '老客户 · 阿联酋 · Omar', '待发货', 'USD 1,275.50', '100% · 已收齐', '8/8 件'],
  ['SO20261115004', '2026-11-15 · 现货', 'TECNO INDUSTRIAL SAC', '老客户 · 秘鲁 · Luis', '已发货', 'USD 2,140.00', '100% · 已收齐', '5/5 件'],
  ['SO20261114001', '2026-11-14 · 期货', 'Kraft Systems GmbH', '新客户 · 德国 · Jonas', '采购中', 'EUR 3,390.00', '50% · 部分到账', '0/6 件'],
  ['SO20261113002', '2026-11-13 · 现货', 'Saigon Electric Co.', '老客户 · 越南 · Minh', '待采购', 'USD 980.00', '0% · 待到账', '0/4 件'],
  ['SO20261112005', '2026-11-12 · 现货', 'Andes Control S.A.', '老客户 · 智利 · Pablo', '已完成', 'USD 6,720.00', '100% · 已收齐', '20/20 件'],
];
const COLS = [['订单编号 · 销售日期', 190], ['客户', 230], ['订单状态', 110], ['合计', 130], ['收款进度', 150], ['货物', 110], ['操作', 96]];
const PILL = { 待采购: ['#FFF1D6', '#B76E00'], 采购中: ['#D6F4F9', '#006C9C'], 待发货: ['#FFF1D6', '#B76E00'], 已发货: ['#D6F4F9', '#006C9C'], 已完成: ['#DCF6E5', '#118D57'] };

const card = (p, o = {}) => V(p, 0, { width: 'fill_container', cornerRadius: 10, fill: fill(CARD), effects: SHADOW, ...o });
const pill = (p, text) => { const b = H(p, 0, { height: 24, padding: [0, 8], cornerRadius: 4, fill: fill(PILL[text][0]) }); T(b, text, { size: 12, weight: 600, color: PILL[text][1] }); };

const titleRow = (p, metricsInline) => {
  const r = H(p, 16, { width: 'fill_container', height: 31, justifyContent: 'space_between' });
  T(r, '销售订单', { size: 20, weight: 600, color: INK });
  const right = H(r, 20);
  if (metricsInline) METRICS.forEach(([k, v]) => { const m = H(right, 6); T(m, k, { size: 13, color: SUB }); T(m, v, { size: 16, weight: 600, color: INK }); });
  const b = H(right, 0, { height: 36, padding: [0, 16], cornerRadius: 8, fill: fill('#FDA92D') }); T(b, '+ 新建订单', { size: 14, weight: 600, color: '#1C252E' });
};
const summaryFull = (p) => {
  const c = card(p, { layout: 'horizontal', padding: [16, 0], height: 96 });
  METRICS.forEach(([k, v, s], i) => {
    const cell = V(c, 4, { width: 'fill_container', padding: [0, 20], ...(i ? { stroke: { thickness: 1, align: 'inside', fill: fill(LINE) } } : {}) });
    T(cell, k, { size: 13, color: SUB }); T(cell, v, { size: 20, weight: 600, color: INK }); if (s) T(cell, s, { size: 12, color: MUTE });
  });
};
const summaryCompact = (p) => {
  const c = card(p, { layout: 'horizontal', padding: [14, 0], height: 52, alignItems: 'center' });
  METRICS.forEach(([k, v, s], i) => {
    const cell = H(c, 8, { width: 'fill_container', padding: [0, 20] });
    T(cell, k, { size: 13, color: SUB }); T(cell, v, { size: 16, weight: 600, color: INK }); if (s && i === 0) T(cell, 'ⓘ', { size: 12, color: MUTE });
  });
};
const filter = (p) => {
  const c = card(p, { layout: 'horizontal', padding: 16, gap: 12, height: 72, alignItems: 'center' });
  [['订单状态', '全部', 200], ['销售日期', '开始日期 → 结束日期', 300], ['收款状态', '全部', 200]].forEach(([k, v, w]) => {
    const f = H(c, 6, { width: w, height: 40, padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, align: 'inside', fill: fill('#DFE3E8') } });
    T(f, k, { size: 14, color: SUB }); T(f, v, { size: 14, color: MUTE });
  });
  I(c, { type: 'frame', width: 'fill_container', height: 1 });
  const r = H(c, 0, { height: 36, padding: [0, 14], cornerRadius: 8, stroke: { thickness: 1, align: 'inside', fill: fill('#DFE3E8') } }); T(r, '重置', { size: 14, color: INK });
  const q = H(c, 0, { height: 36, padding: [0, 14], cornerRadius: 8, fill: fill('#1C252E') }); T(q, '查询', { size: 14, weight: 600, color: '#FFFFFF' });
};
const main = (p, room) => {
  const c = card(p, { padding: [16, 16, 0, 16], gap: 16, clipContent: true, height: room });
  const tb = H(c, 12, { width: 'fill_container', height: 32 });
  T(tb, '共 26 条', { size: 14, color: SUB });
  const q = H(tb, 0, { height: 28, padding: [0, 12], cornerRadius: 14, fill: fill('#EDEFF2') }); T(q, '毛利为负', { size: 13, color: SUB });
  const t = V(c, 0, { width: 'fill_container' });
  const head = H(t, 0, { width: 'fill_container', height: 48, padding: [0, 12], fill: fill(HEAD) });
  COLS.forEach(([h, w]) => { const x = H(head, 0, { width: w }); T(x, h, { size: 13, color: HEAD_C }); });
  ROWS.forEach((r) => {
    const row = H(t, 0, { width: 'fill_container', height: 64, padding: [0, 12], stroke: { thickness: 1, align: 'inside', fill: fill(LINE) } });
    const a = V(row, 2, { width: 190 }); T(a, r[0], { size: 14, weight: 600, color: INK }); T(a, r[1], { size: 12, color: MUTE });
    const b = V(row, 2, { width: 230 }); T(b, r[2], { size: 14, color: INK }); T(b, r[3], { size: 12, color: MUTE });
    const s = H(row, 0, { width: 110 }); pill(s, r[4]);
    const m = H(row, 0, { width: 130 }); T(m, r[5], { size: 14, color: INK });
    const g = H(row, 0, { width: 150 }); T(g, r[6], { size: 13, color: SUB });
    const h = H(row, 0, { width: 110 }); T(h, r[7], { size: 14, color: INK });
    const o = H(row, 0, { width: 96 }); T(o, '···', { size: 14, color: MUTE });
  });
};

// 每种方案：内容区按实际尺寸画，超出屏幕底边（768）的部分裁掉，下方标出首屏行数
const variant = (root, title, note, rows, good, build) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 600, color: INK });
  const tag = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill(good ? '#DCF6E5' : '#EDEFF2') }); T(tag, `首屏 ${rows} 行${good ? ' · 推荐' : ''}`, { size: 12, weight: 600, color: good ? '#118D57' : SUB });
  T(col, note, { size: 13, color: SUB, w: W });
  const screen = V(col, 16, { width: W, height: VIEW, padding: [18, 0, 0, 0], fill: fill(CANVAS), clipContent: true, stroke: { thickness: 1, align: 'outside', fill: fill('#C4CDD5') } });
  build(screen);
  const edge = H(col, 6); T(edge, `↑ 屏幕底边（1366×768）`, { size: 12, color: RED });
};

const root = I(null, { type: 'frame', name: 'R35 列表首屏高度对比', layout: 'vertical', gap: 40, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '列表首屏能看几行（R35）', { size: 20, weight: 600, color: INK });
T(root, '1366×768 下销售订单列表表头在 y=408（页头 + 汇总卡 96 + 搜索区 72 + 操作行），两行行高 64 只看得到 4 行；规范要求不少于 5 行。8 个付费模板实测表头多在 252–338，一屏 6–10 行。尺寸按本机实测，数据为示意。', { size: 13, color: SUB, w: W });

variant(root, 'A 现状', '汇总卡 96 高：名称、数值、副文字三行。', 4, false, (s) => {
  titleRow(s, false); summaryFull(s); filter(s); main(s, VIEW - 18 - 31 - 96 - 72 - 48);
});
variant(root, 'B 汇总卡改一行（推荐）', '仍按 D69：3 个及以上指标放一张白卡、等宽分栏。名称和数值排一行，数值 16/600；副文字收进帮助图标（悬停看「线上 · 线下」）。卡高 96 → 52，表头上移 44，首屏 5 行。只改公共 PageHeader 的 summary，全站有 3 个以上指标的列表一起变。', 5, true, (s) => {
  titleRow(s, false); summaryCompact(s); filter(s); main(s, VIEW - 18 - 31 - 52 - 72 - 48);
});
variant(root, 'C 指标并进标题行', '照 1–2 个指标的写法放在标题同一行右侧，不另占一行；表头上移约 110，首屏 6 行。但 4 个指标挤在标题行，和主按钮抢位置，副文字只能放进悬停；会推翻 D69 里「3 个及以上放一张白卡」。', 6, false, (s) => {
  titleRow(s, true); filter(s); main(s, VIEW - 18 - 31 - 72 - 32);
});
