import { ModalForm, ProForm, ProFormText } from '@ant-design/pro-components';
import { Modal, message } from 'antd';
import React from 'react';
import type { CustomerItem } from '@/services/zhul/masterdata';
import { createCustomer } from '@/services/zhul/masterdata';

interface CustomerQuickCreateModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 创建成功（或用户选择使用已有客户）后回调，调用方用它自动选中该客户 */
  onCreated: (customer: CustomerItem) => void;
}

/**
 * 客户快速创建弹窗（M03）。名称必填，其余选填；同名时提示是否使用已有客户，
 * 参照 ui-design-patterns.md 弹窗结构规范与「询盘单-客户快速创建弹窗.png」。
 */
const CustomerQuickCreateModal: React.FC<CustomerQuickCreateModalProps> = ({
  open,
  onOpenChange,
  onCreated,
}) => {
  const submit = async (values: {
    name: string;
    country?: string;
    contactName?: string;
    contactPhone?: string;
    contactEmail?: string;
  }): Promise<boolean> => {
    const result = await createCustomer(values);
    if (result.duplicate && result.existingCustomer) {
      const existing = result.existingCustomer;
      return new Promise<boolean>((resolve) => {
        Modal.confirm({
          title: '该名称已存在',
          content: `客户"${existing.name}"已存在，是否使用已有客户？`,
          okText: '使用已有客户',
          cancelText: '仍然新建',
          onOk: () => {
            onCreated(existing);
            resolve(true);
          },
          onCancel: async () => {
            const forced = await createCustomer({ ...values, force: true });
            if (forced.createdCustomer) {
              message.success('客户创建成功');
              onCreated(forced.createdCustomer);
            }
            resolve(true);
          },
        });
      });
    }
    if (result.createdCustomer) {
      message.success('客户创建成功');
      onCreated(result.createdCustomer);
    }
    return true;
  };

  return (
    <ModalForm
      title="新建客户"
      width={640}
      open={open}
      onOpenChange={onOpenChange}
      onFinish={submit}
      modalProps={{ destroyOnClose: true, maskClosable: false }}
      layout="vertical"
      submitter={{ searchConfig: { submitText: '创建' } }}
    >
      <ProFormText
        name="name"
        label="名称"
        placeholder="请输入客户名称"
        rules={[{ required: true, message: '请输入客户名称' }]}
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
    </ModalForm>
  );
};

export default CustomerQuickCreateModal;
