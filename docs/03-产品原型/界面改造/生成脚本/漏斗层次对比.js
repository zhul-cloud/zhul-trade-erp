// 卡里套卡对比图的公共写法见「卡里套卡对比.js」；本文件为销售统计漏斗层次对比图（R9）。单独用 op design 生成。
const C = {
  ink: '#212B36', sub: '#3E4750', mute: '#637381', faint: '#919EAB', line: '#E5E8EB', head: '#F9FAFB', inset: '#F7F8FA',
  card: '#FFFFFF', canvas: '#F4F6F8', primary: '#FDA92D', primarySoft: '#FFF5E6', primaryLine: '#FEDDAB', link: '#B76E00',
  orange: '#9A5B00', orangeSoft: '#FFF5E0', green: '#0D7A4A', greenSoft: '#E3F6EA', red: '#B71D18', redSoft: '#FFEBE6',
  warnBg: '#FFF8E1', warnLine: '#FFE7A3', neutralSoft: '#ECEEF0', bar: '#DFE3E8', control: '#D3D5D8',
};
const F = 'Public Sans', MONO = 'JetBrains Mono';
const fill = (c) => [{ type: 'solid', color: c }];
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: o.font || F, fontSize: o.size || 14, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color || C.sub), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const pill = (p, text, fg, bg, ic) => { const b = H(p, 4, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); if (ic) icon(b, ic, 12, fg); T(b, text, { size: 12, weight: 600, color: fg }); return b; };
const card = (p, o = {}) => V(p, o.gap ?? 12, { width: o.width || 'fill_container', padding: o.pad || [20, 24], cornerRadius: 12, fill: fill(C.card), effects: SH, ...(o.height ? { height: o.height } : {}) });
const sp = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1 });
const hr = (p) => I(p, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) });
const bar = (p, w, total, color) => { const b = H(p, 0, { width: total, height: 6, cornerRadius: 3, fill: fill(C.neutralSoft) }); if (w > 0) I(b, { type: 'rectangle', width: w, height: 6, cornerRadius: 3, fill: fill(color) }); };

const PW = 620;
let SHB;
const panel = (row, title, note, good) => {
  const col = V(row, 10, { width: PW });
  const h = H(col, 8); T(h, title, { size: 15, weight: 700, color: C.ink }); if (good) pill(h, '推荐', C.green, C.greenSoft);
  if (note) T(col, note, { size: 12, color: C.mute, w: PW });
  return V(col, 16, { width: PW, padding: 20, cornerRadius: 10, fill: fill(C.canvas) });
};
const section = (root, title, why) => {
  const s = V(root, 14);
  T(s, title, { size: 18, weight: 700, color: C.ink });
  T(s, why, { size: 13, color: C.sub, w: PW * 2 + 40 });
  return H(s, 40, { alignItems: 'start' });
};


