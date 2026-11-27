import { history, useSearchParams } from '@umijs/max';
import { App, Button, Checkbox, Input, type InputRef, Select } from 'antd';
import { Pencil, Plus, Search } from 'lucide-react';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  FilterChip,
  type FilterChipItem,
  FilterPopover,
  PageHeader,
  ViewTabs,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { useWide } from '@/pages/inquiry/shared/components';
import { categoryGroups } from '@/pages/supplier/productScope';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { usePageSize } from '@/utils/pageSize';
import {
  Card,
  type FlowView,
  SupplyFlow,
  useOptions,
  useUsers,
} from '../components';
import {
  CandidateModal,
  CandidateRecordModal,
  type CandidateTarget,
  ExpansionModal,
} from '../devDialogs';
import {
  ClassifyModal,
  DeferModal,
  ProjectModal,
  RecordModal,
  SignalModal,
} from '../dialogs';
import { ownerParam } from '../rules';
import {
  type Candidate,
  COVER_NAMES,
  type Demand,
  type DevGroup,
  type DevGroupPage,
  type DevTodayItem,
  type Group,
  type GroupPage,
  HEAT_NAMES,
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
import { DevTable } from './DevTable';
import { GroupTable } from './GroupTable';
import { SignalTable } from './SignalTable';
import TeamView, { type TeamJump } from './TeamView';
import { TodayView } from './TodayView';

const PAGE_SIZE = 20;

interface CandidateRecordTarget {
  title: string;
  candidates: Candidate[];
  checkedIds?: number[];
}

/** 开发任务名称；名称里没有品牌品类时补上 */
const devTitle = (g: DevGroup) =>
  g.projectName?.includes(scopeName(g))
    ? g.projectName
    : [g.projectName, scopeName(g)].filter(Boolean).join(' · ');

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
  const { brands, tree, categories, tops } = useOptions();
  const [params] = useSearchParams();
  const users = useUsers();

  const [view, setView] = useState<'mine' | 'team'>('mine');
  const [flow, setFlow] = useState<FlowView>('today');
  const [owner, setOwner] = useState('me');
  /** 自己名下没有需求时默认看全部，只判断一次 */
  const ownerChecked = useRef(false);
  /** 今日待办里搜索后切到供应覆盖：切过去后让新位置的搜索框接着获得焦点 */
  const searchRef = useRef<InputRef>(null);
  const refocusSearch = useRef(false);
  useEffect(() => {
    if (!refocusSearch.current || flow === 'today') return;
    refocusSearch.current = false;
    searchRef.current?.focus();
  }, [flow]);
  const [overview, setOverview] = useState<Overview>();
  const [overviewError, setOverviewError] = useState('');
  const [today, setToday] = useState<Today>();
  const [todayError, setTodayError] = useState('');
  const [stage, setStage] = useState<string>();
  const [stockout, setStockout] = useState(false);
  /** 无货统计期：团队视图「近 90 天」下钻时为 90 */
  const [stockoutDays, setStockoutDays] = useState(30);
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
  const [signalSize, setSignalSize] = usePageSize(PAGE_SIZE, 'signals');
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
    dev?: boolean;
  }>();
  const [devPage, setDevPage] = useState<DevGroupPage>();
  const [devStage, setDevStage] = useState('gap');
  const [parentCategoryId, setParentCategoryId] = useState<number>();
  const [heat, setHeat] = useState<number>();
  const [expansionOnly, setExpansionOnly] = useState(false);
  const [focusKey, setFocusKey] = useState<string>();
  const [expansion, setExpansion] = useState<{
    brandId: number;
    categoryId?: number;
  } | null>();
  const [candidate, setCandidate] = useState<CandidateTarget>();
  const [candRecord, setCandRecord] = useState<CandidateRecordTarget>();
  const [classify, setClassify] = useState<Signal[]>();
  const [ignore, setIgnore] = useState<Signal[]>();
  const [signalOpen, setSignalOpen] = useState(false);
  const reqRef = useRef(0);

  const canEdit = !!overview?.canEdit;
  const canTeam = !!overview?.canTeam;
  const canProject = !!overview?.canProject;
  const canDev = !!overview?.canDev;
  const canSeeSupplier = !!overview?.canSeeSupplier;
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
        setOverviewError('');
      })
      .catch((e) => setOverviewError(readBizError(e).message));
    // 取数失败显示失败与重试，不能显示成「都处理完了」（audit UI-091）
    supplyApi
      .today()
      .then((t) => {
        setToday(t);
        setTodayError('');
      })
      .catch((e) => {
        setToday(undefined);
        setTodayError(readBizError(e).message);
      });
  }, [owner]);

  const loadList = useCallback(
    (p: number) => {
      if (flow === 'today') return;
      const req = ++reqRef.current;
      setLoading(true);
      const done =
        flow === 'dev'
          ? supplyApi
              .devGroups({
                stage: devStage,
                keyword: keyword.trim() || undefined,
                brandId,
                categoryId,
                parentCategoryId,
                heat,
                source: expansionOnly ? 'expansion' : undefined,
                ownerId: ownerParam(owner),
                page: p,
                size: PAGE_SIZE,
              })
              .then((r) => {
                if (req !== reqRef.current) return;
                setPage(p);
                setDevPage((old) =>
                  p === 1 || !old
                    ? r
                    : { ...r, groups: [...old.groups, ...r.groups] },
                );
              })
          : unclassifiedView
            ? supplyApi.unclassified(p, signalSize, signalType).then((r) => {
                if (req !== reqRef.current) return;
                setSignals(r);
                setPage(p);
              })
            : supplyApi
                .groups({
                  board: flow,
                  stage,
                  stockout: stockout || undefined,
                  stockoutDays: stockout ? stockoutDays : undefined,
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
      signalSize,
      stage,
      stockout,
      stockoutDays,
      pending,
      single,
      keyword,
      brandId,
      categoryId,
      priority,
      owner,
      message,
      devStage,
      parentCategoryId,
      heat,
      expansionOnly,
    ],
  );

  // 覆盖矩阵「按供应商」点格子跳过来：?board=dev&brandId=…&categoryId=…
  useEffect(() => {
    if (params.get('board') !== 'dev') return;
    const b = Number(params.get('brandId')) || undefined;
    const c = params.get('categoryId');
    setOwner('all');
    ownerChecked.current = true;
    setFlow('dev');
    setDevStage('all');
    setBrandId(b);
    setCategoryId(c != null ? Number(c) : undefined);
    if (b != null && c != null) setFocusKey(`${b}:${c}`);
  }, [params]);

  useEffect(() => {
    if (view === 'mine') loadTop();
  }, [loadTop, view]);
  useEffect(() => {
    if (view === 'mine') loadList(1);
  }, [loadList, view]);
  // 筛选条件变了清空勾选，批量操作只作用于看得见的型号（audit UI-086）
  useEffect(() => {
    setPicked(new Set());
    setPickedSignals([]);
  }, [
    stage,
    stockout,
    stockoutDays,
    pending,
    single,
    brandId,
    categoryId,
    priority,
    keyword,
    owner,
    signalType,
  ]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(() => {
    if (view !== 'mine') return;
    loadTop();
    loadList(1);
  });

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
    setStockoutDays(30);
    setPending(false);
    setSingle(false);
    setGroups(undefined);
    setDevPage(undefined);
    setFocusKey(undefined);
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

  /** 团队视图下钻：沿用统计口径（统计期、负责人），清掉与统计无关的关键词和优先级（audit UI-089） */
  const jump = (j: TeamJump) => {
    setView('mine');
    setOwner(j.ownerId ? String(j.ownerId) : 'all');
    switchFlow('cover');
    setBrandId(j.brandId);
    setCategoryId(j.categoryId);
    setPriority(undefined);
    setKeyword('');
    setKeywordInput('');
    if (j.brandId || j.categoryId) {
      setStockout(true);
      setStockoutDays(j.days ?? 30);
    }
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
  const devFocus =
    devPage?.groups.find((g) => g.key === focusKey) ?? devPage?.groups[0];
  const battleStage =
    flow === 'dev'
      ? `dev:${['todo', 'doing', 'done'].includes(devStage) ? devStage : (devFocus?.stage ?? 'todo')}`
      : flow === 'intel'
        ? (stage ?? 'watching')
        : (stage ?? String(selected?.coverageStatus ?? 3));
  const devChips: [string, string, number][] = [
    ['gap', '有缺口', o?.dev?.gapGroups ?? 0],
    ['todo', '待开发', o?.dev?.todo ?? 0],
    ['doing', '开发中', o?.dev?.doing ?? 0],
    ['done', '已达标', o?.dev?.done ?? 0],
  ];
  const openDev = (i: DevTodayItem) => {
    switchFlow('dev');
    setDevStage('all');
    setOwner('all');
    setBrandId(i.brandId);
    setCategoryId(i.categoryId);
    setFocusKey(`${i.brandId}:${i.categoryId}`);
  };

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
        background: palette.primarySoft, // lint-ui-ignore primary-soft: 批量操作条（已选 N 条），属于选中状态
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
          <Button onClick={() => setDefer(pickedDemands.map((d) => d.id))}>
            延后
          </Button>
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

  // 搜索、负责人放在列表工具栏（与客户跟进同一写法，R10 方案 B），页头只留视图切换和主按钮
  const search = (
    <Input
      ref={searchRef}
      allowClear
      prefix={<Search size="1em" />}
      placeholder={flow === 'dev' ? '搜索品牌、品类' : '搜索型号'}
      aria-label={flow === 'dev' ? '搜索品牌、品类' : '搜索型号'}
      style={{ width: 200 }}
      value={keywordInput}
      onChange={(e) => setKeywordInput(e.target.value)}
      onPressEnter={(e) => {
        setKeyword(e.currentTarget.value);
        // 在今日待办里搜索时切到供应覆盖；搜索框换了位置，切过去后接着获得焦点
        if (flow === 'today') {
          refocusSearch.current = true;
          switchFlow('cover');
        }
      }}
      onClear={() => setKeyword('')}
    />
  );
  const tools = (
    <>
      {search}
      {canTeam && (
        <Select
          style={{ width: 160 }}
          aria-label="负责人"
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
    </>
  );

  // 选项里找名称（分组的品类选项也找得到），给已生效条件的胶囊用
  const nameOf = (opts: readonly unknown[] | undefined, v: unknown): string => {
    for (const o of (opts ?? []) as {
      value?: unknown;
      label?: unknown;
      options?: unknown[];
    }[]) {
      if (o.options) {
        const n = nameOf(o.options, v);
        if (n) return n;
      } else if (o.value === v) return String(o.label);
    }
    return '';
  };
  // 次要条件收进「筛选」弹层，工具栏保持一行；已生效的条件显示成胶囊，能直接去掉（UI-087：带进来的品类要看得见、能清掉）
  const brandSel = (
    <Select
      allowClear
      style={{ width: '100%' }}
      placeholder="全部品牌"
      aria-label="品牌"
      showSearch={{ optionFilterProp: 'label' }}
      value={brandId}
      onChange={setBrandId}
      options={brands}
    />
  );
  const devFilters = [
    {
      label: '只看',
      node: (
        <Checkbox
          checked={expansionOnly}
          onChange={(e) => setExpansionOnly(e.target.checked)}
        >
          主动拓展
        </Checkbox>
      ),
    },
    { label: '品牌', node: brandSel },
    {
      label: '一级品类',
      node: (
        <Select
          allowClear
          style={{ width: '100%' }}
          placeholder="全部一级品类"
          aria-label="一级品类"
          showSearch={{ optionFilterProp: 'label' }}
          value={parentCategoryId}
          onChange={(v) => {
            setParentCategoryId(v);
            setCategoryId(undefined);
          }}
          options={tops}
        />
      ),
    },
    {
      label: '二级品类',
      node: (
        <Select
          allowClear
          style={{ width: '100%' }}
          placeholder="全部二级品类"
          aria-label="二级品类"
          showSearch={{ optionFilterProp: 'label' }}
          value={categoryId}
          onChange={setCategoryId}
          options={
            parentCategoryId
              ? categoryGroups(tree.filter((t) => t.id === parentCategoryId))
              : categories
          }
        />
      ),
    },
    {
      label: '热度',
      node: (
        <Select
          allowClear
          style={{ width: '100%' }}
          placeholder="全部热度"
          aria-label="热度"
          value={heat}
          onChange={setHeat}
          options={[3, 2, 1].map((k) => ({ value: k, label: HEAT_NAMES[k] }))}
        />
      ),
    },
  ];
  const devChipsOn: FilterChipItem[] = [
    expansionOnly && {
      key: 'expansion',
      label: '主动拓展',
      onRemove: () => setExpansionOnly(false),
    },
    brandId !== undefined && {
      key: 'brand',
      label: `品牌：${nameOf(brands, brandId)}`,
      onRemove: () => setBrandId(undefined),
    },
    parentCategoryId !== undefined && {
      key: 'parent',
      label: `一级品类：${nameOf(tops, parentCategoryId)}`,
      onRemove: () => {
        setParentCategoryId(undefined);
        setCategoryId(undefined);
      },
    },
    categoryId !== undefined && {
      key: 'category',
      label: `二级品类：${nameOf(categories, categoryId)}`,
      onRemove: () => setCategoryId(undefined),
    },
    heat !== undefined && {
      key: 'heat',
      label: `热度：${HEAT_NAMES[heat]}`,
      onRemove: () => setHeat(undefined),
    },
  ].filter(Boolean) as FilterChipItem[];
  const coverToggles = flow === 'cover' && {
    label: '只看',
    node: (
      <div style={{ display: 'grid', gap: 6 }}>
        <Checkbox
          checked={stockout}
          onChange={(e) => {
            setStockout(e.target.checked);
            setStockoutDays(30);
          }}
        >
          近 30 天无货 · {o?.stockoutOpen ?? 0}
        </Checkbox>
        <Checkbox
          checked={pending}
          onChange={(e) => setPending(e.target.checked)}
        >
          待确认 · {o?.pending ?? 0}
        </Checkbox>
        <Checkbox
          checked={single}
          onChange={(e) => setSingle(e.target.checked)}
        >
          单一渠道 · {o?.single ?? 0}
        </Checkbox>
      </div>
    ),
  };
  const boardFilters = [
    ...(coverToggles ? [coverToggles] : []),
    { label: '品牌', node: brandSel },
    {
      label: '品类',
      node: (
        <Select
          allowClear
          style={{ width: '100%' }}
          placeholder="全部品类"
          aria-label="品类"
          showSearch={{ optionFilterProp: 'label' }}
          value={categoryId}
          onChange={setCategoryId}
          options={categories}
        />
      ),
    },
    {
      label: '优先级',
      node: (
        <Select
          allowClear
          style={{ width: '100%' }}
          placeholder="全部优先级"
          aria-label="优先级"
          value={priority}
          onChange={setPriority}
          options={[3, 2, 1].map((k) => ({
            value: k,
            label: PRIORITY_NAMES[k],
          }))}
        />
      ),
    },
  ];
  const boardChipsOn: FilterChipItem[] = [
    flow === 'cover' &&
      stockout && {
        key: 'stockout',
        label: `近 ${stockoutDays} 天无货`,
        onRemove: () => {
          setStockout(false);
          setStockoutDays(30);
        },
      },
    flow === 'cover' &&
      pending && {
        key: 'pending',
        label: '待确认',
        onRemove: () => setPending(false),
      },
    flow === 'cover' &&
      single && {
        key: 'single',
        label: '单一渠道',
        onRemove: () => setSingle(false),
      },
    brandId !== undefined && {
      key: 'brand',
      label: `品牌：${nameOf(brands, brandId)}`,
      onRemove: () => setBrandId(undefined),
    },
    categoryId !== undefined && {
      key: 'category',
      label: `品类：${nameOf(categories, categoryId)}`,
      onRemove: () => setCategoryId(undefined),
    },
    priority !== undefined && {
      key: 'priority',
      label: `优先级：${PRIORITY_NAMES[priority]}`,
      onRemove: () => setPriority(undefined),
    },
  ].filter(Boolean) as FilterChipItem[];

  return (
    <div>
      <PageHeader
        extra={
          view === 'mine' &&
          canEdit &&
          flow !== 'dev' && (
            <Button
              type="primary"
              icon={<Pencil size="1em" />}
              onClick={() => setRecord({ demands: pickedDemands })}
            >
              记一次处理
            </Button>
          )
        }
      />
      {/* 团队是另一张统计视图（不是同一列表看更多人），用视图页签（复审 S3） */}
      {canTeam && (
        <ViewTabs
          activeKey={view}
          onChange={(k) => setView(k as 'mine' | 'team')}
          items={[
            { key: 'mine', label: '我的' },
            { key: 'team', label: '团队' },
          ]}
        />
      )}
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
            error={overviewError}
            onRetry={loadTop}
            active={flow}
            onSelect={(f) => f !== flow && switchFlow(f)}
          />
          {flow === 'today' ? (
            <TodayView
              today={today}
              error={todayError}
              onRetry={loadTop}
              canEdit={canEdit}
              onRecord={(c: TodayCard) =>
                setRecord({ title: scopeName(c), demands: c.items })
              }
              onDefer={(c) => setDefer(c.items.map((d) => d.id))}
              onBrowse={() => switchFlow('cover')}
              onDev={openDev}
              // 只放搜索：今日待办固定看自己的（后端 /today 按当前用户取数），负责人对它不起作用
              tools={search}
            />
          ) : flow === 'dev' ? (
            <>
              <div
                style={{
                  display: 'flex',
                  gap: 8,
                  flexWrap: 'wrap',
                  marginBottom: 12,
                }}
              >
                {devChips.map(([k, label, n]) => (
                  <Chip
                    key={k}
                    active={devStage === k}
                    onClick={() => setDevStage(k)}
                  >
                    {label} <span style={{ opacity: 0.7 }}>{n}</span>
                  </Chip>
                ))}
                {devChipsOn.map((c) => (
                  <FilterChip key={c.key} item={c} />
                ))}
                <span style={{ flex: 1 }} />
                {tools}
                <FilterPopover count={devChipsOn.length} items={devFilters} />
                {canDev && (
                  <Button
                    icon={<Plus size="1em" />}
                    onClick={() => setExpansion(null)}
                  >
                    加入主动拓展
                  </Button>
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
                <Card style={{ padding: 0, overflow: 'hidden', minWidth: 0 }}>
                  <DevTable
                    page={devPage}
                    loading={loading}
                    focusKey={focusKey}
                    canDev={canDev}
                    canProject={canProject}
                    canSeeSupplier={canSeeSupplier}
                    onMore={() => loadList(page + 1)}
                    onProject={(g) =>
                      setProjectScope({
                        brandId: g.brandId,
                        categoryId: g.categoryId || undefined,
                        dev: true,
                      })
                    }
                    onRecord={(g, ids) =>
                      setCandRecord({
                        title: devTitle(g),
                        candidates: g.candidates,
                        checkedIds: ids,
                      })
                    }
                    onCandidate={(g, supplier) => {
                      if (g.projectTarget == null || !g.projectId) {
                        message.info('先建开发任务，再把这家加为候选');
                        if (canProject)
                          setProjectScope({
                            brandId: g.brandId,
                            categoryId: g.categoryId || undefined,
                            dev: true,
                          });
                        return;
                      }
                      setCandidate({
                        projectId: g.projectId,
                        title: devTitle(g),
                        count: g.candidates.length,
                        supplier,
                      });
                    }}
                  />
                </Card>
                <div style={wide ? { position: 'sticky', top: 16 } : undefined}>
                  <BattleCard stage={battleStage} />
                </div>
              </div>
            </>
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
                {!unclassifiedView &&
                  boardChipsOn.map((c) => <FilterChip key={c.key} item={c} />)}
                <span style={{ flex: 1 }} />
                {/* 待归类的信号接口不按关键词、负责人筛选，不放这两个控件 */}
                {!unclassifiedView && tools}
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
                  <FilterPopover
                    count={boardChipsOn.length}
                    items={boardFilters}
                  />
                )}
                {flow === 'intel' && canEdit && (
                  <Button
                    icon={<Plus size="1em" />}
                    onClick={() => setSignalOpen(true)}
                  >
                    录入信号
                  </Button>
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
                        pageSize={signalSize}
                        loading={loading}
                        picked={pickedSignals}
                        canEdit={canEdit}
                        onPick={setPickedSignals}
                        onPage={(p, s) =>
                          s === signalSize ? loadList(p) : setSignalSize(s)
                        }
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
          const dev = projectScope?.dev;
          setProjectScope(undefined);
          if (dev) refresh();
          else history.push(PATHS.project(id));
        }}
      />
      <ExpansionModal
        open={expansion !== undefined}
        scope={expansion ?? undefined}
        onClose={() => setExpansion(undefined)}
        onDone={() => {
          setExpansion(undefined);
          refresh();
        }}
      />
      <CandidateModal
        target={candidate}
        canSeeSupplier={canSeeSupplier}
        onClose={() => setCandidate(undefined)}
        onDone={() => {
          setCandidate(undefined);
          refresh();
        }}
      />
      <CandidateRecordModal
        open={!!candRecord}
        title={candRecord?.title}
        candidates={candRecord?.candidates}
        checkedIds={candRecord?.checkedIds}
        canSeeSupplier={canSeeSupplier}
        onLink={(c) => {
          const title = candRecord?.title ?? '';
          setCandRecord(undefined);
          setCandidate({
            projectId: c.projectId,
            title,
            count: 0,
            link: c,
          });
        }}
        onClose={() => setCandRecord(undefined)}
        onDone={() => {
          setCandRecord(undefined);
          refresh();
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
