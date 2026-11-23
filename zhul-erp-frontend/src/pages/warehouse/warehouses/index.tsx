import {
  CodeSandboxOutlined,
  HomeOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Form, Input, Modal, Select, Skeleton, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { FilterField, MainCard, PageHeader } from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { FilterCard } from '../inventoryParts';
import { type InvWarehouse, WH_OWN, warehouseApi } from '../inventoryService';
import { readBizError } from '../service';

const MODES = [
  {
    value: 'dropship',
    label: '无货源',
    icon: <CodeSandboxOutlined />,
    hint: '货都是为订单采购的。交货代时库存不够，自动补一笔「系统外入库」，不拦业务。',
  },
  {
    value: 'stock',
    label: '库存模式',
    icon: <HomeOutlined />,
    hint: '有备货。交货代时库存不够，提示库存不足，不能交货代。',
  },
];

interface Filters {
  keyword?: string;
  type?: number;
  status?: number;
}

/** 库存模式：两张单选卡片；切换要二次确认 */
const ModeCard: React.FC<{ editable: boolean }> = ({ editable }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const [mode, setMode] = useState<string>();
  useEffect(() => {
    warehouseApi
      .mode()
      .then((r) => setMode(r.mode))
      .catch((e) => message.error(readBizError(e).message));
  }, [message]);

  const change = (next: (typeof MODES)[number]) => {
    if (!editable || next.value === mode) return;
    modal.confirm({
      title: `改为「${next.label}」？`,
      content: next.hint,
      okText: '确认修改',
      onOk: async () => {
        try {
          const r = await warehouseApi.setMode(next.value);
          setMode(r.mode);
          message.success(`库存模式已改为「${next.label}」`);
        } catch (e) {
          message.error(readBizError(e).message);
          throw e;
        }
      },
    });
  };

  return (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(160px, 0.6fr) 1fr 1fr',
          gap: 16,
          alignItems: 'stretch',
        }}
      >
        <div>
          <b style={{ color: palette.ink }}>库存模式</b>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
            决定交货代时库存不够怎么办
          </div>
        </div>
        {!mode ? (
          <Skeleton.Button active block style={{ height: 56 }} />
        ) : (
          MODES.map((m) => {
            const on = m.value === mode;
            return (
              <button
                key={m.value}
                type="button"
                aria-pressed={on}
                disabled={!editable && !on}
                onClick={() => change(m)}
                style={{
                  textAlign: 'left',
                  padding: '12px 16px',
                  borderRadius: 12,
                  border: `1px solid ${on ? palette.link : palette.hairline}`,
                  background: on ? palette.primarySoft : palette.inset,
                  cursor: editable && !on ? 'pointer' : 'default',
                  opacity: !editable && !on ? 0.6 : 1,
                }}
              >
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <span
                    style={{
                      width: 14,
                      height: 14,
                      borderRadius: 7,
                      border: `2px solid ${on ? palette.link : palette.control}`,
                      boxShadow: on
                        ? `inset 0 0 0 2px ${palette.card}`
                        : undefined,
                      background: on ? palette.link : 'transparent',
                    }}
                  />
                  <span style={{ color: on ? palette.link : palette.sub }}>
                    {m.icon}
                  </span>
                  <b style={{ color: palette.ink }}>{m.label}</b>
                </div>
                <div
                  style={{
                    fontSize: 12,
                    color: palette.mute,
                    marginTop: 4,
                    paddingLeft: 22,
                  }}
                >
                  {m.hint}
                </div>
              </button>
            );
          })
        )}
      </div>
    </Card>
  );
};

