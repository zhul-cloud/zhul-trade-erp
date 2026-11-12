import { history } from '@umijs/max';
import { App, Button, Form, Input, Modal, Radio, Skeleton, Table } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { readBizError } from '../service';
import { GRADE_NAMES, type Grade, profileApi, type RfmDetail } from './service';
import { GradeBadge, Stars } from './ui';

const fmt = (n: number) => Number(n).toLocaleString('en-US');

/** R/F/M 每一档的文字：R 是天数上限，F、M 是下限 */
const tiers = (kind: 'r' | 'f' | 'm', a: number[]) => {
  if (kind === 'r')
    return [
      `${a[0]} 天以内`,
      `${a[0]}–${a[1]} 天`,
      `${a[1]}–${a[2]} 天`,
      `${a[2]}–${a[3]} 天`,
      `${a[3]} 天以上`,
    ];
  if (kind === 'f')
    return [
      `${a[0]} 张及以上`,
      ...[1, 2, 3].map((i) =>
        a[i] === a[i - 1] - 1 ? `${a[i]} 张` : `${a[i]}–${a[i - 1] - 1} 张`,
      ),
      a[3] <= 1 ? '0 张' : `少于 ${a[3]} 张`,
    ];
  return [
    `${fmt(a[0])} 及以上`,
    ...[1, 2, 3].map((i) => `${fmt(a[i])} 以上`),
    `${fmt(a[3])} 以下`,
  ];
};

const gradeRange = (cutoffs: number[], g: number) =>
  g === 1
    ? `≥${cutoffs[0]}`
    : g === 4
      ? `<${cutoffs[2]}`
      : `${cutoffs[g - 1]}–${(cutoffs[g - 2] - 0.1).toFixed(1)}`;

const adjustText = (g: Pick<Grade, 'calc' | 'adjust'>) =>
  g.adjust > 0 ? '上调一级' : g.adjust < 0 ? '下调一级' : '';

