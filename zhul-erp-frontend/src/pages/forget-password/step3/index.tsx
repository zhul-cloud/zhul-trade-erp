import { LockOutlined } from '@ant-design/icons';
import { Helmet, history } from '@umijs/max';
import { Alert, Button, Form, Input, Progress } from 'antd';
import React, { useEffect, useState } from 'react';
import { AuthLayout } from '@/components';
import { resetPassword } from '@/services/zhul/auth';
import StepsBar from '../components/StepsBar';

interface Strength {
  percent: number;
  color: string;
  label: string;
}

function evaluateStrength(pw: string): Strength {
  if (!pw) return { percent: 0, color: '#f0f0f0', label: '' };
  const len = pw.length;
  const hasLetter = /[A-Za-z]/.test(pw);
  const hasDigit = /[0-9]/.test(pw);
  const hasSpecial = /[^A-Za-z0-9]/.test(pw);
  const isPureDigit = /^\d+$/.test(pw);
  const isPureLetter = /^[A-Za-z]+$/.test(pw);

  if (isPureDigit || isPureLetter || len < 8) {
    return { percent: 33, color: '#ff4d4f', label: '弱' };
  }
  if ((hasLetter && hasDigit && len >= 12) || (hasSpecial && len >= 8)) {
    return { percent: 100, color: '#52c41a', label: '强' };
  }
  if (hasLetter && hasDigit) {
    return { percent: 66, color: '#faad14', label: '一般' };
  }
  return { percent: 33, color: '#ff4d4f', label: '弱' };
}

const Step3: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [expiredError, setExpiredError] = useState(false);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [strength, setStrength] = useState<Strength>({
    percent: 0,
    color: '#f0f0f0',
    label: '',
  });

  const verifyToken = sessionStorage.getItem('fpVerifyToken');

  useEffect(() => {
    if (!verifyToken) {
      history.replace('/forget-password/step1');
    }
  }, [verifyToken]);

  const handleSubmit = async (values: {
    newPassword: string;
    confirmPassword: string;
  }) => {
    if (!verifyToken) return;
    setLoading(true);
    setExpiredError(false);
    setFieldError(null);
    try {
      const res = await resetPassword({
        verifyToken,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword,
      });
      if (res.code === 0) {
        sessionStorage.removeItem('fpUsername');
        sessionStorage.removeItem('fpEmail');
        sessionStorage.removeItem('fpVerifyToken');
        history.replace('/forget-password/done');
      }
    } catch (err: any) {
      if (err?.name === 'BizError' && err?.info) {
        const { errorCode, errorMessage } = err.info;
        if (errorCode === 1012) {
          setExpiredError(true);
        } else {
          setFieldError(errorMessage);
        }
      } else {
        setFieldError('服务器繁忙，请稍后重试');
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
      <div
        style={{
          fontSize: 20,
          fontWeight: 600,
          textAlign: 'center',
          color: '#262626',
          marginBottom: 16,
        }}
      >
        忘记密码
      </div>

      <StepsBar current={2} />

      {expiredError && (
        <Alert
          type="error"
          showIcon
          title="重置凭证已失效，请重新发起找回密码"
          style={{ marginBottom: 16 }}
          action={
            <a
              onClick={() => {
                sessionStorage.removeItem('fpUsername');
                sessionStorage.removeItem('fpEmail');
                sessionStorage.removeItem('fpVerifyToken');
                history.push('/forget-password/step1');
              }}
            >
              重新发起
            </a>
          }
        />
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        disabled={loading}
      >
        <Form.Item
          name="newPassword"
          label="新密码"
          validateStatus={fieldError ? 'error' : undefined}
          help={fieldError || undefined}
          rules={[
            { required: true, message: '请输入新密码' },
            { min: 8, max: 32, message: '密码长度需为8-32个字符' },
            {
              pattern: /^(?=.*[A-Za-z])(?=.*\d).*$/,
              message: '密码需同时包含字母和数字',
            },
          ]}
        >
          <Input.Password
            size="large"
            prefix={<LockOutlined />}
            placeholder="请输入新密码（8-32位，含字母和数字）"
            maxLength={32}
            autoFocus
            onChange={(e) => {
              setStrength(evaluateStrength(e.target.value));
              setFieldError(null);
            }}
          />
        </Form.Item>

        {strength.label && (
          <div
            style={{
              marginTop: -16,
              marginBottom: 16,
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <Progress
              percent={strength.percent}
              showInfo={false}
              strokeColor={strength.color}
              size="small"
              style={{ flex: 1 }}
            />
            <span style={{ fontSize: 12, color: strength.color }}>
              {strength.label}
            </span>
          </div>
        )}

        <Form.Item
          name="confirmPassword"
          label="确认新密码"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('两次输入的密码不一致'));
              },
            }),
          ]}
        >
          <Input.Password
            size="large"
            prefix={<LockOutlined />}
            placeholder="请再次输入新密码"
            maxLength={32}
          />
        </Form.Item>

        <Form.Item style={{ marginBottom: 0 }}>
          <Button
            type="primary"
            htmlType="submit"
            block
            size="large"
            loading={loading}
          >
            确认重置
          </Button>
        </Form.Item>
      </Form>
    </AuthLayout>
  );
};

export default Step3;
