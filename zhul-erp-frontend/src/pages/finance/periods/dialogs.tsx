import { InfoCircleOutlined, LockOutlined } from '@ant-design/icons';
import { App, Modal, Skeleton } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import { ReasonModal } from '@/pages/warehouse/components';
import { readBizError } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { SignedMoney } from '../costingParts';
import { type Period, periodApi } from '../costingService';

const nextMonth = (month: string) =>
  dayjs(`${month}-01`).add(1, 'month').format('YYYY-MM');

/** 带正负号的人民币金额：+CNY 12.00 / −CNY 3.50；0 为 CNY 0.00 */
export const signedCny = (v?: number) => {
  const n = Number(v ?? 0);
  const text = formatAmount(Math.abs(n), 'CNY');
  return n > 0 ? `+${text}` : n < 0 ? `−${text}` : text;
};

/**
 * 月份汇总按算式排（ui-design-patterns「汇总数字按算式排」，原型 P1b、P2）：
 * 期初 + 本月入库 − 本月出库 + 在库成本调整 = 期末；下面一行是不进算式的本月销售成本与成本调整单合计。
 */
export const PeriodTiles: React.FC<{ p: Period }> = ({ p }) => {
  const { palette } = useAppTheme();
  const items: [string, string, React.CSSProperties?][] = [
    ['期初库存金额', formatAmount(p.openingAmount, 'CNY')],
    ['本月入库', formatAmount(p.inAmount, 'CNY')],
    ['本月出库', formatAmount(p.outAmount, 'CNY')],
    [
      '在库成本调整',
      signedCny(p.stockAdjustAmount),
      p.stockAdjustAmount
        ? { color: p.stockAdjustAmount > 0 ? palette.green : palette.red }
        : undefined,
    ],
    ['期末库存金额', formatAmount(p.closingAmount, 'CNY')],
  ];
  const ops = ['+', '−', '+', '='];
  const parts = [
    ['在库', p.stockAdjustAmount],
    ['销售成本', p.adjustCogsAmount],
    ['其他出库', p.adjustOtherAmount],
  ].filter(([, v]) => Number(v ?? 0) !== 0);
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {items.map(([label, value, style], i) => (
          <React.Fragment key={label}>
            <div
              style={{
                flex: 1,
                minWidth: 0,
                border: `1px solid ${palette.hairline}`,
                borderRadius: 12,
                padding: '12px 14px',
              }}
            >
              <div style={{ fontSize: 12, color: palette.sub }}>{label}</div>
              <div
                style={{
                  fontSize: 18,
                  fontWeight: i === items.length - 1 ? 700 : 600,
                  color: palette.ink,
                  marginTop: 4,
                  fontVariantNumeric: 'tabular-nums',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  ...style,
                }}
                title={value}
              >
                {value}
              </div>
            </div>
            {i < ops.length && (
              <span style={{ color: palette.mute, fontSize: 16 }}>
                {ops[i]}
              </span>
            )}
          </React.Fragment>
        ))}
      </div>
      <div
        style={{
          marginTop: 12,
          fontSize: 13,
          color: palette.sub,
          display: 'flex',
          flexWrap: 'wrap',
          gap: '4px 12px',
        }}
      >
        <span>
          本月销售成本{' '}
          <b style={{ color: palette.ink }}>
            {formatAmount(p.cogsAmount, 'CNY')}
          </b>
        </span>
        <span>
          本月成本调整单合计 <SignedMoney value={p.adjustAmount} />
          {parts.length > 0 &&
            `（${parts.map(([l, v]) => `${l} ${signedCny(v as number).replace('CNY ', '')}`).join('、')}）`}
        </span>
      </div>
    </div>
  );
};

/** 结账确认（原型 P1b）：看汇总与结账前提醒，二次确认后结账 */
export const CloseModal: React.FC<{
  month?: string;
  onClose: () => void;
  onDone: () => void;
}> = ({ month, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [p, setP] = useState<Period>();
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    setP(undefined);
    if (!month) return;
    periodApi
      .detail(month)
      .then(setP)
      .catch((e) => message.error(readBizError(e).message));
  }, [month, message]);

  return (
    <Modal
      open={!!month}
      title={
        <>
          <LockOutlined style={{ color: palette.link, marginRight: 8 }} />
          结账 {month}
        </>
      }
      okText="确认结账"
      okButtonProps={{ loading: busy, disabled: !p, icon: <LockOutlined /> }}
      onCancel={onClose}
      onOk={async () => {
        if (!month) return;
        setBusy(true);
        try {
          await periodApi.close(month);
          message.success(`${month} 已结账`);
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      width={880}
      destroyOnHidden
    >
      {month && (
        <div style={{ color: palette.sub, fontSize: 13, marginBottom: 16 }}>
          结账后，业务日期在 {month}{' '}
          的单据不能再新建、作废或改日期；之后的成本变化记到 {nextMonth(month)}
        </div>
      )}
      {!p ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : (
        <>
          <PeriodTiles p={p} />
          {(p.reminders?.length ?? 0) > 0 && (
            <>
              <div style={{ fontWeight: 600, margin: '20px 0 8px' }}>
                结账前留意
              </div>
              {p.reminders?.map((r) => (
                <div
                  key={r}
                  style={{
                    display: 'flex',
                    gap: 8,
                    padding: '10px 12px',
                    borderRadius: 8,
                    background: palette.inset,
                    marginBottom: 8,
                    fontSize: 13,
                  }}
                >
                  <InfoCircleOutlined
                    style={{ color: palette.orange, marginTop: 3 }}
                  />
                  {r}
                </div>
              ))}
              <div style={{ color: palette.mute, fontSize: 12 }}>
                这些不影响结账；处理完再结账，月结快照会更准确
              </div>
            </>
          )}
        </>
      )}
    </Modal>
  );
};

/** 反结账（原型 P3）：只能反结最近一个已结账月份，原因必填 */
export const ReopenModal: React.FC<{
  month?: string;
  onClose: () => void;
  onDone: () => void;
}> = ({ month, onClose, onDone }) => {
  const { message } = App.useApp();
  // 业务凭证已导出时提示会计去 T+ 删除（spec finance/period-close「反结账已导出的月份」）
  const [exported, setExported] = useState(false);
  useEffect(() => {
    setExported(false);
    if (!month) return;
    periodApi
      .detail(month)
      .then((p) => setExported(!!p.voucherExported))
      .catch(() => setExported(false));
  }, [month]);
  return (
    <ReasonModal
      open={!!month}
      title={`反结账 ${month ?? ''}`}
      danger
      okText="确认反结账"
      placeholder="例如：补登 11-30 的出运单，当时漏登"
      description={
        exported ? (
          <>
            {month} 的凭证已导入 T+。反结账后这些凭证作废，请在 T+
            里删除它们，重新结账后再导入新凭证。
          </>
        ) : (
          <>
            反结账后 {month}{' '}
            的单据可以再改，结账快照与业务凭证作废，需要重新结账；月份、原因、操作人记入操作日志
          </>
        )
      }
      onCancel={onClose}
      onOk={async (reason) => {
        if (!month) return;
        try {
          await periodApi.reopen(month, reason);
          message.success(`${month} 已反结账`);
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      }}
    />
  );
};
