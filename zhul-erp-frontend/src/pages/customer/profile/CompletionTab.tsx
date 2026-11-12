import {
  ArrowRightOutlined,
  CaretRightOutlined,
  CheckCircleOutlined,
  CheckOutlined,
  CopyOutlined,
  LoadingOutlined,
  StarFilled,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import { App, Button, Checkbox, Select, Skeleton } from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictOptions } from '@/utils/dict';
import {
  BackgroundActions,
  PriorityPill,
  useBackgroundReports,
} from '../background/components';
import { useBackgroundPrecheck } from '../background/precheck';
import Suggestions from '../background/Suggestions';
import {
  type CompletionItem,
  type CompletionRow,
  type CompletionStep,
  type CompletionWizard,
  completionApi,
  reportPath,
  STATUS,
} from '../background/service';
import { LIST_PATH } from '../components';
import { ROLE_OPTIONS } from '../constants';
import { readBizError } from '../service';
import { ContactModal } from './ContactsCard';
import ModuleEditModal, { ALL_FIELDS } from './ModuleEditModal';
import { COUNTRY_PATH } from './ProfileModules';
import {
  type ContactPayload,
  filled,
  PROFILE_DICTS,
  type Profile,
  type ProfilePatch,
  profileApi,
} from './service';
import { GradeBadge, Progress5, Stars } from './ui';

const PAGE_SIZE = 20;
const MULTI = new Set(['protocols', 'voltages', 'certifications']);
const DICTS = PROFILE_DICTS as Record<string, string>;
type Option = { value: number; label: string };

/** 编辑值的键：联系人字段加 c. 前缀，避免和客户字段（如 name）重名 */
const valueKey = (i: CompletionItem) => `${i.contact ? 'c.' : ''}${i.key}`;

/** 这一项能不能在向导里直接点选：字典项、公司类型、潜力评级；联系人字段要有联系人 */
const inline = (i: CompletionItem, hasContact: boolean) =>
  i.contact
    ? hasContact && !!DICTS[i.key]
    : i.key === 'customerRole' || i.key === 'potentialRating' || !!DICTS[i.key];

/** 点选：一排可切换的小按钮（第③步） */
const Chips: React.FC<{
  options: Option[];
  multi: boolean;
  value?: number | number[];
  onChange: (v?: number | number[]) => void;
}> = ({ options, multi, value, onChange }) => {
  const { palette } = useAppTheme();
  const picked = (v: number) =>
    multi ? ((value as number[]) ?? []).includes(v) : value === v;
  const toggle = (v: number) => {
    if (!multi) return onChange(value === v ? undefined : v);
    const cur = (value as number[]) ?? [];
    return onChange(cur.includes(v) ? cur.filter((x) => x !== v) : [...cur, v]);
  };
  return (
    <span style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
      {options.map((o) => (
        <button
          type="button"
          key={o.value}
          aria-pressed={picked(o.value)}
          onClick={() => toggle(o.value)}
          style={{
            height: 30,
            padding: '0 12px',
            borderRadius: 15,
            fontSize: 13,
            cursor: 'pointer',
            color: picked(o.value) ? palette.link : palette.sub,
            background: picked(o.value) ? palette.accentSoft : 'transparent',
            border: `1px solid ${picked(o.value) ? palette.accentLine : palette.hairline}`,
          }}
        >
          {o.label}
        </button>
      ))}
    </span>
  );
};

/** 字段的录入控件：第③步点选，第④步下拉 */
const Choice: React.FC<{
  item: CompletionItem;
  variant: 'chips' | 'select';
  value?: number | number[];
  onChange: (v?: number | number[]) => void;
  suggestedStars?: number;
}> = ({ item, variant, value, onChange, suggestedStars }) => {
  const { palette } = useAppTheme();
  const dict = useDictOptions(DICTS[item.key] ?? '', '');
  const multi = MULTI.has(item.key);
  if (item.key === 'potentialRating') {
    return (
      <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            type="button"
            key={n}
            aria-label={`${n} 星`}
            onClick={() => onChange(value === n ? undefined : n)}
            style={{
              width: 30,
              height: 30,
              borderRadius: 8,
              cursor: 'pointer',
              color: (value as number) >= n ? '#FBBF24' : palette.faint,
              background:
                (value as number) >= n ? palette.orangeSoft : 'transparent',
              border: `1px solid ${palette.hairline}`,
            }}
          >
            <StarFilled />
          </button>
        ))}
        {suggestedStars ? (
          <span style={{ fontSize: 12, color: palette.violet }}>
            背调建议 {suggestedStars} 星
          </span>
        ) : null}
      </span>
    );
  }
  const options =
    item.key === 'customerRole' && !item.contact ? ROLE_OPTIONS : dict.options;
  if (variant === 'chips') {
    return (
      <Chips
        options={options}
        multi={multi}
        value={value}
        onChange={onChange}
      />
    );
  }
  return (
    <Select
      mode={multi ? 'multiple' : undefined}
      allowClear
      placeholder="客户回答后在这里选"
      options={options}
      value={value}
      onChange={(v) => onChange(v ?? undefined)}
      style={{ width: '100%' }}
    />
  );
};

