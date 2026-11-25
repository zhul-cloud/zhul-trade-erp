import { QuestionCircleOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { Tooltip } from 'antd';
import React from 'react';
import type { Palette } from '@/theme/palette';
import { GRADE_NAMES } from './profile/service';
import { GradeBadge, Stars } from './profile/ui';
import type { CustomerListItem } from './service';

/** 两列的排序状态：sortField 为 bizGrade、potentialRating 时对应列显示排序箭头 */
export interface GradeSort {
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

const orderOf = (sort: GradeSort, field: string) =>
  sort.sortField === field
    ? sort.sortOrder === 'asc'
      ? ('ascend' as const)
      : ('descend' as const)
    : null;

/** 表头带说明图标 */
export const headerTip = (palette: Palette, text: string, tip: string) => (
  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
    {text}
    <Tooltip title={tip}>
      <QuestionCircleOutlined
        aria-label={`${text}说明`}
        style={{ color: palette.mute, cursor: 'help' }}
      />
    </Tooltip>
  </span>
);

/**
 * 「经营等级」「潜力等级」两列（客户列表与公海客户共用，spec master-data/customer-profile、master-data/customer-pool）：
 * 表头可排序，调过级的经营等级悬停看计算等级与调级原因
 */
export const gradeColumns = (
  palette: Palette,
  sort: GradeSort,
): TableColumnsType<CustomerListItem> => [
  {
    title: headerTip(
      palette,
      '经营等级',
      '按近 12 个月的成交（最近一次下单、下单次数、金额）每天凌晨算出 S / A / B / C，S 最好；没有成交过的显示「未成交」。业务员可以上调或下调一级',
    ),
    dataIndex: 'bizGrade',
    width: 130,
    sorter: true,
    showSorterTooltip: false,
    sortOrder: orderOf(sort, 'bizGrade'),
    render: (v: number, r) =>
      r.bizGradeAdjust ? (
        <Tooltip
          title={`计算等级 ${GRADE_NAMES[r.bizGradeCalc]}，${r.bizGradeAdjust > 0 ? '上调' : '下调'}一级：${r.bizGradeAdjustReason || '—'}${r.bizGradeAdjustByName ? ` · ${r.bizGradeAdjustByName}` : ''}`}
        >
          <span>
            <GradeBadge value={v} adjusted />
          </span>
        </Tooltip>
      ) : (
        <GradeBadge value={v} />
      ),
  },
  {
    title: headerTip(
      palette,
      '潜力等级',
      '1–5 星，表示这个客户值不值得投入精力跟，所有客户都能评，也可以采纳背调报告的评分；没评的显示「未评」',
    ),
    dataIndex: 'potentialRating',
    width: 130,
    sorter: true,
    showSorterTooltip: false,
    sortOrder: orderOf(sort, 'potentialRating'),
    render: (v: number) => <Stars value={v} />,
  },
];
