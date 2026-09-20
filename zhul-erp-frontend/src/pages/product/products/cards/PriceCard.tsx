import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint } from '../../components/EmptyHint';
import { CURRENCY_OPTIONS } from '../../constants';
import { priceApi, type ReferencePrice, readBizError } from '../../service';
import { useProductTheme } from '../../theme';
import {
  CardEmpty,
  CardShell,
  Field,
  FieldGrid,
  useEditFocus,
  useUnsaved,
} from './CardShell';

const money = (v?: number | null) =>
  v === null || v === undefined
    ? ''
    : v.toLocaleString('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });

/** 平台参考价：平台层面的参考信息，不是任何租户的报价或售价 */
export const PriceCard: React.FC<{
  productId: number;
  readOnly: boolean;
  done?: boolean;
  onChanged: () => void;
}> = ({ productId, readOnly, done, onChanged }) => {
  const { message, modal } = App.useApp();
  const { palette } = useProductTheme();
  const [form] = Form.useForm<ReferencePrice & { date?: dayjs.Dayjs | null }>();
  const [price, setPrice] = useState<ReferencePrice>();
  const [error, setError] = useState<string>();
  const [editing, setEditing] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const currency = Form.useWatch('currencyCode', form);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setPrice(await priceApi.get(productId));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [productId]);

  useEffect(() => {
    load();
  }, [load]);

  const has = !!price?.id;

  const startEdit = () => {
    form.setFieldsValue({
      ...price,
      currencyCode: price?.currencyCode ?? 'USD',
      date: price?.priceDate ? dayjs(price.priceDate) : null,
    });
    setDirty(false);
    setEditing(true);
  };

  const save = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      const saved = await priceApi.save(productId, {
        priceOriginal: v.priceOriginal,
        currencyCode: v.currencyCode,
        exchangeRate: v.currencyCode === 'CNY' ? null : v.exchangeRate,
        priceSource: v.priceSource ?? '',
        priceDate: v.date ? v.date.format('YYYY-MM-DD') : null,
      });
      setPrice(saved);
      message.success('参考价已保存');
      if (saved.currencyCode !== 'CNY' && saved.priceCny == null)
        message.info('汇率未维护，本位币金额暂未计算');
      setEditing(false);
      setDirty(false);
      onChanged();
    } finally {
      setSaving(false);
    }
  };

  useEditFocus('card-price', editing);
  useUnsaved(
    'price',
    editing && dirty
      ? {
          save,
          discard: () => {
            setEditing(false);
            setDirty(false);
          },
        }
      : null,
  );

  const clear = () =>
    modal.confirm({
      title: '清除参考价？',
      content: '清除后商品回到"未维护参考价"，之后可以重新填写。',
      okText: '清除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await priceApi.clear(productId);
        message.success('已清除');
        setPrice(undefined);
        await load();
        onChanged();
      },
    });

  return (
    <CardShell
      id="card-price"
      title="平台参考价"
      done={done}
      actions={
        !readOnly &&
        !editing &&
        has && (
          <Space>
            <Button onClick={startEdit}>编辑</Button>
            <Button danger onClick={clear}>
              清除
            </Button>
          </Space>
        )
      }
    >
      <div style={{ marginBottom: 12, fontSize: 13, color: palette.sub }}>
        这是平台参考价，不是任何租户的报价或售价。
      </div>
      {error ? (
        <EmptyHint
          title="加载失败"
          description={error}
          actionText="重试"
          onAction={load}
        />
      ) : editing ? (
        <Form
          form={form}
          layout="vertical"
          onValuesChange={() => setDirty(true)}
          requiredMark={false}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
              gap: '0 24px',
            }}
          >
            <Form.Item
              name="currencyCode"
              label="币种"
              rules={[{ required: true, message: '请选择币种' }]}
            >
              <Select options={CURRENCY_OPTIONS} />
            </Form.Item>
            <Form.Item
              name="priceOriginal"
              label="参考价（原币）"
              rules={[
                { required: true, message: '请输入参考价' },
                {
                  validator: (_, v) =>
                    v == null || v > 0
                      ? Promise.resolve()
                      : Promise.reject(new Error('参考价必须大于 0')),
                },
              ]}
            >
              <InputNumber
                style={{ width: '100%' }}
                precision={2}
                controls={false}
                aria-label="参考价"
              />
            </Form.Item>
            {currency !== 'CNY' && (
              <Form.Item
                name="exchangeRate"
                label="汇率（原币 → 人民币）"
                extra="不填则本位币金额显示为「未计算」"
                rules={[
                  {
                    validator: (_, v) =>
                      v == null || v > 0
                        ? Promise.resolve()
                        : Promise.reject(new Error('汇率必须大于 0')),
                  },
                ]}
              >
                <InputNumber
                  style={{ width: '100%' }}
                  precision={6}
                  controls={false}
                  aria-label="汇率"
                />
              </Form.Item>
            )}
            <Form.Item name="priceSource" label="价格来源">
              <Input maxLength={128} placeholder="如 厂商官网目录价" />
            </Form.Item>
            <Form.Item name="date" label="取价日期">
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Space>
            <Button type="primary" loading={saving} onClick={save}>
              保存
            </Button>
            <Button
              onClick={() => {
                setEditing(false);
                setDirty(false);
              }}
            >
              取消
            </Button>
          </Space>
        </Form>
      ) : !has ? (
        <CardEmpty
          benefit="报价时有个参照，不必每次重新查；本位币金额按汇率自动折算，四舍五入保留 2 位。"
          actionText={readOnly ? undefined : '记录参考价'}
          onAction={startEdit}
        />
      ) : (
        <FieldGrid>
          <Field label="币种">{price?.currencyCode}</Field>
          <Field label="参考价（原币）">
            <span className="num" style={{ textAlign: 'right' }}>
              {money(price?.priceOriginal)}
            </span>
          </Field>
          <Field label="汇率">
            <span className="num">{price?.exchangeRate ?? ''}</span>
          </Field>
          <Field label="本位币金额（CNY）">
            {price?.priceCny != null ? (
              <span className="num">{money(price.priceCny)}</span>
            ) : (
              <span style={{ color: palette.orange }}>
                未计算（汇率未维护）
              </span>
            )}
          </Field>
          <Field label="价格来源">{price?.priceSource}</Field>
          <Field label="取价日期">{price?.priceDate}</Field>
        </FieldGrid>
      )}
    </CardShell>
  );
};
