import {
  AlertOutlined,
  ApartmentOutlined,
  DownloadOutlined,
  InboxOutlined,
  OrderedListOutlined,
  RollbackOutlined,
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
  Modal,
  Segmented,
  Skeleton,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
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
import TaskDetailDrawer from './TaskDetailDrawer';

const UNASSIGNED = 1;
const SOURCING = 2;
/** 已回价、业务员还没报价：可以看修改记录、调整采购成本价 */
const DONE = 3;

/** 分配工作台：采购负责人把待分配的询价任务交给合适的采购 */
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

  const load = useCallback(async () => {
    setError('');
    try {
      setBoard(await boardApi.board(tab));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [tab]);

  useEffect(() => {
    setBoard(null);
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

  const groups = useMemo(() => {
    const map = new Map<string, BoardTask[]>();
    for (const t of board?.tasks ?? []) {
      const list = map.get(t.brand) ?? [];
      list.push(t);
      map.set(t.brand, list);
    }
    return [...map.entries()];
  }, [board]);

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
        crumbs={['分配工作台']}
        title="分配工作台"
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
              icon={<RollbackOutlined />}
              color={palette.orange}
              soft={palette.orangeSoft}
              label="被退回"
              value={s.returned}
              hint={
                <span style={{ color: palette.mute }}>
                  {s.returnedForDoubt
                    ? `型号存疑 ${s.returnedForDoubt}`
                    : '退回的任务会回到待分配池'}
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
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <Segmented
                  value={tab}
                  onChange={(v) => setTab(v as number)}
                  options={[
                    { value: UNASSIGNED, label: `待分配 ${s.unassigned}` },
                    { value: SOURCING, label: `询价中 ${s.sourcing}` },
                    { value: DONE, label: `已回价 ${s.done ?? 0}` },
                  ]}
                />
                <span
                  style={{
                    marginLeft: 'auto',
                    color: palette.mute,
                    fontSize: 12,
                  }}
                >
                  自动分配
                  {s.autoAssign ? '已开启，新任务会按规则自动分配' : '未开启'}
                </span>
              </div>

              {tab === UNASSIGNED && canAssign && selected.length > 0 && (
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
                  <a onClick={() => setSelected([])}>取消选择</a>
                  <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
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

              {groups.length === 0 && (
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
              {groups.map(([brand, tasks]) => (
                <Card key={brand} style={{ padding: 0, overflow: 'hidden' }}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '14px 20px',
                      background: palette.inset,
                    }}
                  >
                    {tab === UNASSIGNED && canAssign && (
                      <Checkbox
                        checked={tasks.every((t) => selected.includes(t.id))}
                        indeterminate={
                          tasks.some((t) => selected.includes(t.id)) &&
                          !tasks.every((t) => selected.includes(t.id))
                        }
                        onChange={(e) =>
                          setSelected((prev) =>
                            e.target.checked
                              ? [
                                  ...new Set([
                                    ...prev,
                                    ...tasks.map((t) => t.id),
                                  ]),
                                ]
                              : prev.filter(
                                  (id) => !tasks.some((t) => t.id === id),
                                ),
                          )
                        }
                      />
                    )}
                    <span
                      style={{
                        fontSize: 15,
                        fontWeight: 700,
                        color: palette.ink,
                      }}
                    >
                      {brand}
                    </span>
                    <Pill tone="gray">{tasks.length} 个任务</Pill>
                  </div>
                  {tasks.map((t) => (
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
                          tab === UNASSIGNED && canAssign ? 'grab' : 'default',
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
                          <a onClick={() => setDetailId(t.id)}>{t.taskCode}</a>
                          <LevelPill value={t.level} />
                          {t.urgent && <Pill tone="red">紧急</Pill>}
                          {t.returnReason > 0 && t.status === UNASSIGNED && (
                            <Pill tone="orange">被退回</Pill>
                          )}
                        </span>
                        <span style={{ color: palette.mute, fontSize: 12 }}>
                          {t.brand}
                          {t.category ? ` · ${t.category}` : ''} · {t.itemCount}{' '}
                          个型号
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
                      </div>
                      {tab === UNASSIGNED ? (
                        <div style={{ display: 'grid', gap: 2 }}>
                          <span style={{ color: palette.mute, fontSize: 12 }}>
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
                          <span style={{ color: palette.mute, fontSize: 12 }}>
                            已回价 {t.pricedCount}/{t.itemCount}
                          </span>
                        </div>
                      )}
                      <div style={{ display: 'grid', gap: 2, minWidth: 0 }}>
                        {tab === UNASSIGNED ? (
                          <>
                            <span
                              style={{ color: palette.ink, fontWeight: 600 }}
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
                            {t.assignees.map((a) => a.name).join(' + ') || '—'}
                            {t.assignees.length > 1 && (
                              <span style={{ color: palette.mute }}>
                                （比价）
                              </span>
                            )}
                          </span>
                        )}
                      </div>
                      {tab === DONE ? (
                        <Button size="small" onClick={() => setDetailId(t.id)}>
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
                                            () => boardApi.reassign(t.id, p.id),
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
                                        () => boardApi.addAssignee(t.id, p.id),
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
                    还没有采购人员：给账号分配带「我的询价任务」菜单的角色（兼职采购用内置角色「兼职采购」）。
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
                  在「询价中」对任务点「追加比价」，可以再分给一人同时询价，两人的价格都进历史询价，报价时取最优。
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

      <TaskDetailDrawer
        taskId={detailId}
        onClose={() => setDetailId(undefined)}
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

export default SourcingBoardPage;
