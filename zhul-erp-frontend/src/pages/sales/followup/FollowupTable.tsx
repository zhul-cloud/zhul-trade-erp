import { DownOutlined, RightOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Checkbox, Progress, Select, Space, Table } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import { Stars } from '@/pages/customer/profile/ui';
import { RECEIPT_META } from '@/pages/sales/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import type { Picked } from './Batch';
import {
  CustomerLink,
  DueText,
  fmtDate,
  Pill,
  SnoozeButton,
  StageTag,
  useBlockers,
  ValueCell,
} from './components';
import { PartsPanel, ProgressCell } from './OrderParts';
import QuotationPreviewDrawer from './QuotationPreviewDrawer';
import type { RecordTarget } from './RecordFollowupModal';
import {
  BOARD_ACCOUNT,
  BOARD_AFTER_SALES,
  BOARD_DEVELOP,
  BOARD_FULFIL,
  BOARD_SALES,
  type CurrencyAmount,
  type CustomerGroup,
  type FollowupItem,
  followupApi,
  GROUP,
  GROUPED_BOARDS,
  NEXT_AFTER_SALES,
  NEXT_USER,
  type OverviewBoard,
  REPORT_PRIORITY,
  readBizError,
  TARGET,
} from './service';

/** 当前看哪一段：全部 / 某个分组 / 某个阶段 / 培育中 / 有异常（订单履约） / 沉睡（客户经营） */
export interface StageFilter {
  group?: number;
  stage?: string;
  nurture?: boolean;
  exception?: boolean;
  dormant?: boolean;
}

/**
 * 阶段导航，带数量，阶段之间用「›」连接：商机成交在每组阶段上方标分组名，可点分组或单阶段，末尾培育中（K2）；
 * 订单履约、售后评价为全部和逐个阶段，订单履约末尾有「有异常」（K10、K11）；
 * 客户经营为全部、G4–G7，末尾沉睡、培育中（K14）
 */
export const StageBar: React.FC<{
  boardNo: number;
  board?: OverviewBoard;
  value: StageFilter;
  onChange: (v: StageFilter) => void;
  blocker?: string;
  onBlocker: (v?: string) => void;
  /** 右侧的额外操作（主动开发：国家筛选、新建开发对象） */
  extra?: React.ReactNode;
}> = ({ boardNo, board, value, onChange, blocker, onBlocker, extra }) => {
  const { palette } = useAppTheme();
  const blockers = useBlockers(boardNo);
  // 商机成交、主动开发：阶段上方标分组名，可点分组
  const sales = boardNo === BOARD_SALES || boardNo === BOARD_DEVELOP;
  const chip = (
    key: string,
    content: React.ReactNode,
    f: StageFilter,
    active: boolean,
  ) => (
    <button
      type="button"
      key={key}
      onClick={() => onChange(f)}
      style={{
        all: 'unset',
        cursor: 'pointer',
        padding: '6px 12px',
        borderRadius: 10,
        fontSize: 13,
        whiteSpace: 'nowrap',
        fontWeight: active ? 600 : 400,
        color: active ? '#fff' : palette.ink,
        background: active ? palette.link : palette.card,
        border: `1px solid ${active ? palette.link : palette.hairline}`,
        transition: 'background .15s',
      }}
    >
      {content}
    </button>
  );
  const count = (n: number, active: boolean) => (
    <span
      style={{
        marginLeft: 6,
        fontWeight: 600,
        color: active ? '#fff' : palette.link,
      }}
    >
      {n}
    </span>
  );
  const gt = (key: string) => (
    <RightOutlined
      key={key}
      style={{
        fontSize: 10,
        color: palette.faint,
        alignSelf: 'flex-end',
        marginBottom: 11,
      }}
    />
  );
  const account = boardNo === BOARD_ACCOUNT;
  const all =
    !value.group &&
    !value.stage &&
    !value.nurture &&
    !value.exception &&
    !value.dormant;
  // 按客户分组的板块：全部 = 客户数，与各阶段（按客户所处阶段）之和一致
  const total = !board
    ? 0
    : GROUPED_BOARDS.includes(boardNo)
      ? board.customers
      : board.total - (boardNo === BOARD_SALES ? board.nurture : 0);
  const groups = board?.groups ?? [];
  const items: React.ReactNode[] = [];
  groups.forEach((g, gi) => {
    if (gi > 0) items.push(gt(`g${g.group}`));
    const groupOn = sales && value.group === g.group && !value.stage;
    const stages: React.ReactNode[] = [];
    g.stages.forEach((st, si) => {
      if (si > 0) stages.push(gt(`s${st.code}`));
      const on = value.stage === st.code;
      stages.push(
        chip(
          st.code,
          <>
            <b>{st.code}</b> {st.name}
            {count(st.count, on)}
          </>,
          sales ? { group: g.group, stage: st.code } : { stage: st.code },
          on,
        ),
      );
    });
    items.push(
      sales ? (
        <div key={`grp${g.group}`} style={{ display: 'grid', gap: 4 }}>
          <button
            type="button"
            onClick={() => onChange({ group: g.group })}
            style={{
              all: 'unset',
              cursor: 'pointer',
              justifySelf: 'start',
              padding: '1px 8px',
              borderRadius: 6,
              fontSize: 12,
              fontWeight: groupOn || value.group === g.group ? 600 : 400,
              color: value.group === g.group ? palette.link : palette.mute,
              background:
                value.group === g.group ? palette.accentSoft : 'transparent',
            }}
          >
            {g.name} {g.count}
          </button>
          <div
            style={{
              display: 'flex',
              gap: 6,
              alignItems: 'center',
              padding: groupOn ? 3 : 0,
              margin: groupOn ? -3 : 0,
              borderRadius: 12,
              background: groupOn ? palette.accentSoft : 'transparent',
            }}
          >
            {stages}
          </div>
        </div>
      ) : (
        <React.Fragment key={`grp${g.group}`}>{stages}</React.Fragment>
      ),
    );
  });
  const divider = (
    <span
      style={{
        width: 1,
        height: 24,
        background: palette.hairline,
        alignSelf: 'flex-end',
        marginBottom: 4,
      }}
    />
  );
  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        alignItems: 'flex-end',
        flexWrap: 'wrap',
        marginBottom: 14,
      }}
    >
      {chip('all', <>全部{count(total, all)}</>, {}, all)}
      {items}
      {account && (
        <>
          {divider}
          {chip(
            'dormant',
            <span>
              沉睡
              <span
                style={{
                  marginLeft: 6,
                  fontWeight: 600,
                  color: value.dormant ? '#fff' : palette.orange,
                }}
              >
                {board?.dormant ?? 0}
              </span>
            </span>,
            { dormant: true },
            !!value.dormant,
          )}
        </>
      )}
      {(sales || account) && (
        <>
          {!account && divider}
          {chip(
            'nurture',
            <>培育中{count(board?.nurture ?? 0, !!value.nurture)}</>,
            { nurture: true },
            !!value.nurture,
          )}
        </>
      )}
      {boardNo === BOARD_FULFIL && (
        <>
          {divider}
          {chip(
            'exception',
            <span>
              有异常
              <span
                style={{
                  marginLeft: 6,
                  fontWeight: 600,
                  color: value.exception ? '#fff' : palette.orange,
                }}
              >
                {board?.exceptions ?? 0}
              </span>
            </span>,
            { exception: true },
            !!value.exception,
          )}
        </>
      )}
      <span style={{ flex: 1 }} />
      {extra}
      {boardNo === BOARD_SALES && (
        <Select
          allowClear
          style={{ width: 150 }}
          placeholder="卡点：全部"
          value={blocker}
          onChange={onBlocker}
          options={blockers.map((b) => ({
            value: b.code,
            label: `卡点：${b.name}`,
          }))}
        />
      )}
    </div>
  );
};

