import { EllipsisOutlined, PlusOutlined } from '@ant-design/icons';
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
import {
  App,
  Button,
  Dropdown,
  Input,
  Select,
  Space,
  Tabs,
  Tooltip,
} from 'antd';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { proAuditColumns } from '@/components/UserName';
import { useIndustryOptions } from '@/utils/dict';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
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
  brandApi,
  type PendingBrand,
  pendingBrandApi,
  readBizError,
} from '../service';
import { ProductThemeProvider } from '../theme';
import PendingTab from './PendingTab';

/** 帮助文字：help-texts.md「品牌管理」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  page: '商品归属的品牌，所有租户共用同一份；核心品牌覆盖 PLC、HMI、驱动、伺服四个主营方向，在各处品牌下拉里排在最前',
} as const;

const LEVEL_TABS: { value: number | 'all'; label: string; key: string }[] = [
  { value: 'all', label: '全部', key: 'all' },
  { value: 2, label: '核心', key: '2' },
  { value: 1, label: '常做', key: '1' },
  { value: 0, label: '普通', key: '0' },
];

const ONE_LINE: React.CSSProperties = {
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
  minWidth: 0,
};

const TWO_LINES: React.CSSProperties = {
  display: '-webkit-box',
  WebkitLineClamp: 2,
  WebkitBoxOrient: 'vertical',
  overflow: 'hidden',
  lineHeight: 1.5,
};

/** 别名里的第一个中文写法，作为品牌的中文名显示 */
const chineseName = (aliases?: string[]) =>
  aliases?.find((a) => /[\u4e00-\u9fff]/.test(a));

/** 品牌等级标签：核心、常做显示，普通不显示 */
const LevelPill: React.FC<{ level?: number }> = ({ level }) =>
  level === 2 ? (
    <Pill tone="violet">核心</Pill>
  ) : level === 1 ? (
    <Pill tone="accent">常做</Pill>
  ) : null;

