// 客户跟进页对比图（R10）：现状 / 推荐 / 备选。单独用 op design 生成。
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
  ['商机成交', 'handshake', '2', '报价前 0 · 报价后 1 · PI 后 1', 0],
  ['订单履约', 'package', '1', '采购中 1 · 验收发货 0 · 在途 0', 1],
  ['售后评价', 'message-square', '0', '签收 0 · 使用 0 · 问题 0 · 满意评价 0', 0],
  ['客户经营', 'trending-up', '0', '需求挖掘 0 · 复购机会 0 · 交叉销售 0 · 长期合作 0', 0],
];
const RAIN = ['#3B82F6', '#14B8A6', '#B87A14', '#E8664A', '#22C55E'];
const ROWS = [
  { tag: '订单履约 · F2 采购下单', next: '到下一阶段：型号全部下单后进入 F3', link: '看订单', remind: '系统提醒：告诉客户原因、新日期和我们在做什么', sop: 'SOP 建议：等采购下单' },
  { tag: '商机成交 · S7 成交推进', next: '到下一阶段：PI 全额到账后成交', link: '看 PI', remind: '系统提醒：PI 发出第 6 天：按客户说的付款日期确认付款进度，没付的问清审批、银行或付款卡点', due: '2 张单据到期' },
];

const root = I(null, { type: 'frame', name: '客户跟进对比', layout: 'vertical', gap: 40, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '客户跟进：现状 / 推荐 / 备选（浅色，1366 宽）', { size: 20, weight: 700, color: C.ink });
const variant = (title, note, good) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: C.ink }); if (good) pill(h, '推荐', C.green, C.greenSoft);
  T(col, note, { size: 13, color: C.sub, w: W });
  return V(col, 16, { width: W, padding: 24, cornerRadius: 12, fill: fill(C.canvas) });
};

