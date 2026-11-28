#!/usr/bin/env node
/**
 * 界面检查（openspec ui-redesign 设计第 4 节）：按新界面规范查出还没改的写法。
 *
 * 用法：
 *   npm run lint:ui                       扫描 src，有错误就失败（已并入 npm run lint）
 *   npm run lint:ui -- src/pages/sales    只扫描指定目录或文件（按当前目录解析），有错误就失败
 *   npm run lint:ui -- --by-domain        按业务域汇总（整理 migration-list.md 用）
 *   npm run lint:ui -- --json             输出 JSON
 *
 * 例外：在代码行尾，或单独一行注释写在它上一行：`lint-ui-ignore 规则名: 原因`。
 * 缺原因、规则名写错的例外报错，没用上的例外提示。确认弹窗（confirm）、警告条（Alert）、
 * Popconfirm 里的文字不检查。用 TypeScript 语法树判断注释、字符串和 JSX，不按文本猜。
 */
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const ROOT = path.resolve(__dirname, '..');

/**
 * 必须通过的范围。任务 7.1–7.7 按业务域逐个加入，2026-11-22 全部迁移完（任务 7.8），改为整个 src：
 * 任何新写的旧写法都会让检查失败，并随 `npm run lint` 在 CI 里拦下。
 */
const ENFORCED = ['src'];

/** 不扫描：多语言文案、生成目录 */
const SKIP_DIRS = new Set([
  '.umi',
  '.umi-production',
  '.umi-test',
  'locales',
  'node_modules',
]);
const SKIP_FILES = [/\.test\.[jt]sx?$/, /(^|[\\/])_mock\.[jt]s$/, /\.d\.ts$/];

const RULE_NAMES = new Set([
  'gradient',
  'cjk-weight',
  'font-scale',
  'choice-primary',
  'bank-label',
  'glow',
  'hardcoded-color',
  'old-header',
  'ibm-plex',
  'filler',
  'filler-maybe',
  'long-placeholder',
  'audit-columns',
  'pager',
  'plain-empty',
  'row-tint',
  'danger-confirm',
  'row-actions',
  'attr-tone',
  'antd-empty',
  'antd-icon',
  'primary-soft',
  'progress-color',
  'table-switch',
  'column-settings',
  'scroll-max-content',
  'page-tabs',
  'scope-switch',
  'currency-symbol',
  'num-align',
  'user-name',
]);

/**
 * 类别、属性标签（不是流程状态）：用灰或属性色（blue / purple / magenta / indigo / plum / brown / olive），
 * 不用状态色（绿、橙、红、青、信息蓝、主题色有好坏或「选中」含义）；同一列不同取值颜色不重复靠评审看
 * （ui-redesign D75，推翻 S13 的「一律中性」）。
 * 名单取自 openspec/changes/ui-redesign/status-colors.md 归「中性」的标签，加现货、期货、新客户
 */
const ATTR_LABELS = [
  'AI 建议',
  'AI 生成',
  '业务员',
  '业务员自询',
  '主要',
  '代收',
  '兼职',
  '员工垫付',
  '售后补发',
  '售后退货',
  '客户指定',
  '客户指定货代',
  '已转样品',
  '战略客户',
  '无货',
  '未成交',
  '本公司',
  '核心',
  '线上',
  '线上店铺',
  '老客户',
  '新客户',
  '营销包',
  '采购入库',
  '默认',
  '对公',
  '对私',
  '上门取件',
  '费用',
  '现货',
  '期货',
];

