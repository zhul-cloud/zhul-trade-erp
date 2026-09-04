import {
  CheckCircleOutlined,
  PlusOutlined,
  ProfileOutlined,
  SendOutlined,
  UserDeleteOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { history, useSearchParams } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Tabs,
  Tag,
  message,
} from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import CustomerQuickCreateModal from '@/components/CustomerQuickCreateModal';
import { getUserList } from '@/pages/system/user/service';
import type { CustomerItem } from '@/services/zhul/masterdata';
import { INQUIRY_ORDER_STATUS, INQUIRY_ORDER_STATUS_META } from '../constants';
import type { InquiryOrderItem } from './service';
import { createInquiryOrderManual, pageInquiryOrders } from './service';

const CATEGORY_OPTIONS = [
  'PLC', 'HMI', '变频器', '伺服驱动器', '伺服电机', '传感器', '断路器', '接触器',
  '继电器', '软启动器', '工业网络', '电源模块', 'I/O模块', '通信模块', 'UPS',
  '其他',
].map((c) => ({ label: c, value: c }));

const InquiryOrderList: React.FC = () => {
  const actionRef = useRef<ActionType>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const [tab, setTab] = useState<'mine' | 'all'>('mine');
  const [userNameMap, setUserNameMap] = useState<Record<number, string>>({});
  const [userOptions, setUserOptions] = useState<
    { label: string; value: number }[]
  >([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [manualCustomerInquiryId, setManualCustomerInquiryId] = useState<
    number | undefined
  >();
  const [stats, setStats] = useState<{
    pendingAssign?: number;
    mine?: number;
    sentThisMonth?: number;
    dealCount?: number;
  }>({});

  // 同 P01：没有专门的聚合统计接口，用 pageSize=1 分页查询取 total 做近似统计；
  // "本月已发供应商"没有单独的"发出时间"聚合口径，退化为全量"已发供应商"状态计数。
  useEffect(() => {
    const load = async () => {
      const [pendingAssign, mine, sent, deal] = await Promise.all([
        pageInquiryOrders({ page: 1, pageSize: 1, statusList: [INQUIRY_ORDER_STATUS.PENDING_ASSIGN] }),
        pageInquiryOrders({ page: 1, pageSize: 1, mine: true }),
        pageInquiryOrders({ page: 1, pageSize: 1, statusList: [INQUIRY_ORDER_STATUS.SENT_TO_SUPPLIER] }),
        pageInquiryOrders({ page: 1, pageSize: 1, statusList: [INQUIRY_ORDER_STATUS.DEAL] }),
      ]);
      setStats({
        pendingAssign: pendingAssign.total,
        mine: mine.total,
        sentThisMonth: sent.total,
        dealCount: deal.total,
      });
    };
    load();
  }, []);

  useEffect(() => {
    getUserList({ current: 1, pageSize: 200 }).then((res) => {
      const map: Record<number, string> = {};
      const options: { label: string; value: number }[] = [];
      res.data.records.forEach((u) => {
        map[u.id] = u.name;
        options.push({ label: u.name, value: u.id });
      });
      setUserNameMap(map);
      setUserOptions(options);
    });
  }, []);

  // 从 P03「解析失败」态跳转过来的入口B：/inquiry/orders?manualCustomerInquiryId=xxx，
  // 自动弹出手动新建弹窗并带上这条已存在客户询盘的id（见 detail.tsx 对 /inquiry/orders/new 的处理）。
  useEffect(() => {
    const raw = searchParams.get('manualCustomerInquiryId');
    if (raw) {
      setManualCustomerInquiryId(Number(raw));
      setCreateOpen(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const closeCreateModal = () => {
    setCreateOpen(false);
    setManualCustomerInquiryId(undefined);
    if (searchParams.get('manualCustomerInquiryId')) {
      searchParams.delete('manualCustomerInquiryId');
      setSearchParams(searchParams);
    }
  };

  const statCards = [
    { icon: <UserDeleteOutlined />, color: '#EA580C', bg: '#FFF7ED', label: '待分配', value: stats.pendingAssign },
    { icon: <ProfileOutlined />, color: '#1677FF', bg: '#E8F3FF', label: '我的待处理', value: stats.mine },
    { icon: <SendOutlined />, color: '#7C3AED', bg: '#F3E8FF', label: '本月已发供应商', value: stats.sentThisMonth },
    { icon: <CheckCircleOutlined />, color: '#16A34A', bg: '#F0FDF4', label: '本月已成交', value: stats.dealCount },
  ];

  const columns: ProColumns<InquiryOrderItem>[] = [
    { title: '询盘单编号', dataIndex: 'inquiryCode', width: 160 },
    { title: '品牌', dataIndex: 'brand', width: 120 },
    {
      title: '品类', dataIndex: 'category', width: 110, valueType: 'select',
      fieldProps: { options: CATEGORY_OPTIONS },
    },
    { title: '型号数', dataIndex: 'itemCount', width: 80, search: false },
    {
      title: '采购员',
      dataIndex: 'assigneeId',
      width: 110,
      valueType: 'select',
      fieldProps: {
        options: userOptions,
        showSearch: true,
        filterOption: (input: string, opt: any) =>
          (opt?.label ?? '').toLowerCase().includes(input.toLowerCase()),
      },
      render: (_, record) =>
        record.assigneeId ? (
          userNameMap[record.assigneeId] ?? record.assigneeId
        ) : (
          <Tag color="default">待分配</Tag>
        ),
    },
    {
      title: '状态', dataIndex: 'status', width: 110, valueType: 'select', fieldProps: { mode: 'multiple' },
      valueEnum: Object.fromEntries(Object.entries(INQUIRY_ORDER_STATUS_META).map(([k, v]) => [k, { text: v.text }])),
      search: { transform: (value) => ({ statusList: value }) },
      render: (_, record) => {
        const meta = INQUIRY_ORDER_STATUS_META[record.status];
        return <Tag color={meta?.color}>{meta?.text ?? record.status}</Tag>;
      },
    },
    {
      title: '是否手动创建', dataIndex: 'manualOnly', hideInTable: true, valueType: 'select',
      valueEnum: { true: { text: '是' }, false: { text: '否' } },
      search: { transform: (value) => ({ manualOnly: value === 'true' }) },
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160, search: false },
    {
      title: '操作',
      width: 100,
      search: false,
      render: (_, record) => (
        <a onClick={() => history.push(`/inquiry/orders/${record.id}`)}>查看</a>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 16 }}>
        {statCards.map((s) => (
          <Card key={s.label} styles={{ body: { padding: 20 } }}>
            <Space align="center">
              <div style={{ width: 36, height: 36, borderRadius: 10, background: s.bg, color: s.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>
                {s.icon}
              </div>
              <span style={{ color: 'rgba(0,0,0,0.45)' }}>{s.label}</span>
            </Space>
            <div style={{ fontSize: 28, fontWeight: 700, marginTop: 12 }}>{s.value ?? '-'}</div>
          </Card>
        ))}
      </div>

      <Tabs
        activeKey={tab}
        onChange={(k) => {
          setTab(k as typeof tab);
          actionRef.current?.reload();
        }}
        items={[
          { key: 'mine', label: '我的待处理' },
          { key: 'all', label: '全部' },
        ]}
      />

      <ProTable<InquiryOrderItem>
        headerTitle="询盘单"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        params={{ tab }}
        request={async (params) => {
          const { current, pageSize, inquiryCode, brand, category, status, assigneeId, manualOnly } =
            params as typeof params & { status?: number[]; manualOnly?: boolean };
          const res = await pageInquiryOrders({
            page: current,
            pageSize,
            inquiryCode,
            brand,
            category,
            statusList: status,
            assigneeId,
            manualOnly,
            mine: tab === 'mine',
          });
          return { data: res.records, total: res.total, success: true };
        }}
        pagination={{ pageSize: 10 }}
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button key="add" type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            手动新建
          </Button>,
        ]}
      />

      <ManualCreateModal
        open={createOpen}
        onOpenChange={(open) => {
          if (!open) closeCreateModal();
          else setCreateOpen(true);
        }}
        customerInquiryId={manualCustomerInquiryId}
      />
    </>
  );
};

interface ManualItemRow {
  key: number;
  model: string;
  quantity?: number;
  unit?: string;
  remark?: string;
}

const ManualCreateModal: React.FC<{
  open: boolean;
  onOpenChange: (open: boolean) => void;
  customerInquiryId?: number;
}> = ({ open, onOpenChange, customerInquiryId }) => {
  const [form] = Form.useForm();
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerItem | undefined>();
  const [customerOptions, setCustomerOptions] = useState<{ label: string; value: number }[]>([]);
  const [quickCreateOpen, setQuickCreateOpen] = useState(false);
  const [rows, setRows] = useState<ManualItemRow[]>([{ key: 1 }] as ManualItemRow[]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      form.resetFields();
      setSelectedCustomer(undefined);
      setRows([{ key: 1, model: '' }]);
    }
  }, [open, form]);

  const handleCustomerSearch = async (keyword: string) => {
    const { searchCustomers } = await import('@/services/zhul/masterdata');
    const list = await searchCustomers(keyword);
    setCustomerOptions(list.map((c) => ({ label: c.name, value: c.id })));
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const items = rows.filter((r) => r.model?.trim());
    if (items.length === 0) {
      message.error('至少需要一条型号明细');
      return;
    }
    setSubmitting(true);
    try {
      const created = await createInquiryOrderManual({
        customerId: customerInquiryId ? undefined : selectedCustomer?.id,
        customerInquiryId,
        brand: values.brand,
        category: values.category,
        items: items.map((r) => ({
          model: r.model,
          quantity: r.quantity,
          unit: r.unit,
          remark: r.remark,
        })),
      });
      message.success('创建成功');
      onOpenChange(false);
      history.push(`/inquiry/orders/${created.id}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Modal
        title="手动新建询盘单"
        width={720}
        open={open}
        onCancel={() => onOpenChange(false)}
        onOk={handleSubmit}
        okText="创建"
        confirmLoading={submitting}
        destroyOnClose
      >
        {customerInquiryId ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={`将关联到客户询盘 ID=${customerInquiryId}（AI解析失败后的手动兜底），无需再选择客户`}
          />
        ) : (
          <Form.Item label="客户" style={{ marginBottom: 16 }}>
            <Select
              showSearch
              allowClear
              filterOption={false}
              placeholder="可选，输入客户名称搜索"
              options={customerOptions}
              onSearch={handleCustomerSearch}
              value={selectedCustomer?.id}
              onChange={(value) => {
                const opt = customerOptions.find((o) => o.value === value);
                setSelectedCustomer(value && opt ? { id: value, name: opt.label } : undefined);
              }}
              dropdownRender={(menu) => (
                <>
                  {menu}
                  <div style={{ padding: 8, borderTop: '1px solid #f0f0f0' }}>
                    <a onClick={() => setQuickCreateOpen(true)}>+ 新建客户</a>
                  </div>
                </>
              )}
            />
          </Form.Item>
        )}
        <Form form={form} layout="vertical">
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="brand" label="品牌" rules={[{ required: true, message: '请输入品牌' }]} style={{ flex: 1 }}>
              <Input placeholder="请输入品牌" />
            </Form.Item>
            <Form.Item name="category" label="品类" rules={[{ required: true, message: '请选择品类' }]} style={{ flex: 1 }}>
              <Select options={CATEGORY_OPTIONS} placeholder="请选择品类" showSearch />
            </Form.Item>
          </Space>
        </Form>

        <div style={{ marginBottom: 8, fontWeight: 600 }}>型号明细</div>
        {rows.map((row, idx) => (
          <Space key={row.key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
            <Input
              placeholder="型号"
              style={{ width: 200 }}
              value={row.model}
              onChange={(e) =>
                setRows((prev) => prev.map((r, i) => (i === idx ? { ...r, model: e.target.value } : r)))
              }
            />
            <Input
              placeholder="数量"
              type="number"
              style={{ width: 90 }}
              value={row.quantity}
              onChange={(e) =>
                setRows((prev) =>
                  prev.map((r, i) => (i === idx ? { ...r, quantity: Number(e.target.value) || undefined } : r)),
                )
              }
            />
            <Input
              placeholder="单位"
              style={{ width: 80 }}
              value={row.unit}
              onChange={(e) =>
                setRows((prev) => prev.map((r, i) => (i === idx ? { ...r, unit: e.target.value } : r)))
              }
            />
            <Input
              placeholder="备注"
              style={{ width: 160 }}
              value={row.remark}
              onChange={(e) =>
                setRows((prev) => prev.map((r, i) => (i === idx ? { ...r, remark: e.target.value } : r)))
              }
            />
            {rows.length > 1 && (
              <a onClick={() => setRows((prev) => prev.filter((_, i) => i !== idx))}>移除</a>
            )}
          </Space>
        ))}
        <Button
          type="dashed"
          block
          onClick={() => setRows((prev) => [...prev, { key: Date.now(), model: '' }])}
        >
          + 添加型号
        </Button>
      </Modal>
      <CustomerQuickCreateModal
        open={quickCreateOpen}
        onOpenChange={setQuickCreateOpen}
        onCreated={(customer) => {
          setSelectedCustomer(customer);
          setCustomerOptions((prev) => [{ label: customer.name, value: customer.id }, ...prev]);
          setQuickCreateOpen(false);
        }}
      />
    </>
  );
};

export default InquiryOrderList;
