import {
  CarOutlined,
  CheckOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { Link, useModel } from '@umijs/max';
import {
  App,
  AutoComplete,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Skeleton,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import {
  BoxEditor,
  boxesProblem,
  emptyBox,
  kg,
  LOGISTICS_PATHS,
  ObStatusPill,
  type PackLine,
} from '@/pages/logistics/components';
import {
  type BoxInput,
  type Outbound,
  outboundApi,
  readBizError,
} from '@/pages/logistics/service';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { CARRIERS } from '@/pages/purchase/shipments/ShipmentDrawer';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';

/** 已有装箱记录转成可编辑的箱子 */
export const toInputs = (ob: Outbound): BoxInput[] =>
  ob.boxes.map((b) => ({
    length: b.length,
    width: b.width,
    height: b.height,
    grossWeight: b.grossWeight,
    netWeight: b.netWeight ?? null,
    items: b.items.map((i) => ({
      key: i.outboundItemId,
      quantity: i.quantity,
    })),
  }));

// ---------------------------------------------------------------- 打包

export const PackDrawer: React.FC<{
  id?: number;
  onClose: () => void;
  onDone: () => void;
}> = ({ id, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [ob, setOb] = useState<Outbound>();
  const [error, setError] = useState<string>();
  const [boxes, setBoxes] = useState<BoxInput[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (id === undefined) return;
    setOb(undefined);
    setError(undefined);
    outboundApi
      .detail(id)
      .then((d) => {
        setOb(d);
        const lines = d.items.map((i) => ({
          key: i.id,
          model: i.model,
          quantity: i.quantity,
        }));
        setBoxes(d.boxes.length ? toInputs(d) : [emptyBox(lines, new Map())]);
      })
      .catch((e) => setError(readBizError(e).message));
  }, [id]);

  const lines: PackLine[] = useMemo(
    () =>
      ob?.items.map((i) => ({
        key: i.id,
        model: i.model,
        quantity: i.quantity,
      })) ?? [],
    [ob],
  );
  const problem = ob ? boxesProblem(lines, boxes) : '加载中';

  const submit = async () => {
    if (!ob) return;
    setBusy(true);
    try {
      await outboundApi.pack(ob.id, boxes);
      message.success(`${ob.obNo} 已打包`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Drawer
      open={id !== undefined}
      onClose={onClose}
      size="min(860px, 96vw)"
      destroyOnHidden
      title={
        <span>
          打包{ob ? ` · ${ob.obNo}` : ''}
          {ob && (
            <span
              style={{
                marginLeft: 10,
                fontSize: 13,
                fontWeight: 400,
                color: palette.mute,
              }}
            >
              {ob.soNo} · {ob.customerName} · 货代 {ob.forwarderName}
            </span>
          )}
        </span>
      }
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              fontSize: 12,
              color: problem && ob ? palette.orange : palette.mute,
            }}
          >
            <InfoCircleOutlined />{' '}
            {problem && ob
              ? problem
              : '体积重 = 长 × 宽 × 高 ÷ 5000；计费重取毛重和体积重的大者'}
          </span>
          <span style={{ flex: 1 }} />
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<CheckOutlined />}
            loading={busy}
            disabled={!!problem}
            onClick={submit}
          >
            完成打包
          </Button>
        </div>
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={onClose} />
      ) : !ob ? (
        <Skeleton active />
      ) : (
        <>
          {ob.note && (
            <div style={{ marginBottom: 12, color: palette.sub }}>
              业务员备注：{ob.note}
            </div>
          )}
          <BoxEditor lines={lines} boxes={boxes} onChange={setBoxes} />
        </>
      )}
    </Drawer>
  );
};

// ---------------------------------------------------------------- 交国内快递

/** 按计费重分摊，两位小数，尾差给计费重最大的一张（与后端一致） */
const splitFreight = (freight: number, weights: number[]) => {
  const total = weights.reduce((a, b) => a + b, 0);
  if (!total || !freight) return weights.map(() => 0);
  const shares = weights.map(
    (w) => Math.round((freight * w * 100) / total) / 100,
  );
  const diff =
    Math.round((freight - shares.reduce((a, b) => a + b, 0)) * 100) / 100;
  const max = weights.indexOf(Math.max(...weights));
  shares[max] = Math.round((shares[max] + diff) * 100) / 100;
  return shares;
};

