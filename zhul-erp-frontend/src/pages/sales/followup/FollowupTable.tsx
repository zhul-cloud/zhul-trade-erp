import { RightOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Progress, Select, Space, Table } from 'antd';
import React, { useEffect, useState } from 'react';
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
import { PartsPanel, ProgressCell } from './OrderParts';
import {
  BOARD_AFTER_SALES,
  BOARD_FULFIL,
  BOARD_SALES,
  type CurrencyAmount,
  type FollowupItem,
  followupApi,
  GROUP,
  NEXT_AFTER_SALES,
  NEXT_USER,
  type OverviewBoard,
  readBizError,
} from './service';

/** 当前看哪一段：全部 / 某个分组 / 某个阶段 / 培育中 / 有异常（订单履约） */
export interface StageFilter {
  group?: number;
  stage?: string;
  nurture?: boolean;
  exception?: boolean;
}

/**
 * 阶段导航，带数量，阶段之间用「›」连接：商机成交在每组阶段上方标分组名，可点分组或单阶段，末尾培育中（K2）；
 * 订单履约、售后评价为全部和逐个阶段，订单履约末尾有「有异常」（K10、K11）
 */
export const StageBar: React.FC<{
  boardNo: number;
  board?: OverviewBoard;
  value: StageFilter;
  onChange: (v: StageFilter) => void;
  blocker?: string;
  onBlocker: (v?: string) => void;
}> = ({ boardNo, board, value, onChange, blocker, onBlocker }) => {
  const { palette } = useAppTheme();
  const blockers = useBlockers(boardNo);
  const sales = boardNo === BOARD_SALES;
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
  const all =
    !value.group && !value.stage && !value.nurture && !value.exception;
  const total = board ? board.total - (sales ? board.nurture : 0) : 0;
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
      {sales && (
        <>
          {divider}
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
      {sales && (
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

const BlockerCell: React.FC<{ item: FollowupItem }> = ({ item }) =>
  item.blockerName ? <Pill tone="orange">{item.blockerName}</Pill> : <>—</>;

/** 跟着售后单走的 A3、A4：下一步只读，操作为「看售后单」 */
const followsAfterSales = (r: FollowupItem) =>
  r.nextActionSource === NEXT_AFTER_SALES && !!r.afterSalesId;

export const FollowupTable: React.FC<{
  boardNo: number;
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
  boardNo,
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
  const sales = boardNo === BOARD_SALES;
  const afterQuote =
    sales && !filter.nurture && filter.group === GROUP.AFTER_QUOTE;
  const afterPi = sales && !filter.nurture && filter.group === GROUP.AFTER_PI;
  const [expanded, setExpanded] = useState<number[]>([]);
  // 换板块或换筛选时收起展开的行
  useEffect(() => setExpanded([]), [boardNo, filter]);
  const toggle = (id: number) =>
    setExpanded((l) =>
      l.includes(id) ? l.filter((x) => x !== id) : [...l, id],
    );

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
  const valueCol: TableColumnsType<FollowupItem>[number] = {
    title: '价值',
    key: 'value',
    width: 150,
    render: (_, r) => <ValueCell item={r} />,
  };
  const docCol: TableColumnsType<FollowupItem>[number] = {
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
              {r.suggestNurture && sales ? (
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
              <Button
                size="small"
                type="primary"
                disabled={!canRecord}
                onClick={() => onRecord(r)}
              >
                记跟进
              </Button>
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
  const nextCol: TableColumnsType<FollowupItem>[number] = {
    title: '下一步',
    key: 'next',
    width: 260,
    render: (_, r) => <NextCell item={r} />,
  };
  const muted = { fontSize: 12, color: palette.mute };

  let columns: TableColumnsType<FollowupItem>;
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
            expanded={expanded.includes(r.id)}
            onToggle={() => toggle(r.id)}
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
        expandable={
          boardNo === BOARD_FULFIL
            ? {
                expandedRowKeys: expanded,
                showExpandColumn: false,
                rowExpandable: (r) => (r.partCount ?? 0) > 1,
                expandedRowRender: (r) => <PartsPanel item={r} />,
              }
            : undefined
        }
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
            : filter.exception
              ? '没有履约异常的订单'
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
