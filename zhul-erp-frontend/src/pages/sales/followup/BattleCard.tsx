import { CopyOutlined, RobotOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Alert, App, Button, Checkbox, Input, Skeleton } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { AccountPanel } from './AccountPanel';
import { Card, fmtDate } from './components';
import {
  BOARD_ACCOUNT,
  BOARD_AFTER_SALES,
  BOARD_FULFIL,
  type FollowupItem,
  followupApi,
  GROUP,
  INTENT,
  readBizError,
  type SopCard,
} from './service';

/** 履约异常位 → 作战卡里对应的应对（SOP 卡点编码） */
const EXCEPTION_BLOCKER: [number, string][] = [
  [1, 'SUPPLY_DELAY'],
  [2, 'DISCREPANCY'],
  [4, 'LOGISTICS'],
];

/** 这一行该看哪条应对：手选的卡点 → 履约异常 → A6 邀请评价 */
const blockerFor = (item: FollowupItem) => {
  if (item.blockerCode) return item.blockerCode;
  const hit = EXCEPTION_BLOCKER.find(
    ([bit]) => (item.exceptionFlags ?? 0) & bit,
  );
  if (hit) return hit[1];
  return item.stageCode === 'A6' ? 'REVIEW_INVITE' : undefined;
};

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

/** 作战卡：跟随选中行显示所在阶段的 SOP（K2、K3 右栏；客户经营见 K14） */
const BattleCard: React.FC<{
  item?: FollowupItem;
  canRecord?: boolean;
  onNewInquiry?: (customerId: number) => void;
  onChanged?: () => void;
}> = ({ item, canRecord = false, onNewInquiry, onChanged }) => {
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
  const [shipmentId, setShipmentId] = useState<number>();

  const stage = item?.stageCode;
  const board = item?.board;
  useEffect(() => {
    if (!stage || !board) return;
    let alive = true;
    setSop(undefined);
    setFailed(false);
    followupApi
      .sop(board, stage)
      .then((c) => alive && setSop(c))
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
    };
  }, [board, stage]);

  // 订单履约「查看出运单」：取最后一批出运单
  const itemId = item?.id;
  const fulfil = board === BOARD_FULFIL;
  useEffect(() => {
    setShipmentId(undefined);
    if (!itemId || !fulfil) return;
    let alive = true;
    followupApi
      .parts(itemId)
      .then(
        (p) =>
          alive && setShipmentId(p.batches[p.batches.length - 1]?.shipmentId),
      )
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [itemId, fulfil]);

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
  const blockerCode = blockerFor(item);
  const blocker = sop.blockers.find((b) => b.code === blockerCode);
  const account = item.board === BOARD_ACCOUNT;
  const sales =
    item.board !== BOARD_FULFIL && item.board !== BOARD_AFTER_SALES && !account;
  const intent =
    item.board === BOARD_FULFIL
      ? INTENT.LOGISTICS
      : item.stageCode === 'A6'
        ? INTENT.REVIEW
        : account
          ? INTENT.ACCOUNT
          : undefined;
  const writeLabel =
    intent === INTENT.LOGISTICS
      ? 'AI 写一条物流更新'
      : intent === INTENT.REVIEW
        ? 'AI 写一条评价邀请'
        : intent === INTENT.ACCOUNT
          ? 'AI 写触达消息'
          : isPi
            ? 'AI 写一条催款消息'
            : 'AI 写一条英文回复';
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
        intent,
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
        {item.docNo && !account && ` · ${item.docNo}`}
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

      {account && (
        <AccountPanel
          item={item}
          canRecord={canRecord}
          onNewInquiry={(id) => onNewInquiry?.(id)}
          onChanged={() => onChanged?.()}
        />
      )}

      {!account && (
        <Title>
          {blocker
            ? `${sales ? '卡点' : ''}「${blocker.name}」怎么应对`
            : item.board === BOARD_FULFIL
              ? '常见异常怎么应对'
              : item.board === BOARD_AFTER_SALES
                ? '常见问题怎么应对'
                : '常见卡点怎么应对'}
        </Title>
      )}
      {account ? null : blocker ? (
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
          {replying ? 'AI 正在写，最多 30 秒…' : writeLabel}
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
          <Title>{sales ? '推进条件' : '下一阶段（系统自动）'}</Title>
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

      {shipmentId && (
        <Button
          type="link"
          style={{ paddingLeft: 0, marginTop: 8, display: 'block' }}
          onClick={() => history.push(`/logistics/shipments/${shipmentId}`)}
        >
          查看出运单 ›
        </Button>
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
