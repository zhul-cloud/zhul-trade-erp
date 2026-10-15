import {
  CheckOutlined,
  DownloadOutlined,
  DownOutlined,
  StopOutlined,
  UpOutlined,
} from '@ant-design/icons';
import XMarkdown from '@ant-design/x-markdown';
import { history, useAccess } from '@umijs/max';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import {
  App,
  Button,
  Collapse,
  Dropdown,
  Space,
  Switch,
  Table,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { LangSwitch } from '../../components/LangSwitch';
import { Pill } from '../../components/Pills';
import {
  type ContentLang,
  contentApi,
  type FactNote,
  type MarketingPlatform,
  type MarketingPlatformOption,
  type MarketingSkip,
  type PackageType,
  type PlatformContent,
  type ProductContent,
  readBizError,
} from '../../service';
import { useProductTheme } from '../../theme';

const LANG_CHIPS: { lang: ContentLang; label: string }[] = [
  { lang: 'en', label: 'EN' },
  { lang: 'ru', label: 'RU' },
];

/** 任务阶段：资料待补 → 资料待核 → 营销待生成 → 已完成 */
export const STAGES: Record<
  number,
  { text: string; tone: 'gray' | 'orange' | 'accent' | 'green' }
> = {
  1: { text: '资料待补', tone: 'gray' },
  2: { text: '资料待核', tone: 'orange' },
  3: { text: '营销待生成', tone: 'accent' },
  4: { text: '已完成', tone: 'green' },
};

/** 资料是否已核对（阶段 3、4） */
export const factsVerified = (stage: number) => stage >= 3;

/** 营销平台的中文名与简称（以后端 MarketingPlatform 为准） */
export const PLATFORM_META: Record<
  MarketingPlatform,
  { label: string; short: string; file: string }
> = {
  alibaba: {
    label: '阿里国际站',
    short: '阿里',
    file: '型号.alibaba.md · 英文',
  },
  mic: { label: '中国制造网', short: '中制', file: '型号.mic.md · 英文' },
  site: {
    label: '独立站',
    short: '独立站',
    file: '型号.site.md · 英文',
  },
  social: { label: '社媒', short: '社媒', file: '型号.social.md · 英文' },
};

/** 各平台写入时间：独立站分英文、俄文 */
export interface PlatformProgress {
  enAt?: string;
  ruAt?: string;
  alibabaAt?: string;
  micAt?: string;
  socialAt?: string;
  skips?: Partial<Record<MarketingPlatform, MarketingSkip>>;
}

const writtenAt = (p: PlatformProgress, platform: MarketingPlatform) =>
  ({
    alibaba: p.alibabaAt,
    mic: p.micAt,
    social: p.socialAt,
    site: p.enAt && p.ruAt ? p.ruAt : undefined,
  })[platform];

type ChipState = 'done' | 'todo' | 'skip';

const Chip: React.FC<{ label: string; state: ChipState; title: string }> = ({
  label,
  state,
  title,
}) => {
  const { palette } = useProductTheme();
  const style: React.CSSProperties =
    state === 'skip'
      ? {
          color: palette.mute,
          border: `1px solid ${palette.hairline}`,
          textDecoration: 'line-through',
        }
      : state === 'done'
        ? { color: palette.green, background: palette.greenSoft }
        : { color: palette.sub, background: palette.inset };
  return (
    <span
      title={title}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 3,
        height: 22,
        padding: '0 8px',
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 600,
        whiteSpace: 'nowrap',
        ...style,
      }}
    >
      {state === 'done' && <CheckOutlined style={{ fontSize: 10 }} />}
      {state === 'skip' && <StopOutlined style={{ fontSize: 10 }} />}
      {label}
    </span>
  );
};

