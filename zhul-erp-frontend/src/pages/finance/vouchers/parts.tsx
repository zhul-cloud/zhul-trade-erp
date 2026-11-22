import { SearchOutlined } from '@ant-design/icons';
import { App, Button, Drawer, Input, Skeleton, Table, Tabs } from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { readBizError } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  CATEGORIES,
  type VoucherAccount,
  type VoucherSupplier,
  voucherApi,
} from './service';

/** 凭证类别标签 */
export const CategoryPill: React.FC<{ category: number }> = ({ category }) => {
  const c = CATEGORIES.find((x) => x.value === category);
  return <Pill tone={c?.tone ?? 'gray'}>{c?.label ?? '—'}</Pill>;
};

/** 核对结果：差额为 0 相符，否则显示差额 */
export const ReconPill: React.FC<{ diff?: number }> = ({ diff }) =>
  Number(diff ?? 0) === 0 ? (
    <Pill tone="green">相符</Pill>
  ) : (
    <Pill tone="red">差 {Number(diff).toFixed(2)}</Pill>
  );

/**
 * 导出 T+（原型 V4a、V4b）：科目没设齐时提示去设置；重复导出或核对不相符时确认后再导出。
 */
export const useVoucherExport = (
  onAccounts: () => void,
  onDone: () => void,
) => {
  const { message, modal } = App.useApp();
  const [busy, setBusy] = useState<string>();

  const run = useCallback(
    async (month: string, exported: boolean) => {
      const attempt = async (confirmed: boolean) => {
        setBusy(month);
        try {
          await voucherApi.export(month, confirmed);
          message.success(`${month} 凭证已导出`);
          onDone();
        } catch (e) {
          const err = readBizError(e);
          if (err.errorCode === 'VOUCHER_ACCOUNT_MISSING') {
            const items = (err.detail?.items as string[] | undefined) ?? [];
            modal.confirm({
              title: `还不能导出 ${month}`,
              content: (
                <>
                  <div style={{ marginBottom: 8 }}>请先在科目设置里填写：</div>
                  {items.map((i) => (
                    <div key={i}>· {i}</div>
                  ))}
                </>
              ),
              okText: '去设置',
              onOk: onAccounts,
            });
          } else if (err.errorCode === 'VOUCHER_EXPORT_CONFIRM') {
            const reasons = (err.detail?.reasons as string[] | undefined) ?? [
              err.message,
            ];
            modal.confirm({
              title: exported ? `${month} 已经导出过` : `确认导出 ${month}`,
              content: reasons.map((r) => (
                <div key={r} style={{ marginBottom: 6 }}>
                  {r}
                </div>
              )),
              okText: '仍然导出',
              onOk: () => attempt(true),
            });
          } else {
            message.error(err.message);
          }
        } finally {
          setBusy(undefined);
        }
      };
      await attempt(false);
    },
    [message, modal, onAccounts, onDone],
  );
  return { exportMonth: run, exporting: busy };
};

type AccountForm = Record<string, { code: string; name: string }>;

