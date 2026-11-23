import { ExclamationCircleFilled } from '@ant-design/icons';
import { App } from 'antd';
import React, { useCallback } from 'react';
import { useAppTheme } from '@/theme/AppTheme';

export interface DangerConfirm {
  /** 问句，写明对象：「删除客户「ABC」？」 */
  title: React.ReactNode;
  /** 后果：删除后会怎样、哪些情况不能删 */
  content?: React.ReactNode;
  /** 按钮写动作本身（「删除」「作废」「禁用」），默认「删除」 */
  okText?: string;
  onOk: () => unknown;
}

/**
 * 危险操作确认（公共组件，规范「组件标准 · 危险操作」，ui-redesign C6）：
 * 错误色图标与按钮，按钮写动作，默认焦点在「取消」，防止回车误删。确认框的文字原样保留。
 */
export const useConfirmDanger = () => {
  const { modal } = App.useApp();
  const { palette } = useAppTheme();
  return useCallback(
    ({ title, content, okText = '删除', onOk }: DangerConfirm) =>
      modal.confirm({
        title,
        content,
        icon: <ExclamationCircleFilled style={{ color: palette.red }} />,
        okText,
        cancelText: '取消',
        okButtonProps: { danger: true },
        autoFocusButton: 'cancel',
        onOk,
      }),
    [modal, palette.red],
  );
};
