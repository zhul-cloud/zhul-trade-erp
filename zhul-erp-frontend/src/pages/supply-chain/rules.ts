import dayjs, { type Dayjs } from 'dayjs';
import {
  CAND,
  type Candidate,
  COVER,
  type Demand,
  type DevGroup,
  type DevSupplier,
  PATHS,
  type Signal,
  SRC,
} from './service';

/**
 * 下一步的推荐日期（与后端 DemandRules.suggestNextDate 一致）：无渠道、有线索未验证 3 天后，需确认 2 天后，
 * 可直接报价按证据到期前 5 天（多个型号取最早到期的；已不足 5 天时为明天）。
 */
export const recommendDate = (
  coverage: number | undefined,
  demands: Pick<Demand, 'evidenceExpireDate'>[],
  today: Dayjs = dayjs(),
): Dayjs => {
  const start = today.startOf('day');
  if (coverage === COVER.READY) {
    const expire = demands
      .map((d) => d.evidenceExpireDate)
      .filter((d): d is string => !!d)
      .sort()[0];
    if (expire) {
      const d = dayjs(expire).subtract(5, 'day');
      return d.isAfter(start) ? d : start.add(1, 'day');
    }
  }
  return start.add(coverage === COVER.CONFIRM ? 2 : 3, 'day');
};

/**
 * 所选型号都已是「可直接报价」且有备选渠道时，下一步可以不填（与后端一致）。
 * singleChannel 只对当前为需确认及以上的型号有意义；其余按至少两个渠道估算，最终以后端校验为准。
 */
export const nextOptional = (
  coverage: number | undefined,
  demands: Pick<Demand, 'coverageStatus' | 'channelCount' | 'singleChannel'>[],
) =>
  demands.length > 0 &&
  demands.every((d) => {
    if ((coverage || d.coverageStatus) !== COVER.READY) return false;
    return d.coverageStatus >= COVER.CONFIRM
      ? !d.singleChannel
      : (d.channelCount ?? 0) >= 2;
  });

/** 负责人筛选：me 为当前用户（不传），all 为全部（0），其他为用户 id */
export const ownerParam = (o: string) =>
  o === 'me' ? undefined : o === 'all' ? 0 : Number(o);

/**
 * 归类的分组：填了型号时全部归到这个型号；没填时，有原始型号的询盘类信号按型号分组
 * （同一型号不分大小写、去掉首尾空格算一组），其余归到品牌 + 品类。
 */
export const classifyPlan = (
  signals: Signal[],
  model?: string,
): { signalIds: number[]; model?: string }[] => {
  const m = model?.trim();
  if (m) return [{ signalIds: signals.map((s) => s.id), model: m }];
  const groups = new Map<string, { signalIds: number[]; model?: string }>();
  for (const s of signals) {
    const raw = s.sourceType <= SRC.LOST ? s.modelRaw?.trim() : undefined;
    const key = raw ? raw.toUpperCase() : '';
    const g = groups.get(key) ?? { signalIds: [], model: raw || undefined };
    g.signalIds.push(s.id);
    groups.set(key, g);
  }
  // 没有型号的一组放最后
  return [...groups.entries()]
    .sort(([a], [b]) => (a === '' ? 1 : 0) - (b === '' ? 1 : 0))
    .map(([, g]) => g);
};

/** 信号来源单据的链接；没有可打开的单据时返回 undefined */
export const signalDoc = (s: Signal) => {
  if (!s.sourceDocNo) return undefined;
  const id = s.sourceDocId;
  if (s.sourceType === SRC.INQUIRY && id)
    return `/inquiry/customer-inquiries/${id}`;
  if (s.sourceType === SRC.ORDER && id) return `/sales/orders/${id}`;
  if (s.sourceType === SRC.LOST && id) return `/quotation/quotations/${id}`;
  if (s.sourceType === SRC.STOCKOUT) return PATHS.sourcingTask(s.sourceDocNo);
  return undefined;
};

const md = (d?: string) => (d ? dayjs(d).format('MM-DD') : '');

/** 时间线上一条信号的说明 */
export const signalText = (s: Signal) => {
  if (s.sourceType === SRC.SEARCH)
    return [
      s.platform,
      `“${s.content ?? ''}”`,
      `出现 ${s.occurCount ?? 0} 次${s.periodStart ? `（${md(s.periodStart)} 至 ${md(s.periodEnd)}）` : ''}`,
    ]
      .filter(Boolean)
      .join(' · ');
  if (s.sourceType === SRC.RESEARCH)
    return [s.platform, s.content].filter(Boolean).join(' · ');
  return [
    s.content,
    s.quantity ? `数量 ${s.quantity}` : '',
    s.reasonText
      ? `${s.sourceType === SRC.LOST ? '未成交原因' : '原因'}：${s.reasonText}`
      : '',
  ]
    .filter(Boolean)
    .join(' · ');
};

// ---------------------------------------------------------------- 第二期：供应商开发

/** 合格依据的说明：「询价 11-08」「采购订单 11-04」 */
export const evidenceText = (type?: number, date?: string) =>
  `${type === 2 ? '采购订单' : '询价'} ${md(date)}`;

/** 候选阶段的颜色 */
export const candidateTone = (stage: number) =>
  stage === CAND.QUALIFIED
    ? ('green' as const)
    : stage === CAND.ABANDONED
      ? ('mute' as const)
      : stage === CAND.QUOTED
        ? ('orange' as const)
        : stage === CAND.CONTACTED
          ? ('accent' as const)
          : ('gray' as const);

/** 范围内合格、但不是本开发任务候选的供应商 */
export const otherQualified = (
  g: Pick<DevGroup, 'qualifiedSuppliers' | 'candidates'>,
): DevSupplier[] => {
  const mine = new Set(g.candidates.map((c) => c.supplierId).filter(Boolean));
  return g.qualifiedSuppliers.filter((s) => !mine.has(s.id));
};

/**
 * 候选记处理后的阶段（与后端 SupplierDevService.targetStage 一致）：不改时保持；已合格的只能改为已放弃。
 */
export const candidateTargetStage = (current: number, requested?: number) => {
  if (requested == null) return current;
  if (current === CAND.QUALIFIED && requested !== CAND.ABANDONED)
    return current;
  return requested;
};

/** 所选候选记完处理后都是合格或已放弃时，下一步可以不填 */
export const candidateNextOptional = (
  candidates: Pick<Candidate, 'stage'>[],
  requested?: number,
) =>
  candidates.length > 0 &&
  candidates.every((c) => {
    const to = candidateTargetStage(c.stage, requested);
    return to === CAND.QUALIFIED || to === CAND.ABANDONED;
  });

/** 建开发任务的默认目标家数：等于缺口，最少 1 家 */
export const defaultTarget = (gap: number) => Math.min(10, Math.max(1, gap));
