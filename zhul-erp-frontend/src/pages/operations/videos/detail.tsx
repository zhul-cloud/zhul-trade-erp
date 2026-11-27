import { history, Link, useAccess, useParams } from '@umijs/max';
import { Alert, App, Button, Input, Modal, Select, Upload } from 'antd';
import dayjs from 'dayjs';
import {
  ArrowRight,
  Check,
  CircleAlert,
  CircleCheck,
  Image as ImageIcon,
  LoaderCircle,
  Plus,
  Trash2,
  Undo2,
  Zap,
} from 'lucide-react';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import { PageHeader } from '@/components/ListLayout';
import {
  useOnReactivate,
  usePageTitle,
  useTabActive,
  useUnsavedGuard,
} from '@/components/PageTabs/hooks';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { STATUS_TONE, Thumb, useTaskLinks } from './components';
import {
  captionSeconds,
  checkImage,
  LIMITS,
  LOCKED,
  PERM_REVIEW,
  PERM_UPLOAD,
  QUEUEABLE,
  readBizError,
  resizeForUpload,
  type VideoOutput,
  type VideoScript,
  type VideoTaskDetail,
  videoApi,
} from './service';

const ASPECT_LABEL: Record<string, string> = {
  '9:16': '竖屏 9:16',
  '16:9': '横屏 16:9',
};
const ASPECT_USE: Record<string, string> = {
  '9:16': 'YouTube Shorts、Facebook Reels、领英移动端',
  '16:9': 'YouTube 正片、独立站商品页',
};

const fmtDuration = (s?: number) =>
  s ? `0:${String(Math.round(s)).padStart(2, '0')}` : '';

const emptyScript = (): VideoScript => ({ title: '', shots: [] });

/** 两个画幅并排：竖屏窄、横屏宽 */
const Outputs: React.FC<{
  outputs: VideoOutput[];
  links: Record<number, { url: string }>;
}> = ({ outputs, links }) => {
  const { palette } = useAppTheme();
  const sorted = [...outputs].sort((a) => (a.aspect === '9:16' ? -1 : 1));
  return (
    <div
      style={{
        display: 'flex',
        gap: 20,
        alignItems: 'flex-start',
        flexWrap: 'wrap',
      }}
    >
      {sorted.map((o) => {
        const portrait = o.aspect === '9:16';
        const w = portrait ? 200 : 420;
        const h = portrait ? 356 : 236;
        return (
          <div key={o.id} style={{ width: w }}>
            {/* biome-ignore lint/a11y/useMediaCaption: 字幕已烧录在画面里 */}
            <video
              controls
              preload="none"
              poster={
                o.coverAttachmentId
                  ? links[o.coverAttachmentId]?.url
                  : undefined
              }
              src={links[o.videoAttachmentId]?.url}
              style={{
                width: w,
                height: h,
                borderRadius: 12,
                // lint-ui-ignore hardcoded-color: 视频播放区的黑底属于画面本身（上下留边），不随主题变
                background: '#000',
                objectFit: 'contain',
              }}
            />
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                marginTop: 6,
              }}
            >
              <b>{ASPECT_LABEL[o.aspect] ?? o.aspect}</b>
              <span style={{ color: palette.mute }}>
                {fmtDuration(o.durationSec)}
              </span>
            </div>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {ASPECT_USE[o.aspect]}
            </div>
          </div>
        );
      })}
    </div>
  );
};

