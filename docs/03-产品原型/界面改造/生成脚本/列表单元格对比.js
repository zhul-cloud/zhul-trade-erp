// 列表单元格写法对比图（R2～R4，2026-11-24 用户按推荐选定：采购需求 C、覆盖矩阵 B、商品候选 B）。
// 不在 build.py 里：单独用 op design @列表单元格对比.js 生成后导出各画板。
const C = {
  ink: '#212B36', sub: '#3E4750', mute: '#637381', line: '#E5E8EB', head: '#F4F6F8', card: '#FFFFFF', canvas: '#F9FAFB',
  orange: '#9A5B00', orangeSoft: '#FFF5E0', red: '#B71D18', redSoft: '#FFEBE6', green: '#0D7A4A', greenSoft: '#E5F8EC',
  info: '#006C9C', infoSoft: '#E0F6FA', neutralSoft: '#ECEEF0', link: '#B76E00', accentSoft: '#FFF5E6',
};
const F = 'Public Sans', MONO = 'JetBrains Mono';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: o.font || F, fontSize: o.size || 14, fontWeight: o.weight || 400, lineHeight: o.lh || (o.size === 12 ? 1.5 : 1.57), fill: fill(o.color || C.sub), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center' });
const pill = (p, text, fg, bg, ic) => { const b = I(p, { type: 'frame', layout: 'horizontal', gap: 4, alignItems: 'center', height: 24, padding: [0, 8], cornerRadius: 4, fill: fill(bg) }); if (ic) I(b, { type: 'icon_font', iconFontName: ic, width: 12, height: 12, fill: fill(fg) }); T(b, text, { size: 12, weight: 600, color: fg }); return b; };
const icon = (p, n, s, c) => I(p, { type: 'icon_font', iconFontName: n, width: s, height: s, fill: fill(c) });
const sub = (p, text, color = C.mute, w) => T(p, text, { size: 12, color, w });
const PADX = 12, PADY = 11;

// 一张小表：cols = [[标题, 宽]...]，rows = [[(cell)=>{} ...]]
const table = (parent, cols, rows) => {
  const W = cols.reduce((a, [, w]) => a + w, 0);
  const t = I(parent, { type: 'frame', layout: 'vertical', width: W + 2, cornerRadius: 10, clipContent: true, fill: fill(C.card), stroke: { thickness: 1, fill: fill(C.line), align: 'inside' } });
  const hd = I(t, { type: 'frame', layout: 'horizontal', height: 48, alignItems: 'center', fill: fill(C.head), width: W });
  for (const [c, w] of cols) { const h = I(hd, { type: 'frame', width: w, padding: [0, PADX], layout: 'horizontal', alignItems: 'center' }); T(h, c, { size: 13, color: C.mute }); }
  rows.forEach((cells) => {
    I(t, { type: 'rectangle', width: W, height: 1, fill: fill(C.line) });
    const row = I(t, { type: 'frame', name: 'row', layout: 'horizontal', alignItems: 'center', width: W, height: 'fit_content', fill: fill(C.card) });
    cells.forEach((fn, i) => { const c = I(row, { type: 'frame', width: cols[i][1], padding: [PADY, PADX], layout: 'vertical', gap: 0 }); fn(c, cols[i][1] - PADX * 2); });
  });
  return t;
};
const variant = (parent, title, desc, cols, rows, extra) => {
  const box = I(parent, { type: 'frame', name: 'V ' + title, layout: 'vertical', gap: 10 });
  T(box, title, { size: 16, weight: 700, color: C.ink, lh: 1.4 });
  T(box, desc, { size: 13, color: C.mute, lh: 1.5, w: cols.reduce((a, [, w]) => a + w, 0) });
  table(box, cols, rows);
  I(box, { type: 'text', name: 'rowh', content: '行高', fontFamily: F, fontSize: 13, fontWeight: 600, fill: fill(C.mute) });
  if (extra) extra(box);
  return box;
};
const section = (name, title, note) => {
  const root = I(null, { type: 'frame', name, layout: 'vertical', gap: 24, padding: [32, 40], width: 'fit_content', fill: fill(C.canvas) });
  T(root, title, { size: 20, weight: 700, color: C.ink, lh: 1.4 });
  T(root, note, { size: 13, color: C.mute });
  return I(root, { type: 'frame', layout: 'horizontal', gap: 40, alignItems: 'start' });
};
const tip = (box, title, lines) => {
  const pop = I(box, { type: 'frame', layout: 'vertical', gap: 6, padding: [10, 12], width: 260, cornerRadius: 8, fill: fill(C.card), stroke: { thickness: 1, fill: fill(C.line), align: 'inside' }, effects: [{ type: 'shadow', offsetX: 0, offsetY: 4, blur: 12, spread: 0, color: 'rgba(0,0,0,0.08)' }] });
  T(pop, title, { size: 12, weight: 600, color: C.ink });
  for (const l of lines) T(pop, l, { size: 12, color: C.sub, w: 236 });
};

