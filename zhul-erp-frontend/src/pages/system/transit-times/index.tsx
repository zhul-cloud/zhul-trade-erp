import {
  ClockCircleOutlined,
  EnvironmentOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  AutoComplete,
  Button,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { CARRIERS } from '@/pages/purchase/shipments/ShipmentDrawer';
import {
  readBizError,
  type TransitTime,
  transitApi,
} from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';

type Editing = Partial<TransitTime> & { open: true };

const TransitTimes: React.FC = () => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();
  const canEdit = !!access['system:transit-time:edit'];
  const [carrier, setCarrier] = useState<string>();
  const [province, setProvince] = useState('');
  const [query, setQuery] = useState<{ carrier?: string; province?: string }>(
    {},
  );
  const [rows, setRows] = useState<TransitTime[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [defaultDays, setDefaultDays] = useState<number | null>(null);
  const [savedDays, setSavedDays] = useState<number>();
  const [editing, setEditing] = useState<Editing>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await transitApi.list(query));
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query]);
  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    transitApi
      .defaultDays()
      .then((d) => {
        setDefaultDays(d);
        setSavedDays(d);
      })
      .catch(() => setSavedDays(undefined));
  }, []);

  const carriers = [...new Set(rows.map((r) => r.carrier))];

  const columns: TableColumnsType<TransitTime> = [
    {
      title: '快递公司',
      dataIndex: 'carrier',
      width: 140,
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    {
      title: '发货省份',
      dataIndex: 'originProvince',
      width: 140,
      render: (v: string) =>
        v || <span style={{ color: palette.mute }}>默认</span>,
    },
    {
      title: '运输天数',
      dataIndex: 'days',
      width: 110,
      render: (v: number) => <b style={{ color: palette.ink }}>{v} 天</b>,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      width: 240,
      render: (v?: string) =>
        v || <span style={{ color: palette.mute }}>—</span>,
    },
    ...auditColumns<TransitTime>(),
    {
      title: '操作',
      key: 'actions',
      width: 110,
      fixed: 'right',
      render: (_, r) =>
        canEdit ? (
          <Space size={12}>
            <a onClick={() => setEditing({ ...r, open: true })}>编辑</a>
            <a
              style={{ color: palette.red }}
              onClick={() =>
                modal.confirm({
                  title: `删除「${r.carrier} · ${r.originProvince || '默认'}」？`,
                  content: '删除后按快递公司默认或默认运输天数估算。',
                  okText: '删除',
                  okButtonProps: { danger: true },
                  onOk: async () => {
                    try {
                      await transitApi.remove(r.id);
                      message.success('已删除');
                      load();
                    } catch (e) {
                      message.error(readBizError(e).message);
                    }
                  },
                })
              }
            >
              删除
            </a>
          </Space>
        ) : null,
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['快递时效']}
        title="快递时效"
        description="货从供应商所在省份发到福州仓库要几天；登记发货时按这里估算预计到货日期。"
        actions={
          canEdit && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setEditing({ open: true, days: 3 })}
            >
              新增规则
            </Button>
          )
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
          gap: 16,
          marginBottom: 16,
        }}
      >
        <Card style={{ padding: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <ClockCircleOutlined style={{ color: palette.link }} />
            <b style={{ color: palette.ink }}>默认运输天数</b>
            <span style={{ flex: 1 }} />
            <InputNumber
              min={1}
              max={30}
              precision={0}
              value={defaultDays}
              disabled={!canEdit}
              onChange={setDefaultDays}
              suffix="天"
              style={{ width: 110 }}
              aria-label="默认运输天数"
            />
            {canEdit && (
              <Button
                disabled={!defaultDays || defaultDays === savedDays}
                onClick={async () => {
                  if (!defaultDays) return;
                  try {
                    setSavedDays(await transitApi.setDefaultDays(defaultDays));
                    message.success('已保存');
                  } catch (e) {
                    message.error(readBizError(e).message);
                  }
                }}
              >
                保存
              </Button>
            )}
          </div>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
            快递公司没有任何规则、或发货单没填快递公司时使用
          </div>
        </Card>
        <Card style={{ padding: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <EnvironmentOutlined style={{ color: palette.link }} />
            <b style={{ color: palette.ink }}>估算顺序</b>
          </div>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
            ① 快递公司 + 发货省份 → ② 快递公司默认 → ③
            默认运输天数。发货省份取供应商资料的「地区」；线上店铺没有地址，按快递公司默认。
          </div>
        </Card>
      </div>
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Select
          allowClear
          placeholder="全部快递公司"
          value={carrier}
          onChange={setCarrier}
          options={carriers.map((c) => ({ value: c, label: c }))}
          style={{ width: 160 }}
          aria-label="快递公司"
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={province}
          onChange={(e) => setProvince(e.target.value)}
          onPressEnter={() =>
            setQuery({ carrier, province: province.trim() || undefined })
          }
          placeholder="发货省份"
          style={{ width: 200 }}
          aria-label="发货省份"
        />
        <span style={{ flex: 1 }} />
        <Button
          type="primary"
          onClick={() =>
            setQuery({ carrier, province: province.trim() || undefined })
          }
        >
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<TransitTime>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1300 }}
          pagination={{ pageSize: 50, showTotal: (t) => `共 ${t} 条` }}
          locale={{ emptyText: '没有规则；估算时按默认运输天数' }}
        />
      )}
      <RuleModal
        value={editing}
        onClose={() => setEditing(undefined)}
        onSaved={() => {
          setEditing(undefined);
          load();
        }}
      />
    </div>
  );
};

