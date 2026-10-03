import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  PlusOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Form, Modal, Radio, Select, Switch, Table } from 'antd';
import React, { useEffect, useState } from 'react';
import BrandCategoryPicker from '@/components/BrandCategoryPicker';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { Card, PageTitle, Pill } from '../shared/components';
import { PATHS } from '../shared/constants';
import {
  type AssignRule,
  boardApi,
  type Purchaser,
  readBizError,
} from '../shared/service';

interface RuleForm {
  matchType: number;
  matchValues: string[];
  assigneeId: number;
  status: boolean;
}

/** 分配规则：按品牌、品类指定采购，没命中按推荐；总开关默认关闭 */
const AssignRulesPage: React.FC = () => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['inquiry:rule:edit'];
  const [rules, setRules] = useState<AssignRule[] | null>(null);
  const [autoAssign, setAutoAssign] = useState(false);
  const [purchasers, setPurchasers] = useState<Purchaser[]>([]);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<AssignRule | 'new' | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<RuleForm>();
  const matchType = Form.useWatch('matchType', form);

  const load = async () => {
    setError('');
    try {
      const r = await boardApi.rules();
      setRules(r.rules);
      setAutoAssign(r.autoAssign);
    } catch (e) {
      setError(readBizError(e).message);
    }
  };

  useEffect(() => {
    load();
    boardApi
      .purchasers()
      .then(setPurchasers)
      .catch(() => setPurchasers([]));
  }, []);

  useEffect(() => {
    if (!editing) return;
    form.setFieldsValue(
      editing === 'new'
        ? { matchType: 1, matchValues: [], assigneeId: undefined, status: true }
        : {
            matchType: editing.matchType,
            matchValues: editing.matchValues,
            assigneeId: editing.assigneeId,
            status: editing.status === 1,
          },
    );
  }, [editing]);

  const act = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn();
      message.success(ok);
      await load();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const move = (index: number, delta: number) => {
    if (!rules) return;
    const ids = rules.map((r) => r.id);
    const [id] = ids.splice(index, 1);
    ids.splice(index + delta, 0, id);
    act(() => boardApi.reorderRules(ids), '顺序已调整');
  };

  const save = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      const data = {
        matchType: v.matchType,
        matchValues: v.matchValues,
        assigneeId: v.assigneeId,
        status: v.status ? 1 : 0,
      };
      if (editing === 'new') await boardApi.createRule(data);
      else if (editing) await boardApi.updateRule(editing.id, data);
      message.success('规则已保存');
      setEditing(null);
      await load();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const columns: TableColumnsType<AssignRule> = [
    {
      title: '优先级',
      width: 120,
      render: (_, r, i) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <b style={{ color: palette.ink, width: 20 }}>{r.priority}</b>
          {canEdit && (
            <>
              <Button
                size="small"
                type="text"
                aria-label="上移"
                icon={<ArrowUpOutlined />}
                disabled={i === 0}
                onClick={() => move(i, -1)}
              />
              <Button
                size="small"
                type="text"
                aria-label="下移"
                icon={<ArrowDownOutlined />}
                disabled={i === (rules?.length ?? 0) - 1}
                onClick={() => move(i, 1)}
              />
            </>
          )}
        </span>
      ),
    },
    {
      title: '匹配',
      render: (_, r) => (
        <span>
          {r.matchType === 1 ? '品牌' : '品类'} = {r.matchValues.join('、')}
        </span>
      ),
    },
    {
      title: '分配给',
      dataIndex: 'assigneeName',
      width: 160,
      render: (v) => v || '—',
    },
    {
      title: '近 30 天命中',
      dataIndex: 'hits',
      width: 130,
      render: (v) => `${v} 次`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v, r) => (
        <Switch
          size="small"
          checked={v === 1}
          disabled={!canEdit}
          onChange={(on) =>
            act(
              () =>
                boardApi.updateRule(r.id, {
                  matchType: r.matchType,
                  matchValues: r.matchValues,
                  assigneeId: r.assigneeId,
                  status: on ? 1 : 0,
                }),
              on ? '规则已启用' : '规则已停用',
            )
          }
        />
      ),
    },
    {
      title: '操作',
      width: 120,
      render: (_, r) =>
        canEdit ? (
          <span style={{ display: 'inline-flex', gap: 12 }}>
            <a onClick={() => setEditing(r)}>编辑</a>
            <a
              style={{ color: palette.red }}
              onClick={() =>
                modal.confirm({
                  title: '删除这条规则？',
                  content:
                    '删除后命中这条规则的任务改为按后面的规则或推荐分配，已分配的任务不受影响。',
                  okText: '删除',
                  okButtonProps: { danger: true },
                  cancelText: '取消',
                  onOk: () =>
                    act(() => boardApi.deleteRule(r.id), '规则已删除'),
                })
              }
            >
              删除
            </a>
          </span>
        ) : null,
    },
  ];

  if (error) return <ErrorHint message={error} onRetry={load} />;

  return (
    <div>
      <PageTitle
        crumbs={[
          <Link key="b" to={PATHS.board}>
            分配工作台
          </Link>,
          '分配规则',
        ]}
        title="分配规则"
        description="按品牌、品类指定采购，没命中时按推荐。开启后新任务自动分配；关闭时可以在工作台选中任务手动「按规则分配」试用。"
        actions={
          canEdit && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setEditing('new')}
            >
              新增规则
            </Button>
          )
        }
      />
      <Card style={{ marginBottom: 16, padding: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <span
            style={{
              width: 40,
              height: 40,
              borderRadius: 12,
              background: palette.orangeSoft,
              color: palette.orange,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 18,
            }}
          >
            <ThunderboltOutlined />
          </span>
          <span style={{ display: 'grid' }}>
            <span style={{ color: palette.ink, fontWeight: 600 }}>
              自动分配
            </span>
            <span style={{ color: palette.mute, fontSize: 12 }}>
              {autoAssign
                ? '已开启：业务员确认型号后，新任务立即按规则分配'
                : '当前关闭：新任务进入待分配池，由采购负责人分配'}
            </span>
          </span>
          <Switch
            style={{ marginLeft: 'auto' }}
            checked={autoAssign}
            disabled={!canEdit}
            onChange={(on) =>
              modal.confirm({
                title: on ? '开启自动分配？' : '关闭自动分配？',
                content: on
                  ? '之后确认的询盘，任务会立即按下面的规则分配给采购，不再经过待分配池。'
                  : '之后的新任务进入待分配池，由采购负责人手动分配。',
                okText: on ? '开启' : '关闭',
                cancelText: '取消',
                onOk: () =>
                  act(
                    () => boardApi.setAutoAssign(on),
                    on ? '自动分配已开启' : '自动分配已关闭',
                  ),
              })
            }
          />
        </div>
      </Card>
      <Table<AssignRule>
        rowKey="id"
        columns={columns}
        dataSource={rules ?? []}
        loading={!rules}
        pagination={false}
        locale={{ emptyText: '还没有规则，没有规则时全部按推荐分配' }}
        footer={() => (
          <span style={{ display: 'flex', gap: 12, color: palette.sub }}>
            <b style={{ color: palette.ink }}>兜底</b>以上都没命中时，按推荐（近
            180 天品牌熟悉度 + 当前负载）
            <Pill tone="gray">始终生效</Pill>
          </span>
        )}
      />
      <div style={{ color: palette.mute, fontSize: 12, marginTop: 12 }}>
        规则按优先级从上到下匹配，同一任务只按第一条命中的规则分配；分配对象已不是采购时跳过这条规则。
      </div>

      <Modal
        open={!!editing}
        title={editing === 'new' ? '新增规则' : '编辑规则'}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        onOk={save}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" requiredMark>
          <Form.Item
            name="matchType"
            label="匹配方式"
            rules={[{ required: true }]}
          >
            <Radio.Group
              options={[
                { value: 1, label: '按品牌' },
                { value: 2, label: '按品类' },
              ]}
              // 品牌和品类的值不通用，切换方式时清空已选
              onChange={() => form.setFieldValue('matchValues', [])}
            />
          </Form.Item>
          <Form.Item
            name="matchValues"
            label="匹配值"
            extra={
              matchType === 2
                ? '可以选多个；也可以输入品类主数据里没有的名称，回车确认'
                : '可以选多个，支持按别名搜索；品牌按名称和别名匹配（如「台达」「Delta」视为同一品牌）'
            }
            rules={[{ required: true, message: '请选择匹配值' }]}
          >
            <BrandCategoryPicker
              multiple
              kind={matchType === 2 ? 'category' : 'brand'}
              placeholder={
                matchType === 2 ? '搜索或选择品类' : '搜索或选择品牌，如 台达'
              }
            />
          </Form.Item>
          <Form.Item
            name="assigneeId"
            label="分配给"
            rules={[{ required: true, message: '请选择采购' }]}
          >
            <Select
              placeholder="选择采购"
              options={purchasers.map((p) => ({
                value: p.id,
                label: `${p.name}${p.partTime ? '（兼职）' : ''}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="status" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AssignRulesPage;
