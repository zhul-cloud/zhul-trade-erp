// 同一类切换控件五六种形态（复审 S3）的统一方案对比（R27）。单独用 op design 生成。
const F = 'Public Sans';
const fill = (c) => [{ type: 'solid', color: c }];
const T = (p, content, o = {}) => I(p, { type: 'text', name: 'c:' + o.color, content, fontFamily: F, fontSize: o.size || 13, fontWeight: o.weight || 400, lineHeight: 1.5, fill: fill(o.color), ...(o.w ? { width: o.w, textGrowth: 'fixed-width' } : {}) });
const H = (p, gap = 6, o = {}) => I(p, { type: 'frame', layout: 'horizontal', gap, alignItems: 'center', ...o });
const V = (p, gap = 0, o = {}) => I(p, { type: 'frame', layout: 'vertical', gap, ...o });
const icon = (p, n, s, c) => I(p, { type: 'icon_font', name: 'c:' + c, iconFontName: n, width: s, height: s, fill: fill(c) });
const INK = '#212B36', SUB = '#3E4750', MUTE = '#637381', LINK = '#B76E00', LINE = '#E5E8EB', CANVAS = '#F4F6F8', TRACK = '#E9ECEF';
const W = 1120;

// 下划线页签（选中主题色字 + 2px 底线），数量写「名称 N」
const underline = (p, items, on = 0) => {
  const bar = H(p, 24, { width: 'fill_container', height: 40, alignItems: 'end' });
  items.forEach(([label, n], i) => {
    const t = V(bar, 0, { width: 'fit_content' });
    const row = H(t, 4, { height: 36 });
    T(row, label, { size: 14, weight: i === on ? 600 : 400, color: i === on ? LINK : SUB });
    if (n !== undefined) T(row, String(n), { size: 14, weight: i === on ? 600 : 400, color: i === on ? LINK : MUTE });
    I(t, { type: 'rectangle', width: 'fill_container', height: 2, fill: fill(i === on ? LINK : '#FFFFFF00') });
  });
  return bar;
};
// 灰轨分段
const seg = (p, items, on = 0) => {
  const s = H(p, 2, { height: 34, padding: [3, 3], cornerRadius: 8, fill: fill(TRACK) });
  items.forEach((label, i) => {
    const b = H(s, 0, { height: 28, padding: [0, 12], cornerRadius: 6, fill: fill(i === on ? '#FFFFFF' : '#FFFFFF00') });
    T(b, label, { size: 13, weight: i === on ? 600 : 400, color: i === on ? INK : SUB });
  });
  return s;
};
const field = (p, label, value, w = 180) => {
  const f = H(p, 6, { width: w, height: 34, padding: [0, 10], cornerRadius: 8, fill: fill('#FFFFFF'), stroke: { thickness: 1, fill: fill(LINE) } });
  T(f, label, { size: 13, color: MUTE }); T(f, value, { size: 13, color: INK });
  I(f, { type: 'frame', width: 'fill_container', height: 1 }); icon(f, 'chevron-down', 14, MUTE);
};
const search = (p, ph, w = 240) => {
  const f = H(p, 6, { width: w, height: 34, padding: [0, 10], cornerRadius: 8, fill: fill('#FFFFFF'), stroke: { thickness: 1, fill: fill(LINE) } });
  icon(f, 'search', 14, MUTE); T(f, ph, { size: 13, color: MUTE });
};
const card = (p, w = 'fill_container') => V(p, 12, { width: w, padding: [14, 16], cornerRadius: 12, fill: fill('#FFFFFF') });
const fakeRows = (p, n = 2) => { for (let i = 0; i < n; i++) I(p, { type: 'rectangle', width: 'fill_container', height: 10, cornerRadius: 4, fill: fill('#EEF1F4') }); };

const root = I(null, { type: 'frame', name: '切换控件统一对比', layout: 'vertical', gap: 36, padding: [32, 40], width: 'fit_content', fill: fill('#FFFFFF') });
T(root, '同一类切换控件五六种形态：统一成什么（复审 S3）', { size: 20, weight: 700, color: INK });
T(root, '切换分三种用途：看不同对象或视图（如全部客户、公海客户）；按状态过滤（如待审核、已驳回）；切换数据范围（我的、团队、全部人员）。现在同一种用途在不同页面用了不同控件、放在不同位置。', { size: 13, color: SUB, w: W });

