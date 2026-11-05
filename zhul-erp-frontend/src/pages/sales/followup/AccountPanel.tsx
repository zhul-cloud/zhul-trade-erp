import { BulbOutlined, CrownOutlined, FlagOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Skeleton,
} from 'antd';
import React, { useEffect, useState } from 'react';
import NewInquiryModal from '@/pages/inquiry/customer-inquiry/NewInquiryModal';
import { inquiryApi } from '@/pages/inquiry/shared/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { fmtDate, Pill } from './components';
import {
  type AccountCard,
  type AnnualPlan,
  type FollowupItem,
  followupApi,
  readBizError,
} from './service';

const PLAN_FOCUS_MAX = 200;
const PLAN_NOTE_MAX = 500;

/**
 * 客户经营「建询盘」：打开现有的新建询盘，客户已带好（K14、K15）。
 * 询盘建好后由询盘钩子触发重新推导，客户进入 G5，经营提醒暂停。
 */
export const AccountInquiryModal: React.FC<{
  customerId?: number;
  onClose: () => void;
  onCreated?: () => void;
}> = ({ customerId, onClose, onCreated }) => {
  const { message } = App.useApp();
  return (
    <NewInquiryModal
      open={!!customerId}
      presetCustomerId={customerId}
      onClose={onClose}
      onCreated={async (created, startParse) => {
        onClose();
        if (startParse) {
          try {
            await inquiryApi.startParse(created.id);
            message.success(
              `${created.inquiryCode} 已提交，AI 正在解析；这笔需求进入「商机成交」`,
            );
          } catch (e) {
            message.warning(
              `${created.inquiryCode} 已保存，AI 解析没有启动：${readBizError(e).message}`,
            );
          }
        } else {
          message.success(
            `${created.inquiryCode} 已保存；这笔需求进入「商机成交」`,
          );
        }
        onCreated?.();
      }}
    />
  );
};

/** 年度计划（K15 右）：目标金额（本位币，元）、重点品类或品牌、备注；已成交与完成比例由后端算 */
export const AnnualPlanModal: React.FC<{
  open: boolean;
  customerId: number;
  customerName?: string;
  plan?: AnnualPlan;
  onClose: () => void;
  onSaved: (p: AnnualPlan) => void;
}> = ({ open, customerId, customerName, plan, onClose, onSaved }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [target, setTarget] = useState<number | null>(null);
  const [focus, setFocus] = useState('');
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTarget(plan?.targetAmount ?? null);
    setFocus(plan?.focus ?? '');
    setNote(plan?.note ?? '');
  }, [open, plan]);

  const percent =
    target && target > 0 && plan
      ? Math.round((Number(plan.dealtAmount) / target) * 100)
      : undefined;

  const save = async () => {
    if (target == null || target < 0) {
      message.warning('请填写年度目标');
      return;
    }
    setSaving(true);
    try {
      const p = await followupApi.savePlan(customerId, {
        targetAmount: target,
        focus: focus.trim() || undefined,
        note: note.trim() || undefined,
      });
      message.success('年度计划已保存');
      onSaved(p);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={`年度计划 · ${plan?.planYear ?? new Date().getFullYear()}`}
      open={open}
      onCancel={onClose}
      onOk={save}
      okText="保存"
      confirmLoading={saving}
      destroyOnHidden
    >
      <div style={{ color: palette.sub, fontSize: 13 }}>{customerName}</div>
      <div
        style={{
          marginTop: 10,
          padding: '8px 12px',
          borderRadius: 10,
          background: palette.inset,
          fontSize: 13,
          color: palette.sub,
        }}
      >
        今年已成交 {formatAmount(plan?.dealtAmount ?? 0, 'CNY')}
        （未取消订单本位币合计，不含售后补发）
      </div>
      <div style={{ fontWeight: 600, margin: '14px 0 6px' }}>
        <span style={{ color: palette.red }}>* </span>年度目标（本位币，元）
      </div>
      <InputNumber
        style={{ width: 240 }}
        min={0}
        precision={2}
        prefix="¥"
        value={target}
        onChange={(v) => setTarget(v == null ? null : Number(v))}
      />
      {percent != null && (
        <span style={{ marginLeft: 12, color: palette.sub, fontSize: 13 }}>
          完成 {percent}%
        </span>
      )}
      <div style={{ fontWeight: 600, margin: '14px 0 6px' }}>
        重点品类或品牌
      </div>
      <Input
        maxLength={PLAN_FOCUS_MAX}
        showCount
        placeholder="例如 Siemens S7-1500 备件、ABB ACS880 变频器"
        value={focus}
        onChange={(e) => setFocus(e.target.value)}
      />
      <div style={{ fontWeight: 600, margin: '14px 0 6px' }}>备注</div>
      <Input.TextArea
        maxLength={PLAN_NOTE_MAX}
        showCount
        autoSize={{ minRows: 2, maxRows: 4 }}
        value={note}
        onChange={(e) => setNote(e.target.value)}
      />
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        完成比例 = 已成交 ÷ 目标，四舍五入取整，只用于显示
      </div>
    </Modal>
  );
};

