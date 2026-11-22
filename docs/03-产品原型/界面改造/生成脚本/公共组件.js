// 公共组件原型（任务 9.1 整改，design-review.md C1–C8、D1–D14）：数字卡组、列表主卡（国旗、系统、操作列、行内提醒、分页）、空状态、更多菜单、危险确认、客户卡
// 令牌与组件函数沿用工作台.js；数字卡照 PrimeDash CRM「Statistics」：分组透明、每张卡独立浮起（浅色三层阴影无边框，深色边框）
// 第二版按 UI 设计师评审修改：带侧栏按真实内容区宽度画、表格首列和操作列固定、行高 60、对比度、对话框遮罩与阴影
const CN = 'Noto Sans SC';
const NUM = 'Public Sans';
const solid = (c) => [{ type: 'solid', color: c }];
const P = '#FDA92D';
// 同一行卡片等高（照 PrimeDash：同一行的卡片高度一致）；数字卡 = 内边距 40 + 名称行 38 + 8 + 数值 30 + 4 + 说明行 24
const CARD_H = 144;
const MINUS = '−';
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 3, blur: 1, spread: -2, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 2, blur: 2, spread: 0, color: 'rgba(145,158,171,0.14)' }, { type: 'shadow', offsetX: 0, offsetY: 1, blur: 5, spread: 0, color: 'rgba(145,158,171,0.12)' }];
// 悬停：Material elevation-8（elevation-4 看不出变化）
const SH_HOVER = [{ type: 'shadow', offsetX: 0, offsetY: 5, blur: 5, spread: -3, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 8, blur: 10, spread: 1, color: 'rgba(145,158,171,0.14)' }, { type: 'shadow', offsetX: 0, offsetY: 3, blur: 14, spread: 2, color: 'rgba(145,158,171,0.12)' }];
// 对话框：Material elevation-24
const SH_DIALOG = [{ type: 'shadow', offsetX: 0, offsetY: 11, blur: 15, spread: -7, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 24, blur: 38, spread: 3, color: 'rgba(145,158,171,0.14)' }, { type: 'shadow', offsetX: 0, offsetY: 9, blur: 46, spread: 8, color: 'rgba(145,158,171,0.12)' }];
const LIGHT = { key: 'light', page: '#F9FAFB', card: '#FFFFFF', head: '#F4F6F8', line: '#E5E8EB', field: '#D4D6D9', ink: '#212B36', body: '#3E4750', sub: '#637381', faint: '#919EAB', soft: '#FEF5E6', tonal: '#F1F3F5', hover: '#FFFFFF', hoverLine: null, rowHover: '#F6F7F8', control: '#D3D5D8', danger: '#B71D18', mask: 'rgba(33,43,54,0.5)', menu: '#FFFFFF', menuHover: '#F6F7F8', shadow: SH, stroke: null,
  st: { ok: ['#0D7A4A', '#E5F8EC'], warn: ['#9A5B00', '#FFF5E0'], info: ['#006C9C', '#E0F6FA'], err: ['#B71D18', '#FFEBE6'], neu: ['#56636F', '#ECEEF0'] },
  fam: { blue: ['#3B82F6', '#E7F0FE'], purple: ['#8B5CF6', '#F1EBFE'], cyan: ['#1F7A76', '#E6F3F3'], gray: ['#637381', '#ECEEF0'] } };
const DARK = { key: 'dark', page: '#0A0A0A', card: '#1A1A1A', head: '#222222', line: '#2A2A2A', field: '#4A4A4A', ink: '#F5F5F5', body: '#E0E0E0', sub: '#A0A0A0', faint: '#6E6E6E', soft: '#3E311D', tonal: '#232323', hover: '#222222', hoverLine: '#3A3A3A', rowHover: '#222222', control: '#4A4A4A', danger: '#D63B1F', mask: 'rgba(0,0,0,0.7)', menu: '#222222', menuHover: '#2C2C2C', shadow: [], stroke: { thickness: 1, fill: solid('#2A2A2A') },
  st: { ok: ['#22C55E', '#1B3525'], warn: ['#FFAB00', '#3F3116'], info: ['#00B8D9', '#163339'], err: ['#FF6A47', '#3F241E'], neu: ['#A0A0A0', '#2F2F2F'] },
  fam: { blue: ['#3B82F6', '#172741'], purple: ['#8B5CF6', '#2C253D'], cyan: ['#2F9E99', '#1D2F2E'], gray: ['#A0A0A0', '#2F2F2F'] } };

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
  const iconBlock = (p, ic, tone, size) => { const s = size || 38; const a = box(p, { width: s, height: s, cornerRadius: 8, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(tone[1]) }); icon(a, ic, Math.round(s * 0.47), tone[0], false); return a; };
  const link = (p, t, o) => { const m = box(p, { layout: 'horizontal', gap: 2, alignItems: 'center' }); tx(m, t, Object.assign({ fontSize: 13, fontWeight: 500, fill: solid(P) }, o || {})); icon(m, 'chevron-right', 14, P); return m; };
  const cap = (p, t) => tx(p, t, { fontSize: 13, fontWeight: 600, fill: solid(T.faint) });
  const sp = (p, h) => box(p, { width: 1, height: h });
  return { tx, box, icon, hr, card, label, iconBlock, link, cap, sp };
}

