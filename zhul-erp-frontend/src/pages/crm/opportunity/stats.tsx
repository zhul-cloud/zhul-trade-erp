import React from 'react';
import { PageHeader } from '@/components/ListLayout';
import { useAppTheme } from '@/theme/AppTheme';
import StatsPanel from './StatsPanel';

/** 商机统计：按首次接触日期看各渠道、各业务员的新增与有效情况 */
const OpportunityStatsPage: React.FC = () => {
  const { palette } = useAppTheme();
  return (
    <div style={{ color: palette.ink }}>
      <PageHeader help="按首次接触日期统计每天各渠道、各业务员新增了多少商机，其中多少无效、多少有效" />
      <StatsPanel />
    </div>
  );
};

export default OpportunityStatsPage;
