// 工作台数字卡被拉高后底部留白对比（R21）。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const mixc = (hex, al) => '#' + [1, 3, 5].map((k) => Math.round(parseInt(hex.slice(k, k + 2), 16) * al + 255 * (1 - al)).toString(16).padStart(2, '0')).join('');
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381';
const W = 1180, CARD_H = 140;

const root = I(null, { type: 'frame', name: '数字卡留白对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '工作台数字卡：卡片被拉高到和左侧图表卡等高时，内容怎么排', { size: 20, weight: 700, color: INK });

const CARDS = [
  ['毛利', 'circle-dollar-sign', '#8E33FF', 'CNY', '1,013.80', ['+12.4%', '#22C55E']],
  ['毛利率', 'percent', '#8E33FF', '', '26.65%', null, '上月无成交，没有可比数据'],
  ['实收回款', 'hand-coins', '#00B8D9', 'CNY', '0.00', null, '上月 CNY 0.00'],
  ['费用', 'file-text', '#F0468E', 'CNY', '0.00', ['-8.0%', '#FF5630']],
];

const card = (p, c, mode) => {
  const [n, ic, col, cur, v, delta] = c;
  const k = V(p, mode === 'top' ? 8 : 0, { width: 'fill_container', height: CARD_H, padding: 16, cornerRadius: 12, fill: fill('#FFFFFF'), effects: SH, justifyContent: mode === 'between' ? 'space_between' : mode === 'center' ? 'center' : 'start' });
  const head = H(k, 10);
  const tl = H(head, 0, { width: 36, height: 36, cornerRadius: 8, justifyContent: 'center', fill: fill(mixc(col, 0.16)) }); icon(tl, ic, 18, col);
  T(head, n, { size: 14, color: MUTE });
  const body = V(k, 4, mode === 'center' ? { padding: [8, 0, 0, 0] } : {});
  const val = H(body, 4, { alignItems: 'end' });
  if (cur) T(val, cur, { size: 13, color: MUTE });
  T(val, v, { size: 24, weight: 700, color: INK });
  if (delta) { const d = H(body, 4, { height: 18 }); T(d, delta[0], { size: 12, weight: 600, color: delta[1] }); T(d, '较上月', { size: 12, color: MUTE }); }
  else if (mode === 'between') { const d = H(body, 4, { height: 18 }); T(d, c[6], { size: 12, color: MUTE }); }
};

const variant = (title, note, good, mode) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = H(col, 16, { width: W, padding: 20, cornerRadius: 12, fill: fill('#F4F6F8') });
  CARDS.forEach((c) => card(pg, c, mode));
};

variant('A 现状：内容贴顶', '图标、名称、数值从上往下排，没有「较上月」的卡（毛利率、实收回款）第三行空着，卡片被拉高后多出来的高度全落在底部，看起来头重脚轻。', false, 'top');
variant('B 推荐：第三行总有内容，数值贴底', '能算出变化时写「+12.4% 较上月」；上月为 0 算不出百分比时改写上月的数（「上月 CNY 0.00」），毛利率上月没有成交时写「上月无成交，没有可比数据」。每张卡都是名称、数值、对比三行，数值和对比贴底、四张卡对齐，被拉高多出的高度留在名称和数值之间。参考 Stripe、Linear 的指标卡。需要后端在数字卡里多传上月的数。', true, 'between');
variant('C 备选：整体垂直居中', '图标、名称、数值作为一组在卡片里居中，上下留白相等；但有较上月和没有较上月的卡，数值高低不一样，并排看不齐。', false, 'center');