/** 本公司启用平台的写入状态：已写入、未写入、不发布；独立站分英文、俄文 */
export const PlatformChips: React.FC<{
  progress: PlatformProgress;
  platforms: MarketingPlatform[];
}> = ({ progress, platforms }) => {
  const fmt = (t?: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '');
  return (
    <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap' }}>
      {platforms.flatMap((platform) => {
        const meta = PLATFORM_META[platform];
        const skip = progress.skips?.[platform];
        if (skip) {
          return [
            <Chip
              key={platform}
              label={meta.short}
              state="skip"
              title={`${meta.label}不发布 · ${skip.markedBy ?? ''} ${fmt(skip.markedAt)}`}
            />,
          ];
        }
        if (platform === 'site') {
          return (['en', 'ru'] as const).map((lang) => {
            const at = lang === 'en' ? progress.enAt : progress.ruAt;
            const label = lang === 'en' ? '独立站 EN' : 'RU';
            return (
              <Chip
                key={`site-${lang}`}
                label={label}
                state={at ? 'done' : 'todo'}
                title={`独立站${lang === 'en' ? '英文' : '俄文'}${at ? ` 已写入 ${fmt(at)}` : ' 未写入'}`}
              />
            );
          });
        }
        const at = writtenAt(progress, platform);
        return [
          <Chip
            key={platform}
            label={meta.short}
            state={at ? 'done' : 'todo'}
            title={`${meta.label}${at ? ` 已写入 ${fmt(at)}` : ' 未写入'}`}
          />,
        ];
      })}
    </span>
  );
};

/** 本公司启用的营销平台；active 为 false 时不请求（没有营销任务菜单的页面） */
export const usePlatforms = (active = true) => {
  const [options, setOptions] = useState<MarketingPlatformOption[]>([]);
  const load = useCallback(async () => {
    if (!active) return;
    try {
      setOptions(await contentApi.platforms());
    } catch {
      setOptions([]);
    }
  }, [active]);
  useEffect(() => {
    load();
  }, [load]);
  const enabled = options.filter((o) => o.enabled).map((o) => o.code);
  return { options, enabled, load, setOptions };
};

/** 本公司英文、俄文营销内容是否已写入的小标签（独立站页签用） */
export const LangChips: React.FC<{ enAt?: string; ruAt?: string }> = (
  props,
) => {
  const { palette } = useProductTheme();
  const at: Record<ContentLang, string | undefined> = {
    en: props.enAt,
    ru: props.ruAt,
  };
  return (
    <Space size={4}>
      {LANG_CHIPS.map(({ lang, label }) => {
        const done = !!at[lang];
        return (
          <span
            key={lang}
            title={
              done
                ? `${label} 已写入 ${dayjs(at[lang]).format('YYYY-MM-DD HH:mm')}`
                : `${label} 未写入`
            }
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 3,
              height: 22,
              padding: '0 8px',
              borderRadius: 6,
              fontSize: 12,
              fontWeight: 600,
              color: done ? palette.green : palette.sub,
              background: done ? palette.greenSoft : palette.inset,
            }}
          >
            {done && <CheckOutlined style={{ fontSize: 10 }} />}
            {label}
          </span>
        );
      })}
    </Space>
  );
};

export const ContentStatusPill: React.FC<{ status: number }> = ({ status }) => {
  const s = STAGES[status] ?? STAGES[1];
  return <Pill tone={s.tone}>{s.text}</Pill>;
};

/** 资料状态的一句话：谁在什么时候导入、核对 */
export const factsSummary = (v: {
  status: number;
  factImportedAt?: string;
  factImportedBy?: string;
  factVerifiedAt?: string;
  factVerifiedBy?: string;
}) => {
  const fmt = (t?: string) => (t ? dayjs(t).format('MM-DD HH:mm') : '');
  if (factsVerified(v.status) && v.factVerifiedAt)
    return `${fmt(v.factVerifiedAt)} ${v.factVerifiedBy} 已核对`;
  if (v.factImportedAt)
    return `${fmt(v.factImportedAt)} ${v.factImportedBy} 导入了资料包，还没人核对`;
  return '还没有导入资料包';
};