// ───────── A 现状 ─────────
{
  const pg = variant('A 现状', '页头挤了分段、搜索、负责人和主按钮；6 张卡放不下要横向滚动（客户经营被截掉）；「今日待办」整块琥珀实底；每张卡的「第 n 步」、图标块各一种颜色，下面再画一条五色轨道和圆圈序号，与「第 n 步」重复；列表三层嵌套（白卡 → 描边客户卡 → 灰底行）；每行三个控件、一个琥珀实底按钮；「今天」「SOP 建议」用琥珀纯文字，像能点。');
  const hd = H(pg, 10, { width: 'fill_container' }); T(hd, '客户跟进', { size: 20, weight: 700, color: C.ink }); icon(hd, 'info', 14, C.faint); sp(hd);
  const sg = H(hd, 0, { padding: 3, cornerRadius: 8, fill: fill(C.track) }); const s1 = H(sg, 0, { padding: [4, 12], cornerRadius: 6, fill: fill(C.card) }); T(s1, '我的', { color: C.ink }); const s2 = H(sg, 0, { padding: [4, 12] }); T(s2, '团队', { color: C.mute });
  const se = H(hd, 6, { width: 200, height: 32, padding: [0, 10], cornerRadius: 6, fill: fill(C.card), stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } }); icon(se, 'search', 13, C.faint); T(se, '搜索客户、单号', { color: C.faint });
  const ow = H(hd, 6, { width: 150, height: 32, padding: [0, 10], cornerRadius: 6, fill: fill(C.card), stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } }); T(ow, '负责人：我', { color: C.ink }); sp(ow); icon(ow, 'chevron-down', 12, C.mute);
  btn(hd, '记一次跟进', { solid: true, ic: 'pencil' });
  const strip = H(pg, 12, { width: 'fill_container', alignItems: 'start', clipContent: true });
  const td = V(strip, 6, { width: 170, height: 124, padding: 16, cornerRadius: 12, fill: fill(C.primary) });
  const tt = H(td, 6); icon(tt, 'calendar', 14, C.ink); T(tt, '今日待办', { weight: 700, color: C.ink }); const tn = H(td, 4, { alignItems: 'end' }); T(tn, '2', { size: 26, weight: 700, color: C.ink }); T(tn, '位客户', { color: C.ink }); T(td, '逾期 1 · 今天 1', { size: 12, color: C.ink });
  const bwrap = V(strip, 8);
  const brow = H(bwrap, 8, { alignItems: 'start' });
  BOARDS.forEach(([n, ic, v, g, od], i) => {
    const c = V(brow, 6, { width: 170, height: 124, padding: 14, cornerRadius: 12, fill: fill(C.card), effects: SH });
    const t = H(c, 6, { width: 'fill_container' }); const tl = H(t, 0, { width: 24, height: 24, cornerRadius: 6, justifyContent: 'center', fill: fill(RAIN[i] + '22') }); icon(tl, ic, 13, RAIN[i]); T(t, n, { weight: 700, color: C.ink }); pill(t, `第 ${i + 1} 步`, RAIN[i], RAIN[i] + '1F');
    const nn = H(c, 4, { alignItems: 'end' }); T(nn, v, { size: 24, weight: 700, color: C.ink }); T(nn, '位客户', { size: 12 }); if (od) pill(nn, `逾期 ${od}`, C.red, C.redSoft);
    T(c, g, { size: 11, color: C.sub, w: 142 });
    if (i < 4) icon(brow, 'chevron-right', 12, C.faint);
  });
  const tr = H(bwrap, 6);
  BOARDS.forEach(([n, , v], i) => { const c = H(tr, 0, { width: 18, height: 18, cornerRadius: 9, justifyContent: 'center', stroke: { thickness: 1, fill: fill(RAIN[i]), align: 'inside' } }); T(c, String(i + 1), { size: 10, color: RAIN[i] }); T(tr, `${n} ${v}`, { size: 11, weight: 600, color: RAIN[i] }); I(tr, { type: 'rectangle', width: 40, height: 4, cornerRadius: 2, fill: fill(RAIN[i]) }); });
  const list = V(pg, 10, { width: 'fill_container', padding: 20, cornerRadius: 12, fill: fill(C.card), effects: SH });
  const ch = H(list, 8); pill(ch, '已逾期 1', C.red, C.redSoft); pill(ch, '今天 1', C.link, C.primarySoft); pill(ch, '本周还有 0', C.sub, C.neutralSoft);
  const sa = H(list, 6); box(sa); T(sa, '全选');
  T(list, '已逾期', { weight: 700, color: C.red });
  const cc = V(list, 8, { width: 'fill_container', padding: [12, 14], cornerRadius: 10, stroke: { thickness: 1, fill: fill(C.line), align: 'inside' } });
  const chh = H(cc, 8, { width: 'fill_container' }); box(chh); T(chh, 'PRIME CONTROLS LLC', { weight: 700, color: C.ink }); pill(chh, 'B', C.sub, C.neutralSoft); T(chh, '未评 · United States', { size: 12, color: C.mute }); sp(chh); T(chh, '逾期 3 天', { weight: 700, color: C.red });
  ROWS.forEach((r) => {
    const row = H(cc, 10, { width: 'fill_container', padding: [10, 12], cornerRadius: 8, fill: fill(C.inset) });
    pill(row, r.tag, r.tag.startsWith('订单') ? C.orange : C.green, r.tag.startsWith('订单') ? C.orangeSoft : C.greenSoft);
    T(row, `${r.next} `, { size: 12, color: C.sub }); T(row, r.link, { size: 12, color: C.link });
    const m = V(row, 2, { width: 'fill_container' }); T(m, r.remind, { size: 12, color: C.ink, w: 330 }); if (r.sop) T(m, r.sop, { size: 12, color: C.link });
    btn(row, '延后', { caret: true }); btn(row, '已跟进', { caret: true, ic: 'check' }); btn(row, '记跟进', { solid: true });
  });
  T(list, '今天', { weight: 700, color: C.link });
}

