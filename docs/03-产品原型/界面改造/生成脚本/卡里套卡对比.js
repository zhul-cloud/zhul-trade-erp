// 卡里套卡与字典管理左栏对比图（R8）：字典左栏、销售订单收款卡、销售统计漏斗、商品详情页头，现状 / 推荐。单独用 op design 生成。
const C = {
  ink: '#212B36', sub: '#3E4750', mute: '#637381', faint: '#919EAB', line: '#E5E8EB', head: '#F9FAFB', inset: '#F7F8FA',
  card: '#FFFFFF', canvas: '#F4F6F8', primary: '#FDA92D', primarySoft: '#FFF5E6', primaryLine: '#FEDDAB', link: '#B76E00',
  orange: '#9A5B00', orangeSoft: '#FFF5E0', green: '#0D7A4A', greenSoft: '#E3F6EA', red: '#B71D18', redSoft: '#FFEBE6',
  warnBg: '#FFF8E1', warnLine: '#FFE7A3', neutralSoft: '#ECEEF0', bar: '#DFE3E8', control: '#D3D5D8',
};
const F = 'Public Sans', MONO = 'JetBrains Mono';
const fill = (c) => [{ type: 'solid', color: c }];
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: o.font || F, fontSize: o.size || 14, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color || C.sub), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const pill = (p, text, fg, bg, ic) => { const b = H(p, 4, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); if (ic) icon(b, ic, 12, fg); T(b, text, { size: 12, weight: 600, color: fg }); return b; };
const card = (p, o = {}) => V(p, o.gap ?? 12, { width: o.width || 'fill_container', padding: o.pad || [20, 24], cornerRadius: 12, fill: fill(C.card), effects: SH, ...(o.height ? { height: o.height } : {}) });
const sp = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1 });
const hr = (p) => I(p, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(C.line) });
const bar = (p, w, total, color) => { const b = H(p, 0, { width: total, height: 6, cornerRadius: 3, fill: fill(C.neutralSoft) }); if (w > 0) I(b, { type: 'rectangle', width: w, height: 6, cornerRadius: 3, fill: fill(color) }); };

const PW = 620;
let SHB;
const panel = (row, title, note, good) => {
  const col = V(row, 10, { width: PW });
  const h = H(col, 8); T(h, title, { size: 15, weight: 700, color: C.ink }); if (good) pill(h, '推荐', C.green, C.greenSoft);
  if (note) T(col, note, { size: 12, color: C.mute, w: PW });
  return V(col, 16, { width: PW, padding: 20, cornerRadius: 10, fill: fill(C.canvas) });
};
const section = (root, title, why) => {
  const s = V(root, 14);
  T(s, title, { size: 18, weight: 700, color: C.ink });
  T(s, why, { size: 13, color: C.sub, w: PW * 2 + 40 });
  return H(s, 40, { alignItems: 'start' });
};

