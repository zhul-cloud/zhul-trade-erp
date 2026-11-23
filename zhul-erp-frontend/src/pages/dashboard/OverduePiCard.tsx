import { history } from '@umijs/max';
import { Tag } from 'antd';
import { CircleCheck } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CountryFlag } from '@/components/CountryText';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { useCountries } from '@/pages/product/components/useCountries';
import { type OverduePis, piApi, readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { MoreLink, WidgetCard } from './widgets/WidgetCard';

/** M 档卡片放得下的明细条数（摘要 + 3 行 × 56），其余点「查看全部」 */
const SHOWN = 3;

/**
 * 工作台 · 过期未收款的 PI（工作台原型 H3）：顶部摘要（张数 28/600 错误色 + 各币种合计），
 * 下面最急的几张（PI 号、国旗 + 客户、合计、已过期天数）；「查看全部」进入带筛选的 PI 列表，数字与列表一致。
 */
const OverduePiCard: React.FC = () => {
  const { palette } = useAppTheme();
  const { find } = useCountries();
  const [data, setData] = useState<OverduePis>();
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    setData(undefined);
    try {
      setData(await piApi.overdue());
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  return (
    <WidgetCard
      title="过期未收款的 PI"
      extra={
        data &&
        data.count > SHOWN && (
          <MoreLink onClick={() => history.push('/sales/pi?expired=1')}>
            查看全部 {data.count}
          </MoreLink>
        )
      }
      loading={!data}
      error={error}
      onRetry={load}
    >
      {data && data.count === 0 ? (
        <div
          style={{
            flex: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            color: palette.sub,
          }}
        >
          <CircleCheck
            size={16}
            aria-hidden
            style={{ color: palette.greenMain }}
          />
          没有过期未收款的 PI
        </div>
      ) : (
        data && (
          <>
            <div
              className="num"
              style={{
                display: 'flex',
                alignItems: 'baseline',
                gap: 8,
                paddingBottom: 12,
              }}
            >
              <span
                style={{ fontSize: 28, fontWeight: 600, color: palette.red }}
              >
                {data.count}
              </span>
              <span style={{ color: palette.mute, fontSize: 13 }}>
                张 · 合计{' '}
                {data.totals
                  .map((t) => formatAmount(t.amount, t.currencyCode))
                  .join('、')}
              </span>
            </div>
            {data.top.slice(0, SHOWN).map((p) => {
              const c = find(p.customerCountry);
              return (
                <button
                  type="button"
                  key={p.id}
                  className="zhul-row-hover"
                  onClick={() => history.push(`/sales/pi/${p.id}`)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    minHeight: 56,
                    padding: '0 8px',
                    margin: '0 -8px',
                    border: 'none',
                    borderTop: `1px solid ${palette.hairline}`,
                    background: 'transparent',
                    cursor: 'pointer',
                    textAlign: 'left',
                    font: 'inherit',
                  }}
                >
                  <span
                    style={{
                      flex: 1,
                      minWidth: 0,
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 4,
                    }}
                  >
                    <span
                      className="num"
                      style={{ fontWeight: 600, color: palette.ink }}
                    >
                      {p.piNo}
                    </span>
                    <span
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 6,
                        minWidth: 0,
                        fontSize: 12,
                        color: palette.mute,
                      }}
                    >
                      <CountryFlag code={c?.code} />
                      <span
                        style={{
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {p.customerName}
                      </span>
                    </span>
                  </span>
                  <span
                    className="num"
                    style={{ fontWeight: 600, color: palette.ink }}
                  >
                    {formatAmount(p.totalAmount, p.currencyCode)}
                  </span>
                  <Tag color="error" style={{ marginInlineEnd: 0 }}>
                    已过期 {p.expiredDays} 天
                  </Tag>
                </button>
              );
            })}
          </>
        )
      )}
    </WidgetCard>
  );
};

export default OverduePiCard;
