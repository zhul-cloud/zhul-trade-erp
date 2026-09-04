import { ProFormDatePicker } from '@ant-design/pro-components';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
  Table,
  Tabs,
} from 'antd';
import React, { useEffect, useState } from 'react';
import SupplierQuickCreateModal from '@/components/SupplierQuickCreateModal';
import type { SupplierItem } from '@/services/zhul/masterdata';
import { searchSuppliers } from '@/services/zhul/masterdata';
import { CHANNEL_PLATFORM_META, SOURCE_TYPE } from '../constants';
import type {
  InquiryOrderItemDetail,
  InquiryOrderSupplierItem,
} from './service';
import { addInquiryOrderSupplier, recordQuote } from './service';

interface AddSupplierQuoteModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  inquiryOrderId: number;
  items: InquiryOrderItemDetail[];
  suppliers: InquiryOrderSupplierItem[];
  onChanged: () => void;
}

const platformOptions = Object.entries(CHANNEL_PLATFORM_META).map(
  ([value, label]) => ({ label, value: Number(value) }),
);

/**
 * M02 添加供应商/录入报价（PRD 6.8）。"添加供应商" Tab 顶部按报价来源类型
 * （正式供应商/电商询价）切换字段；"录入报价" Tab 选定一个已关联来源后逐条
 * 明细录入。本位币金额由后端按 HALF_UP 计算，前端不重复计算、不可编辑。
 */
