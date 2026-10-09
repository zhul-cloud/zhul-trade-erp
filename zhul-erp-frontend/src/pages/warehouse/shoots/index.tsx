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
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import {
  ACCEPT_IMAGE,
  ACCEPT_VIDEO,
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

/** 还缺什么：视频、图片 */
const missingText = (t: ShootTask) => {
  const parts: string[] = [];
  if (t.photoCount < NEED_PHOTOS)
    parts.push(`实物图还差 ${NEED_PHOTOS - t.photoCount} 张`);
  const videos = [
    t.unboxingCount < 1 && '拆箱视频',
    t.inspectionCount < 1 && '验货视频',
  ].filter(Boolean);
  if (videos.length) parts.push(`还缺${videos.join('和')}`);
  return parts.join('，');
};

const Shoots: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const canEdit = !!access['warehouse:shoot:edit'];
  const [status, setStatus] = useState<number | 'all'>(1);
  const [pending, setPending] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
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

  const reuse = async (t: ShootTask) => {
    try {
      await shootApi.reuse(t.id);
      message.success('已复用同型号素材');
      load();
      refreshCount();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

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
        if (r.status === 1 && r.reusable)
          return (
            <div>
              <b style={{ color: palette.link, fontSize: 12 }}>
                已有素材，可复用
              </b>
              {r.reusableShotAt &&
                sub(
                  palette.mute,
                  `上次 ${dayjs(r.reusableShotAt).format('YYYY-MM-DD')} 拍摄`,
                )}
            </div>
          );
        const videos =
          Math.min(1, r.unboxingCount) + Math.min(1, r.inspectionCount);
        const done = r.status === 2;
        return (
          <div>
            <b
              style={{
                fontSize: 12,
                color: done ? palette.green : palette.orange,
              }}
            >
              视频 {videos}/2 · 图片 {Math.min(r.photoCount, NEED_PHOTOS)}/
              {NEED_PHOTOS}
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
          {r.status === 1 && r.reusable && canEdit && (
            <a onClick={() => reuse(r)}>复用</a>
          )}
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
      <WarehousePageTitle
        crumbs={['拍摄任务']}
        title="拍摄任务"
        description="验收入库后拍拆箱视频、验货视频和 6 张实物图，素材齐了就完成；素材归到型号名下，下次同型号可以复用。"
      />
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
          placeholder="型号、品牌、入库单号、订单号"
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
                : '没有符合条件的拍摄任务',
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
        <InfoCircleOutlined /> 这里只负责拍摄；发布到 B2B
        平台、独立站以后在运营管理里做。
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
  { type: 1, title: '拆箱视频', need: 1, accept: ACCEPT_VIDEO },
  { type: 2, title: '验货视频', need: 1, accept: ACCEPT_VIDEO },
  { type: 3, title: '实物图', need: NEED_PHOTOS, accept: ACCEPT_IMAGE },
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
          ? '素材齐了，任务已完成'
          : '素材不齐，回到待拍摄',
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
          ? () => run(() => shootApi.removeMedia(t.id, m.id))
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
            拍摄任务 · {t.model}
            <ShootStatusPill value={t.status}>{t.statusName}</ShootStatusPill>
          </span>
        ) : (
          '拍摄任务'
        )
      }
      footer={
        t && (
          <div style={{ display: 'flex', gap: 8 }}>
            {canEdit && t.status === 1 && (
              <Button onClick={() => setSkipping(true)}>跳过</Button>
            )}
            <span style={{ flex: 1 }} />
            {canEdit && t.status === 1 && t.reusable && (
              <Button onClick={() => run(() => shootApi.reuse(t.id))}>
                复用已有素材
              </Button>
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
                      accept={s.accept}
                      onUploaded={(a) => {
                        queue.current = queue.current.then(() =>
                          run(() => shootApi.addMedia(t.id, a.id, s.type)),
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
            <InfoCircleOutlined /> 拆箱视频、验货视频各 1 个以上，实物图 6
            张以上，齐了任务自动完成。素材会归到「
            {t.brand} {t.model}」名下，以后运营发布、同型号复用都从这里取。
          </div>
          {d.sameModel.length > 0 && (
            <div>
              <b
                style={{
                  color: palette.ink,
                  display: 'block',
                  marginBottom: 8,
                }}
              >
                同型号已有的素材（{d.sameModel.length}）
              </b>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                {d.sameModel.map((m) => tile(m, false))}
              </div>
            </div>
          )}
        </div>
      )}
      {preview.node}
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
