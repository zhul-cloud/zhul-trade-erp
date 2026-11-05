import {
  CheckCircleFilled,
  InfoCircleOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Button, Input, Modal, Skeleton } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { Pill } from '../components';
import { readBizError } from '../service';
import { MergeModal } from './DuplicatesTab';
import { type DupGroup, profileApi } from './service';

type Member = DupGroup['members'][number];

/**
 * 手动合并（原型 M1 → M2）：从客户详情发起，先搜索选另一个客户，再沿用疑似重复的合并预览。
 * 当前客户默认保留，预览里可以对调。
 */
const ManualMergeModal: React.FC<{
  open: boolean;
  customerId: number;
  onClose: () => void;
  /** keepId：保留下来的客户（对调后可能不是当前客户） */
  onDone: (keepId: number) => void;
}> = ({ open, customerId, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const [keyword, setKeyword] = useState('');
  const [data, setData] = useState<DupGroup>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [picked, setPicked] = useState<Member>();
  const [step, setStep] = useState<1 | 2>(1);

  useEffect(() => {
    if (!open) {
      setKeyword('');
      setPicked(undefined);
      setStep(1);
      setData(undefined);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const k = keyword.trim();
    const t = setTimeout(
      () => {
        setLoading(true);
        setError(undefined);
        profileApi
          .mergeCandidates(customerId, k)
          .then(setData)
          .catch((e) => setError(readBizError(e).message))
          .finally(() => setLoading(false));
      },
      k ? 300 : 0,
    );
    return () => clearTimeout(t);
  }, [open, customerId, keyword]);

  const me = data?.members[0];
  const candidates = data?.members.slice(1) ?? [];
  const searching = keyword.trim().length >= 2;
  // 合并弹窗按 group 变化重新取预览，所以只在选择变化时新建
  const group = useMemo<DupGroup | undefined>(
    () =>
      open && step === 2 && me && picked
        ? { reason: 'MANUAL', reasonText: '手动合并', members: [me, picked] }
        : undefined,
    [open, step, me, picked],
  );

  const kv = (k: string, v: React.ReactNode) => (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        fontSize: 12,
        padding: '3px 0',
      }}
    >
      <span style={{ color: palette.mute }}>{k}</span>
      <b className="num">{v}</b>
    </div>
  );

  return (
    <>
      <Modal
        open={open && step === 1}
        title={
          <div>
            合并客户
            <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
              从客户详情发起 · 第 1 步，共 2 步：选择要合并的另一个客户
            </div>
          </div>
        }
        onCancel={onClose}
        width={860}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <span
              style={{ marginRight: 'auto', color: palette.sub, fontSize: 13 }}
            >
              {picked
                ? `已选：${picked.name}（${picked.customerCode}）`
                : '还没有选择客户'}
            </span>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              disabled={!picked || !me}
              onClick={() => setStep(2)}
            >
              下一步：预览合并
            </Button>
          </div>
        }
      >
        <div style={{ display: 'grid', gap: 14, marginTop: 8 }}>
          {me ? (
            <div
              style={{
                padding: 14,
                borderRadius: 14,
                border: `1px solid ${palette.accentLine}`,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Pill tone="accent">当前客户</Pill>
                <span
                  className="num"
                  style={{ fontSize: 12, color: palette.mute }}
                >
                  {me.customerCode}
                </span>
              </div>
              <div
                style={{ margin: '8px 0', fontWeight: 600, color: palette.ink }}
              >
                {me.name}
              </div>
              {kv('负责业务员', me.ownerName || '未分配')}
              {kv('询盘 / 订单', `${me.inquiryCount} / ${me.orderCount}`)}
            </div>
          ) : (
            <Skeleton active paragraph={{ rows: 2 }} />
          )}
          <Input
            allowClear
            autoFocus
            prefix={<SearchOutlined />}
            placeholder="搜索客户名称、编号、联系人、邮箱、WhatsApp（至少 2 个字）"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setPicked(undefined);
            }}
          />
          {error && <div style={{ color: palette.red }}>{error}</div>}
          {searching && !error && (
            <div
              style={{
                border: `1px solid ${palette.hairline}`,
                borderRadius: 12,
                overflow: 'hidden',
                maxHeight: 320,
                overflowY: 'auto',
              }}
            >
              {loading && candidates.length === 0 ? (
                <div style={{ padding: 16 }}>
                  <Skeleton active paragraph={{ rows: 2 }} />
                </div>
              ) : candidates.length === 0 ? (
                <div
                  style={{
                    padding: 20,
                    textAlign: 'center',
                    color: palette.mute,
                  }}
                >
                  没有找到客户，换个关键词试试
                </div>
              ) : (
                candidates.map((m, i) => {
                  const sel = picked?.id === m.id;
                  return (
                    <button
                      key={m.id}
                      type="button"
                      onClick={() => setPicked(m)}
                      style={{
                        display: 'flex',
                        width: '100%',
                        alignItems: 'center',
                        gap: 12,
                        padding: '12px 16px',
                        border: 'none',
                        borderBottom:
                          i < candidates.length - 1
                            ? `1px solid ${palette.hairline}`
                            : 'none',
                        background: sel ? palette.accentSoft : 'transparent',
                        cursor: 'pointer',
                        textAlign: 'left',
                        color: palette.ink,
                      }}
                    >
                      <span
                        style={{
                          width: 18,
                          height: 18,
                          flex: 'none',
                          borderRadius: '50%',
                          border: sel ? 'none' : `1.5px solid ${palette.mute}`,
                          color: palette.link,
                          fontSize: 18,
                          lineHeight: 1,
                        }}
                      >
                        {sel && <CheckCircleFilled />}
                      </span>
                      <span style={{ flex: 1, minWidth: 0 }}>
                        <span
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            fontWeight: 600,
                          }}
                        >
                          <span
                            style={{
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {m.name}
                          </span>
                          {m.hint && <Pill tone="orange">{m.hint}</Pill>}
                        </span>
                        <span style={{ fontSize: 12, color: palette.mute }}>
                          {m.customerCode}
                          {m.country ? ` · ${m.country}` : ''}
                        </span>
                      </span>
                      <span
                        style={{
                          display: 'grid',
                          justifyItems: 'end',
                          fontSize: 12,
                          color: palette.sub,
                          flex: 'none',
                        }}
                      >
                        <span>负责 {m.ownerName || '未分配'}</span>
                        <span className="num">
                          询盘 / 订单 {m.inquiryCount} / {m.orderCount}
                        </span>
                      </span>
                    </button>
                  );
                })
              )}
            </div>
          )}
          <div style={{ fontSize: 12, color: palette.mute }}>
            <InfoCircleOutlined /> 只列出你数据范围内的客户，不含当前客户，最多
            20 个。下一步先看合并预览，确认后才会合并。
          </div>
        </div>
      </Modal>
      <MergeModal
        group={group}
        index={0}
        onClose={onClose}
        onBack={() => setStep(1)}
        onDone={onDone}
      />
    </>
  );
};

export default ManualMergeModal;
