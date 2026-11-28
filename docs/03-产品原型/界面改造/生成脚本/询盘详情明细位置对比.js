// 询盘详情的型号明细放哪（R40，UI-119）：明细 8 列共 1504 宽，现状放在 660 宽的左栏里，只露出 41%，要横向滚很远；
// 规范 D80「明细超过主栏宽度一倍以上时通栏」。单独用 op design 生成，尺寸按本机 1366 实测，数据为示意。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const INK = '#212B36', SUB = '#454F5B', MUTE = '#919EAB', HEAD_C = '#637381', LINE = '#EDEFF2', HEAD = '#F9FAFB', CANVAS = '#F4F6F8', CARD = '#FFFFFF', INSET = '#F7F8FA';
const GREEN = '#118D57', GREEN_SOFT = '#DCF6E5', INFO = '#006C9C', INFO_SOFT = '#D6F4F9', PRIMARY = '#FDA92D';
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const EDGE = [{ type: 'shadow', offsetX: 6, offsetY: 0, blur: 8, spread: -4, color: 'rgba(33,43,54,0.16)' }];
const W = 1060, SIDE = 380, MAIN = W - SIDE - 20;

const card = (p, w, title, o = {}) => { const c = V(p, 12, { width: w, padding: 20, cornerRadius: 10, fill: fill(CARD), effects: SHADOW, ...o }); if (title) T(c, title, { size: 16, weight: 600, color: INK }); return c; };
const pill = (p, text, bg, fg) => { const b = H(p, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); T(b, text, { size: 12, weight: 600, color: fg }); };

const ROWS = [
  { model: 'LC2D18B7', brand: 'Schneider Electric · 接触器', qty: '20', cost: 'CNY 92.00', shop: '1688 · 施耐德一级代理', arc: '已建档', desc: '要 AC220V 线圈，客户现场是 220V' },
  { model: 'E3Z-D61', brand: 'OMRON · 传感器', qty: '10', cost: 'CNY 86.00', shop: '淘宝 · 欧姆龙工控店', arc: '已建档', desc: 'NPN 输出，线长 2 米' },
  { model: '6ES7214-1AG40-0XB0', brand: 'Siemens · PLC', qty: '2', cost: 'CNY 1,780.00', shop: '1688 · 西门子授权', arc: '候选中', desc: '' },
];
// 8 列，共 1504；固定在左的是「型号 · 品牌」
const COLS = [['数量', 80, 'qty', 'right'], ['回价', 110, 'q'], ['采购成本价', 300, 'cost'], ['任务', 64, 't'], ['货源店铺', 220, 'shop'], ['建档', 150, 'arc'], ['描述', 320, 'desc']];
const PIN = 260;

