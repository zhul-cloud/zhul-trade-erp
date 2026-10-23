import { InfoCircleOutlined, SearchOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Drawer,
  Input,
  Segmented,
  Skeleton,
  Space,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import MediaDrawer from '@/pages/operations/media/MediaDrawer';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { usePageSize } from '@/utils/pageSize';
import {
  ACCEPT_IMAGE,
  MediaTile,
  Pill,
  ReasonModal,
  ShootStatusPill,
  sub,
  UploadTile,
  usePreview,
  WarehousePageTitle,
} from '../components';
import {
  readBizError,
  type ShootDetail,
  type ShootMedia,
  type ShootTask,
  shootApi,
} from '../service';

const NEED_PHOTOS = 6;

/** 还缺几张营销图 */
const missingText = (t: ShootTask) =>
  t.photoCount < NEED_PHOTOS
    ? `营销图还差 ${NEED_PHOTOS - t.photoCount} 张`
    : '';

const Shoots: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess();
  const canEdit = !!access['warehouse:shoot:edit'];
  const [status, setStatus] = useState<number | 'all'>(1);
  const [pending, setPending] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<ShootTask[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [open, setOpen] = useState<number>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await shootApi.page({
        keyword: query,
        status: status === 'all' ? undefined : status,
        page,
        pageSize,
      });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, status, page, pageSize]);
  const refreshCount = useCallback(() => {
    shootApi
      .counts()
      .then((c) => setPending(c.pending))
      .catch(() => setPending(undefined));
  }, []);
  useEffect(() => {
    load();
  }, [load]);
  useEffect(refreshCount, [refreshCount]);

  const columns: TableColumnsType<ShootTask> = [
    {
      title: '型号 · 品牌 · 品类',
      dataIndex: 'model',
      width: 220,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <b style={{ color: palette.ink }}>{v}</b>
          {sub(
            palette.mute,
            [r.brand, r.category].filter(Boolean).join(' · ') || '—',
          )}
        </div>
      ),
    },
    {
      title: '来源',
      key: 'source',
      width: 180,
      render: (_, r) => (
        <div>
          <span style={{ color: palette.link }}>{r.grNo}</span>
          {r.soNo && sub(palette.mute, r.soNo)}
        </div>
      ),
    },
    {
      title: '素材',
      key: 'media',
      width: 240,
      render: (_, r) => {
        if (r.status === 3) return sub(palette.mute, r.skipReason);
        if (r.reusedFromTaskId)
          return sub(palette.green, '复用了同型号已有素材');
        const done = r.status === 2;
        return (
          <div>
            <b
              style={{
                fontSize: 12,
                color: done ? palette.green : palette.orange,
              }}
            >
              营销图 {Math.min(r.photoCount, NEED_PHOTOS)}/{NEED_PHOTOS}
              {r.photoCount > NEED_PHOTOS &&
                ` (+${r.photoCount - NEED_PHOTOS})`}
            </b>
            {!done && sub(palette.orange, missingText(r))}
          </div>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: number, r) => (
        <ShootStatusPill value={v}>{r.statusName}</ShootStatusPill>
      ),
    },
    {
      title: '拍摄人',
      dataIndex: 'shooterName',
      width: 90,
      render: (v?: string) =>
        v || <span style={{ color: palette.mute }}>—</span>,
    },
    ...auditColumns<ShootTask>(),
    {
      title: '操作',
      key: 'actions',
      width: 110,
      fixed: 'right',
      render: (_, r) => (
        <Space size={12}>
          <a onClick={() => setOpen(r.id)}>
            {r.status === 1 && canEdit ? '拍摄' : '查看'}
          </a>
        </Space>
      ),
    },
  ];

  const apply = () => {
    setQuery(keyword.trim() || undefined);
    setPage(1);
  };

  return (
    <div>
      <WarehousePageTitle description="还没有营销图的型号到货后，拍 6 张带公司 Logo 摆台的营销图就完成；每个型号拍一次，拍好的图进素材库，以后同型号一直可以用。验货照片和视频在验收入库时上传。" />
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Segmented<number | 'all'>
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
          options={[
            { value: 1, label: pending ? `待拍摄 · ${pending}` : '待拍摄' },
            { value: 2, label: '已完成' },
            { value: 3, label: '已跳过' },
            { value: 'all', label: '全部' },
          ]}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={apply}
          placeholder="型号、品牌、采购入库单号、订单号"
          style={{ width: 300 }}
          aria-label="关键词"
        />
        <span style={{ flex: 1 }} />
        <Button type="primary" onClick={apply}>
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<ShootTask>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1700 }}
          locale={{
            emptyText:
              status === 1
                ? '没有待拍摄的任务；验收入库后合格的型号会出现在这里'
                : '没有符合条件的实拍任务',
          }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        <InfoCircleOutlined /> 这里只负责拍摄；拍好的营销图在「运营管理 →
        素材库」里，运营从中挑选用于发品的图片。
      </div>
      <ShootDrawer
        id={open}
        canEdit={canEdit}
        onClose={() => setOpen(undefined)}
        onChanged={() => {
          load();
          refreshCount();
        }}
      />
    </div>
  );
};

// ---------------------------------------------------------------- 拍摄抽屉

const SLOTS = [
  { type: 3, title: '营销图', need: NEED_PHOTOS, accept: ACCEPT_IMAGE },
];

