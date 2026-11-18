import {
  cardState,
  connFormValues,
  defaultModel,
  describeFailovers,
  isDirty,
  modelOptions,
  moveCard,
  orderChanged,
  summarize,
  toCards,
  toPayload,
  toSavePayload,
  usable,
} from './aiConfig';
import type { AiConfig, AiConnection } from './service';

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

const conn = (overrides: Partial<AiConnection> = {}): AiConnection => ({
  mode: 'api',
  provider: 'claude',
  order: 1,
  enabled: true,
  state: 'ready',
  pausedReason: null,
  pausedUntil: null,
  saved: true,
  hasKey: true,
  apiKeyMasked: 'sk-ant-••••abcd',
  parseModel: '',
  translateModel: '',
  parseEffort: '',
  translateEffort: '',
  maxWebSearches: 20,
  settingsReadonly: false,
  ...overrides,
});

const config = (connections: AiConnection[], platform = false): AiConfig => ({
  platform,
  subscriptionAllowed: connections.some((c) => c.mode === 'subscription'),
  connections,
  defaultModels: DEFAULTS,
});

const FOUR = [
  conn(),
  conn({ provider: 'openai', order: 2, parseModel: 'gpt-6-terra' }),
  conn({ mode: 'subscription', state: 'logged_in', hasKey: false, order: 3 }),
  conn({
    mode: 'subscription',
    provider: 'openai',
    state: 'logged_in',
    hasKey: false,
    order: 4,
  }),
];

describe('connFormValues', () => {
  it('Key 不回填，模型没填时预填推荐模型，思考强度空值显示为未选', () => {
    const values = connFormValues(conn({ translateEffort: 'high' }), DEFAULTS);
    expect(values.apiKey).toBe('');
    expect(values.parseModel).toBe('claude-opus-5-5');
    expect(values.parseEffort).toBeUndefined();
    expect(values.translateEffort).toBe('high');
  });

  it('按连接方式取各自的设置', () => {
    expect(connFormValues(FOUR[1], DEFAULTS)).toMatchObject({
      mode: 'api',
      provider: 'openai',
      parseModel: 'gpt-6-terra',
      translateModel: 'gpt-6-luna',
    });
  });
});

describe('moveCard', () => {
  const cards = toCards(config(FOUR));

  it('拖动交换顺序', () => {
    const next = moveCard(cards, 'api:openai', 'api:claude');
    expect(next.map((c) => `${c.mode}:${c.provider}`)).toEqual([
      'api:openai',
      'api:claude',
      'subscription:claude',
      'subscription:openai',
    ]);
    expect(orderChanged(next, config(FOUR))).toBe(true);
  });

  it('平台订阅可以拖到 API Key 前面', () => {
    const next = moveCard(cards, 'subscription:claude', 'api:claude');
    expect(next.map((c) => `${c.mode}:${c.provider}`)).toEqual([
      'subscription:claude',
      'api:claude',
      'api:openai',
      'subscription:openai',
    ]);
    expect(orderChanged(next, config(FOUR))).toBe(true);
  });

  it('放回原位不算修改', () => {
    expect(moveCard(cards, 'api:claude', 'api:claude')).toBe(cards);
    expect(orderChanged(cards, config(FOUR))).toBe(false);
  });
});

describe('toSavePayload', () => {
  it('提交全部顺序与开关，只带改过设置的连接方式，租户的平台订阅不提交', () => {
    const c = config([
      FOUR[0],
      FOUR[1],
      conn({
        mode: 'subscription',
        state: 'available',
        hasKey: false,
        settingsReadonly: true,
      }),
    ]);
    const cards = toCards(c).map((x) =>
      x.provider === 'openai' ? { ...x, enabled: false } : x,
    );
    const drafts = Object.fromEntries(
      c.connections.map((x) => [
        `${x.mode}:${x.provider}`,
        connFormValues(x, DEFAULTS),
      ]),
    );
    drafts['api:claude'] = { ...drafts['api:claude'], apiKey: ' sk-new ' };
    drafts['subscription:claude'] = {
      ...drafts['subscription:claude'],
      maxWebSearches: 3,
    };
    const payload = toSavePayload(cards, drafts, c);
    expect(payload.order).toEqual([
      { mode: 'api', provider: 'claude', enabled: true },
      { mode: 'api', provider: 'openai', enabled: false },
      { mode: 'subscription', provider: 'claude', enabled: true },
    ]);
    expect(payload.profiles).toHaveLength(1);
    expect(payload.profiles[0]).toMatchObject({
      mode: 'api',
      provider: 'claude',
      apiKey: 'sk-new',
    });
  });
});

