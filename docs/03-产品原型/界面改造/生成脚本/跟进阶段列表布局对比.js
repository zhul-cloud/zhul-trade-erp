// 客户跟进「商机成交」等阶段列表：右侧作战卡常驻占 360，表格只剩 717 宽、操作列「记跟进」被截掉（截手册图时发现）的三种布局对比（R33）。单独用 op design 生成。
// 列与文案取自本机页面（PI 后：客户 / 商机、价值、PI · 金额 · 发出日期、已收 / 未收、预计付款、下一步、操作）。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', AMBER = '#FDA92D', LINE = '#E5E8EB', CANVAS = '#F4F6F8', HEAD = '#F6F7F8', CONTROL = '#DFE3E8';
const W = 1116;
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 1, blur: 2, spread: 0, color: '#919EAB33' }];

const COLS = [
  ['客户 / 商机', 200, 'PRIME CONTROLS LLC', '美国 · Mike'],
  ['价值', 120, 'B 未评', ''],
  ['PI · 金额 · 发出日期', 190, '2 张 PI', 'USD 1,000.00'],
  ['已收 / 未收', 150, 'USD 0.00', '未收 USD 1,000.00'],
  ['预计付款', 100, '—', ''],
  ['下一步', 260, 'PI 发出第 8 天：按客户说的付款', '日期确认付款进度…'],
];

