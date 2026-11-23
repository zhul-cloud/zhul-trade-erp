import { Eye } from 'lucide-react';
import { BranchesOutlined, SearchOutlined } from '@ant-design/icons';
import { history, Link, useLocation } from '@umijs/max';
import { Input, Select, Table, type TableColumnsType } from 'antd';
import dayjs from 'dayjs';
import { PageHeader } from '@/components/ListLayout';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { liveRequests } from '@/utils/liveRequests';
import { usePageSize, PAGER_STYLE } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { Card, CoverPill, NextCell, PriorityPill, useUsers } from '../components';
import {
  COVER_NAMES,
  type Demand,
  modelName,
  PATHS,
  PRIORITY_NAMES,
  readBizError,
  scopeName,
  supplyApi,
} from '../service';
import { auditColumns } from '@/components/UserName';
import { RowActions, rowActionsWidth } from '@/components/RowActions';

/** 列表筛选与页码放在一起：改筛选和回到第 1 页一次完成，不会先按旧页码多发一次请求（audit UI-090） */
interface Filters {
  priority?: number;
  coverage?: number;
  ownerId?: number;
  /** 只看近 30 / 90 天无货的（矩阵「按无货」点格子带来） */
  stockoutDays?: number;
  keyword: string;
  page: number;
}

/** 从覆盖矩阵格子带来的筛选（audit UI-089） */
const fromUrl = (q: URLSearchParams): Filters => ({
  priority: Number(q.get('priority')) || undefined,
  ownerId: Number(q.get('ownerId')) || undefined,
  stockoutDays: Number(q.get('stockoutDays')) || undefined,
  keyword: '',
  page: 1,
});

