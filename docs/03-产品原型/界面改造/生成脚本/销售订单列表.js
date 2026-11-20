const CN = 'Noto Sans SC';
const NUM = 'Public Sans';
const MONO = 'Roboto Mono';
const solid = (c) => [{ type: 'solid', color: c }];
const P = '#FDA92D';            // 主题色：琥珀（PrimeDash 预设）
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 3, blur: 1, spread: -2, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 2, blur: 2, spread: 0, color: 'rgba(145,158,171,0.14)' }, { type: 'shadow', offsetX: 0, offsetY: 1, blur: 5, spread: 0, color: 'rgba(145,158,171,0.12)' }];
const LIGHT = { key: 'light', page: '#F9FAFB', card: '#FFFFFF', head: '#F4F6F8', line: '#E5E8EB', field: '#D4D6D9', ink: '#212B36', body: '#3E4750', sub: '#637381', faint: '#919EAB', navOn: '#EDEEEF', soft: '#FEF5E6', shadow: SH, stroke: null,
  st: { ok: ['#22C55E', '#E5F8EC'], warn: ['#FFAB00', '#FFF5E0'], info: ['#00B8D9', '#E0F6FA'], err: ['#FF5630', '#FFEBE6'], neu: ['#637381', '#ECEEF0'] } };
const DARK = { key: 'dark', page: '#0A0A0A', card: '#1A1A1A', head: '#222222', line: '#2A2A2A', field: '#4A4A4A', ink: '#F5F5F5', body: '#E0E0E0', sub: '#A0A0A0', faint: '#6E6E6E', navOn: '#2C2C2C', soft: '#3E311D', shadow: [], stroke: { thickness: 1, fill: solid('#2A2A2A') },
  st: { ok: ['#22C55E', '#1B3525'], warn: ['#FFAB00', '#3F3116'], info: ['#00B8D9', '#163339'], err: ['#FF5630', '#3F241E'], neu: ['#A0A0A0', '#2F2F2F'] } };

// 图标：name 记下颜色，生成后由 fixfill.py 还原（OpenPencil 会把低对比度填充改成近黑）；
// 与中文并排时下移 1px，和文字视觉中心对齐；单独的图标按钮传 inline=false
function mkIcon(p, n, s, c, inline) {
  const node = { type: 'icon_font', name: 'fill=' + c, iconFontName: n, width: s, height: s, fill: solid(c) };
  if (inline === false) return I(p, node);
  const w = I(p, { type: 'frame', name: 'icon', width: s, height: s + 2, layout: 'vertical', justifyContent: 'end' });
  return I(w, node);
}

