import { ThunderboltOutlined } from '@ant-design/icons';
import { history, useParams } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  InputNumber,
  message,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import {
  CONFIDENCE_META,
  CUSTOMER_INQUIRY_MANUAL_ADVANCE_SEQUENCE,
  CUSTOMER_INQUIRY_SOURCE_META,
  CUSTOMER_INQUIRY_STATUS,
  CUSTOMER_INQUIRY_STATUS_META,
  INQUIRY_ORDER_STATUS_META,
} from '../constants';
import type {
  AiParseItem,
  CustomerInquiryItem,
  RelatedInquiryOrderItem,
} from './service';
import {
  advanceCustomerInquiryStatus,
  cancelCustomerInquiry,
  confirmSplit,
  getCustomerInquiry,
  getInquiryPreview,
  listRelatedInquiryOrders,
  retryParse,
  startAiParse,
} from './service';

interface EditableGroup {
  groupIndex: number;
  brand: string;
  category: string;
  items: AiParseItem[];
}

const { Title, Text, Paragraph } = Typography;

const CustomerInquiryDetail: React.FC = () => {
  const { palette: p } = useAppTheme();
  const { id } = useParams<{ id: string }>();
  const inquiryId = Number(id);
  const [inquiry, setInquiry] = useState<CustomerInquiryItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [previewStats, setPreviewStats] = useState<{
    totalItemCount: number;
    confirmedCount: number;
    correctedCount: number;
    pendingVerifyCount: number;
    unrecognizedCount: number;
  } | null>(null);
  const [groups, setGroups] = useState<EditableGroup[]>([]);
  const [relatedOrders, setRelatedOrders] = useState<RelatedInquiryOrderItem[]>(
    [],
  );
  const [starting, setStarting] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const load = async () => {
    setLoading(true);
    const data = await getCustomerInquiry(inquiryId);
    setInquiry(data);
    if (data.status === CUSTOMER_INQUIRY_STATUS.PENDING_CONFIRM) {
      const preview = await getInquiryPreview(inquiryId);
      setPreviewStats({
        totalItemCount: preview.totalItemCount,
        confirmedCount: preview.confirmedCount,
        correctedCount: preview.correctedCount,
        pendingVerifyCount: preview.pendingVerifyCount,
        unrecognizedCount: preview.unrecognizedCount,
      });
      setGroups(
        preview.groups.map((g, idx) => ({
          groupIndex: idx,
          brand: g.brand,
          category: g.category,
          items: g.items.map((it) => ({ ...it })),
        })),
      );
    }
    if (data.status >= CUSTOMER_INQUIRY_STATUS.CONFIRMED) {
      const orders = await listRelatedInquiryOrders(inquiryId);
      setRelatedOrders(orders);
    }
    setLoading(false);
  };

  useEffect(() => {
    if (inquiryId) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inquiryId]);

  if (loading || !inquiry) {
    return (
      <Card>
        <Spin />
      </Card>
    );
  }

  const statusMeta = CUSTOMER_INQUIRY_STATUS_META[inquiry.status];

  const handleStartParse = async () => {
    setStarting(true);
    try {
      await startAiParse(inquiryId);
      message.success('已发起 AI 解析');
      await load();
    } finally {
      setStarting(false);
    }
  };

  const handleRetryParse = async () => {
    setRetrying(true);
    try {
      await retryParse(inquiryId);
      message.success('已重新发起 AI 解析');
      await load();
    } finally {
      setRetrying(false);
    }
  };

  const handleRemoveItem = (groupIndex: number, itemIdx: number) => {
    setGroups((prev) =>
      prev.map((g) =>
        g.groupIndex === groupIndex
          ? { ...g, items: g.items.filter((_, i) => i !== itemIdx) }
          : g,
      ),
    );
  };

  const handleItemChange = (
    groupIndex: number,
    itemIdx: number,
    patch: Partial<AiParseItem>,
  ) => {
    setGroups((prev) =>
      prev.map((g) =>
        g.groupIndex === groupIndex
          ? {
              ...g,
              items: g.items.map((it, i) =>
                i === itemIdx ? { ...it, ...patch } : it,
              ),
            }
          : g,
      ),
    );
  };

  const handleConfirmSplit = async () => {
    setConfirming(true);
    try {
      await confirmSplit(inquiryId, {
        groups: groups.map((g) => ({
          groupIndex: g.groupIndex,
          items: g.items.map((it) => ({
            originalModel: it.originalModel,
            confirmedModel: it.confirmedModel,
            confidence: it.confidence,
            correctionNote: it.correctionNote,
            description: it.description,
            quantity: it.quantity,
            unit: it.unit,
            remark: it.remark,
          })),
        })),
      });
      message.success('拆单已确认，询盘单已生成');
      await load();
    } finally {
      setConfirming(false);
    }
  };

  const handleCancel = async () => {
    await cancelCustomerInquiry(inquiryId);
    message.success('询盘已取消');
    await load();
  };

  const advanceOptions = CUSTOMER_INQUIRY_MANUAL_ADVANCE_SEQUENCE.map((s) => ({
    label: CUSTOMER_INQUIRY_STATUS_META[s].text,
    value: s,
  }));

  return (
    <Card>
      <Space align="center" style={{ marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>
          {inquiry.inquiryCode}
        </Title>
        <Tag color={statusMeta?.color}>{statusMeta?.text}</Tag>
      </Space>

      <Descriptions column={4} style={{ marginBottom: 24 }}>
        <Descriptions.Item label="询盘来源">
          {CUSTOMER_INQUIRY_SOURCE_META[inquiry.source]}
        </Descriptions.Item>
        <Descriptions.Item label="询盘日期">
          {inquiry.inquiryDate}
        </Descriptions.Item>
        <Descriptions.Item label="期望回复日期">
          {inquiry.expectedReplyDate ?? '-'}
        </Descriptions.Item>
        <Descriptions.Item label="创建时间">
          {inquiry.createTime}
        </Descriptions.Item>
      </Descriptions>

      {inquiry.status === CUSTOMER_INQUIRY_STATUS.PENDING_PARSE && (
        <>
          <Card type="inner" title="原始内容" style={{ marginBottom: 24 }}>
            <Paragraph style={{ whiteSpace: 'pre-wrap' }}>
              {inquiry.rawContent || inquiry.rawAttachmentUrl || '（无内容）'}
            </Paragraph>
          </Card>
          <Card style={{ textAlign: 'center', padding: '48px 0' }}>
            <ThunderboltOutlined style={{ fontSize: 40, color: p.link }} />
            <Title level={4}>AI 尚未开始解析</Title>
            <Text type="secondary">
              确认原始内容无误后，点击下方按钮发起 AI
              解析：识别型号、核验规格、按品牌品类拆单
            </Text>
            <div style={{ marginTop: 24 }}>
              <Button
                type="primary"
                size="large"
                loading={starting}
                onClick={handleStartParse}
              >
                开始 AI 解析
              </Button>
            </div>
          </Card>
        </>
      )}

      {inquiry.status === CUSTOMER_INQUIRY_STATUS.PARSING && (
        <Card style={{ textAlign: 'center', padding: '80px 0' }}>
          <Spin size="large" />
          <Paragraph style={{ marginTop: 16 }}>
            AI 正在解析询盘内容，预计需要几分钟，你可以先去处理其他事情
          </Paragraph>
        </Card>
      )}

      {inquiry.status === CUSTOMER_INQUIRY_STATUS.PENDING_CONFIRM &&
        previewStats && (
          <>
            <Space size={32} style={{ marginBottom: 16 }}>
              <span>共明细数 {previewStats.totalItemCount}</span>
              <span style={{ color: p.green }}>
                确认 {previewStats.confirmedCount}
              </span>
              <span style={{ color: p.link }}>
                已纠正 {previewStats.correctedCount}
              </span>
              <span style={{ color: p.orange }}>
                待核实 {previewStats.pendingVerifyCount}
              </span>
              <span style={{ color: p.red }}>
                未识别 {previewStats.unrecognizedCount}
              </span>
            </Space>
            {groups.map((g) => (
              <Card
                key={g.groupIndex}
                type="inner"
                title={`${g.brand} · ${g.category}`}
                style={{ marginBottom: 16 }}
              >
                <Table
                  rowKey={(_, i) => `${g.groupIndex}-${i}`}
                  pagination={false}
                  dataSource={g.items}
                  onRow={(record) => ({
                    style:
                      record.confidence === 4
                        ? { background: p.redSoft }
                        : record.confidence === 3
                          ? { background: p.orangeSoft }
                          : undefined,
                  })}
                  columns={[
                    {
                      title: '原始型号',
                      dataIndex: 'originalModel',
                      width: 160,
                    },
                    {
                      title: '确认型号',
                      dataIndex: 'confirmedModel',
                      width: 180,
                      render: (v, _record, idx) => (
                        <Input
                          value={v}
                          onChange={(e) =>
                            handleItemChange(g.groupIndex, idx, {
                              confirmedModel: e.target.value,
                            })
                          }
                        />
                      ),
                    },
                    {
                      title: '置信度',
                      dataIndex: 'confidence',
                      width: 90,
                      render: (v) => {
                        const meta = CONFIDENCE_META[v as number];
                        return <Tag color={meta?.color}>{meta?.text ?? v}</Tag>;
                      },
                    },
                    {
                      title: '数量',
                      dataIndex: 'quantity',
                      width: 90,
                      render: (v, _record, idx) => (
                        <InputNumber
                          value={v}
                          min={0}
                          onChange={(val) =>
                            handleItemChange(g.groupIndex, idx, {
                              quantity: val ?? 0,
                            })
                          }
                        />
                      ),
                    },
                    { title: '单位', dataIndex: 'unit', width: 70 },
                    { title: '描述', dataIndex: 'description', ellipsis: true },
                    {
                      title: '操作',
                      width: 70,
                      render: (_, __, idx) => (
                        <a onClick={() => handleRemoveItem(g.groupIndex, idx)}>
                          移除
                        </a>
                      ),
                    },
                  ]}
                />
                {g.items.length === 0 && (
                  <Text type="secondary">
                    本组已无明细，确认后不会生成询盘单
                  </Text>
                )}
              </Card>
            ))}
            <Space style={{ marginTop: 8 }}>
              <Popconfirm title="确认取消该询盘？" onConfirm={handleCancel}>
                <Button danger>取消询盘</Button>
              </Popconfirm>
              <Button
                type="primary"
                loading={confirming}
                onClick={handleConfirmSplit}
              >
                确认拆单
              </Button>
            </Space>
          </>
        )}

      {inquiry.status === CUSTOMER_INQUIRY_STATUS.PARSE_FAILED && (
        <Alert
          type="error"
          showIcon
          title="AI 解析失败"
          description={inquiry.remark || '未知原因，请重试或改为手动创建询盘单'}
          action={
            <Space>
              <Button
                type="primary"
                icon={<ThunderboltOutlined />}
                loading={retrying}
                onClick={handleRetryParse}
              >
                重试
              </Button>
              <Button
                onClick={() =>
                  history.push(
                    `/inquiry/orders/new?customerInquiryId=${inquiryId}`,
                  )
                }
              >
                手动创建询盘单
              </Button>
            </Space>
          }
        />
      )}

      {inquiry.status >= CUSTOMER_INQUIRY_STATUS.CONFIRMED && (
        <>
          {inquiry.status < CUSTOMER_INQUIRY_STATUS.CANCELLED && (
            <Space style={{ marginBottom: 16 }}>
              <span>推进客户询盘状态：</span>
              <Select
                style={{ width: 160 }}
                placeholder="选择下一状态"
                options={advanceOptions.filter((o) => o.value > inquiry.status)}
                onChange={async (value: number) => {
                  await advanceCustomerInquiryStatus(inquiryId, value);
                  message.success('状态已更新');
                  await load();
                }}
              />
            </Space>
          )}
          <Table
            rowKey="id"
            dataSource={relatedOrders}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无关联询盘单" /> }}
            columns={[
              {
                title: '询盘单编号',
                dataIndex: 'inquiryCode',
                render: (v, record) => (
                  <a
                    onClick={() => history.push(`/inquiry/orders/${record.id}`)}
                  >
                    {v}
                  </a>
                ),
              },
              { title: '品牌', dataIndex: 'brand' },
              { title: '品类', dataIndex: 'category' },
              { title: '型号数', dataIndex: 'itemCount' },
              {
                title: '状态',
                dataIndex: 'status',
                render: (v) => {
                  const meta = INQUIRY_ORDER_STATUS_META[v as number];
                  return <Tag color={meta?.color}>{meta?.text ?? v}</Tag>;
                },
              },
            ]}
          />
        </>
      )}
    </Card>
  );
};

export default CustomerInquiryDetail;
