import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import ts from 'typescript';

/**
 * 没有 href、只绑 onClick 的 <a> 不能用 Tab 选中，键盘用户点不到（audit UI-022）。
 * 命令用 <button type="button" className="zhul-link">（外观同链接），跳转用带地址的链接。
 */
const SRC = join(__dirname);

const tsxFiles = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    if (e.name.startsWith('.umi')) return [];
    const p = join(dir, e.name);
    if (e.isDirectory()) return tsxFiles(p);
    return p.endsWith('.tsx') ? [p] : [];
  });

const clickOnlyAnchors = (file: string): string[] => {
  const sf = ts.createSourceFile(
    file,
    readFileSync(file, 'utf8'),
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX,
  );
  const found: string[] = [];
  const visit = (n: ts.Node) => {
    const open = ts.isJsxElement(n)
      ? n.openingElement
      : ts.isJsxSelfClosingElement(n)
        ? n
        : undefined;
    if (open && open.tagName.getText(sf) === 'a') {
      const names = open.attributes.properties.map((p) =>
        p.name ? p.name.getText(sf) : '...',
      );
      if (names.includes('onClick') && !names.includes('href')) {
        const line = sf.getLineAndCharacterOfPosition(open.getStart(sf)).line;
        found.push(`${relative(SRC, file)}:${line + 1}`);
      }
    }
    ts.forEachChild(n, visit);
  };
  visit(sf);
  return found;
};

describe('列表与页面操作可用键盘（UI-022）', () => {
  it('没有只绑 onClick、不带 href 的 <a>', () => {
    expect(tsxFiles(SRC).flatMap(clickOnlyAnchors)).toEqual([]);
  });
});
