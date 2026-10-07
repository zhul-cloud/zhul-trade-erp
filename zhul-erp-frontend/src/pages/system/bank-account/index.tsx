import {
  InfoCircleOutlined,
  NumberOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Form,
  Input,
  Modal,
  Select,
  Skeleton,
  Space,
  Table,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { CURRENCIES } from '@/pages/quotation/components';
import { useAppTheme } from '@/theme/AppTheme';
import {
  type BankAccount,
  bankAccountApi,
  prefixApi,
  readBizError,
  type SaveBankAccount,
} from './service';

const PREFIX_RULE = /^[A-Z]{2,4}$/;

// ---------------------------------------------------------------- 新增 / 编辑

const AccountModal: React.FC<{
  open: boolean;
  editing?: BankAccount;
  onClose: () => void;
  onSaved: () => void;
}> = ({ open, editing, onClose, onSaved }) => {
  const { message } = App.useApp();
  const [form] = Form.useForm<SaveBankAccount>();
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (!editing) {
      form.setFieldsValue({ currencyCode: 'USD' });
      return;
    }
    setLoading(true);
    bankAccountApi
      .get(editing.id)
      .then((a) => form.setFieldsValue(a))
      .catch((e) => message.error(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [open, editing, form, message]);

  const submit = async () => {
    const values = await form.validateFields();
    setBusy(true);
    try {
      if (editing) await bankAccountApi.update(editing.id, values);
      else await bankAccountApi.create(values);
      message.success(editing ? '收款账户已保存' : '收款账户已添加');
      onSaved();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={640}
      title={editing ? '编辑收款账户' : '新增收款账户'}
      okText="保存"
      confirmLoading={busy}
      onOk={submit}
    >
      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <Form form={form} layout="vertical" requiredMark="optional">
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
              columnGap: 16,
            }}
          >
            <Form.Item
              name="currencyCode"
              label="币种"
              rules={[{ required: true, message: '请选择币种' }]}
            >
              <Select
                options={CURRENCIES.map((c) => ({ value: c, label: c }))}
              />
            </Form.Item>
            <Form.Item name="swiftCode" label="SWIFT Code">
              <Input maxLength={11} placeholder="CHASSGSGXXX" />
            </Form.Item>
            <Form.Item
              name="bankName"
              label="银行名称"
              rules={[{ required: true, message: '请填写银行名称' }]}
              style={{ gridColumn: 'span 2' }}
            >
              <Input
                maxLength={128}
                placeholder="JPMorgan Chase Bank N.A., Singapore Branch"
              />
            </Form.Item>
            <Form.Item
              name="accountName"
              label="账户名称"
              rules={[{ required: true, message: '请填写账户名称' }]}
            >
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item
              name="accountNo"
              label="账号"
              rules={[
                { required: true, message: '请填写账号' },
                {
                  pattern: /^[A-Za-z0-9 -]{4,64}$/,
                  message: '账号只能包含字母、数字、空格与连字符，4–64 位',
                },
              ]}
            >
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="country" label="国家 / 地区">
              <Input maxLength={64} placeholder="Singapore" />
            </Form.Item>
            <Form.Item name="bankCode" label="Bank Code">
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item
              name="bankAddress"
              label="银行地址"
              style={{ gridColumn: 'span 2' }}
            >
              <Input maxLength={300} />
            </Form.Item>
            <Form.Item name="branchCode" label="Branch Code">
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item name="remark" label="备注">
              <Input maxLength={200} />
            </Form.Item>
          </div>
        </Form>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 单据前缀

const PrefixCard: React.FC<{ editable: boolean }> = ({ editable }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [saved, setSaved] = useState<string>();
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    prefixApi
      .get()
      .then((r) => {
        setSaved(r.prefix);
        setValue(r.prefix);
      })
      .catch(() => setSaved(''));
  }, []);

  const v = value.trim();
  const invalid = v !== '' && !PREFIX_RULE.test(v);
  const today = dayjs().format('YYYYMMDD');
  const shown = invalid ? (saved ?? '') : v;
  const samples: [string, boolean][] = [
    ['QT', true],
    ['PI', true],
    ['CI', true],
    ['SO', false],
    ['IQ', false],
  ];

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

  return (
    <Card style={{ padding: 20, marginTop: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <NumberOutlined style={{ color: palette.link }} />
        <b style={{ color: palette.ink }}>单据编号前缀（系统设置）</b>
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
          <div style={{ width: 220 }}>
            <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
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
              <div style={{ fontSize: 12, color: palette.red, marginTop: 4 }}>
                单据前缀只能是 2–4 位大写字母
              </div>
            )}
          </div>
          <div>
            <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
              预览
            </div>
            <Space size={8} wrap>
              {samples.map(([type, external]) => (
                <Pill key={type} tone={external ? 'accent' : 'gray'}>
                  {`${external ? shown : ''}${type}${today}001`}
                </Pill>
              ))}
            </Space>
          </div>
        </div>
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 12 }}>
        只加在对外单据（报价单、PI、CI、PL、采购单、借项 /
        贷项通知单、对账单）上；询盘、销售订单等内部单据不加。不含连字符等符号，避免客户系统不支持；修改前缀只影响之后新建的单据。
      </div>
    </Card>
  );
};

