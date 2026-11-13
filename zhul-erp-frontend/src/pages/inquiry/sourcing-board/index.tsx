import {
  AlertOutlined,
  ApartmentOutlined,
  ClockCircleOutlined,
  CloseOutlined,
  DownloadOutlined,
  EllipsisOutlined,
  InboxOutlined,
  OrderedListOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  UploadOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import {
  App,
  Button,
  Checkbox,
  Dropdown,
  Input,
  Modal,
  Segmented,
  Select,
  Skeleton,
  Tooltip,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { usePageSize } from '@/utils/pageSize';
import {
  BrandGroupHeader,
  FoldToggle,
  groupByBrand,
  TaskPager,
  useFold,
} from '../shared/BrandGroups';
import {
  Card,
  CustomerBrief,
  LevelPill,
  PageTitle,
  Pill,
  Stat,
  TaskStatusPill,
  useWide,
} from '../shared/components';
import { formatMinutes, PATHS } from '../shared/constants';
import ImportModal from '../shared/ImportModal';
import {
  type Board,
  type BoardTask,
  boardApi,
  type Purchaser,
  type RulePreview,
  readBizError,
} from '../shared/service';
import AllTasksTable from './AllTasksTable';
import DeferModal, { type DeferTarget } from './DeferModal';
import DeferredList from './DeferredList';
import ReviewDrawer from './ReviewDrawer';
import TaskDetailDrawer from './TaskDetailDrawer';

const UNASSIGNED = 1;
const SOURCING = 2;
/** 已回价、业务员还没报价：可以看修改记录、调整采购成本价 */
const DONE = 3;
/** 待审核：任务下有兼职回价等采购负责人审核（不是任务状态，与后端 BOARD_TAB_REVIEW 一致） */
const REVIEW = 5;
/** 已延后：待分配页签里的切换，与后端 BOARD_TAB_DEFERRED 一致 */
const DEFERRED = 8;
/** 全部：全部询价任务（含已报价、已关闭），只在前端使用，走 /all 接口 */
const ALL = 9;

const TAB_HINT: Record<number, string> = {
  [REVIEW]:
    '「待审核」：兼职采购提交的回价，审核通过后业务员才能看到；正式采购的回价不经过这里',
  [DONE]:
    '「已回价」：已回齐、业务员还没报价的任务，可看回价、看修改记录、定成本价',
};

/** 询价分配：采购负责人把待分配的询价任务交给合适的采购 */
const SourcingBoardPage: React.FC = () => {
  const wide = useWide();
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const canAssign = !!access['inquiry:task:assign'];
  const canProxy = !!access['inquiry:import:proxy'];
  const [tab, setTab] = useState(UNASSIGNED);
  const [board, setBoard] = useState<Board | null>(null);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const [dragOver, setDragOver] = useState<number>();
  const [preview, setPreview] = useState<RulePreview[] | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [detailId, setDetailId] = useState<number>();
  const [reviewId, setReviewId] = useState<number>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [purchaserId, setPurchaserId] = useState<number>();
  const [deferredView, setDeferredView] = useState(false);
  const [deferTarget, setDeferTarget] = useState<DeferTarget>();
  const fold = useFold();
  const showDeferred = tab === UNASSIGNED && deferredView;

  const load = useCallback(async () => {
    setError('');
    try {
      // 「全部」页签只用这次请求取统计、页签数字与采购名单，表格由 AllTasksTable 自己查
      const all = tab === ALL;
      const b = await boardApi.board(
        all ? UNASSIGNED : showDeferred ? DEFERRED : tab,
        all ? 1 : page,
        all ? 1 : pageSize,
        { keyword: search || undefined, purchaserId },
      );
      // 分配后最后一页可能空了，退回到还有任务的最后一页
      if (b.tasks.length === 0 && page > 1 && b.total > 0) {
        setPage(Math.ceil(b.total / pageSize));
        return;
      }
      setBoard(b);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [tab, page, pageSize, search, purchaserId, showDeferred]);

  useEffect(() => {
    setBoard(null);
  }, [tab, showDeferred]);

  // 搜索条件变化时回到第一页
  useEffect(() => setPage(1), [search, purchaserId]);

  useEffect(() => {
    setSelected([]);
    load();
  }, [load]);

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try {
      await fn();
      message.success(ok);
      setSelected([]);
      await load();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const groups = useMemo(
    () => (tab === ALL || showDeferred ? [] : groupByBrand(board?.tasks ?? [])),
    [board, tab, showDeferred],
  );
  const brandSummary = useMemo(
    () => new Map((board?.brands ?? []).map((b) => [b.brand, b])),
    [board],
  );

  const purchaserMenu = (
    onPick: (p: Purchaser) => void,
    exclude: number[] = [],
  ) => ({
    items: (board?.purchasers ?? [])
      .filter((p) => !exclude.includes(p.id))
      .map((p) => ({
        key: String(p.id),
        label: (
          <span
            style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}
          >
            {p.name}
            {p.partTime && <Pill tone="violet">兼职</Pill>}
            <span style={{ color: palette.mute, fontSize: 12 }}>
              进行中 {p.activeTasks}
            </span>
          </span>
        ),
      })),
    onClick: ({ key }: { key: string }) => {
      const p = board?.purchasers.find((x) => x.id === Number(key));
      if (p) onPick(p);
    },
  });

  const closeDefer = useCallback(() => setDeferTarget(undefined), []);

  const openRulePreview = async () => {
    setBusy(true);
    try {
      setPreview(await boardApi.rulePreview(selected));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  if (error) return <ErrorHint message={error} onRetry={load} />;

  const s = board?.stats;
  return (
    <div>
      <PageTitle
        description="把待分配的询价任务交给合适的采购；推荐依据近 180 天的品牌熟悉度和当前负载。"
        actions={
          <>
            <Button
              icon={<SettingOutlined />}
              onClick={() => history.push(PATHS.rules)}
            >
              分配规则
            </Button>
            {canProxy && (
              <Button
                icon={<UploadOutlined />}
                onClick={() => setImportOpen(true)}
              >
                导入询价结果
              </Button>
            )}
          </>
        }
      />
      {!s ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
              gap: 16,
              marginBottom: 16,
            }}
          >
            <Stat
              icon={<InboxOutlined />}
              color={palette.orange}
              soft={palette.orangeSoft}
              label="待分配"
              value={s.unassigned}
              hint={
                <span
                  style={{
                    color:
                      s.longestWaitingMinutes > 120
                        ? palette.orange
                        : palette.mute,
                  }}
                >
                  {s.unassigned
                    ? `最久已等 ${formatMinutes(s.longestWaitingMinutes)}`
                    : '都分配完了'}
                  {s.deferred ? ` · 另有 ${s.deferred} 个已延后` : ''}
                </span>
              }
            />
            <Stat
              icon={<ApartmentOutlined />}
              color={palette.link}
              soft={palette.accentSoft}
              label="询价中"
              value={s.sourcing}
              hint={
                <span style={{ color: palette.mute }}>
                  {s.multiAssigned
                    ? `其中 ${s.multiAssigned} 个多人比价`
                    : '采购正在问价'}
                </span>
              }
            />
            <Stat
              icon={<AlertOutlined />}
              color={palette.red}
              soft={palette.redSoft}
              label="超时"
              value={s.timeout}
              hint={
                <span style={{ color: palette.red }}>
                  紧急 {s.urgentTimeoutHours} 小时 · 普通 {s.timeoutHours} 小时
                </span>
              }
            />
            <Stat
              icon={<SafetyCertificateOutlined />}
              color={palette.link}
              soft={palette.accentSoft}
              label="待审核"
              value={s.pendingReview ?? 0}
              hint={
                <span style={{ color: palette.mute }}>
                  {s.pendingReview
                    ? `兼职回价 ${s.pendingReviewItems ?? 0} 个型号 · 最久已等 ${formatMinutes(s.longestReviewMinutes)}`
                    : '兼职提交的回价审核后才给业务员看'}
                </span>
              }
            />
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: wide
                ? 'minmax(0, 1fr) 360px'
                : 'minmax(0, 1fr)',
              gap: 20,
              alignItems: 'start',
            }}
          >
            <div style={{ display: 'grid', gap: 12 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  flexWrap: 'wrap',
                }}
              >
                <Input.Search
                  allowClear
                  placeholder="搜任务编号、询盘单号、品牌、型号"
                  style={{ width: 320 }}
                  value={keyword}
                  onChange={(e) => {
                    setKeyword(e.target.value);
                    if (!e.target.value) setSearch('');
                  }}
                  onSearch={(v) => setSearch(v.trim())}
                  aria-label="搜索询价任务"
                />
                <Select
                  allowClear
                  showSearch={{ optionFilterProp: 'label' }}
                  placeholder="采购员：全部"
                  style={{ width: 180 }}
                  value={purchaserId}
                  onChange={setPurchaserId}
                  options={(board?.purchasers ?? []).map((p) => ({
                    value: p.id,
                    label: p.name,
                  }))}
                  aria-label="按采购员筛选"
                />
                {(search || purchaserId) && (
                  <span style={{ color: palette.mute, fontSize: 12 }}>
                    页签数字按搜索条件统计；顶部卡片仍是全部任务
                  </span>
                )}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <Segmented
                  value={tab}
                  onChange={(v) => {
                    setTab(v as number);
                    setDeferredView(false);
                    setPage(1);
                  }}
                  options={[
                    {
                      value: UNASSIGNED,
                      label: `待分配 ${board.tabs?.unassigned ?? s.unassigned}`,
                    },
                    {
                      value: SOURCING,
                      label: `询价中 ${board.tabs?.sourcing ?? s.sourcing}`,
                    },
                    {
                      value: REVIEW,
                      label: `待审核 ${board.tabs?.review ?? s.pendingReview ?? 0}`,
                    },
                    {
                      value: DONE,
                      label: `已回价 ${board.tabs?.done ?? s.done ?? 0}`,
                    },
                    { value: ALL, label: '全部' },
                  ]}
                />
                {tab === UNASSIGNED && (
                  <Button
                    size="small"
                    type={showDeferred ? 'primary' : 'default'}
                    ghost={showDeferred}
                    icon={<ClockCircleOutlined />}
                    onClick={() => {
                      setDeferredView(!showDeferred);
                      setPage(1);
                    }}
                    aria-pressed={showDeferred}
                  >
                    已延后 {board.tabs?.deferred ?? s.deferred ?? 0}
                    {showDeferred && <CloseOutlined style={{ fontSize: 10 }} />}
                  </Button>
                )}
                {TAB_HINT[tab] && (
                  <span
                    style={{
                      color: palette.mute,
                      fontSize: 12,
                      flex: 1,
                      minWidth: 0,
                    }}
                  >
                    {TAB_HINT[tab]}
                  </span>
                )}
                <span style={{ marginLeft: 'auto' }} />
                {tab !== REVIEW && tab !== ALL && !showDeferred && (
                  <FoldToggle mode={fold.mode} onChange={fold.setAll} />
                )}
                <span
                  style={{
                    color: palette.mute,
                    fontSize: 12,
                    whiteSpace: 'nowrap',
                  }}
                >
                  自动分配
                  {s.autoAssign ? '已开启，新任务会按规则自动分配' : '未开启'}
                </span>
              </div>

              {tab === UNASSIGNED &&
                !showDeferred &&
                canAssign &&
                selected.length > 0 && (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '12px 16px',
                      borderRadius: 12,
                      background: palette.accentSoft,
                      border: `1px solid ${palette.accentLine}`,
                    }}
                  >
                    <span style={{ color: palette.ink, fontWeight: 600 }}>
                      已选 {selected.length} 个任务
                    </span>
                    <button
                      type="button"
                      className="zhul-link"
                      onClick={() => setSelected([])}
                    >
                      取消选择
                    </button>
                    <span
                      style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}
                    >
                      <Button
                        type="primary"
                        size="small"
                        icon={<ThunderboltOutlined />}
                        loading={busy}
                        onClick={() =>
                          run(
                            () => boardApi.assignRecommended(selected),
                            `已按推荐分配 ${selected.length} 个任务`,
                          )
                        }
                      >
                        按推荐分配
                      </Button>
                      <Dropdown
                        menu={purchaserMenu((p) =>
                          run(
                            () => boardApi.assign(selected, p.id),
                            `已分配给 ${p.name}`,
                          ),
                        )}
                        trigger={['click']}
                      >
                        <Button size="small" icon={<UserAddOutlined />}>
                          分配给…
                        </Button>
                      </Dropdown>
                      <Button
                        size="small"
                        icon={<OrderedListOutlined />}
                        onClick={openRulePreview}
                        loading={busy}
                      >
                        按规则分配
                      </Button>
                      <Button
                        size="small"
                        icon={<ClockCircleOutlined />}
                        onClick={() =>
                          setDeferTarget({ kind: 'tasks', taskIds: selected })
                        }
                      >
                        延后
                      </Button>
                      {canProxy && (
                        <Button
                          size="small"
                          icon={<DownloadOutlined />}
                          onClick={() =>
                            boardApi
                              .downloadPackage(selected)
                              .catch((e) =>
                                message.error(readBizError(e).message),
                              )
                          }
                        >
                          下载询价包
                        </Button>
                      )}
                    </span>
                  </div>
                )}

              {tab === REVIEW && (
                <ReviewList
                  tasks={board.tasks}
                  onOpen={setReviewId}
                  onOpenTask={setDetailId}
                />
              )}
              {tab === ALL && (
                <AllTasksTable
                  keyword={search}
                  purchaserId={purchaserId}
                  onOpen={setDetailId}
                />
              )}
              {showDeferred && (
                <DeferredList
                  groups={board.deferredGroups ?? []}
                  canAssign={canAssign}
                  busy={busy}
                  onOpenTask={setDetailId}
                  onOpenInquiry={(id) =>
                    history.push(`${PATHS.inquiries}/${id}`)
                  }
                  onChangeDate={(g) =>
                    setDeferTarget({
                      kind: 'tasks',
                      taskIds: g.taskIds,
                      current: g.deferUntil,
                    })
                  }
                  onNow={(g) =>
                    run(
                      () => boardApi.undefer(g.taskIds),
                      `${g.taskCount} 个任务已回到待分配`,
                    )
                  }
                />
              )}
              {tab !== REVIEW &&
                tab !== ALL &&
                !showDeferred &&
                groups.length === 0 && (
                  <Card>
                    <EmptyHint
                      title={
                        tab === UNASSIGNED
                          ? '没有待分配的任务'
                          : '没有询价中的任务'
                      }
                      description={
                        tab === UNASSIGNED
                          ? '业务员确认型号后，待询价的型号会按品牌拆成任务出现在这里。'
                          : '分配出去的任务会出现在这里，可以改派或追加比价。'
                      }
                    />
                  </Card>
                )}
              {tab !== REVIEW &&
                groups.map(([brand, tasks]) => (
                  <Card key={brand} style={{ padding: 0, overflow: 'hidden' }}>
                    <BrandGroupHeader
                      brand={brand}
                      count={tasks.length}
                      summary={brandSummary.get(brand)}
                      folded={fold.isFolded(brand)}
                      onToggle={() => fold.toggle(brand)}
                      checkbox={
                        tab === UNASSIGNED && canAssign
                          ? {
                              checked: tasks.every((t) =>
                                selected.includes(t.id),
                              ),
                              indeterminate:
                                tasks.some((t) => selected.includes(t.id)) &&
                                !tasks.every((t) => selected.includes(t.id)),
                              onChange: (checked) =>
                                setSelected((prev) =>
                                  checked
                                    ? [
                                        ...new Set([
                                          ...prev,
                                          ...tasks.map((t) => t.id),
                                        ]),
                                      ]
                                    : prev.filter(
                                        (id) => !tasks.some((t) => t.id === id),
                                      ),
                                ),
                            }
                          : undefined
                      }
                      extra={
                        tab === UNASSIGNED &&
                        brandSummary.get(brand)?.minutes != null ? (
                          <span
                            style={{
                              color:
                                (brandSummary.get(brand)?.minutes ?? 0) > 120
                                  ? palette.orange
                                  : palette.mute,
                            }}
                          >
                            最久已等{' '}
                            {formatMinutes(brandSummary.get(brand)?.minutes)}
                          </span>
                        ) : undefined
                      }
                    />
                    {!fold.isFolded(brand) &&
                      tasks.map((t) => (
                        <div
                          key={t.id}
                          draggable={tab === UNASSIGNED && canAssign}
                          onDragStart={(e) =>
                            e.dataTransfer.setData('text/plain', String(t.id))
                          }
                          style={{
                            display: 'grid',
                            gridTemplateColumns:
                              tab === UNASSIGNED
                                ? '24px 260px 120px minmax(0, 1fr) auto'
                                : '260px 140px minmax(0, 1fr) auto',
                            gap: 14,
                            alignItems: 'center',
                            padding: '14px 20px',
                            borderTop: `1px solid ${palette.hairline}`,
                            background: selected.includes(t.id)
                              ? palette.hover
                              : 'transparent',
                            cursor:
                              tab === UNASSIGNED && canAssign
                                ? 'grab'
                                : 'default',
                          }}
                        >
                          {tab === UNASSIGNED && (
                            <Checkbox
                              disabled={!canAssign}
                              checked={selected.includes(t.id)}
                              onChange={(e) =>
                                setSelected((prev) =>
                                  e.target.checked
                                    ? [...prev, t.id]
                                    : prev.filter((x) => x !== t.id),
                                )
                              }
                            />
                          )}
                          <div style={{ display: 'grid', gap: 4 }}>
                            <span
                              style={{
                                display: 'inline-flex',
                                gap: 6,
                                alignItems: 'center',
                                flexWrap: 'wrap',
                              }}
                            >
                              <button
                                type="button"
                                className="zhul-link"
                                onClick={() => setDetailId(t.id)}
                              >
                                {t.taskCode}
                              </button>
                              <LevelPill value={t.level} />
                              {t.urgent && <Pill tone="red">紧急</Pill>}
                              {t.returnReason > 0 &&
                                t.status === UNASSIGNED && (
                                  <Pill tone="orange">被退回</Pill>
                                )}
                              {t.selfSourcedBy && (
                                <Pill tone="violet">
                                  业务员自询 · {t.selfSourcedBy}
                                </Pill>
                              )}
                            </span>
                            <span style={{ color: palette.mute, fontSize: 12 }}>
                              {t.brand}
                              {t.category ? ` · ${t.category}` : ''} ·{' '}
                              {t.itemCount} 个型号
                            </span>
                            <span
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 4,
                                color: palette.sub,
                                fontSize: 12,
                              }}
                            >
                              <UserOutlined style={{ color: palette.mute }} />
                              业务员 {t.salesName || '—'}
                            </span>
                            <CustomerBrief
                              customerType={t.customerType}
                              customerName={t.customerName}
                            />
                            {t.inquiryRemark && (
                              <Tooltip title={t.inquiryRemark}>
                                <span
                                  style={{
                                    color: palette.sub,
                                    fontSize: 12,
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                  }}
                                >
                                  备注：{t.inquiryRemark}
                                </span>
                              </Tooltip>
                            )}
                          </div>
                          {tab === UNASSIGNED ? (
                            <div style={{ display: 'grid', gap: 2 }}>
                              <span
                                style={{ color: palette.mute, fontSize: 12 }}
                              >
                                已等待
                              </span>
                              <span
                                style={{
                                  fontWeight: 600,
                                  color:
                                    (t.waitingMinutes ?? 0) > 120
                                      ? palette.orange
                                      : palette.sub,
                                }}
                              >
                                {formatMinutes(t.waitingMinutes)}
                              </span>
                            </div>
                          ) : (
                            <div style={{ display: 'grid', gap: 4 }}>
                              <TaskStatusPill
                                status={t.status}
                                timeout={t.timeout}
                              />
                              <span
                                style={{ color: palette.mute, fontSize: 12 }}
                              >
                                已回价 {t.pricedCount}/{t.itemCount}
                              </span>
                            </div>
                          )}
                          <div style={{ display: 'grid', gap: 2, minWidth: 0 }}>
                            {tab === UNASSIGNED ? (
                              <>
                                <span
                                  style={{
                                    color: palette.ink,
                                    fontWeight: 600,
                                  }}
                                >
                                  <ThunderboltOutlined
                                    style={{ color: palette.link }}
                                  />{' '}
                                  推荐 {t.recommendedName ?? '—'}
                                </span>
                                <span
                                  style={{
                                    color: t.returnReason
                                      ? palette.orange
                                      : palette.mute,
                                    fontSize: 12,
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                  }}
                                >
                                  {t.returnReason
                                    ? `${t.returnedByName ?? ''}退回：${t.returnReasonLabel}${t.returnNote ? `，${t.returnNote}` : ''}`
                                    : t.recommendReason}
                                </span>
                              </>
                            ) : (
                              <span style={{ color: palette.sub }}>
                                {t.assignees.map((a) => a.name).join(' + ') ||
                                  '—'}
                                {t.assignees.length > 1 && (
                                  <span style={{ color: palette.mute }}>
                                    （比价）
                                  </span>
                                )}
                              </span>
                            )}
                          </div>
                          {tab === DONE ? (
                            <Button
                              size="small"
                              onClick={() => setDetailId(t.id)}
                            >
                              看回价 / 定成本价
                            </Button>
                          ) : (
                            canAssign && (
                              <span style={{ display: 'inline-flex', gap: 6 }}>
                                {tab === UNASSIGNED ? (
                                  <>
                                    {t.recommendedId && (
                                      <Button
                                        size="small"
                                        loading={busy}
                                        onClick={() =>
                                          run(
                                            () =>
                                              boardApi.assign(
                                                [t.id],
                                                t.recommendedId as number,
                                              ),
                                            `已分配给 ${t.recommendedName}`,
                                          )
                                        }
                                      >
                                        分配给 {t.recommendedName}
                                      </Button>
                                    )}
                                    <Dropdown
                                      menu={purchaserMenu((p) =>
                                        run(
                                          () => boardApi.assign([t.id], p.id),
                                          `已分配给 ${p.name}`,
                                        ),
                                      )}
                                      trigger={['click']}
                                    >
                                      <Button size="small">其他人…</Button>
                                    </Dropdown>
                                    <Dropdown
                                      trigger={['click']}
                                      menu={{
                                        items: [
                                          {
                                            key: 'task',
                                            icon: <ClockCircleOutlined />,
                                            label: '延后这个任务',
                                          },
                                          {
                                            key: 'inquiry',
                                            icon: <ClockCircleOutlined />,
                                            label: '延后整张询盘',
                                          },
                                        ],
                                        onClick: ({ key }) =>
                                          setDeferTarget(
                                            key === 'task'
                                              ? {
                                                  kind: 'tasks',
                                                  taskIds: [t.id],
                                                }
                                              : {
                                                  kind: 'inquiry',
                                                  customerInquiryId:
                                                    t.customerInquiryId,
                                                },
                                          ),
                                      }}
                                    >
                                      <Button
                                        size="small"
                                        icon={<EllipsisOutlined />}
                                        aria-label="更多操作"
                                      />
                                    </Dropdown>
                                  </>
                                ) : (
                                  <>
                                    <Dropdown
                                      menu={purchaserMenu(
                                        (p) =>
                                          modal.confirm({
                                            title: `改派给 ${p.name}？`,
                                            content:
                                              '原来的采购将看不到这个任务，他已录入的价格会保留。',
                                            okText: '改派',
                                            cancelText: '取消',
                                            onOk: () =>
                                              run(
                                                () =>
                                                  boardApi.reassign(t.id, p.id),
                                                `已改派给 ${p.name}`,
                                              ),
                                          }),
                                        t.assignees.map((a) => a.id),
                                      )}
                                      trigger={['click']}
                                    >
                                      <Button size="small">改派</Button>
                                    </Dropdown>
                                    <Dropdown
                                      menu={purchaserMenu(
                                        (p) =>
                                          run(
                                            () =>
                                              boardApi.addAssignee(t.id, p.id),
                                            `已追加 ${p.name} 比价`,
                                          ),
                                        t.assignees.map((a) => a.id),
                                      )}
                                      trigger={['click']}
                                    >
                                      <Button size="small">追加比价</Button>
                                    </Dropdown>
                                  </>
                                )}
                              </span>
                            )
                          )}
                        </div>
                      ))}
                  </Card>
                ))}
              {tab !== ALL && !showDeferred && (
                <TaskPager
                  total={board.total}
                  page={page}
                  pageSize={pageSize}
                  onChange={(p, size) => {
                    setPage(size === pageSize ? p : 1);
                    setPageSize(size);
                  }}
                />
              )}
            </div>

            <div style={{ display: 'grid', gap: 12 }}>
              <Card
                title="采购负载"
                extra={
                  canAssign && tab === UNASSIGNED ? (
                    <span style={{ color: palette.mute, fontSize: 12 }}>
                      把任务拖到某人身上即可分配
                    </span>
                  ) : undefined
                }
              >
                {board.purchasers.length === 0 ? (
                  <div style={{ color: palette.mute }}>
                    还没有采购人员：给账号分配带「我的询价」菜单的角色（兼职采购用内置角色「兼职采购」）。
                  </div>
                ) : (
                  <div style={{ display: 'grid', gap: 8 }}>
                    {board.purchasers.map((p) => (
                      <div
                        key={p.id}
                        onDragOver={(e) => {
                          if (!canAssign || tab !== UNASSIGNED) return;
                          e.preventDefault();
                          setDragOver(p.id);
                        }}
                        onDragLeave={() => setDragOver(undefined)}
                        onDrop={(e) => {
                          e.preventDefault();
                          setDragOver(undefined);
                          const id = Number(
                            e.dataTransfer.getData('text/plain'),
                          );
                          if (id)
                            run(
                              () => boardApi.assign([id], p.id),
                              `已分配给 ${p.name}`,
                            );
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 10,
                          padding: 10,
                          borderRadius: 12,
                          background:
                            dragOver === p.id
                              ? palette.accentSoft
                              : palette.inset,
                          border: `1px ${dragOver === p.id ? 'dashed' : 'solid'} ${dragOver === p.id ? palette.link : 'transparent'}`,
                          transition: 'background 150ms ease-out',
                        }}
                      >
                        <span
                          style={{
                            width: 30,
                            height: 30,
                            borderRadius: 15,
                            background: p.partTime
                              ? palette.violetSoft
                              : palette.accentSoft,
                            color: p.partTime ? palette.violet : palette.link,
                            fontWeight: 700,
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          {p.name.slice(0, 1)}
                        </span>
                        <span style={{ display: 'grid', gap: 4 }}>
                          <span
                            style={{
                              display: 'inline-flex',
                              gap: 6,
                              alignItems: 'center',
                              color: palette.ink,
                              fontWeight: 600,
                            }}
                          >
                            {p.name}
                            {p.partTime && <Pill tone="violet">兼职</Pill>}
                          </span>
                          <span
                            style={{
                              width: 90,
                              height: 5,
                              borderRadius: 3,
                              background: palette.hairline,
                              overflow: 'hidden',
                            }}
                          >
                            <span
                              style={{
                                display: 'block',
                                height: '100%',
                                width: `${Math.min(100, p.activeTasks * 10)}%`,
                                background:
                                  p.activeTasks >= 8
                                    ? palette.orange
                                    : palette.link,
                              }}
                            />
                          </span>
                        </span>
                        <span
                          style={{
                            marginLeft: 'auto',
                            color: palette.sub,
                            fontSize: 12,
                          }}
                        >
                          进行中 {p.activeTasks}
                        </span>
                        {p.timeoutTasks > 0 && (
                          <Pill tone="red">超时 {p.timeoutTasks}</Pill>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </Card>
              <Card title="多人比价">
                <div
                  style={{ color: palette.mute, fontSize: 12, lineHeight: 1.6 }}
                >
                  在「询价中」对任务点「追加比价」，可以再分给一人同时询价，两人的价格都进询价记录，报价时取最优。
                </div>
              </Card>
            </div>
          </div>
        </>
      )}

      <Modal
        open={!!preview}
        title="按规则分配 · 预览"
        okText={`确认分配 ${preview?.filter((p) => p.assigneeId).length ?? 0} 个任务`}
        cancelText="取消"
        confirmLoading={busy}
        onCancel={() => setPreview(null)}
        onOk={async () => {
          await run(() => boardApi.assignByRule(selected), '已按规则分配');
          setPreview(null);
        }}
        width={640}
      >
        <div style={{ display: 'grid', gap: 8 }}>
          {(preview ?? []).map((p) => (
            <div
              key={p.taskId}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: 12,
                borderRadius: 10,
                background: palette.inset,
              }}
            >
              <span
                style={{ color: palette.link, fontWeight: 600, width: 150 }}
              >
                {p.taskCode}
              </span>
              <span style={{ width: 170 }}>
                {p.brand}
                {p.category ? ` · ${p.category}` : ''}
              </span>
              <span style={{ color: palette.ink, fontWeight: 600 }}>
                → {p.assigneeName ?? '无法分配'}
              </span>
              <span
                style={{
                  marginLeft: 'auto',
                  color: palette.mute,
                  fontSize: 12,
                }}
              >
                {p.basis}
              </span>
            </div>
          ))}
        </div>
      </Modal>

      <DeferModal
        target={deferTarget}
        onClose={closeDefer}
        onDone={() => {
          setDeferTarget(undefined);
          setSelected([]);
          load();
        }}
      />
      <TaskDetailDrawer
        taskId={detailId}
        onClose={() => setDetailId(undefined)}
      />
      <ReviewDrawer
        taskId={reviewId}
        onClose={() => setReviewId(undefined)}
        onChanged={load}
      />

      <ImportModal
        open={importOpen}
        title="导入询价结果（代采购导入）"
        preview={boardApi.importPreview}
        confirm={boardApi.importConfirm}
        onClose={() => setImportOpen(false)}
        onDone={() => {
          setImportOpen(false);
          load();
        }}
      />
    </div>
  );
};

/** 待审核页签：不按品牌分组，按紧急、询盘等级、最早提交排好序（后端已排序） */
const ReviewList: React.FC<{
  tasks: BoardTask[];
  onOpen: (id: number) => void;
  onOpenTask: (id: number) => void;
}> = ({ tasks, onOpen, onOpenTask }) => {
  const { palette } = useAppTheme();
  if (tasks.length === 0) {
    return (
      <Card>
        <EmptyHint
          title="没有待审核的兼职回价"
          description="兼职采购提交的回价会出现在这里，审核后业务员才能看到。"
        />
      </Card>
    );
  }
  const items = tasks.reduce((n, t) => n + (t.reviewItemCount ?? 0), 0);
  return (
    <Card style={{ padding: 0, overflow: 'hidden' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '14px 20px',
          background: palette.inset,
        }}
      >
        <span style={{ fontSize: 15, fontWeight: 700, color: palette.ink }}>
          兼职回价待审核
        </span>
        <Pill tone="gray">
          {tasks.length} 个任务 · {items} 个型号
        </Pill>
        <span style={{ marginLeft: 'auto', color: palette.mute, fontSize: 12 }}>
          按紧急、询盘等级、最早提交排序
        </span>
      </div>
      {tasks.map((t) => (
        <div
          key={t.id}
          style={{
            display: 'grid',
            gridTemplateColumns: '280px 200px minmax(0, 1fr) auto',
            gap: 14,
            alignItems: 'center',
            padding: '16px 20px',
            borderTop: `1px solid ${palette.hairline}`,
          }}
        >
          <div style={{ display: 'grid', gap: 4 }}>
            <span
              style={{
                display: 'inline-flex',
                gap: 6,
                alignItems: 'center',
                flexWrap: 'wrap',
              }}
            >
              <button
                type="button"
                className="zhul-link"
                onClick={() => onOpenTask(t.id)}
              >
                {t.taskCode}
              </button>
              <LevelPill value={t.level} />
              {t.urgent && <Pill tone="red">紧急</Pill>}
            </span>
            <span style={{ color: palette.mute, fontSize: 12 }}>
              {t.brand}
              {t.category ? ` · ${t.category}` : ''} · {t.itemCount} 个型号
            </span>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                color: palette.sub,
                fontSize: 12,
              }}
            >
              <UserOutlined style={{ color: palette.mute }} />
              {(t.reviewBuyerNames ?? []).join('、') || '—'}
              <Pill tone="violet">兼职</Pill>
            </span>
          </div>
          <div style={{ display: 'grid', gap: 2 }}>
            <span style={{ color: palette.mute, fontSize: 12 }}>
              待审核型号
            </span>
            <b style={{ color: palette.ink, fontSize: 15 }}>
              {t.reviewItemCount ?? 0} / {t.itemCount}
            </b>
            <span style={{ color: palette.mute, fontSize: 12 }}>
              业务员已可见 {t.pricedCount} 个
            </span>
          </div>
          <div style={{ display: 'grid', gap: 2 }}>
            <span style={{ color: palette.mute, fontSize: 12 }}>
              {t.reviewSubmittedAt
                ? `${dayjs(t.reviewSubmittedAt).format('MM-DD HH:mm')} 提交`
                : '—'}
            </span>
            <span
              style={{
                fontWeight: 600,
                color:
                  (t.reviewWaitingMinutes ?? 0) > 120
                    ? palette.orange
                    : palette.sub,
              }}
            >
              已等 {formatMinutes(t.reviewWaitingMinutes)}
            </span>
          </div>
          <Button
            type="primary"
            ghost
            icon={<SafetyCertificateOutlined />}
            onClick={() => onOpen(t.id)}
          >
            去审核
          </Button>
        </div>
      ))}
    </Card>
  );
};

export default SourcingBoardPage;
