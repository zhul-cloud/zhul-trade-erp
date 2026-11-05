import { FrownOutlined, MehOutlined, SmileOutlined } from '@ant-design/icons';
import { Checkbox, DatePicker, Input, Segmented, Tag } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { fmtDate } from './components';
import {
  ACCOUNT_ANSWER,
  ANSWER,
  DISSATISFIED_REASONS,
  type FollowupItem,
  ISSUE_TYPES,
  NO_NEED_REASONS,
  NOTICE_OPTIONS,
  REVIEW_OPTIONS_A5,
  REVIEW_OPTIONS_A6,
  SATISFACTION,
  TOUCH,
} from './service';
import {
  CROSS_CATEGORY_MAX,
  SIGNAL_MAX,
  type StageAnswers,
  type StageKind,
} from './stageForm';

const Label: React.FC<{ children: React.ReactNode; hint?: string }> = ({
  children,
  hint,
}) => {
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
      <span style={{ color: palette.red }}>* </span>
      {children}
      {hint && (
        <span style={{ fontWeight: 400, color: palette.mute, marginLeft: 6 }}>
          {hint}
        </span>
      )}
    </div>
  );
};

/** 卡片式单选项：标题 + 选后会怎样，选中时可带子内容 */
const Choice: React.FC<{
  active: boolean;
  danger?: boolean;
  title: string;
  desc: string;
  onClick: () => void;
  children?: React.ReactNode;
}> = ({ active, danger, title, desc, onClick, children }) => {
  const { palette } = useAppTheme();
  const line = active
    ? danger
      ? palette.red
      : palette.link
    : palette.hairline;
  return (
    <div
      style={{
        border: `1px solid ${line}`,
        borderRadius: 10,
        background: active
          ? danger
            ? palette.redSoft
            : palette.accentSoft
          : 'transparent',
        transition: 'background .15s, border-color .15s',
      }}
    >
      <button
        type="button"
        onClick={onClick}
        style={{
          all: 'unset',
          cursor: 'pointer',
          display: 'block',
          width: '100%',
          boxSizing: 'border-box',
          padding: '10px 12px',
        }}
      >
        <div style={{ fontWeight: 600, color: palette.ink }}>{title}</div>
        <div style={{ fontSize: 12, color: palette.sub }}>→ {desc}</div>
      </button>
      {active && children && (
        <div style={{ padding: '0 12px 10px' }}>{children}</div>
      )}
    </div>
  );
};

const IssueTags: React.FC<{
  value: string[];
  onChange: (v: string[]) => void;
}> = ({ value, onChange }) => (
  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
    {ISSUE_TYPES.map((t) => (
      <Tag.CheckableTag
        key={t}
        checked={value.includes(t)}
        onChange={(c) =>
          onChange(c ? [...value, t] : value.filter((x) => x !== t))
        }
      >
        {t}
      </Tag.CheckableTag>
    ))}
  </div>
);

const disabledPast = (d: dayjs.Dayjs) => d.isBefore(dayjs().startOf('day'));