export const HandOverModal: React.FC<{
  open: boolean;
  outbounds: Outbound[];
  onClose: () => void;
  onDone: () => void;
}> = ({ open, outbounds, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const { initialState } = useModel('@@initialState');
  const [carrier, setCarrier] = useState('顺丰');
  const [trackingNo, setTrackingNo] = useState('');
  const [sentDate, setSentDate] = useState<Dayjs>(dayjs());
  const [freight, setFreight] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      setTrackingNo('');
      setSentDate(dayjs());
      setFreight(null);
    }
  }, [open]);

  const weights = outbounds.map((o) => o.chargeableWeight);
  const shares = splitFreight(freight ?? 0, weights);
  const forwarder = outbounds[0]?.forwarderName;

  const submit = async () => {
    setBusy(true);
    try {
      await outboundApi.handOver({
        outboundIds: outbounds.map((o) => o.id),
        carrier: carrier.trim(),
        trackingNo: trackingNo.trim() || undefined,
        sentDate: sentDate.format('YYYY-MM-DD'),
        freight: freight ?? 0,
      });
      message.success('已交货代');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const field = (label: React.ReactNode, node: React.ReactNode) => (
    <div>
      <div style={{ color: palette.sub, marginBottom: 6 }}>{label}</div>
      {node}
    </div>
  );

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={720}
      destroyOnHidden
      title={
        <span>
          <CarOutlined /> 交国内快递{forwarder ? ` · ${forwarder}` : ''}
        </span>
      }
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<CarOutlined />}
            loading={busy}
            disabled={!carrier.trim() || freight === null}
            onClick={submit}
          >
            确认交快递
          </Button>
        </>
      }
    >
      <div style={{ color: palette.sub, marginBottom: 16 }}>
        {outbounds.length > 1
          ? `同一家货代的 ${outbounds.length} 张出库单合成一票寄出，运费按计费重分摊`
          : '一张出库单单独寄出'}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: 12,
          marginBottom: 12,
        }}
      >
        {field(
          '快递公司',
          <AutoComplete
            value={carrier}
            options={CARRIERS.filter((c) => c.value !== '供应商送货')}
            onChange={setCarrier}
            style={{ width: '100%' }}
            aria-label="快递公司"
          />,
        )}
        {field(
          '快递单号',
          <Input
            value={trackingNo}
            onChange={(e) => setTrackingNo(e.target.value)}
            maxLength={64}
            aria-label="快递单号"
          />,
        )}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr 1fr',
          gap: 12,
          marginBottom: 16,
        }}
      >
        {field(
          '发出日期',
          <DatePicker
            value={sentDate}
            allowClear={false}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && setSentDate(d)}
            style={{ width: '100%' }}
            aria-label="发出日期"
          />,
        )}
        {field(
          <span>
            运费 <span style={{ color: palette.red }}>*</span>
          </span>,
          <InputNumber
            value={freight}
            min={0}
            precision={2}
            prefix="¥"
            onChange={(v) => setFreight(v === null ? null : Number(v))}
            style={{ width: '100%' }}
            aria-label="运费"
          />,
        )}
        {field(
          '垫付人',
          <Input
            value={initialState?.currentUser?.name ?? '我'}
            disabled
            aria-label="垫付人"
          />,
        )}
      </div>
      <Table<Outbound>
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={outbounds}
        columns={[
          { title: '出库单', dataIndex: 'obNo' },
          { title: '订单', dataIndex: 'soNo' },
          { title: '箱数', dataIndex: 'boxCount', width: 70 },
          {
            title: '计费重',
            dataIndex: 'chargeableWeight',
            render: (v: number) => kg(v),
          },
          {
            title: '分到运费',
            key: 'share',
            render: (_, __, i) =>
              freight === null ? '—' : formatAmount(shares[i], 'CNY'),
          },
        ]}
      />
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 12 }}>
        <InfoCircleOutlined />{' '}
        登记后出库单为「已交货代」，订单型号自动推进；运费由垫付人以后走报销。
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 查看

