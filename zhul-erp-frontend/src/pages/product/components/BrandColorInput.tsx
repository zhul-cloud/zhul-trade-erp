import { Button, ColorPicker, Space } from 'antd';
import React from 'react';
import { BRAND_COLOR_PRESETS, randomBrandColor } from '../constants';

/**
 * 品牌主题色输入：颜色选择器加「随机换一个」，可清空。
 * 用在 Form.Item 里，value 是 #RRGGBB 字符串，清空时是空串。
 */
const BrandColorInput: React.FC<{
  value?: string;
  onChange?: (value: string) => void;
}> = ({ value, onChange }) => (
  <Space size={12}>
    <ColorPicker
      value={value || null}
      format="hex"
      disabledAlpha
      allowClear
      showText={(color) =>
        value ? color.toHexString().toUpperCase() : '未设置'
      }
      presets={[{ label: '推荐色', colors: [...BRAND_COLOR_PRESETS] }]}
      onChange={(color) => onChange?.(color.toHexString().toUpperCase())}
      onClear={() => onChange?.('')}
      aria-label="品牌主题色"
    />
    <Button type="link" onClick={() => onChange?.(randomBrandColor())}>
      随机换一个
    </Button>
  </Space>
);

export default BrandColorInput;
