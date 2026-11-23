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
const SKIP_DIRS = new Set(['.umi', '.umi-production', '.umi-test', 'locales', 'node_modules']);
const SKIP_FILES = [/\.test\.[jt]sx?$/, /(^|[\\/])_mock\.[jt]s$/, /\.d\.ts$/];

const RULE_NAMES = new Set([
  'gradient',
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
]);

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
    re: /emptyText:\s*['`]/,
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
    re: /okButtonProps:\s*\{[^}]*\bdanger:\s*true|okType:\s*'danger'/,
  },
  {
    id: 'filler-maybe',
    level: 'warn',
    message: '「保存后生效」类说法：没有额外信息就删掉，有就保留（人工判断）',
    text: true,
    re: /保存后(即可)?生效/,
  },
];

const MSG = {
  color: '页面里不写死颜色，用 useAppTheme().palette 的令牌',
  header: '旧页头与面包屑：改用 ListLayout 的 PageHeader（不带说明文字），不显示面包屑',
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
  if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n)) return n.text;
  if (ts.isJsxExpression(n)) return stringOf(n.expression);
  return null;
};

const EXEMPT_TAGS = new Set(['Alert', 'Popconfirm']);
const EXEMPT_CALL = /^(confirm|(\w+\.)?confirm|[Mm]odal\.(confirm|warning|info|error|success))$/;

