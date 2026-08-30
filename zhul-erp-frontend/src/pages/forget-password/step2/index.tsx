import { LeftOutlined } from '@ant-design/icons';
import { Helmet, history } from '@umijs/max';
import { Alert, Button, Input } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { AuthLayout } from '@/components';
import { sendResetCode, verifyResetCode } from '@/services/zhul/auth';
import StepsBar from '../components/StepsBar';

const RESEND_SECONDS = 60;

const Step2: React.FC = () => {
  const username = sessionStorage.getItem('fpUsername');
  const email = sessionStorage.getItem('fpEmail');

  const [code, setCode] = useState('');
  const [shake, setShake] = useState(false);
  const [errorText, setErrorText] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(RESEND_SECONDS);
  const [verifying, setVerifying] = useState(false);
  const [resending, setResending] = useState(false);
  const otpRef = useRef<any>(null);

  useEffect(() => {
    if (!username) {
      history.replace('/forget-password/step1');
    }
  }, [username]);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((c) => (c > 0 ? c - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [countdown]);

  const doVerify = async (value: string) => {
    if (!username || verifying) return;
    setVerifying(true);
    setErrorText(null);
    try {
      const res = await verifyResetCode({ username, code: value });
      if (res.code === 0 && res.data) {
        sessionStorage.setItem('fpVerifyToken', res.data.verifyToken);
        history.push('/forget-password/step3');
        return;
      }
    } catch (err: any) {
      if (err?.name === 'BizError' && err?.info) {
        const { errorCode, errorMessage } = err.info;
        setCode('');
        setShake(true);
        setTimeout(() => setShake(false), 500);
        otpRef.current?.focus();
        if (errorCode === 1012) {
          // 验证码已过期：忽略倒计时，立即可重发
          setCountdown(0);
        }
        setErrorText(errorMessage);
      } else {
        setErrorText('服务器繁忙，请稍后重试');
      }
    } finally {
      setVerifying(false);
    }
  };

  const handleChange = (value: string) => {
    setCode(value);
    if (value.length === 6) {
      doVerify(value);
    }
  };

  const handleResend = async () => {
    if (!username || !email || countdown > 0) return;
    setResending(true);
    try {
      await sendResetCode({ username, email });
      setCountdown(RESEND_SECONDS);
      setCode('');
      setErrorText(null);
    } finally {
      setResending(false);
    }
  };

  return (
    <AuthLayout>
      <Helmet>
        <title>忘记密码 - 烛龙ERP</title>
      </Helmet>
      <style>{`
        @keyframes fp-shake {
          10%, 90% { transform: translateX(-2px); }
          20%, 80% { transform: translateX(4px); }
          30%, 50%, 70% { transform: translateX(-8px); }
          40%, 60% { transform: translateX(8px); }
        }
        .fp-otp-shake { animation: fp-shake 0.5s; }
      `}</style>
      <div style={{ position: 'relative', marginBottom: 16 }}>
        <a
          onClick={() => {
            sessionStorage.removeItem('fpUsername');
            sessionStorage.removeItem('fpEmail');
            history.push('/forget-password/step1');
          }}
          style={{ position: 'absolute', left: 0, top: 4, fontSize: 13 }}
        >
          <LeftOutlined /> 上一步
        </a>
        <div
          style={{
            fontSize: 20,
            fontWeight: 600,
            textAlign: 'center',
            color: '#262626',
          }}
        >
          忘记密码
        </div>
      </div>

      <StepsBar current={1} />

      <div
        style={{
          fontSize: 12,
          color: '#8c8c8c',
          textAlign: 'center',
          marginBottom: 16,
        }}
      >
        验证码已发送至您绑定的邮箱
      </div>

      <div
        className={shake ? 'fp-otp-shake' : undefined}
        style={{ display: 'flex', justifyContent: 'center', marginBottom: 16 }}
      >
        <Input.OTP
          ref={otpRef}
          length={6}
          value={code}
          onChange={handleChange}
          size="large"
          status={errorText ? 'error' : undefined}
          disabled={verifying}
          autoFocus
        />
      </div>

      {errorText && (
        <div
          style={{
            color: '#ff4d4f',
            fontSize: 13,
            textAlign: 'center',
            marginBottom: 8,
          }}
        >
          {errorText}
        </div>
      )}

      <div style={{ textAlign: 'right', marginBottom: 24 }}>
        {countdown > 0 ? (
          <span
            style={{ color: '#bfbfbf', fontSize: 13, cursor: 'not-allowed' }}
          >
            重新发送验证码（{countdown}秒后可重发）
          </span>
        ) : (
          <a onClick={handleResend} style={{ fontSize: 13 }}>
            {resending ? '发送中...' : '重新发送验证码'}
          </a>
        )}
      </div>

      <Button
        type="primary"
        block
        size="large"
        disabled={code.length < 6}
        loading={verifying}
        onClick={() => doVerify(code)}
      >
        下一步
      </Button>

      {!email && (
        <Alert
          type="warning"
          showIcon
          title="缺少邮箱信息，如需重新发送验证码请返回上一步"
          style={{ marginTop: 16 }}
        />
      )}
    </AuthLayout>
  );
};

export default Step2;
