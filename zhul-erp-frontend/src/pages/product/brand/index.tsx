import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProForm,
  ProFormRadio,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Select, Space, Table, Tabs, Tooltip } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { formatDateTime } from '@/utils/format';
import BrandColorInput from '../components/BrandColorInput';
import { BrandMark, Pill } from '../components/Pills';
import { useCountries } from '../components/useCountries';
import {
  BRAND_COLOR_PATTERN,
  DESCRIPTION_MAX,
  randomBrandColor,
} from '../constants';
import {
  type Brand,
  type BrandOption,
  brandApi,
  type PendingBrand,
  pendingBrandApi,
  readBizError,
} from '../service';
import { PageHeader, ProductThemeProvider } from '../theme';

const BrandPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess();
  const actionRef = useRef<ActionType>(undefined);
  const [editing, setEditing] = useState<Brand | null>(null);
  const [open, setOpen] = useState(false);
  // 新建品牌时预填的随机主题色：打开表单时取一次，不能放在 initialValues 里每次渲染重算
  const [defaultColor, setDefaultColor] = useState('');
  const { options: countryOptions, zhOf: countryZh } = useCountries();
  const [tab, setTab] = useState<'brands' | 'pending'>('brands');
  const [pending, setPending] = useState<PendingBrand[]>([]);
  const [pendingLoading, setPendingLoading] = useState(false);
  // 「新建为品牌」复用品牌表单：记下正在确认的待确认品牌
  const [creatingFrom, setCreatingFrom] = useState<PendingBrand | null>(null);
  const [linking, setLinking] = useState<PendingBrand | null>(null);
  const [linkTarget, setLinkTarget] = useState<number>();
  const [brandOptions, setBrandOptions] = useState<BrandOption[]>([]);
  const canConfirm = !!access['product:brand:edit'];

  const loadPending = useCallback(async () => {
    setPendingLoading(true);
    try {
      setPending(await pendingBrandApi.list());
    } finally {
      setPendingLoading(false);
    }
  }, []);

  // 页签上要显示待确认数量作为提醒，所以进页面就取一次
  useEffect(() => {
    if (canConfirm) loadPending();
  }, [canConfirm, loadPending]);

  const openForm = (row: Brand | null, from: PendingBrand | null = null) => {
    setEditing(row);
    setCreatingFrom(from);
    if (!row) setDefaultColor(randomBrandColor());
    setOpen(true);
  };

  const openLink = async (row: PendingBrand) => {
    setLinking(row);
    setLinkTarget(undefined);
    setBrandOptions(await brandApi.options());
  };

  const confirmLink = async () => {
    if (!linking || !linkTarget) return;
    try {
      await pendingBrandApi.linkAsAlias(linking.pendingKey, linkTarget);
      message.success(
        `已设为别名，${linking.supplierCount} 个供应商已关联到该品牌`,
      );
      setLinking(null);
      loadPending();
      actionRef.current?.reload();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const toggleStatus = (row: Brand) => {
    const disabling = row.status === 1;
    modal.confirm({
      title: disabling
        ? `停用「${row.brandName}」？`
        : `启用「${row.brandName}」？`,
      content: disabling
        ? '停用后新建商品时选不到这个品牌，已有商品仍正常显示品牌名称。'
        : '启用后可以在新建商品时选择这个品牌。',
      okText: disabling ? '停用' : '启用',
      cancelText: '取消',
      onOk: async () => {
        await brandApi.setStatus(row.id, disabling ? 0 : 1);
        message.success(disabling ? '已停用' : '已启用');
        actionRef.current?.reload();
      },
    });
  };

  const remove = (row: Brand) => {
    modal.confirm({
      title: `删除「${row.brandName}」？`,
      content:
        '删除后品牌不再出现在列表中，同名品牌也不能重新创建。这个操作不能在页面上撤销。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await brandApi.remove(row.id);
          message.success('已删除');
          actionRef.current?.reload();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });
  };

  const columns: ProColumns<Brand>[] = [
    {
      title: '品牌名称',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '输入品牌名称或别名' },
    },
    {
      title: '状态',
      dataIndex: 'statusFilter',
      hideInTable: true,
      valueType: 'select',
      valueEnum: { 1: { text: '启用' }, 0: { text: '停用' } },
    },
    {
      title: '品牌',
      dataIndex: 'brandName',
      search: false,
      render: (_, row) => (
        <Space>
          <BrandMark name={row.brandName} color={row.brandColor} />
          <span style={{ fontWeight: 600 }}>{row.brandName}</span>
        </Space>
      ),
    },
    {
      title: '别名',
      dataIndex: 'aliases',
      search: false,
      width: 200,
      render: (_, r) =>
        r.aliases?.length ? (
          <Space size={4} wrap>
            {r.aliases.map((a) => (
              <Pill key={a} tone="gray">
                {a}
              </Pill>
            ))}
          </Space>
        ) : (
          <span style={{ opacity: 0.6 }}>—</span>
        ),
    },
    {
      title: '原产地',
      dataIndex: 'country',
      search: false,
      width: 110,
      render: (_, r) => countryZh(r.country) || '—',
    },
    {
      title: '简介',
      dataIndex: 'description',
      search: false,
      ellipsis: true,
      width: 200,
      render: (_, r) =>
        r.description || <span style={{ opacity: 0.6 }}>未填写</span>,
    },
    {
      title: '类型',
      dataIndex: 'isGenuine',
      search: false,
      width: 120,
      render: (_, r) =>
        r.isGenuine === 1 ? (
          <Pill tone="green">原厂正品</Pill>
        ) : (
          <Pill tone="orange">兼容 / 非原厂</Pill>
        ),
    },
    {
      title: '商品数',
      dataIndex: 'productCount',
      search: false,
      align: 'right',
      render: (_, r) => <span className="num">{r.productCount}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      render: (_, r) =>
        r.status === 1 ? (
          <Pill tone="green">启用</Pill>
        ) : (
          <Pill tone="gray">已停用</Pill>
        ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      search: false,
      width: 160,
      render: (_, r) => (
        <span className="num">{formatDateTime(r.createTime)}</span>
      ),
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      search: false,
      width: 90,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      search: false,
      width: 160,
      render: (_, r) => (
        <span className="num">{formatDateTime(r.updateTime)}</span>
      ),
    },
    {
      title: '更新人',
      dataIndex: 'updateBy',
      search: false,
      width: 90,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 150,
      render: (_, row) => [
        access['product:brand:edit'] && (
          <a key="edit" onClick={() => openForm(row)}>
            编辑
          </a>
        ),
        access['product:brand:edit'] && (
          <a key="status" onClick={() => toggleStatus(row)}>
            {row.status === 1 ? '停用' : '启用'}
          </a>
        ),
        access['product:brand:delete'] &&
          (row.productCount > 0 ? (
            <Tooltip
              key="delete"
              title={`已有 ${row.productCount} 个商品使用，请先停用`}
            >
              <span
                aria-disabled="true"
                style={{ opacity: 0.6, cursor: 'not-allowed' }}
              >
                删除
              </span>
            </Tooltip>
          ) : (
            <a key="delete" onClick={() => remove(row)}>
              删除
            </a>
          )),
      ],
    },
  ];

  return (
    <ProductThemeProvider>
      <PageHeader
        eyebrow="PRODUCT MASTER"
        title="品牌"
        description="商品归属的品牌，所有租户共用同一份。同一个品牌只保留一条记录。"
        actions={
          access['product:brand:add'] && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openForm(null)}
            >
              新增品牌
            </Button>
          )
        }
      />
      {canConfirm && (
        <Tabs
          activeKey={tab}
          onChange={(k) => setTab(k as 'brands' | 'pending')}
          items={[
            { key: 'brands', label: '品牌' },
            {
              key: 'pending',
              label: pending.length
                ? `待确认品牌 ${pending.length}`
                : '待确认品牌',
            },
          ]}
        />
      )}
      {tab === 'pending' && canConfirm ? (
        <Table<PendingBrand>
          rowKey="pendingKey"
          loading={pendingLoading}
          dataSource={pending}
          pagination={false}
          title={() =>
            '业务员在供应商主营产品里手填、品牌清单中还没有的名称。确认后相关供应商自动关联到正式品牌。'
          }
          locale={{ emptyText: '没有待确认的品牌' }}
          columns={[
            {
              title: '待确认名称',
              dataIndex: 'name',
              render: (v: string) => (
                <span style={{ fontWeight: 600 }}>{v}</span>
              ),
            },
            {
              title: '使用的供应商数',
              dataIndex: 'supplierCount',
              align: 'right',
              width: 140,
              render: (v: number) => <span className="num">{v}</span>,
            },
            {
              title: '首次出现',
              dataIndex: 'firstSeen',
              width: 180,
              render: (v: string) => (
                <span className="num">{formatDateTime(v)}</span>
              ),
            },
            {
              title: '操作',
              width: 260,
              render: (_: unknown, row: PendingBrand) => (
                <Space size={12} style={{ whiteSpace: 'nowrap' }}>
                  <a onClick={() => openLink(row)}>设为已有品牌的别名</a>
                  {access['product:brand:add'] && (
                    <a onClick={() => openForm(null, row)}>新建为品牌</a>
                  )}
                </Space>
              ),
            },
          ]}
        />
      ) : (
        <ProTable<Brand>
          rowKey="id"
          actionRef={actionRef}
          columns={columns}
          search={{ labelWidth: 'auto', defaultCollapsed: false }}
          options={false}
          request={async (params) => {
            const res = await brandApi.page({
              page: params.current,
              pageSize: params.pageSize,
              keyword: params.keyword,
              status:
                params.statusFilter === undefined
                  ? undefined
                  : Number(params.statusFilter),
            });
            return { data: res.records, total: res.total, success: true };
          }}
          pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 条记录` }}
          locale={{
            emptyText: '还没有品牌。先新增一个，新建商品时才能选择品牌。',
          }}
        />
      )}
      <ModalForm<{
        brandName: string;
        country?: string;
        logoUrl?: string;
        brandColor?: string;
        description?: string;
        isGenuine: number;
        aliases?: string[];
      }>
        title={editing ? '编辑品牌' : creatingFrom ? '新建为品牌' : '新增品牌'}
        open={open}
        onOpenChange={setOpen}
        width={560}
        modalProps={{ destroyOnHidden: true }}
        initialValues={
          editing
            ? { ...editing }
            : {
                isGenuine: 1,
                brandColor: defaultColor,
                brandName: creatingFrom?.name,
              }
        }
        onFinish={async (values) => {
          const data = { ...values, aliases: values.aliases ?? [] };
          try {
            if (editing) await brandApi.update(editing.id, data);
            else if (creatingFrom) {
              await pendingBrandApi.createBrand(creatingFrom.pendingKey, data);
              loadPending();
            } else await brandApi.create(data);
          } catch (e) {
            message.error(readBizError(e).message);
            return false;
          }
          message.success(editing ? '已保存' : '已新增');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText
          name="brandName"
          label="品牌名称"
          placeholder="如 Siemens"
          rules={[
            { required: true, message: '请输入品牌名称' },
            { max: 64, message: '品牌名称不超过 64 个字符' },
          ]}
          extra={
            creatingFrom
              ? `改成规范写法时，原名「${creatingFrom.name}」会自动成为别名`
              : '不区分大小写，Siemens 和 siemens 视为同一个品牌'
          }
        />
        <ProFormSelect
          name="aliases"
          label="别名"
          mode="tags"
          placeholder="输入后回车，如 西门子、SIEMENS AG"
          fieldProps={{
            tokenSeparators: [',', '，'],
            open: false,
            suffixIcon: null,
          }}
          extra="询盘、供应商里出现这些写法时会自动归到这个品牌；别名不能与其他品牌的名称或别名重复"
        />
        <ProForm.Group>
          <ProFormSelect
            name="country"
            label="原产地"
            width="sm"
            placeholder="选择或搜索国家 / 地区"
            options={countryOptions}
            fieldProps={{
              showSearch: true,
              allowClear: true,
              optionFilterProp: 'label',
            }}
            extra="只能从清单里选，中文名或英文名都能搜"
          />
          <ProForm.Item
            name="brandColor"
            label="品牌主题色"
            rules={[
              {
                pattern: BRAND_COLOR_PATTERN,
                message: '主题色格式应为 #RRGGBB',
              },
            ]}
            extra="新建时随机预填一个，可改、可清空"
          >
            <BrandColorInput />
          </ProForm.Item>
        </ProForm.Group>
        <ProFormTextArea
          name="description"
          label="品牌简介"
          placeholder="一两句话介绍这个品牌，独立站品牌页会用到"
          fieldProps={{
            maxLength: DESCRIPTION_MAX,
            showCount: true,
            autoSize: { minRows: 3, maxRows: 6 },
          }}
          rules={[{ max: DESCRIPTION_MAX, message: '简介不能超过 500 个字符' }]}
        />
        <ProFormText
          name="logoUrl"
          label="Logo 地址"
          rules={[
            { max: 256 },
            {
              pattern: /^$|^(https?:\/\/|\/(?![/\\]))\S+$/i,
              message: '地址必须以 http://、https:// 或 / 开头',
            },
          ]}
        />
        <ProFormRadio.Group
          name="isGenuine"
          label="是否原厂正品"
          options={[
            { value: 1, label: '原厂正品' },
            { value: 0, label: '兼容 / 非原厂' },
          ]}
          extra="标为「兼容 / 非原厂」的品牌，下游不能对它的商品使用「正品」类表述"
        />
      </ModalForm>
      <ModalForm
        title={linking ? `把「${linking.name}」设为已有品牌的别名` : ''}
        open={!!linking}
        onOpenChange={(v) => !v && setLinking(null)}
        width={440}
        modalProps={{ destroyOnHidden: true }}
        submitter={{
          searchConfig: { submitText: '设为别名' },
          submitButtonProps: { disabled: !linkTarget },
        }}
        onFinish={async () => {
          await confirmLink();
          return false;
        }}
      >
        <ProForm.Item
          label="归到品牌"
          extra={
            linking
              ? `确认后 ${linking.supplierCount} 个供应商的主营产品会改为关联这个品牌`
              : undefined
          }
        >
          <Select
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="搜索品牌名称或别名"
            value={linkTarget}
            onChange={setLinkTarget}
            options={brandOptions.map((b) => ({
              value: b.id,
              label: b.aliases?.length
                ? `${b.brandName}（${b.aliases.join('、')}）`
                : b.brandName,
            }))}
          />
        </ProForm.Item>
      </ModalForm>
    </ProductThemeProvider>
  );
};

export default BrandPage;