/** 营销包下载范围：全部启用平台、某个平台、独立站俄文 */
export type MarketingPick = MarketingPlatform | 'all' | 'site-ru';

/** 下载任务包；营销包默认全部启用平台，资料没补全或没核对时提醒一句，但不拦着 */
export const useDownloadPackage = () => {
  const { message } = App.useApp();
  /** 正在下载的包：facts 或营销平台 */
  const [downloading, setDownloading] = useState<string>();
  const download = async (
    productIds: number[],
    type: PackageType,
    stages: number[] = [],
    platform?: MarketingPick,
  ) => {
    if (type === 'marketing') {
      if (stages.some((s) => s === 1))
        message.warning('有商品资料尚未补全，营销内容会缺少依据');
      else if (stages.some((s) => s === 2))
        message.warning('有商品资料尚未核对，生成营销内容前建议先核对');
    }
    setDownloading(platform ?? type);
    try {
      await contentApi.download(
        productIds,
        type,
        platform === 'all' ? undefined : platform,
      );
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setDownloading(undefined);
    }
  };
  return { download, downloading };
};

/** 商品详情里的内容数据：资料状态与本公司营销内容共用一份 */
export const useProductContent = (productId: number) => {
  const [data, setData] = useState<ProductContent>();
  const [error, setError] = useState<string>();
  const load = useCallback(async () => {
    setError(undefined);
    try {
      setData(await contentApi.productContent(productId));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [productId]);
  useEffect(() => {
    load();
  }, [load]);
  return { data, error, load };
};

/** 「资料已核」：二次确认后调用，成功后刷新；表格行里用链接样式 */
export const VerifyFactsButton: React.FC<{
  productId: number;
  label: string;
  size?: 'small' | 'middle';
  link?: boolean;
  onDone: () => void;
}> = ({ productId, label, size, link, onDone }) => {
  const { message, modal } = App.useApp();
  return (
    <Button
      size={size}
      type={link ? 'link' : 'primary'}
      icon={link ? undefined : <CheckOutlined />}
      style={link ? { padding: 0, height: 'auto' } : undefined}
      onClick={() =>
        modal.confirm({
          title: `确认「${label}」的资料已核对？`,
          content:
            '未核实的中文规格和应用场景会标为已核实，以后导入资料包不再覆盖它们。核对人和时间所有公司都看得到。',
          okText: '资料已核',
          cancelText: '取消',
          onOk: async () => {
            try {
              await contentApi.verifyFacts(productId);
              message.success('已标记资料已核');
              onDone();
            } catch (e) {
              message.error(readBizError(e).message);
            }
          },
        })
      }
    >
      资料已核
    </Button>
  );
};

/** 商品资料组的状态条：资料阶段（所有公司共享）+ 核对、下载资料包 */
export const FactsStatusBar: React.FC<{
  productId: number;
  mpn: string;
  data?: ProductContent;
  onChanged: () => void;
}> = ({ productId, mpn, data, onChanged }) => {
  const { palette } = useProductTheme();
  const access = useAccess() as Record<string, boolean>;
  const { download, downloading } = useDownloadPackage();
  const [showNotes, setShowNotes] = useState(false);
  if (!data) return null;
  const verified = factsVerified(data.status);
  const notes = data.factNotes ?? [];
  return (
    <div style={{ marginBottom: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
          padding: '12px 16px',
          borderRadius: 12,
          background: palette.inset,
        }}
      >
        <Pill tone={verified ? 'green' : data.status === 2 ? 'orange' : 'gray'}>
          {verified ? '资料已核对' : STAGES[data.status]?.text}
        </Pill>
        <span style={{ fontSize: 13, color: palette.sub }}>
          {factsSummary(data)}
        </span>
        {notes.length > 0 && (
          <Button
            size="small"
            type="text"
            aria-expanded={showNotes}
            onClick={() => setShowNotes((v) => !v)}
            style={{ padding: 0, height: 'auto' }}
          >
            <Pill tone="orange">
              待确认 {notes.length} 项{' '}
              {showNotes ? (
                <UpOutlined style={{ fontSize: 10 }} />
              ) : (
                <DownOutlined style={{ fontSize: 10 }} />
              )}
            </Pill>
          </Button>
        )}
        <span style={{ flex: 1 }} />
        {access.productFactTasks && (
          <Space size={8}>
            {!verified && access['product:content:edit'] && (
              <VerifyFactsButton
                productId={productId}
                label={mpn}
                size="small"
                onDone={onChanged}
              />
            )}
            <Button
              size="small"
              icon={<DownloadOutlined />}
              loading={downloading === 'facts'}
              onClick={() => download([productId], 'facts')}
            >
              下载资料包
            </Button>
          </Space>
        )}
      </div>
      {showNotes && notes.length > 0 && <FactNotes notes={notes} />}
    </div>
  );
};

/** 资料待确认项：来自资料包的「关键未知字段」「冲突与待确认」，所有公司共用，每次导入替换 */
const FactNotes: React.FC<{ notes: FactNote[] }> = ({ notes }) => {
  const { palette } = useProductTheme();
  return (
    <div
      style={{
        marginTop: 10,
        padding: '14px 16px',
        borderRadius: 12,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <div style={{ fontSize: 12, color: palette.sub, marginBottom: 10 }}>
        来自资料包「关键未知字段」「冲突与待确认」，每次导入资料包整份替换；只是提示，不影响资料核对
      </div>
      <Table<FactNote>
        size="small"
        pagination={false}
        rowKey={(n) => `${n.kind}-${n.item}`}
        dataSource={notes}
        scroll={{ x: 760 }}
        columns={[
          {
            title: '类型',
            dataIndex: 'kind',
            width: 100,
            render: (k: number) =>
              k === 1 ? (
                <Pill tone="gray">未知字段</Pill>
              ) : (
                <Pill tone="orange">冲突</Pill>
              ),
          },
          { title: '项目', dataIndex: 'item', width: 260 },
          {
            title: '当前状态',
            dataIndex: 'statusText',
            width: 180,
            render: (v?: string) => v || '—',
          },
          {
            title: '影响 / 说明',
            dataIndex: 'impact',
            render: (v?: string) => (
              <span style={{ color: palette.sub }}>{v || '—'}</span>
            ),
          },
        ]}
      />
    </div>
  );
};

/** 下载营销包：第一项为全部启用平台（一个文件），其次是单个平台和独立站俄文 */
export const MarketingDownload: React.FC<{
  platforms: MarketingPlatform[];
  downloading?: string;
  disabled?: boolean;
  size?: 'small' | 'middle';
  link?: boolean;
  label?: string;
  onPick: (pick: MarketingPick) => void;
}> = ({ platforms, downloading, disabled, size, link, label, onPick }) => {
  const option = (key: MarketingPick, name: string, file: string) => ({
    key,
    label: (
      <span
        style={{
          display: 'inline-flex',
          gap: 12,
          justifyContent: 'space-between',
          minWidth: 260,
        }}
      >
        <span>{name}</span>
        <span style={{ opacity: 0.6, fontSize: 12 }}>{file}</span>
      </span>
    ),
  });
  const menu = {
    items: [
      option(
        'all',
        '全部启用平台（推荐）',
        platforms.includes('site')
          ? '型号.marketing.md · 含独立站俄文'
          : '型号.marketing.md · 一个文件',
      ),
      { type: 'divider' as const },
      ...platforms.map((p) =>
        option(p, PLATFORM_META[p].label, PLATFORM_META[p].file),
      ),
      ...(platforms.includes('site')
        ? [
            option(
              'site-ru',
              '独立站俄文（单独重做）',
              '型号.site.ru.md · 俄文',
            ),
          ]
        : []),
    ],
    onClick: ({ key }: { key: string }) => onPick(key as MarketingPick),
  };
  const loading = !!downloading && downloading !== 'facts';
  if (link) {
    return (
      <Dropdown menu={menu} trigger={['click']} disabled={disabled}>
        <a>
          {label ?? '下载营销包'} <DownOutlined style={{ fontSize: 10 }} />
        </a>
      </Dropdown>
    );
  }
  return (
    <Dropdown menu={menu} trigger={['click']} disabled={disabled}>
      <Button size={size} icon={<DownloadOutlined />} loading={loading}>
        {label ?? '下载营销包'} <DownOutlined style={{ fontSize: 10 }} />
      </Button>
    </Dropdown>
  );
};

const Field: React.FC<{ label: string; children: React.ReactNode }> = ({
  label,
  children,
}) => {
  const { palette } = useProductTheme();
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ fontSize: 12, color: palette.sub, marginBottom: 4 }}>
        {label}
      </div>
      <div style={{ color: palette.ink, lineHeight: 1.6 }}>{children}</div>
    </div>
  );
};

const muted = (palette: { mute: string }, v?: string) =>
  v ? v : <span style={{ color: palette.mute }}>—</span>;

/** 关键词库：按核心、主要、长尾分组，组内按顺序 */
const KeywordLibrary: React.FC<{ keywords: PlatformContent['keywords'] }> = ({
  keywords,
}) => {
  const { palette } = useProductTheme();
  if (keywords.length === 0) return muted(palette);
  const tiers = [
    { tier: 1, name: '核心', fg: palette.link, bg: palette.accentSoft },
    { tier: 2, name: '主要', fg: palette.violet, bg: palette.violetSoft },
    { tier: 3, name: '长尾', fg: palette.sub, bg: palette.inset },
  ];
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        padding: 12,
        borderRadius: 10,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      {tiers.map(({ tier, name, fg, bg }) => {
        const words = keywords.filter((k) => k.tier === tier);
        if (words.length === 0) return null;
        return (
          <div key={tier} style={{ display: 'flex', gap: 12 }}>
            <div style={{ width: 52, flex: '0 0 52px' }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: fg }}>
                {name}
              </div>
              <div style={{ fontSize: 11, color: palette.mute }}>
                {words.length} 个
              </div>
            </div>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {words.map((w) => (
                <span
                  key={w.keyword}
                  style={{
                    padding: '2px 8px',
                    borderRadius: 6,
                    fontSize: 12,
                    fontWeight: 500,
                    color: fg,
                    background: bg,
                  }}
                >
                  {w.keyword}
                </span>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
};

const LongText: React.FC<{ text?: string; rows?: number }> = ({
  text,
  rows = 4,
}) => {
  const { palette } = useProductTheme();
  if (!text) return muted(palette);
  return (
    <Typography.Paragraph
      style={{ margin: 0, whiteSpace: 'pre-wrap' }}
      ellipsis={{ rows, expandable: true, symbol: '展开' }}
    >
      {text}
    </Typography.Paragraph>
  );
};

const SourceLine: React.FC<{
  fileName?: string;
  confirmedBy?: string;
  confirmedAt?: string;
}> = ({ fileName, confirmedBy, confirmedAt }) => {
  const { palette } = useProductTheme();
  if (!fileName) return null;
  return (
    <div style={{ fontSize: 12, color: palette.mute }}>
      来自 {fileName} · {confirmedBy} ·{' '}
      {dayjs(confirmedAt).format('YYYY-MM-DD HH:mm')}
    </div>
  );
};

/** 问答列表：平台 FAQ、独立站营销问答 */
const FaqList: React.FC<{ faqs?: { question: string; answer: string }[] }> = ({
  faqs,
}) => {
  const { palette } = useProductTheme();
  if (!faqs || faqs.length === 0) return muted(palette);
  return (
    <ol style={{ margin: 0, paddingLeft: 18 }}>
      {faqs.map((f) => (
        <li key={f.question} style={{ marginBottom: 8 }}>
          <div style={{ fontWeight: 600 }}>{f.question}</div>
          <div style={{ color: palette.sub }}>{f.answer}</div>
        </li>
      ))}
    </ol>
  );
};

/** 两列表：产品属性、图片说明文字 */
const PairTable: React.FC<{
  head: [string, string];
  rows?: [string, string][];
}> = ({ head, rows }) => {
  const { palette } = useProductTheme();
  if (!rows || rows.length === 0) return muted(palette);
  return (
    <Table
      size="small"
      pagination={false}
      rowKey={(r) => r[0]}
      dataSource={rows}
      columns={[
        {
          title: head[0],
          key: 'k',
          width: 220,
          render: (_: unknown, r: [string, string]) => <b>{r[0]}</b>,
        },
        { title: head[1], key: 'v', render: (_: unknown, r) => r[1] },
      ]}
    />
  );
};

/** 社媒帖子、视频方案：按原文的 Markdown 排版，默认折叠 */
const MarkdownList: React.FC<{
  items: { key: string; title: string; markdown: string }[];
}> = ({ items }) => {
  const { palette } = useProductTheme();
  if (items.length === 0) return muted(palette);
  return (
    <Collapse
      size="small"
      items={items.map((it) => ({
        key: it.key,
        label: it.title,
        children: <XMarkdown>{it.markdown}</XMarkdown>,
      }))}
    />
  );
};

/** 阿里国际站、中国制造网、社媒的内容 */
const PlatformBody: React.FC<{
  platform: MarketingPlatform;
  content?: PlatformContent;
}> = ({ platform, content: c }) => {
  const { palette } = useProductTheme();
  if (!c) {
    return (
      <div style={{ color: palette.sub, padding: '8px 0' }}>
        还没有{PLATFORM_META[platform].label}的营销内容。下载营销包交给 AI
        生成，生成的 Markdown 在这里上传即可。
      </div>
    );
  }
  if (platform === 'social') {
    const posts = c.posts ?? [];
    const videos = c.videos ?? [];
    return (
      <>
        {posts.length > 0 || videos.length > 0 ? (
          <>
            <Field label={`帖子（${posts.length} 篇）`}>
              <MarkdownList
                items={posts.map((p, i) => ({
                  key: `p${i}`,
                  title: `${p.channel} · ${p.label}`,
                  markdown: p.text,
                }))}
              />
            </Field>
            <Field label={`视频方案（${videos.length} 个）`}>
              <MarkdownList
                items={videos.map((v, i) => ({
                  key: `v${i}`,
                  title: `YouTube · ${v.label}`,
                  markdown: v.markdown,
                }))}
              />
            </Field>
          </>
        ) : (
          <>
            <Field label="LinkedIn 帖子">
              <LongText text={c.linkedinPost} />
            </Field>
            <Field label="Facebook 帖子">
              <LongText text={c.facebookPost} />
            </Field>
            <Field label="YouTube 视频描述">
              <LongText text={c.youtubeDescription} />
            </Field>
          </>
        )}
        <SourceLine {...c} />
      </>
    );
  }
  const alternates = c.titleAlternates ?? [];
  return (
    <>
      <Field label="标题（推荐）">
        {c.title ? (
          <span>
            {c.title}
            <span style={{ fontSize: 12, color: palette.mute }}>
              {' · '}
              {c.title.length} 字符
            </span>
          </span>
        ) : (
          muted(palette)
        )}
      </Field>
      {alternates.length > 0 && (
        <Field label={`备选标题（${alternates.length} 个）`}>
          {alternates.map((t) => (
            <div key={t.title}>
              {t.title}
              {t.angle && (
                <span style={{ fontSize: 12, color: palette.mute }}>
                  （{t.angle}）
                </span>
              )}
            </div>
          ))}
        </Field>
      )}
      <Field
        label={`选用关键词（${c.keywords.length} 个，核心 → 主要 → 长尾）`}
      >
        <KeywordLibrary keywords={c.keywords} />
      </Field>
      <Field label={`产品属性（${c.attributes?.length ?? 0} 项）`}>
        <PairTable
          head={['属性', '值']}
          rows={c.attributes?.map((a) => [a.name, a.value])}
        />
      </Field>
      <Field label={`卖点（${c.sellingPoints.length} 条）`}>
        {c.sellingPoints.length > 0 ? (
          <ol style={{ margin: 0, paddingLeft: 18 }}>
            {c.sellingPoints.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ol>
        ) : (
          muted(palette)
        )}
      </Field>
      <Field label="产品详情">
        <LongText text={c.detail} rows={6} />
      </Field>
      <Field label={`FAQ（${c.faqs?.length ?? 0} 条）`}>
        <FaqList faqs={c.faqs} />
      </Field>
      <Field label="询盘引导语">{muted(palette, c.cta)}</Field>
      <SourceLine {...c} />
    </>
  );
};

/** 独立站：英文、俄文的 SEO、H1、URL 别名、GEO、长描述、营销问答、图片说明文字 */
const SiteBody: React.FC<{ data: ProductContent }> = ({ data }) => {
  const { palette } = useProductTheme();
  const [lang, setLang] = useState<ContentLang>('en');
  const c = data.langs[lang];
  const empty =
    !c ||
    (!c.seoTitle &&
      !c.metaDescription &&
      !c.geoAnswer &&
      !c.longDescription &&
      !c.h1 &&
      c.faqs.length === 0);
  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          marginBottom: 14,
        }}
      >
        <LangSwitch value={lang} onChange={setLang} />
        <LangChips enAt={data.enAt} ruAt={data.ruAt} />
      </div>
      {empty ? (
        <div style={{ color: palette.sub, padding: '8px 0' }}>
          这种语言还没有营销内容。下载营销包（全部平台的文件已含独立站俄文章节）交给
          AI 生成，生成的 Markdown 在这里上传即可。
        </div>
      ) : (
        <>
          <Field label="SEO 标题">{muted(palette, c?.seoTitle)}</Field>
          <Field label="SEO 描述">{muted(palette, c?.metaDescription)}</Field>
          <Field label="H1">{muted(palette, c?.h1)}</Field>
          <Field label="URL 别名">
            {c?.urlSlug ? <code>{c.urlSlug}</code> : muted(palette)}
          </Field>
          <Field label="首屏定义块（GEO）">
            {muted(palette, c?.geoAnswer)}
          </Field>
          <Field label="产品长描述">
            <LongText text={c?.longDescription} rows={3} />
          </Field>
          <Field label={`营销问答（${c?.faqs.length ?? 0} 条）`}>
            <FaqList faqs={c?.faqs} />
          </Field>
          <Field label={`图片说明文字（${c?.altTexts?.length ?? 0} 张）`}>
            <PairTable
              head={['图片', 'Alt Text']}
              rows={c?.altTexts?.map((a) => [a.image, a.alt])}
            />
          </Field>
          <SourceLine {...c} />
        </>
      )}
    </>
  );
};

/** 「不发布」开关：二次确认后标记或撤销，只影响本公司 */
const SkipSwitch: React.FC<{
  productId: number;
  platform: MarketingPlatform;
  skip?: MarketingSkip;
  disabled?: boolean;
  onDone: () => void;
}> = ({ productId, platform, skip, disabled, onDone }) => {
  const { message, modal } = App.useApp();
  const label = PLATFORM_META[platform].label;
  const toggle = (on: boolean) =>
    modal.confirm({
      title: on ? `这个商品不在${label}发布？` : `撤销${label}的「不发布」？`,
      content: on
        ? `标了不发布的平台不计进度，已写入的内容保留，以后也能照常上传。只影响本公司。`
        : `撤销后${label}重新计入进度。`,
      okText: on ? '不发布' : '撤销',
      cancelText: '取消',
      onOk: async () => {
        try {
          await (on
            ? contentApi.skip(productId, platform)
            : contentApi.unskip(productId, platform));
          message.success(on ? `已标为${label}不发布` : '已撤销不发布');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <Switch
        size="small"
        checked={!!skip}
        disabled={disabled}
        onChange={toggle}
        aria-label={`${label}不发布`}
      />
      <span style={{ fontSize: 12 }}>不发布</span>
    </span>
  );
};

/** 商品详情的运营概况（本公司，只读）：各平台写入状态 +「去营销任务」；营销内容在运营管理 → 营销任务维护 */
export const OperationsSummary: React.FC<{
  productId: number;
  data?: ProductContent;
}> = ({ productId, data }) => {
  const { palette } = useProductTheme();
  const access = useAccess() as Record<string, boolean>;
  if (!data) return null;
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        flexWrap: 'wrap',
        padding: '14px 18px',
        borderRadius: 16,
        border: `1px solid ${palette.hairline}`,
        background: palette.card,
        marginBottom: 20,
      }}
    >
      <b style={{ color: palette.ink }}>运营概况</b>
      <Pill tone="violet">本公司</Pill>
      <PlatformChips progress={data} platforms={data.platforms} />
      <span style={{ fontSize: 12, color: palette.sub }}>
        营销内容在「运营管理 → 营销任务」维护
      </span>
      <span style={{ flex: 1 }} />
      {access.operationsProducts && (
        <Button
          size="small"
          onClick={() => history.push(`/operations/products/${productId}`)}
        >
          去营销任务
        </Button>
      )}
    </div>
  );
};

/** 单个平台的营销内容：写入状态条（下载营销包、上传、不发布开关）+ 内容；营销任务详情的平台页签用 */
export const PlatformPanel: React.FC<{
  productId: number;
  platform: MarketingPlatform;
  data: ProductContent;
  onChanged: () => void;
  onUpload?: () => void;
}> = ({ productId, platform: p, data: d, onChanged, onUpload }) => {
  const { palette } = useProductTheme();
  const access = useAccess() as Record<string, boolean>;
  const { download, downloading } = useDownloadPackage();
  const skip = d.skips?.[p];
  const at = writtenAt(d, p);
  const pc = d.platformContents?.[p];
  const text = skip
    ? `${dayjs(skip.markedAt).format('YYYY-MM-DD HH:mm')} ${skip.markedBy ?? ''} 标为不发布，不计进度；已写入的内容保留`
    : p === 'site'
      ? at
        ? '英文、俄文都已写入'
        : d.enAt
          ? '英文已写入，俄文还没写入'
          : '还没写入'
      : at
        ? `${dayjs(at).format('YYYY-MM-DD HH:mm')}${pc?.confirmedBy ? ` · ${pc.confirmedBy}` : ''} 写入`
        : '还没写入';
  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          flexWrap: 'wrap',
          padding: '10px 14px',
          borderRadius: 12,
          background: palette.inset,
          marginBottom: 16,
        }}
      >
        {skip ? (
          <Pill tone="gray">不发布</Pill>
        ) : (
          <Pill tone={at ? 'green' : 'gray'}>{at ? '已写入' : '未写入'}</Pill>
        )}
        <span style={{ fontSize: 12, color: palette.sub }}>{text}</span>
        <span style={{ flex: 1 }} />
        <Space size={10}>
          <Button
            size="small"
            icon={<DownloadOutlined />}
            loading={downloading === p}
            onClick={() => download([productId], 'marketing', [d.status], p)}
          >
            下载营销包
          </Button>
          {onUpload && (
            <Button size="small" onClick={onUpload}>
              上传营销包
            </Button>
          )}
          <SkipSwitch
            productId={productId}
            platform={p}
            skip={skip}
            disabled={!access['operations:marketing:edit']}
            onDone={onChanged}
          />
        </Space>
      </div>
      {p === 'site' ? (
        <SiteBody data={d} />
      ) : (
        <PlatformBody platform={p} content={pc} />
      )}
    </>
  );
};