const BrandPage: React.FC = () => {
  const pager = usePagination(20);
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
  const canConfirm = !!access['product:brand:edit'];
  const [level, setLevel] = useState<number | 'all'>('all');
  const [levelCounts, setLevelCounts] = useState<Record<string, number>>({});
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState<string>();
  const [country, setCountry] = useState<string>();
  const [industry, setIndustry] = useState<string>();
  const { options: industryOptions, labelOf: industryLabel } =
    useIndustryOptions();
  const [status, setStatus] = useState<number>();
  /** 原产地、行业点「查询」才生效；等级、状态切换后立即查询 */
  const [draft, setDraft] = useState<{ country?: string; industry?: string }>(
    {},
  );
  const apply = () => {
    setKeyword(keywordInput.trim() || undefined);
    setCountry(draft.country);
    setIndustry(draft.industry);
  };
  const [total, setTotal] = useState(0);

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

  const muted = <span style={{ opacity: 0.55 }}>—</span>;
  const columns: ProColumns<Brand>[] = [
    {
      title: '品牌',
      dataIndex: 'brandName',
      fixed: 'left',
      width: 250,
      render: (_, row) => {
        const zh = chineseName(row.aliases);
        const place = countryZh(row.country);
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <BrandMark name={row.brandName} color={row.brandColor} size={32} />
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                  {row.brandName}
                </span>
                <LevelPill level={row.brandLevel} />
              </div>
              <div
                style={{ fontSize: 12, opacity: 0.65, whiteSpace: 'nowrap' }}
              >
                {[zh, place].filter(Boolean).join(' · ') || '—'}
              </div>
            </div>
          </div>
        );
      },
    },
    {
      title: '别名',
      dataIndex: 'aliases',
      width: 190,
      render: (_, r) => {
        const list = r.aliases ?? [];
        if (!list.length) return muted;
        const shown = list.slice(0, 3).join('、');
        return (
          <Tooltip title={list.join('、')}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={ONE_LINE}>{shown}</span>
              {list.length > 3 && <Pill tone="gray">+{list.length - 3}</Pill>}
            </div>
          </Tooltip>
        );
      },
    },
    {
      title: '行业',
      dataIndex: 'industryCodes',
      width: 150,
      render: (_, r) =>
        r.industryCodes?.length ? (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
            {r.industryCodes.map((c) => (
              <Pill key={c} tone="accent">
                {industryLabel(c)}
              </Pill>
            ))}
          </div>
        ) : (
          muted
        ),
    },
    {
      title: '中文简介',
      dataIndex: 'descriptionZh',
      width: 280,
      render: (_, r) =>
        r.descriptionZh ? (
          <span title={r.descriptionZh} style={TWO_LINES}>
            {r.descriptionZh}
          </span>
        ) : (
          <span style={{ opacity: 0.55 }}>未填写</span>
        ),
    },
    {
      title: '商品数',
      dataIndex: 'productCount',
      align: 'right',
      width: 80,
      render: (_, r) => <span className="num">{r.productCount}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 150,
      render: (_, r) => (
        <Space size={4}>
          {r.status === 1 ? (
            <Pill tone="green">启用</Pill>
          ) : (
            <Pill tone="gray">已停用</Pill>
          )}
          {r.isGenuine === 0 && (
            <Tooltip title="兼容 / 非原厂：不能对这个品牌的商品使用「正品」类表述">
              <span>
                <Pill tone="orange">非原厂</Pill>
              </span>
            </Tooltip>
          )}
        </Space>
      ),
    },
    ...proAuditColumns<Brand>(),
    {
      title: '操作',
      hideInTable:
        !access['product:brand:edit'] && !access['product:brand:delete'],
      valueType: 'option',
      fixed: 'right',
      width: 140,
      render: (_, row) => {
        const canEdit = !!access['product:brand:edit'];
        const canDelete = !!access['product:brand:delete'];
        return (
          <Space size={12} style={{ whiteSpace: 'nowrap' }}>
            {canEdit && (
              <button
                type="button"
                className="zhul-link"
                onClick={() => openForm(row)}
              >
                编辑
              </button>
            )}
            {canEdit && (
              <button
                type="button"
                className="zhul-link"
                onClick={() => toggleStatus(row)}
              >
                {row.status === 1 ? '停用' : '启用'}
              </button>
            )}
            {canDelete && (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    {
                      key: 'delete',
                      danger: true,
                      disabled: row.productCount > 0,
                      label:
                        row.productCount > 0 ? (
                          <Tooltip
                            title={`已有 ${row.productCount} 个商品使用，请先停用`}
                          >
                            删除
                          </Tooltip>
                        ) : (
                          '删除'
                        ),
                      onClick: () => remove(row),
                    },
                  ],
                }}
              >
                <Button
                  type="text"
                  size="small"
                  icon={<EllipsisOutlined />}
                  aria-label={`${row.brandName} 更多操作`}
                />
              </Dropdown>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <ProductThemeProvider>
      <PageHeader
        help={HELP.page}
        extra={
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
        <PendingTab
          pending={pending}
          loading={pendingLoading}
          reload={loadPending}
          canAdd={!!access['product:brand:add']}
          onCreate={(row) => openForm(null, row)}
          onLinked={() => actionRef.current?.reload()}
        />
      ) : (
        <>
          <FilterCard
            onSearch={apply}
            onReset={() => {
              setKeywordInput('');
              setKeyword(undefined);
              setDraft({});
              setCountry(undefined);
              setIndustry(undefined);
              setLevel('all');
              setStatus(undefined);
            }}
            moreCount={[country, industry].filter((v) => v != null).length}
            more={
              <>
                <FilterField label="原产地">
                  <Select
                    allowClear
                    showSearch={{ optionFilterProp: 'label' }}
                    placeholder="全部"
                    value={draft.country}
                    onChange={(v) => setDraft({ ...draft, country: v })}
                    options={countryOptions}
                    aria-label="原产地"
                  />
                </FilterField>
                <FilterField label="行业">
                  <Select
                    allowClear
                    placeholder="全部"
                    value={draft.industry}
                    onChange={(v) => setDraft({ ...draft, industry: v })}
                    options={industryOptions}
                    aria-label="行业"
                  />
                </FilterField>
              </>
            }
          >
            <FilterField label="关键词" span={2}>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="品牌名称或别名"
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                onPressEnter={apply}
                aria-label="关键词"
              />
            </FilterField>
            <FilterField label="等级">
              {/* 等级带数量，和状态一样切换后立即查询（原来的分段按钮） */}
              <Select<number | 'all'>
                value={level}
                onChange={setLevel}
                aria-label="等级"
                options={LEVEL_TABS.map((t) => ({
                  value: t.value,
                  // 为 0 时只写名称；「全部」不带数量（与各页状态下拉一致）
                  label:
                    levelCounts[t.key] && t.key !== 'all'
                      ? `${t.label} · ${levelCounts[t.key]}`
                      : t.label,
                }))}
              />
            </FilterField>
            <FilterField label="状态">
              <Select
                allowClear
                placeholder="全部"
                value={status}
                onChange={setStatus}
                options={[
                  { value: 1, label: '启用' },
                  { value: 0, label: '已停用' },
                ]}
                aria-label="状态"
              />
            </FilterField>
          </FilterCard>
          <MainCard left={`共 ${total} 个品牌`}>
            <ProTable<Brand>
              rowKey="id"
              actionRef={actionRef}
              columns={columns}
              params={{ level, keyword, country, status, industry }}
              search={false}
              options={false}
              toolBarRender={false}
              scroll={{ x: tableScrollX(columns) }}
              request={async (params) => {
                const [res, counts] = await Promise.all([
                  brandApi.page({
                    page: params.current,
                    pageSize: params.pageSize,
                    keyword,
                    country,
                    status,
                    industryCode: industry,
                    brandLevel: level === 'all' ? undefined : level,
                  }),
                  brandApi.levelCounts({ status }),
                ]);
                setLevelCounts(counts);
                setTotal(res.total);
                return { data: res.records, total: res.total, success: true };
              }}
              pagination={pager}
              locale={{
                emptyText: '没有符合条件的品牌',
              }}
            />
          </MainCard>
        </>
      )}
      <ModalForm<{
        brandName: string;
        country?: string;
        logoUrl?: string;
        brandColor?: string;
        description?: string;
        descriptionZh?: string;
        brandLevel: number;
        isGenuine: number;
        aliases?: string[];
        industryCodes?: string[];
      }>
        name="brandEdit"
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
                brandLevel: 0,
                industryCodes: industryOptions.slice(0, 1).map((o) => o.value),
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
        <ProFormSelect
          name="industryCodes"
          label="所属行业"
          mode="multiple"
          placeholder="选择行业，可多选"
          options={industryOptions}
          rules={[{ required: true, message: '请选择行业' }]}
          extra="租户只看到本行业的品牌和它的系列；跨行业的品牌选多个"
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
        <ProFormRadio.Group
          name="brandLevel"
          label="品牌等级"
          radioType="button"
          options={[
            { value: 2, label: '核心' },
            { value: 1, label: '常做' },
            { value: 0, label: '普通' },
          ]}
          extra="核心品牌是主营品牌；各处品牌下拉按核心 → 常做 → 普通排序"
        />
        <ProFormTextArea
          name="descriptionZh"
          label="中文简介"
          placeholder="一两句话介绍这个品牌，系统内显示"
          fieldProps={{
            maxLength: DESCRIPTION_MAX,
            showCount: true,
            autoSize: { minRows: 2, maxRows: 5 },
          }}
          rules={[{ max: DESCRIPTION_MAX, message: '简介不能超过 500 个字符' }]}
        />
        <ProFormTextArea
          name="description"
          label="英文简介"
          placeholder="English description for the website brand page"
          fieldProps={{
            maxLength: DESCRIPTION_MAX,
            showCount: true,
            autoSize: { minRows: 2, maxRows: 5 },
          }}
          rules={[{ max: DESCRIPTION_MAX, message: '简介不能超过 500 个字符' }]}
          extra="独立站品牌页等对外场景使用"
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
    </ProductThemeProvider>
  );
};

export default BrandPage;
