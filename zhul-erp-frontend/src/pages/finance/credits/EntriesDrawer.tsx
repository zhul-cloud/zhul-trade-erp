import { EditOutlined, SendOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Drawer, Empty, Skeleton } from 'antd';
import React, { useEffect, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  type CreditAccount,
  type CreditEntry,
  creditApi,
  ENTRY_TONES,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 来源单据的跳转地址 */
const sourceHref = (e: CreditEntry): string | undefined => {
  if (!e.sourceId) return undefined;
  if (e.sourceType === 'PI') return `/sales/pi/${e.sourceId}`;
  if (e.sourceType === 'SO') return `/sales/orders/${e.sourceId}`;
  if (e.sourceType === 'REFUND') return '/finance/refunds';
  return undefined;
};

/** 余额流水（余额管理与客户详情共用；readOnly 时不显示转给客户与手工调整） */
const EntriesDrawer: React.FC<{
  account?: CreditAccount;
  open: boolean;
  onClose: () => void;
  readOnly?: boolean;
  onWithdraw?: (a: CreditAccount) => void;
  onAdjust?: (a: CreditAccount) => void;
  /** 刷新计数：外部操作完成后递增以重新加载 */
  version?: number;
}> = ({ account, open, onClose, readOnly, onWithdraw, onAdjust, version }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<CreditEntry[] | null>(null);

  useEffect(() => {
    if (!open || !account) return;
    setRows(null);
    creditApi
      .entries(account.customerId, account.currencyCode, account.businessUnitId)
      .then(setRows)
      .catch((e) => {
        message.error(readBizError(e).message);
        setRows([]);
      });
  }, [open, account, message, version]);

  const cur = account?.currencyCode ?? 'USD';
  const box = (label: string, value: React.ReactNode, color: string) => (
    <div
      style={{
        flex: 1,
        padding: '12px 14px',
        borderRadius: 12,
        background: palette.inset,
      }}
    >
      <div style={{ fontSize: 12, color: palette.mute }}>{label}</div>
      <div style={{ ...num, fontSize: 16, fontWeight: 700, color }}>
        {value}
      </div>
    </div>
  );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size="large"
      title={
        account && (
          <div>
            <div>
              {account.customerName} · {cur}
            </div>
            <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
              {[account.businessUnitName, account.customerCountry]
                .filter(Boolean)
                .join(' · ')}
            </div>
          </div>
        )
      }
      destroyOnHidden
    >
      {account && (
        <div style={{ display: 'grid', gap: 16 }}>
          <div style={{ display: 'flex', gap: 12 }}>
            {box(
              '可用余额',
              formatAmount(account.available, cur),
              palette.green,
            )}
            {box(
              'PI 占用中',
              formatAmount(account.occupied, cur),
              account.occupied > 0 ? palette.orange : palette.mute,
            )}
            {box(
              '人民币价值',
              formatAmount(account.availableCny, 'CNY'),
              palette.ink,
            )}
          </div>
          {!readOnly && (
            <div style={{ display: 'flex', gap: 8 }}>
              <Button
                type="primary"
                size="small"
                icon={<SendOutlined />}
                disabled={account.available <= 0}
                onClick={() => onWithdraw?.(account)}
              >
                转给客户
              </Button>
              <Button
                size="small"
                icon={<EditOutlined />}
                onClick={() => onAdjust?.(account)}
              >
                手工调整
              </Button>
            </div>
          )}
          <div style={{ fontWeight: 600, color: palette.ink }}>流水</div>
          {!rows ? (
            <Skeleton active />
          ) : rows.length === 0 ? (
            <Empty description="还没有流水" />
          ) : (
            <div
              style={{
                borderRadius: 12,
                overflow: 'hidden',
                border: `1px solid ${palette.hairline}`,
              }}
            >
              {rows.map((e) => {
                const href = sourceHref(e);
                return (
                  <div
                    key={e.id}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '96px minmax(0, 1fr) 130px',
                      gap: 12,
                      padding: '12px 14px',
                      borderTop: `1px solid ${palette.hairline}`,
                    }}
                  >
                    <div style={{ fontSize: 12, color: palette.sub }}>
                      {e.createTime?.slice(5, 16).replace('T', ' ')}
                      <div style={{ color: palette.mute }}>{e.createBy}</div>
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div
                        style={{
                          display: 'flex',
                          gap: 8,
                          alignItems: 'center',
                          flexWrap: 'wrap',
                        }}
                      >
                        <Pill tone={ENTRY_TONES[e.entryType] ?? 'gray'}>
                          {e.typeName}
                        </Pill>
                        {e.sourceNo &&
                          (href ? (
                            <a onClick={() => history.push(href)}>
                              {e.sourceNo}
                            </a>
                          ) : (
                            <span style={{ color: palette.sub }}>
                              {e.sourceNo}
                            </span>
                          ))}
                      </div>
                      {e.remark && (
                        <div
                          style={{
                            fontSize: 12,
                            color: palette.mute,
                            marginTop: 4,
                          }}
                        >
                          {e.remark}
                        </div>
                      )}
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <div
                        style={{
                          ...num,
                          fontWeight: 700,
                          color: e.amount > 0 ? palette.green : palette.ink,
                        }}
                      >
                        {e.amount > 0 ? '+' : ''}
                        {formatAmount(e.amount, cur)}
                      </div>
                      <div
                        style={{ ...num, fontSize: 12, color: palette.mute }}
                      >
                        {formatAmount(e.amountCny, 'CNY')}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
          <div style={{ fontSize: 12, color: palette.mute }}>
            人民币价值在流水生成时锁定，之后结汇不再改变；转出按先转入的先用。流水不能修改，单据作废时生成反向流水。
          </div>
        </div>
      )}
    </Drawer>
  );
};

export default EntriesDrawer;
