// 单据详情的整宽明细表（R36，UI-118）：1366 下销售订单明细内容宽 2020、可见 1012，采购订单 1340、报价 1674，
// 都要横向滚动，且左侧没有固定列——滚到「进度、货物」时看不到是哪个型号。单独用 op design 生成，列宽按本机实测。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const INK = '#212B36', SUB = '#454F5B', MUTE = '#919EAB', HEAD_C = '#637381', LINE = '#EDEFF2', HEAD = '#F9FAFB', CANVAS = '#F4F6F8', CARD = '#FFFFFF', ORANGE = '#B76E00';
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const EDGE = [{ type: 'shadow', offsetX: 6, offsetY: 0, blur: 8, spread: -4, color: 'rgba(33,43,54,0.16)' }];
const VIS = 1012; // 1366 下明细卡可见宽

const ROWS = [
  { no: 1, brand: 'Schneider Electric', cat: '接触器', model: 'LC2D18B7C', note: '客户型号：LC2D18B7', qty: 20, price: '18.50', sub: '370.00', prog: '已下单', goods: '已出运 2 / 待发货 18', goodsSub: '预计 11-20 发货，已过 8 天', stock: '现货', buyer: '王磊', po: 'PO20261118001' },
  { no: 2, brand: 'Omron', cat: '传感器', model: 'E3Z-D61', note: '', qty: 10, price: '16.20', sub: '162.00', prog: '待采购', goods: '待发货 10', goodsSub: '', stock: '现货', buyer: '王磊', po: '—' },
  { no: 3, brand: 'Siemens', cat: 'PLC 与控制器', model: '6ES7214-1AG40-0XB0', note: '', qty: 2, price: '268.00', sub: '536.00', prog: '已入库', goods: '已入库 2', goodsSub: '待交货代', stock: '期货', buyer: '李娜', po: 'PO20261116002' },
];
// 滚动到中段时露出的列（各方案相同，便于比较）
const MID = [['数量', 80, (r) => [String(r.qty)], 'right'], ['单价（USD）', 110, (r) => [r.price], 'right'], ['小计（USD）', 110, (r) => [r.sub], 'right'], ['进度', 110, (r) => [r.prog]], ['货物', 300, (r) => [r.goods, r.goodsSub]], ['现货 / 期货', 100, (r) => [r.stock]], ['采购员', 100, (r) => [r.buyer]], ['采购订单 · 发货单', 180, (r) => [r.po]]];

const cellText = (p, lines, align) => {
  const c = V(p, 2, { width: 'fill_container', alignItems: align === 'right' ? 'end' : 'start' });
  T(c, lines[0], { size: 14, color: INK });
  if (lines[1]) T(c, lines[1], { size: 12, color: lines[1].includes('已过') ? ORANGE : MUTE });
};
const headCell = (p, text, w, align) => { const c = H(p, 0, { width: w, padding: [0, 12], justifyContent: align === 'right' ? 'end' : 'start' }); T(c, text, { size: 13, color: HEAD_C }); };

