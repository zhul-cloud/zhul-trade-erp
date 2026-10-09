import { AuditOutlined, InfoCircleOutlined } from '@ant-design/icons';
import {
  App,
  AutoComplete,
  Checkbox,
  Input,
  InputNumber,
  Modal,
  Radio,
} from 'antd';
import React, { useEffect, useState } from 'react';
import { AttachmentWall } from '@/pages/warehouse/components';
import {
  type Attachment,
  type Discrepancy,
  discrepancyApi,
  type HandleDiscrepancyBody,
  readBizError,
} from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { CARRIERS } from './ShipmentDrawer';

/** 差异类型 → 可选的处理方式与说明（与后端 WarehouseConstants.RESOLUTIONS_BY_TYPE 一致） */
const OPTIONS: Record<
  number,
  { value: number; label: string; desc: (n: number) => string }[]
> = {
  1: [
    {
      value: 1,
      label: '等补发',
      desc: (n) => `供应商会补发；这 ${n} 个计为未发，补发时再登记发货`,
    },
    {
      value: 2,
      label: '不补了',
      desc: (n) => `采购单数量减 ${n}，回到采购需求另找渠道`,
    },
  ],
  2: [
    {
      value: 3,
      label: '退货换货',
      desc: (n) => `不良的 ${n} 个退回，供应商补发；这 ${n} 个重新算作未发`,
    },
    {
      value: 4,
      label: '退货不补',
      desc: (n) => `退回后不再补发，采购单数量减 ${n}，回到采购需求另找渠道`,
    },
    {
      value: 5,
      label: '折价接收',
      desc: () => '按合格入库，记录折价金额（第③期计入应付）',
    },
  ],
  3: [
    {
      value: 6,
      label: '退回供应商',
      desc: (n) => `多发的 ${n} 个寄回，不入库`,
    },
    {
      value: 7,
      label: '暂存',
      desc: (n) => `多发的 ${n} 个留在仓库，进入暂存货`,
    },
  ],
};
const RETURNS = [3, 4, 6];

const HandleDiscrepancyModal: React.FC<{
  target?: Discrepancy;
  onClose: () => void;
  onDone: () => void;
}> = ({ target, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [body, setBody] = useState<HandleDiscrepancyBody>({ resolution: 0 });
  const [evidence, setEvidence] = useState<{
    note?: string;
    attachments: Attachment[];
  }>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!target) return;
    setBody({ resolution: 0 });
    setEvidence(undefined);
    discrepancyApi
      .evidence(target.id)
      .then(setEvidence)
      .catch(() => setEvidence({ attachments: [] }));
  }, [target]);

  if (!target) return null;
  const r = body.resolution;
  const set = (patch: Partial<HandleDiscrepancyBody>) =>
    setBody((b) => ({ ...b, ...patch }));
  const invalid = !r || (r === 5 && body.discountAmount == null);

  return (
    <Modal
      open
      width={620}
      title={
        <span>
          <AuditOutlined style={{ color: palette.link, marginRight: 8 }} />
          处理到货差异 · {target.typeName} {target.quantity}
        </span>
      }
      okText="确定处理"
      okButtonProps={{ disabled: invalid, loading: busy }}
      onCancel={onClose}
      onOk={async () => {
        setBusy(true);
        try {
          await discrepancyApi.handle(target.id, body);
          message.success('已处理');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <div style={{ color: palette.sub, marginBottom: 12 }}>
        {target.model} · {target.brand} · {target.poNo} · {target.grNo}
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
          marginBottom: 14,
        }}
      >
        {evidence && (
          <AttachmentWall
            ownerType="RECEIPT"
            value={evidence.attachments}
            empty="仓库没有上传验收照片"
          />
        )}
        {evidence?.note && (
          <span style={{ fontSize: 13, color: palette.sub }}>
            仓库说明：{evidence.note}
          </span>
        )}
      </div>

      <Radio.Group
        value={r || undefined}
        onChange={(e) => set({ resolution: e.target.value })}
        style={{ display: 'grid', gap: 10, width: '100%' }}
      >
        {(OPTIONS[target.type] ?? []).map((o) => (
          <Radio
            key={o.value}
            value={o.value}
            style={{
              width: '100%',
              margin: 0,
              padding: '12px 14px',
              borderRadius: 10,
              border: `1px solid ${r === o.value ? palette.link : palette.hairline}`,
              background: r === o.value ? palette.accentSoft : palette.inset,
            }}
          >
            <b style={{ color: palette.ink }}>{o.label}</b>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {o.desc(target.quantity)}
            </div>
          </Radio>
        ))}
      </Radio.Group>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
          gap: 12,
          marginTop: 14,
        }}
      >
        {r === 5 && (
          <div>
            <div style={{ color: palette.sub, marginBottom: 6 }}>
              折价金额 <span style={{ color: palette.red }}>*</span>
            </div>
            <InputNumber
              min={0}
              precision={2}
              prefix={target.currencyCode}
              value={body.discountAmount}
              onChange={(v) => set({ discountAmount: v })}
              style={{ width: '100%' }}
              aria-label="折价金额"
            />
          </div>
        )}
        {RETURNS.includes(r) && (
          <>
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                退货快递
              </div>
              <AutoComplete
                options={CARRIERS}
                value={body.returnCarrier}
                onChange={(v) => set({ returnCarrier: v })}
                placeholder="选填"
                style={{ width: '100%' }}
                aria-label="退货快递公司"
              />
            </div>
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                退货单号
              </div>
              <Input
                maxLength={64}
                value={body.returnTrackingNo}
                onChange={(e) => set({ returnTrackingNo: e.target.value })}
                placeholder="选填"
                aria-label="退货快递单号"
              />
            </div>
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>运费</div>
              <InputNumber
                min={0}
                precision={2}
                prefix="¥"
                value={body.returnFreight}
                onChange={(v) => set({ returnFreight: v })}
                placeholder="选填"
                style={{ width: '100%' }}
                aria-label="退货运费"
              />
            </div>
          </>
        )}
        {r === 7 && (
          <>
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                位置备注
              </div>
              <Input
                maxLength={100}
                value={body.locationNote}
                onChange={(e) => set({ locationNote: e.target.value })}
                placeholder="如 2 号架"
                aria-label="位置备注"
              />
            </div>
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-end',
                paddingBottom: 6,
              }}
            >
              <Checkbox
                checked={body.freeOfCharge}
                onChange={(e) => set({ freeOfCharge: e.target.checked })}
              >
                供应商白送（成本记 ¥0）
              </Checkbox>
            </div>
          </>
        )}
        <div style={{ gridColumn: '1 / -1' }}>
          <div style={{ color: palette.sub, marginBottom: 6 }}>说明</div>
          <Input
            maxLength={300}
            value={body.note}
            onChange={(e) => set({ note: e.target.value })}
            placeholder="如：供应商同意每个让 50 元"
            aria-label="处理说明"
          />
        </div>
      </div>
      <div
        style={{
          marginTop: 14,
          padding: '10px 12px',
          borderRadius: 8,
          background: palette.inset,
          fontSize: 12,
          color: palette.mute,
        }}
      >
        <InfoCircleOutlined />{' '}
        处理后采购单与订单进度随之更新；处理后不能直接改，需要更正时点「重新打开」。
      </div>
    </Modal>
  );
};

export default HandleDiscrepancyModal;
