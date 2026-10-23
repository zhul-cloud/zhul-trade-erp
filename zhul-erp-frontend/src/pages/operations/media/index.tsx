import { SearchOutlined } from '@ant-design/icons';
import { useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Input, Segmented, Space, Table } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { usePageSize } from '@/utils/pageSize';
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
          <a onClick={() => setOpen(r)}>
            <b>
              {r.brand} · {r.model}
            </b>
          </a>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {r.productName || '—'}
          </div>
        </div>
      ),
    },
    { title: '品牌', dataIndex: 'brand', width: 110 },
    {
      title: '品类',
      dataIndex: 'category',
      width: 120,
      render: (v?: string) => v || '—',
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
      render: (_, r) => <a onClick={() => setOpen(r)}>查看素材</a>,
    },
  ];

  return (
    <div>
      <PageTitle description="一个型号在询价、到货、拍摄、发货各环节的图片和视频都汇总在这里；可发客户的可以下载发给客户，运营从中挑选用于发品的素材。" />
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={apply}
          placeholder="品牌、型号"
          style={{ width: 260 }}
          aria-label="关键词"
        />
        <Segmented<Tri>
          value={marketing}
          onChange={(v) => {
            setMarketing(v);
            setPage(1);
          }}
          options={[
            { value: 'all', label: '全部' },
            { value: 'yes', label: '有营销实拍' },
            { value: 'no', label: '没有营销实拍' },
          ]}
        />
        <Segmented<Tri>
          value={listing}
          onChange={(v) => {
            setListing(v);
            setPage(1);
          }}
          options={[
            { value: 'all', label: '全部' },
            { value: 'yes', label: '有用于发品的' },
            { value: 'no', label: '还没选发品素材' },
          ]}
        />
        <span style={{ flex: 1 }} />
        <Button type="primary" onClick={apply}>
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<MediaModel>
          rowKey={(r) => `${r.brand}|${r.model}`}
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1100 }}
          locale={{
            emptyText: query
              ? '没有找到这个型号的素材'
              : '还没有素材；询盘、回价、到货、实拍、打包时上传的图片和视频会自动汇总到这里',
          }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 个型号`,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
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
