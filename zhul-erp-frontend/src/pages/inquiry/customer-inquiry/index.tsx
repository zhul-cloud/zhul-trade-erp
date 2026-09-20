import {
  CheckCircleOutlined,
  DollarOutlined,
  InboxOutlined,
  PlusOutlined,
  SmileOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormDatePicker,
  ProFormSelect,
  ProFormTextArea,
  ProFormUploadDragger,
  ProTable,
} from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Button, Card, message, Space, Tabs, Tag } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import CustomerQuickCreateModal from '@/components/CustomerQuickCreateModal';
import { getUserList } from '@/pages/system/user/service';
import type { CustomerItem } from '@/services/zhul/masterdata';
import { getCustomer, searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import {
  CUSTOMER_INQUIRY_SOURCE_META,
  CUSTOMER_INQUIRY_STATUS,
  CUSTOMER_INQUIRY_STATUS_META,
} from '../constants';
import type { CustomerInquiryItem } from './service';
import { pageCustomerInquiries, submitCustomerInquiry } from './service';

const startOfMonth = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
};

const today = (): string => new Date().toISOString().slice(0, 10);

const CustomerInquiryList: React.FC = () => {
  const { palette: p } = useAppTheme();
  const actionRef = useRef<ActionType>(null);
  const [customerNameMap, setCustomerNameMap] = useState<
    Record<number, string>
  >({});
  const [ownerNameMap, setOwnerNameMap] = useState<Record<number, string>>({});
  const [ownerOptions, setOwnerOptions] = useState<
    { label: string; value: number }[]
  >([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [quickCreateOpen, setQuickCreateOpen] = useState(false);
  const [selectedCustomer, setSelectedCustomer] = useState<
    CustomerItem | undefined
  >();
  const [customerOptions, setCustomerOptions] = useState<
    { label: string; value: number }[]
  >([]);
  const [sourceTab, setSourceTab] = useState<'text' | 'excel' | 'image'>(
    'text',
  );
  const [stats, setStats] = useState<{
    newThisMonth?: number;
    pendingConfirm?: number;
    pendingQuote?: number;
    dealCount?: number;
  }>({});

  // 后端本轮未提供专门的统计接口（不在任务组7范围内新增），这里用带筛选条件、
  // pageSize=1 的分页查询只取 total 字段做轻量统计；"本月已成交"因为数据模型
  // 没有单独的"成交时间"字段，退化为全量已成交数（不做月份范围过滤），这是已知的近似值。
  useEffect(() => {
    const load = async () => {
      const [newThisMonth, pendingConfirm, pendingQuote, dealCount] =
        await Promise.all([
          pageCustomerInquiries({
            page: 1,
            pageSize: 1,
            inquiryDateFrom: startOfMonth(),
            inquiryDateTo: today(),
          }),
          pageCustomerInquiries({
            page: 1,
            pageSize: 1,
            statusList: [CUSTOMER_INQUIRY_STATUS.PENDING_CONFIRM],
          }),
          pageCustomerInquiries({
            page: 1,
            pageSize: 1,
            statusList: [CUSTOMER_INQUIRY_STATUS.PENDING_QUOTE],
          }),
          pageCustomerInquiries({
            page: 1,
            pageSize: 1,
            statusList: [CUSTOMER_INQUIRY_STATUS.DEAL],
          }),
        ]);
      setStats({
        newThisMonth: newThisMonth.total,
        pendingConfirm: pendingConfirm.total,
        pendingQuote: pendingQuote.total,
        dealCount: dealCount.total,
      });
    };
    load();
  }, []);

  // 负责人下拉/展示：用户数量在本项目规模下预期有限，一次性拉一页（200条）建立 id→姓名 映射，
  // 而不是逐个 id 反查（后端没有按 id 批量查询用户的接口）。
  useEffect(() => {
    getUserList({ current: 1, pageSize: 200 }).then((res) => {
      const map: Record<number, string> = {};
      const options: { label: string; value: number }[] = [];
      res.data.records.forEach((u) => {
        map[u.id] = u.name;
        options.push({ label: u.name, value: u.id });
      });
      setOwnerNameMap(map);
      setOwnerOptions(options);
    });
  }, []);

  const resolveCustomerNames = async (records: CustomerInquiryItem[]) => {
    const missingIds = Array.from(
      new Set(
        records
          .map((r) => r.customerId)
          .filter((id) => id && !customerNameMap[id]),
      ),
    );
    if (missingIds.length === 0) return;
    const fetched: (CustomerItem | null)[] = await Promise.all(
      missingIds.map((id) => getCustomer(id).catch(() => null)),
    );
    setCustomerNameMap((prev) => {
      const next = { ...prev };
      fetched.forEach((c) => {
        if (c) next[c.id] = c.name;
      });
      return next;
    });
  };

  const statCards = [
    {
      icon: <InboxOutlined />,
      color: p.link,
      bg: p.accentSoft,
      label: '本月新增询盘',
      value: stats.newThisMonth,
    },
    {
      icon: <SmileOutlined />,
      color: p.orange,
      bg: p.orangeSoft,
      label: '待确认',
      value: stats.pendingConfirm,
    },
    {
      icon: <DollarOutlined />,
      color: p.violet,
      bg: p.violetSoft,
      label: '待报价',
      value: stats.pendingQuote,
    },
    {
      icon: <CheckCircleOutlined />,
      color: p.green,
      bg: p.greenSoft,
      label: '本月已成交',
      value: stats.dealCount,
    },
  ];

  const columns: ProColumns<CustomerInquiryItem>[] = [
    { title: '询盘编号', dataIndex: 'inquiryCode', width: 160 },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 160,
      render: (_, record) =>
        customerNameMap[record.customerId] ?? record.customerId,
    },
    {
      title: '询盘来源',
      dataIndex: 'source',
      width: 100,
      search: false,
      render: (_, record) => CUSTOMER_INQUIRY_SOURCE_META[record.source] ?? '-',
    },
    { title: '询盘日期', dataIndex: 'inquiryDate', width: 120, search: false },
    {
      title: '询盘日期',
      dataIndex: 'inquiryDateRange',
      valueType: 'dateRange',
      hideInTable: true,
      search: {
        transform: (value) => ({
          inquiryDateFrom: value[0],
          inquiryDateTo: value[1],
        }),
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      valueType: 'select',
      fieldProps: { mode: 'multiple' },
      valueEnum: Object.fromEntries(
        Object.entries(CUSTOMER_INQUIRY_STATUS_META).map(([k, v]) => [
          k,
          { text: v.text },
        ]),
      ),
      search: { transform: (value) => ({ statusList: value }) },
      render: (_, record) => {
        const meta = CUSTOMER_INQUIRY_STATUS_META[record.status];
        return <Tag color={meta?.color}>{meta?.text ?? record.status}</Tag>;
      },
    },
    {
      title: '总询盘单数',
      dataIndex: 'totalOrderCount',
      width: 100,
      search: false,
    },
    {
      title: '待核实数',
      dataIndex: 'pendingVerifyCount',
      width: 90,
      search: false,
      render: (_, record) =>
        record.pendingVerifyCount > 0 ? (
          <span style={{ color: p.orange }}>{record.pendingVerifyCount}</span>
        ) : (
          record.pendingVerifyCount
        ),
    },
    {
      title: '负责人',
      dataIndex: 'ownerId',
      width: 110,
      valueType: 'select',
      fieldProps: {
        options: ownerOptions,
        showSearch: true,
        filterOption: (input: string, opt: any) =>
          (opt?.label ?? '').toLowerCase().includes(input.toLowerCase()),
      },
      render: (_, record) =>
        record.ownerId ? (ownerNameMap[record.ownerId] ?? record.ownerId) : '-',
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160, search: false },
    {
      title: '操作',
      dataIndex: 'option',
      width: 80,
      search: false,
      render: (_, record) => (
        <a
          onClick={() =>
            history.push(`/inquiry/customer-inquiries/${record.id}`)
          }
        >
          查看
        </a>
      ),
    },
  ];

  const handleCustomerSearch = async (keyword: string) => {
    const list = await searchCustomers(keyword);
    setCustomerOptions(list.map((c) => ({ label: c.name, value: c.id })));
  };

  return (
    <>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 1fr)',
          gap: 16,
          marginBottom: 16,
        }}
      >
        {statCards.map((s) => (
          <Card key={s.label} styles={{ body: { padding: 20 } }}>
            <Space align="center">
              <div
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: 10,
                  background: s.bg,
                  color: s.color,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 18,
                }}
              >
                {s.icon}
              </div>
              <span style={{ color: p.mute }}>{s.label}</span>
            </Space>
            <div style={{ fontSize: 28, fontWeight: 700, marginTop: 12 }}>
              {s.value ?? '-'}
            </div>
          </Card>
        ))}
      </div>

      <ProTable<CustomerInquiryItem>
        headerTitle="客户询盘"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const {
            current,
            pageSize,
            inquiryCode,
            customerName,
            status,
            ownerId,
            inquiryDateFrom,
            inquiryDateTo,
          } = params as typeof params & {
            status?: number[];
            inquiryDateFrom?: string;
            inquiryDateTo?: string;
          };
          const res = await pageCustomerInquiries({
            page: current,
            pageSize,
            inquiryCode,
            customerName,
            statusList: status,
            ownerId,
            inquiryDateFrom,
            inquiryDateTo,
          });
          await resolveCustomerNames(res.records);
          return { data: res.records, total: res.total, success: true };
        }}
        pagination={{ pageSize: 10 }}
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setCreateOpen(true)}
          >
            新建客户询盘
          </Button>,
        ]}
      />

      <ModalForm
        title="新建客户询盘"
        width={640}
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open);
          if (!open) setSelectedCustomer(undefined);
        }}
        modalProps={{ destroyOnClose: true, maskClosable: false }}
        layout="vertical"
        submitter={{ searchConfig: { submitText: '创建询盘' } }}
        onFinish={async (values: any) => {
          if (!selectedCustomer) {
            message.error('请选择客户');
            return false;
          }
          const sourceMap: Record<string, number> = {
            text: 1,
            excel: 2,
            image: 3,
          };
          const created = await submitCustomerInquiry({
            customerId: selectedCustomer.id,
            source: sourceMap[sourceTab],
            rawContent: values.rawContent,
            rawAttachmentUrl: values.attachment?.[0]?.name,
            inquiryDate: today(),
            expectedReplyDate: values.expectedReplyDate,
            remark: values.remark,
          });
          message.success('创建成功');
          history.push(`/inquiry/customer-inquiries/${created.id}`);
          return true;
        }}
      >
        <ProFormSelect
          label="客户"
          name="customerId"
          placeholder="输入客户名称搜索"
          rules={[{ required: true, message: '请选择客户' }]}
          fieldProps={{
            showSearch: true,
            filterOption: false,
            options: customerOptions,
            onSearch: handleCustomerSearch,
            value: selectedCustomer?.id,
            onChange: (value: number) => {
              const opt = customerOptions.find((o) => o.value === value);
              if (opt) setSelectedCustomer({ id: value, name: opt.label });
            },
            dropdownRender: (menu: React.ReactNode) => (
              <>
                {menu}
                <div style={{ padding: 8, borderTop: `1px solid ${p.hairline}` }}>
                  <a onClick={() => setQuickCreateOpen(true)}>+ 新建客户</a>
                </div>
              </>
            ),
          }}
        />

        <Tabs
          activeKey={sourceTab}
          onChange={(k) => setSourceTab(k as typeof sourceTab)}
          items={[
            {
              key: 'text',
              label: '文本输入',
              children: (
                <ProFormTextArea
                  name="rawContent"
                  placeholder="粘贴客户原始询盘内容"
                  fieldProps={{ rows: 6 }}
                />
              ),
            },
            {
              key: 'excel',
              label: 'Excel上传',
              children: (
                <ProFormUploadDragger
                  name="attachment"
                  title="点击或拖拽 Excel 文件到此处"
                  fieldProps={{
                    accept: '.xlsx,.xls,.csv',
                    beforeUpload: () => false,
                    maxCount: 1,
                  }}
                  extra="仅支持 .xlsx/.xls/.csv，暂未接入真实文件存储服务，此处仅记录文件名（见完成报告说明）"
                />
              ),
            },
            {
              key: 'image',
              label: '图片上传',
              children: (
                <ProFormUploadDragger
                  name="attachment"
                  title="点击或拖拽图片到此处（支持多图）"
                  fieldProps={{
                    accept: '.jpg,.jpeg,.png',
                    beforeUpload: () => false,
                    multiple: true,
                  }}
                  extra="仅支持 .jpg/.png，暂未接入真实文件存储服务，此处仅记录文件名"
                />
              ),
            },
          ]}
        />

        <ProFormDatePicker
          name="expectedReplyDate"
          label="期望回复日期"
          width="md"
        />
      </ModalForm>

      <CustomerQuickCreateModal
        open={quickCreateOpen}
        onOpenChange={setQuickCreateOpen}
        onCreated={(customer) => {
          setSelectedCustomer(customer);
          setCustomerOptions((prev) => [
            { label: customer.name, value: customer.id },
            ...prev,
          ]);
          setQuickCreateOpen(false);
        }}
      />
    </>
  );
};

export default CustomerInquiryList;
