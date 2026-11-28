import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  ExportOutlined,
  LockOutlined,
  PictureOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { Link } from '@umijs/max';
import { App, Button, InputNumber, Modal, Segmented } from 'antd';
import React, { useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { fieldLabel } from '@/pages/operations/websites/service';
import { useAppTheme } from '@/theme/AppTheme';
import { readBizError } from '../service';
import { MONO } from './ReleaseDrawer';
import {
  type Change,
  LANG_LABEL,
  type Preview,
  sitePublishApi,
} from './service';

const str = (v: unknown) => (typeof v === 'string' ? v : '');
const count = (v: unknown) => (Array.isArray(v) ? v.length : 0);

/** 小标题 + 正文的灰底块 */
const Block: React.FC<{ label: string; value?: string }> = ({
  label,
  value,
}) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        padding: '10px 12px',
        borderRadius: 10,
        background: palette.inset,
      }}
    >
      <div style={{ fontSize: 12, color: palette.mute }}>{label}</div>
      <div
        style={{
          marginTop: 4,
          fontSize: 13,
          color: value ? palette.ink : palette.red,
          wordBreak: 'break-word',
        }}
      >
        {value || '缺少'}
      </div>
    </div>
  );
};

export const LangSwitch: React.FC<{
  langs: string[];
  value: string;
  onChange: (l: string) => void;
}> = ({ langs, value, onChange }) =>
  langs.length > 1 ? (
    <Segmented<string>
      value={value}
      onChange={onChange}
      options={langs.map((l) => ({
        value: l,
        label: l === 'ru' ? 'Русский' : l === 'en' ? 'English' : l,
      }))}
    />
  ) : null;

/** 图片和视频缩略条，第一张为主图 */
export const MediaStrip: React.FC<{ p: Preview; size?: number }> = ({
  p,
  size = 76,
}) => {
  const { palette } = useAppTheme();
  const photos = p.data.photos ?? [];
  const videos = p.data.videos ?? [];
  if (!photos.length && !videos.length)
    return <div style={{ fontSize: 13, color: palette.red }}>没有任何图片</div>;
  const box: React.CSSProperties = {
    width: size,
    height: size,
    borderRadius: 10,
    overflow: 'hidden',
    background: palette.inset,
    border: `1px solid ${palette.hairline}`,
    position: 'relative',
    flexShrink: 0,
    display: 'block',
  };
  return (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      {photos.map((m, i) => (
        <a
          key={m.src}
          href={m.src}
          target="_blank"
          rel="noopener noreferrer"
          style={box}
        >
          <PictureOutlined
            style={{
              position: 'absolute',
              inset: 0,
              margin: 'auto',
              width: 20,
              height: 20,
              fontSize: 20,
              color: palette.faint,
            }}
          />
          <img
            src={m.src}
            alt=""
            loading="lazy"
            onError={(e) => {
              e.currentTarget.style.visibility = 'hidden';
            }}
            style={{
              position: 'relative',
              width: '100%',
              height: '100%',
              objectFit: 'cover',
            }}
          />
          {i === 0 && (
            <span
              style={{
                position: 'absolute',
                top: 4,
                left: 4,
                padding: '0 6px',
                borderRadius: 6,
                fontSize: 11,
                color: '#fff',
                background: palette.link,
              }}
            >
              主图
            </span>
          )}
        </a>
      ))}
      {videos.map((m) => (
        <a
          key={m.src}
          href={m.src}
          target="_blank"
          rel="noopener noreferrer"
          style={{
            ...box,
            width: size * 1.8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: palette.sub,
            fontSize: 26,
          }}
        >
          <PlayCircleOutlined />
        </a>
      ))}
    </div>
  );
};