const Box: React.FC<{
  tone: 'green' | 'orange' | 'inset';
  children: React.ReactNode;
}> = ({ tone, children }) => {
  const { palette } = useAppTheme();
  const bg =
    tone === 'green'
      ? palette.greenSoft
      : tone === 'orange'
        ? palette.orangeSoft
        : palette.inset;
  return (
    <div
      style={{
        marginTop: 12,
        padding: '10px 12px',
        borderRadius: 10,
        background: bg,
        fontSize: 13,
        color: palette.sub,
      }}
    >
      {children}
    </div>
  );
};

/**
 * 客户经营作战卡的上半部分（K14 右、K15 下）：G1 分层 · G2 档案 · G3 采购规律、触达理由、交叉销售建议、战略客户与年度计划。
 * 「AI 写触达消息」沿用作战卡下方的 AI 区块。
 */
export const AccountPanel: React.FC<{
  item: FollowupItem;
  canRecord: boolean;
  onNewInquiry: (customerId: number) => void;
  onChanged: () => void;
}> = ({ item, canRecord, onNewInquiry, onChanged }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [card, setCard] = useState<AccountCard>();
  const [failed, setFailed] = useState(false);
  const [planOpen, setPlanOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const customerId = item.customerId;
  // 列表刷新后（阶段、理由变了）version 变化，重新取
  const version = `${item.stageCode}|${item.accountReason}|${item.strategic}`;

  useEffect(() => {
    let alive = true;
    setFailed(false);
    followupApi
      .account(customerId)
      .then((c) => alive && setCard(c))
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
    };
  }, [customerId, version]);

  if (failed)
    return (
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        客户经营信息加载失败，请刷新重试
      </div>
    );
  if (!card || card.customerId !== customerId)
    return <Skeleton active paragraph={{ rows: 3 }} />;

  const toggleStrategic = async (strategic: boolean) => {
    setBusy(true);
    try {
      await followupApi.setStrategic(customerId, strategic);
      message.success(
        strategic
          ? item.stageCode === 'G5'
            ? '已设为战略客户；复购机会结束后进入 G7 长期合作'
            : '已设为战略客户，进入 G7 长期合作'
          : '已取消战略客户',
      );
      setCard(await followupApi.account(customerId));
      onChanged();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const plan = card.plan;
  return (
    <>
      {/* G1–G3：系统已完成 */}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 10 }}>
        系统已完成（G1 分层 · G2 档案 · G3 采购规律）
      </div>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
        {card.bizGrade && (
          <Pill tone="accent">
            {card.bizGrade} 级
            {card.bizGradeScore != null && ` · RFM ${card.bizGradeScore} 分`}
          </Pill>
        )}
        <Pill tone="gray">
          档案 {card.profileDone}/{card.profileTotal}
        </Pill>
        {card.patternText && (
          <Pill tone="green">{card.patternText}（系统推测）</Pill>
        )}
        {card.strategic && <Pill tone="violet">战略客户</Pill>}
      </div>
      <div style={{ fontSize: 12, color: palette.sub, marginTop: 6 }}>
        近 12 个月 {formatAmount(card.recentAmount ?? 0, card.currencyCode)} ·{' '}
        {card.recentOrderCount ?? 0} 单
        {card.lastOrderDate && ` · 最近 ${fmtDate(card.lastOrderDate)}`}
        {card.dormantDays != null && (
          <span style={{ color: palette.orange }}>
            {' '}
            · 沉睡 {card.dormantDays} 天
          </span>
        )}
      </div>

      {/* 触达理由 */}
      <Box tone="green">
        <div style={{ fontWeight: 600, color: palette.green, marginBottom: 4 }}>
          <BulbOutlined /> 这次的触达理由（系统推测）
        </div>
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          {card.reason && <li>{card.reason}</li>}
          {card.lastOrderItems && <li>最近一单：{card.lastOrderItems}</li>}
          {card.patternBrands && <li>常买：{card.patternBrands}</li>}
          {card.crossCategory && (
            <li>上次聊到的新品类：{card.crossCategory}</li>
          )}
        </ul>
      </Box>

      {/* 交叉销售 */}
      {card.crossSell.length > 0 && (
        <Box tone="orange">
          <div
            style={{ fontWeight: 600, color: palette.orange, marginBottom: 4 }}
          >
            交叉销售建议（系统推测）
          </div>
          <div>
            可以试试：
            {card.crossSell
              .map(
                (c, i) =>
                  `${c.category}（${i === 0 ? '另有 ' : ''}${c.customers} 个${i === 0 ? '买过同品类的客户也买' : ''}）`,
              )
              .join('、')}
          </div>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
            先问客户是否也负责这个品类的采购，确认后记跟进选「聊了新品类」
          </div>
        </Box>
      )}

      {/* 战略客户与年度计划 */}
      {card.strategic ? (
        <Box tone="inset">
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 6,
            }}
          >
            <span style={{ fontWeight: 600, color: palette.ink }}>
              <CrownOutlined /> 年度计划 · {plan?.planYear}
            </span>
            <span style={{ flex: 1 }} />
            <Button
              size="small"
              type="link"
              disabled={!canRecord}
              onClick={() => setPlanOpen(true)}
              style={{ padding: 0 }}
            >
              {plan?.targetAmount != null ? '修改' : '填写'}
            </Button>
          </div>
          {plan?.targetAmount != null ? (
            <>
              <div>
                已成交 {formatAmount(plan.dealtAmount, 'CNY')} /{' '}
                {formatAmount(plan.targetAmount, 'CNY')}
                {plan.percent != null && ` · ${plan.percent}%`}
              </div>
              <Progress
                percent={Math.min(plan.percent ?? 0, 100)}
                size="small"
                showInfo={false}
              />
              {plan.focus && (
                <div style={{ fontSize: 12 }}>重点：{plan.focus}</div>
              )}
            </>
          ) : (
            <div style={{ fontSize: 12, color: palette.mute }}>
              还没填今年的目标；今年已成交{' '}
              {formatAmount(plan?.dealtAmount ?? 0, 'CNY')}
            </div>
          )}
          <div style={{ fontSize: 12, marginTop: 6 }}>
            季度回顾：
            {card.lastReviewDate
              ? `上次 ${fmtDate(card.lastReviewDate)}`
              : '还没回顾过'}
            {card.nextReviewDate && ` · 下次 ${fmtDate(card.nextReviewDate)}`}
          </div>
          <Popconfirm
            title="取消战略客户？"
            description="客户回到 G4（有进行中的询盘时为 G5），年度计划保留"
            okText="取消战略客户"
            cancelText="再想想"
            onConfirm={() => toggleStrategic(false)}
          >
            <Button
              size="small"
              type="link"
              danger
              disabled={!canRecord}
              loading={busy}
              style={{ padding: 0, marginTop: 4 }}
            >
              取消战略客户
            </Button>
          </Popconfirm>
        </Box>
      ) : (
        <Box tone="inset">
          {card.suggestStrategic && (
            <div style={{ color: palette.violet, marginBottom: 4 }}>
              <CrownOutlined /> S 级客户，建议设为战略客户：每 90
              天季度回顾，填写年度计划
            </div>
          )}
          <Button
            size="small"
            disabled={!canRecord || !card.bizGrade}
            loading={busy}
            onClick={() => toggleStrategic(true)}
          >
            设为战略客户
          </Button>
        </Box>
      )}

      {/* 发现需求就建询盘 */}
      <div
        style={{
          fontWeight: 600,
          fontSize: 13,
          color: palette.ink,
          margin: '14px 0 6px',
        }}
      >
        <FlagOutlined /> 发现需求就建询盘
      </div>
      <div style={{ fontSize: 13, color: palette.sub }}>
        客户提到具体型号、数量或项目 → 点「建询盘」，这笔需求进入「商机成交 ·
        报价前」；当前没需求：记跟进时选下次联系日期即可
      </div>
      <Button
        type="primary"
        style={{ marginTop: 8 }}
        disabled={!canRecord}
        onClick={() => onNewInquiry(customerId)}
      >
        建询盘
      </Button>

      <AnnualPlanModal
        open={planOpen}
        customerId={customerId}
        customerName={item.customerName}
        plan={plan}
        onClose={() => setPlanOpen(false)}
        onSaved={(p) => {
          setPlanOpen(false);
          setCard((c) => (c ? { ...c, plan: p } : c));
        }}
      />
    </>
  );
};
