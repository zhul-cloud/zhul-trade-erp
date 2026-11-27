import {
  ExclamationCircleOutlined,
  FolderAddOutlined,
  MoonOutlined,
  OrderedListOutlined,
  SearchOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { history, Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Checkbox,
  Input,
  Modal,
  Select,
  Table,
  Tabs,
  TimePicker,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  categoryApi,
} from '@/pages/product/service';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { usePageSize } from '@/utils/pageSize';
import { isPlatformAccount } from '@/utils/platform';
import { tableScrollX } from '@/utils/tableScroll';
import {
  StatusCell,
  TABS,
  Thumb,
  useTaskLinks,
  windowOpen,
} from './components';
import {
  LIMITS,
  PERM_UPLOAD,
  QUEUEABLE,
  readBizError,
  type VideoQuery,
  type VideoTab,
  type VideoTask,
  videoApi,
} from './service';
import { BatchUploadModal, UploadModal } from './UploadDialogs';

/** 素材缩略图：选用的前 3 张 + 选用/总数 */
const MaterialCell: React.FC<{ task: VideoTask }> = ({ task: t }) => {
  const { palette } = useAppTheme();
  const links = useTaskLinks(t.id, t.thumbnailIds ?? []);
  const short = t.usedCount < LIMITS.minShots;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
      {(t.thumbnailIds ?? []).map((id) => (
        <Thumb key={id} url={links[id]?.url} size={28} />
      ))}
      <span
        style={{
          marginLeft: 4,
          fontVariantNumeric: 'tabular-nums',
          color: short ? palette.orange : palette.sub,
        }}
      >
        {t.usedCount} / {t.imageCount}
      </span>
    </span>
  );
};

/** 已通过的竖屏视频封面，点击新窗口播放 */
const VideoCell: React.FC<{ task: VideoTask }> = ({ task: t }) => {
  const ids = [t.coverAttachmentId, t.videoAttachmentId].filter(
    (x): x is number => !!x,
  );
  const links = useTaskLinks(t.videoAttachmentId ? t.id : undefined, ids);
  if (!t.videoAttachmentId) return <span>—</span>;
  const play = links[t.videoAttachmentId]?.url;
  return (
    <Thumb
      video
      width={28}
      height={44}
      url={t.coverAttachmentId ? links[t.coverAttachmentId]?.url : undefined}
      title="播放"
      onClick={play ? () => window.open(play, '_blank', 'noopener') : undefined}
    />
  );
};

/** 生成时段设置（只有平台账号能改） */
const WindowModal: React.FC<{
  value: string;
  open: boolean;
  onClose: (saved?: string) => void;
}> = ({ value, open, onClose }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [start, end] = value.split('-');
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs(start, 'HH:mm'),
    dayjs(end, 'HH:mm'),
  ]);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (open) setRange([dayjs(start, 'HH:mm'), dayjs(end, 'HH:mm')]);
  }, [open, start, end]);
  const text = `${range[0].format('HH:mm')}-${range[1].format('HH:mm')}`;
  return (
    <Modal
      title="生成设置"
      open={open}
      onCancel={() => onClose()}
      okText="保存"
      confirmLoading={saving}
      onOk={async () => {
        setSaving(true);
        try {
          const r = await videoApi.saveWindow(text);
          message.success('已保存');
          onClose(r.window);
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setSaving(false);
        }
      }}
    >
      <div style={{ display: 'grid', gap: 10 }}>
        <div>每天生成时段</div>
        <TimePicker.RangePicker
          format="HH:mm"
          order={false}
          value={range}
          allowClear={false}
          onChange={(v) =>
            v?.[0] &&
            v[1] &&
            setRange([v[0], v[1]] as [dayjs.Dayjs, dayjs.Dayjs])
          }
          aria-label="生成时段"
        />
        <div style={{ fontSize: 12, color: palette.mute }}>
          结束早于开始表示跨零点（如
          20:00–07:00）；开始等于结束表示全天。全服务器所有公司共用一个队列，同一时间只生成一个。
        </div>
      </div>
    </Modal>
  );
};

