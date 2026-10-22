import {
  BarChartOutlined,
  CarOutlined,
  DashboardOutlined,
  FileDoneOutlined,
  InboxOutlined,
  RightOutlined,
  SendOutlined,
  ShoppingOutlined,
  UnorderedListOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Tooltip } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { LevelPill, Pill } from '@/pages/inquiry/shared/components';
import { formatMinutes, PATHS } from '@/pages/inquiry/shared/constants';
import type { MyTask } from '@/pages/inquiry/shared/service';
import { useAppTheme } from '@/theme/AppTheme';
import type { MyPurchase, QuoteBoard } from '../service';
import { Metric, TodoItem, useWidgetData, WidgetCard } from './WidgetCard';

const TaskRow: React.FC<{ task: MyTask }> = ({ task: t }) => {
  const { palette } = useAppTheme();
  return (
    <button
      type="button"
      onClick={() => history.push(`${PATHS.myTasks}?task=${t.id}`)}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        width: '100%',
        padding: '12px 4px',
        border: 'none',
        borderBottom: `1px solid ${palette.hairline}`,
        background: 'transparent',
        cursor: 'pointer',
        textAlign: 'left',
      }}
    >
      <span style={{ display: 'grid', gap: 4, minWidth: 0, flex: 1 }}>
        <span
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            color: palette.ink,
            fontWeight: 600,
          }}
        >
          <span
            style={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {t.brand}
            {t.category ? ` · ${t.category}` : ''}
          </span>
          <LevelPill value={t.level} />
          {t.urgent && <Pill tone="red">紧急</Pill>}
        </span>
        <span style={{ color: palette.mute, fontSize: 12 }}>
          {t.taskCode} · {t.salesName ? `业务员 ${t.salesName} · ` : ''}已填{' '}
          {t.filledCount}/{t.itemCount} 个型号
        </span>
      </span>
      <span
        style={{
          fontSize: 12,
          whiteSpace: 'nowrap',
          color: t.timeout ? palette.red : palette.sub,
        }}
      >
        {t.timeout
          ? `已超时 ${formatMinutes(t.remainingMinutes)}`
          : `剩 ${formatMinutes(t.remainingMinutes)}`}
      </span>
      <RightOutlined style={{ color: palette.faint, fontSize: 12 }} />
    </button>
  );
};

/** 我的询价：待处理（超时、紧急）、今天到期、被退回待补，以及待处理前 5 条 */
export const MyInquiryWidget: React.FC = () => {
  const { palette } = useAppTheme();
  const { data, error, reload } = useWidgetData<QuoteBoard>('my-inquiry');
  return (
    <WidgetCard
      icon={<UnorderedListOutlined />}
      tone="blue"
      title="我的询价"
      sub="分配给我的询价任务，最急的在前"
      extra={
        <Button size="small" onClick={() => history.push(PATHS.myTasks)}>
          去我的询价
        </Button>
      }
      loading={!data}
      error={error}
      onRetry={reload}
    >
      {data && (
        <>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Metric
              label="待处理任务"
              value={data.pendingTasks}
              hint={
                data.timeoutTasks || data.urgentTasks
                  ? `其中超时 ${data.timeoutTasks}、紧急 ${data.urgentTasks}`
                  : data.pendingTasks
                    ? '都还没超时'
                    : '暂时没有待办'
              }
              hintColor={data.timeoutTasks ? palette.orange : undefined}
              onClick={() => history.push(PATHS.myTasks)}
            />
            <Metric
              label="今天到期"
              value={data.dueTodayTasks}
              hint="今天之内要回价的任务"
            />
            <Metric
              label="被退回待补"
              value={data.returnedItems}
              hint="审核退回、还没再次提交的型号"
              hintColor={data.returnedItems ? palette.red : undefined}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            {data.todo.length ? (
              data.todo.map((t) => <TaskRow key={t.id} task={t} />)
            ) : (
              <div
                style={{
                  color: palette.mute,
                  textAlign: 'center',
                  padding: '28px 0 12px',
                }}
              >
                暂时没有待处理的任务，新任务分配给你后会出现在这里
              </div>
            )}
          </div>
        </>
      )}
    </WidgetCard>
  );
};

