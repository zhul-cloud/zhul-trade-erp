// 长详情页往下滚后找不到主操作（复审 S15）的处理对比（R24）。单独用 op design 生成。
// 场景：销售订单详情（整页约 2660 高）滚到「型号明细」中间，1366 宽、视口 800 高，只画内容区。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}), ...(o.align ? { textAlign: o.align } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const R = (p, w, h, c, o = {}) => I(p, { type: 'rectangle', width: w, height: h, cornerRadius: o.r ?? 0, fill: fill(c), ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINK = '#B76E00', LINE = '#E5E8EB', CANVAS = '#F4F6F8', HEAD = '#F9FAFB', AMBER = '#FDA92D', AMBER_SOFT = '#FFF5E6', ON_PRIMARY = '#4A2E00';
const W = 1100, VIEW_H = 520;

const root = I(null, { type: 'frame', name: '详情页吸顶对比', layout: 'vertical', gap: 32, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '长详情页往下滚后：单号、状态和主操作放哪（复审 S15）', { size: 20, weight: 700, color: INK });
T(root, '场景：销售订单详情整页约 2660 高（1366×800 下 3 屏多），往下滚到「型号明细」中间。下面每张图是滚动后看到的内容区（顶栏标签页本来就固定在上方）。', { size: 13, color: SUB, w: W });

const topBar = (p) => {
  const b = H(p, 24, { width: W, height: 64, padding: [0, 16], cornerRadius: 10, fill: fill('#FFFFFF'), effects: SH });
  [['工作台'], ['SO20261118001', true], ['库存查询'], ['收款管理']].forEach(([n, on]) => { const t = V(b, 0, { height: 64, justifyContent: 'center' }); T(t, n, { size: 14, color: on ? LINK : SUB }); });
};

const button = (p, label, o = {}) => {
  const b = H(p, 6, { height: 36, padding: [0, o.primary ? 16 : 10], cornerRadius: 8, fill: fill(o.primary ? AMBER : '#FFFFFF00') });
  if (o.icon) icon(b, o.icon, 16, o.primary ? ON_PRIMARY : o.mute ? MUTE : LINK);
  if (label) T(b, label, { size: 14, weight: o.primary ? 600 : 400, color: o.primary ? ON_PRIMARY : o.mute ? MUTE : LINK });
};
const actions = (p) => {
  const a = H(p, 4);
  button(a, '售后', { icon: 'headset' });
  button(a, '客户已收货', { icon: 'check', mute: true });
  button(a, '', { icon: 'ellipsis', mute: true });
  button(a, '发货通知', { icon: 'send', primary: true });
};

const compactHeader = (p, anchors) => {
  const h = H(p, 12, { width: W, height: 56, padding: [0, 20], cornerRadius: 10, fill: fill('#FFFFFF'), effects: SH, justifyContent: 'space_between' });
  const l = H(h, 10);
  T(l, 'SO20261118001', { size: 16, weight: 700, color: INK });
  const tag = H(l, 0, { height: 24, padding: [0, 8], cornerRadius: 4, fill: fill('#ECEEF0') }); T(tag, '待采购', { size: 12, weight: 600, color: '#56636F' });
  T(l, 'PRIME CONTROLS LLC', { size: 13, color: MUTE });
  if (anchors) {
    const n = H(h, 20);
    ['概况', '型号明细', '金额与条款', '附件与备注'].forEach((x, i) => { const t = V(n, 0, { height: 56, justifyContent: 'center' }); T(t, x, { size: 14, color: i === 1 ? LINK : SUB }); });
  }
  actions(h);
};

const COLS = [['#', 44], ['品牌', 150], ['品类', 100], ['型号', 220], ['数量', 90], ['单价（USD）', 120], ['小计（USD）', 130], ['现货 / 期货', 110]];
const NUM = [4, 5, 6]; // 数量、金额右对齐
const ROWS = [
  ['3', 'Schneider Electric', '接触器', 'LC1D25M7C', '4', '62.00', '248.00', '现货'],
  ['4', 'Omron', '继电器', 'MY2N-GS DC24', '20', '3.80', '76.00', '现货'],
  ['5', 'Siemens', 'PLC', '6ES7214-1AG40-0XB0', '1', '208.00', '208.00', '期货'],
  ['6', 'ABB', '断路器', 'S203-C16', '6', '18.50', '111.00', '现货'],
  ['7', 'Phoenix Contact', '端子', 'UT 2,5', '200', '0.42', '84.00', '现货'],
];
const tableHeader = (p) => {
  const h = H(p, 0, { width: W - 48, height: 44, cornerRadius: 8, fill: fill(HEAD) });
  COLS.forEach(([n, w], i) => { const c = H(h, 0, { width: w, padding: [0, 12] }); T(c, n, { size: 13, color: MUTE, w: w - 24, align: NUM.includes(i) ? 'right' : 'left' }); });
};
const rows = (p, n) => ROWS.slice(0, n).forEach((r) => {
  const wrap = V(p, 0, { width: W - 48 });
  const row = H(wrap, 0, { width: W - 48, height: 63 });
  COLS.forEach(([, w], i) => { const c = H(row, 0, { width: w, padding: [0, 12] }); T(c, r[i], { size: 14, weight: i === 3 || i === 6 ? 600 : 400, color: i === 1 || i === 2 ? SUB : INK, w: w - 24, align: NUM.includes(i) ? 'right' : 'left' }); });
  R(wrap, W - 48, 1, LINE);
});

const viewport = (title, note, good, kind) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = V(col, 16, { width: W + 32, height: VIEW_H, padding: 16, cornerRadius: 12, fill: fill(CANVAS), clipContent: true });
  topBar(pg);
  if (kind !== 'A') compactHeader(pg, kind === 'C');
  const card = V(pg, 12, { width: W, padding: [kind === 'A' ? 0 : 16, 24, 16, 24], cornerRadius: 12, fill: fill('#FFFFFF'), effects: SH });
  if (kind === 'A') { rows(card, 5); return; }
  tableHeader(card);
  rows(card, 4);
};

viewport('A 现状：页头和表头都滚走了', '单号、状态、「发货通知」等操作要滚回页面最上面才能看到；型号明细的表头也已滚出屏幕，看不出哪列是单价、哪列是小计。', false, 'A');
viewport('B 页头吸顶缩成 56 高 + 表头吸顶', '原页头滚出屏幕后，顶栏下方出现一条 56 高的白卡：单号、状态、客户，右侧是和原页头相同的操作（主按钮仍是唯一的主题色按钮）；滚回顶部时收起。长表格的表头贴在这条下面，横向滚动条贴在视口底部（不用滚到表格最下面才能左右拖）。只在页面超过两屏时启用。参考 GitHub PR 页、Linear 详情页的吸顶标题条。', true, 'B');
viewport('C 在 B 的基础上加分区导航', '吸顶条中间加「概况 / 型号明细 / 金额与条款 / 附件与备注」，点击跳到对应分区、滚动时高亮当前分区。导航更快，但吸顶条要同时放单号、状态、客户、4 个分区和全部操作，这张单在 1366 宽下刚好放下，客户名更长或操作更多的单据就要省略客户名或把操作收进「更多」；分区名还要随单据类型维护。', false, 'C');
