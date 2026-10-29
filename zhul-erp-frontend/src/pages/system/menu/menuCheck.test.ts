import { indexMenuTree, splitChecked, toggleMenuKey } from './menuCheck';
import type { MenuItem } from './service';

const node = (id: number, type: number, children?: MenuItem[]): MenuItem =>
  ({ id, type, name: String(id), children }) as MenuItem;

// 业务设置（目录 1）→ AI 设置（菜单 10）→ 编辑（按钮 101）、使用平台订阅（按钮 102）；汇率（菜单 20）
const tree = [
  node(1, 1, [node(10, 2, [node(101, 3), node(102, 3)]), node(20, 2)]),
];
const index = indexMenuTree(tree);
const sorted = (keys: number[]) => [...keys].sort((a, b) => a - b);

describe('menuCheck', () => {
  it('上级已授权、新增的下级没授权时，上级显示半选，下级不勾', () => {
    const { checked, halfChecked } = splitChecked(index, [1, 10, 101, 20]);
    expect(sorted(checked)).toEqual([20, 101]);
    expect(sorted(halfChecked)).toEqual([1, 10]);
  });

  it('勾选一个按钮只加它和上级，不带上同级的其他按钮', () => {
    expect(sorted(toggleMenuKey(index, [20], 101, true))).toEqual([
      1, 10, 20, 101,
    ]);
  });

  it('勾选菜单会连同下级一起勾上', () => {
    expect(sorted(toggleMenuKey(index, [], 10, true))).toEqual([
      1, 10, 101, 102,
    ]);
  });

  it('取消按钮时，菜单本身保留（只读访问）', () => {
    expect(sorted(toggleMenuKey(index, [1, 10, 101], 101, false))).toEqual([
      1, 10,
    ]);
  });

  it('目录下一个都不剩时，目录也取消', () => {
    expect(toggleMenuKey(index, [1, 20], 20, false)).toEqual([]);
  });
});
