import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Space, Table, Tooltip } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import { SignedMoney } from '../costingParts';
import {
  PERIOD_CLOSED,
  type Period,
  periodApi,
  periodPath,
} from '../costingService';
import { CloseModal, ReopenModal } from './dialogs';

/** 财务管理 → 库存月结（spec finance/period-close，原型 P1）：按月份顺序结账，可反结最近一个月 */
const PeriodsPage: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const [rows, setRows] = useState<Period[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [closing, setClosing] = useState<string>();
  const [reopening, setReopening] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await periodApi.list());
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  const canClose = !!access['inventory:period:close'];
  const canReopen = !!access['inventory:period:reopen'];

  const columns: TableColumnsType<Period> = [
    {
      title: '月份',
      dataIndex: 'month',
      width: 100,
      fixed: 'left',
      render: (v: string) => (
        <Link to={periodPath(v)}>
          <b>{v}</b>
        </Link>
      ),
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 90,
      render: (v: string, r) => (
        <Pill tone={r.status === PERIOD_CLOSED ? 'green' : 'orange'}>{v}</Pill>
      ),
    },
    // 汇总按算式排（ui-design-patterns「汇总数字按算式排」）：期初 + 入库 − 出库 + 在库调整 = 期末
    {
      title: '期初库存金额',
      dataIndex: 'openingAmount',
      width: 140,
      align: 'right',
      render: (v: number) => formatAmount(v, 'CNY'),
    },
    {
      title: '+ 本月入库',
      dataIndex: 'inAmount',
      width: 140,
      align: 'right',
      render: (v: number) => formatAmount(v, 'CNY'),
    },
    {
      title: '− 本月出库',
      dataIndex: 'outAmount',
      width: 140,
      align: 'right',
      render: (v: number) => formatAmount(v, 'CNY'),
    },
    {
      title: '+ 在库成本调整',
      dataIndex: 'stockAdjustAmount',
      width: 130,
      align: 'right',
      render: (v: number) => <SignedMoney value={v} />,
    },
    {
      title: '= 期末库存金额',
      dataIndex: 'closingAmount',
      width: 150,
      align: 'right',
      render: (v: number) => <b>{formatAmount(v, 'CNY')}</b>,
    },
    {
      title: '本月销售成本',
      dataIndex: 'cogsAmount',
      width: 140,
      align: 'right',
      render: (v: number) => (
        <span style={{ color: palette.sub }}>{formatAmount(v, 'CNY')}</span>
      ),
    },
    {
      title: '结账人',
      dataIndex: 'closedByName',
      width: 100,
      render: (v?: string) => v || '—',
    },
    {
      title: '结账时间',
      dataIndex: 'closedAt',
      width: 160,
      render: (v?: string) => (v ? formatDateTime(v).slice(0, 16) : '—'),
    },
    {
      title: '操作',
      key: 'op',
      width: 150,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status !== PERIOD_CLOSED && canClose && (
            <Tooltip title={r.closable ? undefined : r.closeBlock}>
              <Button
                type="link"
                size="small"
                disabled={!r.closable}
                onClick={() => setClosing(r.month)}
              >
                结账
              </Button>
            </Tooltip>
          )}
          <Link to={periodPath(r.month)}>查看</Link>
          {r.reopenable && canReopen && (
            <Button
              type="link"
              size="small"
              danger
              onClick={() => setReopening(r.month)}
            >
              反结账
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageTitle />
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 16 }}>
          <div style={{ color: palette.sub, fontSize: 13, marginBottom: 12 }}>
            按月份顺序结账；结账后，业务日期在这个月的入库、出库、出运不能再新建、作废或改日期
          </div>
          <Table<Period>
            rowKey="month"
            size="middle"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: tableScrollX(columns) }}
            locale={{ emptyText: '还没有库存流水，没有可结账的月份' }}
            pagination={
              rows.length > 20
                ? { pageSize: 20, showTotal: (t) => `共 ${t} 条` }
                : false
            }
          />
        </Card>
      )}
      <CloseModal
        month={closing}
        onClose={() => setClosing(undefined)}
        onDone={() => {
          setClosing(undefined);
          load();
        }}
      />
      <ReopenModal
        month={reopening}
        onClose={() => setReopening(undefined)}
        onDone={() => {
          setReopening(undefined);
          load();
        }}
      />
    </div>
  );
};

export default PeriodsPage;
