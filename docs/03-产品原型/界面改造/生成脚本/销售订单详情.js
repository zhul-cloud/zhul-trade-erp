// 销售订单详情原型（照 PrimeDash 订单详情 order/overview、发票详情 invoice/details、Stepper 组件）
// 数据与列表原型第 1 行一致：SO20261119004
const CN = 'Noto Sans SC';
const NUM = 'Public Sans';
const MONO = 'Roboto Mono';
const solid = (c) => [{ type: 'solid', color: c }];
const P = '#FDA92D';
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 3, blur: 1, spread: -2, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 2, blur: 2, spread: 0, color: 'rgba(145,158,171,0.14)' }, { type: 'shadow', offsetX: 0, offsetY: 1, blur: 5, spread: 0, color: 'rgba(145,158,171,0.12)' }];
const LIGHT = { key: 'light', page: '#F9FAFB', card: '#FFFFFF', head: '#F4F6F8', line: '#E5E8EB', field: '#D4D6D9', ink: '#212B36', body: '#3E4750', sub: '#637381', faint: '#919EAB', navOn: '#EDEEEF', soft: '#FEF5E6', tonal: '#F1F3F5', shadow: SH, stroke: null,
  st: { ok: ['#22C55E', '#E5F8EC'], warn: ['#FFAB00', '#FFF5E0'], info: ['#00B8D9', '#E0F6FA'], err: ['#FF5630', '#FFEBE6'], neu: ['#637381', '#ECEEF0'] } };
const DARK = { key: 'dark', page: '#0A0A0A', card: '#1A1A1A', head: '#222222', line: '#2A2A2A', field: '#4A4A4A', ink: '#F5F5F5', body: '#E0E0E0', sub: '#A0A0A0', faint: '#6E6E6E', navOn: '#2C2C2C', soft: '#3E311D', tonal: '#232323', shadow: [], stroke: { thickness: 1, fill: solid('#2A2A2A') },
  st: { ok: ['#22C55E', '#1B3525'], warn: ['#FFAB00', '#3F3116'], info: ['#00B8D9', '#163339'], err: ['#FF5630', '#3F241E'], neu: ['#A0A0A0', '#2F2F2F'] } };
// 货物进度条：图表系列色（2026-11-20 用户确认）
const G = { shipped: '#2E9A62', handed: '#2F9E99', inWh: '#3B82F6', transit: '#B87A14', pendShip: '#E8664A', pendBuy: '#7A8794' };

function mkIcon(p, n, s, c, inline) {
  const node = { type: 'icon_font', name: 'fill=' + c, iconFontName: n, width: s, height: s, fill: solid(c) };
  if (inline === false) return I(p, node);
  const w = I(p, { type: 'frame', name: 'icon', width: s, height: s + 2, layout: 'vertical', justifyContent: 'end' });
  return I(w, node);
}

