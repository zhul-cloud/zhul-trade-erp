import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Switch,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint } from '../../components/EmptyHint';
import { Pill } from '../../components/Pills';
import {
  DOCUMENT_TYPE_OPTIONS,
  DOCUMENT_TYPES,
  FAQ_SOURCE,
  isSafeUrl,
  URL_RULE_MESSAGE,
} from '../../constants';
import {
  type ApplicationItem,
  applicationApi,
  type DocumentItem,
  documentApi,
  type FaqItem,
  faqApi,
  readBizError,
} from '../../service';
import { useProductTheme } from '../../theme';
import { CardEmpty, CardShell } from './CardShell';

interface Common {
  productId: number;
  readOnly: boolean;
  done?: boolean;
  onChanged: () => void;
}

/** 三张内容卡的共同逻辑：加载、失败重试、增删改后刷新 */
function useList<T>(fetcher: () => Promise<T[]>) {
  const [items, setItems] = useState<T[]>([]);
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setItems(await fetcher());
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [fetcher]);
  useEffect(() => {
    load();
  }, [load]);
  return { items, error, loading, load };
}

const RowActions: React.FC<{
  label: string;
  onEdit: () => void;
  onDelete: () => Promise<void>;
}> = ({ label, onEdit, onDelete }) => (
  <Space size={4}>
    <Button
      size="small"
      type="text"
      aria-label={`编辑${label}`}
      icon={<EditOutlined />}
      onClick={onEdit}
    />
    <Popconfirm
      title={`删除${label}？`}
      okText="删除"
      cancelText="取消"
      okButtonProps={{ danger: true }}
      onConfirm={onDelete}
    >
      <Button
        size="small"
        type="text"
        danger
        aria-label={`删除${label}`}
        icon={<DeleteOutlined />}
      />
    </Popconfirm>
  </Space>
);

// ---------------- 技术资料 ----------------

