import { Link } from '@umijs/max';
import {
  Button,
  Progress,
  Select,
  Skeleton,
  Table,
  type TableColumnsType,
} from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { liveRequests } from '@/utils/liveRequests';
import { tableScrollX } from '@/utils/tableScroll';
import { Card, Pill } from '../components';
import {
  PATHS,
  type Rank,
  readBizError,
  supplyApi,
  type Team,
} from '../service';

const PERIODS = [
  { value: 30, label: '近 30 天' },
  { value: 90, label: '近 90 天' },
];

const ATTENTION: Record<
  Team['attention'][number]['kind'],
  { label: string; tone: 'red' | 'orange' }
> = {
  overdue: { label: '逾期', tone: 'red' },
  defer: { label: '多次延后', tone: 'orange' },
  'no-owner': { label: '没有负责人', tone: 'orange' },
  project: { label: '专项超期', tone: 'red' },
};

const rate = (v?: number | null) => (v == null ? '—' : `${v}%`);

export interface TeamJump {
  brandId?: number;
  categoryId?: number;
  ownerId?: number;
  /** 无货统计期（30 / 90 天），与团队视图一致 */
  days?: number;
}

const Kpi: React.FC<{
  label: string;
  value: React.ReactNode;
  sub: string;
  color?: string;
}> = ({ label, value, sub, color }) => {
  const { palette } = useAppTheme();
  return (
    <Card style={{ padding: '16px 20px' }}>
      <div style={{ fontSize: 13, color: palette.sub }}>{label}</div>
      <div
        style={{
          fontSize: 28,
          fontWeight: 700,
          color: color ?? palette.ink,
          margin: '4px 0',
        }}
      >
        {value}
      </div>
      <div style={{ fontSize: 12, color: palette.mute }}>{sub}</div>
    </Card>
  );
};

const RankList: React.FC<{
  title: string;
  rows: Rank[];
  color: string;
  reason?: boolean;
  onJump?: (r: Rank) => void;
  onProject?: (r: Rank) => void;
}> = ({ title, rows, color, reason, onJump, onProject }) => {
  const { palette } = useAppTheme();
  const max = Math.max(1, ...rows.map((r) => (reason ? r.times : r.models)));
  return (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 13, color: palette.mute, marginBottom: 8 }}>
        {title}
      </div>
      {rows.length === 0 && (
        <div style={{ color: palette.mute }}>没有无货记录</div>
      )}
      {rows.map((r) => (
        <div
          key={r.label}
          style={{
            display: 'grid',
            gridTemplateColumns: reason
              ? '100px minmax(0, 1fr) 50px'
              : '90px minmax(0, 1fr) 110px 60px 60px',
            gap: 10,
            alignItems: 'center',
            padding: '5px 0',
            fontSize: 13,
          }}
        >
          {onJump ? (
            <Button
              type="link"
              size="small"
              style={{ padding: 0, justifyContent: 'flex-start' }}
              onClick={() => onJump(r)}
            >
              {r.label}
            </Button>
          ) : (
            <span>{r.label}</span>
          )}
          <span
            style={{ height: 6, borderRadius: 3, background: palette.hairline }}
          >
            <span
              style={{
                display: 'block',
                height: 6,
                borderRadius: 3,
                width: `${((reason ? r.times : r.models) / max) * 100}%`,
                background: color,
              }}
            />
          </span>
          {reason ? (
            <span>{r.times} 次</span>
          ) : (
            <>
              <span>
                {r.models} 个型号 · {r.times} 次
              </span>
              <span style={{ color: r.open ? palette.orange : palette.mute }}>
                待解决 {r.open}
              </span>
              {r.projectId ? (
                <Link to={PATHS.project(r.projectId)}>专项</Link>
              ) : onProject && r.brandId ? (
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0 }}
                  onClick={() => onProject(r)}
                >
                  建专项
                </Button>
              ) : (
                <span />
              )}
            </>
          )}
        </div>
      ))}
    </div>
  );
};

