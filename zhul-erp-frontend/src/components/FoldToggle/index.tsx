import { Segmented } from 'antd';
import { Maximize2, Minimize2 } from 'lucide-react';
import React from 'react';

/**
 * 分组列表的「全部展开 / 全部折叠」（公共组件，UI-117）：全站统一用小号分段控件，
 * 不再各页写两个主题色文字按钮（采购需求）或别的措辞。
 */
export const FoldToggle: React.FC<{
  mode: 'open' | 'fold';
  onChange: (m: 'open' | 'fold') => void;
}> = ({ mode, onChange }) => (
  <Segmented
    size="small"
    value={mode}
    onChange={(v) => onChange(v as 'open' | 'fold')}
    options={[
      { value: 'open', label: '全部展开', icon: <Maximize2 size="1em" /> },
      { value: 'fold', label: '全部折叠', icon: <Minimize2 size="1em" /> },
    ]}
  />
);

export default FoldToggle;
