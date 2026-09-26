import {
  ModalForm,
  ProForm,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { Modal, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { type BrandOption, brandApi } from '@/pages/product/service';
import type { SupplierItem } from '@/services/zhul/masterdata';
import { createSupplier } from '@/services/zhul/masterdata';

interface SupplierQuickCreateModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 创建成功（或用户选择使用已有供应商）后回调，调用方用它自动选中该供应商 */
  onCreated: (supplier: SupplierItem) => void;
  /** 从电商询价渠道"转为正式供应商"时预填店铺/卖家名称 */
  initialName?: string;
}

/**
 * 供应商快速创建弹窗（M04）。名称必填，其余选填（含主营品牌）；同名时提示是否使用已有供应商。
 */
const SupplierQuickCreateModal: React.FC<SupplierQuickCreateModalProps> = ({
  open,
  onOpenChange,
  onCreated,
  initialName,
}) => {
  const [brands, setBrands] = useState<BrandOption[]>([]);
  useEffect(() => {
    if (open)
      brandApi
        .options()
        .then(setBrands)
        .catch(() => undefined);
  }, [open]);

  const submit = async ({
    brandNames,
    ...rest
  }: {
    name: string;
    country?: string;
    contactName?: string;
    contactPhone?: string;
    contactEmail?: string;
    brandNames?: string[];
  }): Promise<boolean> => {
    const values = {
      ...rest,
      productScopes: (brandNames ?? [])
        .map((n) => n.trim())
        .filter(Boolean)
        .map((brandName) => ({ brandName, categoryIds: [] })),
    };
    const result = await createSupplier(values);
    if (result.duplicate && result.existingSupplier) {
      const existing = result.existingSupplier;
      return new Promise<boolean>((resolve) => {
        Modal.confirm({
          title: '该名称已存在',
          content: `供应商"${existing.name}"已存在，是否使用已有供应商？`,
          okText: '使用已有供应商',
          cancelText: '仍然新建',
          onOk: () => {
            onCreated(existing);
            resolve(true);
          },
          onCancel: async () => {
            const forced = await createSupplier({ ...values, force: true });
            if (forced.createdSupplier) {
              message.success('供应商创建成功');
              onCreated(forced.createdSupplier);
            }
            resolve(true);
          },
        });
      });
    }
    if (result.createdSupplier) {
      message.success('供应商创建成功');
      onCreated(result.createdSupplier);
    }
    return true;
  };

  return (
    <ModalForm
      title="新建供应商"
      width={640}
      open={open}
      onOpenChange={onOpenChange}
      onFinish={submit}
      modalProps={{ destroyOnClose: true, maskClosable: false }}
      layout="vertical"
      initialValues={initialName ? { name: initialName } : undefined}
      submitter={{ searchConfig: { submitText: '创建' } }}
    >
      <ProFormText
        name="name"
        label="名称"
        placeholder="请输入供应商名称"
        rules={[{ required: true, message: '请输入供应商名称' }]}
      />
      <ProForm.Group>
        <ProFormText
          name="country"
          label="国家/地区"
          placeholder="请输入国家/地区"
          width="md"
        />
        <ProFormText
          name="contactName"
          label="联系人"
          placeholder="请输入联系人姓名"
          width="md"
        />
      </ProForm.Group>
      <ProForm.Group>
        <ProFormText
          name="contactPhone"
          label="联系电话"
          placeholder="请输入联系电话"
          width="md"
        />
        <ProFormText
          name="contactEmail"
          label="联系邮箱"
          placeholder="请输入联系邮箱"
          width="md"
        />
      </ProForm.Group>
      <ProFormSelect
        name="brandNames"
        label="主营品牌"
        mode="tags"
        placeholder="搜索或输入品牌，可多选"
        extra="清单里没有的品牌可直接输入，保存为待确认品牌；细分品类可稍后在供应商管理中补充"
        options={brands.map((b) => ({
          value: b.brandName,
          label: b.brandName,
          aliases: b.aliases ?? [],
        }))}
        fieldProps={{
          tokenSeparators: [',', '，'],
          filterOption: (input, option) =>
            !!option &&
            [
              String(option.label),
              ...((option.aliases as string[]) ?? []),
            ].some((t) => t.toLowerCase().includes(input.trim().toLowerCase())),
        }}
      />
    </ModalForm>
  );
};

export default SupplierQuickCreateModal;