// pinned: 固定在左的列（每列 [标题, 宽, 渲染]），从左起依次
const table = (p, pinned, scrolledHint) => {
  const card = V(p, 12, { width: VIS + 32, padding: 16, cornerRadius: 10, fill: fill(CARD), effects: SHADOW });
  const head = H(card, 8); T(head, '型号明细', { size: 16, weight: 600, color: INK }); T(head, scrolledHint, { size: 12, color: MUTE });
  const view = H(card, 0, { width: VIS, clipContent: true, alignItems: 'start' });
  const pinW = pinned.reduce((s, c) => s + c[1], 0);
  const left = V(view, 0, { width: pinW, fill: fill(CARD), effects: pinned.length ? EDGE : [] });
  const right = V(view, 0, { width: VIS - pinW, clipContent: true });
  // 表头
  if (pinned.length) { const h = H(left, 0, { width: pinW, height: 48, fill: fill(HEAD) }); pinned.forEach(([t, w]) => headCell(h, t, w)); }
  const rh = H(right, 0, { height: 48, fill: fill(HEAD), width: 'fit_content' }); MID.forEach(([t, w, , a]) => headCell(rh, t, w, a));
  ROWS.forEach((r) => {
    if (pinned.length) { const row = H(left, 0, { width: pinW, height: 72, stroke: { thickness: 1, align: 'inside', fill: fill(LINE) } }); pinned.forEach(([, w, render]) => { const c = H(row, 0, { width: w, padding: [0, 12] }); render(c, r); }); }
    const row = H(right, 0, { height: 72, width: 'fit_content', stroke: { thickness: 1, align: 'inside', fill: fill(LINE) } });
    MID.forEach(([, w, get, a]) => { const c = H(row, 0, { width: w, padding: [0, 12] }); cellText(c, get(r), a); });
  });
  const bar = H(card, 0, { width: VIS, height: 6, cornerRadius: 3, fill: fill('#EDEFF2') });
  I(bar, { type: 'frame', width: Math.round(VIS * 0.45), height: 6, cornerRadius: 3, fill: fill('#C4CDD5') });
};

const modelLines = (c, r, withBrand) => {
  const v = V(c, 2, { width: 'fill_container' });
  T(v, r.model, { size: 14, weight: 600, color: INK });
  T(v, withBrand ? `${r.brand} · ${r.cat}` : r.note || ' ', { size: 12, color: MUTE });
};

const variant = (root, title, note, good, pinned, hint) => {
  const col = V(root, 10, { width: VIS + 32 });
  const h = H(col, 8); T(h, title, { size: 16, weight: 600, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: VIS + 32 });
  const bg = V(col, 0, { padding: 16, cornerRadius: 12, fill: fill(CANVAS) });
  table(bg, pinned, hint);
};

const root = I(null, { type: 'frame', name: 'R36 明细表固定列对比', layout: 'vertical', gap: 40, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '单据详情的明细表：横向滚动时左边留什么（R36）', { size: 20, weight: 600, color: INK });
T(root, '1366 下销售订单明细内容宽 2020、可见 1012（采购订单 1340、报价 1674），都要横向滚动，左侧没有固定列。下图都是「滚到进度、货物这几列」时的样子。列宽按本机实测，数据为示意。', { size: 13, color: SUB, w: VIS + 32 });

variant(root, 'A 现状：不固定', '滚过去后品牌、品类、型号都看不见，只能对着行号猜是哪个型号；8 个付费模板里没有这种做法。', false, [], '← 向左滚才能看到型号');
variant(root, 'B 固定「#、品牌、品类、型号」', '照现在的列不动，左侧固定 504 宽（# 44 + 品牌 130 + 品类 110 + 型号 220）。能看到型号，但可滚动区只剩约一半，进度、货物几列要滚得更多。', false,
  [['#', 44, (c, r) => T(c, String(r.no), { size: 14, color: SUB })], ['品牌', 130, (c, r) => T(c, r.brand, { size: 14, weight: 600, color: INK })], ['品类', 110, (c, r) => T(c, r.cat, { size: 14, color: INK })], ['型号', 220, (c, r) => modelLines(c, r, false)]], '左侧固定 504');
variant(root, 'C 品牌、品类并到型号第二行，只固定「#、型号」', '型号格第一行型号、第二行「品牌 · 品类」（客户型号、备注照旧在下面），左侧只固定 284（# 44 + 型号 240）。表总宽少 240，滚动区更宽；采购订单原本就是「型号 · 品牌」合并列，三种单据统一。改公共 docBrandColumns / DocModelCell，销售订单、报价、PI、采购订单、出入库等明细一起变；单据页规范「品牌、品类单独两列」改为合并。', true,
  [['#', 44, (c, r) => T(c, String(r.no), { size: 14, color: SUB })], ['型号 · 品牌', 240, (c, r) => modelLines(c, r, true)]], '左侧固定 284');
