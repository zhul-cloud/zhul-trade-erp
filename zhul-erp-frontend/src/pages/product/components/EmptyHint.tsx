import { Button } from 'antd';
import React from 'react';
import { useProductTheme } from '../theme';

/** 空状态：不写"暂无数据"，说明原因或规则，并给出下一步操作 */
export const EmptyHint: React.FC<{
  title: string;
  description?: string;
  actionText?: string;
  onAction?: () => void;
  secondaryText?: string;
  onSecondary?: () => void;
}> = ({
  title,
  description,
  actionText,
  onAction,
  secondaryText,
  onSecondary,
}) => {
  const { palette } = useProductTheme();
  return (
    <div style={{ padding: '48px 24px', textAlign: 'center' }}>
      <div style={{ fontSize: 16, fontWeight: 600, color: palette.ink }}>
        {title}
      </div>
      {description && (
        <div
          style={{
            margin: '8px auto 0',
            maxWidth: 420,
            color: palette.sub,
            fontSize: 14,
          }}
        >
          {description}
        </div>
      )}
      {(actionText || secondaryText) && (
        <div
          style={{
            marginTop: 20,
            display: 'flex',
            gap: 12,
            justifyContent: 'center',
          }}
        >
          {secondaryText && (
            <Button onClick={onSecondary}>{secondaryText}</Button>
          )}
          {actionText && (
            <Button type="primary" onClick={onAction}>
              {actionText}
            </Button>
          )}
        </div>
      )}
    </div>
  );
};

/** 加载失败：原因 + 重试 */
export const ErrorHint: React.FC<{ message: string; onRetry: () => void }> = ({
  message,
  onRetry,
}) => (
  <EmptyHint
    title="加载失败"
    description={message}
    actionText="重试"
    onAction={onRetry}
  />
);
