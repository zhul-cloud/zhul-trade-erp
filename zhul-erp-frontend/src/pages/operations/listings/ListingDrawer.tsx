import {
  CheckOutlined,
  CopyOutlined,
  DownloadOutlined,
  EditOutlined,
  ExportOutlined,
  PictureOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { Link, useAccess } from '@umijs/max';
import {
  App,
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Segmented,
  Skeleton,
  Tooltip,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import {
  type FetchUrls,
  loadPreviews,
  MediaGrid,
  usePreviewer,
} from '@/pages/operations/media/MediaDrawer';
import {
  downloadAll,
  type MediaItem,
  type MediaReference,
  refOf,
} from '@/pages/operations/media/service';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import type { PlatformContent, ProductContent } from '@/pages/product/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { usePermission } from '@/utils/permission';
import {
  type ListingDetail,
  type ListingTask,
  listingApi,
  PERM_EDIT,
  type PublishBody,
  readBizError,
} from './service';

const MAX_DOWNLOAD = 50;

/** 复制到剪贴板：优先用剪贴板接口，不可用时退回选中文本复制 */
const copyText = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    const el = document.createElement('textarea');
    el.value = text;
    el.style.position = 'fixed';
    el.style.opacity = '0';
    document.body.appendChild(el);
    el.select();
    const ok = document.execCommand('copy');
    el.remove();
    return ok;
  }
};

/** 复制助手的一段：标题、内容预览、复制按钮；内容为空时不显示 */
interface Section {
  label: string;
  text?: string;
  /** 内容是 Markdown 或长文：预览区按原文排版，限制高度 */
  long?: boolean;
}

const faqText = (faqs?: { question: string; answer: string }[]) =>
  (faqs ?? []).map((f) => `Q: ${f.question}\nA: ${f.answer}`).join('\n\n');

/** 阿里国际站、中国制造网：每段的纯文本（spec「复制助手」的格式） */
const marketSections = (c?: PlatformContent): Section[] => [
  { label: '推荐标题', text: c?.title },
  {
    label: '备选标题',
    text: (c?.titleAlternates ?? []).map((t) => t.title).join('\n'),
  },
  {
    label: '选用关键词',
    text: (c?.keywords ?? []).map((k) => k.keyword).join(', '),
  },
  {
    label: '产品属性',
    text: (c?.attributes ?? []).map((a) => `${a.name}: ${a.value}`).join('\n'),
  },
  { label: '卖点', text: (c?.sellingPoints ?? []).join('\n') },
  { label: '产品详情', text: c?.detail, long: true },
  { label: 'FAQ', text: faqText(c?.faqs), long: true },
  { label: '询盘引导语', text: c?.cta },
];

const siteSections = (l?: ProductContent['langs']['en']): Section[] => [
  { label: 'SEO 标题', text: l?.seoTitle },
  { label: 'SEO 描述', text: l?.metaDescription },
  { label: 'H1', text: l?.h1 },
  { label: 'URL 别名', text: l?.urlSlug },
  { label: '首屏定义块', text: l?.geoAnswer, long: true },
  { label: '产品长描述', text: l?.longDescription, long: true },
  { label: '营销问答', text: faqText(l?.faqs), long: true },
  {
    label: '图片说明文字',
    text: (l?.altTexts ?? []).map((a) => `${a.image}: ${a.alt}`).join('\n'),
  },
];

const socialSections = (c?: PlatformContent): Section[] => {
  const posts = c?.posts ?? [];
  const list: Section[] = posts.length
    ? posts.map((p) => ({
        label: `${p.channel} · ${p.label}`,
        text: p.text,
        long: true,
      }))
    : [
        { label: 'LinkedIn 帖子', text: c?.linkedinPost, long: true },
        { label: 'Facebook 帖子', text: c?.facebookPost, long: true },
        { label: 'YouTube 描述', text: c?.youtubeDescription, long: true },
      ];
  return list.concat(
    (c?.videos ?? []).map((v) => ({
      label: `视频方案 · ${v.label}`,
      text: v.markdown,
      long: true,
    })),
  );
};

