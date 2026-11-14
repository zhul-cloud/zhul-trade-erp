import { BulbOutlined, CheckOutlined, PlusOutlined } from '@ant-design/icons';
import { Link, useParams } from '@umijs/max';
import {
  App,
  Button,
  InputNumber,
  Modal,
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
import {
  Card,
  CoverPill,
  DueText,
  EvidenceText,
  NextCell,
  Pill,
} from '../components';
import {
  CandidateModal,
  CandidateRecordModal,
  type CandidateTarget,
} from '../devDialogs';
import { CloseProjectModal, ProjectItemsModal } from '../dialogs';
import { candidateTone, evidenceText } from '../rules';
import {
  CAND,
  CAND_NAMES,
  CAND_SRC_NAMES,
  type Candidate,
  COVER,
  type Demand,
  modelName,
  PATHS,
  PROJECT,
  type Project,
  readBizError,
  scopeName,
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

/** 两段进度：「本专项合格候选 1 / 2」「范围内合格供应商 2 / 3」 */
const DevProgress: React.FC<{
  label: string;
  value: number;
  total: number;
  color: string;
  hint: string;
}> = ({ label, value, total, color, hint }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ minWidth: 0 }}>
      <span style={{ display: 'inline-flex', gap: 10, alignItems: 'baseline' }}>
        <span style={{ fontSize: 13, color: palette.sub }}>{label}</span>
        <b style={{ fontSize: 20 }}>
          {value} / {total}
        </b>
      </span>
      <Progress
        percent={total ? Math.min(100, Math.round((value / total) * 100)) : 0}
        showInfo={false}
        strokeColor={color}
      />
      <div style={{ fontSize: 12, color: palette.mute }}>{hint}</div>
    </div>
  );
};

/** 改开发目标：1–10 家，或去掉（只管型号时） */
const TargetModal: React.FC<{
  project?: Project;
  onClose: () => void;
  onDone: () => void;
}> = ({ project, onClose, onDone }) => {
  const { message } = App.useApp();
  const [target, setTarget] = useState<number | null>(1);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (project)
      setTarget(project.supplierTarget ?? project.dev?.rangeGap ?? 1);
  }, [project]);
  if (!project) return null;
  const save = async (t?: number) => {
    setSaving(true);
    try {
      await supplyApi.projectTarget(project.id, t);
      message.success(t ? '已修改开发目标' : '已去掉开发目标');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title={project.supplierTarget ? '改开发目标' : '加开发目标'}
      open
      width={420}
      onCancel={onClose}
      onOk={() => target && save(target)}
      okButtonProps={{ loading: saving, disabled: !target }}
      footer={(origin) => (
        <div style={{ display: 'flex', alignItems: 'center' }}>
          {project.supplierTarget != null && (project.total ?? 0) > 0 && (
            <Button type="link" danger onClick={() => save(undefined)}>
              去掉开发目标
            </Button>
          )}
          <span style={{ flex: 1 }} />
          {origin}
        </div>
      )}
    >
      <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
        <span>目标家数</span>
        <InputNumber
          min={1}
          max={10}
          precision={0}
          value={target}
          onChange={setTarget}
        />
      </div>
    </Modal>
  );
};

