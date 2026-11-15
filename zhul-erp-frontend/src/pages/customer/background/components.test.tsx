import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history } from '@umijs/max';
import { App, ConfigProvider } from 'antd';
import React from 'react';
import { BackgroundActions } from './components';
import { backgroundApi, ERR_RECENT } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
jest.mock('@/components/PageTabs/hooks', () => ({ useTabActive: () => true }));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  backgroundApi: { research: jest.fn(), precheck: jest.fn(), list: jest.fn() },
}));

const h = React.createElement;

/** 页面持有检查窗口的开关（与 useBackgroundPrecheck 相同）；检查接口失败时直接发起背调 */
const Actions = () => {
  const [open, setOpen] = React.useState(false);
  return h(BackgroundActions, {
    customerId: 7,
    customerName: 'Pacific Controls Pty Ltd',
    running: false,
    onStarted: jest.fn(),
    precheck: {
      data: undefined,
      reload: jest.fn().mockResolvedValue(undefined),
      open,
      setOpen,
    },
  });
};

/** audit UI-071：「再背调一次？」里点「先看上次的报告」跳页前先关弹窗，不能盖在报告页上 */
it('先看上次的报告：先关掉弹窗再跳到报告页', async () => {
  (backgroundApi.research as jest.Mock).mockRejectedValue({
    info: {
      data: {
        errorCode: ERR_RECENT,
        detail: {
          reportId: 31,
          reportNo: 'BG001',
          hoursAgo: 3,
          requestedByName: '张伟',
        },
      },
    },
  });
  // 关掉动画，弹窗关闭后立即隐藏
  render(
    h(
      ConfigProvider,
      { theme: { token: { motion: false } } },
      h(App, null, h(Actions)),
    ),
  );
  fireEvent.click(screen.getByRole('button', { name: /AI 背调/ }));
  fireEvent.click(await screen.findByText('先看上次的报告'));
  expect(history.push).toHaveBeenCalledWith('/customer/list/7/background/31');
  await waitFor(() =>
    expect(screen.queryByRole('button', { name: '先看上次的报告' })).toBeNull(),
  );
});
