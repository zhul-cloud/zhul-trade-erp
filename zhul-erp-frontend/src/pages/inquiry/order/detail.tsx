import { history, useParams, useSearchParams } from '@umijs/max';
import {
  Avatar,
  Button,
  Card,
  Descriptions,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import React, { useEffect, useState } from 'react';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  CHANNEL_PLATFORM_META,
  CONFIDENCE_META,
  INQUIRY_ORDER_MANUAL_ADVANCE_SEQUENCE,
  INQUIRY_ORDER_STATUS_META,
  SOURCE_TYPE,
  SUPPLIER_ASSOCIATION_STATUS_META,
} from '../constants';
import AddSupplierQuoteModal from './AddSupplierQuoteModal';
import type {
  InquiryOrderItem,
  InquiryOrderItemDetail,
  InquiryOrderSupplierItem,
  InquiryTemplate,
  QuoteComparison,
  QuoteComparisonRow,
} from './service';
import {
  advanceInquiryOrderStatus,
  assignPurchaser,
  convertToFormalSupplier,
  getInquiryOrder,
  getInquiryOrderTemplates,
  getQuoteComparison,
  listInquiryOrderItems,
  listInquiryOrderSuppliers,
} from './service';

const { Title, Paragraph } = Typography;

const InquiryOrderDetail: React.FC = () => {
  const { palette: p } = useAppTheme();
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();

  // "新建" 场景：P03「解析失败」态跳转过来时用 /inquiry/orders/new?customerInquiryId=xxx
  // 打开，这里不是一个真实询盘单id，转到列表页让它以弹窗形式处理手动创建（任务8.1）。
  useEffect(() => {
    if (id === 'new') {
      const customerInquiryId = searchParams.get('customerInquiryId');
      history.replace(
        `/inquiry/orders${customerInquiryId ? `?manualCustomerInquiryId=${customerInquiryId}` : ''}`,
      );
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const orderId = Number(id);
  const [order, setOrder] = useState<InquiryOrderItem | null>(null);
  const [items, setItems] = useState<InquiryOrderItemDetail[]>([]);
  const [suppliers, setSuppliers] = useState<InquiryOrderSupplierItem[]>([]);
  const [comparison, setComparison] = useState<QuoteComparison | null>(null);
  const [templates, setTemplates] = useState<InquiryTemplate | null>(null);
  const [userNameMap, setUserNameMap] = useState<Record<number, string>>({});
  const [userOptions, setUserOptions] = useState<
    { label: string; value: number }[]
  >([]);
  const [loading, setLoading] = useState(true);
  const [assignOpen, setAssignOpen] = useState(false);
  const [addSupplierOpen, setAddSupplierOpen] = useState(false);

  const load = async () => {
    if (!orderId) return;
    setLoading(true);
    const [o, its, sups, cmp, tpl] = await Promise.all([
      getInquiryOrder(orderId),
      listInquiryOrderItems(orderId),
      listInquiryOrderSuppliers(orderId),
      getQuoteComparison(orderId),
      getInquiryOrderTemplates(orderId),
    ]);
    setOrder(o);
    setItems(its);
    setSuppliers(sups);
    setComparison(cmp);
    setTemplates(tpl);
    setLoading(false);
  };

  useEffect(() => {
    if (orderId) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

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

  if (id === 'new') {
    return (
      <Card>
        <Spin />
      </Card>
    );
  }

  if (loading || !order) {
    return (
      <Card>
        <Spin />
      </Card>
    );
  }

  const statusMeta = INQUIRY_ORDER_STATUS_META[order.status];
  const advanceOptions = INQUIRY_ORDER_MANUAL_ADVANCE_SEQUENCE.map((s) => ({
    label: INQUIRY_ORDER_STATUS_META[s].text,
    value: s,
  })).filter((o) => o.value > order.status);

  const handleCopy = (text?: string) => {
    if (!text) return;
    navigator.clipboard?.writeText(text);
    message.success('已复制到剪贴板');
  };

  return (
    <Card>
      <Space align="center" style={{ marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>
          {order.inquiryCode}
        </Title>
        <Select
          size="small"
          style={{ width: 140 }}
          value={order.status}
          options={[
            { label: statusMeta?.text, value: order.status },
            ...advanceOptions,
          ]}
          onChange={async (value) => {
            if (value === order.status) return;
            await advanceInquiryOrderStatus(orderId, value);
            message.success('状态已更新');
            await load();
          }}
        />
        {!order.aiTaskId && <Tag>手动创建</Tag>}
      </Space>

      <Descriptions column={5} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="品牌">{order.brand}</Descriptions.Item>
        <Descriptions.Item label="品类">{order.category}</Descriptions.Item>
        <Descriptions.Item label="型号数">{order.itemCount}</Descriptions.Item>
        <Descriptions.Item label="创建时间">
          {order.createTime}
        </Descriptions.Item>
        <Descriptions.Item label="采购员">
          {order.assigneeId ? (
            <Space>
              <Avatar size="small">
                {(userNameMap[order.assigneeId] ?? '?').slice(0, 1)}
              </Avatar>
              {userNameMap[order.assigneeId] ?? order.assigneeId}
              <a onClick={() => setAssignOpen(true)}>重新分配</a>
            </Space>
          ) : (
            <Button size="small" onClick={() => setAssignOpen(true)}>
              分配
            </Button>
          )}
        </Descriptions.Item>
      </Descriptions>

      <Tabs
        items={[
          {
            key: 'items',
            label: '型号明细',
            children: (
              <Table
                rowKey="id"
                pagination={false}
                dataSource={items}
                columns={[
                  { title: '原始型号', dataIndex: 'originalModel', width: 150 },
                  { title: '确认型号', dataIndex: 'confirmedModel', width: 150 },
                  {
                    title: '置信度',
                    dataIndex: 'confidence',
                    width: 90,
                    render: (v) => {
                      const meta = CONFIDENCE_META[v as number];
                      return <Tag color={meta?.color}>{meta?.text ?? v}</Tag>;
                    },
                  },
                  { title: '数量', dataIndex: 'quantity', width: 80 },
                  { title: '单位', dataIndex: 'unit', width: 70 },
                  {
                    title: '交期要求',
                    dataIndex: 'deliveryRequirement',
                    width: 110,
                  },
                  { title: '描述', dataIndex: 'description', ellipsis: true },
                  { title: '备注', dataIndex: 'remark', ellipsis: true },
                ]}
              />
            ),
          },
          {
            key: 'suppliers',
            label: '供应商与报价',
            children: (
              <div>
                <div style={{ textAlign: 'right', marginBottom: 16 }}>
                  <Button
                    type="primary"
                    onClick={() => setAddSupplierOpen(true)}
                  >
                    添加供应商
                  </Button>
                </div>
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'repeat(auto-fill, minmax(260px, 1fr))',
                    gap: 16,
                    marginBottom: 24,
                  }}
                >
                  {suppliers.map((s) => {
                    const isChannel = s.sourceType === SOURCE_TYPE.ECOMMERCE_CHANNEL;
                    const statusMetaS = SUPPLIER_ASSOCIATION_STATUS_META[s.status];
                    return (
                      <Card key={s.id} size="small">
                        <Space
                          align="center"
                          style={{ justifyContent: 'space-between', width: '100%' }}
                        >
                          <Space>
                            <strong>{s.displayName}</strong>
                            {isChannel && <Tag color="default">电商</Tag>}
                          </Space>
                          <Tag color={statusMetaS?.color}>{statusMetaS?.text}</Tag>
                        </Space>
                        {isChannel && (
                          <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
                            {CHANNEL_PLATFORM_META[s.channelPlatform ?? 0]}
                            {s.channelLink && (
                              <>
                                {' · '}
                                <a href={s.channelLink} target="_blank" rel="noreferrer">
                                  查看商品页
                                </a>
                              </>
                            )}
                          </Paragraph>
                        )}
                        <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 8 }}>
                          发出：{s.sentDate ?? '-'} · 截止：{s.replyDeadline ?? '-'}
                        </Paragraph>
                        {isChannel && (
                          <a
                            onClick={async () => {
                              await convertToFormalSupplier(s.id, {});
                              message.success('已转为正式供应商');
                              await load();
                            }}
                          >
                            转为正式供应商
                          </a>
                        )}
                      </Card>
                    );
                  })}
                </div>

                <Table
                  rowKey="inquiryOrderItemId"
                  pagination={false}
                  dataSource={comparison?.rows ?? []}
                  columns={[
                    { title: '型号', dataIndex: 'confirmedModel', width: 150, fixed: 'left' },
                    ...(comparison?.columns ?? []).map((col) => ({
                      title: col.displayName,
                      key: `col-${col.id}`,
                      render: (_: unknown, row: QuoteComparisonRow) => {
                        const cell = row.cellsBySupplierId[col.id];
                        if (!cell?.quoted) return '未报价';
                        return (
                          <span
                            style={
                              cell.lowestPrice
                                ? { background: p.greenSoft, padding: '2px 6px', borderRadius: 4 }
                                : undefined
                            }
                          >
                            {cell.currencyCode} {cell.quotePriceCny}
                            {cell.supplierDelivery ? ` · 货期${cell.supplierDelivery}` : ''}
                          </span>
                        );
                      },
                    })),
                  ]}
                />
              </div>
            ),
          },
          {
            key: 'templates',
            label: '询价话术',
            children: (
              <div>
                {[
                  { title: '询价话术模版（中英文通用，可直接复制发送）', text: templates?.inquiryTemplate },
                  { title: '邮件模版（中文）', text: templates?.emailTemplateCn },
                  { title: '邮件模版（英文）', text: templates?.emailTemplateEn },
                ].map((block) => (
                  <Card
                    key={block.title}
                    type="inner"
                    title={block.title}
                    extra={<a onClick={() => handleCopy(block.text)}>复制</a>}
                    style={{ marginBottom: 16 }}
                  >
                    <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                      {block.text || '（暂无内容）'}
                    </Paragraph>
                  </Card>
                ))}
              </div>
            ),
          },
        ]}
      />

      <Modal
        title={order.assigneeId ? '重新分配' : '分配采购员'}
        open={assignOpen}
        onCancel={() => setAssignOpen(false)}
        footer={null}
        destroyOnClose
      >
        <AssignForm
          orderId={orderId}
          userOptions={userOptions}
          onDone={async () => {
            setAssignOpen(false);
            await load();
          }}
        />
      </Modal>

      <AddSupplierQuoteModal
        open={addSupplierOpen}
        onOpenChange={setAddSupplierOpen}
        inquiryOrderId={orderId}
        items={items}
        suppliers={suppliers}
        onChanged={load}
      />
    </Card>
  );
};

const AssignForm: React.FC<{
  orderId: number;
  userOptions: { label: string; value: number }[];
  onDone: () => void;
}> = ({ orderId, userOptions, onDone }) => {
  const [value, setValue] = useState<number | undefined>();
  const [submitting, setSubmitting] = useState(false);
  return (
    <div>
      <Select
        style={{ width: '100%', marginBottom: 16 }}
        showSearch
        placeholder="选择采购员"
        options={userOptions}
        filterOption={(input, opt) =>
          (opt?.label ?? '').toString().toLowerCase().includes(input.toLowerCase())
        }
        value={value}
        onChange={setValue}
      />
      <div style={{ textAlign: 'right' }}>
        <Button
          type="primary"
          disabled={!value}
          loading={submitting}
          onClick={async () => {
            if (!value) return;
            setSubmitting(true);
            try {
              await assignPurchaser(orderId, value);
              message.success('分配成功');
              onDone();
            } finally {
              setSubmitting(false);
            }
          }}
        >
          确定分配
        </Button>
      </div>
    </div>
  );
};

export default InquiryOrderDetail;
