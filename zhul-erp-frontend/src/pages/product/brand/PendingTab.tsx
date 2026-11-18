import { BulbOutlined, SearchOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Input,
  Modal,
  Segmented,
  Select,
  Space,
  Table,
} from 'antd';
import React, { useMemo, useState } from 'react';
import { formatDateTime } from '@/utils/format';
import { Pill } from '../components/Pills';
import {
  type BrandOption,
  brandApi,
  type PendingBrand,
  pendingBrandApi,
  readBizError,
} from '../service';
import { useProductTheme } from '../theme';

type Source = 'all' | 'docs' | 'supplier';

const docRows = (p: PendingBrand) =>
  p.inquiryCount + p.orderCount + p.purchaseCount;

/** 出现在：只列有数量的来源 */
const Where: React.FC<{ row: PendingBrand }> = ({ row }) => {
  const parts: [string, number][] = [
    ['询盘', row.inquiryCount],
    ['订单', row.orderCount],
    ['采购单', row.purchaseCount],
    ['供应商', row.supplierCount],
  ];
  return (
    <Space size={6} wrap>
      {parts
        .filter(([, n]) => n > 0)
        .map(([label, n]) => (
          <Pill key={label} tone="gray">
            {label} <b className="num">{n}</b>
          </Pill>
        ))}
    </Space>
  );
};

/** 合计影响：确认弹窗里列出会改的单据 */
const Impact: React.FC<{ rows: PendingBrand[]; keep?: boolean }> = ({
  rows,
  keep,
}) => {
  const { palette } = useProductTheme();
  const sum = (f: (p: PendingBrand) => number) =>
    rows.reduce((s, p) => s + f(p), 0);
  const lines: [string, string][] = [
    ['询盘型号', `${sum((p) => p.inquiryCount)} 行`],
    ['销售订单', `${sum((p) => p.orderCount)} 行`],
    ['采购订单', `${sum((p) => p.purchaseCount)} 行`],
    ['供应商主营产品', `${sum((p) => p.supplierCount)} 个供应商`],
  ];
  return (
    <div
      style={{
        background: palette.inset,
        borderRadius: 12,
        padding: '12px 14px',
        display: 'grid',
        gap: 6,
      }}
    >
      <div style={{ color: palette.sub, fontSize: 12 }}>
        {keep ? '涉及的单据（保持原样）' : '确认后会改的单据'}
      </div>
      {lines
        .filter(([, v]) => !v.startsWith('0 '))
        .map(([label, v]) => (
          <div key={label} style={{ display: 'flex', gap: 8 }}>
            <span style={{ flex: 1 }}>{label}</span>
            <b className="num">{v}</b>
          </div>
        ))}
      {!keep && (
        <div style={{ color: palette.sub, fontSize: 12 }}>
          采购需求、报价单与 PI 草稿同步改；已发给客户的报价单、PI 不改
        </div>
      )}
    </div>
  );
};