// ================= 1 采购需求 =================
{
  const g = section('P1 采购需求', '采购需求列表：三种写法对比', '一行是一个型号的采购需求。现状有三处撑高：型号格三行（型号＋超时标签 / 品牌·品类 / 备注）、状态格的「去下单 → 供应商」折行。规范要求两行内容 60–64。');
  const R = [
    { m: 'E3Z-D61', over: '超时 4 天', bc: 'OMRON · 传感器', rm: 'NPN 输出，线长 2 米', d: '光电开关，漫反射 100mm，NPN', q: '10', qs: '草稿中 10', st: ['草稿中', C.mute, C.neutralSoft], po: '去下单 →', sup: '深圳捷运国际物流有限公司' },
    { m: '6ES7214-1AG40-0XB0', bc: '西门子 · PLC', d: 'CPU 1214C DC/DC/DC', q: '3', qs: '已下单 3', st: ['已下单', C.info, C.infoSoft], po: 'PO20261118001' },
  ];
  const cols = [['型号 · 品牌 · 品类', 230], ['描述', 220], ['数量', 90], ['状态', 170]];
  const modelLine = (c, r, pillStyle) => { const l = H(c, 6); T(l, r.m, { font: MONO, weight: 600, color: C.ink, size: 13 }); if (r.over) { if (pillStyle) pill(l, r.over, C.red, C.redSoft, 'clock'); else { const o = H(l, 3); icon(o, 'clock', 12, C.red); T(o, r.over, { size: 12, weight: 600, color: C.red }); } } };
  const qty = (c, r) => { T(c, r.q, { color: C.ink }); sub(c, r.qs); };
  const status = (c, r, w, wrap) => { pill(c, r.st[0], r.st[1], r.st[2]); const l = H(c, 4); T(l, r.po, { size: 12, color: r.sup ? C.link : C.ink, weight: r.sup ? 400 : 600 }); if (r.sup) T(l, wrap ? r.sup : '深圳捷运国际物流…', { size: 12, color: C.mute, w: wrap ? w - 60 : undefined }); };
  variant(g, 'A 现状', '超时用红色标签、备注单独一行，状态格供应商名折行。', cols, R.map((r) => [
    (c) => { modelLine(c, r, true); sub(c, r.bc); if (r.rm) sub(c, '▸ ' + r.rm, C.orange); },
    (c, w) => sub(c, r.d, C.sub, w),
    (c) => qty(c, r),
    (c, w) => status(c, r, w, true),
  ]));
  variant(g, 'B 备注收成图标', '型号后的超时改红色文字；备注收成品牌后的橙色小图标，悬停看全文（见下）；供应商名单行省略。', cols, R.map((r) => [
    (c) => { modelLine(c, r, false); const l = H(c, 6); sub(l, r.bc); if (r.rm) icon(l, 'message-square-text', 14, C.orange); },
    (c, w) => sub(c, r.d, C.sub, w),
    (c) => qty(c, r),
    (c, w) => status(c, r, w, false),
  ]), (box) => tip(box, '悬停备注图标', ['NPN 输出，线长 2 米']));
  variant(g, 'C 备注挪到描述列（推荐）', '型号格只放型号＋超时、品牌·品类两行；备注接在描述下面一行（橙色「备注：」），不用悬停就能看到；供应商名单行省略。', cols, R.map((r) => [
    (c) => { modelLine(c, r, false); sub(c, r.bc); },
    (c, w) => { sub(c, r.d, C.sub, w); if (r.rm) sub(c, '备注：' + r.rm, C.orange); },
    (c) => qty(c, r),
    (c, w) => status(c, r, w, false),
  ]));
}

