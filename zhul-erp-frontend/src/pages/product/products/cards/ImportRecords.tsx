import { DownloadOutlined, FileTextOutlined } from '@ant-design/icons';
import XMarkdown from '@ant-design/x-markdown';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import type { TableColumnsType } from 'antd';
import { App, Button, Drawer, Segmented, Space, Table } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint } from '../../components/EmptyHint';
import { Pill } from '../../components/Pills';
import {
  type ContentImportRecord,
  contentApi,
  type MarketingPlatform,
  type PackageType,
  readBizError,
} from '../../service';
import { useProductTheme } from '../../theme';
import { CardShell } from './CardShell';
import { PLATFORM_META } from './SeoCard';

const LANG_NAME: Record<string, string> = { zh: '中文', en: 'EN', ru: 'RU' };

const fmt = (t?: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '');

/** 下载原文件：用保存的原文生成同名文件 */
const downloadRaw = (fileName: string, text: string) => {
  const url = window.URL.createObjectURL(
    new Blob([text], { type: 'text/markdown;charset=utf-8' }),
  );
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(url), 1000);
};

const TypeCell: React.FC<{ r: ContentImportRecord }> = ({ r }) => (
  <Space size={6}>
    {r.packageType === 'facts' ? (
      <Pill tone="accent">资料包</Pill>
    ) : r.packageType === 'marketing' ? (
      <Pill tone="violet">营销包</Pill>
    ) : (
      <Pill tone="gray">未知</Pill>
    )}
    {r.platform && (
      <span>{PLATFORM_META[r.platform as MarketingPlatform]?.label}</span>
    )}
  </Space>
);

/** 上传记录原文预览：Markdown 排版或原文，可下载 */
export const ImportPreviewDrawer: React.FC<{
  id?: number;
  onClose: () => void;
}> = ({ id, onClose }) => {
  const { palette } = useProductTheme();
  const { message } = App.useApp();
  const [r, setR] = useState<ContentImportRecord>();
  const [mode, setMode] = useState<'render' | 'raw'>('render');

  useEffect(() => {
    setR(undefined);
    setMode('render');
    if (id === undefined) return;
    contentApi
      .importDetail(id)
      .then(setR)
      .catch((e) => message.error(readBizError(e).message));
  }, [id, message]);

  const raw = r?.rawText ?? '';
  // 排版时去掉开头的 frontmatter，它在上面的信息里已经体现
  const body = raw.replace(/^\s*---\n[\s\S]*?\n---\n?/, '');

  return (
    <Drawer
      open={id !== undefined}
      onClose={onClose}
      size="min(860px, 96vw)"
      destroyOnHidden
      title={r?.fileName ?? '上传记录'}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button
            icon={<DownloadOutlined />}
            disabled={!r}
            onClick={() => r && downloadRaw(r.fileName, raw)}
          >
            下载原文件
          </Button>
          <Button onClick={onClose}>关闭</Button>
        </div>
      }
    >
      {!r ? (
        <div style={{ color: palette.sub }}>正在加载…</div>
      ) : (
        <div>
          <Space size={8} style={{ marginBottom: 8 }}>
            <TypeCell r={r} />
            {r.lang && (
              <span style={{ color: palette.sub }}>
                {LANG_NAME[r.lang] ?? r.lang}
              </span>
            )}
          </Space>
          <div style={{ fontSize: 12, color: palette.sub, marginBottom: 12 }}>
            确认：{r.mine ? r.confirmedBy : '其他公司'} · {fmt(r.confirmedAt)}
          </div>
          {r.summary && (
            <div
              style={{
                padding: '10px 12px',
                borderRadius: 10,
                background: palette.inset,
                marginBottom: 16,
              }}
            >
              <div style={{ fontSize: 12, color: palette.sub }}>写入摘要</div>
              <div style={{ color: palette.ink }}>{r.summary}</div>
            </div>
          )}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              marginBottom: 10,
            }}
          >
            <b style={{ color: palette.ink }}>原文</b>
            <span style={{ flex: 1 }} />
            <Segmented
              size="small"
              value={mode}
              onChange={setMode}
              options={[
                { value: 'render', label: '排版' },
                { value: 'raw', label: 'Markdown 原文' },
              ]}
            />
          </div>
          <div
            style={{
              padding: 16,
              borderRadius: 12,
              background: palette.inset,
              overflowX: 'auto',
            }}
          >
            {mode === 'render' ? (
              <XMarkdown>{body}</XMarkdown>
            ) : (
              <pre
                style={{
                  margin: 0,
                  whiteSpace: 'pre-wrap',
                  fontSize: 12,
                  color: palette.ink,
                }}
              >
                {raw}
              </pre>
            )}
          </div>
        </div>
      )}
    </Drawer>
  );
};