/** 下一步一列：我定的 / 系统提醒，建议转培育 */
const NextCell: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ display: 'grid', gap: 2 }}>
      <span style={{ color: palette.ink }}>
        {item.suggestNurture ? (
          <span style={{ color: palette.orange }}>
            {item.board === BOARD_DEVELOP
              ? '建议转培育或停止开发'
              : '建议转培育，发一封收尾邮件'}
          </span>
        ) : (
          item.nextAction || '—'
        )}
      </span>
      <span style={{ fontSize: 12 }}>
        <DueText item={item} />
        <span style={{ color: palette.mute }}>
          {' · '}
          {item.nextActionSource === NEXT_USER
            ? '我定的'
            : item.nextActionSource === NEXT_AFTER_SALES
              ? '来自售后单'
              : '系统提醒'}
        </span>
        {item.manySnoozes && (
          <span style={{ marginLeft: 6 }}>
            <Pill tone="orange">多次延后</Pill>
          </span>
        )}
      </span>
    </div>
  );
};

const LastCell: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  if (!item.lastFollowupAt)
    return <span style={{ color: palette.mute }}>还没跟进</span>;
  return (
    <div style={{ display: 'grid', gap: 2, maxWidth: 220 }}>
      <span style={{ fontSize: 12, color: palette.mute }}>
        {fmtDate(item.lastFollowupAt)}
        {item.lastChannelName && ` · ${item.lastChannelName}`}
      </span>
      {item.lastSummary && (
        <span
          style={{
            fontSize: 12,
            color: palette.sub,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
          title={item.lastSummary}
        >
          {item.lastSummary}
        </span>
      )}
    </div>
  );
};

/** 客户经营：上次联系，超过等级间隔标红 */
const ContactCell: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  if (!item.lastContactDate)
    return <span style={{ color: palette.mute }}>还没联系过</span>;
  const days = dayjs().startOf('day').diff(dayjs(item.lastContactDate), 'day');
  const color = item.contactOverdue ? palette.red : palette.ink;
  return (
    <div style={{ display: 'grid', gap: 2 }}>
      <span style={{ color }}>
        {days <= 0 ? '今天' : days === 1 ? '昨天' : `${days} 天前`}
      </span>
      {item.followDays != null && item.contactOverdue && (
        <span style={{ fontSize: 12, color }}>
          {item.bizGrade} 级每 {item.followDays} 天
        </span>
      )}
    </div>
  );
};

