import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { Alert, Button, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { type OverduePis, piApi, readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';

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

  const viewAll = () => history.push('/sales/pi?expired=1');

  return (
    <Card style={{ padding: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          marginBottom: 12,
        }}
      >
        <span
          style={{
            width: 32,
            height: 32,
            borderRadius: 10,
            background: palette.redSoft,
            color: palette.red,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <ClockCircleOutlined />
        </span>
        <b style={{ color: palette.ink, fontSize: 15 }}>过期未收款的 PI</b>
        {data && data.count > 0 && (
          <Button
            type="link"
            size="small"
            style={{ marginLeft: 'auto' }}
            onClick={viewAll}
          >
            查看全部 <RightOutlined />
          </Button>
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
          <CheckCircleOutlined style={{ color: palette.green }} />
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
          <div style={{ display: 'grid', gap: 8 }}>
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
                  gap: 12,
                  padding: '10px 12px',
                  borderRadius: 10,
                  background: palette.inset,
                  border: `1px solid ${palette.hairline}`,
                }}
              >
                <span style={{ minWidth: 0, flex: 1 }}>
                  <span
                    style={{
                      display: 'block',
                      color: palette.link,
                      fontWeight: 600,
                    }}
                  >
                    {p.piNo}
                  </span>
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    {p.customerName}
                  </span>
                </span>
                <span style={{ ...num, color: palette.sub, fontSize: 13 }}>
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
