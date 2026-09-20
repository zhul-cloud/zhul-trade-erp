import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  App,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint } from '../../components/EmptyHint';
import { Pill } from '../../components/Pills';
import { ProductSelect } from '../../components/ProductSelect';
import {
  CONFIDENCE,
  CONFIDENCE_OPTIONS,
  RELATIONSHIP_OPTIONS,
  RELATIONSHIP_TYPES,
} from '../../constants';
import {
  type Relationship,
  readBizError,
  relationshipApi,
  type SaveRelationship,
} from '../../service';
import { useProductTheme } from '../../theme';
import { CardEmpty, CardShell } from './CardShell';

/** 添加型号关系（M05）：从商品库选，或手动输入型号；对称类型可同时创建反向关系 */
const RelationModal: React.FC<{
  productId: number;
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ productId, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useProductTheme();
  const [form] = Form.useForm();
  const [mode, setMode] = useState<'library' | 'manual'>('library');
  const type = Form.useWatch('relationshipType', form);
  const confidence = Form.useWatch('confidence', form);
  const relatedProductId = Form.useWatch('relatedProductId', form);
  const symmetric = type ? RELATIONSHIP_TYPES[type]?.symmetric : false;

  return (
    <Modal
      open={open}
      title="添加型号关系"
      onCancel={onClose}
      destroyOnHidden
      okText="添加"
      cancelText="取消"
      onOk={async () => {
        const v = (await form.validateFields()) as SaveRelationship;
        await relationshipApi.create(productId, {
          ...v,
          createReverse:
            symmetric &&
            !!v.createReverse &&
            !!(mode === 'library' && v.relatedProductId),
        });
        message.success('已添加');
        onDone();
        onClose();
      }}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ relationshipType: 4, confidence: 3 }}
        preserve={false}
      >
        <Radio.Group
          value={mode}
          onChange={(e) => setMode(e.target.value)}
          optionType="button"
          style={{ marginBottom: 16 }}
          options={[
            { value: 'library', label: '从商品库选择' },
            { value: 'manual', label: '手动输入型号' },
          ]}
        />
        {mode === 'library' ? (
          <Form.Item
            name="relatedProductId"
            label="关联商品"
            rules={[{ required: true, message: '请选择商品' }]}
          >
            <ProductSelect style={{ width: '100%' }} aria-label="关联商品" />
          </Form.Item>
        ) : (
          <Form.Item
            name="relatedMpn"
            label="关联型号"
            extra="可以是目录里没有的旧型号或停产型号，只保存型号原文"
            rules={[
              { required: true, message: '请输入关联型号' },
              { max: 128 },
            ]}
          >
            <Input />
          </Form.Item>
        )}
        <Form.Item name="relationshipType" label="关系类型">
          <Select options={RELATIONSHIP_OPTIONS} />
        </Form.Item>
        <div
          style={{
            marginTop: -16,
            marginBottom: 16,
            fontSize: 12,
            color: palette.sub,
          }}
        >
          {type ? RELATIONSHIP_TYPES[type]?.hint : ''}
        </div>
        {symmetric && mode === 'library' && (
          <Form.Item name="createReverse" valuePropName="checked">
            <Checkbox disabled={!relatedProductId}>
              同时创建反向关系（两边都是目录内商品）
            </Checkbox>
          </Form.Item>
        )}
        <Form.Item name="confidence" label="置信度">
          <Select options={CONFIDENCE_OPTIONS} />
        </Form.Item>
        {confidence === 1 && (
          <Form.Item
            name="verifiedBy"
            label="核实人"
            rules={[
              { required: true, message: '置信度为「已验证」时必须填写核实人' },
              { max: 32 },
            ]}
          >
            <Input />
          </Form.Item>
        )}
        <Form.Item name="note" label="说明">
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export const RelationCard: React.FC<{
  productId: number;
  readOnly: boolean;
  done?: boolean;
  onChanged: () => void;
}> = ({ productId, readOnly, done, onChanged }) => {
  const { message, modal } = App.useApp();
  const { palette } = useProductTheme();
  const [items, setItems] = useState<Relationship[]>([]);
  const [error, setError] = useState<string>();
  const [open, setOpen] = useState(false);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setItems(await relationshipApi.list(productId));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [productId]);

  useEffect(() => {
    load();
  }, [load]);

  const changed = () => {
    load();
    onChanged();
  };

  const remove = (r: Relationship) =>
    modal.confirm({
      title: `删除与 ${r.relatedMpn} 的关系？`,
      content: '只删除这一条；如果之前同时创建了反向关系，它会保留。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await relationshipApi.remove(productId, r.id);
        message.success('已删除');
        changed();
      },
    });

  return (
    <CardShell
      id="card-relations"
      title="型号关系"
      done={done}
      actions={
        !readOnly && (
          <Button icon={<PlusOutlined />} onClick={() => setOpen(true)}>
            添加关系
          </Button>
        )
      }
    >
      {error ? (
        <EmptyHint
          title="加载失败"
          description={error}
          actionText="重试"
          onAction={load}
        />
      ) : items.length === 0 ? (
        <CardEmpty
          benefit="停产、缺货时能快速找到替代型号；关联型号可以是目录里没有的旧型号。"
          actionText={readOnly ? undefined : '添加第一个关系'}
          onAction={() => setOpen(true)}
        />
      ) : (
        <Table<Relationship>
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={items}
          columns={[
            {
              title: '关联型号',
              render: (_, r) =>
                r.relatedProductId ? (
                  <a
                    onClick={() =>
                      history.push(`/product/products/${r.relatedProductId}`)
                    }
                  >
                    {r.relatedProductDisplay ?? r.relatedMpn}
                  </a>
                ) : (
                  r.relatedMpn
                ),
            },
            {
              title: '目录',
              render: (_, r) =>
                r.relatedProductId ? (
                  <Pill tone="accent">{r.relatedBrandName ?? '目录内'}</Pill>
                ) : (
                  <Pill tone="gray">目录外</Pill>
                ),
            },
            {
              title: '关系类型',
              render: (_, r) => (
                <Pill tone="accent">
                  {RELATIONSHIP_TYPES[r.relationshipType]?.text}
                </Pill>
              ),
            },
            {
              title: '置信度',
              render: (_, r) => (
                <span
                  style={{
                    color: r.confidence >= 4 ? palette.orange : palette.ink,
                  }}
                >
                  {CONFIDENCE[r.confidence]}
                  {r.confidence === 1 && r.verifiedBy
                    ? ` · ${r.verifiedBy}`
                    : ''}
                </span>
              ),
            },
            {
              title: '说明',
              dataIndex: 'note',
              ellipsis: true,
              render: (v) => v || '—',
            },
            ...(readOnly
              ? []
              : [
                  {
                    title: '',
                    width: 56,
                    render: (_: unknown, r: Relationship) => (
                      <Button
                        aria-label={`删除与 ${r.relatedMpn} 的关系`}
                        danger
                        type="text"
                        icon={<DeleteOutlined />}
                        onClick={() => remove(r)}
                      />
                    ),
                  },
                ]),
          ]}
        />
      )}
      <RelationModal
        productId={productId}
        open={open}
        onClose={() => setOpen(false)}
        onDone={changed}
      />
    </CardShell>
  );
};
