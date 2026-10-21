import { nextActiveTask } from './activeTask';

const list = [{ id: 3 }, { id: 7 }];

describe('nextActiveTask', () => {
  it('原选中任务仍在列表里时保留，并标记为同一任务', () => {
    expect(nextActiveTask(7, list)).toEqual({ id: 7, kept: true });
  });

  it('原选中任务不在列表里时改选第一个', () => {
    expect(nextActiveTask(9, list)).toEqual({ id: 3, kept: false });
  });

  it('原来没有选中任务时选第一个', () => {
    expect(nextActiveTask(undefined, list)).toEqual({ id: 3, kept: false });
  });

  it('列表为空时不选任何任务', () => {
    expect(nextActiveTask(7, [])).toEqual({ id: undefined, kept: false });
  });
});
