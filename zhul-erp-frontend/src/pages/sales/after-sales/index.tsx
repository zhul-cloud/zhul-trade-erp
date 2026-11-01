import {
  AlertOutlined,
  CheckCircleOutlined,
  MessageOutlined,
  PlusOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Segmented, Select, Table } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictTexts } from '@/utils/dict';
import { usePageSize } from '@/utils/pageSize';
import { readBizError } from '../service';
import { CreateAfterSalesModal, useOwnerOptions } from './dialogs';
import {
  type AfterSales,
  AS_STATUS,
  AS_STATUS_TONE,
  afterSalesApi,
  DICT_LEVEL,
  LEVEL_TONE,
  levelShort,
} from './service';

/** 销售管理 → 售后管理（spec sales/after-sales） */
const AfterSalesPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const levels = useDictTexts(DICT_LEVEL);
  const owners = useOwnerOptions();
  const [status, setStatus] = useState<number | 'all'>(AS_STATUS.OPEN);
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [level, setLevel] = useState<string>();
  const [ownerId, setOwnerId] = useState<number>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<AfterSales[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [counts, setCounts] = useState<{
    open: number;
    waiting: number;
    overdue: number;
    closedThisMonth: number;
  }>();
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, c] = await Promise.all([
        afterSalesApi.page({
          page,
          pageSize,
          status: status === 'all' ? undefined : status,
          keyword: query || undefined,
          levelCode: level,
          ownerId,
        }),
        afterSalesApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, query, level, ownerId, message]);

  useEffect(() => {
    load();
  }, [load]);

  const sub = (t?: React.ReactNode, color?: string) =>
    t ? (
      <div style={{ fontSize: 12, color: color ?? palette.mute }}>{t}</div>
    ) : null;

  const columns: TableColumnsType<AfterSales> = [
    {
      title: '售后单',
      dataIndex: 'asNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <a onClick={() => history.push(`/sales/after-sales/${r.id}`)}>{v}</a>
      ),
    },
    {
      title: '客户 · 订单',
      key: 'customer',
      width: 210,
      render: (_, r) => (
        <div>
          <div style={{ fontWeight: 600, color: palette.ink }}>
            {r.customerName}
          </div>
          {r.soId ? (
            <a
              style={{ fontSize: 12 }}
              onClick={() => history.push(`/sales/orders/${r.soId}`)}
            >
              {r.soNo}
            </a>
          ) : (
            sub('没有订单（按型号追溯）')
          )}
        </div>
      ),
    },
    {
      title: '型号',
      dataIndex: 'itemSummary',
      width: 200,
      ellipsis: true,
      render: (v?: string, r?: AfterSales) => (
        <div>
          <div>{v || '—'}</div>
          {sub(r?.symptom)}
        </div>
      ),
    },
    {
      title: '等级',
      dataIndex: 'levelCode',
      width: 80,
      render: (v: string, r) => (
        <Pill tone={LEVEL_TONE[v] ?? 'gray'}>{levelShort(v, r.levelName)}</Pill>
      ),
    },
    {
      title: '原因 · 方案',
      key: 'reason',
      width: 170,
      render: (_, r) => (
        <div>
          <div
            style={{
              color:
                r.reasonCode === 'INVESTIGATING' ? palette.orange : palette.sub,
            }}
          >
            {r.reasonName}
          </div>
          {sub(r.solutionNames.join('、') || '还没定方案')}
        </div>
      ),
    },
    { title: '负责人', dataIndex: 'ownerName', width: 90 },
    {
      title: '下一步 · 下次更新',
      key: 'next',
      width: 210,
      render: (_, r) => (
        <div>
          <div style={{ color: palette.sub }}>{r.nextAction || '—'}</div>
          {r.nextUpdateAt &&
            sub(
              `${dayjs(r.nextUpdateAt).format('MM-DD HH:mm')}${r.overdue ? ' · 已过期' : ''}`,
              r.overdue ? palette.red : undefined,
            )}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 110,
      render: (v: string, r) => (
        <Pill tone={AS_STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
      ),
    },
    ...auditColumns<AfterSales>(),
  ];

  const stat = (
    icon: React.ReactNode,
    label: string,
    value: number | undefined,
    hint: string,
    color: string,
    soft: string,
    hintColor?: string,
  ) => (
    <Card style={{ padding: '18px 20px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span
          style={{
            width: 32,
            height: 32,
            borderRadius: 10,
            background: soft,
            color,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {icon}
        </span>
        <span style={{ color: palette.sub }}>{label}</span>
      </div>
      <div
        style={{
          fontSize: 24,
          fontWeight: 700,
          color: palette.ink,
          margin: '8px 0 2px',
        }}
      >
        {value ?? '—'}
      </div>
      <div style={{ fontSize: 12, color: hintColor ?? palette.mute }}>
        {hint}
      </div>
    </Card>
  );

  return (
    <div>
      <PageTitle
        description="客户收货后的问题：跟进进度、退货、退款和费用都记在售后单上。"
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setCreating(true)}
          >
            新建售后单
          </Button>
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 16,
          marginBottom: 16,
        }}
      >
        {stat(
          <SyncOutlined />,
          '处理中',
          counts?.open,
          '还在跟进的售后单',
          palette.link,
          palette.accentSoft,
        )}
        {stat(
          <MessageOutlined />,
          '待客户确认',
          counts?.waiting,
          '方案已给，等客户回复',
          palette.violet,
          palette.violetSoft,
        )}
        {stat(
          <AlertOutlined />,
          '需要更新客户',
          counts?.overdue,
          '下次更新时间已过',
          palette.red,
          palette.redSoft,
          (counts?.overdue ?? 0) > 0 ? palette.red : undefined,
        )}
        {stat(
          <CheckCircleOutlined />,
          '本月结案',
          counts?.closedThisMonth,
          '这个月已结案的售后单',
          palette.green,
          palette.greenSoft,
        )}
      </div>
      <Card style={{ padding: 16 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
            alignItems: 'center',
            marginBottom: 12,
          }}
        >
          <Segmented<number | 'all'>
            value={status}
            onChange={(v) => {
              setStatus(v);
              setPage(1);
            }}
            options={[
              { value: AS_STATUS.OPEN, label: `处理中 ${counts?.open ?? ''}` },
              {
                value: AS_STATUS.WAITING,
                label: `待客户确认 ${counts?.waiting ?? ''}`,
              },
              { value: AS_STATUS.CLOSED, label: '已结案' },
              { value: AS_STATUS.CANCELLED, label: '已撤销' },
              { value: 'all', label: '全部' },
            ]}
          />
          <Input.Search
            allowClear
            placeholder="售后单号、订单号、型号"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={(v) => {
              setQuery(v.trim());
              setPage(1);
            }}
            style={{ width: 240 }}
            aria-label="关键词"
          />
          <Select
            allowClear
            placeholder="全部等级"
            value={level}
            onChange={(v) => {
              setLevel(v);
              setPage(1);
            }}
            options={levels.map((l) => ({ value: l.code, label: l.name }))}
            style={{ width: 170 }}
            aria-label="等级"
          />
          <Select
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="全部负责人"
            value={ownerId}
            onChange={(v) => {
              setOwnerId(v);
              setPage(1);
            }}
            options={owners}
            style={{ width: 150 }}
            aria-label="负责人"
          />
        </div>
        <Table<AfterSales>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1800 }}
          locale={{
            emptyText:
              status === AS_STATUS.OPEN
                ? '没有处理中的售后单；客户反馈问题时点右上角「新建售后单」，或在订单详情点「售后」'
                : '没有符合条件的售后单',
          }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </Card>
      <CreateAfterSalesModal
        open={creating}
        onClose={() => setCreating(false)}
        onDone={(a) => {
          setCreating(false);
          history.push(`/sales/after-sales/${a.id}`);
        }}
      />
    </div>
  );
};

export default AfterSalesPage;
