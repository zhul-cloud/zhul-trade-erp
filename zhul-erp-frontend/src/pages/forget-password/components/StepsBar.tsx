import { Steps } from 'antd';
import React from 'react';

const StepsBar: React.FC<{ current: number }> = ({ current }) => (
  <Steps
    size="small"
    current={current}
    items={[
      { title: '验证账号' },
      { title: '验证邮箱' },
      { title: '设置密码' },
    ]}
    style={{ marginBottom: 24 }}
  />
);

export default StepsBar;
