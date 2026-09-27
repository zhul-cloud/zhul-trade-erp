import {
  App,
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint } from '../../components/EmptyHint';
import { COUNTRY_OPTIONS, normalizeHsCode } from '../../constants';
import {
  type Customs,
  customsApi,
  type Logistics,
  logisticsApi,
  readBizError,
} from '../../service';
import {
  CardEmpty,
  CardShell,
  Field,
  FieldGrid,
  useEditFocus,
  useUnsaved,
} from './CardShell';

const positive = (label: string) => ({
  validator: (_: unknown, v: number | null | undefined) =>
    v === null || v === undefined || v > 0
      ? Promise.resolve()
      : Promise.reject(new Error(`${label}请输入大于 0 的数值`)),
});

const fmt = (v?: number | null, unit = '') =>
  v === null || v === undefined ? '' : `${v} ${unit}`.trim();

/** 物流与海关：两张相邻的表单卡片，各自保存（接口也是分开的） */
export const LogisticsCustomsCard: React.FC<{
  productId: number;
  readOnly: boolean;
  logisticsDone?: boolean;
  customsDone?: boolean;
  onChanged: () => void;
}> = ({ productId, readOnly, logisticsDone, customsDone, onChanged }) => {
  const { message } = App.useApp();
  const [lForm] = Form.useForm<Logistics>();
  const [cForm] = Form.useForm<Customs>();
  const [logistics, setLogistics] = useState<Logistics>();
  const [customs, setCustoms] = useState<Customs>();
  const [error, setError] = useState<string>();
  const [editL, setEditL] = useState(false);
  const [editC, setEditC] = useState(false);
  const [dirtyL, setDirtyL] = useState(false);
  const [dirtyC, setDirtyC] = useState(false);
  const [savingL, setSavingL] = useState(false);
  const [savingC, setSavingC] = useState(false);
  const hs = Form.useWatch('hsCode', cForm);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const [l, c] = await Promise.all([
        logisticsApi.get(productId),
        customsApi.get(productId),
      ]);
      setLogistics(l);
      setCustoms(c);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [productId]);

  useEffect(() => {
    load();
  }, [load]);

  const saveL = async () => {
    const v = await lForm.validateFields();
    setSavingL(true);
    try {
      setLogistics(
        await logisticsApi.save(productId, {
          ...v,
          isDangerous: v.isDangerous ? 1 : 0,
        }),
      );
      message.success('物流信息已保存');
      setEditL(false);
      setDirtyL(false);
      onChanged();
    } finally {
      setSavingL(false);
    }
  };
  const saveC = async () => {
    const v = await cForm.validateFields();
    setSavingC(true);
    try {
      setCustoms(
        await customsApi.save(productId, {
          ...v,
          hsCode: normalizeHsCode(v.hsCode ?? ''),
        }),
      );
      message.success('海关信息已保存');
      setEditC(false);
      setDirtyC(false);
      onChanged();
    } finally {
      setSavingC(false);
    }
  };

  useEditFocus('card-logistics', editL);
  useEditFocus('card-customs', editC);
  useUnsaved(
    'logistics',
    editL && dirtyL
      ? {
          save: saveL,
          discard: () => {
            setEditL(false);
            setDirtyL(false);
          },
        }
      : null,
  );
  useUnsaved(
    'customs',
    editC && dirtyC
      ? {
          save: saveC,
          discard: () => {
            setEditC(false);
            setDirtyC(false);
          },
        }
      : null,
  );

  const num = (
    name: keyof Logistics,
    label: string,
    unit: string,
    precision: number,
  ) => (
    <Form.Item name={name} label={label} rules={[positive(label)]}>
      <InputNumber
        style={{ width: '100%' }}
        precision={precision}
        suffix={unit}
        controls={false}
        aria-label={`${label}（${unit}）`}
      />
    </Form.Item>
  );

  const hsClean = normalizeHsCode(hs ?? '');
  const hsInvalid = hsClean !== '' && !/^\d{6,10}$/.test(hsClean);

  if (error) {
    return (
      <CardShell id="card-logistics" title="物流与海关">
        <EmptyHint
          title="加载失败"
          description={error}
          actionText="重试"
          onAction={load}
        />
      </CardShell>
    );
  }

  return (
    <>
      <CardShell
        id="card-logistics"
        title="物流信息"
        done={logisticsDone}
        actions={
          !readOnly &&
          !editL && (
            <Button
              onClick={() => {
                lForm.setFieldsValue({
                  ...logistics,
                  isDangerous: logistics?.isDangerous ?? 0,
                });
                setDirtyL(false);
                setEditL(true);
              }}
            >
              {logisticsDone ? '编辑' : '填写'}
            </Button>
          )
        }
      >
        {editL ? (
          <Form
            form={lForm}
            layout="vertical"
            onValuesChange={() => setDirtyL(true)}
            requiredMark={false}
          >
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
                gap: '0 24px',
              }}
            >
              {num('netWeightKg', '净重', 'kg', 3)}
              <Form.Item
                name="grossWeightKg"
                label="毛重（含包装）"
                dependencies={['netWeightKg']}
                rules={[
                  positive('毛重'),
                  ({ getFieldValue }) => ({
                    validator: (_, v) => {
                      const net = getFieldValue('netWeightKg');
                      return v != null && net != null && v < net
                        ? Promise.reject(new Error('毛重不能小于净重'))
                        : Promise.resolve();
                    },
                  }),
                ]}
              >
                <InputNumber
                  style={{ width: '100%' }}
                  precision={3}
                  suffix="kg"
                  controls={false}
                  aria-label="毛重（kg）"
                />
              </Form.Item>
              {num('lengthMm', '单品长', 'mm', 1)}
              {num('widthMm', '单品宽', 'mm', 1)}
              {num('heightMm', '单品高', 'mm', 1)}
              <Form.Item name="packageType" label="包装类型">
                <Input maxLength={32} placeholder="盒装 / 箱装 / 托盘" />
              </Form.Item>
              {num('packageLengthMm', '包装长', 'mm', 1)}
              {num('packageWidthMm', '包装宽', 'mm', 1)}
              {num('packageHeightMm', '包装高', 'mm', 1)}
              <Form.Item
                name="packageQuantity"
                label="每包装件数"
                rules={[
                  { type: 'integer', min: 1, message: '请输入大于 0 的整数' },
                ]}
              >
                <InputNumber
                  style={{ width: '100%' }}
                  precision={0}
                  min={1}
                  controls={false}
                />
              </Form.Item>
            </div>
            <Form.Item name="isDangerous" valuePropName="checked">
              <Checkbox>危险品或含电池等限运品</Checkbox>
            </Form.Item>
            <Form.Item name="shippingNote" label="运输备注">
              <Input maxLength={255} placeholder="如 需防潮、含锂电池" />
            </Form.Item>
            <Space>
              <Button type="primary" loading={savingL} onClick={saveL}>
                保存
              </Button>
              <Button
                onClick={() => {
                  setEditL(false);
                  setDirtyL(false);
                }}
              >
                取消
              </Button>
            </Space>
          </Form>
        ) : !logisticsDone && !logistics?.id ? (
          <CardEmpty
            benefit="重量尺寸用来估运费和订舱，没填的项会保持为空，不会当成 0。"
            actionText={readOnly ? undefined : '填写重量尺寸'}
            onAction={() => {
              lForm.setFieldsValue({ isDangerous: 0 });
              setEditL(true);
            }}
          />
        ) : (
          <FieldGrid>
            <Field label="净重">{fmt(logistics?.netWeightKg, 'kg')}</Field>
            <Field label="毛重">{fmt(logistics?.grossWeightKg, 'kg')}</Field>
            <Field label="单品尺寸（长×宽×高）">
              {[
                logistics?.lengthMm,
                logistics?.widthMm,
                logistics?.heightMm,
              ].some((v) => v != null)
                ? `${logistics?.lengthMm ?? '?'} × ${logistics?.widthMm ?? '?'} × ${logistics?.heightMm ?? '?'} mm`
                : ''}
            </Field>
            <Field label="包装类型">{logistics?.packageType}</Field>
            <Field label="包装尺寸">
              {[
                logistics?.packageLengthMm,
                logistics?.packageWidthMm,
                logistics?.packageHeightMm,
              ].some((v) => v != null)
                ? `${logistics?.packageLengthMm ?? '?'} × ${logistics?.packageWidthMm ?? '?'} × ${logistics?.packageHeightMm ?? '?'} mm`
                : ''}
            </Field>
            <Field label="每包装件数">
              {logistics?.packageQuantity != null
                ? String(logistics.packageQuantity)
                : ''}
            </Field>
            <Field label="限运品">
              {logistics?.isDangerous ? '是（危险品或含电池等）' : '否'}
            </Field>
            <Field label="运输备注">{logistics?.shippingNote}</Field>
          </FieldGrid>
        )}
      </CardShell>

      <CardShell
        id="card-customs"
        title="海关信息"
        done={customsDone}
        actions={
          !readOnly &&
          !editC && (
            <Button
              onClick={() => {
                cForm.setFieldsValue({ ...customs });
                setDirtyC(false);
                setEditC(true);
              }}
            >
              {customsDone ? '编辑' : '填写'}
            </Button>
          )
        }
      >
        {editC ? (
          <Form
            form={cForm}
            layout="vertical"
            onValuesChange={() => setDirtyC(true)}
            requiredMark={false}
          >
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
                gap: '0 24px',
              }}
            >
              <Form.Item
                name="hsCode"
                label="HS 编码"
                validateTrigger="onBlur"
                extra={
                  hsClean && !hsInvalid ? (
                    <span>
                      将保存为 <b className="num">{hsClean}</b>
                    </span>
                  ) : (
                    '可以带点或空格，如 8537.10.90'
                  )
                }
                rules={[
                  {
                    validator: (_, v) =>
                      !v || /^\d{6,10}$/.test(normalizeHsCode(v))
                        ? Promise.resolve()
                        : Promise.reject(new Error('HS 编码应为 6–10 位数字')),
                  },
                ]}
              >
                <Input maxLength={20} />
              </Form.Item>
              <Form.Item
                name="originCountry"
                label="默认原产国"
                extra="同一型号不同批次可能不同，实际以货物单据为准"
              >
                <Select
                  allowClear
                  showSearch={{ optionFilterProp: 'label' }}
                  options={COUNTRY_OPTIONS}
                  placeholder="请选择国家"
                />
              </Form.Item>
              <Form.Item name="customsNameCn" label="申报品名（中文）">
                <Input maxLength={128} />
              </Form.Item>
              <Form.Item name="customsNameEn" label="申报品名（英文）">
                <Input maxLength={128} />
              </Form.Item>
              <Form.Item name="supervisionConditions" label="监管条件">
                <Input maxLength={32} placeholder="如 A / B，没有可留空" />
              </Form.Item>
              <Form.Item
                name="exportRebateRate"
                label="出口退税率"
                extra="政策会调整，以最近一次维护为准"
                rules={[
                  {
                    validator: (_, v) =>
                      v == null || (v >= 0 && v <= 100)
                        ? Promise.resolve()
                        : Promise.reject(new Error('退税率应在 0 到 100 之间')),
                  },
                ]}
              >
                <InputNumber
                  style={{ width: '100%' }}
                  precision={2}
                  min={0}
                  max={100}
                  suffix="%"
                  controls={false}
                />
              </Form.Item>
            </div>
            <Form.Item name="declarationElements" label="申报要素">
              <Input.TextArea rows={2} maxLength={500} showCount />
            </Form.Item>
            <Space>
              <Button type="primary" loading={savingC} onClick={saveC}>
                保存
              </Button>
              <Button
                onClick={() => {
                  setEditC(false);
                  setDirtyC(false);
                }}
              >
                取消
              </Button>
            </Space>
          </Form>
        ) : !customsDone && !customs?.id ? (
          <CardEmpty
            benefit="HS 编码和申报品名是报关必填项，提前填好能少走一遍确认。"
            actionText={readOnly ? undefined : '填写 HS 编码'}
            onAction={() => setEditC(true)}
          />
        ) : (
          <FieldGrid>
            <Field label="HS 编码">
              <span className="num">{customs?.hsCode}</span>
            </Field>
            <Field label="默认原产国">{customs?.originCountry}</Field>
            <Field label="申报品名（中文）">{customs?.customsNameCn}</Field>
            <Field label="申报品名（英文）">{customs?.customsNameEn}</Field>
            <Field label="监管条件">{customs?.supervisionConditions}</Field>
            <Field label="出口退税率">
              <span className="num">{fmt(customs?.exportRebateRate, '%')}</span>
            </Field>
            <Field label="申报要素">{customs?.declarationElements}</Field>
          </FieldGrid>
        )}
      </CardShell>
    </>
  );
};