// ---- 现状
const now = V(root, 10, { width: W });
T(now, '现状', { size: 16, weight: 700, color: INK });
const nowRows = [H(now, 16, { width: W, alignItems: 'start' }), H(now, 16, { width: W, alignItems: 'start' })];
let nowIdx = 0;
const sample = (title, note, draw) => {
  const c = V(nowRows[Math.floor(nowIdx++ / 2)], 8, { width: 552, padding: [14, 14], cornerRadius: 12, fill: fill(CANVAS) });
  T(c, title, { size: 13, weight: 600, color: INK });
  draw(c);
  T(c, note, { size: 12, color: MUTE, w: 520 });
};
sample('客户管理', '看不同对象，用灰轨分段，放页头下方', (c) => seg(c, ['全部客户 128', '档案补全', '疑似重复 3', '公海客户 46']));
sample('收款管理', '看不同对象，用下划线页签，数量「· N」', (c) => { const k = V(c, 0, { width: 520, padding: [0, 12], fill: fill('#FFFFFF') }); underline(k, [['待确认 · 5'], ['未认领到账 · 2']]); });
sample('商品候选', '按状态过滤，却做成页签（另有「待关联」是不同视图）', (c) => { const k = V(c, 0, { width: 520, padding: [0, 12], fill: fill('#FFFFFF') }); underline(k, [['待审核', 12], ['已建档'], ['已驳回'], ['待关联']]); });
sample('报销 / 客户跟进', '切换范围：报销放卡片左上，客户跟进放页头，名称也不同（全部人员 / 团队）', (c) => { const r = H(c, 12); seg(r, ['我的', '全部人员']); seg(r, ['我的', '团队']); });

// ---- 方案
const variant = (title, note, good, draw) => {
  const col = V(root, 10, { width: W });
  const h = H(col, 8); T(h, title, { size: 16, weight: 700, color: INK });
  if (good) { const b = H(h, 0, { height: 22, padding: [0, 8], cornerRadius: 4, fill: fill('#DCF6E5') }); T(b, '推荐', { size: 12, weight: 600, color: '#118D57' }); }
  T(col, note, { size: 13, color: SUB, w: W });
  const pg = H(col, 20, { width: W, padding: [20, 20], cornerRadius: 12, fill: fill(CANVAS), alignItems: 'start' });
  draw(pg);
};
// 一个页面的示意：页头（标题 + 右侧）、切换、搜索区、表格卡
const page = (p, { title, right, tabs, filters }) => {
  const pg = V(p, 12, { width: 530 });
  const head = H(pg, 8, { width: 'fill_container', height: 36 });
  T(head, title, { size: 18, weight: 700, color: INK });
  I(head, { type: 'frame', width: 'fill_container', height: 1 });
  if (right) right(head);
  if (tabs) tabs(pg);
  const f = card(pg); const fr = H(f, 8, { width: 'fill_container' }); filters(fr);
  const tb = card(pg); fakeRows(tb, 3);
};

variant('A 按用途分三种控件', '不同对象或视图：页头下方下划线页签，数量写「名称 N」（为 0 只写名称）。按状态过滤：一律放搜索区的状态下拉，选项带数量。切换数据范围：灰轨分段，统一放页头右侧，名称统一为「我的 / 全部」。商品候选的「待审核 / 已建档 / 已驳回」改为状态下拉，「待关联」作为不同视图保留成页签。', true, (pg) => {
  page(pg, {
    title: '客户管理',
    tabs: (p) => underline(p, [['全部客户', 128], ['档案补全'], ['疑似重复', 3], ['公海客户', 46]]),
    filters: (fr) => { search(fr, '客户名称、联系人'); field(fr, '国家', '全部', 140); },
  });
  page(pg, {
    title: '报销管理',
    right: (h) => seg(h, ['我的', '全部']),
    tabs: (p) => underline(p, [['待报销', 3], ['报销单']], 1),
    filters: (fr) => { search(fr, '报销单号、提交人'); field(fr, '状态', '待付款 · 2', 170); },
  });
});

variant('B 全部用灰轨分段', '三种用途都用灰轨分段，放在页头下方左侧；状态也做成分段。形态只有一种，但一行里可能同时出现两组分段（视图 + 范围），状态多时分段会很长，也和「状态放搜索区下拉」的已定规则冲突。', false, (pg) => {
  page(pg, {
    title: '客户管理',
    tabs: (p) => seg(p, ['全部客户 128', '档案补全', '疑似重复 3', '公海客户 46']),
    filters: (fr) => { search(fr, '客户名称、联系人'); field(fr, '国家', '全部', 140); },
  });
  page(pg, {
    title: '报销管理',
    tabs: (p) => { const r = H(p, 8); seg(r, ['待报销 3', '报销单']); seg(r, ['我的', '全部']); seg(r, ['待付款 2', '已报销', '已撤回']); },
    filters: (fr) => { search(fr, '报销单号、提交人'); },
  });
});
