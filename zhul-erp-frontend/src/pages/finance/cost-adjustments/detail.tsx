import { Link, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Skeleton, Table } from 'antd';
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
import { PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { BatchLink } from '@/pages/warehouse/inventoryParts';
import { docHref } from '@/pages/warehouse/inventoryService';
import { readBizError } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { tableScrollX } from '@/utils/tableScroll';
import { ReasonPill, SignedMoney } from '../costingParts';
import {
  type CostAdjust,
  type CostAdjustBatch,
  type CostAdjustPart,
  costAdjustApi,
} from '../costingService';

const PART_TONE: Record<number, 'accent' | 'green' | 'orange'> = {
  1: 'accent',
  2: 'green',
  3: 'orange',
};

const unit = (v?: number) => (v == null ? '—' : Number(v).toFixed(6));

/** 单位成本的变化量：+3.000000 / −1.500000；没变化「—」 */
const UnitDiff: React.FC<{ value?: number }> = ({ value }) => {
  const { palette } = useAppTheme();
  const v = Number(value ?? 0);
  if (v === 0) return <span style={{ color: palette.mute }}>—</span>;
  return (
    <span style={{ color: v > 0 ? palette.green : palette.red }}>
      {v > 0 ? '+' : '−'}
      {Math.abs(v).toFixed(6)}
    </span>
  );
};

/** 成本调整单详情（spec finance/inventory-cost「成本调整页」，原型 C2）：按批次展开在库、已出运、其他出库各调了多少 */
const CostAdjustDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { palette } = useAppTheme();
  const wide = useWide();
  const [r, setR] = useState<CostAdjust>();
  const [error, setError] = useState<string>();
  usePageTitle(r ? `成本调整单 ${r.adjustNo}` : undefined, r?.adjustNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setR(await costAdjustApi.detail(id));
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

  const sourceHref = docHref(r.sourceDocType, r.sourceDocId);

  const batchColumns: TableColumnsType<CostAdjustBatch> = [
    ...docBrandColumns<CostAdjustBatch>((x) => x),
    {
      title: '型号',
      dataIndex: 'model',
      width: 200,
      render: (_, x) => <DocModelCell model={x.model} brand={x.brand} />,
    },
    {
      title: '批次',
      dataIndex: 'batchNo',
      width: 170,
      render: (v: string) => <BatchLink batchNo={v} />,
    },
    {
      title: '原单位成本',
      dataIndex: 'oldUnitCost',
      width: 120,
      render: unit,
    },
    {
      title: '新单位成本',
      dataIndex: 'newUnitCost',
      width: 120,
      render: (v: number) => <b>{unit(v)}</b>,
    },
    {
      title: '货款',
      dataIndex: 'diffGoods',
      width: 110,
      render: (v: number) => <UnitDiff value={v} />,
    },
    {
      title: '开票',
      dataIndex: 'diffInvoice',
      width: 110,
      render: (v: number) => <UnitDiff value={v} />,
    },
    {
      title: '采购费用',
      dataIndex: 'diffFee',
      width: 110,
      render: (v: number) => <UnitDiff value={v} />,
    },
    {
      title: '质量索赔',
      dataIndex: 'diffClaim',
      width: 110,
      render: (v: number) => <UnitDiff value={v} />,
    },
    { title: '数量', dataIndex: 'quantity', width: 70 },
    {
      title: '调整金额',
      dataIndex: 'amountCny',
      width: 110,
      render: (v: number) => <SignedMoney value={v} />,
    },
  ];

  const partColumns: TableColumnsType<CostAdjustPart> = [
    {
      title: '部分',
      dataIndex: 'partName',
      width: 90,
      render: (v: string, p) => (
        <Pill tone={PART_TONE[p.part] ?? 'gray'}>{v}</Pill>
      ),
    },
    {
      title: '仓库 / 单据',
      key: 'where',
      render: (_, p) =>
        p.part === 1 ? (
          p.warehouseName
        ) : (
          <div>
            <SourceNos
              mode="inline"
              refs={
                p.docNo
                  ? [{ no: p.docNo, href: docHref(p.docType, p.docId) }]
                  : []
              }
            />
            {p.soNo && (
              <div style={{ fontSize: 12, color: palette.mute }}>
                {p.soId ? (
                  <Link to={`/sales/orders/${p.soId}`}>{p.soNo}</Link>
                ) : (
                  p.soNo
                )}
              </div>
            )}
          </div>
        ),
    },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    {
      title: '调整金额',
      dataIndex: 'amountCny',
      width: 120,
      render: (v: number) => <SignedMoney value={v} />,
    },
  ];

  const main = (
    <>
      <InfoGrid
        items={[
          { label: '所属月份', value: r.periodMonth },
          { label: '调整日期', value: r.adjustDate },
          {
            label: '来源单号',
            value: r.sourceDocNo ? (
              <SourceNos
                mode="inline"
                refs={[{ no: r.sourceDocNo, href: sourceHref }]}
              />
            ) : (
              '—'
            ),
          },
          {
            label: '在库调整',
            value: <SignedMoney value={r.stockAmount} yuan />,
          },
          {
            label: '销售成本调整',
            value: <SignedMoney value={r.cogsAmount} yuan />,
          },
          {
            label: '其他出库调整',
            value: <SignedMoney value={r.otherAmount} yuan />,
          },
          { label: '合计', value: <SignedMoney value={r.totalAmount} yuan /> },
        ]}
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle>
          明细
          <span style={{ fontSize: 12, color: palette.mute, marginLeft: 8 }}>
            {r.batches?.length ?? 0} 个批次 · 单位成本为人民币 6 位小数
          </span>
        </SectionTitle>
        <Table<CostAdjustBatch>
          rowKey="batchId"
          size="middle"
          pagination={false}
          columns={batchColumns}
          dataSource={r.batches}
          scroll={{ x: tableScrollX(batchColumns) }}
          expandable={{
            defaultExpandAllRows: true,
            rowExpandable: (b) => b.parts.length > 0,
            expandedRowRender: (b) => (
              <Table<CostAdjustPart>
                rowKey="id"
                size="small"
                pagination={false}
                columns={partColumns}
                dataSource={b.parts}
              />
            ),
          }}
        />
      </Card>
    </>
  );

  const side = (
    <>
      <DocFilesCard
        ownerType="COST_ADJUST"
        files={r.attachments ?? []}
        remark={r.remark}
        editable
        onAttach={(ids) => costAdjustApi.attach(r.id, ids)}
        onRemove={(fid) => costAdjustApi.removeAttachment(r.id, fid)}
        onRemark={(text) => costAdjustApi.remark(r.id, text)}
        onChanged={load}
      />
      <DocLogCard logs={r.logs} />
    </>
  );

  return (
    <div>
      <PageHeader
        title={r.adjustNo}
        tags={<ReasonPill reason={r.reason} name={r.reasonName} />}
        sub={
          <>
            所属 {r.periodMonth} · {r.adjustDate} 系统生成
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
    </div>
  );
};

export default CostAdjustDetail;
