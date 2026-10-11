import { CheckOutlined, DownloadOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import { App, Button, Space, Typography } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint } from '../../components/EmptyHint';
import { LangSwitch } from '../../components/LangSwitch';
import { Pill } from '../../components/Pills';
import {
  type ContentLang,
  contentApi,
  type PackageType,
  type ProductContent,
  readBizError,
} from '../../service';
import { useProductTheme } from '../../theme';
import { CardShell } from './CardShell';

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

/** 本公司英文、俄文营销内容是否已写入的小标签，内容任务列表也用 */
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

/** 下载任务包；营销包在资料没补全或没核对时提醒一句，但不拦着 */
export const useDownloadPackage = () => {
  const { message } = App.useApp();
  const [downloading, setDownloading] = useState<PackageType>();
  const download = async (
    productIds: number[],
    type: PackageType,
    stages: number[] = [],
  ) => {
    if (type === 'marketing') {
      if (stages.some((s) => s === 1))
        message.warning('有商品资料尚未补全，营销内容会缺少依据');
      else if (stages.some((s) => s === 2))
        message.warning('有商品资料尚未核对，生成营销内容前建议先核对');
    }
    setDownloading(type);
    try {
      await contentApi.download(productIds, type);
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
  if (!data) return null;
  const verified = factsVerified(data.status);
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        flexWrap: 'wrap',
        padding: '12px 16px',
        borderRadius: 12,
        background: palette.inset,
        marginBottom: 20,
      }}
    >
      <Pill tone={verified ? 'green' : data.status === 2 ? 'orange' : 'gray'}>
        {verified ? '资料已核对' : STAGES[data.status]?.text}
      </Pill>
      <span style={{ fontSize: 13, color: palette.sub }}>
        {factsSummary(data)}
      </span>
      <span style={{ flex: 1 }} />
      {access.productContentTasks && (
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

/** 本公司的独立站营销内容（英文、俄文）与写入进度；其他公司看不到 */
export const SeoCard: React.FC<{
  mpn: string;
  productId: number;
  data?: ProductContent;
  error?: string;
  onRetry: () => void;
}> = ({ mpn, productId, data, error, onRetry }) => {
  const { palette } = useProductTheme();
  const access = useAccess() as Record<string, boolean>;
  const [lang, setLang] = useState<ContentLang>('en');
  const { download, downloading } = useDownloadPackage();

  const c = data?.langs[lang];
  const empty =
    !c ||
    (!c.seoTitle &&
      !c.metaDescription &&
      !c.geoAnswer &&
      !c.longDescription &&
      c.faqs.length === 0);
  const muted = (v?: string) =>
    v ? v : <span style={{ color: palette.sub }}>—</span>;

  return (
    <CardShell
      id="card-seo"
      title="独立站营销内容"
      actions={<LangSwitch value={lang} onChange={setLang} />}
    >
      {error ? (
        <EmptyHint
          title="加载失败"
          description={error}
          actionText="重试"
          onAction={onRetry}
        />
      ) : !data ? (
        <div style={{ color: palette.sub }}>正在加载…</div>
      ) : (
        <>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              flexWrap: 'wrap',
              padding: '10px 14px',
              borderRadius: 12,
              background: palette.inset,
              marginBottom: 16,
            }}
          >
            <span style={{ fontSize: 13, color: palette.sub }}>写入进度</span>
            <LangChips enAt={data.enAt} ruAt={data.ruAt} />
            {!factsVerified(data.status) && (
              <span style={{ fontSize: 12, color: palette.orange }}>
                商品资料{data.status === 1 ? '尚未补全' : '尚未核对'}
              </span>
            )}
            <span style={{ flex: 1 }} />
            {access.productContentTasks && (
              <Space size={8}>
                <Button
                  size="small"
                  icon={<DownloadOutlined />}
                  loading={downloading === 'marketing'}
                  onClick={() =>
                    download([productId], 'marketing', [data.status])
                  }
                >
                  下载营销包
                </Button>
                <Button
                  size="small"
                  onClick={() =>
                    history.push(
                      `/product/content-tasks?keyword=${encodeURIComponent(mpn)}`,
                    )
                  }
                >
                  去上传
                </Button>
              </Space>
            )}
          </div>
          {empty ? (
            <div style={{ color: palette.sub, padding: '8px 0' }}>
              这种语言还没有营销内容。下载营销包交给 AI 生成，生成的 Markdown
              在「内容任务」上传即可。
            </div>
          ) : (
            <>
              <Field label="SEO 标题">{muted(c?.seoTitle)}</Field>
              <Field label="SEO 描述">{muted(c?.metaDescription)}</Field>
              <Field label="首屏定义块（GEO）">{muted(c?.geoAnswer)}</Field>
              <Field label="产品长描述">
                {c?.longDescription ? (
                  <Typography.Paragraph
                    style={{ margin: 0, whiteSpace: 'pre-line' }}
                    ellipsis={{ rows: 3, expandable: true, symbol: '展开' }}
                  >
                    {c.longDescription}
                  </Typography.Paragraph>
                ) : (
                  muted()
                )}
              </Field>
              <Field label={`营销问答（${c?.faqs.length ?? 0} 条）`}>
                {c && c.faqs.length > 0 ? (
                  <ol style={{ margin: 0, paddingLeft: 18 }}>
                    {c.faqs.map((f) => (
                      <li key={f.question} style={{ marginBottom: 8 }}>
                        <div style={{ fontWeight: 600 }}>{f.question}</div>
                        <div style={{ color: palette.sub }}>{f.answer}</div>
                      </li>
                    ))}
                  </ol>
                ) : (
                  muted()
                )}
              </Field>
              {c?.fileName && (
                <div style={{ fontSize: 12, color: palette.sub }}>
                  来自 {c.fileName} · {c.confirmedBy} ·{' '}
                  {dayjs(c.confirmedAt).format('YYYY-MM-DD HH:mm')}
                </div>
              )}
            </>
          )}
        </>
      )}
    </CardShell>
  );
};