const root = I(null, { type: 'frame', name: 'R33 跟进阶段列表布局对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '客户跟进阶段列表：表格太窄、操作被截掉怎么办（R33）', { size: 20, weight: 700, color: INK });
T(root, '实测（1440 宽，商机成交 · PI 后）：右侧「作战卡」常驻占 360，表格容器只剩 717，内容宽 1449；左侧客户列、右侧操作列固定后，中间能看的只有约 270，要一直横向拖；操作列 190 宽放不下「延后 · 已跟进 · 记跟进」（要 249），「记跟进」被截掉。供应链跟进的阶段列表是同一种布局。', { size: 13, color: SUB, w: W });

const table = (p, width, cols, ops) => {
  const t = V(p, 0, { width, clipContent: true, cornerRadius: 8 });
  const head = H(t, 0, { width: 'fill_container', height: 44, fill: fill(HEAD), padding: [0, 12] });
  const body = H(t, 0, { width: 'fill_container', height: 64, padding: [0, 12] });
  I(head, { type: 'rectangle', width: 28, height: 16, fill: fill('#FFFFFF00') });
  I(body, { type: 'rectangle', width: 16, height: 16, cornerRadius: 4, stroke: { thickness: 1, fill: fill(CONTROL) }, fill: fill('#FFFFFF') });
  I(body, { type: 'rectangle', width: 12, height: 16, fill: fill('#FFFFFF00') });
  cols.forEach(([h, w, a, b]) => {
    const hc = H(head, 0, { width: w }); T(hc, h, { size: 13, color: MUTE });
    const bc = V(body, 2, { width: w }); T(bc, a, { size: 14, weight: h === '客户 / 商机' ? 600 : 400, color: INK }); if (b) T(bc, b, { size: 12, color: MUTE });
  });
  const ho = H(head, 0, { width: ops.w }); T(ho, '操作', { size: 13, color: MUTE });
  const bo = H(body, 8, { width: ops.w });
  ops.items.forEach(([label, primary]) => {
    const b = H(bo, 4, { height: 28, padding: [0, 10], cornerRadius: 6, ...(primary ? { stroke: { thickness: 1, fill: fill(CONTROL) } } : { stroke: { thickness: 1, fill: fill(CONTROL) } }) });
    T(b, label, { size: 13, color: INK });
  });
  return t;
};
const card = (p, o = {}) => V(p, 12, { cornerRadius: 10, fill: fill('#FFFFFF'), effects: SHADOW, padding: [16, 16], ...o });
const battle = (p, w) => {
  const c = card(p, { width: w });
  const h = H(c, 8); T(h, '作战卡 · S7 成交推进', { size: 16, weight: 600, color: INK });
  T(c, 'PRIME CONTROLS LLC · 2 张 PI', { size: 12, color: MUTE });
  T(c, '这一步的目标', { size: 13, weight: 600, color: INK });
  T(c, '按客户说的付款日期确认付款进度，没付的问清审批、银行或付款卡点。', { size: 13, color: SUB, w: w - 32 });
  T(c, '该做的事', { size: 13, weight: 600, color: INK });
  ['确认客户收到 PI', '问清付款审批到哪一步', '约定下次确认的日期'].forEach((s) => { const r = H(c, 8); I(r, { type: 'rectangle', width: 14, height: 14, cornerRadius: 3, stroke: { thickness: 1, fill: fill(CONTROL) } }); T(r, s, { size: 13, color: INK }); });
  return c;
};

const variant = (title, note, good, draw) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8);
  T(h, title, { size: 16, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = V(col, 12, { width: W, padding: [16, 16], cornerRadius: 12, clipContent: true, fill: fill(CANVAS), stroke: { thickness: 1, fill: fill(LINE) } });
  draw(pg);
};

variant('A 现状：作战卡常驻右侧', '表格容器 717（这里按比例画出），横向要拖动，「记跟进」被截在操作列外。', false, (pg) => {
  const r = H(pg, 16, { width: 'fill_container', alignItems: 'start' });
  const left = card(r, { width: 704 });
  const clip = I(left, { type: 'frame', width: 672, height: 112, clipContent: true, layout: 'none' });
  const inner = V(clip, 0, { x: 0, y: 0, width: 'fit_content' });
  table(inner, 'fit_content', COLS, { w: 150, items: [['延后'], ['已跟进']] });
  battle(r, 364);
});
variant('B 推荐：作战卡改成可收起，默认收在表头右侧一个按钮里', '作战卡默认收起，表格占满整张卡（1116）；表头右侧一个「作战卡」按钮，点开从右侧滑出、盖在表格上，看完关掉，展开状态按人记住。表格的「价值」「预计付款」默认收进「列」设置，操作列只留「记跟进」，「延后」「已跟进」收进「…」：默认列在 1366 宽不超过 1043，不横向滚动。参考 Linear 的详情侧栏（可收起、盖在列表上）、Jira 列表的详情抽屉、HubSpot 记录列表的预览面板。作战卡内容不变。', true, (pg) => {
  const c = card(pg, { width: 'fill_container' });
  const top = H(c, 8, { width: 'fill_container', justifyContent: 'space_between' });
  T(top, '共 1 个客户', { size: 13, color: SUB });
  const b = H(top, 6, { height: 32, padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: fill(CONTROL) } }); icon(b, 'panel-right-open', 16, SUB); T(b, '作战卡', { size: 13, color: INK });
  const cols = COLS.filter(([h]) => h !== '价值' && h !== '预计付款');
  table(c, 'fill_container', cols, { w: 150, items: [['记跟进', true], ['…']] });
});
variant('C 作战卡放到表格上方，折叠成一行', '作战卡移到阶段导航下面，默认只露一行「这一步的目标」，点开展开清单；表格占满宽度。改动小，但每次切阶段都要上下看，展开时会把表格推下去。', false, (pg) => {
  const c = card(pg, { width: 'fill_container' });
  const strip = H(c, 10, { width: 'fill_container', height: 40, padding: [0, 12], cornerRadius: 8, fill: fill(HEAD) });
  T(strip, '作战卡 · S7 成交推进', { size: 13, weight: 600, color: INK });
  T(strip, '按客户说的付款日期确认付款进度，没付的问清审批、银行或付款卡点。', { size: 13, color: SUB });
  icon(strip, 'chevron-down', 16, MUTE);
  const cols = COLS.filter(([h]) => h !== '价值' && h !== '预计付款');
  table(c, 'fill_container', cols, { w: 150, items: [['记跟进', true], ['…']] });
});