function draw(T, W, name, scrolled) {
  // scrolled：表格向右滚动后的样子，订单编号固定在左；1 = 第 2–6 列滚出视野，2 = 滚到最右
  const hide = (i) => scrolled && i >= 1 && i <= (scrolled === 2 ? 10 : 5);
  // 货物进度条：图表系列色（status-colors.md 待确认项）
  const G = { shipped: '#2E9A62', handed: '#2F9E99', inWh: '#3B82F6', transit: '#B87A14', pendShip: '#E8664A', pendBuy: '#7A8794' };
  const TRASH = scrolled ? I(null, { type: 'frame', name: 'TRASH', width: 10, height: 10 }) : null;
  const tx = (p, content, o) => { const n = Object.assign({ type: 'text', content, fontSize: 14, fontFamily: CN, fill: solid(T.body) }, o || {}); n.name = 'fill=' + n.fill[0].color; return I(p, n); };
  const box = (p, o) => I(p, Object.assign({ type: 'frame', width: 'fit_content', height: 'fit_content' }, o || {}));
  const icon = (p, n, s, c, inline) => mkIcon(p, n, s, c, inline);
  const hr = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
  const card = (p, o) => box(p, Object.assign({ fill: solid(T.card), cornerRadius: 10, effects: T.shadow }, T.stroke ? { stroke: T.stroke } : {}, o));
  const label = (p, t, tone, ic) => { const b = box(p, { layout: 'horizontal', gap: 4, alignItems: 'center', padding: [3, 8], cornerRadius: 4, fill: solid(tone[1]) }); if (ic) icon(b, ic, 12, tone[0]); tx(b, t, { fontSize: 12, fill: solid(tone[0]) }); };
  const primaryBtn = (p, t, ic) => { const b = box(p, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 36, padding: [0, 16], cornerRadius: 8, fill: solid(P), effects: T.key === 'light' ? SH : [] }); if (ic) icon(b, ic, 18, '#FFFFFF'); tx(b, t, { fontWeight: 500, fill: solid('#FFFFFF') }); };

  const root = I(null, { type: 'frame', name, width: W, height: 900, layout: 'horizontal', gap: 16, padding: [16, 16, 16, 16], fill: solid(T.page), clipContent: true });

  // 侧栏：悬浮白卡片，宽 244，内边距 14
  const side = card(root, { width: 244, height: 'fill_container', layout: 'vertical', gap: 2, padding: [16, 14] });
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

  // 主区
  const main = box(root, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 16 });
  const top = card(main, { width: 'fill_container', height: 64, layout: 'horizontal', alignItems: 'end', gap: 4, padding: [0, 16] });
  for (const [l, on] of [['工作台', 0], ['销售订单', 1], ['SO20261119004', 0]]) {
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

  const content = box(main, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 16 });
  const head = box(content, { width: 'fill_container', layout: 'horizontal', justifyContent: 'space_between', alignItems: 'center' });
  tx(head, '销售订单', { fontSize: 20, fontWeight: 600, fill: solid(T.ink) });
  primaryBtn(head, '新建销售订单', 'plus');

  // 汇总行：统计卡片里的指标类与「进行中」合计
  const sum = box(content, { width: 'fill_container', layout: 'horizontal', gap: 32, alignItems: 'end' });
  const metric = (l, v, s2, help) => { const m = box(sum, { layout: 'vertical', gap: 2 }); const lb = box(m, { layout: 'horizontal', gap: 4, alignItems: 'center' }); tx(lb, l, { fontSize: 12, fill: solid(T.sub) }); if (help) icon(lb, 'info', 12, T.sub); const r = box(m, { layout: 'horizontal', gap: 8, alignItems: 'end' }); tx(r, v, { fontSize: 18, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) }); if (s2) tx(r, s2, { fontSize: 12, fill: solid(T.sub) }); };
  metric('本月实收人民币', 'CNY 386,420.00', '线上 CNY 120,300.00 · 线下 CNY 266,120.00');
  // 分阶段数量放不下时整段隐藏（状态下拉里也能看到），不截断
  metric('进行中', '18', W >= 1440 ? '待采购 5 · 已下单 7 · 已入库 3 · 已交货代 2 · 已出运 1' : '', 1);
  metric('本月订单', '26', '', 1);
  metric('未指定采购员', '3', '', 1);

  // 搜索条件区
  const fc = card(content, { width: 'fill_container', layout: 'vertical', gap: 14, padding: [20, 20] });
  const grid = box(fc, { width: 'fill_container', layout: 'horizontal', gap: 12, alignItems: 'end' });
  const field = (l, w, v, o) => {
    const f = box(grid, { width: w, layout: 'vertical', gap: 6 });
    const lb = box(f, { layout: 'horizontal', gap: 4, alignItems: 'center' });
    tx(lb, l, { fontSize: 13, fontWeight: 500, fill: solid(T.body) });
    if (o.help) icon(lb, 'info', 14, T.sub);
    const inp = box(f, { width: 'fill_container', height: 40, layout: 'horizontal', gap: 8, alignItems: 'center', padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.field) } });
    if (o.search) icon(inp, 'search', 18, T.faint);
    tx(inp, v, { fontSize: 14, width: 'fill_container', fill: solid(o.ph ? T.faint : T.body) });
    if (o.sel) icon(inp, 'chevron-down', 18, T.sub);
  };
  const narrow = W < 1400;
  field('关键词', narrow ? 220 : 260, '订单编号、客户、型号', { help: 1, search: 1, ph: 1 });
  field('订单状态', narrow ? 120 : 140, '全部', { sel: 1 });
  field('销售日期', narrow ? 210 : 220, '2026-08-20 ~ 2026-11-20', { help: 1 });
  field('收款状态', narrow ? 110 : 130, '全部', { sel: 1 });
  const btns = box(grid, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', alignItems: 'center', gap: 8 });
  const mb = box(btns, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 36, padding: [0, 8], cornerRadius: 8 }); icon(mb, 'sliders-horizontal', 18, P); tx(mb, '更多筛选', { fontWeight: 500, fill: solid(P) });
  const rb = box(btns, { layout: 'horizontal', alignItems: 'center', height: 36, padding: [0, 14], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.st.err[0]) } }); tx(rb, '重置', { fontWeight: 500, fill: solid(T.st.err[0]) });
  primaryBtn(btns, '查询');
  hr(fc);
  const qf = box(fc, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' });
  tx(qf, '快捷筛选', { fontSize: 13, fontWeight: 500, fill: solid(T.sub) });
  const chip = box(qf, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 30, padding: [0, 12], cornerRadius: 999, fill: solid(T.st.neu[1]) });
  tx(chip, '毛利为负', { fontSize: 13, fill: solid(T.body) });

  // 主内容区：表格卡片
  const mc = card(content, { width: 'fill_container', layout: 'vertical', clipContent: true });
  const act = box(mc, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', height: 52, padding: [0, 20] });
  tx(act, '共 128 条', { fontSize: 14, fill: solid(T.sub) });
  const PUW = scrolled === 2 ? 188 : 130;
  const cols = [['订单编号 · 销售日期', 168], ['客户', 200], ['现货/期货', 88], ['型号 · 数量', 196], ['品牌', 96], ['品类', 96], ['合计', 128], ['毛利', 140, 1, 1], ['收款进度', 150, 1], ['订单状态', 100, 1], ['货物', 190], ['采购员', PUW], ['来源单号', 140], ['业务员', 80], ['创建时间', 150], ['创建人', 80], ['更新时间', 150], ['更新人', 80]];
  // 收款进度条颜色照 ReceiptProgress：已到账 成功色、待到账 警告色、其余 主题色
  // 货物照 GoodsBar 与提示：已出运 成功、在途 信息（status-colors 归信息）、待采购 浅灰；「还有型号没下采购订单」是提醒，用警告标签
  const rowsData = [
    { no: 'SO20261119004', d: '2026-11-19', cu: 'Arabian Automation LLC', cs: '老客户 · 阿联酋', stock: '现货', m: '6ES7214-1AG40-0XB0', ms: '共 4 个型号 · 23 件 · ', br: '西门子、AB', ca: 'PLC、电源', amt: 'USD 12,860.00', gp: 'CNY 24,256.00', gr: '26.57% · 未结算', pay: ['部分到账', 'info'], payPct: 0.3, payBar: P, st: ['待采购', 'neu'], gParts: [[20, G.transit], [3, G.pendBuy]], gText: '在途 20 · 待采购 3 / 23 件', gHint: ['在途最早 11-25 到', G.transit], pu: '林熙', un: '1 个型号未指定', src: 'PI20261118003', sa: 'Kevin' },
    { no: 'SO20261118002', d: '2026-11-18', cu: 'Delta Controls S.A.', cs: '新客户 · 智利', stock: '期货', m: 'SV660NS2R8I', ms: '2 件', br: '汇川', ca: '伺服', amt: 'USD 1,980.00', gp: 'CNY 2,310.00', gr: '16.8% · 未结算', pay: ['待到账', 'warn'], payPct: 0, payBar: T.st.warn[0], st: ['待采购', 'neu'], gParts: [[2, G.pendBuy]], gText: '待采购 2 / 2 件', gHint: ['还有型号没下采购订单', 'warnLabel'], pu: '', un: '未指定', src: 'PI20261117001', sa: 'Kevin' },
    { no: 'SO20261115004', d: '2026-11-15', cu: 'Prime Industrial Supply', cs: '老客户 · 美国', stock: '现货', m: '1756-L83E', ms: '共 2 个型号 · 3 件 · ', br: 'AB', ca: 'PLC', amt: 'USD 21,400.00', gp: 'CNY -860.00', gr: '-0.6% · 已结算', pay: ['已到账', 'ok'], payPct: 1, payBar: T.st.ok[0], st: ['已出运', 'info'], gParts: [[3, G.shipped]], gText: '已出运 3 / 3 件', gHint: ['全部出运', G.shipped], pu: '林熙、周宁', un: '', src: '手动创建', sa: 'Lily' },
  ];
  const ROWH = 80;
  // 滚到最右时最后一列紧贴操作列：表格总宽比可视区少 58，补在采购员列上

  const table = box(mc, { width: 'fill_container', layout: 'horizontal' });
  const scroll = box(table, { width: 'fill_container', layout: 'vertical', clipContent: true });
  const fixed = box(table, { width: 92, layout: 'vertical', fill: solid(T.card), effects: [{ type: 'shadow', offsetX: -6, offsetY: 0, blur: 8, spread: -4, color: T.key === 'light' ? 'rgba(145,158,171,0.35)' : 'rgba(0,0,0,0.6)' }] });
  // 表头
  const hdr = box(scroll, { layout: 'horizontal', height: 56, alignItems: 'center', fill: solid(T.head), padding: [0, 0, 0, 20] });
  cols.forEach(([c, w, h, sort], ci) => { const hc = box(hide(ci) ? TRASH : hdr, { width: w, layout: 'horizontal', gap: 4, alignItems: 'center' }); tx(hc, c, { fontWeight: 500, fill: solid(T.ink) }); if (h) icon(hc, 'info', 14, T.sub); if (sort) icon(hc, 'arrow-up-down', 14, T.faint); });
  const fh = box(fixed, { width: 'fill_container', height: 56, layout: 'horizontal', alignItems: 'center', padding: [0, 16], fill: solid(T.head) }); tx(fh, '操作', { fontWeight: 500, fill: solid(T.ink) });
  for (const r of rowsData) {
    const line = () => I(scroll, { type: 'frame', width: 2600, height: 1, fill: solid(T.line) }); line();
    I(fixed, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
    const row = box(scroll, { layout: 'horizontal', height: ROWH, alignItems: 'center', padding: [0, 0, 0, 20] });
    let ci = 0; const cell = (w, o) => box(hide(ci++) ? TRASH : row, Object.assign({ width: w, layout: 'vertical', gap: 3 }, o || {}));
    let c = cell(168); tx(c, r.no, { fontWeight: 600, fontFamily: NUM, fill: solid(P) }); tx(c, r.d, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
    c = cell(200); tx(c, r.cu, { fontFamily: NUM, fill: solid(T.ink) }); tx(c, r.cs, { fontSize: 12, fill: solid(T.sub) });
    c = cell(88); label(c, r.stock, r.stock === '现货' ? T.st.ok : T.st.warn);
    c = cell(196); tx(c, r.m, { fontSize: 13, fontFamily: MONO, fill: solid(T.ink) }); const ms = box(c, { layout: 'horizontal' }); tx(ms, r.ms, { fontSize: 12, fill: solid(T.sub) }); if (r.ms.endsWith(' · ')) tx(ms, '查看', { fontSize: 12, fontWeight: 600, fill: solid(P) });
    c = cell(96); tx(c, r.br, { fontSize: 13, fill: solid(T.body) });
    c = cell(96); tx(c, r.ca, { fontSize: 13, fill: solid(T.body) });
    c = cell(128); tx(c, r.amt, { fontFamily: NUM, fill: solid(T.ink) });
    c = cell(140); tx(c, r.gp, { fontFamily: NUM, fill: solid(r.gp.indexOf('-') >= 0 ? T.st.err[0] : T.ink) }); tx(c, r.gr, { fontSize: 12, fill: solid(T.sub) });
    c = cell(150, { gap: 6 });
    const pb = box(c, { layout: 'horizontal', gap: 8, alignItems: 'center' });
    const bar = box(pb, { width: 100, height: 6, cornerRadius: 3, fill: solid(T.line), layout: 'horizontal', clipContent: true }); if (r.payPct > 0) I(bar, { type: 'frame', width: Math.round(100 * r.payPct), height: 6, fill: solid(r.payBar) });
    tx(pb, Math.round(r.payPct * 100) + '%', { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
    label(c, r.pay[0], T.st[r.pay[1]]);
    c = cell(100); label(c, r.st[0], T.st[r.st[1]]);
    c = cell(190, { gap: 4 });
    const gb = box(c, { width: 160, height: 6, cornerRadius: 3, fill: solid(T.line), layout: 'horizontal', clipContent: true });
    const gTot = r.gParts.reduce((a, [n]) => a + n, 0); for (const [n, col] of r.gParts) I(gb, { type: 'frame', width: Math.round(160 * n / gTot), height: 6, fill: solid(col) });
    tx(c, r.gText, { fontSize: 12, fill: solid(T.sub) });
    if (r.gHint[1] === 'warnLabel') label(c, r.gHint[0], T.st.warn, 'triangle-alert'); else tx(c, r.gHint[0], { fontSize: 12, fill: solid(r.gHint[1]) });
    c = cell(PUW, { gap: 4 }); if (r.pu) tx(c, r.pu, { fill: solid(T.body) }); if (r.un) label(c, r.un, T.st.warn, 'triangle-alert');
    c = cell(140); tx(c, r.src, { fontFamily: r.src.startsWith('PI') ? NUM : CN, fontWeight: r.src.startsWith('PI') ? 600 : 400, fill: solid(r.src.startsWith('PI') ? P : T.sub) });
    c = cell(80); tx(c, r.sa, { fill: solid(T.body) });
    c = cell(150); tx(c, '2026-11-19 14:20:05', { fontSize: 13, fontFamily: NUM, fill: solid(T.body) });
    c = cell(80); tx(c, '林熙', { fill: solid(T.body) });
    c = cell(150); tx(c, '2026-11-20 09:12:41', { fontSize: 13, fontFamily: NUM, fill: solid(T.body) });
    c = cell(80); tx(c, 'johnny', { fill: solid(T.body) });
    const fr = box(fixed, { width: 'fill_container', height: ROWH, layout: 'horizontal', alignItems: 'center', gap: 4, padding: [0, 12] });
    for (const ic of ['eye', 'copy-plus']) { const ib = box(fr, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }); icon(ib, ic, 18, T.sub, false); }
  }
  // 横向滚动条与分页
  const sb = box(mc, { width: 'fill_container', height: 10, layout: 'horizontal', padding: [3, 20], fill: solid(T.card) });
  if (scrolled) box(sb, { width: Math.round((W - 340) * (scrolled === 2 ? 0.55 : 0.3)) });
  I(sb, { type: 'frame', width: Math.round((W - 340) * 0.45), height: 4, cornerRadius: 2, fill: solid(T.faint) });
  hr(mc);
  const ft = box(mc, { width: 'fill_container', height: 60, layout: 'horizontal', alignItems: 'center', gap: 12, padding: [0, 20] });
  tx(ft, '每页条数', { fill: solid(T.body) });
  const ps = box(ft, { width: 90, height: 40, layout: 'horizontal', alignItems: 'center', padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.field) } }); tx(ps, '20', { width: 'fill_container', fontFamily: NUM, fill: solid(T.body) }); icon(ps, 'chevron-down', 18, T.sub);
  const pr = box(ft, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', alignItems: 'center', gap: 4 });
  tx(pr, '1-20 共 128 条', { fontFamily: NUM, fill: solid(T.body) });
  box(pr, { width: 16 });
  for (const [ic, dis] of [['chevron-first', 1], ['chevron-left', 1], ['chevron-right', 0], ['chevron-last', 0]]) { const b = box(pr, { width: 36, height: 36, cornerRadius: 18, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }); icon(b, ic, 20, dis ? T.faint : T.body, false); }
}

function helpState(T, name) {
  const tx = (p, content, o) => { const n = Object.assign({ type: 'text', content, fontSize: 14, fontFamily: CN, fill: solid(T.body) }, o || {}); n.name = 'fill=' + n.fill[0].color; return I(p, n); };
  const box = (p, o) => I(p, Object.assign({ type: 'frame', width: 'fit_content', height: 'fit_content' }, o || {}));
  const icon = (p, n, s, c, inline) => mkIcon(p, n, s, c, inline);
  const hv = I(null, { type: 'frame', name, width: 460, height: 200, layout: 'vertical', gap: 8, padding: [20, 20], fill: solid(T.page) });
  const lb = box(hv, { layout: 'horizontal', gap: 4, alignItems: 'center' });
  tx(lb, '关键词', { fontSize: 13, fontWeight: 500 }); icon(lb, 'info', 14, P);
  const tip = box(hv, Object.assign({ width: 340, layout: 'vertical', gap: 2, padding: [6, 16], cornerRadius: 8, fill: solid('#000000') }, T.key === 'dark' ? { stroke: { thickness: 1, fill: solid('#3A3A3A') } } : {}));
  tx(tip, '可搜：订单编号、PI 编号（不带前缀也能搜到）、客户名称或简称、型号', { lineHeight: 1.6, width: 'fill_container', textGrowth: 'fixed-width', fill: solid('#FFFFFF') });
  tx(tip, '包含匹配，整段文字一起匹配', { lineHeight: 1.6, width: 'fill_container', textGrowth: 'fixed-width', fill: solid('#FFFFFF') });
  const inp = box(hv, { width: 260, height: 40, layout: 'horizontal', gap: 8, alignItems: 'center', padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.field) } });
  icon(inp, 'search', 18, T.faint); tx(inp, '订单编号、客户、型号', { fill: solid(T.faint) });
}

