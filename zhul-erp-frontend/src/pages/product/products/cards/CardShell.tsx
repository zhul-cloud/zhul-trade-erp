import { CheckCircleFilled, ClockCircleFilled } from '@ant-design/icons';
import React, { createContext, useContext, useEffect, useRef } from 'react';
import { Pill } from '../../components/Pills';
import { useProductTheme } from '../../theme';

/** 有未保存修改的卡片登记在这里，页面底部的保存条据此显示，离开页面前据此提示。 */
export interface UnsavedEntry {
  save: () => Promise<void>;
  discard: () => void;
}

interface Registry {
  set: (key: string, entry: UnsavedEntry | null) => void;
}

export const UnsavedContext = createContext<Registry>({ set: () => {} });

/** 卡片进入"有未保存修改"状态时登记，保存或放弃后取消登记 */
export function useUnsaved(key: string, entry: UnsavedEntry | null) {
  const { set } = useContext(UnsavedContext);
  const latest = useRef(entry);
  latest.current = entry;
  const active = entry !== null;
  useEffect(() => {
    if (!active) {
      set(key, null);
      return;
    }
    // 登记的是"取最新回调"的代理，避免闭包里拿到旧的表单值
    set(key, {
      save: () => latest.current?.save() ?? Promise.resolve(),
      discard: () => latest.current?.discard(),
    });
    return () => set(key, null);
  }, [active, key, set]);
}

/** 档案页的一张卡片：图标 + 标题 + 状态徽章 + 操作；空卡片写明"填了有什么用"并给一个主操作 */
export const CardShell: React.FC<{
  id: string;
  title: string;
  /** undefined 表示不显示徽章（租户账号、或没有完成条件的卡片） */
  done?: boolean;
  actions?: React.ReactNode;
  children: React.ReactNode;
}> = ({ id, title, done, actions, children }) => {
  const { palette } = useProductTheme();
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      style={{
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        borderRadius: 16,
        padding: 24,
        marginBottom: 20,
        scrollMarginTop: 24,
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          marginBottom: 16,
          flexWrap: 'wrap',
        }}
      >
        <h2
          id={`${id}-title`}
          style={{
            margin: 0,
            fontSize: 16,
            fontWeight: 600,
            flex: 1,
            minWidth: 120,
          }}
        >
          {title}
        </h2>
        {done !== undefined &&
          (done ? (
            <Pill tone="green">
              <CheckCircleFilled style={{ marginRight: 4 }} />
              已完成
            </Pill>
          ) : (
            <Pill tone="orange">
              <ClockCircleFilled style={{ marginRight: 4 }} />
              待补充
            </Pill>
          ))}
        {actions}
      </header>
      {children}
    </section>
  );
};

/** 空卡片：一句话说明填了有什么用，加一个主操作 */
export const CardEmpty: React.FC<{
  benefit: string;
  actionText?: string;
  onAction?: () => void;
}> = ({ benefit, actionText, onAction }) => {
  const { palette } = useProductTheme();
  return (
    <div
      style={{
        padding: '20px 16px',
        borderRadius: 12,
        background: palette.inset,
        border: `1px dashed ${palette.control}`,
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        flexWrap: 'wrap',
      }}
    >
      <span
        style={{ flex: 1, minWidth: 200, color: palette.sub, fontSize: 14 }}
      >
        {actionText ? benefit : '这部分内容暂未提供。'}
      </span>
      {actionText && onAction && (
        <button
          type="button"
          onClick={onAction}
          style={{
            height: 36,
            padding: '0 16px',
            borderRadius: 10,
            border: `1px solid ${palette.link}`,
            background: 'transparent',
            color: palette.link,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          {actionText}
        </button>
      )}
    </div>
  );
};

/** 键值行：用于卡片里的只读展示 */
export const Field: React.FC<{ label: string; children: React.ReactNode }> = ({
  label,
  children,
}) => {
  const { palette } = useProductTheme();
  return (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 2 }}>
        {label}
      </div>
      <div
        style={{ fontSize: 14, color: palette.ink, wordBreak: 'break-word' }}
      >
        {children || '—'}
      </div>
    </div>
  );
};

export const FieldGrid: React.FC<{
  children: React.ReactNode;
  columns?: number;
}> = ({ children, columns = 3 }) => (
  <div
    style={{
      display: 'grid',
      gridTemplateColumns: `repeat(auto-fill, minmax(${columns === 2 ? 240 : 180}px, 1fr))`,
      gap: '16px 24px',
    }}
  >
    {children}
  </div>
);
