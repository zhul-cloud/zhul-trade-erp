import { EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { Button, Input, Segmented, Select } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useWide } from '@/pages/inquiry/shared/components';
import { useCountries } from '@/pages/product/components/useCountries';
import { getUserList } from '@/pages/system/user/service';
import { SalesPageTitle } from '../components';
import { AccountInquiryModal } from './AccountPanel';
import {
  BatchBar,
  BatchFollowupModal,
  type Picked,
  type SelectAll,
  usePicks,
} from './Batch';
import BattleCard from './BattleCard';
import { VIEW_TODAY } from './boardStyle';
import { Card, NurtureModal } from './components';
import {
  ConvertOpportunityModal,
  NewProspectModal,
  StopDevelopModal,
} from './DevelopPanel';
import { FlowChain } from './FlowChain';
import {
  FollowupTable,
  StageBar,
  type StageFilter,
  stageItem,
} from './FollowupTable';
import { QuickBatchModal } from './QuickActions';
import RecordFollowupModal, { type RecordTarget } from './RecordFollowupModal';
import {
  ANSWER_STAGES,
  BOARD_DEVELOP,
  BOARD_SALES,
  type FollowupItem,
  followupApi,
  GROUPED_BOARDS,
  type GroupList,
  type ItemList,
  type Overview,
  PERM_RECORD,
  PERM_TEAM,
  type Today,
} from './service';
import TeamView, { type TeamJump } from './TeamView';
import { TodayList } from './TodayList';

const PAGE_SIZE = 20;

