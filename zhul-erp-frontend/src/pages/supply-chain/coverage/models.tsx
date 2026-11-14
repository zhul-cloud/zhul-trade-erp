import { BranchesOutlined, SearchOutlined } from '@ant-design/icons';
import { Link, useLocation } from '@umijs/max';
import { Input, Select, Table, type TableColumnsType } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import { usePageTitle } from '@/components/PageTabs/hooks';
import { PageTitle } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { Card, CoverPill, NextCell, PriorityPill } from '../components';
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

/** 覆盖矩阵 → 型号清单（F14）：某品牌（· 品类）下进入矩阵的型号 */
const CoverageModelsPage: React.FC = () => {
  const { palette } = useAppTheme();
  const { search } = useLocation();
  const q = useMemo(() => new URLSearchParams(search), [search]);
  const brandId = Number(q.get('brandId')) || undefined;
  const categoryId = q.has('categoryId')
    ? Number(q.get('categoryId'))
    : undefined;
  const [priority, setPriority] = useState<number>();
  const [coverage, setCoverage] = useState<number>();
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Demand[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [dist, setDist] = useState<{ total: number; coverage: Record<number, number> }>();
  const [scope, setScope] = useState<string>();

  useEffect(() => {
    if (!brandId) return;
    supplyApi
      .matrix({ brandId })
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
  }, [brandId, categoryId]);

  useEffect(() => setPage(1), [priority, coverage, keyword]);
  useEffect(() => {
    setLoading(true);
    supplyApi
      .models({ brandId, categoryId, coverage, priority, keyword: keyword.trim() || undefined, page, size: pageSize })
      .then((r) => {
        setRows(r.records);
        setTotal(r.total);
        setError('');
      })
      .catch((e) => setError(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [brandId, categoryId, coverage, priority, keyword, page, pageSize]);

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
          <span style={{ color: palette.cyan }}>
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
    { title: '创建时间', dataIndex: 'createTime', width: 170, render: (v: string) => formatDateTime(v) },
    { title: '创建人', dataIndex: 'createBy', width: 90, render: (v?: string) => v || '—' },
    { title: '更新时间', dataIndex: 'updateTime', width: 170, render: (v: string) => formatDateTime(v) },
    { title: '更新人', dataIndex: 'updateBy', width: 90, render: (v?: string) => v || '—' },
    {
      title: '操作',
      key: 'op',
      width: 70,
      fixed: 'right',
      render: (_, d) => <Link to={PATHS.demand(d.id)}>查看</Link>,
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['覆盖矩阵', title]}
        title={title}
        description={
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
        }
        actions={
          <>
            <Select
              allowClear
              style={{ width: 120 }}
              placeholder="优先级"
              value={priority}
              onChange={setPriority}
              options={[3, 2, 1].map((k) => ({ value: k, label: PRIORITY_NAMES[k] }))}
            />
            <Select
              allowClear
              style={{ width: 150 }}
              placeholder="覆盖状态"
              value={coverage}
              onChange={setCoverage}
              options={[4, 3, 2, 1].map((k) => ({ value: k, label: COVER_NAMES[k] }))}
            />
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="型号"
              style={{ width: 220 }}
              value={keywordInput}
              onChange={(e) => {
                setKeywordInput(e.target.value);
                if (!e.target.value) setKeyword('');
              }}
              onPressEnter={() => setKeyword(keywordInput)}
            />
          </>
        }
      />
      <Card style={{ overflow: 'hidden' }}>
        <Table<Demand>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: error || '没有符合条件的型号' }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(s === pageSize ? p : 1);
              setPageSize(s);
            },
          }}
        />
      </Card>
    </div>
  );
};

export default CoverageModelsPage;
