import type { FollowupItem } from './service';
import {
  applyAiAnswers,
  emptyAnswers,
  needsAfterSales,
  stageEffect,
  stageKind,
  stagePayload,
  validateStage,
} from './stageForm';

const TODAY = '2026-11-04';
const item = (board: number, stageCode: string) =>
  ({ id: 1, board, stageCode }) as FollowupItem;

describe('stageKind', () => {
  it('按板块与阶段区分', () => {
    expect(stageKind(item(3, 'F3'))).toBe('fulfil');
    expect(stageKind(item(4, 'A1'))).toBe('receipt');
    expect(stageKind(item(4, 'A2'))).toBe('use');
    expect(stageKind(item(4, 'A3'))).toBe('general');
    expect(stageKind(item(4, 'A5'))).toBe('satisfaction');
    expect(stageKind(item(4, 'A6'))).toBe('review');
    expect(stageKind(item(2, 'S4'))).toBe('general');
    expect(stageKind(undefined)).toBe('general');
  });
});

describe('validateStage', () => {
  it('履约：至少选一项沟通内容', () => {
    expect(validateStage('fulfil', emptyAnswers(), TODAY)).toBe(
      '请选择这次和客户沟通了什么',
    );
    expect(
      validateStage('fulfil', { ...emptyAnswers(), noticeMask: 8 }, TODAY),
    ).toBeUndefined();
  });

  it('签收：必选答案，有问题选类型，还没收到填不早于今天的日期', () => {
    const a = emptyAnswers();
    expect(validateStage('receipt', a, TODAY)).toBe('请选择客户收到了吗');
    expect(validateStage('receipt', { ...a, receiptAnswer: 2 }, TODAY)).toBe(
      '请选择问题类型',
    );
    expect(
      validateStage(
        'receipt',
        { ...a, receiptAnswer: 2, issueTypes: ['破损'] },
        TODAY,
      ),
    ).toBeUndefined();
    expect(validateStage('receipt', { ...a, receiptAnswer: 3 }, TODAY)).toBe(
      '还没收到时请填新的预计送达日',
    );
    expect(
      validateStage(
        'receipt',
        { ...a, receiptAnswer: 3, etaDate: '2026-11-03' },
        TODAY,
      ),
    ).toBe('新的预计送达日不能早于今天');
    expect(
      validateStage(
        'receipt',
        { ...a, receiptAnswer: 3, etaDate: TODAY },
        TODAY,
      ),
    ).toBeUndefined();
    expect(
      validateStage('receipt', { ...a, receiptAnswer: 1 }, TODAY),
    ).toBeUndefined();
  });

  it('使用：必选答案，有问题选类型', () => {
    const a = emptyAnswers();
    expect(validateStage('use', a, TODAY)).toBe('请选择用得好吗');
    expect(validateStage('use', { ...a, useAnswer: 2 }, TODAY)).toBe(
      '请选择问题类型',
    );
    expect(validateStage('use', { ...a, useAnswer: 3 }, TODAY)).toBeUndefined();
  });

  it('满意度：满意选评价状态，一般 / 不满意选原因', () => {
    const a = emptyAnswers();
    expect(validateStage('satisfaction', a, TODAY)).toBe('请选择客户满意吗');
    expect(
      validateStage('satisfaction', { ...a, satisfaction: 1 }, TODAY),
    ).toBe('请选择评价状态');
    expect(
      validateStage(
        'satisfaction',
        { ...a, satisfaction: 1, reviewStatus: 2 },
        TODAY,
      ),
    ).toBeUndefined();
    expect(
      validateStage('satisfaction', { ...a, satisfaction: 2 }, TODAY),
    ).toBe('请选择一般的原因');
    expect(
      validateStage('satisfaction', { ...a, satisfaction: 3 }, TODAY),
    ).toBe('请选择不满意的原因');
    expect(
      validateStage(
        'satisfaction',
        { ...a, satisfaction: 3, dissatisfiedReason: 'DELIVERY' },
        TODAY,
      ),
    ).toBeUndefined();
  });

  it('邀请评价：必选评价状态；通用阶段不校验', () => {
    expect(validateStage('review', emptyAnswers(), TODAY)).toBe(
      '请选择评价状态',
    );
    expect(
      validateStage('review', { ...emptyAnswers(), reviewStatus: 6 }, TODAY),
    ).toBeUndefined();
    expect(validateStage('general', emptyAnswers(), TODAY)).toBeUndefined();
  });
});

