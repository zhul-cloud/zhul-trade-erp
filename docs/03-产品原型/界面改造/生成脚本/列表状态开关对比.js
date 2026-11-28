// 列表里的启用 / 停用开关（R37，UI-118）：客户、供应商等 4 个列表每行一个主题色小开关，一屏 8 个琥珀色，
// 和规范「表格里的主题色只出现在勾选框和选中行」（检查表 #9）冲突；「组件一律跟主题色」又要求开关用主题色。
// 单独用 op design 生成；主按钮、开关颜色照本机琥珀主题（主题色 #FDA92D、主按钮白字，D70）。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const INK = '#212B36', SUB = '#454F5B', MUTE = '#919EAB', HEAD_C = '#637381', LINE = '#EDEFF2', HEAD = '#F9FAFB', CANVAS = '#F4F6F8', CARD = '#FFFFFF';
const PRIMARY = '#FDA92D', NEUTRAL_ON = '#637381', OFF = '#DFE3E8';
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const W = 860;

const ROWS = [
  ['PRIME CONTROLS LLC', 'C10001', '美国', 'B', true],
  ['Nordic Automation AB', 'C10002', '瑞典', 'A', true],
  ['Al Noor Trading LLC', 'C10003', '阿联酋', 'S', true],
  ['TECNO INDUSTRIAL SAC', 'C10004', '秘鲁', '未成交', false],
  ['Kraft Systems GmbH', 'C10005', '德国', 'C', true],
  ['Saigon Electric Co.', 'C10006', '越南', 'B', true],
];
const COLS = [['客户名称', 230], ['客户编码', 110], ['国家/地区', 110], ['经营等级', 110], ['状态', 110], ['操作', 120]];

const sw = (p, on, onColor) => {
  const t = H(p, 0, { width: 28, height: 16, cornerRadius: 8, padding: [0, 2], fill: fill(on ? onColor : OFF), justifyContent: on ? 'end' : 'start' });
  I(t, { type: 'ellipse', width: 12, height: 12, fill: fill('#FFFFFF') });
};
const pill = (p, text, bg, fg) => { const b = H(p, 0, { height: 24, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); T(b, text, { size: 12, weight: 600, color: fg }); };
const grade = (p, g) => g === '未成交' ? pill(p, g, '#ECEEF0', SUB) : pill(p, g, g === 'S' ? PRIMARY : '#FFF1DB', g === 'S' ? '#FFFFFF' : INK);
const ico = (p, n) => I(p, { type: 'icon_font', name: 'c:' + MUTE, iconFontName: n, width: 18, height: 18, fill: fill(MUTE) });
const icons = (p, more) => { const a = H(p, 14); ico(a, 'eye'); ico(a, 'pencil'); if (more) ico(a, 'ellipsis'); };

const table = (p, statusCell, moreMenu) => {
  const card = V(p, 0, { width: W, padding: [16, 16, 8, 16], cornerRadius: 10, fill: fill(CARD), effects: SHADOW, clipContent: false });
  const head = H(card, 0, { width: 'fill_container', height: 48, fill: fill(HEAD), padding: [0, 12] });
  COLS.forEach(([h, w]) => { const c = H(head, 0, { width: w }); T(c, h, { size: 13, color: HEAD_C }); });
  ROWS.forEach((r, i) => {
    const row = H(card, 0, { width: 'fill_container', height: 56, padding: [0, 12], stroke: { thickness: 1, align: 'inside', fill: fill(LINE) } });
    const a = H(row, 0, { width: 230 }); T(a, r[0], { size: 14, weight: 600, color: INK });
    const b = H(row, 0, { width: 110 }); T(b, r[1], { size: 14, color: SUB });
    const c = H(row, 0, { width: 110 }); T(c, r[2], { size: 14, color: INK });
    const d = H(row, 0, { width: 110 }); grade(d, r[3]);
    const e = H(row, 0, { width: 110 }); statusCell(e, r[4], i);
    const f = H(row, 0, { width: 120 }); icons(f, true);
  });
};

const variant = (root, title, note, good, statusCell, moreMenu) => {
  const col = V(root, 10, { width: W + 230 });
  const h = H(col, 8); T(h, title, { size: 16, weight: 600, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: W + 230 });
  const bg = H(col, 16, { padding: 16, cornerRadius: 12, fill: fill(CANVAS), alignItems: 'start' });
  table(bg, statusCell, moreMenu);
  if (moreMenu) {
    const side = V(bg, 6, { width: 170 });
    T(side, '点行尾「···」：', { size: 12, color: SUB });
    const m = V(side, 2, { width: 160, padding: 6, cornerRadius: 8, fill: fill(CARD), effects: [{ type: 'shadow', offsetX: 0, offsetY: 8, blur: 16, spread: -4, color: 'rgba(33,43,54,0.2)' }] });
    [['转移负责人', INK], ['禁用', INK], ['删除', '#B71D18']].forEach(([t, c]) => { const it = H(m, 0, { width: 'fill_container', height: 32, padding: [0, 10], cornerRadius: 6, fill: fill(t === '禁用' ? '#F4F6F8' : CARD) }); T(it, t, { size: 14, color: c }); });
  }
};

const root = I(null, { type: 'frame', name: 'R37 列表状态开关对比', layout: 'vertical', gap: 40, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '列表里的启用 / 禁用怎么放（R37）', { size: 20, weight: 600, color: INK });
T(root, '客户、供应商等 4 个列表每行一个主题色小开关（StatusSwitch），一屏 8 个琥珀色，表格里主题色面积最大的就是它；规范两条打架：检查表 #9「表格里主题色只给勾选框和选中行」与「组件一律跟主题色（含开关）」。8 个付费模板的列表都是状态标签，没有一家在每行放开关。数据为示意。', { size: 13, color: SUB, w: W + 230 });

variant(root, 'A 现状：主题色开关', '一眼能改状态；但满屏琥珀，比状态、金额还抢眼，误点就停用了客户（有确认框兜底）。', false,
  (c, on) => sw(c, on, PRIMARY));
variant(root, 'B 开关改中性色', '开关保留在列里，打开时用次要灰（#637381），悬停才变主题色。主题色面积降下来，操作习惯不变；但开关仍是每行最显眼的控件，「开着 = 正常」的信息量很低。需在规范里给开关开例外：表格里的开关用中性色。', false,
  (c, on) => sw(c, on, NEUTRAL_ON));
variant(root, 'C 状态写成标签，启用 / 禁用收进「更多」（推荐）', '状态列显示「启用」（绿）/「禁用」（灰），和其他列表的状态标签一致；启用、禁用放进行尾「···」菜单（带确认框，同删除）。表格里不再有主题色控件，误点风险也没了；代价是改状态多点一下。改公共 StatusSwitch 所在的 4 个列表，规范的状态开关一节改为「列表行里不放开关」。', true,
  (c, on) => (on ? pill(c, '启用', '#DCF6E5', '#118D57') : pill(c, '禁用', '#ECEEF0', SUB)), true);