/** 某商品的上传记录列表：本公司全部 + 其他公司的资料包，点一条预览原文 */
export const ImportRecordList: React.FC<{
  productId: number;
  /** 只看资料包或营销包 */
  type?: PackageType;
}> = ({ productId, type }) => {
  const { palette } = useProductTheme();
  const [rows, setRows] = useState<ContentImportRecord[]>();
  const [error, setError] = useState<string>();
  const [viewing, setViewing] = useState<number>();

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setRows(await contentApi.imports(productId, type));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [productId, type]);

  useEffect(() => {
    load();
  }, [load]);

  const columns: TableColumnsType<ContentImportRecord> = [
    {
      title: '文件',
      dataIndex: 'fileName',
      width: 220,
      render: (v: string, r) => (
        <a onClick={() => setViewing(r.id)}>
          <FileTextOutlined /> {v}
        </a>
      ),
    },
    {
      title: '类型',
      key: 'type',
      width: 170,
      render: (_, r) => <TypeCell r={r} />,
    },
    {
      title: '语言',
      dataIndex: 'lang',
      width: 70,
      render: (v?: string) => (v ? (LANG_NAME[v] ?? v) : '—'),
    },
    {
      title: '写入摘要',
      dataIndex: 'summary',
      ellipsis: true,
      render: (v?: string) => v || '—',
    },
    {
      title: '确认',
      key: 'confirmed',
      width: 150,
      render: (_, r) => (
        <div>
          <div style={{ color: r.mine ? palette.ink : palette.sub }}>
            {r.mine ? r.confirmedBy : '其他公司'}
          </div>
          <div style={{ fontSize: 12, color: palette.sub }}>
            {fmt(r.confirmedAt)}
          </div>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 70,
      render: (_, r) => <a onClick={() => setViewing(r.id)}>预览</a>,
    },
  ];

  if (error) {
    return (
      <EmptyHint
        title="加载失败"
        description={error}
        actionText="重试"
        onAction={load}
      />
    );
  }
  return (
    <>
      <Table<ContentImportRecord>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={!rows}
        pagination={
          rows && rows.length > 10
            ? { pageSize: 10, showSizeChanger: false }
            : false
        }
        scroll={{ x: 820 }}
        locale={{ emptyText: '还没有上传记录' }}
      />
      <div style={{ marginTop: 8, fontSize: 12, color: palette.sub }}>
        {type === 'facts'
          ? '只显示资料包，营销包在「运营管理 → 营销任务」查看；只记录确认写入的文件，其他公司的资料包也能看到。'
          : type === 'marketing'
            ? '只显示本公司的营销包；资料包在商品详情查看。只记录确认写入的文件。'
            : '只记录确认写入的文件；其他公司只显示资料包（写的是共享商品库），营销包看不到。'}
      </div>
      <ImportPreviewDrawer id={viewing} onClose={() => setViewing(undefined)} />
    </>
  );
};

/** 商品详情的「上传记录」卡片：只看资料包 */
export const ImportRecordCard: React.FC<{ productId: number }> = ({
  productId,
}) => (
  <CardShell id="card-imports" title="上传记录">
    <ImportRecordList productId={productId} type="facts" />
  </CardShell>
);

/** 资料任务、营销任务行内打开的上传记录，按入口只看对应包类型 */
export const ImportRecordsDrawer: React.FC<{
  product?: { id: number; label: string };
  type: PackageType;
  onClose: () => void;
}> = ({ product, type, onClose }) => (
  <Drawer
    open={!!product}
    onClose={onClose}
    size="min(1000px, 96vw)"
    destroyOnHidden
    title={product ? `上传记录 · ${product.label}` : '上传记录'}
  >
    {product && <ImportRecordList productId={product.id} type={type} />}
  </Drawer>
);
