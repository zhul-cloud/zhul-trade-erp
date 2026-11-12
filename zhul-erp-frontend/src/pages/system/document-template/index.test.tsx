import { fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import DocumentTemplatePage from './index';
import { templateApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/system/document-template', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  templateApi: { types: jest.fn(), versions: jest.fn() },
}));

/** UI-006：首次加载失败，网络恢复后点重试能回到正常页面 */
it('加载失败后点重试能恢复', async () => {
  (templateApi.types as jest.Mock)
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValue([
      { docType: 1, name: '报价单模板-RT', generatable: true, versionCount: 0 },
    ]);
  (templateApi.versions as jest.Mock).mockResolvedValue([]);
  render(
    React.createElement(App, null, React.createElement(DocumentTemplatePage)),
  );
  expect(await screen.findByText('加载失败')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
  expect(await screen.findByText(/报价单模板-RT/)).toBeTruthy();
  expect(screen.queryByText('加载失败')).toBeNull();
});