const root = I(null, { type: 'frame', name: '卡里套卡对比', layout: 'vertical', gap: 44, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '卡里套卡与字典管理左栏：现状 / 推荐（浅色）', { size: 20, weight: 700, color: C.ink });
T(root, '规则（S1）：最多两层——页面底、白卡；卡内最多再有一层无边框的浅灰凹格。卡里的分组用小标题加分隔线，不再套描边卡。同一个状态在一张卡里只出现一次。', { size: 13, color: C.mute, w: PW * 2 + 40 });

// ───────── 1 字典管理左栏 ─────────
{
  const row = section(root, '1　字典管理左栏', '图标 13px 挂在文字上，键盘点不到、点击区不足 24；每行常驻红色删除；「内置」标签与分组标题重复；技术编码每行常显；两个琥珀主按钮并排抢主次。');
  const types = [['订单状态', 'sys_order_status'], ['用户状态', 'sys_user_status'], ['生命周期', 'inquiry_lifecycle'], ['货期', 'inquiry_lead_time'], ['货况', 'inquiry_item_condition'], ['来源渠道', 'crm_source_channel']];
  const shell = (p) => H(p, 0, { width: 'fill_container', height: 430, cornerRadius: 12, fill: fill(C.card), effects: SH, alignItems: 'start', clipContent: true });
  // A
  {
    const pg = panel(row, 'A 现状', '');
    const sh = shell(pg);
    const left = V(sh, 8, { width: 250, height: 'fill_container', padding: 14, stroke: { thickness: 1, fill: fill(C.line), align: 'inside' } });
    const b = H(left, 6, { width: 'fill_container', height: 32, cornerRadius: 6, fill: fill(C.primary), justifyContent: 'center' }); icon(b, 'plus', 14, C.ink); T(b, '新增字典类型', { size: 13, color: C.ink });
    const s = H(left, 6, { width: 'fill_container', height: 32, padding: [0, 10], cornerRadius: 6, stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } }); icon(s, 'search', 14, C.faint); T(s, '搜索字典名称', { size: 13, color: C.faint });
    T(left, '系统内置', { size: 12, weight: 600, color: C.mute });
    for (const [i, [n, k]] of types.slice(0, 5).entries()) {
      const r = H(left, 6, { width: 'fill_container', padding: [5, 8], cornerRadius: 8, fill: i === 0 ? fill(C.primarySoft) : undefined });
      const a = V(r, 0, { width: 'fill_container' }); const t = H(a, 6); T(t, n, { size: 13, weight: i === 0 ? 600 : 500, color: i === 0 ? C.link : C.ink }); pill(t, '内置', C.sub, C.neutralSoft);
      T(a, k, { size: 11, color: C.mute, font: MONO });
      icon(r, 'pencil', 13, C.mute); icon(r, 'trash-2', 13, i === 0 ? C.faint : C.faint);
    }
    const right = V(sh, 10, { width: 'fill_container', padding: 18 });
    const rh = H(right, 8, { width: 'fill_container' }); const rt = V(rh, 0, { width: 'fill_container' }); T(rt, '当前字典：订单状态', { size: 15, weight: 700, color: C.ink }); T(rt, 'sys_order_status', { size: 12, color: C.mute, font: MONO });
    const b2 = H(rh, 6, { height: 32, padding: [0, 12], cornerRadius: 6, fill: fill(C.primary) }); icon(b2, 'plus', 14, C.ink); T(b2, '新增字典项', { size: 13, color: C.ink });
    const tb = V(right, 0, { width: 'fill_container' }); const th = H(tb, 0, { width: 'fill_container', height: 36, fill: fill(C.head), padding: [0, 10] }); T(th, '字典项名称　　字典值　　状态', { size: 12, color: C.mute });
    for (const n of ['待确认', '已确认', '已发货']) { const tr = H(tb, 0, { width: 'fill_container', height: 40, padding: [0, 10] }); T(tr, n, { size: 13, color: C.ink }); }
  }
  // B
  {
    const pg = panel(row, 'B 推荐', '', true);
    SHB = shell(pg); const sh = SHB;
    const left = V(sh, 6, { width: 250, height: 'fill_container', padding: 14, stroke: { thickness: 1, fill: fill(C.line), align: 'inside' } });
    const top = H(left, 8, { width: 'fill_container' }); T(top, '字典类型', { size: 14, weight: 600, color: C.ink }); T(top, '23', { size: 13, color: C.mute }); sp(top);
    const ib = H(top, 0, { width: 32, height: 32, cornerRadius: 6, justifyContent: 'center', stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } }); icon(ib, 'plus', 16, C.sub);
    const s = H(left, 6, { width: 'fill_container', height: 32, padding: [0, 10], cornerRadius: 6, stroke: { thickness: 1, fill: fill(C.control), align: 'inside' } }); icon(s, 'search', 14, C.faint); T(s, '搜索字典名称', { size: 13, color: C.faint });
    const g = H(left, 6, { padding: [6, 8, 0, 8] }); T(g, '系统内置', { size: 12, weight: 600, color: C.mute }); T(g, '18', { size: 12, color: C.faint });
    for (const [i, [n]] of types.entries()) {
      const r = H(left, 6, { width: 'fill_container', height: 36, padding: [0, 4, 0, 10], cornerRadius: 8, fill: i === 0 ? fill(C.primarySoft) : i === 2 ? fill(C.inset) : undefined });
      T(r, n, { size: 14, weight: i === 0 ? 600 : 400, color: i === 0 ? C.link : C.ink }); sp(r);
      if (i === 0 || i === 2) { const m = H(r, 0, { width: 28, height: 28, cornerRadius: 6, justifyContent: 'center', fill: i === 2 ? fill(C.neutralSoft) : undefined }); icon(m, 'ellipsis', 16, C.mute); }
      if (i === 2) menuAt(left);
    }
  }
  function menuAt(left) {
    // 悬停行「…」展开的菜单（示意）
    const wrap = H(left, 0, { width: 'fill_container', justifyContent: 'end' });
    const menu = V(wrap, 2, { width: 168, padding: 6, cornerRadius: 10, fill: fill(C.card), effects: SH });
    const m1 = H(menu, 8, { width: 'fill_container', height: 32, padding: [0, 10], cornerRadius: 6 }); icon(m1, 'pencil', 14, C.sub); T(m1, '编辑', { size: 13, color: C.ink });
    const m2 = H(menu, 8, { width: 'fill_container', height: 32, padding: [0, 10], cornerRadius: 6 }); icon(m2, 'trash-2', 14, C.control); T(m2, '删除', { size: 13, color: C.faint });
    T(menu, '系统内置，不能删除', { size: 12, color: C.mute });
  }
  {
    const sh = SHB;
    const right = V(sh, 10, { width: 'fill_container', padding: 18 });
    const rh = H(right, 8, { width: 'fill_container' }); const rt = V(rh, 0, { width: 'fill_container' }); const tl = H(rt, 8); T(tl, '订单状态', { size: 16, weight: 700, color: C.ink }); pill(tl, '内置', C.sub, C.neutralSoft); T(rt, '编码 sys_order_status', { size: 12, color: C.mute });
    const b2 = H(rh, 6, { height: 32, padding: [0, 12], cornerRadius: 6, fill: fill(C.primary) }); icon(b2, 'plus', 14, C.ink); T(b2, '新增字典项', { size: 13, color: C.ink });
    const tb = V(right, 0, { width: 'fill_container' }); const th = H(tb, 0, { width: 'fill_container', height: 36, fill: fill(C.head), padding: [0, 10] }); T(th, '字典项名称　　字典值　　状态', { size: 12, color: C.mute });
    for (const n of ['待确认', '已确认', '已发货']) { const tr = H(tb, 0, { width: 'fill_container', height: 40, padding: [0, 10] }); T(tr, n, { size: 13, color: C.ink }); }
  }
  I(root, { type: 'text', content: 'B：左栏只放名称（单行 36 高，同屏多一倍）；编辑、删除收进行尾「…」，悬停或选中时出现（点击区 28，键盘可聚焦），内置项的删除置灰并写原因；「内置」只在分组标题和右侧标题旁出现一次；编码挪到右侧标题下（去掉「当前字典：」前缀）；新增类型改成描边图标按钮，整页只留「新增字典项」一个主按钮；进入页面默认选中第一个类型，右侧不再空着。参照 Linear 设置页标签列表、GitHub Labels、Polaris ResourceList：行内操作悬停出现、危险操作收进菜单。', fontFamily: F, fontSize: 13, lineHeight: 1.5, fill: fill(C.sub), width: PW * 2 + 40, textGrowth: 'fixed-width' });
}

