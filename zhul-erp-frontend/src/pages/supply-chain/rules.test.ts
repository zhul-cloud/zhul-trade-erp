import dayjs from 'dayjs';
import {
  classifyPlan,
  nextOptional,
  ownerParam,
  recommendDate,
  signalDoc,
  signalText,
} from './rules';
import type { Demand, Signal } from './service';

const sig = (p: Partial<Signal>): Signal => ({
  id: 1,
  sourceType: 1,
  occurredAt: '2026-11-01',
  ...p,
});

describe('recommendDate', () => {
  const today = dayjs('2026-11-14 15:30');
  const fmt = (c: number | undefined, d: Partial<Demand>[] = []) =>
    recommendDate(c, d, today).format('YYYY-MM-DD');

  it('无渠道、有线索未验证 3 天后，需确认 2 天后', () => {
    expect(fmt(1)).toBe('2026-11-17');
    expect(fmt(2)).toBe('2026-11-17');
    expect(fmt(3)).toBe('2026-11-16');
    expect(fmt(undefined)).toBe('2026-11-17');
  });
  it('可直接报价按最早到期的证据前 5 天', () => {
    expect(
      fmt(4, [
        { evidenceExpireDate: '2026-12-01' },
        { evidenceExpireDate: '2026-11-30' },
        {},
      ]),
    ).toBe('2026-11-25');
  });
  it('证据不足 5 天到期时推荐明天；没有证据按 3 天', () => {
    expect(fmt(4, [{ evidenceExpireDate: '2026-11-19' }])).toBe('2026-11-15');
    expect(fmt(4, [{ evidenceExpireDate: '2026-11-20' }])).toBe('2026-11-15');
    expect(fmt(4, [{ evidenceExpireDate: '2026-11-21' }])).toBe('2026-11-16');
    expect(fmt(4, [{}])).toBe('2026-11-17');
  });
});

describe('nextOptional', () => {
  const ready = { coverageStatus: 4, channelCount: 2, singleChannel: false };
  it('都已是可直接报价且有备选渠道时可以不填', () => {
    expect(nextOptional(undefined, [ready, ready])).toBe(true);
  });
  it('有一个单一渠道或不是可直接报价就要填', () => {
    expect(
      nextOptional(undefined, [ready, { ...ready, singleChannel: true }]),
    ).toBe(false);
    expect(nextOptional(undefined, [{ ...ready, coverageStatus: 3 }])).toBe(
      false,
    );
    expect(nextOptional(3, [ready])).toBe(false);
  });
  it('本次改为可直接报价时，原来未到需确认的按渠道数估算', () => {
    expect(
      nextOptional(4, [
        { coverageStatus: 2, channelCount: 2, singleChannel: false },
      ]),
    ).toBe(true);
    expect(
      nextOptional(4, [
        { coverageStatus: 2, channelCount: 1, singleChannel: false },
      ]),
    ).toBe(false);
  });
  it('没有型号时不能跳过', () => {
    expect(nextOptional(4, [])).toBe(false);
  });
});

describe('ownerParam', () => {
  it('我 → 不传，全部 → 0，其他 → 用户 id', () => {
    expect(ownerParam('me')).toBeUndefined();
    expect(ownerParam('all')).toBe(0);
    expect(ownerParam('1000000123')).toBe(1000000123);
  });
});

describe('classifyPlan', () => {
  const signals = [
    sig({ id: 1, sourceType: 1, modelRaw: '6ES7214-1AG40-0XB0' }),
    sig({ id: 2, sourceType: 3, modelRaw: ' 6es7214-1ag40-0xb0 ' }),
    sig({ id: 3, sourceType: 2, modelRaw: '6ES7315-2AG10-0AB0' }),
    sig({ id: 4, sourceType: 5, content: 'siemens plc', modelRaw: 'X1' }),
    sig({ id: 5, sourceType: 1 }),
  ];

  it('填了型号时全部归到这个型号', () => {
    expect(classifyPlan(signals, ' ABC ')).toEqual([
      { signalIds: [1, 2, 3, 4, 5], model: 'ABC' },
    ]);
  });

  it('没填型号时按原始型号分组，同型号不分大小写，其余放最后归到品牌品类', () => {
    expect(classifyPlan(signals)).toEqual([
      { signalIds: [1, 2], model: '6ES7214-1AG40-0XB0' },
      { signalIds: [3], model: '6ES7315-2AG10-0AB0' },
      { signalIds: [4, 5], model: undefined },
    ]);
  });

  it('只填空格等于没填', () => {
    expect(classifyPlan([sig({ id: 7 })], '  ')).toEqual([
      { signalIds: [7], model: undefined },
    ]);
  });

  it('没有信号时没有分组', () => {
    expect(classifyPlan([])).toEqual([]);
  });
});

describe('signalDoc', () => {
  it('按来源类型给出单据链接', () => {
    expect(
      signalDoc(sig({ sourceType: 1, sourceDocId: 9, sourceDocNo: 'IQ1' })),
    ).toBe('/inquiry/customer-inquiries/9');
    expect(
      signalDoc(sig({ sourceType: 2, sourceDocId: 9, sourceDocNo: 'SO1' })),
    ).toBe('/sales/orders/9');
    expect(
      signalDoc(sig({ sourceType: 4, sourceDocId: 9, sourceDocNo: 'QT1' })),
    ).toBe('/quotation/quotations/9');
    expect(
      signalDoc(sig({ sourceType: 3, sourceDocId: 9, sourceDocNo: 'T 1' })),
    ).toBe('/inquiry/sourcing-board?keyword=T%201');
  });
  it('没有单号或是手工录入的信号时没有链接', () => {
    expect(signalDoc(sig({ sourceType: 1, sourceDocId: 9 }))).toBeUndefined();
    expect(signalDoc(sig({ sourceType: 5, sourceDocNo: 'X' }))).toBeUndefined();
  });
});

describe('signalText', () => {
  it('搜索关键词：平台 ·“词”· 出现次数和区间', () => {
    expect(
      signalText(
        sig({
          sourceType: 5,
          platform: '阿里国际站',
          content: '6ES7 214 PLC',
          occurCount: 38,
          periodStart: '2026-11-01',
          periodEnd: '2026-11-07',
        }),
      ),
    ).toBe('阿里国际站 · “6ES7 214 PLC” · 出现 38 次（11-01 至 11-07）');
  });
  it('报价未成交带未成交原因，询价无货带原因', () => {
    expect(
      signalText(sig({ sourceType: 4, reasonText: '交期无法满足需求' })),
    ).toBe('未成交原因：交期无法满足需求');
    expect(
      signalText(sig({ sourceType: 3, quantity: 5, reasonText: '停产' })),
    ).toBe('数量 5 · 原因：停产');
  });
  it('同行调研只显示平台和内容', () => {
    expect(
      signalText(sig({ sourceType: 6, platform: '1688', content: '同行在推' })),
    ).toBe('1688 · 同行在推');
  });
});
