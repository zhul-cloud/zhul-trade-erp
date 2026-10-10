import { BankOutlined, PlusOutlined, UserOutlined } from '@ant-design/icons';
import { App, Button, Checkbox, Form, Input, Modal, Segmented } from 'antd';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import {
  ACCOUNT_CORPORATE,
  ACCOUNT_NO_PATTERN,
  ACCOUNT_PERSONAL,
  ID_NO_PATTERN,
  MAX_ACCOUNTS,
  PHONE_PATTERN,
} from './constants';
import type { SupplierAccount, SupplierFormValues } from './service';

/** 表单里的一行账户：payeeIdNo 只在本次重新填写时有值，否则沿用 payeeIdNoMasked 对应的原值 */
export interface AccountRow {
  key: string;
  id?: number;
  accountType: number;
  accountName: string;
  bankName: string;
  accountNo: string;
  payeePhone: string;
  payeeIdNo?: string;
  payeeIdNoMasked: string;
  defaultAccount: boolean;
}

let seq = 0;
const newKey = () => `new-${Date.now()}-${seq++}`;

export const toAccountRows = (accounts?: SupplierAccount[]): AccountRow[] =>
  (accounts ?? []).map((a) => ({
    key: `id-${a.id}`,
    id: a.id,
    accountType: a.accountType,
    accountName: a.accountName,
    bankName: a.bankName,
    accountNo: a.accountNo,
    payeePhone: a.payeePhone,
    payeeIdNoMasked: a.payeeIdNoMasked,
    defaultAccount: a.defaultAccount,
  }));

export const toAccountPayload = (
  rows?: AccountRow[],
): SupplierFormValues['accounts'] =>
  (rows ?? []).map((r) => {
    const personal = r.accountType === ACCOUNT_PERSONAL;
    return {
      id: r.id,
      accountType: r.accountType,
      accountName: r.accountName,
      bankName: r.bankName,
      accountNo: r.accountNo,
      payeePhone: personal ? r.payeePhone : '',
      // 不传表示沿用原值；新账户没填则传空串
      payeeIdNo: personal ? (r.payeeIdNo ?? (r.id ? undefined : '')) : '',
      defaultAccount: r.defaultAccount,
    };
  });

/** 与后端一致：保留前 6 位和后 4 位 */
const maskIdNo = (idNo: string) =>
  `${idNo.slice(0, 6)}${'*'.repeat(idNo.length - 10)}${idNo.slice(-4)}`;

/** 4 位一组展示账号，便于核对 */
const groupDigits = (no: string) =>
  no.includes('*') ? no : no.replace(/(\d{4})(?=\d)/g, '$1 ');

const TypePill: React.FC<{ type: number }> = ({ type }) => {
  const { palette } = useAppTheme();
  const corporate = type === ACCOUNT_CORPORATE;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 22,
        padding: '0 8px',
        borderRadius: 11,
        fontSize: 12,
        fontWeight: 600,
        color: corporate ? palette.link : palette.orange,
        background: corporate ? palette.accentSoft : palette.orangeSoft,
      }}
    >
      {corporate ? '对公' : '对私'}
    </span>
  );
};

/** 一个账户卡片：详情与表单共用，actions 为空时只读 */
export const AccountCard: React.FC<{
  row: Pick<
    AccountRow,
    | 'accountType'
    | 'accountName'
    | 'bankName'
    | 'accountNo'
    | 'payeePhone'
    | 'payeeIdNoMasked'
    | 'defaultAccount'
  >;
  actions?: React.ReactNode;
}> = ({ row, actions }) => {
  const { palette } = useAppTheme();
  const corporate = row.accountType === ACCOUNT_CORPORATE;
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        padding: '14px 18px',
        borderRadius: 12,
        background: palette.inset,
        border: `1px solid ${row.defaultAccount ? palette.accentLine : palette.hairline}`,
      }}
    >
      <span
        aria-hidden
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 40,
          height: 40,
          flex: 'none',
          borderRadius: 10,
          fontSize: 18,
          color: corporate ? palette.link : palette.orange,
          background: corporate ? palette.accentSoft : palette.orangeSoft,
        }}
      >
        {corporate ? <BankOutlined /> : <UserOutlined />}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
          }}
        >
          <TypePill type={row.accountType} />
          <span style={{ fontWeight: 600, color: palette.ink }}>
            {row.accountName}
          </span>
          {row.defaultAccount && (
            <span
              style={{
                height: 22,
                lineHeight: '22px',
                padding: '0 8px',
                borderRadius: 11,
                fontSize: 12,
                fontWeight: 600,
                color: palette.green,
                background: palette.greenSoft,
              }}
            >
              默认
            </span>
          )}
        </div>
        <div
          style={{
            display: 'flex',
            gap: 16,
            flexWrap: 'wrap',
            marginTop: 6,
            fontSize: 13,
            color: palette.sub,
          }}
        >
          <span>{row.bankName}</span>
          <span className="num" style={{ color: palette.ink }}>
            {groupDigits(row.accountNo)}
          </span>
          {row.payeePhone && (
            <span style={{ color: palette.mute }}>手机 {row.payeePhone}</span>
          )}
          {row.payeeIdNoMasked && (
            <span className="num" style={{ color: palette.mute }}>
              身份证 {row.payeeIdNoMasked}
            </span>
          )}
        </div>
      </div>
      {actions && (
        <div style={{ display: 'flex', gap: 16, flex: 'none' }}>{actions}</div>
      )}
    </div>
  );
};

