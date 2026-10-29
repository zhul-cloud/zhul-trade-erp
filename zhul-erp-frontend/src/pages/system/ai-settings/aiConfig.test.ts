import {
  defaultModel,
  isDirty,
  modelOptions,
  profileFormValues,
  subscriptionState,
  testSlot,
  toFormValues,
  toPayload,
} from './aiConfig';
import type { AiConfig } from './service';

const DEFAULTS = {
  claude: { parse: 'claude-opus-5-5', translate: 'claude-haiku-5-5' },
  openai: { parse: 'gpt-6.1-sol', translate: 'gpt-6-luna' },
};

describe('toPayload', () => {
  it('Key 留空时不提交，后端沿用已保存的 Key', () => {
    const payload = toPayload({
      provider: 'openai',
      apiKey: '  ',
      maxWebSearches: 5,
    });
    expect(payload).not.toHaveProperty('apiKey');
    expect(payload).toMatchObject({
      mode: 'api',
      provider: 'openai',
      maxWebSearches: 5,
    });
  });

  it('手动输入的模型名原样提交（去掉首尾空格）', () => {
    const payload = toPayload({
      parseModel: ' my-custom-model ',
      apiKey: ' sk-new ',
    });
    expect(payload.parseModel).toBe('my-custom-model');
    expect(payload.apiKey).toBe('sk-new');
    expect(payload.provider).toBe('claude');
    expect(payload.maxWebSearches).toBe(20);
  });
});

const profile = (overrides: Record<string, unknown> = {}) => ({
  saved: true,
  hasKey: false,
  apiKeyMasked: '',
  parseModel: '',
  translateModel: '',
  parseEffort: '',
  translateEffort: '',
  maxWebSearches: 20,
  ...overrides,
});

const config = (overrides: Record<string, unknown> = {}) =>
  ({
    configured: true,
    mode: 'api',
    provider: 'claude',
    platform: false,
    subscriptionAllowed: true,
    apiProfiles: {
      claude: profile({ hasKey: true, translateEffort: 'high' }),
      openai: profile({ parseModel: 'gpt-6-terra', maxWebSearches: 8 }),
    },
    subscriptionProfiles: {
      claude: profile({ parseModel: 'claude-opus-5-1' }),
      openai: profile(),
    },
    defaultModels: DEFAULTS,
    ...overrides,
  }) as AiConfig;

describe('profileFormValues', () => {
  it('Key 不回填，模型没填时预填推荐模型，思考强度空值显示为未选', () => {
    const values = toFormValues(config());
    expect(values.apiKey).toBe('');
    expect(values.parseModel).toBe('claude-opus-5-5');
    expect(values.parseEffort).toBeUndefined();
    expect(values.translateEffort).toBe('high');
  });

  it('按连接方式取各自的设置，两家互不影响', () => {
    expect(profileFormValues(config(), 'api', 'openai')).toMatchObject({
      parseModel: 'gpt-6-terra',
      translateModel: 'gpt-6-luna',
      maxWebSearches: 8,
    });
    expect(
      profileFormValues(config(), 'subscription', 'claude').parseModel,
    ).toBe('claude-opus-5-1');
  });
});

describe('subscriptionState', () => {
  const status = { available: { claude: true, openai: false } };

  it('平台看登录状态，租户只看可用与否', () => {
    expect(subscriptionState(status, 'claude', true)).toBe('in');
    expect(subscriptionState(status, 'openai', true)).toBe('out');
    expect(subscriptionState(status, 'claude', false)).toBe('avail');
    expect(subscriptionState(status, 'openai', false)).toBe('unavail');
  });

  it('查询失败或还没查到为未知', () => {
    expect(
      subscriptionState(
        { available: { claude: null, openai: null } },
        'claude',
        false,
      ),
    ).toBe('unknown');
    expect(subscriptionState(undefined, 'openai', true)).toBe('unknown');
  });
});

describe('defaultModel', () => {
  it('按服务商和字段取默认模型，服务商为空时按 claude', () => {
    expect(defaultModel(DEFAULTS, 'openai', 'translate')).toBe('gpt-6-luna');
    expect(defaultModel(DEFAULTS, undefined, 'parse')).toBe('claude-opus-5-5');
    expect(defaultModel(undefined, 'openai', 'parse')).toBe('');
  });
});

describe('modelOptions', () => {
  it('推荐模型排最前并标记，账号默认另行标记', () => {
    const options = modelOptions(
      {
        source: 'provider',
        models: [
          { id: 'gpt-6-terra', defaultModel: false },
          { id: 'gpt-6.1-sol', defaultModel: true },
          { id: 'gpt-6-luna', defaultModel: false },
        ],
      },
      'gpt-6-luna',
    );
    expect(options.map((o) => o.value)).toEqual([
      'gpt-6-luna',
      'gpt-6-terra',
      'gpt-6.1-sol',
    ]);
    expect(options[0].tags).toEqual(['推荐']);
    expect(options[1].tags).toEqual([]);
    expect(options[2].tags).toEqual(['账号默认']);
  });

  it('没有获取到列表时仍有推荐模型可选', () => {
    expect(modelOptions(undefined, 'claude-opus-5-5')).toEqual([
      { value: 'claude-opus-5-5', tags: ['推荐'] },
    ]);
  });
});

describe('isDirty', () => {
  const saved = { provider: 'claude' as const, maxWebSearches: 20 };

  it('值相同为未修改，思考强度空值与「中」视为相同', () => {
    expect(isDirty({ ...saved, parseEffort: 'medium' }, saved)).toBe(false);
    expect(isDirty({ ...saved, parseModel: '  ' }, saved)).toBe(false);
  });

  it('改了任一字段或新填了 Key 都算有修改', () => {
    expect(isDirty({ ...saved, maxWebSearches: 30 }, saved)).toBe(true);
    expect(isDirty({ ...saved, provider: 'openai' }, saved)).toBe(true);
    expect(isDirty({ ...saved, apiKey: 'sk-new' }, saved)).toBe(true);
  });
});

describe('testSlot', () => {
  const items = [
    { name: 'API Key', ok: true, message: '有效' },
    { name: '解析模型 claude-opus-5-5', ok: false, message: '模型不存在' },
  ];

  it('按名称前缀归位，没测到的项为空', () => {
    expect(testSlot(items, 'API Key')?.ok).toBe(true);
    expect(testSlot(items, '解析模型')?.message).toBe('模型不存在');
    expect(testSlot(items, '翻译模型')).toBeUndefined();
  });
});