// 国旗（原型用色块画几面简单的旗；实现用 flag-icons 按 ISO 代码取 SVG，缺代码时显示 globe）：16×12，圆角 2，内描边 1px，和文字间距 4
function flag(T, p, code) {
  const K = kit(T);
  const W = 16; const H = 12;
  const f = K.box(p, { width: W, height: H, cornerRadius: 2, clipContent: true, layout: code === 'DE' ? 'vertical' : 'horizontal', stroke: { thickness: 1, fill: solid(T.key === 'light' ? 'rgba(33,43,54,0.12)' : 'rgba(255,255,255,0.16)') } });
  const bands = { DE: ['#000000', '#DD0000', '#FFCE00'], FR: ['#0055A4', '#FFFFFF', '#EF4135'], IT: ['#009246', '#FFFFFF', '#CE2B37'] }[code];
  if (bands) {
    for (const c of bands) I(f, { type: 'frame', width: code === 'DE' ? W : (c === bands[1] ? 6 : 5), height: code === 'DE' ? 4 : H, fill: solid(c) });
    return f;
  }
  const jp = K.box(f, { width: W, height: H, fill: solid('#FFFFFF'), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  I(jp, { type: 'ellipse', width: 6, height: 6, fill: solid('#BC002D') });
  return f;
}
const country = (T, p, code, name, o) => { const K = kit(T); const r = K.box(p, { layout: 'horizontal', gap: 4, alignItems: 'center' }); flag(T, r, code); K.tx(r, name, Object.assign({ fontSize: 12, fill: solid(T.sub) }, o || {})); return r; };

// 数字卡（高 144）：图标块在左上（先认出是哪类指标，照参考站阅读顺序）+ 名称 14/500 紧贴右侧 → 10 → 数值（整行宽、与图标左缘对齐，CNY 弱化）→ 4 → 说明行（固定高 24，同排等高）
function mkMetric(T, p, l, v, ic, fam, o) {
  const { tx, box, icon, label, iconBlock, sp } = kit(T);
  o = o || {};
  const hov = !!o.hover;
  const st = T.key === 'light' ? { effects: hov ? SH_HOVER : SH } : { stroke: { thickness: 1, fill: solid(hov ? T.hoverLine : T.line) } };
  const m = box(p, Object.assign({ width: 'fill_container', height: CARD_H, layout: 'vertical', padding: [20, 20], cornerRadius: 10, fill: solid(hov ? T.hover : T.card) }, st));
  const top = box(m, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 12 });
  iconBlock(top, ic, o.alert ? T.st[o.alert] : (T.fam[fam] || T.st[fam]));
  const lr = box(top, { width: 'fill_container', layout: 'horizontal', gap: hov ? 4 : 2, alignItems: 'center' });
  tx(lr, l, { fontSize: 14, fontWeight: 500, fill: solid(T.body) });
  if (o.link) icon(lr, 'chevron-right', 14, T.sub);
  sp(m, 10);
  const vr = box(m, { layout: 'horizontal', gap: 4, alignItems: 'end' });
  if (v.startsWith('CNY ')) {
    const cw = box(vr, { layout: 'vertical', padding: [0, 0, 3, 0] }); tx(cw, 'CNY', { fontSize: 14, fontWeight: 600, fontFamily: NUM, lineHeight: 1.25, fill: solid(T.sub) });
    tx(vr, v.slice(4), { fontSize: 22, fontWeight: 700, fontFamily: NUM, lineHeight: 1.25, fill: solid(T.ink) });
  } else tx(vr, v, { fontSize: 22, fontWeight: 700, fontFamily: NUM, lineHeight: 1.25, fill: solid(T.ink) });
  sp(m, 4);
  const hl = box(m, { width: 'fill_container', height: 24, layout: 'horizontal', alignItems: 'center' });
  if (o.alertText) label(hl, o.alertText, T.st[o.alert], 'triangle-alert');
  else if (o.delta) { const up = o.delta[0] === '+'; const col = up ? T.st.err[0] : T.st.ok[0]; const d = box(hl, { layout: 'horizontal', gap: 4, alignItems: 'center' }); icon(d, up ? 'trending-up' : 'trending-down', 14, col); tx(d, '较上月 ' + o.delta, { fontSize: 12, fontFamily: NUM, fill: solid(col) }); }
  else if (o.hint !== '') tx(hl, o.hint || '上月无数据', { fontSize: 12, fill: solid(T.sub) });
  return m;
}

// 真实宽度：侧栏 244 + 外边距 16，1440 下内容区 1148
function shell(T, name, H, gap) {
  const K = kit(T); const { tx, box, icon, card } = K;
  const root = I(null, { type: 'frame', name, width: 1440, height: H || 1760, layout: 'horizontal', gap: 16, padding: [16, 16, 32, 16], fill: solid(T.page), alignItems: 'start' });
  const side = card(root, { width: 244, height: 'fill_container', layout: 'vertical', gap: 2, padding: [16, 14] });
  const brand = box(side, { layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 6, 14, 6] });
  const logo = box(brand, { width: 30, height: 30, cornerRadius: 8, fill: solid(P), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  tx(logo, '烛', { fontSize: 15, fontWeight: 700, fill: solid('#FFFFFF') });
  tx(brand, '烛龙ERP', { fontSize: 20, fontWeight: 700, fill: solid(P) });
  for (const [ic, l, on] of [['layout-dashboard', '工作台', 1], ['briefcase', '销售管理'], ['shopping-cart', '采购管理'], ['warehouse', '仓库管理'], ['wallet', '财务管理'], ['package', '商品管理']]) {
    const row = box(side, { width: 'fill_container', height: 40, layout: 'horizontal', gap: 12, alignItems: 'center', padding: [0, 12, 0, 14], cornerRadius: 8, fill: solid(on ? T.soft : T.card) });
    icon(row, ic, 20, on ? P : T.body); tx(row, l, { fontSize: 16, fill: solid(on ? P : T.body), width: 'fill_container' });
  }
  return box(root, { width: 'fill_container', layout: 'vertical', gap: gap === undefined ? 28 : gap });
}

function board(T, name) {
  const K = kit(T); const { tx, box, icon, hr, card, label, iconBlock, link, cap, sp } = K;
  const root = shell(T, name);

  // ———— 1 数字卡组：标题行落在页面底上，卡片独立浮起；第一张为悬停态
  cap(root, '数字卡组 · MetricGroup（第一张为悬停态）');
  const g1 = box(root, { width: 'fill_container', layout: 'vertical', gap: 12 });
  const gh = box(g1, { width: 'fill_container', height: 28, layout: 'horizontal', gap: 8, alignItems: 'center' });
  tx(gh, '经营概览', { fontSize: 18, fontWeight: 500, fill: solid(T.ink) });
  tx(gh, '本月 · 人民币', { fontSize: 13, fill: solid(T.sub) });
  icon(gh, 'info', 14, T.sub);
  const ghr = box(gh, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end' }); link(ghr, '经营统计');
  const mrow = box(g1, { width: 'fill_container', layout: 'horizontal', gap: 16 });
  const metric = (...args) => mkMetric(T, ...args);
  metric(mrow, '成交额', 'CNY 412,860.00', 'handshake', 'blue', { link: 1, delta: '+9.6%', hover: 1 });
  metric(mrow, '毛利', 'CNY 87,940.00', 'coins', 'purple', { link: 1, delta: '+6.1%' });
  metric(mrow, '毛利率', '21.30%', 'percent', 'purple', { delta: MINUS + '0.8 个百分点' });
  metric(mrow, '实收回款', 'CNY 386,420.00', 'hand-coins', 'cyan', { delta: MINUS + '4.3%' });
  metric(mrow, '费用', 'CNY 42,100.00', 'receipt-text', 'gray', {});

  // 资金（半宽）与风险提醒（半宽、全为 0 收成一行）；两列标题对齐
  const two = box(root, { width: 'fill_container', layout: 'horizontal', gap: 16, alignItems: 'start' });
  const group = (p, title, period, right) => {
    const g = box(p, { width: 'fill_container', layout: 'vertical', gap: 12 });
    const h = box(g, { width: 'fill_container', height: 28, layout: 'horizontal', gap: 8, alignItems: 'center' });
    tx(h, title, { fontSize: 18, fontWeight: 500, fill: solid(T.ink) });
    if (period) tx(h, period, { fontSize: 13, fill: solid(T.sub) });
    const r = box(h, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end' });
    if (right) tx(r, right, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
    return g;
  };
  const g2 = group(two, '资金', '截至今天', '未结汇外币：没有待结汇的外币');
  const g2r = box(g2, { width: 'fill_container', layout: 'horizontal', gap: 16 });
  metric(g2r, '应收未收', 'CNY 7,150.00', 'file-clock', 'cyan', { link: 1, hint: '没有过期未收款' });
  metric(g2r, '本月到期应付', 'CNY 19,000.00', 'calendar-clock', 'gray', { link: 1, alert: 'err', alertText: '其中已逾期 CNY 19,000.00' });
  // 风险提醒：2×2 小格（32 图标 + 名称 + 数量），全为 0 时四格灰色 0、标题旁「全部正常」，有风险时位置不变、数量用错误色可点
  const g3 = group(two, '风险提醒', null, '全部正常 · 截至 16:40');
  const rk = card(g3, { width: 'fill_container', height: CARD_H, layout: 'vertical', gap: 8, padding: [16, 16] });
  for (const pair of [[['file-clock', '过期未收款 PI'], ['truck', '超期未到货']], [['package-x', '到货差异'], ['trending-down', '负毛利订单']]]) {
    const rr = box(rk, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', gap: 8 });
    for (const [ic, nm] of pair) {
      const cell = box(rr, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 12], cornerRadius: 8, fill: solid(T.tonal) });
      const ib = box(cell, { width: 28, height: 28, cornerRadius: 6, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.card) }); icon(ib, ic, 16, T.sub, false);
      tx(cell, nm, { fontSize: 13, fill: solid(T.body), width: 'fill_container' });
      tx(cell, '0', { fontSize: 16, fontWeight: 700, fontFamily: NUM, fill: solid(T.faint) });
    }
  }

  // ———— 2 列表主卡：首列、操作列固定，中间横向滚动（真实内容区放不下 8 列）
  cap(root, '列表主卡 · CountryText / UserName / RowActions / RowAlert / TablePager（第 3 行为悬停态）');
  const mc = card(root, { width: 'fill_container', layout: 'vertical', clipContent: true });
  const act = box(mc, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', height: 52, padding: [0, 20] });
  tx(act, '共 236 条', { fontSize: 14, fill: solid(T.sub) });
  const ROWH = 60; const HH = 52;
  // 询盘状态与颜色取自 inquiry/shared/constants.ts STATUS_META，按 status-colors 归类；「已过报价截止」取自工作台询价进度
  const rows = [
    { no: 'INQ20261121004', d: '2026-11-21', cu: 'Müller Automation GmbH', cs: '老客户', cc: 'DE', cn: '德国', ct: 'Tom Becker', st: ['待确认', 'warn'], src: 'OPP20261120002', cb: '林熙', ub: '系统' },
    { no: 'INQ20261120007', d: '2026-11-20', cu: 'Électro Industrie SARL', cs: '新客户', cc: 'FR', cn: '法国', ct: 'Claire Dubois', st: ['询价中', 'info'], alert: ['已过报价截止', 'err'], src: '', cb: 'Kevin', ub: 'Kevin' },
    { no: 'INQ20261119003', d: '2026-11-19', cu: 'Kansai Robotics Co., Ltd.', cs: '老客户', cc: 'JP', cn: '日本', ct: 'Sato Ken', st: ['可报价', 'ok'], src: 'OPP20261118005', cb: '系统', ub: '周宁' },
    { no: 'INQ20261118001', d: '2026-11-18', cu: 'Automazione Rossi S.r.l.', cs: '老客户', cc: 'IT', cn: '意大利', ct: 'Marco Rossi', st: ['已成交', 'ok'], src: '', cb: 'Lily', ub: 'Lily' },
  ];
  const tbl = box(mc, { width: 'fill_container', layout: 'horizontal' });
  const edge = (dx) => (T.key === 'light' ? { effects: [{ type: 'shadow', offsetX: dx, offsetY: 0, blur: 8, spread: -2, color: 'rgba(145,158,171,0.32)' }] } : {});
  const fl = box(tbl, Object.assign({ width: 186, layout: 'vertical', fill: solid(T.card) }, edge(4)));
  if (T.key === 'dark') I(tbl, { type: 'frame', width: 1, height: 'fill_container', fill: solid(T.line) });
  const sc = box(tbl, { width: 'fill_container', layout: 'vertical', clipContent: true });
  if (T.key === 'dark') I(tbl, { type: 'frame', width: 1, height: 'fill_container', fill: solid(T.line) });
  const fr = box(tbl, Object.assign({ width: 136, layout: 'vertical', fill: solid(T.card) }, edge(-4)));
  const flh = box(fl, { width: 'fill_container', height: HH, layout: 'horizontal', alignItems: 'center', padding: [0, 0, 0, 20], fill: solid(T.head) }); tx(flh, '询盘编号 · 日期', { fontWeight: 500, fill: solid(T.ink) });
  const mid = [['客户', 250], ['状态', 130], ['来源单号', 150], ['创建时间', 150], ['创建人', 72], ['更新时间', 150], ['更新人', 72]];
  const hdr = box(sc, { width: 'fill_container', height: HH, layout: 'horizontal', alignItems: 'center', fill: solid(T.head), padding: [0, 0, 0, 16] });
  mid.forEach(([c, w]) => { const hc = box(hdr, { width: w }); tx(hc, c, { fontWeight: 500, fill: solid(T.ink) }); });
  const frh = box(fr, { width: 'fill_container', height: HH, layout: 'horizontal', alignItems: 'center', padding: [0, 12], fill: solid(T.head) }); tx(frh, '操作', { fontWeight: 500, fill: solid(T.ink) });
  rows.forEach((r, ri) => {
    const bg = ri === 2 ? T.rowHover : T.card;
    for (const col of [fl, sc, fr]) I(col, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
    // 首列：左侧 3px 色条（行内提醒），单号 14/600 + 日期 12
    const a = box(fl, { width: 'fill_container', height: ROWH, layout: 'horizontal', alignItems: 'center', fill: solid(bg) });
    I(a, { type: 'frame', width: 3, height: ROWH, fill: solid(r.alert ? T.st[r.alert[1]][0] : bg) });
    const ac = box(a, { layout: 'vertical', gap: 2, padding: [0, 0, 0, 17] }); tx(ac, r.no, { fontWeight: 600, fontFamily: NUM, fill: solid(P) }); tx(ac, r.d, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
    const row = box(sc, { width: 'fill_container', layout: 'horizontal', height: ROWH, alignItems: 'center', padding: [0, 0, 0, 16], fill: solid(bg) });
    let c = box(row, { width: 250, layout: 'vertical', gap: 2 }); tx(c, r.cu, { fontFamily: NUM, fill: solid(T.ink) });
    const cl = box(c, { layout: 'horizontal', gap: 4, alignItems: 'center' }); tx(cl, r.cs + ' ·', { fontSize: 12, fill: solid(T.sub) }); country(T, cl, r.cc, r.cn); tx(cl, '· ' + r.ct, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
    c = box(row, { width: 130, layout: 'vertical', gap: 2 }); label(c, r.st[0], T.st[r.st[1]]); if (r.alert) label(c, r.alert[0], T.st[r.alert[1]], 'triangle-alert');
    c = box(row, { width: 150 }); tx(c, r.src || '—', { fontFamily: NUM, fontWeight: r.src ? 500 : 400, fill: solid(r.src ? P : T.faint) });
    c = box(row, { width: 150 }); tx(c, r.d + ' 10:2' + ri + ':05', { fontSize: 13, fontFamily: NUM, fill: solid(T.body) });
    c = box(row, { width: 72 }); tx(c, r.cb, { fontSize: 13, fill: solid(r.cb === '系统' ? T.sub : T.body) });
    c = box(row, { width: 150 }); tx(c, '2026-11-21 16:4' + ri + ':12', { fontSize: 13, fontFamily: NUM, fill: solid(T.body) });
    c = box(row, { width: 72 }); tx(c, r.ub, { fontSize: 13, fill: solid(r.ub === '系统' ? T.sub : T.body) });
    // 操作：32×32 按钮、间距 8、左右 12；第一行的「⋯」为展开态
    const ops = box(fr, { width: 'fill_container', height: ROWH, layout: 'horizontal', alignItems: 'center', gap: 8, padding: [0, 12], fill: solid(bg) });
    ['eye', 'pencil', 'ellipsis'].forEach((ic, k) => { const on = ri === 0 && k === 2; const ib = box(ops, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(on ? T.tonal : bg) }); icon(ib, ic, 18, T.sub, false); });
  });
  // 细滚动条（深色用深色轨道），只在中间可滚动区下方
  const sb = box(mc, { width: 'fill_container', height: 14, layout: 'horizontal', alignItems: 'center', padding: [0, 140, 0, 190] });
  const trk = box(sb, { width: 'fill_container', height: 6, cornerRadius: 3, fill: solid(T.key === 'light' ? '#F1F3F5' : '#232323'), layout: 'horizontal' });
  I(trk, { type: 'frame', width: 560, height: 6, cornerRadius: 3, fill: solid(T.key === 'light' ? '#C4CDD5' : '#4A4A4A') });
  hr(mc);
  // 分页：左每页条数；右页码（当前页主题浅底 + 正文色字）、上一页下一页、跳页；控件统一高 32
  const ft = box(mc, { width: 'fill_container', height: 56, layout: 'horizontal', alignItems: 'center', gap: 8, padding: [0, 20] });
  tx(ft, '每页', { fill: solid(T.body) });
  const ps = box(ft, { width: 88, height: 32, layout: 'horizontal', alignItems: 'center', padding: [0, 10], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.field) } }); tx(ps, '20 条', { width: 'fill_container', fontFamily: NUM, fill: solid(T.body) }); icon(ps, 'chevron-down', 16, T.sub);
  const pr = box(ft, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', alignItems: 'center', gap: 4 });
  const pbtn = (content, o) => { o = o || {}; const b = box(pr, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(o.on ? T.soft : T.card) }); if (o.ic) icon(b, o.ic, 18, o.dis ? T.faint : T.body, false); else tx(b, content, { fontSize: 14, fontFamily: NUM, fontWeight: o.on ? 600 : 400, fill: solid(o.on ? (T.key === 'light' ? T.ink : P) : T.body) }); };
  pbtn('', { ic: 'chevron-left' }); pbtn('1'); pbtn('2', { on: 1 }); pbtn('3'); pbtn('4'); pbtn('5'); pbtn('', { ic: 'ellipsis', dis: 1 }); pbtn('12'); pbtn('', { ic: 'chevron-right' });
  box(pr, { width: 12 });
  tx(pr, '跳至', { fill: solid(T.body) });
  box(pr, { width: 52, height: 32, cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.field) } });
  tx(pr, '页', { fill: solid(T.body) });

  // ———— 3 空状态（整宽表格）
  cap(root, '空状态 · EmptyState（首次为空引导去做事；筛选后为空写「没有符合条件的…」并给「清空筛选」）');
  const ec = card(root, { width: 'fill_container', layout: 'vertical', clipContent: true });
  const eh = box(ec, { width: 'fill_container', height: 52, layout: 'horizontal', alignItems: 'center', padding: [0, 20], fill: solid(T.head) });
  for (const [t, w] of [['月份', 160], ['凭证数', 120], ['借方合计', 180], ['贷方合计', 180], ['状态', 'fill_container']]) { const hc = box(eh, { width: w }); tx(hc, t, { fontWeight: 500, fill: solid(T.ink) }); }
  const eb = box(ec, { width: 'fill_container', layout: 'vertical', alignItems: 'center', padding: [40, 20, 44, 20] });
  const ei = box(eb, { width: 56, height: 56, cornerRadius: 28, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.tonal) }); icon(ei, 'file-text', 26, T.sub, false);
  sp(eb, 16); tx(eb, '还没有业务凭证', { fontSize: 16, fontWeight: 600, fill: solid(T.ink) });
  sp(eb, 4); tx(eb, '库存月结结账后自动生成', { fontSize: 13, fill: solid(T.sub) });
  sp(eb, 16);
  const eba = box(eb, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 32, padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.control) } }); tx(eba, '去库存月结', { fontSize: 13, fontWeight: 500, fill: solid(T.ink) }); icon(eba, 'arrow-right', 14, T.sub);

  // ———— 4 更多菜单、危险确认（带遮罩）、客户卡
  cap(root, '更多菜单（第一项悬停）　　危险确认 · confirmDanger（默认焦点在「取消」）　　详情页客户卡');
  const r4 = box(root, { width: 'fill_container', layout: 'horizontal', gap: 16, alignItems: 'start' });
  const STAGE = 260;
  const stage = (w) => box(r4, { width: w, height: STAGE, layout: 'vertical', padding: [24, 24], cornerRadius: 10, fill: solid(T.key === 'light' ? '#F1F3F5' : '#141414') });
  const st1 = stage(228);
  const menu = box(st1, Object.assign({ width: 180, layout: 'vertical', padding: [6, 6], cornerRadius: 8, fill: solid(T.menu), effects: T.key === 'light' ? [{ type: 'shadow', offsetX: 0, offsetY: 8, blur: 16, spread: 0, color: 'rgba(145,158,171,0.24)' }] : [] }, T.key === 'light' ? {} : { stroke: { thickness: 1, fill: solid('#3A3A3A') } }));
  for (const [ic, t, danger] of [['copy', '复制为新询盘'], ['file-down', '导出'], ['-'], ['trash-2', '删除', 1]]) {
    if (ic === '-') { const dv = box(menu, { width: 'fill_container', layout: 'vertical', padding: [4, 0] }); I(dv, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.key === 'light' ? '#E5E8EB' : '#3A3A3A') }); continue; }
    const it = box(menu, { width: 'fill_container', height: 36, layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 10], cornerRadius: 6, fill: solid(ic === 'copy' ? T.menuHover : T.menu) });
    icon(it, ic, 16, danger ? T.st.err[0] : T.body); tx(it, t, { fill: solid(danger ? T.st.err[0] : T.body) });
  }
  const mask = box(r4, { width: 'fill_container', height: STAGE, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', cornerRadius: 10, fill: solid(T.mask) });
  const dlg = box(mask, Object.assign({ width: 400, layout: 'vertical', gap: 20, padding: [24, 24], cornerRadius: 8, fill: solid(T.key === 'light' ? T.card : '#222222'), effects: T.key === 'light' ? SH_DIALOG : [] }, T.key === 'light' ? {} : { stroke: { thickness: 1, fill: solid('#3A3A3A') } }));
  const dh = box(dlg, { width: 'fill_container', layout: 'horizontal', gap: 12, alignItems: 'start' });
  const di = box(dh, { width: 40, height: 40, cornerRadius: 20, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.st.err[1]) }); icon(di, 'trash-2', 20, T.st.err[0], false);
  const dt = box(dh, { width: 'fill_container', layout: 'vertical', gap: 6 });
  tx(dt, '删除询盘 INQ20261119003？', { fontSize: 16, fontWeight: 600, fill: solid(T.ink) });
  tx(dt, '删除后不再出现在列表中。', { fontSize: 13, fill: solid(T.sub) });
  const df = box(dlg, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', gap: 8 });
  // 焦点框：浅色内圈 2px 主题色 + 外圈 2px #637381（对白底 4.88:1），深色 2px 深色链接色
  const ring = box(df, Object.assign({ padding: [2, 2], cornerRadius: 12 }, T.key === 'light' ? { stroke: { thickness: 2, fill: solid('#637381') } } : {}));
  const cb = box(ring, { layout: 'horizontal', alignItems: 'center', height: 32, padding: [0, 12], cornerRadius: 10, stroke: { thickness: 2, fill: solid(P) } }); tx(cb, '取消', { fontWeight: 500, fill: solid(T.body) });
  const db = box(df, { layout: 'horizontal', alignItems: 'center', height: 36, padding: [0, 16], cornerRadius: 8, fill: solid(T.danger) }); tx(db, '删除', { fontWeight: 500, fill: solid('#FFFFFF') });

  const st3 = stage(348);
  const cc = card(st3, { width: 'fill_container', layout: 'vertical', padding: [20, 20] });
  const ch = box(cc, { width: 'fill_container', layout: 'horizontal', alignItems: 'center' });
  tx(ch, '客户', { fontSize: 16, fontWeight: 500, fill: solid(T.ink), width: 'fill_container' });
  const ci = box(ch, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.st.neu[1]) }); icon(ci, 'building-2', 16, T.sub, false);
  sp(cc, 12); tx(cc, 'Müller Automation GmbH', { fontSize: 16, fontWeight: 600, fontFamily: NUM, fill: solid(P) });
  sp(cc, 8);
  const cr = box(cc, { layout: 'horizontal', gap: 4, alignItems: 'center' }); tx(cr, '老客户 ·', { fontSize: 13, fill: solid(T.sub) }); country(T, cr, 'DE', '德国', { fontSize: 13, fill: solid(T.sub) });
  sp(cc, 8);
  const cp = box(cc, { layout: 'horizontal', gap: 6, alignItems: 'center' }); icon(cp, 'user', 14, T.sub); tx(cp, 'Tom Becker · 采购经理', { fontSize: 13, fontFamily: NUM, fill: solid(T.body) });
}