// ================= 2 覆盖矩阵·型号 =================
{
  const g = section('P2 覆盖矩阵型号', '供应链覆盖矩阵（型号）：下一步行动列两种写法', '「下一步行动」文字较长时在 220 宽里折成两行，再加日期一行，行高 90。');
  const R = [
    { a: '新进入矩阵，确认覆盖状态', sys: true, due: '11-18 · 逾期 6 天', late: true },
    { a: '联系供应商确认交期', due: '11-26 · 还有 2 天' },
    { a: '等询价任务 SQ1118003 回价', due: '回价后自动登记', task: true },
  ];
  const cols = [['型号', 150], ['覆盖状态', 160], ['下一步行动 · 日期', 220]];
  const base = (r, i) => [
    (c) => T(c, ['LC2D18B7', 'E3Z-D61', '1756-L83E'][i], { font: MONO, size: 13, weight: 600, color: C.ink }),
    (c) => { const l = H(c, 8); pill(l, ['可直接报价', '无渠道', '询价中'][i], [C.green, C.red, C.info][i], [C.greenSoft, C.redSoft, C.infoSoft][i]); sub(l, '待确认'); },
  ];
  variant(g, 'A 现状', '行动文字折行，「（系统）」跟在句尾。', cols, R.map((r, i) => [...base(r, i),
    (c, w) => { T(c, r.a + (r.sys ? '（系统）' : ''), { color: C.ink, w }); sub(c, r.due, r.late ? C.red : C.mute); },
  ]));
  variant(g, 'B 单行省略，「系统」挪到第二行（推荐）', '行动一行，放不下省略、悬停看全文；「系统生成」作为来源写在日期前面，不挤正文。', cols, R.map((r, i) => [...base(r, i),
    (c, w) => { T(c, r.a, { color: C.ink }); const l = H(c, 6); if (r.sys) sub(l, '系统 ·'); sub(l, r.due, r.late ? C.red : C.mute); },
  ]));
}

// ================= 3 商品候选 =================
{
  const g = section('P3 商品候选', '商品候选（待审核）：来源与被单据引用两种写法', '来源列每条一个标签、最多三条（三个 24 高的标签叠起来）；被单据引用的长句折行。行高 86–117。');
  const R = [
    { b: '待确认品牌：Schneider · LC2D18B7C', bs: '品牌未识别 · 商品编号 10', src: [['成交', C.green, C.greenSoft, 'SO20261118001'], ['采购回填', C.link, C.accentSoft, 'IQ1118AM'], ['回价有货', C.info, C.infoSoft, 'IQ1118AM']], ref: '询盘型号 1 · 报价 2 · PI 1 · 销售订单 1 · 采购需求 1 · 采购订单 1 · 供应商发货 1 · 采购入库 1 · 销售出库 1', n: 9, deal: true },
    { b: '待确认品牌：Siemens · 6ES7214-1AG40-0XB1', bs: '品牌未识别 · 商品编号 11', src: [['采购回填', C.link, C.accentSoft, 'IQ1118AM'], ['回价有货', C.info, C.infoSoft, 'IQ1118AM']], ref: '询盘型号 1 · 报价 2 · PI 1', n: 3 },
  ];
  const cols = [['品牌 · 型号', 280], ['来源', 230], ['被单据引用', 190]];
  variant(g, 'A 现状', '品牌型号折行；来源每条一个彩色标签；引用整句列出。', cols, R.map((r) => [
    (c, w) => { T(c, r.b, { weight: 600, color: C.orange, w }); sub(c, r.bs); },
    (c) => { for (const [t, fg, bg, no] of r.src) { const l = H(c, 6); pill(l, t, fg, bg); T(l, no, { size: 12, color: C.ink }); I(c, { type: 'frame', height: 3, width: 1 }); } },
    (c, w) => { sub(c, r.ref, C.ink, w); if (r.deal) sub(c, '已成交，不能驳回', C.orange); },
  ]));
  variant(g, 'B 每格两行，其余悬停看（推荐）', '品牌型号单行省略；来源只显示最新一条（类型写文字），第二行「等 N 条」悬停看全部；引用显示最重要的一类加「等 N 类」，「已成交，不能驳回」留在第二行。', cols, R.map((r) => [
    (c) => { T(c, r.b.length > 30 ? r.b.slice(0, 29) + '…' : r.b, { weight: 600, color: C.orange }); sub(c, r.bs); },
    (c) => { const [t, fg, , no] = r.src[0]; const l = H(c, 6); T(l, t, { size: 12, weight: 600, color: fg }); T(l, no, { size: 14, color: C.ink }); sub(c, `等 ${r.src.length} 条来源`); },
    (c) => { T(c, r.deal ? `销售订单 1 等 ${r.n} 类` : `报价 2 等 ${r.n} 类`, { color: C.ink }); sub(c, r.deal ? '已成交，不能驳回' : '未成交', r.deal ? C.orange : C.mute); },
  ]), (box) => tip(box, '悬停「等 3 条来源」', ['成交 SO20261118001', '采购回填真实型号 IQ1118AM', '采购回价有货 IQ1118AM']));
}
