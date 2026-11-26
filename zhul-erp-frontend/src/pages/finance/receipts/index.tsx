import { Button } from 'antd';
import { Plus } from 'lucide-react';
import React, { useCallback, useState } from 'react';
import { PageHeader, ViewTabs } from '@/components/ListLayout';
import DeskTab from './DeskTab';
import OverpaidTab from './OverpaidTab';
import RecordsTab from './RecordsTab';
import UnclaimedTab from './UnclaimedTab';

/** 财务管理 → 收款单：待确认（水单）、未认领到账、收款记录（现阶段由总经理兼任财务） */
const ReceiptManagement: React.FC = () => {
  // 订单详情「去处理」带 ?tab=overpaid 进来
  const [tab, setTab] = useState(
    () => new URLSearchParams(window.location.search).get('tab') ?? 'desk',
  );
  const [overpaidCount, setOverpaidCount] = useState<number>();
  const onOverpaid = useCallback((n: number) => setOverpaidCount(n), []);
  const [deskCount, setDeskCount] = useState<number>();
  const [unclaimedCount, setUnclaimedCount] = useState<number>();
  const [registerSignal, setRegisterSignal] = useState(0);
  const onDesk = useCallback((n: number) => setDeskCount(n), []);
  const onUnclaimed = useCallback((n: number) => setUnclaimedCount(n), []);

  return (
    <div>
      <PageHeader
        help="客户付款的三条路：业务员上传水单后在「待确认」确认到账；钱先到账的在「未认领到账」登记、由业务员认领；平台收款由业务员直接登记"
        extra={
          <Button
            type="primary"
            icon={<Plus size="1em" />}
            onClick={() => {
              setTab('unclaimed');
              setRegisterSignal((s) => s + 1);
            }}
          >
            登记未认领到账
          </Button>
        }
      />
      <ViewTabs
        activeKey={tab}
        onChange={setTab}
        destroyOnHidden={false}
        items={[
          {
            key: 'desk',
            label: '待确认',
            count: deskCount,
            children: <DeskTab onTotal={onDesk} />,
          },
          {
            key: 'unclaimed',
            label: '未认领到账',
            count: unclaimedCount,
            forceRender: true,
            children: (
              <UnclaimedTab
                registerSignal={registerSignal}
                onCount={onUnclaimed}
              />
            ),
          },
          {
            key: 'overpaid',
            label: '待处理多收',
            count: overpaidCount,
            forceRender: true,
            children: <OverpaidTab onCount={onOverpaid} />,
          },
          {
            key: 'records',
            label: '收款单',
            children: <RecordsTab />,
          },
        ]}
      />
    </div>
  );
};

export default ReceiptManagement;
