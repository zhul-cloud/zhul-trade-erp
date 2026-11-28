// 配色系统对比图（R14）：图形用色偏暗的问题，对照参考模板（Minimal / PrimeDash）。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', CANVAS = '#F4F6F8', PRIMARY = '#FDA92D', LINK = '#B76E00', PSOFT = '#FFF5E6';
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color || SUB), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const mixc = (hex, al) => '#' + [1, 3, 5].map((k) => Math.round(parseInt(hex.slice(k, k + 2), 16) * al + 255 * (1 - al)).toString(16).padStart(2, '0')).join('');
const card = (p, o = {}) => V(p, o.gap ?? 8, { padding: o.pad ?? 16, cornerRadius: 10, fill: fill('#FFFFFF'), effects: SH, width: o.w ?? 'fill_container' });
const W = 1180;

const root = I(null, { type: 'frame', name: '配色系统对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '配色系统：现状 / 推荐 / 备选（浅色，琥珀主题）', { size: 20, weight: 700, color: INK });

const variant = (title, note, good, S) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK }); if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#E5F8EC') }); T(b, '推荐', { size: 12, weight: 600, color: '#078039' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = V(col, 16, { width: W, padding: 24, cornerRadius: 12, fill: fill(CANVAS) });

  // 1 工作台数字卡：图标块按色族
  const r1 = H(pg, 16, { width: 'fill_container' });
  [['毛利', 'circle-dollar-sign', S.fam.purple, 'CNY 1,013.80'], ['毛利率', 'percent', S.fam.purple, '26.65%'], ['实收回款', 'hand-coins', S.fam.cyan, 'CNY 0.00'], ['应收未收', 'receipt', S.fam.blue, 'CNY 3,803.80']].forEach(([n, ic, c, v]) => {
    const k = card(r1, { gap: 10 });
    const t = H(k, 10); const tl = H(t, 0, { width: 36, height: 36, cornerRadius: 8, justifyContent: 'center', fill: fill(mixc(c, S.tint)) }); icon(tl, ic, 18, c); T(t, n, { size: 14, color: MUTE });
    T(k, v, { size: 22, weight: 700, color: INK });
  });

  // 2 跟进板块图标块 + 3 货物条 + 4 状态标签
  const r2 = H(pg, 16, { width: 'fill_container', alignItems: 'start' });
  const b = card(r2, { w: 560, gap: 10 }); T(b, '跟进板块（类别色）', { size: 13, weight: 600, color: INK });
  const bs = H(b, 8);
  [['主动开发', 'compass'], ['商机成交', 'id-card'], ['订单履约', 'inbox'], ['售后评价', 'message-square'], ['客户经营', 'trending-up']].forEach(([n, ic], i) => {
    const it = H(bs, 6, { padding: [6, 10], cornerRadius: 8, fill: fill('#FFFFFF'), stroke: { thickness: 1, fill: fill('#E5E8EB'), align: 'inside' } });
    const c = S.steps[i]; const tl = H(it, 0, { width: 22, height: 22, cornerRadius: 6, justifyContent: 'center', fill: fill(mixc(c, S.tint)) }); icon(tl, ic, 12, c); T(it, n, { size: 12, color: INK });
  });
  T(b, '货物进度（6 段）', { size: 13, weight: 600, color: INK });
  const gb = H(b, 2, { width: 'fill_container' });
  [[3, S.goods[0], '已出运'], [2, S.goods[1], '已交货代'], [2, S.goods[2], '在仓'], [1, S.goods[3], '在途'], [2, S.goods[4], '待发货'], [2, S.goods[5], '待采购']].forEach(([w, c]) => I(gb, { type: 'rectangle', width: w * 40, height: 8, cornerRadius: 4, fill: fill(c) }));
  const lg = H(b, 10); [['已出运', S.goods[0]], ['已交货代', S.goods[1]], ['在仓', S.goods[2]], ['在途', S.goods[3]], ['待发货', S.goods[4]], ['待采购', S.goods[5]]].forEach(([t, c]) => { const x = H(lg, 4); I(x, { type: 'ellipse', width: 8, height: 8, fill: fill(c) }); T(x, t, { size: 12, color: MUTE }); });

  const c3 = card(r2, { gap: 10 }); T(c3, '多系列图表（询盘来源）', { size: 13, weight: 600, color: INK });
  const ch = H(c3, 10, { height: 120, alignItems: 'end' });
  [[60, 40, 30, 22, 14], [70, 52, 26, 30, 18], [84, 46, 40, 24, 20], [92, 58, 36, 34, 26]].forEach((g) => { const gg = H(ch, 3, { alignItems: 'end' }); g.forEach((h, i) => I(gg, { type: 'rectangle', width: 12, height: h, cornerRadius: [3, 3, 0, 0], fill: fill(S.series[i]) })); });
  const lg2 = H(c3, 10); ['官网', '展会', '老客户', '平台', '其他'].forEach((t, i) => { const x = H(lg2, 4); I(x, { type: 'ellipse', width: 8, height: 8, fill: fill(S.series[i]) }); T(x, t, { size: 12, color: MUTE }); });
  const st = H(c3, 8); [['已完成', S.lbl.g, '#22C55E'], ['部分到账', S.lbl.o, '#FFAB00'], ['已逾期', '#B71D18', '#FF5630'], ['处理中', '#006C9C', '#00B8D9']].forEach(([t, fg, m]) => { const p = H(st, 0, { height: 24, padding: [0, 8], cornerRadius: 4, fill: fill(mixc(m, S.lbl.a)) }); T(p, t, { size: 12, weight: 600, color: fg }); });
};

