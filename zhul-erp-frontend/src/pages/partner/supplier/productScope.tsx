import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Form, Select } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import {
  type BrandOption,
  brandApi,
  type Category,
  categoryApi,
} from '@/pages/product/service';
import { useAppTheme } from '@/theme/AppTheme';
import type { SupplierProductScope } from './service';

/** 主营品牌最多 50 个，与后端一致 */
export const MAX_SCOPES = 50;

/** 表单里的一行主营产品：正式品牌带 brandId，待确认品牌只有名称 */
export interface ScopeRow {
  brand?: { brandId?: number; brandName: string; pending?: boolean };
  categoryIds?: number[];
}

export const toScopeRows = (scopes?: SupplierProductScope[]): ScopeRow[] =>
  (scopes ?? []).map((s) => ({
    brand: {
      brandId: s.brandId ?? undefined,
      brandName: s.brandName,
      pending: s.pending,
    },
    categoryIds: s.categories.map((c) => c.id),
  }));

export const toScopePayload = (rows?: ScopeRow[]) =>
  (rows ?? [])
    .filter((r) => r.brand)
    .map((r) => ({
      ...(r.brand?.brandId
        ? { brandId: r.brand.brandId }
        : { brandName: r.brand?.brandName }),
      categoryIds: r.categoryIds ?? [],
    }));

const norm = (s: string) => s.trim().toLowerCase();

/** 启用品牌与品类树：表单页、列表筛选共用，只在挂载时取一次 */
export function useScopeCatalog() {
  const [brands, setBrands] = useState<BrandOption[]>([]);
  const [tree, setTree] = useState<Category[]>([]);
  useEffect(() => {
    brandApi
      .options()
      .then(setBrands)
      .catch(() => undefined);
    categoryApi
      .tree()
      .then(setTree)
      .catch(() => undefined);
  }, []);
  return { brands, tree };
}

/** 细分品类下拉：按一级品类分组；停用的品类不能新选，但已选的仍显示 */
export const categoryGroups = (tree: Category[], selected: number[] = []) =>
  tree
    .map((top) => ({
      label: top.categoryNameZh || top.categoryName,
      title: top.categoryName,
      options: (top.children ?? [])
        .filter((c) => c.status === 1 || selected.includes(c.id))
        .map((c) => ({
          value: c.id,
          label: c.categoryNameZh || c.categoryName,
          disabled: c.status !== 1,
        })),
    }))
    .filter((g) => g.options.length);

export const PendingTag: React.FC = () => {
  const { palette } = useAppTheme();
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 20,
        padding: '0 8px',
        marginLeft: 6,
        borderRadius: 10,
        fontSize: 12,
        fontWeight: 600,
        color: palette.orange,
        background: palette.orangeSoft,
        whiteSpace: 'nowrap',
      }}
    >
      待确认
    </span>
  );
};

const NEW_PREFIX = 'new:';

/** 品牌选择：可搜索名称或别名；清单里没有时可直接用输入的名称作为待确认品牌 */
const BrandPicker: React.FC<{
  value?: ScopeRow['brand'];
  onChange?: (v: ScopeRow['brand']) => void;
  brands: BrandOption[];
}> = ({ value, onChange, brands }) => {
  const [search, setSearch] = useState('');
  const typed = search.trim();
  const exact =
    typed &&
    brands.some(
      (b) =>
        norm(b.brandName) === norm(typed) ||
        b.aliases?.some((a) => norm(a) === norm(typed)),
    );
  const options = [
    ...brands.map((b) => ({
      value: String(b.id),
      label: b.brandName,
      aliases: b.aliases ?? [],
    })),
    ...(typed && !exact
      ? [
          {
            value: `${NEW_PREFIX}${typed}`,
            label: `使用「${typed}」作为待确认品牌`,
            aliases: [] as string[],
          },
        ]
      : []),
    // 已保存的待确认品牌不在清单里，补一个选项让它能正常显示
    ...(value && !value.brandId
      ? [
          {
            value: `${NEW_PREFIX}${value.brandName}`,
            label: value.brandName,
            aliases: [] as string[],
          },
        ]
      : []),
  ];
  const current = value
    ? value.brandId
      ? String(value.brandId)
      : `${NEW_PREFIX}${value.brandName}`
    : undefined;

  return (
    <Select
      value={current}
      placeholder="搜索品牌名称或别名"
      options={options}
      showSearch={{
        searchValue: search,
        onSearch: setSearch,
        filterOption: (input, option) =>
          !!option &&
          (String(option.value).startsWith(NEW_PREFIX) ||
            norm(option.label).includes(norm(input)) ||
            option.aliases.some((a) => norm(a).includes(norm(input)))),
      }}
      labelRender={(p) =>
        value && !value.brandId ? (
          <span>
            {value.brandName}
            <PendingTag />
          </span>
        ) : (
          p.label
        )
      }
      notFoundContent="输入品牌名称"
      onChange={(v: string) => {
        setSearch('');
        if (v.startsWith(NEW_PREFIX)) {
          onChange?.({ brandName: v.slice(NEW_PREFIX.length), pending: true });
        } else {
          const b = brands.find((x) => String(x.id) === v);
          if (b) onChange?.({ brandId: b.id, brandName: b.brandName });
        }
      }}
    />
  );
};

const brandKey = (b?: ScopeRow['brand']) =>
  b ? (b.brandId ? `id:${b.brandId}` : `name:${norm(b.brandName)}`) : '';