/** 回价统计（本月）：口径同原兼职看板 */
export const QuoteStatsWidget: React.FC = () => {
  const { data, error, reload } = useWidgetData<QuoteBoard>('quote-stats');
  return (
    <WidgetCard
      icon={<DashboardOutlined />}
      tone="green"
      title="回价统计"
      sub="本月"
      loading={!data}
      error={error}
      onRetry={reload}
    >
      {data && (
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ display: 'flex', gap: 12 }}>
            <Metric
              label="本月回价型号"
              value={data.monthQuotedItems}
              hint={`有货报价 ${data.monthQuotes} 条 · 无货 ${data.monthNoStock} 条`}
            />
            <Metric
              label="本月完成任务"
              value={data.monthCompletedTasks}
              hint={
                data.monthAvgHours == null
                  ? '本月还没有提交'
                  : data.monthAvgHours < 1
                    ? '平均不到 1 小时回价'
                    : `平均 ${data.monthAvgHours} 小时回价`
              }
            />
          </div>
          <Metric
            label="累计回价型号"
            value={data.totalQuotedItems}
            hint="开始合作以来"
          />
        </div>
      )}
    </WidgetCard>
  );
};

/** 近 30 天每天回价的型号数：柱高按当期最大值缩放，悬停看具体数字 */
export const QuoteTrendWidget: React.FC = () => {
  const { palette } = useAppTheme();
  const { data, error, reload } = useWidgetData<QuoteBoard>('quote-trend');
  const trend = data?.trend ?? [];
  const max = Math.max(1, ...trend.map((d) => d.items));
  const total = trend.reduce((sum, d) => sum + d.items, 0);
  return (
    <WidgetCard
      icon={<BarChartOutlined />}
      tone="blue"
      title="近 30 天回价"
      sub="每天回价的型号数"
      extra={
        <span style={{ color: palette.mute, fontSize: 12 }}>
          共 {total} 个型号
        </span>
      }
      loading={!data}
      error={error}
      onRetry={reload}
    >
      <div
        style={{ display: 'flex', alignItems: 'flex-end', gap: 4, height: 150 }}
      >
        {trend.map((d) => (
          <Tooltip
            key={d.date}
            title={`${dayjs(d.date).format('M月D日')} · ${d.items} 个型号`}
          >
            <span
              style={{
                flex: 1,
                minWidth: 0,
                height: d.items ? `${(d.items / max) * 100}%` : 3,
                borderRadius: 4,
                background: d.items ? palette.link : palette.hairline,
                opacity: d.items ? 0.85 : 1,
                transition: 'height 200ms ease-out',
              }}
            />
          </Tooltip>
        ))}
      </div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          color: palette.mute,
          fontSize: 12,
          marginTop: 8,
        }}
      >
        <span>{trend[0] ? dayjs(trend[0].date).format('M月D日') : ''}</span>
        <span>今天</span>
      </div>
    </WidgetCard>
  );
};

/** 我的采购：只显示有页面权限的数字，口径同采购需求、采购订单、供应商发货页 */
export const MyPurchaseWidget: React.FC = () => {
  const { data, error, reload } = useWidgetData<MyPurchase>('my-purchase');
  const items: React.ReactNode[] = [];
  if (data?.openRequirements !== undefined) {
    items.push(
      <TodoItem
        key="req"
        icon={<InboxOutlined />}
        tone="blue"
        title="待下单的采购需求"
        desc="销售订单已确认，还没全部下单"
        count={data.openRequirements}
        alert
        onClick={() => history.push('/purchase/requirements')}
      />,
    );
  }
  if (data?.pendingShip !== undefined) {
    items.push(
      <TodoItem
        key="ship"
        icon={<SendOutlined />}
        tone="violet"
        title="待供应商发货"
        desc={
          data.overdueShip
            ? `已下单还有未发数量，其中 ${data.overdueShip} 张已过预计发货日期`
            : '已下单，还有未发数量'
        }
        count={data.pendingShip}
        onClick={() => history.push('/purchase/orders')}
      />,
      <TodoItem
        key="draft"
        icon={<FileDoneOutlined />}
        tone="gray"
        title="草稿采购订单"
        desc="还没确认下单"
        count={data.drafts ?? 0}
        onClick={() => history.push('/purchase/orders')}
      />,
    );
  }
  if (data?.inTransit !== undefined) {
    items.push(
      <TodoItem
        key="transit"
        icon={<CarOutlined />}
        tone="green"
        title="在途"
        desc="供应商已发货，还没到仓"
        count={data.inTransit}
        onClick={() => history.push('/purchase/shipments')}
      />,
      <TodoItem
        key="diff"
        icon={<WarningOutlined />}
        tone="red"
        title="到货差异待处理"
        desc="少发、错发、不良"
        count={data.discrepancies ?? 0}
        alert
        onClick={() => history.push('/purchase/shipments?tab=diffs')}
      />,
    );
  }
  return (
    <WidgetCard
      icon={<ShoppingOutlined />}
      tone="violet"
      title="我的采购"
      sub="点击进入对应列表"
      loading={!data}
      error={error}
      onRetry={reload}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
          gap: 8,
        }}
      >
        {items}
      </div>
    </WidgetCard>
  );
};
