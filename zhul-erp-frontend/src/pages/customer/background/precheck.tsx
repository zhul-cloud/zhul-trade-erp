import {
  BulbOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  MinusCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Button, Modal } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import {
  type BackgroundPrecheck,
  backgroundApi,
  type PrecheckItem,
  type PrecheckLevel,
} from './service';

/** 背调前检查（spec crm/customer-background「背调前检查」，原型 P1–P6） */

export interface PrecheckState {
  data?: BackgroundPrecheck;
  reload: () => Promise<BackgroundPrecheck | undefined>;
  /**
   * 检查窗口是否打开。放在页面级 hook 里：「去补充」保存后页面重新加载会卸载按钮，
   * 重新挂上时窗口照样打开，回到检查窗口
   */
  open: boolean;
  setOpen: (open: boolean) => void;
}

/** 客户的背调前检查结果；补充信息保存后调用 reload 重新计算 */
export const useBackgroundPrecheck = (customerId?: number): PrecheckState => {
  const [data, setData] = useState<BackgroundPrecheck>();
  const [open, setOpen] = useState(false);
  const reload = useCallback(async () => {
    if (!customerId) return undefined;
    try {
      const d = await backgroundApi.precheck(customerId);
      setData(d);
      return d;
    } catch {
      return undefined;
    }
  }, [customerId]);
  useEffect(() => {
    reload();
  }, [reload]);
  return { data, reload, open, setOpen };
};

const LEVEL_TEXT: Record<PrecheckLevel, string> = {
  LOW: '很少',
  MEDIUM: '一般',
  HIGH: '充足',
};

const TIPS: Record<PrecheckLevel, string> = {
  LOW: '官网、企业邮箱、询盘原文一项都没有，AI 多半只能给出待核实清单。补上任意一项再背调，结果会准很多。',
  MEDIUM: '再补上企业邮箱或询盘原文，背调结果会更完整。',
  HIGH: '关键信息齐全，可以直接背调。',
};

const useLevelColor = () => {
  const { palette } = useAppTheme();
  return (level: PrecheckLevel) =>
    level === 'LOW'
      ? { color: palette.red, soft: palette.redSoft }
      : level === 'MEDIUM'
        ? { color: palette.orange, soft: palette.orangeSoft }
        : { color: palette.green, soft: palette.greenSoft };
};

/** 「信息：一般」标签 */
export const LevelPill: React.FC<{ level: PrecheckLevel }> = ({ level }) => {
  const c = useLevelColor()(level);
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '2px 10px',
        borderRadius: 999,
        fontSize: 12,
        fontWeight: 600,
        color: c.color,
        background: c.soft,
        whiteSpace: 'nowrap',
      }}
    >
      {level === 'HIGH' ? (
        <CheckCircleOutlined />
      ) : (
        <ExclamationCircleOutlined />
      )}
      信息：{LEVEL_TEXT[level]}
    </span>
  );
};

/** 缺失与需要处理的项名称，如「企业邮箱、询盘原文、领英」 */
export const missingLabels = (d: BackgroundPrecheck) =>
  d.items.filter((i) => i.state !== 'OK').map((i) => i.label);

const ItemIcon: React.FC<{ item: PrecheckItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  if (item.state === 'OK')
    return <CheckCircleOutlined style={{ color: palette.green }} />;
  if (item.state === 'WARN')
    return <WarningOutlined style={{ color: palette.orange }} />;
  return item.keyInfo ? (
    <CloseCircleOutlined style={{ color: palette.red }} />
  ) : (
    <MinusCircleOutlined style={{ color: palette.mute }} />
  );
};

