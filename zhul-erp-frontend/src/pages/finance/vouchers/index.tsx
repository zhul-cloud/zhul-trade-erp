import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Space, Table } from 'antd';
import { FilePlus, Settings } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import { MainCard, PageHeader } from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { AccountDrawer, ReconPill, useVoucherExport } from './parts';
import { type VoucherMonth, voucherApi, voucherPath } from './service';

/** 财务管理 → 业务凭证（spec finance/business-voucher，原型 V1）：已结账月份的凭证、核对与导出 */
const VouchersPage: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const [rows, setRows] = useState<VoucherMonth[]>([]);
  const pager = usePagination(20, '', rows.length);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [generating, setGenerating] = useState<string>();
  const [accountsOpen, setAccountsOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await voucherApi.months());
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const canExport = !!access['finance:voucher:export'];
  const { exportMonth, exporting } = useVoucherExport(
    () => setAccountsOpen(true),
    load,
  );

  const generate = async (month: string) => {
    setGenerating(month);
    try {
      const d = await voucherApi.generate(month);
      message.success(`${month} 已生成 ${d.vouchers.length} 张凭证`);
      load();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setGenerating(undefined);
    }
  };

  const dash = <span style={{ color: palette.mute }}>—</span>;
  const columns: TableColumnsType<VoucherMonth> = [
    {
      title: '月份',
      dataIndex: 'month',
      width: 110,
      fixed: 'left',
      render: (v: string, r) =>
        r.generated ? (
          <Link to={voucherPath(v)}>
            <b>{v}</b>
          </Link>
        ) : (
          <b>{v}</b>
        ),
    },
    {
      title: '凭证',
      dataIndex: 'voucherCount',
      width: 120,
      align: 'right',
      render: (v: number, r) =>
        r.generated ? (
          `${v} 张`
        ) : (
          <span style={{ color: palette.mute }}>还没有凭证</span>
        ),
    },
    {
      title: '借方合计',
      dataIndex: 'debitTotal',
      width: 150,
      align: 'right',
      render: (v: number, r) =>
        r.generated ? formatAmount(v, r.currencyCode) : dash,
    },
    {
      title: '核对',
      dataIndex: 'reconDiff',
      width: 110,
      render: (v: number, r) => (r.generated ? <ReconPill diff={v} /> : dash),
    },
    // lint-ui-ignore num-align: 格子里是导出状态标签，不是数字
    {
      title: '导出状态',
      dataIndex: 'exportCount',
      width: 110,
      render: (v: number, r) =>
        !r.generated ? (
          dash
        ) : v > 0 ? (
          <Pill tone="green">{v > 1 ? `已导出 ${v} 次` : '已导出'}</Pill>
        ) : (
          <Pill tone="orange">未导出</Pill>
        ),
    },
    {
      title: '导出人',
      dataIndex: 'exportedByName',
      width: 100,
      render: (v?: string) => v || dash,
    },
    {
      title: '导出时间',
      dataIndex: 'exportedAt',
      width: 160,
      render: (v?: string) => (v ? formatDateTime(v).slice(0, 16) : dash),
    },
    {
      // lint-ui-ignore row-actions: 生成凭证要显示进度，保留按钮
      title: '操作',
      key: 'op',
      width: 140,
      fixed: 'right',
      render: (_, r) =>
        !r.generated ? (
          canExport && (
            <Button
              size="small"
              icon={<FilePlus size="1em" />}
              loading={generating === r.month}
              onClick={() => generate(r.month)}
            >
              生成凭证
            </Button>
          )
        ) : (
          <Space size={4}>
            <Link to={voucherPath(r.month)}>查看</Link>
            {canExport && (
              <Button
                type="link"
                size="small"
                loading={exporting === r.month}
                onClick={() => exportMonth(r.month, (r.exportCount ?? 0) > 0)}
              >
                导出
              </Button>
            )}
          </Space>
        ),
    },
  ];

  return (
    <div>
      <PageHeader
        extra={
          <Button
            type="text"
            icon={<Settings size="1em" />}
            style={{ color: palette.link }}
            onClick={() => setAccountsOpen(true)}
          >
            科目设置
          </Button>
        }
      />
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard count={rows.length} loading={loading}>
          <Table<VoucherMonth>
            rowKey="month"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: tableScrollX(columns) }}
            locale={{
              emptyText: tableEmpty(
                '还没有已结账的月份，库存月结结账后自动生成凭证',
                loading,
              ),
            }}
            pagination={pager}
          />
        </MainCard>
      )}
      <AccountDrawer
        open={accountsOpen}
        canEdit={!!access['finance:voucher:account']}
        onClose={() => setAccountsOpen(false)}
        onSaved={() => {
          setAccountsOpen(false);
          load();
        }}
      />
    </div>
  );
};

export default VouchersPage;