// 销售统计漏斗配色与层次对比图（R9），单独用 op design 生成。
const root = I(null, { type: 'frame', name: '漏斗配色对比', layout: 'vertical', gap: 28, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '销售统计漏斗：怎么让每一步有区分、有立体感（浅色）', { size: 20, weight: 700, color: C.ink });
T(root, '漏斗 6 步是同一个量（单据数）在先后环节的递减，不是 6 个不同类别。颜色应该表达含义：类别用不同色相，数量大小用同一色相的深浅。', { size: 13, color: C.mute, w: 1300 });
const steps = [['商机', 0, ''], ['询盘', 2, '询盘转化 —'], ['报价', 4, ''], ['意向 PI', 2, 'CNY 7,150.00'], ['待付款', 2, 'CNY 7,150.00'], ['成交订单', 1, '订单转化 50%']];
const RAIN = ['#3B82F6', '#14B8A6', '#A855F7', '#F59E0B', '#EF4444', '#22C55E'];
const SEQ = ['#FEE6BF', '#FDD08A', '#FDBB55', '#FDA92D', '#E08A0B', '#B76E00'];
const variant = (title, note, good, opt) => {
  const col = V(root, 10, { width: 1300 });
  const h = H(col, 8); T(h, title, { size: 15, weight: 700, color: C.ink }); if (good) pill(h, '推荐', C.green, C.greenSoft);
  T(col, note, { size: 12, color: C.sub, w: 1300 });
  const pg = V(col, 0, { width: 1300, padding: 20, cornerRadius: 10, fill: fill(C.canvas) });
  const c = card(pg, { gap: 14 });
  T(c, '销售漏斗', { size: 14, weight: 700, color: C.ink });
  const r = H(c, opt.cells ? 12 : 0, { width: 'fill_container', alignItems: 'start' });
  steps.forEach(([n, v, cap], i) => {
    if (i > 0 && !opt.cells) { const a = V(r, 0, { width: 28, padding: [24, 0, 0, 0], alignItems: 'center' }); icon(a, 'chevron-right', 16, C.faint); }
    const s = V(r, 6, { width: 'fill_container', ...(opt.cells ? { padding: [12, 14], cornerRadius: 10, fill: fill(C.inset) } : {}) });
    T(s, n, { size: 13, color: C.mute });
    T(s, String(v), { size: 24, weight: 700, color: C.ink });
    const color = opt.rain ? RAIN[i] : opt.seq ? SEQ[i] : i === steps.length - 1 ? C.primary : C.bar;
    const W = 140, w = Math.max(v > 0 ? 6 : 0, Math.round((v / 4) * W));
    const b = H(s, 0, { width: W, height: 8, cornerRadius: 4, fill: fill(C.neutralSoft) }); if (w > 0) I(b, { type: 'rectangle', width: w, height: 8, cornerRadius: 4, fill: fill(color) });
    T(s, cap || ' ', { size: 12, color: C.mute });
  });
};

// A 现状
{
  const col = V(root, 10, { width: 1300 });
  T(col, 'A 现状', { size: 15, weight: 700, color: C.ink });
  T(col, '6 个一样大小、一样颜色的奶油色块：块和块之间没有区分，数字看不出递减；奶油色在白卡上对比太弱，显得平；而且它是「选中」专用的主题色浅底。', { size: 12, color: C.sub, w: 1300 });
  const pg = V(col, 0, { width: 1300, padding: 20, cornerRadius: 10, fill: fill(C.canvas) });
  const c = card(pg, { gap: 10 });
  T(c, '漏斗（所选期间）', { size: 14, weight: 700, color: C.ink });
  const r = H(c, 10, { width: 'fill_container', alignItems: 'start' });
  for (const [n, v, cap] of steps) { const st = V(r, 4, { width: 'fill_container' }); T(st, n, { size: 12, color: C.sub }); const b = H(st, 0, { width: 'fill_container', height: 40, padding: [0, 14], cornerRadius: 8, fill: fill(C.primarySoft) }); T(b, String(v), { size: 18, weight: 800, color: C.ink }); T(st, cap || ' ', { size: 11, color: C.mute }); }
}
// E 每步独立白卡
{
  const col = V(root, 10, { width: 1300 });
  const h = H(col, 8); T(h, 'E 每步一张独立白卡（新推荐）', { size: 15, weight: 700, color: C.ink }); pill(h, '推荐', C.green, C.greenSoft);
  T(col, '去掉外层大卡，标题「销售漏斗」直接放在页面上，6 步各是一张落在页面上的白卡（白卡 + 阴影压在灰色页面上，这才是立体感的来源），和工作台的指标卡同一写法。每张卡用不同图标区分步骤，数字 24 号，下面按数量成比例的条；转化率沿用现有的「询盘转化」「订单转化」，写在对应卡的条下面。只有终点「成交订单」的图标块和条用主题色，其余中性——区分靠图标、数字和条长，不靠颜色。仍是两层（页面、白卡），不是卡里套卡。', { size: 12, color: C.sub, w: 1300 });
  const pg = V(col, 12, { width: 1300, padding: 20, cornerRadius: 10, fill: fill(C.canvas) });
  const th = H(pg, 8); T(th, '销售漏斗', { size: 16, weight: 700, color: C.ink }); T(th, '本月', { size: 13, color: C.mute });
  const r = H(pg, 0, { width: 'fill_container', alignItems: 'center' });
  const ICON = ['target', 'message-square', 'file-text', 'receipt', 'wallet', 'circle-check'];
  const CONV = ['', '', '', '', '', ''];
  steps.forEach(([n, v, cap], i) => {
    if (i > 0) { const a = V(r, 2, { width: 44, alignItems: 'center' }); icon(a, 'chevron-right', 14, C.faint); T(a, CONV[i] || ' ', { size: 11, color: C.mute }); }
    const last = i === steps.length - 1;
    const c = V(r, 10, { width: 'fill_container', padding: [16, 16], cornerRadius: 12, fill: fill(C.card), effects: SH });
    const top = H(c, 8, { width: 'fill_container' });
    const tile = H(top, 0, { width: 32, height: 32, cornerRadius: 8, justifyContent: 'center', fill: fill(last ? C.primarySoft : C.neutralSoft) }); icon(tile, ICON[i], 16, last ? C.link : C.sub);
    T(top, n, { size: 13, color: C.sub });
    T(c, String(v), { size: 24, weight: 700, color: C.ink });
    const W = 120, w = Math.max(v > 0 ? 6 : 0, Math.round((v / 4) * W));
    const b = H(c, 0, { width: W, height: 6, cornerRadius: 3, fill: fill(C.neutralSoft) }); if (w > 0) I(b, { type: 'rectangle', width: w, height: 6, cornerRadius: 3, fill: fill(last ? C.primary : C.bar) });
    T(c, cap || ' ', { size: 12, color: C.mute });
  });
}
variant('B 一张卡里平铺（上一版的推荐）', '比 A 清楚，但 6 步挤在一张卡里没有底色，你说的「没有立体感」它确实没解决。', false, {});
variant('D 每步一个颜色', '颜色没有含义：读者会以为是 6 个不同类别；绿、红、橙和全站「正常 / 逾期 / 提醒」状态色撞车（红色「待付款」像出了问题）；色盲用户分不清相邻几步。区分有了，但区分错了。不推荐。', false, { rain: true });
