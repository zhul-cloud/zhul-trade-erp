// 商品主数据的枚举展示配置。取值需与后端保持一致，见：
// zhul-erp-backend .../modules/product/constants/{LifecycleStatus,RelationshipType,RelationshipConfidence}.java
// 文案与配色依据 PRD 6.4、6.5 及 design.md 决策 7、11。

export const LIFECYCLE = {
  ACTIVE: 1,
  CURRENT: 2,
  LEGACY: 3,
  DISCONTINUED: 4,
  OBSOLETE: 5,
  UNKNOWN: 6,
} as const;

type Tone = 'green' | 'orange' | 'red' | 'gray';

export const LIFECYCLE_META: Record<number, { text: string; tone: Tone }> = {
  1: { text: '在产', tone: 'green' },
  2: { text: '现行', tone: 'green' },
  3: { text: '旧款', tone: 'orange' },
  4: { text: '已停产', tone: 'red' },
  5: { text: '停产无替代', tone: 'red' },
  6: { text: '未知', tone: 'gray' },
};

export const LIFECYCLE_OPTIONS = Object.entries(LIFECYCLE_META).map(
  ([value, meta]) => ({ value: Number(value), label: meta.text }),
);

export const RELATIONSHIP_TYPES: Record<
  number,
  { text: string; symmetric: boolean; hint: string }
> = {
  1: { text: '官方直接替代', symmetric: false, hint: '厂商明确给出的替代型号' },
  2: { text: '厂商后续型号', symmetric: false, hint: '同一产品线的新一代型号' },
  3: { text: '功能性替代', symmetric: true, hint: '功能相近，可以顶替使用' },
  4: { text: '兼容', symmetric: true, hint: '接口、尺寸可以互换' },
  5: { text: '交叉引用', symmetric: true, hint: '不同品牌的对应型号' },
  6: { text: '同系列', symmetric: true, hint: '属于同一个系列' },
};

export const RELATIONSHIP_OPTIONS = Object.entries(RELATIONSHIP_TYPES).map(
  ([value, meta]) => ({ value: Number(value), label: meta.text }),
);

export const CONFIDENCE: Record<number, string> = {
  1: '已验证',
  2: '高',
  3: '中',
  4: '低',
  5: '未知',
};

export const CONFIDENCE_OPTIONS = Object.entries(CONFIDENCE).map(
  ([value, label]) => ({ value: Number(value), label }),
);

export const DOCUMENT_TYPES: Record<number, string> = {
  1: 'Datasheet',
  2: 'Manual',
  3: 'Installation Guide',
  4: 'User Manual',
  5: 'CAD',
  6: 'Drawing',
  7: 'Brochure',
  8: 'Certificate',
};

export const DOCUMENT_TYPE_OPTIONS = Object.entries(DOCUMENT_TYPES).map(
  ([value, label]) => ({ value: Number(value), label }),
);

export const FAQ_SOURCE = { TEMPLATE: 1, MANUAL: 2, PENDING: 3 } as const;

export const CURRENCY_OPTIONS = ['USD', 'EUR', 'CNY', 'JPY', 'GBP', 'HKD'].map(
  (code) => ({ value: code, label: code }),
);

export const COUNTRY_OPTIONS = [
  { value: 'DE', label: '德国 DE' },
  { value: 'CN', label: '中国 CN' },
  { value: 'US', label: '美国 US' },
  { value: 'JP', label: '日本 JP' },
  { value: 'CH', label: '瑞士 CH' },
  { value: 'FR', label: '法国 FR' },
  { value: 'IT', label: '意大利 IT' },
  { value: 'GB', label: '英国 GB' },
  { value: 'KR', label: '韩国 KR' },
  { value: 'TW', label: '中国台湾 TW' },
];