board(LIGHT, 'G1 公共组件 · 浅色');
board(DARK, 'G2 公共组件 · 深色');

// ———————— 工作台布局（按行的 12 栏栅格、同行等高、组件按高度档出内容；取代瀑布流）
// 高度档含标题：S 200、M 360。白卡：上 20 + 标题 24 + 12 + 内容区 + 下 20（S 内容区 124、M 284）
// 透明数字卡组同一盒模型：上 20 + 标题 24 + 12 + 卡片 144 = 200，标题和相邻白卡的标题同一水平线，底边对齐
const TIER = { S: 200, M: 360 };
function dash(T, name, H, rows, greet) {
  const K = kit(T); const { tx, box, icon, card, link } = K;
  const root = shell(T, name, H, 0);
  const head = box(root, { width: 'fill_container', height: 36, layout: 'horizontal', alignItems: 'center' });
  tx(head, greet, { fontSize: 22, fontWeight: 600, fill: solid(T.ink) });
  const grid = box(root, { width: 'fill_container', layout: 'vertical', gap: 16 });
  for (const r of rows) {
    const row = box(grid, { width: 'fill_container', height: r.h, layout: 'horizontal', gap: 16 });
    for (const w of r.items) w(row, r.h);
  }
  return K;
}
// 标题行：标题 18/500 + 期间 + 帮助 + 右侧（链接或说明）
function wHead(T, p, title, o) {
  const { tx, box, icon, link } = kit(T); o = o || {};
  const h = box(p, { width: 'fill_container', height: 24, layout: 'horizontal', gap: 8, alignItems: 'center' });
  tx(h, title, { fontSize: 18, fontWeight: 500, fill: solid(T.ink) });
  if (o.sub) tx(h, o.sub, { fontSize: 13, fill: solid(T.sub) });
  if (o.help) icon(h, 'info', 14, T.sub);
  const r = box(h, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end' });
  if (o.more) link(r, o.more);
  if (o.note) tx(r, o.note, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
  return h;
}
// 白卡组件：拉伸到行高，内容顶对齐
const wCard = (T, title, o, fill) => (row, h) => {
  const { card, box } = kit(T);
  const c = card(row, { width: 'fill_container', height: h, layout: 'vertical', gap: 12, padding: [20, 20] });
  wHead(T, c, title, o);
  const body = box(c, { width: 'fill_container', height: 'fill_container', layout: 'vertical' });
  fill(body);
};
// 透明数字卡组
const wGroup = (T, title, o, ms) => (row) => {
  const { box } = kit(T);
  const g = box(row, { width: 'fill_container', layout: 'vertical', gap: 12, padding: [20, 0, 0, 0] });
  wHead(T, g, title, o);
  const mr = box(g, { width: 'fill_container', layout: 'horizontal', gap: 16 });
  for (const m of ms) mkMetric(T, mr, ...m);
};

function bossBoard(T, name) {
  const K = kit(T); const { tx, box, icon, hr } = K;
  const fund = wGroup(T, '资金', { sub: '截至今天', note: '未结汇外币：没有' }, [
    ['应收未收', 'CNY 7,150.00', 'file-clock', 'cyan', { link: 1, hint: '没有过期未收款' }],
    ['本月到期应付', 'CNY 19,000.00', 'calendar-clock', 'gray', { link: 1, alert: 'err', alertText: '其中已逾期 CNY 19,000.00' }]]);
  const risk = wCard(T, '风险提醒', { note: '全部正常 · 截至 16:40' }, (b) => {
    const g = box(b, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 8 });
    for (const pair of [[['file-clock', '过期未收款 PI'], ['truck', '超期未到货']], [['package-x', '到货差异'], ['trending-down', '负毛利订单']]]) {
      const rr = box(g, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', gap: 8 });
      for (const [ic, nm] of pair) {
        const cell = box(rr, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 12], cornerRadius: 8, fill: solid(T.tonal) });
        const ib = box(cell, { width: 28, height: 28, cornerRadius: 6, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.card) }); icon(ib, ic, 16, T.sub, false);
        tx(cell, nm, { fontSize: 13, fill: solid(T.body), width: 'fill_container' });
        tx(cell, '0', { fontSize: 16, fontWeight: 700, fontFamily: NUM, fill: solid(T.faint) });
      }
    }
  });
  // 销售漏斗：5 条，每条 52；比例超过 100% 不显示
  const funnel = wCard(T, '销售漏斗', { sub: '本月', help: 1 }, (b) => {
    const data = [['新增商机', 12, 12, null], ['询盘', 9, 12, '75%'], ['报价', 14, 14, null], ['PI', 6, 14, '43%'], ['成交订单', 4, 14, '67%']];
    for (const [l, v, max, rate] of data) {
      const r = box(b, { width: 'fill_container', height: 52, layout: 'horizontal', gap: 12, alignItems: 'center' });
      tx(r, l, { fontSize: 13, fill: solid(T.body), width: 64 });
      const tr = box(r, { width: 'fill_container', height: 24, cornerRadius: 6, fill: solid(T.tonal), layout: 'horizontal', clipContent: true });
      I(tr, { type: 'frame', width: Math.round(362 * v / max), height: 24, cornerRadius: 6, fill: solid(T.key === 'light' ? P : '#FDA92D') });
      tx(r, String(v), { fontSize: 14, fontWeight: 700, fontFamily: NUM, fill: solid(T.ink), width: 28, textAlign: 'right' });
      tx(r, rate || '', { fontSize: 12, fontFamily: NUM, fill: solid(T.sub), width: 36, textAlign: 'right' });
    }
  });
  // 近 12 个月成交额：柱顶标数值，空月份画基线；毛利率一句贴底
  const trend = wCard(T, '近 12 个月成交额', { sub: '人民币' }, (b) => {
    const vals = [0, 0, 0, 0, 0, 0, 18.6, 22.4, 31.2, 26.8, 35.1, 41.3];
    const mon = ['12月', '1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月'];
    const ch = box(b, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'end' });
    vals.forEach((v, i) => {
      const col = box(ch, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 4, alignItems: 'center', justifyContent: 'end' });
      if (v) tx(col, v.toFixed(1) + '万', { fontSize: 11, fontFamily: NUM, fill: solid(T.sub) });
      I(col, { type: 'frame', width: 'fill_container', height: v ? Math.round(v * 4.4) : 2, cornerRadius: v ? 4 : 1, fill: solid(v ? P : T.line) });
      tx(col, mon[i], { fontSize: 11, fill: solid(T.sub) });
    });
    const nt = box(b, { width: 'fill_container', layout: 'horizontal', padding: [8, 0, 0, 0] });
    tx(nt, '毛利率 26.65%，12 个月前无数据', { fontSize: 12, fill: solid(T.sub) });
  });
  // 排行：同一张卡里两栏，各前 5 条，行高 44，表头 40
  const rank = (row, h) => {
    const c = K.card(row, { width: 'fill_container', height: h, layout: 'vertical', gap: 12, padding: [20, 20] });
    wHead(T, c, '排行', { sub: '本月' });
    const two = box(c, { width: 'fill_container', layout: 'horizontal', gap: 32 });
    const tbl = (title, cols, data) => {
      const t = box(two, { width: 'fill_container', layout: 'vertical' });
      const th = box(t, { width: 'fill_container', height: 40, layout: 'horizontal', alignItems: 'center', padding: [0, 12], cornerRadius: 6, fill: solid(T.head) });
      cols.forEach(([n, w, right]) => { const hc = box(th, { width: w, layout: 'horizontal', justifyContent: right ? 'end' : 'start' }); tx(hc, n, { fontSize: 13, fontWeight: 500, fill: solid(T.ink) }); });
      data.forEach((d, i) => {
        if (i) hr(t);
        const r = box(t, { width: 'fill_container', height: 44, layout: 'horizontal', alignItems: 'center', padding: [0, 12] });
        d.forEach((cell, k) => { const [, w, right] = cols[k]; const cc = box(r, { width: w, layout: 'horizontal', gap: 6, alignItems: 'center', justifyContent: right ? 'end' : 'start' }); if (typeof cell === 'function') cell(cc); else tx(cc, cell, { fontSize: 13, fontFamily: NUM, fill: solid(k === 0 ? T.ink : T.body) }); });
      });
    };
    tbl('业务员', [['业务员', 'fill_container'], ['成交额', 150, 1], ['毛利率', 90, 1]], [['林熙', 'CNY 186,420.00', '24.10%'], ['Kevin', 'CNY 132,800.00', '21.65%'], ['Lily', 'CNY 61,240.00', '19.80%'], ['周宁', 'CNY 22,600.00', '27.30%'], ['陈默', 'CNY 9,800.00', '18.20%']]);
    const cu = (nm, cc) => (p) => { const v = box(p, { layout: 'horizontal', gap: 8, alignItems: 'center' }); flag(T, v, cc); tx(v, nm, { fontSize: 13, fontFamily: NUM, fill: solid(T.ink) }); };
    tbl('客户', [['客户', 'fill_container'], ['成交额', 150, 1], ['订单', 60, 1]], [[cu('Müller Automation GmbH', 'DE'), 'CNY 98,400.00', '3'], [cu('Kansai Robotics Co., Ltd.', 'JP'), 'CNY 76,250.00', '2'], [cu('Électro Industrie SARL', 'FR'), 'CNY 54,900.00', '2'], [cu('Automazione Rossi S.r.l.', 'IT'), 'CNY 31,600.00', '1'], [cu('Müller Automation GmbH', 'DE'), 'CNY 12,300.00', '1']]);
  };
  dash(T, name, 1270, [
    { h: TIER.S, items: [wGroup(T, '经营概览', { sub: '本月 · 人民币', help: 1, more: '经营统计' }, [
      ['成交额', 'CNY 412,860.00', 'handshake', 'blue', { link: 1, delta: '+9.6%' }],
      ['毛利', 'CNY 87,940.00', 'coins', 'purple', { link: 1, delta: '+6.1%' }],
      ['毛利率', '21.30%', 'percent', 'purple', { delta: MINUS + '0.8 个百分点' }],
      ['实收回款', 'CNY 386,420.00', 'hand-coins', 'cyan', { delta: MINUS + '4.3%' }],
      ['费用', 'CNY 42,100.00', 'receipt-text', 'gray', {}]])] },
    { h: TIER.S, items: [fund, risk] },
    { h: TIER.M, items: [funnel, trend] },
    { h: 340, items: [rank] },
  ], '晚上好，老板');
}

