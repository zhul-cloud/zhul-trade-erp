import { EditOutlined, SearchOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { Button, Input, Select } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useWide } from '@/pages/inquiry/shared/components';
import { getUserList } from '@/pages/system/user/service';
import { SalesPageTitle } from '../components';
import BattleCard from './BattleCard';
import { Card, FlowCards, NurtureModal, TodayList } from './components';
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

const PAGE_SIZE = 20;

const FollowupPage: React.FC = () => {
  const access = useAccess() as Record<string, boolean>;
  const canRecord = !!access[PERM_RECORD];
  const canTeam = !!access[PERM_TEAM];
  const wide = useWide();

  const [ownerScope, setOwnerScope] = useState('me');
  const [owners, setOwners] = useState<{ value: string; label: string }[]>([]);
  const [overview, setOverview] = useState<Overview>();
  const [today, setToday] = useState<Today>();
  const [filter, setFilter] = useState<StageFilter>({});
  const [blocker, setBlocker] = useState<string>();
  const [keyword, setKeyword] = useState('');
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
          board: BOARD_SALES,
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
    [filter, blocker, keyword, ownerScope],
  );

  useEffect(loadTop, [loadTop]);
  useEffect(() => loadList(1), [loadList]);

  const refresh = () => {
    loadTop();
    loadList(1);
  };

  const openRecord = (item?: FollowupItem) => {
    setRecordTarget(item ? { item, customerId: item.customerId } : {});
    setRecordOpen(true);
  };

  const board = overview?.boards.find((b) => b.board === BOARD_SALES);

  return (
    <div>
      <SalesPageTitle
        description="今天该跟谁、跟到哪一步、下一步做什么"
        actions={
          <>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索客户、单号"
              style={{ width: 220 }}
              onPressEnter={(e) => setKeyword(e.currentTarget.value)}
              onClear={() => setKeyword('')}
            />
            {canTeam && (
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
            {canRecord && (
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
      <FlowCards overview={overview} />
      <TodayList
        today={today}
        canRecord={canRecord}
        onRecord={openRecord}
        onChanged={refresh}
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide ? 'minmax(0, 1fr) 360px' : 'minmax(0, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <Card style={{ padding: '16px 20px' }}>
          <StageBar
            board={board}
            value={filter}
            onChange={setFilter}
            blocker={blocker}
            onBlocker={setBlocker}
          />
          <FollowupTable
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
