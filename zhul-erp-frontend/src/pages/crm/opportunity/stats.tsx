import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { PageTitle } from './components';
import StatsPanel from './StatsPanel';

/** 商机统计：按首次接触日期看各渠道、各业务员的新增与有效情况 */
const OpportunityStatsPage: React.FC = () => {
  const { palette } = useAppTheme();
  return (
    <div style={{ color: palette.ink }}>
      <PageTitle
        title="商机统计"
        section="商机统计"
        description="按首次接触日期统计每天各渠道、各业务员新增了多少商机，其中多少无效、多少有效。"
      />
      <StatsPanel />
    </div>
  );
};

export default OpportunityStatsPage;
