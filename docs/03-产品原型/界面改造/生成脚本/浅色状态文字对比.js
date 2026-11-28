// 浅色模式状态文字对比图（R18）：现状 dark 档偏暗 / 提亮一档 / 原色。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const mixc = (hex, al) => '#' + [1, 3, 5].map((k) => Math.round(parseInt(hex.slice(k, k + 2), 16) * al + 255 * (1 - al)).toString(16).padStart(2, '0')).join('');
const MAIN = { g: '#22C55E', o: '#FFAB00', r: '#FF5630', i: '#00B8D9' };
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINK = '#B76E00';
const W = 1180;

const root = I(null, { type: 'frame', name: '浅色状态文字对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '浅色模式：状态文字（标签、逾期、金额红绿）现状 / 推荐 / 备选', { size: 20, weight: 700, color: INK });

const variant = (title, note, good, S) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#E4F8EC') }); T(b, '推荐', { size: 12, weight: 600, color: '#15A050' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = V(col, 0, { width: W, padding: 20, cornerRadius: 12, fill: fill('#F4F6F8') });
  const tb = V(pg, 0, { width: 'fill_container', padding: [8, 16], cornerRadius: 10, fill: fill('#FFFFFF') });
  const hd = H(tb, 0, { width: 'fill_container', height: 36 });
  const cols = [['单号', 160], ['客户', 200], ['状态', 120], ['收款', 120], ['毛利', 150], ['交期', 160], ['提醒', 180]];
  cols.forEach(([t, w]) => { const c = H(hd, 0, { width: w, padding: [0, 10] }); T(c, t, { size: 12, weight: 600, color: MUTE }); });
  const pill = (p, w, t, k) => { const c = H(p, 0, { width: w, padding: [0, 10] }); const x = H(c, 0, { height: 24, padding: [0, 8], cornerRadius: 6, fill: fill(mixc(MAIN[k], S.a)) }); T(x, t, { size: 12, weight: 600, color: S[k] }); };
  const txt = (p, w, t, color, weight = 400) => { const c = H(p, 0, { width: w, padding: [0, 10] }); T(c, t, { color, weight }); };
  [
    ['SO20261118001', 'PRIME CONTROLS LLC', ['已下单', 'i'], ['待到账', 'o'], ['CNY 1,013.80', 'g'], ['2026-11-20', 'r'], ['已过 4 天', 'r']],
    ['SO20261117004', 'Pacific Controls', ['部分发货', 'i'], ['部分到账', 'o'], ['CNY -320.00', 'r'], ['今天', 'o'], ['还有型号没下采购', 'o']],
    ['SO20261115002', 'Delta Automation', ['已完成', 'g'], ['已收齐', 'g'], ['CNY 2,460.00', 'g'], ['2026-12-02', null], ['缺 5 家报价', 'r']],
  ].forEach((r) => {
    I(tb, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill('#E5E8EB') });
    const tr = H(tb, 0, { width: 'fill_container', height: 48 });
    txt(tr, 160, r[0], LINK); txt(tr, 200, r[1], INK, 600);
    pill(tr, 120, r[2][0], r[2][1]); pill(tr, 120, r[3][0], r[3][1]);
    txt(tr, 150, r[4][0], S[r[4][1]], 600);
    txt(tr, 160, r[5][0], r[5][1] ? S[r[5][1]] : SUB, r[5][1] ? 600 : 400);
    txt(tr, 180, r[6][0], S[r[6][1]]);
  });
  const sw = H(col, 14); [['绿', 'g'], ['橙', 'o'], ['红', 'r'], ['信息', 'i']].forEach(([t, k]) => { const x = H(sw, 6); I(x, { type: 'rectangle', width: 16, height: 16, cornerRadius: 4, fill: fill(S[k]) }); T(x, `${t} ${S[k]}  对白 ${S.c[k]}`, { size: 12, color: MUTE }); });
};

variant('A 现状：参考模板的 dark 档', '红 #B71D18 是砖红、橙 #B76E00 发褐、绿 #118D57 偏墨绿、蓝 #006C9C 偏藏青，单看都清楚，但一页里几处一起出现时整体发沉，就是「压抑」的来源。', false,
  { g: '#118D57', o: '#B76E00', r: '#B71D18', i: '#006C9C', a: 0.16, c: { g: '4.2:1', o: '4.0:1', r: '6.6:1', i: '5.8:1' } });
variant('B 推荐：提亮一档（介于原色与 dark 档之间）', '红 #DC2626、蓝 #0284C7、绿 #15A050、橙 #D27600，和 Tailwind、Shopify Polaris 等用作文字的那一档相当，明显更鲜亮；标签浅底从 16% 降到 12%，让字和底拉开。代价：绿、橙对白底约 3.1:1，低于正文 4.5:1，但它们都是加粗的短标签或旁边有文字说明，不靠颜色单独传达。', true,
  { g: '#15A050', o: '#D27600', r: '#DC2626', i: '#0284C7', a: 0.12, c: { g: '3.1:1', o: '3.1:1', r: '4.8:1', i: '4.1:1' } });
variant('C 备选：直接用原色当文字', '和图表、圆点同色，最亮；但绿、橙对白底只有 1.9–2.3:1，表格里的「待到账」「已过 4 天」在亮屏上会发虚，不推荐。', false,
  { g: '#22C55E', o: '#FFAB00', r: '#FF5630', i: '#00B8D9', a: 0.16, c: { g: '2.3:1', o: '1.9:1', r: '3.2:1', i: '2.4:1' } });
