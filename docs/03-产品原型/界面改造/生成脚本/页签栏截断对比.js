// 页签栏放不下时左端只露半个标签（复审 S7）的三种处理对比（R22）。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINK = '#B76E00', LINE = '#E5E8EB', CANVAS = '#F4F6F8';
const W = 1040, BAR = 760;

const TABS = [
  ['首页', true], ['销售订单'], ['销售订单 SO20261118001'], ['客户档案'], ['PRIME CONTROLS LLC'],
  ['采购订单'], ['采购订单 PO20261120003'], ['库存查询'], ['出运 SH20261119001', false, true], ['收款工作台'],
];

const root = I(null, { type: 'frame', name: '页签栏截断对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '页签栏放不下时：左端怎么处理（复审 S7）', { size: 20, weight: 700, color: INK });
T(root, '场景：打开 10 个标签，当前在「出运 SH20261119001」，顶栏留给标签的宽度约 760。三种方案右端都保留现有的「全部标签」下拉（显示没露出来的数量）。', { size: 13, color: SUB, w: W });

const tab = (p, [label, pinned, on]) => {
  const t = V(p, 0, { height: 60, width: 'fit_content' });
  const row = H(t, 4, { height: 58, padding: pinned ? [2, 16, 0, 16] : [2, 8, 0, 16] });
  if (pinned) icon(row, 'house', 14, on ? LINK : SUB);
  T(row, label, { size: 14, weight: 500, color: on ? LINK : SUB });
  if (!pinned) icon(row, 'x', 14, on ? LINK : MUTE);
  I(t, { type: 'rectangle', name: 'line', width: 'fill_container', height: 2, fill: fill(on ? LINK : '#FFFFFF') });
};

// 一条标签栏：fade 两端渐隐，arrows 两端箭头
const bar = (p, { key, fade, arrows, hiddenCount }) => {
  const shell = H(p, 8, { width: W, height: 60, padding: [0, 16], fill: fill('#FFFFFF') });
  if (arrows) { const a = H(shell, 0, { width: 28, height: 28, cornerRadius: 6, justifyContent: 'center', fill: fill('#F6F7F8') }); icon(a, 'chevron-left', 16, SUB); }
  const clip = I(shell, { type: 'frame', name: 'clip', width: arrows ? BAR - 72 : BAR, height: 60, clipContent: true, layout: 'none' });
  const strip = I(clip, { type: 'frame', name: 'strip-' + key, layout: 'horizontal', gap: 4, alignItems: 'start', x: 0, y: 0, height: 60, width: 'fit_content' });
  TABS.forEach((t) => tab(strip, t));
  if (fade) {
    // 右边没有更多标签，只画左侧阴影
    I(clip, { type: 'rectangle', name: 'shadow-l', x: 0, y: 0, width: 12, height: 58, fill: [{ type: 'linear_gradient', angle: 90, stops: [{ offset: 0, color: '#1C252E24' }, { offset: 1, color: '#1C252E00' }] }] });
  }
  if (arrows) { const a = H(shell, 0, { width: 28, height: 28, cornerRadius: 6, justifyContent: 'center', fill: fill('#F6F7F8') }); icon(a, 'chevron-right', 16, SUB); }
  const more = H(shell, 4, { height: 32, padding: [0, 10], cornerRadius: 8, stroke: { thickness: 1, fill: fill(LINE) } });
  icon(more, 'layers', 14, SUB); T(more, `全部标签 ${hiddenCount}`, { size: 13, color: SUB });
};

const variant = (title, note, good, opts) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = V(col, 0, { width: W, padding: [0, 0, 24, 0], cornerRadius: 12, clipContent: true, fill: fill(CANVAS), stroke: { thickness: 1, fill: fill(LINE) } });
  bar(pg, opts);
};

// 标签条的左移量由 R22 的导出脚本按实际排版设置：A 停在某个标签中间，B、C 整签对齐，三者当前标签都完整露出
variant('A 现状：滚动停在任意位置', '切换标签时只保证当前标签露出来（scrollIntoView nearest），左端经常停在某个标签中间，只剩「…ROLS LLC ×」半截，看不出是什么页面，也看不出左边还有标签。', false, { key: 'A', hiddenCount: 6 });
variant('B 推荐：整签对齐 + 边缘阴影', '滚动结束时吸附到整个标签（CSS scroll-snap），左端永远从一个完整标签开始；哪一侧还有标签，哪一侧边缘就有一道 12 宽的浅阴影（这里左边还有 5 个），提示可以滚动。鼠标滚轮在标签栏上转为左右滚动；左边藏起来的标签仍可从「全部标签」里选。参考 Ant Design Tabs（两侧内阴影 + 更多下拉）、VS Code 编辑器标签栏（滚动 + 下拉列表）。不占额外宽度。', true, { key: 'B', fade: true, hiddenCount: 5 });
variant('C 备选：两端箭头', '左右各一个箭头按钮，点一下移动一个标签，同样整签对齐。参考 Material 3 可滚动标签。箭头常驻占掉约 72 宽，能显示的标签少一个；鼠标用户多一种操作方式，但和「全部标签」下拉功能重叠。', false, { key: 'C', arrows: true, hiddenCount: 6 });
