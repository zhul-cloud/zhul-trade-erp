// 视图页签（ViewTabs）在浅色模式下没有底、悬在页面底色上（用户 2026-11-27：「选项卡本身没有背景在明亮模式下很奇怪，黑暗模式下问题不大」）的三种处理对比（R31）。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', FAINT = '#919EAB', AMBER = '#FDA92D', LINE = '#E5E8EB', CANVAS = '#F4F6F8', CONTROL = '#DFE3E8';
const W = 1040;
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 1, blur: 2, spread: 0, color: '#919EAB33' }];
const VIEWS = [['全部客户', 8], ['档案补全', 3], ['疑似重复'], ['公海客户', 120]];

const root = I(null, { type: 'frame', name: 'R31 视图页签底色对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '视图页签：浅色模式下给它一个底（R31）', { size: 20, weight: 700, color: INK });
T(root, '现状：页面上其他东西都在白卡里（顶部标签栏、搜索区、表格），只有视图页签直接压在灰色页面底上，像漏放了容器；深色模式下页面底和卡片接近，不明显。三种方案的选中态都照 R27 原型修正：选中 600 字重，没选中的用次要文字色（现在没选中的反而是正文深色，比选中的还重）。', { size: 13, color: SUB, w: W });

const tabs = (p, o = {}) => {
  const row = H(p, 28, { width: 'fill_container', height: o.h || 44, alignItems: 'end', padding: o.pad || [0, 0] });
  VIEWS.forEach(([label, n], i) => {
    const on = i === 0;
    const t = V(row, 0, { width: 'fit_content' });
    const r = H(t, 4, { height: (o.h || 44) - 4 });
    T(r, label, { size: 14, weight: on ? 600 : 400, color: on ? AMBER : SUB });
    if (n !== undefined) T(r, String(n), { size: 14, weight: on ? 600 : 400, color: on ? AMBER : MUTE });
    I(t, { type: 'rectangle', name: 'line', width: 'fill_container', height: 2, cornerRadius: [2, 2, 0, 0], fill: fill(on ? AMBER : '#FFFFFF00') });
  });
  return row;
};
const field = (p, label, ph, w) => {
  const f = H(p, 8, { width: w, height: 40, padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: fill(CONTROL) } });
  T(f, label, { size: 14, color: SUB });
  T(f, ph, { size: 14, color: FAINT });
};
const filters = (p) => {
  const r = H(p, 12, { width: 'fill_container', padding: [16, 20] });
  const s = H(r, 8, { width: 300, height: 40, padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: fill(CONTROL) } });
  icon(s, 'search', 16, MUTE);
  T(s, '英文名、中文名或简称', { size: 14, color: FAINT });
  field(r, '客户编码', '', 200);
  field(r, '主要联系人', '', 200);
  const b = H(r, 0, { height: 36, padding: [0, 16], cornerRadius: 8, fill: fill(AMBER) });
  T(b, '查询', { size: 14, weight: 600, color: '#FFFFFF' });
  return r;
};
const card = (p, o = {}) => V(p, 0, { width: 'fill_container', cornerRadius: 10, fill: fill('#FFFFFF'), effects: SHADOW, ...o });
const header = (pg) => {
  const h = H(pg, 8, { width: 'fill_container', height: 40 });
  T(h, '客户管理', { size: 22, weight: 700, color: INK });
  icon(h, 'info', 14, MUTE);
};
const tableTop = (pg) => {
  const c = card(pg, { padding: [16, 20, 0, 20] });
  T(c, '共 8 条', { size: 14, color: SUB });
  I(c, { type: 'rectangle', width: 'fill_container', height: 40, fill: fill('#FFFFFF') });
};

const variant = (title, note, good, draw) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8);
  T(h, title, { size: 16, weight: 700, color: INK });
  if (good) {
    const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') });
    T(b, '推荐', { size: 12, weight: 600, color: '#118D57' });
  }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = V(col, 16, { width: W, padding: [20, 24, 0, 24], cornerRadius: 12, clipContent: true, fill: fill(CANVAS), stroke: { thickness: 1, fill: fill(LINE) } });
  draw(pg);
};

variant('A 现状', '页签压在灰色页面底上，下面一条细线贴着页面底。', false, (pg) => {
  header(pg);
  const b = V(pg, 0, { width: 'fill_container' });
  tabs(b);
  I(b, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(LINE) });
  const f = card(pg);
  filters(f);
  tableTop(pg);
});
variant('B 推荐：页签做搜索区白卡的卡头', '页签放进搜索区那张白卡的顶部，下面一条细线，再往下是搜索条件：页签管的就是下面的搜索和表格，放在同一张卡上关系最清楚，也不多占高度（比现状矮约 8）。参考 Shopify 后台订单列表（视图页签和筛选在同一张卡顶部）、Ant Design Pro ProTable（工具栏页签在卡内）、GitHub Issues（页签与筛选同在列表卡头）。', true, (pg) => {
  header(pg);
  const c = card(pg);
  tabs(c, { pad: [0, 20], h: 48 });
  I(c, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(LINE) });
  filters(c);
  tableTop(pg);
});
variant('C 页签单独一条白卡', '页签单独放一张矮白卡（高 48），和顶部标签栏同一种做法；改动最小，但页面上多一张卡、多一层间距，表格再往下推约 16。', false, (pg) => {
  header(pg);
  const c = card(pg);
  tabs(c, { pad: [0, 20], h: 48 });
  const f = card(pg);
  filters(f);
  tableTop(pg);
});
