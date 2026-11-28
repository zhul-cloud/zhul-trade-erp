// 跟进板块配色对比图（R11）：现状全中性 / 图标块按板块着色 / 再加顶部色条。单独用 op design 生成。
const C = {
  ink: '#212B36', sub: '#3E4750', mute: '#637381', faint: '#919EAB', line: '#E5E8EB', head: '#F9FAFB', inset: '#F7F8FA',
  card: '#FFFFFF', canvas: '#F4F6F8', primary: '#FDA92D', primarySoft: '#FFF5E6', link: '#B76E00', track: '#E9ECEF',
  orange: '#9A5B00', orangeSoft: '#FFF5E0', green: '#0D7A4A', greenSoft: '#E3F6EA', red: '#B71D18', redSoft: '#FFEBE6', redMain: '#C4302B',
  info: '#006C9C', infoSoft: '#E0F6FA', neutralSoft: '#ECEEF0', control: '#D3D5D8', bar: '#C4CDD5',
};
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color || C.sub), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const pill = (p, text, fg, bg, ic) => { const b = H(p, 4, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); if (ic) icon(b, ic, 12, fg); T(b, text, { size: 12, weight: 600, color: fg }); return b; };
const sp = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1 });
const hr = (p) => I(p, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) });
const btn = (p, text, o = {}) => { const b = H(p, 4, { height: 30, padding: [0, 12], cornerRadius: 6, fill: fill(o.solid ? C.primary : C.card), stroke: o.solid ? undefined : { thickness: 1, fill: fill(C.control), align: 'inside' } }); if (o.ic) icon(b, o.ic, 13, o.solid ? C.ink : C.sub); T(b, text, { size: 13, color: C.ink }); if (o.caret) icon(b, 'chevron-down', 12, C.mute); return b; };
const box = (p) => I(p, { type: 'rectangle', width: 16, height: 16, cornerRadius: 4, fill: fill(C.card), stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } });
const W = 1180;


const BOARDS = [
  ['主动开发', 'compass', '0', '找客户 0 · 找人触达 0 · 挖需求 0', 0],
  ['商机成交', 'id-card', '2', '报价前 0 · 报价后 1 · PI 后 1', 0],
  ['订单履约', 'inbox', '1', '采购中 1 · 验收发货 0 · 在途 0', 1],
  ['售后评价', 'message-square', '0', '签收 0 · 使用 0 · 问题 0', 0],
  ['客户经营', 'trending-up', '0', '需求挖掘 0 · 复购机会 0', 0],
];
// 图表系列色（CHART_SERIES 前 4 个 + 第 5 个绿），浅底为 10% 混白
const HUE = [['#3B82F6', '#EBF3FE'], ['#2F9E99', '#EAF5F5'], ['#B87A14', '#F8F2E8'], ['#E8664A', '#FDF0ED'], ['#2E9A62', '#EAF5EF']];

const root = I(null, { type: 'frame', name: '跟进板块配色对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '跟进板块配色：现状 / 推荐 / 备选（浅色，选中「商机成交」）', { size: 20, weight: 700, color: C.ink });
const variant = (title, note, good) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: C.ink }); if (good) pill(h, '推荐', C.green, C.greenSoft);
  T(col, note, { size: 13, color: C.sub, w: W });
  return V(col, 12, { width: W, padding: 24, cornerRadius: 12, fill: fill(C.canvas) });
};
const strip = (pg, mode) => {
  const th = H(pg, 8, { width: 'fill_container' }); T(th, '跟进板块', { size: 16, weight: 600, color: C.ink }); sp(th); icon(th, 'repeat', 13, C.mute); T(th, '复购回到第 2 步', { size: 12, color: C.mute });
  const row = H(pg, 0, { width: 'fill_container', alignItems: 'center' });
  const card = (p, sel, ic, name, step, v, groups, od, hue) => {
    const c = V(p, 6, { width: 'fill_container', height: 112, padding: 12, cornerRadius: 10, fill: fill(C.card), effects: SH, clipContent: true, stroke: sel ? { thickness: 2, fill: fill(C.primary), align: 'inside' } : undefined });
    if (mode === 'bar' && hue) I(c, { type: 'rectangle', width: 'fill_container', height: 3, cornerRadius: 2, fill: fill(hue[0]) });
    const colored = mode !== 'flat' && hue;
    const t = H(c, 6, { width: 'fill_container' });
    const tl = H(t, 0, { width: 24, height: 24, cornerRadius: 6, justifyContent: 'center', fill: fill(colored ? hue[1] : sel ? C.primarySoft : C.neutralSoft) });
    icon(tl, ic, 13, colored ? hue[0] : sel ? C.link : C.sub);
    T(t, name, { size: 14, weight: 600, color: C.ink }); sp(t); if (step) T(t, step, { size: 12, color: C.mute });
    const nn = H(c, 4, { alignItems: 'end' }); T(nn, v, { size: 24, weight: 700, color: C.ink }); T(nn, '位客户', { size: 12, color: C.mute }); if (od) { sp(nn); pill(nn, `逾期 ${od}`, C.red, C.redSoft); }
    T(c, groups.length > 16 ? groups.slice(0, 15) + '…' : groups, { size: 12, color: C.mute });
  };
  card(row, false, 'calendar-check', '今日待办', '', '2', '逾期 1 · 今天 1', 0, null);
  I(row, { type: 'frame', width: 20, height: 1 });
  BOARDS.forEach(([n, ic, v, g, od], i) => {
    if (i > 0) { const a = V(row, 0, { width: 20, alignItems: 'center' }); icon(a, 'chevron-right', 13, C.faint); }
    card(row, i === 1, ic, n, `第 ${i + 1} 步`, v, g, od, HUE[i]);
  });
  // 列表里的一行：阶段标签保持中性
  const list = V(pg, 6, { width: 'fill_container', padding: [14, 20], cornerRadius: 10, fill: fill(C.card), effects: SH });
  const l1 = H(list, 8); pill(l1, '商机成交 · S7 成交推进', C.sub, C.neutralSoft); T(l1, '系统提醒：PI 发出第 6 天，确认付款进度', { size: 13, color: C.ink });
};

{
  const pg = variant('A 现状：全部中性', '卡片、图标块全是灰色，只有当前卡的主题色描边。六张卡长得一样，像未加载或禁用；全站其他数字卡（工作台）的图标块都有颜色，这一组成了唯一没有颜色的。', false);
  strip(pg, 'flat');
}
{
  const pg = variant('B 推荐：图标块按板块着色', '五个板块是不同的工作阶段（类别），给每个板块一个固定颜色，只用在 24px 图标块上（浅底 + 板块色图标），和工作台数字卡按指标家族给图标块着色是同一写法。今日待办图标块中性；当前卡仍用主题色描边；数字、文字、列表里的阶段标签都不变色。颜色只占卡面很小一块，既能一眼区分板块，又不花。', true);
  strip(pg, 'tile');
}
{
  const pg = variant('C 备选：图标块着色 + 卡片顶部色条', '在 B 的基础上，每张板块卡顶部加一条 3px 板块色。更醒目，远看也能分辨；但六张卡顶部一排彩条，与当前卡的主题色描边抢注意力，整页颜色偏多。', false);
  strip(pg, 'bar');
}