/** 检查清单：关键信息与其他信息两组；onFix 为空时不显示「去补充」 */
export const PrecheckList: React.FC<{
  data: BackgroundPrecheck;
  dense?: boolean;
  onFix?: (target: string) => void;
}> = ({ data, dense, onFix }) => {
  const { palette } = useAppTheme();
  const size = dense ? 13 : 14;
  const group = (title: string, note: string, items: PrecheckItem[]) => (
    <div>
      <div style={{ fontSize: 12, marginBottom: 2 }}>
        <span style={{ color: palette.mute, fontWeight: 600 }}>{title}</span>
        <span style={{ color: palette.faint, marginLeft: 8 }}>{note}</span>
      </div>
      {items.map((i, idx) => (
        <div
          key={i.key}
          style={{
            display: 'flex',
            alignItems: 'baseline',
            gap: 10,
            padding: dense ? '7px 0' : '10px 0',
            borderTop: idx > 0 ? `1px solid ${palette.hairline}` : undefined,
            fontSize: size,
          }}
        >
          <span style={{ fontSize: 15 }}>
            <ItemIcon item={i} />
          </span>
          <span
            style={{
              flex: `0 0 ${dense ? 72 : 88}px`,
              color: palette.sub,
              fontWeight: 500,
            }}
          >
            {i.label}
          </span>
          <span
            style={{
              flex: 1,
              minWidth: 0,
              wordBreak: 'break-word',
              color:
                i.state === 'OK'
                  ? palette.ink
                  : i.keyInfo
                    ? palette.sub
                    : palette.mute,
            }}
          >
            {i.state === 'OK' ? i.value : i.reason}
          </span>
          {onFix && i.state !== 'OK' && i.fixTarget && (
            <button
              type="button"
              className="zhul-link"
              style={{ flex: 'none', fontWeight: 600 }}
              onClick={() => onFix(i.fixTarget as string)}
            >
              去补充
            </button>
          )}
        </div>
      ))}
    </div>
  );
  return (
    <div
      style={{ display: 'flex', flexDirection: 'column', gap: dense ? 12 : 16 }}
    >
      {group(
        '关键信息',
        `${data.keyCount} / 3 · 决定充足度`,
        data.items.filter((i) => i.keyInfo),
      )}
      {group(
        '其他信息',
        '建议补充，不影响充足度',
        data.items.filter((i) => !i.keyInfo),
      )}
    </div>
  );
};

/** 点「AI 背调」先弹出的检查窗口（原型 P1–P3）；不拦截 */
export const PrecheckModal: React.FC<{
  open: boolean;
  customerName: string;
  data?: BackgroundPrecheck;
  starting: boolean;
  onFix?: (target: string) => void;
  onCancel: () => void;
  onStart: () => void;
}> = ({ open, customerName, data, starting, onFix, onCancel, onStart }) => {
  const { palette } = useAppTheme();
  const levelColor = useLevelColor();
  if (!data) return null;
  const c = levelColor(data.level);
  return (
    <Modal
      open={open}
      width={620}
      onCancel={onCancel}
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
          背调前检查
          <LevelPill level={data.level} />
        </span>
      }
      footer={[
        <Button key="fix" onClick={onCancel}>
          先去补充
        </Button>,
        <Button
          key="start"
          type="primary"
          loading={starting}
          autoFocus={data.level === 'HIGH'}
          onClick={onStart}
        >
          {data.level === 'LOW' ? '仍要背调' : '直接背调'}
        </Button>,
      ]}
    >
      <div style={{ fontSize: 13, color: palette.mute, marginBottom: 12 }}>
        {customerName} · AI 会拿这些信息去联网搜索
      </div>
      <PrecheckList data={data} onFix={onFix} />
      <div
        style={{
          display: 'flex',
          gap: 8,
          marginTop: 16,
          padding: '10px 14px',
          borderRadius: 10,
          background: c.soft,
          fontSize: 13,
          color: palette.sub,
          lineHeight: 1.6,
        }}
      >
        <BulbOutlined style={{ color: c.color, marginTop: 4 }} />
        <span>{TIPS[data.level]}</span>
      </div>
    </Modal>
  );
};
