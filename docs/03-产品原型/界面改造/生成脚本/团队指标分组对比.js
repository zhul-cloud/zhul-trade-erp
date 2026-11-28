// 客户跟进「团队」15 张指标卡堆在一起（用户 2026-11-27 指出「第一眼看着痛苦」）的三种处理对比（R30）。单独用 op design 生成。
// 指标与副文字取自本机页面（TeamView tiles，三组见 K16：商机到售后、客户经营、主动开发）。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', ZERO = '#919EAB', LINE = '#E5E8EB', CANVAS = '#F4F6F8';
const W = 1120;
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 1, blur: 2, spread: 0, color: '#919EAB33' }];

const GROUPS = [
  { name: '商机到售后', items: [
    ['新商机', '6', '上周 0'], ['发出报价', '0', '上周 1'], ['发出 PI', '0', '上周 2'], ['到账成交', '0 单', 'CNY 0.00'],
    ['按时送达', '0 / 0', '还没有签收的订单'], ['获得评价', '0', '邀请 0 · 婉拒 0'], ['老客户成交', '0 单', 'CNY 0.00'],
  ] },
  { name: '客户经营', items: [
    ['老客户按时联系', '—', '这段时间没有应联系的老客户'], ['老客户新询盘', '0', '上周 1'], ['沉睡客户', '0', '本期激活 0'],
  ] },
  { name: '主动开发', items: [
    ['新增对象', '0', '本期新建或加入'], ['首次触达', '0', '首次触达日在本期'], ['回复', '0', '本期还没有首次触达'],
    ['转为商机', '0', '进入商机成交'], ['停止开发', '0', '可在客户详情重新开发'],
  ] },
];
const isZero = (v) => /^(0|0 单|0 \/ 0|—)$/.test(v);

const root = I(null, { type: 'frame', name: 'R30 团队指标分组对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '客户跟进「团队」：15 个指标怎么排才不像一面数字墙（R30）', { size: 20, weight: 700, color: INK });
T(root, '现状：15 张一样大、一样重的白卡分三行（7 / 3 / 5 张），三行宽度不一，没有组名，看不出哪几个是一组、先看哪个；大部分是 0，0 和有数的指标一样醒目，真正有数的「新商机 6」淹没在里面。下方「卡在哪一步」已经是「左侧行名 + 一行格子」的写法。', { size: 13, color: SUB, w: W });

const tile = (p, [label, value, sub], o) => {
  const c = V(p, 4, { width: 'fill_container', padding: o.pad, ...(o.card ? { cornerRadius: 10, fill: fill('#FFFFFF'), effects: SHADOW } : {}) });
  T(c, label, { size: 13, color: MUTE });
  T(c, value, { size: o.size || 22, weight: 700, color: o.dimZero && isZero(value) ? ZERO : INK });
  T(c, sub, { size: 12, color: MUTE });
};
const strip = (p, items, o) => {
  items.forEach((it, i) => {
    if (i) I(p, { type: 'rectangle', name: 'div', width: 1, height: 56, fill: fill(LINE) });
    tile(p, it, { pad: [0, 16], dimZero: o.dimZero, size: 20 });
  });
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
  const pg = V(col, 12, { width: W, padding: [20, 24, 24, 24], cornerRadius: 12, fill: fill(CANVAS), stroke: { thickness: 1, fill: fill(LINE) } });
  draw(pg);
};

variant('A 现状', '15 张同样的卡，三行 7 / 3 / 5，没有组名，0 和有数的一样醒目。', false, (pg) => {
  GROUPS.forEach((g) => {
    const r = H(pg, 12, { width: 'fill_container', alignItems: 'start' });
    g.items.forEach((it) => tile(r, it, { card: true, pad: [16, 16] }));
  });
});
variant('B 推荐：一张卡、三行，左侧组名，格子竖线分隔，0 变淡', '15 张卡合成一张：每组一行，左侧写组名（与下方「卡在哪一步」同一种写法），格子之间竖线分隔、行之间横线分隔，不再是 15 个盒子。数值为 0 或「—」用浅灰，有数的保持墨色，扫一眼就能找到「新商机 6」。参考 Stripe 控制台指标卡（同卡分栏、竖线分隔）、Linear Insights（零值弱化）、Shopify 后台分析（一卡多指标）。需要在规范补「零值弱化」，与 P16「汇总行大数字照常写 0」不冲突：仍写 0，只是颜色变淡。', true, (pg) => {
  const card = V(pg, 0, { width: 'fill_container', cornerRadius: 10, fill: fill('#FFFFFF'), effects: SHADOW, padding: [4, 0] });
  GROUPS.forEach((g, gi) => {
    if (gi) I(card, { type: 'rectangle', name: 'hdiv', width: 'fill_container', height: 1, fill: fill(LINE) });
    const r = H(card, 0, { width: 'fill_container', padding: [16, 16, 16, 20], alignItems: 'center' });
    T(r, g.name, { size: 14, weight: 600, color: INK, w: 96 });
    const s = H(r, 0, { width: 'fill_container', alignItems: 'center' });
    strip(s, g.items, { dimZero: true });
  });
});
variant('C 分三张组卡，组名做卡片标题', '每组一张白卡，卡头写组名；第一行「商机到售后」占满，第二行「客户经营」「主动开发」并排（3 : 5）。分组清楚，但页面上仍有 3 张卡加下面的负责人表、卡在哪一步，卡片数量多；零值不处理。', false, (pg) => {
  const box = (p, g, width) => {
    const c = V(p, 12, { width, padding: [16, 0, 16, 0], cornerRadius: 10, fill: fill('#FFFFFF'), effects: SHADOW });
    T(H(c, 0, { padding: [0, 20] }), g.name, { size: 14, weight: 600, color: INK });
    const s = H(c, 0, { width: 'fill_container', alignItems: 'center', padding: [0, 4] });
    strip(s, g.items, { dimZero: false });
  };
  box(pg, GROUPS[0], 'fill_container');
  const r = H(pg, 12, { width: 'fill_container', alignItems: 'start' });
  box(r, GROUPS[1], 400);
  box(r, GROUPS[2], 'fill_container');
});
