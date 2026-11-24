import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Segmented,
  Select,
  Skeleton,
  Table,
} from 'antd';
import { Ban, CircleCheck, Pencil, Plus, Search } from 'lucide-react';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { Pill } from '@/pages/inquiry/shared/components';
import { useCountries } from '@/pages/product/components/useCountries';
import { auditColumns } from '@/pages/purchase/components';
import {
  AccountListEditor,
  type AccountRow,
  toAccountPayload,
  toAccountRows,
} from '@/pages/supplier/accounts';
import { searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  type ForwarderQuery,
  type ForwarderRow,
  forwarderApi,
  readBizError,
} from '../service';

// ---------------------------------------------------------------- 新增 / 编辑（客户详情也用）

interface FormValues {
  name: string;
  shortName?: string;
  customerId?: number;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  address?: string;
  volumeDivisor: number;
  accounts: AccountRow[];
  remark?: string;
}

/** 归属客户：按名称搜索（自己数据范围内的客户） */
const CustomerSelect: React.FC<{
  value?: number;
  onChange?: (v?: number) => void;
  initialLabel?: string | null;
}> = ({ value, onChange, initialLabel }) => {
  // 国家按中文名显示（同 CountryText）；下拉选项是纯文字，用不了国旗组件
  const { find: findCountry } = useCountries();
  const [options, setOptions] = useState<{ value: number; label: string }[]>(
    [],
  );
  const timer = useRef<number | undefined>(undefined);
  const search = (kw: string) => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      searchCustomers(kw.trim() || undefined)
        .then((list) =>
          setOptions(
            list.map((c) => ({
              value: c.id,
              label: c.country
                ? `${c.name} · ${findCountry(c.country)?.nameZh ?? c.country}`
                : c.name,
            })),
          ),
        )
        .catch(() => setOptions([]));
    }, 250);
  };
  useEffect(() => {
    search('');
    return () => window.clearTimeout(timer.current);
  }, []);
  const merged =
    value != null && initialLabel && !options.some((o) => o.value === value)
      ? [{ value, label: initialLabel }, ...options]
      : options;
  return (
    <Select
      allowClear
      aria-label="归属客户"
      placeholder="不选为公司货代"
      value={value}
      onChange={(v?: number) => onChange?.(v)}
      options={merged}
      showSearch={{ onSearch: search, filterOption: false }}
    />
  );
};

/**
 * 新增、编辑货代（spec logistics/forwarder）。customerId 传入时为客户详情里新增客户指定货代：
 * 归属客户固定为这个客户，不显示归属选择。
 */