const FollowupPage: React.FC = () => {
  const access = useAccess() as Record<string, boolean>;
  const canRecord = !!access[PERM_RECORD];
  const canTeam = !!access[PERM_TEAM];
  const wide = useWide();

  const [view, setView] = useState<'mine' | 'team'>('mine');
  /** 下方显示什么：今日待办（默认）或某个板块 */
  const [boardNo, setBoardNo] = useState(VIEW_TODAY);
  const [ownerScope, setOwnerScope] = useState('me');
  const [owners, setOwners] = useState<{ value: string; label: string }[]>([]);
  const [overview, setOverview] = useState<Overview>();
  const [today, setToday] = useState<Today>();
  const [filter, setFilter] = useState<StageFilter>({});
  const [blocker, setBlocker] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [list, setList] = useState<ItemList>({
    total: 0,
    records: [],
    unpaid: [],
  });
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  /** 按客户分组的板块（商机成交、订单履约、售后评价）的列表 */
  const [groupList, setGroupList] = useState<GroupList>();
  const [selected, setSelected] = useState<FollowupItem>();
  /** 选中的行：客户行 c{客户}、单据行 i{单据} */
  const [selectedKey, setSelectedKey] = useState<string>();
  const picks = usePicks();
  const [batchOpen, setBatchOpen] = useState(false);
  const [quickOpen, setQuickOpen] = useState(false);
  const [recordTarget, setRecordTarget] = useState<RecordTarget>();
  const [recordOpen, setRecordOpen] = useState(false);
  const [nurtureItem, setNurtureItem] = useState<FollowupItem>();
  /** 从记跟进改为转培育时带入的天数；列表里转培育用默认 */
  const [nurtureDays, setNurtureDays] = useState<number>();
  /** 客户经营「建询盘」的客户 */
  const [inquiryCustomer, setInquiryCustomer] = useState<number>();
  /** 主动开发：转为商机、停止开发（preset 为预选原因）、新建开发对象、国家筛选 */
  const [convertItem, setConvertItem] = useState<FollowupItem>();
  const [stop, setStop] = useState<{ item: FollowupItem; preset?: string }>();
  const [prospectOpen, setProspectOpen] = useState(false);
  const [country, setCountry] = useState<string>();
  const { options: countryOptions } = useCountries();
  const reqRef = useRef(0);

  useEffect(() => {
    if (!canTeam) return;
    getUserList({ current: 1, pageSize: 200 })
      .then((res) =>
        setOwners(
          res.data.records.map((u) => ({
            value: String(u.id),
            label: u.name,
          })),
        ),
      )
      .catch(() => setOwners([]));
  }, [canTeam]);

  const loadTop = useCallback(() => {
    followupApi
      .overview(ownerScope)
      .then(setOverview)
      .catch(() => setOverview(undefined));
    followupApi
      .today(ownerScope)
      .then(setToday)
      .catch(() =>
        setToday({
          overdue: [],
          today: [],
          handled: [],
          weekRemaining: 0,
          tomorrow: 0,
        }),
      );
  }, [ownerScope]);

  const loadList = useCallback(
    (p: number) => {
      const req = ++reqRef.current;
      setLoading(true);
      const params = {
        board: boardNo,
        ...filter,
        blocker,
        country: boardNo === BOARD_DEVELOP ? country : undefined,
        keyword: keyword.trim() || undefined,
        ownerScope,
        page: p,
        pageSize: PAGE_SIZE,
      };
      const grouped = GROUPED_BOARDS.includes(boardNo);
      const done = grouped
        ? followupApi.groups(params).then((r) => {
            if (req !== reqRef.current) return;
            setPage(p);
            setGroupList((old) =>
              p === 1 || !old
                ? r
                : { ...r, records: [...old.records, ...r.records] },
            );
            if (p === 1) {
              const first = r.records[0];
              setSelectedKey((k) => {
                const keep = r.records.some(
                  (g) =>
                    k === `c${g.customerId}` ||
                    g.items.some((i) => k === `i${i.id}`),
                );
                if (keep) return k;
                setSelected(first && stageItem(first));
                return first && `c${first.customerId}`;
              });
            }
          })
        : followupApi.items(params).then((r) => {
            if (req !== reqRef.current) return;
            setGroupList(undefined);
            setPage(p);
            setList((old) =>
              p === 1 ? r : { ...r, records: [...old.records, ...r.records] },
            );
            if (p === 1)
              setSelected((s) => {
                const next =
                  r.records.find((x) => x.id === s?.id) ?? r.records[0];
                setSelectedKey(next && `i${next.id}`);
                return next;
              });
          });
      done
        .catch(() => {
          if (req !== reqRef.current || p !== 1) return;
          setList({ total: 0, records: [], unpaid: [] });
          setGroupList(
            grouped
              ? { total: 0, docTotal: 0, records: [], unpaid: [] }
              : undefined,
          );
        })
        .finally(() => req === reqRef.current && setLoading(false));
    },
    [boardNo, filter, blocker, country, keyword, ownerScope],
  );

  useEffect(() => {
    if (view === 'mine') loadTop();
  }, [loadTop, view]);
  useEffect(() => {
    if (view === 'mine' && boardNo !== VIEW_TODAY) loadList(1);
  }, [loadList, view, boardNo]);

  const refresh = () => {
    picks.clear();
    loadTop();
    if (boardNo !== VIEW_TODAY) loadList(1);
  };

  const openRecord = (item?: FollowupItem) => {
    setRecordTarget(item ? { item, customerId: item.customerId } : {});
    setRecordOpen(true);
  };
  const openTarget = (t: RecordTarget) => {
    setRecordTarget(t);
    setRecordOpen(true);
  };
  /** 批量跟进被跳过的客户单独记：带入跳过的那个板块的单据 */
  const recordPicked = (p: Picked) => {
    const lead =
      p.items.find((i) => ANSWER_STAGES.includes(i.stageCode)) ?? p.items[0];
    openTarget({
      customerId: p.customerId,
      customerName: p.customerName,
      item: lead,
      items: p.items.filter((i) => i.board === lead?.board),
    });
  };

  const board = overview?.boards.find((b) => b.board === boardNo);
  const renderBatchBar = (selectAll?: SelectAll) => (
    <BatchBar
      picked={picks.list}
      canRecord={canRecord}
      selectAll={selectAll}
      onClear={picks.clear}
      onRecord={() => setBatchOpen(true)}
      onQuick={() => setQuickOpen(true)}
      onChanged={refresh}
    />
  );
  const batchBar = renderBatchBar();

  const switchBoard = (b: number, stage?: string) => {
    setBoardNo(b);
    setFilter(stage ? { stage } : {});
    setBlocker(undefined);
    setSelected(undefined);
    setSelectedKey(undefined);
    setGroupList(undefined);
    picks.clear();
  };

  /** 团队视图点数字：回到「我的」视图，负责人筛选为该人（或全部） */
  const jump = (j: TeamJump) => {
    const scope = j.ownerId ? String(j.ownerId) : 'all';
    if (j.ownerId && !owners.some((o) => o.value === scope))
      setOwners((l) => [...l, { value: scope, label: j.ownerName ?? scope }]);
    setOwnerScope(scope);
    if (j.today) switchBoard(VIEW_TODAY);
    else switchBoard(j.board ?? BOARD_SALES, j.stage);
    setKeywordInput(j.keyword ?? '');
    setKeyword(j.keyword ?? '');
    setView('mine');
  };

  return (
    <div>
      <SalesPageTitle
        description={
          view === 'team'
            ? '哪里卡住了、谁需要帮一把'
            : '今天该跟谁、跟到哪一步、下一步做什么'
        }
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
                placeholder="搜索客户、单号"
                style={{ width: 220 }}
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                onPressEnter={(e) => {
                  setKeyword(e.currentTarget.value);
                  // 在今日待办里搜索时切到商机成交列表
                  if (boardNo === VIEW_TODAY) switchBoard(BOARD_SALES);
                }}
                onClear={() => setKeyword('')}
              />
            )}
            {view === 'mine' && canTeam && (
              <Select
                style={{ width: 160 }}
                value={ownerScope}
                onChange={setOwnerScope}
                showSearch={{ optionFilterProp: 'label' }}
                options={[
                  { value: 'me', label: '负责人：我' },
                  { value: 'all', label: '负责人：全部' },
                  ...owners.map((o) => ({ ...o, label: `负责人：${o.label}` })),
                ]}
              />
            )}
            {view === 'mine' && canRecord && (
              <Button
                type="primary"
                icon={<EditOutlined />}
                onClick={() => openRecord()}
              >
                记一次跟进
              </Button>
            )}
          </>
        }
      />
      {view === 'team' ? (
        <TeamView onJump={jump} />
      ) : (
        <>
          <FlowChain
            overview={overview}
            active={boardNo}
            onSelect={(b) => b !== boardNo && switchBoard(b)}
          />
          {boardNo === VIEW_TODAY ? (
            <TodayList
              today={today}
              canRecord={canRecord}
              picked={picks.picked}
              onPick={picks.toggle}
              onPickAll={picks.replace}
              batchBar={renderBatchBar}
              onRecord={openTarget}
              onChanged={refresh}
              onBrowse={() => switchBoard(BOARD_SALES)}
            />
          ) : (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: wide
                  ? 'minmax(0, 1fr) 360px'
                  : 'minmax(0, 1fr)',
                gap: 16,
                alignItems: 'start',
              }}
            >
              <Card style={{ padding: '16px 20px' }}>
                <StageBar
                  boardNo={boardNo}
                  board={board}
                  value={filter}
                  onChange={setFilter}
                  blocker={blocker}
                  onBlocker={setBlocker}
                  extra={
                    boardNo === BOARD_DEVELOP && (
                      <>
                        <Select
                          allowClear
                          style={{ width: 170 }}
                          placeholder="国家：全部"
                          value={country}
                          onChange={setCountry}
                          showSearch={{ optionFilterProp: 'label' }}
                          options={countryOptions}
                        />
                        {canRecord && (
                          <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() => setProspectOpen(true)}
                          >
                            新建开发对象
                          </Button>
                        )}
                      </>
                    )
                  }
                />
                <FollowupTable
                  boardNo={boardNo}
                  filter={filter}
                  records={list.records}
                  total={groupList?.total ?? list.total}
                  unpaid={groupList?.unpaid ?? list.unpaid}
                  groups={groupList?.records}
                  docTotal={groupList?.docTotal}
                  loading={loading}
                  selectedKey={selectedKey}
                  canRecord={canRecord}
                  showOwner={ownerScope !== 'me'}
                  onSelect={(item, key) => {
                    setSelected(item);
                    setSelectedKey(key);
                  }}
                  onRecord={openRecord}
                  onRecordTarget={openTarget}
                  picked={picks.picked}
                  onPick={picks.toggle}
                  batchBar={batchBar}
                  onNurture={(i) => {
                    setNurtureDays(undefined);
                    setNurtureItem(i);
                  }}
                  onChanged={refresh}
                  onMore={() => loadList(page + 1)}
                  onNewInquiry={setInquiryCustomer}
                  onConvert={setConvertItem}
                />
              </Card>
              <div style={wide ? { position: 'sticky', top: 16 } : undefined}>
                <BattleCard
                  item={selected}
                  canRecord={canRecord}
                  onNewInquiry={setInquiryCustomer}
                  onChanged={refresh}
                  onConvert={setConvertItem}
                  onStop={(item) => setStop({ item })}
                />
              </div>
            </div>
          )}
        </>
      )}
      <RecordFollowupModal
        open={recordOpen}
        target={recordTarget}
        onClose={() => setRecordOpen(false)}
        onDone={(_, newInquiryCustomerId, refusedItem) => {
          setRecordOpen(false);
          refresh();
          if (newInquiryCustomerId) setInquiryCustomer(newInquiryCustomerId);
          // 主动开发选了「明确拒绝」：接着打开停止开发，原因预选「明确拒绝」
          if (refusedItem) setStop({ item: refusedItem, preset: 'REFUSED' });
        }}
        onNurture={(i, days) => {
          setRecordOpen(false);
          setNurtureDays(days);
          setNurtureItem(i);
        }}
      />
      <BatchFollowupModal
        open={batchOpen}
        picked={picks.list}
        onClose={() => setBatchOpen(false)}
        onDone={refresh}
        onRecordOne={recordPicked}
      />
      <QuickBatchModal
        open={quickOpen}
        picked={picks.list}
        onClose={() => setQuickOpen(false)}
        onDone={refresh}
      />
      <ConvertOpportunityModal
        item={convertItem}
        onClose={() => setConvertItem(undefined)}
        onDone={() => {
          setConvertItem(undefined);
          refresh();
        }}
      />
      <StopDevelopModal
        item={stop?.item}
        preset={stop?.preset}
        onClose={() => setStop(undefined)}
        onDone={() => {
          setStop(undefined);
          refresh();
        }}
      />
      <NewProspectModal
        open={prospectOpen}
        onClose={() => setProspectOpen(false)}
        onDone={() => {
          setProspectOpen(false);
          refresh();
        }}
      />
      <AccountInquiryModal
        customerId={inquiryCustomer}
        onClose={() => setInquiryCustomer(undefined)}
        onCreated={refresh}
      />
      <NurtureModal
        item={nurtureItem}
        defaultDays={nurtureDays}
        onClose={() => setNurtureItem(undefined)}
        onDone={() => {
          setNurtureItem(undefined);
          refresh();
        }}
      />
    </div>
  );
};

export default FollowupPage;