const PendingTab: React.FC<{
  pending: PendingBrand[];
  loading: boolean;
  reload: () => void;
  canAdd: boolean;
  onCreate: (row: PendingBrand) => void;
  onLinked: () => void;
}> = ({ pending, loading, reload, canAdd, onCreate, onLinked }) => {
  const { message, modal } = App.useApp();
  const { palette } = useProductTheme();
  const [source, setSource] = useState<Source>('all');
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const [linking, setLinking] = useState<PendingBrand[] | null>(null);
  const [linkTarget, setLinkTarget] = useState<number>();
  const [brandOptions, setBrandOptions] = useState<BrandOption[]>([]);
  const [saving, setSaving] = useState(false);

  const rows = useMemo(() => {
    const k = keyword.trim().toLowerCase();
    return pending.filter(
      (p) =>
        (source === 'all' ||
          (source === 'docs' ? docRows(p) > 0 : p.supplierCount > 0)) &&
        (!k || p.name.toLowerCase().includes(k)),
    );
  }, [pending, source, keyword]);

  const picked = (keys: string[]) =>
    pending.filter((p) => keys.includes(p.pendingKey));

  const done = () => {
    setSelected([]);
    reload();
  };

  const openLink = async (list: PendingBrand[]) => {
    const ids = new Set(list.map((p) => p.suggestBrandId));
    const only = ids.size === 1 ? list[0].suggestBrandId : undefined;
    setLinking(list);
    setLinkTarget(only ?? undefined);
    if (!brandOptions.length) setBrandOptions(await brandApi.options());
  };

  const confirmLink = async () => {
    if (!linking || !linkTarget) return;
    const brand = brandOptions.find((b) => b.id === linkTarget)?.brandName;
    setSaving(true);
    try {
      await pendingBrandApi.linkAsAlias(
        linking.map((p) => p.pendingKey),
        linkTarget,
      );
      message.success(`已设为别名，单据里的写法统一为 ${brand ?? '该品牌'}`);
      setLinking(null);
      done();
      onLinked();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const ignore = (list: PendingBrand[]) => {
    modal.confirm({
      title:
        list.length === 1
          ? `把「${list[0].name}」标为不是品牌？`
          : `把 ${list.length} 个写法标为不是品牌？`,
      width: 480,
      content: (
        <Space orientation="vertical" size={12} style={{ width: '100%' }}>
          <span>
            以后不再出现在待确认列表里。单据里的品牌保持原样，需要时由业务员在询盘里改。
          </span>
          <Impact rows={list} keep />
        </Space>
      ),
      okText: '标为不是品牌',
      cancelText: '取消',
      onOk: async () => {
        try {
          await pendingBrandApi.ignore(list.map((p) => p.pendingKey));
          message.success('已标为不是品牌');
          done();
        } catch (e) {
          message.error(readBizError(e).message);
          throw e;
        }
      },
    });
  };

  const suggestion = (row: PendingBrand) => {
    if (!row.suggestKind) {
      return <span style={{ color: palette.sub }}>—</span>;
    }
    const warn = row.suggestKind === 'notBrand';
    return (
      <button
        type="button"
        className="zhul-link"
        style={{
          color: warn ? palette.orange : palette.link,
          textAlign: 'left',
        }}
        onClick={() => (warn ? ignore([row]) : openLink([row]))}
      >
        <BulbOutlined style={{ marginRight: 4 }} />
        {row.suggestText}
      </button>
    );
  };

  const selectedRows = picked(selected);

  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Segmented<Source>
          value={source}
          onChange={setSource}
          options={[
            { value: 'all', label: '全部来源' },
            { value: 'docs', label: '询盘与订单' },
            { value: 'supplier', label: '供应商主营' },
          ]}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="搜索写法"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: 240 }}
        />
        <div style={{ flex: 1 }} />
        <Button
          disabled={!selected.length}
          onClick={() => openLink(selectedRows)}
        >
          批量设为别名
        </Button>
        <Button
          disabled={!selected.length}
          onClick={() => ignore(selectedRows)}
        >
          批量标为不是品牌
        </Button>
      </div>
      <Table<PendingBrand>
        rowKey="pendingKey"
        loading={loading}
        dataSource={rows}
        pagination={{ defaultPageSize: 50, hideOnSinglePage: true }}
        rowSelection={{
          selectedRowKeys: selected,
          onChange: (keys) => setSelected(keys as string[]),
        }}
        locale={{ emptyText: '没有待确认的品牌' }}
        footer={() =>
          '设为别名或新建品牌后，询盘、订单、采购单里这个写法自动换成正式品牌名'
        }
        columns={[
          {
            title: '待确认写法',
            dataIndex: 'name',
            width: 240,
            render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
          },
          {
            title: '出现在',
            key: 'where',
            render: (_: unknown, row) => <Where row={row} />,
          },
          {
            title: '建议',
            key: 'suggest',
            width: 300,
            render: (_: unknown, row) => suggestion(row),
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
            key: 'op',
            width: 240,
            render: (_: unknown, row) => (
              <Space size={12} style={{ whiteSpace: 'nowrap' }}>
                <button
                  type="button"
                  className="zhul-link"
                  onClick={() => openLink([row])}
                >
                  设为别名
                </button>
                {canAdd && (
                  <button
                    type="button"
                    className="zhul-link"
                    onClick={() => onCreate(row)}
                  >
                    新建为品牌
                  </button>
                )}
                <button
                  type="button"
                  className="zhul-link"
                  style={{ color: palette.sub }}
                  onClick={() => ignore([row])}
                >
                  不是品牌
                </button>
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={
          linking?.length === 1
            ? `把「${linking[0].name}」设为别名`
            : `把 ${linking?.length ?? 0} 个写法设为别名`
        }
        open={!!linking}
        onCancel={() => setLinking(null)}
        onOk={confirmLink}
        okText="设为别名"
        cancelText="取消"
        okButtonProps={{ disabled: !linkTarget, loading: saving }}
        width={520}
        destroyOnHidden
      >
        <Space orientation="vertical" size={16} style={{ width: '100%' }}>
          <div>
            <div style={{ marginBottom: 8 }}>属于哪个品牌</div>
            <Select
              showSearch={{ optionFilterProp: 'label' }}
              placeholder="搜索品牌名称或别名"
              value={linkTarget}
              onChange={setLinkTarget}
              style={{ width: '100%' }}
              options={brandOptions.map((b) => ({
                value: b.id,
                label: b.aliases?.length
                  ? `${b.brandName}（${b.aliases.join('、')}）`
                  : b.brandName,
              }))}
            />
          </div>
          {linking && <Impact rows={linking} />}
        </Space>
      </Modal>
    </>
  );
};

export default PendingTab;
