import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { CloseModal } from './dialogs';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  history: { push: jest.fn() },
}));
jest.mock('@/pages/sales/followup/service', () => ({
  followupApi: {
    blockers: jest.fn().mockResolvedValue([]),
    customerRecords: jest.fn().mockResolvedValue({ records: [] }),
  },
}));
jest.mock('@/pages/inquiry/shared/service', () => ({ inquiryApi: {} }));
jest.mock('@/pages/inquiry/shared/components', () => ({
  useLevels: () => [],
}));
jest.mock('@/utils/dict', () => ({
  DICT_SOURCE_CHANNEL: 'source_channel',
  useDictOptions: () => ({ options: [], labelOf: () => '' }),
}));

const detail = (nurtureDefaultDays?: number): any => ({
  id: 1,
  customerId: 2,
  stageCode: 'S3',
  stageName: '需求确认',
  nurtureDefaultDays,
});

const h = React.createElement;
const show = (d: any) =>
  render(
    h(
      App,
      null,
      h(CloseModal, {
        detail: d,
        kind: 'LOST',
        onClose: () => {},
        onDone: () => {},
      }),
    ),
  );

/** audit UI-068：转培育的提醒天数取租户参数，与后端实际激活日期一致 */
it('输单弹窗按租户的培育默认天数提示', () => {
  show(detail(30));
  expect(screen.getByText('转入培育，30 天后提醒重新激活')).toBeTruthy();
});
