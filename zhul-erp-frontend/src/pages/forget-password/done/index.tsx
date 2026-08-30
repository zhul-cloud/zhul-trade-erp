import { CheckCircleFilled } from '@ant-design/icons';
import { Helmet, history } from '@umijs/max';
import { Button } from 'antd';
import React, { useEffect, useState } from 'react';
import { AuthLayout } from '@/components';

const Done: React.FC = () => {
  const [countdown, setCountdown] = useState(3);

  useEffect(() => {
    // 禁止浏览器前进/后退回到设置密码页
    history.replace('/forget-password/done');
  }, []);

  useEffect(() => {
    if (countdown <= 0) {
      history.replace('/login');
      return;
    }
    const timer = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  return (
    <AuthLayout>
      <Helmet>
        <title>密码重置成功 - 烛龙ERP</title>
      </Helmet>
      <div style={{ textAlign: 'center' }}>
        <CheckCircleFilled style={{ fontSize: 72, color: '#52c41a' }} />
        <div
          style={{
            fontSize: 20,
            fontWeight: 600,
            color: '#262626',
            marginTop: 20,
          }}
        >
          密码重置成功
        </div>
        <div style={{ fontSize: 14, color: '#595959', marginTop: 12 }}>
          您的密码已重置，请使用新密码重新登录
        </div>
        <div
          style={{
            fontSize: 12,
            color: '#8c8c8c',
            marginTop: 8,
            marginBottom: 24,
          }}
        >
          {countdown} 秒后自动跳转至登录页...
        </div>
        <Button
          type="primary"
          block
          size="large"
          onClick={() => history.replace('/login')}
        >
          立即去登录
        </Button>
      </div>
    </AuthLayout>
  );
};

export default Done;
