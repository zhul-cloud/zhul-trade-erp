import {
  CheckCircleFilled,
  CloseCircleFilled,
  EditOutlined,
  FileTextOutlined,
  InboxOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import XMarkdown from '@ant-design/x-markdown';
import { useAccess } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Drawer,
  Input,
  Modal,
  Result,
  Space,
  Switch,
  Table,
  Tooltip,
  Upload,
} from 'antd';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import React, { useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { PLATFORM_META } from '../products/cards/SeoCard';
import {
  type ContentBlock,
  type ContentConfirmResult,
  type ContentFile,
  type ContentPreview,
  contentApi,
  type MarketingPlatform,
  type PackageType,
  readBizError,
} from '../service';

const LANG_NAME: Record<string, string> = {
  zh: '中文',
  en: 'EN',
  ru: 'RU',
};
const TYPE_NAME: Record<string, string> = {
  facts: '资料包',
  marketing: '营销包',
};
/** 文件说明：资料包 · 中文 / 阿里国际站营销包 · EN / 关键词库 · EN；旧格式另外标出 */
const packageLabel = (f: {
  type?: string;
  platform?: string;
  part?: string;
  lang?: string;
  schema?: number;
}) =>
  [
    f.part === 'keywords'
      ? '关键词库'
      : f.platform && PLATFORM_META[f.platform as MarketingPlatform]
        ? `${PLATFORM_META[f.platform as MarketingPlatform].label}${TYPE_NAME.marketing}`
        : TYPE_NAME[f.type ?? ''],
    LANG_NAME[f.lang ?? ''] ?? f.lang,
    f.type && f.schema !== 2 ? '旧格式' : undefined,
  ]
    .filter(Boolean)
    .join(' · ') || '—';

/** 块键「部分:章节名」里的章节名 */
const sectionName = (b: ContentBlock) => b.section.split(':').pop() ?? '';
/** 列表块摘要取哪一列：应用场景、关键词库、帖子取第二列 */
const SUMMARY_COLUMN: Record<string, number> = {
  APPS: 1,
  KEYWORD_LIBRARY: 1,
  POSTS: 1,
  VIDEOS: 1,
};
/** 帖子、视频方案的原文按 Markdown 排版显示 */
const MARKDOWN_SECTIONS = new Set(['POSTS', 'VIDEOS']);
const MAX_FILES = 50;
const MAX_BYTES = 1024 * 1024;

const fileState = (p: ContentPreview) => {
  if (p.confirmable) return { text: '可确认', tone: 'green' as const };
  if (p.errors.length) return { text: '不能确认', tone: 'red' as const };
  const n = p.blocks.filter((b) => !b.skipped && b.errors.length).length;
  return { text: `${n} 处待处理`, tone: 'orange' as const };
};

/** 解析结果一列：文本块显示正文，列表块显示条数与前几条 */
const BlockSummary: React.FC<{ b: ContentBlock }> = ({ b }) => {
  const { palette } = useAppTheme();
  if (b.text !== undefined && b.text !== null) {
    return (
      <span
        style={{
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
          color: palette.ink,
        }}
      >
        {b.text}
      </span>
    );
  }
  return (
    <span style={{ color: palette.ink }}>
      {b.count} 条
      {b.rows.length > 0 && (
        <span style={{ color: palette.mute }}>
          {' · '}
          {b.rows
            .slice(0, 3)
            .map((r) => r[SUMMARY_COLUMN[sectionName(b)] ?? 0])
            .join('、')}
          {b.rows.length > 3 ? ' …' : ''}
        </span>
      )}
    </span>
  );
};

export const UploadDrawer: React.FC<{
  open: boolean;
  onClose: () => void;
  onDone: () => void;
  /** 行内上传：只接收这个商品的文件 */
  product?: { id: number; label: string };
  /** 入口：facts 资料任务只收资料包，marketing 商品运营只收营销包 */
  entry: PackageType;
}> = ({ open, onClose, onDone, product, entry }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const canWrite =
    !!access[
      entry === 'facts' ? 'product:content:edit' : 'operations:marketing:edit'
    ];
  const [files, setFiles] = useState<ContentFile[]>([]);
  const [previews, setPreviews] = useState<ContentPreview[]>([]);
  const [active, setActive] = useState(0);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<ContentBlock>();
  const [draft, setDraft] = useState('');
  const [result, setResult] = useState<ContentConfirmResult>();

  const reset = () => {
    setFiles([]);
    setPreviews([]);
    setActive(0);
    setResult(undefined);
  };

  const close = () => {
    if (result && result.succeeded > 0) onDone();
    reset();
    onClose();
  };

  const runPreview = async (next: ContentFile[]) => {
    setLoading(true);
    try {
      setPreviews(await contentApi.preview(entry, next, product?.id));
      setFiles(next);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  };

  const pick = async (list: File[]) => {
    if (list.length > MAX_FILES) {
      message.error(`一次最多上传 ${MAX_FILES} 个文件`);
      return;
    }
    const big = list.find((f) => f.size > MAX_BYTES);
    if (big) {
      message.error(`「${big.name}」超过 1MB`);
      return;
    }
    const read = await Promise.all(
      list.map(async (f) => ({ fileName: f.name, content: await f.text() })),
    );
    setActive(0);
    await runPreview(read);
  };

  const update = (index: number, change: Partial<ContentFile>) =>
    runPreview(files.map((f, i) => (i === index ? { ...f, ...change } : f)));

  /** 当前项对应的上传文件（v2 营销文件拆成多项时，多项对应同一个文件） */
  const fileOf = (i: number) => previews[i]?.fileIndex ?? i;

  const toggleSkip = (b: ContentBlock, skip: boolean) => {
    const index = fileOf(active);
    const current = files[index].skip ?? [];
    update(index, {
      skip: skip
        ? [...current, b.section]
        : current.filter((s) => s !== b.section),
    });
  };

  const saveEdit = async () => {
    if (!editing) return;
    const index = fileOf(active);
    await update(index, {
      edits: { ...(files[index].edits ?? {}), [editing.section]: draft },
    });
    setEditing(undefined);
  };

  const confirm = async () => {
    setLoading(true);
    try {
      // 有可确认项的文件整份提交；服务端重新解析，只写可确认的项
      const indexes = new Set(
        previews.filter((p) => p.confirmable).map((p) => p.fileIndex),
      );
      const ok = files.filter((_, i) => indexes.has(i));
      setResult(await contentApi.confirm(entry, ok, product?.id));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  };

  const confirmable = previews.filter((p) => p.confirmable).length;
  const blocked = previews.length - confirmable;
  const p = previews[active];

  const blockColumns = [
    {
      title: '章节',
      key: 'label',
      width: 130,
      render: (_: unknown, b: ContentBlock) => (
        <span
          style={{
            fontWeight: 600,
            color: b.skipped ? palette.mute : palette.ink,
          }}
        >
          {b.label}
          {b.edited && (
            <span style={{ fontSize: 12, color: palette.link, marginLeft: 4 }}>
              已修改
            </span>
          )}
        </span>
      ),
    },
    {
      title: '解析结果',
      key: 'summary',
      render: (_: unknown, b: ContentBlock) => (
        <div style={{ opacity: b.skipped ? 0.45 : 1 }}>
          <BlockSummary b={b} />
          {b.errors.map((e) => (
            <div key={e} style={{ fontSize: 12, color: palette.red }}>
              {e}
            </div>
          ))}
          {b.hints.slice(1).map((h) => (
            <div key={h} style={{ fontSize: 12, color: palette.orange }}>
              {h}
            </div>
          ))}
        </div>
      ),
    },
    {
      title: '写入位置',
      key: 'target',
      width: 110,
      render: (_: unknown, b: ContentBlock) =>
        b.target === 'shared' ? (
          <Pill tone="cyan">共享商品库</Pill>
        ) : (
          <Pill tone="violet">本公司</Pill>
        ),
    },
    {
      title: '处理',
      key: 'action',
      width: 200,
      render: (_: unknown, b: ContentBlock) => (
        <Space size={8}>
          <Switch
            size="small"
            checked={!b.skipped}
            onChange={(on) => toggleSkip(b, !on)}
            aria-label={`写入${b.label}`}
          />
          <span
            style={{
              fontSize: 12,
              color: b.skipped ? palette.mute : palette.sub,
            }}
          >
            {b.skipped ? '跳过' : (b.hints[0] ?? '写入')}
          </span>
          <Tooltip title="修改这一节">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                setDraft(b.raw);
                setEditing(b);
              }}
              aria-label={`修改${b.label}`}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      open={open}
      onClose={close}
      size="min(1240px, 96vw)"
      destroyOnHidden
      title={
        result
          ? '写入结果'
          : product
            ? `上传 · ${product.label}（只接收这个商品的文件）`
            : '上传内容包 · 解析预览'
      }
      footer={
        result ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" onClick={close}>
              完成
            </Button>
          </div>
        ) : previews.length > 0 ? (
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <Button icon={<UploadOutlined />} onClick={reset}>
              重新选择文件
            </Button>
            <span style={{ flex: 1 }} />
            <Button onClick={close}>取消</Button>
            <Tooltip
              title={
                canWrite
                  ? undefined
                  : `没有「${entry === 'facts' ? '商品内容维护' : '营销内容维护'}」权限`
              }
            >
              <Button
                type="primary"
                loading={loading}
                disabled={!canWrite || confirmable === 0}
                onClick={confirm}
              >
                确认写入 {confirmable}{' '}
                {previews.length > files.length ? '项' : '个文件'}
              </Button>
            </Tooltip>
          </div>
        ) : null
      }
    >
      {result ? (
        <div>
          <Result
            status={result.failed ? 'warning' : 'success'}
            title={`写入成功 ${result.succeeded} 个${result.failed ? `，失败 ${result.failed} 个` : ''}`}
          />
          <div style={{ display: 'grid', gap: 8 }}>
            {result.items.map((it, i) => (
              <div
                key={it.fileName + String(i)}
                style={{
                  display: 'flex',
                  gap: 10,
                  padding: '10px 14px',
                  borderRadius: 10,
                  background: palette.inset,
                }}
              >
                {it.success ? (
                  <CheckCircleFilled style={{ color: palette.green }} />
                ) : (
                  <CloseCircleFilled style={{ color: palette.red }} />
                )}
                <div>
                  <b style={{ color: palette.ink }}>{it.fileName}</b>
                  {it.productLabel && (
                    <span style={{ color: palette.mute }}>
                      {' '}
                      · {it.productLabel} ·{' '}
                      {it.part === 'keywords'
                        ? '关键词库 · '
                        : it.platform
                          ? `${PLATFORM_META[it.platform]?.label} · `
                          : ''}
                      {LANG_NAME[it.lang ?? ''] ?? ''}
                    </span>
                  )}
                  <div style={{ fontSize: 12, color: palette.sub }}>
                    {it.message}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : previews.length === 0 ? (
        <div>
          <Upload.Dragger
            multiple
            accept=".md,.markdown,.txt"
            showUploadList={false}
            disabled={loading}
            beforeUpload={(_, list) => {
              // 多选时每个文件都会回调一次，只在最后一个处理整批
              if (_ === list[list.length - 1]) pick(list);
              return false;
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">
              {loading ? '正在解析…' : '点击或拖入 Markdown 文件'}
            </p>
            <p className="ant-upload-hint">
              {entry === 'facts'
                ? '按《商品内容规范 v2》的资料包（schema: 2、type: facts、lang: zh）；旧格式资料包和 llm-wiki 商品文件也能上传。营销包请到运营管理 → 商品运营上传。'
                : '按《商品内容规范 v2》的营销包（schema: 2、type: marketing）：一个文件可以包含关键词库和多个平台，按平台分项预览、分项写入；俄文文件只写独立站。旧格式营销包也能上传。资料包请到商品资料 → 资料任务上传。'}
              一次最多 50 个，每个不超过 1MB
            </p>
          </Upload.Dragger>
          <Alert
            style={{ marginTop: 16 }}
            type="info"
            showIcon
            title="上传后先解析预览，确认后才写入"
            description="资料包（中文）写入共享商品库：规格、应用场景只替换未核实的，兼容型号、技术资料只增不删，技术问答待平台审核，资料待确认项整份替换。营销包只属于本公司：关键词库整份替换，各平台的标题、属性、卖点、详情、FAQ、帖子等按块替换。"
          />
        </div>
      ) : (
        <div
          style={{
            display: 'flex',
            gap: 20,
            alignItems: 'flex-start',
            flexWrap: 'wrap',
          }}
        >
          <div style={{ flex: '0 0 300px', maxWidth: '100%' }}>
            <div
              style={{
                fontSize: 12,
                fontWeight: 600,
                color: palette.sub,
                marginBottom: 8,
              }}
            >
              {files.length} 个文件
              {previews.length > files.length ? `，${previews.length} 项` : ''}
              {blocked > 0 ? ` · ${blocked} 项不能确认` : ''}
            </div>
            <div style={{ display: 'grid', gap: 8 }}>
              {previews.map((f, i) => {
                const s = fileState(f);
                const on = i === active;
                return (
                  <button
                    type="button"
                    key={`${f.fileIndex}-${f.part ?? ''}`}
                    onClick={() => setActive(i)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '10px 12px',
                      borderRadius: 10,
                      textAlign: 'left',
                      cursor: 'pointer',
                      background: on ? palette.accentSoft : palette.inset,
                      border: `1px solid ${on ? palette.link : 'transparent'}`,
                      color: palette.ink,
                    }}
                  >
                    <FileTextOutlined
                      style={{ color: on ? palette.link : palette.mute }}
                    />
                    <span style={{ flex: 1, minWidth: 0 }}>
                      <span
                        style={{
                          display: 'block',
                          fontSize: 13,
                          fontWeight: 600,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {f.fileName}
                        {f.part && (
                          <span style={{ color: palette.link }}>
                            {' · '}
                            {f.part === 'keywords'
                              ? '关键词库'
                              : PLATFORM_META[f.part as MarketingPlatform]
                                  ?.label}
                          </span>
                        )}
                      </span>
                      <span style={{ fontSize: 12, color: palette.mute }}>
                        {f.productLabel ?? f.model ?? '—'} · {packageLabel(f)}
                      </span>
                    </span>
                    <Pill tone={s.tone}>{s.text}</Pill>
                  </button>
                );
              })}
            </div>
          </div>
          {p && (
            <div style={{ flex: 1, minWidth: 320 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  marginBottom: 12,
                }}
              >
                <b style={{ fontSize: 15, color: palette.ink }}>
                  {p.productLabel ?? `${p.brand ?? '—'} · ${p.model ?? '—'}`}
                </b>
                {(p.type || p.lang) && (
                  <Pill tone={p.type === 'marketing' ? 'violet' : 'accent'}>
                    {packageLabel(p)}
                  </Pill>
                )}
              </div>
              {p.errors.length > 0 && (
                <Alert
                  type="error"
                  showIcon
                  style={{ marginBottom: 12 }}
                  title="这个文件不能确认"
                  description={p.errors.join('；')}
                />
              )}
              <Table<ContentBlock>
                rowKey="section"
                size="middle"
                columns={blockColumns}
                dataSource={p.blocks}
                pagination={false}
                loading={loading}
                locale={{ emptyText: '没有解析到内容' }}
                expandable={{
                  rowExpandable: (b) => b.rows.length > 0,
                  expandedRowRender: (b) =>
                    MARKDOWN_SECTIONS.has(sectionName(b)) ? (
                      <div style={{ display: 'grid', gap: 12 }}>
                        {b.rows.map((r, i) => (
                          <div
                            key={`${r[0]}-${r[1]}-${String(i)}`}
                            style={{
                              padding: '10px 14px',
                              borderRadius: 10,
                              background: palette.inset,
                            }}
                          >
                            <div
                              style={{
                                fontSize: 12,
                                color: palette.sub,
                                marginBottom: 6,
                              }}
                            >
                              {r[0]} · {r[1]}
                            </div>
                            <XMarkdown>{r[2]}</XMarkdown>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <Table
                        size="small"
                        rowKey={(_, i) => String(i)}
                        pagination={false}
                        dataSource={b.rows}
                        columns={b.columns.map((title, ci) => ({
                          title,
                          key: String(ci),
                          render: (_: unknown, r: string[]) => r[ci] || '—',
                        }))}
                      />
                    ),
                }}
              />
              {p.notes.length > 0 && (
                <div
                  style={{ marginTop: 10, fontSize: 12, color: palette.mute }}
                >
                  {p.notes.join('；')}
                </div>
              )}
            </div>
          )}
        </div>
      )}
      <Modal
        open={!!editing}
        title={`修改「${editing?.label ?? ''}」`}
        onCancel={() => setEditing(undefined)}
        onOk={saveEdit}
        okText="重新解析"
        cancelText="取消"
        width={720}
        destroyOnHidden
      >
        <div style={{ fontSize: 12, color: palette.sub, marginBottom: 8 }}>
          按内容包的格式修改这一节的正文（不含标题），清空等于不写入这一节。
        </div>
        <Input.TextArea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          autoSize={{ minRows: 8, maxRows: 20 }}
          style={{ fontFamily: 'JetBrains Mono, Menlo, monospace' }}
        />
      </Modal>
    </Drawer>
  );
};