/** 供应商表单「主营产品」卡片内容，字段名 productScopes */
export const ProductScopeEditor: React.FC = () => {
  const { palette } = useAppTheme();
  const { brands, tree } = useScopeCatalog();
  const form = Form.useFormInstance();
  const rows: ScopeRow[] = Form.useWatch('productScopes', form) ?? [];
  const catalog = useMemo(() => ({ brands, tree }), [brands, tree]);

  return (
    <Form.List name="productScopes">
      {(fields, { add, remove }) => (
        <>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              gap: 12,
              margin: '-8px 0 16px',
              fontSize: 13,
              color: palette.mute,
            }}
          >
            <span>
              按品牌录入：每个品牌可选若干细分品类，不选表示该品牌全部品类。询盘单会按「品牌
              + 品类」自动推荐主营相符的供应商。
            </span>
            <span className="num" style={{ whiteSpace: 'nowrap' }}>
              {fields.length} / {MAX_SCOPES}
            </span>
          </div>
          {fields.length === 0 ? (
            <div
              style={{
                textAlign: 'center',
                padding: '28px 16px',
                borderRadius: 12,
                border: `1px dashed ${palette.hairline}`,
                color: palette.mute,
              }}
            >
              <div style={{ color: palette.ink, fontWeight: 600 }}>
                还没有主营产品
              </div>
              <div style={{ margin: '6px 0 14px', fontSize: 13 }}>
                填上主营品牌后，询盘单会自动推荐这个供应商。
              </div>
              <Button icon={<PlusOutlined />} onClick={() => add({})}>
                添加品牌
              </Button>
            </div>
          ) : (
            <>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'minmax(180px, 260px) 1fr 32px',
                  gap: 12,
                  fontSize: 13,
                  color: palette.sub,
                  marginBottom: 8,
                }}
              >
                <span>品牌</span>
                <span>细分品类</span>
              </div>
              {fields.map((field) => (
                <div
                  key={field.key}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'minmax(180px, 260px) 1fr 32px',
                    gap: 12,
                    alignItems: 'start',
                  }}
                >
                  <Form.Item
                    name={[field.name, 'brand']}
                    rules={[
                      { required: true, message: '请选择品牌' },
                      {
                        validator: (_, v: ScopeRow['brand']) =>
                          v &&
                          rows.filter((r) => brandKey(r?.brand) === brandKey(v))
                            .length > 1
                            ? Promise.reject(
                                new Error(`「${v.brandName}」已添加过`),
                              )
                            : Promise.resolve(),
                      },
                    ]}
                  >
                    <BrandPicker brands={catalog.brands} />
                  </Form.Item>
                  <Form.Item name={[field.name, 'categoryIds']}>
                    <Select
                      mode="multiple"
                      allowClear
                      placeholder="全部品类（不选即表示该品牌全部品类）"
                      options={categoryGroups(
                        catalog.tree,
                        rows[field.name]?.categoryIds,
                      )}
                      showSearch={{ optionFilterProp: 'label' }}
                      maxTagCount="responsive"
                    />
                  </Form.Item>
                  <Button
                    aria-label="删除这一行"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => remove(field.name)}
                  />
                </div>
              ))}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  fontSize: 13,
                  color: palette.mute,
                }}
              >
                <Button
                  icon={<PlusOutlined />}
                  disabled={fields.length >= MAX_SCOPES}
                  onClick={() => add({})}
                >
                  添加品牌
                </Button>
                品牌不在清单里时可以直接输入，保存为「待确认」，由平台统一确认
              </div>
            </>
          )}
        </>
      )}
    </Form.List>
  );
};

/** 详情页：每个品牌一行「Siemens：PLC、HMI」 */
export const ScopeLines: React.FC<{ scopes?: SupplierProductScope[] }> = ({
  scopes,
}) => {
  const { palette } = useAppTheme();
  if (!scopes?.length) {
    return <span style={{ color: palette.mute }}>未填写主营产品</span>;
  }
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      {scopes.map((s) => (
        <div
          key={`${s.brandId ?? ''}${s.brandName}`}
          style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}
        >
          <span
            style={{
              minWidth: 140,
              fontWeight: 600,
              color: s.pending ? palette.sub : palette.ink,
            }}
          >
            {s.brandName}
            {s.pending && <PendingTag />}
          </span>
          <span
            style={{ color: s.categories.length ? palette.ink : palette.mute }}
          >
            {s.categories.length
              ? s.categories.map((c) => c.name).join('、')
              : '全部品类'}
          </span>
        </div>
      ))}
    </div>
  );
};

/** 列表列：最多 3 个品牌标签，其余显示「等 N 个」 */
export const ScopeBrandTags: React.FC<{ scopes?: SupplierProductScope[] }> = ({
  scopes,
}) => {
  const { palette } = useAppTheme();
  if (!scopes?.length) return <span style={{ color: palette.mute }}>—</span>;
  const shown = scopes.slice(0, 3);
  return (
    <span
      style={{ display: 'inline-flex', flexWrap: 'wrap', gap: 4 }}
      title={scopes.map((s) => s.brandName).join('、')}
    >
      {shown.map((s) =>
        s.pending ? (
          <span
            key={s.brandName}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              fontSize: 12,
            }}
          >
            {s.brandName}
            <PendingTag />
          </span>
        ) : (
          <span
            key={s.brandName}
            style={{
              height: 22,
              lineHeight: '20px',
              padding: '0 8px',
              borderRadius: 6,
              fontSize: 12,
              color: palette.ink,
              background: palette.inset,
              border: `1px solid ${palette.hairline}`,
            }}
          >
            {s.brandName}
          </span>
        ),
      )}
      {scopes.length > 3 && (
        <span
          style={{ fontSize: 12, color: palette.mute, alignSelf: 'center' }}
        >
          等 {scopes.length - 3} 个
        </span>
      )}
    </span>
  );
};
