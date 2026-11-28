// 琥珀主题实底上的文字对比图（R17）：深石板 / 深琥珀棕 / 白字 / 白字加深底。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const W = 1180;

const root = I(null, { type: 'frame', name: '琥珀按钮文字对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '琥珀主题：主题色实底上的文字（主按钮、Logo 首字、勾选框）', { size: 20, weight: 700, color: '#212B36' });

const scene = (parent, S, bg, ink, card, line) => {
  const box = H(parent, 16, { width: 'fill_container', padding: 20, cornerRadius: 12, fill: fill(bg) });
  const c = H(box, 16, { width: 'fill_container', padding: [14, 16], cornerRadius: 10, fill: fill(card), stroke: { thickness: 1, fill: fill(line), align: 'inside' } });
  const lo = H(c, 0, { width: 28, height: 28, cornerRadius: 7, justifyContent: 'center', fill: fill(S.bg) }); T(lo, '烛', { weight: 700, color: S.fg });
  const b = H(c, 6, { height: 36, padding: [0, 16], cornerRadius: 8, fill: fill(S.bg) }); icon(b, 'plus', 18, S.fg); T(b, '新建销售订单', { size: 14, weight: 600, color: S.fg });
  const ck = H(c, 8); const k = H(ck, 0, { width: 16, height: 16, cornerRadius: 4, justifyContent: 'center', fill: fill(S.bg) }); icon(k, 'check', 12, S.fg); T(ck, '已选 3 条', { color: ink });
};

const variant = (title, note, good, S) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: '#212B36' });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: '#3E4750', w: W });
  const r = H(col, 16, { width: W });
  scene(r, S, '#F4F6F8', '#212B36', '#FFFFFF', '#E5E8EB');
  scene(r, S, '#0A0A0A', '#F5F5F5', '#1A1A1A', '#2A2A2A');
};

variant('A 现状：深石板字 #212B36', '对比度 7.4:1，清楚；但字是偏蓝的近黑，压在暖色琥珀上像「黑字」，和主题色不是一家。', false, { bg: '#FDA92D', fg: '#212B36' });
variant('B 推荐：深琥珀棕字 #4A2E00', '从琥珀同色相取最深一档做字（Material 3 的「主题色上的文字」就是这样取的），对比度 6.5:1。读起来是深棕而不是黑，和按钮底色是一个色系，观感更柔和。', true, { bg: '#FDA92D', fg: '#4A2E00' });
variant('C 白字 #FFFFFF', '参考站琥珀主题的做法。对比度只有 1.9:1，远低于大字 3:1 的下限，白天在亮屏上「新建」两个字会发虚，深色模式下也一样。', false, { bg: '#FDA92D', fg: '#FFFFFF' });
variant('D 白字 + 按钮底加深到 #D97706', '白字对比度到 3.2:1（仍不到正文 4.5:1）；代价是主按钮不再是琥珀色而是偏棕的橙，和侧栏、选中态、图表里的主题色对不上。', false, { bg: '#D97706', fg: '#FFFFFF' });