// 推荐与备选共用：页头、板块条
const goodHeader = (pg) => {
  const hd = H(pg, 10, { width: 'fill_container' }); T(hd, '客户跟进', { size: 20, weight: 700, color: C.ink }); icon(hd, 'info', 14, C.faint); sp(hd);
  const sg = H(hd, 0, { padding: 3, cornerRadius: 8, fill: fill(C.track) }); const s1 = H(sg, 0, { padding: [4, 12], cornerRadius: 6, fill: fill(C.card) }); T(s1, '我的', { color: C.ink }); const s2 = H(sg, 0, { padding: [4, 12] }); T(s2, '团队', { color: C.mute });
  btn(hd, '记一次跟进', { solid: true, ic: 'pencil' });
};
const goodStrip = (pg) => {
  const th = H(pg, 8, { width: 'fill_container' }); T(th, '跟进板块', { size: 15, weight: 700, color: C.ink }); sp(th); icon(th, 'repeat', 13, C.mute); T(th, '复购回到第 2 步', { size: 12, color: C.mute });
  const strip = H(pg, 0, { width: 'fill_container', alignItems: 'center' });
  const card = (p, sel, ic, name, step, v, groups, od) => {
    const c = V(p, 6, { width: 'fill_container', height: 128, padding: 14, cornerRadius: 12, fill: fill(C.card), effects: SH, stroke: sel ? { thickness: 2, fill: fill(C.primary), align: 'inside' } : undefined });
    const t = H(c, 8, { width: 'fill_container' }); const tl = H(t, 0, { width: 28, height: 28, cornerRadius: 8, justifyContent: 'center', fill: fill(sel ? C.primarySoft : C.neutralSoft) }); icon(tl, ic, 14, sel ? C.link : C.sub); T(t, name, { weight: 600, color: C.ink }); sp(t); if (step) T(t, step, { size: 12, color: C.faint });
    const nn = H(c, 4, { alignItems: 'end' }); T(nn, v, { size: 24, weight: 700, color: C.ink }); T(nn, '位客户', { size: 12, color: C.mute }); if (od) { sp(nn); pill(nn, `逾期 ${od}`, C.red, C.redSoft); }
    T(c, groups, { size: 12, color: C.mute });
  };
  card(strip, true, 'calendar-check', '今日待办', '', '2', '逾期 1 · 今天 1', 0);
  I(strip, { type: 'frame', width: 16, height: 1 });
  BOARDS.forEach(([n, ic, v, g, od], i) => {
    if (i > 0) { const a = V(strip, 0, { width: 18, alignItems: 'center' }); icon(a, 'chevron-right', 13, C.faint); }
    card(strip, false, ic, n, `第 ${i + 1} 步`, v, g.length > 18 ? g.slice(0, 17) + '…' : g, od);
  });
};
const goodRow = (p, r, first) => {
  const row = V(p, 4, { width: 'fill_container', padding: [10, 0, 10, 26] });
  const l1 = H(row, 8, { width: 'fill_container' });
  pill(l1, r.tag, C.sub, C.neutralSoft);
  T(l1, r.next, { size: 12, color: C.mute }); T(l1, r.link, { size: 12, color: C.link });
  if (r.due) { icon(l1, 'files', 12, C.mute); T(l1, r.due, { size: 12, color: C.mute }); T(l1, '展开', { size: 12, color: C.link }); }
  sp(l1);
  btn(l1, '延后', { caret: true }); btn(l1, '已跟进', { caret: true, ic: 'check' }); btn(l1, '记跟进', { ic: 'pencil' });
  const l2 = H(row, 6, { width: 'fill_container', alignItems: 'start' }); icon(l2, 'bell', 13, C.mute); T(l2, r.remind, { size: 13, color: C.ink, w: 760 });
  if (r.sop) { const l3 = H(row, 6); icon(l3, 'lightbulb', 13, C.faint); T(l3, r.sop, { size: 12, color: C.mute }); }
};
const custHead = (p, name, meta, due, red) => {
  const h = H(p, 8, { width: 'fill_container' }); box(h); T(h, name, { size: 14, weight: 700, color: C.ink }); pill(h, 'B', C.sub, C.neutralSoft); T(h, meta, { size: 12, color: C.mute }); sp(h); T(h, due, { size: 13, weight: 600, color: red ? C.red : C.sub });
};
const toolbar = (list) => {
  const tb = H(list, 8, { width: 'fill_container' });
  const q = (t, n, on, red) => { const b = H(tb, 4, { height: 30, padding: [0, 12], cornerRadius: 15, fill: fill(on ? C.primarySoft : C.neutralSoft) }); if (on) icon(b, 'check', 12, C.link); T(b, t, { size: 13, color: on ? C.link : C.sub }); T(b, n, { size: 13, weight: 600, color: red ? C.red : on ? C.link : C.sub }); };
  q('已逾期', '1', false, true); q('今天', '1'); T(tb, '本周还有 0', { size: 12, color: C.mute });
  sp(tb);
  const se = H(tb, 6, { width: 220, height: 32, padding: [0, 10], cornerRadius: 6, fill: fill(C.card), stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } }); icon(se, 'search', 13, C.faint); T(se, '搜索客户、单号', { color: C.faint });
  const ow = H(tb, 6, { width: 150, height: 32, padding: [0, 10], cornerRadius: 6, fill: fill(C.card), stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } }); T(ow, '负责人：我', { color: C.ink }); sp(ow); icon(ow, 'chevron-down', 12, C.mute);
};

