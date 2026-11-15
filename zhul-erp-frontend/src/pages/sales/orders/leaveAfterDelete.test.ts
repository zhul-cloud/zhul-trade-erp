import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import ts from 'typescript';

/**
 * audit UI-085：删除订单后离开详情页走标签页的 tabsActions.backOr：有上一页退回保活的上一页（列表由 useOnReactivate 刷新），
 * 没有才替换成列表；不能用 history.replace，否则标签栈留下重复的列表、从其他单据进来回不到来源页。
 */
const onDeletedText = () => {
  const file = join(__dirname, 'detail.tsx');
  const sf = ts.createSourceFile(
    file,
    readFileSync(file, 'utf8'),
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX,
  );
  let found = '';
  const visit = (n: ts.Node) => {
    if (
      (ts.isJsxSelfClosingElement(n) || ts.isJsxOpeningElement(n)) &&
      n.tagName.getText(sf) === 'DeleteOrderModal'
    ) {
      const attr = n.attributes.properties.find(
        (p) => p.name?.getText(sf) === 'onDeleted',
      );
      found = attr?.getText(sf) ?? '';
    }
    ts.forEachChild(n, visit);
  };
  visit(sf);
  return found;
};

it('删除订单后用 tabsActions.backOr 离开', () => {
  const text = onDeletedText();
  expect(text).toContain("tabsActions.backOr('/sales/orders')");
  expect(text).not.toContain('history.replace');
});
