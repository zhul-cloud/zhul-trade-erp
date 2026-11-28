// 页签选中态看不清（用户 2026-11-27 指出客户管理「全部客户 8」难看）的三种处理对比（R28）。单独用 op design 生成。
// 实测现状：选中 #FDA92D 400 字重、其余页签正文色 #212B36 400，选中的比没选中的还浅；数量与名称同色同重，只隔 4px。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', AMBER = '#FDA92D', DEEP = '#B76E00', LINE = '#E5E8EB', CANVAS = '#F4F6F8', PILL = '#DFE3E8';
const W = 1040;

const VIEWS = [['全部客户', 8], ['档案补全', 3], ['疑似重复'], ['公海客户', 120]];
const TOPS = [['工作台', 'house'], ['客户管理'], ['SO20261118001'], ['采购订单']];

const root = I(null, { type: 'frame', name: 'R28 页签选中态对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '页签选中态：怎么让「选中的那个」一眼看出来（R28）', { size: 20, weight: 700, color: INK });
T(root, '实测现状：选中页签是琥珀原色 #FDA92D、400 字重，页面底色上对比度 1.78:1；没选中的页签反而是正文深色 #212B36。选中的比没选中的还浅，层次倒过来了。数量和名称同色同重、只隔 4px，「全部客户 8」读成一个词。顶部标签栏的选中标签同样是琥珀字。R27 原型画的是深琥珀 #B76E00、600 字重，实现时用了链接色令牌，没照原型。', { size: 13, color: SUB, w: W });

// 一组视图页签 + 一段顶部标签栏
const demo = (p, s) => {
  const pg = V(p, 20, { width: W, padding: [20, 24, 0, 24], cornerRadius: 12, clipContent: true, fill: fill(CANVAS), stroke: { thickness: 1, fill: fill(LINE) } });
  // 顶部标签栏
  const top = H(pg, 4, { width: 'fill_container', height: 56, padding: [0, 16], cornerRadius: 12, fill: fill('#FFFFFF'), alignItems: 'start' });
  TOPS.forEach(([label, ic], i) => {
    const on = i === 2;
    const t = V(top, 0, { width: 'fit_content' });
    const row = H(t, 6, { height: 54, padding: [0, 14] });
    if (ic) icon(row, ic, 14, on ? s.on : s.off);
    T(row, label, { size: 14, weight: on ? s.weight : 400, color: on ? s.on : s.off });
    if (!ic) icon(row, 'x', 14, MUTE);
    I(t, { type: 'rectangle', name: 'line', width: 'fill_container', height: 2, cornerRadius: [2, 2, 0, 0], fill: fill(on ? AMBER : '#FFFFFF') });
  });
  // 页头 + 视图页签
  T(pg, '客户管理', { size: 22, weight: 700, color: INK });
  const bar = V(pg, 0, { width: 'fill_container' });
  const row = H(bar, 28, { width: 'fill_container', height: 44, alignItems: 'end' });
  VIEWS.forEach(([label, n], i) => {
    const on = i === 0;
    const t = V(row, 0, { width: 'fit_content' });
    const r = H(t, s.pill ? 6 : 4, { height: 40 });
    T(r, label, { size: 14, weight: on ? s.weight : 400, color: on ? s.on : s.off });
    if (n !== undefined) {
      if (s.pill) {
        const b = H(r, 0, { height: 20, padding: [0, 7], cornerRadius: 10, fill: fill(PILL) });
        T(b, String(n), { size: 12, weight: 600, color: SUB });
      } else {
        T(r, String(n), { size: 14, weight: on ? s.weight : 400, color: on ? s.countOn : MUTE });
      }
    }
    I(t, { type: 'rectangle', name: 'line', width: 'fill_container', height: 2, cornerRadius: [2, 2, 0, 0], fill: fill(on ? AMBER : '#FFFFFF00') });
  });
  I(bar, { type: 'rectangle', name: 'base', width: 'fill_container', height: 1, fill: fill(LINE) });
  // 下面是主卡的上沿，示意页签管辖的内容
  I(pg, { type: 'rectangle', name: 'card', width: 'fill_container', height: 28, cornerRadius: [10, 10, 0, 0], fill: fill('#FFFFFF') });
};

const variant = (title, note, good, s) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8);
  T(h, title, { size: 16, weight: 700, color: INK });
  if (good) {
    const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') });
    T(b, '推荐', { size: 12, weight: 600, color: '#118D57' });
  }
  T(col, note, { size: 13, color: SUB, w: W });
  demo(col, s);
};

variant('A 现状', '选中：琥珀字 #FDA92D、400（1.78:1）；其余：正文深色 #212B36、400；数量与名称同色。', false,
  { on: AMBER, off: INK, weight: 400, countOn: AMBER });
variant('B 推荐：深色加粗字 + 琥珀底线，数量用灰色小胶囊', '选中：正文深色 #212B36、600 字重，靠字重和 2px 琥珀底线区分（13:1）；其余：次要文字色 #3E4750、400。数量放进灰色小胶囊（12 号、圆角 10，7.3:1），和名称分开，所有页签同一种胶囊，不随选中变色。主题色只出现在底线上，换任何主题色文字都清楚。顶部标签栏同样改。参考 GitHub Primer UnderlineNav（选中深色加粗 + 彩色底线 + 灰色 Counter）、Atlassian Tabs（数量用 Badge）、Shopify Polaris Tabs（数量用灰色徽标）。需要改规范「选中用主题色字」与「数量写名称 N、不加徽标」两条。', true,
  { on: INK, off: SUB, weight: 600, pill: true });
variant('C 照 R27 原型：深琥珀字', '选中：深琥珀 #B76E00、600（3.7:1，低于正文 4.5:1）；其余次要文字色；数量照旧写在名称后、选中时同为深琥珀。改动最小，但深琥珀只对琥珀主题成立，换主题要另配一档深色；数量仍和名称粘在一起。', false,
  { on: DEEP, off: SUB, weight: 600, countOn: DEEP });
