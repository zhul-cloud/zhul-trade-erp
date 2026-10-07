import { BranchesOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import React from 'react';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';

/** 版本列表 + 与上一版本的差异（PI、报价单共用） */

export interface DiffRow {
  kind: '新增' | '删除' | '修改';
  label: string;
  before: string;
  after: string;
}

export interface VersionItem {
  versionNo: number;
  /** 1-编辑中、2-已发送、3-已放弃 */
  status: number;
  totalAmount: number;
  sentAt?: string;
}

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

export const VersionPanel: React.FC<{
  versions: VersionItem[];
  currentVersionNo: number;
  editingVersionNo?: number | null;
  /** 正在查看的版本号与合计 */
  viewingVersionNo: number;
  totalAmount: number;
  currency: string;
  /** 对比的基准版本，没有时显示「第一个版本」 */
  base?: { versionNo: number; totalAmount: number };
  diffs: DiffRow[];
  /** 有未保存的修改：差异按已保存的内容计算 */
  dirty?: boolean;
  wide: boolean;
  /** 切换版本；传 undefined 表示回到默认版本（修改中的，没有时为当前有效版本） */
  onSelect: (versionNo?: number) => void;
}> = ({
  versions,
  currentVersionNo,
  editingVersionNo,
  viewingVersionNo,
  totalAmount,
  currency,
  base,
  diffs,
  dirty,
  wide,
  onSelect,
}) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: wide ? 'minmax(280px, 1fr) minmax(0, 2fr)' : '1fr',
        gap: 16,
        marginBottom: 16,
      }}
    >
      <Card style={{ padding: 16 }}>
        <div style={{ fontSize: 13, color: palette.sub, marginBottom: 8 }}>
          版本
        </div>
        {[...versions].reverse().map((x) => {
          const active = x.versionNo === viewingVersionNo;
          const tag =
            x.status === 1 ? (
              <Pill tone="orange">编辑中</Pill>
            ) : x.versionNo === currentVersionNo ? (
              <Pill tone="green">当前有效</Pill>
            ) : x.status === 3 ? (
              <Pill tone="mute">已放弃</Pill>
            ) : null;
          return (
            <button
              type="button"
              key={x.versionNo}
              onClick={() =>
                onSelect(
                  x.versionNo === (editingVersionNo ?? currentVersionNo)
                    ? undefined
                    : x.versionNo,
                )
              }
              style={{
                all: 'unset',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                width: '100%',
                boxSizing: 'border-box',
                padding: '8px 10px',
                borderRadius: 10,
                background: active ? palette.accentSoft : 'transparent',
              }}
            >
              <b style={{ color: palette.ink }}>Rev.{x.versionNo}</b>
              {tag}
              <span
                style={{
                  ...num,
                  marginLeft: 'auto',
                  fontSize: 12,
                  color: palette.mute,
                }}
              >
                {x.sentAt
                  ? `${dayjs(x.sentAt).format('MM-DD HH:mm')} 发送 · `
                  : x.status === 1
                    ? '未发送 · '
                    : ''}
                {formatAmount(x.totalAmount, currency)}
              </span>
            </button>
          );
        })}
      </Card>
      <Card style={{ padding: 16 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginBottom: 10,
          }}
        >
          <BranchesOutlined style={{ color: palette.link }} />
          <b style={{ color: palette.ink }}>
            {base
              ? `Rev.${base.versionNo} → Rev.${viewingVersionNo} 改了什么`
              : `Rev.${viewingVersionNo}`}
          </b>
          {dirty && (
            <span style={{ fontSize: 12, color: palette.orange }}>
              按已保存的内容对比
            </span>
          )}
        </div>
        {!base ? (
          <div style={{ color: palette.mute, fontSize: 13 }}>
            第一个版本，没有可对比的上一版本
          </div>
        ) : diffs.length === 0 ? (
          <div style={{ color: palette.mute, fontSize: 13 }}>
            与 Rev.{base.versionNo} 内容相同
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 6 }}>
            {diffs.map((d) => (
              <div
                key={`${d.kind}-${d.label}`}
                style={{
                  display: 'grid',
                  gridTemplateColumns:
                    '48px minmax(0, 1.4fr) minmax(0, 1fr) 16px minmax(0, 1fr)',
                  gap: 10,
                  alignItems: 'center',
                  padding: '8px 10px',
                  borderRadius: 10,
                  background: palette.inset,
                  fontSize: 13,
                }}
              >
                <Pill
                  tone={
                    d.kind === '新增'
                      ? 'green'
                      : d.kind === '删除'
                        ? 'red'
                        : 'orange'
                  }
                >
                  {d.kind}
                </Pill>
                <span style={{ color: palette.ink, fontWeight: 600 }}>
                  {d.label}
                </span>
                <span style={{ ...num, color: palette.mute }}>{d.before}</span>
                <span style={{ color: palette.mute }}>→</span>
                <span style={{ ...num, color: palette.orange }}>{d.after}</span>
              </div>
            ))}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                padding: '8px 10px',
                fontSize: 13,
              }}
            >
              <b style={{ color: palette.ink }}>合计</b>
              <span style={num}>
                {formatAmount(base.totalAmount, currency)} →{' '}
                <b style={{ color: palette.orange }}>
                  {formatAmount(totalAmount, currency)}
                </b>
              </span>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
};