// ───────── B 推荐 ─────────
{
  const pg = variant('B 推荐：板块等宽不滚动，列表两层', '页头只留「我的 / 团队」和主按钮，搜索、负责人挪到列表工具栏；六张卡等宽一排（窄屏 3+3，与漏斗同一算法），不再横向滚动；当前查看的卡用主题色描边、图标块浅底，不再整块实底；「第 n 步」灰字、图标块中性，去掉五色轨道和圆圈序号，「复购回到第 2 步」写在标题行右侧；逾期只在数字旁一个红标签。「已逾期」「今天」做成和全站一样的快捷筛选，「本周还有」只是数字，写成灰字。列表在一张卡里：客户之间分隔线、逾期客户左侧红色竖条，板块行不再包灰底；行内按钮都是描边，只有页头一个实底主按钮；阶段标签中性；「SOP 建议」灰字配灯泡，只有「看订单」这类能点的才用链接色。', true);
  goodHeader(pg); goodStrip(pg);
  const list = V(pg, 0, { width: 'fill_container', padding: 20, cornerRadius: 12, fill: fill(C.card), effects: SH });
  toolbar(list);
  I(list, { type: 'frame', width: 1, height: 14 });
  const g1 = H(list, 6, { padding: [0, 0, 8, 0] }); T(g1, '已逾期', { weight: 700, color: C.ink }); pill(g1, '1', C.red, C.redSoft);
  const c1 = H(list, 0, { width: 'fill_container', alignItems: 'start' });
  I(c1, { type: 'rectangle', width: 3, height: 'fill_container', cornerRadius: 2, fill: fill(C.redMain) });
  const c1b = V(c1, 0, { width: 'fill_container', padding: [10, 0, 0, 12] });
  custHead(c1b, 'PRIME CONTROLS LLC', '未评 · 美国', '逾期 3 天', true);
  goodRow(c1b, ROWS[0]); hr(c1b); goodRow(c1b, ROWS[1]);
  hr(list);
  const g2 = H(list, 6, { padding: [14, 0, 8, 0] }); T(g2, '今天', { weight: 700, color: C.ink }); pill(g2, '1', C.sub, C.neutralSoft);
  const c2 = V(list, 0, { width: 'fill_container', padding: [0, 0, 0, 15] });
  custHead(c2, 'Pacific Controls', '未成交 · 未评 · 澳大利亚', '今天');
  goodRow(c2, { tag: '商机成交 · S4 已报价', next: '', link: '', remind: '系统提醒：第 7 天：问客户的目标价或决策时间，给出明确的下一步' });
}

// ───────── C 备选 ─────────
{
  const pg = variant('C 备选：每个客户一张白卡', '页头、板块条同 B；列表改成每个客户一张与工具栏同级的白卡（同采购需求 D39 的分组写法），卡里板块行用分隔线。客户之间更分明，但每个客户都多一层阴影，同屏少看 1～2 个客户；客户多时页面更长。', false);
  goodHeader(pg); goodStrip(pg);
  const tbc = V(pg, 0, { width: 'fill_container', padding: [14, 20], cornerRadius: 12, fill: fill(C.card), effects: SH }); toolbar(tbc);
  const g1 = H(pg, 6); T(g1, '已逾期', { weight: 700, color: C.ink }); pill(g1, '1', C.red, C.redSoft);
  const c1 = H(pg, 0, { width: 'fill_container', cornerRadius: 12, fill: fill(C.card), effects: SH, clipContent: true, alignItems: 'start' });
  I(c1, { type: 'rectangle', width: 3, height: 'fill_container', fill: fill(C.redMain) });
  const c1b = V(c1, 0, { width: 'fill_container', padding: [14, 20, 4, 17] });
  custHead(c1b, 'PRIME CONTROLS LLC', '未评 · 美国', '逾期 3 天', true);
  goodRow(c1b, ROWS[0]); hr(c1b); goodRow(c1b, ROWS[1]);
  const g2 = H(pg, 6); T(g2, '今天', { weight: 700, color: C.ink }); pill(g2, '1', C.sub, C.neutralSoft);
  const c2 = V(pg, 0, { width: 'fill_container', padding: [14, 20, 4, 20], cornerRadius: 12, fill: fill(C.card), effects: SH });
  custHead(c2, 'Pacific Controls', '未成交 · 未评 · 澳大利亚', '今天');
  goodRow(c2, { tag: '商机成交 · S4 已报价', next: '', link: '', remind: '系统提醒：第 7 天：问客户的目标价或决策时间，给出明确的下一步' });
}

T(root, 'B、C 都要同步修改主规范 crm/customer-followup「客户跟进页与板块链路」：卡片间「渐变箭头」改为中性箭头，「进度轨道」改为标题行右侧的「复购回到第 2 步」；其余要求（今日待办卡、五个板块按顺序、第 n 步、客户数与分组数、逾期数）不变。', { size: 13, color: C.sub, w: W });
