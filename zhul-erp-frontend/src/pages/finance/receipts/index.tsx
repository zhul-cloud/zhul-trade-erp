import { PlusOutlined } from '@ant-design/icons';
import { Badge, Button, Tabs } from 'antd';
import React, { useCallback, useState } from 'react';
import { PageTitle } from '@/pages/inquiry/shared/components';
import DeskTab from './DeskTab';
import RecordsTab from './RecordsTab';
import UnclaimedTab from './UnclaimedTab';

/** 财务管理 → 收款管理：待确认（水单）、未认领到账、收款记录（现阶段由总经理兼任财务） */
const ReceiptManagement: React.FC = () => {
  const [tab, setTab] = useState('desk');
  const [deskCount, setDeskCount] = useState<number>();
  const [unclaimedCount, setUnclaimedCount] = useState<number>();
  const [registerSignal, setRegisterSignal] = useState(0);
  const onDesk = useCallback((n: number) => setDeskCount(n), []);
  const onUnclaimed = useCallback((n: number) => setUnclaimedCount(n), []);

  const label = (text: string, n?: number) => (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      {text}
      {n ? <Badge count={n} color="orange" /> : null}
    </span>
  );

  return (
    <div>
      <PageTitle
        crumbs={['收款管理']}
        title="收款管理"
        description="客户付款的三条路：业务员上传水单后在「待确认」确认到账；钱先到账的在「未认领到账」登记、由业务员认领；平台收款由业务员直接登记。"
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setTab('unclaimed');
              setRegisterSignal((s) => s + 1);
            }}
          >
            登记未认领到账
          </Button>
        }
      />
      <Tabs
        activeKey={tab}
        onChange={setTab}
        destroyOnHidden={false}
        items={[
          {
            key: 'desk',
            label: label('待确认', deskCount),
            children: <DeskTab onTotal={onDesk} />,
          },
          {
            key: 'unclaimed',
            label: label('未认领到账', unclaimedCount),
            forceRender: true,
            children: (
              <UnclaimedTab
                registerSignal={registerSignal}
                onCount={onUnclaimed}
              />
            ),
          },
          {
            key: 'records',
            label: label('收款记录'),
            children: <RecordsTab />,
          },
        ]}
      />
    </div>
  );
};

export default ReceiptManagement;