describe('stagePayload', () => {
  it('履约：只有 F6 提交预计送达', () => {
    const a = { ...emptyAnswers(), noticeMask: 3, etaDate: '2026-11-09' };
    expect(stagePayload('fulfil', a, 'F3')).toEqual({
      noticeMask: 3,
      etaDate: undefined,
    });
    expect(stagePayload('fulfil', a, 'F6').etaDate).toBe('2026-11-09');
  });

  it('签收：问题类型和日期只随对应答案提交', () => {
    const a = {
      ...emptyAnswers(),
      issueTypes: ['破损'],
      etaDate: '2026-11-09',
    };
    expect(stagePayload('receipt', { ...a, receiptAnswer: 1 })).toEqual({
      receiptAnswer: 1,
      issueTypes: undefined,
      etaDate: undefined,
    });
    expect(
      stagePayload('receipt', { ...a, receiptAnswer: 2 }).issueTypes,
    ).toEqual(['破损']);
    expect(stagePayload('receipt', { ...a, receiptAnswer: 3 }).etaDate).toBe(
      '2026-11-09',
    );
  });

  it('满意度：不满意不带评价，满意不带原因', () => {
    const a = {
      ...emptyAnswers(),
      reviewStatus: 2,
      reviewPlatform: ' 阿里国际站 ',
      dissatisfiedReason: 'PRICE',
    };
    expect(stagePayload('satisfaction', { ...a, satisfaction: 1 })).toEqual({
      satisfaction: 1,
      reviewStatus: 2,
      reviewPlatform: '阿里国际站',
      dissatisfiedReason: undefined,
    });
    expect(stagePayload('satisfaction', { ...a, satisfaction: 3 })).toEqual({
      satisfaction: 3,
      reviewStatus: undefined,
      reviewPlatform: undefined,
      dissatisfiedReason: 'PRICE',
    });
  });

  it('通用阶段不带阶段字段', () => {
    expect(
      stagePayload('general', { ...emptyAnswers(), noticeMask: 1 }),
    ).toEqual({});
  });
});

describe('needsAfterSales 与 stageEffect', () => {
  it('A1、A2 有问题时保存后打开新建售后单', () => {
    expect(
      needsAfterSales('receipt', { ...emptyAnswers(), receiptAnswer: 2 }),
    ).toBe(true);
    expect(needsAfterSales('use', { ...emptyAnswers(), useAnswer: 2 })).toBe(
      true,
    );
    expect(needsAfterSales('use', { ...emptyAnswers(), useAnswer: 1 })).toBe(
      false,
    );
    expect(
      stageEffect('receipt', { ...emptyAnswers(), receiptAnswer: 2 }),
    ).toContain('打开新建售后单');
  });

  it('F6 提示按预计送达转入售后', () => {
    expect(
      stageEffect('fulfil', emptyAnswers(), {
        ...item(3, 'F6'),
        etaDate: '2026-11-06',
      }),
    ).toBe(
      '阶段仍按单据走，不会被这条记录改动；11-06 自动转入「售后评价 · 签收确认」',
    );
  });

  it('满意度和评价的结束提示', () => {
    expect(
      stageEffect('satisfaction', {
        ...emptyAnswers(),
        satisfaction: 1,
        reviewStatus: 2,
      }),
    ).toContain('进入 A6');
    expect(
      stageEffect('satisfaction', { ...emptyAnswers(), satisfaction: 2 }),
    ).toContain('售后结束');
    expect(stageEffect('review', { ...emptyAnswers(), reviewStatus: 6 })).toBe(
      '仍在 A6，按提醒规则继续',
    );
    expect(stageEffect('review', emptyAnswers())).toBeUndefined();
  });
});

