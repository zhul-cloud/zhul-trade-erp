import { App, AutoComplete, Checkbox, Input, Modal, Table } from 'antd';
import { Info, Search } from 'lucide-react';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { MONO } from './components';
import { type CategoryMapRow, readBizError, siteApi } from './service';

/** 表格里的一行：一级、二级展平，二级记住所属一级 */
interface Row {
  key: number;
  categoryId: number;
  name: string;
  productCount: number;
  /** 直接挂在这个品类上的商品数（一级不含下级）：没有对照时这些商品不能发布 */
  ownCount: number;
  parentId?: number;
}

const CODE = /^[a-z0-9][a-z0-9_-]{0,63}$/;

/** 品类对照：ERP 品类 → 站点品类；二级没有单独设置时沿用一级 */
export const CategoryMapModal: React.FC<{
  siteId: number;
  siteName: string;
  open: boolean;
  readOnly?: boolean;
  onClose: (saved?: CategoryMapRow[]) => void;
}> = ({ siteId, siteName, open, readOnly, onClose }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [tree, setTree] = useState<CategoryMapRow[]>([]);
  const [values, setValues] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [onlyMissing, setOnlyMissing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const t = await siteApi.categoryMap(siteId);
      setTree(t);
      const v: Record<number, string> = {};
      for (const r of t) {
        v[r.categoryId] = r.siteCategory;
        for (const c of r.children) v[c.categoryId] = c.siteCategory;
      }
      setValues(v);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [siteId]);
  useEffect(() => {
    if (open) {
      setKeyword('');
      setOnlyMissing(false);
      load();
    }
  }, [open, load]);

  /** 已用过的站点品类，作为下拉选项；新代码直接输入 */
  const codes = useMemo(
    () =>
      [...new Set(Object.values(values).filter(Boolean))]
        .sort()
        .map((c) => ({ value: c })),
    [values],
  );

  const effective = useCallback(
    (r: Row) =>
      (values[r.categoryId] ?? '').trim() ||
      (r.parentId ? (values[r.parentId] ?? '').trim() : ''),
    [values],
  );

  const rows = useMemo(() => {
    const k = keyword.trim().toLowerCase();
    const out: Row[] = [];
    for (const p of tree) {
      const subs: Row[] = p.children.map((c) => ({
        key: c.categoryId,
        categoryId: c.categoryId,
        name: c.name,
        productCount: c.productCount,
        ownCount: c.productCount,
        parentId: p.categoryId,
      }));
      const top: Row = {
        key: p.categoryId,
        categoryId: p.categoryId,
        name: p.name,
        productCount: p.productCount,
        ownCount:
          p.productCount - p.children.reduce((n, c) => n + c.productCount, 0),
      };
      const hit = (r: Row) =>
        (!k || r.name.toLowerCase().includes(k)) &&
        (!onlyMissing || !effective(r));
      const shownSubs = subs.filter(hit);
      if (hit(top) || shownSubs.length) out.push(top, ...shownSubs);
    }
    return out;
  }, [tree, keyword, onlyMissing, effective]);

  const save = async () => {
    const items = Object.entries(values).map(([id, code]) => ({
      categoryId: Number(id),
      siteCategory: code.trim().toLowerCase(),
    }));
    const bad = items.find((i) => i.siteCategory && !CODE.test(i.siteCategory));
    if (bad) {
      message.error(
        `站点品类「${bad.siteCategory}」不正确：只能用小写字母、数字、- 和 _`,
      );
      return;
    }
    setSaving(true);
    try {
      const saved = await siteApi.saveCategoryMap(siteId, items);
      message.success('品类对照已保存');
      onClose(saved);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={
        <div>
          <div>品类对照 · {siteName}</div>
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            ERP 品类 → 站点品类
          </div>
        </div>
      }
      width={860}
      onCancel={() => onClose()}
      okText="保存"
      onOk={save}
      confirmLoading={saving}
      okButtonProps={{ disabled: readOnly || loading || !!error }}
      destroyOnHidden
    >
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <>
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 10,
              background: palette.inset,
              color: palette.mute,
              fontSize: 12,
              display: 'grid',
              gap: 4,
              marginBottom: 14,
            }}
          >
            <span>
              <Info size="1em" />{' '}
              按二级品类设置；二级没有单独设置时沿用一级品类的对照
            </span>
            <span>
              <Info size="1em" />{' '}
              站点品类是站点自己的品类代码，可以在下拉里新增；修改后，受影响的已发布商品标「内容有更新」
            </span>
          </div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              marginBottom: 12,
            }}
          >
            <Input
              allowClear
              prefix={<Search size="1em" />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="搜索品类"
              style={{ width: 240 }}
              aria-label="搜索品类"
            />
            <span style={{ flex: 1 }} />
            <Checkbox
              checked={onlyMissing}
              onChange={(e) => setOnlyMissing(e.target.checked)}
            >
              只看没有对照的
            </Checkbox>
          </div>
          <Table<Row>
            rowKey="key"
            size="small"
            loading={loading}
            dataSource={rows}
            pagination={false}
            scroll={{ y: 460 }}
            locale={{
              emptyText: onlyMissing ? '所有品类都有对照' : '没有品类',
            }}
            columns={[
              {
                title: 'ERP 品类',
                dataIndex: 'name',
                render: (v: string, r) =>
                  r.parentId ? (
                    <span style={{ paddingLeft: 18, color: palette.sub }}>
                      └ {v}
                    </span>
                  ) : (
                    <b>{v}</b>
                  ),
              },
              {
                title: '站点品类',
                key: 'code',
                width: 250,
                render: (_, r) => {
                  const own = values[r.categoryId] ?? '';
                  const inherited = r.parentId
                    ? (values[r.parentId] ?? '').trim()
                    : '';
                  const missing = !effective(r) && r.ownCount > 0;
                  return (
                    <AutoComplete
                      value={own}
                      options={codes}
                      disabled={readOnly}
                      status={missing ? 'error' : undefined}
                      placeholder={
                        inherited ? `沿用 ${inherited}` : '选择或新增站点品类'
                      }
                      onChange={(v: string) =>
                        setValues((m) => ({ ...m, [r.categoryId]: v }))
                      }
                      style={{ width: 220, fontFamily: MONO }}
                      aria-label={`${r.name}的站点品类`}
                    />
                  );
                },
              },
              {
                title: '商品数',
                align: 'right' as const,
                dataIndex: 'productCount',
                width: 80,
                render: (v: number) => (
                  <span style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {v}
                  </span>
                ),
              },
              {
                title: '说明',
                key: 'note',
                width: 230,
                render: (_, r) => {
                  if (!effective(r) && r.ownCount > 0)
                    return (
                      <span style={{ color: palette.orange, fontSize: 12 }}>
                        还没有对照，这 {r.ownCount} 个商品不能发布
                      </span>
                    );
                  if (r.parentId && (values[r.categoryId] ?? '').trim())
                    return (
                      <span style={{ color: palette.mute, fontSize: 12 }}>
                        单独设置
                      </span>
                    );
                  return null;
                },
              },
            ]}
          />
        </>
      )}
    </Modal>
  );
};
