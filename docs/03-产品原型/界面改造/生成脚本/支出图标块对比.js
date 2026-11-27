// 工作台「支出」类数字卡图标块对比（R20）：跟主题色会和其他家族撞色。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const mixc = (hex, al) => '#' + [1, 3, 5].map((k) => Math.round(parseInt(hex.slice(k, k + 2), 16) * al + 255 * (1 - al)).toString(16).padStart(2, '0')).join('');
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381';
const W = 1180;
const THEMES = [['琥珀主题', '#FDA92D'], ['紫主题', '#7635DC'], ['宝蓝主题', '#2065D1']];

const root = I(null, { type: 'frame', name: '支出图标块对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '工作台数字卡：「费用」「应付」等支出类图标块用什么颜色', { size: 20, weight: 700, color: INK });

const variant = (title, note, good, expense) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = V(col, 12, { width: W, padding: 20, cornerRadius: 12, fill: fill('#F4F6F8') });
  for (const [tn, primary] of THEMES) {
    const row = H(pg, 14, { width: 'fill_container' });
    T(row, tn, { size: 12, color: MUTE, w: 64 });
    const [ec, es] = expense(primary);
    [['毛利', 'circle-dollar-sign', '#8E33FF', mixc('#8E33FF', 0.16), 'CNY 1,013.80'],
      ['实收回款', 'hand-coins', '#00B8D9', mixc('#00B8D9', 0.16), 'CNY 0.00'],
      ['应收未收', 'receipt', '#1877F2', mixc('#1877F2', 0.16), 'CNY 3,803.80'],
      ['费用', 'file-text', ec, es, 'CNY 2,150.00']].forEach(([n, ic, c, s, v]) => {
      const k = V(row, 8, { width: 'fill_container', padding: 14, cornerRadius: 10, fill: fill('#FFFFFF'), effects: SH });
      const t = H(k, 10); const tl = H(t, 0, { width: 34, height: 34, cornerRadius: 8, justifyContent: 'center', fill: fill(s) }); icon(tl, ic, 17, c); T(t, n, { size: 13, color: MUTE });
      T(k, v, { size: 18, weight: 700, color: INK });
    });
  }
};

variant('A 现状：支出类跟主题色', '琥珀主题下好看；但紫主题下「费用」和「毛利」同为紫色，宝蓝主题下和「应收未收」同为蓝色，天蓝、青绿主题同样撞色。6 种主题色里 4 种撞色，读不出哪张卡属于哪一类。', false, (p) => [p, mixc(p, 0.16)]);
variant('B 推荐：支出类用独立的洋红 #F0468E', '数字卡本来就是「区分多类」的类别图标块，可以多色。洋红不在任何主题色和其他家族色附近（与红主题相差 30° 以上），经调色板校验与蓝、紫、青都能区分；鲜亮，不会像禁用。所有主题下颜色固定。', true, () => ['#F0468E', mixc('#F0468E', 0.16)]);
variant('C 备选：中性但加深（石板灰图标）', '不撞色，含义上「支出是中性的」；图标用深灰 #454F5B，比原来的浅灰不那么像禁用，但仍是全场唯一没有颜色的卡，和旁边三张鲜亮的卡放在一起显得暗。', false, () => ['#454F5B', '#DFE3E8']);
