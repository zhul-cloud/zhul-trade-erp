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

/** 三个汇总：期末库存金额、本月销售成本、本月成本调整 */
export const PeriodTiles: React.FC<{ p: Period; tiles?: 3 | 5 }> = ({
  p,
  tiles = 3,
}) => {
  const { palette } = useAppTheme();
  const items: [string, React.ReactNode][] = [
    ...(tiles === 5
      ? ([
          ['期初库存金额', formatAmount(p.openingAmount ?? 0, 'CNY')],
          ['本月入库', formatAmount(p.inAmount ?? 0, 'CNY')],
        ] as [string, React.ReactNode][])
      : []),
    ['本月销售成本', formatAmount(p.cogsAmount, 'CNY')],
    ['本月成本调整', <SignedMoney key="a" value={p.adjustAmount} yuan />],
    ['期末库存金额', formatAmount(p.closingAmount, 'CNY')],
  ];
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(auto-fit, minmax(${tiles === 5 ? 160 : 140}px, 1fr))`,
        gap: 12,
      }}
    >
      {items.map(([label, value]) => (
        <div
          key={label}
          style={{
            border: `1px solid ${palette.hairline}`,
            borderRadius: 12,
            padding: '12px 16px',
          }}
        >
          <div style={{ fontSize: 12, color: palette.sub }}>{label}</div>
          <div
            style={{
              fontSize: 20,
              fontWeight: 700,
              color: palette.ink,
              marginTop: 4,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {value}
          </div>
        </div>
      ))}
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
      width={600}
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
  return (
    <ReasonModal
      open={!!month}
      title={`反结账 ${month ?? ''}`}
      danger
      okText="确认反结账"
      placeholder="例如：补登 11-30 的出运单，当时漏登"
      description={
        <>
          反结账后 {month}{' '}
          的单据可以再改，结账快照作废，需要重新结账；月份、原因、操作人记入操作日志
        </>
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
