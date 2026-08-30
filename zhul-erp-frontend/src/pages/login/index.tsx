import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Helmet, history, useModel } from '@umijs/max';
import { Alert, App, Button, Checkbox, Form, Input } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { AuthLayout } from '@/components';
import { login } from '@/services/zhul/auth';
import { setToken } from '@/utils/tokenStore';

interface LoginErrorState {
  type: 'error' | 'warning';
  message: string;
}

const getSafeRedirectUrl = (redirect: string | null): string => {
  if (!redirect?.startsWith('/')) return '/dashboard';
  if (redirect.startsWith('//')) return '/dashboard';
  try {
    const parsed = new URL(redirect, window.location.origin);
    if (parsed.origin !== window.location.origin) return '/dashboard';
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return '/dashboard';
  }
};

const LoginPage: React.FC = () => {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const { setInitialState } = useModel('@@initialState');
  const usernameRef = useRef<any>(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<LoginErrorState | null>(null);
  const [expiredTip, setExpiredTip] = useState(false);

  useEffect(() => {
    usernameRef.current?.focus();
    const params = new URL(window.location.href).searchParams;
    if (params.get('reason') === 'token_expired') {
      setExpiredTip(true);
    }
    // reason=logout 静默，不展示提示
  }, []);

  const handleSubmit = async (values: {
    username: string;
    password: string;
    rememberMe?: boolean;
  }) => {
    setLoading(true);
    setError(null);
    try {
      const res = await login({
        username: values.username,
        password: values.password,
        rememberMe: values.rememberMe,
      });

      if (res.code === 0 && res.data) {
        setToken(res.data.accessToken, !!values.rememberMe);
        localStorage.setItem(
          'zhul_user',
          JSON.stringify({
            name: res.data.nickname || res.data.username,
            avatar: res.data.avatarUrl || '',
            userid: res.data.username,
            access: res.data.isAdmin ? 'admin' : 'user',
          }),
        );

        message.success('登录成功！');
        await setInitialState((s) => ({ ...s, currentUser: undefined }));

        const urlParams = new URL(window.location.href).searchParams;
        const redirectUrl = getSafeRedirectUrl(urlParams.get('redirect'));
        window.location.href = redirectUrl;
        return;
      }
      message.error(res.message || '登录失败，请重试');
    } catch (err: any) {
      if (err?.name === 'BizError' && err?.info) {
        const { errorCode, errorMessage } = err.info;
        setError({
          type: errorCode === 1010 ? 'warning' : 'error',
          message: errorMessage,
        });
        // 登录失败时密码保留，焦点移至密码框
        form.setFieldValue('password', values.password);
      } else if (typeof navigator !== 'undefined' && !navigator.onLine) {
        message.error('网络不可用，请检查网络连接', 5);
      } else {
        message.error('服务器繁忙，请稍后重试', 3);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <Helmet>
        <title>登录 - 烛龙ERP</title>
      </Helmet>
      <div
        style={{
          fontSize: 24,
          fontWeight: 600,
          color: '#262626',
          textAlign: 'center',
          marginBottom: 24,
        }}
      >
        欢迎登录
      </div>

      {expiredTip && (
        <Alert
          type="info"
          showIcon
          closable
          title="登录状态已过期，请重新登录"
          style={{ marginBottom: 16 }}
          onClose={() => setExpiredTip(false)}
        />
      )}
      {error && (
        <Alert
          type={error.type}
          showIcon
          title={error.message}
          style={{ marginBottom: 16 }}
        />
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        initialValues={{ rememberMe: false }}
        disabled={loading}
      >
        <Form.Item
          name="username"
          label="用户名"
          rules={[
            { required: true, message: '请输入用户名' },
            { min: 4, max: 50, message: '用户名长度为4-50个字符' },
          ]}
        >
          <Input
            ref={usernameRef}
            size="large"
            prefix={<UserOutlined />}
            placeholder="请输入用户名"
            maxLength={50}
            autoComplete="username"
          />
        </Form.Item>

        <Form.Item
          name="password"
          label="密码"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 6, max: 32, message: '密码长度为6-32个字符' },
          ]}
        >
          <Input.Password
            size="large"
            prefix={<LockOutlined />}
            placeholder="请输入密码"
            maxLength={32}
            autoComplete="current-password"
          />
        </Form.Item>

        <Form.Item style={{ marginBottom: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Form.Item name="rememberMe" valuePropName="checked" noStyle>
              <Checkbox>记住我（7天）</Checkbox>
            </Form.Item>
            <a onClick={() => history.push('/forget-password/step1')}>
              忘记密码?
            </a>
          </div>
        </Form.Item>

        <Form.Item style={{ marginBottom: 0 }}>
          <Button
            type="primary"
            htmlType="submit"
            block
            size="large"
            loading={loading}
          >
            {loading ? '登录中...' : '登  录'}
          </Button>
        </Form.Item>
      </Form>
    </AuthLayout>
  );
};

export default LoginPage;
