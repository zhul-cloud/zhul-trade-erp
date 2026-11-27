import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { candidateApi } from './service';
import { UnlinkedPanel } from './UnlinkedPanel';

/** openspec unlinked-docs-enhance：显示单号，AI 只给建议，批量采纳只提交有建议型号的组 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
  useLocation: () => ({ pathname: '/product/candidates' }),
}));
jest.mock('@/components/PageTabs/hooks', () => ({
  useOnReactivate: () => undefined,
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  candidateApi: {
    unlinked: jest.fn(),
    adoptUnlinked: jest.fn(),
    aiUnlinkedStatus: jest.fn(),
  },
}));

const refs = (n: number) =>
  Array.from({ length: n }, (_, i) => ({
    type: 'SO',
    id: i + 1,
    no: `SO2026091200${i + 1}`,
  }));

it('显示最近 3 个单号与「等 N 张」，批量采纳只提交 AI 识别为型号的组', async () => {
  (candidateApi.unlinked as jest.Mock).mockResolvedValue({
    total: 2,
    records: [
      {
        key: 'ulm|6es7214',
        brand: '',
        model: '6es7 214',
        docs: { 销售订单: 5 },
        lineCount: 5,
        docRefs: refs(5),
        docCount: 5,
        aiKind: 1,
        aiBrand: 'Siemens',
        aiModel: '6ES7214-1AG40-0XB0',
      },
      {
        key: '|dhl',
        brand: '',
        model: 'DHL运费',
        docs: { 销售订单: 1 },
        lineCount: 1,
        docRefs: refs(1),
        docCount: 1,
        aiKind: 2,
      },
    ],
  });
  (candidateApi.adoptUnlinked as jest.Mock).mockResolvedValue({
    adopted: 1,
    lines: 5,
    failures: [],
  });
  render(
    React.createElement(
      App,
      null,
      React.createElement(UnlinkedPanel, { onChanged: () => undefined }),
    ),
  );
  expect(await screen.findByText('等 5 张')).toBeTruthy();
  expect(screen.getByText('Siemens · 6ES7214-1AG40-0XB0')).toBeTruthy();
  expect(screen.getByText('费用')).toBeTruthy();
  expect(screen.queryByText('SO20260912004')).toBeNull();

  const boxes = screen.getAllByRole('checkbox');
  // 第 0 个是表头全选（是否已标记改在搜索区的状态下拉里）
  fireEvent.click(boxes[1]);
  fireEvent.click(boxes[2]);
  const adopt = await screen.findByRole('button', { name: /批量采纳 1 组/ });
  fireEvent.click(adopt);
  await waitFor(() => expect(candidateApi.adoptUnlinked).toHaveBeenCalled());
  expect((candidateApi.adoptUnlinked as jest.Mock).mock.calls[0][0]).toEqual([
    'ulm|6es7214',
  ]);
});