/** 客户经营：经营建议（触达理由）+ 所在阶段 */
const AdviceCell: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ display: 'grid', gap: 2, maxWidth: 240 }}>
      <span
        style={{
          color: item.accountNeed === 1 ? palette.green : palette.ink,
        }}
      >
        {item.accountReason || '—'}
      </span>
      <span style={{ fontSize: 12, color: palette.mute }}>
        {item.dormantSince
          ? `沉睡${item.dormantDays != null ? ` ${item.dormantDays} 天` : ''}`
          : `${item.stageCode} ${item.stageName}`}
      </span>
    </div>
  );
};

/** 主动开发「开发阶段」下的一行说明 */
const developSub = (r: FollowupItem) => {
  switch (r.stageCode) {
    case 'D3':
      return '找到联系人后进 D4';
    case 'D4':
      return '联系人已找到';
    case 'D5':
      return r.suggestNurture
        ? `序列已发完 · 第 ${r.sequenceDay} 天后`
        : `已触达 ${r.touchCount ?? 0} 次${r.sequenceDay ? ` · 下一封第 ${r.sequenceDay} 天` : ''}`;
    case 'D6':
      return '客户回复过';
    case 'D7':
      return r.signalText || '出现采购信号';
    default:
      return '';
  }
};

const BlockerCell: React.FC<{ item: FollowupItem }> = ({ item }) =>
  item.blockerName ? <Pill tone="orange">{item.blockerName}</Pill> : <>—</>;

/** 跟着售后单走的 A3、A4：下一步只读，操作为「看售后单」 */
const followsAfterSales = (r: FollowupItem) =>
  r.nextActionSource === NEXT_AFTER_SALES && !!r.afterSalesId;

/** 列表的一行：客户行（名下多张单据时）或单据行 */
type Row = FollowupItem & {
  _key: string;
  /** 客户行（名下多张单据）；只有一张单据的客户直接是单据行，也带 _group 用于勾选 */
  _head?: boolean;
  _group?: CustomerGroup;
  /** 展开后的单据行 */
  _child?: boolean;
  /** 按阶段筛选时不在范围内的单据，标灰 */
  _out?: boolean;
};

const EXPAND_KEY = 'zhul:followup:expand-all';
const readExpandAll = () => {
  try {
    return window.localStorage.getItem(EXPAND_KEY) === '1';
  } catch {
    return false;
  }
};
const writeExpandAll = (v: boolean) => {
  try {
    window.localStorage.setItem(EXPAND_KEY, v ? '1' : '0');
  } catch {
    // 浏览器禁用存储时只在本页生效
  }
};

const TYPE_LABEL: Record<number, string> = {
  [TARGET.OPPORTUNITY]: '商机',
  [TARGET.INQUIRY]: '询盘',
  [TARGET.QUOTATION]: '报价单',
  [TARGET.PI]: 'PI',
  [TARGET.ORDER]: '订单',
};
/** 「38 张询盘 · 2 张报价单」 */
const typeText = (g: CustomerGroup) =>
  Object.entries(g.typeCounts)
    .map(([t, n]) => {
      const label = TYPE_LABEL[Number(t)] ?? '单据';
      return `${n} 张${/^[A-Z]/.test(label) ? ' ' : ''}${label}`;
    })
    .join(' · ');

/** 客户行在作战卡里按客户所处阶段显示：取名下处在这个阶段的单据 */
export const stageItem = (g: CustomerGroup) =>
  g.items.find((i) => i.stageCode === g.stageCode) ?? g.lead;

/** 勾选、记跟进时带入的单据：本板块全部单据 */
export const pickOfGroup = (g: CustomerGroup): Picked => ({
  customerId: g.customerId,
  customerName: g.customerName,
  items: g.items,
});

/** 客户行的「延后」只延后到期单据 */
const dueIds = (g: CustomerGroup) =>
  g.items
    .filter(
      (i) =>
        !!i.nextActionDate &&
        !dayjs(i.nextActionDate).isAfter(dayjs(), 'day') &&
        i.nextActionSource !== NEXT_AFTER_SALES,
    )
    .map((i) => i.id);

const buildRows = (
  groups: CustomerGroup[] | undefined,
  records: FollowupItem[],
  isOpen: (customerId: number) => boolean,
): Row[] => {
  if (!groups) return records.map((r) => ({ ...r, _key: `i${r.id}` }));
  const out: Row[] = [];
  for (const g of groups) {
    const inScope = g.inScope?.length ? new Set(g.inScope) : undefined;
    if (g.items.length <= 1) {
      out.push({ ...g.lead, _key: `c${g.customerId}`, _group: g });
      continue;
    }
    out.push({ ...g.lead, _key: `c${g.customerId}`, _head: true, _group: g });
    if (isOpen(g.customerId))
      for (const i of g.items)
        out.push({
          ...i,
          _key: `i${i.id}`,
          _child: true,
          _out: !!inScope && !inScope.has(i.id),
        });
  }
  return out;
};