const RuleModal: React.FC<{
  value?: Editing;
  onClose: () => void;
  onSaved: () => void;
}> = ({ value, onClose, onSaved }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [carrier, setCarrier] = useState('');
  const [province, setProvince] = useState('');
  const [days, setDays] = useState<number | null>(3);
  const [remark, setRemark] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!value) return;
    setCarrier(value.carrier ?? '');
    setProvince(value.originProvince ?? '');
    setDays(value.days ?? 3);
    setRemark(value.remark ?? '');
  }, [value]);
  if (!value) return null;
  const label = (text: string, required?: boolean) => (
    <div style={{ color: palette.sub, marginBottom: 6 }}>
      {text}
      {required && <span style={{ color: palette.red }}> *</span>}
    </div>
  );
  return (
    <Modal
      open
      title={value.id ? '编辑快递时效' : '新增快递时效'}
      okText="保存"
      okButtonProps={{ disabled: !carrier.trim() || !days, loading: busy }}
      onCancel={onClose}
      onOk={async () => {
        if (!days) return;
        setBusy(true);
        const body = {
          carrier: carrier.trim(),
          originProvince: province.trim(),
          days,
          remark: remark.trim(),
        };
        try {
          if (value.id) await transitApi.update(value.id, body);
          else await transitApi.create(body);
          message.success('已保存');
          onSaved();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: 12,
          marginTop: 8,
        }}
      >
        <div>
          {label('快递公司', true)}
          <AutoComplete
            value={carrier}
            options={CARRIERS}
            onChange={setCarrier}
            style={{ width: '100%' }}
            placeholder="如 顺丰"
            aria-label="快递公司"
          />
        </div>
        <div>
          {label('发货省份')}
          <Input
            value={province}
            maxLength={16}
            onChange={(e) => setProvince(e.target.value)}
            placeholder="留空表示这家快递的默认天数"
            aria-label="发货省份"
          />
        </div>
        <div>
          {label('运输天数', true)}
          <InputNumber
            min={1}
            max={30}
            precision={0}
            value={days}
            onChange={setDays}
            suffix="天"
            style={{ width: '100%' }}
            aria-label="运输天数"
          />
        </div>
        <div>
          {label('备注')}
          <Input
            value={remark}
            maxLength={100}
            onChange={(e) => setRemark(e.target.value)}
            placeholder="如 偏远、省内次日达"
            aria-label="备注"
          />
        </div>
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 12 }}>
        省份写简称或全称都可以（「广东省」会存成「广东」）；同一快递公司同一省份只能有一条。
      </div>
    </Modal>
  );
};

export default TransitTimes;
