import { LockOutlined } from '@ant-design/icons';
import { App, Button, Form, Input, Select, Space } from 'antd';
import React, { useEffect, useState } from 'react';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  categoryApi,
  type Product,
  productApi,
  type SeriesOption,
  seriesApi,
} from '../../service';
import { CardShell, Field, FieldGrid, useUnsaved } from './CardShell';

interface FormValues {
  brandId: number;
  categoryId: number;
  seriesId?: number;
  mpnRaw: string;
  mpnDisplay?: string;
  productName?: string;
  specSummary?: string;
  shortDescription?: string;
}

/** 基本信息：品牌、型号、品类、系列、名称、摘要、简介；被引用后品牌和原始型号锁定 */
export const BasicCard: React.FC<{
  product: Product;
  readOnly: boolean;
  done?: boolean;
  onChanged: () => void;
}> = ({ product, readOnly, done, onChanged }) => {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [editing, setEditing] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [brands, setBrands] = useState<BrandOption[]>([]);
  const [categories, setCategories] = useState<CategoryOption[]>([]);
  const [seriesList, setSeriesList] = useState<SeriesOption[]>([]);
  const locked = (product.usageCount ?? 0) > 0;
  const brandId = Form.useWatch('brandId', form);

  useEffect(() => {
    if (!editing) return;
    brandApi
      .options()
      .then(setBrands)
      .catch(() => {});
    categoryApi
      .options()
      .then(setCategories)
      .catch(() => {});
  }, [editing]);

  useEffect(() => {
    if (!editing || !brandId) return;
    seriesApi
      .options(brandId)
      .then(setSeriesList)
      .catch(() => setSeriesList([]));
  }, [editing, brandId]);

  const startEdit = () => {
    form.setFieldsValue({
      brandId: product.brandId,
      categoryId: product.categoryId,
      seriesId: product.seriesId,
      mpnRaw: product.mpnRaw,
      mpnDisplay: product.mpnDisplay,
      productName: product.productName,
      specSummary: product.specSummary,
      shortDescription: product.shortDescription,
    });
    setDirty(false);
    setEditing(true);
  };

  const discard = () => {
    setEditing(false);
    setDirty(false);
  };

  const save = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      const saved = await productApi.update(product.id, {
        ...v,
        seriesId: v.seriesId ?? null,
        lifecycleStatus: product.lifecycleStatus,
        lifecycleSource: product.lifecycleSource,
      });
      message.success('基本信息已保存');
      for (const w of saved.warnings ?? []) message.warning(w);
      setEditing(false);
      setDirty(false);
      onChanged();
    } finally {
      setSaving(false);
    }
  };

  useUnsaved('basic', editing && dirty ? { save, discard } : null);

  const lockHint = '已被引用，不可修改';

  return (
    <CardShell
      id="card-basic"
      title="基本信息"
      done={done}
      actions={
        !readOnly &&
        !editing && (
          <Button onClick={startEdit} aria-label="编辑基本信息">
            编辑
          </Button>
        )
      }
    >
      {editing ? (
        <Form
          form={form}
          layout="vertical"
          onValuesChange={() => setDirty(true)}
          requiredMark="optional"
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
              gap: '0 24px',
            }}
          >
            <Form.Item
              name="brandId"
              label={
                locked ? (
                  <span>
                    <LockOutlined /> 品牌（{lockHint}）
                  </span>
                ) : (
                  '品牌'
                )
              }
              rules={[{ required: true, message: '请选择品牌' }]}
            >
              <Select
                showSearch={{ optionFilterProp: 'label' }}
                disabled={locked}
                options={brands.map((b) => ({
                  value: b.id,
                  label: b.brandName,
                }))}
                onChange={() => form.setFieldValue('seriesId', undefined)}
              />
            </Form.Item>
            <Form.Item
              name="mpnRaw"
              label={
                locked ? (
                  <span>
                    <LockOutlined /> 原始型号（{lockHint}）
                  </span>
                ) : (
                  '原始型号'
                )
              }
              rules={[
                { required: true, message: '请输入型号' },
                { max: 128, message: '型号不超过 128 个字符' },
              ]}
            >
              <Input disabled={locked} />
            </Form.Item>
            <Form.Item
              name="categoryId"
              label="品类"
              rules={[{ required: true, message: '请选择品类' }]}
            >
              <Select
                options={categories.map((c) => ({
                  value: c.id,
                  label: c.categoryName,
                }))}
              />
            </Form.Item>
            <Form.Item name="seriesId" label="系列（可选）">
              <Select
                allowClear
                placeholder={brandId ? '不属于任何系列' : '先选择品牌'}
                disabled={!brandId}
                options={seriesList.map((s) => ({
                  value: s.id,
                  label: s.seriesName,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="mpnDisplay"
              label="展示型号"
              extra="页面标题使用，留空则与原始型号相同"
            >
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="productName" label="产品名称">
              <Input maxLength={128} placeholder="如 SITOP Power Supply" />
            </Form.Item>
          </div>
          <Form.Item
            name="specSummary"
            label="一句话规格摘要"
            extra="列表页展示，如 24V DC / 5A / 120W"
          >
            <Input maxLength={300} showCount />
          </Form.Item>
          <Form.Item
            name="shortDescription"
            label="简介"
            extra="需要能追溯到官方资料或询盘单，不要凭空扩写"
          >
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Space>
            <Button type="primary" loading={saving} onClick={save}>
              保存
            </Button>
            <Button onClick={discard}>取消</Button>
          </Space>
        </Form>
      ) : (
        <>
          <FieldGrid>
            <Field label="品牌">
              {locked && (
                <LockOutlined
                  aria-label={lockHint}
                  style={{ marginRight: 4 }}
                />
              )}
              {product.brandName}
            </Field>
            <Field label="原始型号">
              {locked && (
                <LockOutlined
                  aria-label={lockHint}
                  style={{ marginRight: 4 }}
                />
              )}
              {product.mpnRaw}
            </Field>
            <Field label="展示型号">{product.mpnDisplay}</Field>
            <Field label="品类">{product.categoryName}</Field>
            <Field label="系列">{product.seriesName}</Field>
            <Field label="产品名称">{product.productName}</Field>
          </FieldGrid>
          <div style={{ marginTop: 16, display: 'grid', gap: 16 }}>
            <Field label="规格摘要">{product.specSummary}</Field>
            <Field label="简介">{product.shortDescription}</Field>
          </div>
        </>
      )}
    </CardShell>
  );
};