/** 运营管理 → 视频任务：每个商品一个任务，上传素材、写脚本，夜间排队生成竖屏和横屏视频，审核通过进素材库 */
const VideoTasksPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canUpload = !!access[PERM_UPLOAD];
  const platform = isPlatformAccount();
  const [tab, setTab] = useState<VideoTab>('all');
  const [keyword, setKeyword] = useState('');
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [query, setQuery] = useState<VideoQuery>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<VideoTask[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<Partial<Record<VideoTab, number>>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [brands, setBrands] = useState<BrandOption[]>([]);
  const [categories, setCategories] = useState<CategoryOption[]>([]);
  const [selected, setSelected] = useState<VideoTask[]>([]);
  const [slot, setSlot] = useState('20:00-07:00');
  const [windowEdit, setWindowEdit] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [batchOpen, setBatchOpen] = useState(false);
  const [acting, setActing] = useState<number>();

  useEffect(() => {
    brandApi
      .options()
      .then(setBrands)
      .catch(() => setBrands([]));
    categoryApi
      .options(1)
      .then(setCategories)
      .catch(() => setCategories([]));
    videoApi
      .window()
      .then((w) => setSlot(w.window))
      .catch(() => undefined);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const [res, c] = await Promise.all([
        videoApi.page({ ...query, tab, page, pageSize }),
        videoApi.counts(query),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
      setSelected((s) =>
        s.filter((x) => res.records.some((r) => r.id === x.id)),
      );
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, tab, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const apply = () => {
    setQuery({ keyword: keyword.trim() || undefined, brandId, categoryId });
    setPage(1);
  };

  const act = async (t: VideoTask, fn: () => Promise<unknown>, ok: string) => {
    setActing(t.id);
    try {
      await fn();
      message.success(ok);
      load();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setActing(undefined);
    }
  };

  const batchQueue = async () => {
    try {
      const r = await videoApi.batchQueue(selected.map((s) => s.id));
      if (r.skipped.length) {
        modal.info({
          title: `已加入队列 ${r.queued} 个，跳过 ${r.skipped.length} 个`,
          content: (
            <div style={{ display: 'grid', gap: 4 }}>
              {r.skipped.map((s) => (
                <div key={s.id}>
                  <b>{s.model}</b>：{s.reason}
                </div>
              ))}
            </div>
          ),
        });
      } else {
        message.success(`已加入队列 ${r.queued} 个`);
      }
      setSelected([]);
      load();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const running = windowOpen(slot);
  const selectedShort = selected.filter(
    (s) => s.usedCount < LIMITS.minShots || !QUEUEABLE.includes(s.status),
  );

  const columns: TableColumnsType<VideoTask> = [
    {
      title: '商品',
      key: 'product',
      width: 260,
      fixed: 'left',
      render: (_, r) => (
        <div>
          <Link to={`/operations/videos/${r.id}`}>
            <span style={{ color: palette.sub }}>{r.brand}</span>
            <b style={{ marginLeft: 6 }}>{r.model}</b>
          </Link>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {r.productName || '—'}
          </div>
        </div>
      ),
    },
    {
      title: '品类',
      dataIndex: 'category',
      width: 110,
      render: (v?: string) => v || '—',
    },
    {
      title: '素材',
      key: 'materials',
      width: 170,
      render: (_, r) => <MaterialCell task={r} />,
    },
    {
      title: '状态',
      key: 'status',
      width: 220,
      render: (_, r) => <StatusCell task={r} />,
    },
    {
      title: '视频',
      key: 'video',
      width: 70,
      render: (_, r) => <VideoCell task={r} />,
    },
    ...auditColumns<VideoTask>(),
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, r) => (
        <span style={{ display: 'inline-flex', gap: 12 }}>
          <Link to={`/operations/videos/${r.id}`}>详情</Link>
          {canUpload && r.status === 2 && (
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              loading={acting === r.id}
              onClick={() => act(r, () => videoApi.dequeue(r.id), '已移出队列')}
            >
              移出队列
            </Button>
          )}
          {canUpload && QUEUEABLE.includes(r.status) && (
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              loading={acting === r.id}
              onClick={() => act(r, () => videoApi.queue(r.id), '已加入队列')}
            >
              {r.status === 5 ? '重新生成' : '加入队列'}
            </Button>
          )}
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageTitle
        actions={
          canUpload && (
            <span style={{ display: 'inline-flex', gap: 8 }}>
              <Button
                icon={<FolderAddOutlined />}
                onClick={() => setBatchOpen(true)}
              >
                批量上传
              </Button>
              <Button
                type="primary"
                icon={<UploadOutlined />}
                onClick={() => setUploadOpen(true)}
              >
                上传素材
              </Button>
            </span>
          )
        }
      />
      <Card style={{ padding: '14px 18px', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span
            style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: palette.violetSoft,
              color: palette.violet,
            }}
          >
            <MoonOutlined />
          </span>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <b>夜间生成 {slot.replace('-', '–')}</b>
              <Pill tone={running ? 'green' : 'gray'}>
                {running ? '运行中' : '未到时段'}
              </Pill>
            </div>
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
              本公司排队 {counts.queued ?? 0} 个 · 待审核 {counts.review ?? 0}{' '}
              个 · 全服务器同一时间只生成一个，按加入队列的先后
            </div>
          </div>
          {platform && (
            <Button
              type="text"
              icon={<SettingOutlined />}
              onClick={() => setWindowEdit(true)}
            >
              生成设置
            </Button>
          )}
        </div>
      </Card>

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: '4px 18px 18px' }}>
          <Tabs
            activeKey={tab}
            onChange={(k) => {
              setTab(k as VideoTab);
              setPage(1);
            }}
            items={TABS.map((t) => ({
              key: t.key,
              label:
                counts[t.key] !== undefined
                  ? `${t.label} ${counts[t.key]}`
                  : t.label,
            }))}
          />
          <div
            style={{
              display: 'flex',
              gap: 12,
              alignItems: 'center',
              flexWrap: 'wrap',
              marginBottom: 12,
            }}
          >
            <Input
              allowClear
              prefix={<SearchOutlined />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={apply}
              placeholder="搜索型号、名称"
              style={{ width: 220 }}
              aria-label="关键词"
            />
            <Select
              allowClear
              showSearch={{ optionFilterProp: 'label' }}
              value={brandId}
              onChange={setBrandId}
              placeholder="品牌"
              options={brands.map((b) => ({ value: b.id, label: b.brandName }))}
              style={{ width: 160 }}
              aria-label="品牌"
            />
            <Select
              allowClear
              showSearch={{ optionFilterProp: 'label' }}
              value={categoryId}
              onChange={setCategoryId}
              placeholder="品类"
              options={categories.map((c) => ({
                value: c.id,
                label: c.categoryNameZh || c.categoryName,
              }))}
              style={{ width: 160 }}
              aria-label="品类"
            />
            <Button onClick={apply}>查询</Button>
          </div>
          {selected.length > 0 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '8px 14px',
                marginBottom: 12,
                borderRadius: 10,
                border: `1px solid ${palette.accentLine}`,
                background: palette.accentSoft,
              }}
            >
              <Checkbox checked onChange={() => setSelected([])}>
                <b>已选 {selected.length} 个</b>
              </Checkbox>
              {selectedShort.length > 0 && (
                <span style={{ fontSize: 12, color: palette.orange }}>
                  <ExclamationCircleOutlined />{' '}
                  {selectedShort
                    .slice(0, 2)
                    .map((s) => s.model)
                    .join('、')}
                  {selectedShort.length > 2
                    ? ` 等 ${selectedShort.length} 个`
                    : ''}
                  不能加入（选用不足 4 张或状态不对），加入时会跳过
                </span>
              )}
              <span style={{ flex: 1 }} />
              <Button type="text" onClick={() => setSelected([])}>
                取消选择
              </Button>
              <Button
                type="primary"
                icon={<OrderedListOutlined />}
                onClick={batchQueue}
              >
                批量加入队列
              </Button>
            </div>
          )}
          <Table<VideoTask>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            rowSelection={
              canUpload
                ? {
                    selectedRowKeys: selected.map((s) => s.id),
                    onChange: (_, rs) => setSelected(rs),
                  }
                : undefined
            }
            locale={{
              emptyText: TABS.find((t) => t.key === tab)?.empty ?? '没有记录',
            }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t: number) => `共 ${t} 个任务`,
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </Card>
      )}

      <UploadModal
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        onDone={async (productId) => {
          setUploadOpen(false);
          try {
            const t = await videoApi.byProduct(productId);
            if (t) history.push(`/operations/videos/${t.id}`);
            else load();
          } catch {
            load();
          }
        }}
      />
      <BatchUploadModal
        open={batchOpen}
        onClose={(changed) => {
          setBatchOpen(false);
          if (changed) load();
        }}
      />
      {platform && (
        <WindowModal
          value={slot}
          open={windowEdit}
          onClose={(saved) => {
            setWindowEdit(false);
            if (saved) setSlot(saved);
          }}
        />
      )}
    </div>
  );
};

export default VideoTasksPage;
