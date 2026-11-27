// 深色模式表面对比图（R16）：现状近黑 / 参考模板石板灰 / 中性亮一档。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const sp = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1 });
const mixc = (hex, bg, al) => '#' + [1, 3, 5].map((k) => Math.round(parseInt(hex.slice(k, k + 2), 16) * al + parseInt(bg.slice(k, k + 2), 16) * (1 - al)).toString(16).padStart(2, '0')).join('');
const W = 1180;
const AMBER = '#FDA92D';

const root = I(null, { type: 'frame', name: '深色表面对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '深色模式表面：现状 / 推荐 / 备选（琥珀主题）', { size: 20, weight: 700, color: '#212B36' });

const variant = (title, note, good, S) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: '#212B36' }); if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: '#3E4750', w: W });
  // 一屏缩略：侧栏 + 主区
  const scr = H(col, 16, { width: W, padding: 16, cornerRadius: 12, fill: fill(S.canvas), alignItems: 'start' });
  const side = V(scr, 4, { width: 190, padding: 12, cornerRadius: 12, fill: fill(S.card), stroke: { thickness: 1, fill: fill(S.line), align: 'inside' } });
  const lg = H(side, 8, { padding: [4, 8, 12, 8] }); const lo = H(lg, 0, { width: 26, height: 26, cornerRadius: 7, justifyContent: 'center', fill: fill(AMBER) }); T(lo, '烛', { weight: 700, color: '#212B36' }); T(lg, '烛龙ERP', { size: 15, weight: 700, color: AMBER });
  [['layout-grid', '工作台', true], ['briefcase', '销售管理'], ['shopping-cart', '采购管理'], ['warehouse', '仓库管理'], ['wallet', '财务管理'], ['package', '商品管理']].forEach(([ic, t, on]) => { const r = H(side, 10, { width: 'fill_container', padding: [8, 10], cornerRadius: 8, fill: fill(on ? S.selSoft : S.card) }); icon(r, ic, 16, on ? AMBER : S.mute); T(r, t, { size: 13, color: on ? AMBER : S.sub, weight: on ? 600 : 400 }); });
  const main = V(scr, 14, { width: 'fill_container' });
  T(main, '晚上好', { size: 20, weight: 700, color: S.ink });
  const kr = H(main, 14, { width: 'fill_container' });
  [['毛利', 'circle-dollar-sign', '#8E33FF', 'CNY 1,013.80'], ['实收回款', 'hand-coins', '#00B8D9', 'CNY 0.00'], ['应收未收', 'receipt', '#1877F2', 'CNY 3,803.80'], ['费用', 'file-text', AMBER, 'CNY 0.00']].forEach(([n, ic, c, v]) => {
    const k = V(kr, 8, { width: 'fill_container', padding: 16, cornerRadius: 10, fill: fill(S.card), stroke: { thickness: 1, fill: fill(S.line), align: 'inside' } });
    const t = H(k, 10); const tl = H(t, 0, { width: 32, height: 32, cornerRadius: 8, justifyContent: 'center', fill: fill(mixc(c, S.card, 0.16)) }); icon(tl, ic, 16, c); T(t, n, { size: 13, color: S.mute });
    T(k, v, { size: 20, weight: 700, color: S.ink });
  });
  const tb = V(main, 0, { width: 'fill_container', padding: [12, 16], cornerRadius: 10, fill: fill(S.card), stroke: { thickness: 1, fill: fill(S.line), align: 'inside' } });
  const bar = H(tb, 8, { width: 'fill_container', padding: [0, 0, 10, 0] }); T(bar, '共 3 条', { color: S.sub }); sp(bar); const btn = H(bar, 0, { height: 30, padding: [0, 14], cornerRadius: 8, fill: fill(AMBER) }); T(btn, '新建销售订单', { weight: 600, color: '#212B36' });
  const hd = H(tb, 0, { width: 'fill_container', height: 36, fill: fill(S.head), cornerRadius: 6 });
  [['订单编号', 170], ['客户', 220], ['订单状态', 130], ['合计', 140], ['收款', 140]].forEach(([t, w]) => { const c = H(hd, 0, { width: w, padding: [0, 10] }); T(c, t, { size: 12, weight: 600, color: S.mute }); });
  [['SO20261118001', 'PRIME CONTROLS LLC', ['待采购', S.mute, S.neutralSoft], 'USD 532.00', ['待到账', '#FFAB00', mixc('#FFAB00', S.card, 0.16)]],
    ['SO20261117004', 'Pacific Controls', ['已下单', '#00B8D9', mixc('#00B8D9', S.card, 0.16)], 'USD 1,280.00', ['部分到账', '#FFAB00', mixc('#FFAB00', S.card, 0.16)]],
    ['SO20261115002', 'Delta Automation', ['已完成', '#22C55E', mixc('#22C55E', S.card, 0.16)], 'USD 860.00', ['已收齐', '#22C55E', mixc('#22C55E', S.card, 0.16)]]].forEach((r) => {
    I(tb, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(S.line) });
    const tr = H(tb, 0, { width: 'fill_container', height: 46 });
    const c1 = H(tr, 0, { width: 170, padding: [0, 10] }); T(c1, r[0], { color: AMBER });
    const c2 = H(tr, 0, { width: 220, padding: [0, 10] }); T(c2, r[1], { color: S.ink, weight: 600 });
    const pl = (w, [t, fg, bg]) => { const c = H(tr, 0, { width: w, padding: [0, 10] }); const p = H(c, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); T(p, t, { size: 12, weight: 600, color: fg }); };
    pl(130, r[2]);
    const c4 = H(tr, 0, { width: 140, padding: [0, 10] }); T(c4, r[3], { color: S.ink, weight: 600 });
    pl(140, r[4]);
  });
  const sw = H(col, 10); [['页面', S.canvas], ['卡片', S.card], ['表头 / 凹格', S.head], ['分隔线', S.line], ['正文', S.ink], ['次要字', S.mute]].forEach(([t, c]) => { const x = H(sw, 6); I(x, { type: 'rectangle', width: 18, height: 18, cornerRadius: 4, fill: fill(c), stroke: { thickness: 1, fill: fill('#C4CDD5'), align: 'inside' } }); T(x, `${t} ${c}`, { size: 12, color: '#637381' }); });
};