function salesBoard(T, name) {
  const K = kit(T); const { tx, box, icon, hr, label } = K;
  // 我的待办：5 行，每行 56（PrimeDash 列表行：38 图标块、行间分隔线）
  const todo = wCard(T, '我的待办', {}, (b) => {
    const tone = { info: T.st.info, ok: T.st.ok, pri: [P, T.soft], blue: T.fam.blue, err: T.st.err };
    const todos = [['inbox', 'info', '待确认的询盘', 'AI 解析完成，等你核对型号', 3], ['file-text', 'ok', '可以报价', '采购已回价，还没做报价单', 5], ['wallet', 'blue', 'PI 待收款', '已发给客户，还没付清', 4], ['rocket', 'blue', '订单待出运', '货已到齐，等安排交货代', 3], ['alarm-clock', 'err', '售后要更新客户', '已逾期或 1 小时内到期的售后单', 1, 1]];
    todos.forEach(([ic, tn, t, d, n, alert], i) => {
      if (i) hr(b);
      const r = box(b, { width: 'fill_container', height: 55, layout: 'horizontal', gap: 12, alignItems: 'center' });
      K.iconBlock(r, ic, tone[tn]);
      const tc = box(r, { width: 'fill_container', layout: 'vertical', gap: 2 }); tx(tc, t, { fontWeight: 500, fill: solid(T.ink) }); tx(tc, d, { fontSize: 12, fill: solid(T.sub) });
      tx(r, String(n), { fontSize: 18, fontWeight: 700, fontFamily: NUM, fill: solid(alert ? T.st.err[0] : T.ink) });
      icon(r, 'chevron-right', 16, T.faint, false);
    });
  });
  // 过期未收款的 PI：前 5 条（这里只有 3 条），不足 5 条时底部贴一行合计
  const pis = wCard(T, '过期未收款的 PI', { more: '查看全部（3）' }, (b) => {
    const sm = box(b, { width: 'fill_container', layout: 'horizontal', gap: 8, alignItems: 'end', padding: [0, 0, 12, 0] });
    tx(sm, '3', { fontSize: 28, fontWeight: 700, fontFamily: NUM, lineHeight: 1.2, fill: solid(T.st.err[0]) });
    const smt = box(sm, { layout: 'vertical', padding: [0, 0, 4, 0] }); tx(smt, '张 · 合计 USD 18,600.00、EUR 2,400.00', { fontSize: 13, fontFamily: NUM, fill: solid(T.sub) });
    hr(b);
    const data = [['PI20261102006', 'Delta Controls S.A.', 'USD 9,800.00', 18], ['PI20261105002', 'Nordic Automation AB', 'EUR 2,400.00', 12], ['PI20261109004', 'Prime Industrial Supply', 'USD 8,800.00', 6]];
    data.forEach(([no, cu, amt, d], i) => {
      if (i) hr(b);
      const r = box(b, { width: 'fill_container', height: 64, layout: 'horizontal', gap: 12, alignItems: 'center' });
      const l = box(r, { width: 'fill_container', layout: 'vertical', gap: 2 }); tx(l, no, { fontSize: 14, fontWeight: 600, fontFamily: NUM, fill: solid(P) }); tx(l, cu, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
      tx(r, amt, { fontSize: 14, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
      label(r, '已过期 ' + d + ' 天', T.st.err);
    });
  });
  dash(T, name, 960, [
    { h: TIER.M, items: [todo, pis] },
    { h: TIER.S, items: [wGroup(T, '我的业绩', { sub: '本月', help: 1 }, [
      ['成交额', 'CNY 412,860.00', 'handshake', 'blue', { link: 1, delta: '+9.6%' }],
      ['毛利', 'CNY 87,940.00', 'coins', 'purple', { delta: '+6.1%' }],
      ['实收回款', 'CNY 386,420.00', 'hand-coins', 'cyan', { delta: MINUS + '4.3%' }],
      ['成交订单', '26', 'shopping-bag', 'blue', { link: 1, delta: '+18.2%' }]])] },
    { h: TIER.S, items: [
      wGroup(T, '询价进度', { help: 1 }, [
        ['等采购回价的型号', '14', 'hourglass', 'info', { link: 1, hint: '来自 4 张询盘' }],
        ['已过报价截止', '1', 'calendar-x', 'gray', { alert: 'err', alertText: '截止日期已过，还在询价' }]]),
      wGroup(T, '我的商机', { sub: '本月' }, [
        ['本月新增商机', '12', 'user-plus', 'blue', { link: 1, hint: '' }],
        ['有效商机', '8', 'badge-check', 'blue', { hint: '有效率 66.7%' }],
        ['赢单', '2', 'trophy', 'blue', { hint: '' }]])] },
  ], '下午好，Kevin');
}

bossBoard(LIGHT, 'H1 工作台 · 老板 · 浅色');
bossBoard(DARK, 'H2 工作台 · 老板 · 深色');
salesBoard(LIGHT, 'H3 工作台 · 业务员 · 浅色');
