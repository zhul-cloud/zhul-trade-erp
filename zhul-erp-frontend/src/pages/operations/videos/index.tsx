import { history, Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Modal, Select, Table, TimePicker } from 'antd';
import dayjs from 'dayjs';
import {
  FolderPlus,
  ListMinus,
  ListPlus,
  RotateCcw,
  Search,
  Settings,
  Upload,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  SelectionCount,
} from '@/components/ListLayout';
import { AlertLabel } from '@/components/MetricCard';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { auditColumns } from '@/components/UserName';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  categoryApi,
} from '@/pages/product/service';
import { useAppTheme } from '@/theme/AppTheme';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
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
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
      {(t.thumbnailIds ?? []).map((id) => (
        <Thumb key={id} url={links[id]?.url} size={28} />
      ))}
      <span
        style={{
          marginLeft: 4,
          fontVariantNumeric: 'tabular-nums',
          // 选用不够的提醒写在状态列（带图标的警告标签），这里只写数
          color: palette.sub,
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
        <div style={{ minWidth: 0 }}>
          <Link to={`/operations/videos/${r.id}`}>
            {r.brand && (
              <span style={{ color: palette.sub, marginRight: 6 }}>
                {r.brand}
              </span>
            )}
            <b>{r.model}</b>
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
      width: rowActionsWidth(1),
      fixed: 'right',
      // 加入队列 / 重新生成 / 移出队列按状态三选一，占一个图标位；点商品名打开详情
      render: (_, r) => (
        <RowActions
          inline={[
            canUpload && r.status === 2
              ? {
                  key: 'dequeue',
                  label: '移出队列',
                  icon: <ListMinus size={18} />,
                  disabledReason: acting === r.id ? '处理中' : undefined,
                  onClick: () =>
                    act(r, () => videoApi.dequeue(r.id), '已移出队列'),
                }
              : canUpload &&
                QUEUEABLE.includes(r.status) && {
                  key: 'queue',
                  label: r.status === 5 ? '重新生成' : '加入队列',
                  icon:
                    r.status === 5 ? (
                      <RotateCcw size={18} />
                    ) : (
                      <ListPlus size={18} />
                    ),
                  disabledReason: acting === r.id ? '处理中' : undefined,
                  onClick: () =>
                    act(r, () => videoApi.queue(r.id), '已加入队列'),
                },
          ]}
        />
      ),
    },
  ];
  const columnsSet = useColumnSettings('operations-videos', columns);

  const reset = () => {
    setKeyword('');
    setBrandId(undefined);
    setCategoryId(undefined);
    setTab('all');
    setQuery({});
    setPage(1);
  };

  return (
    <div>
      <PageHeader
        extra={
          (platform || canUpload) && (
            <>
              {/* 生成时段只有平台账号能改：操作放页头，不放汇总行副文字（窄时副文字会被隐藏） */}
              {platform && (
                <Button
                  type="text"
                  icon={<Settings size={16} />}
                  style={{ color: palette.link }}
                  onClick={() => setWindowEdit(true)}
                >
                  生成设置
                </Button>
              )}
              {canUpload && (
                <Button
                  icon={<FolderPlus size={16} />}
                  onClick={() => setBatchOpen(true)}
                >
                  批量上传
                </Button>
              )}
              {canUpload && (
                <Button
                  type="primary"
                  icon={<Upload size={16} />}
                  onClick={() => setUploadOpen(true)}
                >
                  上传素材
                </Button>
              )}
            </>
          )
        }
        summary={[
          {
            key: 'window',
            label: '夜间生成',
            help: '全服务器同一时间只生成一个，按加入队列的先后',
            value: slot.replace('-', '–'),
            sub: (
              <Pill tone={running ? 'green' : 'gray'}>
                {running ? '运行中' : '未到时段'}
              </Pill>
            ),
          },
        ]}
      />
      <FilterCard onSearch={apply} onReset={reset}>
        <FilterField label="关键词" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={apply}
            placeholder="型号、名称"
            aria-label="关键词"
          />
        </FilterField>
        {/* 全部、待生成……生成失败是同一种工作按进度筛：状态下拉，选项带数量（D61） */}
        <FilterField label="状态">
          <Select<VideoTab>
            value={tab}
            onChange={(v) => {
              setTab(v);
              setPage(1);
              setSelected([]);
            }}
            options={TABS.map((t) => {
              const n = t.key === 'all' ? undefined : counts[t.key];
              return {
                value: t.key,
                label: n ? `${t.label} · ${n}` : t.label,
              };
            })}
            aria-label="状态"
          />
        </FilterField>
        <FilterField label="品牌">
          <Select
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            value={brandId}
            onChange={setBrandId}
            placeholder="全部"
            options={brands.map((b) => ({ value: b.id, label: b.brandName }))}
            aria-label="品牌"
          />
        </FilterField>
        <FilterField label="品类">
          <Select
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            value={categoryId}
            onChange={setCategoryId}
            placeholder="全部"
            options={categories.map((c) => ({
              value: c.id,
              label: c.categoryNameZh || c.categoryName,
            }))}
            aria-label="品类"
          />
        </FilterField>
      </FilterCard>

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          count={selected.length > 0 ? undefined : total}
          unit="个"
          loading={loading}
          settings={columnsSet.settings}
          left={
            selected.length > 0 && (
              <>
                <SelectionCount
                  count={selected.length}
                  unit="个"
                  onClear={() => setSelected([])}
                />
                {selectedShort.length > 0 && (
                  <AlertLabel tone="warning">
                    {selectedShort
                      .slice(0, 2)
                      .map((s) => s.model)
                      .join('、')}
                    {selectedShort.length > 2
                      ? ` 等 ${selectedShort.length} 个`
                      : ''}
                    选用不足 4 张或状态不对，加入时跳过
                  </AlertLabel>
                )}
              </>
            )
          }
          right={
            selected.length > 0 && (
              <Button
                type="primary"
                icon={<ListPlus size={16} />}
                onClick={batchQueue}
              >
                批量加入队列
              </Button>
            )
          }
        >
          <Table<VideoTask>
            rowKey="id"
            columns={columnsSet.columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columnsSet.columns) }}
            rowSelection={
              canUpload
                ? {
                    selectedRowKeys: selected.map((s) => s.id),
                    onChange: (_, rs) => setSelected(rs),
                  }
                : undefined
            }
            locale={{
              emptyText: tableEmpty(
                query.keyword || query.brandId || query.categoryId
                  ? '没有符合条件的视频任务，换个条件试试'
                  : (TABS.find((t) => t.key === tab)?.empty ?? '没有记录'),
                loading,
              ),
            }}
            pagination={{
              ...PAGER_STYLE,
              current: page,
              pageSize,
              total,
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </MainCard>
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