/** 仓库管理 → 仓库档案（spec warehouse/inventory「仓库档案」，原型 I4）：库存模式；公司仓库可改名，货代仓跟着货代 */
const WarehousesPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const editable = !!access['inventory:warehouse:edit'];
  const [form] = Form.useForm();
  const initial = (): Filters => ({ status: 1 });
  const [filters, setFilters] = useState<Filters>(initial);
  const [list, setList] = useState<InvWarehouse[]>();
  const [error, setError] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [renaming, setRenaming] = useState<InvWarehouse>();
  const [name, setName] = useState('');
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setList(await warehouseApi.list());
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, []);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  const kw = filters.keyword?.trim().toLowerCase();
  const rows = (list ?? []).filter(
    (w) =>
      (!kw ||
        w.name.toLowerCase().includes(kw) ||
        w.code.toLowerCase().includes(kw)) &&
      (filters.type == null || w.type === filters.type) &&
      (filters.status == null || w.status === filters.status),
  );

  const columns: TableColumnsType<InvWarehouse> = [
    {
      title: '编码',
      dataIndex: 'code',
      width: 100,
      fixed: 'left',
      render: (v: string) => (
        <span style={{ fontFamily: 'ui-monospace, Menlo, monospace' }}>
          {v}
        </span>
      ),
    },
    {
      title: '仓库名称',
      dataIndex: 'name',
      width: 200,
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (v: number, r) => (
        <Pill tone={v === WH_OWN ? 'accent' : 'violet'}>{r.typeName}</Pill>
      ),
    },
    {
      title: '关联货代',
      dataIndex: 'forwarderId',
      width: 180,
      render: (v: number | undefined, r) =>
        v ? (
          <Link
            to={`/logistics/forwarders?keyword=${encodeURIComponent(r.name)}`}
          >
            {r.name}
          </Link>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '在库件数',
      dataIndex: 'onHand',
      width: 90,
      render: (v: number) => <b>{v}</b>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number, r) => (
        <Pill tone={v === 1 ? 'green' : 'mute'}>{r.statusName}</Pill>
      ),
    },
    ...auditColumns<InvWarehouse>(),
    {
      title: '操作',
      key: 'op',
      width: 140,
      fixed: 'right',
      render: (_, r) => (
        <span style={{ display: 'inline-flex', gap: 12 }}>
          {r.type === WH_OWN && editable && (
            <button
              type="button"
              className="zhul-link"
              onClick={() => {
                setRenaming(r);
                setName(r.name);
              }}
            >
              改名
            </button>
          )}
          <Link
            to={
              r.type === WH_OWN
                ? '/warehouse/stocks?warehouse=own'
                : `/warehouse/stocks?warehouseId=${r.id}`
            }
          >
            查看库存
          </Link>
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader help="货代仓在新建货代时自动生成，名称跟着货代" />
      <ModeCard editable={editable} />
      <FilterCard
        form={form}
        initialValues={initial()}
        onSearch={(v) => {
          setFilters(v as Filters);
          setPage(1);
        }}
        onReset={() => {
          setFilters(initial());
          setPage(1);
        }}
      >
        <FilterField label="关键词" span={2}>
          <Form.Item name="keyword" noStyle>
            <Input
              aria-label="关键词"
              allowClear
              prefix={<SearchOutlined />}
              onPressEnter={() => form.submit()}
              placeholder="仓库名称、编码"
            />
          </Form.Item>
        </FilterField>
        <FilterField label="类型">
          <Form.Item name="type" noStyle>
            <Select
              aria-label="类型"
              allowClear
              placeholder="全部"
              options={[
                { value: 1, label: '自有仓' },
                { value: 2, label: '货代仓' },
              ]}
            />
          </Form.Item>
        </FilterField>
        <FilterField label="状态">
          <Form.Item name="status" noStyle>
            <Select
              aria-label="状态"
              allowClear
              placeholder="全部"
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
          </Form.Item>
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard left={list ? `共 ${rows.length} 条` : undefined}>
          <Table<InvWarehouse>
            rowKey="id"
            size="middle"
            loading={!list}
            columns={columns}
            dataSource={rows}
            scroll={{ x: tableScrollX(columns) }}
            locale={{ emptyText: '没有符合条件的仓库' }}
            pagination={{
              current: page,
              pageSize,
              showSizeChanger: true,
              onChange: (p, s) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </MainCard>
      )}
      <Modal
        open={!!renaming}
        title="仓库改名"
        okText="保存"
        confirmLoading={saving}
        onCancel={() => setRenaming(undefined)}
        onOk={async () => {
          if (!renaming) return;
          if (!name.trim()) {
            message.warning('请填写仓库名称');
            return;
          }
          setSaving(true);
          try {
            await warehouseApi.rename(renaming.id, name.trim());
            message.success('已改名');
            setRenaming(undefined);
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          } finally {
            setSaving(false);
          }
        }}
        destroyOnHidden
      >
        <Input
          value={name}
          maxLength={50}
          aria-label="仓库名称"
          onChange={(e) => setName(e.target.value)}
          onPressEnter={(e) => e.currentTarget.blur()}
        />
      </Modal>
    </div>
  );
};

export default WarehousesPage;