const STEP_TONE = ['green', 'violet', 'link', 'orange'] as const;

const StepCard: React.FC<{
  step: CompletionStep;
  subtitle: React.ReactNode;
  children?: React.ReactNode;
}> = ({ step, subtitle, children }) => {
  const { palette } = useAppTheme();
  const tone = palette[STEP_TONE[step.step - 1]];
  const done = step.step > 1 && step.items.length === 0;
  return (
    <section
      aria-label={step.name}
      style={{
        padding: 16,
        borderRadius: 14,
        background: palette.inset,
        border: `1px solid ${step.step === 1 ? `${palette.green}55` : palette.hairline}`,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          flexWrap: 'wrap',
        }}
      >
        <span
          style={{
            width: 24,
            height: 24,
            borderRadius: 12,
            display: 'inline-grid',
            placeItems: 'center',
            fontSize: 12,
            fontWeight: 700,
            color: '#fff',
            background: done || step.step === 1 ? palette.green : tone,
          }}
        >
          {done || step.step === 1 ? <CheckOutlined /> : step.step}
        </span>
        <span style={{ fontSize: 14, fontWeight: 700, color: palette.ink }}>
          {step.name}
        </span>
        <span style={{ fontSize: 12, color: palette.mute }}>
          {done ? '已完成' : subtitle}
        </span>
      </div>
      {!done && children}
    </section>
  );
};

