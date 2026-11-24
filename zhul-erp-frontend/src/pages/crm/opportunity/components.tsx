import { App } from 'antd';
import { Eye, FileImage, FileSpreadsheet } from 'lucide-react';
import React from 'react';
import { Panel } from '@/components/ListLayout';
import { Pill, type PillTone } from '@/components/Pill';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  CATEGORY_ACTIVE,
  CATEGORY_INVALID,
  CATEGORY_LOST,
  CATEGORY_WON,
} from './constants';
import { opportunityApi, readBizError } from './service';

type Tone = PillTone;

export { Pill };

/** 历史迁移的单据（只读）标记；不是迁移数据时不显示（openspec migrate-feishu-history） */
export const MigratedPill: React.FC<{ migrated?: boolean }> = ({ migrated }) =>
  migrated ? <Pill tone="orange">历史迁移</Pill> : null;

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

/** 卡片：标题（右侧可放操作）+ 内容 */
/** 页面卡片：外观同骨架组件 Panel（圆角 10，浅色阴影、深色边框）；标题 18/500 同 Section */
export const Card: React.FC<{
  title?: string;
  extra?: React.ReactNode;
  children: React.ReactNode;
  style?: React.CSSProperties;
}> = ({ title, extra, children, style }) => {
  const { palette } = useAppTheme();
  return (
    <Panel style={{ padding: 24, minWidth: 0, ...style }}>
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
    </Panel>
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
          color: palette.sub,
          background: palette.neutralSoft,
        }}
      >
        {image ? <FileImage size="1em" /> : <FileSpreadsheet size="1em" />}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          title={fileName}
          style={{
            fontSize: 12,
            fontWeight: 400,
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
      <Eye size="1em" />
    </button>
  );
};
