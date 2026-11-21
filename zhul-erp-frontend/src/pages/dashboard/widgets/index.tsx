import type React from 'react';
import OpportunityCard from '../OpportunityCard';
import OverduePiCard from '../OverduePiCard';
import { blockWidget } from './BlockWidget';
import {
  MyInquiryWidget,
  MyPurchaseWidget,
  QuoteStatsWidget,
  QuoteTrendWidget,
} from './purchaser';

/**
 * 看板组件注册表：编码与后端 WorkbenchWidget 一致。数据按通用数据块返回的组件只登记标题、统计期间与口径（blockWidget），
 * 模板（后端 workbench_template）里引用编码即可；后端有、前端还没登记的编码直接跳过。
 * 统计期间（如「本月」）显示在标题旁，口径放进帮助图标（help-texts.md「工作台」）；数字可点、带右箭头，不另写「点击进入」。
 */
export const WIDGETS: Record<string, React.FC> = {
  // 通用
  'opportunity-stats': OpportunityCard,
  'overdue-pi': OverduePiCard,
  // 采购员
  'my-inquiry': MyInquiryWidget,
  'quote-stats': QuoteStatsWidget,
  'quote-trend': QuoteTrendWidget,
  'my-purchase': MyPurchaseWidget,
  // 业务员
  'my-todo': blockWidget({ key: 'my-todo', title: '我的待办' }),
  'my-performance': blockWidget({
    key: 'my-performance',
    title: '我的业绩',
    period: '本月',
    help: '按你的数据范围统计，口径同销售统计',
  }),
  'quote-progress': blockWidget({
    key: 'quote-progress',
    title: '询价进度',
    help: '我的询盘里还在询价的',
  }),
  'my-opportunities': blockWidget({
    key: 'my-opportunities',
    title: '我的商机',
    period: '本月',
  }),
  // 业务负责人
  'team-performance': blockWidget({
    key: 'team-performance',
    title: '团队业绩',
    period: '本月',
    help: '按你的数据范围统计，口径同销售统计',
    more: { link: '/sales/stats' },
  }),
  'follow-ups': blockWidget({ key: 'follow-ups', title: '待跟进' }),
  'sales-compare': blockWidget({
    key: 'sales-compare',
    title: '业务员对比',
    period: '本月',
    help: '按成交额排序',
  }),
  'quote-conversion': blockWidget({
    key: 'quote-conversion',
    title: '报价转化',
    period: '近 6 个月',
    help: '报价单转为 PI 的比例',
  }),
  // 采购负责人
  'sourcing-overview': blockWidget({
    key: 'sourcing-overview',
    title: '询价',
    help: '口径同询价分配',
    more: { link: '/inquiry/sourcing-board' },
  }),
  'buyer-efficiency': blockWidget({
    key: 'buyer-efficiency',
    title: '采购员效率',
    period: '本月',
    help: '口径同询价统计',
    more: { link: '/inquiry/sourcing-stats' },
  }),
  'purchase-execution': blockWidget({
    key: 'purchase-execution',
    title: '采购执行',
  }),
  'bargain-result': blockWidget({
    key: 'bargain-result',
    title: '砍价成果',
    period: '本月',
    help: '口径同采购订单',
  }),
  payables: blockWidget({
    key: 'payables',
    title: '应付',
    more: { link: '/finance/payables' },
  }),
  // 运营负责人
  'content-pipeline': blockWidget({
    key: 'content-pipeline',
    title: '内容流水线',
    period: '本公司 · 当前',
  }),
  'platform-progress': blockWidget({
    key: 'platform-progress',
    title: '平台发布进度',
    help: '按发品任务统计',
  }),
  'media-overview': blockWidget({
    key: 'media-overview',
    title: '素材',
    more: { link: '/operations/media' },
  }),
  'monthly-output': blockWidget({ key: 'monthly-output', title: '本月产出' }),
  // 老板
  'biz-overview': blockWidget({
    key: 'biz-overview',
    title: '经营概览',
    period: '本月 · 人民币',
    help: '口径同经营统计',
    more: { link: '/finance/operating-report' },
  }),
  cash: blockWidget({ key: 'cash', title: '资金', period: '截至今天' }),
  'sales-funnel': blockWidget({
    key: 'sales-funnel',
    title: '销售漏斗',
    period: '本月',
    help: '各级之间的转化率',
  }),
  'revenue-trend': blockWidget({
    key: 'revenue-trend',
    title: '近 12 个月',
    help: '每月成交额',
  }),
  'risk-alerts': blockWidget({ key: 'risk-alerts', title: '风险提醒' }),
  rankings: blockWidget({ key: 'rankings', title: '排行', period: '本月' }),
};