/** 左侧：待补全的客户，按价值排好 */
const Queue: React.FC<{
  rows: CompletionRow[];
  total: number;
  selected?: number;
  loadingMore: boolean;
  onSelect: (id: number) => void;
  onMore: () => void;
}> = ({ rows, total, selected, loadingMore, onSelect, onMore }) => {
  const { palette } = useAppTheme();
  return (
    <aside
      aria-label="待补全的客户"
      style={{
        flex: '0 0 340px',
        maxWidth: '100%',
        maxHeight: 'calc(100vh - 180px)',
        overflowY: 'auto',
        position: 'sticky',
        top: 16,
        padding: 16,
        borderRadius: 16,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 6,
        }}
      >
        <span style={{ fontWeight: 700, color: palette.ink }}>待补全</span>
        <span
          className="num"
          style={{
            padding: '0 8px',
            borderRadius: 10,
            fontSize: 12,
            background: palette.inset,
            color: palette.sub,
          }}
        >
          {total.toLocaleString()}
        </span>
        <span style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}>
          按价值排好
        </span>
      </div>
      {rows.map((r) => {
        const on = r.customerId === selected;
        return (
          <button
            type="button"
            key={r.customerId}
            onClick={() => onSelect(r.customerId)}
            aria-current={on}
            style={{
              textAlign: 'left',
              padding: '10px 12px',
              borderRadius: 12,
              cursor: 'pointer',
              background: on ? palette.accentSoft : 'transparent',
              border: `1px solid ${on ? palette.accentLine : 'transparent'}`,
              display: 'flex',
              flexDirection: 'column',
              gap: 4,
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              {on && <CaretRightOutlined style={{ color: palette.link }} />}
              <span
                style={{
                  flex: 1,
                  minWidth: 0,
                  fontWeight: 600,
                  color: palette.ink,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {r.name || `未填客户名称 · ${r.customerCode}`}
              </span>
              {r.bizGrade > 0 ? (
                <GradeBadge value={r.bizGrade} />
              ) : r.potentialRating > 0 ? (
                <Stars value={r.potentialRating} />
              ) : null}
            </span>
            <span
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                fontSize: 12,
              }}
            >
              <span
                className="num"
                style={{ color: palette.orange, fontWeight: 600 }}
              >
                {r.done}/5
              </span>
              <span style={{ color: palette.mute }}>
                还差 {r.missingCount} 项{r.bizGrade === 0 ? ' · 未成交' : ''}
              </span>
              {r.askedDays != null && (
                <span
                  style={{
                    padding: '0 6px',
                    borderRadius: 6,
                    color: palette.violet,
                    background: palette.violetSoft,
                  }}
                >
                  已问 {r.askedDays} 天
                </span>
              )}
            </span>
          </button>
        );
      })}
      {rows.length < total && (
        <Button type="link" loading={loadingMore} onClick={onMore}>
          加载更多
        </Button>
      )}
      <div
        style={{
          fontSize: 12,
          color: palette.mute,
          lineHeight: 1.6,
          marginTop: 6,
        }}
      >
        成交客户按经营等级 S→C，未成交的按潜力；已建档的不在这里。
      </div>
    </aside>
  );
};

/** 右侧：一个客户的四步补全（原型 C5） */
const Wizard: React.FC<{
  wizard: CompletionWizard;
  profile?: Profile;
  grade?: number;
  onSaved: (next: boolean) => void;
  onReload: () => void;
}> = ({ wizard: w, profile, grade, onSaved, onReload }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['partner:customer:edit'];
  const [values, setValues] = useState<
    Record<string, number | number[] | undefined>
  >({});
  const [saving, setSaving] = useState<'save' | 'next'>();
  const [editModule, setEditModule] = useState<number>();
  const [contactOpen, setContactOpen] = useState(false);
  const bg = useBackgroundReports(w.customerId);
  const precheck = useBackgroundPrecheck(w.customerId);
  /** 背调前检查「去补充」：档案编辑对应页签或联系人弹窗 */
  const fixPrecheck = canEdit
    ? (target: string) => {
        if (target.startsWith('profile:')) {
          setEditModule(Number(target.slice('profile:'.length)));
        } else if (target === 'contact') {
          setContactOpen(true);
        }
      }
    : undefined;

  useEffect(() => setValues({}), [w]);

  // 背调完成后刷新向导，第②步出现字段建议
  const wasRunning = useRef(false);
  useEffect(() => {
    if (wasRunning.current && !bg.running) onReload();
    wasRunning.current = bg.running;
  }, [bg.running, onReload]);

  const step = (n: number) => w.steps.find((s) => s.step === n);
  const s1 = step(1);
  const s2 = step(2);
  const s3 = step(3);
  const s4 = step(4);
  const report = s2?.report;
  const hasContact = w.contactId != null;
  const contact = profile?.contacts.find((c) => c.id === w.contactId);

  const set = (i: CompletionItem, v?: number | number[]) =>
    setValues((cur) => ({ ...cur, [valueKey(i)]: v }));

  /** 不能直接点选的项：客户字段打开编辑弹窗（不在弹窗里的去编辑页），联系人字段打开联系人弹窗 */
  const openEditor = (i: CompletionItem) => {
    if (i.contact) setContactOpen(true);
    else if (ALL_FIELDS.includes(i.key)) setEditModule(i.module);
    else history.push(`${LIST_PATH}/${w.customerId}/edit?from=detail`);
  };

  const editorLink = (i: CompletionItem) =>
    canEdit && (
      <a style={{ fontSize: 13 }} onClick={() => openEditor(i)}>
        {i.contact && !hasContact ? '添加联系人' : '去填写'}
      </a>
    );

  const allItems = [s2, s3, s4].flatMap((s) => s?.items ?? []);
  const remaining = allItems.filter((i) => !filled(values[valueKey(i)]));
  const entered = Object.entries(values).filter(([, v]) => filled(v));

  const save = async (next: boolean) => {
    const customer = entered.filter(([k]) => !k.startsWith('c.'));
    const contactVals = entered.filter(([k]) => k.startsWith('c.'));
    if (entered.length === 0) {
      if (next) onSaved(true);
      return;
    }
    setSaving(next ? 'next' : 'save');
    try {
      if (customer.length) {
        await profileApi.update(w.customerId, {
          ...Object.fromEntries(customer),
          fields: customer.map(([k]) => k),
        } as unknown as ProfilePatch);
      }
      if (contactVals.length && contact) {
        await profileApi.updateContact(w.customerId, contact.id, {
          ...contact,
          ...Object.fromEntries(contactVals.map(([k, v]) => [k.slice(2), v])),
        } as unknown as ContactPayload);
      }
      message.success(`已保存 ${entered.length} 项`);
      onSaved(next);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(undefined);
    }
  };

  const copy = (text: string) =>
    navigator.clipboard
      .writeText(text)
      .then(() => message.success('已复制'))
      .catch(() => message.error('复制失败，请手动选中复制'));

  const askedAll = !!s4?.items.length && s4.items.every((i) => i.askedAt);
  const toggleAsked = (asked: boolean) =>
    s4 &&
    profileApi
      .asked(
        w.customerId,
        s4.items.map((i) => i.sourceKey),
        asked,
      )
      .then(onReload)
      .catch((e) => message.error(readBizError(e).message));

  const labelCell = (i: CompletionItem) => (
    <span
      style={{
        width: 120,
        flex: '0 0 120px',
        fontSize: 13,
        color: palette.sub,
      }}
    >
      {i.label}
      {i.contact && w.contactName ? ` · ${w.contactName}` : ''}
    </span>
  );

  return (
    <section
      aria-label={`补全 ${w.name}`}
      style={{
        flex: '1 1 440px',
        minWidth: 0,
        padding: 20,
        borderRadius: 16,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
      }}
    >
      <header
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
            fontSize: 18,
            fontWeight: 700,
            color: palette.ink,
          }}
        >
          {w.name || `未填客户名称 · ${w.customerCode}`}
        </h2>
        {grade ? <GradeBadge value={grade} /> : null}
        <span style={{ fontSize: 12, color: palette.mute }}>{w.country}</span>
        <span
          style={{
            marginLeft: 'auto',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
          }}
        >
          <span
            className="num"
            style={{ fontSize: 18, fontWeight: 700, color: palette.orange }}
          >
            {w.done} / 5
          </span>
          <Progress5 mask={(1 << w.done) - 1} />
          <a onClick={() => history.push(`${LIST_PATH}/${w.customerId}`)}>
            打开客户
          </a>
        </span>
      </header>

      {s1 && (
        <StepCard
          step={s1}
          subtitle={
            s1.autoFilled?.length
              ? `自动补上 ${s1.autoFilled.length} 项，不用填`
              : '不用填'
          }
        >
          {(s1.autoFilled?.length || s1.countryProfileMissing) && (
            <div
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 8,
                fontSize: 12,
              }}
            >
              {s1.autoFilled?.map((t) => (
                <span
                  key={t}
                  style={{
                    padding: '4px 10px',
                    borderRadius: 8,
                    color: palette.sub,
                    background: palette.card,
                  }}
                >
                  {t}
                </span>
              ))}
              {s1.countryProfileMissing && (
                <span style={{ color: palette.orange, padding: '4px 0' }}>
                  国家资料缺失（电压、外汇管制）
                  {access.systemCountryProfiles && (
                    <a
                      style={{ marginLeft: 8 }}
                      onClick={() => history.push(COUNTRY_PATH)}
                    >
                      去维护国家资料
                    </a>
                  )}
                </span>
              )}
            </div>
          )}
        </StepCard>
      )}

      {s2 && (
        <StepCard
          step={s2}
          subtitle={
            report?.status === STATUS.DONE
              ? `${s2.items.length} 项 · 背调已完成，勾选后保存即采纳`
              : report?.status === STATUS.RUNNING || bg.running
                ? `${s2.items.length} 项 · 背调中`
                : `${s2.items.length} 项 · 发起 AI 背调或上传报告后，在这里勾选采纳`
          }
        >
          {report?.status === STATUS.DONE ? (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span
                  className="num"
                  style={{
                    padding: '1px 8px',
                    borderRadius: 6,
                    fontSize: 12,
                    color: palette.violet,
                    background: palette.violetSoft,
                  }}
                >
                  {report.reportNo}
                </span>
                <PriorityPill value={report.priority} />
                {report.score > 0 && (
                  <span style={{ fontSize: 12, color: palette.sub }}>
                    {report.score} 分
                  </span>
                )}
                <a
                  style={{ marginLeft: 'auto', fontSize: 13 }}
                  onClick={() =>
                    history.push(reportPath(w.customerId, report.id))
                  }
                >
                  看完整报告
                </a>
              </div>
              <Suggestions
                customerId={w.customerId}
                reportId={report.id}
                suggestions={report.suggestions}
                canEdit={canEdit}
                compact
                onChanged={onReload}
              />
            </>
          ) : report?.status === STATUS.RUNNING || bg.running ? (
            <div style={{ fontSize: 13, color: palette.link }}>
              <LoadingOutlined /> 正在背调，完成后这里会列出字段建议
            </div>
          ) : (
            access['partner:customer:background'] && (
              <div style={{ display: 'flex', gap: 10 }}>
                <BackgroundActions
                  customerId={w.customerId}
                  customerName={w.name}
                  running={bg.running}
                  onStarted={bg.reload}
                  precheck={precheck}
                  onFix={fixPrecheck}
                />
              </div>
            )
          )}
          {s2.items.length > 0 && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              还缺：{s2.items.map((i) => i.label).join('、')}
              {canEdit && (
                <a
                  style={{ marginLeft: 8 }}
                  onClick={() => openEditor(s2.items[0])}
                >
                  手动填写
                </a>
              )}
            </div>
          )}
        </StepCard>
      )}

      {s3 && (
        <StepCard step={s3} subtitle="凭报价和沟通的印象，点选即可">
          {s3.items.map((i) => (
            <div
              key={valueKey(i)}
              style={{ display: 'flex', alignItems: 'center', gap: 12 }}
            >
              {labelCell(i)}
              {canEdit && inline(i, hasContact) ? (
                <Choice
                  item={i}
                  variant="chips"
                  value={values[valueKey(i)]}
                  onChange={(v) => set(i, v)}
                  suggestedStars={
                    report?.status === STATUS.DONE ? report.score : undefined
                  }
                />
              ) : (
                editorLink(i)
              )}
            </div>
          ))}
        </StepCard>
      )}

      {s4 && (
        <StepCard
          step={s4}
          subtitle={`${s4.items.length} 项 · 复制问法发给客户，拿到回答后填在这里`}
        >
          {s4.items.map((i) => (
            <div
              key={valueKey(i)}
              style={{
                padding: 12,
                borderRadius: 12,
                background: palette.card,
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 13, color: palette.sub }}>
                  {i.label}
                  {i.contact && w.contactName ? ` · ${w.contactName}` : ''}
                </span>
                {i.askedDays != null && (
                  <span style={{ fontSize: 12, color: palette.violet }}>
                    {i.askedByName} 已问 {i.askedDays} 天
                  </span>
                )}
                {i.question && (
                  <Button
                    size="small"
                    icon={<CopyOutlined />}
                    style={{ marginLeft: 'auto' }}
                    onClick={() => copy(i.question ?? '')}
                  >
                    复制
                  </Button>
                )}
              </div>
              {i.question && (
                <div style={{ fontSize: 14, color: palette.ink }}>
                  "{i.question}"
                </div>
              )}
              {canEdit && inline(i, hasContact) ? (
                <Choice
                  item={i}
                  variant="select"
                  value={values[valueKey(i)]}
                  onChange={(v) => set(i, v)}
                />
              ) : (
                <div>{editorLink(i)}</div>
              )}
            </div>
          ))}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {canEdit && (
              <Checkbox
                checked={askedAll}
                onChange={(e) => toggleAsked(e.target.checked)}
              >
                已经问了，等回复
              </Checkbox>
            )}
            {s4.copyAll && (
              <Button
                size="small"
                icon={<CopyOutlined />}
                style={{ marginLeft: 'auto' }}
                onClick={() => copy(s4.copyAll ?? '')}
              >
                复制全部问题
              </Button>
            )}
          </div>
        </StepCard>
      )}

      <footer
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <span
          style={{ fontSize: 13, color: palette.mute, flex: 1, minWidth: 200 }}
        >
          {remaining.length === 0
            ? '保存后这个客户就补全了'
            : `保存后还差 ${remaining.length} 项：${remaining.map((i) => i.label).join('、')}`}
        </span>
        {canEdit && (
          <Button
            loading={saving === 'save'}
            disabled={!!saving || entered.length === 0}
            onClick={() => save(false)}
          >
            保存
          </Button>
        )}
        <Button
          type="primary"
          icon={<ArrowRightOutlined />}
          loading={saving === 'next'}
          disabled={!!saving}
          onClick={() => save(true)}
        >
          {entered.length ? '保存，下一个客户' : '下一个客户'}
        </Button>
      </footer>

      {profile && (
        <ModuleEditModal
          open={editModule != null}
          module={editModule ?? 1}
          profile={profile}
          onClose={() => setEditModule(undefined)}
          onDone={() => {
            setEditModule(undefined);
            onReload();
            precheck.reload();
          }}
        />
      )}
      <ContactModal
        open={contactOpen}
        customerId={w.customerId}
        customerName={w.name}
        contact={contact}
        first={!hasContact}
        onClose={() => setContactOpen(false)}
        onDone={() => {
          setContactOpen(false);
          onReload();
          precheck.reload();
        }}
      />
    </section>
  );
};

