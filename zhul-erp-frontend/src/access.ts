export default function access(initialState: {
  currentUser?: API.CurrentUser & { permissions?: string[] };
}) {
  const { currentUser } = initialState || {};
  const isAdmin = currentUser?.access === 'admin';
  const perms = new Set(currentUser?.permissions || []);

  const can = (key: string) => isAdmin || perms.has('*') || perms.has(key);

  return {
    canAdmin: isAdmin,
    // 菜单级
    dashboard: can('/dashboard'),
    systemUser: can('/system/user'),
    systemRole: can('/system/role'),
    systemMenu: can('/system/menu'),
    systemDept: can('/system/dept'),
    systemPosition: can('/system/position'),
    systemDict: can('/system/dict'),
    systemConfig: can('/system/config'),
    systemLogOperate: can('/system/log/operate'),
    systemLogLogin: can('/system/log/login'),
    tenantList: can('/tenant/list'),
    tenantPackage: can('/tenant/package'),
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
  };
}