/** 「记一次跟进」中间按阶段替换的问题（K12） */
const StageQuestions: React.FC<{
  kind: StageKind;
  item: FollowupItem;
  value: StageAnswers;
  onChange: (v: StageAnswers) => void;
}> = ({ kind, item, value: a, onChange }) => {
  const { palette } = useAppTheme();
  const set = (patch: Partial<StageAnswers>) => onChange({ ...a, ...patch });

  if (kind === 'fulfil')
    return (
      <>
        <Label hint="可多选">这次和客户沟通了什么</Label>
        <Checkbox.Group
          style={{ display: 'grid', gap: 6 }}
          value={NOTICE_OPTIONS.filter((o) => a.noticeMask & o.value).map(
            (o) => o.value,
          )}
          options={NOTICE_OPTIONS}
          onChange={(vals) =>
            set({ noticeMask: (vals as number[]).reduce((x, y) => x | y, 0) })
          }
        />
        {item.stageCode === 'F6' && (
          <div
            style={{
              marginTop: 12,
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.inset,
              display: 'grid',
              gap: 4,
            }}
          >
            <div style={{ fontSize: 12, color: palette.mute }}>预计送达</div>
            <div
              style={{
                display: 'flex',
                gap: 8,
                alignItems: 'center',
                flexWrap: 'wrap',
              }}
            >
              <b style={{ color: palette.ink }}>
                {fmtDate(a.etaDate || item.etaDate)}
              </b>
              {!a.etaDate && item.etaNote && (
                <span style={{ fontSize: 12, color: palette.sub }}>
                  {item.etaNote}
                </span>
              )}
              <DatePicker
                size="small"
                placeholder="改一下"
                value={a.etaDate ? dayjs(a.etaDate) : null}
                disabledDate={disabledPast}
                onChange={(d) =>
                  set({ etaDate: d ? d.format('YYYY-MM-DD') : undefined })
                }
              />
            </div>
            <div style={{ fontSize: 12, color: palette.mute }}>
              承运商给了准确日期时改这里，售后评价按新日期开始
            </div>
          </div>
        )}
      </>
    );

  if (kind === 'receipt' || kind === 'use') {
    const receipt = kind === 'receipt';
    const answer = receipt ? a.receiptAnswer : a.useAnswer;
    const pick = (v: number) =>
      set(receipt ? { receiptAnswer: v } : { useAnswer: v });
    return (
      <>
        <Label>{receipt ? '客户收到了吗' : '用得好吗'}</Label>
        <div style={{ display: 'grid', gap: 8 }}>
          <Choice
            active={answer === ANSWER.OK}
            title={receipt ? '已收到，数量外观没问题' : '正常使用'}
            desc={receipt ? 'A2 使用确认，过几天问装机情况' : 'A5 满意度确认'}
            onClick={() => pick(ANSWER.OK)}
          />
          <Choice
            active={answer === ANSWER.ISSUE}
            danger
            title={receipt ? '已收到，有问题' : '有问题'}
            desc="保存后打开新建售后单，客户和订单已带好"
            onClick={() => pick(ANSWER.ISSUE)}
          >
            <IssueTags
              value={a.issueTypes}
              onChange={(v) => set({ issueTypes: v })}
            />
          </Choice>
          <Choice
            active={answer === ANSWER.NOT_YET}
            title={receipt ? '还没收到' : '还没开始用'}
            desc={receipt ? '填新的预计送达日，到那天再问' : '按你定的日期再问'}
            onClick={() => pick(ANSWER.NOT_YET)}
          >
            {receipt && (
              <span style={{ fontSize: 13 }}>
                新的预计送达{' '}
                <DatePicker
                  size="small"
                  value={a.etaDate ? dayjs(a.etaDate) : null}
                  disabledDate={disabledPast}
                  onChange={(d) =>
                    set({ etaDate: d ? d.format('YYYY-MM-DD') : undefined })
                  }
                />
              </span>
            )}
          </Choice>
        </div>
      </>
    );
  }

  if (kind === 'satisfaction') {
    const faces = [
      { v: SATISFACTION.SATISFIED, label: '满意', icon: <SmileOutlined /> },
      { v: SATISFACTION.NEUTRAL, label: '一般', icon: <MehOutlined /> },
      {
        v: SATISFACTION.DISSATISFIED,
        label: '不满意',
        icon: <FrownOutlined />,
      },
    ];
    const satisfied = a.satisfaction === SATISFACTION.SATISFIED;
    return (
      <>
        <Label>客户满意吗</Label>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: 8,
          }}
        >
          {faces.map((f) => {
            const on = a.satisfaction === f.v;
            const good = f.v === SATISFACTION.SATISFIED;
            return (
              <button
                key={f.v}
                type="button"
                onClick={() =>
                  set({
                    satisfaction: f.v,
                    reviewStatus: good ? a.reviewStatus : undefined,
                    dissatisfiedReason: good ? undefined : a.dissatisfiedReason,
                  })
                }
                style={{
                  all: 'unset',
                  cursor: 'pointer',
                  textAlign: 'center',
                  padding: '8px 0',
                  borderRadius: 10,
                  fontWeight: on ? 600 : 400,
                  color: on
                    ? good
                      ? palette.green
                      : palette.orange
                    : palette.sub,
                  background: on
                    ? good
                      ? palette.greenSoft
                      : palette.orangeSoft
                    : 'transparent',
                  border: `1px solid ${
                    on
                      ? good
                        ? palette.green
                        : palette.orange
                      : palette.hairline
                  }`,
                }}
              >
                {f.icon} {f.label}
              </button>
            );
          })}
        </div>
        {satisfied && (
          <>
            <Label hint="满意时才出现">评价</Label>
            <Segmented
              // 没选时不要默认高亮第一项
              value={a.reviewStatus ?? ''}
              options={REVIEW_OPTIONS_A5}
              onChange={(v) => set({ reviewStatus: v as number })}
            />
            <div
              style={{
                display: 'flex',
                gap: 8,
                alignItems: 'center',
                marginTop: 8,
              }}
            >
              <span style={{ fontSize: 13, color: palette.sub }}>平台</span>
              <Input
                style={{ width: 200 }}
                maxLength={32}
                placeholder="如 阿里国际站、Google"
                value={a.reviewPlatform}
                onChange={(e) => set({ reviewPlatform: e.target.value })}
              />
            </div>
          </>
        )}
        {a.satisfaction && !satisfied && (
          <>
            <Label>
              {a.satisfaction === SATISFACTION.NEUTRAL ? '一般' : '不满意'}
              的原因
            </Label>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {DISSATISFIED_REASONS.map((r) => (
                <Tag.CheckableTag
                  key={r.value}
                  checked={a.dissatisfiedReason === r.value}
                  onChange={(c) =>
                    set({ dissatisfiedReason: c ? r.value : undefined })
                  }
                >
                  {r.label}
                </Tag.CheckableTag>
              ))}
            </div>
          </>
        )}
      </>
    );
  }

  if (kind === 'review')
    return (
      <>
        <Label>客户评价了吗</Label>
        <Segmented
          value={a.reviewStatus ?? ''}
          options={REVIEW_OPTIONS_A6}
          onChange={(v) => set({ reviewStatus: v as number })}
        />
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            marginTop: 8,
          }}
        >
          <span style={{ fontSize: 13, color: palette.sub }}>平台</span>
          <Input
            style={{ width: 200 }}
            maxLength={32}
            placeholder={item.reviewPlatform || '如 阿里国际站、Google'}
            value={a.reviewPlatform}
            onChange={(e) => set({ reviewPlatform: e.target.value })}
          />
        </div>
      </>
    );

  if (kind === 'account') {
    const pick = (accountAnswer: number) =>
      set({ accountAnswer, noNeedReason: undefined });
    return (
      <>
        <Label hint="单选">客户情况</Label>
        <div style={{ display: 'grid', gap: 8 }}>
          <Choice
            active={a.accountAnswer === ACCOUNT_ANSWER.NEW_NEED}
            title="客户有新需求"
            desc="保存后打开新建询盘，客户已带好；这笔需求进入「商机成交」"
            onClick={() => pick(ACCOUNT_ANSWER.NEW_NEED)}
          />
          <Choice
            active={a.accountAnswer === ACCOUNT_ANSWER.CROSS}
            title="聊了新品类"
            desc="进入 G6 交叉销售"
            onClick={() => pick(ACCOUNT_ANSWER.CROSS)}
          >
            <Input
              style={{ width: 240 }}
              maxLength={CROSS_CATEGORY_MAX}
              placeholder="品类，例如 HMI"
              value={a.crossCategory}
              onChange={(e) => set({ crossCategory: e.target.value })}
            />
          </Choice>
          <Choice
            active={a.accountAnswer === ACCOUNT_ANSWER.NO_NEED}
            title="当前没有需求"
            desc={`选原因；下次联系默认按等级间隔${item.followDays ? `（${item.bizGrade ?? ''} 级 ${item.followDays} 天）` : ''}，可改`}
            onClick={() => pick(ACCOUNT_ANSWER.NO_NEED)}
          >
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {NO_NEED_REASONS.map((r) => (
                <Tag.CheckableTag
                  key={r.value}
                  checked={a.noNeedReason === r.value}
                  onChange={(c) =>
                    set({ noNeedReason: c ? r.value : undefined })
                  }
                >
                  {r.label}
                </Tag.CheckableTag>
              ))}
            </div>
          </Choice>
          <Choice
            active={a.accountAnswer === ACCOUNT_ANSWER.NO_REPLY}
            title="没回复"
            desc="阶段不变，连续 3 次没回复建议转培育"
            onClick={() => pick(ACCOUNT_ANSWER.NO_REPLY)}
          />
        </div>
        {item.strategic && (
          <Checkbox
            style={{ marginTop: 10 }}
            checked={a.quarterReview}
            onChange={(e) => set({ quarterReview: e.target.checked })}
          >
            <span style={{ color: palette.sub }}>
              这次是季度回顾（回顾日期更新为今天，90 天后再提醒）
            </span>
          </Checkbox>
        )}
      </>
    );
  }

  if (kind === 'develop') {
    const pick = (touchResult: number) => set({ touchResult });
    const seq = item.sequenceDay
      ? `序列下一封第 ${item.sequenceDay} 天 · `
      : '';
    return (
      <>
        <Label hint="单选">触达结果</Label>
        <div style={{ fontSize: 12, color: palette.mute, marginBottom: 6 }}>
          {seq}已触达 {item.touchCount ?? 0} 次
          {item.firstTouchDate && ` · 首次触达 ${fmtDate(item.firstTouchDate)}`}
        </div>
        <div style={{ display: 'grid', gap: 8 }}>
          <Choice
            active={a.touchResult === TOUCH.SENT}
            title="已发出触达"
            desc="触达次数 +1，下方「方式」选这次用的渠道"
            onClick={() => pick(TOUCH.SENT)}
          />
          <Choice
            active={a.touchResult === TOUCH.REPLIED}
            title="客户回复了"
            desc="进入 D6 建立连接"
            onClick={() => pick(TOUCH.REPLIED)}
          />
          <Choice
            active={a.touchResult === TOUCH.SIGNAL}
            title="出现采购信号"
            desc="填型号、数量、时间；进入 D7，可以转为商机"
            onClick={() => pick(TOUCH.SIGNAL)}
          >
            <Input
              maxLength={SIGNAL_MAX}
              placeholder="例如：6ES7 214-1AG40 ×6，下月采购"
              value={a.signalText}
              onChange={(e) => set({ signalText: e.target.value })}
            />
          </Choice>
          <Choice
            active={a.touchResult === TOUCH.NO_REPLY}
            title="没回复"
            desc="阶段不变，按序列下一封提醒"
            onClick={() => pick(TOUCH.NO_REPLY)}
          />
          <Choice
            active={a.touchResult === TOUCH.REFUSED}
            danger
            title="明确拒绝"
            desc="保存后接着打开停止开发，原因预选「明确拒绝」"
            onClick={() => pick(TOUCH.REFUSED)}
          />
        </div>
      </>
    );
  }

  return null;
};

export default StageQuestions;
