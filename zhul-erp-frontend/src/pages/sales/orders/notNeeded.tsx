import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/** 型号行进度下的「不需采购 N（原因）」（删除采购需求，openspec purchase-requirement-delete） */
export const NotNeededNote: React.FC<{ qty?: number; reasons?: string[] }> = ({
  qty,
  reasons,
}) => {
  const { palette } = useAppTheme();
  if (!qty) return null;
  return (
    <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
      不需采购 {qty}
      {reasons?.length ? `（${reasons.join('；')}）` : ''}
    </div>
  );
};
