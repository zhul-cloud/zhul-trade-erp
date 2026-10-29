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

const MASK = 'sk-ant-••••••••••••••••abcd';

const profile = (overrides: Record<string, unknown> = {}) => ({
  saved: false,
  hasKey: false,
  apiKeyMasked: '',
  parseModel: '',
  translateModel: '',
  parseEffort: '',
  translateEffort: '',
  maxWebSearches: 20,
  ...overrides,
});

const config = (overrides: Record<string, unknown> = {}) => ({
  configured: true,
  mode: 'api',
  provider: 'claude',
  platform: false,
  subscriptionAllowed: false,
  apiProfiles: {
    claude: profile({ saved: true, hasKey: true, apiKeyMasked: MASK }),
    openai: profile(),
  },
  defaultModels: {
    claude: { parse: 'claude-opus-5-5', translate: 'claude-haiku-5-5' },
    openai: { parse: 'gpt-6.1-sol', translate: 'gpt-6-luna' },
  },
  updateTime: '2026-10-29T14:30:00',
  updateBy: 'zhangmin',
  ...overrides,
});

const subscriptionProfiles = {
  claude: profile({ saved: true, parseModel: 'claude-opus-5-1' }),
  openai: profile(),
};

const renderPage = () =>
  render(React.createElement(App, null, React.createElement(AiSettingsPage)));

/** 连接方式行，顺序：Claude API、Claude 订阅、OpenAI API、OpenAI 订阅（未授权时没有订阅行） */
const rows = () =>
  Array.from(document.querySelectorAll<HTMLElement>('div[role="radio"]'));
const saveButton = () => screen.queryByRole('button', { name: '保 存' });

