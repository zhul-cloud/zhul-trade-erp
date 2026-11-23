// 公共组件与工作台原型（任务 9.1 整改，design-review.md D1–D31、S1–S18）
// 规则以 .claude/context/ui-design-patterns.md「设计原则与流程」为准；本版按视觉精修方案（2026-11-23）重写：
// 柔和扩散阴影、顶栏与侧栏、涨跌胶囊、主角卡、细条漏斗、带占比条的排行、风险两种状态、列表两列审计、四种空状态
const CN = 'Noto Sans SC';
const NUM = 'Public Sans';
const solid = (c) => [{ type: 'solid', color: c }];
const P = '#FDA92D';
const MINUS = '−';
// 数字卡 144 = 内边距 40 + 名称行 38 + 10 + 数值 28 + 4 + 说明行 24
const CARD_H = 144;
// 卡片阴影：柔和扩散（D30）；悬停加深
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const SH_HOVER = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 16, blur: 32, spread: -4, color: 'rgba(145,158,171,0.2)' }];
// 对话框：Material elevation-24
const SH_DIALOG = [{ type: 'shadow', offsetX: 0, offsetY: 11, blur: 15, spread: -7, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 24, blur: 38, spread: 3, color: 'rgba(145,158,171,0.14)' }, { type: 'shadow', offsetX: 0, offsetY: 9, blur: 46, spread: 8, color: 'rgba(145,158,171,0.12)' }];
const LIGHT = { key: 'light', page: '#F9FAFB', card: '#FFFFFF', head: '#F4F6F8', line: '#E5E8EB', field: '#D4D6D9', ink: '#212B36', body: '#3E4750', sub: '#637381', faint: '#919EAB', soft: '#FEF5E6', tonal: '#F1F3F5', navOn: '#EDEEEF', hover: '#FFFFFF', hoverLine: null, rowHover: '#F6F7F8', control: '#D3D5D8', danger: '#B71D18', mask: 'rgba(33,43,54,0.5)', menu: '#FFFFFF', menuHover: '#F6F7F8',
  chartMuted: '#DFE3E8', chartMutedHover: '#C4CDD5', barMuted: '#C4CDD5', grid: '#E5E8EB', shadow: SH, stroke: null,
  st: { ok: ['#0D7A4A', '#E5F8EC'], warn: ['#9A5B00', '#FFF5E0'], info: ['#006C9C', '#E0F6FA'], err: ['#B71D18', '#FFEBE6'], neu: ['#56636F', '#ECEEF0'] },
  fam: { blue: ['#3B82F6', '#E7F0FE'], purple: ['#8B5CF6', '#F1EBFE'], cyan: ['#1F7A76', '#E6F3F3'], gray: ['#637381', '#ECEEF0'] } };
const DARK = { key: 'dark', page: '#0A0A0A', card: '#1A1A1A', head: '#222222', line: '#2A2A2A', field: '#4A4A4A', ink: '#F5F5F5', body: '#E0E0E0', sub: '#A0A0A0', faint: '#6E6E6E', soft: '#3E311D', tonal: '#232323', navOn: '#2C2C2C', hover: '#222222', hoverLine: '#3A3A3A', rowHover: '#222222', control: '#4A4A4A', danger: '#D63B1F', mask: 'rgba(0,0,0,0.7)', menu: '#222222', menuHover: '#2C2C2C',
  chartMuted: '#363636', chartMutedHover: '#4A4A4A', barMuted: '#4A4A4A', grid: '#2A2A2A', shadow: [], stroke: { thickness: 1, fill: solid('#2A2A2A') },
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
  const iconBlock = (p, ic, tone, size) => { const s = size || 38; const a = box(p, { width: s, height: s, cornerRadius: 8, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(tone[1]) }); icon(a, ic, s === 38 ? 20 : Math.round(s * 0.5), tone[0], false); return a; };
  // 卡头链接：次要色，悬停才着色（D23 同理）
  const link = (p, t) => { const m = box(p, { layout: 'horizontal', gap: 2, alignItems: 'center' }); tx(m, t, { fontSize: 13, fill: solid(T.sub) }); icon(m, 'chevron-right', 14, T.sub); return m; };
  const cap = (p, t) => tx(p, t, { fontSize: 13, fontWeight: 600, fill: solid(T.faint) });
  const sp = (p, h) => box(p, { width: 1, height: h });
  // 涨跌胶囊：方向一眼可辨，数字与「较上月」分层；颜色按涨跌方向（默认涨红跌绿）
  const deltaPill = (p, d, suffix) => {
    const up = d[0] === '+'; const tone = up ? T.st.err : T.st.ok;
    const r = box(p, { layout: 'horizontal', gap: 8, alignItems: 'center' });
    const pill = box(r, { height: 20, layout: 'horizontal', gap: 2, alignItems: 'center', padding: [0, 8], cornerRadius: 10, fill: solid(tone[1]) });
    icon(pill, up ? 'arrow-up-right' : 'arrow-down-right', 12, tone[0], false);
    tx(pill, d.replace(/^[+−]/, ''), { fontSize: 12, fontWeight: 600, fontFamily: NUM, fill: solid(tone[0]) });
    tx(r, suffix || '较上月', { fontSize: 12, fill: solid(T.sub) });
    return r;
  };
  return { tx, box, icon, hr, card, label, iconBlock, link, cap, sp, deltaPill };
}

