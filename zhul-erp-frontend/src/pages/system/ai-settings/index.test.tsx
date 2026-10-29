import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import AiSettingsPage from './index';
import {
  getAiConfig,
  getAiModels,
  getAiSubscriptionStatus,
  saveAiConfig,
  testAiConfig,
} from './service';

jest.mock('@umijs/max', () => ({
  useAccess: () => ({ 'system:ai-config:edit': true }),
  useLocation: () => ({ pathname: '/system/ai-settings' }),
  useModel: () => ({ initialState: {} }),
  Link: () => null,
  request: jest.fn(),
}));

jest.mock('./service', () => ({
  getAiConfig: jest.fn(),
  saveAiConfig: jest.fn(),
  testAiConfig: jest.fn(),
  getAiModels: jest.fn(),
  getAiSubscriptionStatus: jest.fn(),
}));

const mocked = {
  getAiConfig: getAiConfig as jest.Mock,
  getAiModels: getAiModels as jest.Mock,
  getAiSubscriptionStatus: getAiSubscriptionStatus as jest.Mock,
  saveAiConfig: saveAiConfig as jest.Mock,
  testAiConfig: testAiConfig as jest.Mock,
};

const config = (overrides: Record<string, unknown> = {}) => ({
  configured: true,
  mode: 'api',
  provider: 'claude',
  hasKey: true,
  apiKeyMasked: 'sk-ant-••••••••••••••••abcd',
  parseModel: '',
  translateModel: '',
  parseEffort: '',
  translateEffort: '',
  maxWebSearches: 20,
  subscriptionAllowed: false,
  defaultModels: {
    claude: { parse: 'claude-opus-5-5', translate: 'claude-haiku-5-5' },
    openai: { parse: 'gpt-6.1-sol', translate: 'gpt-6-luna' },
  },
  updateTime: '2026-10-29T14:30:00',
  updateBy: 'zhangmin',
  ...overrides,
});

const renderPage = () =>
  render(React.createElement(App, null, React.createElement(AiSettingsPage)));

const saveButton = () => screen.getByRole('button', { name: '保 存' });