export const FollowupTable: React.FC<{
  boardNo: number;
  filter: StageFilter;
  records: FollowupItem[];
  total: number;
  unpaid: CurrencyAmount[];
  loading: boolean;
  /** 按客户分组的板块（商机成交、订单履约、售后评价）：客户行 + 展开的单据行 */
  groups?: CustomerGroup[];
  docTotal?: number;
  selectedKey?: string;
  canRecord: boolean;
  showOwner: boolean;
  onSelect: (item: FollowupItem, key: string) => void;
  onRecord: (item: FollowupItem) => void;
  /** 客户行「记跟进」：带入本板块全部单据 */
  onRecordTarget?: (t: RecordTarget) => void;
  picked?: Record<number, Picked>;
  onPick?: (p: Picked, on: boolean) => void;
  /** 已选客户时的批量操作条 */
  batchBar?: React.ReactNode;
  onNurture: (item: FollowupItem) => void;
  onChanged: () => void;
  onMore: () => void;
  /** 客户经营「建询盘」 */
  onNewInquiry?: (customerId: number) => void;
  /** 主动开发「转为商机」 */
  onConvert?: (item: FollowupItem) => void;
}> = ({
  boardNo,
  filter,
  records,
  total,
  unpaid,
  loading,
  groups,
  docTotal,
  selectedKey,
  canRecord,
  showOwner,
  onSelect,
  onRecord,
  onRecordTarget,
  picked,
  onPick,
  batchBar,
  onNurture,
  onChanged,
  onMore,
  onNewInquiry,
  onConvert,
}) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const sales = boardNo === BOARD_SALES;
  const develop = boardNo === BOARD_DEVELOP;
  const account = boardNo === BOARD_ACCOUNT;
  const afterQuote =
    sales && !filter.nurture && filter.group === GROUP.AFTER_QUOTE;
  const afterPi = sales && !filter.nurture && filter.group === GROUP.AFTER_PI;
  const [expanded, setExpanded] = useState<string[]>([]);
  /** 全部展开（记住上次的选择）；flipped 为与之相反的客户 */
  const [expandAll, setExpandAll] = useState(readExpandAll);
  const [flipped, setFlipped] = useState<number[]>([]);
  const [quotePreview, setQuotePreview] = useState<{
    id: number;
    versionNo?: number;
  }>();
  // 换板块或换筛选时收起展开的行
  useEffect(() => {
    setExpanded([]);
    setFlipped([]);
  }, [boardNo, filter]);
  const toggle = (key: string) =>
    setExpanded((l) =>
      l.includes(key) ? l.filter((x) => x !== key) : [...l, key],
    );
  const isOpen = (customerId: number) =>
    expandAll !== flipped.includes(customerId);
  const flip = (customerId: number) =>
    setFlipped((l) =>
      l.includes(customerId)
        ? l.filter((x) => x !== customerId)
        : [...l, customerId],
    );
  const rows = useMemo(
    () => buildRows(groups, records, (c) => expandAll !== flipped.includes(c)),
    [groups, records, expandAll, flipped],
  );
  const multi = (groups ?? []).some((g) => g.items.length > 1);

  const activate = async (item: FollowupItem) => {
    try {
      await followupApi.activate(item.id);
      message.success('已重新激活，回到原阶段');
      onChanged();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const customerCol: TableColumnsType<Row>[number] = {
    title: sales ? '客户 / 商机' : '客户',
    key: 'customer',
    width: 220,
    render: (_, r) => (
      <div style={{ display: 'grid', gap: 4 }}>
        <CustomerLink item={r} />
        <span style={{ fontSize: 12, color: palette.mute }}>
          {[r.country, showOwner ? r.ownerName : undefined]
            .filter(Boolean)
            .join(' · ') || '—'}
        </span>
        {sales && !afterQuote && !afterPi && (
          <span>
            <StageTag item={r} />
          </span>
        )}
      </div>
    ),
  };
  const valueCol: TableColumnsType<Row>[number] = {
    title: '价值',
    key: 'value',
    width: 150,
    render: (_, r) => <ValueCell item={r} />,
  };
  const docCol: TableColumnsType<Row>[number] = {
    title: !sales
      ? '订单 · 金额'
      : afterPi
        ? 'PI · 金额'
        : afterQuote
          ? '报价单 · 金额'
          : '单据 · 金额',
    key: 'doc',
    width: 190,
    render: (_, r) => (
      <div style={{ display: 'grid', gap: 2 }}>
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
          {r.docNo && r.targetType === TARGET.QUOTATION ? (
            // 点报价单号打开预览，不切换选中行、不展开行
            <a
              onClick={(e) => {
                e.stopPropagation();
                setQuotePreview({ id: r.targetId, versionNo: r.versionNo });
              }}
            >
              {r.docNo}
            </a>
          ) : (
            r.docNo || '—'
          )}
          {afterQuote && r.versionNo && r.versionNo > 1 && (
            <span style={{ color: palette.mute, fontSize: 12 }}>
              {' '}
              第 {r.versionNo} 版
            </span>
          )}
        </span>
        <span style={{ fontSize: 12, color: palette.sub }}>
          {r.amount != null ? formatAmount(r.amount, r.currencyCode) : '—'}
        </span>
      </div>
    ),
  };
  const actionCol: TableColumnsType<Row>[number] = {
    title: '操作',
    key: 'action',
    width: 190,
    fixed: 'right',
    render: (_, r) =>
      filter.nurture ? (
        <Button
          size="small"
          disabled={!canRecord}
          onClick={(e) => {
            e.stopPropagation();
            activate(r);
          }}
        >
          重新激活
        </Button>
      ) : (
        // 阻止冒泡：点操作按钮时不切换选中行
        <span onClick={(e) => e.stopPropagation()}>
          {followsAfterSales(r) ? (
            <Button
              size="small"
              onClick={() =>
                history.push(`/sales/after-sales/${r.afterSalesId}`)
              }
            >
              看售后单
            </Button>
          ) : (
            <Space size={6}>
              {r.suggestNurture && (sales || develop) ? (
                <Button
                  size="small"
                  disabled={!canRecord}
                  onClick={() => onNurture(r)}
                >
                  转培育
                </Button>
              ) : (
                <SnoozeButton
                  item={r}
                  disabled={!canRecord}
                  onDone={onChanged}
                />
              )}
              {develop && r.stageCode === 'D7' ? (
                <>
                  <Button
                    size="small"
                    type="primary"
                    disabled={!canRecord}
                    onClick={() => onConvert?.(r)}
                  >
                    转为商机
                  </Button>
                  <Button
                    size="small"
                    type="link"
                    disabled={!canRecord}
                    onClick={() => onRecord(r)}
                    style={{ padding: 0 }}
                  >
                    记跟进
                  </Button>
                </>
              ) : account && r.accountNeed === 1 ? (
                <>
                  <Button
                    size="small"
                    type="primary"
                    disabled={!canRecord}
                    onClick={() => onNewInquiry?.(r.customerId)}
                  >
                    建询盘
                  </Button>
                  <Button
                    size="small"
                    type="link"
                    disabled={!canRecord}
                    onClick={() => onRecord(r)}
                    style={{ padding: 0 }}
                  >
                    记跟进
                  </Button>
                </>
              ) : (
                <Button
                  size="small"
                  type="primary"
                  disabled={!canRecord}
                  onClick={() => onRecord(r)}
                >
                  记跟进
                </Button>
              )}
              {sales &&
                !r.suggestNurture &&
                r.stageGroup === GROUP.AFTER_QUOTE && (
                  <Button
                    size="small"
                    type="link"
                    disabled={!canRecord}
                    onClick={() => onNurture(r)}
                    style={{ padding: 0 }}
                  >
                    转培育
                  </Button>
                )}
            </Space>
          )}
        </span>
      ),
  };
  const nextCol: TableColumnsType<Row>[number] = {
    title: '下一步',
    key: 'next',
    width: 260,
    render: (_, r) => <NextCell item={r} />,
  };
  const muted = { fontSize: 12, color: palette.mute };

  let columns: TableColumnsType<Row>;
  if (boardNo === BOARD_FULFIL) {
    columns = [
      customerCol,
      valueCol,
      docCol,
      {
        title: '履约进度',
        key: 'progress',
        width: 170,
        render: (_, r) => (
          <ProgressCell
            item={r}
            expanded={expanded.includes(r._key)}
            onToggle={() => toggle(r._key)}
          />
        ),
      },
      {
        title: '预计送达',
        key: 'eta',
        width: 140,
        render: (_, r) =>
          r.etaDate ? (
            <div style={{ display: 'grid', gap: 2 }}>
              <span>{fmtDate(r.etaDate)}</span>
              {r.etaNote && <span style={muted}>{r.etaNote}</span>}
            </div>
          ) : r.stageCode === 'F6' ? (
            '—'
          ) : (
            <span style={{ color: palette.sub }}>待定</span>
          ),
      },
      {
        title: '现在的情况（系统带出）',
        key: 'situation',
        width: 240,
        render: (_, r) => (
          <div style={{ display: 'grid', gap: 2 }}>
            <span style={{ color: palette.ink }}>{r.situationText || '—'}</span>
            {r.exceptionText && (
              <span style={{ fontSize: 12, color: palette.orange }}>
                {r.exceptionText}
                {r.exceptionSince && ` · ${fmtDate(r.exceptionSince)} 起`}
              </span>
            )}
          </div>
        ),
      },
      nextCol,
      actionCol,
    ];
  } else if (boardNo === BOARD_AFTER_SALES) {
    columns = [
      customerCol,
      valueCol,
      docCol,
      {
        title: '售后阶段',
        key: 'stage',
        width: 220,
        render: (_, r) => (
          <div style={{ display: 'grid', gap: 4 }}>
            <span>
              <Pill tone="violet">
                {r.stageCode} {r.stageName}
              </Pill>
            </span>
            {r.afterSalesNo && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  history.push(`/sales/after-sales/${r.afterSalesId}`);
                }}
                style={{
                  all: 'unset',
                  cursor: 'pointer',
                  display: 'grid',
                  fontSize: 12,
                  color: palette.link,
                }}
              >
                <span style={{ whiteSpace: 'nowrap' }}>
                  售后单 {r.afterSalesNo}
                </span>
                {r.afterSalesStatusName && (
                  <span style={{ color: palette.sub }}>
                    {r.afterSalesStatusName}
                  </span>
                )}
              </button>
            )}
          </div>
        ),
      },
      {
        title: '签收',
        key: 'signed',
        width: 120,
        render: (_, r) =>
          r.signedDate ? (
            <span>{r.signedDays ?? 0} 天前</span>
          ) : (
            <div style={{ display: 'grid', gap: 2 }}>
              <span>预计 {fmtDate(r.etaDate)}</span>
              <span style={muted}>还没确认</span>
            </div>
          ),
      },
      {
        title: '客户反馈',
        key: 'feedback',
        width: 220,
        render: (_, r) =>
          r.lastSummary ? (
            <div style={{ display: 'grid', gap: 2, maxWidth: 220 }}>
              <span
                style={{
                  color: palette.ink,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={r.lastSummary}
              >
                {r.lastSummary}
              </span>
              <span style={muted}>
                {[
                  fmtDate(r.lastFollowupAt),
                  r.lastChannelName,
                  r.satisfactionName,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </span>
            </div>
          ) : (
            <span style={{ color: palette.mute }}>—</span>
          ),
      },
      nextCol,
      actionCol,
    ];
  } else if (filter.nurture) {
    columns = [
      customerCol,
      valueCol,
      ...(account || develop ? [] : [docCol]),
      {
        title: '培育原因',
        key: 'reason',
        width: 140,
        render: (_, r) => r.nurtureReasonName || '—',
      },
      {
        title: '哪天激活',
        key: 'until',
        width: 120,
        render: (_, r) => fmtDate(r.nurtureUntil),
      },
      {
        title: '上次跟进',
        key: 'last',
        render: (_, r) => <LastCell item={r} />,
      },
      actionCol,
    ];
  } else if (develop) {
    columns = [
      {
        title: '公司 / 联系人',
        key: 'customer',
        width: 220,
        render: (_, r) => (
          <div style={{ display: 'grid', gap: 4 }}>
            <CustomerLink item={r} />
            <span style={muted}>
              {[
                r.country,
                r.contactName
                  ? `${r.contactName}${r.contactTitle ? `（${r.contactTitle}）` : ''}`
                  : r.contactEmail
                    ? `只有 ${r.contactEmail}`
                    : '还没有联系人',
                showOwner ? r.ownerName : undefined,
              ]
                .filter(Boolean)
                .join(' · ')}
            </span>
          </div>
        ),
      },
      {
        title: '价值',
        key: 'value',
        width: 160,
        render: (_, r) => {
          const p = r.reportPriority
            ? REPORT_PRIORITY[r.reportPriority]
            : undefined;
          return (
            <span
              style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}
            >
              <Pill tone={p?.tone ?? 'mute'}>{p?.label ?? '未出结论'}</Pill>
              <Stars value={r.potentialRating} />
            </span>
          );
        },
      },
      {
        title: '开发阶段',
        key: 'stage',
        width: 150,
        render: (_, r) => (
          <div style={{ display: 'grid', gap: 2 }}>
            <span>
              <Pill tone="violet">{r.stageCode}</Pill>{' '}
              <b style={{ fontWeight: 600 }}>{r.stageName}</b>
            </span>
            <span style={muted}>{developSub(r)}</span>
          </div>
        ),
      },
      {
        title: '为什么值得开发',
        key: 'reason',
        width: 220,
        render: (_, r) => (
          <div style={{ display: 'grid', gap: 2, maxWidth: 220 }}>
            <span style={{ color: palette.ink }}>
              {r.prospectReason || '—'}
            </span>
            {r.prospectSourceName && (
              <span style={muted}>{r.prospectSourceName}</span>
            )}
          </div>
        ),
      },
      {
        title: '触达',
        key: 'touch',
        width: 70,
        render: (_, r) => `${r.touchCount ?? 0} 次`,
      },
      {
        title: '上次触达',
        key: 'last',
        width: 200,
        render: (_, r) => <LastCell item={r} />,
      },
      nextCol,
      actionCol,
    ];
  } else if (account) {
    columns = [
      customerCol,
      valueCol,
      {
        title: '近 12 个月成交',
        key: 'recent',
        width: 160,
        render: (_, r) => (
          <div style={{ display: 'grid', gap: 2 }}>
            <span style={{ fontVariantNumeric: 'tabular-nums' }}>
              {formatAmount(r.amountCny ?? 0, 'CNY')}
            </span>
            <span style={muted}>
              {r.recentOrderCount ?? 0} 单
              {r.lastOrderDate && ` · 最近 ${fmtDate(r.lastOrderDate)}`}
            </span>
          </div>
        ),
      },
      {
        title: '采购规律（系统推测）',
        key: 'pattern',
        width: 200,
        render: (_, r) => (
          <span style={{ color: palette.sub }}>{r.patternText || '—'}</span>
        ),
      },
      {
        title: '上次联系',
        key: 'contact',
        width: 110,
        render: (_, r) => <ContactCell item={r} />,
      },
      {
        title: '经营建议',
        key: 'advice',
        width: 240,
        render: (_, r) => <AdviceCell item={r} />,
      },
      nextCol,
      actionCol,
    ];
  } else if (afterQuote) {
    columns = [
      customerCol,
      valueCol,
      docCol,
      {
        title: '发出',
        key: 'sent',
        width: 90,
        render: (_, r) =>
          r.sentDays != null ? (
            <span
              style={{
                color: (r.overdueDays ?? 0) > 0 ? palette.red : palette.sub,
                fontWeight: (r.overdueDays ?? 0) > 0 ? 600 : 400,
              }}
            >
              第 {r.sentDays} 天
            </span>
          ) : (
            '—'
          ),
      },
      {
        title: '卡点',
        key: 'blocker',
        width: 100,
        render: (_, r) => <BlockerCell item={r} />,
      },
      {
        title: '上次跟进',
        key: 'last',
        width: 200,
        render: (_, r) => <LastCell item={r} />,
      },
      {
        title: '下一步',
        key: 'next',
        width: 280,
        render: (_, r) => <NextCell item={r} />,
      },
      actionCol,
    ];
  } else if (afterPi) {
    columns = [
      customerCol,
      valueCol,
      {
        title: 'PI · 金额 · 发出日期',
        key: 'doc',
        width: 200,
        render: (_, r) => (
          <div style={{ display: 'grid', gap: 2 }}>
            <span>{r.docNo || '—'}</span>
            <span style={{ fontSize: 12, color: palette.sub }}>
              {formatAmount(r.amount, r.currencyCode)} · {fmtDate(r.sentAt)}
            </span>
          </div>
        ),
      },
      {
        title: '已收 / 未收',
        key: 'received',
        width: 200,
        render: (_, r) => {
          const amount = Number(r.amount ?? 0);
          const pct =
            amount > 0
              ? Math.round((Number(r.receivedAmount ?? 0) / amount) * 100)
              : 0;
          const meta = r.receiptStatus
            ? RECEIPT_META[r.receiptStatus]
            : undefined;
          return (
            <div style={{ display: 'grid', gap: 2 }}>
              <Progress percent={pct} size="small" showInfo={false} />
              <span style={{ fontSize: 12, color: palette.sub }}>
                未收 {formatAmount(r.unpaidAmount, r.currencyCode)}
              </span>
              {meta && (
                <span>
                  <Pill tone={meta.tone}>
                    {r.receiptStatus === 2 ? '水单已上传' : meta.label}
                  </Pill>
                </span>
              )}
            </div>
          );
        },
      },
      {
        title: '预计付款',
        key: 'pay',
        width: 110,
        render: (_, r) => fmtDate(r.expectedPayDate),
      },
      {
        title: '下一步',
        key: 'next',
        width: 280,
        render: (_, r) => <NextCell item={r} />,
      },
      actionCol,
    ];
  } else {
    columns = [
      customerCol,
      valueCol,
      docCol,
      {
        title: '卡点',
        key: 'blocker',
        width: 100,
        render: (_, r) => <BlockerCell item={r} />,
      },
      {
        title: '上次跟进',
        key: 'last',
        width: 200,
        render: (_, r) => <LastCell item={r} />,
      },
      {
        title: '下一步',
        key: 'next',
        width: 280,
        render: (_, r) => <NextCell item={r} />,
      },
      actionCol,
    ];
  }

  // ---------------------------------------------------------------- 客户行
  const headCustomer = (g: CustomerGroup) => (
    <div style={{ display: 'grid', gap: 4 }}>
      <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
        <button
          type="button"
          aria-label={isOpen(g.customerId) ? '收起' : '展开'}
          onClick={(e) => {
            e.stopPropagation();
            flip(g.customerId);
          }}
          style={{
            all: 'unset',
            cursor: 'pointer',
            color: palette.mute,
            fontSize: 11,
            width: 14,
          }}
        >
          {isOpen(g.customerId) ? <DownOutlined /> : <RightOutlined />}
        </button>
        <CustomerLink item={g} />
      </span>
      <span style={{ fontSize: 12, color: palette.mute }}>
        {[g.country, showOwner ? g.ownerName : undefined]
          .filter(Boolean)
          .join(' · ') || '—'}
      </span>
      <span>
        <StageTag
          item={{ ...g, board: boardNo, stageName: g.stageName }}
          withCode
        />
      </span>
    </div>
  );
  const headActions = (g: CustomerGroup) => {
    const due = dueIds(g);
    return filter.nurture ? null : (
      <span onClick={(e) => e.stopPropagation()}>
        <Space size={6}>
          <SnoozeButton
            entries={[{ customerId: g.customerId, itemIds: due }]}
            disabled={!canRecord || due.length === 0}
            onDone={onChanged}
          />
          <Button
            size="small"
            type="primary"
            disabled={!canRecord}
            onClick={() =>
              onRecordTarget?.({
                customerId: g.customerId,
                customerName: g.customerName,
                item: g.lead,
                items: g.items,
              })
            }
          >
            记跟进
          </Button>
        </Space>
      </span>
    );
  };
  /** 客户行沿用最紧急单据的列 */
  const LEAD_COLS = ['next', 'last', 'feedback', 'blocker'];
  const cols: TableColumnsType<Row> = columns.map((c) => {
    const render = c.render;
    const key = String(c.key);
    return {
      ...c,
      render: (v: unknown, r: Row, i: number) => {
        const g = r._group;
        if (r._head && g) {
          if (key === 'customer') return headCustomer(g);
          if (key === 'value') return <ValueCell item={g} />;
          if (key === 'doc')
            return (
              <span style={{ fontSize: 12, color: palette.sub }}>
                {typeText(g)}
              </span>
            );
          if (key === 'action') return headActions(g);
          if (LEAD_COLS.includes(key) && render) return render(v, r, i);
          return null;
        }
        if (r._child) {
          if (key === 'customer')
            return (
              <span style={{ paddingLeft: 20 }}>
                <StageTag item={r} withCode />
              </span>
            );
          if (key === 'value') return null;
        }
        return render ? render(v, r, i) : null;
      },
    } as TableColumnsType<Row>[number];
  });
  if (groups && onPick && !filter.nurture)
    cols.unshift({
      key: 'pick',
      width: 40,
      render: (_, r) =>
        r._group ? (
          <span onClick={(e) => e.stopPropagation()}>
            <Checkbox
              checked={!!picked?.[r._group.customerId]}
              disabled={!canRecord}
              onChange={(e) =>
                r._group && onPick(pickOfGroup(r._group), e.target.checked)
              }
            />
          </span>
        ) : null,
    });

  return (
    <>
      {batchBar}
      {groups && multi && (
        <div style={{ textAlign: 'right', marginBottom: 4 }}>
          <Button
            type="link"
            size="small"
            onClick={() => {
              writeExpandAll(!expandAll);
              setExpandAll(!expandAll);
              setFlipped([]);
            }}
          >
            {expandAll ? '全部收起' : '全部展开'}
          </Button>
        </div>
      )}
      <Table<Row>
        rowKey="_key"
        size="middle"
        columns={cols}
        dataSource={rows}
        loading={loading && rows.length === 0}
        pagination={false}
        scroll={{ x: 'max-content' }}
        expandable={
          boardNo === BOARD_FULFIL
            ? {
                expandedRowKeys: expanded,
                showExpandColumn: false,
                rowExpandable: (r) => !r._head && (r.partCount ?? 0) > 1,
                expandedRowRender: (r) => <PartsPanel item={r} />,
              }
            : undefined
        }
        onRow={(r) => ({
          onClick: () =>
            onSelect(r._head && r._group ? stageItem(r._group) : r, r._key),
          style: {
            cursor: 'pointer',
            opacity: r._out ? 0.45 : undefined,
            background:
              r._key === selectedKey
                ? palette.accentSoft
                : r._child
                  ? palette.inset
                  : undefined,
          },
        })}
        locale={{
          emptyText: filter.nurture
            ? '没有培育中的客户'
            : filter.exception
              ? '没有履约异常的订单'
              : filter.dormant
                ? '没有沉睡的客户'
                : develop
                  ? '这一段没有开发对象，点右上角「新建开发对象」'
                  : '这一段没有要跟进的客户',
        }}
      />
      {(groups ? groups.length : records.length) > 0 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 12,
            padding: '12px 0 4px',
            color: palette.mute,
            fontSize: 12,
            flexWrap: 'wrap',
          }}
        >
          <span>
            {groups
              ? `共 ${total} 个客户 · ${docTotal ?? 0} 张单据 · 已显示 ${groups.length} 个客户`
              : `共 ${total} 个 · 已显示 ${records.length}`}
          </span>
          {afterPi && unpaid.length > 0 && (
            <span style={{ color: palette.ink, fontWeight: 600 }}>
              未收合计{' '}
              {unpaid
                .map((u) => formatAmount(u.amount, u.currencyCode))
                .join('、')}
            </span>
          )}
          {(groups ? groups.length : records.length) < total && (
            <Button size="small" loading={loading} onClick={onMore}>
              加载更多
            </Button>
          )}
        </div>
      )}
      <QuotationPreviewDrawer
        target={quotePreview}
        onClose={() => setQuotePreview(undefined)}
      />
    </>
  );
};