variant('A 现状：中性近黑', '页面 #0A0A0A、卡片 #1A1A1A，几乎是纯黑；黑和深灰之间层次很小，大面积纯黑显得沉、压抑。', false,
  { canvas: '#0A0A0A', card: '#1A1A1A', head: '#222222', line: '#2A2A2A', ink: '#F5F5F5', sub: '#E0E0E0', mute: '#A0A0A0', neutralSoft: '#2F2F2F', selSoft: '#3E311D' });
variant('B 推荐：照参考模板的石板灰（带一点蓝）', '照参考模板（Minimal 开源版配色源码）的深色：页面 #141A21、卡片 #1C252E，表头和凹格 #212B36，次要字 #919EAB。比现状亮一档，带一点冷蓝灰，不再是死黑；各种主题色、状态色在上面都更鲜亮。正文与次要字对卡片的对比度分别约 15:1、5.6:1。', true,
  { canvas: '#141A21', card: '#1C252E', head: '#212B36', line: '#2D3843', ink: '#FFFFFF', sub: '#DFE3E8', mute: '#919EAB', neutralSoft: '#28323D', selSoft: '#3A3324' });
variant('C 备选：中性灰亮一档（不带色相）', '参照苹果系统深色的分层灰：页面 #1C1C1E、卡片 #2C2C2E，表头 #323236。更亮、纯中性，不带冷色；但卡片偏亮时，琥珀主题和状态色的浅底不如 B 分明。', false,
  { canvas: '#1C1C1E', card: '#2C2C2E', head: '#333336', line: '#3A3A3C', ink: '#F5F5F7', sub: '#E5E5EA', mute: '#A1A1A6', neutralSoft: '#3A3A3C', selSoft: '#4A3F2C' });
