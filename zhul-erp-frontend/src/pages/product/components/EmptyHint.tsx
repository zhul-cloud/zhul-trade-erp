import { Button } from 'antd';
import React from 'react';
import { EmptyState, type EmptyKind } from '@/components/EmptyState';
import { Panel } from '@/components/ListLayout';

/**
 * 空状态：不写"暂无数据"，说明原因或规则，并给出下一步操作。
 * 外观统一走公共 EmptyState（图标 + 一句话 + 说明 + 按钮，UI-117），这里只保留旧的参数写法。
 */
export const EmptyHint: React.FC<{
  title: string;
  description?: string;
  actionText?: string;
  onAction?: () => void;
  secondaryText?: string;
  onSecondary?: () => void;
  kind?: EmptyKind;
}> = ({
  title,
  description,
  actionText,
  onAction,
  secondaryText,
  onSecondary,
  kind,
}) => (
  <EmptyState
    kind={kind}
    title={title}
    description={description}
    action={
      (actionText || secondaryText) && (
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
          {secondaryText && (
            <Button onClick={onSecondary}>{secondaryText}</Button>
          )}
          {actionText && (
            <Button type="primary" onClick={onAction}>
              {actionText}
            </Button>
          )}
        </div>
      )
    }
  />
);

/**
 * 按后端的提示分辨：记录不存在、业务还没就绪（如「还没结账」）、没有权限，重试没有意义，不给「重试」；
 * 其余（网络、服务出错）才是「加载失败」加「重试」（检查表 #20，UI-117）
 */
const classify = (message: string): EmptyKind => {
  if (/无权|没有权限|权限不足/.test(message)) return 'forbidden';
  if (/不存在|已删除|找不到|还没|尚未|未结账|未开始/.test(message))
    return 'empty';
  return 'error';
};

/**
 * 加载失败：原因 + 重试；不存在、未就绪、无权限按各自的状态显示，不给重试。
 * page：整页只有这一块时（详情页取数失败）放进白卡，不直接落在页面底上
 */
export const ErrorHint: React.FC<{
  message: string;
  onRetry: () => void;
  page?: boolean;
}> = ({ message, onRetry, page }) => {
  const kind = classify(message);
  const body =
    kind === 'error' ? (
      <EmptyState
        kind="error"
        description={message}
        action={<Button onClick={onRetry}>重试</Button>}
      />
    ) : (
      <EmptyState kind={kind} title={message} />
    );
  return page ? <Panel style={{ padding: 24 }}>{body}</Panel> : body;
};
