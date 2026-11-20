import { Button } from 'antd';
import React from 'react';
import { Card } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 单据列表骨架（openspec doc-list-unify，UI 规范「单据页统一规范 · 列表页骨架」）：
 * 搜索条件区（标签在上的网格，状态放这里的下拉）+ 主内容区（操作行、表格、分页）。
 */

/** 搜索条件区的一项：标签在上、输入在下；关键词占两格 */
export const FilterField: React.FC<{
  label: string;
  span?: 1 | 2;
  children: React.ReactNode;
}> = ({ label, span = 1, children }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        gridColumn: span === 2 ? 'span 2' : undefined,
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        minWidth: 0,
      }}
    >
      <span style={{ fontSize: 12, color: palette.mute }}>{label}</span>
      {children}
    </div>
  );
};

/** 搜索条件区：筛选项网格，「重置」「查询」靠右 */
export const FilterCard: React.FC<{
  children: React.ReactNode;
  onSearch: () => void;
  onReset: () => void;
}> = ({ children, onSearch, onReset }) => (
  <Card style={{ marginBottom: 16, padding: 20 }}>
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))',
        gap: 12,
        alignItems: 'end',
      }}
    >
      {children}
      <div
        style={{
          gridColumn: '-2 / -1',
          display: 'flex',
          gap: 8,
          justifyContent: 'flex-end',
        }}
      >
        <Button onClick={onReset}>重置</Button>
        <Button type="primary" onClick={onSearch}>
          查询
        </Button>
      </div>
    </div>
  </Card>
);

/** 主内容区：操作行（左：结果数或已选；右：主操作）+ 表格 */
export const MainCard: React.FC<{
  left?: React.ReactNode;
  right?: React.ReactNode;
  children: React.ReactNode;
}> = ({ left, right, children }) => {
  const { palette } = useAppTheme();
  return (
    <Card style={{ padding: 16, minWidth: 0 }}>
      {(left || right) && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            flexWrap: 'wrap',
            marginBottom: 12,
            color: palette.sub,
            fontSize: 13,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              flex: 1,
              minWidth: 0,
            }}
          >
            {left}
          </div>
          {right}
        </div>
      )}
      {children}
    </Card>
  );
};