/** 专项详情（F7）：目标与进度、型号清单、候选供应商、处理记录 */
const ProjectDetailPage: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { palette } = useAppTheme();
  const wide = useWide();
  const [p, setP] = useState<Project>();
  const [error, setError] = useState('');
  const [items, setItems] = useState(false);
  const [close, setClose] = useState<boolean>();
  const [targetOpen, setTargetOpen] = useState(false);
  const [candidate, setCandidate] = useState<CandidateTarget>();
  const [record, setRecord] = useState<number[] | null>();

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
  const dev = p.dev;
  const devTask = p.supplierTarget != null;
  const canDev = active && devTask && !!dev?.canEdit;
  const canSee = !!dev?.canSeeSupplier;
  const devTitle = p.name.includes(scopeName(p))
    ? p.name
    : `${p.name} · ${scopeName(p)}`;
  const records = [...(p.records ?? []), ...(dev?.records ?? [])].sort((a, b) =>
    (b.time ?? '').localeCompare(a.time ?? ''),
  );
  const candColumns: TableColumnsType<Candidate> = [
    {
      title: '候选供应商',
      key: 'name',
      width: 200,
      render: (_, c) => (
        <span style={{ display: 'grid', gap: 2 }}>
          <span
            style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}
          >
            <b>{c.name ?? '—'}</b>
            {!c.supplierId && <Pill tone="orange">未关联</Pill>}
          </span>
          <span style={{ fontSize: 12, color: palette.mute }}>
            {[c.contactName, c.contactInfo].filter(Boolean).join(' · ') || '—'}
          </span>
        </span>
      ),
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 80,
      render: (v: number) => CAND_SRC_NAMES[v],
    },
    {
      title: '阶段',
      key: 'stage',
      width: 110,
      render: (_, c) => (
        <span style={{ display: 'grid', gap: 2, justifyItems: 'start' }}>
          <Pill tone={candidateTone(c.stage)}>{CAND_NAMES[c.stage]}</Pill>
          {c.stage === CAND.QUALIFIED && (
            <span style={{ fontSize: 12, color: palette.green }}>
              依据：{evidenceText(c.evidenceType, c.evidenceDate)}
            </span>
          )}
          {c.stage === CAND.ABANDONED && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              {c.abandonReason}
            </span>
          )}
        </span>
      ),
    },
    {
      title: '最近一次处理',
      key: 'last',
      render: (_, c) => (
        <span style={{ display: 'grid', gap: 2 }}>
          <span>{c.lastAction || '—'}</span>
          {c.lastActionAt && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              {dayjs(c.lastActionAt).format('MM-DD')} ·{' '}
              {c.lastActionByName ?? ''}
            </span>
          )}
        </span>
      ),
    },
    {
      title: '下一步行动 · 日期',
      key: 'next',
      width: 160,
      render: (_, c) =>
        c.stage === CAND.QUALIFIED ||
        c.stage === CAND.ABANDONED ||
        !c.nextAction ? (
          '—'
        ) : (
          <span style={{ display: 'grid', gap: 2 }}>
            <span>{c.nextAction}</span>
            <span style={{ fontSize: 12 }}>
              <DueText date={c.nextActionDate} prefix />
            </span>
          </span>
        ),
    },
    {
      title: '操作',
      key: 'op',
      width: 150,
      render: (_, c) =>
        canDev && (
          <span style={{ display: 'inline-flex', gap: 8 }}>
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              onClick={() => setRecord([c.id])}
            >
              记处理
            </Button>
            {!c.supplierId && c.stage !== CAND.ABANDONED && canSee && (
              <Button
                type="link"
                size="small"
                style={{ padding: 0 }}
                onClick={() =>
                  setCandidate({
                    projectId: p.id,
                    title: devTitle,
                    count: 0,
                    link: c,
                  })
                }
              >
                关联供应商库
              </Button>
            )}
          </span>
        ),
    },
  ];
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
            {devTask && <Pill tone="cyan">开发供应商</Pill>}
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
              <Button onClick={() => setTargetOpen(true)}>
                {devTask ? '改目标' : '加开发目标'}
              </Button>
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
              {p.total > 0 && (
                <div style={{ textAlign: 'right' }}>
                  <b style={{ fontSize: 16 }}>
                    已解决 {p.solved} / 共 {p.total}
                  </b>
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    型号变为需确认或可直接报价即算已解决
                  </div>
                </div>
              )}
            </div>
            {p.total > 0 && (
              <Progress
                percent={Math.round((p.solved / p.total) * 100)}
                showInfo={false}
                strokeColor={palette.green}
                style={{ marginTop: 12 }}
              />
            )}
            {devTask && dev && (
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: wide ? '1fr 1fr' : '1fr',
                  gap: 32,
                  marginTop: 16,
                }}
              >
                <DevProgress
                  label="本专项合格候选"
                  value={dev.qualified}
                  total={dev.target}
                  color={palette.green}
                  hint="候选在范围内有近 90 天的有效报价或采购订单即为合格"
                />
                <DevProgress
                  label="范围内合格供应商"
                  value={dev.rangeQualified}
                  total={dev.required}
                  color={palette.link}
                  hint={`热度要求 ${dev.required} 家；含不是候选的供应商`}
                />
              </div>
            )}
            {active && dev?.suggestClose && p.canClose && (
              <div style={{ marginTop: 12, color: palette.green }}>
                <CheckOutlined />{' '}
                {dev.qualified >= dev.target
                  ? '合格候选已达到目标'
                  : '这个品牌品类的合格供应商已经够了'}
                ，可以标为已完成
              </div>
            )}
            {p.resultNote && (
              <div style={{ marginTop: 12, color: palette.sub }}>
                {st.label}说明：{p.resultNote}
              </div>
            )}
          </Card>
          {(p.items?.length ?? 0) > 0 && (
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
          )}
          {dev && (
            <>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: 12,
                }}
              >
                <b style={{ fontSize: 16 }}>
                  候选供应商
                  {!devTask && (
                    <span
                      style={{
                        fontSize: 13,
                        color: palette.mute,
                        marginLeft: 8,
                      }}
                    >
                      已去掉开发目标，候选保留但不再计算进度
                    </span>
                  )}
                </b>
                {canDev && (
                  <span style={{ display: 'inline-flex', gap: 8 }}>
                    <Button
                      disabled={!dev.candidates.length}
                      onClick={() => setRecord(null)}
                    >
                      记一次处理
                    </Button>
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() =>
                        setCandidate({
                          projectId: p.id,
                          title: devTitle,
                          count: dev.candidates.length,
                        })
                      }
                    >
                      加候选
                    </Button>
                  </span>
                )}
              </div>
              <Card style={{ overflow: 'hidden' }}>
                <Table<Candidate>
                  rowKey="id"
                  size="middle"
                  columns={candColumns}
                  dataSource={dev.candidates}
                  pagination={false}
                  scroll={{ x: 760 }}
                  locale={{
                    emptyText:
                      '还没有候选，从线索供应商、搜索、展会或同行介绍里加',
                  }}
                />
              </Card>
              {dev.others.length > 0 && (
                <Card
                  style={{
                    padding: '12px 16px',
                    display: 'flex',
                    gap: 8,
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    color: palette.sub,
                  }}
                >
                  <BulbOutlined style={{ color: palette.link }} />
                  <span>
                    范围内另有 {dev.others.length} 家合格供应商不是候选
                    {canSee &&
                      `：${dev.others
                        .map(
                          (s) =>
                            `${s.name}（${evidenceText(s.evidenceType, s.evidenceDate)}）`,
                        )
                        .join('、')}`}
                  </span>
                  {canDev &&
                    canSee &&
                    dev.others.map((s) => (
                      <Button
                        key={s.id}
                        type="link"
                        size="small"
                        style={{ padding: 0 }}
                        onClick={() =>
                          setCandidate({
                            projectId: p.id,
                            title: devTitle,
                            count: dev.candidates.length,
                            supplier: s,
                          })
                        }
                      >
                        加 {s.name} 为候选
                      </Button>
                    ))}
                </Card>
              )}
            </>
          )}
        </div>
        <div style={{ display: 'grid', gap: 16, minWidth: 0 }}>
          <Card style={{ padding: 20 }}>
            <SectionTitle>处理记录</SectionTitle>
            {!records.length && (
              <div style={{ color: palette.mute, fontSize: 13 }}>暂无</div>
            )}
            <div style={{ display: 'grid', gap: 14 }}>
              {records.map((r) => (
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
      <TargetModal
        project={targetOpen ? p : undefined}
        onClose={() => setTargetOpen(false)}
        onDone={() => {
          setTargetOpen(false);
          load();
        }}
      />
      <CandidateModal
        target={candidate}
        canSeeSupplier={canSee}
        onClose={() => setCandidate(undefined)}
        onDone={() => {
          setCandidate(undefined);
          load();
        }}
      />
      <CandidateRecordModal
        open={record !== undefined}
        title={devTitle}
        candidates={dev?.candidates}
        checkedIds={record ?? undefined}
        canSeeSupplier={canSee}
        onLink={(c) => {
          setRecord(undefined);
          setCandidate({ projectId: p.id, title: devTitle, count: 0, link: c });
        }}
        onClose={() => setRecord(undefined)}
        onDone={() => {
          setRecord(undefined);
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
