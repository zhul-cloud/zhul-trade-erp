// 列表首次加载、重新查询时的样子（复审 S17）对比（R23）。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}), ...(o.opacity ? { opacity: o.opacity } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const R = (p, w, h, c, o = {}) => I(p, { type: 'rectangle', width: w, height: h, cornerRadius: o.r ?? 4, fill: fill(c), ...o });
const SH = [{ type: 'shadow', offsetX: 0, offsetY: 0, blur: 2, spread: 0, color: 'rgba(145,158,171,0.2)' }, { type: 'shadow', offsetX: 0, offsetY: 12, blur: 24, spread: -4, color: 'rgba(145,158,171,0.12)' }];
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINK = '#B76E00', LINE = '#E5E8EB', CANVAS = '#F4F6F8', HEAD = '#F9FAFB', SKEL = '#ECEEF0', AMBER = '#FDA92D';
const PW = 540;
const CW = PW - 32;

const COLS = [['销售订单号 · 下单日期', 150], ['客户', 150], ['状态', 80], ['合计', 110]];
const ROWS = [
  ['SO20261118001', '2026-11-18', 'PRIME CONTROLS LLC', '待采购', 'USD 532.00'],
  ['SO20261117002', '2026-11-17', 'Pacific Controls', '待发货', 'USD 7,149.80'],
  ['SO20261116003', '2026-11-16', 'Nordic Automation AB', '已完成', 'EUR 1,280.00'],
  ['SO20261115001', '2026-11-15', 'PRIME CONTROLS LLC', '待到账', 'USD 838.00'],
];

const root = I(null, { type: 'frame', name: '加载状态对比', layout: 'vertical', gap: 32, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '列表首次加载、重新查询时显示什么（复审 S17）', { size: 20, weight: 700, color: INK });
T(root, '空、无结果、出错、无权限已有公共 EmptyState；加载中不再显示「共 0 条」；核对后正常重新查询不会清空表格（清空只发生在出错时）。剩下要定的是下面两种加载的样子。', { size: 13, color: SUB, w: PW * 2 + 24 });

// 一张列表卡：mode = spin-empty | skeleton | spin-dim | bar-dim
const card = (p, mode) => {
  const PW = CW; // 卡片放在底板里，宽度扣掉底板内边距
  const c = V(p, 0, { width: PW, cornerRadius: 12, fill: fill('#FFFFFF'), effects: SH, clipContent: true });
  if (mode === 'bar-dim') { const bar = H(c, 0, { width: PW, height: 2, fill: fill(SKEL) }); R(bar, PW * 0.4, 2, AMBER, { r: 0 }); }
  const tool = H(c, 8, { width: PW, height: 52, padding: [0, 20] });
  if (mode === 'skeleton') R(tool, 64, 12, SKEL);
  else if (mode === 'spin-empty') I(tool, { type: 'frame', width: 1, height: 1 });
  else T(tool, '共 128 条', { size: 13, weight: 600, color: INK });
  const tb = V(c, 0, { width: PW, padding: [0, 16, 16, 16] });
  const head = H(tb, 0, { width: PW - 32, height: 44, cornerRadius: 8, fill: fill(HEAD) });
  COLS.forEach(([n, w], i) => { const cell = H(head, 0, { width: i === 3 ? 'fill_container' : w, padding: [0, 12], justifyContent: i === 3 ? 'end' : 'start' }); T(cell, n, { size: 13, color: MUTE }); });
  // 重新查询时转圈叠在旧数据中间：外面套一层不自动排版的容器
  const holder = mode === 'spin-dim' ? I(tb, { type: 'frame', layout: 'none', width: PW - 32, height: 240 }) : null;
  const body = holder ? I(holder, { type: 'frame', layout: 'vertical', x: 0, y: 0, width: PW - 32, height: 240 }) : V(tb, 0, { width: PW - 32, height: 240 });
  if (mode === 'spin-empty') {
    const box = V(body, 8, { width: PW - 32, height: 240, justifyContent: 'center', alignItems: 'center' });
    I(box, { type: 'icon_font', name: 'c:' + AMBER, iconFontName: 'loader', width: 28, height: 28, fill: fill(AMBER) });
    return;
  }
  const dim = mode === 'spin-dim' ? 0.5 : mode === 'bar-dim' ? 0.6 : 1;
  ROWS.forEach((r, k) => {
    const wrap = V(body, 0, { width: PW - 32, height: 60 });
    const row = H(wrap, 0, { width: PW - 32, height: 59 });
    R(wrap, PW - 32, 1, LINE, { r: 0 });
    COLS.forEach(([, w], i) => {
      const cell = V(row, 4, { width: i === 3 ? 'fill_container' : w, padding: [0, 12], alignItems: i === 3 ? 'end' : 'start' });
      if (mode === 'skeleton') {
        R(cell, [96, 112, 48, 72][i] - (k % 2) * 12, 12, SKEL);
        if (i < 2) R(cell, 64, 10, SKEL);
        return;
      }
      if (i === 0) { T(cell, r[0], { size: 14, weight: 600, color: INK, opacity: dim }); T(cell, r[1], { size: 12, color: MUTE, opacity: dim }); }
      if (i === 1) T(cell, r[2], { size: 14, color: INK, opacity: dim });
      if (i === 2) { const tag = H(cell, 0, { height: 24, padding: [0, 8], cornerRadius: 4, fill: fill('#ECEEF0'), opacity: dim }); T(tag, r[3], { size: 12, weight: 600, color: '#56636F' }); }
      if (i === 3) T(cell, r[4], { size: 14, weight: 600, color: INK, opacity: dim });
    });
  });
  if (holder) I(holder, { type: 'icon_font', name: 'c:' + AMBER, iconFontName: 'loader', x: (PW - 32) / 2 - 14, y: 106, width: 28, height: 28, fill: fill(AMBER) });
};

const section = (title, items) => {
  const s = V(root, 12);
  T(s, title, { size: 16, weight: 700, color: INK });
  const row = H(s, 24, { alignItems: 'start' });
  items.forEach(([name, note, good, mode]) => {
    const col = V(row, 8, { width: PW });
    const h = H(col, 8); T(h, name, { size: 14, weight: 700, color: INK });
    if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
    T(col, note, { size: 13, color: SUB, w: PW });
    const pg = V(col, 0, { width: PW, padding: 16, cornerRadius: 12, fill: fill(CANVAS) });
    card(pg, mode);
  });
};

section('1 首次打开页面（还没有任何数据）', [
  ['A 现状：表格区中间转圈', '表头下是 240 高的空白加一个转圈；数据回来后表格从空白突然撑开，页面高度跳一下。', false, 'spin-empty'],
  ['B 骨架行', '画出和真实行同高的灰条（4–6 行），数据回来时原位替换，页面不跳；请求在 300ms 内返回的不画骨架，避免一闪。参考 Polaris（SkeletonBodyText）、Atlassian、Material。', true, 'skeleton'],
]);
section('2 换筛选条件、翻页、排序（表里已有数据）', [
  ['A 现状：旧数据半透明 + 中间转圈', 'antd 表格默认做法：旧数据留着、变淡并不能点，中间转圈，新数据回来后替换。Ant Design、Polaris、Atlassian 的数据表都是这样；建议只加 300ms 延迟（快的请求不闪）。', true, 'spin-dim'],
  ['B 顶部 2px 进度条 + 旧数据变淡', '复审原提议：卡片顶部一条主题色细进度条，不转圈。参考 Material 数据表、Linear。和 A 的差别只在提示的位置；细条在宽屏上离用户视线远，翻页时容易没注意到在加载。', false, 'bar-dim'],
]);