/** 按行检查的文字类规则（在去掉注释后的代码上匹配）；text: 确认弹窗等豁免范围里不查 */
const LINE_RULES = [
  {
    id: 'gradient',
    level: 'error',
    message: '不用渐变（spec interface-style「颜色只表达功能」）',
    re: /\b(repeating-)?(linear|radial|conic)-gradient\(|create(Linear|Radial)Gradient\(/,
  },
  {
    id: 'glow',
    level: 'error',
    message: '不用发光效果（文字阴影、drop-shadow、0 0 Npx 外发光）',
    // 一组阴影开头（或逗号之后）是 0 0 且模糊不小于 6px：外发光；0 0 0 4px 这类焦点环不算
    re: /\btextShadow\b|text-shadow\s*:|drop-shadow\(|(boxShadow|box-shadow)\s*[:=]\s*[`'"]?\s*(?:[^`'";]*,\s*)?0(px)?\s+0(px)?\s+([6-9]|\d{2,})px\b/,
  },
  {
    id: 'ibm-plex',
    level: 'error',
    message: '等宽字体用 src/theme/fonts.ts 的 MONO_FONT',
    re: /IBM Plex Mono/,
  },
  {
    id: 'filler',
    level: 'error',
    message: '常识性套话：可点的内容已带箭头或主题色，不写「点击进入」一类说明',
    text: true,
    re: /点击[^，。；、\s'"`]{0,6}(进入|跳转|查看)|点击可跳转/,
  },
  {
    id: 'audit-columns',
    level: 'error',
    message:
      '列表的创建人、更新人与时间用公共的 auditColumns()（「创建」「更新」两列，ui-redesign D25）',
    re: /title:\s*'(创建时间|创建人|更新时间|更新人)'/,
  },
  {
    id: 'pager',
    level: 'error',
    message:
      '分页用 usePagination() 或 { ...PAGER_STYLE }（每页 20 条、跳页、不重复总数），不自己写 showSizeChanger',
    re: /showSizeChanger:\s*true/,
  },
  {
    id: 'plain-empty',
    level: 'warn',
    message:
      '表格空状态用 tableEmpty(文字, loading) 或 listEmpty()：加载中不显示、配图标，不直接写一句话',
    re: /emptyText:\s*(['`]|[\w.]+\s*(\?|\|\|))/,
  },
  {
    id: 'row-tint',
    level: 'error',
    message:
      '需要关注的行用 rowAlert()（行首 3px 色条加状态标签），不整行铺底色（ui-redesign D7）',
    re: /\{\s*style:\s*\{[^}]*background:\s*\w+\.(redSoft|orangeSoft)\b/,
  },
  {
    id: 'danger-confirm',
    level: 'error',
    message:
      '危险操作的确认框用 useConfirmDanger()（问句标题写明对象、按钮写动作、默认焦点在取消），不自己给 confirm 配红色按钮',
    re: /okButtonProps:\s*(\w+\s*\?\s*)?\{[^}]*\bdanger:\s*true|okType:\s*'danger'/,
  },
  {
    id: 'antd-icon',
    level: 'error',
    message:
      '图标统一用 lucide-react（规范「图标」），不用 @ant-design/icons；品牌标识等 lucide 没有的，用 lint-ui-ignore antd-icon 写明原因',
    re: /from\s+'@ant-design\/icons'/,
  },
  {
    id: 'antd-empty',
    level: 'error',
    message:
      '空状态用公共 EmptyState（小地方加 compact，出错 kind="error"），不用 antd 的 Empty（ui-redesign C4）',
    re: /<Empty[\s/>]/,
  },
  {
    id: 'attr-tone',
    level: 'error',
    message:
      '类别、属性标签（客户指定、老客户、现货、默认等）用灰或属性色 blue / purple / magenta / indigo / plum / brown / olive，不用状态色；同一列不同取值颜色不重复（ui-redesign D75）',
    re: new RegExp(
      `<Pill\\s+tone="(green|orange|red|cyan|info|accent|violet|selected)"[^>]*>\\s*(${ATTR_LABELS.join('|')})\\s*</Pill>`,
    ),
  },
  {
    id: 'primary-soft',
    level: 'error',
    message:
      '主题色浅底只表示选中、当前（ui-redesign D42）：说明文字用 Callout，数字或汇总块用中性浅底 palette.inset，属性标签用中性色；批量操作条、搜索命中等选中类用法写 lint-ui-ignore primary-soft 说明',
    re: /\bbackground(Color)?:\s*[\w.]+\.primarySoft\b/,
  },
  {
    id: 'cjk-weight',
    level: 'error',
    message:
      '字重只用 400、600（检查表 #7）：Windows 的微软雅黑只有常规和粗体，500 显示成常规、700 和 600 看不出差别；<b>、<strong> 已全局兜底为 600',
    re: /\bfontWeight:[^,}\n]*\b(500|700|800|900)\b|\bfontWeight:\s*['"]bold|font-weight:\s*(500|700|800|900|bold)\b/,
  },
  {
    id: 'font-scale',
    level: 'error',
    message:
      '字号只用 12、13、14、16、20、24（看板主角数字 28）：15、17 不在字阶里，标题、强调数字用 16（检查表 #6，UI-117）',
    re: /\bfontSize:\s*(15|17)\b|font-size:\s*(15|17)px/,
  },
  {
    id: 'choice-primary',
    level: 'error',
    message:
      '选中态不用实底或描边主按钮（一屏只有一个主按钮，主题色浅底才表示选中，D42）：单选选项用 ChoiceChips，开关式筛选用 QuickFilters；按状态切成主操作的写 lint-ui-ignore choice-primary 说明',
    re: /\btype=\{[^}]*\?\s*'primary'\s*:/,
  },
  {
    id: 'bank-label',
    level: 'error',
    message:
      '银行账户选项用公共 BankAccountLabel：整串拼接时长银行名会把账号尾号挤出去、或压在右侧的「默认」「事业部」提示上（UI-107）',
    re: /\$\{\w+\.accountNoMasked\}/,
  },
  {
    id: 'scroll-max-content',
    level: 'error',
    message:
      "表格横向滚动宽度用 tableScrollX(columns)（各列宽度之和），不用 'max-content'：那样按内容排版，列宽不生效、长文本不省略（audit UI-041、ui-redesign P10）",
    re: /\bx:\s*'max-content'/,
  },
  {
    id: 'page-tabs',
    level: 'error',
    pages: true,
    message:
      '页面里看不同对象或视图用公共 ViewTabs（下划线页签，数量写「名称 N」）；按状态过滤用搜索区的状态下拉，同一批数据换分组用主卡右侧的分段（复审 S3）。卡片、抽屉里的分区页签写 lint-ui-ignore page-tabs 说明',
    re: /<Tabs\b/,
  },
  {
    id: 'scope-switch',
    level: 'error',
    pages: true,
    message:
      '切换数据范围用公共 ScopeSwitch（页头右侧，统一叫「我的 / 全部」）；内容不同的视图（如团队统计）用 ViewTabs（复审 S3）',
    re: /\bvalue:\s*'mine'\s*,\s*label:|label:\s*'(全部人员|我负责的)'/,
  },
  {
    id: 'currency-symbol',
    level: 'error',
    pages: true,
    message:
      '金额写「币种代码 + 空格 + 金额」（formatAmount），不用 ¥ 符号（ui-design-patterns「金额显示」，复审 S8）',
    re: /['"`]¥/,
  },
  {
    id: 'filler-maybe',
    level: 'warn',
    message: '「保存后生效」类说法：没有额外信息就删掉，有就保留（人工判断）',
    text: true,
    re: /保存后(即可)?生效/,
  },
];

const FONT_SCALE = new Set([12, 13, 14, 16, 20, 24, 28]);
const MSG = {
  fontScale:
    '文字字号只用 12、13、14、16、20、24（看板主角数字 28）：10、11 改 12，18 改 16，22 改 20（检查表 #6）；图标靠 fontSize 定大小不算，分不清的写 lint-ui-ignore font-scale 说明',
  progressColor:
    '进度条只用主题色（palette.chart），程度用深浅表示，完成时也不换成绿色或别的颜色（2026-11-24 用户确认）；多色图表、状态标签不用 Progress',
  columnSettings:
    '用了「创建」「更新」列（auditColumns）的列表要接列设置 useColumnSettings：这两列默认收起，用户可从「列」按钮打开（ui-redesign D55）',
  tableSwitch:
    '列表行里的启用 / 停用开关用公共 StatusSwitch（小号、跟主题色、读屏文字、不触发整行点击、值没变不回调，D48）',
  rowActions:
    '操作列用 RowActions（行内最多 2 个图标，其余收进「更多」，列宽 rowActionsWidth）；审批、工作队列等例外用 lint-ui-ignore row-actions 写明原因',
  numAlign:
    '数量、金额、单价、笔数等数字列要右对齐（align 写 right，规范「对齐出秩序」）；格子里放的不是单个数字（如型号带件数、状态标签）的写 lint-ui-ignore num-align 说明',
  userName:
    '创建人、确认人、操作人等字段存的是登录账号，显示时用公共 UserName / useUserName 换成姓名（ui-redesign C7）；不是用来显示的（拼 key 等）写 lint-ui-ignore user-name 说明',
  color: '页面里不写死颜色，用 useAppTheme().palette 的令牌',
  header:
    '旧页头与面包屑：改用 ListLayout 的 PageHeader（不带说明文字），不显示面包屑',
};

const isSkipped = (rel) => SKIP_FILES.some((r) => r.test(rel));
const posix = (p) => p.split(path.sep).join('/');

function listFiles(abs) {
  if (fs.statSync(abs).isFile()) return [abs];
  const out = [];
  for (const name of fs.readdirSync(abs)) {
    if (SKIP_DIRS.has(name)) continue;
    const p = path.join(abs, name);
    if (fs.statSync(p).isDirectory()) out.push(...listFiles(p));
    else if (/\.(tsx?|jsx?)$/.test(name)) out.push(p);
  }
  return out;
}

/** 遍历语法树（含关键字、括号等 token），用于收集注释 */
function walkAll(node, sf, fn) {
  fn(node);
  for (const c of node.getChildren(sf)) walkAll(c, sf, fn);
}

/** 只遍历语法节点 */
function walk(node, fn) {
  fn(node);
  ts.forEachChild(node, (c) => walk(c, fn));
}

const tagName = (el) => el.tagName.getText();
const attrs = (el) => (el.attributes ? el.attributes.properties : []);
const attrName = (a) => (ts.isJsxAttribute(a) ? a.name.getText() : '');

/** 字符串常量：'…'、"…"、`…`（不含插值时）以及 JSX 属性里的字面量 */
const stringOf = (n) => {
  if (!n) return null;
  if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n))
    return n.text;
  if (ts.isJsxExpression(n)) return stringOf(n.expression);
  return null;
};

const EXEMPT_TAGS = new Set(['Alert', 'Popconfirm']);
const EXEMPT_CALL =
  /^(confirm|(\w+\.)?confirm|[Mm]odal\.(confirm|warning|info|error|success))$/;

/** 检查一个文件的源码；rel 为相对 zhul-erp-frontend 的路径（决定是否为页面文件） */
function checkSource(src, rel) {
  const kind = /\.tsx$|\.jsx$/.test(rel)
    ? ts.ScriptKind.TSX
    : /\.js$/.test(rel)
      ? ts.ScriptKind.JSX
      : ts.ScriptKind.TS;
  const sf = ts.createSourceFile(rel, src, ts.ScriptTarget.Latest, true, kind);
  const isPage = posix(rel).startsWith('src/pages/');
  const lines = src.split('\n');
  const lineOf = (pos) => sf.getLineAndCharacterOfPosition(pos).line;

  // 注释：按位置收集，检查时替换成空格（保留换行），注释里的说法不检查
  const comments = new Map();
  walkAll(sf, sf, (n) => {
    for (const fn of [
      ts.getLeadingCommentRanges,
      ts.getTrailingCommentRanges,
    ]) {
      for (const r of fn(src, n.pos) || []) comments.set(r.pos, r.end);
    }
  });
  const chars = src.split('');
  for (const [a, b] of comments)
    for (let i = a; i < b; i++) if (chars[i] !== '\n') chars[i] = ' ';
  const code = chars.join('');
  const codeLines = code.split('\n');

  // 豁免范围：确认弹窗、警告条、气泡确认
  const exempt = [];
  walk(sf, (n) => {
    if (
      (ts.isJsxElement(n) && EXEMPT_TAGS.has(tagName(n.openingElement))) ||
      (ts.isJsxSelfClosingElement(n) && EXEMPT_TAGS.has(tagName(n)))
    ) {
      exempt.push([n.getStart(sf), n.end]);
    } else if (
      ts.isCallExpression(n) &&
      EXEMPT_CALL.test(n.expression.getText(sf))
    ) {
      exempt.push([n.getStart(sf), n.end]);
    }
  });
  const inExempt = (pos) => exempt.some(([a, b]) => pos >= a && pos < b);

  // 例外注释：行号 -> { rule, reason, line, used, own }，own 为单独一行的注释（才管下一行）
  const ignores = new Map();
  for (const [a, b] of comments) {
    const m = src
      .slice(a, b)
      .match(/lint-ui-ignore\s+([\w-]+)\s*(:\s*(.*?))?\s*(\*\/)?\s*$/s);
    if (!m) continue;
    const line = lineOf(a);
    const own = lines[line]
      .trim()
      .replace(/^\{/, '')
      .trim()
      .startsWith(src.slice(a, a + 2));
    ignores.set(line, {
      rule: m[1],
      reason: (m[3] || '').replace(/\*\/$/, '').trim(),
      line,
      used: false,
      own,
    });
  }

  const issues = [];
  const seen = new Set();
  const push = (rule, level, message, line) => {
    const key = `${line}:${rule}`;
    if (seen.has(key)) return;
    seen.add(key);
    for (const ig of [ignores.get(line), ignores.get(line - 1)]) {
      if (!ig || ig.rule !== rule) continue;
      if (ig.line === line - 1 && !ig.own) continue;
      ig.used = true;
      return;
    }
    issues.push({ file: rel, line: line + 1, rule, level, message });
  };

  // 按行的文字类规则
  let offset = 0;
  codeLines.forEach((l, i) => {
    for (const r of LINE_RULES) {
      if (r.pages && !isPage) continue;
      const m = r.re.exec(l);
      if (!m) continue;
      if (r.text && inExempt(offset + m.index)) continue;
      push(r.id, r.level, r.message, i);
    }
    offset += l.length + 1;
  });

  // 旧页头：包装过的 *PageTitle、PageContainer、Breadcrumb、crumbs，带说明属性或来自旧位置的 PageHeader
  const oldHeaderImports = new Set();
  walk(sf, (n) => {
    if (
      ts.isImportDeclaration(n) &&
      n.importClause?.namedBindings &&
      ts.isNamedImports(n.importClause.namedBindings)
    ) {
      const from = n.moduleSpecifier.text;
      for (const el of n.importClause.namedBindings.elements) {
        const name = el.name.text;
        if (
          name === 'Breadcrumb' ||
          /^([A-Z]\w*)?PageTitle$/.test(name) ||
          (name === 'PageHeader' && from !== '@/components/ListLayout')
        ) {
          if (name === 'PageHeader') oldHeaderImports.add(name);
          push('old-header', 'error', MSG.header, lineOf(el.getStart(sf)));
        }
      }
    }
  });
  walk(sf, (n) => {
    const el = ts.isJsxElement(n)
      ? n.openingElement
      : ts.isJsxSelfClosingElement(n)
        ? n
        : null;
    if (el) {
      const name = tagName(el);
      const props = attrs(el).map(attrName);
      if (
        /^([A-Z]\w*)?PageTitle$/.test(name) ||
        name === 'PageContainer' ||
        name === 'Breadcrumb' ||
        (name === 'PageHeader' &&
          (oldHeaderImports.has('PageHeader') ||
            props.some((p) => p === 'description' || p === 'eyebrow')))
      ) {
        push('old-header', 'error', MSG.header, lineOf(el.getStart(sf)));
      }
      if (props.includes('crumbs'))
        push('old-header', 'error', MSG.header, lineOf(el.getStart(sf)));
    }
    if (
      ts.isVariableDeclaration(n) &&
      ts.isIdentifier(n.name) &&
      /^([A-Z]\w*)?PageTitle$/.test(n.name.text)
    ) {
      push('old-header', 'error', MSG.header, lineOf(n.getStart(sf)));
    }
  });

  // 文字字号要在字阶里：JSX style 里的 fontSize 只用 FONT_SCALE（15、17 由按行规则管）。
  // 图标靠 fontSize 定大小，不算：自闭合元素（<X size="1em" style=…/>）、只包着图标的容器放行；
  // 子元素是表达式、分不清是图标还是文字的，写 lint-ui-ignore font-scale 说明（UI-117 第 8 批）
  walk(sf, (n) => {
    if (!ts.isJsxAttribute(n) || n.name.getText(sf) !== 'style') return;
    const obj = n.initializer?.expression;
    if (!obj || !ts.isObjectLiteralExpression(obj)) return;
    const fs = obj.properties.find(
      (x) =>
        ts.isPropertyAssignment(x) &&
        x.name.getText(sf) === 'fontSize' &&
        ts.isNumericLiteral(x.initializer),
    );
    if (!fs) return;
    const size = Number(fs.initializer.text);
    if (FONT_SCALE.has(size) || size === 15 || size === 17) return;
    const opening = n.parent.parent;
    if (ts.isJsxSelfClosingElement(opening)) return;
    const el = opening.parent;
    if (
      opening.attributes.properties.some(
        (a) => ts.isJsxAttribute(a) && a.name.getText(sf) === 'aria-hidden',
      )
    )
      return;
    // 图标：大写开头的自闭合组件、名字以 icon 结尾的变量、两边都是图标的条件式
    const isIcon = (e) => {
      if (!e) return false;
      if (ts.isParenthesizedExpression(e)) return isIcon(e.expression);
      if (ts.isJsxSelfClosingElement(e)) return /^[A-Z]/.test(tagName(e));
      if (ts.isIdentifier(e) || ts.isPropertyAccessExpression(e))
        return /icon$/i.test(e.getText(sf));
      if (ts.isConditionalExpression(e))
        return isIcon(e.whenTrue) && isIcon(e.whenFalse);
      if (ts.isBinaryExpression(e)) return isIcon(e.right);
      return false;
    };
    const onlyIcons =
      ts.isJsxElement(el) &&
      el.children.every(
        (c) =>
          (ts.isJsxText(c) && !c.text.trim()) ||
          (ts.isJsxExpression(c) ? isIcon(c.expression) : isIcon(c)),
      );
    if (onlyIcons) return;
    push('font-scale', 'error', MSG.fontScale, lineOf(fs.getStart(sf)));
  });

  // 表格操作列：title 为「操作」的列用 RowActions（审批、工作队列等例外写 lint-ui-ignore 并说明）
  walk(sf, (n) => {
    if (!ts.isObjectLiteralExpression(n)) return;
    const prop = (k) =>
      n.properties.find(
        (x) => ts.isPropertyAssignment(x) && x.name.getText() === k,
      );
    const title = prop('title');
    if (!title || stringOf(title.initializer) !== '操作') return;
    const render = prop('render');
    if (render && !render.initializer.getText(sf).includes('RowActions')) {
      push('row-actions', 'error', MSG.rowActions, lineOf(title.getStart(sf)));
    }
  });

  // 数字列右对齐：dataIndex 名是数量、金额、单价、笔数类（…Qty、…Amount、…Price、…Count、…Total 等）的列要写 align
  const NUM_INDEX =
    /(qty|Qty|quantity|Quantity|amount|Amount|Cny|Count|total|Total|price|Price|cost|Cost|fee|Fee)$/;
  walk(sf, (n) => {
    if (!ts.isObjectLiteralExpression(n)) return;
    const prop = (k) =>
      n.properties.find(
        (x) => ts.isPropertyAssignment(x) && x.name.getText() === k,
      );
    const di = prop('dataIndex');
    if (!prop('title') || !di || prop('align')) return;
    const name = stringOf(di.initializer);
    if (name && NUM_INDEX.test(name))
      push('num-align', 'error', MSG.numAlign, lineOf(n.getStart(sf)));
  });

  // 账号字段（…By、operator）直接显示：要经 UserName / useUserName 换成姓名。
  // 只看 JSX 子节点、title 属性、模板字符串里的值；作比较、作条件、传给 value / by 属性或 useUserName 的不算
  const ACCOUNT_FIELD = /^([a-z]+By|operator)$/;
  walk(sf, (n) => {
    if (!ts.isPropertyAccessExpression(n) || !ACCOUNT_FIELD.test(n.name.text))
      return;
    let c = n;
    while (c.parent) {
      const p = c.parent;
      if (
        (ts.isJsxAttribute(p) && /^(value|by)$/.test(p.name.getText(sf))) ||
        (ts.isCallExpression(p) &&
          /useUserName/.test(p.expression.getText(sf))) ||
        (ts.isBinaryExpression(p) &&
          (/^[!=]==?$/.test(p.operatorToken.getText(sf)) ||
            (p.operatorToken.getText(sf) === '&&' && p.left === c))) ||
        (ts.isConditionalExpression(p) && p.condition === c) ||
        ts.isBlock(p) ||
        ts.isSourceFile(p) ||
        ts.isCallExpression(p)
      )
        return;
      const shown =
        ts.isTemplateSpan(p) ||
        (ts.isJsxExpression(p) &&
          (ts.isJsxElement(p.parent) ||
            ts.isJsxFragment(p.parent) ||
            (ts.isJsxAttribute(p.parent) &&
              p.parent.name.getText(sf) === 'title')));
      if (shown) {
        push('user-name', 'error', MSG.userName, lineOf(n.getStart(sf)));
        return;
      }
      c = p;
    }
  });

  // 表格列里直接写 antd Switch：改用 StatusSwitch（列对象 = 有 render，且有 title、dataIndex 或 key）
  walk(sf, (n) => {
    if (!ts.isObjectLiteralExpression(n)) return;
    const prop = (k) =>
      n.properties.find(
        (x) => ts.isPropertyAssignment(x) && x.name.getText() === k,
      );
    const render = prop('render');
    if (!render || !(prop('title') || prop('dataIndex') || prop('key'))) return;
    walk(render.initializer, (c) => {
      const el = ts.isJsxElement(c)
        ? c.openingElement
        : ts.isJsxSelfClosingElement(c)
          ? c
          : null;
      if (el && tagName(el) === 'Switch')
        push('table-switch', 'error', MSG.tableSwitch, lineOf(el.getStart(sf)));
    });
  });

  // 列表用了「创建」「更新」两列就要接列设置（默认收起，必须能打开）；按语法树找调用，注释里写的不算
  const calls = [];
  walk(sf, (n) => {
    if (ts.isCallExpression(n)) calls.push(n);
  });
  const hasSettings = calls.some(
    (n) => n.expression.getText(sf) === 'useColumnSettings',
  );
  if (isPage && !hasSettings) {
    for (const n of calls) {
      const name = n.expression.getText(sf);
      if (name === 'auditColumns' || name === 'proAuditColumns') {
        push(
          'column-settings',
          'error',
          MSG.columnSettings,
          lineOf(n.getStart(sf)),
        );
      }
    }
  }

  // 主题色浅底的条件写法（按语法树判断，D42）：background / backgroundColor 的条件表达式里，
  // 浅底只能出现在「是」分支，且条件是选中、当前类变量（on、active、checked、current 等）或比较、调用（mode === v、picked(id)）；
  // 按类别区分（user ? …、last ? …）、取反（!active ? …）、写在「否」分支、嵌套三元里的都报错
  const SELECTED_COND =
    /^(on|active|checked|selected|sel|cur|current|groupOn|hit|picked|isActive)$|\.current$|===|\(/;
  const PRIMARY_SOFT_MSG = LINE_RULES.find(
    (r) => r.id === 'primary-soft',
  ).message;
  const unwrap = (x) =>
    ts.isParenthesizedExpression(x) ? unwrap(x.expression) : x;
  const isSoft = (x) => /\.primarySoft$/.test(unwrap(x).getText(sf).trim());
  // inSelected：外层已经是选中分支（active ? (danger ? redSoft : primarySoft) : …），里层继承选中态
  const badSoft = (e, inSelected = false) => {
    const yes = unwrap(e.whenTrue);
    const no = unwrap(e.whenFalse);
    // 双重取反（!!x）等于 x 本身，先去掉再判断
    const cond = e.condition.getText(sf).replace(/\s+/g, '').replace(/^!!/, '');
    // 取反（!x、!(x)、!picked(id)）和不等比较（!==）表示「没选中」，不算选中态
    const negated = cond.startsWith('!') || cond.includes('!==');
    const selected = inSelected || (!negated && SELECTED_COND.test(cond));
    if (isSoft(yes) && !selected) return true;
    // 外层已是选中分支时，内层两个分支都属选中态
    if (isSoft(no) && !inSelected) return true;
    return (
      (ts.isConditionalExpression(yes) && badSoft(yes, selected)) ||
      (ts.isConditionalExpression(no) && badSoft(no, inSelected))
    );
  };
  walk(sf, (n) => {
    if (
      !ts.isPropertyAssignment(n) ||
      !['background', 'backgroundColor'].includes(n.name.getText(sf))
    )
      return;
    const init = unwrap(n.initializer);
    if (ts.isConditionalExpression(init) && badSoft(init))
      push('primary-soft', 'error', PRIMARY_SOFT_MSG, lineOf(n.getStart(sf)));
  });

  // 进度条颜色：strokeColor 只能是 palette.chart（按语法树判断，换行写法也查得到）
  walk(sf, (n) => {
    if (
      !ts.isJsxAttribute(n) ||
      n.name.getText(sf) !== 'strokeColor' ||
      !n.initializer
    )
      return;
    const init = n.initializer;
    const expr = ts.isJsxExpression(init) ? init.expression : init;
    if (expr && expr.getText(sf).replace(/\s+/g, '') === 'palette.chart')
      return;
    push('progress-color', 'error', MSG.progressColor, lineOf(n.getStart(sf)));
  });

  // 写死的颜色：只看字符串里的颜色值，链接地址、id 不算
  if (isPage) {
    const COLOR =
      /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![\w-])|\b(rgba?|hsla?)\(\s*\d/g;
    const NOT_COLOR_ATTR = new Set(['href', 'to', 'id', 'key', 'htmlFor']);
    const check = (text, node) => {
      const p = node.parent;
      const owner =
        p && ts.isJsxAttribute(p)
          ? p.name.getText()
          : p && ts.isJsxExpression(p) && ts.isJsxAttribute(p.parent)
            ? p.parent.name.getText()
            : p && ts.isPropertyAssignment(p)
              ? p.name.getText()
              : '';
      if (NOT_COLOR_ATTR.has(owner)) return;
      COLOR.lastIndex = 0;
      for (let m = COLOR.exec(text); m; m = COLOR.exec(text)) {
        if (/^#f{3}(f{3})?$/i.test(m[0])) continue;
        push('hardcoded-color', 'error', MSG.color, lineOf(node.getStart(sf)));
        return;
      }
    };
    walk(sf, (n) => {
      if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n))
        check(n.text, n);
      else if (
        ts.isTemplateHead(n) ||
        ts.isTemplateMiddle(n) ||
        ts.isTemplateTail(n)
      )
        check(n.text, n);
    });
  }

  // 输入框提示：搜索框超过三项判错；其余超过三项或超过 20 个字只提示；多行文本框不查
  walk(sf, (n) => {
    let text = null;
    let el = null;
    if (ts.isJsxAttribute(n) && n.name.getText() === 'placeholder') {
      text = stringOf(n.initializer);
      el = n.parent.parent;
    } else if (
      ts.isPropertyAssignment(n) &&
      n.name.getText() === 'placeholder'
    ) {
      text = stringOf(n.initializer);
    }
    if (text === null || inExempt(n.getStart(sf))) return;
    const tag = el ? tagName(el) : '';
    if (/TextArea|textarea/.test(tag)) return;
    let search =
      /Search/.test(tag) ||
      (el
        ? attrs(el).some(
            (a) => attrName(a) === 'prefix' && /Search/.test(a.getText(sf)),
          )
        : false);
    for (let p = n.parent; p && !search; p = p.parent) {
      if (ts.isJsxElement(p) && tagName(p.openingElement) === 'FilterField')
        search = true;
    }
    const isUrl = /:\/\/|www\./.test(text);
    const items = isUrl
      ? 1
      : text.split(/[、，,]/).filter((s) => s.trim()).length;
    const line = lineOf(n.getStart(sf));
    if (items > 3 && search)
      push(
        'long-placeholder',
        'error',
        `搜索框提示最多三项（现在 ${items} 项），完整范围放进帮助图标`,
        line,
      );
    else if (items > 3)
      push(
        'long-placeholder',
        'warn',
        `输入框提示有 ${items} 项，考虑只留最常用的`,
        line,
      );
    else if (text.length > 20)
      push(
        'long-placeholder',
        'warn',
        `输入框提示 ${text.length} 个字，单行输入框里可能显示不全`,
        line,
      );
  });

  // 例外本身的问题
  for (const ig of ignores.values()) {
    if (!RULE_NAMES.has(ig.rule)) {
      issues.push({
        file: rel,
        line: ig.line + 1,
        rule: 'ignore-invalid',
        level: 'error',
        message: `例外里的规则名「${ig.rule}」不存在`,
      });
    } else if (!ig.reason) {
      issues.push({
        file: rel,
        line: ig.line + 1,
        rule: 'ignore-invalid',
        level: 'error',
        message: '例外要写明原因：lint-ui-ignore 规则名: 原因',
      });
    } else if (!ig.used) {
      issues.push({
        file: rel,
        line: ig.line + 1,
        rule: 'ignore-unused',
        level: 'warn',
        message: '这条例外没有用上，可以删掉',
      });
    }
  }
  return issues.sort((a, b) => a.line - b.line);
}

function run(targets, base = ROOT) {
  const issues = [];
  const missing = [];
  for (const t of targets) {
    const abs = path.resolve(base, t);
    if (!fs.existsSync(abs)) {
      missing.push(t);
      continue;
    }
    for (const f of listFiles(abs)) {
      const rel = path.relative(ROOT, f);
      if (isSkipped(rel)) continue;
      issues.push(...checkSource(fs.readFileSync(f, 'utf8'), rel));
    }
  }
  return { issues, missing };
}

const domainOf = (file) => posix(file).split('/').slice(1, 3).join('/');

function main(argv) {
  const flags = new Set(argv.filter((a) => a.startsWith('--')));
  const targets = argv.filter((a) => !a.startsWith('--'));
  const explicit = targets.length > 0;
  // npm run 会把当前目录记在 INIT_CWD：指定的路径按调用时所在目录解析
  const base = explicit ? process.env.INIT_CWD || process.cwd() : ROOT;
  const { issues, missing } = run(explicit ? targets : ENFORCED, base);
  if (missing.length) {
    process.stderr.write(
      `lint:ui：找不到 ${missing.join('、')}（按 ${base} 解析）\n`,
    );
    return 2;
  }

  if (flags.has('--json')) {
    process.stdout.write(`${JSON.stringify(issues, null, 2)}\n`);
  } else if (flags.has('--by-domain')) {
    const by = new Map();
    for (const it of issues) {
      const d = domainOf(it.file);
      const row = by.get(d) || { domain: d, error: 0, warn: 0, rules: {} };
      row[it.level]++;
      row.rules[it.rule] = (row.rules[it.rule] || 0) + 1;
      by.set(d, row);
    }
    for (const r of [...by.values()].sort((a, b) =>
      a.domain.localeCompare(b.domain),
    )) {
      const rules = Object.entries(r.rules)
        .map(([k, v]) => `${k} ${v}`)
        .join('、');
      process.stdout.write(
        `${r.domain}\t错误 ${r.error}\t提示 ${r.warn}\t${rules}\n`,
      );
    }
  } else {
    for (const it of issues) {
      process.stdout.write(
        `${posix(it.file)}:${it.line}  ${it.level === 'error' ? '错误' : '提示'}  ${it.rule}  ${it.message}\n`,
      );
    }
  }
  const errors = issues.filter((i) => i.level === 'error');
  if (!flags.has('--json')) {
    process.stderr.write(
      `\nlint:ui：${errors.length} 个错误，${issues.length - errors.length} 个提示\n`,
    );
  }
  return errors.length > 0 ? 1 : 0;
}

module.exports = { checkSource, run, main, ENFORCED };

if (require.main === module) {
  process.exitCode = main(process.argv.slice(2));
}
