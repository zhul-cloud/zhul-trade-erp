import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Radio,
  Segmented,
  Select,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pill } from '@/pages/customer/components';
import { labelOf } from '@/pages/customer/constants';
import { useProfileDicts } from '@/pages/customer/profile/ProfileModules';
import {
  type CountryProfile,
  countryProfileApi,
  FX_OPTIONS,
} from '@/pages/customer/profile/service';
import { readBizError } from '@/pages/customer/service';
import { PageTitle } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useCountries } from '@/pages/product/components/useCountries';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';

type Filter = 'all' | 'unmaintained' | 'strict';
type FormState = Omit<CountryProfile, 'id' | 'customerCount'>;

const FX_TONE: Record<number, 'green' | 'violet' | 'orange'> = {
  1: 'green',
  2: 'violet',
  3: 'orange',
};

/** 编辑国家资料（原型 C7b） */
const EditModal: React.FC<{
  editing?: CountryProfile | 'new';
  maintained: Set<string>;
  onClose: () => void;
  onDone: () => void;
}> = ({ editing, maintained, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { options: countryOptions, zhOf } = useCountries();
  const d = useProfileDicts();
  const [form] = Form.useForm<FormState>();
  const [saving, setSaving] = useState(false);
  const isNew = editing === 'new';
  const row = isNew ? undefined : editing;

  useEffect(() => {
    if (!editing) return;
    form.resetFields();
    form.setFieldsValue({
      country: row?.country,
      voltages: row?.voltages ?? [],
      certifications: row?.certifications ?? [],
      plug: row?.plug ?? '',
      fxControl: row?.fxControl || undefined,
      fxNote: row?.fxNote ?? '',
      holidays: row?.holidays ?? '',
      tariffNote: row?.tariffNote ?? '',
    } as FormState);
  }, [editing, row, form]);

  const submit = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      await countryProfileApi.save({
        ...v,
        country: row?.country ?? v.country,
        plug: v.plug ?? '',
        fxControl: v.fxControl ?? 0,
        fxNote: v.fxNote ?? '',
        holidays: v.holidays ?? '',
        tariffNote: v.tariffNote ?? '',
        voltages: v.voltages ?? [],
        certifications: v.certifications ?? [],
      });
      message.success('国家资料已保存');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={!!editing}
      title={
        <div>
          {row ? `编辑国家资料 · ${zhOf(row.country)}` : '新增国家资料'}
          {row && (
            <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
              {row.customerCount ?? 0} 个客户在用这份资料
            </div>
          )}
        </div>
      }
      onCancel={onClose}
      onOk={submit}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      destroyOnHidden
      width={720}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
        {isNew && (
          <Form.Item
            name="country"
            label="国家"
            rules={[{ required: true, message: '请选择国家' }]}
          >
            <Select
              showSearch={{ optionFilterProp: 'label' }}
              placeholder="请选择国家"
              options={countryOptions.filter((o) => !maintained.has(o.value))}
            />
          </Form.Item>
        )}
        <Form.Item name="voltages" label="工业电压与频率" extra="可多选">
          <Checkbox.Group options={d.voltage.options} />
        </Form.Item>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            columnGap: 16,
          }}
        >
          <Form.Item name="plug" label="插头类型" rules={[{ max: 50 }]}>
            <Input placeholder="例如 D / G 型" />
          </Form.Item>
          <Form.Item
            name="fxControl"
            label="外汇与支付管制"
            extra="选「严格」时，这个国家的客户详情顶部会提示"
          >
            <Radio.Group optionType="button" options={FX_OPTIONS} />
          </Form.Item>
        </div>
        <Form.Item name="fxNote" label="管制说明" rules={[{ max: 300 }]}>
          <Input.TextArea
            rows={2}
            placeholder="例如：美元购汇排队，客户常要求分批付款"
          />
        </Form.Item>
        <Form.Item name="holidays" label="重大节假日" rules={[{ max: 300 }]}>
          <Input.TextArea rows={2} placeholder="例如：12 月 25 日圣诞" />
        </Form.Item>
        <Form.Item
          name="tariffNote"
          label="进口关税备注"
          rules={[{ max: 300 }]}
        >
          <Input.TextArea
            rows={2}
            placeholder="例如：PLC 类（HS 8537）关税约 5%"
          />
        </Form.Item>
        <Form.Item
          name="certifications"
          label="常见认证要求"
          style={{ marginBottom: 0 }}
        >
          <Checkbox.Group options={d.cert.options} />
        </Form.Item>
      </Form>
    </Modal>
  );
};