const items = (p, w) => {
  const c = card(p, w, null);
  const h = H(c, 8); T(h, '型号明细', { size: 16, weight: 600, color: INK }); T(h, `表格 1504 宽，能看到 ${Math.round((w - 40) / 1504 * 100)}%`, { size: 12, color: MUTE });
  const vis = w - 40;
  const view = H(c, 0, { width: vis, clipContent: true, alignItems: 'start' });
  const left = V(view, 0, { width: PIN, fill: fill(CARD), effects: EDGE });
  const right = V(view, 0, { width: vis - PIN, clipContent: true });
  T(H(left, 0, { width: PIN, height: 44, fill: fill(HEAD), padding: [0, 12] }), '型号 · 品牌', { size: 13, color: HEAD_C });
  const rh = H(right, 0, { height: 44, fill: fill(HEAD), width: 'fit_content' });
  COLS.forEach(([t, cw, , a]) => { const x = H(rh, 0, { width: cw, padding: [0, 12], justifyContent: a === 'right' ? 'end' : 'start' }); T(x, t, { size: 13, color: HEAD_C }); });
  ROWS.forEach((r) => {
    const lc = V(left, 2, { width: PIN, height: 64, padding: [0, 12], justifyContent: 'center', stroke: { thickness: 1, align: 'inside', fill: fill(LINE) } });
    T(lc, r.model, { size: 14, weight: 600, color: INK }); T(lc, r.brand, { size: 12, color: MUTE });
    const row = H(right, 0, { height: 64, width: 'fit_content', stroke: { thickness: 1, align: 'inside', fill: fill(LINE) } });
    COLS.forEach(([, cw, k, a]) => {
      const x = H(row, 0, { width: cw, padding: [0, 12], justifyContent: a === 'right' ? 'end' : 'start' });
      if (k === 'q') pill(x, '已报出', INFO_SOFT, INFO);
      else if (k === 't') T(x, '1', { size: 14, color: INK });
      else if (k === 'arc') r.arc === '已建档' ? pill(x, r.arc, GREEN_SOFT, GREEN) : pill(x, r.arc, '#FFF1D6', '#B76E00');
      else T(x, r[k] || '—', { size: 14, color: r[k] ? INK : MUTE });
    });
  });
  const bar = H(c, 0, { width: vis, height: 6, cornerRadius: 3, fill: fill('#EDEFF2') });
  I(bar, { type: 'frame', width: Math.round(vis * vis / 1504), height: 6, cornerRadius: 3, fill: fill('#C4CDD5') });
};
const raw = (p, w) => {
  const c = card(p, w, '原始内容');
  T(c, 'Hi, please quote the following items, delivery to Lima:\n1. LC2D18B7 x 20 pcs (coil AC220V)\n2. E3Z-D61 x 10 pcs\n3. 6ES7214-1AG40-0XB0 x 2 pcs\nBest regards, Carlos', { size: 13, color: SUB, w: w - 40 });
  const f = H(c, 8); [1, 2].forEach(() => I(f, { type: 'frame', width: 72, height: 54, cornerRadius: 6, fill: fill(INSET) }));
};
const quotes = (p, w) => {
  const c = V(p, 12, { width: w });
  T(c, '报价单', { size: 16, weight: 600, color: INK });
  [['QT20261118903', '1 个型号', 'USD 468.00', '已发送'], ['QT20261118902', '2 个型号', 'USD 532.00', '已成交'], ['QT20261118901', '2 个型号', 'USD 838.00', '草稿']].forEach(([no, n, amt, st]) => {
    const q = V(c, 4, { width: w, padding: [14, 16], cornerRadius: 10, fill: fill(CARD), effects: SHADOW });
    const t = H(q, 8, { width: 'fill_container' }); T(t, no, { size: 14, weight: 600, color: INK }); I(t, { type: 'frame', width: 'fill_container', height: 1 });
    st === '草稿' ? pill(t, st, '#ECEEF0', SUB) : pill(t, st, st === '已成交' ? GREEN_SOFT : INFO_SOFT, st === '已成交' ? GREEN : INFO);
    const b = H(q, 8, { width: 'fill_container' }); T(b, n, { size: 12, color: MUTE }); I(b, { type: 'frame', width: 'fill_container', height: 1 }); T(b, amt, { size: 13, weight: 600, color: INK });
  });
};
const chain = (p, w) => { const c = card(p, w, '来源 / 去向'); [['商机', 'OPP20261125006'], ['销售订单', 'SO20261119001']].forEach(([a, b]) => { const r = H(c, 10, { width: 'fill_container', padding: [10, 12], cornerRadius: 8, fill: fill(INSET) }); T(r, a, { size: 13, color: SUB }); T(r, b, { size: 13, weight: 600, color: INK }); }); };
const tasks = (p, w) => { const c = card(p, w, '询价任务'); T(c, 'IT20261118001 · 王磊 · 已回价 3/3', { size: 13, color: INK }); T(c, '11-18 02:08 分配 · 11-18 14:37 完成', { size: 12, color: MUTE }); };
const side = (p) => { const s = V(p, 16, { width: SIDE }); quotes(s, SIDE); chain(s, SIDE); tasks(s, SIDE); };

