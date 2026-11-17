import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
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

let mockCanEdit = true;

jest.mock('@umijs/max', () => ({
  useAccess: () => ({ 'system:ai-config:edit': mockCanEdit }),
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

/** 记下每个分组拖动区域的 onDragEnd，测试里直接触发一次拖放（jsdom 没有布局，无法真实拖动） */
const mockDragEnds: Record<string, (e: unknown) => void> = {};
jest.mock('@dnd-kit/core', () => {
  const actual = jest.requireActual('@dnd-kit/core');
  const R = jest.requireActual('react');
  return {
    ...actual,
    DndContext: (props: { id: string; onDragEnd: (e: unknown) => void }) => {
      mockDragEnds[props.id] = props.onDragEnd;
      return R.createElement(actual.DndContext, props);
    },
  };
});

const mocked = {
  getAiConfig: getAiConfig as jest.Mock,
  getAiModels: getAiModels as jest.Mock,
  getAiSubscriptionStatus: getAiSubscriptionStatus as jest.Mock,
  saveAiConfig: saveAiConfig as jest.Mock,
  testAiConfig: testAiConfig as jest.Mock,
};

const MASK = 'sk-ant-••••••••••••••••abcd';

const conn = (overrides: Record<string, unknown> = {}) => ({
  mode: 'api',
  provider: 'claude',
  order: 1,
  enabled: true,
  state: 'ready',
  pausedReason: null,
  pausedUntil: null,
  saved: true,
  hasKey: true,
  apiKeyMasked: MASK,
  parseModel: '',
  translateModel: '',
  parseEffort: '',
  translateEffort: '',
  maxWebSearches: 20,
  settingsReadonly: false,
  ...overrides,
});

const openaiNoKey = conn({
  provider: 'openai',
  order: 2,
  state: 'no_key',
  saved: false,
  hasKey: false,
  apiKeyMasked: '',
});
const openaiReady = conn({
  provider: 'openai',
  order: 2,
  apiKeyMasked: 'sk-proj••••9f2c',
});

const config = (
  connections: Record<string, unknown>[],
  overrides: Record<string, unknown> = {},
) => ({
  platform: false,
  subscriptionAllowed: connections.some((c) => c.mode === 'subscription'),
  connections,
  defaultModels: {
    claude: { parse: 'claude-opus-5-5', translate: 'claude-haiku-5-5' },
    openai: { parse: 'gpt-6.1-sol', translate: 'gpt-6-luna' },
  },
  ...overrides,
});

const renderPage = () =>
  render(React.createElement(App, null, React.createElement(AiSettingsPage)));

const card = (key: string) => screen.getByTestId(`conn-${key}`);
const select = (key: string) =>
  fireEvent.click(within(card(key)).getAllByRole('button')[0]);
const saveButton = () => screen.queryByRole('button', { name: '保 存' });
/** 在某一组的拖动区域里触发一次拖放 */
const drag = (group: 'api' | 'subscription', active: string, over: string) =>
  act(() => {
    mockDragEnds[`ai-connections-${group}`]({
      active: { id: active },
      over: { id: over },
    });
  });

describe('AiSettingsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    for (const k of Object.keys(mockDragEnds)) delete mockDragEnds[k];
    mockCanEdit = true;
    mocked.saveAiConfig.mockResolvedValue(undefined);
  });

  it('未授权订阅的租户只有 API Key 组，Key 显示脱敏值，模型预填推荐值，没有修改时不出现保存条', async () => {
    mocked.getAiConfig.mockResolvedValue(config([conn(), openaiNoKey]));
    renderPage();
    expect(await screen.findByText('AI 可用')).toBeTruthy();
    expect(screen.queryByText('平台订阅')).toBeNull();
    expect(screen.getByText(MASK)).toBeTruthy();
    expect(screen.getByText('填写 Key 后参与使用')).toBeTruthy();
    expect(screen.getByText('模型与用量 · Claude API Key')).toBeTruthy();
    expect(screen.getByDisplayValue('claude-opus-5-5')).toBeTruthy();
    expect(mocked.getAiSubscriptionStatus).not.toHaveBeenCalled();
    expect(saveButton()).toBeNull();
    expect(
      screen.getByText(/每次调用最多搜索几次；大文件分批解析时每批各自计算/),
    ).toBeTruthy();
  });

  it('组内拖动后保存条说明新顺序，保存提交调整后的顺序', async () => {
    mocked.getAiConfig.mockResolvedValue(config([conn(), openaiReady]));
    renderPage();
    await screen.findByText('AI 可用');
    drag('api', 'api:openai', 'api:claude');
    expect(
      await screen.findByText(
        '连接方式顺序已调整：OpenAI API Key 排到第 1 位，保存后新任务按新顺序使用',
      ),
    ).toBeTruthy();
    expect(
      screen.getByText('顺序调整后还没保存，新任务仍按原顺序使用'),
    ).toBeTruthy();
    fireEvent.click(saveButton() as HTMLButtonElement);
    await waitFor(() => expect(mocked.saveAiConfig).toHaveBeenCalledTimes(1));
    expect(mocked.saveAiConfig.mock.calls[0][0]).toEqual({
      order: [
        { mode: 'api', provider: 'openai', enabled: true },
        { mode: 'api', provider: 'claude', enabled: true },
      ],
      profiles: [],
    });
  });

  it('订阅卡片拖到 API Key 组不移动', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config([
        conn(),
        conn({ mode: 'subscription', state: 'available', hasKey: false }),
      ]),
    );
    renderPage();
    await screen.findByText('AI 可用');
    drag('subscription', 'subscription:claude', 'api:claude');
    expect(saveButton()).toBeNull();
  });

  it('关闭一张卡片并给另一家填写 Key：确认后一次保存开关与这一种的设置', async () => {
    mocked.getAiConfig.mockResolvedValue(config([conn(), openaiNoKey]));
    renderPage();
    await screen.findByText('AI 可用');
    fireEvent.click(
      screen.getByRole('switch', { name: 'Claude API Key 是否参与使用' }),
    );
    expect(within(card('api:claude')).getByText('已关闭')).toBeTruthy();
    select('api:openai');
    expect(screen.getByDisplayValue('gpt-6.1-sol')).toBeTruthy();
    fireEvent.change(screen.getByPlaceholderText('粘贴 OpenAI 的 API Key'), {
      target: { value: 'sk-proj-new' },
    });
    fireEvent.click(saveButton() as HTMLButtonElement);
    fireEvent.click(await screen.findByRole('button', { name: '确认保存' }));
    await waitFor(() => expect(mocked.saveAiConfig).toHaveBeenCalledTimes(1));
    const payload = mocked.saveAiConfig.mock.calls[0][0];
    expect(payload.order).toEqual([
      { mode: 'api', provider: 'claude', enabled: false },
      { mode: 'api', provider: 'openai', enabled: true },
    ]);
    expect(payload.profiles).toHaveLength(1);
    expect(payload.profiles[0]).toMatchObject({
      mode: 'api',
      provider: 'openai',
      apiKey: 'sk-proj-new',
      parseModel: 'gpt-6.1-sol',
    });
  });

  it('暂停中的卡片显示原因与恢复时间，顶部说明先用哪个', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config([
        conn({ pausedReason: 'quota', pausedUntil: '2026-11-17T14:32:00' }),
        openaiReady,
      ]),
    );
    renderPage();
    expect(
      await screen.findByText('AI 可用 · 1 个连接方式暂停中'),
    ).toBeTruthy();
    expect(
      screen.getByText(
        'Claude API Key 额度用完，新任务先用 OpenAI API Key，14:32 后恢复按原顺序',
      ),
    ).toBeTruthy();
    expect(within(card('api:claude')).getByText('暂停中')).toBeTruthy();
    expect(
      screen.getByText(
        '额度用完，14:32 自动恢复；更换 Key 或测试通过后立即恢复',
      ),
    ).toBeTruthy();
    expect(screen.getByText('暂停中 · 额度用完')).toBeTruthy();
  });

  it('测试通过后刷新状态（解除暂停），未保存的修改保留', async () => {
    mocked.getAiConfig
      .mockResolvedValueOnce(
        config([
          conn({ pausedReason: 'auth', pausedUntil: '2026-11-17T14:32:00' }),
          openaiReady,
        ]),
      )
      .mockResolvedValue(config([conn(), openaiReady]));
    mocked.testAiConfig.mockResolvedValue({ ok: true, items: [] });
    renderPage();
    await screen.findByText('AI 可用 · 1 个连接方式暂停中');
    drag('api', 'api:openai', 'api:claude');
    fireEvent.click(
      within(card('api:claude')).getByRole('button', { name: /^测\s?试$/ }),
    );
    await waitFor(() => expect(mocked.getAiConfig).toHaveBeenCalledTimes(2));
    expect(mocked.testAiConfig.mock.calls[0][0]).toMatchObject({
      mode: 'api',
      provider: 'claude',
    });
    expect(await screen.findByText('AI 可用')).toBeTruthy();
    expect(saveButton()).toBeTruthy();
  });

  it('测试未通过时卡片下方列出原因', async () => {
    mocked.getAiConfig.mockResolvedValue(config([conn(), openaiReady]));
    mocked.testAiConfig.mockResolvedValue({
      ok: false,
      items: [{ name: 'API Key', ok: false, message: 'Key 无效' }],
    });
    renderPage();
    await screen.findByText('AI 可用');
    fireEvent.click(
      within(card('api:openai')).getByRole('button', { name: /^测\s?试$/ }),
    );
    expect(await screen.findByText('API Key：Key 无效')).toBeTruthy();
  });

  it('获授权的租户：订阅卡片只显示可用与否，模型由平台设置，不出现登录信息', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config([
        conn(),
        conn({
          mode: 'subscription',
          state: 'available',
          hasKey: false,
          parseModel: 'claude-opus-5-1',
          settingsReadonly: true,
        }),
        conn({
          mode: 'subscription',
          provider: 'openai',
          state: 'unavailable',
          hasKey: false,
          settingsReadonly: true,
        }),
      ]),
    );
    renderPage();
    await screen.findByText('AI 可用');
    expect(screen.getByText('API Key 都不可用时，才使用平台订阅')).toBeTruthy();
    expect(within(card('subscription:claude')).getByText('可用')).toBeTruthy();
    expect(
      within(card('subscription:openai')).getByText('不可用'),
    ).toBeTruthy();
    select('subscription:claude');
    expect(await screen.findByText('由平台设置')).toBeTruthy();
    expect(screen.getByText('claude-opus-5-1')).toBeTruthy();
    expect(saveButton()).toBeNull();
    expect(document.body.textContent).not.toMatch(/登录|login/);
  });

  it('平台选中未登录的订阅时显示登录命令', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config(
        [
          conn(),
          conn({ mode: 'subscription', state: 'logged_in', hasKey: false }),
          conn({
            mode: 'subscription',
            provider: 'openai',
            state: 'logged_out',
            hasKey: false,
          }),
        ],
        { platform: true },
      ),
    );
    mocked.getAiSubscriptionStatus.mockResolvedValue({
      available: { claude: true, openai: false },
      loginCommands: { claude: 'claude-cmd', openai: 'codex-cmd' },
    });
    renderPage();
    await screen.findByText('AI 可用');
    expect(screen.getByText('已登录')).toBeTruthy();
    expect(screen.getByText('未登录')).toBeTruthy();
    select('subscription:openai');
    expect(await screen.findByText('codex-cmd')).toBeTruthy();
    expect(screen.getByText('获授权的公司共用')).toBeTruthy();
  });

  it('没有可用的连接方式时提示暂不可用', async () => {
    mocked.getAiConfig.mockResolvedValue(
      config([
        conn({ pausedReason: 'auth', pausedUntil: '2026-11-17T14:32:00' }),
        openaiNoKey,
      ]),
    );
    renderPage();
    expect(await screen.findByText('AI 暂不可用')).toBeTruthy();
    expect(
      screen.getByText('认证失败，14:32 自动恢复；Key 可能已失效，建议更换'),
    ).toBeTruthy();
  });

  it('只读用户不能拖动、开关或测试', async () => {
    mockCanEdit = false;
    mocked.getAiConfig.mockResolvedValue(config([conn(), openaiReady]));
    renderPage();
    await screen.findByText('AI 可用');
    expect(screen.queryByText('拖动卡片调整顺序')).toBeNull();
    const handle = document.querySelector(
      'button[aria-label="拖动调整 Claude API Key 的顺序"]',
    ) as HTMLButtonElement;
    expect(handle.disabled).toBe(true);
    expect(
      (
        screen.getByRole('switch', {
          name: 'Claude API Key 是否参与使用',
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
    expect(screen.queryByRole('button', { name: /^测\s?试$/ })).toBeNull();
  });

  it('首次展开模型下拉框时按选中的卡片获取列表', async () => {
    mocked.getAiConfig.mockResolvedValue(config([conn(), openaiReady]));
    mocked.getAiModels.mockResolvedValue({
      models: [{ id: 'gpt-6-terra', defaultModel: false }],
      source: 'provider',
    });
    const { container } = renderPage();
    await screen.findByText('AI 可用');
    select('api:openai');
    fireEvent.mouseDown(
      container.querySelectorAll('input[role="combobox"]')[0],
    );
    expect(await screen.findByText('gpt-6-terra')).toBeTruthy();
    expect(mocked.getAiModels.mock.calls[0][0]).toMatchObject({
      mode: 'api',
      provider: 'openai',
    });
  });
});
