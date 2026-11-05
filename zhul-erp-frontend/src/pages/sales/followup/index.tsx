import { EditOutlined, SearchOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { Button, Input, Segmented, Select } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useWide } from '@/pages/inquiry/shared/components';
import { getUserList } from '@/pages/system/user/service';
import { SalesPageTitle } from '../components';
import BattleCard from './BattleCard';
import { VIEW_TODAY } from './boardStyle';
import { Card, NurtureModal, TodayList } from './components';
import { FlowChain } from './FlowChain';
import { FollowupTable, StageBar, type StageFilter } from './FollowupTable';
import RecordFollowupModal, { type RecordTarget } from './RecordFollowupModal';
import {
  BOARD_SALES,
  type FollowupItem,
  followupApi,
  type ItemList,
  type Overview,
  PERM_RECORD,
  PERM_TEAM,
  type Today,
} from './service';
import TeamView, { type TeamJump } from './TeamView';

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
  const [selected, setSelected] = useState<FollowupItem>();
  const [recordTarget, setRecordTarget] = useState<RecordTarget>();
  const [recordOpen, setRecordOpen] = useState(false);
  const [nurtureItem, setNurtureItem] = useState<FollowupItem>();
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
        setToday({ overdue: [], today: [], weekRemaining: 0, tomorrow: 0 }),
      );
  }, [ownerScope]);

  const loadList = useCallback(
    (p: number) => {
      const req = ++reqRef.current;
      setLoading(true);
      followupApi
        .items({
          board: boardNo,
          ...filter,
          blocker,
          keyword: keyword.trim() || undefined,
          ownerScope,
          page: p,
          pageSize: PAGE_SIZE,
        })
        .then((r) => {
          if (req !== reqRef.current) return;
          setPage(p);
          setList((old) =>
            p === 1 ? r : { ...r, records: [...old.records, ...r.records] },
          );
          if (p === 1)
            setSelected(
              (s) => r.records.find((x) => x.id === s?.id) ?? r.records[0],
            );
        })
        .catch(() => {
          if (req === reqRef.current && p === 1)
            setList({ total: 0, records: [], unpaid: [] });
        })
        .finally(() => req === reqRef.current && setLoading(false));
    },
    [boardNo, filter, blocker, keyword, ownerScope],
  );

  useEffect(() => {
    if (view === 'mine') loadTop();
  }, [loadTop, view]);
  useEffect(() => {
    if (view === 'mine' && boardNo !== VIEW_TODAY) loadList(1);
  }, [loadList, view, boardNo]);

  const refresh = () => {
    loadTop();
    if (boardNo !== VIEW_TODAY) loadList(1);
  };

  const openRecord = (item?: FollowupItem) => {
    setRecordTarget(item ? { item, customerId: item.customerId } : {});
    setRecordOpen(true);
  };

  const board = overview?.boards.find((b) => b.board === boardNo);

  const switchBoard = (b: number, stage?: string) => {
    setBoardNo(b);
    setFilter(stage ? { stage } : {});
    setBlocker(undefined);
    setSelected(undefined);
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
              onRecord={openRecord}
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
                />
                <FollowupTable
                  boardNo={boardNo}
                  filter={filter}
                  records={list.records}
                  total={list.total}
                  unpaid={list.unpaid}
                  loading={loading}
                  selectedId={selected?.id}
                  canRecord={canRecord}
                  showOwner={ownerScope !== 'me'}
                  onSelect={setSelected}
                  onRecord={openRecord}
                  onNurture={setNurtureItem}
                  onChanged={refresh}
                  onMore={() => loadList(page + 1)}
                />
              </Card>
              <div style={wide ? { position: 'sticky', top: 16 } : undefined}>
                <BattleCard item={selected} />
              </div>
            </div>
          )}
        </>
      )}
      <RecordFollowupModal
        open={recordOpen}
        target={recordTarget}
        onClose={() => setRecordOpen(false)}
        onDone={() => {
          setRecordOpen(false);
          refresh();
        }}
      />
      <NurtureModal
        item={nurtureItem}
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
