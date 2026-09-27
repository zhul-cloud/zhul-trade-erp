import { LeftOutlined, MailOutlined, UserOutlined } from '@ant-design/icons';
import { Helmet, history } from '@umijs/max';
import { Alert, Button, Form, Input } from 'antd';
import React, { useState } from 'react';
import { AuthLayout } from '@/components';
import { sendResetCode } from '@/services/zhul/auth';
import { useAppTheme } from '@/theme/AppTheme';
import StepsBar from '../components/StepsBar';

const Step1: React.FC = () => {
  const { palette: p } = useAppTheme();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (values: { username: string; email: string }) => {
    setLoading(true);
    setError(null);
    try {
      const res = await sendResetCode(values);
      if (res.code === 0) {
        sessionStorage.setItem('fpUsername', values.username);
        sessionStorage.setItem('fpEmail', values.email);
        history.push('/forget-password/step2');
      }
    } catch (err: any) {
      if (err?.name === 'BizError' && err?.info) {
        setError(err.info.errorMessage);
      } else {
        setError('服务器繁忙，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <Helmet>
        <title>忘记密码 - 烛龙ERP</title>
      </Helmet>
      <div style={{ position: 'relative', marginBottom: 16 }}>
        <a
          onClick={() => history.push('/login')}
          style={{ position: 'absolute', left: 0, top: 4, fontSize: 13 }}
        >
          <LeftOutlined /> 返回登录
        </a>
        <div
          style={{
            fontSize: 20,
            fontWeight: 600,
            textAlign: 'center',
            color: p.ink,
          }}
        >
          忘记密码
        </div>
      </div>

      <StepsBar current={0} />

      {error && (
        <Alert
          type="error"
          showIcon
          title={error}
          style={{ marginBottom: 16 }}
        />
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        disabled={loading}
      >
        <Form.Item
          name="username"
          label="用户名"
          rules={[{ required: true, message: '请输入用户名' }]}
        >
          <Input
            size="large"
            prefix={<UserOutlined />}
            placeholder="请输入用户名"
            maxLength={50}
            autoFocus
          />
        </Form.Item>
        <Form.Item
          name="email"
          label="绑定邮箱"
          rules={[
            { required: true, message: '请输入邮箱地址' },
            {
              pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
              message: '邮箱格式不正确',
            },
          ]}
        >
          <Input
            size="large"
            prefix={<MailOutlined />}
            placeholder="请输入注册时绑定的邮箱地址"
            maxLength={100}
          />
        </Form.Item>

        <Form.Item style={{ marginBottom: 8 }}>
          <Button
            type="primary"
            htmlType="submit"
            block
            size="large"
            loading={loading}
          >
            发送验证码
          </Button>
        </Form.Item>
      </Form>
      <div style={{ fontSize: 12, color: p.mute, textAlign: 'center' }}>
        验证码将发送到注册邮箱，有效期10分钟
      </div>
    </AuthLayout>
  );
};

export default Step1;
