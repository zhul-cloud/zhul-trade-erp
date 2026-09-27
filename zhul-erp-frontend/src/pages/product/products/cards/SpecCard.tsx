import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { App, Button, Form, Input, Space, Switch, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint } from '../../components/EmptyHint';
import { Pill } from '../../components/Pills';
import { readBizError, type SpecItem, specApi } from '../../service';
import { useProductTheme } from '../../theme';
import { CardEmpty, CardShell, useEditFocus, useUnsaved } from './CardShell';

/** 规格名称 → 规格编码：小写、空格变下划线，只留字母数字 */
const slug = (label: string) =>
  label
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .replace(/^(\d)/, 'k_$1');

export const SpecCard: React.FC<{
  productId: number;
  readOnly: boolean;
  done?: boolean;
  onChanged: () => void;
}> = ({ productId, readOnly, done, onChanged }) => {
  const { message } = App.useApp();
  const { palette } = useProductTheme();
  const [form] = Form.useForm<{ items: SpecItem[] }>();
  const [items, setItems] = useState<SpecItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [editing, setEditing] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setItems(await specApi.list(productId));
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [productId]);

  useEffect(() => {
    load();
  }, [load]);

  const startEdit = () => {
    form.setFieldsValue({
      items: items.length
        ? items
        : [{ specKey: '', specLabel: '', specValue: '' }],
    });
    setDirty(false);
    setEditing(true);
  };

  const discard = () => {
    setEditing(false);
    setDirty(false);
  };

  const save = async () => {
    const { items: rows } = await form.validateFields();
    const cleaned = (rows ?? []).map((r, i) => ({
      specKey: (r.specKey || slug(r.specLabel)).trim(),
      specLabel: r.specLabel.trim(),
      specValue: r.specValue.trim(),
      specUnit: r.specUnit?.trim() ?? '',
      source: r.source?.trim() ?? '',
      verified: r.verified ? 1 : 0,
      sortOrder: i,
    }));
    setSaving(true);
    try {
      setItems(await specApi.replace(productId, cleaned));
      message.success('规格参数已保存');
      setEditing(false);
      setDirty(false);
      onChanged();
    } finally {
      setSaving(false);
    }
  };

  useUnsaved('specs', editing && dirty ? { save, discard } : null);
  useEditFocus('card-specs', editing);

  return (
    <CardShell
      id="card-specs"
      title="规格参数"
      done={done}
      actions={
        !readOnly &&
        !editing &&
        items.length > 0 && <Button onClick={startEdit}>编辑</Button>
      }
    >
      {error ? (
        <EmptyHint
          title="加载失败"
          description={error}
          actionText="重试"
          onAction={load}
        />
      ) : loading ? (
        <div style={{ color: palette.sub }}>正在加载…</div>
      ) : editing ? (
        <Form
          form={form}
          layout="vertical"
          onValuesChange={() => setDirty(true)}
          requiredMark={false}
        >
          <Form.List name="items">
            {(fields, { add, remove, move }) => (
              <>
                {fields.map((f, index) => (
                  <fieldset
                    key={f.key}
                    style={{
                      border: 0,
                      padding: 0,
                      margin: '0 0 4px',
                      minWidth: 0,
                      display: 'grid',
                      gridTemplateColumns:
                        'minmax(120px,1.2fr) minmax(90px,0.8fr) minmax(120px,1.4fr) 90px minmax(100px,1fr) 64px auto',
                      gap: 8,
                      alignItems: 'start',
                    }}
                  >
                    <legend
                      style={{
                        position: 'absolute',
                        width: 1,
                        height: 1,
                        overflow: 'hidden',
                        clip: 'rect(0 0 0 0)',
                      }}
                    >
                      {`第 ${index + 1} 行规格`}
                    </legend>
                    <Form.Item
                      name={[f.name, 'specLabel']}
                      label={index === 0 ? '规格名称' : undefined}
                      rules={[
                        { required: true, message: '请输入规格名称' },
                        { max: 64, message: '不超过 64 个字符' },
                      ]}
                    >
                      <Input
                        aria-label="规格名称"
                        placeholder="如 Rated Voltage"
                      />
                    </Form.Item>
                    <Form.Item
                      name={[f.name, 'specKey']}
                      label={index === 0 ? '编码' : undefined}
                      tooltip={
                        index === 0
                          ? '留空会按规格名称自动生成；同一商品内不能重复'
                          : undefined
                      }
                      dependencies={['items']}
                      rules={[
                        {
                          pattern: /^([a-z][a-z0-9_]*)?$/,
                          message: '小写字母开头，只含小写字母、数字、下划线',
                        },
                        ({ getFieldValue }) => ({
                          validator: (_, v) => {
                            const all: SpecItem[] =
                              getFieldValue('items') ?? [];
                            const key = (
                              v || slug(all[f.name]?.specLabel ?? '')
                            ).trim();
                            const dup =
                              key &&
                              all.filter(
                                (r) =>
                                  (r.specKey || slug(r.specLabel ?? '')) ===
                                  key,
                              ).length > 1;
                            return dup
                              ? Promise.reject(new Error('编码重复'))
                              : Promise.resolve();
                          },
                        }),
                      ]}
                    >
                      <Input aria-label="规格编码" placeholder="自动生成" />
                    </Form.Item>
                    <Form.Item
                      name={[f.name, 'specValue']}
                      label={index === 0 ? '规格值' : undefined}
                      rules={[
                        { required: true, message: '请输入规格值' },
                        { max: 256 },
                      ]}
                    >
                      <Input aria-label="规格值" />
                    </Form.Item>
                    <Form.Item
                      name={[f.name, 'specUnit']}
                      label={index === 0 ? '单位' : undefined}
                    >
                      <Input aria-label="单位" placeholder="如 V DC" />
                    </Form.Item>
                    <Form.Item
                      name={[f.name, 'source']}
                      label={index === 0 ? '来源' : undefined}
                    >
                      <Input aria-label="来源" placeholder="如 Datasheet" />
                    </Form.Item>
                    <Form.Item
                      name={[f.name, 'verified']}
                      valuePropName="checked"
                      label={index === 0 ? '已核实' : undefined}
                    >
                      <Switch aria-label="已核实" />
                    </Form.Item>
                    <Space
                      size={4}
                      style={{ paddingTop: index === 0 ? 30 : 0 }}
                    >
                      <Button
                        aria-label="上移"
                        icon={<ArrowUpOutlined />}
                        disabled={index === 0}
                        onClick={() => move(index, index - 1)}
                      />
                      <Button
                        aria-label="下移"
                        icon={<ArrowDownOutlined />}
                        disabled={index === fields.length - 1}
                        onClick={() => move(index, index + 1)}
                      />
                      <Button
                        aria-label="删除这一行"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => remove(f.name)}
                      />
                    </Space>
                  </fieldset>
                ))}
                <Button
                  icon={<PlusOutlined />}
                  onClick={() =>
                    add({ specKey: '', specLabel: '', specValue: '' })
                  }
                  style={{ marginBottom: 16 }}
                >
                  添加一行
                </Button>
              </>
            )}
          </Form.List>
          <div>
            <Space>
              <Button type="primary" loading={saving} onClick={save}>
                保存
              </Button>
              <Button onClick={discard}>取消</Button>
            </Space>
            <span style={{ marginLeft: 12, color: palette.sub, fontSize: 12 }}>
              保存会整体替换：提交后商品的规格就是这里列出的这些。
            </span>
          </div>
        </Form>
      ) : items.length === 0 ? (
        <CardEmpty
          benefit="客户按规格筛选和比对，报价时也要核对，尽量从官方数据手册里抄。"
          actionText={readOnly ? undefined : '添加规格'}
          onAction={startEdit}
        />
      ) : (
        <Table<SpecItem>
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={items}
          columns={[
            { title: '规格名称', dataIndex: 'specLabel' },
            {
              title: '规格值',
              dataIndex: 'specValue',
              render: (v) => <span className="num">{v}</span>,
            },
            { title: '单位', dataIndex: 'specUnit', render: (v) => v || '—' },
            { title: '来源', dataIndex: 'source', render: (v) => v || '—' },
            {
              title: '已核实',
              dataIndex: 'verified',
              render: (v) =>
                v ? (
                  <Pill tone="green">已核实</Pill>
                ) : (
                  <Pill tone="gray">未核实</Pill>
                ),
            },
          ]}
        />
      )}
    </CardShell>
  );
};