const ShootDrawer: React.FC<{
  id?: number;
  canEdit: boolean;
  onClose: () => void;
  onChanged: () => void;
}> = ({ id, canEdit, onClose, onChanged }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const preview = usePreview();
  const [d, setD] = useState<ShootDetail>();
  const [error, setError] = useState<string>();
  const [skipping, setSkipping] = useState(false);
  const [showMedia, setShowMedia] = useState(false);
  const access = useAccess();
  /** 素材库菜单：拍摄时参考这个型号已有的验货记录、供方实拍等 */
  const canViewMedia = !!access.operationsMedia;
  /** 多个文件同时上传完时，挂到任务上的请求排队发，界面按最后一次结果显示 */
  const queue = useRef<Promise<void>>(Promise.resolve());

  useEffect(() => {
    if (id === undefined) return;
    setD(undefined);
    setError(undefined);
    shootApi
      .detail(id)
      .then(setD)
      .catch((e) => setError(readBizError(e).message));
  }, [id]);

  const update = (next: ShootDetail) => {
    if (d && next.task.status !== d.task.status) {
      message.success(
        next.task.status === 2
          ? '营销图够 6 张，任务已完成'
          : '营销图不足 6 张，回到待拍摄',
      );
    }
    setD(next);
    onChanged();
  };
  const run = async (fn: () => Promise<ShootDetail>) => {
    try {
      update(await fn());
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const t = d?.task;
  const editable = canEdit && !!t && t.status !== 3 && !t.reusedFromTaskId;
  const of = (type: number) =>
    d?.media.filter((m) => m.mediaType === type) ?? [];
  const tile = (m: ShootMedia, removable: boolean) => (
    <MediaTile
      key={m.id}
      attachment={{ id: m.attachmentId, fileName: m.fileName, kind: m.kind }}
      onOpen={() =>
        preview.open({ id: m.attachmentId, fileName: m.fileName, kind: m.kind })
      }
      onRemove={
        removable && t
          ? () => run(() => shootApi.removeMedia(t.id, m.attachmentId))
          : undefined
      }
    />
  );

  return (
    <Drawer
      open={id !== undefined}
      onClose={onClose}
      size="min(760px, 96vw)"
      destroyOnHidden
      title={
        t ? (
          <span
            style={{ display: 'inline-flex', gap: 10, alignItems: 'center' }}
          >
            实拍任务 · {t.model}
            <ShootStatusPill value={t.status}>{t.statusName}</ShootStatusPill>
          </span>
        ) : (
          '实拍任务'
        )
      }
      footer={
        t && (
          <div style={{ display: 'flex', gap: 8 }}>
            {canEdit && t.status === 1 && (
              <Button onClick={() => setSkipping(true)}>跳过</Button>
            )}
            <span style={{ flex: 1 }} />
            {canViewMedia && (
              <Button onClick={() => setShowMedia(true)}>这个型号的素材</Button>
            )}
            <Button
              type={t.status === 2 ? 'primary' : 'default'}
              onClick={onClose}
            >
              关闭
            </Button>
          </div>
        )
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={onClose} />
      ) : !t || !d ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 14 }}>
          <div style={{ color: palette.mute }}>
            {[t.brand, t.category, t.grNo, t.soNo].filter(Boolean).join(' · ')}
          </div>
          {t.status === 3 && (
            <div style={{ color: palette.sub }}>已跳过：{t.skipReason}</div>
          )}
          {t.reusedFromTaskId && (
            <div style={{ color: palette.green }}>复用了同型号已有的素材</div>
          )}
          {SLOTS.map((s) => {
            const list = of(s.type);
            const ok = list.length >= s.need;
            return (
              <div
                key={s.type}
                style={{
                  padding: '14px 16px',
                  borderRadius: 12,
                  background: palette.inset,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    gap: 8,
                    alignItems: 'center',
                    marginBottom: 10,
                  }}
                >
                  <b style={{ color: palette.ink }}>{s.title}</b>
                  <Pill tone={ok ? 'green' : 'orange'}>
                    {list.length}/{s.need}
                  </Pill>
                </div>
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                  {list.map((m) => tile(m, editable))}
                  {editable && (
                    <UploadTile
                      ownerType="SHOOT"
                      ownerId={t.id}
                      accept={s.accept}
                      onUploaded={(a) => {
                        queue.current = queue.current.then(() =>
                          run(() => shootApi.addMedia(t.id, a.id)),
                        );
                      }}
                    />
                  )}
                  {!editable && list.length === 0 && (
                    <span style={{ fontSize: 12, color: palette.mute }}>
                      没有
                    </span>
                  )}
                </div>
              </div>
            );
          })}
          <div
            style={{
              padding: '10px 12px',
              borderRadius: 8,
              background: palette.inset,
              fontSize: 12,
              color: palette.mute,
            }}
          >
            <InfoCircleOutlined /> 拍 6 张带公司 Logo
            摆台的营销图，够了任务自动完成。图片归到「{t.brand} {t.model}
            」名下，进素材库「营销实拍」，以后同型号到货不再需要拍。
          </div>
        </div>
      )}
      {preview.node}
      <MediaDrawer
        open={showMedia}
        brand={t?.brand}
        model={t?.model}
        onClose={() => setShowMedia(false)}
      />
      <ReasonModal
        open={skipping}
        title="跳过拍摄"
        placeholder="如：客户不需要、重复入库"
        okText="跳过"
        onCancel={() => setSkipping(false)}
        onOk={async (reason) => {
          if (!t) return;
          await run(() => shootApi.skip(t.id, reason));
          setSkipping(false);
        }}
      />
    </Drawer>
  );
};

export default Shoots;