describe('applyAiAnswers', () => {
  const res = {
    nextActions: [],
    profileSuggestions: [],
    receiptAnswer: 2,
    issueTypes: ['破损'],
    satisfaction: 1,
    reviewStatus: 3,
    etaDate: '2026-11-09',
  };
  it('只预填当前阶段、业务员还没选的', () => {
    const r = applyAiAnswers('receipt', emptyAnswers(), res);
    expect(r.receiptAnswer).toBe(2);
    expect(r.issueTypes).toEqual(['破损']);
    expect(r.satisfaction).toBeUndefined();
    const kept = applyAiAnswers(
      'receipt',
      { ...emptyAnswers(), receiptAnswer: 1 },
      res,
    );
    expect(kept.receiptAnswer).toBe(1);
    expect(
      applyAiAnswers('fulfil', emptyAnswers(), res, 'F3').etaDate,
    ).toBeUndefined();
    expect(applyAiAnswers('fulfil', emptyAnswers(), res, 'F6').etaDate).toBe(
      '2026-11-09',
    );
    expect(applyAiAnswers('review', emptyAnswers(), res).reviewStatus).toBe(3);
  });
});

describe('客户经营', () => {
  const g4 = item(5, 'G4');
  const base = () => ({ ...emptyAnswers(), crossCategory: '' });

  it('板块 5 为客户经营表单', () => {
    expect(stageKind(g4)).toBe('account');
  });

  it('校验：必选客户情况，没有需求要选原因，品类不超过 50 字', () => {
    expect(validateStage('account', base(), TODAY)).toBe('请选择客户情况');
    expect(
      validateStage('account', { ...base(), accountAnswer: 3 }, TODAY),
    ).toBe('请选择没有需求的原因');
    expect(
      validateStage(
        'account',
        { ...base(), accountAnswer: 3, noNeedReason: 'NO_BUDGET' },
        TODAY,
      ),
    ).toBeUndefined();
    expect(
      validateStage(
        'account',
        { ...base(), accountAnswer: 2, crossCategory: 'x'.repeat(51) },
        TODAY,
      ),
    ).toBe('品类不能超过50个字');
    expect(
      validateStage(
        'account',
        { ...base(), accountAnswer: 2, crossCategory: 'x'.repeat(50) },
        TODAY,
      ),
    ).toBeUndefined();
  });

  it('提交：只带当前答案对应的字段', () => {
    const a = {
      ...base(),
      crossCategory: ' HMI ',
      noNeedReason: 'NO_BUDGET',
      quarterReview: true,
    };
    expect(stagePayload('account', { ...a, accountAnswer: 2 })).toEqual({
      accountAnswer: 2,
      crossCategory: 'HMI',
      noNeedReason: undefined,
      quarterReview: true,
    });
    expect(
      stagePayload('account', { ...a, accountAnswer: 3, quarterReview: false }),
    ).toEqual({
      accountAnswer: 3,
      crossCategory: undefined,
      noNeedReason: 'NO_BUDGET',
      quarterReview: undefined,
    });
  });

  it('保存后的阶段说明', () => {
    expect(stageEffect('account', { ...base(), accountAnswer: 1 }, g4)).toMatch(
      /G5.*新建询盘/,
    );
    expect(stageEffect('account', { ...base(), accountAnswer: 2 }, g4)).toBe(
      '进入 G6 交叉销售',
    );
    expect(stageEffect('account', { ...base(), accountAnswer: 3 }, g4)).toBe(
      '回到 G4 需求挖掘',
    );
    const g7 = { ...item(5, 'G7'), strategic: true } as FollowupItem;
    expect(
      stageEffect(
        'account',
        { ...base(), accountAnswer: 3, quarterReview: true },
        g7,
      ),
    ).toMatch(/^留在 G7 长期合作；回顾日期/);
    expect(stageEffect('account', base(), g4)).toBeUndefined();
  });

  it('AI 建议：有答案用答案，只有没回复时选没回复，已选的不覆盖', () => {
    expect(
      applyAiAnswers('account', base(), { accountAnswer: 2 } as never, 'G4')
        .accountAnswer,
    ).toBe(2);
    expect(
      applyAiAnswers('account', base(), { situation: 4 } as never, 'G4')
        .accountAnswer,
    ).toBe(4);
    expect(
      applyAiAnswers(
        'account',
        { ...base(), accountAnswer: 1 },
        { accountAnswer: 3 } as never,
        'G4',
      ).accountAnswer,
    ).toBe(1);
  });
});
