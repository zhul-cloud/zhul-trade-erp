// 登记到账弹窗原型（照 PrimeDash Dialog 组件）；令牌与组件函数与销售订单详情.js 相同
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


// ———— 登记到账弹窗（照 PrimeDash Dialog：宽 600、圆角 8、标题 18/500、内容内边距 20；字段按项目规范标签在上）
// 字段与文字取自 pages/sales/pi/dialogs.tsx 的 ConfirmReceiptModal、receiptDialogs.tsx 的 PaymentMethodSelect 与 FxEstimateHint
const SCRIM = { light: '#8D9298', dark: '#050505' };
const MODAL_SH = [{ type: 'shadow', offsetX: 0, offsetY: 11, blur: 15, spread: -7, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 24, blur: 38, spread: 3, color: 'rgba(145,158,171,0.14)' }, { type: 'shadow', offsetX: 0, offsetY: 9, blur: 46, spread: 8, color: 'rgba(145,158,171,0.12)' }];

function modalKit(T) {
  const K = kit(T);
  const { tx, box, icon } = K;
  const modal = (p, title, w) => {
    const m = box(p, Object.assign({ width: w || 600, layout: 'vertical', cornerRadius: 8, fill: solid(T.card), effects: T.key === 'light' ? MODAL_SH : [] }, T.stroke ? { stroke: T.stroke } : {}));
    const h = box(m, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', padding: [20, 20, 4, 24] });
    tx(h, title, { fontSize: 18, fontWeight: 500, fill: solid(T.ink), width: 'fill_container' });
    const x = box(h, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }); icon(x, 'x', 18, T.sub, false);
    const b = box(m, { width: 'fill_container', layout: 'vertical', gap: 16, padding: [16, 24, 8, 24] });
    const f = box(m, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 8, padding: [12, 20, 20, 24] });
    return { m, b, f };
  };
  // 字段：标签在上（与搜索区一致），输入框描边、高 40、圆角 8
  const field = (p, label, value, o) => {
    o = o || {};
    const fw = box(p, { width: o.w || 'fill_container', layout: 'vertical', gap: 6 });
    const lb = box(fw, { layout: 'horizontal', gap: 4, alignItems: 'center' });
    if (o.req) tx(lb, '*', { fontSize: 13, fill: solid(T.st.err[0]) });
    tx(lb, label, { fontSize: 13, fontWeight: 500, fill: solid(T.body) });
    if (o.help) icon(lb, 'info', 14, T.sub);
    const inp = box(fw, { width: 'fill_container', height: 40, layout: 'horizontal', gap: 8, alignItems: 'center', padding: [0, 12], cornerRadius: 8, stroke: { thickness: o.focus ? 1.5 : 1, fill: solid(o.focus ? P : T.field) } });
    if (o.prefix) tx(inp, o.prefix, { fontFamily: NUM, fill: solid(T.sub) });
    tx(inp, value, { fontFamily: o.num ? NUM : CN, fill: solid(o.ph ? T.faint : T.ink), width: 'fill_container' });
    if (o.sel) icon(inp, 'chevron-down', 18, T.sub);
    if (o.date) icon(inp, 'calendar', 18, T.sub);
    return fw;
  };
  return Object.assign({}, K, { modal, field });
}

function receiptForm(T, b, o) {
  const MK = modalKit(T); const { tx, box, icon, label, field } = MK;
  o = o || {};
  // 收款概况：合计、已到账、剩余
  const sum = box(b, { width: 'fill_container', layout: 'horizontal', gap: 12, padding: [14, 16], cornerRadius: 8, fill: solid(T.tonal) });
  for (const [k, v] of [['合计', 'USD 12,860.00'], ['已到账', 'USD 3,858.00'], ['剩余', 'USD 9,002.00']]) {
    const c = box(sum, { width: 'fill_container', layout: 'vertical', gap: 2 });
    tx(c, k, { fontSize: 12, fill: solid(T.sub) });
    tx(c, v, { fontSize: 16, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink) });
  }
  if (o.top) o.top(b);
  const g1 = box(b, { width: 'fill_container', layout: 'horizontal', gap: 16, alignItems: 'start' });
  const am = field(g1, '到账金额（银行实收）', o.amount || '9,002.00', { req: 1, help: 1, prefix: 'USD', num: 1, focus: o.focus });
  if (o.amountNote) o.amountNote(am); else { const n = box(am, { layout: 'horizontal', gap: 4, alignItems: 'center' }); icon(n, 'circle-check', 14, T.st.ok[0]); tx(n, '本次到账后结清', { fontSize: 12, fill: solid(T.st.ok[0]) }); }
  field(g1, '到账日期', '2026-11-24', { req: 1, num: 1, date: 1 });
  const g2 = box(b, { width: 'fill_container', layout: 'horizontal', gap: 16, alignItems: 'start' });
  field(g2, '收款账户', (o.account !== undefined ? o.account : '中国银行 ****6688 · 福州事业部'), { req: 1, sel: 1 });
  field(g2, '对应水单（选填）', o.slip || '2026-11-23 USD 9,002.00 · 尾款', { sel: 1, num: 1, ph: o.slipPh });
  // 收错账户的警告跨两栏放在这一行下方，文字原样
  if (o.accountNote) o.accountNote(b);
  const g3 = box(b, { width: 'fill_container', layout: 'horizontal', gap: 16, alignItems: 'start' });
  field(g3, '付款方式', '银行转账', { sel: 1 });
  field(g3, '说明（选填）', '', { ph: 1 });
}

