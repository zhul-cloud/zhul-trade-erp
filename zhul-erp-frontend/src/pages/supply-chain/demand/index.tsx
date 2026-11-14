import {
  BranchesOutlined,
  CalendarOutlined,
  CloseCircleOutlined,
  EditOutlined,
  EyeOutlined,
  MessageOutlined,
  MoreOutlined,
  PlusOutlined,
  RobotOutlined,
  SearchOutlined,
  ShoppingCartOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { history, Link, useParams } from '@umijs/max';
import {
  App,
  Button,
  Dropdown,
  Modal,
  Result,
  Segmented,
  Select,
  Skeleton,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { DocLogCard, SectionTitle } from '@/components/DocFields/DocPage';
import { usePageTitle } from '@/components/PageTabs/hooks';
import SupplierSelect from '@/pages/inquiry/my-tasks/SupplierSelect';
import {
  PageTitle,
  useQuoteDicts,
  useWide,
} from '@/pages/inquiry/shared/components';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { Card, CoverPill, LevelPill, Pill, PriorityPill } from '../components';
import {
  CoverageModal,
  NextModal,
  PriorityModal,
  RecordModal,
} from '../dialogs';
import { signalDoc, signalText } from '../rules';
import {
  type Channel,
  DEMAND,
  type Demand,
  type DemandDetail,
  type Evidence,
  modelName,
  PATHS,
  readBizError,
  SRC,
  SRC_NAMES,
  supplyApi,
  type TimelineItem,
} from '../service';

/** 时间线默认显示条数 */
const FOLD = 7;

const SIGNAL_ICON: Record<number, React.ReactNode> = {
  [SRC.INQUIRY]: <MessageOutlined />,
  [SRC.ORDER]: <ShoppingCartOutlined />,
  [SRC.STOCKOUT]: <StopOutlined />,
  [SRC.LOST]: <CloseCircleOutlined />,
  [SRC.SEARCH]: <SearchOutlined />,
  [SRC.RESEARCH]: <EyeOutlined />,
};

const evidenceDoc = (e: Evidence) =>
  e.docNo
    ? e.type === 2 && e.docId
      ? `/purchase/orders/${e.docId}`
      : PATHS.sourcingTask(e.docNo)
    : undefined;

const TimelineRow: React.FC<{ t: TimelineItem }> = ({ t }) => {
  const { palette } = useAppTheme();
  const s = t.signal;
  const icon = s ? (
    SIGNAL_ICON[s.sourceType]
  ) : t.system ? (
    <RobotOutlined />
  ) : (
    <EditOutlined />
  );
  const color = s
    ? s.sourceType === SRC.STOCKOUT
      ? palette.red
      : s.sourceType === SRC.LOST
        ? palette.orange
        : s.sourceType === SRC.ORDER
          ? palette.green
          : palette.link
    : t.system
      ? palette.green
      : palette.sub;
  const title = s
    ? SRC_NAMES[s.sourceType]
    : t.system
      ? '系统 自动登记'
      : `${t.operatorName ?? ''} 记处理`;
  const docUrl = s ? signalDoc(s) : undefined;
  const body = s
    ? signalText(s)
    : [
        t.content,
        t.nextAction
          ? `下一步${t.nextAction}${t.nextActionDate ? `（${dayjs(t.nextActionDate).format('MM-DD')}）` : ''}`
          : '',
        t.statusChange ?? '',
      ]
        .filter(Boolean)
        .join('；');
  return (
    <div style={{ display: 'flex', gap: 14, opacity: s?.cancelled ? 0.5 : 1 }}>
      <span
        style={{
          width: 32,
          height: 32,
          borderRadius: 16,
          flexShrink: 0,
          display: 'grid',
          placeItems: 'center',
          background: palette.inset,
          color,
        }}
      >
        {icon}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', gap: 10, alignItems: 'baseline' }}>
          <b>{title}</b>
          <span style={{ fontSize: 12, color: palette.mute }}>
            {dayjs(t.time).format('YYYY-MM-DD')}
          </span>
          {s?.cancelled && <Pill tone="gray">已撤回</Pill>}
          <span style={{ flex: 1 }} />
          {s?.sourceDocNo &&
            (docUrl ? (
              <Link to={docUrl}>{s.sourceDocNo}</Link>
            ) : (
              <span>{s.sourceDocNo}</span>
            ))}
        </div>
        {body && (
          <div
            style={{
              marginTop: 2,
              color: palette.sub,
              overflowWrap: 'anywhere',
            }}
          >
            {body}
          </div>
        )}
      </div>
    </div>
  );
};

const Stat: React.FC<{ n: number; label: string; color?: string }> = ({
  n,
  label,
  color,
}) => {
  const { palette } = useAppTheme();
  return (
    <div>
      <div
        style={{
          fontSize: 28,
          fontWeight: 700,
          color: n ? (color ?? palette.ink) : palette.ink,
        }}
      >
        {n}
      </div>
      <div style={{ fontSize: 13, color: palette.sub }}>{label}</div>
    </div>
  );
};

const ChannelCard: React.FC<{ c: Channel }> = ({ c }) => {
  const { palette } = useAppTheme();
  const { conditionLabel, leadTimeLabel } = useQuoteDicts();
  const primary = c.role === 1;
  return (
    <div
      style={{
        padding: 14,
        borderRadius: 10,
        background: palette.inset,
        display: 'grid',
        gap: 6,
      }}
    >
      <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
        <Pill tone={primary ? 'accent' : 'gray'}>
          {primary ? '主渠道' : '备选'}
        </Pill>
        <b>{c.supplierName ?? '—'}</b>
      </span>
      {c.unitPrice != null ? (
        <span>
          <b>{formatAmount(c.unitPrice, c.currencyCode)}</b>
          <span style={{ fontSize: 12, color: palette.sub, marginLeft: 8 }}>
            {[
              c.taxIncluded ? '含税' : '不含税',
              conditionLabel(c.itemCondition),
              leadTimeLabel(c.leadTime),
            ]
              .filter(Boolean)
              .join(' · ')}
          </span>
        </span>
      ) : (
        <span style={{ fontSize: 12, color: palette.mute }}>
          没有这个型号的价格记录
        </span>
      )}
      {c.evidenceDate && (
        <span
          style={{
            fontSize: 12,
            color: palette.mute,
            display: 'inline-flex',
            gap: 6,
          }}
        >
          <CalendarOutlined />
          {c.evidenceType === 2 ? '采购' : '询价'}{' '}
          {dayjs(c.evidenceDate).format('YYYY-MM-DD')}
        </span>
      )}
    </div>
  );
};

/** 设主渠道、加备选渠道 */
const ChannelModal: React.FC<{
  detail?: DemandDetail;
  onClose: () => void;
  onDone: () => void;
}> = ({ detail, onClose, onDone }) => {
  const { message } = App.useApp();
  const channels = detail?.channels ?? [];
  const current = channels.find((c) => c.role === 1);
  const [primary, setPrimary] = useState<number>();
  const [backup, setBackup] = useState<number>();
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    setPrimary(current?.supplierId);
    setBackup(undefined);
  }, [current?.supplierId]);
  if (!detail) return null;
  const backups = channels.filter((c) => c.role !== 1).map((c) => c.supplierId);
  const submit = async () => {
    if (!primary && !backup) {
      message.warning('请选择供应商');
      return;
    }
    setSaving(true);
    try {
      const all = backup ? [...backups, backup] : backups;
      await supplyApi.channels(
        detail.demand.id,
        primary ?? backup,
        all.filter((id) => id !== (primary ?? backup)),
      );
      message.success('已保存渠道');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      open
      title="渠道"
      okText="保存"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <div style={{ display: 'grid', gap: 16, marginTop: 8 }}>
        <div style={{ display: 'grid', gap: 6 }}>
          <span>主渠道</span>
          <SupplierSelect
            value={primary}
            label={current?.supplierName}
            onChange={(v) => setPrimary(v)}
          />
        </div>
        <div style={{ display: 'grid', gap: 6 }}>
          <span>添加备选渠道</span>
          <SupplierSelect value={backup} onChange={(v) => setBackup(v)} />
        </div>
      </div>
    </Modal>
  );
};

/** 合并重复：当前型号的全部信号并到所选型号，当前型号删除 */
const MergeModal: React.FC<{
  demand?: Demand;
  onClose: () => void;
}> = ({ demand: d, onClose }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [options, setOptions] = useState<Demand[]>([]);
  const [keep, setKeep] = useState<number>();
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    setKeep(undefined);
    setOptions([]);
  }, [d?.id]);
  if (!d) return null;
  const search = (keyword: string) => {
    if (!keyword.trim()) return;
    supplyApi
      .models({ keyword, all: true, brandId: d.brandId, size: 20 })
      .then((p) => setOptions(p.records.filter((x) => x.id !== d.id)))
      .catch(() => setOptions([]));
  };
  const submit = async () => {
    if (!keep) {
      message.warning('请选择要保留的型号');
      return;
    }
    setSaving(true);
    try {
      await supplyApi.merge(keep, d.id);
      message.success('已合并');
      onClose();
      history.replace(PATHS.demand(keep));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      open
      title="合并重复"
      okText="合并"
      okButtonProps={{ danger: true }}
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <div style={{ color: palette.sub, margin: '8px 0 12px' }}>
        「{modelName(d)}
        」的全部需求信号会并到下面选的型号，当前型号随后删除，不能撤销。
      </div>
      <Select
        style={{ width: '100%' }}
        placeholder="搜索同品牌的型号，选择要保留的一方"
        value={keep}
        onChange={setKeep}
        showSearch={{ onSearch: search, filterOption: false }}
        options={options.map((x) => ({
          value: x.id,
          label: `${modelName(x)} · ${x.categoryName ?? '未分类'}`,
        }))}
        notFoundContent="输入型号搜索"
      />
    </Modal>
  );
};