const CopySections: React.FC<{ sections: Section[] }> = ({ sections }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const shown = sections.filter((s) => s.text?.trim());
  if (shown.length === 0) {
    return (
      <div style={{ color: palette.mute, fontSize: 13 }}>
        这个平台还没有营销内容。
      </div>
    );
  }
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      {shown.map((s) => (
        <div
          key={s.label}
          style={{
            border: `1px solid ${palette.hairline}`,
            borderRadius: 12,
            padding: '10px 12px',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 6,
            }}
          >
            <span style={{ fontWeight: 600, color: palette.ink, flex: 1 }}>
              {s.label}
            </span>
            <Button
              size="small"
              icon={<CopyOutlined />}
              onClick={async () => {
                if (await copyText(s.text as string)) {
                  message.success('已复制');
                } else {
                  message.error('复制失败，请手动选中文字复制');
                }
              }}
            >
              复制
            </Button>
          </div>
          <div
            style={{
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              fontSize: 13,
              color: palette.sub,
              maxHeight: s.long ? 220 : undefined,
              overflowY: s.long ? 'auto' : undefined,
            }}
          >
            {s.text}
          </div>
        </div>
      ))}
    </div>
  );
};

/** 一组素材：网格预览 + 全部下载 */
const MediaBlock: React.FC<{
  title: React.ReactNode;
  items: MediaItem[];
  brand: string;
  model: string;
  fetchUrls: FetchUrls;
  extra?: React.ReactNode;
}> = ({ title, items, brand, model, fetchUrls, extra }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [previews, setPreviews] = useState<Record<string, string>>({});
  const preview = usePreviewer(brand, model, fetchUrls);

  useEffect(() => {
    loadPreviews(items, brand, model, fetchUrls)
      .then(setPreviews)
      .catch(() => setPreviews({}));
  }, [items, brand, model, fetchUrls]);

  return (
    <div style={{ display: 'grid', gap: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontWeight: 600, color: palette.ink, flex: 1 }}>
          {title}
          <span style={{ marginLeft: 8, fontSize: 12, color: palette.mute }}>
            {items.filter((i) => i.kind === 1).length} 张图片 ·{' '}
            {items.filter((i) => i.kind === 2).length} 个视频
          </span>
        </span>
        {extra}
        <Button
          size="small"
          icon={<DownloadOutlined />}
          onClick={async () => {
            try {
              await downloadAll(
                await fetchUrls(
                  items
                    .slice(0, MAX_DOWNLOAD)
                    .map((i) => refOf(i, brand, model)),
                  true,
                ),
              );
            } catch (e) {
              message.error(readBizError(e).message);
            }
          }}
        >
          全部下载
        </Button>
      </div>
      <MediaGrid
        items={items}
        previews={previews}
        onOpen={(i) => preview.open(i, previews)}
      />
      {preview.node}
    </div>
  );
};

/** 选择参考素材：查找顺序与素材库相同，按热度排序 */
const ReferencePicker: React.FC<{
  id?: number;
  onPick: (ref: { brand: string; model: string }) => Promise<void>;
  onClose: () => void;
}> = ({ id, onPick, onClose }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [data, setData] = useState<MediaReference>();
  const [picking, setPicking] = useState<string>();

  useEffect(() => {
    if (id === undefined) return;
    setData(undefined);
    listingApi
      .references(id)
      .then(setData)
      .catch((e) => message.error(readBizError(e).message));
  }, [id, message]);

  return (
    <Modal
      open={id !== undefined}
      title="用参考素材发布"
      footer={null}
      width={640}
      onCancel={onClose}
      destroyOnHidden
    >
      {!data ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : data.level === 0 ? (
        <div style={{ color: palette.mute }}>
          同系列、同品牌、同品类都没有找到有素材的型号。可以先在素材库为这个型号上传网络图片。
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 8 }}>
          <div style={{ fontSize: 13, color: palette.sub }}>
            按{data.levelName}找到 {data.models.length}{' '}
            个有素材的型号，按询盘热度排序；选用后任务显示这个型号的可发客户图片和视频。
            {data.warning && (
              <span style={{ color: palette.orange }}>{data.warning}</span>
            )}
          </div>
          {data.models.map((m) => {
            const key = `${m.brand}|${m.model}`;
            return (
              <div
                key={key}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  border: `1px solid ${palette.hairline}`,
                  borderRadius: 12,
                  padding: '8px 12px',
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, color: palette.ink }}>
                    {m.brand} {m.model}
                  </div>
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    {m.productName} · 询盘 {m.inquiryCount} 次 · 成交{' '}
                    {m.dealCount} 次
                  </div>
                </div>
                <Button
                  size="small"
                  type="primary"
                  loading={picking === key}
                  disabled={!!picking && picking !== key}
                  onClick={async () => {
                    setPicking(key);
                    try {
                      await onPick({ brand: m.brand, model: m.model });
                    } finally {
                      setPicking(undefined);
                    }
                  }}
                >
                  选用
                </Button>
              </div>
            );
          })}
        </div>
      )}
    </Modal>
  );
};

