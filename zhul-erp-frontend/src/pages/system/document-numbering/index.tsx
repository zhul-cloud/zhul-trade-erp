import { NumberOutlined, OrderedListOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Alert, App, Button, Input, Skeleton, Space, Table } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { prefixApi, readBizError } from './service';

const PREFIX_RULE = /^[A-Z]{2,4}$/;

interface RuleRow {
  name: string;
  code: string;
  external: boolean;
  example?: string;
}

/** 编号规则：前缀 + 类型代码 + 年月日 + 当日流水；只有对外单据带前缀 */
const RULES = (prefix: string, today: string): RuleRow[] => [
  {
    name: '报价单',
    code: 'QT',
    external: true,
    example: `${prefix}QT${today}001`,
  },
  { name: 'PI', code: 'PI', external: true, example: `${prefix}PI${today}001` },
  { name: '销售订单', code: 'SO', external: false, example: `SO${today}001` },
  { name: '客户询盘', code: 'IQ', external: false, example: `IQ${today}001` },
  {
    name: '采购单、CI / PL、借贷项通知单、对账单',
    code: 'PO · CI · PL · DN · CN · ST',
    external: true,
  },
];

/** 业务设置 → 单据编号：对外单据的编号前缀与编号规则；修改需要「编辑单据编号」 */
const DocumentNumberingPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const editable = !!access['system:document-numbering:edit'];
  const [saved, setSaved] = useState<string>();
  const [value, setValue] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    prefixApi
      .get()
      .then((r) => {
        setSaved(r.prefix);
        setValue(r.prefix);
      })
      .catch((e) => setError(readBizError(e).message));
  }, []);

  const v = value.trim();
  const invalid = v !== '' && !PREFIX_RULE.test(v);
  const shown = invalid ? (saved ?? '') : v;
  const today = dayjs().format('YYYYMMDD');

  const save = async () => {
    setBusy(true);
    try {
      const r = await prefixApi.save(v);
      setSaved(r.prefix);
      setValue(r.prefix);
      message.success(
        r.prefix ? `单据前缀已改为 ${r.prefix}` : '已去掉单据前缀',
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const columns: TableColumnsType<RuleRow> = [
    { title: '单据', dataIndex: 'name' },
    { title: '类型代码', dataIndex: 'code', width: 200 },
    {
      title: '带前缀',
      dataIndex: 'external',
      width: 100,
      render: (x: boolean) => (
        <Pill tone={x ? 'accent' : 'gray'}>{x ? '是' : '否'}</Pill>
      ),
    },
    {
      title: '示例',
      dataIndex: 'example',
      width: 220,
      render: (x?: string) =>
        x ? (
          <span style={{ fontVariantNumeric: 'tabular-nums' }}>{x}</span>
        ) : (
          <span style={{ color: palette.mute }}>随各模块上线</span>
        ),
    },
  ];

  const sectionTitle = (icon: React.ReactNode, text: string) => (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <span style={{ color: palette.link }}>{icon}</span>
      <b style={{ color: palette.ink }}>{text}</b>
    </div>
  );

  return (
    <div>
      <PageTitle
        crumbs={['单据编号']}
        title="单据编号"
        description="对外单据的编号前缀与编号规则。"
      />
      {error ? (
        <Alert type="error" showIcon title={error} />
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          <Card style={{ padding: 20 }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 14,
              }}
            >
              {sectionTitle(<NumberOutlined />, '单据前缀')}
              {!editable && <Pill tone="gray">只读</Pill>}
            </div>
            {saved === undefined ? (
              <Skeleton active paragraph={{ rows: 2 }} />
            ) : (
              <div
                style={{
                  display: 'flex',
                  gap: 24,
                  flexWrap: 'wrap',
                  alignItems: 'flex-start',
                }}
              >
                <div style={{ width: 240 }}>
                  <div
                    style={{
                      fontSize: 13,
                      color: palette.sub,
                      marginBottom: 6,
                    }}
                  >
                    前缀
                  </div>
                  <Space.Compact style={{ width: '100%' }}>
                    <Input
                      value={value}
                      maxLength={4}
                      disabled={!editable}
                      placeholder="如 FW，可留空"
                      status={invalid ? 'error' : undefined}
                      onChange={(e) => setValue(e.target.value.toUpperCase())}
                      aria-label="单据前缀"
                    />
                    {editable && (
                      <Button
                        type="primary"
                        loading={busy}
                        disabled={invalid || v === saved}
                        onClick={save}
                      >
                        保存
                      </Button>
                    )}
                  </Space.Compact>
                  {invalid && (
                    <div
                      style={{ fontSize: 12, color: palette.red, marginTop: 4 }}
                    >
                      单据前缀只能是 2–4 位大写字母
                    </div>
                  )}
                </div>
                <div>
                  <div
                    style={{
                      fontSize: 13,
                      color: palette.sub,
                      marginBottom: 6,
                    }}
                  >
                    今天的编号预览
                  </div>
                  <Space size={8} wrap>
                    {(['QT', 'PI', 'CI'] as const).map((t) => (
                      <Pill
                        key={t}
                        tone="accent"
                      >{`${shown}${t}${today}001`}</Pill>
                    ))}
                    {(['SO', 'IQ'] as const).map((t) => (
                      <Pill key={t} tone="gray">{`${t}${today}001`}</Pill>
                    ))}
                  </Space>
                </div>
              </div>
            )}
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 14 }}>
              2–4
              位大写字母，可以留空，不含连字符等符号（有些客户系统不支持）。只加在对外单据：报价单、PI、CI、PL、采购单、借项
              /
              贷项通知单、对账单；询盘、销售订单、发货批次等内部单据不加。修改前缀只影响之后新建的单据。
            </div>
            {!editable && (
              <Alert
                type="info"
                showIcon
                style={{ marginTop: 12 }}
                title="你没有「编辑单据编号」权限，只能查看。"
              />
            )}
          </Card>
          <Card style={{ padding: 20 }}>
            <div style={{ marginBottom: 14 }}>
              {sectionTitle(<OrderedListOutlined />, '编号规则')}
            </div>
            <Table<RuleRow>
              rowKey="name"
              size="middle"
              columns={columns}
              dataSource={RULES(saved ?? '', today)}
              pagination={false}
            />
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 12 }}>
              前缀 + 类型代码 + 年月日 + 当日 3 位流水（超过 999 自动变 4
              位）；同一发货批次的 CI 与 PL 共用编号主体。
            </div>
          </Card>
        </div>
      )}
    </div>
  );
};

export default DocumentNumberingPage;
