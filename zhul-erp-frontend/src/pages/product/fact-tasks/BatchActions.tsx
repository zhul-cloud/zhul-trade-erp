import { Button, Modal, Space } from 'antd';
import React, { useState } from 'react';
import { Pill } from '@/pages/product/components/Pills';
import { useAppTheme } from '@/theme/AppTheme';
import {
  type ContentBatchResult,
  type ContentTask,
  contentApi,
  readBizError,
} from '../service';

/** AI 生成记录的「待确认」状态 */
export const AI_PENDING = 3;

const OUTCOME: Record<
  ContentBatchResult['items'][number]['outcome'],
  { text: string; tone: 'green' | 'orange' | 'red' }
> = {
  done: { text: '已完成', tone: 'green' },
  partial: { text: '部分写入', tone: 'orange' },
  failed: { text: '未处理', tone: 'red' },
};

/** 逐个列出批量操作的结果 */
const ResultList: React.FC<{
  result: ContentBatchResult;
  doneText: string;
}> = ({ result, doneText }) => {
  const { palette } = useAppTheme();
  return (
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      <Space size={16}>
        <span style={{ color: palette.green, fontWeight: 600 }}>
          {doneText} {result.done}
        </span>
        {result.partial > 0 && (
          <span style={{ color: palette.orange, fontWeight: 600 }}>
            部分写入 {result.partial}
          </span>
        )}
        {result.failed > 0 && (
          <span style={{ color: palette.red, fontWeight: 600 }}>
            未处理 {result.failed}
          </span>
        )}
      </Space>
      <div
        style={{
          background: palette.inset,
          borderRadius: 12,
          padding: '4px 14px',
          maxHeight: 420,
          overflowY: 'auto',
        }}
      >
        {result.items.map((it, i) => (
          <div
            key={it.id}
            style={{
              padding: '10px 0',
              borderTop: i ? `1px solid ${palette.hairline}` : undefined,
              display: 'grid',
              gap: 4,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span className="num" style={{ fontWeight: 600, flex: 1 }}>
                {it.label}
              </span>
              <Pill tone={OUTCOME[it.outcome].tone}>
                {it.outcome === 'done' ? doneText : OUTCOME[it.outcome].text}
              </Pill>
            </div>
            <span style={{ fontSize: 12, color: palette.mute }}>
              {it.message}
            </span>
          </div>
        ))}
      </div>
    </Space>
  );
};

/**
 * 批量确认 AI 结果（openspec content-ai-batch-hs）：只处理勾选里「待确认」的，按权限写入，
 * 有问题的章节跳过；写完逐个列出结果。
 */
export const BatchConfirmModal: React.FC<{
  rows?: ContentTask[];
  onClose: () => void;
  onDone: () => void;
}> = ({ rows, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<ContentBatchResult>();
  const [error, setError] = useState<string>();
  const pending = (rows ?? []).filter((r) => r.ai?.status === AI_PENDING);

  const close = () => {
    if (result) onDone();
    setResult(undefined);
    setError(undefined);
    onClose();
  };

  const confirm = async () => {
    setSaving(true);
    setError(undefined);
    try {
      setResult(
        await contentApi.aiConfirmBatch(pending.map((r) => r.ai?.id as number)),
      );
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={!!rows}
      onCancel={close}
      width={560}
      destroyOnHidden
      title={
        result ? '确认 AI 结果' : `确认 ${pending.length} 个商品的 AI 结果？`
      }
      footer={
        result ? (
          <Button type="primary" onClick={close}>
            完成
          </Button>
        ) : (
          <Space>
            <Button onClick={close}>取消</Button>
            <Button
              type="primary"
              loading={saving}
              disabled={!pending.length}
              onClick={confirm}
            >
              确认写入
            </Button>
          </Space>
        )
      }
    >
      {result ? (
        <ResultList result={result} doneText="已写入" />
      ) : (
        <Space orientation="vertical" size={16} style={{ width: '100%' }}>
          <span>
            按你有权限的部分写入，不逐个打开查看；解析出问题的章节或文件跳过，写完列出结果。
            {pending.length < (rows?.length ?? 0) &&
              ` 勾选里有 ${(rows?.length ?? 0) - pending.length} 个不是待确认，不处理。`}
          </span>
          <div
            style={{
              background: palette.inset,
              borderRadius: 12,
              padding: '12px 14px',
              display: 'grid',
              gap: 6,
            }}
          >
            <span style={{ fontSize: 12, color: palette.mute }}>会写入</span>
            <span>资料包与营销包：技术问答随资料包审核通过</span>
            <span style={{ fontSize: 12, color: palette.mute }}>
              资料写入后为「资料待核」，可以再勾选批量「资料已核」。
            </span>
          </div>
          {error && <span style={{ color: palette.red }}>{error}</span>}
        </Space>
      )}
    </Modal>
  );
};

/** 批量资料已核：已核对过、之后没有再导入的会跳过并说明原因 */
export const BatchVerifyModal: React.FC<{
  rows?: ContentTask[];
  onClose: () => void;
  onDone: () => void;
}> = ({ rows, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<ContentBatchResult>();
  const [error, setError] = useState<string>();
  const list = rows ?? [];

  const close = () => {
    if (result) onDone();
    setResult(undefined);
    setError(undefined);
    onClose();
  };

  const verify = async () => {
    setSaving(true);
    setError(undefined);
    try {
      setResult(
        await contentApi.verifyFactsBatch(list.map((r) => r.productId)),
      );
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={!!rows}
      onCancel={close}
      width={560}
      destroyOnHidden
      title={result ? '资料已核' : `把 ${list.length} 个商品标为资料已核？`}
      footer={
        result ? (
          <Button type="primary" onClick={close}>
            完成
          </Button>
        ) : (
          <Space>
            <Button onClick={close}>取消</Button>
            <Button type="primary" loading={saving} onClick={verify}>
              资料已核
            </Button>
          </Space>
        )
      }
    >
      {result ? (
        <ResultList result={result} doneText="已核对" />
      ) : (
        <Space orientation="vertical" size={12} style={{ width: '100%' }}>
          <span>
            未核实的中文规格参数、应用场景会标为已核实，以后导入资料包不再覆盖。已核对过、之后没有再导入的商品会跳过。
          </span>
          <Space size={6} wrap>
            {list.map((r) => (
              <Pill key={r.productId} tone="gray">
                {r.mpn}
              </Pill>
            ))}
          </Space>
          {error && <span style={{ color: palette.red }}>{error}</span>}
        </Space>
      )}
    </Modal>
  );
};