// ───────── 2 销售订单详情收款卡 ─────────
{
  const row = section(root, '2　销售订单详情「收款」卡', '卡里套一块灰底进度区，再套一张描边的水单卡（三层）；「待到账」在卡头、进度区、水单上出现 3 次；「剩余」用橙色像告警。');
  // A
  {
    const pg = panel(row, 'A 现状', '');
    const c = card(pg, { width: 360 });
    const h = H(c, 6, { width: 'fill_container' }); T(h, '收款', { size: 16, weight: 700, color: C.ink }); icon(h, 'info', 13, C.faint); pill(h, '待到账', C.orange, C.orangeSoft); sp(h); T(h, '来自 PI20261118001', { size: 12, color: C.mute });
    const ins = V(c, 6, { width: 'fill_container', padding: 14, cornerRadius: 12, fill: fill(C.inset) });
    T(ins, '已到账 USD 0.00 / USD 532.00', { size: 13, weight: 700, color: C.ink });
    const br = H(ins, 8, { width: 'fill_container' }); bar(br, 0, 260, C.primary); T(br, '0%', { size: 12, color: C.mute });
    pill(ins, '待到账', C.orange, C.orangeSoft);
    T(ins, '剩余 USD 532.00', { size: 12, color: C.orange });
    const rc = V(c, 4, { width: 'fill_container', padding: [10, 12], cornerRadius: 12, stroke: { thickness: 1, fill: fill(C.line), align: 'inside' } });
    const r1 = H(rc, 8, { width: 'fill_container' }); icon(r1, 'file-text', 14, C.link); T(r1, '水单', { size: 13, weight: 700, color: C.ink }); T(r1, 'USD 532.00', { size: 13, weight: 700, color: C.ink }); pill(r1, '待到账', C.orange, C.orangeSoft); sp(r1); T(r1, '11-18', { size: 12, color: C.mute });
    T(rc, '银行转账 · 测试管理员上传', { size: 12, color: C.sub });
    const f = H(rc, 4); icon(f, 'paperclip', 12, C.link); T(f, 'slip.png', { size: 12, color: C.link });
  }
  // B
  {
    const pg = panel(row, 'B 推荐', '', true);
    const c = card(pg, { width: 360, gap: 14 });
    const h = H(c, 6, { width: 'fill_container' }); T(h, '收款', { size: 16, weight: 700, color: C.ink }); icon(h, 'info', 13, C.faint); pill(h, '待到账', C.orange, C.orangeSoft); sp(h); T(h, '来自 PI20261118001', { size: 12, color: C.mute });
    const p1 = V(c, 8, { width: 'fill_container' });
    const l1 = H(p1, 4, { width: 'fill_container', alignItems: 'end' }); T(l1, 'USD 0.00', { size: 20, weight: 700, color: C.ink }); T(l1, '/ USD 532.00', { size: 13, color: C.mute }); sp(l1); T(l1, '剩余 USD 532.00', { size: 13, color: C.sub });
    bar(p1, 0, 312, C.primary);
    hr(c);
    const lt = H(c, 6); T(lt, '收款记录', { size: 13, weight: 600, color: C.ink }); T(lt, '1', { size: 13, color: C.mute });
    const rc = V(c, 2, { width: 'fill_container' });
    const r1 = H(rc, 8, { width: 'fill_container' }); icon(r1, 'file-text', 14, C.mute); T(r1, '水单', { size: 13, weight: 600, color: C.ink }); pill(r1, '待到账', C.orange, C.orangeSoft); sp(r1); T(r1, 'USD 532.00', { size: 13, weight: 600, color: C.ink });
    const r2 = H(rc, 6, { width: 'fill_container', padding: [0, 0, 0, 22] }); T(r2, '11-18 · 银行转账 · 测试管理员上传 ·', { size: 12, color: C.mute }); const f = H(r2, 3); icon(f, 'paperclip', 12, C.link); T(f, 'slip.png', { size: 12, color: C.link });
  }
  I(root, { type: 'text', content: 'B：进度区不再包灰底，直接放在卡里（已到账大字 + 合计 + 剩余，一条进度条）；进度条不再自带状态标签，卡头的「待到账」管整单，水单上的「待到账」只管这一张水单；收款记录用小标题 + 分隔线列出，每条两行，不套描边卡；金额右对齐；「剩余」用正文色（没逾期就不是告警）。「按付款方式」同样改成小标题 + 两端对齐的行。参照 Shopify 订单页 Payment 卡、Stripe 付款详情：卡内只用分隔线分组。', fontFamily: F, fontSize: 13, lineHeight: 1.5, fill: fill(C.sub), width: PW * 2 + 40, textGrowth: 'fixed-width' });
}