describe('usable 与 cardState', () => {
  it('关闭、暂停、未配置、未登录都不可用；平台查不到登录状态时照常使用订阅', () => {
    expect(usable(conn(), false)).toBe(true);
    expect(usable(conn({ enabled: false }), false)).toBe(false);
    expect(usable(conn({ pausedReason: 'quota' }), false)).toBe(false);
    expect(usable(conn({ state: 'no_key' }), false)).toBe(false);
    const sub = (state: AiConnection['state']) =>
      conn({ mode: 'subscription', state });
    expect(usable(sub('logged_out'), true)).toBe(false);
    expect(usable(sub('unknown'), true)).toBe(true);
    expect(usable(sub('unknown'), false)).toBe(false);
    expect(usable({ ...sub('unknown'), saved: false }, true)).toBe(false);
  });

  it('状态标签：关闭优先，其次暂停', () => {
    expect(cardState(conn({ pausedReason: 'auth' }), false)).toEqual([
      '已关闭',
      'gray',
    ]);
    expect(cardState(conn({ pausedReason: 'auth' }), true)).toEqual([
      '暂停中',
      'orange',
    ]);
    expect(cardState(conn({ state: 'no_key' }), true)).toEqual([
      '未配置',
      'gray',
    ]);
    expect(
      cardState(conn({ mode: 'subscription', state: 'unavailable' }), true),
    ).toEqual(['不可用', 'red']);
  });
});

describe('summarize', () => {
  it('全部可用', () => {
    expect(summarize(config(FOUR, true), false)).toMatchObject({
      tone: 'ok',
      title: 'AI 可用',
      desc: '新任务按下面的顺序使用，出问题时自动换下一个',
    });
  });

  it('有暂停：说明原因、先用哪个、何时恢复', () => {
    const list = [
      conn({ pausedReason: 'quota', pausedUntil: '2026-11-17T14:32:00' }),
      FOUR[1],
    ];
    expect(summarize(config(list), false)).toEqual({
      tone: 'warn',
      title: 'AI 可用 · 1 个连接方式暂停中',
      desc: 'Claude API Key 额度用完，新任务先用 OpenAI API Key，14:32 后恢复按原顺序',
    });
  });

  it('顺序调整未保存、有关闭的方式时分别提示', () => {
    expect(summarize(config(FOUR), true).desc).toBe(
      '顺序调整后还没保存，新任务仍按原顺序使用',
    );
    expect(
      summarize(config([FOUR[0], { ...FOUR[1], enabled: false }]), false).desc,
    ).toBe('只使用 Claude API Key；OpenAI API Key 已关闭，不参与');
  });

  it('一个都不能用时区分未配置与暂不可用', () => {
    expect(
      summarize(config([conn({ state: 'no_key', hasKey: false })]), false),
    ).toMatchObject({ tone: 'idle', title: '还没有配置 AI' });
    expect(
      summarize(
        config([
          conn({ pausedReason: 'auth' }),
          conn({ mode: 'subscription', state: 'unavailable', hasKey: false }),
        ]),
        false,
      ),
    ).toMatchObject({ tone: 'bad', title: 'AI 暂不可用' });
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

describe('describeFailovers', () => {
  it('列出失败的方式与原因，以及最后改用的方式', () => {
    expect(
      describeFailovers('api:openai', [
        { connection: 'api:claude', reason: 'quota', at: 1 },
      ]),
    ).toBe('Claude API Key 额度用完，改用 OpenAI API Key 完成');
    expect(
      describeFailovers('subscription:openai', [
        { connection: 'api:claude', reason: 'auth' },
      ]),
    ).toBe('Claude API Key 认证失败，改用 OpenAI 平台订阅完成');
    expect(
      describeFailovers(null, [
        { connection: 'api:claude', reason: 'auth' },
        { connection: 'subscription:openai', reason: 'unknown_code' },
      ]),
    ).toBe('Claude API Key 认证失败、OpenAI 平台订阅不可用');
  });
});
