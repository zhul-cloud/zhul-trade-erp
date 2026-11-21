import { history } from '@umijs/max';
import { Alert, Button, Skeleton } from 'antd';
import { CircleCheck } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CardTitle } from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { type OverduePis, piApi, readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { MoreLink } from './widgets/WidgetCard';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 工作台 · 过期未收款的 PI：已发送、未付款且过了有效期；「查看全部」进入带筛选的 PI 列表，数字与列表一致 */
const OverduePiCard: React.FC = () => {
  const { palette } = useAppTheme();
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

  const viewAll = () => history.push('/sales/pi?expired=1');

  return (
    <Card>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 16,
        }}
      >
        <CardTitle>过期未收款的 PI</CardTitle>
        {data && data.count > 0 && (
          <span style={{ marginLeft: 'auto' }}>
            <MoreLink onClick={viewAll}>查看全部</MoreLink>
          </span>
        )}
      </div>
      {error ? (
        <Alert
          type="error"
          showIcon
          title={`过期 PI 加载失败：${error}`}
          action={
            <Button size="small" onClick={load}>
              重试
            </Button>
          }
        />
      ) : !data ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : data.count === 0 ? (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            color: palette.sub,
            padding: '8px 0',
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
        <>
          <div
            style={{
              display: 'flex',
              alignItems: 'baseline',
              gap: 8,
              marginBottom: 12,
            }}
          >
            <span
              style={{
                ...num,
                fontSize: 28,
                fontWeight: 700,
                color: palette.ink,
              }}
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
          {/* 行间细分隔线（原型 D1） */}
          <div style={{ display: 'grid' }}>
            {data.top.map((p) => (
              <button
                type="button"
                key={p.id}
                onClick={() => history.push(`/sales/pi/${p.id}`)}
                style={{
                  all: 'unset',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 16,
                  padding: '12px 0',
                  borderTop: `1px solid ${palette.hairline}`,
                }}
              >
                <span
                  style={{
                    ...num,
                    flex: 'none',
                    color: palette.link,
                    fontWeight: 600,
                  }}
                >
                  {p.piNo}
                </span>
                <span
                  style={{
                    flex: 1,
                    minWidth: 0,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    fontSize: 13,
                    color: palette.ink,
                  }}
                >
                  {p.customerName}
                </span>
                <span
                  style={{
                    ...num,
                    color: palette.ink,
                    fontWeight: 600,
                    fontSize: 13,
                  }}
                >
                  {formatAmount(p.totalAmount, p.currencyCode)}
                </span>
                <Pill tone="red">已过期 {p.expiredDays} 天</Pill>
              </button>
            ))}
          </div>
        </>
      )}
    </Card>
  );
};

export default OverduePiCard;
