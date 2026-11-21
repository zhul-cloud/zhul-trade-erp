#!/usr/bin/env node
/**
 * 界面检查（openspec ui-redesign 设计第 4 节）：按新界面规范查出还没改的写法。
 *
 * 用法：
 *   npm run lint:ui                  扫描 src；只有「已迁移目录」（ENFORCED）里有错误才失败
 *   npm run lint:ui -- src/pages/sales   只扫描指定目录或文件，有错误就失败
 *   npm run lint:ui -- --by-domain   按业务域汇总（整理 migration-list.md 用）
 *   npm run lint:ui -- --json        输出 JSON
 *
 * 例外：在代码行上或上一行写 `lint-ui-ignore 规则名: 原因`（如 `// lint-ui-ignore hardcoded-color: 品牌官方色`），
 * 没写原因的例外本身也报错。确认弹窗（confirm）、警告条（Alert）、Popconfirm 里的文字不检查。
 */
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..');

/** 已迁移、必须通过的目录（相对 zhul-erp-frontend），每迁完一个业务域加进来（任务 7.x） */
const ENFORCED = [
  'src/components/ListLayout',
  'src/components/Shell',
  'src/components/PageTabs',
  'src/theme',
];

/** 不扫描：设计令牌本身、多语言文案、生成目录 */
const SKIP_DIRS = new Set([
  '.umi',
  '.umi-production',
  '.umi-test',
  'locales',
  'node_modules',
]);
const SKIP_FILES = [/\.test\.[jt]sx?$/, /(^|[\\/])_mock\.[jt]s$/, /\.d\.ts$/];
const SKIP_PATHS = [path.join('src', 'theme') + path.sep];