// ───────── 3 销售统计漏斗 ─────────
{
  const row = section(root, '3　销售统计「漏斗」', '6 个主题色浅底块（主题色当装饰，S4），数字大小一样看不出递减；转化率零散地写在块下面；「（所选期间）」与上方期间筛选重复。');
  const steps = [['商机', 0, ''], ['询盘', 2, '询盘转化 —'], ['报价', 4, ''], ['意向 PI', 2, 'CNY 7,150.00'], ['待付款', 2, 'CNY 7,150.00'], ['成交订单', 1, '订单转化 50%']];
  {
    const pg = panel(row, 'A 现状', '');
    const c = card(pg, { gap: 10 });
    T(c, '漏斗（所选期间）', { size: 14, weight: 700, color: C.ink });
    const r = H(c, 8, { width: 'fill_container', alignItems: 'start' });
    for (const [n, v, cap] of steps) { const s = V(r, 4, { width: 'fill_container' }); T(s, n, { size: 12, color: C.sub }); const b = H(s, 0, { width: 'fill_container', height: 32, padding: [0, 8], cornerRadius: 6, fill: fill(C.primarySoft) }); T(b, String(v), { size: 15, weight: 700, color: C.ink }); if (cap) T(s, cap, { size: 11, color: C.mute }); }
  }
  {
    const pg = panel(row, 'B 推荐', '', true);
    const c = card(pg, { gap: 14 });
    T(c, '销售漏斗', { size: 14, weight: 700, color: C.ink });
    const r = H(c, 0, { width: 'fill_container', alignItems: 'start' });
    const max = 4;
    steps.forEach(([n, v, cap], i) => {
      if (i > 0) { const a = V(r, 0, { width: 22, padding: [22, 0, 0, 0], alignItems: 'center' }); icon(a, 'chevron-right', 14, C.faint); }
      const s = V(r, 4, { width: 'fill_container' });
      T(s, n, { size: 12, color: C.mute });
      T(s, String(v), { size: 22, weight: 700, color: C.ink });
      bar(s, Math.round((v / max) * 70), 70, i === steps.length - 1 ? C.primary : C.bar);
      T(s, cap || ' ', { size: 12, color: C.mute });
    });
  }
  I(root, { type: 'text', content: 'B：各步平铺在一张白卡里，不再包色块；数字放大到 22，下面一条与数量成比例的中性条，只有最后一步「成交订单」用主题色（和工作台漏斗同一种写法）；转化率、金额统一写在条下面一行。标题改为「销售漏斗」，期间看上方筛选即可。参照 Mixpanel / Amplitude 的漏斗：单色条形、只强调终点。', fontFamily: F, fontSize: 13, lineHeight: 1.5, fill: fill(C.sub), width: PW * 2 + 40, textGrowth: 'fixed-width' });
}

