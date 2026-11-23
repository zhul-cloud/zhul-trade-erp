import { App } from 'antd';
import { Trash2, TriangleAlert } from 'lucide-react';
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
 * 左侧 40 圆错误浅底图标（按钮文字含「删除」用垃圾桶，作废、禁用等用警告三角，同原型 G2），错误色按钮写动作，
 * 默认焦点在「取消」，防止回车误删。确认框的文字原样保留。
 */
export const useConfirmDanger = () => {
  const { modal } = App.useApp();
  const { palette } = useAppTheme();
  return useCallback(
    ({ title, content, okText = '删除', onOk }: DangerConfirm) =>
      modal.confirm({
        title,
        content,
        icon: (
          <span
            aria-hidden
            style={{
              width: 40,
              height: 40,
              flex: 'none',
              marginInlineEnd: 12,
              borderRadius: '50%',
              display: 'grid',
              placeItems: 'center',
              background: palette.redSoft,
              color: palette.red,
            }}
          >
            {okText.includes('删除') ? (
              <Trash2 size={20} />
            ) : (
              <TriangleAlert size={20} />
            )}
          </span>
        ),
        okText,
        cancelText: '取消',
        okButtonProps: { danger: true },
        autoFocusButton: 'cancel',
        onOk,
      }),
    [modal, palette.red, palette.redSoft],
  );
};
