import {
  ArrowRightOutlined,
  InfoCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { App, InputNumber, Modal, Skeleton, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { readBizError } from '@/pages/crm/opportunity/service';
import { Card, PageTitle, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  type ExchangeRate,
  type ExchangeRateLog,
  exchangeRateApi,
} from './service';

const fmt = (v?: number | null) => (v == null ? '—' : Number(v).toFixed(6));
const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

const ExchangeRatePage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['system:exchange-rate:edit'];
  const [rows, setRows] = useState<ExchangeRate[]>();
  const [error, setError] = useState<string>();
  const [logCurrency, setLogCurrency] = useState<string>();
  const [logs, setLogs] = useState<ExchangeRateLog[]>();
  const [editing, setEditing] = useState<ExchangeRate>();
  const [value, setValue] = useState<number | null>(null);
  const [inputError, setInputError] = useState<string>();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const list = await exchangeRateApi.list();
      setRows(list);
      setLogCurrency(
        (c) =>
          c ??
          list.find((r) => r.rate != null)?.currencyCode ??
          list[0]?.currencyCode,
      );
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!logCurrency) return;
    setLogs(undefined);
    exchangeRateApi
      .logs(logCurrency)
      .then(setLogs)
      .catch(() => setLogs([]));
  }, [logCurrency]);

  const openEdit = (r: ExchangeRate) => {
    setEditing(r);
    setValue(r.rate ?? null);
    setInputError(undefined);
    setConfirming(false);
  };

  const validate = () => {
    if (value == null || value <= 0) {
      setInputError('汇率需要大于 0');
      return false;
    }
    if (editing?.rate != null && Number(editing.rate) === value) {
      setInputError('汇率没有变化');
      return false;
    }
    return true;
  };

  const submit = async () => {
    if (!editing || value == null) return;
    setBusy(true);
    try {
      await exchangeRateApi.save(editing.currencyCode, value);
      message.success(`${editing.currencyCode} 汇率已更新为 ${fmt(value)}`);
      setEditing(undefined);
      setLogCurrency(editing.currencyCode);
      await load();
      setLogs(await exchangeRateApi.logs(editing.currencyCode));
    } catch (e) {
      setInputError(readBizError(e).message);
      setConfirming(false);
    } finally {
      setBusy(false);
    }
  };

  if (error) return <ErrorHint message={error} onRetry={load} />;

  return (
    <div>
      <PageTitle
        crumbs={['汇率']}
        title="汇率"
        description="全公司统一使用这里的汇率；报价单新建时取当前汇率并保存快照，单据上不能修改。"
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide
            ? 'minmax(0, 2fr) minmax(320px, 1fr)'
            : 'minmax(0, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div>
          <Card style={{ padding: 0, overflow: 'hidden' }}>
            {!rows ? (
              <Skeleton active style={{ padding: 24 }} />
            ) : (
              <Table<ExchangeRate>
                rowKey="currencyCode"
                pagination={false}
                dataSource={rows}
                onRow={(r) => ({
                  style:
                    r.currencyCode === logCurrency
                      ? { background: palette.accentSoft }
                      : undefined,
                })}
                columns={[
                  {
                    title: '币种',
                    dataIndex: 'currencyCode',
                    width: 90,
                    render: (v: string) => <b>{v}</b>,
                  },
                  {
                    title: '1 外币 = 人民币',
                    dataIndex: 'rate',
                    width: 160,
                    render: (v?: number) =>
                      v == null ? (
                        <b style={{ color: palette.orange }}>未设置</b>
                      ) : (
                        <b style={{ ...num, fontSize: 16 }}>{fmt(v)}</b>
                      ),
                  },
                  {
                    title: '来源',
                    dataIndex: 'sourceName',
                    width: 100,
                    render: (v?: string) => v ?? '—',
                  },
                  {
                    title: '更新',
                    key: 'updated',
                    render: (_, r) =>
                      r.rate == null ? (
                        <span style={{ color: palette.orange }}>
                          {r.currencyCode} 客户暂时不能新建报价单
                        </span>
                      ) : (
                        <span style={{ color: palette.sub }}>
                          {r.updatedByName ?? '—'} ·{' '}
                          {formatDateTime(r.rateTime).slice(0, 16)}
                        </span>
                      ),
                  },
                  {
                    title: '操作',
                    key: 'actions',
                    width: 150,
                    render: (_, r) => (
                      <span style={{ display: 'inline-flex', gap: 12 }}>
                        {canEdit && (
                          <a onClick={() => openEdit(r)}>
                            {r.rate == null ? '设置' : '修改'}
                          </a>
                        )}
                        {r.rate != null && (
                          <a onClick={() => setLogCurrency(r.currencyCode)}>
                            变更记录
                          </a>
                        )}
                      </span>
                    ),
                  },
                ]}
              />
            )}
          </Card>
          <div
            style={{
              marginTop: 12,
              padding: '10px 14px',
              borderRadius: 10,
              background: palette.inset,
              color: palette.sub,
              fontSize: 13,
            }}
          >
            <InfoCircleOutlined />{' '}
            汇率调整后：已发送的报价单不变；打开草稿报价单时会提示「系统汇率已更新」，业务员可一键按新汇率重算。
          </div>
        </div>

        <Card title={`变更记录 · ${logCurrency ?? ''}`}>
          {!logs ? (
            <Skeleton active />
          ) : logs.length === 0 ? (
            <div style={{ color: palette.mute }}>还没有变更记录</div>
          ) : (
            <div style={{ display: 'grid', gap: 8 }}>
              {logs.map((l) => (
                <div
                  key={l.operatedAt + String(l.newRate)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '10px 12px',
                    borderRadius: 10,
                    background: palette.inset,
                    fontSize: 13,
                  }}
                >
                  <span style={{ ...num, color: palette.mute }}>
                    {l.oldRate == null ? '—' : fmt(l.oldRate)}
                  </span>
                  <ArrowRightOutlined
                    style={{ color: palette.mute, fontSize: 11 }}
                  />
                  <b style={{ ...num, color: palette.ink }}>{fmt(l.newRate)}</b>
                  <span style={{ marginLeft: 'auto', color: palette.sub }}>
                    {l.operatorName ?? '—'}
                  </span>
                  <span style={{ ...num, color: palette.mute, fontSize: 12 }}>
                    {formatDateTime(l.operatedAt).slice(0, 16)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      <Modal
        open={!!editing && !confirming}
        title={`${editing?.rate == null ? '设置' : '修改'} ${editing?.currencyCode ?? ''} 汇率`}
        okText={editing?.rate == null ? '保存' : '下一步'}
        onCancel={() => setEditing(undefined)}
        onOk={() => {
          if (!validate()) return;
          if (editing?.rate == null) submit();
          else setConfirming(true);
        }}
        confirmLoading={busy}
        width={460}
      >
        <div style={{ marginBottom: 6, color: palette.sub }}>
          1 {editing?.currencyCode} = 人民币{' '}
          <span style={{ color: palette.red }}>*</span>
        </div>
        <InputNumber
          autoFocus
          style={{ width: '100%' }}
          min={0}
          precision={6}
          step={0.0001}
          value={value}
          status={inputError ? 'error' : undefined}
          onChange={(v) => {
            setValue(v);
            setInputError(undefined);
          }}
          aria-label="汇率"
        />
        {inputError ? (
          <div style={{ color: palette.red, marginTop: 6, fontSize: 13 }}>
            {inputError}
          </div>
        ) : (
          <div style={{ color: palette.mute, marginTop: 6, fontSize: 12 }}>
            保留 6 位小数，例如 9.120000
          </div>
        )}
      </Modal>

      <Modal
        open={!!editing && confirming}
        title={
          <span>
            <WarningOutlined
              style={{ color: palette.orange, marginRight: 8 }}
            />
            修改 {editing?.currencyCode} 汇率？
          </span>
        }
        okText="确认修改"
        onCancel={() => setConfirming(false)}
        onOk={submit}
        confirmLoading={busy}
        width={460}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 32,
            margin: '8px 0 16px',
          }}
        >
          <div>
            <div style={{ fontSize: 12, color: palette.mute }}>原汇率</div>
            <div
              style={{
                ...num,
                fontSize: 20,
                fontWeight: 700,
                color: palette.sub,
              }}
            >
              {fmt(editing?.rate)}
            </div>
          </div>
          <ArrowRightOutlined style={{ color: palette.mute }} />
          <div>
            <div style={{ fontSize: 12, color: palette.mute }}>新汇率</div>
            <div
              style={{
                ...num,
                fontSize: 20,
                fontWeight: 700,
                color: palette.ink,
              }}
            >
              {fmt(value)}
            </div>
          </div>
        </div>
        <div style={{ color: palette.sub }}>
          之后新建的 {editing?.currencyCode} 报价单使用 {fmt(value)}
          ；已发送的报价单不变；草稿会提示按新汇率重算。本次修改记入变更记录。
        </div>
      </Modal>
    </div>
  );
};

export default ExchangeRatePage;
