import { Switch } from 'antd';
import React from 'react';

/**
 * 列表行里的启用 / 停用开关（公共组件）：一律小号（28×16），颜色跟主题色。
 * 默认 44×24 的开关放在表格行里比数据还抢眼（2026-11-24 用户指出「好丑」）；
 * 点开关不触发整行点击（不会顺带打开详情）。读屏念「名称 已启用，点击停用」。
 */
export const StatusSwitch: React.FC<{
  checked: boolean;
  /** 这一行的名称，用在读屏文字里 */
  name: string;
  disabled?: boolean;
  loading?: boolean;
  /** 关掉时的说法，默认「停用」（客户、供应商用「禁用」） */
  offText?: string;
  onChange: (checked: boolean) => void;
}> = ({ checked, name, disabled, loading, offText = '停用', onChange }) => (
  <Switch
    size="small"
    checked={checked}
    disabled={disabled}
    loading={loading}
    aria-label={
      checked
        ? `${name} 已启用，点击${offText}`
        : `${name} 已${offText}，点击启用`
    }
    onClick={(_, e) => e.stopPropagation()}
    // rc-switch 按左右方向键时不管值变没变都会回调；值没变就忽略，免得已停用时按左键弹出「启用？」
    onChange={(c) => c !== checked && onChange(c)}
  />
);

export default StatusSwitch;
