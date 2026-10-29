import {
  defaultModel,
  isDirty,
  modelAfterProviderChange,
  modelOptions,
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

describe('toFormValues', () => {
  it('Key 不回填，思考强度空值显示为未选', () => {
    const values = toFormValues({
      configured: true,
      mode: 'api',
      provider: 'claude',
      hasKey: true,
      apiKeyMasked: 'sk-ant-••••••••••••••••abcd',
      parseModel: '',
      translateModel: '',
      parseEffort: '',
      translateEffort: 'high',
      maxWebSearches: 8,
      subscriptionAllowed: false,
      defaultModels: DEFAULTS,
    } as AiConfig);
    expect(values).not.toHaveProperty('apiKey');
    expect(values.parseModel).toBe('claude-opus-5-5');
    expect(values.translateModel).toBe('claude-haiku-5-5');
    expect(values.parseEffort).toBeUndefined();
    expect(values.translateEffort).toBe('high');
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

describe('modelAfterProviderChange', () => {
  it('没填或是推荐模型时换成新服务商的推荐模型', () => {
    expect(
      modelAfterProviderChange('claude-opus-5-5', DEFAULTS, 'openai', 'parse'),
    ).toBe('gpt-6.1-sol');
    expect(modelAfterProviderChange('', DEFAULTS, 'openai', 'translate')).toBe(
      'gpt-6-luna',
    );
  });

  it('用户自己填的模型保持不变', () => {
    expect(
      modelAfterProviderChange('my-model', DEFAULTS, 'openai', 'parse'),
    ).toBe('my-model');
  });
});
