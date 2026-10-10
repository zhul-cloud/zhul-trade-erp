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
  type ProductContent,
  readBizError,
} from '../../service';
import { useProductTheme } from '../../theme';
import { CardShell } from './CardShell';

const LANG_CHIPS: { lang: ContentLang; label: string }[] = [
  { lang: 'zh', label: '中' },
  { lang: 'en', label: 'EN' },
  { lang: 'ru', label: 'RU' },
];

const STATUS: Record<
  number,
  { text: string; tone: 'gray' | 'orange' | 'green' }
> = {
  1: { text: '待生成', tone: 'gray' },
  2: { text: '进行中', tone: 'orange' },
  3: { text: '已完成', tone: 'green' },
};

/** 三种语言是否已写入的小标签，内容任务列表也用 */
export const LangChips: React.FC<{
  zhAt?: string;
  enAt?: string;
  ruAt?: string;
}> = (props) => {
  const { palette } = useProductTheme();
  const at: Record<ContentLang, string | undefined> = {
    zh: props.zhAt,
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
  const s = STATUS[status] ?? STATUS[1];
  return <Pill tone={s.tone}>{s.text}</Pill>;
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

/** 本公司的 SEO & GEO 内容与内容任务进度；其他公司看不到 */
export const SeoCard: React.FC<{ productId: number; mpn: string }> = ({
  productId,
  mpn,
}) => {
  const { message } = App.useApp();
  const { palette } = useProductTheme();
  const access = useAccess() as Record<string, boolean>;
  const [data, setData] = useState<ProductContent>();
  const [error, setError] = useState<string>();
  const [lang, setLang] = useState<ContentLang>('en');
  const [downloading, setDownloading] = useState(false);

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

  const download = async () => {
    setDownloading(true);
    try {
      await contentApi.download([productId]);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setDownloading(false);
    }
  };

  const c = data?.langs[lang];
  const empty =
    !c ||
    (!c.seoTitle &&
      !c.metaDescription &&
      !c.geoAnswer &&
      !c.longDescription &&
      !c.specSummary &&
      c.faqs.length === 0);
  const muted = (v?: string) =>
    v ? v : <span style={{ color: palette.sub }}>—</span>;

  return (
    <CardShell
      id="card-seo"
      title="SEO & GEO"
      actions={
        <Space size={8} wrap>
          <Pill tone="accent">本公司</Pill>
          <LangSwitch value={lang} onChange={setLang} />
        </Space>
      }
    >
      {error ? (
        <EmptyHint
          title="加载失败"
          description={error}
          actionText="重试"
          onAction={load}
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
            <span style={{ fontSize: 13, color: palette.sub }}>内容任务</span>
            <LangChips zhAt={data.zhAt} enAt={data.enAt} ruAt={data.ruAt} />
            <ContentStatusPill status={data.status} />
            <span style={{ flex: 1 }} />
            {access.productContentTasks && (
              <Space size={8}>
                <Button
                  size="small"
                  icon={<DownloadOutlined />}
                  loading={downloading}
                  onClick={download}
                >
                  下载任务包
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
              这种语言还没有内容。下载任务包交给 AI 生成，生成的 Markdown
              在「内容任务」上传即可。
            </div>
          ) : (
            <>
              <Field label="SEO 标题">{muted(c?.seoTitle)}</Field>
              <Field label="SEO 描述">{muted(c?.metaDescription)}</Field>
              <Field label="首屏定义块（GEO）">{muted(c?.geoAnswer)}</Field>
              <Field label="一句话规格摘要（共享）">
                {muted(c?.specSummary)}
              </Field>
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
              <Field label={`FAQ（${c?.faqs.length ?? 0} 条）`}>
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