/** 档案完整度的 10 个模块（顺序、文案与 PRD 2.14 一致）；minutes 为待补清单里的预计用时 */
export const COMPLETENESS_MODULES: {
  key: string;
  label: string;
  minutes: number;
  anchor: string;
  benefit: string;
}[] = [
  {
    key: 'basic',
    label: '基本信息',
    minutes: 1,
    anchor: 'card-basic',
    benefit: '品牌、型号和品类是商品的身份',
  },
  {
    key: 'media',
    label: '图片与视频',
    minutes: 2,
    anchor: 'card-media',
    benefit: '有主图，客户才能一眼认出这个商品',
  },
  {
    key: 'specifications',
    label: '规格参数',
    minutes: 5,
    anchor: 'card-specs',
    benefit: '客户按规格筛选和比对，报价时也要核对',
  },
  {
    key: 'logistics',
    label: '物流信息',
    minutes: 2,
    anchor: 'card-logistics',
    benefit: '重量尺寸用来估运费和订舱',
  },
  {
    key: 'customs',
    label: '海关信息',
    minutes: 2,
    anchor: 'card-customs',
    benefit: 'HS 编码和申报品名是报关必填项',
  },
  {
    key: 'referencePrice',
    label: '平台参考价',
    minutes: 1,
    anchor: 'card-price',
    benefit: '报价时有个参照，不必每次重新查',
  },
  {
    key: 'relationships',
    label: '型号关系',
    minutes: 3,
    anchor: 'card-relations',
    benefit: '停产、缺货时能快速找到替代型号',
  },
  {
    key: 'documents',
    label: '技术资料',
    minutes: 2,
    anchor: 'card-documents',
    benefit: '数据手册和手册是客户最常要的资料',
  },
  {
    key: 'applications',
    label: '应用场景',
    minutes: 2,
    anchor: 'card-applications',
    benefit: '说明这个商品用在哪里',
  },
  {
    key: 'faq',
    label: '常见问题',
    minutes: 3,
    anchor: 'card-faq',
    benefit: '提前回答客户最常问的问题',
  },
];

/** 完整度 ≥ 8 段用绿色，其余用强调色 */
export const COMPLETENESS_GOOD = 8;

/** 上传预检：与后端 ProductMediaStorageServiceImpl 的规则一致，前端先挡一道，服务端仍会再校验 */
export const UPLOAD_RULES = {
  image: {
    ext: ['jpg', 'jpeg', 'png', 'webp'],
    maxBytes: 5 * 1024 * 1024,
    label: '图片不能超过 5MB',
  },
  video: {
    ext: ['mp4', 'webm'],
    maxBytes: 100 * 1024 * 1024,
    label: '视频不能超过 100MB',
  },
};

export const UPLOAD_TYPE_MESSAGE =
  '不支持该文件类型，请上传 jpg、png、webp 图片或 mp4、webm 视频';

/** 返回预检失败的原因；通过返回 undefined */
export function precheckUpload(
  file: { name: string; size: number },
  mediaType: 1 | 2,
): string | undefined {
  const rule = mediaType === 1 ? UPLOAD_RULES.image : UPLOAD_RULES.video;
  const ext = file.name.includes('.')
    ? file.name.split('.').pop()?.toLowerCase()
    : '';
  if (!ext || !rule.ext.includes(ext)) return UPLOAD_TYPE_MESSAGE;
  if (file.size > rule.maxBytes) return rule.label;
  return undefined;
}

/** 与后端 UrlRules 一致：只允许 http://、https:// 或以单个 / 开头的站内路径 */
export function isSafeUrl(url: string): boolean {
  return /^(?:https?:\/\/[^\s\p{Cc}]+|\/(?![/\\])[^\s\p{Cc}]*)$/iu.test(url);
}

export const URL_RULE_MESSAGE = '地址必须以 http://、https:// 或 / 开头';

/** 与后端 MpnNormalizer 一致：NFKC → 小写 → 去掉所有非字母数字 */
export function normalizeMpn(raw: string): string {
  return raw
    .trim()
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
}

/** 与后端 CompletenessSql/HS 规则一致：去掉点和空格后应为 6–10 位数字 */
export function normalizeHsCode(raw: string): string {
  return raw.replace(/[.\s]/g, '');
}
