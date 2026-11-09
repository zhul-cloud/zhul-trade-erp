import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Modal, Table, Tooltip } from 'antd';
import React, { useEffect, useState } from 'react';
import EllipsisText from '@/components/EllipsisText';
import { useAppTheme } from '@/theme/AppTheme';
import { Pill } from '../shared/components';
import { PATHS } from '../shared/constants';
import {
  type CustomerInquiry,
  type InquiryModelLine,
  inquiryApi,
  readBizError,
} from '../shared/service';

/** 品牌、品类：显示前两个，其余收成「+N」，鼠标移上去看全部 */
export const TagList: React.FC<{ values?: string[]; label: string }> = ({
  values,
  label,
}) => {
  const { palette } = useAppTheme();
  if (!values?.length) return <span style={{ color: palette.mute }}>—</span>;
  const rest = values.length - 2;
  return (
    <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap' }}>
      {values.slice(0, 2).map((v) => (
        <Pill key={v} tone="gray">
          {v}
        </Pill>
      ))}
      {rest > 0 && (
        <Tooltip
          title={
            <div>
              <div style={{ opacity: 0.65, fontSize: 12 }}>
                全部{label}（{values.length}）
              </div>
              {values.join('、')}
            </div>
          }
        >
          <span style={{ cursor: 'default' }}>
            <Pill tone="accent">+{rest}</Pill>
          </span>
        </Tooltip>
      )}
    </span>
  );
};

/** 型号列：第一个型号；多于一个时下面一行「共 N 个 · 查看」 */
export const ModelCell: React.FC<{
  r: CustomerInquiry;
  onView: (r: CustomerInquiry) => void;
}> = ({ r, onView }) => {
  const { palette } = useAppTheme();
  if (!r.firstModel) return <span style={{ color: palette.mute }}>—</span>;
  return (
    <span style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
      <EllipsisText text={r.firstModel} style={{ fontFamily: 'monospace' }} />
      {r.totalItemCount > 1 && (
        <span style={{ fontSize: 12, color: palette.mute }}>
          共 {r.totalItemCount} 个 · <a onClick={() => onView(r)}>查看</a>
        </span>
      )}
    </span>
  );
};

type Row = InquiryModelLine & { no: number };

/** 「查看」弹窗：只看这张询盘的全部型号，不用进详情 */
export const ModelsModal: React.FC<{
  target?: CustomerInquiry;
  onClose: () => void;
}> = ({ target, onClose }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!target) return;
    setRows([]);
    setLoading(true);
    inquiryApi
      .models(target.id)
      .then((list) => setRows(list.map((r, i) => ({ ...r, no: i + 1 }))))
      .catch((e) => message.error(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [target, message]);

  const copy = () => {
    navigator.clipboard
      .writeText(rows.map((r) => r.model).join('\n'))
      .then(() => message.success('已复制全部型号'))
      .catch(() => message.error('复制失败，请手动选择复制'));
  };

  const columns: TableColumnsType<Row> = [
    {
      title: '#',
      dataIndex: 'no',
      width: 44,
      render: (v: number) => <span style={{ color: palette.mute }}>{v}</span>,
    },
    { title: '品牌', dataIndex: 'brand', width: 120, ellipsis: true },
    {
      title: '型号',
      dataIndex: 'model',
      render: (v: string) => (
        <span style={{ fontFamily: 'monospace' }}>{v}</span>
      ),
    },
    {
      title: '品类',
      dataIndex: 'category',
      width: 110,
      ellipsis: true,
      render: (v: string) => (
        <span style={{ color: palette.sub }}>{v || '—'}</span>
      ),
    },
    {
      title: '数量',
      key: 'qty',
      width: 80,
      render: (_, r) =>
        r.quantity != null ? `${r.quantity} ${r.unit || ''}`.trim() : '—',
    },
  ];

  return (
    <Modal
      open={!!target}
      onCancel={onClose}
      width={640}
      destroyOnHidden
      title={
        <div>
          <div>全部型号（{target?.totalItemCount ?? 0}）</div>
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            {target?.inquiryCode} · {target?.customerName}
          </div>
        </div>
      }
      footer={
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          <span
            style={{
              flex: 1,
              textAlign: 'left',
              fontSize: 12,
              color: palette.mute,
            }}
          >
            按询盘里的顺序；已确认的询盘显示确认后的型号
          </span>
          <Button disabled={!rows.length} onClick={copy}>
            复制全部型号
          </Button>
          <Button
            type="primary"
            onClick={() =>
              target && history.push(`${PATHS.inquiries}/${target.id}`)
            }
          >
            打开详情
          </Button>
        </div>
      }
    >
      <Table<Row>
        size="small"
        rowKey="no"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        scroll={{ y: 420 }}
      />
    </Modal>
  );
};