/** 业务设置 → 国家资料（spec master-data/country-profile）：未维护的国家按客户数排在前面 */
const CountryProfilesPage: React.FC = () => {
  const { palette } = useAppTheme();
  const pager = usePagination(50, 'country-profiles');
  const { zhOf } = useCountries();
  const d = useProfileDicts();
  const [search, setSearch] = useSearchParams();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const [rows, setRows] = useState<CountryProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [editing, setEditing] = useState<CountryProfile | 'new'>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await countryProfileApi.list(query || undefined));
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    load();
  }, [load]);

  // 从客户详情「去维护」跳过来时直接打开这个国家
  const target = search.get('country');
  useEffect(() => {
    if (!target || loading) return;
    const row = rows.find((r) => r.country === target);
    setEditing(row ?? ({ country: target } as CountryProfile));
    setSearch({}, { replace: true });
  }, [target, loading, rows, setSearch]);

  const unmaintained = rows.filter((r) => !r.id);
  const strict = rows.filter((r) => r.fxControl === 3);
  const shown =
    filter === 'unmaintained'
      ? unmaintained
      : filter === 'strict'
        ? strict
        : rows;
  const maintained = useMemo(
    () => new Set(rows.filter((r) => r.id).map((r) => r.country)),
    [rows],
  );
  const dash = <span style={{ color: palette.faint }}>—</span>;

  const columns: TableColumnsType<CountryProfile> = [
    {
      title: '国家',
      dataIndex: 'country',
      width: 180,
      render: (v: string) => (
        <span style={{ fontWeight: 600, color: palette.ink }}>
          {zhOf(v)}
          {zhOf(v) !== v && (
            <span
              style={{
                marginLeft: 6,
                fontSize: 12,
                color: palette.mute,
                fontWeight: 400,
              }}
            >
              {v}
            </span>
          )}
        </span>
      ),
    },
    {
      title: '客户数',
      dataIndex: 'customerCount',
      width: 80,
      align: 'right',
      render: (v?: number) => <span className="num">{v ?? 0}</span>,
    },
    {
      title: '工业电压',
      dataIndex: 'voltages',
      width: 180,
      render: (v: number[], r) =>
        r.id ? (
          v.map((x) => d.voltage.labelOf(x)).join(' · ') || dash
        ) : (
          <Pill tone="orange">未维护</Pill>
        ),
    },
    {
      title: '插头',
      dataIndex: 'plug',
      width: 100,
      render: (v?: string) => v || dash,
    },
    {
      title: '外汇管制',
      dataIndex: 'fxControl',
      width: 100,
      render: (v: number) =>
        v ? <Pill tone={FX_TONE[v]}>{labelOf(FX_OPTIONS, v)}</Pill> : dash,
    },
    {
      title: '重大节假日',
      width: 240,
      dataIndex: 'holidays',
      ellipsis: true,
      render: (v?: string) => v || dash,
    },
    ...auditColumns<CountryProfile>().map((c) => ({
      ...c,
      render: (v: string | undefined, r: CountryProfile, i: number) =>
        r.id ? (c.render ? c.render(v, r, i) : v) : dash,
    })),
    {
      title: '操作',
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (_, r) => (
        <a onClick={() => setEditing(r)}>{r.id ? '编辑' : '去维护'}</a>
      ),
    },
  ];

  return (
    <div>
      <PageTitle
        description="按国家维护一次，所有这个国家的客户都能看到。先补客户多的国家。"
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setEditing('new')}
          >
            新增国家
          </Button>
        }
      />
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="搜索国家"
          style={{ width: 280 }}
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value);
            if (!e.target.value) setQuery('');
          }}
          onPressEnter={() => setQuery(keyword.trim())}
        />
        <Segmented
          value={filter}
          onChange={(v) => setFilter(v as Filter)}
          options={[
            { value: 'all', label: '全部' },
            { value: 'unmaintained', label: `未维护 ${unmaintained.length}` },
            { value: 'strict', label: `外汇管制严格 ${strict.length}` },
          ]}
        />
      </div>
      <div
        style={{
          background: palette.card,
          borderRadius: 16,
          border: `1px solid ${palette.hairline}`,
          overflow: 'hidden',
        }}
      >
        {error ? (
          <ErrorHint message={error} onRetry={load} />
        ) : (
          <Table<CountryProfile>
            rowKey="country"
            columns={columns}
            dataSource={shown}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            pagination={pager}
            locale={{
              emptyText:
                filter === 'all'
                  ? '还没有客户，也没有维护过国家资料'
                  : '没有符合条件的国家',
            }}
          />
        )}
      </div>
      <EditModal
        editing={editing}
        maintained={maintained}
        onClose={() => setEditing(undefined)}
        onDone={() => {
          setEditing(undefined);
          load();
        }}
      />
    </div>
  );
};

export default CountryProfilesPage;
