import { DatePicker, Segmented } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useState } from 'react';

/** 统计分析页的时间范围：快捷选项 + 自定义日期范围；默认当月 */
export type RangePreset =
  | 'today'
  | 'yesterday'
  | 'week'
  | 'month'
  | 'lastMonth'
  | 'halfYear'
  | 'custom';

const LABELS: Record<RangePreset, string> = {
  today: '今天',
  yesterday: '昨天',
  week: '近 7 天',
  month: '当月',
  lastMonth: '上个月',
  halfYear: '近半年',
  custom: '自定义',
};

export const DEFAULT_PRESETS: RangePreset[] = [
  'month',
  'lastMonth',
  'halfYear',
];

/** 近半年：含当月在内的 6 个自然月 */
export const presetRange = (p: RangePreset): [Dayjs, Dayjs] => {
  const today = dayjs().startOf('day');
  switch (p) {
    case 'today':
      return [today, today];
    case 'yesterday':
      return [today.subtract(1, 'day'), today.subtract(1, 'day')];
    case 'week':
      return [today.subtract(6, 'day'), today];
    case 'lastMonth': {
      const last = today.subtract(1, 'month');
      return [last.startOf('month'), last.endOf('month').startOf('day')];
    }
    case 'halfYear':
      return [today.subtract(5, 'month').startOf('month'), today];
    default:
      return [today.startOf('month'), today];
  }
};

const matchPreset = (range: [Dayjs, Dayjs], presets: RangePreset[]) =>
  presets.find((p) => {
    const [a, b] = presetRange(p);
    return range[0].isSame(a, 'day') && range[1].isSame(b, 'day');
  }) ?? 'custom';

const StatsRangeFilter: React.FC<{
  value: [Dayjs, Dayjs];
  onChange: (range: [Dayjs, Dayjs]) => void;
  presets?: RangePreset[];
}> = ({ value, onChange, presets = DEFAULT_PRESETS }) => {
  const [preset, setPreset] = useState<RangePreset>(() =>
    matchPreset(value, presets),
  );
  return (
    <>
      <Segmented<RangePreset>
        value={preset}
        options={[...presets, 'custom' as const].map((p) => ({
          value: p,
          label: LABELS[p],
        }))}
        onChange={(p) => {
          setPreset(p);
          if (p !== 'custom') onChange(presetRange(p));
        }}
      />
      <DatePicker.RangePicker
        value={value}
        allowClear={false}
        disabledDate={(d) => d.isAfter(dayjs(), 'day')}
        onChange={(v) => {
          if (v?.[0] && v[1]) {
            setPreset('custom');
            onChange([v[0], v[1]]);
          }
        }}
      />
    </>
  );
};

export default StatsRangeFilter;
