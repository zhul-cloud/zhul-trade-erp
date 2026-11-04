import type { TableColumnsType } from 'antd';
import { Button, Segmented, Skeleton, Table, TreeSelect } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import { useWide } from '@/pages/inquiry/shared/components';
import {
  type DeptTreeOption,
  getDeptTreeOptions,
} from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { Card, Pill } from './components';
import {
  BOARD_ACCOUNT,
  BOARD_AFTER_SALES,
  BOARD_FULFIL,
  BOARD_SALES,
  followupApi,
  readBizError,
  type Team,
  type TeamException,
  type TeamOwner,
} from './service';

/** 从团队视图回到「我的」列表时带上的筛选 */
export interface TeamJump {
  /** 空为全部负责人 */
  ownerId?: number;
  ownerName?: string;
  board?: number;
  stage?: string;
  /** 回到今日待办 */
  today?: boolean;
  keyword?: string;
}

const PERIODS = [
  { value: 'week', label: '本周' },
  { value: 'last-week', label: '上周' },
  { value: 'month', label: '本月' },
];
const BOARD_COLS = ['主动开发', '商机成交', '订单履约', '售后评价', '客户经营'];
const STAGE_ROWS = [
  { board: BOARD_SALES, name: '商机成交' },
  { board: BOARD_FULFIL, name: '订单履约' },
  { board: BOARD_AFTER_SALES, name: '售后评价' },
  { board: BOARD_ACCOUNT, name: '客户经营' },
];
const EX_TONE: Record<string, 'red' | 'orange' | 'violet'> = {
  OVERDUE: 'red',
  SNOOZED: 'orange',
  FULFIL: 'orange',
  ISSUE_LONG: 'violet',
  DISSATISFIED: 'red',
};
/** 逾期、多次延后达到这个数标红 */
const ALERT_AT = 3;

const NumButton: React.FC<{
  value: number;
  color?: string;
  onClick: () => void;
}> = ({ value, color, onClick }) => (
  <button
    type="button"
    onClick={(e) => {
      e.stopPropagation();
      onClick();
    }}
    style={{
      all: 'unset',
      cursor: 'pointer',
      color,
      fontWeight: color ? 600 : 400,
      fontVariantNumeric: 'tabular-nums',
    }}
  >
    {value}
  </button>
);

