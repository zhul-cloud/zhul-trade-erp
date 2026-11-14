import { history, Link, useAccess } from '@umijs/max';
import { Empty, Segmented, Select, Skeleton, Switch } from 'antd';
import React, { useEffect, useState } from 'react';
import { PageTitle } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { Card, CoverBar, useCoverColors, useOptions, useUsers } from '../components';
import { ProjectModal } from '../dialogs';
import { COVER_NAMES, type Matrix, PATHS, PERM_PROJECT, PRIORITY_NAMES, readBizError, supplyApi } from '../service';

type Cell = Matrix['cells'][number];
type Mode = 'cover' | 'stockout';

const PERIODS = [
  { value: 30, label: '近 30 天' },
  { value: 90, label: '近 90 天' },
];

const CoverCell: React.FC<{ c: Cell }> = ({ c }) => {
  const { palette } = useAppTheme();
  const colors = useCoverColors();
  const tint = colors[c.worst] ?? palette.hairline;
  return (
    <Link
      to={PATHS.models(c.brandId, c.categoryId)}
      style={{
        display: 'grid',
        gap: 8,
        padding: '10px 14px',
        borderRadius: 10,
        color: palette.ink,
        background: `color-mix(in srgb, ${tint} 12%, ${palette.card})`,
        border: c.worst === 1 ? `1px solid color-mix(in srgb, ${tint} 45%, transparent)` : '1px solid transparent',
      }}
    >
      <span style={{ display: 'inline-flex', gap: 8, alignItems: 'baseline' }}>
        <b style={{ fontSize: 20 }}>{c.total}</b>
        <span style={{ fontSize: 12, color: palette.sub, letterSpacing: 1 }}>
          {[4, 3, 2, 1].map((k) => c.coverage[k] ?? 0).join('/')}
        </span>
      </span>
      <CoverBar coverage={c.coverage} width={140} legend={false} />
      {c.projectId && (
        <span style={{ fontSize: 12, color: palette.link }}>专项中</span>
      )}
    </Link>
  );
};

const StockoutCell: React.FC<{ c: Cell; max: number }> = ({ c, max }) => {
  const { palette } = useAppTheme();
  const pct = Math.round(10 + (c.stockoutTimes / max) * 30);
  return (
    <Link
      to={PATHS.models(c.brandId, c.categoryId)}
      style={{
        display: 'grid',
        gap: 4,
        padding: '10px 14px',
        borderRadius: 10,
        color: palette.ink,
        background: `color-mix(in srgb, ${palette.red} ${pct}%, ${palette.card})`,
      }}
    >
      <span>
        <b style={{ fontSize: 20 }}>{c.stockoutModels}</b>
        <span style={{ fontSize: 12, marginLeft: 6 }}>个型号</span>
      </span>
      <span style={{ fontSize: 12, color: palette.red }}>
        {c.stockoutTimes} 次
        {c.projectId && <span style={{ color: palette.link }}> · 专项中</span>}
      </span>
    </Link>
  );
};

