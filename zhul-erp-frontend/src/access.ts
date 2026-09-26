import { isPlatformAccount } from '@/utils/platform';

export default function access(initialState: {
  currentUser?: API.CurrentUser & { permissions?: string[] };
}) {
  const { currentUser } = initialState || {};
  const isAdmin = currentUser?.access === 'admin';
  const perms = new Set(currentUser?.permissions || []);

  // permissions 数组现在由后端统一算好（平台超管/租户套餐/角色三层限制都已经算进去），
  // 前端不再对 admin 账号做直通——否则租户套餐没勾的菜单，管理员账号照样能在前端看到，
  // 跟服务端接口的实际拦截结果不一致。isAdmin 只用来做"管理员/成员"这类纯展示文案。
  const can = (key: string) => perms.has(key);
  // 商品主数据是平台共享数据：写按钮除了权限码，还要求平台账号（服务端同样会校验）
  const platform = isPlatformAccount();
  const canWrite = (key: string) => platform && can(key);

  return {
    canAdmin: isAdmin,
    // 菜单级
    dashboard: can('/dashboard'),
    inquiryMenu: can('/inquiry'),
    inquiryCustomerInquiry: can('/inquiry/customer-inquiries'),
    inquiryOrder: can('/inquiry/orders'),
    systemUser: can('/system/user'),
    systemRole: can('/system/role'),
    systemMenu: can('/system/menu'),
    systemDept: can('/system/dept'),
    systemPosition: can('/system/position'),
    systemDict: can('/system/dict'),
    systemConfig: can('/system/config'),
    systemLogOperate: can('/system/log/operate'),
    systemLogLogin: can('/system/log/login'),
    // 租户/套餐管理入口本身也要求平台账号：admin_flag=1 对任何租户管理员都成立，
    // 光靠 can() 区分不出"平台超管"和"租户内超管"，得再叠加 isPlatformAccount()
    tenantList: canWrite('/tenant/list'),
    tenantPackage: canWrite('/tenant/package'),
    productBrand: can('/product/brands'),
    productCategory: can('/product/categories'),
    productSeries: can('/product/series'),
    productList: can('/product/products'),
    // 客商管理：partnerMenu 是两个子权限任一为真，供 /partner 父路由的 access 用
    partnerCustomer: can('/partner/customers'),
    partnerSupplier: can('/partner/suppliers'),
    partnerMenu: can('/partner/customers') || can('/partner/suppliers'),
    // 平台账号才有档案完整度、缺项筛选等平台视角
    productPlatform: platform,
    // 按钮级 - 用户管理
    'system:user:add': can('system:user:add'),
    'system:user:edit': can('system:user:edit'),
    'system:user:delete': can('system:user:delete'),
    'system:user:resetPwd': can('system:user:resetPwd'),
    'system:user:status': can('system:user:status'),
    // 按钮级 - 角色管理
    'system:role:add': can('system:role:add'),
    'system:role:edit': can('system:role:edit'),
    'system:role:delete': can('system:role:delete'),
    'system:role:assign': can('system:role:assign'),
    // 按钮级 - 菜单管理
    'system:menu:add': can('system:menu:add'),
    'system:menu:edit': can('system:menu:edit'),
    'system:menu:delete': can('system:menu:delete'),
    // 按钮级 - 部门管理
    'system:dept:add': can('system:dept:add'),
    'system:dept:edit': can('system:dept:edit'),
    'system:dept:delete': can('system:dept:delete'),
    // 按钮级 - 岗位管理
    'system:position:add': can('system:position:add'),
    'system:position:edit': can('system:position:edit'),
    'system:position:delete': can('system:position:delete'),
    // 按钮级 - 字典管理
    'system:dict:add': can('system:dict:add'),
    'system:dict:edit': can('system:dict:edit'),
    'system:dict:delete': can('system:dict:delete'),
    // 按钮级 - 系统设置
    'system:config:add': can('system:config:add'),
    'system:config:edit': can('system:config:edit'),
    'system:config:delete': can('system:config:delete'),
    // 按钮级 - 登录日志
    'system:log:login:forceLogout': can('system:log:login:forceLogout'),
    // 按钮级 - 租户管理（读写都要求平台账号，跟商品主数据一样服务端会再校验一次）
    'tenant:list:add': canWrite('tenant:list:add'),
    'tenant:list:edit': canWrite('tenant:list:edit'),
    'tenant:list:status': canWrite('tenant:list:status'),
    'tenant:list:resetPwd': canWrite('tenant:list:resetPwd'),
    // 按钮级 - 套餐管理（同上，读写都要求平台账号）
    'tenant:package:add': canWrite('tenant:package:add'),
    'tenant:package:edit': canWrite('tenant:package:edit'),
    'tenant:package:status': canWrite('tenant:package:status'),
    'tenant:package:delete': canWrite('tenant:package:delete'),
    // 按钮级 - 商品主数据（品牌 / 品类 / 系列 / 商品）
    'product:brand:add': canWrite('product:brand:add'),
    'product:brand:edit': canWrite('product:brand:edit'),
    'product:brand:delete': canWrite('product:brand:delete'),
    'product:category:add': canWrite('product:category:add'),
    'product:category:edit': canWrite('product:category:edit'),
    'product:category:delete': canWrite('product:category:delete'),
    'product:series:add': canWrite('product:series:add'),
    'product:series:edit': canWrite('product:series:edit'),
    'product:series:delete': canWrite('product:series:delete'),
    'product:product:add': canWrite('product:product:add'),
    'product:product:edit': canWrite('product:product:edit'),
    'product:product:delete': canWrite('product:product:delete'),
    // 按钮级 - 客户管理 / 供应商管理（租户自己的数据，不要求平台账号，跟商品主数据不同）
    'partner:customer:add': can('partner:customer:add'),
    'partner:customer:edit': can('partner:customer:edit'),
    'partner:customer:delete': can('partner:customer:delete'),
    'partner:customer:status': can('partner:customer:status'),
    'partner:customer:transfer': can('partner:customer:transfer'),
    'partner:customer:export': can('partner:customer:export'),
    'partner:supplier:add': can('partner:supplier:add'),
    'partner:supplier:edit': can('partner:supplier:edit'),
    'partner:supplier:delete': can('partner:supplier:delete'),
    'partner:supplier:status': can('partner:supplier:status'),
    'partner:supplier:export': can('partner:supplier:export'),
  };
}
