import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormDependency,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Input, Select } from 'antd';
import {
  Ban,
  CircleCheck,
  FolderPlus,
  Pencil,
  Plus,
  Search,
  Trash2,
} from 'lucide-react';
import React, { useRef, useState } from 'react';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import EllipsisText from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { proAuditColumns } from '@/components/UserName';
import { MONO_FONT } from '@/theme/fonts';
import { useIndustryOptions } from '@/utils/dict';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { Pill } from '../components/Pills';
import { DESCRIPTION_MAX } from '../constants';
import { type Category, categoryApi, readBizError } from '../service';
import { ProductThemeProvider } from '../theme';
import TariffModal from './TariffModal';

const TWO_LINES: React.CSSProperties = {
  display: '-webkit-box',
  WebkitLineClamp: 2,
  WebkitBoxOrient: 'vertical',
  overflow: 'hidden',
  lineHeight: 1.5,
};

const CategoryPage: React.FC = () => {
  const pager = usePagination(20);
  const { message, modal } = App.useApp();
  const confirmDanger = useConfirmDanger();
  const access = useAccess();
  const actionRef = useRef<ActionType>(undefined);
  const [editing, setEditing] = useState<Category | null>(null);
  // 新增细分品类时预选的上级品类
  const [presetParent, setPresetParent] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [topLevels, setTopLevels] = useState<Category[]>([]);
  /** 正在查看关税参考的海关编码前 6 位 */
  const [tariffHs6, setTariffHs6] = useState<string>();

  const openForm = (row: Category | null, parentId: number | null = null) => {
    setEditing(row);
    setPresetParent(parentId);
    setOpen(true);
  };

  const toggleStatus = (row: Category) => {
    const disabling = row.status === 1;
    modal.confirm({
      title: disabling
        ? `停用「${row.categoryName}」？`
        : `启用「${row.categoryName}」？`,
      content: disabling
        ? '停用后新建商品时选不到这个品类，已有商品仍正常显示。'
        : '启用后可以在新建商品时选择这个品类。',
      okText: disabling ? '停用' : '启用',
      cancelText: '取消',
      onOk: async () => {
        await categoryApi.setStatus(row.id, disabling ? 0 : 1);
        message.success(disabling ? '已停用' : '已启用');
        actionRef.current?.reload();
      },
    });
  };

  const remove = (row: Category) => {
    confirmDanger({
      title: `删除「${row.categoryName}」？`,
      content: row.parentId
        ? '删除后细分品类不再出现，同编码的品类也不能重新创建。被供应商主营产品使用的细分品类不能删除，可改为停用。'
        : '删除后品类不再出现在列表中，同编码的品类也不能重新创建。',
      onOk: async () => {
        try {
          await categoryApi.remove(row.id);
          message.success('已删除');
          actionRef.current?.reload();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });
  };

  const { options: industryOptions, labelOf: industryLabel } =
    useIndustryOptions();
  /** 搜索条件：状态切换后立即查询，关键词、行业点「查询」才生效 */
  const [filters, setFilters] = useState<{
    keyword?: string;
    industry?: string;
    status?: number;
  }>({});
  const [keyword, setKeyword] = useState('');
  const [draftIndustry, setDraftIndustry] = useState<string>();
  // 首次查询返回前没有总数，主卡不显示「共 0 条」
  const [total, setTotal] = useState<number>();

  const columns: ProColumns<Category>[] = [
    {
      // 名称在前、编码在后（ui-redesign S9：编码是次要信息）
      title: '品类名称',
      dataIndex: 'categoryName',
      search: false,
      width: 240,
      render: (_, r) =>
        r.categoryNameZh ? (
          <EllipsisText
            tip={`${r.categoryNameZh} ${r.categoryName}`}
            text={
              <>
                {r.categoryNameZh}
                <span style={{ opacity: 0.6, marginLeft: 8 }}>
                  {r.categoryName}
                </span>
              </>
            }
          />
        ) : (
          <EllipsisText text={r.categoryName} />
        ),
    },
    {
      title: '品类编码',
      dataIndex: 'categoryCode',
      search: false,
      width: 200,
      render: (_, r) => (
        <code style={{ fontFamily: MONO_FONT }}>{r.categoryCode}</code>
      ),
    },
    {
      title: '层级',
      dataIndex: 'parentId',
      search: false,
      width: 90,
      render: (_, r) =>
        r.parentId ? (
          <Pill tone="gray">细分品类</Pill>
        ) : (
          <Pill tone="accent">一级品类</Pill>
        ),
    },
    {
      title: '默认海关编码',
      dataIndex: 'effectiveHsCode',
      search: false,
      width: 150,
      render: (_, r) =>
        r.effectiveHsCode ? (
          <span style={{ display: 'grid' }}>
            <span className="num" style={{ fontWeight: 600 }}>
              {r.effectiveHsCode}
            </span>
            {r.hsInherited && (
              <span style={{ fontSize: 12, opacity: 0.6 }}>沿用上级</span>
            )}
          </span>
        ) : (
          <span style={{ opacity: 0.45 }}>未填</span>
        ),
    },
    {
      title: '目的国关税参考',
      dataIndex: 'tariffFilled',
      search: false,
      width: 230,
      render: (_, r) => {
        if (!r.effectiveHsCode) return <span style={{ opacity: 0.45 }}>—</span>;
        const missing = r.tariffMissing ?? [];
        const text =
          r.tariffFilled && missing.length
            ? `${r.tariffFilled} 国已填 · ${missing.slice(0, 2).join('、')}${missing.length > 2 ? ' 等' : ''}未填`
            : r.tariffFilled
              ? `${r.tariffFilled} 国已填${r.tariffRange ? ` · ${r.tariffRange}` : ''}`
              : '未填';
        return (
          <span
            style={{ display: 'inline-flex', gap: 8, whiteSpace: 'nowrap' }}
          >
            <span
              title={missing.length ? `未填：${missing.join('、')}` : undefined}
              style={{ opacity: r.tariffFilled ? 1 : 0.6 }}
            >
              {text}
            </span>
            <button
              type="button"
              className="zhul-link"
              onClick={() => setTariffHs6(r.effectiveHsCode?.slice(0, 6))}
            >
              {access['product:category:edit'] ? '维护' : '查看'}
            </button>
          </span>
        );
      },
    },
    {
      title: '行业',
      dataIndex: 'industryCode',
      search: false,
      width: 110,
      render: (_, r) =>
        r.parentId ? (
          <span style={{ opacity: 0.55 }}>跟随上级</span>
        ) : (
          <Pill tone="accent">{industryLabel(r.industryCode)}</Pill>
        ),
    },
    {
      title: '中文简介',
      dataIndex: 'descriptionZh',
      search: false,
      width: 280,
      render: (_, r) =>
        r.descriptionZh ? (
          <span title={r.descriptionZh} style={TWO_LINES}>
            {r.descriptionZh}
          </span>
        ) : (
          <span style={{ opacity: 0.6 }}>未填写</span>
        ),
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      search: false,
      width: 90,
      align: 'right',
      render: (_, r) => <span className="num">{r.sortOrder}</span>,
    },
    {
      title: '商品数',
      dataIndex: 'productCount',
      search: false,
      width: 90,
      align: 'right',
      render: (_, r) => <span className="num">{r.productCount}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      width: 90,
      render: (_, r) =>
        r.status === 1 ? (
          <Pill tone="green">启用</Pill>
        ) : (
          <Pill tone="gray">已停用</Pill>
        ),
    },
    ...proAuditColumns<Category>(),
    {
      title: '操作',
      hideInTable:
        !access['product:category:add'] &&
        !access['product:category:edit'] &&
        !access['product:category:delete'],
      fixed: 'right',
      valueType: 'option',
      // 编辑、加细分品类放行内；停用/启用、删除收进「更多」（ui-redesign S6）
      width: rowActionsWidth(3),
      render: (_, row) => {
        const canEdit = !!access['product:category:edit'];
        const canDelete = !!access['product:category:delete'];
        return (
          <RowActions
            inline={[
              canEdit && {
                key: 'edit',
                label: '编辑',
                icon: <Pencil size={18} />,
                onClick: () => openForm(row),
              },
              access['product:category:add'] &&
                !row.parentId && {
                  key: 'sub',
                  label: '添加细分品类',
                  icon: <FolderPlus size={18} />,
                  onClick: () => openForm(null, row.id),
                },
            ]}
            more={[
              canEdit && {
                key: 'status',
                label: row.status === 1 ? '停用' : '启用',
                icon:
                  row.status === 1 ? (
                    <Ban size={16} />
                  ) : (
                    <CircleCheck size={16} />
                  ),
              },
              canDelete && {
                key: 'delete',
                label: '删除',
                icon: <Trash2 size={16} />,
                danger: true,
                disabledReason:
                  (row.children?.length ?? 0) > 0
                    ? '下面还有细分品类，请先删除或移走'
                    : row.productCount > 0
                      ? `已有 ${row.productCount} 个商品使用，请先停用`
                      : undefined,
              },
            ]}
            onMore={(key) => {
              if (key === 'status') toggleStatus(row);
              if (key === 'delete') remove(row);
            }}
          />
        );
      },
    },
  ];

  const locked = !!editing && editing.productCount > 0;

  return (
    <ProductThemeProvider>
      <PageHeader
        help="一级品类用于商品和独立站，编码有商品后不能再改；细分品类用于供应商主营产品和询盘单匹配"
        extra={
          access['product:category:add'] && (
            <Button
              type="primary"
              icon={<Plus size="1em" />}
              onClick={() => openForm(null)}
            >
              新增品类
            </Button>
          )
        }
      />
      <FilterCard
        onSearch={() =>
          setFilters({
            ...filters,
            keyword: keyword.trim(),
            industry: draftIndustry,
          })
        }
        onReset={() => {
          setKeyword('');
          setDraftIndustry(undefined);
          setFilters({});
        }}
      >
        <FilterField label="关键词" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="品类编码或名称"
            aria-label="关键词"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() =>
              setFilters({
                ...filters,
                keyword: keyword.trim(),
                industry: draftIndustry,
              })
            }
          />
        </FilterField>
        <FilterField label="状态">
          <Select
            allowClear
            placeholder="全部"
            aria-label="状态"
            value={filters.status}
            // 状态下拉切换后立即查询
            onChange={(v) => setFilters({ ...filters, status: v })}
            options={[
              { value: 1, label: '启用' },
              { value: 0, label: '停用' },
            ]}
          />
        </FilterField>
        <FilterField label="行业">
          <Select
            allowClear
            placeholder="全部"
            aria-label="行业"
            value={draftIndustry}
            onChange={setDraftIndustry}
            options={industryOptions}
          />
        </FilterField>
      </FilterCard>
      <MainCard count={total} unit="个一级品类">
        <ProTable<Category>
          rowKey="id"
          actionRef={actionRef}
          columns={columns}
          search={false}
          options={false}
          toolBarRender={false}
          params={filters}
          request={async () => {
            const tree = await categoryApi.tree(filters.industry || undefined);
            setTopLevels(tree);
            // 两级品类数量不大，一次取回整棵树在前端筛选：命中细分品类时保留它的一级品类
            const keyword = (filters.keyword ?? '').toLowerCase();
            const status = filters.status;
            const hit = (c: Category) =>
              (!keyword ||
                [c.categoryCode, c.categoryName, c.categoryNameZh].some((v) =>
                  (v ?? '').toLowerCase().includes(keyword),
                )) &&
              (status === undefined || c.status === status);
            const data: Category[] = [];
            for (const top of tree) {
              const children = (top.children ?? []).filter(hit);
              if (!hit(top) && !children.length) continue;
              // children 为空时去掉该键，避免一级品类出现空的展开按钮
              const { children: _, ...rest } = top;
              data.push(children.length ? { ...rest, children } : rest);
            }
            setTotal(data.length);
            return { data, total: data.length, success: true };
          }}
          expandable={{ defaultExpandAllRows: false }}
          scroll={{ x: tableScrollX(columns) }}
          pagination={pager}
          locale={{
            emptyText: tableEmpty(
              '还没有品类。先新增一个，新建商品时才能选择品类。',
            ),
          }}
        />
      </MainCard>
      <ModalForm<{
        categoryCode: string;
        categoryName: string;
        categoryNameZh?: string;
        parentId?: number | null;
        description?: string;
        descriptionZh?: string;
        sortOrder?: number;
        industryCode?: string;
        hsCode?: string;
      }>
        name="categoryEdit"
        title={
          editing
            ? editing.parentId
              ? '编辑细分品类'
              : '编辑品类'
            : presetParent
              ? '新增细分品类'
              : '新增品类'
        }
        open={open}
        onOpenChange={setOpen}
        width={520}
        modalProps={{ destroyOnHidden: true }}
        initialValues={
          editing
            ? { ...editing }
            : {
                sortOrder: 0,
                parentId: presetParent,
                industryCode: industryOptions[0]?.value,
              }
        }
        onFinish={async (values) => {
          const data = {
            ...values,
            parentId: values.parentId ?? null,
            hsCode: values.hsCode ?? '',
          };
          if (editing) await categoryApi.update(editing.id, data);
          else await categoryApi.create(data);
          message.success(editing ? '已保存' : '已新增');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText
          name="categoryCode"
          label="品类编码"
          placeholder="如 controllers"
          disabled={locked}
          tooltip={
            locked ? undefined : '小写字母开头，只含小写字母、数字和下划线'
          }
          extra={
            locked
              ? '这个品类下已有商品，编码已被使用，不能修改（名称和排序仍可修改）'
              : '独立站的品类页面地址会用到它，创建后有商品就不能改了'
          }
          rules={[
            { required: true, message: '请输入品类编码' },
            { max: 32, message: '编码不超过 32 个字符' },
            {
              pattern: /^[a-z][a-z0-9_]*$/,
              message: '编码须以小写字母开头，只含小写字母、数字和下划线',
            },
          ]}
        />
        <ProFormSelect
          name="parentId"
          label="上级品类"
          placeholder="不选则为一级品类"
          allowClear
          disabled={!!editing && (editing.children?.length ?? 0) > 0}
          extra="只能选一级品类；细分品类下不能再建下级。商品只能挂一级品类"
          options={topLevels
            .filter((t) => t.id !== editing?.id)
            .map((t) => ({
              value: t.id,
              label: t.categoryNameZh
                ? `${t.categoryNameZh} ${t.categoryName}`
                : t.categoryName,
            }))}
        />
        <ProFormDependency name={['parentId']}>
          {({ parentId }) =>
            parentId ? null : (
              <ProFormSelect
                name="industryCode"
                label="所属行业"
                placeholder="选择行业"
                options={industryOptions}
                rules={[{ required: true, message: '请选择行业' }]}
                extra="租户只看到本行业的品类；细分品类跟随上级，不用选"
              />
            )
          }
        </ProFormDependency>
        <ProFormText
          name="categoryNameZh"
          label="中文名称"
          placeholder="如 伺服驱动器"
          dependencies={['parentId']}
          rules={[
            ({ getFieldValue }) => ({
              required: !!getFieldValue('parentId'),
              message: '细分品类必须填写中文名称',
            }),
            { max: 32, message: '中文名称不超过 32 个字符' },
          ]}
          extra="业务界面优先显示中文名；细分品类必填"
        />
        <ProFormText
          name="categoryName"
          label="品类名称（英文）"
          placeholder="如 PLC & Controllers"
          rules={[
            { required: true, message: '请输入品类名称' },
            { max: 64, message: '名称不超过 64 个字符' },
          ]}
        />
        <ProFormText
          name="hsCode"
          label="默认海关编码"
          placeholder="如 8536509000"
          rules={[
            {
              validator: (_, v?: string) =>
                !v || /^\d{6,10}$/.test(v.replace(/[.\s]/g, ''))
                  ? Promise.resolve()
                  : Promise.reject(
                      new Error('去掉点和空格后应为 6 到 10 位数字'),
                    ),
            },
          ]}
          extra="AI 生成资料时查不到型号的编码就用它；细分品类不填时沿用上级品类的"
        />
        <ProFormTextArea
          name="descriptionZh"
          label="中文简介"
          placeholder="一两句话介绍这个品类，系统内显示"
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
          placeholder="English description for the website category page"
          fieldProps={{
            maxLength: DESCRIPTION_MAX,
            showCount: true,
            autoSize: { minRows: 2, maxRows: 5 },
          }}
          rules={[{ max: DESCRIPTION_MAX, message: '简介不能超过 500 个字符' }]}
          extra="独立站品类页等对外场景使用"
        />
        <ProFormDigit
          name="sortOrder"
          label="排序"
          min={0}
          fieldProps={{ precision: 0 }}
          extra="数字越小越靠前"
        />
      </ModalForm>
      <TariffModal
        hs6={tariffHs6}
        canEdit={!!access['product:category:edit']}
        onClose={() => setTariffHs6(undefined)}
        onSaved={() => actionRef.current?.reload()}
      />
    </ProductThemeProvider>
  );
};

export default CategoryPage;
