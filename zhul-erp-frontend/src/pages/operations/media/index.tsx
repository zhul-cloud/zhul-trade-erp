import { useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Input, Select, Space, Table } from 'antd';
import dayjs from 'dayjs';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import EllipsisText, { ellipsisRender } from '@/components/EllipsisText';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import MediaDrawer from './MediaDrawer';
import { type MediaModel, mediaApi, readBizError } from './service';

/** 列表上显示的分组（官方资料是共享商品库的，不计在这里） */
const GROUPS = [
  '客户询盘',
  '采购询价',
  '到货验收',
  '营销拍摄',
  '仓库发货',
  '售后反馈',
];

type Tri = 'all' | 'yes' | 'no';

const STATUS_TONES: Record<
  MediaModel['status'],
  'green' | 'accent' | 'orange'
> = {
  listed: 'green',
  marketing: 'accent',
  noMarketing: 'orange',
};
const triValue = (v: Tri) => (v === 'all' ? undefined : v === 'yes');

/**
 * 运营管理 → 素材库：按型号汇总各环节的图片、视频与资料。与其他运营列表一样以商品、品牌、品类、状态开头；
 * 这是按型号汇总的视图，没有单条记录的创建人、更新人，不显示四个审计列；按最近新增素材的时间倒序。
 */
const MediaLibraryPage: React.FC = () => {
  const { palette } = useAppTheme();
  const [search] = useSearchParams();
  const initialKeyword = search.get('keyword') ?? '';
  const [keyword, setKeyword] = useState(initialKeyword);
  const [query, setQuery] = useState<string | undefined>(
    initialKeyword || undefined,
  );
  const [marketing, setMarketing] = useState<Tri>('all');
  const [listing, setListing] = useState<Tri>('all');
  /** 两个条件点「查询」才生效 */
  const [draft, setDraft] = useState<{ marketing: Tri; listing: Tri }>({
    marketing: 'all',
    listing: 'all',
  });
  const runSearch = () => {
    setMarketing(draft.marketing);
    setListing(draft.listing);
    apply();
  };
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<MediaModel[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [open, setOpen] = useState<MediaModel>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const r = await mediaApi.page({
        keyword: query,
        hasMarketing: triValue(marketing),
        hasListing: triValue(listing),
        page,
        pageSize,
      });
      setRows(r.records);
      setTotal(r.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, marketing, listing, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  const apply = () => {
    setQuery(keyword.trim() || undefined);
    setPage(1);
  };

  const columns: TableColumnsType<MediaModel> = [
    {
      title: '商品',
      key: 'model',
      width: 260,
      fixed: 'left',
      render: (_, r) => (
        <div>
          <button
            type="button"
            className="zhul-link"
            onClick={() => setOpen(r)}
          >
            <b>
              {r.brand} · {r.model}
            </b>
          </button>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {r.productName || '—'}
          </div>
        </div>
      ),
    },
    { title: '品牌', dataIndex: 'brand', width: 110, render: ellipsisRender },
    {
      title: '品类',
      dataIndex: 'category',
      width: 120,
      render: (v?: string) => <EllipsisText text={v || '—'} />,
    },
    {
      title: '状态',
      key: 'status',
      width: 130,
      render: (_, r) => (
        <Pill tone={STATUS_TONES[r.status]}>{r.statusName}</Pill>
      ),
    },
    {
      title: '各环节素材',
      key: 'groups',
      width: 240,
      render: (_, r) => (
        <Space size={[6, 6]} wrap>
          {GROUPS.filter((g) => r.groupCounts[g]).map((g) => (
            <Pill key={g} tone="gray">
              {g} {r.groupCounts[g]}
            </Pill>
          ))}
        </Space>
      ),
    },
    {
      title: '可发客户',
      dataIndex: 'sendableCount',
      width: 100,
      align: 'right',
    },
    {
      title: '用于发品',
      dataIndex: 'listingCount',
      width: 100,
      align: 'right',
    },
    {
      title: '最近新增素材',
      dataIndex: 'lastAddedAt',
      width: 170,
      render: (v?: string) =>
        v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—',
    },
    {
      title: '操作',
      key: 'op',
      width: 90,
      fixed: 'right',
      render: (_, r) => (
        <button type="button" className="zhul-link" onClick={() => setOpen(r)}>
          查看素材
        </button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader help="一个型号在询价、到货、拍摄、发货各环节的图片和视频都汇总在这里；可发客户的可以下载发给客户，运营从中挑选用于发品的素材" />
      <FilterCard
        onSearch={runSearch}
        onReset={() => {
          setKeyword('');
          setQuery(undefined);
          setDraft({ marketing: 'all', listing: 'all' });
          setMarketing('all');
          setListing('all');
          setPage(1);
        }}
      >
        <FilterField label="关键词" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={runSearch}
            placeholder="品牌、型号"
            aria-label="关键词"
          />
        </FilterField>
        <FilterField label="营销实拍">
          <Select<Tri>
            value={draft.marketing}
            onChange={(v) => setDraft({ ...draft, marketing: v })}
            options={[
              { value: 'all', label: '全部' },
              { value: 'yes', label: '有营销实拍' },
              { value: 'no', label: '没有营销实拍' },
            ]}
            aria-label="营销实拍"
          />
        </FilterField>
        <FilterField label="发品素材">
          <Select<Tri>
            value={draft.listing}
            onChange={(v) => setDraft({ ...draft, listing: v })}
            options={[
              { value: 'all', label: '全部' },
              { value: 'yes', label: '有用于发品的' },
              { value: 'no', label: '还没选发品素材' },
            ]}
            aria-label="发品素材"
          />
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard left={`共 ${total} 个型号`}>
          <Table<MediaModel>
            rowKey={(r) => `${r.brand}|${r.model}`}
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            locale={{
              emptyText: query
                ? '没有找到这个型号的素材'
                : '还没有素材；询盘、回价、到货、实拍、打包时上传的图片和视频会自动汇总到这里',
            }}
            pagination={{
              ...PAGER_STYLE,
              current: page,
              pageSize,
              total,
              onChange: (p, s) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </MainCard>
      )}
      <MediaDrawer
        open={!!open}
        brand={open?.brand}
        model={open?.model}
        onClose={() => setOpen(undefined)}
        onChanged={load}
      />
    </div>
  );
};

export default MediaLibraryPage;
