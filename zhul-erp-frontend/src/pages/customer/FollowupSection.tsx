import {
  AuditOutlined,
  CheckOutlined,
  ClockCircleOutlined,
  DownOutlined,
  EditOutlined,
  FileTextOutlined,
  MailOutlined,
  MessageOutlined,
  PhoneOutlined,
  RiseOutlined,
  UpOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Checkbox, Segmented, Skeleton, Tooltip } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import {
  DueText,
  Pill,
  SnoozeButton,
  StageTag,
  ValueCell,
} from '@/pages/sales/followup/components';
import {
  JoinDevelopModal,
  StopDevelopModal,
  useDevelopCard,
} from '@/pages/sales/followup/DevelopPanel';
import RecordFollowupModal from '@/pages/sales/followup/RecordFollowupModal';
import {
  type CustomerFollowup,
  type FollowupItem,
  type FollowupRecord,
  followupApi,
  RECORD_STAGE,
  RECORD_USER,
  readBizError,
  type SopCard,
} from '@/pages/sales/followup/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';

const COLLAPSE_KEY = 'zhul:customer:followup:collapsed';
const PAGE = 5;
const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const BOARDS = ['主动开发', '商机成交', '订单履约', '售后评价', '客户经营'];
const FOLLOWUP_PATH = '/sales/followup';

const readCollapsed = () => {
  try {
    return window.localStorage.getItem(COLLAPSE_KEY) === '1';
  } catch {
    return false;
  }
};
const saveCollapsed = (v: boolean) => {
  try {
    window.localStorage.setItem(COLLAPSE_KEY, v ? '1' : '0');
  } catch {
    // 浏览器禁用本地存储时只是不记住
  }
};

/** 客户的跟进概况：详情页的「跟进」分区与右栏两张卡共用 */
export const useCustomerFollowup = (customerId: number, enabled: boolean) => {
  const [data, setData] = useState<CustomerFollowup>();
  const [failed, setFailed] = useState(false);
  const reload = useCallback(() => {
    if (!enabled) return;
    followupApi
      .customerSummary(customerId)
      .then((d) => {
        setData(d);
        setFailed(false);
      })
      .catch(() => setFailed(true));
  }, [customerId, enabled]);
  useEffect(reload, [reload]);
  return { data, failed, reload };
};

const channelIcon = (r: FollowupRecord) => {
  if (r.recordType !== RECORD_USER)
    return r.targetType === 4 ? <AuditOutlined /> : <FileTextOutlined />;
  switch (r.channel) {
    case 1:
      return <MessageOutlined />;
    case 2:
      return <MailOutlined />;
    case 3:
      return <PhoneOutlined />;
    default:
      return <UserOutlined />;
  }
};

