import {
  AlertOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  EditOutlined,
  FileTextOutlined,
  HistoryOutlined,
  MessageOutlined,
  PauseCircleOutlined,
  RollbackOutlined,
  SendOutlined,
  ShoppingCartOutlined,
  StopOutlined,
  SwapOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import { App, Button, Checkbox, Input, Skeleton } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { RelatedDocsCard } from '@/components/DocFields/DocPage';
import { listHref } from '@/components/DocFields/SourceNos';
import { usePageTitle } from '@/components/PageTabs/hooks';
import ExpenseQuickModal from '@/pages/finance/expenses/AfterSalesExpense';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { AttachmentWall, ReasonModal } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { Card, PATHS, Pill, SalesPageTitle } from '../components';
import { CreateReturnModal } from '../returns/dialogs';
import { readBizError } from '../service';
import {
  CloseModal,
  PlanModal,
  RefundRequestModal,
  ReplacementModal,
} from './dialogs';
import {
  type AfterSales,
  AS_STATUS,
  AS_STATUS_TONE,
  afterSalesApi,
  HANDLING,
  LEVEL_TONE,
  levelShort,
  PR_STATUS_TONE,
  RQ_STATUS_TONE,
  RT_STATUS_TONE,
  TH_STATUS_TONE,
  type Tone,
} from './service';
import { dueLeftText, SLA_TEXT, WORK_HOURS_TEXT } from './sla';

const LOG_TONE: Record<string, Tone> = {
  NOTE: 'accent',
  LEVEL: 'orange',
  STATUS: 'green',
  PLAN: 'violet',
  RETURN: 'cyan',
  REFUND: 'red',
  EXPENSE: 'gray',
  CREATE: 'gray',
  REPLACEMENT: 'violet',
};
type Dialog =
  | 'plan'
  | 'close'
  | 'returnOnly'
  | 'returnRefund'
  | 'refund'
  | 'expense'
  | 'cancel'
  | 'replacement';

/** 售后单详情（spec sales/after-sales） */
const AfterSalesDetail: React.FC = () => {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const [a, setA] = useState<AfterSales>();
  usePageTitle(a ? `售后单 ${a.asNo}` : undefined, a?.asNo);
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<Dialog>();
  const [note, setNote] = useState('');
  const [noteFiles, setNoteFiles] = useState<Attachment[]>([]);
  const [notified, setNotified] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setA(await afterSalesApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!a) return <Skeleton active paragraph={{ rows: 12 }} />;

  const active = a.status === AS_STATUS.OPEN || a.status === AS_STATUS.WAITING;
  const done = (next: AfterSales) => {
    setA(next);
    setDialog(undefined);
  };
  const reload = () => {
    setDialog(undefined);
    load();
  };

  const kv = (k: string, v: React.ReactNode) => (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '90px 1fr',
        gap: 12,
        fontSize: 13,
        marginBottom: 8,
      }}
    >
      <span style={{ color: palette.mute }}>{k}</span>
      <span style={{ color: palette.ink }}>{v || '—'}</span>
    </div>
  );
  const section = (
    icon: React.ReactNode,
    title: string,
    extra?: React.ReactNode,
  ) => (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        marginBottom: 12,
      }}
    >
      <span style={{ color: palette.link }}>{icon}</span>
      <b style={{ color: palette.ink }}>{title}</b>
      <span style={{ marginLeft: 'auto' }}>{extra}</span>
    </div>
  );

  const saveNote = async () => {
    if (!note.trim()) {
      message.error('请填写跟进内容');
      return;
    }
    setSaving(true);
    try {
      setA(
        await afterSalesApi.note(
          a.id,
          note.trim(),
          noteFiles.map((f) => f.id),
          notified,
        ),
      );
      setNote('');
      setNoteFiles([]);
      setNotified(false);
      message.success(
        notified ? '已添加跟进，重新计算下次更新截止' : '已添加跟进',
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const writeNote = () => {
    setNotified(true);
    document.getElementById('after-sales-note')?.focus();
  };
  const bar = (
    tone: 'red' | 'orange' | 'accent' | 'gray' | 'green',
    icon: React.ReactNode,
    title: string,
    desc: string,
    action?: boolean,
  ) => {
    const fg = {
      red: palette.red,
      orange: palette.orange,
      accent: palette.link,
      gray: palette.sub,
      green: palette.green,
    }[tone];
    const bg = {
      red: palette.redSoft,
      orange: palette.orangeSoft,
      accent: palette.accentSoft,
      gray: palette.inset,
      green: palette.greenSoft,
    }[tone];
    return (
      <div
        role="status"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          padding: '12px 16px',
          borderRadius: 14,
          background: bg,
          border: `1px solid ${tone === 'gray' ? palette.hairline : fg}`,
          marginBottom: 16,
        }}
      >
        <span style={{ fontSize: 20, color: fg }}>{icon}</span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 15, color: fg }}>
            {title}
          </div>
          <div style={{ fontSize: 12, color: palette.sub }}>{desc}</div>
        </div>
        {action && (
          <Button type="primary" icon={<MessageOutlined />} onClick={writeNote}>
            写跟进并告知客户
          </Button>
        )}
      </div>
    );
  };
  const dueTime = a.dueAt ? dayjs(a.dueAt).format('MM-DD HH:mm') : '';
  const levelText = levelShort(a.levelCode, a.levelName);
  const slaBar = (() => {
    if (a.status === AS_STATUS.CLOSED) {
      return bar(
        'green',
        <CheckCircleOutlined />,
        '已结案 · 不再提醒',
        a.respondedAt
          ? `首次响应 ${formatDateTime(a.respondedAt).slice(5, 16)}`
          : '没有记录「已告知客户」',
      );
    }
    if (a.status === AS_STATUS.CANCELLED) return null;
    if (!a.dueAt) {
      return a.status === AS_STATUS.WAITING
        ? bar(
            'gray',
            <PauseCircleOutlined />,
            '待客户确认 · 暂停计时',
            '等客户回复期间不按等级计时；填了下次更新时间时按它提醒。改回「处理中」后从那一刻重新起算。',
          )
        : null;
    }
    const late = a.dueState === 'OVERDUE';
    const soon = a.dueState === 'SOON';
    const tone = late ? 'red' : soon ? 'orange' : 'accent';
    const icon = late ? <AlertOutlined /> : <ClockCircleOutlined />;
    const left = dueLeftText(a);
    if (a.dueType === 1) {
      return bar(
        tone,
        icon,
        `首次响应${late || soon ? left : `截止 ${dueTime}`}`,
        `截止 ${dueTime}（${levelText}，从收到反馈 ${formatDateTime(a.receivedAt).slice(5, 16)} 起算）。写跟进并勾选「已告知客户」即完成首次响应。`,
        late || soon,
      );
    }
    if (a.dueType === 2) {
      return bar(
        tone,
        icon,
        `更新客户${late || soon ? left : `截止 ${dueTime}`}`,
        `截止 ${dueTime}（${levelText}，从${a.status === AS_STATUS.OPEN && a.lastContactAt ? `上次告知客户 ${formatDateTime(a.lastContactAt).slice(5, 16)}` : '改回处理中'} 起算）。${a.nextAction ? `下一步：${a.nextAction}` : ''}`,
        late || soon,
      );
    }
    return bar(
      tone,
      icon,
      `下次更新客户${late || soon ? left : `：${dueTime}`}（手填时间）`,
      a.levelDueAt
        ? `负责人填的下次更新时间早于等级截止 ${dayjs(a.levelDueAt).format('MM-DD HH:mm')}，以手填为准。`
        : `截止 ${dueTime}，按负责人填的下次更新时间提醒。`,
      late || soon,
    );
  })();

  const problem = (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      {section(<WarningOutlined />, '问题')}
      {kv(
        '型号',
        (a.items ?? [])
          .map(
            (i) =>
              `${i.model} × ${i.quantity}${i.brand ? `（${i.brand}）` : ''}`,
          )
          .join('、'),
      )}
      {kv('现象', a.symptom)}
      {kv('客户诉求', a.customerRequest)}
      {kv('收到时间', formatDateTime(a.receivedAt).slice(0, 16))}
      {(a.attachments ?? []).length > 0 && (
        <div style={{ marginTop: 8 }}>
          <AttachmentWall ownerType="AFTER_SALES" value={a.attachments ?? []} />
        </div>
      )}
      {a.status === AS_STATUS.CLOSED &&
        kv('处理结果', <b style={{ color: palette.green }}>{a.result}</b>)}
      {a.status === AS_STATUS.CLOSED &&
        !!a.caseNote &&
        kv('案例沉淀', a.caseNote)}
      {a.status === AS_STATUS.CANCELLED && kv('撤销原因', a.cancelReason)}
    </Card>
  );

  const timeline = (
    <Card style={{ padding: 20 }}>
      {section(<HistoryOutlined />, '跟进记录')}
      {a.status !== AS_STATUS.CANCELLED && (
        <div
          style={{
            padding: 12,
            borderRadius: 12,
            background: palette.inset,
            marginBottom: 14,
          }}
        >
          <Input.TextArea
            id="after-sales-note"
            rows={2}
            maxLength={1000}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="写下这次跟进的内容、客户回复或供应商结论…"
            aria-label="跟进内容"
          />
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 8,
              marginTop: 8,
            }}
          >
            <div style={{ flex: 1 }}>
              <AttachmentWall
                ownerType="AFTER_SALES_LOG"
                value={noteFiles}
                onChange={setNoteFiles}
                max={10}
                empty=""
                accept="image/*,video/mp4,video/quicktime,application/pdf"
              />
            </div>
            {active && (
              <Checkbox
                checked={notified}
                onChange={(e) => setNotified(e.target.checked)}
                style={{ marginTop: 4 }}
              >
                已告知客户
              </Checkbox>
            )}
            <Button type="primary" loading={saving} onClick={saveNote}>
              保存跟进
            </Button>
          </div>
        </div>
      )}
      <div style={{ display: 'grid', gap: 12 }}>
        {(a.logs ?? []).map((l) => (
          <div
            key={l.id}
            style={{
              display: 'grid',
              gridTemplateColumns: '96px 56px 1fr',
              gap: 12,
              alignItems: 'start',
            }}
          >
            <div style={{ fontSize: 12, color: palette.mute }}>
              <div>{dayjs(l.createTime).format('MM-DD HH:mm')}</div>
              <div style={{ color: palette.sub }}>{l.operatorName ?? '—'}</div>
            </div>
            <Pill tone={LOG_TONE[l.logType] ?? 'gray'}>{l.logTypeName}</Pill>
            <div>
              <div style={{ color: palette.sub, fontSize: 13 }}>
                {l.content}
              </div>
              {l.customerNotified && (
                <div
                  style={{
                    marginTop: 4,
                    fontSize: 12,
                    fontWeight: 600,
                    color: palette.green,
                  }}
                >
                  <SendOutlined /> 已告知客户
                </div>
              )}
              {(l.attachments ?? []).length > 0 && (
                <div style={{ marginTop: 6 }}>
                  <AttachmentWall
                    ownerType="AFTER_SALES_LOG"
                    value={l.attachments ?? []}
                  />
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </Card>
  );

  const replacements = (a.replacements ?? []).length > 0 && (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      {section(
        <ShoppingCartOutlined />,
        '补发订单',
        <span style={{ fontSize: 12, color: palette.mute }}>
          {
            (a.replacements ?? []).filter((r) => r.status === 1 && !r.shipped)
              .length
          }{' '}
          张还没出运
        </span>,
      )}
      <div style={{ display: 'grid', gap: 10 }}>
        {(a.replacements ?? []).map((r) => (
          <div
            key={r.soId}
            style={{
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.inset,
              opacity: r.status === 1 ? 1 : 0.6,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <a onClick={() => history.push(`/sales/orders/${r.soId}`)}>
                {r.soNo}
              </a>
              <Pill
                tone={r.status !== 1 ? 'mute' : r.shipped ? 'green' : 'orange'}
              >
                {r.progressName}
              </Pill>
              <span style={{ marginLeft: 'auto', textAlign: 'right' }}>
                <b style={{ color: palette.ink }}>
                  {formatAmount(r.declaredAmount, r.currencyCode)}
                </b>
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {' '}
                  申报货值 · 订单金额 0
                </span>
              </span>
            </div>
            <div style={{ color: palette.sub, fontSize: 13, marginTop: 4 }}>
              {r.summary}
            </div>
          </div>
        ))}
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 10 }}>
        补发订单没出运（也没取消）时不能结案；补发的采购、运费与费用计入原订单成本。
      </div>
    </Card>
  );

  const plan = (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      {section(
        <EditOutlined />,
        '处理',
        active && (
          <Button type="link" size="small" onClick={() => setDialog('plan')}>
            编辑
          </Button>
        ),
      )}
      {kv(
        '原因与责任',
        <span
          style={{
            color:
              a.reasonCode === 'INVESTIGATING' ? palette.orange : palette.ink,
            fontWeight: 600,
          }}
        >
          {a.reasonName}
        </span>,
      )}
      {kv(
        '处理方案',
        a.solutionNames.length ? (
          <span style={{ display: 'inline-flex', gap: 6, flexWrap: 'wrap' }}>
            {a.solutionNames.map((s) => (
              <Pill key={s} tone="accent">
                {s}
              </Pill>
            ))}
          </span>
        ) : (
          '还没定方案'
        ),
      )}
      {kv('负责人', a.ownerName)}
      {kv('下一步', a.nextAction)}
      {active && (
        <div
          style={{
            padding: '10px 12px',
            borderRadius: 10,
            background: palette.inset,
            marginTop: 4,
          }}
        >
          <b style={{ color: palette.ink, fontSize: 13 }}>
            处理时限 · {levelShort(a.levelCode, a.levelName)}
          </b>
          <div style={{ marginTop: 8 }}>
            {kv('收到反馈', formatDateTime(a.receivedAt).slice(0, 16))}
            {kv(
              '首次响应',
              a.respondedAt ? (
                <span style={{ color: palette.green }}>
                  {formatDateTime(a.respondedAt).slice(5, 16)}
                </span>
              ) : (
                '还没告知客户'
              ),
            )}
            {kv(
              '上次告知客户',
              a.lastContactAt && formatDateTime(a.lastContactAt).slice(5, 16),
            )}
            {kv(
              '等级截止',
              a.levelDueAt ? (
                dayjs(a.levelDueAt).format('MM-DD HH:mm')
              ) : (
                <span style={{ color: palette.mute }}>
                  {a.status === AS_STATUS.WAITING
                    ? '待客户确认，暂停'
                    : a.levelCode === 'S5'
                      ? '不按等级'
                      : '下次告知客户后开始计时'}
                </span>
              ),
            )}
            {kv(
              '手填下次更新',
              a.nextUpdateAt && dayjs(a.nextUpdateAt).format('MM-DD HH:mm'),
            )}
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {SLA_TEXT[a.levelCode] ?? SLA_TEXT.PENDING}
            {WORK_HOURS_TEXT}
          </div>
        </div>
      )}
      {active && (
        <div style={{ marginTop: 12 }}>
          <Button
            size="small"
            onClick={async () => {
              try {
                setA(
                  await afterSalesApi.status(
                    a.id,
                    a.status === AS_STATUS.OPEN
                      ? AS_STATUS.WAITING
                      : AS_STATUS.OPEN,
                  ),
                );
              } catch (e) {
                message.error(readBizError(e).message);
              }
            }}
          >
            {a.status === AS_STATUS.OPEN ? '改为待客户确认' : '改回处理中'}
          </Button>
        </div>
      )}
    </Card>
  );

  const cost = (
    <Card style={{ padding: 20 }}>
      {section(<DollarOutlined />, '售后成本')}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
        }}
      >
        <span style={{ color: palette.sub }}>
          已发生（退款 + 费用 + 退回运费）
        </span>
        <b style={{ fontSize: 18, color: palette.ink }}>
          {formatAmount(
            Number(a.refundCny ?? 0) +
              Number(a.expenseCny ?? 0) +
              Number(a.returnFreightCny ?? 0),
            'CNY',
          )}
        </b>
      </div>
      {Number(a.returnCreditCny ?? 0) > 0 && (
        <div style={{ fontSize: 12, color: palette.green, marginTop: 4 }}>
          退货去向冲减原订单成本 −{formatAmount(a.returnCreditCny ?? 0, 'CNY')}
        </div>
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        退款冲减订单收入，费用计入订单成本；退货去向处理后，退回供应商或转闲置冲减订单成本；补发订单的成本计入原订单。
      </div>
    </Card>
  );

  const returnItems = (a.items ?? [])
    .filter((i) => i.soItemId)
    .map((i) => ({
      soItemId: i.soItemId as number,
      model: i.model,
      brand: i.brand,
      category: i.category ?? undefined,
      shippedQty: i.shippedQty ?? 0,
      returnable: i.returnableQty ?? 0,
      unitPrice: i.unitPrice ?? undefined,
    }));
  const noReturnable = returnItems.every((i) => i.returnable <= 0);
  const options: {
    key: Dialog;
    icon: React.ReactNode;
    title: string;
    desc: string;
    disabled?: string;
  }[] = [
    {
      key: 'returnOnly',
      icon: <RollbackOutlined />,
      title: '仅退货',
      desc: '客户寄回，不退钱',
      disabled: noReturnable ? '没有可退的数量（已出运的才能退）' : undefined,
    },
    {
      key: 'returnRefund',
      icon: <SwapOutlined />,
      title: '退货退款',
      desc: '寄回后退钱，可先退款',
      disabled: noReturnable ? '没有可退的数量（已出运的才能退）' : undefined,
    },
    {
      key: 'refund',
      icon: <DollarOutlined />,
      title: '仅退款',
      desc: '不寄回，按金额退',
    },
    {
      key: 'replacement',
      icon: <ShoppingCartOutlined />,
      title: '补发',
      desc: '生成补发订单',
    },
  ];
  const handlingCard = active && (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      {section(<EditOutlined />, '处理方式')}
      {!a.soId ? (
        <div style={{ color: palette.mute, fontSize: 13 }}>
          售后单没有关联订单，不能退货、退款或补发
        </div>
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
            gap: 12,
          }}
        >
          {options.map((o) => (
            <button
              key={o.key}
              type="button"
              disabled={!!o.disabled}
              title={o.disabled}
              onClick={() => setDialog(o.key)}
              style={{
                display: 'flex',
                gap: 12,
                alignItems: 'center',
                textAlign: 'left',
                padding: '14px 16px',
                borderRadius: 12,
                border: `1px solid ${palette.hairline}`,
                background: palette.inset,
                color: palette.ink,
                font: 'inherit',
                cursor: o.disabled ? 'not-allowed' : 'pointer',
                opacity: o.disabled ? 0.5 : 1,
              }}
            >
              <span
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: 10,
                  display: 'grid',
                  placeItems: 'center',
                  flexShrink: 0,
                  background: palette.accentSoft,
                  color: palette.link,
                  fontSize: 16,
                }}
              >
                {o.icon}
              </span>
              <span style={{ minWidth: 0 }}>
                <b style={{ display: 'block' }}>{o.title}</b>
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {o.disabled ?? o.desc}
                </span>
              </span>
            </button>
          ))}
        </div>
      )}
    </Card>
  );

  const docs = [
    ...(a.returns ?? []).flatMap((r) => [
      {
        label: '销售退货单',
        no: r.returnNo,
        href: PATHS.salesReturn(r.id),
        statusName: r.statusName,
        tone: TH_STATUS_TONE[r.status],
        extra: <span style={{ color: palette.sub }}>{r.handlingName}</span>,
      },
      ...(r.receiptId
        ? [
            {
              label: '退货入库单',
              no: r.receiptNo,
              href: PATHS.returnReceipt(r.receiptId),
              statusName: r.receiptStatusName,
              tone:
                r.receiptStatus == null
                  ? undefined
                  : RT_STATUS_TONE[r.receiptStatus],
            },
          ]
        : []),
    ]),
    ...(a.requests ?? []).map((q) => ({
      label: '退款申请',
      no: q.requestNo,
      href: PATHS.refundRequest(q.id),
      statusName: q.statusName,
      tone: RQ_STATUS_TONE[q.status],
      extra: (
        <span style={{ color: palette.sub }}>
          {formatAmount(q.amount, q.currencyCode)}
        </span>
      ),
    })),
    ...(a.refunds ?? []).map((f) => ({
      label: '退款单',
      no: f.refundNo,
      href: listHref('/finance/refunds', f.refundNo),
      extra: (
        <span style={{ color: palette.sub }}>
          {formatAmount(f.amount, f.currencyCode)} · {f.methodName}
        </span>
      ),
    })),
    ...(a.purchaseReturns ?? []).map((p) => ({
      label: '采购退货单',
      no: p.no,
      href: PATHS.purchaseReturn(p.id),
      statusName: p.statusName,
      tone: PR_STATUS_TONE[p.status],
    })),
    ...(a.expenses ?? []).map((e) => ({
      label: '费用单',
      no: e.expenseNo,
      extra: (
        <span style={{ color: palette.sub }}>
          {e.name} · {formatAmount(e.amountCny, 'CNY')}
        </span>
      ),
    })),
  ];
  const docsCard = docs.length > 0 && (
    <RelatedDocsCard
      title={
        <>
          售后单据{' '}
          <span style={{ color: palette.mute, fontWeight: 400 }}>
            {docs.length}
          </span>
        </>
      }
      docs={docs}
    />
  );

  return (
    <div>
      <SalesPageTitle
        crumbs={[<MenuLink key="list" to="/sales/after-sales" />, a.asNo]}
        title={
          <>
            {a.asNo}
            <Pill tone={LEVEL_TONE[a.levelCode] ?? 'gray'}>
              {levelShort(a.levelCode, a.levelName)}
            </Pill>
            <Pill tone={AS_STATUS_TONE[a.status] ?? 'gray'}>
              {a.statusName}
            </Pill>
          </>
        }
        description={
          <>
            {a.customerName}
            {a.soId ? (
              <>
                {' · 订单 '}
                <a onClick={() => history.push(`/sales/orders/${a.soId}`)}>
                  {a.soNo}
                </a>
              </>
            ) : (
              ' · 没有订单'
            )}
            {` · 负责人 ${a.ownerName ?? '—'} · ${formatDateTime(a.createTime).slice(0, 16)} 创建`}
          </>
        }
        actions={
          active && (
            <>
              {a.soId && access['finance:expense:edit'] && (
                <Button
                  icon={<FileTextOutlined />}
                  onClick={() => setDialog('expense')}
                >
                  登记费用
                </Button>
              )}
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={() => setDialog('close')}
              >
                结案
              </Button>
              <Button
                danger
                icon={<StopOutlined />}
                onClick={() => setDialog('cancel')}
              >
                撤销
              </Button>
            </>
          )
        }
      />
      {slaBar}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 2.2fr) minmax(340px, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ minWidth: 0 }}>
          {handlingCard}
          {docsCard}
          {problem}
          {replacements}
          {timeline}
        </div>
        <div style={{ minWidth: 0 }}>
          {plan}
          {cost}
        </div>
      </div>
      <PlanModal
        open={dialog === 'plan'}
        data={a}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <CloseModal
        open={dialog === 'close'}
        data={a}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      {a.soId && (
        <ReplacementModal
          open={dialog === 'replacement'}
          data={a}
          onClose={() => setDialog(undefined)}
          onDone={done}
        />
      )}
      <CreateReturnModal
        open={dialog === 'returnOnly' || dialog === 'returnRefund'}
        handling={dialog === 'returnRefund' ? HANDLING.REFUND : HANDLING.RETURN}
        afterSalesId={a.id}
        asNo={a.asNo}
        soNo={a.soNo}
        currencyCode={a.currencyCode}
        items={returnItems}
        onClose={() => setDialog(undefined)}
        onDone={(r) => {
          setDialog(undefined);
          history.push(PATHS.salesReturn(r.id));
        }}
      />
      {a.soId && (
        <RefundRequestModal
          open={dialog === 'refund'}
          soId={a.soId}
          afterSalesId={a.id}
          onClose={() => setDialog(undefined)}
          onDone={reload}
        />
      )}
      {a.soId && (
        <ExpenseQuickModal
          open={dialog === 'expense'}
          afterSales={a}
          onClose={() => setDialog(undefined)}
          onDone={reload}
        />
      )}
      <ReasonModal
        open={dialog === 'cancel'}
        title={`撤销 ${a.asNo}？`}
        description="只有还没有退货、退款申请、退款与费用的售后单能撤销；撤销后只读。"
        okText="撤销"
        danger
        onCancel={() => setDialog(undefined)}
        onOk={async (why) => {
          done(await afterSalesApi.cancel(a.id, why));
          message.success('已撤销');
        }}
      />
    </div>
  );
};

export default AfterSalesDetail;
