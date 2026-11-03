import {
  CheckCircleOutlined,
  StopOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { App, Button, Checkbox } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { readBizError } from '../service';
import { backgroundApi, type Suggestion } from './service';

const CIRCLED = ['①', '②', '③', '④', '⑤'];

/** 能勾选：待处理且能采纳 */
const selectable = (s: Suggestion) =>
  s.state === 'pending' && !s.unavailableReason;

/**
 * 字段建议（原型 C8 右下）：当前为空的默认勾选，已有值的不勾（勾上会覆盖原值）；
 * 选项不存在等不能采纳的只显示原因。采纳后来源记为这份报告。
 * 档案补全第②步复用（compact：只列待处理的，不显示统计）。
 */
const Suggestions: React.FC<{
  customerId: number;
  reportId: number;
  suggestions: Suggestion[];
  canEdit: boolean;
  compact?: boolean;
  onChanged: () => void;
}> = ({ customerId, reportId, suggestions, canEdit, compact, onChanged }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [checked, setChecked] = useState<number[]>([]);
  const [busy, setBusy] = useState<'adopt' | 'ignore'>();

  useEffect(() => {
    setChecked(
      suggestions.flatMap((s, i) =>
        selectable(s) && s.defaultChecked ? [i] : [],
      ),
    );
  }, [suggestions]);

  const items = useMemo(
    () =>
      suggestions
        .map((s, i) => ({ s, i }))
        .filter(({ s }) => !compact || s.state === 'pending'),
    [suggestions, compact],
  );
  const adopted = suggestions.filter((s) => s.state === 'adopted').length;
  const unavailable = suggestions.filter(
    (s) => s.state === 'pending' && s.unavailableReason,
  ).length;
  const pending = suggestions.filter(selectable).length;
  const unchecked = suggestions.flatMap((s, i) =>
    s.state === 'pending' && !checked.includes(i) ? [i] : [],
  );

  const run = async (kind: 'adopt' | 'ignore', indexes: number[]) => {
    setBusy(kind);
    try {
      await backgroundApi[kind](customerId, reportId, indexes);
      message.success(
        kind === 'adopt'
          ? `已采纳 ${indexes.length} 条，写入客户档案`
          : `已忽略 ${indexes.length} 条`,
      );
      onChanged();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(undefined);
    }
  };

  const pill = (text: string, fg: string, bg: string) => (
    <span
      style={{
        padding: '1px 8px',
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 600,
        color: fg,
        background: bg,
      }}
    >
      {text}
    </span>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {!compact && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
          }}
        >
          <UnorderedListOutlined style={{ color: palette.link }} />
          <span style={{ fontSize: 14, fontWeight: 600, color: palette.ink }}>
            字段建议 {suggestions.length} 条
          </span>
          {adopted > 0 &&
            pill(`已采纳 ${adopted}`, palette.green, palette.greenSoft)}
          {pending > 0 &&
            pill(`待处理 ${pending}`, palette.orange, palette.orangeSoft)}
          {unavailable > 0 &&
            pill(`无法采纳 ${unavailable}`, palette.sub, palette.inset)}
        </div>
      )}
      {items.length === 0 && (
        <div style={{ fontSize: 13, color: palette.mute, padding: '8px 0' }}>
          {compact ? '这份报告的建议都处理过了。' : '这份报告没有字段建议。'}
        </div>
      )}
      {items.map(({ s, i }) => {
        const canPick = canEdit && selectable(s);
        const on = checked.includes(i);
        return (
          <div
            key={i}
            style={{
              display: 'flex',
              gap: 12,
              padding: '12px 14px',
              borderRadius: 12,
              background: palette.inset,
              border: `1px solid ${on ? palette.accentLine : 'transparent'}`,
              opacity: s.state === 'ignored' ? 0.6 : 1,
            }}
          >
            <span style={{ paddingTop: 2 }}>
              {s.state === 'adopted' ? (
                <CheckCircleOutlined style={{ color: palette.green }} />
              ) : s.unavailableReason || s.state === 'ignored' ? (
                <StopOutlined style={{ color: palette.mute }} />
              ) : (
                <Checkbox
                  checked={on}
                  disabled={!canPick}
                  onChange={(e) =>
                    setChecked(
                      e.target.checked
                        ? [...checked, i]
                        : checked.filter((x) => x !== i),
                    )
                  }
                />
              )}
            </span>
            <span
              style={{
                flex: 1,
                minWidth: 0,
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
              }}
            >
              <span
                style={{ fontSize: 13, fontWeight: 600, color: palette.ink }}
              >
                <span style={{ color: palette.mute, marginRight: 6 }}>
                  {CIRCLED[(s.module ?? 1) - 1]}
                </span>
                {s.label ?? s.field}
                {s.contact?.name && (
                  <span style={{ color: palette.sub, fontWeight: 400 }}>
                    {' '}
                    · {s.contact.name}
                  </span>
                )}
              </span>
              <span
                style={{
                  fontSize: 14,
                  color: palette.ink,
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 6,
                  alignItems: 'baseline',
                }}
              >
                {s.state !== 'adopted' && (
                  <>
                    <span style={{ color: palette.mute }}>
                      {s.currentValue || '空'}
                    </span>
                    <span style={{ color: palette.mute }}>→</span>
                  </>
                )}
                <b style={{ wordBreak: 'break-word' }}>{s.value}</b>
                {s.state === 'pending' &&
                  !s.unavailableReason &&
                  s.currentValue && (
                    <span style={{ fontSize: 12, color: palette.orange }}>
                      会覆盖原值
                    </span>
                  )}
              </span>
              <span
                style={{ fontSize: 12, color: palette.mute, lineHeight: 1.6 }}
              >
                {s.unavailableReason ? (
                  s.unavailableReason
                ) : (
                  <>
                    {s.state === 'adopted' || s.state === 'ignored'
                      ? [
                          s.evidence,
                          `${s.operatorName ?? ''} ${formatDateTime(s.at).slice(5, 10)} ${s.state === 'adopted' ? '采纳' : '忽略'}`.trim(),
                        ]
                          .filter(Boolean)
                          .join(' · ')
                      : s.evidence}
                    {s.sourceUrl && (
                      <a
                        href={s.sourceUrl}
                        target="_blank"
                        rel="noreferrer"
                        style={{ marginLeft: 6 }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        来源
                      </a>
                    )}
                  </>
                )}
              </span>
            </span>
          </div>
        );
      })}
      {canEdit && pending + unavailable > 0 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            marginTop: 4,
            flexWrap: 'wrap',
          }}
        >
          <span style={{ fontSize: 13, color: palette.mute }}>
            已勾选 {checked.length} 条
          </span>
          <span style={{ flex: 1 }} />
          {!compact && unchecked.length > 0 && (
            <Button
              loading={busy === 'ignore'}
              disabled={!!busy}
              onClick={() => run('ignore', unchecked)}
            >
              忽略未勾选
            </Button>
          )}
          {pending > 0 && (
            <Button
              type="primary"
              loading={busy === 'adopt'}
              disabled={!!busy || checked.length === 0}
              onClick={() => run('adopt', checked)}
            >
              采纳 {checked.length} 条
            </Button>
          )}
        </div>
      )}
    </div>
  );
};

export default Suggestions;
