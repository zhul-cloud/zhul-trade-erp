// 外壳原型：侧栏、顶栏、头像菜单、主题设置；令牌与组件函数与主题色对比.js 相同
const CN = 'Noto Sans SC';
const NUM = 'Public Sans';
const MONO = 'Roboto Mono';
const solid = (c) => [{ type: 'solid', color: c }];
const P = '#FDA92D';
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 3, blur: 1, spread: -2, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 2, blur: 2, spread: 0, color: 'rgba(145,158,171,0.14)' }, { type: 'shadow', offsetX: 0, offsetY: 1, blur: 5, spread: 0, color: 'rgba(145,158,171,0.12)' }];
const LIGHT = { key: 'light', page: '#F4F6F8', card: '#FFFFFF', head: '#F9FAFB', line: '#E5E8EB', field: '#D4D6D9', ink: '#212B36', body: '#3E4750', sub: '#637381', faint: '#919EAB', navOn: '#EDEEEF', soft: '#FEF5E6', tonal: '#F7F8FA', shadow: SH, stroke: null,
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

// 6 种主题色预设，取值见 design.md 第 2 节预设表
const PRESETS = [
  { n: '琥珀', c: '#FDA92D', def: 1, soft: '#FFF5E6', dl: '#FDA92D', ds: '#3E311D', drop: '#B87A14', note: '与警告色相近，警告靠图标和浅底标签区分' },
  { n: '青绿', c: '#0D9394', soft: '#E2F2F2', dl: '#2FA2A3', ds: '#182D2E', drop: '#2F9E99', note: '与成功色同为绿系，成功标签靠浅底区分' },
  { n: '天蓝', c: '#078DEE', soft: '#E1F1FD', dl: '#2098F0', ds: '#172C3C', drop: '#3B82F6', note: '与信息色同为蓝绿系，信息标签靠浅底区分' },
  { n: '紫', c: '#7635DC', soft: '#EFE7FB', dl: '#A77EE9', ds: '#291E39', drop: '#3B82F6', note: '与各状态色都分得开' },
  { n: '宝蓝', c: '#2065D1', soft: '#E4EDF9', dl: '#5E94E6', ds: '#1B2637', drop: '#3B82F6', note: '与各状态色都分得开' },
  { n: '红', c: '#FF3030', soft: '#FFE6E6', dl: '#FF5959', ds: '#3F1E1E', drop: '#E8664A', note: '与错误色、涨跌色里的红都相近，不另加提示' },
];

// ———— 外壳：入口以现有代码为准（TopBar、SidebarUser + AvatarDropdown、NotificationBell、ThemeToggle、LangDropdown、PageTabs）
// 照参考站：收起按钮放在侧栏 Logo 右侧；顶栏左侧标签页，右侧主题设置、语言、通知、头像；侧栏用户卡并入头像菜单。
// 外观（Logo、站点名）全平台统一，公司名是唯一能看出当前所在公司的地方，常显在站点名下方，头像菜单里也写。
// 站点名、公司名、姓名过长时单行省略，悬停显示全文。
const NAV = [['layout-dashboard', '工作台', 'top'], ['briefcase', '销售管理', 'open'], ['', '客户跟进'], ['', '询盘管理'], ['', '报价管理'], ['', '形式发票'], ['', '销售订单', 'on'], ['', '售后管理'], ['shopping-cart', '采购管理', 'grp'], ['warehouse', '仓库管理', 'grp'], ['truck', '物流管理', 'grp'], ['wallet', '财务管理', 'grp'], ['package', '商品管理', 'grp'], ['bar-chart-3', '统计分析', 'grp']];
const MENU_SH = [{ type: 'shadow', offsetX: 0, offsetY: 8, blur: 16, spread: 0, color: 'rgba(145,158,171,0.24)' }];
const TABS3 = [['工作台', 0, 'home'], ['销售订单', 1], ['SO20261119004', 0]];

function tones(T, pr) {
  pr = pr || PRESETS[0];
  return { C: pr.c, L: T.key === 'light' ? pr.c : pr.dl, S: T.key === 'light' ? pr.soft : pr.ds };
}

// 弹出层（菜单、气泡卡）：浅色白底加阴影；深色 #222222 底加 1px #3A3A3A 边，和下面的卡片分开
const PB = (T) => (T.key === 'light' ? T.card : '#222222');
function popup(T, parent, o) {
  const st = T.key === 'light' ? { fill: solid(T.card), effects: MENU_SH } : { fill: solid('#222222'), effects: [], stroke: { thickness: 1, fill: solid('#3A3A3A') } };
  return I(parent, Object.assign({ type: 'frame', height: 'fit_content', layout: 'vertical', cornerRadius: 8 }, st, o));
}

function logo(T, p, C) {
  const { tx, box } = kit(T);
  const l = box(p, { width: 30, height: 30, cornerRadius: 8, fill: solid(C), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  tx(l, '烛', { fontSize: 15, fontWeight: 700, fill: solid('#FFFFFF') });
}

// 顶栏图标按钮：只有图标，悬停显示名称；pressed = 弹出层打开时的按下态
function iconBtn(T, p, n, pressed) {
  const { box, icon } = kit(T);
  const b = box(p, { width: 36, height: 36, cornerRadius: 18, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(pressed ? T.navOn : T.card) });
  icon(b, n, 20, T.body, false);
  return b;
}

// 页签：撑满顶栏 64 高，文字垂直居中，选中时底部 2px 主题色线；关闭按钮图标 14、可点区域 24
function tab(T, p, l, on, o) {
  o = o || {};
  const { tx, box, icon } = kit(T);
  const { L } = tones(T);
  const t = box(p, { height: 'fill_container', layout: 'vertical' });
  const tt = box(t, { height: 'fill_container', layout: 'horizontal', gap: 4, alignItems: 'center', padding: [0, o.home ? 16 : 8, 0, 16] });
  if (o.home) icon(tt, 'house', 14, on ? L : T.body);
  tx(tt, l, { fontWeight: 500, fontFamily: /^[A-Z]{2}\d/.test(l) ? NUM : CN, fill: solid(on ? L : T.body) });
  if (o.dirty) { const dw = box(tt, { layout: 'horizontal', padding: [0, 0, 0, 4] }); I(dw, { type: 'ellipse', width: 6, height: 6, fill: solid(T.body) }); }
  if (!o.home) { const x = box(tt, { width: 24, height: 24, cornerRadius: 12, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }); icon(x, 'x', 14, on ? L : T.sub, false); }
  I(t, { type: 'frame', width: 'fill_container', height: 2, fill: solid(on ? L : T.card) });
}

function brandBlock(T, side, C, L) {
  const { tx, box, icon } = kit(T);
  // Logo 中心与下方菜单图标中心对齐
  const brand = box(side, { width: 'fill_container', layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 0, 14, 9] });
  logo(T, brand, C);
  const nm = box(brand, { width: 'fill_container', layout: 'vertical', gap: 2 });
  tx(nm, '烛龙ERP', { fontSize: 20, fontWeight: 700, fill: solid(L) });
  tx(nm, '示例贸易有限公司', { fontSize: 12, fill: solid(T.sub) });
  const cb = box(brand, { width: 32, height: 32, cornerRadius: 16, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  icon(cb, 'panel-left-close', 18, T.sub, false);
}

function shell(T, name, o) {
  o = o || {};
  const { tx, box, icon, card } = kit(T);
  const { C, L, S } = tones(T);
  const root = I(null, { type: 'frame', name, width: 1440, height: 900, layout: 'none', fill: solid(T.page), clipContent: true });
  // 弹出层先插入：OpenPencil 里先插入的节点在上层
  if (o.userMenu) userMenu(T, root, 1148, 88);
  if (o.theme) themePanel(T, root, 936, 88, { mode: T.key === 'light' ? 1 : 2 });
  const frame = box(root, { x: 0, y: 0, width: 1440, height: 900, layout: 'horizontal', gap: 16, padding: [16, 16, 16, 16] });

  if (!o.collapsed) {
    const side = card(frame, { width: 244, height: 'fill_container', layout: 'vertical', gap: 2, padding: [16, 14] });
    brandBlock(T, side, C, L);
    for (const [ic, l, k] of NAV) {
      const isSub = !ic; const on = k === 'on'; const open = k === 'open';
      const row = box(side, { width: 'fill_container', height: 40, layout: 'horizontal', gap: 12, alignItems: 'center', padding: isSub ? [0, 12, 0, 16] : [0, 12, 0, 14], cornerRadius: 8, fill: solid(on ? S : open ? T.navOn : T.card) });
      if (isSub) icon(row, on ? 'circle-dot' : 'circle', 12, on ? L : T.faint);
      else icon(row, ic, 20, open ? L : T.body);
      tx(row, l, { fontSize: 16, fill: solid(on || open ? L : T.body), width: 'fill_container' });
      if (open) icon(row, 'chevron-down', 18, L); else if (k === 'grp') icon(row, 'chevron-right', 18, T.sub);
    }
  } else {
    // 收起：64 宽图标栏，Logo 下方是展开按钮；当前所在的菜单组为主题色浅底；悬停 Logo 显示站点名和公司名
    const side = card(frame, { width: 64, height: 'fill_container', layout: 'vertical', gap: 4, alignItems: 'center', padding: [16, 0] });
    logo(T, side, C);
    const eb = box(side, { width: 40, height: 40, cornerRadius: 20, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
    icon(eb, 'panel-left-open', 18, T.sub, false);
    I(side, { type: 'frame', width: 32, height: 1, fill: solid(T.line) });
    for (const [ic, , k] of NAV) {
      if (!ic) continue;
      const on = k === 'open';
      const b = box(side, { width: 40, height: 40, cornerRadius: 8, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(on ? S : T.card) });
      icon(b, ic, 20, on ? L : T.body, false);
    }
  }

  // 顶栏：左侧标签页，右侧主题设置（色卡图标 swatch-book，不用明暗图标；palette 在 OpenPencil 里渲染成实心块）、语言、通知、头像
  const main = box(frame, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 16 });
  const top = card(main, { width: 'fill_container', height: 64, layout: 'horizontal', alignItems: 'center', gap: 4, padding: [0, 16] });
  for (const [l, on, h] of TABS3) tab(T, top, l, on, { home: !!h });
  const tools = box(top, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', justifyContent: 'end', alignItems: 'center', gap: 8 });
  iconBtn(T, tools, 'swatch-book', o.theme);
  iconBtn(T, tools, 'languages');
  bellBtn(T, tools);
  const ring = box(tools, Object.assign({ width: 40, height: 40, cornerRadius: 20, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }, o.userMenu ? { stroke: { thickness: 2, fill: solid(L) } } : {}));
  const av = box(ring, { width: 36, height: 36, cornerRadius: 18, fill: solid(S), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  tx(av, '王', { fontWeight: 700, fill: solid(L) });

  // 内容区：本图只看外壳，页面内容见各页原型；页脚不变
  const content = box(main, { width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 16 });
  tx(content, '销售订单', { fontSize: 20, fontWeight: 600, fill: solid(T.ink) });
  const ph = card(content, { width: 'fill_container', height: 'fill_container', layout: 'vertical', justifyContent: 'center', alignItems: 'center' });
  tx(ph, '页面内容见各页原型', { fill: solid(T.faint) });
}

// 通知：角标为未处理待办 + 未读知会，12px
function bellBtn(T, p) {
  const { tx, box } = kit(T);
  const bell = box(p, { width: 36, height: 36, layout: 'none' });
  I(bell, { type: 'icon_font', name: 'fill=' + T.body, iconFontName: 'bell', width: 20, height: 20, x: 8, y: 8, fill: solid(T.body) });
  const badge = box(bell, { x: 20, y: 0, height: 18, layout: 'horizontal', alignItems: 'center', padding: [0, 5], cornerRadius: 9, fill: solid(T.st.err[0]), stroke: { thickness: 1.5, fill: solid(T.card) } });
  tx(badge, '3', { fontSize: 12, fontWeight: 600, fontFamily: NUM, fill: solid('#FFFFFF') });
}

// 头像菜单：姓名、角色、公司名，退出登录（点后仍弹现有的确认框）
function userMenu(T, root, x, y) {
  const { tx, box, icon, label, hr } = kit(T);
  const { L, S } = tones(T);
  const m = popup(T, root, { x, y, width: 260, padding: [6, 6] });
  const head = box(m, { width: 'fill_container', layout: 'horizontal', gap: 12, alignItems: 'center', padding: [10, 10, 12, 10] });
  const av = box(head, { width: 40, height: 40, cornerRadius: 20, fill: solid(S), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
  tx(av, '王', { fontSize: 16, fontWeight: 700, fill: solid(L) });
  const info = box(head, { width: 'fill_container', layout: 'vertical', gap: 4 });
  const nr = box(info, { layout: 'horizontal', gap: 8, alignItems: 'center' });
  tx(nr, '王敏', { fontWeight: 600, fill: solid(T.ink) });
  label(nr, '管理员', T.st.neu);
  tx(info, '示例贸易有限公司', { fontSize: 12, fill: solid(T.sub) });
  hr(m);
  const it = box(m, { width: 'fill_container', height: 38, layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 10], cornerRadius: 6 });
  icon(it, 'log-out', 18, T.body);
  tx(it, '退出登录', { fill: solid(T.body) });
}

// 主题设置：模式三选一、6 种主题色、涨跌颜色；都只保存在本机浏览器。选中统一用主题色表示
function themePanel(T, root, x, y, o) {
  const { tx, box, icon } = kit(T);
  const pr = o.preset || PRESETS[0];
  const { L, S } = tones(T, pr);
  const p = popup(T, root, { x, y, width: 340, padding: [16, 16], gap: 16 });
  tx(p, '主题设置', { fontSize: 16, fontWeight: 500, fill: solid(T.ink) });
  const sec = (t) => { const s = box(p, { width: 'fill_container', layout: 'vertical', gap: 8 }); tx(s, t, { fontSize: 13, fontWeight: 500, fill: solid(T.sub) }); return s; };
  // 模式：分段选择，选中项为主题色浅底、主题色字
  const seg = box(sec('模式'), { width: 'fill_container', layout: 'horizontal', gap: 2, padding: [3, 3], cornerRadius: 8, fill: solid(T.tonal) });
  [['monitor', '跟随系统'], ['sun', '浅色'], ['moon', '深色']].forEach(([ic, t], i) => {
    const on = i === o.mode;
    const b = box(seg, { width: 'fill_container', height: 32, layout: 'horizontal', gap: 6, justifyContent: 'center', alignItems: 'center', cornerRadius: 6, fill: solid(on ? S : T.tonal) });
    icon(b, ic, 16, on ? L : T.sub);
    tx(b, t, { fontSize: 13, fontWeight: on ? 500 : 400, fill: solid(on ? L : T.sub) });
  });
  // 主题色：圆形色块，选中的带白色对勾和外圈
  const sw = box(sec('主题色'), { width: 'fill_container', layout: 'horizontal', gap: 4 });
  for (const q of PRESETS) {
    const on = q.n === pr.n;
    const col = box(sw, { width: 'fill_container', layout: 'vertical', gap: 6, alignItems: 'center' });
    const ring = box(col, Object.assign({ width: 36, height: 36, cornerRadius: 18, layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }, on ? { stroke: { thickness: 2, fill: solid(T.key === 'light' ? q.c : q.dl) } } : {}));
    const dot = box(ring, { width: 28, height: 28, cornerRadius: 14, fill: solid(q.c), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' });
    if (on) icon(dot, 'check', 16, '#FFFFFF', false);
    tx(col, q.n, { fontSize: 12, fill: solid(on ? T.ink : T.sub) });
  }
  // 涨跌颜色：二选一，默认涨红跌绿放左边；选中靠单选圆点，卡片用中性边框，避免红色主题下像报错
  const dsec = sec('涨跌颜色');
  const opts = box(dsec, { width: 'fill_container', layout: 'horizontal', gap: 8 });
  for (const [t, upC, downC, on] of [['涨红跌绿', T.st.err[0], T.st.ok[0], !o.greenUp], ['涨绿跌红', T.st.ok[0], T.st.err[0], !!o.greenUp]]) {
    const c = box(opts, { width: 'fill_container', layout: 'vertical', gap: 8, padding: [10, 12], cornerRadius: 8, fill: solid(on ? T.tonal : PB(T)), stroke: { thickness: 1, fill: solid(on ? T.field : T.line) } });
    const h = box(c, { layout: 'horizontal', gap: 6, alignItems: 'center' });
    const rd = box(h, { width: 16, height: 16, cornerRadius: 8, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', stroke: { thickness: 1.5, fill: solid(on ? L : T.faint) } });
    if (on) I(rd, { type: 'ellipse', width: 8, height: 8, fill: solid(L) });
    tx(h, t, { fontSize: 13, fontWeight: 500, fill: solid(T.ink) });
    const r = box(c, { layout: 'horizontal', gap: 10, alignItems: 'center' });
    for (const [ic, v, cc] of [['trending-up', '+12.5%', upC], ['trending-down', '-3.7%', downC]]) {
      const d = box(r, { layout: 'horizontal', gap: 3, alignItems: 'center' });
      icon(d, ic, 14, cc); tx(d, v, { fontSize: 12, fontFamily: NUM, fill: solid(cc) });
    }
  }
}

// 外壳状态：焦点与悬停名称、跳到主要内容、语言下拉、收起侧栏的悬停子菜单、标签放不下与全部标签
function shellStates(T, name) {
  const { tx, box, icon, card, hr } = kit(T);
  const { C, L, S } = tones(T);
  const root = I(null, { type: 'frame', name, width: 1440, height: 760, layout: 'vertical', gap: 20, padding: [24, 24], fill: solid(T.page) });
  const cap = (p, t) => tx(p, t, { fontSize: 13, fontWeight: 500, fill: solid(T.sub) });
  const tip = (p, t) => { const b = box(p, { layout: 'horizontal', padding: [6, 10], cornerRadius: 8, fill: solid('#212B36') }); tx(b, t, { fontSize: 13, fill: solid('#FFFFFF') }); };

  const r1 = box(root, { width: 'fill_container', layout: 'horizontal', gap: 20, alignItems: 'start' });
  // 1 键盘焦点 + 悬停名称
  const a = box(r1, { width: 300, layout: 'vertical', gap: 10 });
  cap(a, '图标按钮：键盘焦点与悬停名称');
  const ac = card(a, { width: 'fill_container', layout: 'vertical', gap: 8, padding: [16, 16], alignItems: 'center' });
  const tl = box(ac, { layout: 'horizontal', gap: 8, alignItems: 'center' });
  const fo = box(tl, { width: 44, height: 44, cornerRadius: 22, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid('#637381') });
  const fi = box(fo, { width: 40, height: 40, cornerRadius: 20, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(T.card), stroke: { thickness: 2, fill: solid(L) } });
  icon(fi, 'swatch-book', 20, T.body, false);
  iconBtn(T, tl, 'languages'); bellBtn(T, tl);
  tip(ac, '主题设置');
  // 2 跳到主要内容：Tab 第一下出现在页面左上角
  const b = box(r1, { width: 260, layout: 'vertical', gap: 10 });
  cap(b, '跳到主要内容（按 Tab 时出现）');
  const bc = card(b, { width: 'fill_container', layout: 'vertical', padding: [16, 16] });
  const so = box(bc, { layout: 'horizontal', padding: [2, 2], cornerRadius: 10, fill: solid('#637381') });
  const si = box(so, { layout: 'horizontal', gap: 6, alignItems: 'center', height: 36, padding: [0, 14], cornerRadius: 8, fill: solid(T.card), stroke: { thickness: 2, fill: solid(L) } });
  icon(si, 'corner-down-right', 16, T.ink); tx(si, '跳到主要内容', { fontWeight: 500, fill: solid(T.ink) });
  // 3 语言下拉：不用国旗，当前语言带主题色对勾
  const c = box(r1, { width: 220, layout: 'vertical', gap: 10 });
  cap(c, '语言');
  const lm = popup(T, c, { width: 'fill_container', padding: [6, 6] });
  for (const [t, on] of [['简体中文', 1], ['繁體中文'], ['English'], ['日本語'], ['Português'], ['Bahasa Indonesia'], ['فارسی'], ['বাংলা']]) {
    const it = box(lm, { width: 'fill_container', height: 36, layout: 'horizontal', gap: 8, alignItems: 'center', padding: [0, 10], cornerRadius: 6, fill: solid(on ? S : PB(T)) });
    tx(it, t, { fill: solid(on ? L : T.body), width: 'fill_container' });
    if (on) icon(it, 'check', 16, L, false);
  }
  // 4 收起侧栏：悬停菜单组弹出子菜单
  const d = box(r1, { width: 'fill_container', layout: 'vertical', gap: 10 });
  cap(d, '收起侧栏：悬停菜单组显示子菜单');
  const dr = box(d, { layout: 'horizontal', gap: 8, alignItems: 'start' });
  const rail = card(dr, { width: 64, layout: 'vertical', gap: 4, alignItems: 'center', padding: [12, 0] });
  for (const [ic, on] of [['layout-dashboard'], ['briefcase', 1], ['shopping-cart'], ['warehouse']]) {
    const bb = box(rail, { width: 40, height: 40, cornerRadius: 8, layout: 'horizontal', justifyContent: 'center', alignItems: 'center', fill: solid(on ? S : T.card) });
    icon(bb, ic, 20, on ? L : T.body, false);
  }
  const fw = box(dr, { layout: 'vertical', padding: [56, 0, 0, 0] });
  const fm = popup(T, fw, { width: 200, padding: [6, 6] });
  tx(box(fm, { width: 'fill_container', padding: [6, 10] }), '销售管理', { fontSize: 13, fontWeight: 500, fill: solid(T.sub) });
  for (const [t, on] of [['客户跟进'], ['询盘管理'], ['报价管理'], ['形式发票'], ['销售订单', 1], ['售后管理']]) {
    const it = box(fm, { width: 'fill_container', height: 36, layout: 'horizontal', gap: 10, alignItems: 'center', padding: [0, 10], cornerRadius: 6, fill: solid(on ? S : PB(T)) });
    icon(it, on ? 'circle-dot' : 'circle', 12, on ? L : T.faint); tx(it, t, { fill: solid(on ? L : T.body) });
  }

  // 5 标签放不下：1366 宽、开满 10 个；右端渐隐，末尾「全部标签」按钮，下拉里列出全部并显示数量
  const e = box(root, { width: 'fill_container', layout: 'vertical', gap: 10 });
  cap(e, '标签放不下（1366 宽、开满 10 个）：超出部分截断，「全部标签」按钮显示没露出来的数量，下拉列出全部；未保存的标签带圆点');
  const er = box(e, { width: 'fill_container', layout: 'horizontal', gap: 16, alignItems: 'start' });
  const bar = card(er, { width: 1074, height: 64, layout: 'horizontal', alignItems: 'center', gap: 8, padding: [0, 16] });
  const strip = box(bar, { width: 'fill_container', height: 'fill_container', layout: 'horizontal', gap: 4, clipContent: true });
  const many = [['工作台', 0, 'home'], ['销售订单', 0], ['SO20261119004', 1, 0, 1], ['采购订单'], ['PO20261118012'], ['收款登记'], ['客户档案'], ['询盘管理'], ['报价管理'], ['形式发票']];
  for (const [l, on, h, dirty] of many) tab(T, strip, l, !!on, { home: !!h, dirty: !!dirty });
  const all = box(bar, { height: 32, layout: 'horizontal', gap: 2, alignItems: 'center', padding: [0, 6, 0, 10], cornerRadius: 16, fill: solid(T.navOn) });
  tx(all, '+4', { fontSize: 13, fontWeight: 600, fontFamily: NUM, fill: solid(T.body) });
  icon(all, 'chevron-down', 16, T.body, false);
  I(bar, { type: 'frame', width: 1, height: 24, fill: solid(T.line) });
  iconBtn(T, bar, 'swatch-book'); iconBtn(T, bar, 'languages'); bellBtn(T, bar);
  const av2 = box(bar, { width: 36, height: 36, cornerRadius: 18, fill: solid(S), layout: 'horizontal', justifyContent: 'center', alignItems: 'center' }); tx(av2, '王', { fontWeight: 700, fill: solid(L) });
  const tm = popup(T, er, { width: 'fill_container', padding: [6, 6] });
  tx(box(tm, { width: 'fill_container', padding: [6, 10] }), '已打开 10 / 10，再打开会关掉最久没用的标签', { fontSize: 12, fill: solid(T.sub), width: 'fill_container', textGrowth: 'fixed-width' });
  for (const [l, on, h, dirty] of many.slice(0, 6)) {
    const it = box(tm, { width: 'fill_container', height: 34, layout: 'horizontal', gap: 8, alignItems: 'center', padding: [0, 10], cornerRadius: 6, fill: solid(on ? S : PB(T)) });
    tx(it, l, { fontFamily: /^[A-Z]{2}\d/.test(l) ? NUM : CN, fill: solid(on ? L : T.body), width: 'fill_container' });
    if (dirty) I(it, { type: 'ellipse', width: 6, height: 6, fill: solid(T.body) });
  }
  tx(box(tm, { width: 'fill_container', padding: [4, 10] }), '……', { fill: solid(T.faint) });
}

shell(LIGHT, 'F1 外壳 · 浅色 · 头像菜单', { userMenu: 1 });
shell(DARK, 'F2 外壳 · 深色 · 头像菜单', { userMenu: 1 });
shell(LIGHT, 'F1b 外壳 · 浅色 · 收起侧栏与主题设置', { collapsed: 1, theme: 1 });
shell(DARK, 'F2b 外壳 · 深色 · 收起侧栏与主题设置', { collapsed: 1, theme: 1 });
shellStates(LIGHT, 'F1d 外壳状态 · 浅色');