// 状态补充：快捷筛选未选中 / 选中（对照警告标签），操作图标悬停提示
function statesBoard(T, name) {
  const tx = (p, content, o) => { const n = Object.assign({ type: 'text', content, fontSize: 14, fontFamily: CN, fill: solid(T.body) }, o || {}); n.name = 'fill=' + n.fill[0].color; return I(p, n); };
  const box = (p, o) => I(p, Object.assign({ type: 'frame', width: 'fit_content', height: 'fit_content' }, o || {}));
  const icon = (p, n, s, c, inline) => mkIcon(p, n, s, c, inline);
  const root = I(null, { type: 'frame', name, width: 620, height: 230, layout: 'vertical', gap: 18, padding: [20, 20], fill: solid(T.card) });
  const cap = (p, t) => tx(p, t, { fontSize: 12, fill: solid(T.sub), width: 96 });
  // 快捷筛选（PrimeDash Chips：tonal、全圆角、高 30）
  let r = box(root, { layout: 'horizontal', gap: 12, alignItems: 'center' });
  cap(r, '快捷筛选');
  const c1 = box(r, { layout: 'horizontal', alignItems: 'center', height: 30, padding: [0, 12], cornerRadius: 999, fill: solid(T.st.neu[1]) }); tx(c1, '毛利为负', { fontSize: 13, fill: solid(T.body) });
  tx(r, '未选中', { fontSize: 12, fill: solid(T.faint) });
  const c2 = box(r, { layout: 'horizontal', gap: 4, alignItems: 'center', height: 30, padding: [0, 12, 0, 8], cornerRadius: 999, fill: solid(T.soft) }); icon(c2, 'check', 16, P); tx(c2, '毛利为负', { fontSize: 13, fontWeight: 500, fill: solid(P) });
  tx(r, '选中', { fontSize: 12, fill: solid(T.faint) });
  // 对照：警告标签（label chip：圆角 4、高 22、带警告图标）
  r = box(root, { layout: 'horizontal', gap: 12, alignItems: 'center' });
  cap(r, '对照警告标签');
  const wl = box(r, { layout: 'horizontal', gap: 4, alignItems: 'center', padding: [3, 8], cornerRadius: 4, fill: solid(T.st.warn[1]) }); icon(wl, 'triangle-alert', 12, T.st.warn[0]); tx(wl, '未指定', { fontSize: 12, fill: solid(T.st.warn[0]) });
  // 操作图标悬停：圆形浅底 + 提示气泡
  r = box(root, { layout: 'horizontal', gap: 12, alignItems: 'start' });
  cap(r, '操作图标悬停');
  const col = box(r, { layout: 'vertical', gap: 6, alignItems: 'center' });
  const tip = box(col, Object.assign({ layout: 'horizontal', padding: [4, 10], cornerRadius: 6, fill: solid('#000000') }, T.key === 'dark' ? { stroke: { thickness: 1, fill: solid('#3A3A3A') } } : {})); tx(tip, '再下一单', { fontSize: 12, fill: solid('#FFFFFF') });
  const ib = box(col, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.navOn) }); icon(ib, 'copy-plus', 18, T.body, false);
  tx(r, '查看、再下一单只用图标，悬停显示名称；费用订单不显示「再下一单」', { fontSize: 12, fill: solid(T.faint), width: 300, textGrowth: 'fixed-width', lineHeight: 1.5 });
}

draw(LIGHT, 1440, 'A1 销售订单列表 · 浅色 · 1440');
draw(DARK, 1440, 'A2 销售订单列表 · 深色 · 1440');
draw(LIGHT, 1366, 'A3 销售订单列表 · 浅色 · 1366');
draw(LIGHT, 1440, 'A1d 销售订单列表 · 表格右滚 · 浅色', true);
draw(DARK, 1440, 'A2d 销售订单列表 · 表格右滚 · 深色', true);
draw(LIGHT, 1440, 'A1e 销售订单列表 · 表格滚到最右 · 浅色', 2);
helpState(LIGHT, 'A1b 关键词帮助悬停 · 浅色');
helpState(DARK, 'A2b 关键词帮助悬停 · 深色');
statesBoard(LIGHT, 'A1c 快捷筛选与操作悬停 · 浅色');
statesBoard(DARK, 'A2c 快捷筛选与操作悬停 · 深色');
