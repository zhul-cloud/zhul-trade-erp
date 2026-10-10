import { buildTaskScript, pendingItems, TASK_SCRIPT_HEAD } from './taskScript';

const items = [
  { model: 'LXM32AD30N4', quantity: 1, unit: '台', quotes: [{}] },
  { model: 'LXM32AD18N4', quantity: 2, unit: '台', quotes: [] },
  { model: 'BMH1003P16A2A', quantity: 1, unit: '台', quotes: [{}] },
  { model: 'BMH0702P12A2A', quantity: 2, unit: '', quotes: [] },
];

describe('buildTaskScript', () => {
  it('全部型号：开头一句 + 编号清单', () => {
    expect(buildTaskScript('Schneider', items.slice(0, 2))).toBe(
      `${TASK_SCRIPT_HEAD}\n1. Schneider LXM32AD30N4，要 1 台\n2. Schneider LXM32AD18N4，要 2 台`,
    );
  });

  it('只含未回价的型号，从 1 开始编号；单位为空写「个」', () => {
    expect(buildTaskScript('Schneider', pendingItems(items))).toBe(
      `${TASK_SCRIPT_HEAD}\n1. Schneider LXM32AD18N4，要 2 台\n2. Schneider BMH0702P12A2A，要 2 个`,
    );
  });

  it('品牌为空只写型号', () => {
    expect(buildTaskScript('', [items[1]])).toBe(
      `${TASK_SCRIPT_HEAD}\n1. LXM32AD18N4，要 2 台`,
    );
  });

  it('都已回价时没有未回价型号', () => {
    expect(
      pendingItems(items.map((i) => ({ ...i, quotes: [{}] }))),
    ).toHaveLength(0);
  });
});
