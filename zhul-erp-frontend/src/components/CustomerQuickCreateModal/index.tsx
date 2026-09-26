import {
  ModalForm,
  ProForm,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components';
import { App } from 'antd';
import React from 'react';
import { useCountries } from '@/pages/product/components/useCountries';
import type {
  CustomerItem,
  SaveCustomerPayload,
} from '@/services/zhul/masterdata';
import { createCustomer, getCustomer } from '@/services/zhul/masterdata';

interface CustomerQuickCreateModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 创建成功（或用户选择使用已有客户）后回调，调用方用它自动选中该客户 */
  onCreated: (customer: CustomerItem) => void;
}

/** 单据字段不允许中日韩文字（允许欧洲语言变音字母），与客户管理一致 */
const CJK_PATTERN = /[぀-ヿ㐀-鿿가-힯豈-﫿]/;

/**
 * 客户快速创建弹窗（M03）。英文名称与国家必填，其余选填；角色、负责人（创建人）、币种等取默认值，
 * 之后可在客户管理补全。名称 + 国家与已有客户重复时不新建：已有客户在自己数据范围内可直接选用，
 * 否则只提示负责业务员（撞单）。见 openspec/changes/enrich-customer-trade-profile/。
 */
const CustomerQuickCreateModal: React.FC<CustomerQuickCreateModalProps> = ({
  open,
  onOpenChange,
  onCreated,
}) => {
  const { message, modal } = App.useApp();
  const { options: countryOptions } = useCountries();

  const submit = async (values: SaveCustomerPayload): Promise<boolean> => {
    try {
      const created = await createCustomer(values);
      message.success('客户创建成功');
      onCreated(created);
      return true;
    } catch (e) {
      const err = e as {
        message?: string;
        info?: {
          data?: {
            errorCode?: string;
            detail?: { existingId?: number; selectable?: boolean };
          };
        };
        response?: { data?: { message?: string } };
      };
      const data = err.info?.data;
      if (
        data?.errorCode === 'CUSTOMER_DUPLICATE' &&
        data.detail?.selectable &&
        data.detail.existingId
      ) {
        const existingId = data.detail.existingId;
        return new Promise<boolean>((resolve) => {
          modal.confirm({
            title: '该客户已存在',
            content: `${err.message}。是否直接使用已有客户？`,
            okText: '使用该客户',
            cancelText: '返回修改',
            onOk: async () => {
              onCreated(await getCustomer(existingId));
              resolve(true);
            },
            onCancel: () => resolve(false),
          });
        });
      }
      message.error(
        err.response?.data?.message ?? err.message ?? '创建失败，请稍后重试',
      );
      return false;
    }
  };

  return (
    <ModalForm<SaveCustomerPayload>
      title="新建客户"
      width={640}
      open={open}
      onOpenChange={onOpenChange}
      onFinish={submit}
      modalProps={{ destroyOnHidden: true, maskClosable: false }}
      layout="vertical"
      submitter={{ searchConfig: { submitText: '创建' } }}
    >
      <ProFormText
        name="name"
        label="客户名称（英文）"
        placeholder="如 ABC Automation GmbH"
        extra="单据上使用的英文法定全称；角色、交易条件等可以之后在客户管理补全"
        rules={[
          { required: true, whitespace: true, message: '请输入客户名称' },
          { max: 200, message: '客户名称不能超过200个字符' },
          {
            validator: (_, v?: string) =>
              v && CJK_PATTERN.test(v)
                ? Promise.reject(new Error('单据字段请使用英文'))
                : Promise.resolve(),
          },
        ]}
      />
      <ProForm.Group>
        <ProFormSelect
          name="country"
          label="国家/地区"
          placeholder="请选择国家/地区"
          width="md"
          options={countryOptions}
          fieldProps={{ showSearch: { optionFilterProp: 'label' } }}
          rules={[{ required: true, message: '请选择国家/地区' }]}
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
          placeholder="+49 89 1234 5678"
          width="md"
          rules={[
            {
              pattern: /^[+0-9 ()-]{0,30}$/,
              message: '只能包含 + 数字 空格 - 括号，最多30位',
            },
          ]}
        />
        <ProFormText
          name="contactEmail"
          label="联系邮箱"
          placeholder="请输入联系邮箱"
          width="md"
          rules={[{ type: 'email', message: '请输入正确的邮箱格式' }]}
        />
      </ProForm.Group>
    </ModalForm>
  );
};

export default CustomerQuickCreateModal;