/** 标记已发布 / 修改发布记录 */
const PublishModal: React.FC<{
  task?: ListingTask;
  mode: 'publish' | 'record';
  onSubmit: (body: PublishBody) => Promise<void>;
  onClose: () => void;
}> = ({ task, mode, onSubmit, onClose }) => {
  const [form] = Form.useForm<PublishBody>();
  const [busy, setBusy] = useState(false);
  const market = task?.platform === 'alibaba' || task?.platform === 'mic';

  useEffect(() => {
    if (!task) return;
    form.setFieldsValue({
      listingUrl: task.listingUrl ?? '',
      platformItemId: task.platformItemId ?? '',
      note: task.note ?? '',
    });
  }, [task, form]);

  return (
    <Modal
      open={!!task}
      title={mode === 'publish' ? '标记已发布' : '修改发布记录'}
      okText={mode === 'publish' ? '已发布' : '保存'}
      confirmLoading={busy}
      onCancel={onClose}
      destroyOnHidden
      onOk={async () => {
        const v = await form.validateFields();
        setBusy(true);
        try {
          await onSubmit({
            listingUrl: v.listingUrl.trim(),
            platformItemId: market ? v.platformItemId?.trim() : undefined,
            note: v.note?.trim(),
          });
        } finally {
          setBusy(false);
        }
      }}
    >
      <Form form={form} layout="vertical" requiredMark={false}>
        <Form.Item
          name="listingUrl"
          label="平台链接"
          rules={[
            { required: true, message: '请填写平台链接' },
            {
              pattern: /^https?:\/\/\S+$/i,
              message: '平台链接要以 http:// 或 https:// 开头',
            },
            { max: 500, message: '最长 500 个字符' },
          ]}
        >
          <Input placeholder="https://" />
        </Form.Item>
        {market && (
          <Form.Item
            name="platformItemId"
            label="平台商品编号（选填）"
            rules={[{ max: 64, message: '最长 64 个字符' }]}
          >
            <Input placeholder="如 1600123456789" />
          </Form.Item>
        )}
        <Form.Item
          name="note"
          label="备注（选填）"
          rules={[{ max: 300, message: '最长 300 个字符' }]}
        >
          <Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} />
        </Form.Item>
      </Form>
    </Modal>
  );
};

const Banner: React.FC<{
  tone: 'orange' | 'accent' | 'mute';
  children: React.ReactNode;
  action?: React.ReactNode;
}> = ({ tone, children, action }) => {
  const { palette } = useAppTheme();
  const colors = {
    orange: { bg: palette.orangeSoft, fg: palette.orange },
    accent: { bg: palette.inset, fg: palette.link },
    mute: { bg: palette.inset, fg: palette.mute },
  }[tone];
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 14px',
        borderRadius: 12,
        background: colors.bg,
        color: colors.fg,
        fontSize: 13,
      }}
    >
      <span style={{ flex: 1 }}>{children}</span>
      {action}
    </div>
  );
};

export const StatusPills: React.FC<{ task: ListingTask }> = ({ task }) => (
  <>
    <Pill
      tone={task.status === 1 ? 'accent' : task.status === 2 ? 'green' : 'mute'}
      dot
    >
      {task.statusName}
    </Pill>
    {task.needMedia && <Pill tone="orange">素材待补</Pill>}
    {task.contentUpdated && <Pill tone="orange">内容有更新</Pill>}
    {task.needReplace && <Pill tone="violet">待替换素材</Pill>}
  </>
);

