import { LinkOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { App, Button, Input, Modal, Space, Spin } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { Pill } from '../components/Pills';
import {
  categoryApi,
  readBizError,
  type Tariff,
  type TariffRate,
} from '../service';
import { useProductTheme } from '../theme';

/** AI 任务状态：完成、失败 */
const AI_DONE = 3;
const AI_FAILED = 4;
const POLL_MS = 4000;

type Row = TariffRate & { ai?: boolean };

/**
 * 目的国进口关税参考（openspec content-ai-batch-hs）：按海关编码前 6 位维护 9 个主要出口国。
 * 「AI 补全」只查未填的国家，结果填进表格作为建议，人核对后保存才生效。
 */
const TariffModal: React.FC<{
  hs6?: string;
  canEdit: boolean;
  onClose: () => void;
  onSaved: () => void;
}> = ({ hs6, canEdit, onClose, onSaved }) => {
  const { palette } = useProductTheme();
  const { message } = App.useApp();
  const [data, setData] = useState<Tariff>();
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [aiTask, setAiTask] = useState<number>();
  const timer = useRef<number>(undefined);

  useEffect(() => {
    if (!hs6) return;
    setLoading(true);
    setAiTask(undefined);
    categoryApi
      .tariff(hs6)
      .then((t) => {
        setData(t);
        setRows(t.rates);
      })
      .catch((e) => message.error(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [hs6]);

  // AI 补全进行中：定时取结果，完成后把建议填进未填的国家
  useEffect(() => {
    if (!aiTask) return undefined;
    timer.current = window.setInterval(async () => {
      try {
        const r = await categoryApi.tariffAiStatus(aiTask);
        if (r.status !== AI_DONE && r.status !== AI_FAILED) return;
        window.clearInterval(timer.current);
        setAiTask(undefined);
        if (r.status === AI_FAILED) {
          message.error(`AI 补全失败：${r.errorMessage || '请稍后重试'}`);
          return;
        }
        const byCountry = new Map(r.rates.map((x) => [x.countryCode, x]));
        setRows((prev) =>
          prev.map((row) => {
            const s = byCountry.get(row.countryCode);
            return s && !row.rate
              ? {
                  ...row,
                  rate: s.rate,
                  note: s.note,
                  source: s.source,
                  ai: true,
                }
              : row;
          }),
        );
        message.success(
          r.rates.length
            ? `AI 查到 ${r.rates.length} 个国家，请核对后保存`
            : 'AI 没有查到可信的来源，请手工填写',
        );
      } catch (e) {
        window.clearInterval(timer.current);
        setAiTask(undefined);
        message.error(readBizError(e).message);
      }
    }, POLL_MS);
    return () => window.clearInterval(timer.current);
  }, [aiTask]);

  const missing = rows.filter((r) => !r.rate.trim());

  const update = (code: string, patch: Partial<Row>) =>
    setRows((prev) =>
      prev.map((r) => (r.countryCode === code ? { ...r, ...patch } : r)),
    );

  const startAi = async () => {
    if (!hs6) return;
    try {
      setAiTask((await categoryApi.tariffAi(hs6)).taskId);
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const save = async () => {
    if (!hs6) return;
    setSaving(true);
    try {
      await categoryApi.saveTariff(
        hs6,
        rows.map((r) => ({
          countryCode: r.countryCode,
          rate: r.rate.trim(),
          note: r.note.trim(),
          source: r.source,
        })),
      );
      message.success('已保存');
      onSaved();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const head = (text: string, width?: number) => (
    <span
      style={{
        fontSize: 12,
        color: palette.mute,
        width,
        flex: width ? undefined : 1,
      }}
    >
      {text}
    </span>
  );

  return (
    <Modal
      open={!!hs6}
      onCancel={onClose}
      width={720}
      destroyOnHidden
      title={`关税参考 · ${hs6 ?? ''}`}
      footer={
        <Space>
          {canEdit && missing.length > 0 && (
            <Button
              icon={<ThunderboltOutlined />}
              loading={!!aiTask}
              onClick={startAi}
            >
              {aiTask ? 'AI 查询中' : `AI 补全未填（${missing.length} 国）`}
            </Button>
          )}
          <Button onClick={onClose}>{canEdit ? '取消' : '关闭'}</Button>
          {canEdit && (
            <Button type="primary" loading={saving} onClick={save}>
              保存
            </Button>
          )}
        </Space>
      }
    >
      <Spin spinning={loading}>
        <Space orientation="vertical" size={10} style={{ width: '100%' }}>
          {data?.categories.length ? (
            <Space size={6} wrap>
              <span style={{ fontSize: 12, color: palette.mute }}>
                用到这个编码的品类：
              </span>
              {data.categories.map((c) => (
                <Pill key={c} tone="gray">
                  {c}
                </Pill>
              ))}
            </Space>
          ) : null}
          <div style={{ display: 'flex', gap: 10 }}>
            {head('国家', 120)}
            {head('进口关税', 120)}
            {head('备注')}
            {head('核对日期', 96)}
          </div>
          {rows.map((r) => (
            <div
              key={r.countryCode}
              style={{ display: 'flex', gap: 10, alignItems: 'center' }}
            >
              <span style={{ width: 120, display: 'flex', gap: 6 }}>
                <b>{r.countryName}</b>
                <span className="num" style={{ color: palette.mute }}>
                  {r.countryCode}
                </span>
              </span>
              <Input
                style={{ width: 120 }}
                value={r.rate}
                placeholder="未填"
                maxLength={32}
                disabled={!canEdit}
                aria-label={`${r.countryName}进口关税`}
                onChange={(e) =>
                  update(r.countryCode, { rate: e.target.value })
                }
              />
              <Input
                style={{ flex: 1 }}
                value={r.note}
                placeholder="备注（选填）"
                maxLength={200}
                disabled={!canEdit}
                aria-label={`${r.countryName}备注`}
                onChange={(e) =>
                  update(r.countryCode, { note: e.target.value })
                }
                suffix={
                  r.source ? (
                    <a
                      href={r.source}
                      target="_blank"
                      rel="noreferrer"
                      title={r.source}
                    >
                      <LinkOutlined />
                    </a>
                  ) : undefined
                }
              />
              <span style={{ width: 96, fontSize: 12, color: palette.mute }}>
                {r.ai ? <Pill tone="gray">AI 建议</Pill> : (r.checkedAt ?? '—')}
              </span>
            </div>
          ))}
          <span style={{ fontSize: 12, color: palette.mute }}>
            参考信息，不代替报关行意见；保存时填了税率的国家核对日期记为今天。
          </span>
        </Space>
      </Spin>
    </Modal>
  );
};

export default TariffModal;
