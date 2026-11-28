// 单据列表列顺序对比图（R7），2026-11-24 用户选定方案 B（D40）。单独用 op design 生成。
const C = { ink: '#212B36', sub: '#3E4750', mute: '#637381', line: '#E5E8EB', head: '#F9FAFB', card: '#FFFFFF', canvas: '#F4F6F8', primary: '#FDA92D', primarySoft: '#FFF5E6', green: '#0D7A4A', greenSoft: '#E5F8EC', info: '#006C9C', infoSoft: '#E0F6FA', neutralSoft: '#ECEEF0', orange: '#9A5B00' };
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', content, fontFamily: o.font || F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.45, fill: fill(o.color || C.sub) });
const H = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const S = 0.62; // 缩放：1366 屏的表格可视区 1043px
const VIEW = 1043;
const KEY = new Set(['订单状态', '合计', '收款进度', '货物', '毛利']);

// 一条表格示意：列块按宽度缩放，可视区外的列变淡
const strip = (parent, title, desc, cols, fixedL, fixedR) => {
  const box = V(parent, 8);
  T(box, title, { size: 16, weight: 700, color: C.ink });
  I(box, { type: 'text', content: desc, fontFamily: F, fontSize: 13, lineHeight: 1.5, fill: fill(C.mute), width: 1500, textGrowth: 'fixed-width' });
  const row = H(box, 0, { alignItems: 'start' });
  let x = 0;
  const all = [[fixedL[0], fixedL[1], 'L'], ...cols.map(([n, w]) => [n, w, '']), [fixedR[0], fixedR[1], 'R']];
  // 可视区：左固定 + 滚动区 + 右固定
  const scrollRoom = VIEW - fixedL[1] - fixedR[1];
  let used = 0;
  const visFrame = H(row, 0, { stroke: { thickness: 2, fill: fill(C.primary), align: 'outside' }, cornerRadius: 6, clipContent: true, fill: fill(C.card) });
  const restFrame = H(row, 0, { opacity: 0.45 });
  const cell = (p, n, w, kind) => {
    const key = KEY.has(n);
    const c = V(p, 2, { width: Math.round(w * S), height: 64, padding: [8, 6], fill: fill(kind ? C.head : key ? C.primarySoft : C.card), stroke: { thickness: 1, fill: fill(C.line), align: 'inside' } });
    T(c, n, { size: 11, weight: key ? 700 : 400, color: key ? C.ink : C.sub });
    T(c, `${w}`, { size: 10, color: C.mute });
  };
  cell(visFrame, fixedL[0], fixedL[1], 'fixed');
  const rest = [];
  let full = false;
  for (const [n, w] of cols) {
    // 按顺序填，放不下就停（露一半的列也算在首屏外）
    if (!full && used + w <= scrollRoom) { cell(visFrame, n, w); used += w; } else { full = true; rest.push([n, w]); }
  }
  cell(visFrame, fixedR[0], fixedR[1], 'fixed');
  for (const [n, w] of rest) cell(restFrame, n, w);
  const lg = H(box, 16);
  T(lg, '橙框内 = 1366×768 下不滚动就能看到的列（表格可视区 1043px，单号固定在左、操作固定在右）', { size: 12, color: C.mute });
  T(lg, '浅橙底 = 用户扫列表最先要看的列（状态、金额、进度）', { size: 12, color: C.orange });
  return box;
};

const root = I(null, { type: 'frame', name: '列顺序对比', layout: 'vertical', gap: 28, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '销售订单列表：1366 屏首屏能看到哪些列（窄屏关键列被挤出，S2）', { size: 20, weight: 700, color: C.ink });

strip(root, 'A 现状', '品牌、品类各 170 排在前面，订单状态、收款进度、货物都在首屏之外；合计只露一半。',
  [['客户', 240], ['现货/期货', 100], ['型号 · 数量', 190], ['品牌', 170], ['品类', 170], ['合计', 150], ['毛利', 150], ['收款进度', 180], ['订单状态', 130], ['货物', 230], ['采购员', 140], ['来源单号', 200], ['业务员', 90], ['创建', 120], ['更新', 120]],
  ['订单编号 · 日期', 170], ['操作', 96]);

strip(root, 'B 先状态和金额，再明细（推荐）', '顺序改为：客户 → 订单状态 → 合计 → 收款进度 → 货物 → 型号 · 数量 → 品牌 → 品类 → 毛利 → 其余；现货/期货是属性，并进单号下面一行（「2026-11-18 · 现货」），省出 100。首屏能看到订单状态、合计、收款进度三个答案，货物紧随其后。',
  [['客户', 240], ['订单状态', 130], ['合计', 150], ['收款进度', 180], ['货物', 230], ['型号 · 数量', 190], ['品牌', 170], ['品类', 170], ['毛利', 150], ['采购员', 140], ['来源单号', 200], ['业务员', 90], ['创建', 120], ['更新', 120]],
  ['订单编号 · 日期 · 现货', 170], ['操作', 96]);

strip(root, 'C 品牌、品类并进型号列', '在 B 的基础上去掉品牌、品类两列，型号列第二行写「施耐德 · 接触器 · 共 2 个型号 · 30 件」。首屏和 B 一样，只是整张表少两列、横向滚动更短。代价：品牌、品类不能单独排序，型号列第二行变长、常被省略，偏离「品牌、品类各占一列」的单据标准。',
  [['客户', 240], ['订单状态', 130], ['合计', 150], ['收款进度', 180], ['货物', 230], ['型号 · 品牌 · 品类', 220], ['毛利', 150], ['采购员', 140], ['来源单号', 200], ['业务员', 90], ['创建', 120], ['更新', 120]],
  ['订单编号 · 日期 · 现货', 170], ['操作', 96]);

I(root, { type: 'text', content: 'PI、采购订单、出运等单据列表按同一规则排：身份（单号、对方）→ 当前状态 → 金额 → 进度 → 明细（型号、品牌、品类）→ 次要信息 → 创建、更新。应付管理同理：编号 → 收款方 → 状态 → 未付 → 下一期到期 → 应付、已付 → 其余。', fontFamily: F, fontSize: 13, lineHeight: 1.5, fill: fill(C.sub), width: 1500, textGrowth: 'fixed-width' });