/** 团队视图（K13）：只读，点数字回到「我的」视图同样的列表 */
const TeamView: React.FC<{ onJump: (j: TeamJump) => void }> = ({ onJump }) => {
  const { palette } = useAppTheme();
  const wide = useWide();
  const [period, setPeriod] = useState('week');
  const [deptId, setDeptId] = useState<number>();
  const [depts, setDepts] = useState<DeptTreeOption[]>([]);
  const [all, setAll] = useState(false);
  const [data, setData] = useState<Team>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    getDeptTreeOptions()
      .then(setDepts)
      .catch(() => setDepts([]));
  }, []);

  useEffect(() => {
    let alive = true;
    setError(undefined);
    followupApi
      .team({ period, deptId, exceptions: all ? 'all' : undefined })
      .then((d) => alive && setData(d))
      .catch((e) => {
        if (!alive) return;
        setData(undefined);
        setError(readBizError(e).message);
      });
    return () => {
      alive = false;
    };
  }, [period, deptId, all]);

  const toolbar = (
    <div
      style={{
        display: 'flex',
        gap: 8,
        alignItems: 'center',
        flexWrap: 'wrap',
        marginBottom: 16,
      }}
    >
      <Segmented
        value={period}
        options={PERIODS}
        onChange={(v) => setPeriod(v as string)}
      />
      {depts.length > 0 && (
        <TreeSelect
          allowClear
          style={{ width: 200 }}
          placeholder="团队：全部"
          treeData={depts}
          treeDefaultExpandAll
          showSearch={{ treeNodeFilterProp: 'title' }}
          value={deptId}
          onChange={setDeptId}
        />
      )}
      {data && (
        <span style={{ fontSize: 12, color: palette.mute }}>
          {dayjs(data.from).format('MM-DD')} ~ {dayjs(data.to).format('MM-DD')}
          ，对比 {dayjs(data.prevFrom).format('MM-DD')} ~{' '}
          {dayjs(data.prevTo).format('MM-DD')}
        </span>
      )}
    </div>
  );

  if (error)
    return (
      <>
        {toolbar}
        <Card style={{ padding: 20 }}>
          <span style={{ color: palette.mute }}>{error}</span>
        </Card>
      </>
    );
  if (!data)
    return (
      <>
        {toolbar}
        <Card style={{ padding: 20 }}>
          <Skeleton active paragraph={{ rows: 6 }} />
        </Card>
      </>
    );

  const r = data.results;
  const prevLabel = period === 'month' ? '上月' : '上周';
  const tiles: {
    label: string;
    value: React.ReactNode;
    sub: string;
    color: string;
  }[] = [
    {
      label: '新商机',
      value: r.newOpportunities.value,
      sub: `${prevLabel} ${r.newOpportunities.prev}`,
      color: palette.violet,
    },
    {
      label: '发出报价',
      value: r.quotationsSent.value,
      sub: `${prevLabel} ${r.quotationsSent.prev}`,
      color: palette.link,
    },
    {
      label: '发出 PI',
      value: r.pisSent.value,
      sub: `${prevLabel} ${r.pisSent.prev}`,
      color: palette.link,
    },
    {
      label: '到账成交',
      value: `${r.orders.count} 单`,
      sub: formatAmount(r.orders.amountCny ?? 0, 'CNY'),
      color: palette.link,
    },
    {
      label: '按时送达',
      value: `${r.onTime} / ${r.delivered}`,
      sub: r.delivered
        ? `${r.delivered - r.onTime} 单晚到`
        : '还没有签收的订单',
      color: palette.link,
    },
    {
      label: '获得评价',
      value: r.reviewed,
      sub: `邀请 ${r.invited} · 婉拒 ${r.declined}`,
      color: palette.red,
    },
    {
      label: '老客户成交',
      value: `${r.repeat.count} 单`,
      sub: formatAmount(r.repeat.amountCny ?? 0, 'CNY'),
      color: palette.green,
    },
    {
      label: '老客户按时联系',
      value:
        r.accountOnTimePercent != null ? `${r.accountOnTimePercent}%` : '—',
      sub: r.accountDue
        ? `应联系 ${r.accountDue} · 按时 ${r.accountOnTime}`
        : '这段时间没有应联系的老客户',
      color: palette.green,
    },
    {
      label: '老客户新询盘',
      value: r.accountInquiries.value,
      sub: `${prevLabel} ${r.accountInquiries.prev}`,
      color: palette.green,
    },
    {
      label: '沉睡客户',
      value: r.dormant,
      sub: `本期激活 ${r.activated}`,
      color: palette.orange,
    },
  ];

  const owner = (o: TeamOwner, extra: Partial<TeamJump> = {}) =>
    onJump({ ownerId: o.ownerId, ownerName: o.ownerName, ...extra });
  const ownerCols: TableColumnsType<TeamOwner> = [
    {
      title: '负责人',
      key: 'name',
      width: 120,
      render: (_, o) => (
        <div style={{ display: 'grid', gap: 2 }}>
          <b style={{ color: palette.ink }}>{o.ownerName}</b>
          {o.attention && (
            <span style={{ fontSize: 12, color: palette.orange }}>
              需要关注
            </span>
          )}
        </div>
      ),
    },
    ...BOARD_COLS.map((name, i) => ({
      title: name,
      key: `b${i}`,
      width: 80,
      render: (_: unknown, o: TeamOwner) => {
        const board = i + 1;
        const open = [
          BOARD_SALES,
          BOARD_FULFIL,
          BOARD_AFTER_SALES,
          BOARD_ACCOUNT,
        ].includes(board);
        const v = o.boards[i] ?? 0;
        return open ? (
          <NumButton value={v} onClick={() => owner(o, { board })} />
        ) : (
          <span style={{ color: palette.mute }}>{v}</span>
        );
      },
    })),
    {
      title: '今天要跟',
      key: 'today',
      width: 80,
      render: (_, o) => (
        <NumButton
          value={o.todayDue}
          color={palette.link}
          onClick={() => owner(o, { today: true })}
        />
      ),
    },
    {
      title: '逾期',
      key: 'overdue',
      width: 70,
      render: (_, o) => (
        <NumButton
          value={o.overdue}
          color={o.overdue >= ALERT_AT ? palette.red : undefined}
          onClick={() => owner(o, { today: true })}
        />
      ),
    },
    {
      title: '多次延后',
      key: 'snoozes',
      width: 80,
      render: (_, o) => (
        <span
          style={{
            color: o.manySnoozes >= ALERT_AT ? palette.red : undefined,
            fontWeight: o.manySnoozes >= ALERT_AT ? 600 : 400,
          }}
        >
          {o.manySnoozes}
        </span>
      ),
    },
    {
      title: '本期记跟进',
      key: 'records',
      width: 120,
      render: (_, o) => {
        const diff = o.records - o.prevRecords;
        return (
          <div style={{ display: 'grid', gap: 2 }}>
            <b style={{ color: palette.ink }}>{o.records}</b>
            <span style={{ fontSize: 12, color: palette.mute }}>
              {diff === 0
                ? `与${prevLabel}持平`
                : `比${prevLabel}${diff > 0 ? '多' : '少'} ${Math.abs(diff)}`}
            </span>
          </div>
        );
      },
    },
  ];

  const exRow = (x: TeamException) => (
    <div
      key={`${x.type}-${x.itemId}`}
      style={{
        border: `1px solid ${palette.hairline}`,
        borderRadius: 12,
        padding: '10px 12px',
        display: 'grid',
        gap: 4,
      }}
    >
      <div
        style={{
          display: 'flex',
          gap: 8,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <Pill tone={EX_TONE[x.type] ?? 'orange'}>{x.typeName}</Pill>
        <b style={{ color: palette.ink }}>{x.customerName}</b>
        <span style={{ color: palette.sub, fontSize: 13 }}>
          · {x.stageCode} {x.stageName}
        </span>
      </div>
      {x.text && (
        <div style={{ fontSize: 13, color: palette.sub }}>{x.text}</div>
      )}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: 12,
          color: palette.mute,
        }}
      >
        <span>
          {[
            x.ownerName,
            x.amount != null && x.currencyCode
              ? formatAmount(x.amount, x.currencyCode)
              : undefined,
          ]
            .filter(Boolean)
            .join(' · ')}
        </span>
        <Button
          size="small"
          type="link"
          style={{ padding: 0 }}
          onClick={() =>
            onJump({
              ownerId: x.ownerId,
              ownerName: x.ownerName,
              board: x.board,
              keyword: x.customerName,
            })
          }
        >
          查看
        </Button>
      </div>
    </div>
  );

  return (
    <>
      {toolbar}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
          gap: 10,
          marginBottom: 16,
        }}
      >
        {tiles.map((t) => (
          <Card key={t.label} style={{ padding: 16 }}>
            <div style={{ fontSize: 12, color: palette.mute }}>{t.label}</div>
            <div
              style={{
                fontSize: 24,
                fontWeight: 700,
                color: t.color,
                margin: '4px 0 2px',
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {t.value}
            </div>
            <div style={{ fontSize: 12, color: palette.sub }}>{t.sub}</div>
          </Card>
        ))}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide ? 'minmax(0, 1fr) 360px' : 'minmax(0, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ display: 'grid', gap: 16, minWidth: 0 }}>
          <Card style={{ padding: '16px 20px' }}>
            <b style={{ color: palette.ink }}>按负责人</b>
            <Table<TeamOwner>
              style={{ marginTop: 10 }}
              rowKey="ownerId"
              size="middle"
              columns={ownerCols}
              dataSource={data.owners}
              pagination={false}
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: '这个范围里没有业务员' }}
              onRow={(o) => ({
                onClick: () => owner(o),
                style: {
                  cursor: 'pointer',
                  background: o.attention ? palette.orangeSoft : undefined,
                },
              })}
            />
          </Card>
          <Card style={{ padding: '16px 20px' }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                gap: 12,
                flexWrap: 'wrap',
              }}
            >
              <b style={{ color: palette.ink }}>卡在哪一步</b>
              <span style={{ fontSize: 12, color: palette.mute }}>
                平均停留超过参考天数的标橙
              </span>
            </div>
            {STAGE_ROWS.map((row) => (
              <div
                key={row.board}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '72px minmax(0, 1fr)',
                  gap: 10,
                  marginTop: 12,
                }}
              >
                <b style={{ color: palette.link, fontSize: 13, paddingTop: 8 }}>
                  {row.name}
                </b>
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'repeat(auto-fill, minmax(118px, 1fr))',
                    gap: 8,
                  }}
                >
                  {data.stages
                    .filter((st) => st.board === row.board)
                    .map((st) => (
                      <button
                        type="button"
                        key={st.stageCode}
                        onClick={() =>
                          onJump({ board: st.board, stage: st.stageCode })
                        }
                        style={{
                          all: 'unset',
                          cursor: 'pointer',
                          padding: '8px 10px',
                          borderRadius: 10,
                          background: st.over
                            ? palette.orangeSoft
                            : palette.inset,
                          border: `1px solid ${st.over ? palette.orange : 'transparent'}`,
                        }}
                      >
                        <div style={{ fontSize: 12, color: palette.sub }}>
                          {st.stageCode} {st.stageName}
                        </div>
                        <div
                          style={{
                            fontSize: 18,
                            fontWeight: 700,
                            color: palette.ink,
                          }}
                        >
                          {st.count}
                          <span style={{ fontSize: 12, fontWeight: 400 }}>
                            {' '}
                            个
                          </span>
                        </div>
                        <div style={{ fontSize: 12, color: palette.mute }}>
                          {st.avgDays == null ? '—' : `平均 ${st.avgDays} 天`}
                          {st.refDays != null && ` / 参考 ${st.refDays}`}
                        </div>
                      </button>
                    ))}
                </div>
              </div>
            ))}
          </Card>
        </div>
        <Card style={{ padding: '16px 20px' }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 10,
            }}
          >
            <b style={{ color: palette.ink }}>需要主管看一眼</b>
            <Pill tone={data.exceptions.total ? 'red' : 'mute'}>
              {data.exceptions.total} 条
            </Pill>
          </div>
          {data.exceptions.items.length === 0 ? (
            <div style={{ color: palette.mute, padding: '12px 0' }}>
              没有需要关注的跟进
            </div>
          ) : (
            <div style={{ display: 'grid', gap: 8 }}>
              {data.exceptions.items.map(exRow)}
            </div>
          )}
          {!all && data.exceptions.total > data.exceptions.items.length && (
            <Button
              type="link"
              style={{ paddingLeft: 0, marginTop: 6 }}
              onClick={() => setAll(true)}
            >
              还有 {data.exceptions.total - data.exceptions.items.length} 条 ·
              全部查看
            </Button>
          )}
        </Card>
      </div>
    </>
  );
};

export default TeamView;