/** 调整经营等级（原型 C11）：只能上调或下调一级，原因必填；选「不调整」即取消调整 */
export const AdjustModal: React.FC<{
  open: boolean;
  customerId: number;
  customerName: string;
  grade: Grade;
  potential: number;
  onClose: () => void;
  onDone: () => void;
}> = ({
  open,
  customerId,
  customerName,
  grade,
  potential,
  onClose,
  onDone,
}) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm<{ adjust: number; reason?: string }>();
  const [saving, setSaving] = useState(false);
  const adjust = Form.useWatch('adjust', form);

  useEffect(() => {
    if (open)
      form.setFieldsValue({ adjust: grade.adjust, reason: grade.adjustReason });
  }, [open, form, grade]);

  const submit = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      await profileApi.adjustGrade(customerId, v.adjust, v.reason?.trim());
      message.success(v.adjust === 0 ? '已取消调整' : '经营等级已调整');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const calc = grade.calc;
  return (
    <Modal
      open={open}
      title={
        <div>
          调整经营等级
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            {customerName}
          </div>
        </div>
      }
      onCancel={onClose}
      onOk={submit}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      destroyOnHidden
      width={560}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '12px 14px',
          margin: '8px 0 20px',
          borderRadius: 12,
          background: palette.inset,
          fontSize: 13,
          color: palette.sub,
        }}
      >
        计算等级 <GradeBadge value={calc} />
        <span className="num">
          R{grade.r} · F{grade.f} · M{grade.m} · 综合 {grade.score}
        </span>
        <span style={{ marginLeft: 'auto' }}>潜力</span>
        <Stars value={potential} />
      </div>
      <Form form={form} layout="vertical">
        <Form.Item
          name="adjust"
          label="调整为"
          extra="只能上调或下调一级；每天重算时保留调整方向"
          rules={[{ required: true, message: '请选择调整方向' }]}
        >
          <Radio.Group
            optionType="button"
            options={[
              {
                value: 1,
                label: `上调一级 → ${GRADE_NAMES[calc - 1] ?? ''}`,
                disabled: calc <= 1,
              },
              { value: 0, label: `不调整（${GRADE_NAMES[calc]}）` },
              {
                value: -1,
                label: `下调一级 → ${GRADE_NAMES[calc + 1] ?? ''}`,
                disabled: calc >= 4,
              },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="reason"
          label="调整原因"
          rules={[
            {
              required: adjust !== 0,
              whitespace: true,
              message: '请填写调整原因',
            },
          ]}
        >
          <Input.TextArea
            rows={3}
            maxLength={200}
            showCount
            disabled={adjust === 0}
            placeholder="例如：系统集成商，项目多、采购频繁"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

/** 经营等级明细（原型 C12）：首单、最近订单、R/F/M 各落在哪一档、参与计算的订单 */
export const RfmModal: React.FC<{
  open: boolean;
  customerId: number;
  customerName: string;
  canAdjust: boolean;
  onAdjust: () => void;
  onClose: () => void;
}> = ({ open, customerId, customerName, canAdjust, onAdjust, onClose }) => {
  const { palette } = useAppTheme();
  const [data, setData] = useState<RfmDetail>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (!open) return;
    setData(undefined);
    setError(undefined);
    profileApi
      .rfm(customerId)
      .then(setData)
      .catch((e) => setError(readBizError(e).message));
  }, [open, customerId]);

  const stat = (label: string, value: React.ReactNode) => (
    <div
      style={{
        flex: '1 1 140px',
        padding: '10px 12px',
        borderRadius: 12,
        background: palette.inset,
      }}
    >
      <div style={{ fontSize: 12, color: palette.mute }}>{label}</div>
      <div
        className="num"
        style={{
          marginTop: 4,
          fontSize: 15,
          fontWeight: 700,
          color: palette.ink,
        }}
      >
        {value}
      </div>
    </div>
  );

  const box = (
    kind: 'r' | 'f' | 'm',
    title: string,
    hint: string,
    value: string,
    score: number | undefined,
    limits: number[],
  ) => (
    <div
      style={{
        flex: '1 1 200px',
        padding: 14,
        borderRadius: 14,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
        <b style={{ color: palette.ink }}>{title}</b>
        <span style={{ fontSize: 12, color: palette.mute }}>{hint}</span>
        <b style={{ marginLeft: 'auto', color: palette.link, fontSize: 16 }}>
          {score ?? '—'} 分
        </b>
      </div>
      <div
        className="num"
        style={{ margin: '6px 0 8px', fontWeight: 600, color: palette.ink }}
      >
        {value}
      </div>
      {tiers(kind, limits).map((t, i) => {
        const s = 5 - i;
        const hit = s === score;
        return (
          <div
            key={t}
            style={{
              display: 'flex',
              gap: 10,
              padding: '5px 8px',
              borderRadius: 8,
              fontSize: 12,
              color: hit ? palette.ink : palette.sub,
              fontWeight: hit ? 600 : 400,
              background: hit ? palette.accentSoft : undefined,
            }}
          >
            <span style={{ color: hit ? palette.link : palette.mute }}>
              {s}
            </span>
            {t}
          </div>
        );
      })}
    </div>
  );

  let body: React.ReactNode = <Skeleton active paragraph={{ rows: 8 }} />;
  if (error) body = <div style={{ color: palette.red }}>{error}</div>;
  else if (data && !data.calc)
    body = (
      <div style={{ padding: '24px 0', color: palette.mute }}>
        这个客户还没有成交订单，没有经营等级。成交后每天凌晨自动计算。
      </div>
    );
  else if (data) {
    const w = data.weights;
    body = (
      <>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          {stat('首单日期', data.firstOrderDate ?? '—')}
          {stat(
            '最近订单',
            data.lastOrderDate
              ? `${data.lastOrderDate} · ${data.daysSinceLast} 天前`
              : '—',
          )}
          {stat('近 12 个月订单', `${data.ordersInYear} 张`)}
          {stat('近 12 个月销售额', formatAmount(data.usdInYear, 'USD'))}
          {stat('累计销售额', formatAmount(data.usdTotal, 'USD'))}
        </div>
        <div
          style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 16 }}
        >
          {box(
            'r',
            'R 近度',
            '距最近一次订单',
            `${data.daysSinceLast ?? '—'} 天`,
            data.r,
            data.recencyDays,
          )}
          {box(
            'f',
            'F 频次',
            '近 12 个月订单数',
            `${data.ordersInYear} 张`,
            data.f,
            data.frequency,
          )}
          {box(
            'm',
            'M 金额',
            '近 12 个月销售额',
            formatAmount(data.usdInYear, 'USD'),
            data.m,
            data.monetaryUsd.map(Number),
          )}
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            flexWrap: 'wrap',
            marginTop: 16,
            padding: '12px 16px',
            borderRadius: 12,
            background: palette.accentSoft,
            border: `1px solid ${palette.accentLine}`,
            fontSize: 13,
          }}
        >
          <span style={{ color: palette.mute }}>综合分数</span>
          <b className="num" style={{ fontSize: 15, color: palette.ink }}>
            {w[0]} × {data.r} + {w[1]} × {data.f} + {w[2]} × {data.m} ={' '}
            {data.score}
          </b>
          <span style={{ color: palette.sub }}>
            → 计算等级 {GRADE_NAMES[data.calc]}（
            {gradeRange(data.cutoffs.map(Number), data.calc)}）
          </span>
          <span
            style={{
              marginLeft: 'auto',
              display: 'flex',
              gap: 12,
              alignItems: 'center',
            }}
          >
            {data.adjust !== 0 && (
              <span style={{ color: palette.violet, fontWeight: 600 }}>
                {adjustText(data)} → 最终 {GRADE_NAMES[data.finalGrade]}
              </span>
            )}
            <span style={{ color: palette.cyan, fontWeight: 600 }}>
              建议每 {data.followDays} 天跟进
            </span>
          </span>
        </div>
        {data.adjust !== 0 && (
          <div style={{ marginTop: 10, fontSize: 12, color: palette.sub }}>
            调整原因：{data.adjustReason}
            {data.adjustByName ? ` · ${data.adjustByName}` : ''}
            {data.adjustAt ? ` ${formatDateTime(data.adjustAt)}` : ''}
          </div>
        )}
        <h3 style={{ margin: '20px 0 10px', fontSize: 14, color: palette.ink }}>
          参与计算的订单（近 12 个月）
        </h3>
        <Table
          size="small"
          rowKey="id"
          pagination={false}
          dataSource={data.orders}
          locale={{ emptyText: '近 12 个月没有订单' }}
          columns={[
            {
              title: '订单号',
              dataIndex: 'soNo',
              render: (v, r) => (
                <button
                  type="button"
                  className="zhul-link"
                  onClick={() => history.push(`/sales/orders/${r.id}`)}
                >
                  {v}
                </button>
              ),
            },
            { title: '下单日期', dataIndex: 'salesDate' },
            {
              title: '原币金额',
              dataIndex: 'amount',
              align: 'right',
              render: (v, r) => (
                <span className="num">{formatAmount(v, r.currencyCode)}</span>
              ),
            },
            {
              title: '折合美元',
              dataIndex: 'usd',
              align: 'right',
              render: (v, r) => (
                <span className="num">
                  {formatAmount(v, 'USD')}
                  {r.currencyCode !== 'USD' && data.usdRate && (
                    <div style={{ fontSize: 11, color: palette.mute }}>
                      {formatAmount(r.amountCny, 'CNY')} ÷ {data.usdRate}
                    </div>
                  )}
                </span>
              ),
            },
          ]}
          summary={() => (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={3}>
                合计
              </Table.Summary.Cell>
              <Table.Summary.Cell index={3} align="right">
                <b className="num">{formatAmount(data.usdInYear, 'USD')}</b>
              </Table.Summary.Cell>
            </Table.Summary.Row>
          )}
        />
      </>
    );
  }

  return (
    <Modal
      open={open}
      title={
        <div>
          经营等级明细
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            {customerName}
            {data?.calcAt ? ` · 计算于 ${formatDateTime(data.calcAt)}` : ''}
          </div>
        </div>
      }
      onCancel={onClose}
      width={880}
      destroyOnHidden
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{ marginRight: 'auto', fontSize: 12, color: palette.mute }}
          >
            非美元订单按当前美元汇率折算；分界与权重在「系统参数」里可调
          </span>
          {canAdjust && !!data?.calc && (
            <Button onClick={onAdjust}>调整等级</Button>
          )}
          <Button type="primary" onClick={onClose}>
            关闭
          </Button>
        </div>
      }
    >
      {body}
    </Modal>
  );
};