type ModalValues = Omit<AccountRow, 'key' | 'id' | 'payeeIdNoMasked'>;

/** 添加 / 编辑账户弹窗；切换对公 / 对私时字段随之变化 */
const AccountModal: React.FC<{
  open: boolean;
  editing?: AccountRow;
  supplierName: string;
  forceDefault: boolean;
  onCancel: () => void;
  onOk: (values: ModalValues) => void;
}> = ({ open, editing, supplierName, forceDefault, onCancel, onOk }) => {
  const [form] = Form.useForm<ModalValues>();
  const type: number = Form.useWatch('accountType', form) ?? ACCOUNT_CORPORATE;
  const personal = type === ACCOUNT_PERSONAL;

  return (
    <Modal
      open={open}
      title={editing ? '编辑收款账户' : '添加收款账户'}
      okText="保存"
      cancelText="取消"
      width={560}
      destroyOnHidden
      onCancel={onCancel}
      onOk={() => form.submit()}
    >
      <Form<ModalValues>
        form={form}
        layout="vertical"
        preserve={false}
        style={{ marginTop: 16 }}
        initialValues={
          editing
            ? { ...editing, payeeIdNo: undefined }
            : {
                accountType: ACCOUNT_CORPORATE,
                accountName: supplierName,
                defaultAccount: forceDefault,
              }
        }
        onValuesChange={(changed) => {
          // 新增时切换类型：对公默认取供应商名称，对私清空让用户填收款人
          if (!editing && 'accountType' in changed) {
            form.setFieldValue(
              'accountName',
              changed.accountType === ACCOUNT_CORPORATE ? supplierName : '',
            );
          }
        }}
        onFinish={(v) =>
          onOk({
            ...v,
            accountName: v.accountName.trim(),
            bankName: v.bankName.trim(),
            accountNo: v.accountNo.replace(/\s/g, ''),
            payeePhone: (v.payeePhone ?? '').trim(),
            payeeIdNo: v.payeeIdNo?.trim() || undefined,
          })
        }
      >
        <Form.Item name="accountType" style={{ marginBottom: 20 }}>
          <Segmented
            block
            options={[
              { value: ACCOUNT_CORPORATE, label: '对公账户' },
              { value: ACCOUNT_PERSONAL, label: '对私账户' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="accountName"
          label={personal ? '收款人姓名' : '户名'}
          extra={personal ? undefined : '默认取供应商名称，可修改'}
          rules={[
            {
              required: true,
              whitespace: true,
              message: personal ? '请填写收款人姓名' : '请填写户名',
            },
            { max: 100, message: '不能超过100个字符' },
          ]}
        >
          <Input maxLength={100} />
        </Form.Item>
        <Form.Item
          name="bankName"
          label="开户银行"
          extra="写到支行"
          rules={[
            { required: true, whitespace: true, message: '请填写开户银行' },
            { max: 100, message: '开户银行不能超过100个字符' },
          ]}
        >
          <Input placeholder="如 招商银行上海张江支行" maxLength={100} />
        </Form.Item>
        <Form.Item
          name="accountNo"
          label={personal ? '银行卡号' : '银行账号'}
          normalize={(v?: string) => v?.replace(/\s/g, '')}
          rules={[
            { required: true, message: '请填写账号' },
            {
              pattern: ACCOUNT_NO_PATTERN,
              message: '账号只能包含数字，长度 8 到 30 位',
            },
          ]}
        >
          <Input inputMode="numeric" maxLength={30} className="num" />
        </Form.Item>
        {personal && (
          <div
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}
          >
            <Form.Item
              name="payeePhone"
              label="收款人手机号"
              rules={[
                { pattern: PHONE_PATTERN, message: '请输入正确的 11 位手机号' },
              ]}
            >
              <Input inputMode="numeric" maxLength={11} className="num" />
            </Form.Item>
            <Form.Item
              name="payeeIdNo"
              label="收款人身份证号"
              extra={
                editing?.payeeIdNoMasked
                  ? `已填写 ${editing.payeeIdNoMasked}，不改动就留空`
                  : '保存后只显示前 6 位和后 4 位'
              }
              normalize={(v?: string) => v?.toUpperCase()}
              rules={[
                {
                  pattern: ID_NO_PATTERN,
                  message: '请输入正确的 18 位身份证号',
                },
              ]}
            >
              <Input
                maxLength={18}
                className="num"
                placeholder={
                  editing?.payeeIdNoMasked ? '重新填写才会覆盖' : undefined
                }
              />
            </Form.Item>
          </div>
        )}
        <Form.Item
          name="defaultAccount"
          valuePropName="checked"
          style={{ marginBottom: 0 }}
        >
          <Checkbox disabled={forceDefault}>设为默认收款账户</Checkbox>
        </Form.Item>
      </Form>
    </Modal>
  );
};

/** 表单「结算信息」卡片内容：Form.Item 自定义控件，值为 AccountRow[] */
export const AccountListEditor: React.FC<{
  value?: AccountRow[];
  onChange?: (rows: AccountRow[]) => void;
  supplierName: string;
}> = ({ value = [], onChange, supplierName }) => {
  const { palette } = useAppTheme();
  const { modal } = App.useApp();
  const [editing, setEditing] = useState<AccountRow | null>(null);
  const [adding, setAdding] = useState(false);

  /** 保证恰好一个默认：没有默认时第一个成为默认 */
  const commit = (rows: AccountRow[]) => {
    if (rows.length && !rows.some((r) => r.defaultAccount)) {
      rows = rows.map((r, i) => ({ ...r, defaultAccount: i === 0 }));
    }
    onChange?.(rows);
  };

  const save = (v: ModalValues) => {
    const personal = v.accountType === ACCOUNT_PERSONAL;
    // 新填的身份证号先在本地脱敏展示；没重新填写就沿用原来的脱敏值；切成对公后清掉
    const masked = (old: string) =>
      !personal ? '' : v.payeeIdNo ? maskIdNo(v.payeeIdNo) : old;
    let rows = editing
      ? value.map((r) =>
          r.key === editing.key
            ? { ...r, ...v, payeeIdNoMasked: masked(r.payeeIdNoMasked) }
            : r,
        )
      : [...value, { ...v, key: newKey(), payeeIdNoMasked: masked('') }];
    const target = editing?.key ?? rows[rows.length - 1].key;
    if (v.defaultAccount) {
      rows = rows.map((r) => ({ ...r, defaultAccount: r.key === target }));
    }
    commit(rows);
    setEditing(null);
    setAdding(false);
  };

  const remove = (row: AccountRow) =>
    modal.confirm({
      title: `删除账户「${row.accountName}」？`,
      content: row.defaultAccount
        ? '这是默认收款账户，删除后第一个账户会成为默认。保存供应商后生效。'
        : '保存供应商后生效。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => commit(value.filter((r) => r.key !== row.key)),
    });

  const setDefault = (row: AccountRow) =>
    commit(value.map((r) => ({ ...r, defaultAccount: r.key === row.key })));

  const full = value.length >= MAX_ACCOUNTS;

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 12,
          margin: '-8px 0 16px',
          fontSize: 13,
          color: palette.mute,
        }}
      >
        <span>
          对公账户户名默认取供应商名称；对私账户是收款人本人的银行卡。付款时默认使用「默认」账户。
        </span>
        {value.length > 0 && (
          <Button
            icon={<PlusOutlined />}
            disabled={full}
            onClick={() => setAdding(true)}
          >
            添加账户
          </Button>
        )}
      </div>
      {value.length === 0 ? (
        <div
          style={{
            textAlign: 'center',
            padding: '28px 16px',
            borderRadius: 12,
            border: `1px dashed ${palette.hairline}`,
            color: palette.mute,
          }}
        >
          <div style={{ color: palette.ink, fontWeight: 600 }}>
            还没有收款账户
          </div>
          <div style={{ margin: '6px 0 14px', fontSize: 13 }}>
            付款前需要先添加对公或对私账户。
          </div>
          <Button icon={<PlusOutlined />} onClick={() => setAdding(true)}>
            添加账户
          </Button>
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 10 }}>
          {value.map((row) => (
            <AccountCard
              key={row.key}
              row={row}
              actions={
                <>
                  {!row.defaultAccount && (
                    <a onClick={() => setDefault(row)}>设为默认</a>
                  )}
                  <a onClick={() => setEditing(row)}>编辑</a>
                  <a style={{ color: palette.red }} onClick={() => remove(row)}>
                    删除
                  </a>
                </>
              }
            />
          ))}
        </div>
      )}
      <AccountModal
        // 每次打开都用新的表单实例，避免沿用上一次的值
        key={editing?.key ?? (adding ? `new-${value.length}` : 'closed')}
        open={adding || !!editing}
        editing={editing ?? undefined}
        supplierName={supplierName}
        forceDefault={!editing && value.length === 0}
        onCancel={() => {
          setAdding(false);
          setEditing(null);
        }}
        onOk={save}
      />
    </>
  );
};