function receiptFooter(T, f, ok, disabled) {
  const MK = modalKit(T); const { tx, box, icon, primaryBtn, textBtn } = MK;
  const note = box(f, { width: 'fill_container', layout: 'horizontal', gap: 6, alignItems: 'center' });
  icon(note, 'lock', 14, T.sub); tx(note, '到账记录保存后不能修改，登记错误时作废后重新登记', { fontSize: 12, fill: solid(T.sub) });
  textBtn(f, '取消', null, T.sub);
  if (disabled) { const d = box(f, { layout: 'horizontal', alignItems: 'center', height: 36, padding: [0, 16], cornerRadius: 8, fill: solid(T.navOn) }); tx(d, ok || '登记', { fontWeight: 500, fill: solid(T.faint) }); }
  else primaryBtn(f, ok || '登记');
}

function modalScreen(T, name) {
  const root = I(null, { type: 'frame', name, width: 1440, height: 900, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(SCRIM[T.key]) });
  const MK = modalKit(T);
  const { b, f } = MK.modal(root, '登记到账');
  receiptForm(T, b);
  receiptFooter(T, f);
}

// 状态：可能收错账户、多收、差额记手续费、帮助悬停
function modalStates(T, name) {
  const root = I(null, { type: 'frame', name, width: 1940, height: 1170, layout: 'horizontal', gap: 32, padding: [32, 32], alignItems: 'start', fill: solid(SCRIM[T.key]) });
  const MK = modalKit(T); const { tx, box, icon, label, primaryBtn, textBtn } = MK;
  // ① 可能收错账户（启用事业部时）：警告条原文保留
  let M = MK.modal(root, '登记到账');
  receiptForm(T, M.b, {
    account: '招商银行 ****2201 · 厦门事业部',
    accountNote: (acc) => {
      const w = box(acc, { width: 'fill_container', layout: 'vertical', gap: 6, padding: [12, 14], cornerRadius: 8, fill: solid(T.st.warn[1]) });
      const h = box(w, { layout: 'horizontal', gap: 8, alignItems: 'center' }); icon(h, 'triangle-alert', 16, T.st.warn[0]); tx(h, '可能收错账户', { fontWeight: 700, fill: solid(T.ink) });
      tx(w, '这个账户属于「厦门事业部」，订单属于「福州事业部」。', { fontSize: 13, fill: solid(T.body), width: 'fill_container', textGrowth: 'fixed-width', lineHeight: 1.5 });
      tx(w, '确认保存后，这笔收款仍计入福州事业部（以订单为准），收款记录会标记「账户与订单不一致」，方便财务事后调账。', { fontSize: 12, fill: solid(T.sub), width: 'fill_container', textGrowth: 'fixed-width', lineHeight: 1.5 });
      const bt = box(w, { layout: 'horizontal', height: 30, alignItems: 'center', padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: solid(P) } }); tx(bt, '改选福州事业部的账户', { fontSize: 13, fontWeight: 500, fill: solid(P) });
    },
  });
  receiptFooter(T, M.f, '确认没收错，继续保存');

  const col = box(root, { layout: 'vertical', gap: 32 });
  // ④ 没有该币种的收款账户 + 没有待确认水单 + 部分到账（差额超过容差）
  M = MK.modal(col, '登记到账');
  receiptForm(T, M.b, {
    amount: '5,000.00', focus: 1, account: '', slip: '没有待确认的水单', slipPh: 1,
    top: (b) => {
      const w = box(b, { width: 'fill_container', layout: 'horizontal', gap: 10, alignItems: 'center', padding: [12, 16], cornerRadius: 8, fill: solid(T.st.warn[1]) });
      icon(w, 'circle-alert', 18, T.st.warn[0]);
      tx(w, '还没有 USD 收款账户，请先在「系统管理 → 收款账户」中添加', { fill: solid(T.key === 'light' ? '#7A5200' : '#FFD27A'), width: 'fill_container', textGrowth: 'fixed-width', lineHeight: 1.5 });
    },
    amountNote: (am) => {
      tx(am, '本次到账后还差 USD 4,002.00', { fontSize: 12, fill: solid(T.sub) });
      const n = box(am, { layout: 'horizontal', gap: 4, alignItems: 'center' }); icon(n, 'info', 12, T.sub); tx(n, '差额不超过 USD 50.00 时可记为手续费', { fontSize: 12, fill: solid(T.sub) });
    },
  });
  receiptFooter(T, M.f, '登记', true);
  // ② 多收：金额下方提示 + 提交时确认框（文字原样）
  M = MK.modal(col, '登记到账');
  receiptForm(T, M.b, {
    amount: '9,102.00', focus: 1,
    amountNote: (am) => { const l = label(am, '多收 USD 100.00，将记为预收', T.st.warn, 'triangle-alert'); },
  });
  receiptFooter(T, M.f);
  // ③ 差额在容差内：可记为手续费
  const col2 = box(root, { layout: 'vertical', gap: 32 });
  M = MK.modal(col2, '登记到账');
  receiptForm(T, M.b, {
    amount: '8,972.00', focus: 1,
    amountNote: (am) => {
      tx(am, '本次到账后还差 USD 30.00', { fontSize: 12, fill: solid(T.sub) });
      const ck = box(am, { layout: 'horizontal', gap: 8, alignItems: 'center' });
      const b = box(ck, { width: 16, height: 16, cornerRadius: 4, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(P) }); icon(b, 'check', 12, '#FFFFFF', false);
      tx(ck, '差额记为银行中转手续费（视为结清）', { fontSize: 13, fill: solid(T.body) });
    },
  });
  receiptFooter(T, M.f);
  // 多收的确认框
  const cf = box(col2, Object.assign({ width: 420, layout: 'vertical', gap: 12, padding: [20, 24], cornerRadius: 8, fill: solid(T.card), effects: T.key === 'light' ? MODAL_SH : [] }, T.stroke ? { stroke: T.stroke } : {}));
  const ch = box(cf, { layout: 'horizontal', gap: 10, alignItems: 'center' }); icon(ch, 'circle-alert', 20, T.st.warn[0]); tx(ch, '多收 USD 100.00，将记为预收', { fontSize: 16, fontWeight: 600, fill: solid(T.ink) });
  tx(cf, '到账金额超过剩余金额，确认继续登记吗？', { fill: solid(T.body), width: 'fill_container', textGrowth: 'fixed-width' });
  const cb = box(cf, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', gap: 8 }); textBtn(cb, '取消', null, T.sub); primaryBtn(cb, '继续登记');
  // 到账金额的帮助悬停
  const tipw = box(col2, { width: 420, layout: 'vertical', gap: 6 });
  const lb = box(tipw, { layout: 'horizontal', gap: 4, alignItems: 'center' }); tx(lb, '到账金额（银行实收）', { fontSize: 13, fontWeight: 500, fill: solid(T.ink) }); icon(lb, 'info', 14, P);
  const tip = box(tipw, Object.assign({ width: 400, layout: 'vertical', padding: [8, 14], cornerRadius: 8, fill: solid('#000000') }, T.key === 'dark' ? { stroke: { thickness: 1, fill: solid('#3A3A3A') } } : {}));
  tx(tip, '人民币先按最近一次结汇的汇率估算，标「未结汇」；结汇后由财务在「结汇管理」登记结汇单，系统按先进先出确定实际人民币。', { fontSize: 13, fill: solid('#FFFFFF'), width: 'fill_container', textGrowth: 'fixed-width', lineHeight: 1.6 });
}

modalScreen(LIGHT, 'C1 登记到账 · 浅色');
modalScreen(DARK, 'C2 登记到账 · 深色');
modalStates(LIGHT, 'C1b 登记到账状态 · 浅色');