// ---------------------------------------------------------------- 页面

const BankAccountPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess();
  const canEdit = !!access['system:bank-account:edit'];
  const [rows, setRows] = useState<BankAccount[]>();
  const [error, setError] = useState<string>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<BankAccount>();

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setRows(await bankAccountApi.list());
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (fn: () => Promise<void>, done: string) => {
    try {
      await fn();
      message.success(done);
      load();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const missing = CURRENCIES.filter(
    (c) => !(rows ?? []).some((r) => r.currencyCode === c && r.enabled),
  );

  const columns: TableColumnsType<BankAccount> = [
    { title: '币种', dataIndex: 'currencyCode', width: 80 },
    {
      title: '银行 · 账户名称',
      key: 'bank',
      width: 340,
      render: (_, r) => (
        <div>
          <div style={{ fontWeight: 600, color: palette.ink }}>
            {r.bankName}
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {r.accountName}
          </div>
        </div>
      ),
    },
    {
      title: '账号',
      dataIndex: 'accountNoMasked',
      width: 120,
      render: (v: string) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>{v}</span>
      ),
    },
    {
      title: 'SWIFT',
      dataIndex: 'swiftCode',
      width: 140,
      render: (v?: string) => v || '—',
    },
    {
      title: '国家 / 地区',
      dataIndex: 'country',
      width: 130,
      render: (v?: string) => v || '—',
    },
    {
      title: '默认',
      key: 'default',
      width: 100,
      render: (_, r) =>
        r.isDefault ? (
          <Pill tone="accent">默认</Pill>
        ) : canEdit && r.enabled ? (
          <a
            onClick={() =>
              run(
                () => bankAccountApi.setDefault(r.id),
                `已设为 ${r.currencyCode} 默认账户`,
              )
            }
          >
            设为默认
          </a>
        ) : (
          '—'
        ),
    },
    {
      title: '状态',
      key: 'enabled',
      width: 90,
      render: (_, r) => (
        <Pill tone={r.enabled ? 'green' : 'mute'} dot>
          {r.enabled ? '启用' : '停用'}
        </Pill>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      hidden: !canEdit,
      render: (_, r) => (
        <Space size={12}>
          <a
            onClick={() => {
              setEditing(r);
              setModalOpen(true);
            }}
          >
            编辑
          </a>
          {r.enabled ? (
            <a
              style={{ color: palette.red }}
              onClick={() =>
                modal.confirm({
                  title: `停用 ${r.bankName}？`,
                  content:
                    '停用后开 PI 时不能再选这个账户，已发送的 PI 不受影响。',
                  okText: '停用',
                  okButtonProps: { danger: true },
                  onOk: () =>
                    run(() => bankAccountApi.setEnabled(r.id, false), '已停用'),
                })
              }
            >
              停用
            </a>
          ) : (
            <a
              onClick={() =>
                run(() => bankAccountApi.setEnabled(r.id, true), '已启用')
              }
            >
              启用
            </a>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageTitle
        root="系统管理"
        crumbs={['收款账户']}
        title="收款账户"
        description="显示在 PI 等对外单据上的收款银行信息；开 PI 时按币种带出默认账户。"
        actions={
          canEdit && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(undefined);
                setModalOpen(true);
              }}
            >
              新增账户
            </Button>
          )
        }
      />
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<BankAccount>
          rowKey="id"
          columns={columns}
          dataSource={rows ?? []}
          loading={!rows}
          pagination={false}
          scroll={{ x: 1120 }}
          locale={{
            emptyText: '还没有收款账户：添加后开 PI 时会按币种自动带出',
          }}
        />
      )}
      {rows && (
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            marginTop: 12,
            padding: '10px 14px',
            borderRadius: 12,
            background: palette.inset,
            fontSize: 12,
            color: palette.sub,
          }}
        >
          <InfoCircleOutlined style={{ color: palette.link }} />
          账号在列表与日志中只显示后 4 位，PI
          上显示完整账号。停用默认账户前要先把同币种的另一个账户设为默认。
          {missing.length > 0 &&
            `${missing.join('、')} 还没有启用的账户：这些币种的 PI 可以存草稿，发送时提示添加。`}
        </div>
      )}
      <PrefixCard editable={!!access['system:config:edit']} />
      <AccountModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false);
          load();
        }}
      />
    </div>
  );
};

export default BankAccountPage;
