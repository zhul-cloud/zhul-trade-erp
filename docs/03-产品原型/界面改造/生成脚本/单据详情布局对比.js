// 采购订单详情（复审 P9）、出运详情（复审 P10）按销售订单标准布局的对比（R25）。单独用 op design 生成。
// 线框按 1366 宽内容区（约 1100）的一半比例画：左栏约 372、右栏 170（实际 744 / 340）。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 11, fontWeight: o.weight || 400, lineHeight: 1.45, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const R = (p, w, h, c, o = {}) => I(p, { type: 'rectangle', width: w, height: h, cornerRadius: o.r ?? 3, fill: fill(c), ...o });
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 6, blur: 12, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINK = '#B76E00', LINE = '#E5E8EB', CANVAS = '#F4F6F8', HEAD = '#F2F4F6', BAR = '#E4E7EB', RED = '#FF5630', AMBER = '#FDA92D';
const PAGE = 550, LEFT = 372, RIGHT = 170, GAP = 8;

const root = I(null, { type: 'frame', name: '单据详情布局对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '采购订单详情、出运详情：照销售订单的「左宽右 340」布局（复审 P9、P10）', { size: 20, weight: 700, color: INK });
T(root, '销售订单详情（全站单据页标准）：上半部左栏放信息卡、右栏 340 放收付款等侧栏，型号明细这类宽表格放到下方通栏。下面按 1366 宽的一半比例画线框，红字为现状的问题。', { size: 13, color: SUB, w: PAGE * 2 + 40 });

// 一张卡：标题 + 若干行灰条；table 为表头列数（画表头条和两行）
const card = (p, w, title, o = {}) => {
  const c = V(p, 6, { width: w, padding: 10, cornerRadius: 6, fill: fill('#FFFFFF'), effects: SH, ...(o.h ? { height: o.h } : {}) });
  const h = H(c, 6, { width: w - 20, justifyContent: 'space_between' });
  T(h, title, { size: 11, weight: 700, color: INK });
  if (o.action) T(h, o.action, { size: 10, color: LINK });
  if (o.warn) T(c, o.warn, { size: 10, color: RED, w: w - 20 });
  (o.lines || []).forEach((lw) => R(c, Math.min(lw, w - 20), 6, BAR));
  if (o.kv) { const g = H(c, 10, { width: w - 20 }); for (let i = 0; i < o.kv; i++) { const k = V(g, 3); R(k, 34, 5, BAR); R(k, 48, 7, '#CFD4DA'); } }
  if (o.table) {
    const tw = o.tableW || w - 20;
    const clip = V(c, 0, { width: w - 20, clipContent: true });
    const th = H(clip, 0, { width: tw, height: 16, cornerRadius: 3, fill: fill(HEAD), padding: [0, 6], gap: 0 });
    o.table.forEach((n) => { const cell = H(th, 0, { width: tw / o.table.length - 2 }); T(cell, n, { size: 8, color: MUTE }); });
    for (let i = 0; i < (o.rows || 2); i++) { const row = H(clip, 0, { width: tw, height: 18, padding: [0, 6] }); o.table.forEach(() => { const cell = H(row, 0, { width: tw / o.table.length - 2 }); R(cell, tw / o.table.length - 14, 5, BAR); }); }
    if (o.scroll) { const sb = H(c, 0, { width: w - 20, height: 4, cornerRadius: 2, fill: fill(HEAD) }); R(sb, (w - 20) * o.scroll, 4, '#B0B7BF', { r: 2 }); }
  }
  return c;
};

const header = (p, no, tag, actions, primary = true) => {
  const h = H(p, 6, { width: PAGE, justifyContent: 'space_between' });
  const l = H(h, 6); T(l, no, { size: 13, weight: 700, color: INK }); const tg = H(l, 0, { height: 14, padding: [0, 4], cornerRadius: 3, fill: fill('#D6F4F9') }); T(tg, tag, { size: 8, weight: 600, color: '#006C9C' });
  const a = H(h, 6);
  actions.forEach((x, i) => { if (primary && i === actions.length - 1) { const b = H(a, 0, { height: 18, padding: [0, 8], cornerRadius: 4, fill: fill(AMBER) }); T(b, x, { size: 9, weight: 600, color: '#4A2E00' }); } else T(a, x, { size: 9, color: LINK }); });
};

const kpis = (p, w, items) => {
  const r = H(p, GAP, { width: w });
  items.forEach(([n, v, sub]) => { const k = V(r, 3, { width: (w - GAP * (items.length - 1)) / items.length, padding: 8, cornerRadius: 6, fill: fill('#FFFFFF'), effects: SH }); T(k, n, { size: 9, color: MUTE }); T(k, v, { size: 12, weight: 700, color: INK }); if (sub) T(k, sub, { size: 8, color: sub.startsWith('!') ? RED : MUTE }); });
};

const twoCol = (p, left, right) => {
  const r = H(p, GAP, { width: PAGE, alignItems: 'start' });
  const l = V(r, GAP, { width: LEFT });
  const rr = V(r, GAP, { width: RIGHT });
  left(l); right(rr);
};

const variant = (parent, title, note, good, draw) => {
  const col = V(parent, 8, { width: PAGE });
  const h = H(col, 6); T(h, title, { size: 14, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 20, padding: [0, 6], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 11, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 11, color: SUB, w: PAGE });
  const pg = V(col, GAP, { width: PAGE + 24, padding: 12, cornerRadius: 10, fill: fill(CANVAS) });
  draw(pg);
};

const pair = (title, a, b) => {
  const s = V(root, 12);
  T(s, title, { size: 16, weight: 700, color: INK });
  const row = H(s, 40, { alignItems: 'start' });
  variant(row, ...a);
  variant(row, ...b);
};

// ---------- 采购订单 ----------
const PO_ITEMS = ['型号 · 品牌', '描述', '来源单号', '客户', '数量', '已发', '已入库'];
const PO_FLOW = ['单据', '类型', '快递 · 单号', '日期', '预计到货', '数量', '状态'];
pair('1 采购订单详情（P9）', [
  'A 现状：单栏通排', '指标卡下面所有卡片都通栏；「付款条件」在指标卡、付款卡、付款条件卡里写了三遍；最下面合同、日志各占半宽，右侧大片空白。', false,
  (pg) => {
    header(pg, 'PO20261118001', '部分发货', ['修改', '…', '登记发货']);
    kpis(pg, PAGE, [['合计', 'CNY 1,840.00'], ['砍价合计', 'CNY 80.00'], ['付款条件', '一次付清', '!与下方重复'], ['发货进度', '已发 2 / 20']]);
    card(pg, PAGE, '型号 (1)', { table: PO_ITEMS, rows: 1, lines: [60] });
    card(pg, PAGE, '发货与入库', { table: PO_FLOW, rows: 2 });
    card(pg, PAGE, '付款', { action: '登记垫付  登记付款', kv: 5, table: ['期次', '比例 · 时点', '金额', '到期日', '已付', '状态'], rows: 1, warn: '付款条件 100% 下单后（重复）' });
    const r = H(pg, GAP, { width: PAGE, alignItems: 'start' });
    card(r, (PAGE - GAP) / 2, '付款条件', { lines: [140, 60], warn: '第三遍' });
    card(r, (PAGE - GAP) / 2, '供应商合同', { lines: [120, 80] });
    const r2 = H(pg, GAP, { width: PAGE, alignItems: 'start' });
    card(r2, (PAGE - GAP) / 2, '操作日志', { lines: [200, 180, 200, 160, 190] });
    T(r2, '空白', { size: 10, color: RED });
  },
], [
  'B 照销售订单：上半两栏 + 宽表通栏', '上半左栏放「供应商」「订单信息」（下单日期、预计发货、采购员、付款条件只写这一处）「发货进度」，右栏 340 放「付款」（应付、已付、未付一列竖排，付款计划、付款单、登记付款）和「供应商合同」；下方通栏放「型号」「发货与入库」两张宽表，最后「操作日志」。去掉单独的付款条件卡；合计、砍价并入订单信息卡。', true,
  (pg) => {
    header(pg, 'PO20261118001', '部分发货', ['修改', '…', '登记发货']);
    twoCol(pg, (l) => {
      const r = H(l, GAP, { width: LEFT, alignItems: 'start' });
      card(r, (LEFT - GAP) / 2, '供应商', { lines: [120, 60] });
      card(r, (LEFT - GAP) / 2, '订单信息', { lines: [140, 110, 120, 100] });
      card(l, LEFT, '发货进度', { lines: [LEFT - 40, 160] });
    }, (r) => {
      card(r, RIGHT, '付款', { action: '登记付款', lines: [120, 90, 100, 140, 120, 80] });
      card(r, RIGHT, '供应商合同', { lines: [110, 70] });
    });
    card(pg, PAGE, '型号 (1)', { table: PO_ITEMS, rows: 1, lines: [60] });
    card(pg, PAGE, '发货与入库', { table: PO_FLOW, rows: 2 });
    card(pg, PAGE, '操作日志', { lines: [300, 260, 280] });
  },
]);

// ---------- 出运 ----------
const SH_ITEMS = ['#', '品牌', '品类', '型号', '数量', '销售出库单', '销售订单'];
const SH_BOX = ['销售出库单', '箱号', '尺寸 cm', '毛重 · 净重', '计费重', '箱内型号', '国际运费分摊'];
pair('2 出运详情（P10）', [
  'A 现状：宽表格挤在左栏', '左栏约 720，明细（7 列合计 824）、装箱（890）都要横向滚动；「出运信息」和「基本信息」重复写了运单号、交货日期、送货运费。', false,
  (pg) => {
    header(pg, 'SH20261119001', '已出运', ['生成 CI/PL', '修改出运信息'], false);
    twoCol(pg, (l) => {
      card(l, LEFT, '基本信息', { kv: 3, lines: [LEFT - 60, 160] });
      card(l, LEFT, '明细', { table: SH_ITEMS, tableW: 412, rows: 1, scroll: 0.9 });
      card(l, LEFT, '装箱', { table: SH_BOX, tableW: 445, rows: 1, scroll: 0.83 });
      card(l, LEFT, '出运信息', { lines: [150, 120, 130, 80, 200], warn: '与基本信息重复' });
      card(l, LEFT, '备注与附件', { lines: [LEFT - 20, 160] });
    }, (r) => {
      card(r, RIGHT, '关联单据', { lines: [140, 140] });
      card(r, RIGHT, '操作日志', { lines: [150, 90, 140, 80] });
    });
  },
], [
  'B 照销售订单：信息两栏 + 宽表通栏', '上半左栏「基本信息」合并原「出运信息」（运单号、交货日期、送货运费只写一次，面单、CI/PL 并进来），右栏 340 放「关联单据」「操作日志」；下方通栏放「明细」「装箱」和「备注与附件」。1366 宽下通栏约 1100，两张表都不用横向滚动。', true,
  (pg) => {
    header(pg, 'SH20261119001', '已出运', ['生成 CI/PL', '修改出运信息'], false);
    twoCol(pg, (l) => {
      card(l, LEFT, '基本信息', { kv: 3, lines: [LEFT - 60, 160, 200, 120] });
    }, (r) => {
      card(r, RIGHT, '关联单据', { lines: [140, 140] });
      card(r, RIGHT, '操作日志', { lines: [150, 90] });
    });
    card(pg, PAGE, '明细', { table: SH_ITEMS, rows: 1 });
    card(pg, PAGE, '装箱', { table: SH_BOX, rows: 1 });
    card(pg, PAGE, '备注与附件', { lines: [PAGE - 20, 200] });
  },
]);
