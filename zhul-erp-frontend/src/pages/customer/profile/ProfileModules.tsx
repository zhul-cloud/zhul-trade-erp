import { history } from '@umijs/max';
import React from 'react';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictOptions } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { Pill } from '../components';
import {
  INDUSTRY_OPTIONS,
  labelOf,
  PAYMENT_OPTIONS,
  ROLE_OPTIONS,
} from '../constants';
import { zoneLabel } from '../time';
import {
  FX_OPTIONS,
  MODULE_NAMES,
  PROFILE_DICTS,
  type Profile,
} from './service';
import { Chip, FieldRow, SourceTag, Stars } from './ui';

export const COUNTRY_PATH = '/system/country-profiles';

/** 五个模块各自的字典标签 */
export const useProfileDicts = () => {
  const size = useDictOptions(PROFILE_DICTS.companySize, '');
  const quality = useDictOptions(PROFILE_DICTS.qualityReq, '');
  const price = useDictOptions(PROFILE_DICTS.priceSensitivity, '');
  const customs = useDictOptions(PROFILE_DICTS.customsAbility, '');
  const design = useDictOptions(PROFILE_DICTS.designAbility, '');
  const protocol = useDictOptions(PROFILE_DICTS.protocols, '');
  const voltage = useDictOptions(PROFILE_DICTS.voltages, '');
  const cert = useDictOptions(PROFILE_DICTS.certifications, '');
  const repair = useDictOptions(PROFILE_DICTS.repairAbility, '');
  return {
    size,
    quality,
    price,
    customs,
    design,
    protocol,
    voltage,
    cert,
    repair,
  };
};

