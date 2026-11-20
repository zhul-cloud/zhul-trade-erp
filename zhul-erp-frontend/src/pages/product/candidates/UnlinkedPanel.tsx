import { SearchOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Modal, Radio, Table, Tag } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Card } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { ProductSelect } from '@/pages/product/components/ProductSelect';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import type { ProductOption } from '../service';
import { candidateApi, readBizError, type UnlinkedGroup } from './service';

/**
 * 待关联单据：还没关联商品的单据明细按「品牌 + 型号」归并，
 * 选择商品或填写真实型号后整组关联（spec product/document-product-link）。
 * 清单按需计算、按最近出现排序，不是业务单据列表，所以不带审计列。
 */
export const UnlinkedPanel: React.FC<{ onChanged: () => void }> = ({
  onChanged,
}) => {
  const { palette } = useAppTheme();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<UnlinkedGroup[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [linking, setLinking] = useState<UnlinkedGroup>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const r = await candidateApi.unlinked({ keyword: query, page, pageSize });
      setRows(r.records);
      setTotal(r.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const columns: TableColumnsType<UnlinkedGroup> = [
    {
      title: '品牌 · 型号（单据原文）',
      key: 'model',
      width: 280,
      fixed: 'left',
      render: (_, g) => (
        <b style={{ color: palette.ink }}>
          {g.brand ? `${g.brand} · ` : ''}
          {g.model}
        </b>
      ),
    },
    {
      title: '涉及单据',
      key: 'docs',
      width: 280,
      render: (_, g) => (
        <span style={{ display: 'inline-flex', flexWrap: 'wrap', gap: 4 }}>
          {Object.entries(g.docs).map(([k, n]) => (
            <Tag key={k} variant="filled">
              {k} {n}
            </Tag>
          ))}
        </span>
      ),
    },
    {
      title: '明细条数',
      dataIndex: 'lineCount',
      width: 100,
      align: 'right',
    },
    {
      title: '最近出现',
      dataIndex: 'lastSeenAt',
      width: 170,
      render: (v?: string) => formatDateTime(v),
    },
    {
      title: '可能是',
      key: 'suggestion',
      width: 260,
      render: (_, g) =>
        g.suggestion ? (
          <span>
            {g.suggestion.brandName} · {g.suggestion.mpnDisplay}
            {g.suggestion.candidate && (
              <Tag color="orange" style={{ marginInlineStart: 6 }}>
                候选
              </Tag>
            )}
          </span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (_, g) => (
        <button
          type="button"
          className="zhul-link"
          onClick={() => setLinking(g)}
        >
          关联
        </button>
      ),
    },
  ];

  return (
    <>
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={() => {
            setQuery(keyword.trim() || undefined);
            setPage(1);
          }}
          placeholder="品牌、型号"
          style={{ width: 260 }}
          aria-label="关键词"
        />
        <span style={{ flex: 1 }} />
        <Button
          type="primary"
          onClick={() => {
            setQuery(keyword.trim() || undefined);
            setPage(1);
          }}
        >
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 0 }}>
          <Table<UnlinkedGroup>
            rowKey="key"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            locale={{ emptyText: '所有单据明细都已关联商品' }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t: number) => `共 ${t} 组`,
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </Card>
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        历史单据里自动匹配不上的型号；同一写法的明细一次关联，按最近出现排序
      </div>
      <LinkModal
        group={linking}
        onClose={() => setLinking(undefined)}
        onDone={() => {
          setLinking(undefined);
          load();
          onChanged();
        }}
      />
    </>
  );
};

/** 关联一组明细：选择已有商品，或填写真实型号（商品库没有的进候选池） */
export const LinkModal: React.FC<{
  group?: UnlinkedGroup;
  onClose: () => void;
  onDone: () => void;
}> = ({ group, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [mode, setMode] = useState<'pick' | 'fill'>('pick');
  const [productId, setProductId] = useState<number>();
  const [brand, setBrand] = useState('');
  const [model, setModel] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (group) {
      setMode('pick');
      setProductId(group.suggestion?.id);
      setBrand(group.brand);
      setModel('');
    }
  }, [group]);

  const submit = async () => {
    if (!group) return;
    setBusy(true);
    try {
      const n = await candidateApi.linkUnlinked(
        mode === 'pick'
          ? { key: group.key, productId }
          : { key: group.key, brand: brand.trim(), model: model.trim() },
      );
      message.success(`已关联 ${n} 条明细`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const initial: ProductOption | undefined = group?.suggestion;
  return (
    <Modal
      open={!!group}
      title={group ? `关联商品 · ${group.model}` : '关联商品'}
      okText="关联"
      confirmLoading={busy}
      okButtonProps={{
        disabled: mode === 'pick' ? !productId : !model.trim(),
      }}
      onCancel={onClose}
      onOk={submit}
      destroyOnHidden
    >
      {group && (
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ color: palette.sub }}>
            {Object.entries(group.docs)
              .map(([k, n]) => `${k} ${n}`)
              .join(' · ')}
            ，共 {group.lineCount} 条明细一起关联
          </div>
          <Radio.Group
            value={mode}
            onChange={(e) => setMode(e.target.value)}
            options={[
              { value: 'pick', label: '选择商品' },
              { value: 'fill', label: '填写真实型号' },
            ]}
          />
          {mode === 'pick' ? (
            <ProductSelect
              value={productId}
              initialOption={initial}
              onChange={(id) => setProductId(id)}
              style={{ width: '100%' }}
              aria-label="商品"
            />
          ) : (
            <div style={{ display: 'grid', gap: 8 }}>
              <Input
                value={brand}
                maxLength={64}
                onChange={(e) => setBrand(e.target.value)}
                placeholder="品牌"
                aria-label="品牌"
              />
              <Input
                value={model}
                maxLength={128}
                onChange={(e) => setModel(e.target.value)}
                placeholder="真实型号，如 6ES7214-1AG40-0XB0"
                aria-label="真实型号"
              />
              <span style={{ fontSize: 12, color: palette.mute }}>
                商品库有这个型号就直接关联，没有的进候选池等审核
              </span>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};
