// 汇总行（SummaryBar）直接铺在页面底色上、只有一个指标也独占一行（用户 2026-11-27 指出售后管理、报价管理）的三种处理对比（R29）。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', AMBER = '#FDA92D', ONP = '#4A2E00', LINE = '#E5E8EB', CANVAS = '#F4F6F8';
const W = 1040;

// 三个真实页面的指标（文案取自本机页面）
const PAGES = [
  { title: '售后管理', btn: '新建售后单', items: [{ label: '本月结案', value: '0', help: true }] },
  { title: '报价管理', btn: '新建报价单', items: [
    { label: '本月成交', value: '1', sub: '成交金额 CNY 0.00 · 净利润 CNY 0.00', help: true },
    { label: '本月平均毛利率', value: '—', help: true },
    { label: '本月低于红线', value: '0', help: true },
  ] },
  { title: '采购订单', btn: '新建采购订单', items: [
    { label: '本月已下单', value: 'CNY 1,840.00', sub: '1 张' },
    { label: '本月砍价合计', value: 'CNY 80.00', sub: '砍价率 4.17%', help: true },
    { label: '待发货', value: '1 张', help: true },
    { label: '久放草稿', value: '1', help: true },
    { label: '来源订单已取消', value: '0', help: true },
  ] },
];

const root = I(null, { type: 'frame', name: 'R29 汇总行对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '汇总行：指标少时不独占一行，指标多时有自己的底（R29）', { size: 20, weight: 700, color: INK });
T(root, '现状：指标直接铺在页面底色上，列宽随内容（第一项带副文字就特别宽，后面挤在右边）；只有 1 个指标的页面（售后管理、报销、费用、其他收入、应收、视频任务 6 页）也单独占一行约 56 高，看起来像漏了东西。全站 30 个页面有汇总行：1 个指标 6 页、2 个 7 页、3 个 4 页、4–5 个 12 页。', { size: 13, color: SUB, w: W });

const header = (pg, page, inline) => {
  const h = H(pg, 12, { width: 'fill_container', height: 40, justifyContent: 'space_between' });
  const left = H(h, 8);
  T(left, page.title, { size: 22, weight: 700, color: INK });
  icon(left, 'info', 14, MUTE);
  if (inline) {
    // 1–2 个指标：跟在标题后面，同一行
    const m = H(left, 16, { padding: [0, 0, 0, 16] });
    page.items.forEach((it) => {
      const c = H(m, 6);
      T(c, it.label, { size: 13, color: MUTE });
      T(c, it.value, { size: 15, weight: 600, color: INK });
    });
  }
  const b = H(h, 6, { height: 36, padding: [0, 16], cornerRadius: 8, fill: fill(AMBER) });
  icon(b, 'plus', 14, ONP);
  T(b, page.btn, { size: 14, weight: 600, color: ONP });
};

const cell = (p, it, o) => {
  const c = V(p, 2, { width: o.width || 'fit_content', padding: o.pad || [0, 0] });
  const l = H(c, 4);
  T(l, it.label, { size: 13, color: MUTE });
  if (it.help) icon(l, 'info', 13, MUTE);
  const v = H(c, 8, { alignItems: 'end' });
  T(v, it.value, { size: o.size || 20, weight: 700, color: INK });
  if (it.sub && !o.subBelow) T(v, it.sub, { size: 12, color: SUB });
  if (it.sub && o.subBelow) T(c, it.sub, { size: 12, color: MUTE });
  return c;
};

// 现状：铺在底色上，项间距 40
const bare = (pg, page) => {
  const r = H(pg, 40, { alignItems: 'start' });
  page.items.forEach((it) => cell(r, it, {}));
};
// 白卡分栏：等宽列、竖线分隔，副文字放到数值下面
const strip = (pg, page, fit) => {
  const card = H(pg, 0, { width: fit ? 'fit_content' : 'fill_container', padding: [16, 0], cornerRadius: 10, fill: fill('#FFFFFF'), alignItems: 'start', effects: [{ type: 'shadow', offsetX: 0, offsetY: 1, blur: 2, spread: 0, color: '#919EAB33' }] });
  page.items.forEach((it, i) => {
    if (i) I(card, { type: 'rectangle', name: 'div', width: 1, height: 52, fill: fill(LINE) });
    cell(card, it, { width: fit ? 220 : 'fill_container', pad: [0, 20], subBelow: true });
  });
};
const filterTop = (pg) => I(pg, { type: 'rectangle', name: 'filter', width: 'fill_container', height: 24, cornerRadius: [10, 10, 0, 0], fill: fill('#FFFFFF') });

const variant = (title, note, good, draw) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8);
  T(h, title, { size: 16, weight: 700, color: INK });
  if (good) {
    const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') });
    T(b, '推荐', { size: 12, weight: 600, color: '#118D57' });
  }
  T(col, note, { size: 13, color: SUB, w: W });
  const wrap = V(col, 12, { width: W });
  PAGES.forEach((page) => {
    const pg = V(wrap, 16, { width: W, padding: [20, 24, 0, 24], cornerRadius: 12, clipContent: true, fill: fill(CANVAS), stroke: { thickness: 1, fill: fill(LINE) } });
    draw(pg, page);
    filterTop(pg);
  });
};

variant('A 现状', '指标铺在页面底色上，没有自己的底；列宽随内容；1 个指标也独占一行。', false, (pg, page) => {
  header(pg, page, false);
  bare(pg, page);
});
variant('B 推荐：按数量分两种，1–2 个跟在标题后，3 个及以上放一张白卡分栏', '1–2 个指标：写在标题同一行右侧（名称次要色 + 数字 15 号加粗），不另占一行，省下约 56 高；帮助说明改为悬停在名称上。3 个及以上：放进一张白卡，等宽分栏、竖线分隔，副文字统一放到数值下面，宽度不够时副文字整段隐藏（规则不变）。参考 Shopify 后台订单页顶部指标条（一张卡里分栏）、Ant Design Pro StatisticCard.Group（统计卡组 + 分隔线）、Stripe 控制台概览（标题行带关键数字）。表格之上多了卡片内边距，3 个及以上的页面比现在高约 16。', true, (pg, page) => {
  const few = page.items.length <= 2;
  header(pg, page, few);
  if (!few) strip(pg, page, false);
});
variant('C 一律放白卡分栏', '所有页面同一种形态，最统一；但只有 1 个指标的页面是一张孤零零的小卡，仍然独占一行、不省高度。', false, (pg, page) => {
  header(pg, page, false);
  strip(pg, page, page.items.length <= 2);
});