const AddSupplierQuoteModal: React.FC<AddSupplierQuoteModalProps> = ({
  open,
  onOpenChange,
  inquiryOrderId,
  items,
  suppliers,
  onChanged,
}) => {
  const [activeTab, setActiveTab] = useState<'supplier' | 'quote'>('supplier');
  const [sourceType, setSourceType] = useState<number>(
    SOURCE_TYPE.FORMAL_SUPPLIER,
  );
  const [supplierOptions, setSupplierOptions] = useState<
    { label: string; value: number }[]
  >([]);
  const [quickCreateOpen, setQuickCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const [quoteSupplierId, setQuoteSupplierId] = useState<number | undefined>();
  const [quoteRows, setQuoteRows] = useState<
    Record<
      number,
      {
        quotePriceOriginal?: number;
        currencyCode?: string;
        exchangeRate?: number;
        supplierDelivery?: string;
        quoteStatus: number;
      }
    >
  >({});
  const [savingQuotes, setSavingQuotes] = useState(false);

  useEffect(() => {
    if (!open) {
      form.resetFields();
      setActiveTab('supplier');
      setSourceType(SOURCE_TYPE.FORMAL_SUPPLIER);
      setQuoteSupplierId(undefined);
      setQuoteRows({});
    }
  }, [open, form]);

  const handleSupplierSearch = async (keyword: string) => {
    const list = await searchSuppliers(keyword);
    setSupplierOptions(list.map((s) => ({ label: s.name, value: s.id })));
  };

  const handleAddSupplier = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await addInquiryOrderSupplier(inquiryOrderId, {
        sourceType,
        supplierId: sourceType === SOURCE_TYPE.FORMAL_SUPPLIER ? values.supplierId : undefined,
        channelPlatform:
          sourceType === SOURCE_TYPE.ECOMMERCE_CHANNEL
            ? values.channelPlatform
            : undefined,
        channelName:
          sourceType === SOURCE_TYPE.ECOMMERCE_CHANNEL
            ? values.channelName
            : undefined,
        channelLink:
          sourceType === SOURCE_TYPE.ECOMMERCE_CHANNEL
            ? values.channelLink
            : undefined,
        sentDate: values.sentDate,
        replyDeadline: values.replyDeadline,
      });
      form.resetFields();
      onChanged();
      onOpenChange(false);
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveQuotes = async () => {
    if (!quoteSupplierId) return;
    setSavingQuotes(true);
    try {
      await Promise.all(
        items.map((item) => {
          const row = quoteRows[item.id];
          if (!row) return Promise.resolve();
          return recordQuote(inquiryOrderId, {
            inquiryOrderItemId: item.id,
            inquiryOrderSupplierId: quoteSupplierId,
            quotePriceOriginal: row.quotePriceOriginal,
            currencyCode: row.currencyCode,
            exchangeRate: row.exchangeRate,
            supplierDelivery: row.supplierDelivery,
            quoteStatus: row.quoteStatus,
          });
        }),
      );
      onChanged();
      onOpenChange(false);
    } finally {
      setSavingQuotes(false);
    }
  };

  return (
    <>
      <Modal
        title="添加供应商 / 录入报价"
        width={720}
        open={open}
        onCancel={() => onOpenChange(false)}
        footer={null}
        destroyOnClose
      >
        <Tabs
          activeKey={activeTab}
          onChange={(k) => setActiveTab(k as typeof activeTab)}
          items={[
            {
              key: 'supplier',
              label: '添加供应商',
              children: (
                <div>
                  <Segmented
                    style={{ marginBottom: 16 }}
                    value={sourceType}
                    onChange={(v) => setSourceType(v as number)}
                    options={[
                      { label: '正式供应商', value: SOURCE_TYPE.FORMAL_SUPPLIER },
                      { label: '电商询价', value: SOURCE_TYPE.ECOMMERCE_CHANNEL },
                    ]}
                  />
                  <Form form={form} layout="vertical">
                    {sourceType === SOURCE_TYPE.FORMAL_SUPPLIER ? (
                      <Form.Item
                        name="supplierId"
                        label="供应商"
                        rules={[{ required: true, message: '请选择供应商' }]}
                      >
                        <Select
                          showSearch
                          filterOption={false}
                          placeholder="输入供应商名称搜索"
                          options={supplierOptions}
                          onSearch={handleSupplierSearch}
                          dropdownRender={(menu) => (
                            <>
                              {menu}
                              <div
                                style={{
                                  padding: 8,
                                  borderTop: '1px solid #f0f0f0',
                                }}
                              >
                                <a onClick={() => setQuickCreateOpen(true)}>
                                  + 新建供应商
                                </a>
                              </div>
                            </>
                          )}
                        />
                      </Form.Item>
                    ) : (
                      <>
                        <Form.Item
                          name="channelPlatform"
                          label="电商平台"
                          rules={[{ required: true, message: '请选择平台' }]}
                        >
                          <Select options={platformOptions} placeholder="请选择平台" />
                        </Form.Item>
                        <Form.Item
                          name="channelName"
                          label="店铺/卖家名称"
                          rules={[{ required: true, message: '请输入店铺/卖家名称' }]}
                        >
                          <Input placeholder="如：东莞市XX五金机电经营部" />
                        </Form.Item>
                        <Form.Item name="channelLink" label="商品/店铺链接">
                          <Input placeholder="粘贴商品或店铺链接" />
                        </Form.Item>
                      </>
                    )}
                    <Space size={16} style={{ display: 'flex' }}>
                      <Form.Item name="sentDate" label="发出日期" style={{ flex: 1 }}>
                        <ProFormDatePicker noStyle />
                      </Form.Item>
                      <Form.Item
                        name="replyDeadline"
                        label="回复截止日期"
                        style={{ flex: 1 }}
                      >
                        <ProFormDatePicker noStyle />
                      </Form.Item>
                    </Space>
                  </Form>
                  <div style={{ textAlign: 'right', marginTop: 8 }}>
                    <Button
                      type="primary"
                      loading={submitting}
                      onClick={handleAddSupplier}
                    >
                      确定添加
                    </Button>
                  </div>
                </div>
              ),
            },
            {
              key: 'quote',
              label: '录入报价',
              children: (
                <div>
                  <Select
                    style={{ width: '100%', marginBottom: 16 }}
                    placeholder="选择报价来源"
                    value={quoteSupplierId}
                    onChange={setQuoteSupplierId}
                    options={suppliers.map((s) => ({
                      label: s.displayName,
                      value: s.id,
                    }))}
                  />
                  {quoteSupplierId && (
                    <>
                      <Table
                        rowKey="id"
                        pagination={false}
                        dataSource={items}
                        columns={[
                          { title: '型号', dataIndex: 'confirmedModel', width: 150 },
                          {
                            title: '原币金额',
                            width: 110,
                            render: (_, record) => (
                              <InputNumber
                                min={0}
                                style={{ width: '100%' }}
                                value={quoteRows[record.id]?.quotePriceOriginal}
                                onChange={(v) =>
                                  setQuoteRows((prev) => ({
                                    ...prev,
                                    [record.id]: {
                                      ...prev[record.id],
                                      quoteStatus: prev[record.id]?.quoteStatus ?? 2,
                                      quotePriceOriginal: v ?? undefined,
                                    },
                                  }))
                                }
                              />
                            ),
                          },
                          {
                            title: '币种',
                            width: 90,
                            render: (_, record) => (
                              <Select
                                style={{ width: '100%' }}
                                value={quoteRows[record.id]?.currencyCode}
                                onChange={(v) =>
                                  setQuoteRows((prev) => ({
                                    ...prev,
                                    [record.id]: {
                                      ...prev[record.id],
                                      quoteStatus: prev[record.id]?.quoteStatus ?? 2,
                                      currencyCode: v,
                                    },
                                  }))
                                }
                                options={['CNY', 'USD', 'EUR', 'GBP', 'JPY'].map(
                                  (c) => ({ label: c, value: c }),
                                )}
                              />
                            ),
                          },
                          {
                            title: '汇率',
                            width: 100,
                            render: (_, record) => (
                              <InputNumber
                                min={0}
                                style={{ width: '100%' }}
                                value={quoteRows[record.id]?.exchangeRate}
                                onChange={(v) =>
                                  setQuoteRows((prev) => ({
                                    ...prev,
                                    [record.id]: {
                                      ...prev[record.id],
                                      quoteStatus: prev[record.id]?.quoteStatus ?? 2,
                                      exchangeRate: v ?? undefined,
                                    },
                                  }))
                                }
                              />
                            ),
                          },
                          {
                            title: '货期',
                            width: 100,
                            render: (_, record) => (
                              <Input
                                value={quoteRows[record.id]?.supplierDelivery}
                                onChange={(e) =>
                                  setQuoteRows((prev) => ({
                                    ...prev,
                                    [record.id]: {
                                      ...prev[record.id],
                                      quoteStatus: prev[record.id]?.quoteStatus ?? 2,
                                      supplierDelivery: e.target.value,
                                    },
                                  }))
                                }
                              />
                            ),
                          },
                          {
                            title: '报价状态',
                            width: 120,
                            render: (_, record) => (
                              <Select
                                style={{ width: '100%' }}
                                value={quoteRows[record.id]?.quoteStatus ?? 1}
                                onChange={(v) =>
                                  setQuoteRows((prev) => ({
                                    ...prev,
                                    [record.id]: {
                                      ...prev[record.id],
                                      quoteStatus: v,
                                    },
                                  }))
                                }
                                options={[
                                  { label: '待报价', value: 1 },
                                  { label: '已报价', value: 2 },
                                  { label: '无法报价', value: 3 },
                                  { label: '客户确认', value: 4 },
                                ]}
                              />
                            ),
                          },
                        ]}
                      />
                      <div style={{ textAlign: 'right', marginTop: 16 }}>
                        <Button
                          type="primary"
                          loading={savingQuotes}
                          onClick={handleSaveQuotes}
                        >
                          保存报价
                        </Button>
                      </div>
                    </>
                  )}
                </div>
              ),
            },
          ]}
        />
      </Modal>
      <SupplierQuickCreateModal
        open={quickCreateOpen}
        onOpenChange={setQuickCreateOpen}
        onCreated={(supplier: SupplierItem) => {
          setSupplierOptions((prev) => [
            { label: supplier.name, value: supplier.id },
            ...prev,
          ]);
          form.setFieldValue('supplierId', supplier.id);
          setQuickCreateOpen(false);
        }}
      />
    </>
  );
};

export default AddSupplierQuoteModal;
