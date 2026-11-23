import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Form, Input, Modal, Select, Table, Tag } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { auditColumns } from '@/components/UserName';
import { useCountries } from '@/pages/product/components/useCountries';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { formatDateTime } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { LIST_PATH } from './components';
import {
  type CustomerListItem,
  type CustomerPoolQuery,
  customerApi,
  readBizError,
} from './service';

/** 放入公海的常用原因：点选后填进原因输入框 */
const REASONS = ['长期无回复', '不是目标客户', '已转给同行', '其他'];

/** 放入公海（spec master-data/customer-pool「放入公海」）：原因必填 */
export const PutIntoPoolModal: React.FC<{
  open: boolean;
  customers: { id: number; name: string; ownerName?: string }[];
  onClose: () => void;
  onDone: () => void;
}> = ({ open, customers, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm<{ reason: string }>();
  const [saving, setSaving] = useState(false);
  const reason = Form.useWatch('reason', form) ?? '';

  useEffect(() => {
    if (open) form.resetFields();
  }, [open, form]);

  const submit = async () => {
    // 校验不过时表单已提示，不再往外抛
    const v = await form.validateFields().catch(() => undefined);
    if (!v) return;
    setSaving(true);
    try {
      await customerApi.putIntoPool(
        customers.map((c) => c.id),
        // 只点了常用原因、没补充说明时，去掉末尾的「：」
        v.reason.trim().replace(/：$/, ''),
      );
      message.success(`已放入公海：${customers.length} 个客户`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const kv = (label: string, value: string) => (
    <div style={{ marginBottom: 16 }}>
      <div style={{ fontSize: 13, color: palette.mute, marginBottom: 6 }}>
        {label}
      </div>
      <div
        style={{ fontWeight: 600, color: palette.ink, wordBreak: 'break-word' }}
      >
        {value}
      </div>
    </div>
  );
  return (
    <Modal
      open={open}
      title={
        customers.length > 1
          ? `放入公海 · ${customers.length} 个客户`
          : '放入公海'
      }
      onCancel={onClose}
      onOk={submit}
      okText="确认放入"
      cancelText="取消"
      confirmLoading={saving}
      destroyOnHidden
      width={480}
    >
      {kv(
        customers.length > 1 ? '已选客户' : '客户',
        customers.map((c) => c.name).join('、'),
      )}
      {kv(
        '当前负责人',
        Array.from(new Set(customers.map((c) => c.ownerName || '未分配'))).join(
          '、',
        ),
      )}
      <Form form={form} layout="vertical" requiredMark>
        <Form.Item
          name="reason"
          label="放入原因"
          rules={[
            { required: true, whitespace: true, message: '请填写放入原因' },
            { max: 200 },
          ]}
          extra={
            <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
              {REASONS.map((r) => (
                <Tag.CheckableTag
                  key={r}
                  checked={reason.startsWith(r)}
                  onChange={() =>
                    form.setFieldValue(
                      'reason',
                      r === '其他'
                        ? ''
                        : reason.startsWith(r)
                          ? reason
                          : `${r}：`,
                    )
                  }
                >
                  {r}
                </Tag.CheckableTag>
              ))}
            </div>
          }
        >
          <Input.TextArea
            rows={3}
            maxLength={200}
            placeholder="如：长期无回复：半年内邮件和 WhatsApp 都没回"
          />
        </Form.Item>
      </Form>
      <div
        style={{
          padding: '10px 12px',
          borderRadius: 10,
          background: palette.primarySoft,
          fontSize: 12,
          color: palette.sub,
        }}
      >
        放入后负责人清空，所有业务员都能看到并认领；已有单据归属不变。
      </div>
    </Modal>
  );
};

/** 认领公海客户：确认后成为负责人；同时有人认领时提示「已被 xxx 认领」 */
export const useClaim = () => {
  const { message, modal } = App.useApp();
  return useCallback(
    (customer: { id: number; displayName: string }, onDone: () => void) =>
      modal.confirm({
        title: `认领 ${customer.displayName}`,
        content: '认领后你成为负责人，客户转到你的事业部。',
        okText: '认领',
        cancelText: '取消',
        onOk: async () => {
          try {
            await customerApi.claim(customer.id);
            message.success('认领成功');
            onDone();
          } catch (e) {
            message.error(readBizError(e).message);
            onDone();
          }
        },
      }),
    [message, modal],
  );
};

/** 「公海客户」页签（spec master-data/customer-pool「公海客户列表」） */
export const PoolTab: React.FC<{ onCount?: (n: number) => void }> = ({
  onCount,
}) => {
  const { palette } = useAppTheme();
  const { labelOf: sourceLabel } = useDictOptions(DICT_SOURCE_CHANNEL);
  const { options: countryOptions, zhOf } = useCountries();
  const claim = useClaim();
  const users = useUserOptions();
  const [query, setQuery] = useState<CustomerPoolQuery>({});
  const [draft, setDraft] = useState<CustomerPoolQuery>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<CustomerListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const data = await customerApi.pool({ ...query, page, pageSize });
      setRows(data.records);
      setTotal(data.total);
      onCount?.(data.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, page, pageSize, onCount]);

  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const sub = (t?: React.ReactNode) => (
    <div style={{ fontSize: 12, color: palette.mute }}>{t || '—'}</div>
  );
  const columns: TableColumnsType<CustomerListItem> = [
    {
      title: '客户',
      key: 'customer',
      width: 260,
      fixed: 'left',
      render: (_, r) => (
        <div>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(`${LIST_PATH}/${r.id}`)}
          >
            {r.displayName}
          </button>
          {sub(
            [r.customerCode, zhOf(r.country), r.contactName]
              .filter(Boolean)
              .join(' · '),
          )}
        </div>
      ),
    },
    {
      title: '来源',
      key: 'source',
      width: 150,
      render: (_, r) =>
        r.aliLevel
          ? `${sourceLabel(r.sourceChannel)} · ${r.aliLevel}`
          : sourceLabel(r.sourceChannel) || '—',
    },
    {
      title: '上一任负责人',
      dataIndex: 'lastOwnerName',
      width: 110,
      render: (v?: string) => v || '—',
    },
    {
      title: '放入原因',
      dataIndex: 'poolReason',
      width: 240,
      render: (v?: string) => (
        <span style={{ color: palette.sub, whiteSpace: 'normal' }}>
          {v || '—'}
        </span>
      ),
    },
    {
      title: '放入时间 · 放入人',
      key: 'pool',
      width: 150,
      render: (_, r) => (
        <div>
          <span className="num">{formatDateTime(r.poolAt).slice(0, 16)}</span>
          {sub(r.poolByName)}
        </div>
      ),
    },
    ...auditColumns<CustomerListItem>(),
    {
      title: '操作',
      key: 'actions',
      width: 110,
      fixed: 'right',
      render: (_, r) => (
        <div style={{ display: 'flex', gap: 12 }}>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(`${LIST_PATH}/${r.id}`)}
          >
            查看
          </button>
          <button
            type="button"
            className="zhul-link"
            style={{ fontWeight: 600 }}
            onClick={() => claim(r, load)}
          >
            认领
          </button>
        </div>
      ),
    },
  ];

  const search = () => {
    setQuery({ ...draft, keyword: draft.keyword?.trim() || undefined });
    setPage(1);
  };
  return (
    <>
      <div
        style={{
          display: 'flex',
          gap: 12,
          flexWrap: 'wrap',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Input
          allowClear
          placeholder="名称、编码、联系人"
          aria-label="搜索公海客户"
          style={{ width: 260 }}
          value={draft.keyword}
          onChange={(e) => setDraft((d) => ({ ...d, keyword: e.target.value }))}
          onPressEnter={search}
        />
        <Select
          allowClear
          showSearch={{ optionFilterProp: 'label' }}
          placeholder="国家/地区"
          aria-label="国家/地区"
          style={{ width: 200 }}
          options={countryOptions}
          value={draft.country}
          onChange={(v?: string) => setDraft((d) => ({ ...d, country: v }))}
        />
        <Select
          allowClear
          showSearch={{ optionFilterProp: 'label' }}
          placeholder="上一任负责人"
          aria-label="上一任负责人"
          style={{ width: 160 }}
          options={users}
          value={draft.lastOwnerId}
          onChange={(v?: number) => setDraft((d) => ({ ...d, lastOwnerId: v }))}
        />
        <Button type="primary" onClick={search}>
          查询
        </Button>
      </div>
      {error ? (
        <div style={{ color: palette.red }}>{error}</div>
      ) : (
        <div
          style={{
            background: palette.card,
            borderRadius: 16,
            border: `1px solid ${palette.hairline}`,
            overflow: 'hidden',
          }}
        >
          <Table<CustomerListItem>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            locale={{ emptyText: '公海里还没有客户' }}
            pagination={{
              ...PAGER_STYLE,
              current: page,
              pageSize,
              total,
              showTotal: (t: number) => `共 ${t} 条`,
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </div>
      )}
    </>
  );
};
