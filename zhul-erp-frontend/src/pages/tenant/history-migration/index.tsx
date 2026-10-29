import { InboxOutlined } from '@ant-design/icons';
import { history, useSearchParams } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Drawer,
  Empty,
  Segmented,
  Space,
  Table,
  Upload,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  getBatch,
  listBatches,
  type MigrationBatch,
  type MigrationBundle,
  rollbackBatch,
  runMigration,
  uploadBundle,
  type VerifyRow,
} from './service';

/** 迁移包里各文件的中文名（counts 的键） */
const FILE_NAMES: Record<string, string> = {
  users: '人员',
  suppliers: '供应商',
  customers: '客户',
  opportunities: '商机',
  inquiries: '询盘',
  sourcing: '询价',
  orders: '销售订单',
  purchases: '采购单',
  shipments: '发货',
  media: '图片',
};

const busy = (b: MigrationBatch) => b.status === 1 || b.rollingBack;

const StatusPill: React.FC<{ b: MigrationBatch }> = ({ b }) => {
  if (b.rollingBack)
    return (
      <Pill tone="orange" dot>
        回滚中
      </Pill>
    );
  switch (b.status) {
    case 1:
      return (
        <Pill tone="accent" dot>
          运行中
        </Pill>
      );
    case 2:
      return <Pill tone="green">成功</Pill>;
    case 3:
      return <Pill tone="red">失败</Pill>;
    default:
      return <Pill tone="mute">已回滚</Pill>;
  }
};

const duration = (b: MigrationBatch) => {
  if (!b.startedAt) return '—';
  const end = b.finishedAt ? dayjs(b.finishedAt) : dayjs();
  const min = end.diff(dayjs(b.startedAt), 'minute');
  return min < 1 ? '不到 1 分钟' : `${min} 分钟`;
};

const HistoryMigrationPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const [search] = useSearchParams();
  const tenantId = Number(search.get('tenantId'));
  const tenantName = search.get('name') ?? '';
  const [bundle, setBundle] = useState<MigrationBundle>();
  const [uploading, setUploading] = useState(false);
  const [batches, setBatches] = useState<MigrationBatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<MigrationBatch>();
  const [rowFilter, setRowFilter] = useState<'不一致' | '已知差异' | '全部'>(
    '不一致',
  );

  const load = useCallback(async () => {
    try {
      setBatches(await listBatches(tenantId));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (tenantId) load();
  }, [tenantId, load]);

  const running = batches.some(busy);
  // 有任务在跑时每 5 秒刷新一次进度
  useEffect(() => {
    if (!running) return undefined;
    const t = window.setInterval(load, 5000);
    return () => window.clearInterval(t);
  }, [running, load]);

  const expired = bundle?.linksExpireAt
    ? dayjs(bundle.linksExpireAt).isBefore(dayjs())
    : false;

  const submit = (dryRun: boolean) => {
    if (!bundle) return;
    const go = async () => {
      await runMigration(tenantId, bundle.id, dryRun);
      message.success(dryRun ? '已开始干跑' : '已开始正式执行');
      load();
    };
    if (dryRun) {
      go();
      return;
    }
    modal.confirm({
      title: `正式执行，把迁移包导入「${tenantName}」？`,
      content:
        '会写入客户、询盘、订单、财务等历史数据并上传图片，大约需要 30 分钟，期间可以离开本页。已经迁移过的记录会跳过；出问题可以按批次回滚。',
      okText: '正式执行',
      onOk: go,
    });
  };

  const rollback = (b: MigrationBatch) =>
    modal.confirm({
      title: `回滚批次 ${b.batchNo}？`,
      content:
        '这个批次写入的数据都会作废（软删除），图片移出素材库。迁移建的员工账号保留。回滚后可以重新执行。',
      okText: '回滚',
      okButtonProps: { danger: true },
      onOk: async () => {
        await rollbackBatch(b.id);
        message.success('已开始回滚');
        load();
      },
    });

  const openDetail = async (b: MigrationBatch) => {
    setDetail(await getBatch(b.id));
  };

  const stageRows = useMemo(
    () =>
      Object.entries(detail?.stageStats ?? {}).flatMap(([stage, kinds]) =>
        Object.entries(kinds).map(([kind, v]) => ({
          key: `${stage}|${kind}`,
          stage,
          kind,
          ...v,
        })),
      ),
    [detail],
  );
  const verifyRows = useMemo(
    () =>
      (detail?.verifyResult?.rows ?? []).filter(
        (r) => rowFilter === '全部' || r.verdict === rowFilter,
      ),
    [detail, rowFilter],
  );

  if (!tenantId) {
    return <Empty description="请从租户列表进入" />;
  }

  const verifyText = (b: MigrationBatch) => {
    const s = b.verifyResult?.summary;
    if (!s) return <span style={{ color: palette.mute }}>—</span>;
    return s.inconsistent ? (
      <Pill tone="red">不一致 {s.inconsistent} 项</Pill>
    ) : (
      <Pill tone="green">
        {s.total} 项无不一致（已知差异 {s.known}）
      </Pill>
    );
  };

  return (
    <div>
      <PageTitle
        root="租户管理"
        crumbs={[
          <a key="list" onClick={() => history.push('/tenant/list')}>
            租户列表
          </a>,
          '历史迁移',
        ]}
        title={`历史迁移：${tenantName}`}
        description="把飞书旧系统的数据导入这个租户。先干跑核对数量，再正式执行；正式执行完自动与旧报表核对，出问题可以按批次回滚。"
      />

      <Card title="迁移包" style={{ marginBottom: 16 }}>
        <Upload.Dragger
          accept=".zip"
          showUploadList={false}
          disabled={uploading}
          beforeUpload={(file) => {
            setUploading(true);
            uploadBundle(file)
              .then((b) => {
                setBundle(b);
                message.success('迁移包已上传');
              })
              .finally(() => setUploading(false));
            return false;
          }}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">
            {uploading ? '正在上传…' : '点击或拖入迁移包（pack.js 打出的 zip）'}
          </p>
          <p className="ant-upload-hint">
            同一个包重复上传不会重复保存；页面刷新后重新上传即可
          </p>
        </Upload.Dragger>

        {bundle && (
          <div style={{ marginTop: 16 }}>
            <Space wrap size={[8, 8]}>
              <span style={{ color: palette.sub }}>版本 {bundle.version}</span>
              {Object.entries(bundle.counts)
                .filter(([k]) => FILE_NAMES[k])
                .map(([k, n]) => (
                  <Pill key={k} tone="gray">
                    {FILE_NAMES[k]} {n}
                  </Pill>
                ))}
            </Space>
            {bundle.linksExpireAt && (
              <Alert
                style={{ marginTop: 12 }}
                type={expired ? 'error' : 'info'}
                showIcon
                title={
                  expired
                    ? '图片下载链接已过期：请在本机重新运行 pack.js，再上传新的迁移包'
                    : `图片下载链接 ${formatDateTime(bundle.linksExpireAt)} 到期，请在这之前完成正式执行`
                }
              />
            )}
            <Space style={{ marginTop: 16 }}>
              <Button disabled={running} onClick={() => submit(true)}>
                干跑
              </Button>
              <Button
                type="primary"
                disabled={running || expired}
                onClick={() => submit(false)}
              >
                正式执行
              </Button>
              {running && (
                <span style={{ color: palette.mute }}>
                  有任务在运行，结束后才能再提交
                </span>
              )}
            </Space>
          </div>
        )}
      </Card>

      <Card title="执行记录">
        <Table<MigrationBatch>
          rowKey="id"
          loading={loading}
          dataSource={batches}
          pagination={false}
          locale={{ emptyText: '还没有执行过' }}
          columns={[
            {
              title: '批次',
              dataIndex: 'batchNo',
              render: (v) => <span className="num">{v}</span>,
            },
            {
              title: '方式',
              dataIndex: 'runMode',
              width: 90,
              render: (v) => (v === 1 ? '干跑' : '正式执行'),
            },
            {
              title: '状态',
              key: 'status',
              width: 100,
              render: (_, b) => <StatusPill b={b} />,
            },
            {
              title: '开始时间',
              dataIndex: 'startedAt',
              width: 170,
              render: (v) => <span className="num">{formatDateTime(v)}</span>,
            },
            {
              title: '用时',
              key: 'duration',
              width: 110,
              render: (_, b) => duration(b),
            },
            {
              title: '核对',
              key: 'verify',
              render: (_, b) => verifyText(b),
            },
            {
              title: '操作',
              key: 'op',
              width: 130,
              render: (_, b) => (
                <Space>
                  <a onClick={() => openDetail(b)}>详情</a>
                  {b.runMode === 2 &&
                    (b.status === 2 || b.status === 3) &&
                    !b.rollingBack && (
                      <a
                        style={{ color: palette.red }}
                        onClick={() => rollback(b)}
                      >
                        回滚
                      </a>
                    )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Drawer
        open={!!detail}
        size="min(960px, 96vw)"
        title={detail ? `批次 ${detail.batchNo}` : ''}
        onClose={() => setDetail(undefined)}
        destroyOnHidden
      >
        {detail && (
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            {detail.errorText && (
              <Alert type="error" showIcon title={detail.errorText} />
            )}
            <Table
              size="small"
              rowKey="key"
              pagination={false}
              dataSource={stageRows}
              columns={[
                { title: '阶段', dataIndex: 'stage' },
                { title: '数据', dataIndex: 'kind' },
                { title: '新建', dataIndex: '新建', align: 'right' },
                { title: '跳过', dataIndex: '跳过', align: 'right' },
                {
                  title: '失败',
                  dataIndex: '失败',
                  align: 'right',
                  render: (v: number) =>
                    v ? <span style={{ color: palette.red }}>{v}</span> : v,
                },
              ]}
            />
            {detail.verifyResult && (
              <>
                <Space>
                  <strong>与旧报表核对</strong>
                  {verifyText(detail)}
                  <Segmented
                    size="small"
                    value={rowFilter}
                    onChange={(v) => setRowFilter(v as typeof rowFilter)}
                    options={['不一致', '已知差异', '全部']}
                  />
                </Space>
                <Table<VerifyRow>
                  size="small"
                  rowKey={(r) => `${r.report}|${r.scope}|${r.metric}`}
                  dataSource={verifyRows}
                  pagination={{ pageSize: 20, showSizeChanger: false }}
                  locale={{ emptyText: '没有' }}
                  columns={[
                    { title: '报表', dataIndex: 'report', width: 110 },
                    { title: '范围', dataIndex: 'scope' },
                    { title: '指标', dataIndex: 'metric' },
                    { title: '旧报表', dataIndex: 'old', align: 'right' },
                    { title: '新系统', dataIndex: 'new', align: 'right' },
                    { title: '说明', dataIndex: 'note' },
                  ]}
                />
              </>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  );
};

export default HistoryMigrationPage;