/** 需求对象详情（F8）：优先级依据、时间线、覆盖情况、负责人与下一步、操作日志 */
const DemandDetailPage: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const [data, setData] = useState<DemandDetail>();
  const [error, setError] = useState('');
  const [filter, setFilter] = useState<'all' | 'signal' | 'log'>('all');
  const [expand, setExpand] = useState(false);
  const [modal, setModal] = useState<
    'priority' | 'pause' | 'record' | 'coverage' | 'next' | 'channel' | 'merge'
  >();

  const load = useCallback(() => {
    supplyApi
      .detail(id)
      .then((d) => {
        setData(d);
        setError('');
      })
      .catch((e) => setError(readBizError(e).message));
  }, [id]);
  useEffect(load, [load]);
  const d = data?.demand;
  usePageTitle(d ? modelName(d) : undefined);

  if (error) return <Result status="warning" title={error} />;
  if (!data || !d)
    return (
      <Card style={{ padding: 24 }}>
        <Skeleton active paragraph={{ rows: 10 }} />
      </Card>
    );

  const done = () => {
    setModal(undefined);
    load();
  };
  const act = async (fn: () => Promise<void>, ok: string) => {
    try {
      await fn();
      message.success(ok);
      load();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const signals = data.timeline.filter((t) => t.kind === 'signal').length;
  const logs = data.timeline.length - signals;
  const rows = data.timeline.filter(
    (t) => filter === 'all' || t.kind === filter,
  );
  const shown = expand ? rows : rows.slice(0, FOLD);
  const project = data.projects.find((p) => p.status === 1) ?? data.projects[0];
  const channels = data.channels ?? [];
  const projectLink = project ? (
    <Link to={PATHS.project(project.id)}>
      {project.name} · {project.solved} / {project.total}
    </Link>
  ) : (
    '—'
  );
  const ev = data.evidence;
  const evUrl = ev ? evidenceDoc(ev) : undefined;
  const statusText =
    d.status === DEMAND.IN_MATRIX
      ? '已进入覆盖矩阵'
      : d.status === DEMAND.PAUSED
        ? `暂不覆盖${d.pauseReason ? `：${d.pauseReason}` : ''}`
        : '观察中';

  return (
    <div>
      <PageTitle
        crumbs={['供应链跟进', modelName(d)]}
        title={
          <span
            style={{ display: 'inline-flex', gap: 10, alignItems: 'center' }}
          >
            {modelName(d)}
            <LevelPill value={d.demandLevel} />
            <PriorityPill value={d.priority} />
          </span>
        }
        description={[
          d.brandName,
          d.categoryName,
          d.lastSignalAt &&
            `最近出现 ${dayjs(d.lastSignalAt).format('YYYY-MM-DD')}`,
          statusText,
        ]
          .filter(Boolean)
          .join(' · ')}
        actions={
          data.canEdit && (
            <>
              <Button onClick={() => setModal('priority')}>改优先级</Button>
              {d.status === DEMAND.PAUSED ? (
                <Button
                  onClick={() => act(() => supplyApi.resume(d.id), '已恢复')}
                >
                  恢复
                </Button>
              ) : (
                <Button onClick={() => setModal('pause')}>暂不覆盖</Button>
              )}
              {d.status === DEMAND.WATCHING && (
                <Button
                  onClick={() =>
                    act(() => supplyApi.join(d.id), '已加入覆盖矩阵')
                  }
                >
                  加入矩阵
                </Button>
              )}
              <Button type="primary" onClick={() => setModal('record')}>
                记处理
              </Button>
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [{ key: 'merge', label: '合并重复' }],
                  onClick: () => setModal('merge'),
                }}
              >
                <Button icon={<MoreOutlined />} aria-label="更多操作" />
              </Dropdown>
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
            <SectionTitle
              extra={
                <span style={{ fontSize: 13, color: palette.sub }}>
                  {d.priorityManual
                    ? `手动调整：${d.priorityReason ?? ''}`
                    : d.priorityRule
                      ? `命中规则：${d.priorityRule}`
                      : ''}
                </span>
              }
            >
              优先级依据
            </SectionTitle>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))',
                gap: 16,
              }}
            >
              <Stat n={d.cntInquiry} label="询盘" />
              <Stat n={d.cntOrder} label="成交" color={palette.green} />
              <Stat n={d.cntStockout} label="询价无货" color={palette.red} />
              <Stat n={d.cntLost} label="未成交" color={palette.orange} />
              <Stat n={d.cntSearch + d.cntResearch} label="搜索与调研" />
            </div>
          </Card>
          <Card style={{ padding: 24 }}>
            <SectionTitle
              extra={
                <Segmented
                  value={filter}
                  onChange={(v) => setFilter(v as typeof filter)}
                  options={[
                    { value: 'all', label: '全部' },
                    { value: 'signal', label: `需求信号 ${signals}` },
                    { value: 'log', label: `处理记录 ${logs}` },
                  ]}
                />
              }
            >
              时间线
            </SectionTitle>
            {rows.length === 0 && (
              <div style={{ color: palette.mute }}>暂无记录</div>
            )}
            <div style={{ display: 'grid', gap: 18 }}>
              {shown.map((t) => (
                <TimelineRow
                  key={`${t.kind}-${t.signal?.id ?? t.logId}`}
                  t={t}
                />
              ))}
            </div>
            {!expand && rows.length > FOLD && (
              <div style={{ textAlign: 'center', marginTop: 12 }}>
                <Button type="link" onClick={() => setExpand(true)}>
                  展开其余 {rows.length - FOLD} 条
                </Button>
              </div>
            )}
          </Card>
        </div>
        <div style={{ display: 'grid', gap: 16, minWidth: 0 }}>
          <Card style={{ padding: 20 }}>
            <SectionTitle
              extra={
                <span
                  style={{
                    display: 'inline-flex',
                    gap: 8,
                    alignItems: 'center',
                  }}
                >
                  <CoverPill status={d.coverageStatus || undefined} />
                  {data.canCoverage && d.status === DEMAND.IN_MATRIX && (
                    <Button
                      type="link"
                      size="small"
                      style={{ padding: 0 }}
                      onClick={() => setModal('coverage')}
                    >
                      改状态
                    </Button>
                  )}
                </span>
              }
            >
              覆盖情况
            </SectionTitle>
            {data.canSeeSupplier ? (
              <div style={{ display: 'grid', gap: 10 }}>
                {channels.length > 0 && (
                  <span
                    style={{
                      color: d.singleChannel ? palette.orange : palette.sub,
                      fontSize: 13,
                    }}
                  >
                    <BranchesOutlined />{' '}
                    {channels.length === 1
                      ? '单一渠道 · 没有备选'
                      : `${channels.length} 个渠道`}
                  </span>
                )}
                {channels.map((c) => (
                  <ChannelCard key={c.supplierId} c={c} />
                ))}
                {data.canCoverage && (
                  <Button
                    block
                    type="dashed"
                    icon={<PlusOutlined />}
                    onClick={() => setModal('channel')}
                  >
                    {channels.length ? '添加备选渠道' : '设置渠道'}
                  </Button>
                )}
              </div>
            ) : (
              <div style={{ color: palette.mute, fontSize: 13 }}>
                {d.channelCount ? `${d.channelCount} 个渠道` : '还没有渠道'}
              </div>
            )}
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 12 }}>
              {d.coveragePending ? '待确认 · ' : ''}
              系统建议：
              <CoverPill status={d.suggestedCoverage || undefined} />（
              {ev ? (
                <>
                  依据：{dayjs(ev.date).format('YYYY-MM-DD')} 的
                  {ev.type === 2 ? '采购记录' : '询价记录'}
                  {evUrl && (
                    <>
                      {' '}
                      <Link to={evUrl}>{ev.docNo}</Link>
                    </>
                  )}
                  {d.evidenceValid ? '，在有效期内' : '，已过有效期'}
                </>
              ) : (
                (d.coverageBasis ?? '没有价格记录')
              )}
              ）
            </div>
          </Card>
          <Card style={{ padding: 20 }}>
            <SectionTitle>责任人与下一步</SectionTitle>
            <div style={{ display: 'grid', gap: 12 }}>
              {(
                [
                  ['负责人', d.ownerName ?? '未指定'],
                  [
                    '下一步行动',
                    d.nextAction
                      ? `${d.nextAction}${d.nextActionSource === 2 ? '（系统）' : ''}`
                      : '—',
                  ],
                  [
                    '行动日期',
                    d.nextActionDate
                      ? dayjs(d.nextActionDate).format('YYYY-MM-DD')
                      : '—',
                  ],
                  ['所属专项', projectLink],
                ] as [string, React.ReactNode][]
              ).map(([k, v]) => (
                <div
                  key={k}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    gap: 12,
                  }}
                >
                  <span style={{ color: palette.sub }}>{k}</span>
                  <span style={{ textAlign: 'right' }}>{v}</span>
                </div>
              ))}
              {data.canEdit && (
                <Button
                  type="link"
                  style={{ padding: 0, justifySelf: 'start' }}
                  onClick={() => setModal('next')}
                >
                  修改
                </Button>
              )}
            </div>
          </Card>
          <DocLogCard logs={data.logs} />
        </div>
      </div>
      <PriorityModal
        demand={modal === 'priority' ? d : undefined}
        onClose={() => setModal(undefined)}
        onDone={done}
      />
      <NextModal
        demand={modal === 'next' ? d : undefined}
        onClose={() => setModal(undefined)}
        onDone={done}
      />
      <CoverageModal
        demand={modal === 'coverage' ? d : undefined}
        onClose={() => setModal(undefined)}
        onDone={done}
      />
      <RecordModal
        open={modal === 'record'}
        title={[d.brandName, d.categoryName].filter(Boolean).join(' · ')}
        demands={[d]}
        onClose={() => setModal(undefined)}
        onDone={done}
      />
      <MergeModal
        demand={modal === 'merge' ? d : undefined}
        onClose={() => setModal(undefined)}
      />
      <ChannelModal
        detail={modal === 'channel' ? data : undefined}
        onClose={() => setModal(undefined)}
        onDone={done}
      />
      <ReasonModal
        open={modal === 'pause'}
        title="暂不覆盖"
        description="暂不覆盖后不再出现在供应覆盖和今日待办里，随时可以恢复。"
        placeholder="原因，如：已停产、利润太低"
        okText="暂不覆盖"
        onCancel={() => setModal(undefined)}
        onOk={async (reason) => {
          try {
            await supplyApi.pause(d.id, reason);
            message.success('已暂不覆盖');
            done();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default DemandDetailPage;