function kit(T) {
  const tx = (p, content, o) => { const n = Object.assign({ type: 'text', content, fontSize: 14, fontFamily: CN, fill: solid(T.body) }, o || {}); n.name = 'fill=' + n.fill[0].color; return I(p, n); };
  const box = (p, o) => I(p, Object.assign({ type: 'frame', width: 'fit_content', height: 'fit_content' }, o || {}));
  const icon = (p, n, s, c, inline) => mkIcon(p, n, s, c, inline);
  const hr = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
  const card = (p, o) => box(p, Object.assign({ fill: solid(T.card), cornerRadius: 10, effects: T.shadow }, T.stroke ? { stroke: T.stroke } : {}, o));
  const label = (p, t, tone, ic) => { const b = box(p, { layout: 'horizontal', gap: 4, alignItems: 'center', padding: [3, 8], cornerRadius: 4, fill: solid(tone[1]) }); if (ic) icon(b, ic, 12, tone[0]); tx(b, t, { fontSize: 12, fill: solid(tone[0]) }); return b; };
  const primaryBtn = (p, t, ic) => { const b = box(p, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 36, padding: [0, 16], cornerRadius: 8, fill: solid(P), effects: T.key === 'light' ? SH : [] }); if (ic) icon(b, ic, 18, '#FFFFFF'); tx(b, t, { fontWeight: 500, fill: solid('#FFFFFF') }); return b; };
  // 次要按钮：PrimeDash 文字按钮；disabled 用禁用灰
  const textBtn = (p, t, ic, c) => { const col = c || P; const b = box(p, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 36, padding: [0, 10], cornerRadius: 8 }); if (ic) icon(b, ic, 18, col); tx(b, t, { fontWeight: 500, fill: solid(col) }); return b; };
  // 卡片标题：18/500，可带帮助图标、标签、右侧内容
  const cardHead = (p, title, o) => {
    o = o || {};
    const h = box(p, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' });
    tx(h, title, { fontSize: 18, fontWeight: 500, fill: solid(T.ink) });
    if (o.help) icon(h, 'info', 14, T.sub);
    for (const l of o.labels || []) label(h, l[0], l[1], l[2]);
    const r = box(h, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', alignItems: 'center', gap: 8 });
    return r;
  };
  // 「标签：数值」行：PrimeDash Price & Taxes，行高 52、14px、底部分隔线
  const kv = (p, k, v, o) => {
    o = o || {};
    const r = box(p, { width: 'fill_container', height: o.h || 44, layout: 'horizontal', alignItems: 'center', gap: 12, padding: [0, 4] });
    tx(r, k, { fill: solid(T.sub), width: o.kw || 96 });
    const vv = box(r, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center', justifyContent: o.right ? 'end' : 'start' });
    if (typeof v === 'string') tx(vv, v, Object.assign({ fontFamily: o.num ? NUM : CN, fill: solid(T.ink) }, o.v || {}));
    else v(vv);
    if (!o.last) hr(p);
    return vv;
  };
  return { tx, box, icon, hr, card, label, primaryBtn, textBtn, cardHead, kv };
}

function shell(T, W, H, name) {
  const { tx, box, icon, card } = kit(T);
  const root = I(null, { type: 'frame', name, width: W, height: H, layout: 'horizontal', gap: 16, padding: [16, 16, 16, 16], fill: solid(T.page), clipContent: true });
  const side = card(root, { width: 244, height: 868, layout: 'vertical', gap: 2, padding: [16, 14] });
  const brand = box(side, { layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 6, 14, 6] });
  const logo = box(brand, { width: 30, height: 30, cornerRadius: 8, fill: solid(P), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  tx(logo, '烛', { fontSize: 15, fontWeight: 700, fill: solid('#FFFFFF') });
  tx(brand, '烛龙ERP', { fontSize: 20, fontWeight: 700, fill: solid(P) });
  const nav = [['layout-dashboard', '工作台', 'top'], ['briefcase', '销售管理', 'open'], ['', '客户跟进'], ['', '询盘管理'], ['', '报价管理'], ['', '形式发票'], ['', '销售订单', 'on'], ['', '售后管理'], ['shopping-cart', '采购管理', 'grp'], ['warehouse', '仓库管理', 'grp'], ['truck', '物流管理', 'grp'], ['wallet', '财务管理', 'grp'], ['package', '商品管理', 'grp'], ['bar-chart-3', '统计分析', 'grp']];
  for (const [ic, l, k] of nav) {
    const isSub = !ic; const on = k === 'on'; const open = k === 'open';
    const row = box(side, { width: 'fill_container', height: 40, layout: 'horizontal', gap: 12, alignItems: 'center', padding: isSub ? [0, 12, 0, 16] : [0, 12, 0, 14], cornerRadius: 8, fill: solid(on ? T.soft : open ? T.navOn : T.card) });
    if (isSub) icon(row, on ? 'circle-dot' : 'circle', 12, on ? P : T.faint);
    else icon(row, ic, 20, open ? P : T.body);
    tx(row, l, { fontSize: 16, fill: solid(on || open ? P : T.body), width: 'fill_container' });
    if (open) icon(row, 'chevron-down', 18, P); else if (k === 'grp') icon(row, 'chevron-right', 18, T.sub);
  }
  const main = box(root, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 16 });
  const top = card(main, { width: 'fill_container', height: 64, layout: 'horizontal', alignItems: 'end', gap: 4, padding: [0, 16] });
  // 详情页用 usePageTitle 登记短名，标签显示单号
  for (const [l, on] of [['工作台', 0], ['SO20261119004', 1]]) {
    const t = box(top, { height: 'fill_container', layout: 'vertical', justifyContent: 'end' });
    const tt = box(t, { height: 44, layout: 'horizontal', gap: 6, alignItems: 'center', padding: [0, 16] });
    tx(tt, l, { fontWeight: 500, fontFamily: l.startsWith('SO') ? NUM : CN, fill: solid(on ? P : T.body) });
    if (l !== '工作台') icon(tt, 'x', 14, on ? P : T.sub);
    I(t, { type: 'frame', width: 'fill_container', height: 2, fill: solid(on ? P : T.card) });
  }
  const tools = box(top, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', justifyContent: 'end', alignItems: 'center', gap: 20 });
  icon(tools, T.key === 'light' ? 'sun' : 'moon', 20, T.body, false); icon(tools, 'languages', 20, T.body, false); icon(tools, 'bell', 20, T.body, false);
  const av = box(tools, { width: 36, height: 36, cornerRadius: 18, fill: solid(T.soft), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  tx(av, 'K', { fontWeight: 700, fill: solid(P) });
  return box(main, { width: 'fill_container', layout: 'vertical', gap: 16 });
}

// 成本明细卡片（照 CostCard 列顺序）：scrolled=true 时画滚到最右（实际成本、退款、行收入、行毛利与合计行）
// 数值为 0 显示「—」；收入未收齐时按销售额折人民币，按型号金额分摊（OrderCostCalc）
const COST_COLS = [['采购货款', 110], ['开票成本', 90], ['采购费用', 90], ['质量索赔', 90], ['退货运费', 90], ['售后退货冲减', 110], ['国内运费', 90], ['国际运费', 100], ['费用单', 90], ['收款手续费', 100], ['实际成本', 120], ['退款', 100], ['行收入', 120], ['行毛利', 168]];
const COST_ROWS = [
  { m: '6ES7214-1AG40-0XB0', q: 10, tips: ['10 个没入库'], pv: 'CNY 24,800.00', cost: 'CNY 24,800.00', rev: 'CNY 30,195.69', pf: 'CNY 5,395.69', pr: '17.87%' },
  { m: '6ES7223-1PL32-0XB0', q: 5, tips: ['5 个没入库'], pv: 'CNY 10,450.00', cost: 'CNY 10,450.00', rev: 'CNY 13,659.95', pf: 'CNY 3,209.95', pr: '23.50%' },
  { m: '1769-L33ER', q: 5, tips: ['5 个没入库'], pv: 'CNY 28,500.00', cost: 'CNY 28,500.00', rev: 'CNY 43,136.69', pf: 'CNY 14,636.69', pr: '33.93%' },
  { m: '6EP1334-3BA10', q: 3, tips: ['3 个没下采购订单，按采购成本价估算', '3 个没入库'], pv: 'CNY 3,300.00', cost: 'CNY 3,300.00', rev: 'CNY 4,313.67', pf: 'CNY 1,013.67', pr: '23.50%' },
];
function costCard(T, parent, scrolled) {
  const { tx, box, icon, card, label } = kit(T);
  const c = card(parent, { width: scrolled ? 792 : 'fill_container', layout: 'vertical', clipContent: true });
  const chd = box(c, { width: 'fill_container', layout: 'vertical', gap: 8, padding: [18, 24, 14, 24] });
  const ch1 = box(chd, { layout: 'horizontal', gap: 8, alignItems: 'center' });
  tx(ch1, '成本明细', { fontSize: 18, fontWeight: 500, fill: solid(T.ink) }); icon(ch1, 'info', 14, T.sub); label(ch1, '未结算', T.st.warn);
  tx(chd, '还差：货款没收齐 · 1 个型号没下采购订单 · 4 个型号没入库', { fontSize: 12, fill: solid(T.sub) });
  const cols = scrolled ? COST_COLS.slice(10) : COST_COLS.slice(0, 7);
  const t = box(c, { width: 'fill_container', layout: 'horizontal' });
  const fixedCol = box(t, { width: 260, layout: 'vertical', fill: solid(T.card), effects: scrolled ? [{ type: 'shadow', offsetX: 6, offsetY: 0, blur: 8, spread: -4, color: T.key === 'light' ? 'rgba(145,158,171,0.35)' : 'rgba(0,0,0,0.6)' }] : [] });
  const sc = box(t, { width: 'fill_container', layout: 'vertical', clipContent: true });
  const fh = box(fixedCol, { width: 'fill_container', height: 44, layout: 'horizontal', alignItems: 'center', padding: [0, 24], fill: solid(T.head) });
  tx(fh, '型号', { fontSize: 13, fontWeight: 500, fill: solid(T.ink) });
  const hh = box(sc, { layout: 'horizontal', height: 44, alignItems: 'center', fill: solid(T.head) });
  for (const [h, w] of cols) { const hc = box(hh, { width: w, layout: 'horizontal', justifyContent: 'end', padding: [0, 12, 0, 0] }); tx(hc, h, { fontSize: 13, fontWeight: 500, fill: solid(T.ink) }); }
  const val = (r, h) => ({ '采购货款': [r.pv, 1], '实际成本': [r.cost, 0, 1], '行收入': [r.rev], '行毛利': [r.pf + ' · ' + r.pr, 0, 1, T.st.ok[0]] })[h] || ['—'];
  for (const r of COST_ROWS) {
    const RH = 64 + 26 * (r.tips.length - 1);
    I(fixedCol, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
    I(sc, { type: 'frame', width: 2000, height: 1, fill: solid(T.line) });
    const mc = box(fixedCol, { width: 'fill_container', height: RH, layout: 'vertical', gap: 4, justifyContent: 'center', padding: [0, 12, 0, 24] });
    const ml = box(mc, { layout: 'horizontal', gap: 4 });
    tx(ml, r.m, { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) }); tx(ml, '× ' + r.q, { fontSize: 13, fontFamily: NUM, fill: solid(T.body) });
    for (const tp of r.tips) label(mc, tp, T.st.warn, 'triangle-alert');
    const row = box(sc, { layout: 'horizontal', height: RH, alignItems: 'center' });
    for (const [h, w] of cols) {
      const [v, dim, bold, col] = val(r, h);
      const cc = box(row, { width: w, layout: 'horizontal', justifyContent: 'end', padding: [0, 12, 0, 0] });
      tx(cc, v, { fontSize: 13, fontWeight: bold ? 600 : 400, fontFamily: NUM, fill: solid(col || (v === '—' || dim ? T.faint : T.body)) });
    }
  }
  I(fixedCol, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
  I(sc, { type: 'frame', width: 2000, height: 1, fill: solid(T.line) });
  const ft = box(fixedCol, { width: 'fill_container', height: 48, layout: 'horizontal', alignItems: 'center', padding: [0, 24], fill: solid(T.head) });
  tx(ft, '合计', { fontWeight: 600, fill: solid(T.ink) });
  const fr = box(sc, { layout: 'horizontal', height: 48, alignItems: 'center', fill: solid(T.head) });
  const tot = { '实际成本': ['CNY 67,050.00', T.ink], '退款': ['—', T.faint], '行收入': ['CNY 91,306.00', T.ink], '行毛利': ['CNY 24,256.00 · 26.57%', T.st.ok[0]] };
  for (const [h, w] of cols) { const cc = box(fr, { width: w, layout: 'horizontal', justifyContent: 'end', padding: [0, 12, 0, 0] }); const v = tot[h]; if (v) tx(cc, v[0], { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(v[1]) }); }
  const sb = box(c, { width: 'fill_container', height: 14, layout: 'horizontal', padding: [5, 24], fill: solid(T.card) });
  if (scrolled) box(sb, { width: 380 });
  I(sb, { type: 'frame', width: 360, height: 4, cornerRadius: 2, fill: solid(T.faint) });
}

function detail(T, name) {
  const K = kit(T); const { tx, box, icon, hr, card, label, primaryBtn, textBtn, cardHead, kv } = K;
  const content = shell(T, 1440, 2720, name);

  // 返回条（标签页框架提供）
  const back = box(content, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 28, padding: [0, 12], cornerRadius: 999, fill: solid(T.soft) });
  icon(back, 'arrow-left', 14, P); tx(back, '返回 销售订单', { fontSize: 13, fontWeight: 600, fill: solid(P) });

  // 页头：单号 + 状态，说明行；右侧操作
  const head = box(content, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 16 });
  const hl = box(head, { width: 'fill_container', layout: 'vertical', gap: 6 });
  const t1 = box(hl, { layout: 'horizontal', gap: 10, alignItems: 'center' });
  tx(t1, 'SO20261119004', { fontSize: 22, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
  label(t1, '待采购', T.st.neu); label(t1, '现货', T.st.ok); label(t1, '部分到账', T.st.info);
  const t2 = box(hl, { layout: 'horizontal', gap: 8, alignItems: 'center' });
  const dot = () => tx(t2, '·', { fill: solid(T.faint) });
  tx(t2, 'Arabian Automation LLC', { fontFamily: NUM, fill: solid(T.body) }); dot();
  tx(t2, '来源单号', { fill: solid(T.sub) }); tx(t2, 'PI20261118003', { fontWeight: 600, fontFamily: NUM, fill: solid(P) }); dot();
  tx(t2, 'Kevin', { fontFamily: NUM, fill: solid(T.body) }); dot();
  tx(t2, '2026-11-19 14:20 创建', { fill: solid(T.sub) });
  const acts = box(head, { layout: 'horizontal', gap: 4, alignItems: 'center' });
  textBtn(acts, '售后', 'headset'); textBtn(acts, '客户已收货', 'check', T.faint);
  const more = box(acts, { width: 36, height: 36, cornerRadius: 18, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }); icon(more, 'ellipsis', 20, T.body, false);
  box(acts, { width: 4 });
  primaryBtn(acts, '发货通知', 'send');

  // 主体：左主栏 + 右侧栏 340
  const body = box(content, { width: 'fill_container', layout: 'horizontal', gap: 16 });
  const L = box(body, { width: 'fill_container', layout: 'vertical', gap: 16 });
  const R = box(body, { width: 340, layout: 'vertical', gap: 16 });

  // ① 信息卡：PrimeDash Customer Info 等 tonal 卡片，2×2
  const infoCard = (p, title, ic, primary) => {
    const c = box(p, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 10, padding: [18, 20], cornerRadius: 10, fill: solid(primary ? (T.key === 'light' ? T.soft : '#2A2318') : T.tonal) });
    const h = box(c, { width: 'fill_container', layout: 'horizontal', alignItems: 'center' });
    tx(h, title, { fontSize: 16, fontWeight: 500, fill: solid(T.ink), width: 'fill_container' });
    const it = box(h, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(primary ? (T.key === 'light' ? '#FDE7C4' : '#5A4422') : (T.key === 'light' ? '#E3E7EB' : '#2E2E2E')) });
    icon(it, ic, 16, primary ? P : T.sub, false);
    return c;
  };
  const r1 = box(L, { width: 'fill_container', layout: 'horizontal', gap: 16 });
  let c = infoCard(r1, '客户', 'user', true);
  tx(c, 'Arabian Automation LLC', { fontSize: 16, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
  tx(c, '老客户 · 阿联酋', { fill: solid(T.body) });
  c = infoCard(r1, '订单信息', 'clipboard-list');
  const row = (p, k, f) => { const r = box(p, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' }); tx(r, k, { fill: solid(T.sub), width: 72 }); f(r); };
  row(c, '销售日期', (r) => { tx(r, '2026-11-19', { fontFamily: NUM, fill: solid(T.ink) }); icon(r, 'pencil', 14, P); });
  row(c, '现货/期货', (r) => label(r, '现货', T.st.ok));
  row(c, '采购员', (r) => { tx(r, '林熙', { fill: solid(T.ink) }); tx(r, '3 个型号', { fontSize: 12, fill: solid(T.sub) }); label(r, '未指定 1 个型号', T.st.warn, 'triangle-alert'); });
  const r2 = box(L, { width: 'fill_container', layout: 'horizontal', gap: 16 });
  c = infoCard(r2, '买方（Bill To）', 'file-text');
  tx(c, 'Arabian Automation LLC', { fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
  tx(c, 'Office 1204, Al Moosa Tower 2, Sheikh Zayed Road, Dubai, UAE', { fontFamily: NUM, fill: solid(T.body), width: 'fill_container', textGrowth: 'fixed-width', lineHeight: 1.5 });
  c = infoCard(r2, '收货人（Consignee）', 'map-pin');
  tx(c, 'Gulf Industrial Trading FZE', { fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
  tx(c, 'Plot S30812, Jebel Ali Free Zone South, Dubai, UAE', { fontFamily: NUM, fill: solid(T.body), width: 'fill_container', textGrowth: 'fixed-width', lineHeight: 1.5 });

  // ② 订单进度：PrimeDash Stepper + 货物 6 格
  c = card(L, { width: 'fill_container', layout: 'vertical', gap: 18, padding: [20, 24] });
  cardHead(c, '订单进度', { help: 1 });
  const stp = box(c, { width: 'fill_container', layout: 'horizontal', alignItems: 'start' });
  const steps = [['待采购', 1], ['已下单', 3], ['已入库', 0], ['已交货代', 0], ['已出运', 0], ['已完成', 0]];
  steps.forEach(([n, cnt], i) => {
    if (i > 0) { const dv = box(stp, { width: 'fill_container', height: 30, layout: 'vertical', justifyContent: 'center' }); I(dv, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.key === 'light' ? '#DADFE3' : '#3A3A3A') }); }
    const s = box(stp, { width: 84, layout: 'vertical', gap: 6, alignItems: 'center' });
    const on = cnt > 0;
    const a = box(s, { width: 30, height: 30, cornerRadius: 15, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(on ? P : (T.key === 'light' ? '#EFF0F1' : '#2C2C2C')) });
    tx(a, String(i + 1), { fontSize: 12, fontFamily: NUM, fill: solid(on ? '#FFFFFF' : T.sub) });
    tx(s, n, { fontWeight: on ? 600 : 400, fill: solid(on ? T.ink : T.sub) });
    tx(s, on ? cnt + ' 个型号' : '—', { fontSize: 12, fill: solid(on ? P : T.faint) });
  });
  hr(c);
  const gg = box(c, { width: 'fill_container', layout: 'horizontal', gap: 10 });
  const goods = [['已出运', 0, G.shipped, '货代已发出'], ['已交货代', 0, G.handed, '在货代，等出运'], ['在仓', 0, G.inWh, '福州仓库'], ['在途', 20, G.transit, '最早 11-25 到'], ['待发货', 0, G.pendShip, '已下单，供应商还没发'], ['待采购', 3, G.pendBuy, '还没下采购订单']];
  for (const [n, q, col, hint] of goods) {
    const g = box(gg, { width: 'fill_container', height: 96, layout: 'vertical', gap: 4, padding: [10, 12], cornerRadius: 8, fill: solid(T.tonal) });
    const gl = box(g, { layout: 'horizontal', gap: 6, alignItems: 'center' }); I(gl, { type: 'ellipse', width: 8, height: 8, fill: solid(col) }); tx(gl, n, { fontSize: 12, fill: solid(T.sub) });
    tx(g, q + ' 件', { fontSize: 18, fontWeight: 700, fontFamily: NUM, fill: solid(q ? T.ink : T.faint) });
    tx(g, hint, { fontSize: 12, fill: solid(T.faint), width: 'fill_container', textGrowth: 'fixed-width', lineHeight: 1.4 });
  }

  // ③ 型号明细：表格卡片（可横向滚动），工具行左侧数量
  c = card(L, { width: 'fill_container', layout: 'vertical', clipContent: true });
  const th = box(c, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 8, padding: [18, 24, 14, 24] });
  tx(th, '型号明细', { fontSize: 18, fontWeight: 500, fill: solid(T.ink) }); icon(th, 'info', 14, T.sub);
  tx(th, '4 个型号 · 23 件', { fontSize: 13, fill: solid(T.sub) });
  const cols = [['', 40], ['#', 36], ['品牌', 72], ['品类', 64], ['型号', 210], ['描述', 190], ['数量', 70], ['单价', 110], ['小计', 120], ['现货/期货', 90], ['采购员', 90], ['货物', 250], ['采购订单 · 发货单', 170], ['进度', 100]];
  const items = [
    { br: '西门子', ca: 'PLC', m: '6ES7214-1AG40-0XB0', d: 'CPU 1214C DC/DC/DC', q: '10', up: 'USD 420.00', amt: 'USD 4,200.00', pu: '林熙', g: [[10, G.transit]], gl: [['在途 10 · 预计 11-25 到（顺丰）', G.transit]], docs: ['PO20261119002', 'SD20261120001 在途 10'], pr: ['已下单', 'info'] },
    { br: '西门子', ca: 'PLC', m: '6ES7223-1PL32-0XB0', d: 'SM 1223 DI16/DQ16', q: '5', up: 'USD 380.00', amt: 'USD 1,900.00', pu: '林熙', remark: '客户要求原厂包装', g: [[5, G.transit]], gl: [['在途 5 · 预计 11-25 到（顺丰）', G.transit]], docs: ['PO20261119002', 'SD20261120001 在途 5'], pr: ['已下单', 'info'] },
    { br: 'AB', ca: 'PLC', m: '1769-L33ER', d: 'CompactLogix 5370 控制器', q: '5', up: 'USD 1,200.00', amt: 'USD 6,000.00', pu: '林熙', g: [[5, G.transit]], gl: [['在途 5 · 预计 11-26 到', G.transit]], docs: ['PO20261119003', 'SD20261121002 在途 5'], pr: ['已下单', 'info'] },
    { br: '西门子', ca: '电源', m: '6EP1334-3BA10', d: 'SITOP PSU100S 24V/10A', q: '3', up: 'USD 200.00', amt: 'USD 600.00', pu: '', g: [[3, G.pendBuy]], gl: [['待采购 3', 'warnLabel']], docs: [], pr: ['待采购', 'neu'] },
  ];
  const tb = box(c, { width: 'fill_container', layout: 'vertical', clipContent: true });
  const hdr = box(tb, { layout: 'horizontal', height: 52, alignItems: 'center', fill: solid(T.head), padding: [0, 0, 0, 16] });
  const chk = (p, on) => { const b = box(p, { width: 18, height: 18, cornerRadius: 4, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(on ? P : T.card), stroke: on ? undefined : { thickness: 1.5, fill: solid(T.faint) } }); if (on) icon(b, 'check', 14, '#FFFFFF', false); };
  for (const [h, w] of cols) { const hc = box(hdr, { width: w, layout: 'horizontal', alignItems: 'center', justifyContent: ['数量', '单价', '小计'].includes(h) ? 'end' : 'start', padding: ['数量', '单价', '小计'].includes(h) ? [0, 12, 0, 0] : 0 }); if (w === 40) chk(hc, 0); else tx(hc, h, { fontWeight: 500, fill: solid(T.ink) }); }
  items.forEach((it, idx) => {
    I(tb, { type: 'frame', width: 2000, height: 1, fill: solid(T.line) });
    const r = box(tb, { layout: 'horizontal', alignItems: 'center', padding: [14, 0, 14, 16] });
    const cell = (w, o) => box(r, Object.assign({ width: w, layout: 'vertical', gap: 4 }, o || {}));
    const rt = (w) => cell(w, { alignItems: 'end', padding: [0, 12, 0, 0] });
    chk(cell(40), 0);
    tx(cell(36), String(idx + 1), { fontFamily: NUM, fill: solid(T.sub) });
    tx(cell(72), it.br, { fontWeight: 600, fill: solid(T.ink) });
    tx(cell(64), it.ca, { fill: solid(T.body) });
    let x = cell(210); tx(x, it.m, { fontSize: 13, fontWeight: 600, fontFamily: MONO, fill: solid(P) });
    const vm = box(x, { layout: 'horizontal', gap: 4, alignItems: 'center' }); icon(vm, 'image', 12, T.sub); tx(vm, '查看素材', { fontSize: 12, fill: solid(T.sub) });
    if (it.remark) { const rr = box(x, { layout: 'horizontal', gap: 6, alignItems: 'center' }); label(rr, it.remark, T.st.warn, 'message-square'); icon(rr, 'pencil', 12, T.faint); tx(x, 'Kevin · 11-19 16:05', { fontSize: 12, fontFamily: NUM, fill: solid(T.faint) }); }
    else { const ab = box(x, { layout: 'horizontal', gap: 2, alignItems: 'center' }); icon(ab, 'plus', 12, P); tx(ab, '备注', { fontSize: 12, fill: solid(P) }); }
    tx(cell(190), it.d, { fontSize: 13, fontFamily: NUM, fill: solid(T.body) });
    tx(rt(70), it.q, { fontFamily: NUM, fill: solid(T.ink) });
    tx(rt(110), it.up, { fontFamily: NUM, fill: solid(T.body) });
    tx(rt(120), it.amt, { fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
    label(cell(90), '现货', T.st.ok);
    x = cell(90); if (it.pu) tx(x, it.pu, { fill: solid(T.body) }); else label(x, '未指定', T.st.warn, 'triangle-alert');
    x = cell(250, { gap: 5 });
    const gb = box(x, { width: 180, height: 6, cornerRadius: 3, fill: solid(T.line), layout: 'horizontal', clipContent: true });
    const tot = it.g.reduce((a, [n]) => a + n, 0); for (const [n, col] of it.g) I(gb, { type: 'frame', width: Math.round(180 * n / tot), height: 6, fill: solid(col) });
    for (const [t, col] of it.gl) {
      if (col === 'warnLabel') { label(x, t, T.st.warn, 'triangle-alert'); continue; }
      const l = box(x, { layout: 'horizontal', gap: 6, alignItems: 'center' }); I(l, { type: 'ellipse', width: 7, height: 7, fill: solid(col) }); tx(l, t, { fontSize: 12, fill: solid(T.sub) });
    }
    x = cell(170, { gap: 2 });
    if (it.docs.length) { tx(x, it.docs[0], { fontSize: 12, fontWeight: 600, fontFamily: NUM, fill: solid(P) }); tx(x, it.docs[1], { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) }); }
    else tx(x, '还没有下采购订单', { fontSize: 12, fill: solid(T.faint) });
    label(cell(100), it.pr[0], T.st[it.pr[1]]);
  });
  const sb = box(c, { width: 'fill_container', height: 14, layout: 'horizontal', padding: [5, 24], fill: solid(T.card) });
  I(sb, { type: 'frame', width: 300, height: 4, cornerRadius: 2, fill: solid(T.faint) });

  // ④ 金额与条款：PrimeDash Price & Taxes 的「标签：数值」行
  c = card(L, { width: 'fill_container', layout: 'vertical', gap: 10, padding: [20, 24] });
  cardHead(c, '金额与条款');
  const two = box(c, { width: 'fill_container', layout: 'horizontal', gap: 32 });
  const tl = box(two, { width: 'fill_container', layout: 'vertical' });
  kv(tl, '交期', '付款后 2-4 周');
  kv(tl, '付款条件', 'T/T 30% 定金，发货前付尾款');
  kv(tl, '贸易术语', 'FOB Shanghai', { v: { fontFamily: NUM } });
  kv(tl, '起运港', 'Shanghai', { v: { fontFamily: NUM }, last: 1 });
  const tr = box(two, { width: 'fill_container', layout: 'vertical' });
  kv(tr, '小计', 'USD 12,700.00', { num: 1, right: 1 });
  kv(tr, '运费', 'USD 160.00', { num: 1, right: 1 });
  const tot = box(tr, { width: 'fill_container', height: 52, layout: 'horizontal', alignItems: 'center', padding: [0, 4] });
  tx(tot, '合计', { fontSize: 16, fontWeight: 600, fill: solid(T.ink), width: 'fill_container' });
  tx(tot, 'USD 12,860.00', { fontSize: 22, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
  const cny = box(tr, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', padding: [0, 4] });
  tx(cny, '≈ CNY 91,306.00（汇率 7.100000）', { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });

  // ⑤ 来源 / 去向
  c = card(L, { width: 'fill_container', layout: 'vertical', gap: 14, padding: [20, 24] });
  cardHead(c, '来源 / 去向', { help: 1 });
  const ch = box(c, { width: 'fill_container', layout: 'horizontal', gap: 12, alignItems: 'end' });
  const chain = [['询盘单', 'IQ20261115007', '已成交'], ['报价单', 'QT20261116002', '已成交'], ['PI', 'PI20261118003', '已转订单'], ['销售订单', 'SO20261119004', '有效', 1]];
  chain.forEach(([s, no, st, cur], i) => {
    if (i > 0) icon(ch, 'arrow-right', 16, T.faint, false);
    const col = box(ch, { layout: 'vertical', gap: 6 });
    tx(col, s, { fontSize: 12, fill: solid(T.sub) });
    const n = box(col, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 32, padding: [0, 10], cornerRadius: 8, fill: solid(cur ? T.soft : T.tonal) });
    tx(n, no, { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(cur ? T.ink : P) });
    tx(n, st, { fontSize: 12, fill: solid(T.sub) });
  });

  // ⑥ 附件与备注
  c = card(L, { width: 'fill_container', layout: 'vertical', gap: 14, padding: [20, 24] });
  const ah = cardHead(c, '附件与备注'); textBtn(ah, '上传附件', 'upload');
  const files = box(c, { width: 'fill_container', layout: 'horizontal', gap: 12 });
  for (const [n, ic, s] of [['客户PO-AA-2611.pdf', 'file-text', '268 KB'], ['原厂包装要求.jpg', 'image', '1.2 MB']]) {
    const f = box(files, { layout: 'horizontal', gap: 10, alignItems: 'center', padding: [10, 14], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.line) } });
    const fi = box(f, { width: 36, height: 36, cornerRadius: 8, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.tonal) }); icon(fi, ic, 18, T.sub, false);
    const fm = box(f, { layout: 'vertical', gap: 2 }); tx(fm, n, { fontSize: 13, fontFamily: NUM, fill: solid(T.ink) }); tx(fm, s, { fontSize: 12, fontFamily: NUM, fill: solid(T.faint) });
  }
  hr(c);
  const rm = box(c, { width: 'fill_container', layout: 'horizontal', gap: 12, alignItems: 'center' });
  tx(rm, '备注', { fill: solid(T.sub), width: 48 });
  tx(rm, '客户 12 月初要赶项目，尽量合并一次出运', { fill: solid(T.ink), width: 'fill_container' });
  textBtn(rm, '编辑', 'pencil');

  // ⑦ 成本明细（表格，横向滚动；列顺序照 CostCard）
  costCard(T, L, false);

  // ⑧ 销售出库单
  c = card(L, { width: 'fill_container', layout: 'vertical', gap: 12, padding: [20, 24] });
  cardHead(c, '销售出库单');
  tx(c, '还没有销售出库单', { fill: solid(T.faint) });

  // —— 右侧栏 ——
  // 收款
  c = card(R, { width: 'fill_container', layout: 'vertical', gap: 14, padding: [20, 20] });
  const rh = cardHead(c, '收款', { help: 1, labels: [['部分到账', T.st.info]] }); tx(rh, '来自', { fontSize: 12, fill: solid(T.sub) }); tx(rh, 'PI20261118003', { fontSize: 12, fontWeight: 600, fontFamily: NUM, fill: solid(P) });
  const rcv = box(c, { width: 'fill_container', layout: 'vertical', gap: 8 });
  const rr = box(rcv, { width: 'fill_container', layout: 'horizontal', alignItems: 'end' });
  tx(rr, '已到账', { fontSize: 13, fill: solid(T.sub), width: 'fill_container' });
  tx(rr, 'USD 3,858.00', { fontSize: 18, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
  tx(rr, ' / USD 12,860.00', { fontSize: 13, fontFamily: NUM, fill: solid(T.sub) });
  const pb = box(rcv, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' });
  const bar = box(pb, { width: 'fill_container', height: 6, cornerRadius: 3, fill: solid(T.line), layout: 'horizontal', clipContent: true }); I(bar, { type: 'frame', width: 78, height: 6, fill: solid(P) });
  tx(pb, '30%', { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
  label(rcv, '剩余 USD 9,002.00', T.st.warn);
  hr(c);
  tx(c, '按付款方式', { fontSize: 12, fill: solid(T.sub) });
  const pm = box(c, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' });
  tx(pm, '电汇', { fill: solid(T.body) }); label(pm, '线下', T.st.neu);
  const pmv = box(pm, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end' }); tx(pmv, 'USD 3,858.00', { fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
  hr(c);
  const rec = (title, ic, no, amt, st, date, sub, file) => {
    const b = box(c, { width: 'fill_container', layout: 'vertical', gap: 6, padding: [12, 14], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.line) } });
    const l1 = box(b, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' });
    icon(l1, ic, 16, T.sub); tx(l1, title, { fontWeight: 600, fill: solid(T.ink) });
    if (no) tx(l1, no, { fontSize: 12, fontWeight: 600, fontFamily: NUM, fill: solid(P) });
    const sp = box(l1, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end' }); tx(sp, date, { fontSize: 12, fontFamily: NUM, fill: solid(T.faint) });
    const l2 = box(b, { layout: 'horizontal', gap: 8, alignItems: 'center' }); tx(l2, amt, { fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) }); if (st) label(l2, st[0], st[1]);
    tx(b, sub, { fontSize: 12, fill: solid(T.sub), width: 'fill_container', textGrowth: 'fixed-width', lineHeight: 1.5 });
    if (file) { const f = box(b, { layout: 'horizontal', gap: 4, alignItems: 'center' }); icon(f, 'paperclip', 12, P); tx(f, file, { fontSize: 12, fontFamily: NUM, fill: solid(P) }); }
  };
  rec('到账', 'landmark', 'SK20261119003', 'USD 3,858.00', null, '11-19', '电汇 · 王敏 登记 · 实收 CNY 27,391.80');
  rec('水单', 'file-text', '', 'USD 3,858.00', ['已到账', T.st.ok], '11-18', '电汇 · Kevin 上传', '水单-AA-1118.jpg');

  // 费用订单
  c = card(R, { width: 'fill_container', layout: 'vertical', gap: 12, padding: [20, 20] });
  const fh = cardHead(c, '费用订单', { help: 1 }); textBtn(fh, '新建', 'plus');
  tx(c, '没有费用订单', { fill: solid(T.faint) });

  // 订单毛利
  c = card(R, { width: 'fill_container', layout: 'vertical', gap: 4, padding: [20, 20] });
  cardHead(c, '订单毛利', { help: 1, labels: [['未结算', T.st.warn]] });
  kv(c, '销售额', (v) => { const vc = box(v, { layout: 'vertical', gap: 2, alignItems: 'end' }); tx(vc, 'USD 12,860.00', { fontFamily: NUM, fill: solid(T.ink) }); tx(vc, '≈ CNY 91,306.00', { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) }); }, { right: 1, kw: 72, h: 56 });
  kv(c, '收入', 'CNY 91,306.00', { num: 1, right: 1, kw: 72 });
  kv(c, '实际成本', 'CNY 67,050.00', { num: 1, right: 1, kw: 72 });
  const pf = box(c, { width: 'fill_container', height: 52, layout: 'horizontal', alignItems: 'center', padding: [0, 4] });
  tx(pf, '预计毛利 · 26.57%', { fontWeight: 600, fill: solid(T.ink), width: 'fill_container' });
  tx(pf, 'CNY 24,256.00', { fontSize: 20, fontWeight: 700, fontFamily: NUM, fill: solid(T.st.ok[0]) });
}

// 状态补充：勾选型号后的批量操作行；「更多」菜单
function states(T, name) {
  const K = kit(T); const { tx, box, icon, hr, card, label, primaryBtn, textBtn } = K;
  const root = I(null, { type: 'frame', name, width: 900, height: 330, layout: 'horizontal', gap: 24, padding: [24, 24], fill: solid(T.page) });
  // 批量操作行（照列表骨架「已选 N 张 · 取消选择」）
  const a = card(root, { width: 560, layout: 'vertical', clipContent: true });
  const bar = box(a, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 8, padding: [14, 20], fill: solid(T.soft) });
  tx(bar, '已选 2 个型号', { fontWeight: 600, fill: solid(T.ink) }); tx(bar, '取消选择', { fontSize: 13, fill: solid(P) });
  const br = box(bar, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', alignItems: 'center', gap: 2 });
  textBtn(br, '指定采购员', 'user'); const sk = textBtn(br, '现货/期货', 'arrow-left-right'); icon(sk, 'chevron-down', 14, P); primaryBtn(br, '更新进度');
  const hdr = box(a, { width: 'fill_container', layout: 'horizontal', height: 44, alignItems: 'center', fill: solid(T.head), padding: [0, 20], gap: 16 });
  const ck = (p, on) => { const b = box(p, { width: 18, height: 18, cornerRadius: 4, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(on === 2 ? P : on ? P : T.card), stroke: on ? undefined : { thickness: 1.5, fill: solid(T.faint) } }); if (on === 1) icon(b, 'check', 14, '#FFFFFF', false); if (on === 2) icon(b, 'minus', 14, '#FFFFFF', false); };
  ck(hdr, 2); tx(hdr, '型号', { fontWeight: 500, fill: solid(T.ink) });
  for (const [m, on] of [['6ES7214-1AG40-0XB0', 1], ['6ES7223-1PL32-0XB0', 1], ['1769-L33ER', 0]]) {
    hr(a); const r = box(a, { width: 'fill_container', layout: 'horizontal', height: 48, alignItems: 'center', padding: [0, 20], gap: 16, fill: solid(on ? (T.key === 'light' ? '#FFFBF4' : '#221C12') : T.card) });
    ck(r, on); tx(r, m, { fontSize: 13, fontWeight: 600, fontFamily: MONO, fill: solid(P) });
  }
  // 「更多」菜单：低频操作收进来，取消订单标红
  const col = box(root, { layout: 'vertical', gap: 8, alignItems: 'end' });
  const mb = box(col, { width: 36, height: 36, cornerRadius: 18, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.navOn) }); icon(mb, 'ellipsis', 20, T.body, false);
  const menu = box(col, Object.assign({ width: 180, layout: 'vertical', padding: [6, 6], cornerRadius: 8, fill: solid(T.card), effects: T.key === 'light' ? [{ type: 'shadow', offsetX: 0, offsetY: 8, blur: 16, spread: 0, color: 'rgba(145,158,171,0.24)' }] : [] }, T.stroke ? { stroke: T.stroke } : {}));
  for (const [t, ic, red] of [['申请退款', 'undo-2'], ['部分取消', 'circle-minus'], ['取消订单', 'ban', 1]]) {
    if (red) I(menu, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
    const it = box(menu, { width: 'fill_container', height: 38, layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 10], cornerRadius: 6 });
    icon(it, ic, 16, red ? T.st.err[0] : T.body); tx(it, t, { fill: solid(red ? T.st.err[0] : T.body) });
  }
}

detail(LIGHT, 'B1 销售订单详情 · 浅色 · 1440');
detail(DARK, 'B2 销售订单详情 · 深色 · 1440');
states(LIGHT, 'B1c 批量操作与更多菜单 · 浅色');
states(DARK, 'B2c 批量操作与更多菜单 · 深色');

// 状态组合：已取消、先采购后收款、售后单、部分取消记录（文字取自 detail.tsx 与字典）
function alertBar(T, p, text, action) {
  const { tx, box, icon } = kit(T);
  const deep = T.key === 'light' ? '#7A5200' : '#FFD27A';
  const a = box(p, { width: 'fill_container', layout: 'horizontal', gap: 10, alignItems: 'center', padding: [12, 16], cornerRadius: 8, fill: solid(T.st.warn[1]) });
  icon(a, 'circle-alert', 18, T.st.warn[0]);
  tx(a, text, { fill: solid(deep), width: 'fill_container', textGrowth: 'fixed-width', lineHeight: 1.5 });
  if (action) tx(a, action, { fontWeight: 600, fill: solid(P) });
}
function stateCombo(T, name) {
  const K = kit(T); const { tx, box, icon, hr, card, label } = K;
  const root = I(null, { type: 'frame', name, width: 1180, height: 590, layout: 'vertical', gap: 24, padding: [24, 24], fill: solid(T.page) });
  const cap = (t) => tx(root, t, { fontSize: 13, fontWeight: 600, fill: solid(T.sub) });

  cap('已取消：页头没有操作，订单进度隐藏');
  let h = box(root, { width: 'fill_container', layout: 'vertical', gap: 12 });
  let t1 = box(h, { layout: 'horizontal', gap: 10, alignItems: 'center' });
  tx(t1, 'SO20261116001', { fontSize: 22, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
  label(t1, '已取消', T.st.neu); label(t1, '现货', T.st.ok); label(t1, '待到账', T.st.warn);
  alertBar(T, h, '订单已取消（Kevin 2026-11-20 10:15）：客户项目暂停');

  cap('先采购后收款：页头多一个标签，提示条带操作');
  h = box(root, { width: 'fill_container', layout: 'vertical', gap: 12 });
  t1 = box(h, { layout: 'horizontal', gap: 10, alignItems: 'center' });
  tx(t1, 'SO20261118006', { fontSize: 22, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
  label(t1, '已下单', T.st.info); label(t1, '现货', T.st.ok); label(t1, '待到账', T.st.warn); label(t1, '先采购后收款', T.st.warn);
  alertBar(T, h, '客户还没有付款（先采购后收款：老客户月底结款 · 王敏 11-18），请跟进收款', '去 PI 上传水单');

  const row2 = box(root, { width: 'fill_container', layout: 'horizontal', gap: 16 });
  // 售后单卡片
  let c = card(row2, { width: 'fill_container', layout: 'vertical', gap: 12, padding: [20, 24] });
  let hh = box(c, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 8 });
  tx(hh, '售后单', { fontSize: 18, fontWeight: 500, fill: solid(T.ink), width: 'fill_container' }); tx(hh, '1 张', { fontSize: 12, fill: solid(T.sub) });
  let r = box(c, { width: 'fill_container', layout: 'horizontal', gap: 10, alignItems: 'center', padding: [10, 14], cornerRadius: 8, fill: solid(T.tonal) });
  tx(r, 'AS20261125001', { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(P) });
  label(r, 'S3', T.st.info);
  tx(r, '6ES7214-1AG40-0XB0 × 1 · 产品质量 · 换货', { fontSize: 12, fontFamily: NUM, fill: solid(T.sub), width: 'fill_container' });
  label(r, '处理中', T.st.info);
  // 部分取消记录 + 合计标题变化
  c = card(row2, { width: 'fill_container', layout: 'vertical', gap: 12, padding: [20, 24] });
  hh = box(c, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 8 });
  tx(hh, '部分取消记录', { fontSize: 18, fontWeight: 500, fill: solid(T.ink), width: 'fill_container' }); tx(hh, '共 1 次 · 少了 USD 600.00', { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
  r = box(c, { width: 'fill_container', layout: 'vertical', gap: 6, padding: [10, 14], cornerRadius: 8, fill: solid(T.tonal) });
  const l1 = box(r, { width: 'fill_container', layout: 'horizontal', gap: 10, alignItems: 'center' });
  tx(l1, '11-21 10:30', { fontSize: 12, fontFamily: NUM, fill: solid(T.faint) });
  tx(l1, '6EP1334-3BA10', { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
  tx(l1, '取消 3 个', { fill: solid(T.sub) }); label(l1, '供应商缺货', T.st.warn);
  const am = box(l1, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', gap: 8, alignItems: 'center' });
  tx(am, '−USD 600.00', { fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) }); tx(am, 'Kevin', { fontSize: 12, fontFamily: NUM, fill: solid(T.faint) });
  tx(r, '采购与库存：关闭未排单的需求 3 个', { fontSize: 12, fill: solid(T.sub) });
  hr(c);
  const tt = box(c, { width: 'fill_container', layout: 'horizontal', alignItems: 'center' });
  tx(tt, '订单合计（部分取消后）', { fontSize: 16, fontWeight: 600, fill: solid(T.ink), width: 'fill_container' });
  tx(tt, 'USD 12,260.00', { fontSize: 22, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
}

// 成本明细滚到最右
function costScrolled(T, name) {
  const root = I(null, { type: 'frame', name, width: 840, height: 520, layout: 'vertical', padding: [24, 24], fill: solid(T.page) });
  costCard(T, root, true);
}

stateCombo(LIGHT, 'B1e 状态组合 · 浅色');
costScrolled(LIGHT, 'B1d 成本明细滚到最右 · 浅色');