/** 文字类规则（套话、输入框提示）在这些写法里不检查：确认弹窗、警告条、气泡确认 */
const EXEMPT_OPENERS = [
  /\bconfirm\(\s*\{/g,
  /\bModal\.(confirm|warning|info|error)\(\s*\{/g,
  /<Alert\b/g,
  /<Popconfirm\b/g,
];

const RULES = [
  {
    id: 'gradient',
    level: 'error',
    message: '不用渐变（spec interface-style「颜色只表达功能」）',
    test: (line) =>
      /\b(repeating-)?(linear|radial|conic)-gradient\(|create(Linear|Radial)Gradient\(/.test(
        line,
      ),
  },
  {
    id: 'glow',
    level: 'error',
    message: '不用发光效果（文字阴影、drop-shadow、0 0 Npx 外发光）',
    test: (line) =>
      /\btextShadow\b|text-shadow\s*:|drop-shadow\(/.test(line) ||
      // 一组阴影开头（或逗号之后）是 0 0 且模糊不小于 6px：外发光；0 0 0 4px 这类焦点环不算
      /(boxShadow|box-shadow)\s*[:=]\s*[`'"]\s*(?:[^`'"]*,\s*)?0(px)?\s+0(px)?\s+([6-9]|\d{2,})px\b/.test(
        line,
      ),
  },
  {
    id: 'hardcoded-color',
    level: 'error',
    message: '页面里不写死颜色，用 useAppTheme().palette 的令牌',
    pagesOnly: true,
    test: (line) => {
      const hex = line.match(/#[0-9a-fA-F]{3,8}\b/g) || [];
      const bad = hex.filter(
        (h) => [4, 7, 9].includes(h.length) && !/^#f{3}(f{3})?$/i.test(h),
      );
      return bad.length > 0 || /\brgba?\(\s*\d/.test(line);
    },
  },
  {
    id: 'old-header',
    level: 'error',
    message: '旧页头与面包屑：改用 ListLayout 的 PageHeader，不显示面包屑',
    test: (line) =>
      /<PageTitle\b|<PageContainer\b|\bBreadcrumb\b|\bcrumbs\b/.test(line),
  },
  {
    id: 'ibm-plex',
    level: 'error',
    message: '等宽字体用 src/theme/fonts.ts 的 MONO_FONT',
    test: (line) => /IBM Plex Mono/.test(line),
  },
  {
    id: 'filler',
    level: 'error',
    message: '常识性套话：可点的内容已带箭头或主题色，不写「点击进入」一类说明',
    text: true,
    test: (line) =>
      /点击进入|点击可跳转|点击跳转|点击可查看|点击查看详情/.test(line),
  },
  {
    id: 'filler-maybe',
    level: 'warn',
    message: '「保存后生效」类说法：没有额外信息就删掉，有就保留（人工判断）',
    text: true,
    test: (line) => /保存后(即可)?生效/.test(line),
  },
];

/** 单行输入框的提示：超过三项判错，超过 20 个字提示 */
const PLACEHOLDER = /placeholder\s*=\s*(?:\{\s*)?(['"`])([^'"`]*)\1/g;

const isSkipped = (rel) =>
  SKIP_FILES.some((r) => r.test(rel)) ||
  SKIP_PATHS.some((p) => rel.startsWith(p));

function listFiles(target) {
  const abs = path.resolve(ROOT, target);
  if (!fs.existsSync(abs)) return [];
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

/** 找到配对的右括号或标签结束位置 */
function matchEnd(src, start, open, close) {
  let depth = 0;
  for (let i = start; i < src.length; i++) {
    if (src[i] === open) depth++;
    else if (src[i] === close) {
      depth--;
      if (depth === 0) return i;
    }
  }
  return src.length;
}

/** 确认弹窗、警告条、气泡确认所占的字符范围 */
function exemptRanges(src) {
  const ranges = [];
  for (const re of EXEMPT_OPENERS) {
    re.lastIndex = 0;
    for (let m = re.exec(src); m; m = re.exec(src)) {
      if (m[0].startsWith('<')) {
        // 标签：自闭合到 />，否则到对应的结束标签
        const name = m[0].slice(1);
        const close = src.indexOf(`</${name}>`, m.index);
        const self = src.indexOf('/>', m.index);
        const open = src.indexOf('>', m.index);
        const end =
          self !== -1 && self < open + 1 ? self : close === -1 ? open : close;
        ranges.push([m.index, end]);
      } else {
        const brace = src.indexOf('{', m.index);
        ranges.push([m.index, matchEnd(src, brace, '{', '}')]);
      }
    }
  }
  return ranges;
}

const IGNORE = /lint-ui-ignore\s+([\w-]+)\s*:?\s*(.*?)\s*(\*\/\s*\}?\s*)?$/;

/** 例外：本行或上一行的 lint-ui-ignore；返回 { rule, reason } */
function ignoreFor(lines, i) {
  for (const j of [i, i - 1]) {
    if (j < 0) continue;
    const m = lines[j].match(IGNORE);
    if (m)
      return {
        rule: m[1],
        reason: m[2].replace(/\*\/.*$/, '').trim(),
        line: j,
      };
  }
  return null;
}

const isComment = (line) => /^\s*(\/\/|\/?\*)/.test(line);

/** 检查一个文件的源码；rel 为相对 zhul-erp-frontend 的路径（决定是否为页面文件） */
function checkSource(src, rel) {
  const issues = [];
  const lines = src.split(/\r?\n/);
  const offsets = [];
  let off = 0;
  for (const l of lines) {
    offsets.push(off);
    off += l.length + 1;
  }
  const exempt = exemptRanges(src);
  const inExempt = (pos) => exempt.some(([a, b]) => pos >= a && pos <= b);
  const isPage = rel.split(path.sep).join('/').startsWith('src/pages/');
  const reportedIgnores = new Set();

  const push = (rule, level, message, i) => {
    const ig = ignoreFor(lines, i);
    if (ig && (ig.rule === rule || ig.rule === 'all')) {
      if (!ig.reason && !reportedIgnores.has(ig.line)) {
        reportedIgnores.add(ig.line);
        issues.push({
          file: rel,
          line: ig.line + 1,
          rule: 'ignore-no-reason',
          level: 'error',
          message: '例外要写明原因：lint-ui-ignore 规则名: 原因',
        });
      }
      return;
    }
    issues.push({ file: rel, line: i + 1, rule, level, message });
  };

  lines.forEach((line, i) => {
    if (
      isComment(line) ||
      (IGNORE.test(line) && /^\s*(\{\s*)?\/[/*]/.test(line))
    )
      return;
    for (const r of RULES) {
      if (r.pagesOnly && !isPage) continue;
      if (r.text && inExempt(offsets[i])) continue;
      if (r.test(line)) push(r.id, r.level, r.message, i);
    }
  });

  // 输入框提示：看它所在的标签，多行文本框不查
  PLACEHOLDER.lastIndex = 0;
  for (let m = PLACEHOLDER.exec(src); m; m = PLACEHOLDER.exec(src)) {
    if (inExempt(m.index)) continue;
    const tagStart = src.lastIndexOf('<', m.index);
    const tag = (src.slice(tagStart + 1).match(/^[\w.]+/) || [''])[0];
    if (/TextArea|textarea/.test(tag)) continue;
    const text = m[2];
    const items = text.split(/[、，,/]/).filter((s) => s.trim()).length;
    const i = src.slice(0, m.index).split('\n').length - 1;
    if (items > 3)
      push(
        'long-placeholder',
        'error',
        `输入框提示最多三项（现在 ${items} 项），完整范围放进帮助图标`,
        i,
      );
    else if (text.length > 20)
      push(
        'long-placeholder',
        'warn',
        `输入框提示 ${text.length} 个字，单行输入框里可能显示不全`,
        i,
      );
  }
  return issues;
}

function run(targets) {
  const files = targets.flatMap(listFiles);
  const issues = [];
  for (const f of files) {
    const rel = path.relative(ROOT, f);
    if (isSkipped(rel)) continue;
    issues.push(...checkSource(fs.readFileSync(f, 'utf8'), rel));
  }
  return issues;
}

const posix = (p) => p.split(path.sep).join('/');
const enforced = (file) =>
  ENFORCED.some((d) => posix(file) === d || posix(file).startsWith(`${d}/`));
const domainOf = (file) => {
  const parts = posix(file).split('/');
  return parts[1] === 'pages'
    ? `pages/${parts[2]}`
    : parts.slice(1, 3).join('/');
};

function main(argv) {
  const flags = new Set(argv.filter((a) => a.startsWith('--')));
  const targets = argv.filter((a) => !a.startsWith('--'));
  const explicit = targets.length > 0;
  const issues = run(explicit ? targets : ['src']);

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
  const blocking = explicit ? errors : errors.filter((i) => enforced(i.file));
  if (!flags.has('--json')) {
    process.stderr.write(
      `\nlint:ui：${errors.length} 个错误，${issues.length - errors.length} 个提示` +
        (explicit
          ? ''
          : `；已迁移目录里的错误 ${blocking.length} 个（${ENFORCED.join('、')}）`) +
        '\n',
    );
  }
  return blocking.length > 0 ? 1 : 0;
}

module.exports = { checkSource, run, main, RULES, ENFORCED };

if (require.main === module) {
  process.exitCode = main(process.argv.slice(2));
}
