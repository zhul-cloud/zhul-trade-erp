import type { TableColumnsType } from 'antd';
import { App, Button, Progress, Select, Space, Table } from 'antd';
import React from 'react';
import { RECEIPT_META } from '@/pages/sales/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
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
import {
  type CurrencyAmount,
  type FollowupItem,
  followupApi,
  GROUP,
  NEXT_USER,
  type OverviewBoard,
  readBizError,
} from './service';

/** 当前看哪一段：全部 / 某个分组 / 某个阶段 / 培育中 */
export interface StageFilter {
  group?: number;
  stage?: string;
  nurture?: boolean;
}

const sameFilter = (a: StageFilter, b: StageFilter) =>
  a.group === b.group && a.stage === b.stage && !!a.nurture === !!b.nurture;

/** 阶段导航：全部、分组、单阶段、培育中，带数量（K2 顶部） */
export const StageBar: React.FC<{
  board?: OverviewBoard;
  value: StageFilter;
  onChange: (v: StageFilter) => void;
  blocker?: string;
  onBlocker: (v?: string) => void;
}> = ({ board, value, onChange, blocker, onBlocker }) => {
  const { palette } = useAppTheme();
  const blockers = useBlockers();
  const btn = (label: React.ReactNode, f: StageFilter, small = false) => {
    const active = sameFilter(value, f);
    return (
      <button
        type="button"
        key={`${f.group}-${f.stage}-${f.nurture}`}
        onClick={() => onChange(f)}
        style={{
          all: 'unset',
          cursor: 'pointer',
          padding: small ? '2px 10px' : '6px 14px',
          borderRadius: small ? 12 : 10,
          fontSize: small ? 12 : 13,
          fontWeight: active ? 600 : 400,
          color: active ? palette.link : palette.sub,
          background: active ? palette.accentSoft : 'transparent',
          border: `1px solid ${active ? palette.accentLine : palette.hairline}`,
        }}
      >
        {label}
      </button>
    );
  };
  const total = board ? board.total - board.nurture : 0;
  const current = board?.groups.find((g) => g.group === value.group);
  return (
    <div style={{ display: 'grid', gap: 10, marginBottom: 12 }}>
      <div
        style={{
          display: 'flex',
          gap: 8,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        {btn(`全部 ${total}`, {})}
        {board?.groups.map((g) =>
          btn(
            <>
              {g.stages[0]?.code}
              {g.stages.length > 1 && `–${g.stages[g.stages.length - 1].code}`}{' '}
              <span style={{ color: palette.mute }}>{g.name}</span> {g.count}
            </>,
            { group: g.group },
          ),
        )}
        {btn(`培育中 ${board?.nurture ?? 0}`, { nurture: true })}
        <span style={{ flex: 1 }} />
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
      </div>
      {current && current.stages.length > 1 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {btn(`全部 ${current.count}`, { group: current.group }, true)}
          {current.stages.map((s) =>
            btn(
              `${s.code} ${s.name} ${s.count}`,
              { group: current.group, stage: s.code },
              true,
            ),
          )}
        </div>
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
            建议转培育，发一封收尾邮件
          </span>
        ) : (
          item.nextAction || '—'
        )}
      </span>
      <span style={{ fontSize: 12 }}>
        <DueText item={item} />
        <span style={{ color: palette.mute }}>
          {' · '}
          {item.nextActionSource === NEXT_USER ? '我定的' : '系统提醒'}
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

const BlockerCell: React.FC<{ item: FollowupItem }> = ({ item }) =>
  item.blockerName ? <Pill tone="orange">{item.blockerName}</Pill> : <>—</>;

export const FollowupTable: React.FC<{
  filter: StageFilter;
  records: FollowupItem[];
  total: number;
  unpaid: CurrencyAmount[];
  loading: boolean;
  selectedId?: number;
  canRecord: boolean;
  showOwner: boolean;
  onSelect: (item: FollowupItem) => void;
  onRecord: (item: FollowupItem) => void;
  onNurture: (item: FollowupItem) => void;
  onChanged: () => void;
  onMore: () => void;
}> = ({
  filter,
  records,
  total,
  unpaid,
  loading,
  selectedId,
  canRecord,
  showOwner,
  onSelect,
  onRecord,
  onNurture,
  onChanged,
  onMore,
}) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const afterQuote = !filter.nurture && filter.group === GROUP.AFTER_QUOTE;
  const afterPi = !filter.nurture && filter.group === GROUP.AFTER_PI;

  const activate = async (item: FollowupItem) => {
    try {
      await followupApi.activate(item.id);
      message.success('已重新激活，回到原阶段');
      onChanged();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const customerCol: TableColumnsType<FollowupItem>[number] = {
    title: '客户 / 商机',
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
        {!afterQuote && !afterPi && (
          <span>
            <StageTag item={r} />
          </span>
        )}
      </div>
    ),
  };
  const valueCol: TableColumnsType<FollowupItem>[number] = {
    title: '价值',
    key: 'value',
    width: 150,
    render: (_, r) => <ValueCell item={r} />,
  };
  const docCol: TableColumnsType<FollowupItem>[number] = {
    title: afterPi ? 'PI · 金额' : afterQuote ? '报价单 · 金额' : '单据 · 金额',
    key: 'doc',
    width: 190,
    render: (_, r) => (
      <div style={{ display: 'grid', gap: 2 }}>
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
          {r.docNo || '—'}
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
  const actionCol: TableColumnsType<FollowupItem>[number] = {
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
          <Space size={6}>
            {r.suggestNurture ? (
              <Button
                size="small"
                disabled={!canRecord}
                onClick={() => onNurture(r)}
              >
                转培育
              </Button>
            ) : (
              <SnoozeButton item={r} disabled={!canRecord} onDone={onChanged} />
            )}
            <Button
              size="small"
              type="primary"
              disabled={!canRecord}
              onClick={() => onRecord(r)}
            >
              记跟进
            </Button>
            {!r.suggestNurture && r.stageGroup === GROUP.AFTER_QUOTE && (
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
        </span>
      ),
  };

  let columns: TableColumnsType<FollowupItem>;
  if (filter.nurture) {
    columns = [
      customerCol,
      valueCol,
      docCol,
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

  return (
    <>
      <Table<FollowupItem>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={records}
        loading={loading && records.length === 0}
        pagination={false}
        scroll={{ x: 'max-content' }}
        onRow={(r) => ({
          onClick: () => onSelect(r),
          style: {
            cursor: 'pointer',
            background: r.id === selectedId ? palette.accentSoft : undefined,
          },
        })}
        locale={{
          emptyText: filter.nurture
            ? '没有培育中的客户'
            : '这一段没有要跟进的客户',
        }}
      />
      {records.length > 0 && (
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
            共 {total} 个 · 已显示 {records.length}
          </span>
          {afterPi && unpaid.length > 0 && (
            <span style={{ color: palette.ink, fontWeight: 600 }}>
              未收合计{' '}
              {unpaid
                .map((u) => formatAmount(u.amount, u.currencyCode))
                .join('、')}
            </span>
          )}
          {records.length < total && (
            <Button size="small" loading={loading} onClick={onMore}>
              加载更多
            </Button>
          )}
        </div>
      )}
    </>
  );
};