export const ForwarderDrawer: React.FC<{
  open: boolean;
  /** 编辑的货代 ID，新增时为空 */
  id?: number;
  customerId?: number;
  customerName?: string;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, id, customerId, customerName, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm<FormValues>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [ownerLabel, setOwnerLabel] = useState<string | null>();
  const name = Form.useWatch('name', form) ?? '';

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    setOwnerLabel(undefined);
    if (!id) {
      form.setFieldsValue({ volumeDivisor: 5000, accounts: [] });
      return;
    }
    setLoading(true);
    forwarderApi
      .form(id)
      .then((f) => {
        setOwnerLabel(f.customerName);
        form.setFieldsValue({
          name: f.name,
          shortName: f.shortName,
          customerId: f.customerId ?? undefined,
          contactName: f.contactName,
          contactPhone: f.contactPhone,
          contactEmail: f.contactEmail,
          address: f.address,
          volumeDivisor: f.volumeDivisor ?? 5000,
          accounts: toAccountRows(f.accounts),
          remark: f.remark,
        });
      })
      .catch((e) => message.error(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [open, id, form, message]);

  const save = async () => {
    // 校验不过时表单已提示，不再往外抛
    const v = await form.validateFields().catch(() => undefined);
    if (!v) return;
    setSaving(true);
    try {
      const body = {
        name: v.name.trim(),
        shortName: v.shortName?.trim(),
        customerId: customerId ?? v.customerId ?? null,
        contactName: v.contactName?.trim(),
        contactPhone: v.contactPhone?.trim(),
        contactEmail: v.contactEmail?.trim(),
        address: v.address?.trim(),
        volumeDivisor: v.volumeDivisor,
        accounts: toAccountPayload(v.accounts),
        remark: v.remark?.trim(),
      };
      if (id) await forwarderApi.update(id, body);
      else if (customerId)
        await forwarderApi.createForCustomer(customerId, body);
      else await forwarderApi.create(body);
      message.success('货代已保存');
      onDone();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size="min(760px, 96vw)"
      destroyOnHidden
      title={id ? '编辑货代' : customerId ? '新增客户指定货代' : '新建货代'}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saving} onClick={save}>
            保存
          </Button>
        </div>
      }
    >
      {loading ? (
        <Skeleton active />
      ) : (
        <Form form={form} layout="vertical" requiredMark>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item
              name="name"
              label="名称"
              style={{ flex: 1 }}
              rules={[
                { required: true, whitespace: true, message: '请填写名称' },
              ]}
            >
              <Input maxLength={100} placeholder="如 深圳捷运国际物流" />
            </Form.Item>
            <Form.Item name="shortName" label="简称" style={{ width: 200 }}>
              <Input maxLength={50} />
            </Form.Item>
          </div>
          {customerId ? (
            <Form.Item label="归属客户">
              <span style={{ color: palette.ink }}>{customerName}</span>
            </Form.Item>
          ) : (
            <Form.Item
              name="customerId"
              label="归属客户"
              extra="不选为公司货代；选了就是这个客户的指定货代，只在这个客户的发货里出现"
            >
              <CustomerSelect initialLabel={ownerLabel} />
            </Form.Item>
          )}
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="contactName" label="联系人" style={{ flex: 1 }}>
              <Input maxLength={50} />
            </Form.Item>
            <Form.Item name="contactPhone" label="联系电话" style={{ flex: 1 }}>
              <Input maxLength={20} />
            </Form.Item>
            <Form.Item
              name="contactEmail"
              label="联系邮箱"
              style={{ flex: 1 }}
              rules={[{ type: 'email', message: '邮箱格式不正确' }]}
            >
              <Input maxLength={100} />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item
              name="volumeDivisor"
              label="体积系数"
              style={{ width: 160 }}
              rules={[{ required: true, message: '请填写体积系数' }]}
            >
              <InputNumber<number>
                min={1000}
                max={10000}
                precision={0}
                style={{ width: '100%' }}
                aria-label="体积系数"
              />
            </Form.Item>
            <Form.Item name="address" label="地址" style={{ flex: 1 }}>
              <Input maxLength={200} />
            </Form.Item>
          </div>
          <Form.Item name="accounts" label="收款账户（选填）">
            <AccountListEditor supplierName={name} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea maxLength={500} autoSize={{ minRows: 2 }} />
          </Form.Item>
        </Form>
      )}
    </Drawer>
  );
};

// ---------------------------------------------------------------- 列表

type Tab = 'all' | 'company' | 'customer';

