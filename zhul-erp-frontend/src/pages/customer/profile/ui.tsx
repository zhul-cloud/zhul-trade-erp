import { StarFilled } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Tooltip } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { type FieldSource, GRADE_NAMES, SOURCE } from './service';

/** 潜力评级：星标 + n/5；0 为未评 */
export const Stars: React.FC<{ value?: number }> = ({ value }) => {
  const { palette } = useAppTheme();
  if (!value) return <span style={{ color: palette.mute }}>未评</span>;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        height: 24,
        padding: '0 8px',
        borderRadius: 8,
        background: palette.orangeSoft,
        color: '#FBBF24',
        fontWeight: 700,
        fontSize: 12,
        whiteSpace: 'nowrap',
      }}
    >
      <StarFilled /> {value} / 5
    </span>
  );
};

/** 经营等级 S/A/B/C；0 为未成交 */
export const GradeBadge: React.FC<{
  value?: number;
  adjusted?: boolean;
  size?: 'small' | 'large';
}> = ({ value, adjusted, size = 'small' }) => {
  const { palette } = useAppTheme();
  if (!value) return <span style={{ color: palette.mute }}>未成交</span>;
  const tone =
    value === 1
      ? { fg: palette.red, bg: palette.redSoft }
      : value === 2
        ? { fg: palette.link, bg: palette.accentSoft }
        : { fg: palette.sub, bg: palette.inset };
  const box = size === 'large' ? 34 : 24;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
      <span
        style={{
          width: box,
          height: box,
          borderRadius: size === 'large' ? 10 : 8,
          display: 'inline-grid',
          placeItems: 'center',
          fontWeight: 800,
          fontSize: size === 'large' ? 18 : 12,
          color: tone.fg,
          background: tone.bg,
        }}
      >
        {GRADE_NAMES[value]}
      </span>
      {adjusted && (
        <span style={{ fontSize: 11, color: palette.violet }}>已调整</span>
      )}
    </span>
  );
};

/** 五段进度：done 为位掩码 */
export const Progress5: React.FC<{ mask: number; width?: number }> = ({
  mask,
  width = 60,
}) => {
  const { palette } = useAppTheme();
  return (
    <span
      role="img"
      aria-label={`完成 ${[0, 1, 2, 3, 4].filter((i) => mask & (1 << i)).length} / 5 个模块`}
      style={{ display: 'inline-flex', gap: 3 }}
    >
      {[0, 1, 2, 3, 4].map((i) => (
        <span
          key={i}
          style={{
            width: width / 5 - 3,
            height: 6,
            borderRadius: 3,
            background: mask & (1 << i) ? palette.green : palette.hairline,
          }}
        />
      ))}
    </span>
  );
};

export const doneOf = (mask?: number) =>
  [0, 1, 2, 3, 4].filter((i) => (mask ?? 0) & (1 << i)).length;

/** 字段来源小标签：人工 · 谁 / 背调报告 / 系统 / 迁移，悬停看时间 */
export const SourceTag: React.FC<{
  source?: FieldSource;
  system?: string;
  /** 传了客户 ID，背调来源可点开报告 */
  customerId?: number;
}> = ({ source, system, customerId }) => {
  const { palette } = useAppTheme();
  if (!source && !system) return null;
  const s = source?.source;
  const text =
    system ??
    (s === SOURCE.SYSTEM
      ? '系统'
      : s === SOURCE.REPORT
        ? source?.reportNo
          ? `背调 ${source.reportNo}${source.reportDeleted ? '（已删除）' : ''}`
          : '背调报告'
        : s === SOURCE.MIGRATED
          ? '迁移'
          : `人工${source?.operatorName ? ` · ${source.operatorName}` : ''}`);
  const color =
    system || s === SOURCE.SYSTEM
      ? palette.cyan
      : s === SOURCE.REPORT
        ? palette.violet
        : palette.mute;
  const tip = [
    text,
    s === SOURCE.REPORT && source?.operatorName && `${source.operatorName}采纳`,
    source?.updateTime && formatDateTime(source.updateTime),
  ]
    .filter(Boolean)
    .join(' · ');
  const link =
    s === SOURCE.REPORT &&
    customerId &&
    source?.reportId &&
    !source.reportDeleted;
  return (
    <Tooltip title={tip}>
      {link ? (
        <a
          onClick={() =>
            history.push(
              `/customer/list/${customerId}/background/${source?.reportId}`,
            )
          }
          style={{ fontSize: 11, color, whiteSpace: 'nowrap' }}
        >
          {text}
        </a>
      ) : (
        <span style={{ fontSize: 11, color, whiteSpace: 'nowrap' }}>
          {text}
        </span>
      )}
    </Tooltip>
  );
};

/** 档案字段一行：标签（选填的标出）、值（未填的核心项标橙色）、来源 */
export const FieldRow: React.FC<{
  label: string;
  value?: React.ReactNode;
  core?: boolean;
  source?: React.ReactNode;
}> = ({ label, value, core, source }) => {
  const { palette } = useAppTheme();
  const empty =
    value === undefined || value === null || value === '' || value === false;
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '8px 0',
        borderBottom: `1px solid ${palette.hairline}`,
        minHeight: 40,
      }}
    >
      <span
        style={{
          width: 120,
          flex: '0 0 120px',
          fontSize: 12,
          color: core ? palette.sub : palette.mute,
        }}
      >
        {label}
        {!core && (
          <span style={{ marginLeft: 4, fontSize: 10, color: palette.faint }}>
            选填
          </span>
        )}
      </span>
      <span
        style={{
          flex: 1,
          minWidth: 0,
          fontSize: 13,
          fontWeight: empty ? 400 : 500,
          color: empty ? (core ? palette.orange : palette.faint) : palette.ink,
          display: 'flex',
          flexWrap: 'wrap',
          gap: 6,
          alignItems: 'center',
          wordBreak: 'break-word',
        }}
      >
        {empty ? '未填' : value}
      </span>
      {source}
    </div>
  );
};

/** 小标签：品牌、多选项 */
export const Chip: React.FC<{ children: React.ReactNode; count?: number }> = ({
  children,
  count,
}) => {
  const { palette } = useAppTheme();
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '1px 8px',
        borderRadius: 8,
        fontSize: 12,
        fontWeight: 600,
        background: palette.inset,
        border: `1px solid ${palette.hairline}`,
        color: palette.ink,
      }}
    >
      {children}
      {count != null && <span style={{ color: palette.cyan }}>{count}</span>}
    </span>
  );
};
