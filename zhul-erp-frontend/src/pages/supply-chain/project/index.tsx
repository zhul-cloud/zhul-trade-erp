import { CheckOutlined } from '@ant-design/icons';
import { Link, useParams } from '@umijs/max';
import {
  Button,
  Progress,
  Result,
  Skeleton,
  Table,
  type TableColumnsType,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { DocLogCard, SectionTitle } from '@/components/DocFields/DocPage';
import { usePageTitle } from '@/components/PageTabs/hooks';
import { PageTitle, useWide } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { Card, CoverPill, EvidenceText, NextCell, Pill } from '../components';
import { CloseProjectModal, ProjectItemsModal } from '../dialogs';
import {
  COVER,
  type Demand,
  modelName,
  PATHS,
  PROJECT,
  type Project,
  readBizError,
  supplyApi,
} from '../service';

const STATUS: Record<
  number,
  { label: string; tone: 'accent' | 'green' | 'gray' }
> = {
  [PROJECT.ACTIVE]: { label: '进行中', tone: 'accent' },
  [PROJECT.DONE]: { label: '已完成', tone: 'green' },
  [PROJECT.CANCELLED]: { label: '已取消', tone: 'gray' },
};

/** 变为需确认或可直接报价即算已解决 */
const solved = (d: Demand) => d.coverageStatus >= COVER.CONFIRM;

/** 专项详情（F7）：目标与进度、型号清单、处理记录 */
const ProjectDetailPage: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { palette } = useAppTheme();
  const wide = useWide();
  const [p, setP] = useState<Project>();
  const [error, setError] = useState('');
  const [items, setItems] = useState(false);
  const [close, setClose] = useState<boolean>();

  const load = useCallback(() => {
    supplyApi
      .project(id)
      .then((r) => {
        setP(r);
        setError('');
      })
      .catch((e) => setError(readBizError(e).message));
  }, [id]);
  useEffect(load, [load]);
  usePageTitle(p?.name);

  if (error) return <Result status="warning" title={error} />;
  if (!p)
    return (
      <Card style={{ padding: 24 }}>
        <Skeleton active paragraph={{ rows: 10 }} />
      </Card>
    );

  const active = p.status === PROJECT.ACTIVE;
  const left = dayjs(p.dueDate)
    .startOf('day')
    .diff(dayjs().startOf('day'), 'day');
  const st = STATUS[p.status] ?? STATUS[PROJECT.ACTIVE];
  const columns: TableColumnsType<Demand> = [
    {
      title: '型号',
      key: 'model',
      width: 220,
      render: (_, d) => <Link to={PATHS.demand(d.id)}>{modelName(d)}</Link>,
    },
    {
      title: '覆盖状态',
      key: 'cover',
      width: 200,
      render: (_, d) => (
        <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <CoverPill status={d.coverageStatus || undefined} />
          {solved(d) && (
            <span style={{ color: palette.green, fontSize: 13 }}>
              <CheckOutlined /> 已解决
            </span>
          )}
        </span>
      ),
    },
    {
      title: '主渠道',
      dataIndex: 'primarySupplier',
      width: 160,
      render: (v?: string) => v || '—',
    },
    {
      title: '最近证据',
      key: 'evidence',
      width: 170,
      render: (_, d) => <EvidenceText d={d} />,
    },
    {
      title: '下一步行动 · 日期',
      key: 'next',
      width: 240,
      render: (_, d) => <NextCell d={d} />,
    },
    {
      title: '负责人',
      dataIndex: 'ownerName',
      width: 100,
      render: (v?: string) => v || '—',
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['供应链跟进', p.name]}
        title={
          <span
            style={{ display: 'inline-flex', gap: 10, alignItems: 'center' }}
          >
            {p.name}
            <Pill tone={st.tone}>{st.label}</Pill>
          </span>
        }
        description={[
          p.brandName,
          p.categoryName,
          p.ownerName && `负责人 ${p.ownerName}`,
          `截止 ${dayjs(p.dueDate).format('YYYY-MM-DD')}`,
          active
            ? left >= 0
              ? `还有 ${left} 天`
              : `超期 ${-left} 天`
            : p.closedAt &&
              `${st.label} ${formatDateTime(p.closedAt).slice(0, 10)}`,
        ]
          .filter(Boolean)
          .join(' · ')}
        actions={
          active &&
          p.canClose && (
            <>
              <Button onClick={() => setItems(true)}>增减型号</Button>
              <Button onClick={() => setClose(false)}>取消专项</Button>
              <Button type="primary" onClick={() => setClose(true)}>
                标为已完成
              </Button>
            </>
          )
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide ? 'minmax(0, 1fr) 400px' : 'minmax(0, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ display: 'grid', gap: 16, minWidth: 0 }}>
          <Card style={{ padding: 24 }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                gap: 16,
                flexWrap: 'wrap',
              }}
            >
              <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: 12, color: palette.mute }}>目标</div>
                <b style={{ fontSize: 16 }}>{p.goal}</b>
              </div>
              <div style={{ textAlign: 'right' }}>
                <b style={{ fontSize: 16 }}>
                  已解决 {p.solved} / 共 {p.total}
                </b>
                <div style={{ fontSize: 12, color: palette.mute }}>
                  型号变为需确认或可直接报价即算已解决
                </div>
              </div>
            </div>
            <Progress
              percent={p.total ? Math.round((p.solved / p.total) * 100) : 0}
              showInfo={false}
              strokeColor={palette.green}
              style={{ marginTop: 12 }}
            />
            {p.resultNote && (
              <div style={{ marginTop: 12, color: palette.sub }}>
                {st.label}说明：{p.resultNote}
              </div>
            )}
          </Card>
          <Card style={{ overflow: 'hidden' }}>
            <Table<Demand>
              rowKey="id"
              size="middle"
              columns={columns}
              dataSource={p.items ?? []}
              pagination={false}
              scroll={{ x: 'max-content' }}
            />
          </Card>
        </div>
        <div style={{ display: 'grid', gap: 16, minWidth: 0 }}>
          <Card style={{ padding: 20 }}>
            <SectionTitle>处理记录</SectionTitle>
            {!p.records?.length && (
              <div style={{ color: palette.mute, fontSize: 13 }}>暂无</div>
            )}
            <div style={{ display: 'grid', gap: 14 }}>
              {p.records?.map((r) => (
                <div key={`${r.kind}-${r.logId ?? r.time}`}>
                  <div
                    style={{ display: 'flex', gap: 10, alignItems: 'baseline' }}
                  >
                    <b>{r.system ? '系统' : r.operatorName}</b>
                    <span style={{ fontSize: 12, color: palette.mute }}>
                      {formatDateTime(r.time).slice(0, 16)}
                    </span>
                  </div>
                  <div style={{ color: palette.sub, overflowWrap: 'anywhere' }}>
                    {[r.content, r.statusChange].filter(Boolean).join('；')}
                  </div>
                </div>
              ))}
            </div>
          </Card>
          <DocLogCard logs={p.logs} />
        </div>
      </div>
      <ProjectItemsModal
        project={items ? p : undefined}
        onClose={() => setItems(false)}
        onDone={() => {
          setItems(false);
          load();
        }}
      />
      <CloseProjectModal
        target={
          close === undefined
            ? undefined
            : { id: p.id, done: close, solved: p.solved, total: p.total }
        }
        onClose={() => setClose(undefined)}
        onDone={() => {
          setClose(undefined);
          load();
        }}
      />
    </div>
  );
};

export default ProjectDetailPage;