/** 团队视图：结果指标、无货集中在哪、按负责人、专项进度、需要主管看一眼 */
const TeamView: React.FC<{
  canProject: boolean;
  onJump: (j: TeamJump) => void;
  onProject: (scope: { brandId: number; categoryId?: number }) => void;
}> = ({ canProject, onJump, onProject }) => {
  const { palette } = useAppTheme();
  const [period, setPeriod] = useState(30);
  const [team, setTeam] = useState<Team>();
  const [error, setError] = useState('');
  const [tick, setTick] = useState(0);
  useEffect(() => {
    setTeam(undefined);
    setError('');
    const { live, cancel } = liveRequests();
    // 失败显示原因与重试，不能一直转骨架屏（audit UI-091）
    live(supplyApi.team(period))
      .then(setTeam)
      .catch((e) => setError(readBizError(e).message));
    return cancel;
  }, [period, tick]);

  const head = (
    <div
      style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}
    >
      <Select
        style={{ width: 130 }}
        value={period}
        onChange={setPeriod}
        options={PERIODS}
      />
    </div>
  );
  if (!team)
    return (
      <>
        {head}
        <Card style={{ padding: 20 }}>
          {error ? (
            <ErrorHint
              message={`团队视图加载失败：${error}`}
              onRetry={() => setTick((n) => n + 1)}
            />
          ) : (
            <Skeleton active paragraph={{ rows: 10 }} />
          )}
        </Card>
      </>
    );
  const t = team;
  const ownerColumns: TableColumnsType<Team['owners'][number]> = [
    {
      title: '负责人',
      key: 'name',
      width: 140,
      render: (_, r) => (
        <div style={{ display: 'grid' }}>
          {/* 没有负责人的行没有对应的筛选，不做成链接，避免跳到「全部负责人」（audit UI-089） */}
          {r.ownerId ? (
            <Button
              type="link"
              size="small"
              style={{ padding: 0, justifyContent: 'flex-start' }}
              onClick={() => onJump({ ownerId: r.ownerId })}
            >
              {r.ownerName ?? '未指定负责人'}
            </Button>
          ) : (
            <span>{r.ownerName ?? '未指定负责人'}</span>
          )}
          {(r.overdue >= 3 || r.multiDefer >= 2) && (
            <span style={{ fontSize: 12, color: palette.orange }}>
              需要关注
            </span>
          )}
        </div>
      ),
    },
    { title: '名下型号', dataIndex: 'models', width: 90 },
    { title: '无渠道', dataIndex: 'noChannel', width: 90 },
    { title: '未验证', dataIndex: 'lead', width: 90 },
    { title: '需确认', dataIndex: 'confirm', width: 90 },
    {
      title: '今天要跟',
      width: 90,
      dataIndex: 'dueToday',
      render: (v, r) => (r.ownerId ? v : '—'),
    },
    {
      title: '逾期',
      width: 90,
      dataIndex: 'overdue',
      render: (v: number, r) =>
        r.ownerId ? (
          <span style={{ color: v ? palette.red : undefined }}>{v}</span>
        ) : (
          '—'
        ),
    },
    {
      title: '多次延后',
      width: 90,
      dataIndex: 'multiDefer',
      render: (v: number, r) =>
        r.ownerId ? (
          <span style={{ color: v ? palette.red : undefined }}>{v}</span>
        ) : (
          '—'
        ),
    },
    {
      title: '本周记处理',
      width: 100,
      dataIndex: 'records',
      render: (v, r) => (r.ownerId ? v : '—'),
    },
  ];
  return (
    <div>
      {head}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
          gap: 12,
        }}
      >
        <Kpi
          label="无货"
          value={`${t.stockoutModels} 个型号`}
          sub={`${t.stockoutTimes} 次 · 上期 ${t.prevStockoutModels} 个型号`}
          color={t.stockoutModels ? palette.red : undefined}
        />
        <Kpi
          label="无货率"
          value={rate(t.stockoutRate)}
          sub="无货型号 / 询过价的型号"
        />
        <Kpi
          label="有效覆盖率"
          value={rate(t.coverRate)}
          sub="可直接报价 / 矩阵内型号"
        />
        <Kpi
          label="可报价率"
          value={rate(t.quotableRate)}
          sub="可直接报价 + 需确认"
        />
        <Kpi
          label="备用渠道率"
          value={rate(t.backupRate)}
          sub="有备选渠道 / 可报价型号"
        />
        <Kpi
          label="补上的缺口"
          value={`${t.filledGaps} 个型号`}
          sub="由无渠道、未验证变为可报"
          color={t.filledGaps ? palette.green : undefined}
        />
      </div>

      <Card style={{ padding: 20, marginTop: 16 }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            marginBottom: 12,
          }}
        >
          <span>
            <b style={{ fontSize: 16 }}>无货集中在哪</b>
            <span style={{ color: palette.mute, marginLeft: 8 }}>
              近 {t.days} 天 · 点名称查看型号
            </span>
          </span>
          <Link to={`${PATHS.coverage}?mode=stockout`}>
            在覆盖矩阵里按无货查看
          </Link>
        </div>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
            gap: 24,
          }}
        >
          <RankList
            title="按品牌"
            rows={t.byBrand}
            color={palette.red}
            onJump={(r) => onJump({ brandId: r.brandId, days: t.days })}
            onProject={
              canProject
                ? (r) => r.brandId && onProject({ brandId: r.brandId })
                : undefined
            }
          />
          <RankList
            title="按品类"
            rows={t.byCategory}
            color={palette.red}
            onJump={(r) => onJump({ categoryId: r.categoryId, days: t.days })}
          />
          <RankList
            title="无货原因"
            rows={t.reasons}
            color={palette.orange}
            reason
          />
        </div>
      </Card>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 2.2fr) minmax(300px, 1fr)',
          gap: 16,
          marginTop: 16,
        }}
      >
        <div style={{ display: 'grid', gap: 16, alignContent: 'start' }}>
          <Card style={{ padding: 20 }}>
            <b style={{ fontSize: 16 }}>按负责人</b>
            <Table
              style={{ marginTop: 12 }}
              size="middle"
              rowKey={(r) => r.ownerId ?? 0}
              pagination={false}
              dataSource={t.owners}
              locale={{
                emptyText: '还没有指定负责人的型号，可在型号详情里指定',
              }}
              scroll={{ x: tableScrollX(ownerColumns) }}
              columns={ownerColumns}
            />
          </Card>
          <Card style={{ padding: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <b style={{ fontSize: 16 }}>专项进度</b>
              <span style={{ color: palette.mute }}>
                进行中 {t.projects.length}
              </span>
            </div>
            {t.projects.length === 0 && (
              <div style={{ color: palette.mute, marginTop: 12 }}>
                没有进行中的专项
              </div>
            )}
            {t.projects.map((p) => (
              <div key={p.id} style={{ marginTop: 16 }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    gap: 12,
                  }}
                >
                  <span
                    style={{
                      display: 'inline-flex',
                      gap: 8,
                      alignItems: 'center',
                    }}
                  >
                    <Link to={PATHS.project(p.id)} style={{ fontWeight: 600 }}>
                      {p.name}
                    </Link>
                    {p.overdueDays > 0 && (
                      <Pill tone="red">超期 {p.overdueDays} 天</Pill>
                    )}
                  </span>
                  <span>
                    已解决 {p.solved} / {p.total}
                  </span>
                </div>
                <Progress
                  percent={p.total ? Math.round((p.solved / p.total) * 100) : 0}
                  showInfo={false}
                  strokeColor={palette.green}
                  style={{ maxWidth: 560, margin: '4px 0' }}
                />
                <div
                  style={{
                    fontSize: 12,
                    color: p.overdueDays > 0 ? palette.red : palette.mute,
                  }}
                >
                  {p.ownerName} · {dayjs(p.dueDate).format('MM-DD')} 截止
                </div>
              </div>
            ))}
          </Card>
        </div>
        <Card style={{ padding: 20, alignSelf: 'start' }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              marginBottom: 12,
            }}
          >
            <b style={{ fontSize: 16 }}>需要主管看一眼</b>
            {t.attention.length > 0 && (
              <Pill tone="red">{t.attention.length} 条</Pill>
            )}
          </div>
          {t.attention.length === 0 && (
            <div style={{ color: palette.mute }}>暂时没有需要关注的</div>
          )}
          <div style={{ display: 'grid', gap: 10 }}>
            {t.attention.map((a) => (
              <div
                key={`${a.kind}-${a.demandId ?? a.projectId}`}
                style={{
                  padding: 12,
                  borderRadius: 10,
                  background: palette.inset,
                  display: 'grid',
                  gap: 4,
                }}
              >
                <span
                  style={{
                    display: 'inline-flex',
                    gap: 8,
                    alignItems: 'center',
                  }}
                >
                  <Pill tone={ATTENTION[a.kind].tone}>
                    {ATTENTION[a.kind].label}
                  </Pill>
                  <b>{a.title}</b>
                </span>
                {a.detail && <span style={{ fontSize: 13 }}>{a.detail}</span>}
                <span
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: 12,
                    color: palette.mute,
                  }}
                >
                  {a.ownerName ?? ''}
                  <Link
                    to={
                      a.projectId
                        ? PATHS.project(a.projectId)
                        : PATHS.demand(a.demandId ?? 0)
                    }
                  >
                    查看
                  </Link>
                </span>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
};

export default TeamView;
