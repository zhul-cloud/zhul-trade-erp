import { StopOutlined } from '@ant-design/icons';
import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Skeleton, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import DocModelCell, {
  docBrandColumns,
} from '@/components/DocFields/DocModelCell';
import {
  DocLogCard,
  InfoGrid,
  SectionTitle,
} from '@/components/DocFields/DocPage';
import SourceNos from '@/components/DocFields/SourceNos';
import { DocFilesCard } from '@/components/DocFiles';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { ReasonModal, WarehousePageTitle } from '../components';
import { BatchLink, SignedQty, StockStatusPill } from '../inventoryParts';
import {
  docHref,
  type InvMove,
  OO_DONE,
  OO_TYPE_TONE,
  type OtherOutbound,
  type OtherOutboundItem,
  otherOutboundApi,
} from '../inventoryService';
import { readBizError } from '../service';

/** 其他出库单详情（spec warehouse/inventory「其他出库」，原型 I6）：明细、库存流水、备注附件、作废 */
const OtherOutboundDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [r, setR] = useState<OtherOutbound>();
  const [error, setError] = useState<string>();
  const [voiding, setVoiding] = useState(false);
  usePageTitle(r ? `其他出库单 ${r.outboundNo}` : undefined, r?.outboundNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setR(await otherOutboundApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!r) return <Skeleton active paragraph={{ rows: 12 }} />;

  const done = r.status === OO_DONE;
  const canEdit = !!access['inventory:other-outbound:edit'];
  const canVoid = !!access['inventory:other-outbound:void'];
  const sourceHref = docHref(r.sourceDocType, r.sourceDocId);

  const itemColumns: TableColumnsType<OtherOutboundItem> = [
    { title: '#', key: 'no', width: 48, render: (_, __, i) => i + 1 },
    ...docBrandColumns<OtherOutboundItem>((x) => x),
    {
      title: '型号',
      dataIndex: 'model',
      width: 200,
      render: (_, x) => <DocModelCell model={x.model} brand={x.brand} />,
    },
    { title: '批次', dataIndex: 'batchNo', width: 170 },
    {
      title: '状态',
      dataIndex: 'stockStatus',
      width: 70,
      render: (v: number, x) => (
        <StockStatusPill status={v} name={x.stockStatusName} />
      ),
    },
    { title: '数量', dataIndex: 'quantity', width: 70 },
    {
      title: '单价（¥）',
      dataIndex: 'unitPriceCny',
      width: 110,
      render: (v: number) => Number(v).toFixed(6),
    },
    {
      title: '金额（¥）',
      dataIndex: 'amountCny',
      width: 110,
      render: (v: number) => formatAmount(v, 'CNY'),
    },
  ];

  const moveColumns: TableColumnsType<InvMove> = [
    {
      title: '发生时间',
      dataIndex: 'moveTime',
      width: 150,
      render: (v: string) => formatDateTime(v).slice(0, 16),
    },
    {
      title: '业务类型',
      dataIndex: 'bizName',
      width: 150,
      render: (v: string, m) => (
        <Pill tone={m.reversal ? 'gray' : 'red'}>
          {m.bizLabel && m.bizLabel !== v ? `${v} · ${m.bizLabel}` : v}
        </Pill>
      ),
    },
    { title: '仓库', dataIndex: 'warehouseName', width: 130 },
    {
      title: '批次',
      dataIndex: 'batchNo',
      width: 170,
      render: (v: string) => <BatchLink batchNo={v} />,
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      width: 70,
      render: (v: number) => <SignedQty value={v} />,
    },
    {
      title: '金额（¥）',
      dataIndex: 'amountCny',
      width: 120,
      render: (v: number) => formatAmount(v, 'CNY'),
    },
  ];

  const main = (
    <>
      <InfoGrid
        items={[
          { label: '出库类型', value: r.outboundTypeName },
          { label: '仓库', value: r.warehouseName },
          { label: '出库日期', value: r.outboundDate },
          {
            label: '来源单号',
            value: r.sourceDocNo ? (
              sourceHref ? (
                <Link to={sourceHref}>{r.sourceDocNo}</Link>
              ) : (
                r.sourceDocNo
              )
            ) : (
              '—'
            ),
          },
          { label: '金额', value: formatAmount(r.amountCny, 'CNY') },
          { label: '原因', value: r.reason },
          { label: '作废原因', value: r.voidReason, hidden: done },
        ]}
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle>明细</SectionTitle>
        <Table<OtherOutboundItem>
          rowKey="id"
          size="middle"
          pagination={false}
          columns={itemColumns}
          dataSource={r.items}
          scroll={{ x: 'max-content' }}
        />
      </Card>
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle>库存流水</SectionTitle>
        <Table<InvMove>
          rowKey="id"
          size="middle"
          pagination={false}
          columns={moveColumns}
          dataSource={r.moves}
          scroll={{ x: 'max-content' }}
        />
      </Card>
    </>
  );

  const side = (
    <>
      <DocFilesCard
        ownerType="OTHER_OUTBOUND"
        files={r.attachments ?? []}
        remark={r.remark}
        editable={done && canEdit}
        onAttach={(ids) => otherOutboundApi.attach(r.id, ids)}
        onRemove={(fid) => otherOutboundApi.removeAttachment(r.id, fid)}
        onRemark={(text) => otherOutboundApi.remark(r.id, text)}
        onChanged={load}
      />
      <DocLogCard logs={r.logs} />
    </>
  );

  return (
    <div>
      <WarehousePageTitle
        crumbs={[
          <MenuLink key="list" to="/warehouse/other-outbounds" />,
          r.outboundNo,
        ]}
        title={
          <>
            {r.outboundNo}
            <Pill tone={done ? 'green' : 'mute'}>{r.statusName}</Pill>
          </>
        }
        description={
          <>
            <Pill tone={OO_TYPE_TONE[r.outboundType] ?? 'gray'}>
              {r.outboundTypeName}
            </Pill>{' '}
            {r.warehouseName} · {r.outboundDate} 出库 · {r.createBy ?? '—'} 创建
            {r.sourceDocNo && (
              <>
                {' · '}
                <SourceNos
                  mode="inline"
                  refs={[{ no: r.sourceDocNo, href: sourceHref }]}
                />
              </>
            )}
          </>
        }
        actions={
          done &&
          canVoid && (
            <Button
              icon={<StopOutlined />}
              disabled={!r.voidable}
              title={
                r.voidable
                  ? undefined
                  : `由退货入库单 ${r.sourceDocNo ?? ''} 报废生成，不能单独作废`
              }
              onClick={() => setVoiding(true)}
            >
              作废
            </Button>
          )
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide
            ? 'minmax(0, 2.6fr) minmax(340px, 1fr)'
            : '1fr',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ minWidth: 0 }}>{main}</div>
        <div style={{ minWidth: 0 }}>{side}</div>
      </div>
      <ReasonModal
        open={voiding}
        title={`作废 ${r.outboundNo}`}
        description="作废后货回到原批次（生成一笔反向流水），出库金额不再计入。"
        okText="作废"
        onCancel={() => setVoiding(false)}
        onOk={async (why) => {
          await otherOutboundApi.void(r.id, why);
          message.success('已作废');
          setVoiding(false);
          load();
        }}
      />
    </div>
  );
};

export default OtherOutboundDetail;
