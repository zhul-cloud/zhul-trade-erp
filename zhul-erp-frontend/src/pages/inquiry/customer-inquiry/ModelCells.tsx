import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Modal, Table, Tooltip } from 'antd';
import React, { useEffect, useState } from 'react';
import EllipsisText from '@/components/EllipsisText';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { Pill } from '../shared/components';
import { PATHS } from '../shared/constants';
import {
  type CustomerInquiry,
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
          共 {r.totalItemCount} 个 ·{' '}
          <button type="button" className="zhul-link" onClick={() => onView(r)}>
            查看
          </button>
        </span>
      )}
    </span>
  );
};

/** 弹窗里的一行型号；单价只有销售订单有 */
export interface ModelRow {
  brand: string;
  model: string;
  category: string;
  quantity?: number;
  unit?: string;
  unitPrice?: number;
  currencyCode?: string;
}

type Row = ModelRow & { no: number };

const PAGE_SIZE = 20;

/**
 * 「查看」弹窗：分页加载全部型号（每页 20 个），不用进详情；询盘列表与销售订单列表共用。
 * 「复制全部型号」另取全部型号，不只复制当前页。
 */
export const PagedModelsModal: React.FC<{
  /** 打开时传当前单据 ID；关闭传 undefined */
  openId?: number;
  title: React.ReactNode;
  note?: string;
  showPrice?: boolean;
  fetchPage: (
    page: number,
    pageSize: number,
  ) => Promise<{ total: number; records: ModelRow[] }>;
  fetchAll: () => Promise<string[]>;
  onOpenDetail: () => void;
  onClose: () => void;
}> = ({
  openId,
  title,
  note,
  showPrice,
  fetchPage,
  fetchAll,
  onOpenDetail,
  onClose,
}) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [loading, setLoading] = useState(false);
  const [copying, setCopying] = useState(false);

  // 关闭时回到第 1 页，下次打开直接从第 1 页加载
  useEffect(() => {
    if (openId !== undefined) return;
    setPage(1);
    setPageSize(PAGE_SIZE);
    setRows([]);
    setTotal(0);
  }, [openId]);

  useEffect(() => {
    if (openId === undefined) return;
    setLoading(true);
    fetchPage(page, pageSize)
      .then((res) => {
        setTotal(res.total);
        setRows(
          res.records.map((r, i) => ({
            ...r,
            no: (page - 1) * pageSize + i + 1,
          })),
        );
      })
      .catch((e) => message.error(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [openId, page, pageSize, message]);

  const copy = async () => {
    setCopying(true);
    try {
      const all = await fetchAll();
      await navigator.clipboard.writeText(all.join('\n'));
      message.success(`已复制全部 ${all.length} 个型号`);
    } catch {
      message.error('复制失败，请手动选择复制');
    } finally {
      setCopying(false);
    }
  };

  const columns: TableColumnsType<Row> = [
    {
      title: '#',
      dataIndex: 'no',
      width: 52,
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
    ...(showPrice
      ? [
          {
            title: '单价',
            key: 'price',
            width: 110,
            render: (_: unknown, r: Row) =>
              r.unitPrice != null
                ? formatAmount(r.unitPrice, r.currencyCode)
                : '—',
          },
        ]
      : []),
  ];

  return (
    <Modal
      open={openId !== undefined}
      onCancel={onClose}
      width={showPrice ? 760 : 640}
      destroyOnHidden
      title={title}
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              flex: 1,
              textAlign: 'left',
              fontSize: 12,
              color: palette.mute,
            }}
          >
            {note}
          </span>
          <Button disabled={!total} loading={copying} onClick={copy}>
            复制全部型号
          </Button>
          <Button type="primary" onClick={onOpenDetail}>
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
        scroll={{ y: 420 }}
        pagination={{
          current: page,
          pageSize,
          total,
          size: 'small',
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          hideOnSinglePage: total <= PAGE_SIZE,
          showTotal: (t, [from, to]) => `第 ${from}–${to} 个，共 ${t} 个`,
          onChange: (p, s) => {
            setPage(s === pageSize ? p : 1);
            setPageSize(s);
          },
        }}
      />
    </Modal>
  );
};

/** 询盘列表「查看」弹窗：只看这张询盘的全部型号，不用进详情 */
export const ModelsModal: React.FC<{
  target?: CustomerInquiry;
  onClose: () => void;
}> = ({ target, onClose }) => (
  <PagedModelsModal
    openId={target?.id}
    title={
      <ModalTitle
        count={target?.totalItemCount}
        sub={`${target?.inquiryCode ?? ''} · ${target?.customerName ?? ''}`}
      />
    }
    note="按询盘里的顺序；已确认的询盘显示确认后的型号"
    fetchPage={(page, pageSize) =>
      inquiryApi.modelsPage(target?.id as number, page, pageSize)
    }
    fetchAll={() =>
      inquiryApi
        .models(target?.id as number)
        .then((list) => list.map((r) => r.model))
    }
    onOpenDetail={() =>
      target && history.push(`${PATHS.inquiries}/${target.id}`)
    }
    onClose={onClose}
  />
);

/** 弹窗标题：全部型号（N）+ 单号 · 客户 */
export const ModalTitle: React.FC<{ count?: number; sub: string }> = ({
  count,
  sub,
}) => {
  const { palette } = useAppTheme();
  return (
    <div>
      <div>全部型号（{count ?? 0}）</div>
      <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
        {sub}
      </div>
    </div>
  );
};