/** 覆盖矩阵（F12 / F13）：品牌 × 品类，按覆盖状态或按无货查看 */
const CoverageMatrixPage: React.FC = () => {
  const { palette } = useAppTheme();
  const colors = useCoverColors();
  const access = useAccess() as Record<string, boolean>;
  const canProject = !!access[PERM_PROJECT];
  const { brands, tops } = useOptions();
  const users = useUsers();
  const [mode, setMode] = useState<Mode>(() =>
    new URLSearchParams(window.location.search).get('mode') === 'stockout' ? 'stockout' : 'cover',
  );
  const [parent, setParent] = useState<number>();
  const [brandId, setBrandId] = useState<number>();
  const [priority, setPriority] = useState<number>();
  const [ownerId, setOwnerId] = useState<number>();
  const [gapsOnly, setGapsOnly] = useState(false);
  const [period, setPeriod] = useState(30);
  const [data, setData] = useState<Matrix>();
  const [error, setError] = useState('');
  const [projectScope, setProjectScope] = useState<{ brandId: number; categoryId?: number }>();

  useEffect(() => {
    setData(undefined);
    const stock = mode === 'stockout';
    supplyApi
      .matrix({
        mode,
        period: stock ? period : undefined,
        parentCategoryId: parent,
        brandId,
        priority: stock ? undefined : priority,
        ownerId: stock ? undefined : ownerId,
        gapsOnly: stock ? undefined : gapsOnly || undefined,
      })
      .then((m) => {
        setData(m);
        setError('');
      })
      .catch((e) => setError(readBizError(e).message));
  }, [mode, period, parent, brandId, priority, ownerId, gapsOnly]);

  const stock = mode === 'stockout';
  const cellOf = new Map((data?.cells ?? []).map((c) => [`${c.brandId}-${c.categoryId}`, c]));
  const brandStock = (id: number) =>
    (data?.cells ?? [])
      .filter((c) => c.brandId === id)
      .reduce((s, c) => [s[0] + c.stockoutModels, s[1] + c.stockoutTimes], [0, 0]);
  const max = Math.max(1, ...(data?.cells ?? []).map((c) => c.stockoutTimes));
  const totals = (data?.cells ?? []).reduce((s, c) => [s[0] + c.stockoutModels, s[1] + c.stockoutTimes], [0, 0]);
  const cols = data?.categories.length ?? 0;

  return (
    <div>
      <PageTitle
        title={
          <span style={{ display: 'inline-flex', gap: 16, alignItems: 'center' }}>
            覆盖矩阵
            <Segmented
              value={mode}
              onChange={(v) => setMode(v as Mode)}
              options={[
                { value: 'cover', label: '按覆盖状态' },
                { value: 'stockout', label: '按无货' },
              ]}
            />
          </span>
        }
        actions={
          stock ? (
            <span style={{ color: palette.mute, fontSize: 13 }}>颜色越深，无货次数越多</span>
          ) : (
            <span style={{ display: 'inline-flex', gap: 16, fontSize: 13 }}>
              {[4, 3, 2, 1].map((k) => (
                <span key={k} style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
                  <span style={{ width: 10, height: 10, borderRadius: 3, background: colors[k] }} />
                  {COVER_NAMES[k]}
                </span>
              ))}
            </span>
          )
        }
      />
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center', marginBottom: 16 }}>
        <Select
          allowClear
          style={{ width: 200 }}
          placeholder="一级品类"
          value={parent}
          onChange={setParent}
          options={tops}
        />
        <Select
          allowClear
          style={{ width: 170 }}
          placeholder="品牌"
          showSearch={{ optionFilterProp: 'label' }}
          value={brandId}
          onChange={setBrandId}
          options={brands}
        />
        {stock ? (
          <Select style={{ width: 130 }} value={period} onChange={setPeriod} options={PERIODS} />
        ) : (
          <>
            <Select
              allowClear
              style={{ width: 130 }}
              placeholder="优先级"
              value={priority}
              onChange={setPriority}
              options={[3, 2, 1].map((k) => ({ value: k, label: PRIORITY_NAMES[k] }))}
            />
            <Select
              allowClear
              style={{ width: 150 }}
              placeholder="负责人"
              showSearch={{ optionFilterProp: 'label' }}
              value={ownerId}
              onChange={setOwnerId}
              options={users}
            />
          </>
        )}
        <span style={{ flex: 1 }} />
        {stock ? (
          <b>
            共 {totals[0]} 个型号 · {totals[1]} 次
          </b>
        ) : (
          <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            <Switch size="small" checked={gapsOnly} onChange={setGapsOnly} />
            只看有缺口
          </span>
        )}
      </div>
      <Card style={{ overflow: 'hidden' }}>
        {error ? (
          <Empty style={{ padding: 40 }} description={error} />
        ) : !data ? (
          <div style={{ padding: 20 }}>
            <Skeleton active paragraph={{ rows: 8 }} />
          </div>
        ) : data.brands.length === 0 ? (
          <Empty
            style={{ padding: 40 }}
            description={stock ? `近 ${data.days} 天没有无货记录` : '没有进入覆盖矩阵的型号'}
          />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: `160px repeat(${cols}, minmax(170px, 1fr))`,
                minWidth: 160 + cols * 180,
              }}
            >
              <span style={{ padding: '12px 16px', color: palette.mute, fontSize: 13 }}>品牌</span>
              {data.categories.map((c) => (
                <span key={c.id} style={{ padding: '12px 8px', color: palette.mute, fontSize: 13 }}>
                  {c.name}
                </span>
              ))}
              {data.brands.map((b) => {
                const [m, t] = brandStock(b.id);
                return (
                  <React.Fragment key={b.id}>
                    <div style={{ padding: '14px 16px', borderTop: `1px solid ${palette.hairline}` }}>
                      <b>{b.name}</b>
                      <div style={{ fontSize: 12, color: palette.sub }}>
                        {stock ? `无货 ${m} 个型号 · ${t} 次` : `${b.total} 个需求`}
                      </div>
                    </div>
                    {data.categories.map((cat) => {
                      const c = cellOf.get(`${b.id}-${cat.id}`);
                      const has = c && (stock ? c.stockoutModels > 0 : c.total > 0);
                      return (
                        <div
                          key={cat.id}
                          style={{
                            padding: 8,
                            borderTop: `1px solid ${palette.hairline}`,
                            display: 'grid',
                            gap: 4,
                            alignContent: 'center',
                          }}
                        >
                          {has && c ? (
                            stock ? (
                              <>
                                <StockoutCell c={c} max={max} />
                                {canProject && !c.projectId && (
                                  <button
                                    type="button"
                                    onClick={() => setProjectScope({ brandId: c.brandId, categoryId: c.categoryId || undefined })}
                                    style={{
                                      border: 0,
                                      padding: 0,
                                      background: 'none',
                                      cursor: 'pointer',
                                      color: palette.link,
                                      fontSize: 12,
                                      justifySelf: 'start',
                                    }}
                                  >
                                    建专项
                                  </button>
                                )}
                              </>
                            ) : (
                              <CoverCell c={c} />
                            )
                          ) : (
                            !stock && <span style={{ color: palette.faint, paddingLeft: 8 }}>—</span>
                          )}
                        </div>
                      );
                    })}
                  </React.Fragment>
                );
              })}
            </div>
          </div>
        )}
      </Card>
      <div style={{ marginTop: 12, fontSize: 13, color: palette.mute }}>
        {stock
          ? '点格子查看无货的型号；在格子下方可以直接建专项。'
          : '格子里的数字是已进入矩阵的需求数，后面依次是可直接报价 / 需确认 / 有线索未验证 / 无渠道的数量；颜色取其中最差的一档。点格子查看型号。'}
      </div>
      <ProjectModal
        scope={projectScope}
        onClose={() => setProjectScope(undefined)}
        onDone={(id) => {
          setProjectScope(undefined);
          history.push(PATHS.project(id));
        }}
      />
    </div>
  );
};

export default CoverageMatrixPage;
