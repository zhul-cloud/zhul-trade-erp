// 单据详情明细表在 1366 宽下价格列被挤出屏幕（复审 P18）的列序对比（R26）。单独用 op design 生成。
// 列宽取自本机实测（销售订单 SO20261118001、采购订单 PO20261118001），按 0.62 缩放；虚线为 1366 宽下表格可见区的右边缘。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const K = 0.62;
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 9, fontWeight: o.weight || 400, lineHeight: 1.4, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}), ...(o.align ? { textAlign: o.align } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const FAINT = '#C4CDD5', INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINE = '#E5E8EB', CANVAS = '#F4F6F8', HEAD = '#F2F4F6', RED = '#FF5630', LINK = '#B76E00';
const W = 1100;

const root = I(null, { type: 'frame', name: '明细表列序对比', layout: 'vertical', gap: 32, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '单据详情明细表：1366 宽下单价、小计在不在屏幕里（复审 P18）', { size: 20, weight: 700, color: INK });
T(root, '列宽为本机实测，按比例缩小；红线右边（浅灰字）是 1366 宽下要横向拖动才看得到的列。数字列右对齐。', { size: 13, color: SUB, w: W });

// cols: [名称, 宽, 示例, 对齐, 固定右侧]
const table = (p, cols, view, o = {}) => {
  const vw = Math.round(view * K);
  const total = cols.reduce((n, c) => n + Math.round(c[1] * K), 0);
  const outer = I(p, { type: 'frame', layout: 'none', width: Math.max(total, vw) + 4, height: 92 });
  const strip = I(outer, { type: 'frame', layout: 'vertical', x: 0, y: 0, width: total, height: 92 });
  const head = H(strip, 0, { width: total, height: 26, fill: fill(HEAD), cornerRadius: 4 });
  const row = H(strip, 0, { width: total, height: 60 });
  let x = 0;
  cols.forEach(([n, w, ex, al, pin]) => {
    const cw = Math.round(w * K);
    // 起点已在可见区右边缘之外的列：浅灰字，表示要横向拖动才看得到
    const off = x >= vw;
    x += cw;
    const bg = pin ? { fill: fill('#FFFFFF'), effects: [{ type: 'shadow', offsetX: -6, offsetY: 0, blur: 8, spread: -4, color: 'rgba(145,158,171,0.35)' }] } : {};
    const h = H(head, 0, { width: cw, height: 26, padding: [0, 5], justifyContent: al === 'r' ? 'end' : 'start', ...(pin ? { fill: fill(HEAD) } : {}) });
    T(h, n, { size: 9, color: off ? FAINT : MUTE });
    const c = V(row, 2, { width: cw, height: 60, padding: [0, 5], justifyContent: 'center', alignItems: al === 'r' ? 'end' : 'start', ...bg });
    String(ex).split('\n').forEach((line, i) => T(c, line, { size: i ? 8 : 10, weight: i === 0 && o.bold && o.bold.includes(n) ? 600 : 400, color: off ? FAINT : i ? MUTE : INK }));
  });
  // 可见区右边缘
  I(outer, { type: 'rectangle', name: 'edge', x: vw, y: -4, width: 2, height: 100, fill: fill(RED) });
  return outer;
};

const variant = (parent, title, note, good, cols, view, o) => {
  const col = V(parent, 8, { width: W });
  const h = H(col, 8); T(h, title, { size: 14, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 20, padding: [0, 6], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 11, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 12, color: SUB, w: W });
  const pg = V(col, 0, { width: W, padding: 14, cornerRadius: 10, fill: fill(CANVAS), clipContent: true });
  const card = V(pg, 0, { width: W - 28, padding: 12, cornerRadius: 8, fill: fill('#FFFFFF'), clipContent: true });
  table(card, cols, view, o);
};

const section = (title) => { const s = V(root, 14); T(s, title, { size: 16, weight: 700, color: INK }); return s; };

// ---------- 销售订单 ----------
const SO = {
  sel: ['', 36, '□'], no: ['#', 44, '1'], brand: ['品牌', 110, 'Schneider\nElectric'], cat: ['品类', 110, '接触器'],
  model: ['型号', 220, 'LC2D18B7C\n客户型号 LC2D18B7'], desc: ['描述', 260, '接触器 18A，3P，\n线圈 AC24V'],
  qty: ['数量', 130, '20', 'r'], price: ['单价（USD）', 120, '18.50', 'r'], amount: ['小计（USD）', 130, '370.00', 'r'],
  spot: ['现货 / 期货', 100, '现货'], buyer: ['采购员', 110, '测试管理员'], goods: ['货物', 320, '已出运 2\n待发货 18 · 预计 11-20'],
  po: ['采购订单 · 发货单', 190, 'PO20261118001'], prog: ['进度', 120, '已下单'],
};
const s1 = section('1 销售订单 · 型号明细（全站单据页的标准，列宽合计 2000，1366 宽可见 1012）');
variant(s1, 'A 现状', '描述（260）排在数量前面，可见区到数量为止，单价只露一半，小计、进度、货物都在屏幕外。', false,
  [SO.sel, SO.no, SO.brand, SO.cat, SO.model, SO.desc, SO.qty, SO.price, SO.amount, SO.spot, SO.buyer, SO.goods, SO.po, SO.prog], 1012, { bold: ['型号', '小计（USD）'] });
variant(s1, 'B 价格紧跟数量，描述等次要信息移到最后', '列序：身份（品牌、品类、型号）→ 数量 → 单价 → 小计 → 进度 → 货物、现货 / 期货、采购员、采购订单 → 描述。1366 宽下数量、单价、小计、进度都在屏幕里；描述较长，放最后靠横向拖动或悬停型号查看。列表页 D40 已按「身份 → 状态 → 金额 → 进度 → 明细 → 次要」排，明细表沿用同一个顺序。', true,
  [SO.sel, SO.no, SO.brand, SO.cat, SO.model, SO.qty, SO.price, SO.amount, SO.prog, SO.goods, SO.spot, SO.buyer, SO.po, SO.desc], 1012, { bold: ['型号', '小计（USD）'] });
variant(s1, 'C 备选：列序不变，单价、小计固定在右侧', '单价、小计常驻表格右边（antd 固定列），中间的列在它们左侧横向滚动。金额总能看到，但固定列一直占 250 宽，中间可见区更窄（描述、数量也常被挤出），两侧都有阴影；横向拖动时数量和单价不在一起，读一行要来回看。', false,
  [SO.sel, SO.no, SO.brand, SO.cat, SO.model, ['描述', 242, '接触器 18A，3P…'], ['单价（USD）', 120, '18.50', 'r', true], ['小计（USD）', 130, '370.00', 'r', true]], 1012, { bold: ['型号', '小计（USD）'] });

// ---------- 采购订单 ----------
const PO = {
  model: ['型号 · 品牌', 200, 'LC2D18B7\nSchneider Electric · 接触器'], desc: ['描述', 240, '接触器 18A，3P，\n线圈 AC24V'],
  src: ['来源单号', 160, 'SO20261118001'], cust: ['客户', 200, 'PRIME CONTROLS LLC\n美国'], srcCust: ['来源单号 · 客户', 200, 'SO20261118001\nPRIME CONTROLS LLC · 美国'],
  qty: ['数量', 100, '20', 'r'], shipped: ['已发', 80, '2', 'r'], recv: ['已入库（合格）', 120, '2', 'r'],
  target: ['目标价', 110, 'CNY 96.00', 'r'], price: ['单价（CNY）', 160, '92.00', 'r'], cny: ['人民币单价', 120, '92.00', 'r'], bargain: ['砍价', 130, '4.00\n4.17%', 'r'],
};
const s2 = section('2 采购订单 · 型号（列宽合计 1620，1366 宽可见 1020）');
variant(s2, 'A 现状', '目标价、单价、人民币单价、砍价四列都在屏幕外；人民币计价的单子「人民币单价」与「单价」完全相同。', false,
  [PO.model, PO.desc, PO.src, PO.cust, PO.qty, PO.shipped, PO.recv, PO.target, PO.price, PO.cny, PO.bargain], 1020, { bold: ['型号 · 品牌', '单价（CNY）'] });
variant(s2, 'B 价格紧跟数量；来源单号与客户合成一列；人民币计价不显示人民币单价', '列序：型号 → 数量 → 单价 → 目标价 → 砍价 → 已发 → 已入库 → 来源单号 · 客户（两行）→ 描述。1366 宽下数量、单价、目标价、砍价、发货入库都在屏幕里，合计 1240（只多出来源与描述）。外币单子「人民币单价」排在单价后面。', true,
  [PO.model, PO.qty, PO.price, PO.target, PO.bargain, PO.shipped, PO.recv, PO.srcCust, PO.desc], 1020, { bold: ['型号 · 品牌', '单价（CNY）'] });
