import { InfoCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Skeleton,
  Space,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { CURRENCIES } from '@/pages/quotation/components';
import { useAppTheme } from '@/theme/AppTheme';
import { UnitPill, UnitSelect, useBusinessUnits } from '@/utils/businessUnit';
import {
  ACCOUNT_TYPE,
  type BankAccount,
  bankAccountApi,
  readBizError,
  type SaveBankAccount,
} from './service';

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
  const [bu] = useBusinessUnits();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (!editing) {
      form.setFieldsValue({
        currencyCode: 'USD',
        accountType: ACCOUNT_TYPE.PUBLIC,
        businessUnitId: bu.myUnitId ?? bu.units[0]?.id,
      });
      return;
    }
    setLoading(true);
    bankAccountApi
      .get(editing.id)
      .then((a) => form.setFieldsValue(a))
      .catch((e) => message.error(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [open, editing, form, message, bu]);

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
              name="accountType"
              label="账户类型"
              rules={[{ required: true, message: '请选择账户类型' }]}
              extra="对私账户（如法人个人账户）用于私对私付款，不能设为默认"
              style={{ gridColumn: '1 / -1' }}
            >
              <Radio.Group
                optionType="button"
                buttonStyle="solid"
                options={[
                  { value: ACCOUNT_TYPE.PUBLIC, label: '对公账户' },
                  { value: ACCOUNT_TYPE.PRIVATE, label: '对私账户' },
                ]}
              />
            </Form.Item>
            {bu.enabled && (
              <Form.Item
                name="businessUnitId"
                label="所属事业部"
                rules={[{ required: true, message: '请选择所属事业部' }]}
                extra="默认账户按事业部和币种各一个；开 PI 时带出本事业部的账户"
                style={{ gridColumn: '1 / -1' }}
              >
                <UnitSelect info={bu} style={{ width: '100%' }} />
              </Form.Item>
            )}
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
  const [bu] = useBusinessUnits();

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
  // 有事业部时：其他事业部已有、本事业部还没有的币种
  const unitMissing = bu.enabled
    ? bu.units
        .map((u) => ({
          name: u.name,
          list: CURRENCIES.filter(
            (c) =>
              (rows ?? []).some((r) => r.currencyCode === c && r.enabled) &&
              !(rows ?? []).some(
                (r) =>
                  r.currencyCode === c &&
                  r.enabled &&
                  r.businessUnitId === u.id,
              ),
          ),
        }))
        .filter((x) => x.list.length > 0)
    : [];
  const sorted = bu.enabled
    ? [...(rows ?? [])].sort(
        (a, b) =>
          bu.units.findIndex((u) => u.id === a.businessUnitId) -
          bu.units.findIndex((u) => u.id === b.businessUnitId),
      )
    : (rows ?? []);

  const columns: TableColumnsType<BankAccount> = [
    ...(bu.enabled
      ? [
          {
            title: '所属事业部',
            key: 'unit',
            width: 180,
            render: (_: unknown, r: BankAccount) => (
              <UnitPill info={bu} id={r.businessUnitId} />
            ),
          },
        ]
      : []),
    {
      title: '类型',
      dataIndex: 'accountType',
      width: 90,
      render: (v: number) =>
        v === ACCOUNT_TYPE.PRIVATE ? (
          <Pill tone="orange">对私</Pill>
        ) : (
          <Pill tone="gray">对公</Pill>
        ),
    },
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
        ) : canEdit && r.enabled && r.accountType !== ACCOUNT_TYPE.PRIVATE ? (
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
        description={
          bu.enabled
            ? '显示在 PI 等对外单据上的收款银行信息；开 PI 时按事业部和币种带出默认账户，只能选本事业部的账户。'
            : '显示在 PI 等对外单据上的收款银行信息；开 PI 时按币种带出默认账户。'
        }
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
          dataSource={sorted}
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
          上显示完整账号。停用默认账户前要先把同币种的另一个账户设为默认。单据前缀在「业务设置
          → 编号规则」设置。
          {missing.length > 0 &&
            `${missing.join('、')} 还没有启用的账户：这些币种的 PI 可以存草稿，发送时提示添加。`}
          {unitMissing.map(
            (x) =>
              ` ${x.name}还没有 ${x.list.join('、')} 账户，开这些币种的 PI 时请先添加。`,
          )}
        </div>
      )}
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