// 国旗（原型用色块画几面简单的旗；实现用 flag-icons 按 ISO 代码取 SVG，缺代码时显示 globe）
// 矩形 16×12（圆角 2、内描边）；圆形 size（排行头像）
function flag(T, p, code, size) {
  const K = kit(T);
  const round = !!size; const W = size || 16; const H = size || 12;
  const f = K.box(p, { width: W, height: H, cornerRadius: round ? W / 2 : 2, clipContent: true, layout: code === 'DE' ? 'vertical' : 'horizontal', stroke: { thickness: 1, fill: solid(T.key === 'light' ? 'rgba(33,43,54,0.12)' : 'rgba(255,255,255,0.16)') } });
  const bands = { DE: ['#000000', '#DD0000', '#FFCE00'], FR: ['#0055A4', '#FFFFFF', '#EF4135'], IT: ['#009246', '#FFFFFF', '#CE2B37'] }[code];
  if (bands) {
    for (const c of bands) I(f, { type: 'frame', width: code === 'DE' ? W : Math.round(W / 3) + (c === bands[1] ? W - 3 * Math.round(W / 3) : 0), height: code === 'DE' ? Math.round(H / 3) + (c === bands[2] ? H - 3 * Math.round(H / 3) : 0) : H, fill: solid(c) });
    return f;
  }
  const jp = K.box(f, { width: W, height: H, fill: solid('#FFFFFF'), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  I(jp, { type: 'ellipse', width: Math.round(H / 2), height: Math.round(H / 2), fill: solid('#BC002D') });
  return f;
}
const country = (T, p, code, name, o) => { const K = kit(T); const r = K.box(p, { layout: 'horizontal', gap: 4, alignItems: 'center' }); flag(T, r, code); K.tx(r, name, Object.assign({ fontSize: 12, fill: solid(T.sub) }, o || {})); return r; };

// 数字卡（高 144）：左上 38 图标块（家族色）+ 名称 14/400 次要色；数值 24/600 整行宽、与图标左缘对齐，CNY 弱化；说明行固定 24
function mkMetric(T, p, l, v, ic, fam, o) {
  const { tx, box, icon, label, iconBlock, sp, deltaPill } = kit(T);
  o = o || {};
  const hov = !!o.hover;
  const st = T.key === 'light' ? { effects: hov ? SH_HOVER : SH } : { stroke: { thickness: 1, fill: solid(hov ? T.hoverLine : T.line) } };
  const m = box(p, Object.assign({ width: 'fill_container', height: CARD_H, layout: 'vertical', padding: [20, 20], cornerRadius: 10, fill: solid(hov ? T.hover : T.card) }, st));
  const top = box(m, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 12 });
  iconBlock(top, ic, o.alert ? T.st[o.alert] : (T.fam[fam] || T.st[fam]));
  const lr = box(top, { width: 'fill_container', layout: 'horizontal', gap: hov ? 4 : 2, alignItems: 'center' });
  tx(lr, l, { fontSize: 14, fill: solid(T.sub) });
  if (o.link) icon(lr, 'chevron-right', 14, hov ? T.ink : T.faint);
  sp(m, 8);
  const vr = box(m, { layout: 'horizontal', gap: 4, alignItems: 'end' });
  if (v.startsWith('CNY ')) {
    const cw = box(vr, { layout: 'vertical', padding: [0, 0, 3, 0] }); tx(cw, 'CNY', { fontSize: 14, fontWeight: 600, fontFamily: NUM, lineHeight: 1.2, fill: solid(T.sub) });
    tx(vr, v.slice(4), { fontSize: 24, fontWeight: 600, fontFamily: NUM, lineHeight: 1.25, fill: solid(T.ink) });
  } else tx(vr, v, { fontSize: 24, fontWeight: 600, fontFamily: NUM, lineHeight: 1.25, fill: solid(T.ink) });
  sp(m, 4);
  const hl = box(m, { width: 'fill_container', height: 24, layout: 'horizontal', gap: 8, alignItems: 'center' });
  if (o.alertText) label(hl, o.alertText, T.st[o.alert], 'triangle-alert');
  else if (o.delta) deltaPill(hl, o.delta);
  else if (o.hint !== '') tx(hl, o.hint || '上月无数据', { fontSize: 12, fill: solid(T.sub) });
  if (o.bar) { const tr = box(hl, { width: 'fill_container', height: 4, cornerRadius: 2, fill: solid(T.tonal), layout: 'horizontal', clipContent: true }); I(tr, { type: 'frame', width: Math.round(110 * o.bar), height: 4, cornerRadius: 2, fill: solid(T.fam.blue[0]) }); }
  return m;
}

// 外壳：侧栏 244 + 顶栏 64（真实内容区 1440 下 1148）
function shell(T, name, H, gap) {
  const K = kit(T); const { tx, box, icon, card } = K;
  const root = I(null, { type: 'frame', name, width: 1440, height: H, layout: 'horizontal', gap: 16, padding: [16, 16, 32, 16], fill: solid(T.page), alignItems: 'start' });
  const side = card(root, { width: 244, height: 'fill_container', layout: 'vertical', gap: 2, padding: [16, 14] });
  const brand = box(side, { layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 6, 14, 6] });
  const logo = box(brand, { width: 30, height: 30, cornerRadius: 8, fill: solid(P), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  tx(logo, '烛', { fontSize: 15, fontWeight: 600, fill: solid('#212B36') });
  const bt = box(brand, { layout: 'vertical', gap: 0 }); tx(bt, '烛龙ERP', { fontSize: 18, fontWeight: 600, fill: solid(T.ink) }); tx(bt, '弗威科技', { fontSize: 12, fill: solid(T.sub) });
  const nav = [['layout-dashboard', '工作台', 1], ['briefcase', '销售管理'], ['shopping-cart', '采购管理'], ['warehouse', '仓库管理'], ['truck', '物流管理'], ['wallet', '财务管理'], ['package', '商品管理'], ['megaphone', '运营管理'], ['bar-chart-3', '统计分析'], ['-'], ['sliders-horizontal', '业务设置'], ['settings', '系统管理'], ['building', '租户管理']];
  for (const [ic, l, on] of nav) {
    if (ic === '-') { const dv = box(side, { width: 'fill_container', layout: 'vertical', padding: [8, 8] }); I(dv, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) }); continue; }
    const row = box(side, { width: 'fill_container', height: 40, layout: 'horizontal', gap: 12, alignItems: 'center', padding: [0, 12, 0, 14], cornerRadius: 8, fill: solid(on ? T.soft : T.card) });
    icon(row, ic, 20, on ? P : T.body); tx(row, l, { fontSize: 16, fontWeight: on ? 600 : 400, fill: solid(on ? T.ink : T.body), width: 'fill_container' });
    if (!on) icon(row, 'chevron-right', 16, T.faint);
  }
  box(side, { width: 'fill_container', height: 'fill_container' });
  const ver = box(side, { width: 'fill_container', layout: 'horizontal', padding: [0, 14] }); tx(ver, 'v1.48.0', { fontSize: 12, fontFamily: NUM, fill: solid(T.faint) });
  const right = box(root, { width: 'fill_container', layout: 'vertical', gap: 16 });
  // 顶栏：页签（选中墨色 600 + 底部 2px 主题色线）；右侧主题、语言、通知、分隔线、用户胶囊
  const top = card(right, { width: 'fill_container', height: 64, layout: 'horizontal', alignItems: 'center', padding: [0, 12, 0, 8] });
  const tab = box(top, { height: 64, layout: 'vertical', justifyContent: 'end' });
  const tr = box(tab, { height: 62, layout: 'horizontal', gap: 6, alignItems: 'center', padding: [0, 16] }); icon(tr, 'house', 16, T.ink); tx(tr, '工作台', { fontWeight: 600, fill: solid(T.ink) });
  I(tab, { type: 'frame', width: 'fill_container', height: 2, fill: solid(P) });
  box(top, { width: 'fill_container', height: 1 });
  for (const ic of ['swatch-book', 'languages']) { const b = box(top, { width: 36, height: 36, cornerRadius: 18, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }); icon(b, ic, 20, T.body, false); box(top, { width: 4, height: 1 }); }
  const bell = box(top, { width: 36, height: 36, layout: 'none' });
  I(bell, { type: 'icon_font', name: 'fill=' + T.body, iconFontName: 'bell', x: 8, y: 8, width: 20, height: 20, fill: solid(T.body) });
  const badge = I(bell, { type: 'frame', x: 18, y: 2, width: 16, height: 16, cornerRadius: 8, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid('#FF5630') });
  tx(badge, '3', { fontSize: 11, fontWeight: 600, fontFamily: NUM, fill: solid('#FFFFFF') });
  box(top, { width: 4, height: 1 });
  const dv = box(top, { layout: 'horizontal', padding: [0, 8] }); I(dv, { type: 'frame', width: 1, height: 24, fill: solid(T.line) });
  const user = box(top, { layout: 'horizontal', gap: 8, alignItems: 'center', padding: [2, 8, 2, 2], cornerRadius: 20 });
  const av = box(user, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.soft) }); tx(av, '王', { fontSize: 14, fontWeight: 600, fill: solid(T.key === 'light' ? T.ink : P) });
  tx(user, '王敏', { fontWeight: 600, fill: solid(T.ink) }); icon(user, 'chevron-down', 14, T.sub);
  return box(right, { width: 'fill_container', layout: 'vertical', gap: gap === undefined ? 28 : gap });
}

