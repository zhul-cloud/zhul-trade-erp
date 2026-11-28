import { Link, useIntl } from '@umijs/max';
import { Button, Card, Result } from 'antd';
import React from 'react';
import { usePageTitle } from '@/components/PageTabs/hooks';

const Exception404: React.FC = () => {
  const intl = useIntl();
  // 不在菜单里的地址：标签名写「页面不存在」，不落到返回条的兜底文案「上一页」
  usePageTitle('页面不存在');
  return (
    <Card variant="borderless">
      <Result
        status="404"
        title="404"
        subTitle={intl.formatMessage({ id: 'pages.404.subTitle' })}
        extra={
          <Link to="/" prefetch>
            <Button type="primary">
              {intl.formatMessage({ id: 'pages.404.buttonText' })}
            </Button>
          </Link>
        }
      />
    </Card>
  );
};

export default Exception404;