/** 覆盖矩阵 → 型号清单（F14）：某品牌（· 品类）下进入矩阵的型号 */
const CoverageModelsPage: React.FC = () => {
  const { palette } = useAppTheme();
  const users = useUsers();
  const { search } = useLocation();
  const q = useMemo(() => new URLSearchParams(search), [search]);
  const brandId = Number(q.get('brandId')) || undefined;
  const categoryId = q.has('categoryId')
    ? Number(q.get('categoryId'))
    : undefined;
  const [f, setF] = useState<Filters>(() => fromUrl(q));
  const [keywordInput, setKeywordInput] = useState('');
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Demand[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [dist, setDist] = useState<{ total: number; coverage: Record<number, number> }>();
  const [scope, setScope] = useState<string>();
  /** 从其他页面返回时加 1，静默重取列表与分布（audit UI-055） */
  const [tick, setTick] = useState(0);
  useOnReactivate(() => setTick((n) => n + 1));
  const change = (patch: Partial<Filters>) => setF((o) => ({ ...o, ...patch, page: 1 }));

  // 地址变了（从矩阵另一个格子进来）按新地址重置筛选
  const firstUrl = useRef(true);
  useEffect(() => {
    if (firstUrl.current) {
      firstUrl.current = false;
      return;
    }
    setF(fromUrl(q));
    setKeywordInput('');
  }, [q]);

  useEffect(() => {
    if (!brandId) return undefined;
    const { live, cancel } = liveRequests();
    live(supplyApi.matrix({ brandId, ownerId: f.ownerId, priority: f.priority }))
      .then((m) => {
        const cells = m.cells.filter((c) => categoryId == null || c.categoryId === categoryId);
        const cov: Record<number, number> = {};
        for (const c of cells) for (const k of [1, 2, 3, 4]) cov[k] = (cov[k] ?? 0) + (c.coverage[k] ?? 0);
        setDist({ total: cells.reduce((s, c) => s + c.total, 0), coverage: cov });
        setScope(
          scopeName({
            brandName: m.brands.find((b) => b.id === brandId)?.name,
            categoryName: m.categories.find((c) => c.id === categoryId)?.name,
          }),
        );
      })
      .catch(() => setDist(undefined));
    return cancel;
  }, [brandId, categoryId, f.ownerId, f.priority, tick]);

  // 只采用最后一次请求的结果，迟到的旧响应丢弃（audit UI-090）
  useEffect(() => {
    setLoading(true);
    const { live, cancel } = liveRequests();
    live(
      supplyApi.models({
        brandId,
        categoryId,
        coverage: f.coverage,
        priority: f.priority,
        ownerId: f.ownerId,
        stockoutDays: f.stockoutDays,
        keyword: f.keyword.trim() || undefined,
        page: f.page,
        size: pageSize,
      }),
    )
      .then((r) => {
        setRows(r.records);
        setTotal(r.total);
        setError('');
      })
      .catch((e) => setError(readBizError(e).message))
      .finally(() => setLoading(false));
    return cancel;
  }, [brandId, categoryId, f, pageSize, tick]);

  const title = scope ?? (rows[0] ? scopeName({ brandName: rows[0].brandName, categoryName: categoryId ? rows[0].categoryName : undefined }) : '型号清单');
  usePageTitle(title);

  const columns: TableColumnsType<Demand> = [
    {
      title: '型号',
      key: 'model',
      width: 200,
      fixed: 'left',
      render: (_, d) => <Link to={PATHS.demand(d.id)}>{modelName(d)}</Link>,
    },
    { title: '优先级', dataIndex: 'priority', width: 80, render: (v: number) => <PriorityPill value={v} /> },
    {
      title: '覆盖状态',
      key: 'cover',
      width: 190,
      render: (_, d) => (
        <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <CoverPill status={d.coverageStatus || undefined} />
          <span style={{ fontSize: 12, color: palette.mute }}>
            {d.coveragePending ? '待确认' : d.coverageNote === '系统降级' ? '系统降级' : ''}
          </span>
        </span>
      ),
    },
    { title: '主渠道', dataIndex: 'primarySupplier', width: 150, render: (v?: string) => v || '—' },
    {
      title: '备选',
      key: 'backup',
      width: 100,
      render: (_, d) =>
        !d.channelCount ? (
          '—'
        ) : d.channelCount === 1 ? (
          <span style={{ color: palette.info }}>
            <BranchesOutlined /> 单一渠道
          </span>
        ) : (
          `${d.channelCount - 1} 个`
        ),
    },
    {
      title: '最近证据',
      key: 'evidence',
      width: 150,
      render: (_, d) =>
        d.evidenceDate ? (
          <div style={{ display: 'grid', color: d.evidenceValid ? undefined : palette.orange }}>
            <span>
              {d.coverageBasis?.startsWith('下单') ? '下单' : '询价'} {dayjs(d.evidenceDate).format('YYYY-MM-DD')}
            </span>
            <span style={{ fontSize: 12, color: d.evidenceValid ? palette.mute : palette.orange }}>
              {d.evidenceValid ? '有效期内' : '已过期'}
            </span>
          </div>
        ) : (
          <span style={{ color: palette.mute }}>{d.cntStockout ? '只有无货记录' : '没有价格记录'}</span>
        ),
    },
    { title: '负责人', dataIndex: 'ownerName', width: 90, render: (v?: string) => v || '—' },
    { title: '下一步行动 · 日期', key: 'next', width: 220, render: (_, d) => <NextCell d={d} /> },
    ...auditColumns<Demand>(),
    {
      title: '操作',
      key: 'op',
      width: rowActionsWidth(1),
      fixed: 'right',
      render: (_, d) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => history.push(PATHS.demand(d.id)),
            },
          ]}
        />
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={title}
        sub={
          f.stockoutDays ? (
            `近 ${f.stockoutDays} 天无货 ${total} 个型号`
          ) : (
            dist && (
              <span style={{ display: 'inline-flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                {dist.total} 个需求
                {[4, 3, 2, 1]
                  .filter((k) => dist.coverage[k])
                  .map((k) => (
                    <span key={k} style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}>
                      <CoverPill status={k} /> {dist.coverage[k]}
                    </span>
                  ))}
              </span>
            )
          )
        }
        extra={
          <>
            <Select
              allowClear
              style={{ width: 120 }}
              placeholder="优先级"
              value={f.priority}
              onChange={(v) => change({ priority: v })}
              options={[3, 2, 1].map((k) => ({ value: k, label: PRIORITY_NAMES[k] }))}
            />
            <Select
              allowClear
              style={{ width: 150 }}
              placeholder="覆盖状态"
              value={f.coverage}
              onChange={(v) => change({ coverage: v })}
              options={[4, 3, 2, 1].map((k) => ({ value: k, label: COVER_NAMES[k] }))}
            />
            <Select
              allowClear
              style={{ width: 140 }}
              placeholder="负责人"
              showSearch={{ optionFilterProp: 'label' }}
              value={f.ownerId}
              onChange={(v) => change({ ownerId: v })}
              options={users}
            />
            <Select
              allowClear
              style={{ width: 140 }}
              placeholder="无货"
              value={f.stockoutDays}
              onChange={(v) => change({ stockoutDays: v })}
              options={[30, 90].map((d) => ({ value: d, label: `近 ${d} 天无货` }))}
            />
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="型号"
              style={{ width: 220 }}
              value={keywordInput}
              onChange={(e) => {
                setKeywordInput(e.target.value);
                if (!e.target.value && f.keyword) change({ keyword: '' });
              }}
              onPressEnter={() => change({ keyword: keywordInput })}
            />
          </>
        }
      />
      <Card style={{ overflow: 'hidden' }}>
        <Table<Demand>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          locale={{ emptyText: error || '没有符合条件的型号' }}
          pagination={{
...PAGER_STYLE,
            current: f.page,
            pageSize,
            total,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setF((o) => ({ ...o, page: s === pageSize ? p : 1 }));
              setPageSize(s);
            },
          }}
        />
      </Card>
    </div>
  );
};

export default CoverageModelsPage;
