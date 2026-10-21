import type React from 'react';
import OpportunityCard from '../OpportunityCard';
import OverduePiCard from '../OverduePiCard';
import {
  MyInquiryWidget,
  MyPurchaseWidget,
  QuoteStatsWidget,
  QuoteTrendWidget,
} from './purchaser';

/**
 * 看板组件注册表：编码与后端 WorkbenchWidget 一致。新增组件 = 新增一个组件文件并在这里登记，
 * 模板（后端 workbench_template）里引用编码即可；后端有、前端还没有的编码直接跳过。
 */
export const WIDGETS: Record<string, React.FC> = {
  'opportunity-stats': OpportunityCard,
  'overdue-pi': OverduePiCard,
  'my-inquiry': MyInquiryWidget,
  'quote-stats': QuoteStatsWidget,
  'quote-trend': QuoteTrendWidget,
  'my-purchase': MyPurchaseWidget,
};