/**
 * 客户管理「档案补全」页签（原型 C5，spec crm/customer-background「档案补全」）：
 * 左侧按价值排好的待补全客户，右侧把还缺的核心项按「谁能给出答案」分四步补。
 */
const CompletionTab: React.FC = () => {
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<CompletionRow[]>([]);
  const [total, setTotal] = useState(0);
  const [todayDone, setTodayDone] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<number>();
  const [wizard, setWizard] = useState<CompletionWizard>();
  const [profile, setProfile] = useState<Profile>();
  const [wizardError, setWizardError] = useState<string>();

  /** 重新取前 n 页（保存后刷新，保持已经加载的条数） */
  const loadQueue = useCallback(async (pages: number) => {
    const res = await completionApi.queue(1, PAGE_SIZE * pages);
    setRows(res.records);
    setTotal(res.total);
    setTodayDone(res.todayDone);
    return res.records;
  }, []);

  useEffect(() => {
    setLoading(true);
    loadQueue(1)
      .then((r) => setSelected(r[0]?.customerId))
      .catch((e) => setError(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [loadQueue]);

  const loadWizard = useCallback(() => {
    if (!selected) return;
    setWizardError(undefined);
    completionApi
      .wizard(selected)
      .then(setWizard)
      .catch((e) => setWizardError(readBizError(e).message));
    profileApi
      .get(selected)
      .then(setProfile)
      .catch(() => setProfile(undefined));
  }, [selected]);

  useEffect(() => {
    setWizard(undefined);
    loadWizard();
  }, [loadWizard]);

  const more = () => {
    setLoadingMore(true);
    completionApi
      .queue(page + 1, PAGE_SIZE)
      .then((res) => {
        setRows((cur) => [...cur, ...res.records]);
        setPage(page + 1);
      })
      .finally(() => setLoadingMore(false));
  };

  const onSaved = async (next: boolean) => {
    const index = rows.findIndex((r) => r.customerId === selected);
    const fresh = await loadQueue(page).catch(() => rows);
    if (!next) {
      loadWizard();
      return;
    }
    // 补完 5/5 的客户会离开队列，原位置就是下一个
    const stay = fresh.some((r) => r.customerId === selected);
    const target = fresh[stay ? index + 1 : index] ?? fresh[0];
    if (target?.customerId === selected) loadWizard();
    else setSelected(target?.customerId);
  };

  const grade = useMemo(
    () => rows.find((r) => r.customerId === selected)?.bizGrade,
    [rows, selected],
  );

  if (loading) {
    return (
      <div style={{ background: palette.card, borderRadius: 16, padding: 24 }}>
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    );
  }
  if (error) return <ErrorHint message={error} onRetry={() => loadQueue(1)} />;
  if (rows.length === 0) {
    return (
      <EmptyHint
        title="客户档案都补全了"
        description="新客户或档案有变化时，会自动回到这里。"
      />
    );
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {todayDone > 0 && (
        <div
          style={{
            alignSelf: 'flex-end',
            fontSize: 13,
            color: palette.green,
            fontWeight: 600,
          }}
        >
          <CheckCircleOutlined /> 今天已补完 {todayDone} 个客户
        </div>
      )}
      <div
        style={{
          display: 'flex',
          gap: 20,
          alignItems: 'flex-start',
          flexWrap: 'wrap',
        }}
      >
        <Queue
          rows={rows}
          total={total}
          selected={selected}
          loadingMore={loadingMore}
          onSelect={setSelected}
          onMore={more}
        />
        {wizardError ? (
          <div style={{ flex: '1 1 440px' }}>
            <ErrorHint message={wizardError} onRetry={loadWizard} />
          </div>
        ) : wizard ? (
          <Wizard
            wizard={wizard}
            profile={profile}
            grade={grade}
            onSaved={onSaved}
            onReload={loadWizard}
          />
        ) : (
          <div
            style={{
              flex: '1 1 440px',
              background: palette.card,
              borderRadius: 16,
              padding: 24,
            }}
          >
            <Skeleton active paragraph={{ rows: 10 }} />
          </div>
        )}
      </div>
    </div>
  );
};

export default CompletionTab;
