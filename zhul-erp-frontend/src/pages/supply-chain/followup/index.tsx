import { EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Input, Segmented, Select } from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { PageTitle, useWide } from '@/pages/inquiry/shared/components';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import {
  Card,
  type FlowView,
  SupplyFlow,
  useOptions,
  useUsers,
} from '../components';
import {
  ClassifyModal,
  DeferModal,
  ProjectModal,
  RecordModal,
  SignalModal,
} from '../dialogs';
import { ownerParam } from '../rules';
import {
  COVER_NAMES,
  type Demand,
  type Group,
  type GroupPage,
  type Overview,
  PATHS,
  PRIORITY_NAMES,
  readBizError,
  type Signal,
  SRC_NAMES,
  scopeName,
  supplyApi,
  type Today,
  type TodayCard,
} from '../service';
import BattleCard from './BattleCard';
import { GroupTable } from './GroupTable';
import { SignalTable } from './SignalTable';
import TeamView, { type TeamJump } from './TeamView';
import { TodayView } from './TodayView';

const PAGE_SIZE = 20;

interface RecordTarget {
  title?: string;
  demands?: Demand[];
  checkedIds?: number[];
}

const Chip: React.FC<{
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}> = ({ active, onClick, children }) => (
  <Button
    type={active ? 'primary' : 'default'}
    ghost={active}
    onClick={onClick}
  >
    {children}
  </Button>
);

const SupplyFollowupPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const { brands, categories } = useOptions();
  const users = useUsers();

  const [view, setView] = useState<'mine' | 'team'>('mine');
  const [flow, setFlow] = useState<FlowView>('today');
  const [owner, setOwner] = useState('me');
  /** 自己名下没有需求时默认看全部，只判断一次 */
  const ownerChecked = useRef(false);
  const [overview, setOverview] = useState<Overview>();
  const [today, setToday] = useState<Today>();
  const [stage, setStage] = useState<string>();
  const [stockout, setStockout] = useState(false);
  const [pending, setPending] = useState(false);
  const [single, setSingle] = useState(false);
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [priority, setPriority] = useState<number>();
  const [signalType, setSignalType] = useState<number>();
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [groups, setGroups] = useState<GroupPage>();
  const [page, setPage] = useState(1);
  const [signals, setSignals] = useState<{ total: number; records: Signal[] }>({
    total: 0,
    records: [],
  });
  const [loading, setLoading] = useState(false);
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [pickedSignals, setPickedSignals] = useState<number[]>([]);
  const [selected, setSelected] = useState<Demand>();
  const [record, setRecord] = useState<RecordTarget>();
  const [defer, setDefer] = useState<number[]>();
  const [projectScope, setProjectScope] = useState<{
    brandId: number;
    categoryId?: number;
  }>();
  const [classify, setClassify] = useState<Signal[]>();
  const [ignore, setIgnore] = useState<Signal[]>();
  const [signalOpen, setSignalOpen] = useState(false);
  const reqRef = useRef(0);

  const canEdit = !!overview?.canEdit;
  const canTeam = !!overview?.canTeam;
  const canProject = !!overview?.canProject;
  const unclassifiedView = flow === 'intel' && stage === 'unclassified';

  const loadTop = useCallback(() => {
    supplyApi
      .overview(ownerParam(owner))
      .then((o) => {
        if (!ownerChecked.current && owner === 'me') {
          ownerChecked.current = true;
          if (
            o.coverTotal + o.watching + o.review + o.paused === 0 &&
            o.canTeam
          ) {
            setOwner('all');
            return;
          }
        }
        ownerChecked.current = true;
        setOverview(o);
      })
      .catch(() => setOverview(undefined));
    supplyApi
      .today()
      .then(setToday)
      .catch(() =>
        setToday({
          overdue: 0,
          dueToday: 0,
          weekRest: 0,
          overdueCards: [],
          todayCards: [],
        }),
      );
  }, [owner]);

  const loadList = useCallback(
    (p: number) => {
      if (flow === 'today') return;
      const req = ++reqRef.current;
      setLoading(true);
      const done = unclassifiedView
        ? supplyApi.unclassified(p, PAGE_SIZE, signalType).then((r) => {
            if (req !== reqRef.current) return;
            setSignals(r);
            setPage(p);
          })
        : supplyApi
            .groups({
              board: flow,
              stage,
              stockout: stockout || undefined,
              pending: pending || undefined,
              single: single || undefined,
              keyword: keyword.trim() || undefined,
              brandId,
              categoryId,
              priority,
              ownerId: ownerParam(owner),
              page: p,
              size: PAGE_SIZE,
            })
            .then((r) => {
              if (req !== reqRef.current) return;
              setPage(p);
              setGroups((old) =>
                p === 1 || !old
                  ? r
                  : { ...r, groups: [...old.groups, ...r.groups] },
              );
              if (p === 1) setSelected((s) => s ?? r.groups[0]?.items[0]);
            });
      done
        .catch(
          (e) =>
            req === reqRef.current && message.error(readBizError(e).message),
        )
        .finally(() => req === reqRef.current && setLoading(false));
    },
    [
      flow,
      unclassifiedView,
      signalType,
      stage,
      stockout,
      pending,
      single,
      keyword,
      brandId,
      categoryId,
      priority,
      owner,
      message,
    ],
  );

  useEffect(() => {
    if (view === 'mine') loadTop();
  }, [loadTop, view]);
  useEffect(() => {
    if (view === 'mine') loadList(1);
  }, [loadList, view]);

  const refresh = () => {
    setPicked(new Set());
    setPickedSignals([]);
    loadTop();
    loadList(1);
  };

  const switchFlow = (f: FlowView, s?: string) => {
    setFlow(f);
    setStage(s);
    setStockout(false);
    setPending(false);
    setSingle(false);
    setGroups(undefined);
    setSelected(undefined);
    setPicked(new Set());
    setPickedSignals([]);
  };

  /** 已加载的型号，批量操作按 id 找回 */
  const loaded = useMemo(() => {
    const m = new Map<number, Demand>();
    for (const g of groups?.groups ?? [])
      for (const d of g.items) m.set(d.id, d);
    return m;
  }, [groups]);

  const pick = (ids: number[], v: boolean) =>
    setPicked((s) => {
      const n = new Set(s);
      for (const id of ids)
        if (v) n.add(id);
        else n.delete(id);
      return n;
    });

  const jump = (j: TeamJump) => {
    setView('mine');
    setOwner(
      j.ownerId != null ? (j.ownerId ? String(j.ownerId) : 'all') : 'all',
    );
    switchFlow('cover');
    setBrandId(j.brandId);
    setCategoryId(j.categoryId);
    if (j.brandId || j.categoryId) setStockout(true);
  };

  const o = overview;
  const coverChips: [string | undefined, string, number][] = [
    [undefined, '全部', o?.coverTotal ?? 0],
    ...[1, 2, 3, 4].map(
      (k) =>
        [String(k), COVER_NAMES[k], o?.coverage[k] ?? 0] as [
          string,
          string,
          number,
        ],
    ),
  ];
  const intelChips: [string | undefined, string, number][] = [
    [undefined, '全部', o?.intelTotal ?? 0],
    ['unclassified', '待归类', o?.unclassified ?? 0],
    ['watching', '观察中', o?.watching ?? 0],
    ['review', '待复核', o?.review ?? 0],
    ['paused', '暂不覆盖', o?.paused ?? 0],
  ];
  const battleStage =
    flow === 'intel'
      ? (stage ?? 'watching')
      : (stage ?? String(selected?.coverageStatus ?? 3));

  const pickedDemands = [...picked]
    .map((id) => loaded.get(id))
    .filter(Boolean) as Demand[];
  const batchBar = (picked.size > 0 || pickedSignals.length > 0) && (
    <Card
      style={{
        padding: '10px 16px',
        marginBottom: 12,
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        background: palette.accentSoft,
      }}
    >
      <b style={{ flex: 1 }}>
        已选 {unclassifiedView ? pickedSignals.length : picked.size} 条
      </b>
      {unclassifiedView ? (
        <>
          <Button
            type="primary"
            onClick={() =>
              setClassify(
                signals.records.filter((s) => pickedSignals.includes(s.id)),
              )
            }
          >
            归到需求对象
          </Button>
          <Button
            onClick={() =>
              setIgnore(
                signals.records.filter((s) => pickedSignals.includes(s.id)),
              )
            }
          >
            忽略
          </Button>
        </>
      ) : (
        <>
          <Button
            type="primary"
            onClick={() => setRecord({ demands: pickedDemands })}
          >
            记处理
          </Button>
          <Button onClick={() => setDefer([...picked])}>延后</Button>
        </>
      )}
      <Button
        type="link"
        onClick={() => {
          setPicked(new Set());
          setPickedSignals([]);
        }}
      >
        取消选择
      </Button>
    </Card>
  );

  return (
    <div>
      <PageTitle
        actions={
          <>
            {canTeam && (
              <Segmented
                value={view}
                onChange={(v) => setView(v as 'mine' | 'team')}
                options={[
                  { value: 'mine', label: '我的' },
                  { value: 'team', label: '团队' },
                ]}
              />
            )}
            {view === 'mine' && (
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="搜索型号"
                style={{ width: 220 }}
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                onPressEnter={(e) => {
                  setKeyword(e.currentTarget.value);
                  if (flow === 'today') switchFlow('cover');
                }}
                onClear={() => setKeyword('')}
              />
            )}
            {view === 'mine' && canTeam && (
              <Select
                style={{ width: 160 }}
                value={owner}
                onChange={setOwner}
                showSearch={{ optionFilterProp: 'label' }}
                options={[
                  { value: 'me', label: '负责人：我' },
                  { value: 'all', label: '负责人：全部' },
                  ...users.map((u) => ({
                    value: String(u.value),
                    label: `负责人：${u.label}`,
                  })),
                ]}
              />
            )}
            {view === 'mine' && flow === 'intel' && canEdit && (
              <Button
                icon={<PlusOutlined />}
                onClick={() => setSignalOpen(true)}
              >
                录入信号
              </Button>
            )}
            {view === 'mine' && canEdit && (
              <Button
                type="primary"
                icon={<EditOutlined />}
                onClick={() => setRecord({ demands: pickedDemands })}
              >
                记一次处理
              </Button>
            )}
          </>
        }
      />
      {view === 'team' ? (
        <TeamView
          canProject={canProject}
          onJump={jump}
          onProject={setProjectScope}
        />
      ) : (
        <>
          <SupplyFlow
            overview={overview}
            active={flow}
            onSelect={(f) => f !== flow && switchFlow(f)}
          />
          {flow === 'today' ? (
            <TodayView
              today={today}
              canEdit={canEdit}
              onRecord={(c: TodayCard) =>
                setRecord({ title: scopeName(c), demands: c.items })
              }
              onDefer={(c) => setDefer(c.items.map((d) => d.id))}
              onBrowse={() => switchFlow('cover')}
            />
          ) : (
            <>
              <div
                style={{
                  display: 'flex',
                  gap: 8,
                  flexWrap: 'wrap',
                  marginBottom: 12,
                }}
              >
                {(flow === 'cover' ? coverChips : intelChips).map(
                  ([k, label, n]) => (
                    <Chip
                      key={label}
                      active={stage === k}
                      onClick={() => setStage(k)}
                    >
                      {label} <span style={{ opacity: 0.7 }}>{n}</span>
                    </Chip>
                  ),
                )}
                <span style={{ flex: 1 }} />
                {flow === 'cover' && (
                  <>
                    <Chip
                      active={stockout}
                      onClick={() => setStockout((v) => !v)}
                    >
                      近 30 天无货 {o?.stockoutOpen ?? 0}
                    </Chip>
                    <Chip
                      active={pending}
                      onClick={() => setPending((v) => !v)}
                    >
                      待确认 {o?.pending ?? 0}
                    </Chip>
                    <Chip active={single} onClick={() => setSingle((v) => !v)}>
                      单一渠道 {o?.single ?? 0}
                    </Chip>
                  </>
                )}
                {unclassifiedView ? (
                  <Select
                    allowClear
                    style={{ width: 140 }}
                    placeholder="来源"
                    value={signalType}
                    onChange={setSignalType}
                    options={Object.entries(SRC_NAMES).map(([v, l]) => ({
                      value: Number(v),
                      label: l,
                    }))}
                  />
                ) : (
                  <>
                    <Select
                      allowClear
                      style={{ width: 150 }}
                      placeholder="品牌"
                      showSearch={{ optionFilterProp: 'label' }}
                      value={brandId}
                      onChange={setBrandId}
                      options={brands}
                    />
                    <Select
                      allowClear
                      style={{ width: 150 }}
                      placeholder="品类"
                      showSearch={{ optionFilterProp: 'label' }}
                      value={categoryId}
                      onChange={setCategoryId}
                      options={categories}
                    />
                    <Select
                      allowClear
                      style={{ width: 110 }}
                      placeholder="优先级"
                      value={priority}
                      onChange={setPriority}
                      options={[3, 2, 1].map((k) => ({
                        value: k,
                        label: PRIORITY_NAMES[k],
                      }))}
                    />
                  </>
                )}
              </div>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: wide
                    ? 'minmax(0, 1fr) 320px'
                    : 'minmax(0, 1fr)',
                  gap: 16,
                  alignItems: 'start',
                }}
              >
                <div style={{ minWidth: 0 }}>
                  {batchBar}
                  <Card
                    style={{
                      padding: unclassifiedView ? 16 : 0,
                      overflow: 'hidden',
                    }}
                  >
                    {unclassifiedView ? (
                      <SignalTable
                        rows={signals.records}
                        total={signals.total}
                        page={page}
                        loading={loading}
                        picked={pickedSignals}
                        canEdit={canEdit}
                        onPick={setPickedSignals}
                        onPage={loadList}
                        onClassify={setClassify}
                        onIgnore={setIgnore}
                      />
                    ) : (
                      <GroupTable
                        board={flow}
                        page={groups}
                        loading={loading}
                        picked={picked}
                        selectedId={selected?.id}
                        canEdit={canEdit}
                        canProject={canProject}
                        onPick={pick}
                        onSelect={setSelected}
                        onRecord={(g: Group, ids?: number[]) =>
                          setRecord({
                            title: scopeName(g),
                            demands: g.items,
                            checkedIds: ids,
                          })
                        }
                        onProject={(g) =>
                          setProjectScope({
                            brandId: g.brandId,
                            categoryId: g.categoryId || undefined,
                          })
                        }
                        onMore={() => loadList(page + 1)}
                      />
                    )}
                  </Card>
                </div>
                <div style={wide ? { position: 'sticky', top: 16 } : undefined}>
                  <BattleCard stage={battleStage} />
                </div>
              </div>
            </>
          )}
        </>
      )}
      <RecordModal
        open={!!record}
        title={record?.title}
        demands={record?.demands}
        checkedIds={record?.checkedIds}
        onClose={() => setRecord(undefined)}
        onDone={() => {
          setRecord(undefined);
          refresh();
        }}
      />
      <DeferModal
        ids={defer}
        onClose={() => setDefer(undefined)}
        onDone={() => {
          setDefer(undefined);
          refresh();
        }}
      />
      <ProjectModal
        scope={projectScope}
        onClose={() => setProjectScope(undefined)}
        onDone={(id) => {
          setProjectScope(undefined);
          history.push(PATHS.project(id));
        }}
      />
      <ClassifyModal
        signals={classify}
        onClose={() => setClassify(undefined)}
        onDone={() => {
          setClassify(undefined);
          refresh();
        }}
      />
      <SignalModal
        open={signalOpen}
        onClose={() => setSignalOpen(false)}
        onDone={() => {
          setSignalOpen(false);
          refresh();
        }}
      />
      <ReasonModal
        open={!!ignore}
        title={`忽略 ${ignore?.length ?? 0} 条信号`}
        placeholder="忽略原因，如：型号无效、重复录入"
        okText="忽略"
        onCancel={() => setIgnore(undefined)}
        onOk={async (reason) => {
          try {
            await supplyApi.ignore(
              (ignore ?? []).map((s) => s.id),
              reason,
            );
            message.success('已忽略');
            setIgnore(undefined);
            refresh();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default SupplyFollowupPage;
