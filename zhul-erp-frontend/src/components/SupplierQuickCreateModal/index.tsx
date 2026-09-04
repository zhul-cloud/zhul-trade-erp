import { ModalForm, ProForm, ProFormText } from '@ant-design/pro-components';
import { Modal, message } from 'antd';
import React from 'react';
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
  const submit = async (values: {
    name: string;
    country?: string;
    contactName?: string;
    contactPhone?: string;
    contactEmail?: string;
    mainBrands?: string;
  }): Promise<boolean> => {
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
      <ProFormText
        name="mainBrands"
        label="主营品牌"
        placeholder="多个品牌用逗号分隔，仅辅助展示"
      />
    </ModalForm>
  );
};

export default SupplierQuickCreateModal;