describe('AiSettingsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mocked.saveAiConfig.mockResolvedValue(undefined);
    mocked.getAiSubscriptionStatus.mockResolvedValue({
      codexLoggedIn: true,
      claudeLoggedIn: false,
      codexLoginCommand: 'codex-cmd',
      claudeLoginCommand: 'claude-cmd',
    });
  });

  it('普通租户看不到运行方式，Key 只显示脱敏值，模型预填推荐值', async () => {
    mocked.getAiConfig.mockResolvedValue(config());
    renderPage();
    expect(await screen.findByText('AI 已配置')).toBeTruthy();
    expect(screen.getByDisplayValue('claude-opus-5-5')).toBeTruthy();
    expect(screen.getByDisplayValue('claude-haiku-5-5')).toBeTruthy();
    expect(screen.queryByText('运行方式')).toBeNull();
    expect(screen.queryByText('账号登录状态')).toBeNull();
    expect(mocked.getAiSubscriptionStatus).not.toHaveBeenCalled();
    expect(screen.getByDisplayValue('sk-ant-••••••••••••••••abcd')).toBeTruthy();
  });

  it('平台租户选订阅方式时显示两个账号的登录状态与登录命令', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config({ subscriptionAllowed: true, mode: 'subscription' }),
    );
    renderPage();
    expect(await screen.findByText('运行方式')).toBeTruthy();
    expect(await screen.findByText('账号登录状态')).toBeTruthy();
    expect(await screen.findByText('claude-cmd')).toBeTruthy();
    expect(screen.queryByText('codex-cmd')).toBeNull();
  });

  it('没有修改时不显示保存条；改了搜索次数后保存，不提交 Key、不弹确认', async () => {
    mocked.getAiConfig.mockResolvedValue(config());
    renderPage();
    await screen.findByText('AI 已配置');
    expect(screen.queryByRole('button', { name: '保 存' })).toBeNull();

    const count = screen.getByRole('spinbutton');
    fireEvent.change(count, { target: { value: '21' } });
    fireEvent.blur(count);
    expect(await screen.findByText('有修改未保存')).toBeTruthy();
    fireEvent.click(saveButton());
    await waitFor(() => expect(mocked.saveAiConfig).toHaveBeenCalledTimes(1));
    expect(mocked.saveAiConfig.mock.calls[0][0]).not.toHaveProperty('apiKey');
    expect(mocked.saveAiConfig.mock.calls[0][0].maxWebSearches).toBe(21);
    expect(screen.queryByText('确认更换 API Key？')).toBeNull();
  });

  it('首次展开模型下拉框时获取列表，切换服务商后重新获取', async () => {
    mocked.getAiConfig.mockResolvedValue(config());
    mocked.getAiModels.mockResolvedValue({
      models: [{ id: 'claude-sonnet-5-5', defaultModel: false }],
      source: 'provider',
    });
    const { container } = renderPage();
    await screen.findByText('AI 已配置');
    const parseModel = () =>
      container.querySelectorAll('input[role="combobox"]')[0];
    fireEvent.mouseDown(parseModel());
    expect(await screen.findByText('claude-sonnet-5-5')).toBeTruthy();
    expect(mocked.getAiModels).toHaveBeenCalledTimes(1);
    expect(mocked.getAiModels.mock.calls[0][0]).toMatchObject({
      provider: 'claude',
    });

    fireEvent.click(screen.getByText('OpenAI'));
    // 没改过的推荐模型随服务商换成新的推荐模型
    await waitFor(() =>
      expect((parseModel() as HTMLInputElement).value).toBe('gpt-6.1-sol'),
    );
    // 换服务商后列表作废，展开翻译模型时重新获取
    fireEvent.mouseDown(
      container.querySelectorAll('input[role="combobox"]')[1],
    );
    await waitFor(() => expect(mocked.getAiModels).toHaveBeenCalledTimes(2));
    expect(mocked.getAiModels.mock.calls[1][0]).toMatchObject({
      provider: 'openai',
    });
  });

  it('未配置时显示引导，填写 Key 之前不能测试和保存', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config({
        configured: false,
        hasKey: false,
        apiKeyMasked: '',
        updateTime: null,
        updateBy: null,
      }),
    );
    renderPage();
    expect(await screen.findByText('还没有配置 AI')).toBeTruthy();
    expect(screen.queryByRole('button', { name: '保 存' })).toBeNull();
    expect(
      (screen.getByRole('button', { name: /测试连接/ }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });

  it('更换 Key 用普通文本框，取消更换后恢复脱敏值', async () => {
    mocked.getAiConfig.mockResolvedValue(config());
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /^更s?换$/ }));
    const input = screen.getByPlaceholderText('粘贴 Claude 的 API Key');
    expect(input.getAttribute('type')).toBe('text');
    fireEvent.click(screen.getByRole('button', { name: '取消更换' }));
    expect(await screen.findByDisplayValue('sk-ant-••••••••••••••••abcd')).toBeTruthy();
  });

  it('测试连接失败时状态卡与 Key 字段都提示，未检测的模型显示未检测', async () => {
    mocked.getAiConfig.mockResolvedValue(config());
    mocked.testAiConfig.mockResolvedValue({
      ok: false,
      items: [{ name: 'API Key', ok: false, message: '认证失败' }],
    });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /测试连接/ }));
    expect(await screen.findByText('连接失败')).toBeTruthy();
    expect(screen.getByText('API Key 无效或已失效')).toBeTruthy();
    expect(screen.getByText('解析模型未检测')).toBeTruthy();
    expect(
      await screen.findByText('Key 无效或已失效，点「更换」粘贴新的 Key'),
    ).toBeTruthy();
  });
});