/** 检查一个文件的源码；rel 为相对 zhul-erp-frontend 的路径（决定是否为页面文件） */
function checkSource(src, rel) {
  const kind = /\.tsx$|\.jsx$/.test(rel) ? ts.ScriptKind.TSX : /\.js$/.test(rel) ? ts.ScriptKind.JSX : ts.ScriptKind.TS;
  const sf = ts.createSourceFile(rel, src, ts.ScriptTarget.Latest, true, kind);
  const isPage = posix(rel).startsWith('src/pages/');
  const lines = src.split('\n');
  const lineOf = (pos) => sf.getLineAndCharacterOfPosition(pos).line;

  // 注释：按位置收集，检查时替换成空格（保留换行），注释里的说法不检查
  const comments = new Map();
  walkAll(sf, sf, (n) => {
    for (const fn of [ts.getLeadingCommentRanges, ts.getTrailingCommentRanges]) {
      for (const r of fn(src, n.pos) || []) comments.set(r.pos, r.end);
    }
  });
  const chars = src.split('');
  for (const [a, b] of comments) for (let i = a; i < b; i++) if (chars[i] !== '\n') chars[i] = ' ';
  const code = chars.join('');
  const codeLines = code.split('\n');

  // 豁免范围：确认弹窗、警告条、气泡确认
  const exempt = [];
  walk(sf, (n) => {
    if ((ts.isJsxElement(n) && EXEMPT_TAGS.has(tagName(n.openingElement))) || (ts.isJsxSelfClosingElement(n) && EXEMPT_TAGS.has(tagName(n)))) {
      exempt.push([n.getStart(sf), n.end]);
    } else if (ts.isCallExpression(n) && EXEMPT_CALL.test(n.expression.getText(sf))) {
      exempt.push([n.getStart(sf), n.end]);
    }
  });
  const inExempt = (pos) => exempt.some(([a, b]) => pos >= a && pos < b);

  // 例外注释：行号 -> { rule, reason, line, used, own }，own 为单独一行的注释（才管下一行）
  const ignores = new Map();
  for (const [a, b] of comments) {
    const m = src.slice(a, b).match(/lint-ui-ignore\s+([\w-]+)\s*(:\s*(.*?))?\s*(\*\/)?\s*$/s);
    if (!m) continue;
    const line = lineOf(a);
    const own = lines[line].trim().replace(/^\{/, '').trim().startsWith(src.slice(a, a + 2));
    ignores.set(line, { rule: m[1], reason: (m[3] || '').replace(/\*\/$/, '').trim(), line, used: false, own });
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
    if (ts.isImportDeclaration(n) && n.importClause?.namedBindings && ts.isNamedImports(n.importClause.namedBindings)) {
      const from = n.moduleSpecifier.text;
      for (const el of n.importClause.namedBindings.elements) {
        const name = el.name.text;
        if (name === 'Breadcrumb' || /^([A-Z]\w*)?PageTitle$/.test(name) || (name === 'PageHeader' && from !== '@/components/ListLayout')) {
          if (name === 'PageHeader') oldHeaderImports.add(name);
          push('old-header', 'error', MSG.header, lineOf(el.getStart(sf)));
        }
      }
    }
  });
  walk(sf, (n) => {
    const el = ts.isJsxElement(n) ? n.openingElement : ts.isJsxSelfClosingElement(n) ? n : null;
    if (el) {
      const name = tagName(el);
      const props = attrs(el).map(attrName);
      if (
        /^([A-Z]\w*)?PageTitle$/.test(name) ||
        name === 'PageContainer' ||
        name === 'Breadcrumb' ||
        (name === 'PageHeader' && (oldHeaderImports.has('PageHeader') || props.some((p) => p === 'description' || p === 'eyebrow')))
      ) {
        push('old-header', 'error', MSG.header, lineOf(el.getStart(sf)));
      }
      if (props.includes('crumbs')) push('old-header', 'error', MSG.header, lineOf(el.getStart(sf)));
    }
    if (ts.isVariableDeclaration(n) && ts.isIdentifier(n.name) && /^([A-Z]\w*)?PageTitle$/.test(n.name.text)) {
      push('old-header', 'error', MSG.header, lineOf(n.getStart(sf)));
    }
  });

  // 写死的颜色：只看字符串里的颜色值，链接地址、id 不算
  if (isPage) {
    const COLOR = /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![\w-])|\b(rgba?|hsla?)\(\s*\d/g;
    const NOT_COLOR_ATTR = new Set(['href', 'to', 'id', 'key', 'htmlFor']);
    const check = (text, node) => {
      const p = node.parent;
      const owner = p && ts.isJsxAttribute(p) ? p.name.getText() : p && ts.isJsxExpression(p) && ts.isJsxAttribute(p.parent) ? p.parent.name.getText() : p && ts.isPropertyAssignment(p) ? p.name.getText() : '';
      if (NOT_COLOR_ATTR.has(owner)) return;
      COLOR.lastIndex = 0;
      for (let m = COLOR.exec(text); m; m = COLOR.exec(text)) {
        if (/^#f{3}(f{3})?$/i.test(m[0])) continue;
        push('hardcoded-color', 'error', MSG.color, lineOf(node.getStart(sf)));
        return;
      }
    };
    walk(sf, (n) => {
      if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n)) check(n.text, n);
      else if (ts.isTemplateHead(n) || ts.isTemplateMiddle(n) || ts.isTemplateTail(n)) check(n.text, n);
    });
  }

  // 输入框提示：搜索框超过三项判错；其余超过三项或超过 20 个字只提示；多行文本框不查
  walk(sf, (n) => {
    let text = null;
    let el = null;
    if (ts.isJsxAttribute(n) && n.name.getText() === 'placeholder') {
      text = stringOf(n.initializer);
      el = n.parent.parent;
    } else if (ts.isPropertyAssignment(n) && n.name.getText() === 'placeholder') {
      text = stringOf(n.initializer);
    }
    if (text === null || inExempt(n.getStart(sf))) return;
    const tag = el ? tagName(el) : '';
    if (/TextArea|textarea/.test(tag)) return;
    let search = /Search/.test(tag) || (el ? attrs(el).some((a) => attrName(a) === 'prefix' && /Search/.test(a.getText(sf))) : false);
    for (let p = n.parent; p && !search; p = p.parent) {
      if (ts.isJsxElement(p) && tagName(p.openingElement) === 'FilterField') search = true;
    }
    const isUrl = /:\/\/|www\./.test(text);
    const items = isUrl ? 1 : text.split(/[、，,]/).filter((s) => s.trim()).length;
    const line = lineOf(n.getStart(sf));
    if (items > 3 && search) push('long-placeholder', 'error', `搜索框提示最多三项（现在 ${items} 项），完整范围放进帮助图标`, line);
    else if (items > 3) push('long-placeholder', 'warn', `输入框提示有 ${items} 项，考虑只留最常用的`, line);
    else if (text.length > 20) push('long-placeholder', 'warn', `输入框提示 ${text.length} 个字，单行输入框里可能显示不全`, line);
  });

  // 例外本身的问题
  for (const ig of ignores.values()) {
    if (!RULE_NAMES.has(ig.rule)) {
      issues.push({ file: rel, line: ig.line + 1, rule: 'ignore-invalid', level: 'error', message: `例外里的规则名「${ig.rule}」不存在` });
    } else if (!ig.reason) {
      issues.push({ file: rel, line: ig.line + 1, rule: 'ignore-invalid', level: 'error', message: '例外要写明原因：lint-ui-ignore 规则名: 原因' });
    } else if (!ig.used) {
      issues.push({ file: rel, line: ig.line + 1, rule: 'ignore-unused', level: 'warn', message: '这条例外没有用上，可以删掉' });
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
    process.stderr.write(`lint:ui：找不到 ${missing.join('、')}（按 ${base} 解析）\n`);
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
    for (const r of [...by.values()].sort((a, b) => a.domain.localeCompare(b.domain))) {
      const rules = Object.entries(r.rules).map(([k, v]) => `${k} ${v}`).join('、');
      process.stdout.write(`${r.domain}\t错误 ${r.error}\t提示 ${r.warn}\t${rules}\n`);
    }
  } else {
    for (const it of issues) {
      process.stdout.write(`${posix(it.file)}:${it.line}  ${it.level === 'error' ? '错误' : '提示'}  ${it.rule}  ${it.message}\n`);
    }
  }
  const errors = issues.filter((i) => i.level === 'error');
  if (!flags.has('--json')) {
    process.stderr.write(`\nlint:ui：${errors.length} 个错误，${issues.length - errors.length} 个提示\n`);
  }
  return errors.length > 0 ? 1 : 0;
}

module.exports = { checkSource, run, main, ENFORCED };

if (require.main === module) {
  process.exitCode = main(process.argv.slice(2));
}
