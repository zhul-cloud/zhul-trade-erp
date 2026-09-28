import {
  EyeOutlined,
  FileExcelOutlined,
  FileImageOutlined,
} from '@ant-design/icons';
import { Link } from '@umijs/max';
import { App, Breadcrumb } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  CATEGORY_ACTIVE,
  CATEGORY_INVALID,
  CATEGORY_LOST,
  CATEGORY_WON,
  LIST_PATH,
} from './constants';
import { opportunityApi, readBizError } from './service';

type Tone =
  | 'gray'
  | 'cyan'
  | 'accent'
  | 'violet'
  | 'orange'
  | 'green'
  | 'red'
  | 'mute';

export const Pill: React.FC<{
  tone: Tone;
  dot?: boolean;
  children: React.ReactNode;
}> = ({ tone, dot, children }) => {
  const { palette } = useAppTheme();
  const colors: Record<Tone, { fg: string; bg: string }> = {
    gray: { fg: palette.sub, bg: palette.inset },
    mute: { fg: palette.mute, bg: palette.inset },
    cyan: { fg: palette.cyan, bg: palette.inset },
    accent: { fg: palette.link, bg: palette.accentSoft },
    violet: { fg: palette.violet, bg: palette.violetSoft },
    orange: { fg: palette.orange, bg: palette.orangeSoft },
    green: { fg: palette.green, bg: palette.greenSoft },
    red: { fg: palette.red, bg: palette.redSoft },
  };
  const c = colors[tone];
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        height: 24,
        padding: '0 10px',
        borderRadius: 12,
        fontSize: 12,
        fontWeight: 600,
        color: c.fg,
        background: c.bg,
        whiteSpace: 'nowrap',
      }}
    >
      {dot && (
        <span
          aria-hidden
          style={{ width: 6, height: 6, borderRadius: 3, background: c.fg }}
        />
      )}
      {children}
    </span>
  );
};

/** 进行中阶段按顺序渐变色调；结束状态按结果着色 */
const ACTIVE_TONES: Tone[] = [
  'gray',
  'cyan',
  'accent',
  'violet',
  'violet',
  'orange',
  'orange',
];

export const StagePill: React.FC<{
  code: string;
  name: string;
  category: number;
  /** 进行中阶段的序号（从 0 开始），用于取色 */
  index?: number;
}> = ({ code, name, category, index = 0 }) => {
  let tone: Tone = 'gray';
  if (category === CATEGORY_ACTIVE) tone = ACTIVE_TONES[index] ?? 'orange';
  else if (category === CATEGORY_WON) tone = 'green';
  else if (category === CATEGORY_LOST) tone = 'red';
  else if (category === CATEGORY_INVALID) tone = 'mute';
  const label = category === CATEGORY_ACTIVE ? `${code} ${name}` : name;
  return (
    <Pill tone={tone} dot>
      {label}
    </Pill>
  );
};

/** 面包屑 + 标题 + 右侧操作 */
export const PageTitle: React.FC<{
  title: React.ReactNode;
  current?: string;
  description?: React.ReactNode;
  actions?: React.ReactNode;
}> = ({ title, current, description, actions }) => {
  const { palette } = useAppTheme();
  const items = [
    { title: '询盘管理' },
    current
      ? { title: <Link to={LIST_PATH}>商机管理</Link> }
      : { title: '商机管理' },
    ...(current ? [{ title: current }] : []),
  ];
  return (
    <header
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-end',
        gap: 16,
        marginBottom: 20,
        flexWrap: 'wrap',
      }}
    >
      <div style={{ minWidth: 0 }}>
        <Breadcrumb items={items} style={{ fontSize: 13 }} />
        <h1
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            flexWrap: 'wrap',
            fontSize: 28,
            fontWeight: 700,
            margin: '8px 0 0',
            color: palette.ink,
          }}
        >
          {title}
        </h1>
        {description && (
          <div style={{ margin: '6px 0 0', color: palette.sub, fontSize: 14 }}>
            {description}
          </div>
        )}
      </div>
      {actions && (
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          {actions}
        </div>
      )}
    </header>
  );
};

/** 卡片：标题（右侧可放操作）+ 内容 */
export const Card: React.FC<{
  title?: string;
  extra?: React.ReactNode;
  children: React.ReactNode;
  style?: React.CSSProperties;
}> = ({ title, extra, children, style }) => {
  const { palette } = useAppTheme();
  return (
    <section
      style={{
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        borderRadius: 16,
        padding: 24,
        ...style,
      }}
    >
      {(title || extra) && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            marginBottom: 16,
          }}
        >
          <h2
            style={{
              margin: 0,
              fontSize: 16,
              fontWeight: 600,
              color: palette.ink,
            }}
          >
            {title}
          </h2>
          {extra}
        </div>
      )}
      {children}
    </section>
  );
};

export const formatSize = (bytes: number) =>
  bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`;

const isImage = (contentType: string) => contentType.startsWith('image/');

/** 附件卡片：图标 + 文件名 + 说明 + 右侧操作 */
export const FileChip: React.FC<{
  fileName: string;
  contentType: string;
  meta: string;
  actions?: React.ReactNode;
}> = ({ fileName, contentType, meta, actions }) => {
  const { palette } = useAppTheme();
  const image = isImage(contentType);
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        width: 280,
        height: 52,
        padding: '0 12px',
        borderRadius: 10,
        background: palette.inset,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <span
        aria-hidden
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 32,
          height: 32,
          flex: 'none',
          borderRadius: 8,
          fontSize: 16,
          color: image ? palette.link : palette.green,
          background: image ? palette.accentSoft : palette.greenSoft,
        }}
      >
        {image ? <FileImageOutlined /> : <FileExcelOutlined />}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          title={fileName}
          style={{
            fontSize: 12,
            fontWeight: 500,
            color: palette.ink,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {fileName}
        </div>
        <div
          title={meta}
          style={{
            fontSize: 11,
            color: palette.mute,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {meta}
        </div>
      </div>
      <div
        style={{ display: 'flex', gap: 10, color: palette.mute, flex: 'none' }}
      >
        {actions}
      </div>
    </div>
  );
};

export const iconButton: React.CSSProperties = {
  border: 0,
  padding: 0,
  background: 'none',
  color: 'inherit',
  cursor: 'pointer',
};

export const fileMeta = (
  contentType: string,
  size: number,
  createBy?: string,
  createTime?: string,
) =>
  [
    isImage(contentType)
      ? contentType === 'image/png'
        ? 'PNG'
        : 'JPG'
      : 'Excel',
    formatSize(size),
    createBy
      ? `${createBy} ${formatDateTime(createTime).slice(5, 10)}`
      : '待保存',
  ].join(' · ');

/** 预览已保存的附件：先同步开窗口，避免被浏览器当作弹窗拦截，再跳到文件地址 */
export const PreviewButton: React.FC<{
  id: number;
  attachmentId: number;
  name: string;
}> = ({ id, attachmentId, name }) => {
  const { message } = App.useApp();
  return (
    <button
      type="button"
      aria-label={`预览 ${name}`}
      style={iconButton}
      onClick={async () => {
        const win = window.open('', '_blank');
        try {
          const url = await opportunityApi.attachmentBlobUrl(id, attachmentId);
          if (win) win.location.href = url;
          setTimeout(() => window.URL.revokeObjectURL(url), 60_000);
        } catch (e) {
          win?.close();
          message.error(readBizError(e).message);
        }
      }}
    >
      <EyeOutlined />
    </button>
  );
};
