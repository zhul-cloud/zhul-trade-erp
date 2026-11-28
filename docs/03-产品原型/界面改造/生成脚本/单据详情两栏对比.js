// 单据详情上半两栏不等高（R39，UI-118）：入库单等基本信息少的单据，左栏「基本信息」比右栏「关联单据 + 操作日志」短约 120，
// 左下留白；现行做法（复审 P18）是上半两栏、明细通栏放下方。单独用 op design 生成，尺寸按本机 1366 实测，数据为示意。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const INK = '#212B36', SUB = '#454F5B', MUTE = '#919EAB', HEAD_C = '#637381', LINE = '#EDEFF2', HEAD = '#F9FAFB', CANVAS = '#F4F6F8', CARD = '#FFFFFF', INSET = '#F7F8FA';
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const W = 1074, SIDE = 260, MAIN = W - SIDE - 16;

const card = (p, w, title, o = {}) => { const c = V(p, 12, { width: w, padding: 20, cornerRadius: 10, fill: fill(CARD), effects: SHADOW, ...o }); if (title) T(c, title, { size: 16, weight: 600, color: INK }); return c; };
const kv = (p, items, cols) => {
  const g = V(p, 14, { width: 'fill_container' });
  for (let i = 0; i < items.length; i += cols) {
    const row = H(g, 24, { width: 'fill_container', alignItems: 'start' });
    items.slice(i, i + cols).forEach(([k, v]) => { const c = V(row, 2, { width: 'fill_container' }); T(c, k, { size: 12, color: MUTE }); T(c, v, { size: 14, color: INK }); });
  }
};
const basic = (p, w) => kv(card(p, w, '基本信息'), [['采购订单', 'PO20261118001'], ['发货单', 'SD20261119001'], ['收货日期', '2026-11-19'], ['收货人', '王磊']], 3);
const related = (p, w) => {
  const c = card(p, w, '关联单据');
  [['发货单', 'SD20261119001', '已入库'], ['采购订单', 'PO20261118001', '已下单'], ['实拍任务', '1 个型号', '待拍摄']].forEach(([a, b, s]) => {
    const r = H(c, 10, { width: 'fill_container', padding: [10, 12], cornerRadius: 8, fill: fill(INSET) });
    T(r, a, { size: 13, color: SUB }); T(r, b, { size: 13, weight: 600, color: INK }); I(r, { type: 'frame', width: 'fill_container', height: 1 }); T(r, s, { size: 12, color: MUTE });
  });
};
const logs = (p, w) => { const c = card(p, w, '操作日志'); T(c, '● 验收入库：LC2D18B7 实收 2、合格 2', { size: 13, color: INK }); T(c, '王磊 · 11-19 15:51', { size: 12, color: MUTE }); T(c, '● 新建入库单', { size: 13, color: INK }); T(c, '王磊 · 11-19 15:40', { size: 12, color: MUTE }); };
const items = (p, w) => {
  const c = card(p, w, '明细  发货 2 · 实收 2 · 合格 2');
  const head = H(c, 0, { width: 'fill_container', height: 40, fill: fill(HEAD), padding: [0, 12] });
  [['#', 40], ['型号 · 品牌', 220], ['发货', 70], ['实收', 70], ['合格', 70], ['不良', 70]].forEach(([h, ww]) => T(H(head, 0, { width: ww }), h, { size: 13, color: HEAD_C }));
  [['1', 'LC2D18B7', 'Schneider Electric · 接触器', '2', '2', '2', '0'], ['2', 'E3Z-D61', 'OMRON · 传感器', '10', '10', '9', '1']].forEach((r) => {
    const row = H(c, 0, { width: 'fill_container', height: 60, padding: [0, 12], stroke: { thickness: 1, align: 'inside', fill: fill(LINE) } });
    T(H(row, 0, { width: 40 }), r[0], { size: 14, color: SUB });
    const m = V(H(row, 0, { width: 220 }), 2); T(m, r[1], { size: 14, weight: 600, color: INK }); T(m, r[2], { size: 12, color: MUTE });
    r.slice(3).forEach((v) => T(H(row, 0, { width: 70 }), v, { size: 14, color: INK }));
  });
};
const files = (p, w) => { const c = card(p, w, '备注与附件'); T(c, '没有备注 · 没有附件', { size: 13, color: MUTE }); };

const variant = (root, title, note, good, build) => {
  const col = V(root, 10, { width: W + 32 });
  const h = H(col, 8); T(h, title, { size: 16, weight: 600, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: W + 32 });
  const bg = V(col, 16, { padding: 16, cornerRadius: 12, fill: fill(CANVAS) });
  const hd = V(bg, 2); T(hd, 'GR20261119001  已入库', { size: 20, weight: 600, color: INK }); T(hd, '深圳捷运国际物流有限公司 · 来源单号 SD20261119001 · 王磊 · 2026-11-19 15:51 创建', { size: 13, color: SUB });
  build(bg);
};

const root = I(null, { type: 'frame', name: 'R39 单据详情两栏对比', layout: 'vertical', gap: 40, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '单据详情的上半两栏不等高怎么办（R39）', { size: 20, weight: 600, color: INK });
T(root, '入库单、出库单、退货单这类基本信息只有一两行的单据，左栏「基本信息」比右栏「关联单据 + 操作日志」短约 120，左下一块空白（检查表 #3）。现行做法（复审 P18）：上半两栏、明细通栏放下方。付费模板（Minimal、Devias、Materio、Vuexy）的单据详情都是「主栏放基本信息和明细，侧栏放关联与时间线」。尺寸按本机 1366，数据为示意。', { size: 13, color: SUB, w: W + 32 });

variant(root, 'A 现状：上半两栏，明细通栏在下', '明细宽、列多也放得下；但基本信息少的单据左下空一块，越往下右栏越短。', false, (bg) => {
  const top = H(bg, 16, { alignItems: 'start' });
  basic(top, MAIN);
  const side = V(top, 16, { width: SIDE }); related(side, SIDE); logs(side, SIDE);
  items(bg, W); files(bg, W);
});
variant(root, 'B 明细进主栏，侧栏一直到底（推荐）', '主栏依次是基本信息、明细、备注与附件，侧栏放关联单据和操作日志；两栏各自往下排，不再有上半截的空洞。明细列多时在主栏里横向滚动，型号列已固定在左（D77）。改公共的单据详情骨架，入库、出库、退货、发货等单据一起变；销售订单、PI 这类明细很宽的仍保持通栏（明细超过主栏宽度一倍以上时）。', true, (bg) => {
  const top = H(bg, 16, { alignItems: 'start' });
  const main = V(top, 16, { width: MAIN }); basic(main, MAIN); items(main, MAIN); files(main, MAIN);
  const side = V(top, 16, { width: SIDE }); related(side, SIDE); logs(side, SIDE);
});
variant(root, 'C 侧栏只放关联单据，操作日志放下方', '右栏只剩关联单据，和基本信息高度接近；操作日志挪到下方与备注附件并排。上半基本等高，但日志离开了侧栏，长单据的日志会很长、要往下翻才看到。', false, (bg) => {
  const top = H(bg, 16, { alignItems: 'start' });
  basic(top, MAIN); related(top, SIDE);
  items(bg, W);
  const bottom = H(bg, 16, { alignItems: 'start' }); files(bottom, MAIN); logs(bottom, SIDE);
});