/** 页面内容：SEO 标题、SEO 描述、首屏定义块、一句话规格、规格表与数量、素材 */
export const PageContent: React.FC<{
  p: Preview;
  lang: string;
  compact?: boolean;
}> = ({ p, lang, compact }) => {
  const { palette } = useAppTheme();
  const d = p.data;
  const ru = lang === 'ru';
  const specs = d.specs ?? [];
  const shown = compact ? [] : specs.slice(0, 6);
  const counts = [
    ['应用场景', count(d.applications)],
    ['FAQ', count(d.faq)],
    ['型号关系', count(d.compatibility)],
    ...(ru ? [['俄文详细内容', count(d.deep_ru)]] : []),
  ] as [string, number][];
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      <Block label="SEO 标题" value={p.seoTitles[lang]} />
      <Block
        label="SEO 描述"
        value={str(ru ? d.meta_description_ru : d.meta_description)}
      />
      {!compact && (
        <Block
          label="首屏定义块"
          value={str(ru ? d.overview_ru : d.overview)}
        />
      )}
      <Block label="一句话规格" value={str(ru ? d.spec_ru : d.spec)} />
      {!compact && specs.length > 0 && !ru && (
        <div>
          <div
            style={{
              display: 'flex',
              fontSize: 12,
              color: palette.mute,
              marginBottom: 6,
            }}
          >
            <span style={{ flex: 1 }}>规格</span>
            {specs.length} 项
            {specs.length > shown.length && ` · 显示前 ${shown.length} 项`}
          </div>
          <div
            style={{
              border: `1px solid ${palette.hairline}`,
              borderRadius: 10,
              overflow: 'hidden',
            }}
          >
            {shown.map(([k, v], i) => (
              <div
                key={k}
                style={{
                  display: 'flex',
                  gap: 12,
                  padding: '7px 12px',
                  fontSize: 13,
                  borderTop: i ? `1px solid ${palette.hairline}` : undefined,
                }}
              >
                <span style={{ width: 180, color: palette.sub }}>{k}</span>
                <span style={{ fontFamily: MONO, color: palette.ink }}>
                  {v}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {counts.map(([k, n]) => (
          <Pill key={k} tone={n ? 'gray' : 'mute'}>
            {k} {n}
          </Pill>
        ))}
      </div>
      <div style={{ fontSize: 12, color: palette.mute }}>
        素材（素材库里「用于发品」的，第一张为主图）
        {p.referenceMedia && ' · 用的是参考型号的素材'}
      </div>
      <MediaStrip p={p} size={compact ? 64 : 76} />
    </div>
  );
};

/** 检查：阻断项（处理后才能发布）与提醒 */
export const Checks: React.FC<{ p: Preview }> = ({ p }) => {
  const { palette } = useAppTheme();
  if (!p.blockers.length && !p.warnings.length)
    return (
      <div style={{ color: palette.green, fontSize: 13 }}>
        <CheckCircleOutlined /> 检查通过
      </div>
    );
  const row = (icon: React.ReactNode, text: string, color: string) => (
    <div
      key={text}
      style={{
        display: 'flex',
        gap: 8,
        alignItems: 'flex-start',
        fontSize: 13,
        color: palette.ink,
      }}
    >
      <span style={{ color }}>{icon}</span>
      <span style={{ flex: 1 }}>{text}</span>
    </div>
  );
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      {p.blockers.length > 0 && (
        <>
          <div style={{ display: 'flex', fontSize: 12, color: palette.red }}>
            <span style={{ flex: 1 }}>
              阻断 {p.blockers.length} · 处理后才能发布
            </span>
            <Link to={`/operations/products/${p.productId}`}>
              <ExportOutlined /> 去营销任务
            </Link>
          </div>
          {p.blockers.map((b) => row(<CloseCircleOutlined />, b, palette.red))}
        </>
      )}
      {p.warnings.length > 0 && (
        <>
          <div style={{ fontSize: 12, color: palette.orange }}>
            提醒 {p.warnings.length} · 可以照样发布
          </div>
          {p.warnings.map((w) =>
            row(<ExclamationCircleOutlined />, w, palette.orange),
          )}
        </>
      )}
    </div>
  );
};

const money = (v?: number | null) =>
  v === null || v === undefined
    ? ''
    : v.toLocaleString('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });

/** 站点数据：售价（来源与算式、手工填写）、站点品类、站点状态、链接别名、上线后的链接 */
export const SiteDataCard: React.FC<{
  p: Preview;
  category?: string;
  canEdit: boolean;
  /** 改售价、站点状态后重新预览 */
  onChanged: () => void;
}> = ({ p, category, canEdit, onChanged }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const [editing, setEditing] = useState(false);
  const [price, setPrice] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const pr = p.price;
  const manual = pr?.source === 'manual';

  const save = async (v: number | null) => {
    setSaving(true);
    try {
      await sitePublishApi.setPrice(p.websiteProductId, v);
      message.success(v === null ? '已改回自动取价' : '已保存站点售价');
      setEditing(false);
      onChanged();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const setStatus = (status: string) =>
    modal.confirm({
      title: status ? '改为 legacy？' : '改回按生命周期？',
      content: status
        ? '站点上显示为老型号（legacy），下次发布生效。'
        : '站点状态按商品生命周期显示（在售或停产），下次发布生效。',
      onOk: async () => {
        try {
          await sitePublishApi.setSiteStatus(p.websiteProductId, status);
          onChanged();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });

  const label = (t: string) => (
    <span style={{ width: 84, flexShrink: 0, color: palette.sub }}>{t}</span>
  );
  const rowStyle: React.CSSProperties = {
    display: 'flex',
    gap: 12,
    alignItems: 'center',
    fontSize: 13,
  };
  const value: React.CSSProperties = {
    flex: 1,
    minWidth: 0,
    fontFamily: MONO,
    color: palette.ink,
    wordBreak: 'break-all',
  };

  return (
    <div style={{ display: 'grid', gap: 12 }}>
      <div>
        <div style={rowStyle}>
          {label('售价')}
          <span style={value}>
            {pr?.amount != null ? (
              `${pr.currency === 'USD' ? '$' : `${pr.currency ?? ''} `}${money(pr.amount)}`
            ) : (
              <span style={{ fontFamily: 'inherit', color: palette.orange }}>
                没有售价，站点显示询价
              </span>
            )}
          </span>
          {pr?.amount != null && <Pill tone="accent">{pr.sourceName}</Pill>}
        </div>
        {pr?.unitPriceCny != null && pr.factor != null && pr.rate != null && (
          <div
            style={{
              marginLeft: 96,
              marginTop: 4,
              fontSize: 12,
              color: palette.mute,
            }}
          >
            ¥{money(pr.unitPriceCny)} × {pr.factor} ÷ {pr.rate}
            {pr.rateDate && ` · ${pr.rateDate} 汇率`}
          </div>
        )}
        {canEdit && (
          <div
            style={{ marginLeft: 96, marginTop: 4, display: 'flex', gap: 4 }}
          >
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              style={{ paddingInline: 0 }}
              onClick={() => {
                setPrice(manual ? (pr?.amount ?? null) : null);
                setEditing(true);
              }}
            >
              {manual ? '修改手工售价' : '手工填写站点售价'}
            </Button>
            {manual && (
              <Button
                type="link"
                size="small"
                loading={saving}
                onClick={() => save(null)}
              >
                改回自动取价
              </Button>
            )}
          </div>
        )}
      </div>
      <div style={rowStyle}>
        {label('站点品类')}
        <span style={value}>
          {p.siteCategory || (
            <span style={{ fontFamily: 'inherit', color: palette.red }}>
              没有对照
            </span>
          )}
        </span>
        {category && p.siteCategory && (
          <span style={{ fontSize: 12, color: palette.mute }}>
            {category} → {p.siteCategory}
          </span>
        )}
      </div>
      <div style={rowStyle}>
        {label('站点状态')}
        <span style={value}>{p.siteStatus || '—'}</span>
        {canEdit && (
          <Button
            type="link"
            size="small"
            onClick={() => setStatus(p.siteStatus === 'legacy' ? '' : 'legacy')}
          >
            {p.siteStatus === 'legacy' ? '改回按生命周期' : '改为 legacy'}
          </Button>
        )}
      </div>
      <div style={rowStyle}>
        {label('链接别名')}
        <span style={value}>{p.slug}</span>
        {!p.firstPublish && (
          <span style={{ fontSize: 12, color: palette.mute }}>
            <LockOutlined /> 发布后锁定
          </span>
        )}
      </div>
      <div style={{ display: 'grid', gap: 4, fontSize: 13 }}>
        <span style={{ color: palette.sub }}>上线后的链接</span>
        {Object.entries(p.liveUrls).map(([l, u]) => (
          <div key={l} style={{ display: 'flex', gap: 8 }}>
            <span style={{ width: 24, color: palette.mute, fontSize: 12 }}>
              {LANG_LABEL[l] ?? l}
            </span>
            <span style={{ ...value, fontSize: 12 }}>{u}</span>
          </div>
        ))}
      </div>
      <Modal
        open={editing}
        title="手工填写站点售价"
        okText="保存"
        confirmLoading={saving}
        okButtonProps={{ disabled: !price }}
        onOk={() => save(price)}
        onCancel={() => setEditing(false)}
        destroyOnHidden
      >
        <div style={{ display: 'grid', gap: 8 }}>
          <InputNumber<number>
            autoFocus
            min={0.01}
            precision={2}
            value={price}
            onChange={setPrice}
            prefix={pr?.currency ?? ''}
            style={{ width: 200 }}
          />
          <span style={{ fontSize: 12, color: palette.mute }}>
            填了手工售价就一直用它，不再按询价或参考价自动取价；下次发布生效
          </span>
        </div>
      </Modal>
    </div>
  );
};

/** 差异里的值：长文本截断，列表显示条数 */
const brief = (v: unknown): string => {
  if (v === null || v === undefined || v === '') return '空';
  if (Array.isArray(v)) return `${v.length} 条`;
  if (typeof v === 'object') return `${Object.keys(v as object).length} 项`;
  const s = String(v);
  return s.length > 80 ? `${s.slice(0, 80)}…` : s;
};

/** 与上次发布的差异 */
export const ChangeTable: React.FC<{ changes: Change[] }> = ({ changes }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        border: `1px solid ${palette.hairline}`,
        borderRadius: 10,
        overflow: 'hidden',
      }}
    >
      {changes.map((c, i) => (
        <div
          key={c.field}
          style={{
            display: 'grid',
            gridTemplateColumns: '120px 1fr 16px 1fr',
            gap: 10,
            alignItems: 'start',
            padding: '8px 12px',
            fontSize: 13,
            borderTop: i ? `1px solid ${palette.hairline}` : undefined,
          }}
        >
          <span style={{ color: palette.sub }}>{fieldLabel(c.field)}</span>
          <span
            style={{
              fontFamily: MONO,
              fontSize: 12,
              color: palette.red,
              wordBreak: 'break-all',
            }}
          >
            {brief(c.before)}
          </span>
          <span style={{ color: palette.mute }}>→</span>
          <span
            style={{
              fontFamily: MONO,
              fontSize: 12,
              color: palette.green,
              wordBreak: 'break-all',
            }}
          >
            {brief(c.after)}
          </span>
        </div>
      ))}
    </div>
  );
};

export const ChangesSummary: React.FC<{ p: Preview; siteName?: string }> = ({
  p,
  siteName,
}) => {
  const { palette } = useAppTheme();
  if (p.firstPublish)
    return (
      <div style={{ fontSize: 13, color: palette.sub }}>
        <Pill tone="accent">首次发布</Pill> 这个商品还没有在{' '}
        {siteName ?? '这个站点'} 发布过
      </div>
    );
  if (!p.changes.length)
    return (
      <div style={{ fontSize: 13, color: palette.mute }}>
        和上次发布的内容一样
      </div>
    );
  return <ChangeTable changes={p.changes} />;
};
