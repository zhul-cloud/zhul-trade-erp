// 客户跟进「团队」15 张指标卡全是同一种白、同一种字（用户 2026-11-27：「多卡片本身没问题，问题是没有做个性化区分，都是同一个颜色」）的三种配色对比（R32）。单独用 op design 生成。
// 指标与副文字取自本机页面；颜色用现有数字卡色系 METRIC_FAMILY（蓝、青、紫，浅底 16%），不新增颜色。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINE = '#E5E8EB', CANVAS = '#F4F6F8';
const W = 1120;
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 1, blur: 2, spread: 0, color: '#919EAB33' }];
const FAM = { blue: ['#1877F2', '#DAE9FD'], cyan: ['#00B8D9', '#D6F4F9'], purple: ['#8E33FF', '#EDDEFF'], pink: ['#F0468E', '#FDE1ED'], neutral: ['#637381', '#ECEEF0'] };
const CYCLE = ['blue', 'cyan', 'purple', 'pink', 'neutral'];

const GROUPS = [
  { name: '商机到售后', fam: 'blue', items: [
    ['新商机', '6', '上周 0', 'sparkles'], ['发出报价', '0', '上周 1', 'file-text'], ['发出 PI', '0', '上周 2', 'file-check'],
    ['到账成交', '0 单', 'CNY 0.00', 'circle-dollar-sign'], ['按时送达', '0 / 0', '还没有签收的订单', 'truck'],
    ['获得评价', '0', '邀请 0 · 婉拒 0', 'star'], ['老客户成交', '0 单', 'CNY 0.00', 'repeat'],
  ] },
  { name: '客户经营', fam: 'cyan', items: [
    ['老客户按时联系', '—', '这段时间没有应联系的老客户', 'phone'], ['老客户新询盘', '0', '上周 1', 'inbox'], ['沉睡客户', '0', '本期激活 0', 'moon'],
  ] },
  { name: '主动开发', fam: 'purple', items: [
    ['新增对象', '0', '本期新建或加入', 'user-plus'], ['首次触达', '0', '首次触达日在本期', 'send'], ['回复', '0', '本期还没有首次触达', 'message-circle'],
    ['转为商机', '0', '进入商机成交', 'arrow-right-left'], ['停止开发', '0', '可在客户详情重新开发', 'circle-pause'],
  ] },
];

const root = I(null, { type: 'frame', name: 'R32 团队指标配色对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '客户跟进「团队」：给 15 张指标卡做区分（R32）', { size: 20, weight: 700, color: INK });
T(root, '现状：15 张卡同一种白底、同一种字，三组之间没有任何区分，扫一眼分不出哪张是哪一类。下面三种都保留多卡片布局（三行 7 / 3 / 5），只加颜色区分；颜色取现有数字卡色系，浅色、深色都已校验过。', { size: 13, color: SUB, w: W });

const tile = (p, [label, value, sub, ic], o = {}) => {
  const c = V(p, 10, { width: 'fill_container', padding: [16, 16], cornerRadius: 10, fill: fill('#FFFFFF'), effects: SHADOW });
  if (o.fam) {
    const [fg, bg] = FAM[o.fam];
    const top = H(c, 10);
    const b = H(top, 0, { width: 32, height: 32, cornerRadius: 8, justifyContent: 'center', fill: fill(bg) });
    icon(b, ic, 18, fg);
    T(top, label, { size: 13, color: MUTE });
  } else {
    T(c, label, { size: 13, color: MUTE });
  }
  T(c, value, { size: 22, weight: 700, color: INK });
  T(c, sub, { size: 12, color: MUTE });
};
const groupHead = (p, g) => {
  const h = H(p, 8, { padding: [4, 0, 0, 0] });
  I(h, { type: 'rectangle', width: 4, height: 14, cornerRadius: 2, fill: fill(FAM[g.fam][0]) });
  T(h, g.name, { size: 14, weight: 600, color: INK });
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

variant('A 现状', '15 张同样的白卡，没有颜色区分。', false, (pg) => {
  GROUPS.forEach((g) => {
    const r = H(pg, 12, { width: 'fill_container', alignItems: 'start' });
    g.items.forEach((it) => tile(r, it));
  });
});
variant('B 推荐：按组配色 + 每张卡一个图标块，组前加组名', '每组一种颜色：商机到售后蓝、客户经营青、主动开发紫。每张卡左上角一个 32 的图标块（该组颜色、浅底 16%），图标表示指标含义（报价、到账、送达、评价……）；每组前一行写组名，带同色短竖条。颜色只用在图标块和竖条上，数字仍是墨色，不会和状态色（红绿橙）混淆。参考 PrimeDash 指标卡（彩色图标块）、Shopify 后台分析（按类别着色）、Material 3 统计卡。符合规范「类别图标块可以多色」。', true, (pg) => {
  GROUPS.forEach((g) => {
    groupHead(pg, g);
    const r = H(pg, 12, { width: 'fill_container', alignItems: 'start' });
    g.items.forEach((it) => tile(r, it, { fam: g.fam }));
  });
});
variant('C 每张卡一种颜色', '15 张卡按 5 种颜色轮流着色，最花哨、每张都不一样；但颜色不再代表分组，同一组里颜色乱跳，相邻两张偶尔同色，看久了反而更乱。数据可视化规范要求颜色跟着类别走、不循环着色。', false, (pg) => {
  let k = 0;
  GROUPS.forEach((g) => {
    const r = H(pg, 12, { width: 'fill_container', alignItems: 'start' });
    g.items.forEach((it) => tile(r, it, { fam: CYCLE[k++ % CYCLE.length] }));
  });
});
