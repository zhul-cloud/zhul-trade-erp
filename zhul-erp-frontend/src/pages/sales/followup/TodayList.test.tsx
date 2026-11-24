import { render, screen } from '@testing-library/react';
import React from 'react';
import { TodayList } from './TodayList';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useLocation: () => ({ pathname: '/sales/followup' }),
  useModel: () => ({ initialState: {} }),
}));
jest.mock('./components', () => ({
  Card: (p: { children?: unknown }) => p.children ?? null,
  CustomerLink: () => null,
  DueText: () => null,
  Pill: (p: { children?: unknown }) => p.children ?? null,
  SnoozeButton: () => null,
  StageTag: () => null,
  ValueCell: () => null,
}));
jest.mock('./QuickActions', () => ({
  QuickButton: () => null,
  StageAdjust: () => null,
}));

const h = React.createElement;

const props = {
  canRecord: true,
  picked: {},
  onPick: jest.fn(),
  onPickAll: jest.fn(),
  batchBar: () => null,
  onRecord: jest.fn(),
  onChanged: jest.fn(),
  onBrowse: jest.fn(),
  tools: h('input', { 'aria-label': '负责人' }),
};

describe('今日待办', () => {
  it('没有待办时仍显示搜索、负责人工具，主管可以换人看', () => {
    render(
      h(TodayList, {
        ...props,
        today: {
          overdue: [],
          today: [],
          handled: [],
          weekRemaining: 0,
          tomorrow: 3,
        },
      }),
    );
    expect(screen.getByText('今天的跟进都处理完了')).toBeTruthy();
    expect(screen.getByLabelText('负责人')).toBeTruthy();
  });
});
