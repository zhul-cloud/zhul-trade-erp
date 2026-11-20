// 工作台原型（照 PrimeDash CRM 看板的列表、指标样式）；令牌与组件函数与销售订单详情.js 相同
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


// ———— 工作台 · 业务员看板（模板 sales：我的待办、我的业绩、询价进度、过期未收款的 PI、我的商机）
// 组件与口径取自 workbench/widget/SalesWidgets.java、dashboard/OverduePiCard.tsx、widgets/index.tsx
function wbShell(T, W, H, name) {
  const { tx, box, icon, card } = kit(T);
  const root = I(null, { type: 'frame', name, width: W, height: H, layout: 'horizontal', gap: 16, padding: [16, 16, 16, 16], fill: solid(T.page), clipContent: true });
  const side = card(root, { width: 244, height: 868, layout: 'vertical', gap: 2, padding: [16, 14] });
  const brand = box(side, { layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 6, 14, 6] });
  const logo = box(brand, { width: 30, height: 30, cornerRadius: 8, fill: solid(P), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  tx(logo, '烛', { fontSize: 15, fontWeight: 700, fill: solid('#FFFFFF') });
  tx(brand, '烛龙ERP', { fontSize: 20, fontWeight: 700, fill: solid(P) });
  const nav = [['layout-dashboard', '工作台', 'on'], ['briefcase', '销售管理', 'grp'], ['shopping-cart', '采购管理', 'grp'], ['warehouse', '仓库管理', 'grp'], ['truck', '物流管理', 'grp'], ['wallet', '财务管理', 'grp'], ['package', '商品管理', 'grp'], ['bar-chart-3', '统计分析', 'grp']];
  for (const [ic, l, k] of nav) {
    const on = k === 'on';
    const row = box(side, { width: 'fill_container', height: 40, layout: 'horizontal', gap: 12, alignItems: 'center', padding: [0, 12, 0, 14], cornerRadius: 8, fill: solid(on ? T.soft : T.card) });
    icon(row, ic, 20, on ? P : T.body);
    tx(row, l, { fontSize: 16, fill: solid(on ? P : T.body), width: 'fill_container' });
    if (k === 'grp') icon(row, 'chevron-right', 18, T.sub);
  }
  const main = box(root, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 16 });
  const top = card(main, { width: 'fill_container', height: 64, layout: 'horizontal', alignItems: 'end', gap: 4, padding: [0, 16] });
  const t = box(top, { height: 'fill_container', layout: 'vertical', justifyContent: 'end' });
  const tt = box(t, { height: 44, layout: 'horizontal', gap: 6, alignItems: 'center', padding: [0, 16] });
  tx(tt, '工作台', { fontWeight: 500, fill: solid(P) });
  I(t, { type: 'frame', width: 'fill_container', height: 2, fill: solid(P) });
  const tools = box(top, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', justifyContent: 'end', alignItems: 'center', gap: 20 });
  icon(tools, T.key === 'light' ? 'sun' : 'moon', 20, T.body, false); icon(tools, 'languages', 20, T.body, false); icon(tools, 'bell', 20, T.body, false);
  const av = box(tools, { width: 36, height: 36, cornerRadius: 18, fill: solid(T.soft), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  tx(av, 'K', { fontWeight: 700, fill: solid(P) });
  return box(main, { width: 'fill_container', layout: 'vertical', gap: 16 });
}

function workbench(T, name, boards) {
  const K = kit(T); const { tx, box, icon, card, label } = K;
  const content = wbShell(T, 1440, 1080, name);
  // 页头：问候；一人多看板时右侧切换（Segmented）
  const head = box(content, { width: 'fill_container', layout: 'horizontal', alignItems: 'center' });
  tx(head, '下午好，Kevin', { fontSize: 22, fontWeight: 600, fill: solid(T.ink), width: 'fill_container' });
  if (boards) {
    const seg = box(head, { layout: 'horizontal', gap: 2, padding: [3, 3], cornerRadius: 8, fill: solid(T.tonal) });
    boards.forEach((b, i) => { const s = box(seg, { layout: 'horizontal', height: 32, alignItems: 'center', padding: [0, 14], cornerRadius: 6, fill: solid(i === 0 ? T.card : T.tonal), effects: i === 0 && T.key === 'light' ? SH : [] }); tx(s, b, { fontSize: 13, fontWeight: i === 0 ? 600 : 400, fill: solid(i === 0 ? T.ink : T.sub) }); });
  }
  const tone = { info: T.st.info, ok: T.st.ok, warn: T.st.warn, err: T.st.err, neu: T.st.neu, pri: [P, T.soft], blue: ['#3B82F6', T.key === 'light' ? '#E7F0FE' : '#172741'] };
  const avatar = (p, ic, tn, size, round) => { const a = box(p, { width: size, height: size, cornerRadius: round ? size / 2 : 8, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(tone[tn][1]) }); icon(a, ic, Math.round(size * 0.47), tone[tn][0], false); return a; };
  const widget = (p, title, o) => {
    o = o || {};
    const c = card(p, { width: 566, layout: 'vertical', gap: 16, padding: [20, 24] });
    const h = box(c, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'center' });
    tx(h, title, { fontSize: 18, fontWeight: 500, fill: solid(T.ink) });
    if (o.sub) tx(h, o.sub, { fontSize: 13, fill: solid(T.sub) });
    if (o.help) icon(h, 'info', 14, T.sub);
    const r = box(h, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end' });
    if (o.more) { const m = box(r, { layout: 'horizontal', gap: 2, alignItems: 'center' }); tx(m, o.more, { fontSize: 13, fontWeight: 500, fill: solid(P) }); icon(m, 'chevron-right', 14, P); }
    return c;
  };
  // 指标：数值 22/700，较上月变化（变好绿、变坏红）或说明
  const metric = (p, l, v, o) => {
    o = o || {};
    // 指标小卡照 PrimeDash 统计块：白底细边框，图标的彩色浅底才清楚
    const m = box(p, { width: 'fill_container', layout: 'vertical', gap: 4, padding: [14, 16], cornerRadius: 8, fill: solid(T.card), stroke: { thickness: 1, fill: solid(T.line) } });
    if (o.ic) { avatar(m, o.ic, o.tn, 36, true); box(m, { width: 4, height: 4 }); }
    const lr = box(m, { layout: 'horizontal', gap: 4, alignItems: 'center' }); tx(lr, l, { fontSize: 13, fill: solid(T.sub) }); if (o.link) icon(lr, 'chevron-right', 12, T.sub);
    tx(m, v, { fontSize: 22, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
    if (o.delta) { const up = o.delta[0] === '+'; const col = (up === (o.better !== false)) ? T.st.ok[0] : T.st.err[0]; const d = box(m, { layout: 'horizontal', gap: 4, alignItems: 'center' }); icon(d, up ? 'trending-up' : 'trending-down', 14, col); tx(d, '较上月 ' + o.delta, { fontSize: 12, fontFamily: NUM, fill: solid(col) }); }
    if (o.hint) tx(m, o.hint, { fontSize: 12, fill: solid(o.hintCol || T.sub) });
    else if (!o.delta) box(m, { width: 10, height: 17 });
  };
  // 两列瀑布流：卡片保持自身高度，按模板顺序放进当前较矮的一列（左：待办、过期 PI；右：业绩、询价进度、商机）
  const grid = box(content, { width: 'fill_container', layout: 'horizontal', gap: 16, alignItems: 'start' });
  const colL = box(grid, { width: 566, layout: 'vertical', gap: 16 });
  const colR = box(grid, { width: 566, layout: 'vertical', gap: 16 });
  const row1 = colL;
  // 我的待办
  let c = widget(row1, '我的待办');
  const todos = [['inbox', 'info', '待确认的询盘', 'AI 解析完成，等你核对型号', 3, 1], ['file-text', 'ok', '可以报价', '采购已回价，还没做报价单', 5, 1], ['wallet', 'pri', 'PI 待收款', '已发给客户，还没付清', 4, 0], ['ship', 'blue', '订单待出运', '货已到齐，等安排交货代', 3, 0], ['alarm-clock', 'err', '售后要更新客户', '已逾期或 1 小时内到期的售后单', 1, 1]];
  // 列表行照 PrimeDash「Crypto Holdings」：无底色，行间分隔线
  const tl = box(c, { width: 'fill_container', layout: 'vertical' });
  todos.forEach(([ic, tn, t, d, n, alert], ti) => {
    if (ti > 0) I(tl, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
    const r = box(tl, { width: 'fill_container', layout: 'horizontal', gap: 12, alignItems: 'center', padding: [12, 0] });
    // 每项一种颜色（PrimeDash 列表行：38、圆角 8、tonal）
    avatar(r, ic === 'ship' ? 'rocket' : ic, tn, 38, false);
    const tc = box(r, { width: 'fill_container', layout: 'vertical', gap: 2 }); tx(tc, t, { fontWeight: 600, fill: solid(T.ink) }); tx(tc, d, { fontSize: 12, fill: solid(T.sub) });
    tx(r, String(n), { fontSize: 22, fontWeight: 700, fontFamily: NUM, fill: solid(alert && n > 0 ? tone[tn][0] : T.ink) });
    icon(r, 'chevron-right', 16, T.faint, false);
  });
  // 我的业绩
  c = widget(colR, '我的业绩', { sub: '本月', help: 1 });
  const pg = box(c, { width: 'fill_container', layout: 'vertical', gap: 12 });
  let pr = box(pg, { width: 'fill_container', layout: 'horizontal', gap: 12 });
  metric(pr, '成交额', 'CNY 412,860.00', { delta: '+9.6%', ic: 'badge-dollar-sign', tn: 'pri' });
  metric(pr, '毛利', 'CNY 87,940.00', { delta: '+6.1%', ic: 'piggy-bank', tn: 'ok' });
  pr = box(pg, { width: 'fill_container', layout: 'horizontal', gap: 12 });
  metric(pr, '实收回款', 'CNY 386,420.00', { delta: '-4.3%', ic: 'landmark', tn: 'info' });
  metric(pr, '成交订单', '26', { delta: '+18.2%', ic: 'shopping-bag', tn: 'blue' });

  // 询价进度
  c = widget(colR, '询价进度', { help: 1 });
  pr = box(c, { width: 'fill_container', layout: 'horizontal', gap: 12 });
  metric(pr, '等采购回价的型号', '14', { link: 1, hint: '来自 4 张询盘', ic: 'hourglass', tn: 'info' });
  metric(pr, '已过报价截止', '1', { hint: '截止日期已过，还在询价', hintCol: T.st.err[0], ic: 'calendar-x', tn: 'err' });
  // 过期未收款的 PI
  c = widget(colL, '过期未收款的 PI', { more: '查看全部' });
  const sm = box(c, { layout: 'horizontal', gap: 8, alignItems: 'end' });
  tx(sm, '3', { fontSize: 22, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
  tx(sm, '张 · 合计 USD 18,600.00、EUR 2,400.00', { fontSize: 13, fontFamily: NUM, fill: solid(T.sub) });
  const pis = [['PI20261102006', 'Delta Controls S.A.', 'USD 9,800.00', 18], ['PI20261105002', 'Nordic Automation AB', 'EUR 2,400.00', 12], ['PI20261109004', 'Prime Industrial Supply', 'USD 8,800.00', 6]];
  const lst = box(c, { width: 'fill_container', layout: 'vertical' });
  pis.forEach(([no, cu, amt, d], i) => {
    if (i > 0) I(lst, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
    const r = box(lst, { width: 'fill_container', height: 48, layout: 'horizontal', gap: 12, alignItems: 'center' });
    tx(r, no, { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(P) });
    tx(r, cu, { fontSize: 13, fontFamily: NUM, fill: solid(T.body), width: 'fill_container' });
    tx(r, amt, { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
    label(r, '已过期 ' + d + ' 天', T.st.err);
  });

  // 我的商机（半宽，右侧留空与代码一致）
  c = widget(colR, '我的商机', { sub: '本月' });
  pr = box(c, { width: 'fill_container', layout: 'horizontal', gap: 12 });
  metric(pr, '本月新增商机', '12', { link: 1, ic: 'user-plus', tn: 'pri' });
  metric(pr, '有效商机', '8', { hint: '有效率 66.7%', ic: 'badge-check', tn: 'ok' });
  metric(pr, '赢单', '2', { ic: 'trophy', tn: 'blue' });
}

workbench(LIGHT, 'D1 工作台 · 业务员 · 浅色');
workbench(DARK, 'D2 工作台 · 业务员 · 深色');

// 状态补充：一人多看板时的切换器；过期 PI 为 0
function wbStates(T, name) {
  const K = kit(T); const { tx, box, icon, card } = K;
  const root = I(null, { type: 'frame', name, width: 1196, height: 330, layout: 'vertical', gap: 24, padding: [24, 24], fill: solid(T.page) });
  const head = box(root, { width: 'fill_container', layout: 'horizontal', alignItems: 'center' });
  tx(head, '下午好，Kevin', { fontSize: 22, fontWeight: 600, fill: solid(T.ink), width: 'fill_container' });
  const seg = box(head, { layout: 'horizontal', gap: 2, padding: [3, 3], cornerRadius: 8, fill: solid(T.tonal) });
  ['业务负责人', '业务员'].forEach((b, i) => { const on = i === 1; const s = box(seg, { layout: 'horizontal', height: 32, alignItems: 'center', padding: [0, 14], cornerRadius: 6, fill: solid(on ? T.card : T.tonal), effects: on && T.key === 'light' ? SH : [] }); tx(s, b, { fontSize: 13, fontWeight: on ? 600 : 400, fill: solid(on ? T.ink : T.sub) }); });
  const c = card(root, { width: 566, layout: 'vertical', gap: 12, padding: [20, 24] });
  tx(c, '过期未收款的 PI', { fontSize: 18, fontWeight: 500, fill: solid(T.ink) });
  // 空状态：卡片里直接一行字，不再套灰底框（与费用订单、出库单等空状态一致）
  const e = box(c, { layout: 'horizontal', gap: 8, alignItems: 'center' });
  icon(e, 'circle-check', 16, T.st.ok[0]); tx(e, '没有过期未收款的 PI', { fill: solid(T.sub) });
}
wbStates(LIGHT, 'D1b 工作台状态 · 浅色');
// 涨跌颜色（每人自选，和主题色放在一起）：颜色只表示方向，上涨一种颜色、下跌一种颜色；盈亏同理
function deltaColors(T, name) {
  const K = kit(T); const { tx, box, icon, card } = K;
  const root = I(null, { type: 'frame', name, width: 1196, height: 250, layout: 'horizontal', gap: 24, padding: [24, 24], fill: solid(T.page) });
  const opts = [['涨绿跌红（默认）', T.st.ok[0], T.st.err[0], 1], ['涨红跌绿', T.st.err[0], T.st.ok[0], 0]];
  for (const [t, upC, downC, on] of opts) {
    const c = card(root, { width: 'fill_container', layout: 'vertical', gap: 14, padding: [20, 24], stroke: on ? { thickness: 1.5, fill: solid(P) } : (T.stroke || undefined) });
    const h = box(c, { layout: 'horizontal', gap: 8, alignItems: 'center' });
    const rd = box(h, { width: 18, height: 18, cornerRadius: 9, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', stroke: { thickness: 1.5, fill: solid(on ? P : T.faint) } }); if (on) I(rd, { type: 'ellipse', width: 8, height: 8, fill: solid(P) });
    tx(h, t, { fontWeight: 600, fill: solid(T.ink) });
    const r = box(c, { width: 'fill_container', layout: 'horizontal', gap: 12 });
    for (const [l, v, d, up] of [['实收回款', 'CNY 386,420.00', '+12.5%', 1], ['费用', 'CNY 42,100.00', '+6.0%', 1], ['成交订单', '26', '-3.7%', 0]]) {
      const m = box(r, { width: 'fill_container', layout: 'vertical', gap: 4, padding: [12, 14], cornerRadius: 8, fill: solid(T.tonal) });
      tx(m, l, { fontSize: 12, fill: solid(T.sub) }); tx(m, v, { fontSize: 18, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
      const col = up ? upC : downC; const dd = box(m, { layout: 'horizontal', gap: 4, alignItems: 'center' }); icon(dd, up ? 'trending-up' : 'trending-down', 14, col); tx(dd, '较上月 ' + d, { fontSize: 12, fontFamily: NUM, fill: solid(col) });
    }
    const pl = box(c, { layout: 'horizontal', gap: 8, alignItems: 'center' }); tx(pl, '盈亏同理：', { fontSize: 12, fill: solid(T.sub) }); tx(pl, '毛利 CNY 24,256.00', { fontSize: 12, fontWeight: 600, fontFamily: NUM, fill: solid(upC) }); tx(pl, '毛利 CNY -860.00', { fontSize: 12, fontWeight: 600, fontFamily: NUM, fill: solid(downC) });
  }
}
deltaColors(LIGHT, 'D1c 涨跌颜色 · 浅色');