/** 运营管理 → 视频任务 → 详情：素材、脚本、生成结果与审核 */
const VideoTaskDetailPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const confirmDanger = useConfirmDanger();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canUpload = !!access[PERM_UPLOAD];
  const canReview = !!access[PERM_REVIEW];
  const active = useTabActive();
  const id = Number(useParams<{ id: string }>().id);
  const [data, setData] = useState<VideoTaskDetail>();
  const [error, setError] = useState<string>();
  const [draft, setDraft] = useState<VideoScript>(emptyScript());
  const [busy, setBusy] = useState<string>();
  const [uploading, setUploading] = useState(0);
  const [dragIndex, setDragIndex] = useState<number>();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState('');
  usePageTitle(
    data ? `视频任务 ${data.model}` : undefined,
    data?.model ?? undefined,
  );

  const saved = useMemo(
    () => JSON.stringify(data?.script ?? emptyScript()),
    [data?.script],
  );
  const dirty = !!data && JSON.stringify(draft) !== saved;
  useUnsavedGuard(dirty);

  const apply = useCallback((d: VideoTaskDetail, keepDraft = false) => {
    setData(d);
    if (!keepDraft) setDraft(d.script ?? emptyScript());
  }, []);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const d = await videoApi.detail(id);
      setData((prev) => prev ?? d);
      apply(d);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id, apply]);

  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(() => {
    if (!dirty) load();
  });

  // AI 写脚本、排队、生成中时定时刷新；有没保存的脚本修改时不覆盖草稿
  const waiting =
    !!data &&
    (data.scriptStatus === 1 || data.status === 2 || data.status === 3);
  useEffect(() => {
    if (!waiting || !active) return undefined;
    const t = setInterval(async () => {
      try {
        apply(await videoApi.detail(id), dirty);
      } catch {
        /* 下次再取 */
      }
    }, 8000);
    return () => clearInterval(t);
  }, [waiting, active, id, dirty, apply]);

  const materialIds = data?.materials.map((m) => m.attachmentId) ?? [];
  const outputIds = useMemo(() => {
    const ids: number[] = [];
    for (const o of [
      ...(data?.outputs ?? []),
      ...(data?.approvedOutputs ?? []),
    ]) {
      ids.push(o.videoAttachmentId);
      if (o.coverAttachmentId) ids.push(o.coverAttachmentId);
    }
    return Array.from(new Set(ids));
  }, [data?.outputs, data?.approvedOutputs]);
  const links = useTaskLinks(data?.id, [...materialIds, ...outputIds]);

  const run = async (
    key: string,
    fn: () => Promise<VideoTaskDetail>,
    ok?: string,
    keepDraft = false,
  ) => {
    setBusy(key);
    try {
      apply(await fn(), keepDraft);
      if (ok) message.success(ok);
      return true;
    } catch (e) {
      message.error(readBizError(e).message);
      return false;
    } finally {
      setBusy(undefined);
    }
  };

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!data) return null;

  const locked = LOCKED.includes(data.status);
  const editable = canUpload && !locked;
  const used = data.materials.filter((m) => m.used);
  const usedIndex = new Map(used.map((m, i) => [m.attachmentId, i + 1]));
  const shotSeconds = draft.shots.map((s) => captionSeconds(s.caption));
  const total = Math.round(shotSeconds.reduce((a, b) => a + b, 0) * 10) / 10;
  const totalOk =
    total >= LIMITS.minSeconds &&
    total <= LIMITS.maxSeconds &&
    draft.shots.length >= LIMITS.minShots &&
    draft.shots.length <= LIMITS.maxShots;
  const current = data.outputs;
  const currentVersion = current[0]?.version;
  const approvedVersion = data.approvedOutputs[0]?.version;
  const regenerating =
    data.approvedOutputs.length > 0 && approvedVersion !== currentVersion;

  const saveMaterials = (list: typeof data.materials) =>
    run(
      'materials',
      () =>
        videoApi.saveMaterials(
          data.id,
          list.map((m) => ({ attachmentId: m.attachmentId, used: m.used })),
        ),
      undefined,
      true,
    );

  const onDrop = (to: number) => {
    if (dragIndex === undefined || dragIndex === to) return;
    const list = [...data.materials];
    const [m] = list.splice(dragIndex, 1);
    list.splice(to, 0, m);
    setDragIndex(undefined);
    setData({ ...data, materials: list });
    saveMaterials(list);
  };

  const addImages = async (files: File[]) => {
    const room = LIMITS.maxImages - data.materials.length;
    const list = files.slice(0, Math.max(room, 0));
    if (files.length > list.length)
      message.warning(`一个任务最多 ${LIMITS.maxImages} 张，多出的没有上传`);
    setUploading(list.length);
    for (const f of list) {
      const err = checkImage(f);
      if (err) {
        message.error(`${f.name}：${err}`);
      } else {
        try {
          const { blob, name } = await resizeForUpload(f);
          await videoApi.upload(data.productId, blob, name);
        } catch (e) {
          message.error(`${f.name}：${readBizError(e).message}`);
        }
      }
      setUploading((n) => n - 1);
    }
    try {
      apply(await videoApi.detail(id), true);
    } catch {
      /* 刷新失败不影响 */
    }
  };

  const setShot = (i: number, change: Partial<VideoScript['shots'][number]>) =>
    setDraft((d) => ({
      ...d,
      shots: d.shots.map((s, j) => (j === i ? { ...s, ...change } : s)),
    }));

  const generate = () => {
    const go = () =>
      run('generate', () => videoApi.generateScript(data.id), 'AI 正在写脚本');
    if (draft.shots.length || draft.title) {
      modal.confirm({
        title: '重新生成脚本？',
        content: '现在的标题和字幕会被 AI 写的新脚本替换。',
        okText: '重新生成',
        onOk: go,
      });
    } else {
      go();
    }
  };

  const queue = async () => {
    if (dirty) {
      const ok = await run(
        'script',
        () => videoApi.saveScript(data.id, draft),
        undefined,
      );
      if (!ok) return;
    }
    run('queue', () => videoApi.queue(data.id), '已加入队列');
  };

  const nextReview = async () => {
    try {
      const next = await videoApi.nextReview(data.id);
      if (next) history.push(`/operations/videos/${next}`);
      else message.info('没有其他待审核的视频了');
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  return (
    <div>
      <PageHeader
        title={`${data.brand ?? ''} ${data.model ?? ''}`.trim()}
        tags={
          <>
            <Pill tone={STATUS_TONE[data.status]}>
              {data.statusName}
              {data.status === 2 && data.queuePosition
                ? ` · 第 ${data.queuePosition} 位`
                : ''}
            </Pill>
            {data.factsUnverified && <Pill tone="orange">资料未核对</Pill>}
          </>
        }
        sub={[
          data.productName,
          data.category,
          data.status === 2 && data.expectedAt
            ? `预计 ${dayjs(data.expectedAt).format('MM-DD HH:mm')} 开始`
            : undefined,
          data.factsUnverified ? undefined : '资料已核对',
        ]
          .filter(Boolean)
          .join(' · ')}
        // 页头操作：一个主按钮（加入队列 / 重新生成），其余为普通或文字按钮
        extra={
          <>
            <Link
              to={`/operations/media?keyword=${encodeURIComponent(data.model ?? '')}`}
            >
              <Button icon={<ImageIcon size="1em" />} type="text">
                在素材库查看
              </Button>
            </Link>
            {canReview && (
              <Button icon={<ArrowRight size="1em" />} onClick={nextReview}>
                下一个待审核
              </Button>
            )}
            {canUpload && data.status === 2 && (
              <Button
                loading={busy === 'dequeue'}
                onClick={() =>
                  run('dequeue', () => videoApi.dequeue(data.id), '已移出队列')
                }
              >
                移出队列
              </Button>
            )}
            {canUpload && QUEUEABLE.includes(data.status) && (
              <Button
                type={data.status === 4 ? 'default' : 'primary'}
                loading={busy === 'queue'}
                onClick={queue}
              >
                {data.status === 5 ? '重新生成' : '加入队列'}
              </Button>
            )}
          </>
        }
      />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1.6fr) minmax(360px, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ display: 'grid', gap: 16 }}>
          <Card
            title="生成结果"
            extra={
              currentVersion ? (
                <span style={{ fontSize: 12, color: palette.mute }}>
                  第 {currentVersion} 版 · {current[0]?.statusName}
                </span>
              ) : undefined
            }
          >
            {data.status === 3 ? (
              <div style={{ padding: '32px 0', textAlign: 'center' }}>
                <LoaderCircle
                  size="1em"
                  className="zhul-spin"
                  style={{ fontSize: 24, color: palette.link }}
                />
                <div style={{ marginTop: 10 }}>
                  正在生成竖屏和横屏视频，约几分钟
                </div>
              </div>
            ) : current.length ? (
              <Outputs outputs={current} links={links} />
            ) : (
              <div style={{ padding: '24px 0', color: palette.mute }}>
                {data.status === 7
                  ? `生成失败：${data.failReason ?? ''}`
                  : data.status === 2
                    ? '排队中，到生成时段后按顺序生成'
                    : '还没有生成。选好 4–8 张图片、写好脚本后加入队列，夜间自动生成竖屏和横屏两个视频。'}
              </div>
            )}
            {/* 要用户处理的提醒用提示条，不写彩色纯文字（规范「警告不用纯文字」） */}
            {data.status === 6 && data.rejectReason && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 12 }}
                title={`退回原因：${data.rejectReason}`}
              />
            )}
            {regenerating && (
              <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
                素材库里仍是已通过的第 {approvedVersion}{' '}
                版，新视频审核通过后替换
              </div>
            )}
            {data.status === 4 && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  marginTop: 16,
                  paddingTop: 14,
                  borderTop: `1px solid ${palette.hairline}`,
                }}
              >
                <span style={{ flex: 1, fontSize: 12, color: palette.mute }}>
                  通过后两个视频进入素材库「视频素材」，并勾选用于发品
                </span>
                {canReview && (
                  <>
                    <Button
                      danger
                      icon={<Undo2 size="1em" />}
                      onClick={() => {
                        setReason('');
                        setRejectOpen(true);
                      }}
                    >
                      退回
                    </Button>
                    <Button
                      type="primary"
                      icon={<Check size="1em" />}
                      loading={busy === 'approve'}
                      onClick={() =>
                        run(
                          'approve',
                          () => videoApi.approve(data.id),
                          '已通过，视频已进素材库',
                        )
                      }
                    >
                      通过
                    </Button>
                  </>
                )}
              </div>
            )}
          </Card>

          <Card
            title="素材"
            extra={
              <span style={{ fontSize: 12, color: palette.mute }}>
                {locked
                  ? '排队中和生成中不能修改'
                  : `选用 ${used.length} / 共 ${data.materials.length} 张 · 拖动调整顺序，点图片切换是否选用`}
              </span>
            }
          >
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              {data.materials.map((m, i) => (
                <div
                  key={m.attachmentId}
                  draggable={editable}
                  onDragStart={() => setDragIndex(i)}
                  onDragOver={(e) => editable && e.preventDefault()}
                  onDrop={() => onDrop(i)}
                  style={{
                    position: 'relative',
                    width: 96,
                    height: 96,
                    opacity: m.used ? 1 : 0.45,
                    cursor: editable ? 'grab' : undefined,
                  }}
                >
                  <Thumb
                    url={links[m.attachmentId]?.url}
                    size={96}
                    title={m.fileName}
                    onClick={
                      editable
                        ? () =>
                            saveMaterials(
                              data.materials.map((x) =>
                                x.attachmentId === m.attachmentId
                                  ? { ...x, used: !x.used }
                                  : x,
                              ),
                            )
                        : undefined
                    }
                  />
                  <span
                    style={{
                      position: 'absolute',
                      top: 6,
                      left: 6,
                      minWidth: 18,
                      height: 18,
                      borderRadius: 4,
                      fontSize: 11,
                      lineHeight: '18px',
                      textAlign: 'center',
                      color: m.used ? palette.onPrimary : '#fff',
                      background: m.used ? palette.primary : palette.mute,
                    }}
                  >
                    {m.used ? usedIndex.get(m.attachmentId) : '—'}
                  </span>
                  {!m.used && (
                    <span
                      style={{
                        position: 'absolute',
                        bottom: 6,
                        left: 6,
                        fontSize: 11,
                        color: '#fff',
                      }}
                    >
                      不用于视频
                    </span>
                  )}
                  {editable && (
                    <Button
                      size="small"
                      type="text"
                      aria-label="删除图片"
                      icon={<Trash2 size="1em" />}
                      style={{
                        position: 'absolute',
                        top: 2,
                        right: 2,
                        color: '#fff',
                      }}
                      onClick={(e) => {
                        e.stopPropagation();
                        confirmDanger({
                          title: '删除这张图片？',
                          content: '同时从素材库移除。',
                          onOk: () =>
                            run(
                              'remove',
                              () =>
                                videoApi.removeMaterial(
                                  data.id,
                                  m.attachmentId,
                                ),
                              '已删除',
                              true,
                            ),
                        });
                      }}
                    />
                  )}
                </div>
              ))}
              {editable && data.materials.length < LIMITS.maxImages && (
                <Upload
                  multiple
                  accept=".jpg,.jpeg,.png,.webp"
                  showUploadList={false}
                  beforeUpload={(f, list) => {
                    if (f === list[0]) addImages(list as File[]);
                    return Upload.LIST_IGNORE;
                  }}
                >
                  <div
                    style={{
                      width: 96,
                      height: 96,
                      borderRadius: 6,
                      border: `1px dashed ${palette.hairline}`,
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: 6,
                      cursor: 'pointer',
                      color: palette.sub,
                      fontSize: 12,
                    }}
                  >
                    {uploading ? (
                      <LoaderCircle size="1em" className="zhul-spin" />
                    ) : (
                      <Plus size="1em" />
                    )}
                    {uploading ? `上传中 ${uploading}` : '添加图片'}
                  </div>
                </Upload>
              )}
            </div>
            <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
              JPG / PNG / WEBP，单张不超过 20MB，上传前自动缩到长边 2048
              像素；HEIC 请先转成 JPG。
            </div>
          </Card>
        </div>

        <div style={{ display: 'grid', gap: 16 }}>
          <Card
            title="视频脚本"
            extra={
              editable && (
                <Button
                  type="link"
                  size="small"
                  icon={<Zap size="1em" />}
                  loading={busy === 'generate' || data.scriptStatus === 1}
                  disabled={!used.length}
                  onClick={generate}
                >
                  {data.scriptStatus === 1
                    ? 'AI 正在写'
                    : draft.shots.length
                      ? '重新生成脚本'
                      : 'AI 写脚本'}
                </Button>
              )
            }
          >
            {data.factsUnverified && (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 10 }}
                title="商品资料还没核对，AI 只写品牌、型号和产品类型；参数请自己核实后再补"
              />
            )}
            {data.scriptStatus === 3 && data.scriptError && (
              <Alert
                type="error"
                showIcon
                style={{ marginBottom: 10 }}
                title={data.scriptError}
              />
            )}
            <div style={{ fontSize: 12, color: palette.mute, marginBottom: 4 }}>
              视频标题
            </div>
            <Input
              value={draft.title}
              maxLength={LIMITS.maxTitle}
              showCount
              disabled={!editable}
              onChange={(e) => setDraft({ ...draft, title: e.target.value })}
              placeholder="品牌 + 型号 + 产品类型，英文"
              aria-label="视频标题"
            />
            <div style={{ display: 'grid', gap: 8, marginTop: 12 }}>
              {draft.shots.map((s, i) => (
                <div
                  // biome-ignore lint/suspicious/noArrayIndexKey: 镜头没有自己的 id，按位置编辑
                  key={i}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '18px 72px 1fr auto 24px',
                    gap: '6px 8px',
                    alignItems: 'center',
                    paddingBottom: 8,
                    borderBottom: `1px solid ${palette.hairline}`,
                  }}
                >
                  <span style={{ color: palette.mute, fontSize: 12 }}>
                    {i + 1}
                  </span>
                  <Select
                    size="small"
                    value={s.attachmentId}
                    disabled={!editable}
                    status={
                      s.attachmentId && usedIndex.has(s.attachmentId)
                        ? undefined
                        : 'error'
                    }
                    onChange={(v) => setShot(i, { attachmentId: v })}
                    options={used.map((m) => ({
                      value: m.attachmentId,
                      label: `图 ${usedIndex.get(m.attachmentId)}`,
                    }))}
                    aria-label={`镜头 ${i + 1} 的图片`}
                    popupMatchSelectWidth={false}
                  />
                  <span />
                  <span
                    style={{
                      fontSize: 12,
                      color: palette.mute,
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    {shotSeconds[i]} 秒
                  </span>
                  {editable ? (
                    <Button
                      size="small"
                      type="text"
                      aria-label={`删除镜头 ${i + 1}`}
                      icon={<Trash2 size="1em" />}
                      onClick={() =>
                        setDraft({
                          ...draft,
                          shots: draft.shots.filter((_, j) => j !== i),
                        })
                      }
                    />
                  ) : (
                    <span />
                  )}
                  <Input.TextArea
                    style={{ gridColumn: '2 / -1' }}
                    autoSize={{ minRows: 2, maxRows: 4 }}
                    value={s.caption}
                    maxLength={LIMITS.maxCaption}
                    disabled={!editable}
                    onChange={(e) => setShot(i, { caption: e.target.value })}
                    placeholder="英文字幕，也是配音文案"
                    aria-label={`镜头 ${i + 1} 的字幕`}
                  />
                </div>
              ))}
            </div>
            {editable && draft.shots.length < LIMITS.maxShots && (
              <Button
                type="dashed"
                block
                icon={<Plus size="1em" />}
                style={{ marginTop: 8 }}
                onClick={() =>
                  setDraft({
                    ...draft,
                    shots: [
                      ...draft.shots,
                      {
                        attachmentId: used[draft.shots.length]?.attachmentId,
                        caption: '',
                      },
                    ],
                  })
                }
              >
                添加镜头
              </Button>
            )}
            <div
              style={{
                marginTop: 12,
                fontSize: 13,
                color: draft.shots.length
                  ? totalOk
                    ? palette.green
                    : palette.orange
                  : palette.mute,
              }}
            >
              {draft.shots.length ? (
                totalOk ? (
                  <>
                    <CircleCheck size="1em" /> 估算总时长 {total} 秒（需要 15–20
                    秒）· {draft.shots.length} 个镜头
                  </>
                ) : (
                  <>
                    <CircleAlert size="1em" />{' '}
                    {draft.shots.length < LIMITS.minShots ||
                    draft.shots.length > LIMITS.maxShots
                      ? `镜头 ${draft.shots.length} 个，需要 4–8 个`
                      : `估算时长 ${total} 秒，需要 15–20 秒，请${total > LIMITS.maxSeconds ? '精简' : '补充'}字幕`}
                  </>
                )
              ) : (
                '还没有脚本：让 AI 写，或者手动添加镜头'
              )}
            </div>
            <div style={{ marginTop: 4, fontSize: 12, color: palette.mute }}>
              字幕同时作为配音文案，只写资料里有的事实；按每秒约 2.5
              个词估算时长。
            </div>
            {editable && (
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'flex-end',
                  gap: 8,
                  marginTop: 12,
                }}
              >
                <Button
                  disabled={!dirty}
                  onClick={() => setDraft(data.script ?? emptyScript())}
                >
                  撤销修改
                </Button>
                <Button
                  type="primary"
                  disabled={!dirty}
                  loading={busy === 'script'}
                  onClick={() =>
                    run(
                      'script',
                      () => videoApi.saveScript(data.id, draft),
                      '脚本已保存',
                    )
                  }
                >
                  保存脚本
                </Button>
              </div>
            )}
          </Card>

          <Card title="历史">
            {data.logs.length ? (
              <div style={{ display: 'grid', gap: 8 }}>
                {data.logs.map((l, i) => (
                  <div
                    // biome-ignore lint/suspicious/noArrayIndexKey: 历史只追加，不重排
                    key={i}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '76px 84px minmax(0, 1fr)',
                      gap: 8,
                      fontSize: 12,
                    }}
                  >
                    <span style={{ color: palette.mute }}>
                      {dayjs(l.time).format('MM-DD HH:mm')}
                    </span>
                    <span
                      title={l.operator}
                      style={{
                        color: palette.sub,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {l.operator}
                    </span>
                    <span>
                      {l.actionName}
                      {l.version ? `（第 ${l.version} 版）` : ''}
                      {l.note ? `：${l.note}` : ''}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <span style={{ color: palette.mute }}>暂无</span>
            )}
          </Card>
        </div>
      </div>

      <Modal
        title="退回视频"
        open={rejectOpen}
        onCancel={() => setRejectOpen(false)}
        okText="退回"
        okButtonProps={{ danger: true, loading: busy === 'reject' }}
        onOk={async () => {
          if (!reason.trim()) {
            message.error('请填写退回原因');
            return;
          }
          const ok = await run(
            'reject',
            () => videoApi.reject(data.id, reason.trim()),
            '已退回',
            true,
          );
          if (ok) setRejectOpen(false);
        }}
      >
        <Input.TextArea
          value={reason}
          maxLength={LIMITS.maxReject}
          showCount
          autoSize={{ minRows: 3, maxRows: 6 }}
          onChange={(e) => setReason(e.target.value)}
          placeholder="如：第 3 个镜头铭牌看不清，换一张图"
          aria-label="退回原因"
        />
        <div style={{ marginTop: 8, fontSize: 12, color: palette.mute }}>
          退回后可以改素材、改脚本，再加入队列重新生成
        </div>
      </Modal>
    </div>
  );
};

export default VideoTaskDetailPage;
