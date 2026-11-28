// 状态色对比图（R13）：浅色下状态标签的文字色、浅底。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINE = '#E5E8EB', HEAD = '#F9FAFB', CANVAS = '#F4F6F8', PRIMARY = '#FDA92D';
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color || SUB), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const sp = (p) => I(p, { type: 'frame', width: 'fill_container', height: 1 });
const mixc = (hex, al) => '#' + [1, 3, 5].map((k) => Math.round(parseInt(hex.slice(k, k + 2), 16) * al + 255 * (1 - al)).toString(16).padStart(2, '0')).join('');
const MAIN = { green: '#22C55E', orange: '#FFAB00', red: '#FF5630', info: '#00B8D9' };
const W = 1180;

const root = I(null, { type: 'frame', name: '状态色对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '状态标签配色：现状 / 推荐 / 备选（浅色；深色模式用原色，不变）', { size: 20, weight: 700, color: INK });

const variant = (title, note, good, tone, alpha) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK }); if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#E4F8EC') }); T(b, '推荐', { size: 12, weight: 600, color: '#078039' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = V(col, 16, { width: W, padding: 24, cornerRadius: 12, fill: fill(CANVAS) });
  const pill = (p, text, k) => { const b = H(p, 4, { height: 24, padding: [0, 8], cornerRadius: 4, fill: fill(k === 'gray' ? '#ECEEF0' : mixc(MAIN[k], alpha)) }); T(b, text, { size: 12, weight: 600, color: k === 'gray' ? '#56636F' : tone[k] }); return b; };
  // 一组标签
  const row = H(pg, 10, { padding: [16, 20], cornerRadius: 10, fill: fill('#FFFFFF'), effects: SH, width: 'fill_container' });
  [['在产', 'green'], ['启用', 'green'], ['已收齐', 'green'], ['待审核', 'orange'], ['部分到账', 'orange'], ['已逾期', 'red'], ['无渠道', 'red'], ['处理中', 'info'], ['已取消', 'gray']].forEach(([t, k]) => pill(row, t, k));
  sp(row);
  T(row, `文字对比度：绿 ${tone.cg} · 橙 ${tone.co} · 红 5.71 · 信息 5.17`, { size: 12, color: MUTE });
  // 列表里的样子
  const card = V(pg, 0, { width: 'fill_container', padding: [8, 20], cornerRadius: 10, fill: fill('#FFFFFF'), effects: SH });
  const hd = H(card, 0, { width: 'fill_container', height: 40, fill: fill(HEAD) });
  [['单号', 200], ['客户', 260], ['订单状态', 160], ['收款', 160], ['生命周期', 140], ['状态', 120]].forEach(([t, w]) => { const c = H(hd, 0, { width: w, padding: [0, 10] }); T(c, t, { size: 13, weight: 600, color: MUTE }); });
  [['SO20261118001', 'PRIME CONTROLS LLC', ['进行中', 'info'], ['部分到账', 'orange'], ['在产', 'green'], ['启用', 'green']],
    ['SO20261117004', 'Pacific Controls', ['已完成', 'green'], ['已收齐', 'green'], ['在产', 'green'], ['启用', 'green']],
    ['SO20261115002', 'Delta Automation', ['已取消', 'gray'], ['已逾期', 'red'], ['停产', 'gray'], ['已停用', 'gray']]].forEach((r) => {
    I(card, { type: 'rectangle', width: 'fill_container', height: 1, fill: fill(LINE) });
    const tr = H(card, 0, { width: 'fill_container', height: 52 });
    const c1 = H(tr, 0, { width: 200, padding: [0, 10] }); T(c1, r[0], { color: '#B76E00' });
    const c2 = H(tr, 0, { width: 260, padding: [0, 10] }); T(c2, r[1], { color: INK, weight: 600 });
    [[r[2], 160], [r[3], 160], [r[4], 140], [r[5], 120]].forEach(([[t, k], w]) => { const c = H(tr, 0, { width: w, padding: [0, 10] }); pill(c, t, k); });
  });
};

variant('A 现状：文字压暗到 4.5:1', '浅色下绿 #0D7A4A、橙 #9A5B00，底是原色 12%。为了正文 4.5:1 把文字压得又深又偏（绿偏青、橙偏褐），配上很淡的底，看起来发暗发土。', false,
  { green: '#0D7A4A', orange: '#9A5B00', red: '#B71D18', info: '#006C9C', cg: '4.86', co: '5.01' }, 0.12);
variant('B 推荐：保持 4.5:1，去掉偏色', '同样满足正文 4.5:1，但绿改用纯正的绿 #078039（色相 145，原来 154 偏青），橙改 #A85D00（色相 33，原来偏褐）；底仍是原色 12%。颜色更正、更亮，红、信息不变（本来就与参考模板一致）。', true,
  { green: '#078039', orange: '#A85D00', red: '#B71D18', info: '#006C9C', cg: '4.56', co: '4.58' }, 0.12);
variant('C 备选：照参考模板（Minimal / PrimeDash）', '绿 #118D57、橙 #B76E00，底是原色 16%。最鲜亮，和参考模板一模一样；但 12px 标签文字对比度只有 3.7:1，低于正文 4.5:1 的无障碍要求（11-21 定过不低于 4.5）。', false,
  { green: '#118D57', orange: '#B76E00', red: '#B71D18', info: '#006C9C', cg: '3.69', co: '3.61' }, 0.16);
