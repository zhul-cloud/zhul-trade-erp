import { CopyOutlined, RobotOutlined } from '@ant-design/icons';
import { Alert, App, Button, Checkbox, Input, Skeleton } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { Card, fmtDate } from './components';
import {
  type FollowupItem,
  followupApi,
  GROUP,
  readBizError,
  type SopCard,
} from './service';

const Title: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        fontSize: 13,
        fontWeight: 600,
        color: palette.ink,
        margin: '14px 0 6px',
      }}
    >
      {children}
    </div>
  );
};

/** 作战卡：跟随选中行显示所在阶段的 SOP（K2、K3 右栏） */
const BattleCard: React.FC<{ item?: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [sop, setSop] = useState<SopCard>();
  const [failed, setFailed] = useState(false);
  const [checked, setChecked] = useState<number[]>([]);
  const [showDialog, setShowDialog] = useState(false);
  const [note, setNote] = useState('');
  const [reply, setReply] = useState<string>();
  const [replying, setReplying] = useState(false);
  const [replyError, setReplyError] = useState<string>();

  const stage = item?.stageCode;
  useEffect(() => {
    if (!stage) return;
    setSop(undefined);
    setFailed(false);
    followupApi
      .sop(stage)
      .then(setSop)
      .catch(() => setFailed(true));
  }, [stage]);

  // 换一行：勾选、AI 回复都清空（勾选只在本页面临时有效）
  useEffect(() => {
    setChecked([]);
    setShowDialog(false);
    setReply(undefined);
    setReplyError(undefined);
    setNote('');
  }, [item?.id]);

  if (!item)
    return (
      <Card style={{ padding: 20 }}>
        <span style={{ color: palette.mute }}>选中一行查看作战卡</span>
      </Card>
    );
  if (failed)
    return (
      <Card style={{ padding: 20 }}>
        <span style={{ color: palette.mute }}>作战卡加载失败，请刷新重试</span>
      </Card>
    );
  if (!sop)
    return (
      <Card style={{ padding: 20 }}>
        <Skeleton active />
      </Card>
    );

  const isPi = item.stageGroup === GROUP.AFTER_PI;
  const blocker = sop.blockers.find((b) => b.code === item.blockerCode);
  const payOverdue =
    isPi &&
    item.expectedPayDate &&
    dayjs(item.expectedPayDate).isBefore(dayjs(), 'day') &&
    Number(item.unpaidAmount ?? 0) > 0;

  const writeReply = async () => {
    setReplying(true);
    setReplyError(undefined);
    try {
      const r = await followupApi.replyDraft({
        customerId: item.customerId,
        itemId: item.id,
        blockerCode: item.blockerCode || undefined,
        note: note.trim() || undefined,
      });
      setReply(r.reply);
    } catch (e) {
      setReplyError(readBizError(e).message);
    } finally {
      setReplying(false);
    }
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(reply ?? '');
      message.success('已复制');
    } catch {
      message.warning('复制失败，请手动选中复制');
    }
  };

  return (
    <Card style={{ padding: 20 }}>
      <div style={{ fontWeight: 700, fontSize: 16, color: palette.ink }}>
        作战卡 · {sop.stageCode} {sop.name}
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
        {item.customerName}
        {item.docNo && ` · ${item.docNo}`}
      </div>

      {payOverdue && (
        <div
          style={{
            marginTop: 12,
            padding: '10px 12px',
            borderRadius: 10,
            background: palette.redSoft,
            color: palette.red,
            fontSize: 13,
          }}
        >
          客户说 {fmtDate(item.expectedPayDate)} 付款，已过{' '}
          {dayjs().diff(dayjs(item.expectedPayDate), 'day')} 天，还有{' '}
          {formatAmount(item.unpaidAmount, item.currencyCode)} 未收
        </div>
      )}

      {sop.goal && (
        <>
          <Title>这一步的目标</Title>
          <div style={{ color: palette.sub, fontSize: 13 }}>{sop.goal}</div>
        </>
      )}

      {sop.checklist.length > 0 && (
        <>
          <Title>该做的事</Title>
          <div style={{ display: 'grid', gap: 4 }}>
            {sop.checklist.map((c, i) => (
              <Checkbox
                key={c}
                checked={checked.includes(i)}
                onChange={(e) =>
                  setChecked((l) =>
                    e.target.checked ? [...l, i] : l.filter((x) => x !== i),
                  )
                }
              >
                <span
                  style={{
                    color: checked.includes(i) ? palette.mute : palette.sub,
                    textDecoration: checked.includes(i)
                      ? 'line-through'
                      : undefined,
                  }}
                >
                  {c}
                </span>
              </Checkbox>
            ))}
          </div>
        </>
      )}

      <Title>
        {blocker ? `卡点「${blocker.name}」怎么应对` : '常见卡点怎么应对'}
      </Title>
      {blocker ? (
        <ol style={{ margin: 0, paddingLeft: 18, color: palette.sub }}>
          {blocker.playbook.map((p) => (
            <li key={p} style={{ fontSize: 13, marginBottom: 2 }}>
              {p}
            </li>
          ))}
        </ol>
      ) : (
        <div style={{ display: 'grid', gap: 6 }}>
          {sop.blockers.slice(0, 3).map((b) => (
            <div key={b.code} style={{ fontSize: 13, color: palette.sub }}>
              <b style={{ color: palette.ink }}>{b.name}：</b>
              {b.playbook[0]}
            </div>
          ))}
        </div>
      )}

      <div
        style={{
          marginTop: 12,
          padding: 12,
          borderRadius: 10,
          background: palette.inset,
        }}
      >
        <Input.TextArea
          autoSize={{ minRows: 1, maxRows: 3 }}
          maxLength={500}
          value={note}
          placeholder="想对客户说的意思（选填，中文即可）"
          onChange={(e) => setNote(e.target.value)}
        />
        <Button
          style={{ marginTop: 8 }}
          icon={<RobotOutlined />}
          loading={replying}
          onClick={writeReply}
          block
        >
          {replying
            ? 'AI 正在写，最多 30 秒…'
            : isPi
              ? 'AI 写一条催款消息'
              : 'AI 写一条英文回复'}
        </Button>
        {replyError && (
          <Alert
            style={{ marginTop: 8 }}
            type="warning"
            showIcon
            title={replyError}
          />
        )}
        {reply && (
          <div style={{ marginTop: 8 }}>
            <div
              style={{
                whiteSpace: 'pre-wrap',
                fontSize: 13,
                color: palette.ink,
                background: palette.card,
                borderRadius: 8,
                padding: 10,
                border: `1px solid ${palette.hairline}`,
              }}
            >
              {reply}
            </div>
            <Button
              size="small"
              type="link"
              icon={<CopyOutlined />}
              onClick={copy}
              style={{ paddingLeft: 0 }}
            >
              复制
            </Button>
          </div>
        )}
      </div>

      {(sop.gateText || sop.autoHint) && (
        <>
          <Title>推进条件</Title>
          {sop.gateText && (
            <div style={{ fontSize: 13, color: palette.sub }}>
              {sop.gateText}
            </div>
          )}
          {sop.autoHint && (
            <div style={{ fontSize: 12, color: palette.green, marginTop: 4 }}>
              {sop.autoHint}
            </div>
          )}
        </>
      )}

      {sop.exampleDialog && (
        <>
          <Button
            type="link"
            style={{ paddingLeft: 0, marginTop: 8 }}
            onClick={() => setShowDialog((v) => !v)}
          >
            {showDialog ? '收起示例对话' : '展开示例对话'}
          </Button>
          {showDialog && (
            <div
              style={{
                whiteSpace: 'pre-wrap',
                fontSize: 12,
                color: palette.sub,
                background: palette.inset,
                borderRadius: 8,
                padding: 10,
              }}
            >
              {sop.exampleDialog}
            </div>
          )}
        </>
      )}
    </Card>
  );
};

export default BattleCard;