/** 发品任务详情：提醒、发布结果、发品素材、复制助手 */
const ListingDrawer: React.FC<{
  id?: number;
  onClose: () => void;
  onChanged: () => void;
}> = ({ id, onClose, onChanged }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const { hasPerm } = usePermission();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = hasPerm(PERM_EDIT);
  const [data, setData] = useState<ListingDetail>();
  const [error, setError] = useState<string>();
  const [lang, setLang] = useState<'en' | 'ru'>('en');
  const [publishMode, setPublishMode] = useState<'publish' | 'record'>();
  const [picking, setPicking] = useState(false);
  const [busy, setBusy] = useState<string>();

  const load = useCallback(async () => {
    if (id === undefined) return;
    setError(undefined);
    try {
      setData(await listingApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);

  useEffect(() => {
    setData(undefined);
    setLang('en');
    load();
  }, [load]);

  const fetchUrls: FetchUrls = useMemo(
    () => (refs, download) => listingApi.urls(id as number, refs, download),
    [id],
  );

  /** 操作后用返回的详情刷新，并通知列表 */
  const act = async (key: string, fn: () => Promise<ListingDetail>) => {
    setBusy(key);
    try {
      setData(await fn());
      onChanged();
      return true;
    } catch (e) {
      message.error(readBizError(e).message);
      return false;
    } finally {
      setBusy(undefined);
    }
  };

  const t = data?.task;
  const brand = t?.brand ?? '';
  const model = t?.model ?? '';
  const own = data?.ownMedia ?? [];
  const ref = data?.referenceMedia ?? [];
  // 待发布：本型号有素材就用本型号的；已发布：用了参考素材就一直显示参考素材，直到点「已替换」
  const showRef = !!t?.refModel && (t.status === 2 || own.length === 0);
  const showOwn = own.length > 0;
  const pending = t?.status === 1;

  const sections = (): Section[] => {
    if (!t || !data) return [];
    if (t.platform === 'site') return siteSections(data.content.langs?.[lang]);
    const c = data.content.platformContents?.[t.platform];
    return t.platform === 'social' ? socialSections(c) : marketSections(c);
  };

  return (
    <Drawer
      open={id !== undefined}
      onClose={onClose}
      size="min(920px, 96vw)"
      destroyOnHidden
      title={
        t ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>
              {brand} {model}
            </span>
            <Pill tone="gray">{t.platformLabel}</Pill>
            <StatusPills task={t} />
          </div>
        ) : (
          '发品任务'
        )
      }
      extra={
        t &&
        canEdit &&
        (pending ? (
          <Tooltip
            title={
              t.needMedia ? '还没有发品素材，先补素材或选择参考素材' : undefined
            }
          >
            <Button
              type="primary"
              icon={<CheckOutlined />}
              disabled={t.needMedia}
              onClick={() => setPublishMode('publish')}
            >
              标记已发布
            </Button>
          </Tooltip>
        ) : (
          t.status === 2 && (
            <Button
              icon={<EditOutlined />}
              onClick={() => setPublishMode('record')}
            >
              修改发布记录
            </Button>
          )
        ))
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !data || !t ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 20 }}>
          <div style={{ fontSize: 13, color: palette.mute }}>
            {[t.productName, t.category].filter(Boolean).join(' · ')}
          </div>

          {t.status === 3 && (
            <Banner tone="mute">
              已取消：{t.cancelReason || '—'}
              。恢复启用平台或撤销「不发布」后，任务自动回到待发布。
            </Banner>
          )}
          {t.contentUpdated && (
            <Banner
              tone="orange"
              action={
                canEdit && (
                  <Button
                    size="small"
                    icon={<SyncOutlined />}
                    loading={busy === 'sync'}
                    onClick={() =>
                      act('sync', () => listingApi.markSynced(t.id))
                    }
                  >
                    已同步
                  </Button>
                )
              }
            >
              营销内容在发布后更新过，请到平台上同步修改。
            </Banner>
          )}
          {t.needReplace && (
            <Banner
              tone="accent"
              action={
                canEdit && (
                  <Button
                    size="small"
                    loading={busy === 'replace'}
                    onClick={() =>
                      act('replace', () => listingApi.markReplaced(t.id))
                    }
                  >
                    已替换
                  </Button>
                )
              }
            >
              本型号已有发品素材，可以替换平台上的参考图。
            </Banner>
          )}

          {t.status === 2 && (
            <div
              style={{
                display: 'grid',
                gap: 4,
                padding: '12px 14px',
                borderRadius: 12,
                background: palette.inset,
                fontSize: 13,
              }}
            >
              <div style={{ fontWeight: 600, color: palette.ink }}>
                发布结果
              </div>
              {t.listingUrl && (
                <a
                  href={t.listingUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ wordBreak: 'break-all' }}
                >
                  <ExportOutlined /> {t.listingUrl}
                </a>
              )}
              <div style={{ color: palette.sub }}>
                {t.platformItemId && `平台商品编号 ${t.platformItemId} · `}
                {t.publishedBy} 发布于 {formatDateTime(t.publishedAt)}
                {t.syncedAt &&
                  ` · ${t.syncedBy} ${formatDateTime(t.syncedAt)} 同步`}
                {t.replacedAt &&
                  ` · ${t.replacedBy} ${formatDateTime(t.replacedAt)} 替换素材`}
              </div>
              {t.note && (
                <div style={{ color: palette.mute }}>备注：{t.note}</div>
              )}
            </div>
          )}

          <section style={{ display: 'grid', gap: 12 }}>
            <div style={{ fontSize: 15, fontWeight: 600, color: palette.ink }}>
              <PictureOutlined /> 发品素材
            </div>
            {showRef && (
              <MediaBlock
                title={`参考素材：${t.refBrand ?? ''} ${t.refModel}`}
                items={ref}
                brand={t.refBrand ?? ''}
                model={t.refModel as string}
                fetchUrls={fetchUrls}
                extra={
                  pending &&
                  canEdit && (
                    <>
                      <Button size="small" onClick={() => setPicking(true)}>
                        改选
                      </Button>
                      <Button
                        size="small"
                        loading={busy === 'unref'}
                        onClick={() =>
                          modal.confirm({
                            title: '取消参考素材？',
                            content: '取消后任务回到「素材待补」。',
                            onOk: () =>
                              act('unref', () => listingApi.setReference(t.id)),
                          })
                        }
                      >
                        取消参考
                      </Button>
                    </>
                  )
                }
              />
            )}
            {showOwn && (
              <MediaBlock
                title={
                  t.needReplace ? '本型号的发品素材（新）' : '本型号的发品素材'
                }
                items={own}
                brand={brand}
                model={model}
                fetchUrls={fetchUrls}
              />
            )}
            {!showOwn && !showRef && (
              <Banner
                tone="orange"
                action={
                  <>
                    {access.operationsMedia && (
                      <Link
                        to={`/operations/media?keyword=${encodeURIComponent(model)}`}
                        target="_blank"
                      >
                        <Button size="small">去素材库</Button>
                      </Link>
                    )}
                    {pending && canEdit && (
                      <Button size="small" onClick={() => setPicking(true)}>
                        用参考素材发布
                      </Button>
                    )}
                  </>
                }
              >
                这个型号还没有用于发品的素材。在素材库勾选「用于发品」后，刷新任务即可看到。
              </Banner>
            )}
          </section>

          <section style={{ display: 'grid', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span
                style={{
                  fontSize: 15,
                  fontWeight: 600,
                  color: palette.ink,
                  flex: 1,
                }}
              >
                <CopyOutlined /> 复制助手
              </span>
              {t.platform === 'site' && (
                <Segmented<'en' | 'ru'>
                  value={lang}
                  onChange={setLang}
                  options={[
                    { value: 'en', label: '英文' },
                    { value: 'ru', label: '俄文' },
                  ]}
                />
              )}
            </div>
            <CopySections sections={sections()} />
          </section>
        </div>
      )}
      <PublishModal
        task={publishMode ? t : undefined}
        mode={publishMode ?? 'publish'}
        onClose={() => setPublishMode(undefined)}
        onSubmit={async (body) => {
          if (!t) return;
          const ok = await act('publish', () =>
            publishMode === 'publish'
              ? listingApi.publish(t.id, body)
              : listingApi.updateRecord(t.id, body),
          );
          if (ok) {
            message.success(
              publishMode === 'publish' ? '已标记发布' : '已保存',
            );
            setPublishMode(undefined);
          }
        }}
      />
      <ReferencePicker
        id={picking ? t?.id : undefined}
        onClose={() => setPicking(false)}
        onPick={async (r) => {
          if (!t) return;
          if (await act('ref', () => listingApi.setReference(t.id, r))) {
            setPicking(false);
          }
        }}
      />
    </Drawer>
  );
};

export default ListingDrawer;
