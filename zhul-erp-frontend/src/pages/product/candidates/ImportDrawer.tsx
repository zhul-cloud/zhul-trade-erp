import {
  CheckCircleFilled,
  CloseCircleFilled,
  InboxOutlined,
  InfoCircleOutlined,
  MinusCircleFilled,
  UploadOutlined,
} from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { App, Button, Drawer, Segmented, Table, Tooltip, Upload } from 'antd';
import React, { useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import {
  candidateApi,
  type ImportFile,
  type ImportPreview,
  type ImportResult,
  type ImportResultCode,
  readBizError,
} from './service';

const MAX_FILES = 200;
const MAX_BYTES = 1024 * 1024;

const RESULT_TONE: Record<
  ImportResultCode,
  'green' | 'accent' | 'gray' | 'orange' | 'mute'
> = {
  NEW: 'green',
  IN_POOL: 'accent',
  IN_PRODUCT: 'gray',
  REJECTED: 'orange',
  DUPLICATE: 'gray',
  INVALID: 'mute',
};

/** 写入的两类：新候选、已在候选池 */
const writable = (p: ImportPreview) =>
  p.result === 'NEW' || p.result === 'IN_POOL';

/**
 * 添加候选（spec product/product-candidate「从商品文件导入候选」）：上传 llm-wiki 商品文件 → 预览 → 确认；
 * 「输入型号（AI 解析）」暂不可用。有商品候选菜单即可使用，建档仍需审核权限。
 */
export const ImportDrawer: React.FC<{
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [files, setFiles] = useState<ImportFile[]>([]);
  const [previews, setPreviews] = useState<ImportPreview[]>([]);
  const [result, setResult] = useState<ImportResult>();
  const [loading, setLoading] = useState(false);

  const reset = () => {
    setFiles([]);
    setPreviews([]);
    setResult(undefined);
  };

  const close = () => {
    if (result) onDone();
    reset();
    onClose();
  };

  const pick = async (list: File[]) => {
    if (list.length > MAX_FILES) {
      message.error(`一次最多上传 ${MAX_FILES} 个文件`);
      return;
    }
    const big = list.find((f) => f.size > MAX_BYTES);
    if (big) {
      message.error(`「${big.name}」超过 1MB`);
      return;
    }
    setLoading(true);
    try {
      const read = await Promise.all(
        list.map(async (f) => ({ fileName: f.name, content: await f.text() })),
      );
      setFiles(read);
      setPreviews(await candidateApi.importPreview(read));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  };

  const confirm = async () => {
    setLoading(true);
    try {
      setResult(await candidateApi.importConfirm(files));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  };

  const count = (codes: ImportResultCode[]) =>
    previews.filter((p) => codes.includes(p.result)).length;
  const toWrite = previews.filter(writable).length;
  const muted = <span style={{ color: palette.mute }}>—</span>;

  const columns: TableColumnsType<ImportPreview> = [
    {
      title: '文件',
      dataIndex: 'fileName',
      width: 200,
      ellipsis: true,
    },
    {
      title: '品牌',
      key: 'brand',
      width: 120,
      render: (_, p) =>
        p.brand ? (
          p.brandId ? (
            p.brandName || p.brand
          ) : (
            <div>
              <div style={{ color: palette.orange }}>{p.brand}</div>
              <div style={{ fontSize: 12, color: palette.orange }}>
                品牌未识别
              </div>
            </div>
          )
        ) : (
          muted
        ),
    },
    {
      title: '型号',
      dataIndex: 'model',
      width: 190,
      render: (v?: string) => (v ? <b>{v}</b> : muted),
    },
    {
      title: '建议品类',
      key: 'category',
      width: 120,
      render: (_, p) =>
        p.categoryName ? (
          p.categoryName
        ) : writable(p) ? (
          <span style={{ color: palette.orange }}>待审核时选</span>
        ) : (
          muted
        ),
    },
    {
      title: '名称',
      dataIndex: 'productName',
      width: 180,
      ellipsis: true,
      render: (v?: string) => v || muted,
    },
    {
      title: '处理结果',
      key: 'result',
      width: 190,
      render: (_, p) => (
        <div>
          <Pill tone={RESULT_TONE[p.result]}>{p.resultName}</Pill>
          {p.note && (
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
              {p.note}
            </div>
          )}
        </div>
      ),
    },
  ];

  const tabs = (
    <Segmented
      value="file"
      options={[
        { value: 'file', label: '上传商品文件' },
        {
          value: 'ai',
          disabled: true,
          label: (
            <Tooltip title="输入型号由 AI 联网核对、纠正型号并建议细分品类，即将推出">
              <span>输入型号（AI 解析）· 即将推出</span>
            </Tooltip>
          ),
        },
      ]}
      style={{ marginBottom: 16 }}
    />
  );

  const outcomeIcon = (o: ImportResult['items'][number]['outcome']) =>
    o === 'FAILED' ? (
      <CloseCircleFilled style={{ color: palette.red }} />
    ) : o === 'SKIPPED' ? (
      <MinusCircleFilled style={{ color: palette.mute }} />
    ) : (
      <CheckCircleFilled style={{ color: palette.green }} />
    );

  return (
    <Drawer
      open={open}
      onClose={close}
      size="min(1100px, 96vw)"
      destroyOnHidden
      title={
        result
          ? '导入结果'
          : previews.length
            ? '添加候选 · 导入预览'
            : '添加候选'
      }
      footer={
        result ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" onClick={close}>
              完成
            </Button>
          </div>
        ) : previews.length > 0 ? (
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <Button icon={<UploadOutlined />} onClick={reset}>
              重新选择文件
            </Button>
            <span style={{ flex: 1 }} />
            <Button onClick={close}>取消</Button>
            <Button
              type="primary"
              disabled={toWrite === 0}
              loading={loading}
              onClick={confirm}
            >
              确认导入 {toWrite} 个
            </Button>
          </div>
        ) : (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button onClick={close}>取消</Button>
          </div>
        )
      }
    >
      {result ? (
        <div>
          <div
            style={{
              display: 'flex',
              gap: 8,
              flexWrap: 'wrap',
              marginBottom: 16,
            }}
          >
            <Pill tone="green">新建 {result.created}</Pill>
            <Pill tone="accent">追加来源 {result.appended}</Pill>
            <Pill tone="gray">跳过 {result.skipped}</Pill>
            {result.failed > 0 && <Pill tone="red">失败 {result.failed}</Pill>}
          </div>
          <div style={{ display: 'grid', gap: 6 }}>
            {result.items.map((it, i) => (
              <div
                // biome-ignore lint/suspicious/noArrayIndexKey: 同名文件可能出现多次，按上传顺序区分
                key={i}
                style={{
                  display: 'flex',
                  gap: 10,
                  alignItems: 'center',
                  padding: '8px 12px',
                  borderRadius: 8,
                  background: palette.inset,
                  fontSize: 13,
                }}
              >
                {outcomeIcon(it.outcome)}
                <b style={{ color: palette.ink }}>{it.fileName}</b>
                {it.model && (
                  <span style={{ color: palette.mute }}>· {it.model}</span>
                )}
                <span style={{ flex: 1 }} />
                <span style={{ fontSize: 12, color: palette.sub }}>
                  {it.message}
                </span>
              </div>
            ))}
          </div>
          <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
            新建的候选在「待审核」里，审核通过后才进入商品库。
          </div>
        </div>
      ) : previews.length === 0 ? (
        <div>
          {tabs}
          <Upload.Dragger
            multiple
            accept=".md,.markdown"
            showUploadList={false}
            disabled={loading}
            beforeUpload={(_, list) => {
              // 多选时每个文件都会回调一次，只在最后一个处理整批
              if (_ === list[list.length - 1]) pick(list);
              return false;
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">
              {loading ? '正在解析…' : '点击或拖入商品 Markdown 文件，可多选'}
            </p>
            <p className="ant-upload-hint">
              支持 llm-wiki
              商品文件（wiki/products/品牌/品类/型号.md）；一次最多 200
              个，每个不超过 1MB
            </p>
          </Upload.Dragger>
          <div
            style={{
              display: 'flex',
              gap: 8,
              marginTop: 16,
              padding: '12px 14px',
              borderRadius: 10,
              background: palette.inset,
              fontSize: 13,
              color: palette.sub,
            }}
          >
            <InfoCircleOutlined style={{ color: palette.link, marginTop: 3 }} />
            <span>
              只取品牌、型号、品类、中文名称与中英文描述，建成待审核候选；规格、HS
              编码、物流、图片等资料以后在内容任务里补。已在商品库的型号会跳过，已在候选池的计一次来源。
            </span>
          </div>
        </div>
      ) : (
        <div>
          {tabs}
          <div
            style={{
              display: 'flex',
              gap: 8,
              alignItems: 'center',
              flexWrap: 'wrap',
              marginBottom: 12,
            }}
          >
            <b style={{ color: palette.ink }}>{previews.length} 个文件</b>
            <Pill tone="green">新建 {count(['NEW'])}</Pill>
            <Pill tone="accent">追加来源 {count(['IN_POOL'])}</Pill>
            <Pill tone="gray">
              跳过 {count(['IN_PRODUCT', 'REJECTED', 'DUPLICATE', 'INVALID'])}
            </Pill>
          </div>
          <Table<ImportPreview>
            rowKey={(p) => previews.indexOf(p)}
            size="small"
            columns={columns}
            dataSource={previews}
            pagination={
              previews.length > 50
                ? { pageSize: 50, showSizeChanger: false }
                : false
            }
            scroll={{ x: 1000 }}
          />
          <div style={{ marginTop: 8, fontSize: 12, color: palette.mute }}>
            建议品类由系统按文件里的品类自动匹配，没匹配上的留给审核时选择。
          </div>
        </div>
      )}
    </Drawer>
  );
};
