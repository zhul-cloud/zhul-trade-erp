import {
  AlertOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileSearchOutlined,
  RightOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import { Button, Skeleton, Tooltip } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import {
  Card,
  LevelPill,
  PageTitle,
  Pill,
  Stat,
  useWide,
} from '../shared/components';
import { formatMinutes, PATHS } from '../shared/constants';
import {
  type MyTask,
  type PartTimeBoard,
  partTimeBoardApi,
  readBizError,
} from '../shared/service';

/** 近 30 天每天回价的型号数：柱高按当期最大值缩放，悬停看具体数字 */
const Trend: React.FC<{ data: PartTimeBoard['trend'] }> = ({ data }) => {
  const { palette } = useAppTheme();
  const max = Math.max(1, ...data.map((d) => d.items));
  const total = data.reduce((sum, d) => sum + d.items, 0);
  return (
    <Card style={{ padding: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'space-between',
          marginBottom: 16,
        }}
      >
        <b style={{ color: palette.ink }}>近 30 天回价</b>
        <span style={{ color: palette.mute, fontSize: 12 }}>
          共 {total} 个型号
        </span>
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-end',
          gap: 4,
          height: 140,
        }}
      >
        {data.map((d) => (
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
        <span>{dayjs(data[0]?.date).format('M月D日')}</span>
        <span>今天</span>
      </div>
    </Card>
  );
};

const TodoRow: React.FC<{ task: MyTask }> = ({ task: t }) => {
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
          {t.taskCode} · 已填 {t.filledCount}/{t.itemCount} 个型号
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

/** 兼职看板：兼职采购登录后的首页，看自己的待办和回价统计 */
const PartTimeBoardPage: React.FC = () => {
  const wide = useWide();
  const { palette } = useAppTheme();
  const { initialState } = useModel('@@initialState');
  const [data, setData] = useState<PartTimeBoard | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      setData(await partTimeBoardApi.get());
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;

  const name = initialState?.currentUser?.name;
  return (
    <div>
      <PageTitle
        crumbs={['兼职看板']}
        title={name ? `你好，${name}` : '兼职看板'}
        description="分配给你的询价任务和本月回价情况，点任务直接去填价。"
        actions={
          <Button type="primary" onClick={() => history.push(PATHS.myTasks)}>
            去询价
          </Button>
        }
      />
      {!data ? (
        <Card style={{ padding: 20 }}>
          <Skeleton active paragraph={{ rows: 6 }} />
        </Card>
      ) : (
        <>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: wide
                ? 'repeat(4, minmax(0, 1fr))'
                : 'repeat(2, minmax(0, 1fr))',
              gap: 16,
              marginBottom: 16,
            }}
          >
            <Stat
              icon={<ClockCircleOutlined />}
              color={palette.orange}
              soft={palette.orangeSoft}
              label="待处理任务"
              value={data.pendingTasks}
              hint={
                data.timeoutTasks ? (
                  <span style={{ color: palette.red }}>
                    <AlertOutlined /> {data.timeoutTasks} 个已超时，请尽快回价
                  </span>
                ) : (
                  <span style={{ color: palette.mute }}>
                    {data.urgentTasks
                      ? `其中 ${data.urgentTasks} 个紧急`
                      : data.pendingTasks
                        ? '都还没超时'
                        : '暂时没有待办'}
                  </span>
                )
              }
            />
            <Stat
              icon={<FileSearchOutlined />}
              color={palette.link}
              soft={palette.accentSoft}
              label="本月回价型号"
              value={data.monthQuotedItems}
              hint={
                <span style={{ color: palette.mute }}>
                  有货报价 {data.monthQuotes} 条 · 无货 {data.monthNoStock} 条
                </span>
              }
            />
            <Stat
              icon={<CheckCircleOutlined />}
              color={palette.green}
              soft={palette.greenSoft}
              label="本月完成任务"
              value={data.monthCompletedTasks}
              hint={
                <span style={{ color: palette.mute }}>
                  {data.monthAvgHours == null
                    ? '本月还没有提交'
                    : data.monthAvgHours < 1
                      ? '平均不到 1 小时回价'
                      : `平均 ${data.monthAvgHours} 小时回价`}
                </span>
              }
            />
            <Stat
              icon={<TrophyOutlined />}
              color={palette.violet}
              soft={palette.violetSoft}
              label="累计回价型号"
              value={data.totalQuotedItems}
              hint={<span style={{ color: palette.mute }}>开始合作以来</span>}
            />
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: wide
                ? 'minmax(0, 3fr) minmax(0, 2fr)'
                : 'minmax(0, 1fr)',
              gap: 16,
            }}
          >
            <Trend data={data.trend} />
            <Card style={{ padding: 20 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'baseline',
                  justifyContent: 'space-between',
                  marginBottom: 4,
                }}
              >
                <b style={{ color: palette.ink }}>待处理任务</b>
                {data.pendingTasks > data.todo.length && (
                  <a onClick={() => history.push(PATHS.myTasks)}>
                    全部 {data.pendingTasks} 个
                  </a>
                )}
              </div>
              {data.todo.length ? (
                data.todo.map((t) => <TodoRow key={t.id} task={t} />)
              ) : (
                <div
                  style={{
                    color: palette.mute,
                    textAlign: 'center',
                    padding: '40px 0',
                  }}
                >
                  暂时没有待处理的任务，新任务分配给你后会出现在这里
                </div>
              )}
            </Card>
          </div>
        </>
      )}
    </div>
  );
};

export default PartTimeBoardPage;