const RecordRow: React.FC<{ r: FollowupRecord; last: boolean }> = ({
  r,
  last,
}) => {
  const { palette } = useAppTheme();
  const [raw, setRaw] = useState(false);
  const t = dayjs(r.createTime);
  const user = r.recordType === RECORD_USER;
  /** 业务员手动调整阶段：摘要为「理由：说明」 */
  const adjust = r.recordType === RECORD_STAGE;
  const [reason, ...rest] = adjust ? (r.summary ?? '').split('：') : [];
  const quick = user && r.quick;
  const moved = !adjust && r.stageAfter && r.stageBefore !== r.stageAfter;
  const title = quick
    ? r.summary
    : user
      ? [r.channelName, r.situationLabels.join('、')]
          .filter(Boolean)
          .join(' · ')
      : r.title;
  const summary = quick ? undefined : adjust ? rest.join('：') : r.summary;
  return (
    <div style={{ display: 'flex', gap: 12 }}>
      <div style={{ width: 84, flexShrink: 0, fontSize: 12 }}>
        <div style={{ color: palette.ink, fontWeight: 600 }}>
          {t.format('MM-DD')} {WEEK[t.day()]}
        </div>
        <div style={{ color: palette.mute }}>{t.format('HH:mm')}</div>
      </div>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          flexShrink: 0,
        }}
      >
        <span
          style={{
            width: 24,
            height: 24,
            borderRadius: 12,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 12,
            color: user ? palette.link : palette.mute,
            background: user ? palette.accentSoft : palette.inset,
          }}
        >
          {channelIcon(r)}
        </span>
        {!last && (
          <span style={{ flex: 1, width: 1, background: palette.hairline }} />
        )}
      </div>
      <div style={{ flex: 1, minWidth: 0, paddingBottom: 18 }}>
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <span
            style={{
              fontWeight: user ? 600 : 400,
              color: user ? palette.ink : palette.sub,
            }}
          >
            {title || '跟进'}
          </span>
          {quick && <Pill tone="orange">快速记录</Pill>}
          {adjust && reason && <Pill tone="gray">{reason}</Pill>}
          {moved && (
            <Pill tone="green">
              <RiseOutlined /> {r.stageBefore || '—'} → {r.stageAfter}
            </Pill>
          )}
          {(r.docNos?.length ?? 0) > 1 && (
            <Tooltip title={r.docNos?.join('、')}>
              <span>
                <Pill tone="gray">涉及 {r.docNos?.length} 张单据</Pill>
              </span>
            </Tooltip>
          )}
          <span style={{ flex: 1 }} />
          <span style={{ fontSize: 12, color: palette.mute }}>
            {user || adjust
              ? `${r.operatorName ?? ''} ${adjust ? '调整' : '记录'}`
              : '系统'}
          </span>
        </div>
        {summary && (
          <div style={{ marginTop: 4, color: palette.sub, fontSize: 13 }}>
            {summary}
          </div>
        )}
        {user && (r.blockerName || r.nextAction) && (
          <div
            style={{
              marginTop: 6,
              fontSize: 12,
              color: palette.sub,
              display: 'flex',
              gap: 8,
              flexWrap: 'wrap',
              alignItems: 'center',
            }}
          >
            {r.blockerName && (
              <>
                卡点 <Pill tone="red">{r.blockerName}</Pill>
              </>
            )}
            {r.nextAction && (
              <span>
                {quick &&
                  r.contactDate &&
                  `实际沟通 ${dayjs(r.contactDate).format('MM-DD')} · `}
                下一步 {r.nextAction}
                {r.nextActionDate &&
                  ` · ${dayjs(r.nextActionDate).format('MM-DD')}`}
              </span>
            )}
            {r.nextAction && (
              <Pill tone={r.nextDone ? 'green' : 'orange'}>
                {r.nextDone ? '已完成' : '待完成'}
              </Pill>
            )}
          </div>
        )}
        {r.profileFields.length > 0 && (
          <div style={{ marginTop: 4, fontSize: 12, color: palette.mute }}>
            <UserOutlined /> 写入档案：
            {r.profileFields.map((f) => `${f.label} ${f.value}`).join('；')}
          </div>
        )}
        {!user && r.rawContent && (
          <div style={{ marginTop: 4, color: palette.sub, fontSize: 13 }}>
            {r.rawContent}
          </div>
        )}
        {user && r.rawContent && (
          <div style={{ marginTop: 6 }}>
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              icon={raw ? <UpOutlined /> : <DownOutlined />}
              onClick={() => setRaw((v) => !v)}
            >
              {raw ? '收起原文' : '展开原文'}
              {r.channelName && ` · ${r.channelName}`}
            </Button>
            {raw && (
              <div
                style={{
                  marginTop: 6,
                  padding: 10,
                  borderRadius: 8,
                  background: palette.inset,
                  border: `1px solid ${palette.hairline}`,
                  whiteSpace: 'pre-wrap',
                  fontSize: 12,
                  color: palette.sub,
                  maxHeight: 320,
                  overflow: 'auto',
                }}
              >
                {r.rawContent}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

/** 时间线：全部 / 业务员记录 / 系统事件，可只看有卡点的，先显示 5 条 */
const FollowupTimeline: React.FC<{
  customerId: number;
  first: CustomerFollowup;
  reloadKey: unknown;
}> = ({ customerId, first, reloadKey }) => {
  const { palette } = useAppTheme();
  const [type, setType] = useState<'all' | 'user' | 'system'>('all');
  const [blockerOnly, setBlockerOnly] = useState(false);
  const [records, setRecords] = useState<FollowupRecord[]>(
    first.records.slice(0, PAGE),
  );
  const [total, setTotal] = useState(first.total ?? 0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  const load = useCallback(
    (p: number) => {
      setLoading(true);
      followupApi
        .customerRecords(customerId, {
          type,
          blockerOnly,
          page: p,
          pageSize: PAGE,
        })
        .then((r) => {
          setPage(p);
          setTotal(r.total ?? 0);
          setRecords((old) => (p === 1 ? r.records : [...old, ...r.records]));
        })
        .catch(() => p === 1 && setRecords([]))
        .finally(() => setLoading(false));
    },
    [customerId, type, blockerOnly],
  );

  // reloadKey 变化（刚记了一次跟进）时重新加载第一页
  useEffect(() => {
    if (reloadKey !== undefined) load(1);
  }, [load, reloadKey]);

  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          margin: '16px 0 12px',
        }}
      >
        <Segmented
          size="small"
          value={type}
          onChange={(v) => setType(v as 'all' | 'user' | 'system')}
          options={[
            { value: 'all', label: '全部' },
            { value: 'user', label: '业务员记录' },
            { value: 'system', label: '系统事件' },
          ]}
        />
        <span style={{ flex: 1 }} />
        <Checkbox
          checked={blockerOnly}
          onChange={(e) => setBlockerOnly(e.target.checked)}
        >
          <span style={{ fontSize: 12, color: palette.sub }}>只看有卡点的</span>
        </Checkbox>
      </div>
      {records.length === 0 ? (
        <div style={{ padding: '12px 0', color: palette.mute, fontSize: 13 }}>
          {loading ? '加载中…' : '还没有跟进记录'}
        </div>
      ) : (
        records.map((r, i) => (
          <RecordRow key={r.id} r={r} last={i === records.length - 1} />
        ))
      )}
      {records.length < total && (
        <div style={{ textAlign: 'center' }}>
          <Button type="link" loading={loading} onClick={() => load(page + 1)}>
            加载更早的 {total - records.length} 条
          </Button>
        </div>
      )}
    </>
  );
};

/** 客户详情主栏最上面的「跟进」分区（K9），可收起 / 展开，记住上次的选择 */
export const FollowupSection: React.FC<{
  customerId: number;
  customerName: string;
  canRecord: boolean;
  data?: CustomerFollowup;
  failed: boolean;
  onChanged: () => void;
}> = ({ customerId, customerName, canRecord, data, failed, onChanged }) => {
  const { palette } = useAppTheme();
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [recording, setRecording] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [joining, setJoining] = useState(false);
  /** 主动开发记跟进选了「明确拒绝」：接着打开停止开发 */
  const [refused, setRefused] = useState<FollowupItem>();
  const { message } = App.useApp();
  // 主动开发状态：开发中 / 已停止可重新开发 / 可以加入（第四期 K17）
  const develop = useDevelopCard(
    customerId,
    `${reloadKey}-${data?.current?.id}`,
  );
  const reopen = async () => {
    try {
      await followupApi.reopenDevelop(customerId);
      message.success('已重新开发，阶段重新推导，触达次数保留');
      setReloadKey((k) => k + 1);
      onChanged();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };
  const toggle = () => {
    setCollapsed((v) => {
      saveCollapsed(!v);
      return !v;
    });
  };
  const cur = data?.current;

  const header = (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        flexWrap: 'wrap',
      }}
    >
      <h2
        style={{
          margin: 0,
          fontSize: 16,
          fontWeight: 600,
          color: palette.ink,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <MessageOutlined style={{ color: palette.link }} />
        跟进
      </h2>
      {cur && <StageTag item={cur} />}
      {cur && <ValueCell item={cur} />}
      {collapsed && cur?.nextAction && (
        <span
          style={{
            fontSize: 13,
            color: palette.red,
            minWidth: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            maxWidth: 360,
          }}
        >
          <ClockCircleOutlined /> 下一步：{cur.nextAction}{' '}
          <DueText item={cur} />
        </span>
      )}
      <span style={{ flex: 1 }} />
      {collapsed && data && (
        <span style={{ fontSize: 12, color: palette.mute }}>
          {data.total ?? 0} 条记录
          {data.lastContactAt &&
            ` · 最近 ${dayjs(data.lastContactAt).format('MM-DD')}`}
        </span>
      )}
      {!collapsed && (
        <Button type="link" onClick={() => history.push(FOLLOWUP_PATH)}>
          在客户跟进中查看
        </Button>
      )}
      {canRecord && (
        <Button
          type="primary"
          icon={<EditOutlined />}
          onClick={() => setRecording(true)}
        >
          记一次跟进
        </Button>
      )}
      <Button
        onClick={toggle}
        icon={collapsed ? <DownOutlined /> : <UpOutlined />}
        iconPlacement="end"
      >
        {collapsed ? '展开' : '收起'}
      </Button>
    </div>
  );

  const stat = (label: string, value: React.ReactNode) => (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 12, color: palette.mute }}>{label}</div>
      <div style={{ fontWeight: 600, color: palette.ink, marginTop: 2 }}>
        {value}
      </div>
    </div>
  );

  let body: React.ReactNode = null;
  if (!collapsed) {
    if (failed) {
      body = (
        <div style={{ marginTop: 12, color: palette.mute }}>
          跟进记录加载失败，请刷新重试
        </div>
      );
    } else if (!data) {
      body = <Skeleton active style={{ marginTop: 12 }} />;
    } else {
      body = (
        <>
          {cur?.nextAction && (
            <div
              style={{
                marginTop: 14,
                padding: '10px 14px',
                borderRadius: 10,
                background: palette.redSoft,
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                flexWrap: 'wrap',
              }}
            >
              <ClockCircleOutlined style={{ color: palette.red }} />
              <b style={{ color: palette.red }}>下一步</b>
              <span style={{ color: palette.ink, flex: 1, minWidth: 0 }}>
                {cur.nextAction}
              </span>
              <DueText item={cur} />
              {canRecord && (
                <>
                  <SnoozeButton item={cur} onDone={onChanged} />
                  <Button
                    size="small"
                    icon={<CheckOutlined />}
                    onClick={() => setRecording(true)}
                  >
                    已完成，记一次
                  </Button>
                </>
              )}
            </div>
          )}
          {develop && develop.state !== 'none' && (
            <div
              style={{
                marginTop: 14,
                padding: '10px 14px',
                borderRadius: 10,
                background: palette.violetSoft,
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                flexWrap: 'wrap',
                fontSize: 13,
              }}
            >
              <b style={{ color: palette.violet }}>主动开发</b>
              <span style={{ color: palette.ink, flex: 1, minWidth: 0 }}>
                {develop.state === 'open'
                  ? `${develop.stageCode} ${develop.stageName} · 已触达 ${develop.touchCount ?? 0} 次${develop.prospectReason ? ` · ${develop.prospectReason}` : ''}`
                  : develop.state === 'stopped'
                    ? `已停止开发（${develop.stopReasonName || '—'}${develop.stopNote ? `：${develop.stopNote}` : ''}），触达 ${develop.touchCount ?? 0} 次`
                    : '还没成交、也没有进行中的询盘和单据，可以加入主动开发'}
              </span>
              {develop.state === 'open' && (
                <Button
                  size="small"
                  onClick={() => history.push(FOLLOWUP_PATH)}
                >
                  去开发
                </Button>
              )}
              {develop.state === 'stopped' && canRecord && (
                <Button size="small" onClick={reopen}>
                  重新开发
                </Button>
              )}
              {develop.state === 'eligible' && canRecord && (
                <Button
                  size="small"
                  type="primary"
                  onClick={() => setJoining(true)}
                >
                  加入主动开发
                </Button>
              )}
            </div>
          )}
          <div
            style={{
              marginTop: 14,
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
              gap: 12,
            }}
          >
            {stat(
              '最近联系',
              data.lastContactAt
                ? `${dayjs(data.lastContactAt).format('MM-DD HH:mm')}${data.lastChannelName ? ` · ${data.lastChannelName}` : ''}`
                : '—',
            )}
            {stat('近 30 天跟进', `${data.recent30 ?? 0} 次`)}
            {stat('连续未回复', `${data.noReplyStreak ?? 0} 次`)}
            {stat(
              '报价 / PI',
              `${data.quoteCount ?? 0} 张报价 · ${data.piCount ?? 0} 张 PI${
                data.openAmounts.length
                  ? ` · ${data.openAmounts.map((a) => formatAmount(a.amount, a.currencyCode)).join('、')}`
                  : ''
              }`,
            )}
          </div>
          <FollowupTimeline
            customerId={customerId}
            first={data}
            reloadKey={reloadKey}
          />
        </>
      );
    }
  }

  return (
    <section
      aria-label="跟进"
      style={{
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        borderRadius: 16,
        padding: collapsed ? '14px 24px' : 24,
        marginBottom: 20,
      }}
    >
      {header}
      {body}
      <RecordFollowupModal
        open={recording}
        target={{ customerId, customerName, item: cur }}
        onClose={() => setRecording(false)}
        onDone={(_, _newInquiry, refusedItem) => {
          setRecording(false);
          setReloadKey((k) => k + 1);
          onChanged();
          if (refusedItem) setRefused(refusedItem);
        }}
      />
      <StopDevelopModal
        item={refused}
        preset="REFUSED"
        onClose={() => setRefused(undefined)}
        onDone={() => {
          setRefused(undefined);
          setReloadKey((k) => k + 1);
          onChanged();
        }}
      />
      <JoinDevelopModal
        customerId={joining ? customerId : undefined}
        customerName={customerName}
        onClose={() => setJoining(false)}
        onDone={() => {
          setJoining(false);
          setReloadKey((k) => k + 1);
          onChanged();
        }}
      />
    </section>
  );
};

const SideCard: React.FC<{ title: string; children: React.ReactNode }> = ({
  title,
  children,
}) => {
  const { palette } = useAppTheme();
  return (
    <section
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        padding: 20,
        borderRadius: 16,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <h2
        style={{ margin: 0, fontSize: 14, fontWeight: 600, color: palette.ink }}
      >
        {title}
      </h2>
      {children}
    </section>
  );
};

/** 右栏「这位客户的跟进链路」：五个板块第几步；同时在多个板块（如一张订单履约、一张售后）时逐个标出 */
export const FollowupChainCard: React.FC<{ data?: CustomerFollowup }> = ({
  data,
}) => {
  const { palette } = useAppTheme();
  const step = data?.step ?? 0;
  const cur = data?.current;
  const inBoards = data?.boards?.length ? data.boards : step ? [step] : [];
  const first = inBoards.length ? Math.min(...inBoards) : 0;
  return (
    <SideCard title="这位客户的跟进链路">
      {BOARDS.map((name, i) => {
        const n = i + 1;
        const active = inBoards.includes(n);
        const done = first > 0 && n < first;
        return (
          <div key={name} style={{ display: 'flex', gap: 10 }}>
            <span
              style={{
                width: 22,
                height: 22,
                borderRadius: 11,
                flexShrink: 0,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 11,
                fontWeight: 700,
                color: active ? '#fff' : done ? palette.green : palette.mute,
                background: active
                  ? palette.link
                  : done
                    ? palette.greenSoft
                    : palette.inset,
              }}
            >
              {done ? <CheckOutlined /> : n}
            </span>
            <div>
              <div
                style={{
                  fontWeight: active ? 600 : 400,
                  color: active ? palette.ink : palette.sub,
                }}
              >
                第 {n} 步 · {name}
              </div>
              {active && (
                <div style={{ fontSize: 12, color: palette.link }}>
                  进行中
                  {cur?.board === n && ` · ${cur.stageCode} ${cur.stageName}`}
                </div>
              )}
              {done && (
                <div style={{ fontSize: 12, color: palette.green }}>已完成</div>
              )}
            </div>
          </div>
        );
      })}
    </SideCard>
  );
};

/** 右栏「SOP 本阶段要拿到」：清单只在本页面临时勾选 */
export const SopStageCard: React.FC<{ data?: CustomerFollowup }> = ({
  data,
}) => {
  const { palette } = useAppTheme();
  const stage = data?.current?.stageCode;
  const board = data?.current?.board;
  const [sop, setSop] = useState<SopCard>();
  const [checked, setChecked] = useState<number[]>([]);
  useEffect(() => {
    setSop(undefined);
    setChecked([]);
    if (!stage || !board) return;
    followupApi
      .sop(board, stage)
      .then(setSop)
      .catch(() => setSop(undefined));
  }, [board, stage]);
  if (!stage || !sop || sop.checklist.length === 0) return null;
  return (
    <SideCard title="SOP 本阶段要拿到">
      <div style={{ display: 'grid', gap: 6 }}>
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
            <span style={{ fontSize: 13, color: palette.sub }}>{c}</span>
          </Checkbox>
        ))}
      </div>
      <button
        type="button"
        className="zhul-link"
        onClick={() => history.push(FOLLOWUP_PATH)}
        style={{ fontSize: 13 }}
      >
        打开作战卡（话术、常见卡点处理）
      </button>
    </SideCard>
  );
};
