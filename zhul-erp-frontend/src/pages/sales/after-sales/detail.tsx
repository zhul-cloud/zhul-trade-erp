import {
  CheckCircleOutlined,
  DollarOutlined,
  EditOutlined,
  FileTextOutlined,
  HistoryOutlined,
  InboxOutlined,
  StopOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import { App, Button, Input, Skeleton } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import ExpenseQuickModal from '@/pages/finance/expenses/AfterSalesExpense';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { AttachmentWall, ReasonModal } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { Card, Pill, SalesPageTitle } from '../components';
import { readBizError } from '../service';
import {
  CloseModal,
  PlanModal,
  RefundRequestModal,
  ReturnModal,
} from './dialogs';
import {
  type AfterSales,
  AS_STATUS,
  AS_STATUS_TONE,
  afterSalesApi,
  LEVEL_TONE,
  levelShort,
  RT_STATUS_TONE,
  type Tone,
} from './service';

const LOG_TONE: Record<string, Tone> = {
  NOTE: 'accent',
  LEVEL: 'orange',
  STATUS: 'green',
  PLAN: 'violet',
  RETURN: 'cyan',
  REFUND: 'red',
  EXPENSE: 'gray',
  CREATE: 'gray',
};
const RQ_TONE: Record<number, Tone> = {
  1: 'accent',
  2: 'orange',
  3: 'green',
  4: 'red',
  5: 'mute',
};

type Dialog = 'plan' | 'close' | 'return' | 'refund' | 'expense' | 'cancel';

/** 售后单详情（spec sales/after-sales） */
const AfterSalesDetail: React.FC = () => {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const [a, setA] = useState<AfterSales>();
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<Dialog>();
  const [note, setNote] = useState('');
  const [noteFiles, setNoteFiles] = useState<Attachment[]>([]);
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
        ),
      );
      setNote('');
      setNoteFiles([]);
      message.success('已添加跟进');
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

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
      {active && (a.nextAction || a.nextUpdateAt) && (
        <div
          style={{
            padding: '10px 12px',
            borderRadius: 10,
            background: a.overdue ? palette.redSoft : palette.inset,
            marginTop: 4,
          }}
        >
          <b style={{ color: palette.ink }}>下一步：{a.nextAction || '—'}</b>
          {a.nextUpdateAt && (
            <div
              style={{
                fontSize: 12,
                color: a.overdue ? palette.red : palette.mute,
              }}
            >
              下次更新客户 {dayjs(a.nextUpdateAt).format('MM-DD HH:mm')}
              {a.overdue ? ' · 已过期' : ''}
            </div>
          )}
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
          padding: 14,
          borderRadius: 12,
          background: palette.inset,
          display: 'grid',
          gap: 10,
          fontSize: 13,
        }}
      >
        <div>
          <div style={{ color: palette.mute, marginBottom: 4 }}>退货</div>
          {(a.returns ?? []).length === 0 && <span>—</span>}
          {(a.returns ?? []).map((r) => (
            <div
              key={r.id}
              style={{ display: 'flex', gap: 8, alignItems: 'center' }}
            >
              <a onClick={() => history.push(`/warehouse/returns?id=${r.id}`)}>
                {r.returnNo}
              </a>
              <span style={{ color: palette.sub, flex: 1 }}>{r.summary}</span>
              <Pill tone={RT_STATUS_TONE[r.status] ?? 'gray'}>
                {r.statusName}
              </Pill>
            </div>
          ))}
        </div>
        <div>
          <div style={{ color: palette.mute, marginBottom: 4 }}>
            退款申请 · 退款
          </div>
          {(a.requests ?? []).length + (a.refunds ?? []).length === 0 && (
            <span>—</span>
          )}
          {(a.requests ?? []).map((q) => (
            <div
              key={`q${q.id}`}
              style={{ display: 'flex', gap: 8, alignItems: 'center' }}
            >
              <span>{q.requestNo}</span>
              <span style={{ color: palette.sub, flex: 1 }}>
                {formatAmount(q.amount, q.currencyCode)}
              </span>
              <Pill tone={RQ_TONE[q.status] ?? 'gray'}>{q.statusName}</Pill>
            </div>
          ))}
          {(a.refunds ?? []).map((f) => (
            <div key={`f${f.id}`} style={{ display: 'flex', gap: 8 }}>
              <span>{f.refundNo}</span>
              <span style={{ color: palette.sub, flex: 1 }}>
                {formatAmount(f.amount, f.currencyCode)} · {f.methodName}
              </span>
              <b>{formatAmount(f.amountCny, 'CNY')}</b>
            </div>
          ))}
        </div>
        <div>
          <div style={{ color: palette.mute, marginBottom: 4 }}>费用</div>
          {(a.expenses ?? []).length === 0 && <span>—</span>}
          {(a.expenses ?? []).map((e) => (
            <div key={e.id} style={{ display: 'flex', gap: 8 }}>
              <span>{e.expenseNo}</span>
              <span style={{ color: palette.sub, flex: 1 }}>{e.name}</span>
              <b>{formatAmount(e.amountCny, 'CNY')}</b>
            </div>
          ))}
        </div>
      </div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          marginTop: 12,
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
        退款冲减订单收入，费用计入订单成本；退货去向处理后，退回供应商或转闲置冲减订单成本。
      </div>
    </Card>
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
              {a.soId && (
                <>
                  <Button
                    icon={<InboxOutlined />}
                    onClick={() => setDialog('return')}
                  >
                    发起退货
                  </Button>
                  <Button
                    icon={<DollarOutlined />}
                    onClick={() => setDialog('refund')}
                  >
                    申请退款
                  </Button>
                  {access['finance:expense:edit'] && (
                    <Button
                      icon={<FileTextOutlined />}
                      onClick={() => setDialog('expense')}
                    >
                      登记费用
                    </Button>
                  )}
                </>
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
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 2.2fr) minmax(340px, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ minWidth: 0 }}>
          {problem}
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
      <ReturnModal
        open={dialog === 'return'}
        data={a}
        onClose={() => setDialog(undefined)}
        onDone={reload}
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