/** 经营等级卡片（原型 C1）：等级、是否调整、R/F/M、建议跟进间隔，点开看明细 */
const GradeCard: React.FC<{
  customerId: number;
  customerName: string;
  grade: Grade;
  potential: number;
  canAdjust: boolean;
  onChanged: () => void;
}> = ({
  customerId,
  customerName,
  grade: g,
  potential,
  canAdjust,
  onChanged,
}) => {
  const { palette } = useAppTheme();
  const [rfmOpen, setRfmOpen] = useState(false);
  const [adjustOpen, setAdjustOpen] = useState(false);
  const dealt = g.finalGrade > 0;
  return (
    <section
      aria-label="经营等级"
      style={{
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        borderRadius: 16,
        padding: '14px 20px',
        marginBottom: 16,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <h2
          style={{
            margin: 0,
            fontSize: 15,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          经营等级
        </h2>
        {dealt ? (
          <button
            type="button"
            title="查看 RFM 明细"
            onClick={() => setRfmOpen(true)}
            style={{
              padding: 0,
              border: 0,
              background: 'none',
              cursor: 'pointer',
            }}
          >
            <GradeBadge value={g.finalGrade} size="large" />
          </button>
        ) : (
          <GradeBadge value={0} />
        )}
        {dealt && g.adjust !== 0 && (
          <span
            style={{
              padding: '2px 10px',
              borderRadius: 10,
              fontSize: 12,
              color: palette.violet,
              background: palette.violetSoft,
            }}
          >
            已调整 · 计算等级 {GRADE_NAMES[g.calc]}，{adjustText(g)}
          </span>
        )}
        {dealt && (
          <>
            <span className="num" style={{ fontSize: 12, color: palette.sub }}>
              R{g.r} · F{g.f} · M{g.m} · 综合 {g.score}
            </span>
            <span
              style={{ fontSize: 12, fontWeight: 600, color: palette.cyan }}
            >
              建议每 {g.followDays} 天跟进一次
            </span>
          </>
        )}
        {!dealt && (
          <span style={{ fontSize: 12, color: palette.mute }}>
            还没有成交订单，成交后每天凌晨按 RFM 自动计算
          </span>
        )}
        <span
          style={{
            marginLeft: 'auto',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            fontSize: 12,
          }}
        >
          {g.calcAt && (
            <span style={{ color: palette.mute }}>
              计算于 {formatDateTime(g.calcAt)}
            </span>
          )}
          {dealt && (
            <button
              type="button"
              className="zhul-link"
              onClick={() => setRfmOpen(true)}
            >
              查看 RFM 明细
            </button>
          )}
          {dealt && canAdjust && (
            <Button size="small" onClick={() => setAdjustOpen(true)}>
              调整
            </Button>
          )}
        </span>
      </div>
      {dealt && g.adjust !== 0 && g.adjustReason && (
        <div style={{ marginTop: 8, fontSize: 12, color: palette.sub }}>
          调整原因：{g.adjustReason}
          {g.adjustByName ? ` · ${g.adjustByName}` : ''}
          {g.adjustAt ? ` ${formatDateTime(g.adjustAt)}` : ''}
        </div>
      )}
      <RfmModal
        open={rfmOpen}
        customerId={customerId}
        customerName={customerName}
        canAdjust={canAdjust}
        onAdjust={() => {
          setRfmOpen(false);
          setAdjustOpen(true);
        }}
        onClose={() => setRfmOpen(false)}
      />
      <AdjustModal
        open={adjustOpen}
        customerId={customerId}
        customerName={customerName}
        grade={g}
        potential={potential}
        onClose={() => setAdjustOpen(false)}
        onDone={() => {
          setAdjustOpen(false);
          onChanged();
        }}
      />
    </section>
  );
};

export default GradeCard;