// ———————— G 公共组件
function board(T, name) {
  const K = kit(T); const { tx, box, icon, hr, card, label, iconBlock, link, cap, sp } = K;
  const root = shell(T, name, 1600);
  const metric = (...args) => mkMetric(T, ...args);

  // 1 数字卡组：标题落在页面底上，卡片独立浮起；第一张为悬停态
  cap(root, '数字卡组 · MetricGroup（第一张为悬停态）');
  const g1 = box(root, { width: 'fill_container', layout: 'vertical', gap: 12 });
  const gh = box(g1, { width: 'fill_container', height: 24, layout: 'horizontal', gap: 8, alignItems: 'center' });
  tx(gh, '经营概览', { fontSize: 16, fontWeight: 600, fill: solid(T.ink) });
  tx(gh, '本月 · 人民币', { fontSize: 13, fill: solid(T.sub) });
  icon(gh, 'info', 14, T.sub);
  const ghr = box(gh, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end' }); link(ghr, '经营统计');
  const mrow = box(g1, { width: 'fill_container', layout: 'horizontal', gap: 16 });
  metric(mrow, '成交额', 'CNY 412,860.00', 'handshake', 'blue', { link: 1, delta: '+9.6%', hover: 1 });
  metric(mrow, '毛利', 'CNY 87,940.00', 'coins', 'purple', { link: 1, delta: '+6.1%' });
  metric(mrow, '毛利率', '21.30%', 'percent', 'purple', { delta: MINUS + '0.8 个百分点' });
  metric(mrow, '实收回款', 'CNY 386,420.00', 'hand-coins', 'cyan', { delta: MINUS + '4.3%' });
  metric(mrow, '费用', 'CNY 42,100.00', 'receipt-text', 'gray', {});

  // 2 列表主卡：审计合并为「创建」「更新」两列后放得下，不横向滚动、不出现固定列阴影；表头 48；单号墨色；悬停行出现复制
  cap(root, '列表主卡 · CountryText / UserName / RowActions / RowAlert / TablePager（第 3 行为悬停态）');
  const mc = card(root, { width: 'fill_container', layout: 'vertical', clipContent: true });
  // 未勾选时的操作行：结果数 + 快捷筛选 + 收起的「更多筛选」里已生效的条件胶囊
  const pre = card(root, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 12, height: 52, padding: [0, 20] });
  const act = pre;
  tx(act, '共 236 条', { fontSize: 14, fill: solid(T.sub) });
  const chip = (t, on, x) => { const c = box(act, { height: 28, layout: 'horizontal', gap: 4, alignItems: 'center', padding: [0, 10], cornerRadius: 14, fill: solid(on ? T.soft : T.tonal) }); if (on) icon(c, 'check', 14, T.ink); tx(c, t, { fontSize: 13, fill: solid(T.body) }); if (x) icon(c, 'x', 14, T.sub); };
  chip('今日新增'); chip('品牌：西门子', true, true);
  const bat = box(mc, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 12, height: 52, padding: [0, 20] });
  tx(bat, '已选 1 张', { fontSize: 14, fontWeight: 600, fill: solid(T.ink) });
  tx(bat, '取消选择', { fontSize: 14, fill: solid(T.sub) });
  box(bat, { width: 'fill_container', height: 1 });
  const bb = box(bat, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 32, padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.control) } }); tx(bb, '批量操作', { fontSize: 13, fill: solid(T.ink) }); icon(bb, 'chevron-down', 14, T.sub);
  const ROWH = 60; const HH = 48;
  // 询盘状态与颜色取自 inquiry/shared/constants.ts STATUS_META，按 status-colors 归类；「已过报价截止」取自工作台询价进度
  const rows = [
    { no: 'INQ20261121004', d: '2026-11-21', cu: 'Müller Automation GmbH', cs: '老客户', cc: 'DE', cn: '德国', ct: 'Tom Becker', st: ['待确认', 'warn'], src: 'OPP20261120002', cb: ['林熙', '11-21 10:20'], ub: ['系统', '11-21 16:40'] },
    { no: 'INQ20261120007', d: '2026-11-20', cu: 'Électro Industrie SARL', cs: '新客户', cc: 'FR', cn: '法国', ct: 'Claire Dubois', st: ['询价中', 'info'], alert: ['已过报价截止', 'err'], src: '', cb: ['Kevin', '11-20 10:21'], ub: ['Kevin', '11-21 16:41'] },
    { no: 'INQ20261119003', d: '2026-11-19', cu: 'Kansai Robotics Co., Ltd.', cs: '老客户', cc: 'JP', cn: '日本', ct: 'Sato Ken', st: ['可报价', 'ok'], src: 'OPP20261118005', cb: ['系统', '11-19 10:22'], ub: ['周宁', '11-21 16:42'] },
    { no: 'INQ20261118001', d: '2026-11-18', cu: 'Automazione Rossi S.r.l.', cs: '老客户', cc: 'IT', cn: '意大利', ct: 'Marco Rossi', st: ['已成交', 'ok'], src: '', cb: ['Lily', '11-18 10:23'], ub: ['Lily', '11-21 16:43'] },
  ];
  const tbl = box(mc, { width: 'fill_container', layout: 'horizontal' });
  const ck = box(tbl, { width: 48, layout: 'vertical', fill: solid(T.card) });
  const fl = box(tbl, { width: 186, layout: 'vertical', fill: solid(T.card) });
  const sc = box(tbl, { width: 'fill_container', layout: 'vertical' });
  const fr = box(tbl, { width: 136, layout: 'vertical', fill: solid(T.card) });
  const th = (p, t, o) => { const h = box(p, Object.assign({ width: 'fill_container', height: HH, layout: 'horizontal', alignItems: 'center', fill: solid(T.head) }, o || {})); return h; };
  // 勾选列（最左）：行内提醒的 3px 色条画在它的左边缘
  const cbx = (p, on, bg, alert) => { const head = bg === undefined; const w0 = box(p, { width: 'fill_container', height: head ? HH : ROWH, layout: 'horizontal', fill: solid(head ? T.head : bg) }); if (!head) I(w0, { type: 'frame', width: 3, height: ROWH, fill: solid(alert || bg) }); const w = box(w0, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', justifyContent: 'center', alignItems: 'center', padding: head ? [0, 0] : [0, 3, 0, 0] }); const b = box(w, Object.assign({ width: 16, height: 16, cornerRadius: 4, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(on ? P : T.card) }, on ? {} : { stroke: { thickness: 1, fill: solid(T.field) } })); if (on) icon(b, 'check', 12, '#212B36', false); };
  cbx(ck, false);
  tx(th(fl, '', { padding: [0, 0, 0, 4] }), '询盘编号 · 日期', { fontSize: 13, fill: solid(T.sub) });
  const mid = [['客户', 280], ['状态', 140], ['来源单号', 170], ['创建', 120], ['更新', 'fill_container']];
  const hdr = th(sc, '', { padding: [0, 0, 0, 16] });
  mid.forEach(([c, w]) => { const hc = box(hdr, { width: w }); tx(hc, c, { fontSize: 13, fill: solid(T.sub) }); });
  tx(th(fr, '', { padding: [0, 12] }), '操作', { fontSize: 13, fill: solid(T.sub) });
  rows.forEach((r, ri) => {
    const hov = ri === 2; const bg = hov ? T.rowHover : (ri === 1 ? T.soft : T.card);
    for (const col of [ck, fl, sc, fr]) I(col, { type: 'frame', width: 'fill_container', height: 1, fill: solid(T.line) });
    cbx(ck, ri === 1, bg, r.alert ? T.st[r.alert[1]][0] : null);
    // 首列：左侧 3px 色条（行内提醒）；单号墨色 14/600，悬停主题色下划线并出现复制
    const a = box(fl, { width: 'fill_container', height: ROWH, layout: 'horizontal', alignItems: 'center', fill: solid(bg) });
    const ac = box(a, { layout: 'vertical', gap: 2, padding: [0, 0, 0, 1] });
    const nr = box(ac, { layout: 'horizontal', gap: 4, alignItems: 'center' });
    const nw = box(nr, { layout: 'vertical', gap: 0 });
    tx(nw, r.no, { fontWeight: 600, fontFamily: NUM, fill: solid(hov ? P : T.ink) });
    if (hov) I(nw, { type: 'frame', width: 'fill_container', height: 1, fill: solid(P) });
    if (hov) icon(nr, 'copy', 14, T.sub, false);
    tx(ac, r.d, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
    const row = box(sc, { width: 'fill_container', layout: 'horizontal', height: ROWH, alignItems: 'center', padding: [0, 0, 0, 16], fill: solid(bg) });
    let c = box(row, { width: 280, layout: 'vertical', gap: 2 }); tx(c, r.cu, { fontFamily: NUM, fill: solid(T.ink) });
    const cl = box(c, { layout: 'horizontal', gap: 4, alignItems: 'center' }); tx(cl, r.cs + ' ·', { fontSize: 12, fill: solid(T.sub) }); country(T, cl, r.cc, r.cn); tx(cl, '· ' + r.ct, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
    c = box(row, { width: 140, layout: 'vertical', gap: 2 }); label(c, r.st[0], T.st[r.st[1]]); if (r.alert) label(c, r.alert[0], T.st[r.alert[1]], 'triangle-alert');
    c = box(row, { width: 170 }); tx(c, r.src || '—', { fontFamily: NUM, fontWeight: r.src ? 600 : 400, fill: solid(r.src ? T.ink : T.faint) });
    for (const [who, when, w] of [[r.cb[0], r.cb[1], 120], [r.ub[0], r.ub[1], 'fill_container']]) {
      c = box(row, { width: w, layout: 'vertical', gap: 2 }); tx(c, who, { fontSize: 13, fill: solid(who === '系统' ? T.sub : T.body) }); tx(c, when, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
    }
    const ops = box(fr, { width: 'fill_container', height: ROWH, layout: 'horizontal', alignItems: 'center', gap: 8, padding: [0, 12], fill: solid(bg) });
    ['eye', 'pencil', 'ellipsis'].forEach((ic, k) => { const on = ri === 0 && k === 2; const ib = box(ops, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(on ? T.tonal : bg) }); icon(ib, ic, 18, T.sub, false); });
  });
  hr(mc);
  // 分页：左每页条数；右页码（当前页主题浅底 + 墨色字）、上一页下一页、跳页；控件统一高 32
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

  // 3 空状态四种语气：首次为空、无结果、错误、无权限（不用插画，用带情境色的图标）
  cap(root, '空状态 · EmptyState（四种语气）');
  const er = box(root, { width: 'fill_container', layout: 'horizontal', gap: 16 });
  const empties = [
    ['file-text', [T.sub, T.tonal], '还没有业务凭证', '库存月结结账后自动生成', '去库存月结'],
    ['search-x', [T.sub, T.tonal], '没有符合条件的询盘', '换个关键词，或清空筛选再看', '清空筛选'],
    ['triangle-alert', T.st.err, '加载失败', '网络或服务出错，筛选条件已保留', '重试'],
    ['lock', [T.sub, T.tonal], '没有查看权限', '需要「业务凭证」查看权限，请联系管理员', null],
  ];
  for (const [ic, tone, t, d, btn] of empties) {
    const ec = card(er, { width: 'fill_container', height: 232, layout: 'vertical', alignItems: 'center', justifyContent: 'center', padding: [24, 20] });
    const ei = box(ec, { width: 64, height: 64, cornerRadius: 32, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(tone[1]) }); icon(ei, ic, 28, tone[0], false);
    sp(ec, 16); tx(ec, t, { fontSize: 16, fontWeight: 600, fill: solid(T.ink) });
    sp(ec, 4); tx(ec, d, { fontSize: 13, fill: solid(T.sub), textAlign: 'center' });
    sp(ec, 16);
    if (btn) { const b = box(ec, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 32, padding: [0, 12], cornerRadius: 8, stroke: { thickness: 1, fill: solid(T.control) } }); tx(b, btn, { fontSize: 13, fill: solid(T.ink) }); }
    else box(ec, { width: 1, height: 32 });
  }

  // 4 更多菜单、危险确认（带遮罩）、客户卡
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
  tx(dt, '删除后不再出现在列表中。', { fontSize: 14, fill: solid(T.body) });
  const df = box(dlg, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', gap: 8 });
  // 焦点框：浅色内圈 2px 主题色 + 外圈 2px #637381，深色 2px 深色链接色
  const ring = box(df, Object.assign({ padding: [2, 2], cornerRadius: 12 }, T.key === 'light' ? { stroke: { thickness: 2, fill: solid('#637381') } } : {}));
  const cb = box(ring, { layout: 'horizontal', alignItems: 'center', height: 32, padding: [0, 12], cornerRadius: 10, stroke: { thickness: 2, fill: solid(P) } }); tx(cb, '取消', { fill: solid(T.body) });
  const db = box(df, { layout: 'horizontal', alignItems: 'center', height: 36, padding: [0, 16], cornerRadius: 8, fill: solid(T.danger) }); tx(db, '删除', { fill: solid('#FFFFFF') });

  const st3 = stage(348);
  const cc = card(st3, { width: 'fill_container', layout: 'vertical', padding: [20, 20] });
  const ch = box(cc, { width: 'fill_container', layout: 'horizontal', alignItems: 'center' });
  tx(ch, '客户', { fontSize: 16, fontWeight: 600, fill: solid(T.ink), width: 'fill_container' });
  const ci = box(ch, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.soft) }); icon(ci, 'building-2', 16, P, false);
  sp(cc, 12); tx(cc, 'Müller Automation GmbH', { fontSize: 16, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
  sp(cc, 8);
  const cr = box(cc, { layout: 'horizontal', gap: 4, alignItems: 'center' }); tx(cr, '老客户 ·', { fontSize: 13, fill: solid(T.sub) }); country(T, cr, 'DE', '德国', { fontSize: 13, fill: solid(T.sub) });
  sp(cc, 8);
  const cp = box(cc, { layout: 'horizontal', gap: 6, alignItems: 'center' }); icon(cp, 'user', 14, T.sub); tx(cp, 'Tom Becker · 采购经理', { fontSize: 13, fontFamily: NUM, fill: solid(T.body) });
}

board(LIGHT, 'G1 公共组件 · 浅色');
board(DARK, 'G2 公共组件 · 深色');

// ———————— H 工作台（按行的 12 栏栅格、同行等高、组件按高度档出内容）
// 1440 下内容区 1148：栏宽 81、间距 16，span 栏宽 = 81 × span + 16 × (span − 1)
// 同一行组件的标题统一放在卡外的页面底上（用户 2026-11-23 选 A），卡片从同一条线开始、到同一条线结束
// 高度档含标题：标题 24 + 12 + 卡片；S 180（卡 144，等于一排数字卡）、M 340（卡 304，等于两排数字卡）
const TIER = { S: 180, M: 340 };
const spanW = (n) => 81 * n + 16 * (n - 1);
function dash(T, name, H, rows, greet) {
  const K = kit(T); const { tx, box } = K;
  const root = shell(T, name, H, 0);
  const head = box(root, { width: 'fill_container', height: 32, layout: 'horizontal', alignItems: 'center' });
  tx(head, greet, { fontSize: 20, fontWeight: 600, fill: solid(T.ink) });
  box(root, { width: 1, height: 16 });
  const grid = box(root, { width: 'fill_container', layout: 'vertical', gap: 16 });
  for (const r of rows) {
    const row = box(grid, { width: 'fill_container', height: r.h, layout: 'horizontal', gap: 16 });
    for (const [w, span] of r.items) w(row, r.h, span === 12 ? 'fill_container' : spanW(span));
  }
  return K;
}
// 标题行：16/600 + 期间 + 帮助 + 右侧（次要色链接或说明）
function wHead(T, p, title, o) {
  const { tx, box, icon, link } = kit(T); o = o || {};
  const h = box(p, { width: 'fill_container', height: 24, layout: 'horizontal', gap: 8, alignItems: 'center' });
  tx(h, title, { fontSize: 16, fontWeight: 600, fill: solid(T.ink) });
  if (o.sub) tx(h, o.sub, { fontSize: 13, fill: solid(T.sub) });
  if (o.help) icon(h, 'info', 14, T.sub);
  const r = box(h, { width: 'fill_container', layout: 'horizontal', justifyContent: 'end', gap: 6, alignItems: 'center' });
  if (o.dot) I(r, { type: 'ellipse', width: 6, height: 6, fill: solid(o.dot) });
  if (o.note) tx(r, o.note, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
  if (o.more) link(r, o.more);
  return h;
}
const wCard = (T, title, o, fill) => (row, h, w) => {
  const { card, box } = kit(T);
  const g = box(row, { width: w, height: h, layout: 'vertical', gap: 12 });
  wHead(T, g, title, o);
  const c = card(g, { width: 'fill_container', height: 'fill_container', layout: 'vertical', padding: o && o.inset ? [20, 12] : [20, 20] });
  const body = box(c, { width: 'fill_container', height: 'fill_container', layout: 'vertical' });
  fill(body, w);
};
// 透明数字卡组；cols 为每排张数
const wGroup = (T, title, o, ms, cols) => (row, h, w) => {
  const { box } = kit(T);
  const g = box(row, { width: w, layout: 'vertical', gap: 12 });
  wHead(T, g, title, o);
  const per = cols || ms.length;
  const rows = box(g, { width: 'fill_container', layout: 'vertical', gap: 16 });
  for (let i = 0; i < ms.length; i += per) {
    const mr = box(rows, { width: 'fill_container', layout: 'horizontal', gap: 16 });
    for (const m of ms.slice(i, i + per)) mkMetric(T, mr, ...m);
  }
};

// 风险提醒：全为 0 时中性四格、灰色 0、标题旁成功色圆点「全部正常」；有风险时该格状态浅底 + 左侧色条 + 彩色数量和箭头
const riskCard = (T, alerts) => wCard(T, '风险提醒', alerts ? { dot: T.st.err[0], note: '2 项需要处理 · 截至 16:40' } : { dot: T.key === 'light' ? '#22C55E' : '#22C55E', note: '全部正常 · 截至 16:40' }, (b) => {
  const { tx, box, icon } = kit(T);
  const items = [['file-clock', '过期未收款 PI', 'err'], ['truck', '超期未到货', 'warn'], ['package-x', '到货差异', 'warn'], ['trending-down', '负毛利订单', 'err']];
  const val = alerts || {};
  const g = box(b, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 8 });
  for (const pair of [items.slice(0, 2), items.slice(2)]) {
    const rr = box(g, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', gap: 8 });
    for (const [ic, nm, tn] of pair) {
      const n = val[nm] || 0; const on = n > 0; const tone = T.st[tn];
      const cell = box(rr, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', alignItems: 'center', cornerRadius: 8, clipContent: true, fill: solid(on && T.key === 'light' ? tone[1] : T.tonal) });
      I(cell, { type: 'frame', width: 3, height: 'fill_container', fill: solid(on ? tone[0] : (on && T.key === 'light' ? tone[1] : T.tonal)) });
      const inner = box(cell, { width: 'fill_container', layout: 'horizontal', gap: 12, alignItems: 'center', padding: [0, 12, 0, 9] });
      icon(inner, ic, 18, on ? tone[0] : T.sub, false);
      tx(inner, nm, { fontSize: 13, fontWeight: on ? 600 : 400, fill: solid(on ? T.ink : T.body), width: 'fill_container' });
      tx(inner, String(n), { fontSize: 20, fontWeight: 600, fontFamily: NUM, fill: solid(on ? tone[0] : T.sub) });
      if (on) icon(inner, 'chevron-right', 14, tone[0], false);
    }
  }
});

function bossBoard(T, name) {
  const K = kit(T); const { tx, box, icon, sp, deltaPill } = K;
  // 主角卡：本月成交额 28/600 + 涨跌胶囊 + 12 个月柱图（往期浅灰、只亮本月，虚线网格与刻度，本月柱顶数值）+ 一句结论
  const hero = (row, h, w) => {
    const g = box(row, { width: w, height: h, layout: 'vertical', gap: 12 });
    wHead(T, g, '本月成交额', { sub: '人民币', help: 1, more: '经营统计' });
    const c = K.card(g, { width: 'fill_container', height: 'fill_container', layout: 'vertical', padding: [20, 20] });
    const vr = box(c, { layout: 'horizontal', gap: 4, alignItems: 'end' });
    const cw = box(vr, { layout: 'vertical', padding: [0, 0, 4, 0] }); tx(cw, 'CNY', { fontSize: 16, fontWeight: 600, fontFamily: NUM, fill: solid(T.sub) });
    tx(vr, '412,860.00', { fontSize: 28, fontWeight: 600, fontFamily: NUM, lineHeight: 1.25, fill: solid(T.ink) });
    sp(c, 4); deltaPill(c, '+9.6%');
    sp(c, 16);
    const vals = [0, 0, 0, 0, 0, 0, 18.6, 22.4, 31.2, 26.8, 35.1, 41.3];
    const mon = ['12月', '1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月'];
    const PH = 124; const PW = w - 40 - 40;
    const ch = box(c, { width: 'fill_container', layout: 'horizontal' });
    // Y 轴刻度：文字竖直中心对准对应网格线
    const ya = box(ch, { width: 40, height: PH + 18, layout: 'none' });
    [['50万', 18], ['25万', 18 + PH / 2], ['0', 18 + PH]].forEach(([t, y]) => I(ya, { type: 'text', name: 'fill=' + T.sub, x: 0, y: y - 8, width: 34, content: t, fontSize: 12, fontFamily: NUM, fill: solid(T.sub) }));
    const pc = box(ch, { width: PW, layout: 'vertical' });
    const plot = box(pc, { width: PW, height: PH + 18, layout: 'none' });
    // 网格线：OpenPencil 的 line 节点坐标会走样，原型用 1px 细线示意（实现时为 4 4 虚线）
    for (const y of [18, 18 + PH / 2]) I(plot, { type: 'frame', x: 0, y, width: PW, height: 1, fill: solid(T.grid) });
    I(plot, { type: 'frame', x: 0, y: 18 + PH, width: PW, height: 1, fill: solid(T.grid) });
    const slot = PW / 12; const bw = Math.round(slot * 0.5);
    vals.forEach((v, i) => {
      // 空月份画 2px 中性短柱，表示 0 而不是缺数据
      if (!v) { I(plot, { type: 'frame', x: Math.round(i * slot + (slot - bw) / 2), y: 18 + PH - 2, width: bw, height: 2, fill: solid(T.chartMuted) }); return; }
      const bh = Math.round(v / 50 * PH); const last = i === 11;
      I(plot, { type: 'frame', x: Math.round(i * slot + (slot - bw) / 2), y: 18 + PH - bh, width: bw, height: bh, cornerRadius: [4, 4, 0, 0], fill: solid(last ? P : T.chartMuted) });
      if (last) I(plot, { type: 'text', name: 'fill=' + T.ink, x: Math.round(i * slot) - 2, y: 18 + PH - bh - 18, width: Math.round(slot) + 4, content: '41.3万', fontSize: 12, fontWeight: 600, fontFamily: NUM, textAlign: 'center', fill: solid(T.ink) });
    });
    const xa = box(pc, { width: PW, height: 24, layout: 'horizontal', alignItems: 'center' });
    mon.forEach((m, i) => { const s = box(xa, { width: 'fill_container', layout: 'horizontal', justifyContent: 'center' }); tx(s, m, { fontSize: 12, fontWeight: i === 11 ? 600 : 400, fill: solid(i === 11 ? T.ink : T.sub) }); });
    box(c, { width: 1, height: 'fill_container' });
    tx(c, '本月是近 12 个月最高 · 近 12 个月合计 CNY 1,754,000.00', { fontSize: 13, fontFamily: NUM, fill: solid(T.sub) });
  };
  const overview = wGroup(T, '经营概览', { sub: '本月 · 人民币' }, [
    ['毛利', 'CNY 87,940.00', 'coins', 'purple', { link: 1, delta: '+6.1%' }],
    ['毛利率', '21.30%', 'percent', 'purple', { delta: MINUS + '0.8 个百分点' }],
    ['实收回款', 'CNY 386,420.00', 'hand-coins', 'cyan', { delta: MINUS + '4.3%' }],
    ['费用', 'CNY 42,100.00', 'receipt-text', 'gray', {}]], 2);
  const fund = wGroup(T, '资金', { sub: '截至今天', note: '未结汇外币：没有' }, [
    ['应收未收', 'CNY 7,150.00', 'file-clock', 'cyan', { link: 1, hint: '没有过期未收款' }],
    ['本月到期应付', 'CNY 19,000.00', 'calendar-clock', 'gray', { link: 1, alert: 'err', alertText: '其中已逾期 CNY 19,000.00' }]]);
  // 销售漏斗：每级两行（名称 + 数量 → 比例；8 高细条，不画底轨），比例超过 100% 不显示；底部一句结论
  const funnel = wCard(T, '销售漏斗', { sub: '本月', help: 1 }, (b, w) => {
    const data = [['新增商机', 12, null], ['询盘', 9, '75%'], ['报价', 14, null], ['PI', 6, '43%'], ['成交订单', 4, '67%']];
    const BW = w - 40;
    data.forEach(([l, v, rate], i) => {
      if (i) sp(b, 14);
      const r1 = box(b, { width: 'fill_container', layout: 'horizontal', alignItems: 'center', gap: 8 });
      tx(r1, l, { fontSize: 13, fill: solid(T.body), width: 'fill_container' });
      tx(r1, String(v), { fontSize: 14, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
      if (rate) tx(r1, '→ ' + rate, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
      sp(b, 8);
      I(b, { type: 'frame', width: Math.round(BW * v / 14), height: 8, cornerRadius: 4, fill: solid(i === data.length - 1 ? P : T.barMuted) });
    });
    box(b, { width: 1, height: 'fill_container' });
    tx(b, '本月新增商机 12 个，成交订单 4 张', { fontSize: 13, fill: solid(T.sub) });
  });
  // 排行：列表而非表格；头像（业务员首字 / 客户圆形国旗）+ 名称 + 4 高占比条（对第一名）+ 右列两行
  const rank = wCard(T, '排行', { sub: '本月' }, (b, w) => {
    const two = box(b, { width: 'fill_container', layout: 'horizontal', gap: 32 });
    const colW = (w - 40 - 32) / 2;
    const list = (title, data, avatar) => {
      const t = box(two, { width: 'fill_container', layout: 'vertical' });
      const th = box(t, { width: 'fill_container', height: 20, layout: 'horizontal', alignItems: 'center' });
      tx(th, title, { fontSize: 13, fontWeight: 600, fill: solid(T.body), width: 'fill_container' }); tx(th, '成交额', { fontSize: 12, fill: solid(T.sub) });
      sp(t, 8);
      const max = data[0][1];
      data.forEach(([nm, amt, second, cc]) => {
        const r = box(t, { width: 'fill_container', height: 46, layout: 'horizontal', gap: 12, alignItems: 'center' });
        avatar(r, nm, cc);
        const m = box(r, { width: 'fill_container', layout: 'vertical', gap: 4 });
        tx(m, nm, { fontFamily: NUM, fill: solid(T.ink) });
        I(m, { type: 'frame', width: Math.max(4, Math.round((colW - 28 - 12 - 120 - 12) * amt / max)), height: 4, cornerRadius: 2, fill: solid(T.barMuted) });
        const rc = box(r, { width: 120, layout: 'vertical', gap: 2, alignItems: 'end' });
        tx(rc, 'CNY ' + String(amt).replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.00', { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
        tx(rc, second, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
      });
    };
    const person = (p, nm) => { const a = box(p, { width: 28, height: 28, cornerRadius: 14, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.tonal) }); tx(a, nm[0], { fontSize: 12, fontWeight: 600, fill: solid(T.sub) }); };
    list('业务员', [['林熙', 186420, '毛利率 24.10%'], ['Kevin', 132800, '毛利率 21.65%'], ['Lily', 61240, '毛利率 19.80%'], ['周宁', 22600, '毛利率 27.30%'], ['陈默', 9800, '毛利率 18.20%']], person);
    list('客户', [['Müller Automation GmbH', 98400, '3 单', 'DE'], ['Kansai Robotics Co., Ltd.', 76250, '2 单', 'JP'], ['Électro Industrie SARL', 54900, '2 单', 'FR'], ['Automazione Rossi S.r.l.', 31600, '1 单', 'IT'], ['Müller Automation GmbH', 12300, '1 单', 'DE']], (p, nm, cc) => flag(T, p, cc, 28));
  });
  dash(T, name, 1080, [
    { h: TIER.M, items: [[hero, 6], [overview, 6]] },
    { h: TIER.S, items: [[fund, 6], [riskCard(T), 6]] },
    { h: TIER.M, items: [[funnel, 4], [rank, 8]] },
  ], '晚上好，王敏');
}

function salesBoard(T, name) {
  const K = kit(T); const { tx, box, icon, hr, label, sp } = K;
  // 我的待办（主角）：5 行每行 56，数量 20/600，第 2 行为悬停态
  const todo = wCard(T, '我的待办', { note: '共 16 件', inset: 1 }, (b) => {
    const tone = { info: T.st.info, ok: T.st.ok, blue: T.fam.blue, err: T.st.err };
    const todos = [['inbox', 'info', '待确认的询盘', 'AI 解析完成，等你核对型号', 3], ['file-text', 'ok', '可以报价', '采购已回价，还没做报价单', 5], ['wallet', 'blue', 'PI 待收款', '已发给客户，还没付清', 4], ['rocket', 'blue', '订单待出运', '货已到齐，等安排交货代', 3], ['alarm-clock', 'err', '售后要更新客户', '已逾期或 1 小时内到期的售后单', 1, 1]];
    todos.forEach(([ic, tn, t, d, n, alert], i) => {
      const r = box(b, { width: 'fill_container', height: 52, layout: 'horizontal', gap: 12, alignItems: 'center', padding: [0, 8], cornerRadius: 8, fill: solid(i === 1 ? T.rowHover : T.card) });
      K.iconBlock(r, ic, tone[tn]);
      const tc = box(r, { width: 'fill_container', layout: 'vertical', gap: 2 }); tx(tc, t, { fontWeight: 600, fill: solid(T.ink) }); tx(tc, d, { fontSize: 12, fill: solid(T.sub) });
      tx(r, String(n), { fontSize: 20, fontWeight: 600, fontFamily: NUM, fill: solid(alert ? T.st.err[0] : T.ink) });
      icon(r, 'chevron-right', 16, T.faint, false);
    });
  });
  // 过期未收款的 PI：顶部汇总（28/600 错误色）+ 明细（PI 号墨色、国旗 + 客户、金额、已过期标签）
  const pis = wCard(T, '过期未收款的 PI', { more: '查看全部 3' }, (b) => {
    const sm = box(b, { width: 'fill_container', height: 48, layout: 'horizontal', gap: 8, alignItems: 'end', padding: [0, 0, 12, 0] });
    tx(sm, '3', { fontSize: 28, fontWeight: 600, fontFamily: NUM, lineHeight: 1.2, fill: solid(T.st.err[0]) });
    const smt = box(sm, { layout: 'vertical', padding: [0, 0, 4, 0] }); tx(smt, '张 · 合计 USD 18,600.00、EUR 2,400.00', { fontSize: 13, fontFamily: NUM, fill: solid(T.sub) });
    const data = [['PI20261102006', 'Müller Automation GmbH', 'DE', 'USD 9,800.00', 18], ['PI20261105002', 'Automazione Rossi S.r.l.', 'IT', 'EUR 2,400.00', 12], ['PI20261109004', 'Électro Industrie SARL', 'FR', 'USD 8,800.00', 6]];
    data.forEach(([no, cu, cc, amt, d]) => {
      hr(b);
      const r = box(b, { width: 'fill_container', height: 56, layout: 'horizontal', gap: 12, alignItems: 'center' });
      const l = box(r, { width: 'fill_container', layout: 'vertical', gap: 4 }); tx(l, no, { fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
      const cl = box(l, { layout: 'horizontal', gap: 6, alignItems: 'center' }); flag(T, cl, cc); tx(cl, cu, { fontSize: 12, fontFamily: NUM, fill: solid(T.sub) });
      tx(r, amt, { fontWeight: 600, fontFamily: NUM, fill: solid(T.ink) });
      label(r, '已过期 ' + d + ' 天', T.st.err);
    });
  });
  dash(T, name, 910, [
    { h: TIER.M, items: [[todo, 6], [pis, 6]] },
    { h: TIER.S, items: [[wGroup(T, '我的业绩', { sub: '本月', help: 1 }, [
      ['成交额', 'CNY 412,860.00', 'handshake', 'blue', { link: 1, delta: '+9.6%' }],
      ['毛利', 'CNY 87,940.00', 'coins', 'purple', { delta: '+6.1%' }],
      ['实收回款', 'CNY 386,420.00', 'hand-coins', 'cyan', { delta: MINUS + '4.3%' }],
      ['成交订单', '26', 'shopping-bag', 'blue', { link: 1, delta: '+18.2%' }]]), 12]] },
    { h: TIER.S, items: [
      [wGroup(T, '询价进度', { help: 1 }, [
        ['等采购回价的型号', '14', 'hourglass', 'info', { link: 1, hint: '来自 4 张询盘' }],
        ['已过报价截止', '1', 'calendar-x', 'gray', { alert: 'err', alertText: '截止日期已过，还在询价' }]]), 5],
      [wGroup(T, '我的商机', { sub: '本月' }, [
        ['本月新增商机', '12', 'user-plus', 'blue', { link: 1, hint: '' }],
        ['有效商机', '8', 'badge-check', 'blue', { hint: '有效率 66.7%', bar: 0.667 }],
        ['赢单', '2', 'trophy', 'blue', { hint: '' }]]), 7]] },
  ], '下午好，Kevin');
}

// 风险提醒两种状态并排（同一张卡在全为 0 与有风险时）
function riskStates(T, name) {
  const K = kit(T); const { box, cap } = K;
  const root = I(null, { type: 'frame', name, width: 1220, height: 280, layout: 'vertical', gap: 16, padding: [24, 24], fill: solid(T.page) });
  cap(root, '风险提醒：全为 0（左）与有风险（右），格子位置不变');
  const row = box(root, { width: 'fill_container', height: TIER.S, layout: 'horizontal', gap: 16 });
  riskCard(T)(row, TIER.S, 'fill_container');
  riskCard(T, { '过期未收款 PI': 2, '超期未到货': 1 })(row, TIER.S, 'fill_container');
}

bossBoard(LIGHT, 'H1 工作台 · 老板 · 浅色');
bossBoard(DARK, 'H2 工作台 · 老板 · 深色');
riskStates(LIGHT, 'H1b 风险提醒两种状态 · 浅色');
salesBoard(LIGHT, 'H3 工作台 · 业务员 · 浅色');
salesBoard(DARK, 'H4 工作台 · 业务员 · 深色');