export const OutboundView: React.FC<{ id?: number; onClose: () => void }> = ({
  id,
  onClose,
}) => {
  const { palette } = useAppTheme();
  const [ob, setOb] = useState<Outbound>();
  const [error, setError] = useState<string>();
  useEffect(() => {
    if (id === undefined) return;
    setOb(undefined);
    setError(undefined);
    outboundApi
      .detail(id)
      .then(setOb)
      .catch((e) => setError(readBizError(e).message));
  }, [id]);
  return (
    <Drawer
      open={id !== undefined}
      onClose={onClose}
      size="min(760px, 96vw)"
      destroyOnHidden
      title={ob ? `出库单 ${ob.obNo}` : '出库单'}
    >
      {error ? (
        <ErrorHint message={error} onRetry={onClose} />
      ) : !ob ? (
        <Skeleton active />
      ) : (
        <OutboundDetail ob={ob} palette={palette} />
      )}
    </Drawer>
  );
};

export const OutboundDetail: React.FC<{
  ob: Outbound;
  palette: ReturnType<typeof useAppTheme>['palette'];
}> = ({ ob, palette }) => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
    <Descriptions
      size="small"
      column={2}
      items={[
        {
          label: '状态',
          children: (
            <ObStatusPill value={ob.status}>{ob.statusName}</ObStatusPill>
          ),
        },
        { label: '来源', children: ob.sourceName },
        {
          label: '订单',
          children: (
            <Link to={LOGISTICS_PATHS.salesOrder(ob.soId)}>{ob.soNo}</Link>
          ),
        },
        { label: '客户', children: ob.customerName },
        { label: '货代', children: ob.forwarderName },
        { label: '业务员', children: ob.ownerName },
        ...(ob.packedByName
          ? [
              {
                label: '打包',
                children: `${ob.packedByName} · ${formatDateTime(ob.packedAt)}`,
              },
            ]
          : []),
        ...(ob.courier
          ? [
              {
                label: '国内快递',
                children: `${ob.courier.carrier} ${ob.courier.trackingNo ?? ''} · ${ob.courier.sentDate ?? ''}`,
              },
              {
                label: '运费',
                children: `${formatAmount(ob.courier.freight, 'CNY')}${
                  ob.courier.outboundCount > 1
                    ? `（${ob.courier.outboundCount} 单共用，本单 ${formatAmount(ob.courier.share, 'CNY')}）`
                    : ''
                } · 垫付人 ${ob.courier.payerName ?? '—'}`,
              },
            ]
          : []),
        ...(ob.shNo ? [{ label: '出运单', children: ob.shNo }] : []),
        ...(ob.note ? [{ label: '备注', children: ob.note }] : []),
        ...(ob.withdrawReason
          ? [{ label: '撤回原因', children: ob.withdrawReason }]
          : []),
      ]}
    />
    <Table
      rowKey="id"
      size="small"
      pagination={false}
      dataSource={ob.items}
      columns={[
        { title: '型号', dataIndex: 'model' },
        { title: '品牌', dataIndex: 'brand', render: (v?: string) => v || '—' },
        { title: '数量', dataIndex: 'quantity', width: 90 },
      ]}
    />
    {ob.boxes.length > 0 && (
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={ob.boxes}
        columns={[
          { title: '箱号', dataIndex: 'boxNo', width: 60 },
          {
            title: '尺寸（cm）',
            key: 'dim',
            render: (_, b) => `${b.length} × ${b.width} × ${b.height}`,
          },
          {
            title: '毛重',
            dataIndex: 'grossWeight',
            render: (v: number) => kg(v),
          },
          {
            title: '计费重',
            dataIndex: 'chargeable',
            render: (v: number) => kg(v),
          },
          {
            title: '箱内型号',
            key: 'items',
            render: (_, b) =>
              b.items.map((i) => (
                <div key={i.outboundItemId} style={{ color: palette.sub }}>
                  {i.model} × {i.quantity}
                </div>
              )),
          },
        ]}
      />
    )}
  </div>
);
