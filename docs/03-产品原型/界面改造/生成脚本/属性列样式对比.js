// 列表单元格里属性的显示方式（R34）：应付列表「付款方式」列对公 / 对私是灰标签、未定是文字，一列两种样式；
// 灰标签又和旁边「状态」列的「未付」一模一样，分不出属性和状态。全站列表约 40 处属性用了灰标签。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const INK = '#212B36', SUB = '#454F5B', MUTE = '#919EAB', LINE = '#EDEFF2', HEAD = '#F6F7F8';
const PILL = { gray: ['#EDEFF2', '#454F5B'], info: ['#D6F4F9', '#006C9C'], green: ['#DCF6E5', '#118D57'] };
const W = 1000;

const pill = (p, text, tone) => { const b = H(p, 0, { height: 24, padding: [0, 8], cornerRadius: 6, fill: fill(PILL[tone][0]) }); T(b, text, { size: 12, weight: 600, color: PILL[tone][1] }); };

const ROWS = [
  ['AP20261118001', '上海倍讯自动化', '对私', 'CNY 2,640.00', ['未付', 'gray']],
  ['AP20261118002', '深圳恒拓电气', '对私', 'CNY 960.00', ['未付', 'gray']],
  ['AP20261119001', '苏州工控之家', '未定', 'CNY 1,280.00', ['未付', 'gray']],
  ['AP20261120001', '杭州联控贸易', '未定', 'CNY 3,150.00', ['部分付款', 'info']],
  ['AP20261121001', '宁波科达物流', '对公', 'CNY 420.00', ['已付清', 'green']],
];

const root = I(null, { type: 'frame', name: 'R34 属性列样式对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '列表单元格里的属性怎么显示（R34）', { size: 20, weight: 700, color: INK });
T(root, '现状：应付列表「付款方式」对公 / 对私是灰标签、未定是文字，一列两种样式；而且「对私」和「状态」列的「未付」都是灰标签，看不出哪个是属性、哪个是状态。规范两条打架：161 条「属性一律中性标签」、282 条「单元格里属性写成次要色文字，不用标签」。全站列表约 40 处属性用了灰标签（代收、员工垫付、兼职、客户指定、上门取件、核心、平台、已停用……）。', { size: 13, color: SUB, w: W });

const table = (p, method) => {
  const t = V(p, 0, { width: 640, cornerRadius: 8, clipContent: true, fill: fill('#FFFFFF') });
  const cols = [['应付单号', 150], ['收款方', 150], ['付款方式', 100], ['金额', 120], ['状态', 120]];
  const head = H(t, 0, { width: 'fill_container', height: 40, fill: fill(HEAD), padding: [0, 12] });
  cols.forEach(([h, w]) => { const c = H(head, 0, { width: w }); T(c, h, { size: 13, color: '#637381' }); });
  ROWS.forEach((r) => {
    I(t, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(LINE) });
    const row = H(t, 0, { width: 'fill_container', height: 52, padding: [0, 12] });
    const c0 = H(row, 0, { width: 150 }); T(c0, r[0], { size: 14, weight: 600, color: INK });
    const c1 = H(row, 0, { width: 150 }); T(c1, r[1], { size: 14, color: INK });
    const c2 = H(row, 0, { width: 100 }); method(c2, r[2]);
    const c3 = H(row, 0, { width: 120 }); T(c3, r[3], { size: 14, color: INK });
    const c4 = H(row, 0, { width: 120 }); pill(c4, r[4][0], r[4][1]);
  });
};

const variant = (title, note, good, method) => {
  const col = H(root, 32, { width: W, alignItems: 'start' });
  const left = V(col, 8, { width: 320 });
  const h = H(left, 8); T(h, title, { size: 16, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(left, note, { size: 13, color: SUB, w: 320 });
  const pg = V(col, 0, { padding: 16, cornerRadius: 12, fill: fill('#F4F6F8') });
  table(pg, method);
};

variant('A 现状', '对公 / 对私灰标签，未定是 14 号浅色文字：一列两种样式；「对私」和「未付」长得一样。', false,
  (c, v) => (v === '未定' ? T(c, v, { size: 14, color: MUTE }) : pill(c, v, 'gray')));
variant('B 未定也做成灰标签', '这一列统一了，但属性和状态还是同一种灰标签，扫一眼分不出「对私」「未付」哪个是状态。改动只这一处，其他 40 处照旧。', false,
  (c, v) => pill(c, v, 'gray'));
variant('C 属性写成文字，标签只给状态', '对公 / 对私写成 14 号正文，未定用浅色；状态列保留标签。一眼分清属性和状态，表格也更干净。按规范 282 条把全站列表约 40 处属性灰标签改成文字（详情页、卡片、弹窗里的标签不动），161 条与 lint attr-tone 改为「属性在详情页可用中性标签，列表单元格写文字」。参照 Polaris、Atlassian、Stripe 的数据表：徽标只给状态，属性是文字。', true,
  (c, v) => T(c, v, { size: 14, color: v === '未定' ? MUTE : INK }));