const ForwarderPage: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const confirmDanger = useConfirmDanger();
  const access = useAccess() as Record<string, boolean>;
  const canAdd = !!access['logistics:forwarder:add'];
  const canEdit = !!access['logistics:forwarder:edit'];
  const canStatus = !!access['logistics:forwarder:status'];
  const [tab, setTab] = useState<Tab>('all');
  const [keyword, setKeyword] = useState('');
  const [kw, setKw] = useState('');
  const [status, setStatus] = useState<number | undefined>(1);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<ForwarderRow[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<{
    all: number;
    company: number;
    customer: number;
  }>();
  const [loading, setLoading] = useState(true);
  const [drawer, setDrawer] = useState<{ open: boolean; id?: number }>({
    open: false,
  });

  const load = useCallback(async () => {
    setLoading(true);
    const q: ForwarderQuery = {
      page,
      pageSize,
      keyword: keyword.trim() || undefined,
      owner: tab === 'all' ? undefined : tab,
      status,
    };
    try {
      const [res, c] = await Promise.all([
        forwarderApi.page(q),
        forwarderApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, keyword, tab, status, message]);

  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const toggle = (r: ForwarderRow) => {
    const next = r.status === 1 ? 0 : 1;
    const apply = async () => {
      try {
        const warn = await forwarderApi.setStatus(r.id, next);
        if (warn) message.warning(`已停用；${warn}，记得在库存查询处理`);
        else message.success(next ? '已启用' : '已停用');
        load();
      } catch (e) {
        message.error(readBizError(e).message);
      }
    };
    if (next) {
      apply();
      return;
    }
    confirmDanger({
      title: `停用「${r.name}」？`,
      content: '停用后发货通知、出运单里不能再选它，已有单据不受影响。',
      okText: '停用',
      onOk: apply,
    });
  };

  const sub = (t?: string | null) => (
    <div style={{ fontSize: 12, color: palette.mute }}>{t || '—'}</div>
  );

  const columns: TableColumnsType<ForwarderRow> = [
    {
      title: '货代',
      key: 'name',
      width: 240,
      fixed: 'left',
      render: (_, r) => (
        <div>
          {canEdit ? (
            <button
              type="button"
              className="zhul-link"
              onClick={() => setDrawer({ open: true, id: r.id })}
            >
              {r.name}
            </button>
          ) : (
            <b>{r.name}</b>
          )}
          {sub([r.code, r.shortName].filter(Boolean).join(' · '))}
        </div>
      ),
    },
    {
      title: '归属',
      key: 'owner',
      width: 240,
      render: (_, r) =>
        r.customerId ? (
          <span
            style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}
          >
            <Pill tone="gray">客户指定</Pill>
            <span style={{ color: palette.link }}>{r.customerName ?? '—'}</span>
          </span>
        ) : (
          <Pill tone="gray">公司货代</Pill>
        ),
    },
    {
      title: '联系人 · 电话',
      key: 'contact',
      width: 180,
      render: (_, r) => (
        <div>
          {r.contactName || '—'}
          {sub(r.contactPhone)}
        </div>
      ),
    },
    { title: '体积系数', dataIndex: 'volumeDivisor', width: 90 },
    {
      title: '收款账户',
      key: 'accounts',
      width: 190,
      render: (_, r) =>
        r.accountCount ? (
          <div>
            {r.accountCount} 个
            {sub(r.defaultAccount && `默认 ${r.defaultAccount}`)}
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) =>
        v === 1 ? (
          <Pill tone="green">启用</Pill>
        ) : (
          <Pill tone="gray">停用</Pill>
        ),
    },
    ...auditColumns<ForwarderRow>(),
    {
      title: '操作',
      key: 'actions',
      // 编辑放行内，停用、启用收进「更多」（ui-redesign S6）
      width: rowActionsWidth(2),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            canEdit && {
              key: 'edit',
              label: '编辑',
              icon: <Pencil size={18} />,
              onClick: () => setDrawer({ open: true, id: r.id }),
            },
          ]}
          more={[
            canStatus &&
              (r.status === 1
                ? {
                    key: 'status',
                    label: '停用',
                    icon: <Ban size={16} />,
                    danger: true,
                  }
                : {
                    key: 'status',
                    label: '启用',
                    icon: <CircleCheck size={16} />,
                  }),
          ]}
          onMore={() => toggle(r)}
        />
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        extra={
          canAdd && (
            <Button
              type="primary"
              icon={<Plus size="1em" />}
              onClick={() => setDrawer({ open: true })}
            >
              新建货代
            </Button>
          )
        }
      />
      <Segmented<Tab>
        value={tab}
        onChange={(v) => {
          setTab(v);
          setPage(1);
        }}
        style={{ marginBottom: 16 }}
        options={[
          { value: 'all', label: `全部${counts ? ` ${counts.all}` : ''}` },
          {
            value: 'company',
            label: `公司货代${counts ? ` ${counts.company}` : ''}`,
          },
          {
            value: 'customer',
            label: `客户指定${counts ? ` ${counts.customer}` : ''}`,
          },
        ]}
      />
      <FilterCard
        onSearch={() => {
          setKeyword(kw.trim());
          setPage(1);
        }}
        onReset={() => {
          setKw('');
          setKeyword('');
          // 回到默认视图：只看启用
          setStatus(1);
          setPage(1);
        }}
        minColumn={130}
      >
        <FilterField
          label="关键词"
          span={2}
          help="可搜：名称、简称、联系人、联系电话"
        >
          <Input
            allowClear
            prefix={<Search size="1em" />}
            placeholder="名称、简称、联系人"
            aria-label="关键词"
            value={kw}
            onChange={(e) => setKw(e.target.value)}
            onPressEnter={() => {
              setKeyword(kw.trim());
              setPage(1);
            }}
          />
        </FilterField>
        <FilterField label="状态">
          <Select
            allowClear
            aria-label="状态"
            placeholder="全部"
            value={status}
            onChange={(v?: number) => {
              // 切换状态立即查询（doc-list-unify 状态下拉的写法）
              setStatus(v);
              setPage(1);
            }}
            options={[
              { value: 1, label: '启用' },
              { value: 0, label: '停用' },
            ]}
          />
        </FilterField>
      </FilterCard>
      <MainCard count={total} loading={loading}>
        <Table<ForwarderRow>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: tableScrollX(columns) }}
          locale={{ emptyText: tableEmpty('还没有货代', loading) }}
          pagination={{
            ...PAGER_STYLE,
            current: page,
            pageSize,
            total,
            onChange: (p: number, s: number) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </MainCard>
      <ForwarderDrawer
        open={drawer.open}
        id={drawer.id}
        onClose={() => setDrawer({ open: false })}
        onDone={load}
      />
    </div>
  );
};

export default ForwarderPage;
