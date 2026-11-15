import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PlanModal } from './dialogs';
import { afterSalesApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
jest.mock('@/pages/warehouse/components', () => ({
  AttachmentWall: () => null,
}));
jest.mock('@/utils/dict', () => ({
  useDictTexts: () => [
    { code: 'INVESTIGATING', name: '调查中' },
    { code: 'QUALITY', name: '产品质量' },
  ],
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  afterSalesApi: {
    plan: jest.fn(),
    owners: jest.fn(() => Promise.resolve([{ id: 1, name: '林' }])),
  },
}));

const data = {
  id: 7,
  asNo: 'AS20261115001',
  levelCode: 'S3',
  reasonCode: 'INVESTIGATING',
  solutions: ['EXCHANGE'],
  ownerId: 1,
  nextAction: '等供应商检测',
  nextUpdateAt: '2026-11-16 10:00:00',
};

beforeEach(() => jest.clearAllMocks());

/** audit BUS-052：只提交改过的字段，打开时快照里没改的原因、方案、负责人不提交，不覆盖他人刚改的值 */
it('处理信息只提交改动的字段', async () => {
  (afterSalesApi.plan as jest.Mock).mockResolvedValue(data);
  const onDone = jest.fn();
  render(
    React.createElement(
      App,
      null,
      React.createElement(PlanModal, {
        open: true,
        data: data as never,
        onClose: () => undefined,
        onDone,
      }),
    ),
  );
  fireEvent.change(screen.getByLabelText('下一步行动'), {
    target: { value: '寄样品给客户' },
  });
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(afterSalesApi.plan).toHaveBeenCalledWith(7, {
    nextAction: '寄样品给客户',
    nextUpdateAt: '2026-11-16T10:00:00',
  });
});

it('什么都没改时不提交处理信息里的其他字段', async () => {
  (afterSalesApi.plan as jest.Mock).mockResolvedValue(data);
  const onDone = jest.fn();
  render(
    React.createElement(
      App,
      null,
      React.createElement(PlanModal, {
        open: true,
        data: data as never,
        onClose: () => undefined,
        onDone,
      }),
    ),
  );
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(afterSalesApi.plan).toHaveBeenCalledWith(7, {});
});
