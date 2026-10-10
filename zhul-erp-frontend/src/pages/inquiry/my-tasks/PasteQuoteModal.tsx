import {
  ArrowLeftOutlined,
  CheckOutlined,
  InfoCircleOutlined,
  ScanOutlined,
  SnippetsOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Checkbox,
  Input,
  InputNumber,
  Modal,
  Select,
  Table,
} from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { Pill, useQuoteDicts } from '../shared/components';
import {
  CHANNEL_OPTIONS,
  CHANNEL_SUPPLIER,
  CHANNEL_TAOBAO,
  channelLabel,
} from '../shared/constants';
import {
  myTaskApi,
  type PasteRow,
  type QuoteEntry,
  readBizError,
} from '../shared/service';
import SupplierSelect from './SupplierSelect';

/** 预览行：识别结果 + 采购的修改 */
interface Row extends PasteRow {
  key: number;
}

export interface PasteFill {
  itemId: number;
  entry: QuoteEntry;
}

const STATUS: Record<
  PasteRow['status'],
  { label: string; tone: 'green' | 'orange' | 'red' }
> = {
  MATCHED: { label: '已识别', tone: 'green' },
  UNMATCHED: { label: '未匹配', tone: 'orange' },
  NO_PRICE: { label: '缺单价', tone: 'red' },
};

/** 一行能不能填：有型号、有单价、型号没报给客户 */
const ready = (r: Row) =>
  !!r.itemId && r.unitPrice != null && r.unitPrice > 0 && !r.locked;

