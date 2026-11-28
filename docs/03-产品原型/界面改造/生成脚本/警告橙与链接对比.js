// 琥珀主题下警告橙文字与链接色对比（R19）：更正 R18 把链接画成褐色的问题。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const mixc = (hex, al) => '#' + [1, 3, 5].map((k) => Math.round(parseInt(hex.slice(k, k + 2), 16) * al + 255 * (1 - al)).toString(16).padStart(2, '0')).join('');
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINK = '#FDA92D';
const W = 1180;

const root = I(null, { type: 'frame', name: '警告橙与链接对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '琥珀主题：警告橙文字和链接（单号）放在一起（绿、红、蓝已按原色）', { size: 20, weight: 700, color: INK });

const variant = (title, note, good, O, withIcon) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = V(col, 0, { width: W, padding: 20, cornerRadius: 12, fill: fill('#F4F6F8') });
  const tb = V(pg, 0, { width: 'fill_container', padding: [8, 16], cornerRadius: 10, fill: fill('#FFFFFF') });
  const hd = H(tb, 0, { width: 'fill_container', height: 36 });
  [['单号', 170], ['客户', 200], ['收款', 120], ['交期', 130], ['提醒', 300], ['应付余额', 160]].forEach(([t, w]) => { const c = H(hd, 0, { width: w, padding: [0, 10] }); T(c, t, { size: 12, weight: 600, color: MUTE }); });
  [['SO20261118001', 'PRIME CONTROLS LLC', '待到账', '今天', '还有型号没下采购订单', 'CNY 1,280.00'],
   ['PO20261117004', 'Pacific Controls', '部分到账', '2026-12-02', '一次最多 50 张', 'CNY 320.00']].forEach((r) => {
    I(tb, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill('#E5E8EB') });
    const tr = H(tb, 0, { width: 'fill_container', height: 48 });
    const cell = (w) => H(tr, 4, { width: w, padding: [0, 10] });
    T(cell(170), r[0], { color: LINK });
    T(cell(200), r[1], { color: INK, weight: 600 });
    const c3 = cell(120); const p = H(c3, 0, { height: 24, padding: [0, 8], cornerRadius: 6, fill: fill(mixc('#FFAB00', 0.16)) }); T(p, r[2], { size: 12, weight: 600, color: O });
    T(cell(130), r[3], { color: r[3] === '今天' ? O : SUB, weight: r[3] === '今天' ? 600 : 400 });
    const c5 = cell(300); if (withIcon) icon(c5, 'triangle-alert', 14, '#FFAB00'); T(c5, r[4], { color: withIcon ? SUB : O });
    T(cell(160), r[5], { color: O, weight: 600 });
  });
};

variant('A 照上次选的：橙也用原色 #FFAB00', '和单号链接色 #FDA92D 几乎一模一样：「还有型号没下采购订单」「CNY 1,280.00」看起来像能点的链接，单号反而不显眼。R18 图里单号画成了褐色，没暴露这个问题，这是图的错误。', false, '#FFAB00', false);
variant('B 推荐：绿、红、蓝用原色；橙保留深一档 #B76E00', '只有橙和琥珀主题撞色，橙文字单独保留深一档，和链接一眼能分开；标签底仍是原色浅底。其他 5 种主题色下也一样用这一档，规则简单。', true, '#B76E00', false);
variant('C 橙用原色，但纯文字提醒一律改「图标 + 正文色」', '提醒句子前加原色三角图标、文字用正文色，金额和标签字仍是原色。最亮，但金额「CNY 1,280.00」仍和链接同色；全站约 270 处橙色文字要逐处改写法，改动大。', false, '#FFAB00', true);