const ModuleCard: React.FC<{
  no: number;
  missing?: string[];
  onEdit?: () => void;
  children: React.ReactNode;
}> = ({ no, missing, onEdit, children }) => {
  const { palette } = useAppTheme();
  const done = !missing || missing.length === 0;
  return (
    <section
      aria-label={MODULE_NAMES[no - 1]}
      style={{
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        borderRadius: 16,
        padding: '16px 20px',
        marginBottom: 16,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          marginBottom: 6,
        }}
      >
        <span
          style={{
            width: 24,
            height: 24,
            borderRadius: 7,
            display: 'inline-grid',
            placeItems: 'center',
            fontSize: 12,
            fontWeight: 700,
            color: done ? palette.green : palette.orange,
            background: done ? palette.greenSoft : palette.orangeSoft,
          }}
        >
          {no}
        </span>
        <h2
          style={{
            margin: 0,
            fontSize: 15,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          {MODULE_NAMES[no - 1]}
        </h2>
        <Pill tone={done ? 'green' : 'orange'}>
          {done ? '已完成' : `缺 ${missing?.length}`}
        </Pill>
        {onEdit && (
          <button
            type="button"
            className="zhul-link"
            style={{ marginLeft: 'auto', fontSize: 13 }}
            onClick={onEdit}
          >
            编辑
          </button>
        )}
      </div>
      {children}
    </section>
  );
};

/**
 * 客户详情的画像模块（原型 C1）：①②③⑤ 四个模块，④ 决策人在右侧联系人卡片里。
 * 没标「选填」的是核心项；来源标签悬停看时间。
 */
const ProfileModules: React.FC<{
  profile: Profile;
  canEditCountry: boolean;
  onEdit?: (module: number) => void;
}> = ({ profile: p, canEditCountry, onEdit }) => {
  const { palette } = useAppTheme();
  const d = useProfileDicts();
  const { zhOf } = useCountries();
  const missing = (m: number) =>
    p.completeness.missing.find((x) => x.module === m)?.labels ?? [];
  const src = (key: string) => (
    <SourceTag source={p.sources[key]} customerId={p.customerId} />
  );
  const sys = <SourceTag system="系统" />;
  const multi = (codes: number[], labelOf: (v?: number) => string) =>
    codes.length
      ? codes.map((c) => <Chip key={c}>{labelOf(c)}</Chip>)
      : undefined;
  const cp = p.countryProfile;
  const edit = (m: number) => (onEdit ? () => onEdit(m) : undefined);
  const payment = labelOf(PAYMENT_OPTIONS, p.paymentMethod);
  const brands = p.currentBrands.length
    ? p.currentBrands.map((b) => <Chip key={b.name}>{b.name}</Chip>)
    : p.derived.topBrands.length
      ? p.derived.topBrands.map((b) => (
          <Chip key={b.name} count={b.count}>
            {b.name}
          </Chip>
        ))
      : undefined;
  const voltages = p.voltages.length ? p.voltages : (cp?.voltages ?? []);
  const certs = p.certifications.length
    ? p.certifications
    : (cp?.certifications ?? []);
  const link = (url: string, text: string) =>
    url ? (
      <a
        href={/^https?:\/\//.test(url) ? url : `https://${url}`}
        target="_blank"
        rel="noreferrer"
      >
        {text}
      </a>
    ) : null;

  return (
    <>
      <ModuleCard no={1} missing={missing(1)} onEdit={edit(1)}>
        <FieldRow
          label="公司类型"
          core
          value={labelOf(ROLE_OPTIONS, p.customerRole)}
          source={src('customerRole')}
        />
        <FieldRow
          label="行业"
          core
          value={labelOf(INDUSTRY_OPTIONS, p.industry)}
          source={src('industry')}
        />
        <FieldRow
          label="官网 / LinkedIn"
          core
          value={
            p.website || p.linkedin ? (
              <>
                {link(p.website, p.website)}
                {link(p.linkedin, 'LinkedIn 公司页')}
              </>
            ) : undefined
          }
          source={src(p.website ? 'website' : 'linkedin')}
        />
        <FieldRow
          label="公司规模"
          core
          value={d.size.labelOf(p.companySize)}
          source={src('companySize')}
        />
        <FieldRow
          label="潜力评级"
          core
          value={
            p.potentialRating ? <Stars value={p.potentialRating} /> : undefined
          }
          source={src('potentialRating')}
        />
        <FieldRow
          label="成立年份"
          value={p.foundedYear ?? undefined}
          source={src('foundedYear')}
        />
        <FieldRow
          label="其他社媒"
          value={link(p.socialUrl, p.socialUrl)}
          source={src('socialUrl')}
        />
        <FieldRow
          label="询盘"
          value={
            p.derived.inquiryCount
              ? `询盘 ${p.derived.inquiryCount} 次 · 首次 ${p.derived.firstInquiryDate} · 最近 ${p.derived.lastInquiryDate}`
              : '暂无'
          }
          source={sys}
        />
      </ModuleCard>

      <ModuleCard no={2} missing={missing(2)} onEdit={edit(2)}>
        <FieldRow
          label="主营产品"
          core
          value={p.mainProducts}
          source={src('mainProducts')}
        />
        <FieldRow
          label="质量要求"
          core
          value={d.quality.labelOf(p.qualityReq)}
          source={src('qualityReq')}
        />
        <FieldRow
          label="价格敏感度"
          core
          value={d.price.labelOf(p.priceSensitivity)}
          source={src('priceSensitivity')}
        />
        <FieldRow
          label="清关能力"
          core
          value={d.customs.labelOf(p.customsAbility)}
          source={src('customsAbility')}
        />
        <FieldRow
          label="付款习惯"
          core
          value={
            payment
              ? [
                  payment,
                  p.depositRatio != null && `${p.depositRatio}% 定金`,
                  p.paymentDays != null && `${p.paymentDays} 天`,
                ]
                  .filter(Boolean)
                  .join(' · ')
              : undefined
          }
          source={src('paymentMethod')}
        />
        <FieldRow
          label="近 12 个月成交"
          value={
            p.derived.ordersInYear
              ? `${formatAmount(p.derived.salesCnyInYear, 'CNY')} · ${p.derived.ordersInYear} 张订单`
              : '暂无'
          }
          source={sys}
        />
        <FieldRow
          label="年度采购量估算"
          value={
            p.estAnnualPurchase != null
              ? formatAmount(p.estAnnualPurchase, 'USD')
              : undefined
          }
          source={src('estAnnualPurchase')}
        />
        <FieldRow
          label="现有中国供应商"
          value={p.cnSuppliers}
          source={src('cnSuppliers')}
        />
      </ModuleCard>

      <ModuleCard no={3} missing={missing(3)} onEdit={edit(3)}>
        <FieldRow
          label="现有使用品牌"
          core
          value={brands}
          source={
            p.currentBrands.length ? (
              src('currentBrands')
            ) : brands ? (
              <SourceTag system="系统 · 询盘统计" />
            ) : undefined
          }
        />
        <FieldRow
          label="设计能力"
          core
          value={d.design.labelOf(p.designAbility)}
          source={src('designAbility')}
        />
        <FieldRow
          label="常用通讯协议"
          core
          value={multi(p.protocols, d.protocol.labelOf)}
          source={src('protocols')}
        />
        <FieldRow
          label="电源电压"
          core
          value={multi(voltages, d.voltage.labelOf)}
          source={
            p.voltages.length ? (
              src('voltages')
            ) : cp?.voltages.length ? (
              <SourceTag system={`国家资料 · ${zhOf(p.country)}`} />
            ) : undefined
          }
        />
        <FieldRow
          label="售后维修能力"
          core
          value={d.repair.labelOf(p.repairAbility)}
          source={src('repairAbility')}
        />
        <FieldRow
          label="认证要求"
          value={multi(certs, d.cert.labelOf)}
          source={
            p.certifications.length ? (
              src('certifications')
            ) : cp?.certifications.length ? (
              <SourceTag system="国家资料" />
            ) : undefined
          }
        />
      </ModuleCard>

      <ModuleCard no={5} missing={missing(5)} onEdit={edit(5)}>
        <FieldRow
          label="国家 / 城市 / 时区"
          core
          value={
            [zhOf(p.country), p.city, p.timezone && zoneLabel(p.timezone)]
              .filter(Boolean)
              .join(' · ') || undefined
          }
          source={src('city')}
        />
        {cp ? (
          <>
            <FieldRow
              label="插头 / 电压"
              value={[
                cp.plug,
                cp.voltages.map((v) => d.voltage.labelOf(v)).join(' · '),
              ]
                .filter(Boolean)
                .join(' · ')}
              source={<SourceTag system="国家资料" />}
            />
            <FieldRow
              label="外汇管制"
              core
              value={
                cp.fxControl ? (
                  <>
                    <Pill
                      tone={
                        cp.fxControl === 3
                          ? 'orange'
                          : cp.fxControl === 2
                            ? 'violet'
                            : 'green'
                      }
                    >
                      {labelOf(FX_OPTIONS, cp.fxControl)}
                    </Pill>
                    {cp.fxNote}
                  </>
                ) : undefined
              }
              source={<SourceTag system="国家资料" />}
            />
            <FieldRow
              label="重大节假日"
              value={cp.holidays}
              source={<SourceTag system="国家资料" />}
            />
            <FieldRow
              label="进口关税"
              value={cp.tariffNote}
              source={<SourceTag system="国家资料" />}
            />
          </>
        ) : (
          <div
            style={{ padding: '12px 0', fontSize: 13, color: palette.orange }}
          >
            这个国家还没有资料（电压、外汇管制、节假日）。
            {canEditCountry && (
              <button
                type="button"
                className="zhul-link"
                onClick={() =>
                  history.push(
                    `${COUNTRY_PATH}?country=${encodeURIComponent(p.country)}`,
                  )
                }
              >
                去维护
              </button>
            )}
          </div>
        )}
      </ModuleCard>
    </>
  );
};

export default ProfileModules;