/** 科目设置（原型 V3、V3b）：业务项的 T+ 科目；凭证里出现过的供应商的 T+ 往来单位编码 */
export const AccountDrawer: React.FC<{
  open: boolean;
  canEdit: boolean;
  onClose: () => void;
  onSaved: () => void;
}> = ({ open, canEdit, onClose, onSaved }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [tab, setTab] = useState('accounts');
  const [accounts, setAccounts] = useState<VoucherAccount[]>();
  const [form, setForm] = useState<AccountForm>({});
  const [suppliers, setSuppliers] = useState<VoucherSupplier[]>();
  const [codes, setCodes] = useState<Record<number, string>>({});
  const [keyword, setKeyword] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTab('accounts');
    setKeyword('');
    setAccounts(undefined);
    setSuppliers(undefined);
    Promise.all([voucherApi.accounts(), voucherApi.suppliers()])
      .then(([a, s]) => {
        setAccounts(a);
        setForm(
          Object.fromEntries(
            a.map((x) => [
              x.itemKey,
              { code: x.accountCode, name: x.accountName },
            ]),
          ),
        );
        setSuppliers(s);
        setCodes(
          Object.fromEntries(s.map((x) => [x.supplierId, x.partnerCode ?? ''])),
        );
      })
      .catch((e) => message.error(readBizError(e).message));
  }, [open, message]);

  const groups = useMemo(() => {
    const out: { group: string; items: VoucherAccount[] }[] = [];
    for (const a of accounts ?? []) {
      const g = out.find((x) => x.group === a.group);
      if (g) g.items.push(a);
      else out.push({ group: a.group, items: [a] });
    }
    return out;
  }, [accounts]);

  const shownSuppliers = (suppliers ?? []).filter((s) => {
    const k = keyword.trim().toLowerCase();
    return (
      !k ||
      s.supplierName.toLowerCase().includes(k) ||
      (s.supplierCode ?? '').toLowerCase().includes(k)
    );
  });

  const save = async () => {
    const changedAccounts = (accounts ?? [])
      .filter(
        (a) =>
          form[a.itemKey].code.trim() !== a.accountCode ||
          form[a.itemKey].name.trim() !== a.accountName,
      )
      .map((a) => ({
        itemKey: a.itemKey,
        accountCode: form[a.itemKey].code.trim(),
        accountName: form[a.itemKey].name.trim(),
      }));
    const changedSuppliers = (suppliers ?? []).filter(
      (s) => (codes[s.supplierId] ?? '').trim() !== (s.partnerCode ?? ''),
    );
    if (changedAccounts.length === 0 && changedSuppliers.length === 0) {
      onClose();
      return;
    }
    setSaving(true);
    try {
      if (changedAccounts.length > 0) {
        await voucherApi.saveAccounts(changedAccounts);
      }
      for (const s of changedSuppliers) {
        await voucherApi.saveSupplier(
          s.supplierId,
          (codes[s.supplierId] ?? '').trim(),
        );
      }
      message.success('科目设置已保存');
      onSaved();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const loading = !accounts || !suppliers;

  return (
    <Drawer
      open={open}
      title="科目设置"
      size={560}
      onClose={onClose}
      destroyOnHidden
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>{canEdit ? '取消' : '关闭'}</Button>
          {canEdit && (
            <Button
              type="primary"
              loading={saving}
              disabled={loading}
              onClick={save}
            >
              保存
            </Button>
          )}
        </div>
      }
    >
      {loading ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : (
        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: 'accounts',
              label: '业务科目',
              children: (
                <div>
                  <div
                    style={{
                      color: palette.sub,
                      fontSize: 13,
                      marginBottom: 12,
                    }}
                  >
                    填写 T+ 里的科目编码与名称，导出时按这里的设置取科目
                  </div>
                  {groups.map((g) => (
                    <div
                      key={g.group}
                      style={{
                        border: `1px solid ${palette.hairline}`,
                        borderRadius: 12,
                        padding: '12px 16px',
                        marginBottom: 12,
                      }}
                    >
                      <div style={{ fontWeight: 600, marginBottom: 8 }}>
                        {g.group === '成本调整对方科目'
                          ? '成本调整对方科目（按触发原因）'
                          : g.group}
                      </div>
                      {g.items.map((a) => {
                        const v = form[a.itemKey];
                        const empty = !v.code.trim();
                        return (
                          <div
                            key={a.itemKey}
                            style={{
                              display: 'grid',
                              gridTemplateColumns: '1fr 120px 180px',
                              gap: 8,
                              alignItems: 'center',
                              marginBottom: 8,
                            }}
                          >
                            <span
                              style={{
                                color: empty ? palette.orange : palette.ink,
                              }}
                            >
                              {a.itemName}
                            </span>
                            <Input
                              aria-label={`${a.itemName}科目编码`}
                              value={v.code}
                              placeholder="科目编码"
                              status={empty ? 'warning' : undefined}
                              disabled={!canEdit}
                              maxLength={32}
                              onChange={(e) =>
                                setForm((f) => ({
                                  ...f,
                                  [a.itemKey]: { ...v, code: e.target.value },
                                }))
                              }
                            />
                            <Input
                              aria-label={`${a.itemName}科目名称`}
                              value={v.name}
                              placeholder="科目名称"
                              status={empty ? 'warning' : undefined}
                              disabled={!canEdit}
                              maxLength={64}
                              onChange={(e) =>
                                setForm((f) => ({
                                  ...f,
                                  [a.itemKey]: { ...v, name: e.target.value },
                                }))
                              }
                            />
                          </div>
                        );
                      })}
                    </div>
                  ))}
                </div>
              ),
            },
            {
              key: 'suppliers',
              label: '供应商往来单位',
              children: (
                <div>
                  <Input
                    allowClear
                    prefix={<SearchOutlined />}
                    placeholder="搜索供应商"
                    value={keyword}
                    onChange={(e) => setKeyword(e.target.value)}
                    style={{ marginBottom: 12 }}
                  />
                  <Table<VoucherSupplier>
                    rowKey="supplierId"
                    size="small"
                    pagination={false}
                    dataSource={shownSuppliers}
                    locale={{ emptyText: '凭证里还没有供应商' }}
                    columns={[
                      {
                        title: '供应商',
                        dataIndex: 'supplierName',
                        render: (v: string) => <b>{v}</b>,
                      },
                      {
                        title: 'ERP 编码',
                        dataIndex: 'supplierCode',
                        width: 120,
                        render: (v: string) => (
                          <span style={{ color: palette.sub }}>{v}</span>
                        ),
                      },
                      {
                        title: 'T+ 往来单位编码',
                        key: 'code',
                        width: 180,
                        render: (_, s) => (
                          <Input
                            aria-label={`${s.supplierName}往来单位编码`}
                            value={codes[s.supplierId] ?? ''}
                            placeholder="不填则用 ERP 编码"
                            disabled={!canEdit}
                            maxLength={32}
                            onChange={(e) =>
                              setCodes((c) => ({
                                ...c,
                                [s.supplierId]: e.target.value,
                              }))
                            }
                          />
                        ),
                      },
                    ]}
                  />
                  <div
                    style={{ color: palette.mute, fontSize: 12, marginTop: 12 }}
                  >
                    只列出在凭证里出现过的供应商
                  </div>
                </div>
              ),
            },
          ]}
        />
      )}
    </Drawer>
  );
};
