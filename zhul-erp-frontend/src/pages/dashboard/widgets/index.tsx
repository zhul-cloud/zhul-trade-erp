import {
  AccountBookOutlined,
  ApartmentOutlined,
  BankOutlined,
  BarChartOutlined,
  BellOutlined,
  ClusterOutlined,
  DashboardOutlined,
  FilterOutlined,
  GlobalOutlined,
  HourglassOutlined,
  LineChartOutlined,
  PictureOutlined,
  ProfileOutlined,
  RiseOutlined,
  SafetyOutlined,
  ShoppingOutlined,
  StarOutlined,
  SwapOutlined,
  TeamOutlined,
  TrophyOutlined,
  UnorderedListOutlined,
  UserAddOutlined,
  WalletOutlined,
} from '@ant-design/icons';
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
 * 看板组件注册表：编码与后端 WorkbenchWidget 一致。数据按通用数据块返回的组件只登记标题与图标（blockWidget），
 * 模板（后端 workbench_template）里引用编码即可；后端有、前端还没登记的编码直接跳过。
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
  'my-todo': blockWidget({
    key: 'my-todo',
    title: '我的待办',
    icon: <UnorderedListOutlined />,
    tone: 'blue',
    sub: '点击进入对应列表',
  }),
  'my-performance': blockWidget({
    key: 'my-performance',
    title: '我的业绩',
    icon: <RiseOutlined />,
    tone: 'green',
    sub: '本月，口径同销售统计',
  }),
  'quote-progress': blockWidget({
    key: 'quote-progress',
    title: '询价进度',
    icon: <HourglassOutlined />,
    tone: 'orange',
    sub: '我的询盘里还在询价的',
  }),
  'my-opportunities': blockWidget({
    key: 'my-opportunities',
    title: '我的商机',
    icon: <UserAddOutlined />,
    tone: 'blue',
    sub: '本月',
  }),
  // 业务负责人
  'team-performance': blockWidget({
    key: 'team-performance',
    title: '团队业绩',
    icon: <TeamOutlined />,
    tone: 'blue',
    sub: '本月，按你的数据范围，口径同销售统计',
    more: { label: '销售统计', link: '/sales/stats' },
  }),
  'follow-ups': blockWidget({
    key: 'follow-ups',
    title: '待跟进',
    icon: <BellOutlined />,
    tone: 'orange',
  }),
  'sales-compare': blockWidget({
    key: 'sales-compare',
    title: '业务员对比',
    icon: <BarChartOutlined />,
    tone: 'violet',
    sub: '本月，按成交额排序',
  }),
  'quote-conversion': blockWidget({
    key: 'quote-conversion',
    title: '报价转化',
    icon: <SwapOutlined />,
    tone: 'green',
    sub: '近 6 个月报价单转为 PI 的比例',
  }),
  // 采购负责人
  'sourcing-overview': blockWidget({
    key: 'sourcing-overview',
    title: '询价',
    icon: <ProfileOutlined />,
    tone: 'blue',
    sub: '口径同询价分配',
    more: { label: '询价分配', link: '/inquiry/sourcing-board' },
  }),
  'buyer-efficiency': blockWidget({
    key: 'buyer-efficiency',
    title: '采购员效率',
    icon: <DashboardOutlined />,
    tone: 'green',
    sub: '本月，口径同询价统计',
    more: { label: '询价统计', link: '/inquiry/sourcing-stats' },
  }),
  'purchase-execution': blockWidget({
    key: 'purchase-execution',
    title: '采购执行',
    icon: <ShoppingOutlined />,
    tone: 'violet',
    sub: '点击进入对应列表',
  }),
  'bargain-result': blockWidget({
    key: 'bargain-result',
    title: '砍价成果',
    icon: <StarOutlined />,
    tone: 'green',
    sub: '本月，口径同采购订单',
  }),
  payables: blockWidget({
    key: 'payables',
    title: '应付',
    icon: <WalletOutlined />,
    tone: 'orange',
    more: { label: '应付单', link: '/finance/payables' },
  }),
  // 运营负责人
  'content-pipeline': blockWidget({
    key: 'content-pipeline',
    title: '内容流水线',
    icon: <ApartmentOutlined />,
    tone: 'blue',
    sub: '本公司 · 当前，点击数字进入对应页面',
  }),
  'platform-progress': blockWidget({
    key: 'platform-progress',
    title: '平台发布进度',
    icon: <GlobalOutlined />,
    tone: 'violet',
    sub: '按发品任务',
  }),
  'media-overview': blockWidget({
    key: 'media-overview',
    title: '素材',
    icon: <PictureOutlined />,
    tone: 'orange',
    more: { label: '素材库', link: '/operations/media' },
  }),
  'monthly-output': blockWidget({
    key: 'monthly-output',
    title: '本月产出',
    icon: <ClusterOutlined />,
    tone: 'green',
  }),
  // 老板
  'biz-overview': blockWidget({
    key: 'biz-overview',
    title: '经营概览',
    icon: <BankOutlined />,
    tone: 'blue',
    sub: '本月 · 人民币，口径同经营统计',
    more: { label: '经营统计', link: '/finance/operating-report' },
  }),
  cash: blockWidget({
    key: 'cash',
    title: '资金',
    icon: <AccountBookOutlined />,
    tone: 'green',
    sub: '截至今天',
  }),
  'sales-funnel': blockWidget({
    key: 'sales-funnel',
    title: '销售漏斗',
    icon: <FilterOutlined />,
    tone: 'violet',
    sub: '本月，逐级转化率',
  }),
  'revenue-trend': blockWidget({
    key: 'revenue-trend',
    title: '近 12 个月',
    icon: <LineChartOutlined />,
    tone: 'blue',
    sub: '每月成交额',
  }),
  'risk-alerts': blockWidget({
    key: 'risk-alerts',
    title: '风险提醒',
    icon: <SafetyOutlined />,
    tone: 'red',
    sub: '点击进入对应列表',
  }),
  rankings: blockWidget({
    key: 'rankings',
    title: '排行',
    icon: <TrophyOutlined />,
    tone: 'orange',
    sub: '本月',
  }),
};