describe('AiSettingsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mocked.saveAiConfig.mockResolvedValue(undefined);
    mocked.getAiSubscriptionStatus.mockResolvedValue({
      available: { claude: true, openai: false },
    });
  });

  it('未授权订阅的租户只有本公司 API Key，Key 显示脱敏值，模型预填推荐值', async () => {
    mocked.getAiConfig.mockResolvedValue(config());
    renderPage();
    expect(await screen.findByText('AI 已配置')).toBeTruthy();
    expect(rows()).toHaveLength(2);
    expect(screen.queryByText('平台订阅')).toBeNull();
    expect(screen.getByText(MASK)).toBeTruthy();
    expect(screen.getByDisplayValue('claude-opus-5-5')).toBeTruthy();
    expect(mocked.getAiSubscriptionStatus).not.toHaveBeenCalled();
    expect(saveButton()).toBeNull();
  });

  it('改选另一家并填写 Key：保存条出现，一次保存选择与这一行的设置', async () => {
    mocked.getAiConfig.mockResolvedValue(config());
    renderPage();
    await screen.findByText('AI 已配置');
    fireEvent.click(screen.getByRole('button', { name: /^填\s?写$/ }));
    expect(screen.getByDisplayValue('gpt-6.1-sol')).toBeTruthy();
    expect(await screen.findByText('填写 API Key 后才能保存')).toBeTruthy();
    expect((saveButton() as HTMLButtonElement).disabled).toBe(true);

    fireEvent.change(screen.getByPlaceholderText('粘贴 OpenAI 的 API Key'), {
      target: { value: 'sk-proj-new' },
    });
    await waitFor(() =>
      expect((saveButton() as HTMLButtonElement).disabled).toBe(false),
    );
    fireEvent.click(saveButton() as HTMLButtonElement);
    fireEvent.click(await screen.findByRole('button', { name: '确认保存' }));
    await waitFor(() => expect(mocked.saveAiConfig).toHaveBeenCalledTimes(1));
    expect(mocked.saveAiConfig.mock.calls[0][0]).toMatchObject({
      mode: 'api',
      provider: 'openai',
      apiKey: 'sk-proj-new',
      parseModel: 'gpt-6.1-sol',
    });
  });

  it('获授权的租户看到可用与否，选订阅后模型只读，不出现登录相关信息', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config({ subscriptionAllowed: true, subscriptionProfiles }),
    );
    renderPage();
    await screen.findByText('AI 已配置');
    expect(rows()).toHaveLength(4);
    expect(await screen.findByText('可用')).toBeTruthy();
    expect(screen.getByText('不可用')).toBeTruthy();
    expect(rows()[3].getAttribute('aria-disabled')).toBe('true');

    fireEvent.click(rows()[1]);
    expect(await screen.findByText('由平台设置')).toBeTruthy();
    expect(screen.getByText('claude-opus-5-1')).toBeTruthy();
    expect(saveButton()).toBeTruthy();
    expect(document.body.textContent).not.toMatch(/登录|login/);
  });

  it('平台看到登录状态，选中未登录的订阅时显示登录命令', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config({
        platform: true,
        subscriptionAllowed: true,
        subscriptionProfiles,
      }),
    );
    mocked.getAiSubscriptionStatus.mockResolvedValue({
      available: { claude: true, openai: false },
      states: { claude: 'logged_in', openai: 'logged_out' },
      loginCommands: { claude: 'claude-cmd', openai: 'codex-cmd' },
    });
    renderPage();
    expect(await screen.findByText('已登录')).toBeTruthy();
    expect(screen.getByText('未登录')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '登录方法' }));
    expect(await screen.findByText('codex-cmd')).toBeTruthy();
    // 平台的订阅设置可编辑，供获授权的公司共用
    expect(screen.getByText('获授权的公司共用')).toBeTruthy();
  });

  it('选用的订阅不可用时只提示改选，不自动切换', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config({
        mode: 'subscription',
        subscriptionAllowed: true,
        subscriptionProfiles,
      }),
    );
    mocked.getAiSubscriptionStatus.mockResolvedValue({
      available: { claude: false, openai: true },
    });
    renderPage();
    expect(await screen.findByText('Claude 平台订阅不可用')).toBeTruthy();
    expect(screen.getByText('请改选其他可用的方式。')).toBeTruthy();
    expect(saveButton()).toBeNull();
    expect(mocked.saveAiConfig).not.toHaveBeenCalled();
  });

  it('订阅授权被收回时提示改用本公司 Key', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config({ mode: 'subscription', subscriptionAllowed: false }),
    );
    renderPage();
    expect(await screen.findByText('平台订阅不可用')).toBeTruthy();
    expect(screen.getByText('请改用本公司 API Key。')).toBeTruthy();
    expect(mocked.getAiSubscriptionStatus).not.toHaveBeenCalled();
  });

  it('订阅状态获取失败时显示状态未知并可重试', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config({
        mode: 'subscription',
        subscriptionAllowed: true,
        subscriptionProfiles,
      }),
    );
    mocked.getAiSubscriptionStatus.mockResolvedValue({
      available: { claude: null, openai: null },
    });
    renderPage();
    expect(await screen.findByText('订阅状态获取失败')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /重\s?试/ }));
    await waitFor(() =>
      expect(mocked.getAiSubscriptionStatus).toHaveBeenCalledTimes(2),
    );
  });

  it('未配置时提示填写 Key，不能测试连接', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config({
        configured: false,
        apiProfiles: { claude: profile(), openai: profile() },
        updateTime: null,
        updateBy: null,
      }),
    );
    renderPage();
    expect(await screen.findByText('还没有配置 AI')).toBeTruthy();
    expect(screen.getByPlaceholderText('粘贴 Claude 的 API Key')).toBeTruthy();
    expect(
      (screen.getByRole('button', { name: /测试连接/ }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });

  it('首次展开模型下拉框时获取列表', async () => {
    mocked.getAiConfig.mockResolvedValue(config());
    mocked.getAiModels.mockResolvedValue({
      models: [{ id: 'claude-sonnet-5-5', defaultModel: false }],
      source: 'provider',
    });
    const { container } = renderPage();
    await screen.findByText('AI 已配置');
    fireEvent.mouseDown(
      container.querySelectorAll('input[role="combobox"]')[0],
    );
    expect(await screen.findByText('claude-sonnet-5-5')).toBeTruthy();
    expect(mocked.getAiModels.mock.calls[0][0]).toMatchObject({
      mode: 'api',
      provider: 'claude',
    });
  });

  it('测试连接失败时状态卡与 Key 输入框都提示', async () => {
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
  });
});
