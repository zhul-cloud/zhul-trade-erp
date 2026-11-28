// 覆盖矩阵的格子（R38，UI-118）：品牌 × 品类，每格是这组需求的覆盖情况（可直接报价 / 需确认 / 有线索未验证 / 无渠道）。
// 现状整格铺「最差一档」的颜色、无渠道格再加描边，满屏色块；「需确认」用的棕色不在状态色里；「0/1/0/0」看不懂。
// 单独用 op design 生成；颜色照本机状态色（成功绿 #118D57、警告橙 #B76E00、信息蓝 #006C9C、危险红 #B71D18），数据为示意。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const INK = '#212B36', SUB = '#454F5B', MUTE = '#919EAB', LINE = '#EDEFF2', CANVAS = '#F4F6F8', CARD = '#FFFFFF', INSET = '#F7F8FA';
const SHADOW = [{ type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
// 现状用色（含不在状态色里的棕）与改后的状态色
const OLD = ['#22C55E', '#B7791F', '#00B8D9', '#FF5630'];
const STATUS = ['#118D57', '#B76E00', '#006C9C', '#B71D18'];
const STATUS_SOFT = ['#DCF6E5', '#FFF1D6', '#D6F4F9', '#FFE4DE'];
const NAMES = ['可直接报价', '需确认', '有线索未验证', '无渠道'];

const BRANDS = ['Schneider Electric', 'OMRON', 'Siemens'];
const CATS = ['接触器', '传感器', 'PLC 与控制器'];
// 每格 [可直接报价, 需确认, 有线索, 无渠道, 专项中]
const CELLS = [
  [[4, 1, 0, 0], [0, 1, 0, 1], null],
  [[2, 0, 1, 0, true], [3, 0, 0, 0], [0, 0, 1, 2]],
  [null, [1, 2, 0, 0], [5, 1, 1, 0]],
];
const worst = (c) => (c[3] ? 3 : c[2] ? 2 : c[1] ? 1 : 0);
const total = (c) => c[0] + c[1] + c[2] + c[3];

const bar = (p, c, colors, w = 150) => {
  const b = H(p, 2, { width: w, height: 6 });
  const t = total(c);
  c.slice(0, 4).forEach((n, k) => { if (n) I(b, { type: 'frame', width: Math.max(6, Math.round((w - 6) * n / t)), height: 6, cornerRadius: 3, fill: fill(colors[k]) }); });
};

const matrix = (p, cellFn, legendColors) => {
  const card = V(p, 0, { width: 860, padding: [8, 16, 12, 16], cornerRadius: 10, fill: fill(CARD), effects: SHADOW });
  const lg = H(card, 14, { padding: [6, 0, 10, 0] });
  NAMES.forEach((n, k) => { const it = H(lg, 6); I(it, { type: 'ellipse', width: 8, height: 8, fill: fill(legendColors[k]) }); T(it, n, { size: 12, color: SUB }); });
  const head = H(card, 0, { height: 40 });
  T(H(head, 0, { width: 170 }), '品牌', { size: 13, color: MUTE });
  CATS.forEach((c) => T(H(head, 0, { width: 220, padding: [0, 8] }), c, { size: 13, color: MUTE }));
  BRANDS.forEach((b, i) => {
    const row = H(card, 0, { height: 96, stroke: { thickness: 1, align: 'inside', fill: fill(LINE) } });
    const bc = V(row, 2, { width: 170, padding: [0, 0] }); T(bc, b, { size: 14, weight: 600, color: INK }); T(bc, `${CELLS[i].filter(Boolean).reduce((s, c) => s + total(c), 0)} 个需求`, { size: 12, color: MUTE });
    CELLS[i].forEach((c) => { const box = H(row, 0, { width: 220, padding: [0, 8], height: 96 }); if (!c) T(box, '—', { size: 14, color: MUTE }); else cellFn(box, c); });
  });
};

const variant = (root, title, note, good, cellFn, legend) => {
  const col = V(root, 10, { width: 892 });
  const h = H(col, 8); T(h, title, { size: 16, weight: 600, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: 892 });
  const bg = V(col, 0, { padding: 16, cornerRadius: 12, fill: fill(CANVAS) });
  matrix(bg, cellFn, legend);
};

const root = I(null, { type: 'frame', name: 'R38 覆盖矩阵格子对比', layout: 'vertical', gap: 40, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '覆盖矩阵的格子怎么画（R38）', { size: 20, weight: 600, color: INK });
T(root, '覆盖矩阵是「品牌 × 品类」的热力图：每格是这组型号需求的渠道覆盖情况，点格子看型号。现状整格铺最差一档的颜色，无渠道格再加一圈描边，满屏色块像卡里套卡；「需确认」用的棕色不在状态色里；「0/1/0/0」要看说明才懂。数据为示意。', { size: 13, color: SUB, w: 892 });

variant(root, 'A 现状：整格铺色', '扫一眼能看出哪里差；但色块面积大、四种颜色叠在一起发脏（深色模式更明显），棕色不在状态色里，描边格像卡里套卡。', false, (box, c) => {
  const w = worst(c);
  const cell = V(box, 6, { width: 200, padding: [10, 14], cornerRadius: 10, fill: fill(OLD[w] + '22'), ...(w === 3 ? { stroke: { thickness: 1, align: 'inside', fill: fill(OLD[3] + '88') } } : {}) });
  const n = H(cell, 8); T(n, String(total(c)), { size: 20, weight: 600, color: INK }); T(n, c.slice(0, 4).join('/'), { size: 12, color: SUB });
  bar(cell, c, OLD, 140);
  if (c[4]) T(cell, '专项中', { size: 12, color: '#006C9C' });
}, OLD);

variant(root, 'B 白底格子 + 状态色堆叠条 + 缺口点名（推荐）', '格子不铺底色，只在悬停时浅灰；数字后写「共 N 个」，下面一条四段堆叠条（颜色改用状态色：可报价绿、需确认橙、有线索信息蓝、无渠道红）；有无渠道的格子在下方用红字写「无渠道 N」，需要处理的一眼能找到。色块面积降到一条 6px 的条，深浅模式都干净。', true, (box, c) => {
  const cell = V(box, 6, { width: 200, padding: [10, 14], cornerRadius: 8 });
  const n = H(cell, 6); T(n, String(total(c)), { size: 20, weight: 600, color: INK }); T(n, '个需求', { size: 12, color: MUTE });
  bar(cell, c, STATUS, 160);
  const s = H(cell, 8);
  if (c[3]) T(s, `无渠道 ${c[3]}`, { size: 12, weight: 600, color: STATUS[3] }); else if (c[1]) T(s, `需确认 ${c[1]}`, { size: 12, color: STATUS[1] }); else T(s, `可报价 ${c[0]}`, { size: 12, color: MUTE });
  if (c[4]) T(s, '· 专项中', { size: 12, color: STATUS[2] });
}, STATUS);

variant(root, 'C 浅色底只给有缺口的格子', '没有缺口的格子白底；有无渠道的格子铺很浅的红底（状态色 12%，不描边），需确认的铺浅橙；条同 B。色块只出现在要处理的地方，热力感更强，但仍有大块底色，深色模式要单独核对。', false, (box, c) => {
  const w = worst(c);
  const tint = w === 3 ? STATUS_SOFT[3] : w === 1 ? STATUS_SOFT[1] : CARD;
  const cell = V(box, 6, { width: 200, padding: [10, 14], cornerRadius: 8, fill: fill(tint) });
  const n = H(cell, 6); T(n, String(total(c)), { size: 20, weight: 600, color: INK }); T(n, '个需求', { size: 12, color: MUTE });
  bar(cell, c, STATUS, 160);
  const s = H(cell, 8);
  if (c[3]) T(s, `无渠道 ${c[3]}`, { size: 12, weight: 600, color: STATUS[3] }); else if (c[1]) T(s, `需确认 ${c[1]}`, { size: 12, color: STATUS[1] }); else T(s, `可报价 ${c[0]}`, { size: 12, color: MUTE });
  if (c[4]) T(s, '· 专项中', { size: 12, color: STATUS[2] });
}, STATUS);