/** 粘贴报价：选渠道店铺 → 粘贴原文识别 → 预览可改 → 填进草稿（不保存） */
const PasteQuoteModal: React.FC<{
  open: boolean;
  taskId: number;
  taskCode: string;
  items: { id: number; model: string; locked?: boolean }[];
  onClose: () => void;
  onFill: (fills: PasteFill[], source: string) => void;
}> = ({ open, taskId, taskCode, items, onClose, onFill }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const { conditionOptions, leadTimeOptions } = useQuoteDicts();
  const [channel, setChannel] = useState(CHANNEL_TAOBAO);
  const [shopName, setShopName] = useState('');
  const [supplierId, setSupplierId] = useState<number>();
  const [text, setText] = useState('');
  const [rows, setRows] = useState<Row[] | null>(null);
  const [common, setCommon] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) setRows(null);
  }, [open]);

  const source = `${channelLabel(channel)} · ${shopName.trim() || '—'}`;
  const canParse =
    !!text.trim() &&
    (channel === CHANNEL_SUPPLIER ? !!supplierId : !!shopName.trim());

  const parse = async () => {
    setBusy(true);
    try {
      const r = await myTaskApi.pastePreview(taskId, text);
      setRows(r.rows.map((x, i) => ({ ...x, key: i })));
      setCommon(r.common);
      if (r.rows.length === 0)
        message.info('没有认出带型号或价格的行，请检查原文');
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const patch = (key: number, p: Partial<Row>) =>
    setRows((rs) =>
      (rs ?? []).map((r) => (r.key === key ? { ...r, ...p } : r)),
    );

  const fillable = (rows ?? []).filter(ready);
  const countOf = (k: PasteRow['status']) =>
    (rows ?? []).filter((r) => r.status === k).length;
  const dupModels = new Set(
    fillable.map((r) => r.itemId).filter((id, i, all) => all.indexOf(id) !== i),
  );

  const fill = () => {
    onFill(
      fillable.map((r) => ({
        itemId: r.itemId as number,
        entry: {
          channel,
          shopName: shopName.trim(),
          supplierId: channel === CHANNEL_SUPPLIER ? supplierId : undefined,
          unitPrice: r.unitPrice,
          taxIncluded: r.taxIncluded,
          taxRate: r.taxIncluded ? (r.taxRate ?? 13) : undefined,
          itemCondition: r.itemCondition || undefined,
          leadTime: r.leadTime || undefined,
          note: r.note || undefined,
        },
      })),
      source,
    );
  };

  const modelOptions = items
    .filter((i) => !i.locked)
    .map((i) => ({ value: i.id, label: i.model }));

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={rows ? 1160 : 720}
      destroyOnHidden
      title={
        <span>
          {rows ? <ScanOutlined /> : <SnippetsOutlined />}{' '}
          {rows ? `识别结果 · ${source}` : `粘贴报价 · ${taskCode}`}
        </span>
      }
      footer={
        rows ? (
          <>
            <Button icon={<ArrowLeftOutlined />} onClick={() => setRows(null)}>
              返回修改原文
            </Button>
            <Button
              type="primary"
              icon={<CheckOutlined />}
              disabled={fillable.length === 0}
              onClick={fill}
            >
              填入 {fillable.length} 条报价
            </Button>
          </>
        ) : (
          <>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              icon={<ScanOutlined />}
              loading={busy}
              disabled={!canParse}
              onClick={parse}
            >
              识别
            </Button>
          </>
        )
      }
    >
      {!rows ? (
        <div style={{ display: 'grid', gap: 14 }}>
          <div style={{ color: palette.sub }}>
            一次粘贴一家店铺或供应商的回复；只在本任务的 {items.length}{' '}
            个型号里匹配。
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '160px 1fr',
              gap: 12,
            }}
          >
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                渠道 <span style={{ color: palette.red }}>*</span>
              </div>
              <Select
                value={channel}
                options={CHANNEL_OPTIONS}
                onChange={(v) => {
                  setChannel(v);
                  setSupplierId(undefined);
                }}
                style={{ width: '100%' }}
                aria-label="渠道"
              />
            </div>
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                {channel === CHANNEL_SUPPLIER ? '供应商' : '店铺名称'}{' '}
                <span style={{ color: palette.red }}>*</span>
              </div>
              {channel === CHANNEL_SUPPLIER ? (
                <SupplierSelect
                  value={supplierId}
                  label={shopName}
                  onChange={(id, name) => {
                    setSupplierId(id);
                    setShopName(name);
                  }}
                />
              ) : (
                <Input
                  value={shopName}
                  maxLength={100}
                  onChange={(e) => setShopName(e.target.value)}
                  placeholder="如 施耐德工控旗舰店"
                  aria-label="店铺名称"
                />
              )}
            </div>
          </div>
          <div>
            <div style={{ color: palette.sub, marginBottom: 6 }}>
              店家回复原文 <span style={{ color: palette.red }}>*</span>
            </div>
            <Input.TextArea
              value={text}
              rows={8}
              maxLength={10000}
              onChange={(e) => setText(e.target.value)}
              placeholder={
                'LXM32AD30N4，要 1 台  3140  现货\nBMH1003P16A2A  2562  5周\n未税包邮 全新原装'
              }
              aria-label="店家回复原文"
            />
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
              每行一个型号；「要 2 台」这类数量会被排除；「未税包邮
              全新原装」这类没有型号的说明会套用到所有行。
            </div>
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 14 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              flexWrap: 'wrap',
            }}
          >
            {(['MATCHED', 'UNMATCHED', 'NO_PRICE'] as const).map(
              (k) =>
                countOf(k) > 0 && (
                  <Pill key={k} tone={STATUS[k].tone}>
                    {STATUS[k].label} {countOf(k)}
                  </Pill>
                ),
            )}
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: palette.mute }}>
              每格都可以改；没有型号或单价的行不会填入
            </span>
          </div>
          {common && (
            <div
              style={{
                padding: '10px 12px',
                borderRadius: 10,
                background: palette.inset,
                color: palette.sub,
                fontSize: 12,
              }}
            >
              <InfoCircleOutlined /> 整段说明「{common}
              」：已套用到没写明含税、货况、货期的行，原文写进每行备注。
            </div>
          )}
          <Table<Row>
            rowKey="key"
            size="small"
            pagination={false}
            dataSource={rows}
            scroll={{ x: 1060, y: 420 }}
            locale={{ emptyText: '没有认出带型号或价格的行' }}
            columns={[
              {
                title: '原文',
                dataIndex: 'raw',
                width: 240,
                render: (v: string) => (
                  <span style={{ fontSize: 12, color: palette.sub }}>{v}</span>
                ),
              },
              {
                title: '对应型号',
                key: 'model',
                width: 200,
                render: (_, r) => (
                  <Select
                    value={r.itemId ?? undefined}
                    allowClear
                    placeholder="选择任务型号"
                    options={modelOptions}
                    status={!r.itemId ? 'warning' : undefined}
                    onChange={(v?: number) =>
                      patch(r.key, { itemId: v ?? null })
                    }
                    style={{ width: 180 }}
                    aria-label={`${r.raw} 对应型号`}
                  />
                ),
              },
              {
                title: '单价 ¥',
                key: 'price',
                width: 120,
                render: (_, r) => (
                  <InputNumber
                    value={r.unitPrice ?? null}
                    min={0}
                    precision={2}
                    placeholder="填单价"
                    status={r.unitPrice == null ? 'error' : undefined}
                    onChange={(v) =>
                      patch(r.key, { unitPrice: v === null ? null : Number(v) })
                    }
                    style={{ width: 106 }}
                    aria-label={`${r.raw} 单价`}
                  />
                ),
              },
              {
                title: '含税',
                key: 'tax',
                width: 60,
                render: (_, r) => (
                  <Checkbox
                    checked={r.taxIncluded}
                    onChange={(e) =>
                      patch(r.key, { taxIncluded: e.target.checked })
                    }
                    aria-label={`${r.raw} 含税`}
                  />
                ),
              },
              {
                title: '货况',
                key: 'cond',
                width: 130,
                render: (_, r) => (
                  <Select
                    value={r.itemCondition || undefined}
                    options={conditionOptions}
                    onChange={(v: number) => patch(r.key, { itemCondition: v })}
                    style={{ width: 118 }}
                    aria-label={`${r.raw} 货况`}
                  />
                ),
              },
              {
                title: '货期',
                key: 'lead',
                width: 120,
                render: (_, r) => (
                  <Select
                    value={r.leadTime || undefined}
                    allowClear
                    placeholder="—"
                    options={leadTimeOptions}
                    onChange={(v?: number) =>
                      patch(r.key, { leadTime: v ?? 0 })
                    }
                    style={{ width: 108 }}
                    aria-label={`${r.raw} 货期`}
                  />
                ),
              },
              {
                title: '状态',
                key: 'status',
                width: 130,
                render: (_, r) =>
                  r.locked ? (
                    <Pill tone="violet">已报给客户，不能改</Pill>
                  ) : ready(r) ? (
                    <span
                      style={{
                        display: 'inline-flex',
                        gap: 4,
                        flexWrap: 'wrap',
                      }}
                    >
                      <Pill tone="green">将填入</Pill>
                      {dupModels.has(r.itemId) && (
                        <Pill tone="orange">同型号多行</Pill>
                      )}
                    </span>
                  ) : (
                    <Pill tone={!r.itemId ? 'orange' : 'red'}>
                      {!r.itemId ? '未匹配' : '缺单价'}
                    </Pill>
                  ),
              },
            ]}
          />
          <div style={{ fontSize: 12, color: palette.mute }}>
            <InfoCircleOutlined />{' '}
            同一渠道、同一店铺已有的草稿会被覆盖，其余追加；填入后不会自动保存，核对后点「保存草稿」或「提交回价」。
          </div>
        </div>
      )}
    </Modal>
  );
};

export default PasteQuoteModal;
