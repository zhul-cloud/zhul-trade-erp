import {
  CarOutlined,
  CheckOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { useModel } from '@umijs/max';
import {
  App,
  AutoComplete,
  Button,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Skeleton,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  BoxEditor,
  boxesProblem,
  emptyBox,
  kg,
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
import {
  ACCEPT_IMAGE,
  AttachmentWall,
  attachmentModels,
  type ModelTagMap,
  ModelTags,
} from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';

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
  /** 打包完成；packedId 是完成的那张单（可能已不是抽屉当前的单） */
  onDone: (packedId: number) => void;
}> = ({ id, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  /** 抽屉当前打开的单：保存进行中换了单，旧单的结果不能动新单的按钮状态 */
  const current = useRef(id);
  current.current = id;
  const [ob, setOb] = useState<Outbound>();
  const [error, setError] = useState<string>();
  const [boxes, setBoxes] = useState<BoxInput[]>([]);
  const [busy, setBusy] = useState(false);
  const [photos, setPhotos] = useState<Attachment[]>([]);
  const [tags, setTags] = useState<ModelTagMap>({});

  useEffect(() => {
    if (id === undefined) return;
    // 关闭或换单后，上一单迟到的回包作废，不能改写当前抽屉（UI-017）
    let alive = true;
    setOb(undefined);
    setTags({});
    setError(undefined);
    setBusy(false);
    outboundApi
      .detail(id)
      .then((d) => {
        if (!alive) return;
        setOb(d);
        setPhotos(d.packingAttachments ?? []);
        const lines = d.items.map((i) => ({
          key: i.id,
          model: i.model,
          quantity: i.quantity,
        }));
        setBoxes(d.boxes.length ? toInputs(d) : [emptyBox(lines, new Map())]);
      })
      .catch((e) => {
        if (alive) setError(readBizError(e).message);
      });
    return () => {
      alive = false;
    };
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
    // 只提交本次打开的单据
    if (!ob || ob.id !== id) return;
    const target = ob.id;
    setBusy(true);
    try {
      await outboundApi.pack(target, boxes, {
        attachmentIds: photos.map((p) => p.id),
        attachmentModels: attachmentModels(photos, tags),
      });
      message.success(`${ob.obNo} 已打包`);
      onDone(target);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      if (current.current === target) setBusy(false);
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
          {(ob.missingMarketing?.length ?? 0) > 0 && (
            <div
              style={{
                marginBottom: 12,
                padding: '8px 12px',
                borderRadius: 10,
                background: palette.orangeSoft,
                color: palette.orange,
                fontSize: 13,
              }}
            >
              <InfoCircleOutlined /> 以下型号还没有营销实拍：
              {ob.missingMarketing?.join('、')}
              。货发走前可以请拍摄人员补拍，不影响打包。
            </div>
          )}
          <BoxEditor lines={lines} boxes={boxes} onChange={setBoxes} />
          <div
            style={{
              margin: '18px 0 8px',
              color: palette.ink,
              fontWeight: 600,
            }}
          >
            打包发货照片
            <span
              style={{
                marginLeft: 8,
                fontSize: 12,
                fontWeight: 400,
                color: palette.mute,
              }}
            >
              选填；发给客户前的打包照片，进素材库「打包发货」，可以发给客户
            </span>
          </div>
          <AttachmentWall
            ownerType="PACKING"
            value={photos}
            onChange={setPhotos}
            accept={ACCEPT_IMAGE}
          />
          <ModelTags
            files={photos}
            models={ob.items}
            value={tags}
            onChange={setTags}
          />
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
          ? `同一家货代的 ${outbounds.length} 张销售出库单合成一票寄出，运费按计费重分摊`
          : '一张销售出库单单独寄出'}
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
            prefix="CNY"
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
          { title: '销售出库单', dataIndex: 'obNo' },
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
        登记后销售出库单为「已交货代」，订单型号自动推进；运费由垫付人以后走报销。
      </div>
    </Modal>
  );
};
