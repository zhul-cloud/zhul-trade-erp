import fs from 'node:fs';
import path from 'node:path';
import { CARD_ICONS, FAMILIES, ICONS } from './widgets/BlockWidget';

/**
 * 工作台图标契约（ui-redesign 复审 P2）：数字卡、待办的图标名和图标家族由后端下发，
 * 后端用到的名称前端必须都认识，否则会退回默认图标、家族色也会错。这里直接读后端工作台源码比对。
 */
const WORKBENCH = path.resolve(
  __dirname,
  '../../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/workbench',
);

const javaFiles = (dir: string): string[] =>
  fs
    .readdirSync(dir, { withFileTypes: true })
    .flatMap((d) =>
      d.isDirectory()
        ? javaFiles(path.join(dir, d.name))
        : d.name.endsWith('.java')
          ? [path.join(dir, d.name)]
          : [],
    );

/** 源码里每个 W.<name>( 调用的参数文本（按括号配对，忽略字符串里的括号） */
const calls = (src: string, name: string): string[] => {
  const out: string[] = [];
  const re = new RegExp(`\\bW\\.${name}\\(`, 'g');
  for (let m = re.exec(src); m; m = re.exec(src)) {
    let depth = 1;
    let i = m.index + m[0].length;
    let inStr = false;
    for (; i < src.length && depth > 0; i++) {
      const c = src[i];
      if (inStr) {
        if (c === '\\') i++;
        else if (c === '"') inStr = false;
      } else if (c === '"') inStr = true;
      else if (c === '(') depth++;
      else if (c === ')') depth--;
    }
    out.push(src.slice(m.index + m[0].length, i - 1));
  }
  return out;
};

const literals = (args: string) =>
  [...args.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((x) => x[1]);

const sources = javaFiles(WORKBENCH).map((f) => fs.readFileSync(f, 'utf8'));

it('能读到后端工作台源码', () => {
  expect(sources.length).toBeGreaterThan(0);
});

it('后端数字卡用到的图标和家族，前端都认识', () => {
  const used = sources.flatMap((s) =>
    calls(s, 'look').map((a) => literals(a).slice(-2)),
  );
  expect(used.length).toBeGreaterThan(10);
  for (const [icon, family] of used) {
    expect({ icon, known: icon in CARD_ICONS }).toEqual({ icon, known: true });
    expect({ family, known: FAMILIES.includes(family as never) }).toEqual({
      family,
      known: true,
    });
  }
});

it('后端待办用到的图标，前端都认识', () => {
  const used = sources.flatMap((s) =>
    calls(s, 'todo').map((a) => literals(a)[0]),
  );
  expect(used.length).toBeGreaterThan(10);
  for (const icon of used) {
    expect({ icon, known: icon in ICONS }).toEqual({ icon, known: true });
  }
});
