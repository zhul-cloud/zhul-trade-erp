import { LockOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';
import { Segmented, Select } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Pill } from '@/pages/crm/opportunity/components';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 事业部（spec system/business-unit）：公司没有设置事业部时 enabled 为 false，各页面不显示事业部字段与筛选。
 * 单据上的「所属事业部」为空表示「公共」（费用、其他收入）或公司没有事业部。
 */
export interface BusinessUnit {
  id: number;
  name: string;
  /** 开始核算月份 yyyy-MM */
  startMonth?: string;
}

export interface BusinessUnitInfo {
  enabled: boolean;
  units: BusinessUnit[];
  /** 公共费用最低分摊比例（%） */
  minSharePercent: number;
  /** 统计页可选的事业部 */
  statsUnitIds: number[];
  /** 统计页能否看全公司合计 */
  statsTotal: boolean;
  /** 当前用户主部门所在的事业部 */
  myUnitId?: number;
}

const EMPTY: BusinessUnitInfo = {
  enabled: false,
  units: [],
  minSharePercent: 20,
  statsUnitIds: [],
  statsTotal: true,
};

let cache: Promise<BusinessUnitInfo> | undefined;

/** 取事业部设置；force 时重新请求（部门管理标记事业部后） */
export const fetchBusinessUnits = (
  force = false,
): Promise<BusinessUnitInfo> => {
  if (force || !cache) {
    cache = request('/api/v1/system/business-units', { method: 'GET' })
      .then((r) => ({ ...EMPTY, ...r.data }) as BusinessUnitInfo)
      .catch((e) => {
        cache = undefined;
        throw e;
      });
  }
  return cache;
};

/** 事业部设置；第二个返回值重新加载 */
export const useBusinessUnits = (): [BusinessUnitInfo, () => void] => {
  const [info, setInfo] = useState<BusinessUnitInfo>(EMPTY);
  const load = useCallback((force = false) => {
    fetchBusinessUnits(force)
      .then(setInfo)
      .catch(() => setInfo(EMPTY));
  }, []);
  useEffect(() => load(), [load]);
  return [info, () => load(true)];
};

const TONES = ['accent', 'violet', 'cyan', 'green', 'orange'] as const;

/** 所属事业部标签；id 为空显示「公共」 */
export const UnitPill: React.FC<{
  info: BusinessUnitInfo;
  id?: number | null;
}> = ({ info, id }) => {
  if (id == null) {
    return <Pill tone="gray">公共</Pill>;
  }
  const index = info.units.findIndex((u) => u.id === id);
  const name = index >= 0 ? info.units[index].name : '—';
  return <Pill tone={TONES[Math.max(index, 0) % TONES.length]}>{name}</Pill>;
};

/** 「公共」在下拉里的取值（提交时转成空） */
export const PUBLIC_UNIT = 0;

/**
 * 选所属事业部。allowPublic 时多一项「公共」（取值 0，提交前用 {@link unitOrPublic} 转成空）。
 */
export const UnitSelect: React.FC<{
  info: BusinessUnitInfo;
  value?: number;
  onChange?: (v: number) => void;
  allowPublic?: boolean;
  style?: React.CSSProperties;
  placeholder?: string;
}> = ({ info, value, onChange, allowPublic, style, placeholder }) => {
  const { palette } = useAppTheme();
  const options: { value: number; label: React.ReactNode }[] = info.units.map(
    (u) => ({ value: u.id, label: u.name }),
  );
  if (allowPublic) {
    options.push({
      value: PUBLIC_UNIT,
      label: (
        <span>
          公共
          <span style={{ color: palette.mute, fontSize: 12, marginLeft: 8 }}>
            按当月销售额比例分摊，每个事业部至少 {info.minSharePercent}%
          </span>
        </span>
      ),
    });
  }
  return (
    <Select
      value={value as number}
      onChange={(v) => onChange?.(v as number)}
      options={options}
      style={style}
      placeholder={placeholder ?? '请选择事业部'}
      labelRender={(p) =>
        p.value === PUBLIC_UNIT ? '公共' : (p.label as React.ReactNode)
      }
    />
  );
};

/** 列表筛选的事业部选项（含「公共」，取值 0） */
export const unitFilterOptions = (
  info: BusinessUnitInfo,
  withPublic = true,
) => [
  ...info.units.map((u) => ({ value: u.id, label: u.name })),
  ...(withPublic ? [{ value: PUBLIC_UNIT, label: '公共' }] : []),
];

/** 表单里的事业部取值转成接口参数：「公共」为空 */
export const unitOrPublic = (v?: number | null) =>
  v == null || v === PUBLIC_UNIT ? undefined : v;

/**
 * 统计页的事业部切换（spec access/data-scope「统计按事业部限定可见范围」）：
 * 能看合计的显示「全公司 + 各事业部」；只能看一个事业部的显示带锁的名称，不能切换。value 为空表示全公司。
 */
export const StatsUnitSwitch: React.FC<{
  info: BusinessUnitInfo;
  value?: number;
  onChange: (v?: number) => void;
}> = ({ info, value, onChange }) => {
  const { palette } = useAppTheme();
  if (!info.enabled) return null;
  const allowed = info.units.filter((u) => info.statsUnitIds.includes(u.id));
  if (!info.statsTotal && allowed.length === 1) {
    return (
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          height: 32,
          padding: '0 12px',
          borderRadius: 8,
          border: `1px solid ${palette.hairline}`,
          background: palette.inset,
          color: palette.ink,
          fontWeight: 600,
        }}
      >
        <LockOutlined style={{ color: palette.mute }} />
        {allowed[0].name}
      </span>
    );
  }
  const options = [
    ...(info.statsTotal ? [{ value: 0, label: '全公司' }] : []),
    ...allowed.map((u) => ({ value: u.id, label: u.name })),
  ];
  return (
    <Segmented
      value={value ?? (info.statsTotal ? 0 : allowed[0]?.id)}
      onChange={(v) => onChange(v === 0 ? undefined : (v as number))}
      options={options}
    />
  );
};