// ───────── 4 商品详情页头 ─────────
{
  const row = section(root, '4　商品详情页头', '页头单独包一张白卡，和其他单据详情（页头直接放在页面上）不一致；展示型号为空时标题整块空白；「只能查看」是一整条黄色警告，像出了错。');
  {
    const pg = panel(row, 'A 现状', '');
    const bk = H(pg, 4, { height: 24, padding: [0, 8], cornerRadius: 12, fill: fill(C.primarySoft) }); icon(bk, 'arrow-left', 12, C.link); T(bk, '返回 商品档案', { size: 12, color: C.link });
    const c = card(pg, { gap: 6 });
    const t = H(c, 8); pill(t, '在产', C.green, C.greenSoft); pill(t, '只读', C.sub, C.neutralSoft, 'lock');
    T(c, 'Siemens · PLC & Controllers · S7-1200 CPU 1214C DC/DC/DC', { size: 13, color: C.sub });
    const al = V(pg, 4, { width: 'fill_container', padding: [14, 18], cornerRadius: 12, fill: fill(C.warnBg), stroke: { thickness: 1, fill: fill(C.warnLine), align: 'inside' } });
    const at = H(al, 8); icon(at, 'circle-alert', 16, C.primary); T(at, '该商品由平台维护，只能查看', { size: 14, color: C.ink });
    T(al, '可以在报价、订单中正常使用它；需要补充规格、资料时，可通过资料包确认写入。', { size: 12, color: C.sub, w: 520 });
    const nx = card(pg, { gap: 6 }); T(nx, '资料完整度  1 / 10 项', { size: 14, weight: 700, color: C.ink });
  }
  {
    const pg = panel(row, 'B 推荐', '', true);
    const bk = H(pg, 4, { height: 24, padding: [0, 8], cornerRadius: 12, fill: fill(C.primarySoft) }); icon(bk, 'arrow-left', 12, C.link); T(bk, '返回 商品档案', { size: 12, color: C.link });
    const hd = H(pg, 12, { width: 'fill_container', alignItems: 'start' });
    const l = V(hd, 4, { width: 'fill_container' });
    const t = H(l, 8); T(t, '6ES7214-1AG40-0XB0', { size: 24, weight: 700, color: C.ink, font: MONO }); pill(t, '在产', C.green, C.greenSoft);
    T(l, 'Siemens · PLC & Controllers · S7-1200 CPU 1214C DC/DC/DC', { size: 13, color: C.sub });
    const ro = H(hd, 4, { height: 28, padding: [0, 10], cornerRadius: 14, fill: fill(C.neutralSoft) }); icon(ro, 'lock', 13, C.sub); T(ro, '平台维护 · 只读', { size: 12, color: C.sub }); icon(ro, 'circle-question-mark', 13, C.faint);
    const tip = V(pg, 4, { width: 300, padding: [10, 12], cornerRadius: 8, fill: fill('#1C252E') });
    T(tip, '悬停「?」：可以在报价、订单中正常使用它；需要补充规格、资料时，可通过资料包确认写入。', { size: 12, color: '#FFFFFF', w: 276 });
    const nx = card(pg, { gap: 6 }); T(nx, '资料完整度  1 / 10 项', { size: 14, weight: 700, color: C.ink });
  }
  I(root, { type: 'text', content: 'B：页头和销售订单等详情页一样直接放在页面上，不包卡；标题用展示型号，为空时退回原始型号（这件商品就是这种情况）；只读改成页头右侧一个中性胶囊「平台维护 · 只读」（其他公司维护时写「其他公司维护 · 只读」），说明放进帮助图标，不再占一整条黄色警告——只读是正常状态，不是错误。「已被 N 处引用」的蓝色提示同理并进胶囊旁的说明。参照 GitHub 只读仓库的「Archived」徽标、Polaris Badge：状态用徽标，警告条只留给需要处理的问题。', fontFamily: F, fontSize: 13, lineHeight: 1.5, fill: fill(C.sub), width: PW * 2 + 40, textGrowth: 'fixed-width' });
}