const CUR = ['#3B82F6', '#2F9E99', '#B87A14', '#E8664A', '#2E9A62', '#7A8794'];
variant('A 现状：图形色也压暗了', '图表系列、类别图标、数字卡色族都为了深浅两种底色都 ≥3:1 压成中间调：青 #2F9E99 饱和度太低读成灰、赭黄 #B87A14 发土、绿 #2E9A62 发闷、数字卡「青」#1F7A76 近乎墨绿；调色板校验还查出赭黄和珊瑚正常视力下都难分。整体显暗。', false,
  { tint: 0.12, fam: { purple: '#8B5CF6', cyan: '#1F7A76', blue: '#3B82F6' }, steps: [PRIMARY, '#3B82F6', '#2F9E99', '#E8664A', '#2E9A62'], goods: ['#2E9A62', '#2F9E99', '#3B82F6', '#B87A14', '#E8664A', '#7A8794'], series: CUR, lbl: { g: '#078039', o: '#A85D00', a: 0.12 } });

const VIV = ['#1877F2', '#FF5630', '#00B8D9', '#8E33FF', '#22C55E'];
variant('B 推荐：图形用参考模板的原色，文字仍保持 4.5:1', '照参考模板的分工：图形（图表、类别图标、进度、圆点）用原色——蓝 #1877F2、橙红 #FF5630、青 #00B8D9、紫 #8E33FF、绿 #22C55E，顺序经调色板校验（色盲可分、饱和度、正常视力全部通过；深色另配同色相 5 色也全通过）；图标块浅底 16%。文字（标签字、金额红绿）继续用 R13 定的深一档，保持 4.5:1。图形对白底不到 3:1 的，旁边都有文字或图例。', true,
  { tint: 0.16, fam: { purple: '#8E33FF', cyan: '#00B8D9', blue: '#1877F2' }, steps: [PRIMARY, '#1877F2', '#00B8D9', '#8E33FF', '#22C55E'], goods: ['#22C55E', '#00B8D9', '#1877F2', '#8E33FF', '#FFAB00', '#919EAB'], series: VIV, lbl: { g: '#078039', o: '#A85D00', a: 0.12 } });

variant('C 备选：完全照参考模板（含标签文字）', '图形同 B；标签文字也照模板用 dark 档（绿 #118D57、橙 #B76E00），浅底 16%。最接近参考模板，但 12px 标签字对比度只有 3.7:1，低于 4.5:1。', false,
  { tint: 0.16, fam: { purple: '#8E33FF', cyan: '#00B8D9', blue: '#1877F2' }, steps: [PRIMARY, '#1877F2', '#00B8D9', '#8E33FF', '#22C55E'], goods: ['#22C55E', '#00B8D9', '#1877F2', '#8E33FF', '#FFAB00', '#919EAB'], series: VIV, lbl: { g: '#118D57', o: '#B76E00', a: 0.16 } });
