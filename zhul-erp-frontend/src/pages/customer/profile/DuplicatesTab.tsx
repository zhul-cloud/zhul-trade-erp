import {
  CheckOutlined,
  InfoCircleOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Modal, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';
import { LIST_PATH, Pill } from '../components';
import { readBizError } from '../service';
import { type DupGroup, type MergePreview, profileApi } from './service';

type Member = DupGroup['members'][number];

/**
 * 合并客户（原型 C6）：保留 ← 被合并，可以对调；先看预览再确认，合并后不能撤销。
 * 手动合并（原型 M2，group.reason = MANUAL）：第一个成员（当前客户）默认保留，左下是「上一步」。
 */
export const MergeModal: React.FC<{
  group?: DupGroup;
  index: number;
  onClose: () => void;
  /** keepId：保留下来的客户 */
  onDone: (keepId: number) => void;
  onDismiss?: (g: DupGroup) => void;
  onBack?: () => void;
}> = ({ group, index, onClose, onDone, onDismiss, onBack }) => {
  const manual = group?.reason === 'MANUAL';
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [pair, setPair] = useState<[Member, Member]>();
  const [preview, setPreview] = useState<MergePreview>();
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!group) return;
    if (group.reason === 'MANUAL') {
      setPair([group.members[0], group.members[1]]);
      return;
    }
    // 默认保留订单多、档案全的那个
    const sorted = [...group.members].sort(
      (a, b) =>
        b.orderCount - a.orderCount ||
        b.profileDone - a.profileDone ||
        b.inquiryCount - a.inquiryCount,
    );
    setPair([sorted[0], sorted[1]]);
  }, [group]);

  useEffect(() => {
    if (!pair) return;
    setPreview(undefined);
    setError(undefined);
    profileApi
      .mergePreview(pair[0].id, pair[1].id)
      .then(setPreview)
      .catch((e) => setError(readBizError(e).message));
  }, [pair]);

  const submit = async () => {
    if (!pair) return;
    setSaving(true);
    try {
      await profileApi.merge(pair[0].id, pair[1].id);
      message.success(`已把 ${pair[1].name} 并入 ${pair[0].name}`);
      onDone(pair[0].id);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const card = (m: Member, keep: boolean) => (
    <div
      style={{
        flex: 1,
        minWidth: 0,
        padding: 14,
        borderRadius: 14,
        border: `1px solid ${keep ? palette.accentLine : palette.hairline}`,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Pill tone={keep ? 'accent' : 'gray'}>{keep ? '保留' : '被合并'}</Pill>
        <span className="num" style={{ fontSize: 12, color: palette.mute }}>
          {m.customerCode}
        </span>
      </div>
      <div
        style={{
          margin: '8px 0',
          fontWeight: 600,
          color: palette.ink,
          wordBreak: 'break-word',
        }}
      >
        {m.name}
      </div>
      {[
        ['负责业务员', m.ownerName || '未分配'],
        ['完整度', `${m.profileDone}/5`],
        ['询盘 / 订单', `${m.inquiryCount} / ${m.orderCount}`],
      ].map(([k, v]) => (
        <div
          key={k}
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
      ))}
    </div>
  );

  const docs = preview ? Object.entries(preview.documents) : [];
  const lines: string[] = [];
  if (pair && preview) {
    lines.push(
      docs.length
        ? `${docs.map(([k, n]) => `${n} 张${k}`).join('、')}改挂到 ${pair[0].name}（单据上的客户名称不变）`
        : '被合并客户没有单据需要改挂',
    );
    if (preview.contacts.length)
      lines.push(
        `联系人 ${preview.contacts.join('、')} 搬过去，成为普通联系人`,
      );
    if (preview.filledFields.length)
      lines.push(
        `${pair[0].name} 为空的「${preview.filledFields.join('」「')}」用被合并客户的补上`,
      );
    lines.push(
      `${pair[1].name} 不再出现在客户列表；负责业务员仍是${pair[0].ownerName || '原负责人'}`,
    );
  }

  return (
    <Modal
      open={!!group}
      title={
        <div>
          合并客户
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            {manual
              ? '从客户详情发起 · 第 2 步，共 2 步：确认合并'
              : `疑似重复 · 第 ${index + 1} 组 · 原因：${group?.reasonText}`}
          </div>
        </div>
      }
      onCancel={onClose}
      width={860}
      destroyOnHidden
      footer={
        <div style={{ display: 'flex', gap: 8 }}>
          {manual ? (
            <Button style={{ marginRight: 'auto' }} onClick={onBack}>
              上一步
            </Button>
          ) : (
            <Button
              style={{ marginRight: 'auto' }}
              onClick={() => group && onDismiss?.(group)}
            >
              不是重复
            </Button>
          )}
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={saving}
            disabled={!preview || !!preview.blocked}
            onClick={submit}
          >
            确认合并
          </Button>
        </div>
      }
    >
      <div
        style={{
          margin: '8px 0 16px',
          padding: '10px 14px',
          borderRadius: 12,
          background: palette.orangeSoft,
          fontSize: 13,
        }}
      >
        <b style={{ color: palette.orange }}>
          {manual
            ? group?.members[1]?.hint
              ? `这两个客户${group.members[1].hint}`
              : '这两个客户不在「疑似重复」里：名称、邮箱、WhatsApp 都不同'
            : group?.reasonText}
        </b>
        <div style={{ color: palette.sub }}>
          确认是同一家公司后再合并，合并后不能撤销。
        </div>
      </div>
      {pair && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {card(pair[0], true)}
          <Button
            type="text"
            icon={<SwapOutlined />}
            aria-label="对调保留和被合并"
            title="对调保留和被合并"
            onClick={() => setPair([pair[1], pair[0]])}
          />
          {card(pair[1], false)}
        </div>
      )}
      {group && !manual && group.members.length > 2 && pair && (
        <div style={{ marginTop: 10, fontSize: 12, color: palette.mute }}>
          这一组有 {group.members.length} 个客户，一次合并两个。被合并的换成：
          {group.members
            .filter((m) => m.id !== pair[0].id && m.id !== pair[1].id)
            .map((m) => (
              <a
                key={m.id}
                style={{ marginLeft: 8 }}
                onClick={() => setPair([pair[0], m])}
              >
                {m.name}
              </a>
            ))}
        </div>
      )}
      <h3 style={{ margin: '20px 0 10px', fontSize: 14, color: palette.ink }}>
        合并后
      </h3>
      {error && <div style={{ color: palette.red }}>{error}</div>}
      {!preview && !error && <Skeleton active paragraph={{ rows: 3 }} />}
      {preview?.blocked && (
        <div
          style={{
            padding: '10px 14px',
            borderRadius: 12,
            background: palette.redSoft,
            color: palette.red,
          }}
        >
          {preview.blocked}
        </div>
      )}
      {preview && !preview.blocked && (
        <div
          style={{
            padding: '10px 14px',
            borderRadius: 12,
            background: palette.inset,
          }}
        >
          {lines.map((l) => (
            <div
              key={l}
              style={{
                display: 'flex',
                gap: 8,
                padding: '4px 0',
                fontSize: 13,
              }}
            >
              <CheckOutlined style={{ color: palette.green, marginTop: 4 }} />
              <span>{l}</span>
            </div>
          ))}
        </div>
      )}
      <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
        <InfoCircleOutlined />{' '}
        被合并客户有客户余额流水或退款单时不能合并，要先找财务处理。
      </div>
    </Modal>
  );
};

/** 疑似重复页签：按「同名同国家」「同邮箱」「同 WhatsApp 或电话」成组 */
const DuplicatesTab: React.FC<{
  canMerge: boolean;
  onCount: (n: number) => void;
  onMerged: () => void;
}> = ({ canMerge, onCount, onMerged }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const { zhOf } = useCountries();
  const [groups, setGroups] = useState<DupGroup[]>();
  const [error, setError] = useState<string>();
  const [merging, setMerging] = useState<number>();

  const load = useCallback(() => {
    setError(undefined);
    profileApi
      .duplicates()
      .then((g) => {
        setGroups(g);
        onCount(g.length);
      })
      .catch((e) => setError(readBizError(e).message));
  }, [onCount]);

  useEffect(() => {
    load();
  }, [load]);

  const dismiss = (g: DupGroup) =>
    modal.confirm({
      title: '标为不是重复？',
      content:
        '这一组不再出现；之后组内客户的信息变化或有新客户加入时会重新出现。',
      okText: '不是重复',
      cancelText: '取消',
      onOk: async () => {
        try {
          await profileApi.dismiss(g.groupKey ?? '');
          message.success('已标为不是重复');
          setMerging(undefined);
          load();
        } catch (e) {
          message.error(readBizError(e).message);
          throw e;
        }
      },
    });

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!groups)
    return (
      <div style={{ padding: 24, background: palette.card, borderRadius: 16 }}>
        <Skeleton active paragraph={{ rows: 6 }} />
      </div>
    );
  if (groups.length === 0)
    return (
      <div
        style={{
          background: palette.card,
          borderRadius: 16,
          border: `1px solid ${palette.hairline}`,
        }}
      >
        <EmptyHint
          title="没有疑似重复的客户"
          description="名称加国家、邮箱、WhatsApp 或电话相同的客户会出现在这里。"
        />
      </div>
    );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {groups.map((g, i) => (
        <section
          key={g.groupKey}
          aria-label={`第 ${i + 1} 组`}
          style={{
            padding: '14px 20px',
            borderRadius: 16,
            background: palette.card,
            border: `1px solid ${palette.hairline}`,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              marginBottom: 8,
            }}
          >
            <b style={{ color: palette.ink }}>第 {i + 1} 组</b>
            <Pill tone="orange">{g.reasonText}</Pill>
            {canMerge && (
              <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
                <Button size="small" onClick={() => dismiss(g)}>
                  不是重复
                </Button>
                <Button
                  size="small"
                  type="primary"
                  onClick={() => setMerging(i)}
                >
                  合并
                </Button>
              </span>
            )}
          </div>
          {g.members.map((m) => (
            <div
              key={m.id}
              style={{
                display: 'grid',
                gridTemplateColumns: '100px minmax(160px, 2fr) 1fr 1fr 1fr 1fr',
                gap: 12,
                alignItems: 'center',
                padding: '8px 0',
                borderTop: `1px solid ${palette.hairline}`,
                fontSize: 13,
              }}
            >
              <span className="num" style={{ color: palette.mute }}>
                {m.customerCode}
              </span>
              <a
                style={{ fontWeight: 600 }}
                onClick={() => history.push(`${LIST_PATH}/${m.id}`)}
              >
                {m.name}
              </a>
              <span>{zhOf(m.country) || '—'}</span>
              <span>{m.ownerName || '未分配'}</span>
              <span className="num">完整度 {m.profileDone}/5</span>
              <span className="num">
                询盘 {m.inquiryCount} · 订单 {m.orderCount}
              </span>
            </div>
          ))}
        </section>
      ))}
      <MergeModal
        group={merging == null ? undefined : groups[merging]}
        index={merging ?? 0}
        onClose={() => setMerging(undefined)}
        onDismiss={dismiss}
        onDone={() => {
          setMerging(undefined);
          load();
          onMerged();
        }}
      />
    </div>
  );
};

export default DuplicatesTab;