export const DocumentCard: React.FC<Common> = ({
  productId,
  readOnly,
  done,
  onChanged,
}) => {
  const { message } = App.useApp();
  const { palette } = useProductTheme();
  const fetcher = useCallback(() => documentApi.list(productId), [productId]);
  const { items, error, loading, load } = useList<DocumentItem>(fetcher);
  const [editing, setEditing] = useState<DocumentItem | 'new' | null>(null);
  const [form] = Form.useForm();

  const open = (item: DocumentItem | 'new') => {
    form.resetFields();
    if (item !== 'new')
      form.setFieldsValue({ ...item, verified: item.verified === 1 });
    else
      form.setFieldsValue({ documentType: 1, language: 'en', verified: false });
    setEditing(item);
  };

  return (
    <CardShell
      id="card-documents"
      title="技术资料"
      done={done}
      actions={
        !readOnly && (
          <Button icon={<PlusOutlined />} onClick={() => open('new')}>
            添加
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
      ) : loading ? (
        <div style={{ color: palette.sub }}>正在加载…</div>
      ) : items.length === 0 ? (
        <CardEmpty
          benefit="数据手册和用户手册是客户最常要的资料，这里只保存文件地址。"
          actionText={readOnly ? undefined : '添加第一份资料'}
          onAction={() => open('new')}
        />
      ) : (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {items.map((d) => (
            <li
              key={d.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '10px 0',
                borderTop: `1px solid ${palette.hairline}`,
              }}
            >
              <Pill tone="accent">{DOCUMENT_TYPES[d.documentType]}</Pill>
              <a
                href={d.fileUrl}
                target="_blank"
                rel="noreferrer"
                style={{
                  flex: 1,
                  minWidth: 0,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {d.title}
              </a>
              <span style={{ color: palette.mute, fontSize: 12 }}>
                {d.language}
                {d.version ? ` · ${d.version}` : ''}
              </span>
              {d.verified === 1 ? (
                <Pill tone="green">已核实</Pill>
              ) : (
                <Pill tone="gray">未核实</Pill>
              )}
              {!readOnly && (
                <RowActions
                  label="资料"
                  onEdit={() => open(d)}
                  onDelete={async () => {
                    await documentApi.remove(productId, d.id);
                    message.success('已删除');
                    load();
                    onChanged();
                  }}
                />
              )}
            </li>
          ))}
        </ul>
      )}
      <Modal
        open={editing !== null}
        title={editing === 'new' ? '添加技术资料' : '编辑技术资料'}
        onCancel={() => setEditing(null)}
        destroyOnHidden
        okText="保存"
        cancelText="取消"
        onOk={async () => {
          const v = await form.validateFields();
          const data = { ...v, verified: v.verified ? 1 : 0 };
          if (editing === 'new') await documentApi.create(productId, data);
          else if (editing)
            await documentApi.update(productId, editing.id, data);
          message.success('已保存');
          setEditing(null);
          load();
          onChanged();
        }}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="documentType"
            label="文档类型"
            rules={[{ required: true }]}
          >
            <Select options={DOCUMENT_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="title"
            label="标题"
            rules={[{ required: true, message: '请输入标题' }, { max: 128 }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="fileUrl"
            label="文件地址"
            validateTrigger="onBlur"
            rules={[
              { required: true, message: '请输入文件地址' },
              {
                validator: (_, v) =>
                  !v || isSafeUrl(v)
                    ? Promise.resolve()
                    : Promise.reject(new Error(URL_RULE_MESSAGE)),
              },
            ]}
          >
            <Input placeholder="https://" />
          </Form.Item>
          <Space size={12} style={{ display: 'flex' }} align="start">
            <Form.Item name="language" label="语言" style={{ width: 120 }}>
              <Input maxLength={8} />
            </Form.Item>
            <Form.Item name="version" label="版本" style={{ width: 160 }}>
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item name="source" label="来源" style={{ flex: 1 }}>
              <Input maxLength={128} />
            </Form.Item>
          </Space>
          <Form.Item name="verified" label="已核实" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </CardShell>
  );
};

// ---------------- 应用场景 ----------------

export const ApplicationCard: React.FC<Common> = ({
  productId,
  readOnly,
  done,
  onChanged,
}) => {
  const { message } = App.useApp();
  const { palette } = useProductTheme();
  const fetcher = useCallback(
    () => applicationApi.list(productId),
    [productId],
  );
  const { items, error, loading, load } = useList<ApplicationItem>(fetcher);
  const [editing, setEditing] = useState<ApplicationItem | 'new' | null>(null);
  const [form] = Form.useForm();

  const open = (item: ApplicationItem | 'new') => {
    form.resetFields();
    if (item !== 'new')
      form.setFieldsValue({ ...item, verified: item.verified === 1 });
    setEditing(item);
  };

  return (
    <CardShell
      id="card-applications"
      title="应用场景"
      done={done}
      actions={
        !readOnly && (
          <Button icon={<PlusOutlined />} onClick={() => open('new')}>
            添加
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
      ) : loading ? (
        <div style={{ color: palette.sub }}>正在加载…</div>
      ) : items.length === 0 ? (
        <CardEmpty
          benefit="说明这个商品用在哪里，必须基于真实用途，不要为了搜索排名编造。"
          actionText={readOnly ? undefined : '添加第一个场景'}
          onAction={() => open('new')}
        />
      ) : (
        <ul
          style={{
            listStyle: 'none',
            margin: 0,
            padding: 0,
            display: 'grid',
            gap: 12,
            gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
          }}
        >
          {items.map((a) => (
            <li
              key={a.id}
              style={{
                padding: 16,
                borderRadius: 12,
                background: palette.inset,
                border: `1px solid ${palette.hairline}`,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 22 }} aria-hidden="true">
                  {a.icon}
                </span>
                <b style={{ flex: 1 }}>{a.title}</b>
                {!readOnly && (
                  <RowActions
                    label="应用场景"
                    onEdit={() => open(a)}
                    onDelete={async () => {
                      await applicationApi.remove(productId, a.id);
                      message.success('已删除');
                      load();
                      onChanged();
                    }}
                  />
                )}
              </div>
              {a.description && (
                <p
                  style={{
                    margin: '8px 0 0',
                    color: palette.sub,
                    fontSize: 13,
                  }}
                >
                  {a.description}
                </p>
              )}
              <div style={{ marginTop: 8 }}>
                {a.verified === 1 ? (
                  <Pill tone="green">已核实</Pill>
                ) : (
                  <Pill tone="gray">未核实</Pill>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <Modal
        open={editing !== null}
        title={editing === 'new' ? '添加应用场景' : '编辑应用场景'}
        onCancel={() => setEditing(null)}
        destroyOnHidden
        okText="保存"
        cancelText="取消"
        onOk={async () => {
          const v = await form.validateFields();
          const data = { ...v, verified: v.verified ? 1 : 0 };
          if (editing === 'new') await applicationApi.create(productId, data);
          else if (editing)
            await applicationApi.update(productId, editing.id, data);
          message.success('已保存');
          setEditing(null);
          load();
          onChanged();
        }}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="title"
            label="标题"
            rules={[
              { required: true, message: '请输入标题' },
              { max: 64, message: '标题不超过 64 个字符' },
            ]}
          >
            <Input placeholder="如 Water & pump stations" />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="icon" label="图标（emoji）">
            <Input maxLength={8} style={{ width: 120 }} placeholder="如 💧" />
          </Form.Item>
          <Form.Item name="verified" label="已核实" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </CardShell>
  );
};

// ---------------- FAQ ----------------

export const FaqCard: React.FC<Common & { platform: boolean }> = ({
  productId,
  readOnly,
  done,
  onChanged,
  platform,
}) => {
  const { message } = App.useApp();
  const { palette } = useProductTheme();
  const fetcher = useCallback(() => faqApi.list(productId), [productId]);
  const { items, error, loading, load } = useList<FaqItem>(fetcher);
  const [filter, setFilter] = useState<'all' | 'pending' | 'confirmed'>('all');
  const [editing, setEditing] = useState<FaqItem | 'new' | null>(null);
  const [form] = Form.useForm();

  const pending = items.filter((f) => f.source === FAQ_SOURCE.PENDING);
  const shown = items.filter(
    (f) =>
      filter === 'all' ||
      (filter === 'pending'
        ? f.source === FAQ_SOURCE.PENDING
        : f.source !== FAQ_SOURCE.PENDING),
  );

  const open = (item: FaqItem | 'new') => {
    form.resetFields();
    if (item !== 'new') form.setFieldsValue(item);
    setEditing(item);
  };

  return (
    <CardShell
      id="card-faq"
      title="常见问题"
      done={done}
      actions={
        !readOnly && (
          <Button icon={<PlusOutlined />} onClick={() => open('new')}>
            添加
          </Button>
        )
      }
    >
      {platform && items.length > 0 && (
        <Segmented
          style={{ marginBottom: 16 }}
          value={filter}
          onChange={(v) => setFilter(v as typeof filter)}
          options={[
            { value: 'all', label: `全部 ${items.length}` },
            { value: 'pending', label: `待审核 ${pending.length}` },
            {
              value: 'confirmed',
              label: `已确认 ${items.length - pending.length}`,
            },
          ]}
        />
      )}
      {error ? (
        <EmptyHint
          title="加载失败"
          description={error}
          actionText="重试"
          onAction={load}
        />
      ) : loading ? (
        <div style={{ color: palette.sub }}>正在加载…</div>
      ) : items.length === 0 ? (
        <CardEmpty
          benefit="提前回答客户最常问的问题；只写商品本身，不写我们自己的质保、库存、发货承诺。"
          actionText={readOnly ? undefined : '添加第一条'}
          onAction={() => open('new')}
        />
      ) : (
        <ul
          style={{
            listStyle: 'none',
            margin: 0,
            padding: 0,
            display: 'grid',
            gap: 12,
          }}
        >
          {shown.map((f) => {
            const isPending = f.source === FAQ_SOURCE.PENDING;
            return (
              <li
                key={f.id}
                style={{
                  padding: 16,
                  borderRadius: 12,
                  background: isPending ? palette.orangeSoft : palette.inset,
                  border: `1px solid ${isPending ? palette.orange : palette.hairline}`,
                }}
              >
                <div
                  style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}
                >
                  <b style={{ flex: 1 }}>{f.question}</b>
                  {isPending && <Pill tone="orange">待审核</Pill>}
                  {!readOnly && isPending && (
                    <Popconfirm
                      title="确认发布这条 FAQ？"
                      description="确认后所有租户都能看到"
                      okText="确认"
                      cancelText="取消"
                      onConfirm={async () => {
                        await faqApi.approve(productId, f.id);
                        message.success('已确认');
                        load();
                        onChanged();
                      }}
                    >
                      <Button size="small" type="primary">
                        确认
                      </Button>
                    </Popconfirm>
                  )}
                  {!readOnly && (
                    <RowActions
                      label="FAQ"
                      onEdit={() => open(f)}
                      onDelete={async () => {
                        await faqApi.remove(productId, f.id);
                        message.success('已删除');
                        load();
                        onChanged();
                      }}
                    />
                  )}
                </div>
                <p
                  style={{
                    margin: '8px 0 0',
                    color: palette.sub,
                    fontSize: 14,
                    whiteSpace: 'pre-wrap',
                  }}
                >
                  {f.answer}
                </p>
                {f.reviewedBy && (
                  <div
                    style={{ marginTop: 8, fontSize: 12, color: palette.mute }}
                  >
                    {f.reviewedBy} 已确认
                    {f.reviewedAt
                      ? ` · ${f.reviewedAt.replace('T', ' ').slice(0, 16)}`
                      : ''}
                  </div>
                )}
              </li>
            );
          })}
          {shown.length === 0 && (
            <div style={{ color: palette.sub }}>这个分类下没有 FAQ。</div>
          )}
        </ul>
      )}
      <Modal
        open={editing !== null}
        title={editing === 'new' ? '添加 FAQ' : '编辑 FAQ'}
        onCancel={() => setEditing(null)}
        destroyOnHidden
        okText="保存"
        cancelText="取消"
        onOk={async () => {
          const v = await form.validateFields();
          if (editing === 'new') await faqApi.create(productId, v);
          else if (editing) await faqApi.update(productId, editing.id, v);
          message.success('已保存');
          setEditing(null);
          load();
          onChanged();
        }}
      >
        {editing &&
          editing !== 'new' &&
          editing.source === FAQ_SOURCE.PENDING && (
            <div
              role="note"
              style={{ marginBottom: 12, color: palette.orange, fontSize: 13 }}
            >
              保存不会改变待审核状态，需要另外点「确认」。
            </div>
          )}
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="question"
            label="问题"
            rules={[
              { required: true, message: '请输入问题' },
              { max: 256, message: '问题不超过 256 个字符' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="answer"
            label="答案"
            rules={[{ required: true, message: '请输入答案' }]}
          >
            <Input.TextArea rows={4} maxLength={5000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </CardShell>
  );
};