const variant = (root, title, note, good, build) => {
  const col = V(root, 10, { width: W + 32 });
  const h = H(col, 8); T(h, title, { size: 16, weight: 600, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill(GREEN_SOFT) }); T(b, '推荐', { size: 12, weight: 600, color: GREEN }); }
  T(col, note, { size: 13, color: SUB, w: W + 32 });
  const bg = V(col, 16, { padding: 16, cornerRadius: 12, fill: fill(CANVAS) });
  const hd = H(bg, 8, { width: W }); const ht = V(hd, 2, { width: 'fill_container' });
  const tl = H(ht, 8); T(tl, 'IQ1118AM', { size: 20, weight: 600, color: INK }); pill(tl, '已成交', GREEN_SOFT, GREEN);
  T(ht, 'PRIME CONTROLS LLC · 新客户 · 询盘日期 2026-11-18', { size: 13, color: SUB });
  const btn = H(hd, 6, { height: 36, padding: [0, 16], cornerRadius: 8, fill: fill(PRIMARY) }); T(btn, '再报一张', { size: 14, weight: 600, color: '#FFFFFF' });
  const sum = H(bg, 32, { width: W, padding: [16, 20], cornerRadius: 10, fill: fill(CARD), effects: SHADOW });
  [['3 个型号', '3 个品牌'], ['B', '询盘等级'], ['测试管理员', '负责人'], ['2026-11-18', '询盘日期'], ['3/3', '回价进度']].forEach(([a, b]) => { const v = V(sum, 2); T(v, a, { size: 14, weight: 600, color: INK }); T(v, b, { size: 12, color: MUTE }); });
  build(bg);
};

const root = I(null, { type: 'frame', name: 'R40 询盘详情明细位置对比', layout: 'vertical', gap: 40, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '询盘详情的型号明细放哪（R40）', { size: 20, weight: 600, color: INK });
T(root, '询盘的型号明细有 8 列、共 1504 宽（型号 · 品牌、数量、回价、采购成本价、任务、货源店铺、建档、描述），现状放在 660 宽的左栏，1366 下只能看到 41%，回价和成本之后的列都要横向滚。规范 D80：明细超过主栏宽度一倍以上时通栏。型号列已固定在左（本批 D77 补改）。尺寸按本机 1366，数据为示意。', { size: 13, color: SUB, w: W + 32 });

variant(root, 'A 现状：明细在左栏，右栏放报价单、来源 / 去向、询价任务', '报价单一直在右边看得到；但明细只露出 41%，货源店铺、建档、描述要滚很远，左右两栏都长。', false, (bg) => {
  const top = H(bg, 20, { alignItems: 'start' });
  const main = V(top, 16, { width: MAIN }); items(main, MAIN); raw(main, MAIN);
  side(top);
});
variant(root, 'B 明细通栏放在汇总下面，其余两栏在下（推荐）', '明细是询盘的主体，放在最上面通栏，1366 下能看到 68%，型号固定在左；下方左栏原始内容，右栏报价单、来源 / 去向、询价任务。报价单要往下翻一屏才看到，但汇总卡的回价进度和明细的回价列已能看出报价情况。', true, (bg) => {
  items(bg, W);
  const bottom = H(bg, 20, { alignItems: 'start' });
  raw(bottom, MAIN); side(bottom);
});
variant(root, 'C 照销售订单：上半两栏，明细通栏在下', '与销售订单一致；但询盘的右栏（3 张报价单 + 来源 / 去向 + 询价任务）比原始内容高得多，左下空一大块（R39 的问题），明细被推到一屏以下。', false, (bg) => {
  const top = H(bg, 20, { alignItems: 'start' });
  raw(top, MAIN); side(top);
  items(bg, W);
});
