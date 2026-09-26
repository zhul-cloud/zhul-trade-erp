export default [
  { path: '/login', name: 'login', layout: false, component: './login' },
  {
    path: '/forget-password',
    layout: false,
    routes: [
      {
        path: '/forget-password/step1',
        component: './forget-password/step1',
      },
      {
        path: '/forget-password/step2',
        component: './forget-password/step2',
      },
      {
        path: '/forget-password/step3',
        component: './forget-password/step3',
      },
      { path: '/forget-password/done', component: './forget-password/done' },
      { path: '/forget-password', redirect: '/forget-password/step1' },
    ],
  },
  {
    path: '/user',
    layout: false,
    routes: [
      { path: '/user/login', redirect: '/login' },
      { path: '/user', redirect: '/login' },
      { path: '/user/*', component: './exception/404' },
    ],
  },
  // 以下顶层业务菜单的先后顺序跟数据库「菜单管理」（resource 表）的 sort 值保持一致：
  // 工作台(1) → 商品管理(2) → 询盘管理(3) → 系统管理(98) → 租户管理(99)
  {
    path: '/dashboard',
    name: 'dashboard',
    icon: 'home',
    access: 'dashboard',
    component: './dashboard',
  },
  {
    // 新建向导占满整页（没有侧栏和顶栏）。layout: false 只对顶层路由生效，所以放在 /product 分组外面
    path: '/product/products/new',
    layout: false,
    access: 'productList',
    component: './product/products/new',
  },
  {
    path: '/product',
    name: 'product',
    icon: 'shopping',
    // 父路由也要挂 access，理由同 /tenant、/inquiry
    access: 'productList',
    routes: [
      { path: '/product', redirect: '/product/products' },
      {
        path: '/product/products',
        name: 'products',
        icon: 'database',
        access: 'productList',
        component: './product/products',
      },
      {
        path: '/product/products/:id',
        hideInMenu: true,
        access: 'productList',
        component: './product/products/detail',
      },
      {
        path: '/product/brands',
        name: 'brands',
        icon: 'tag',
        access: 'productBrand',
        component: './product/brand',
      },
      {
        path: '/product/categories',
        name: 'categories',
        icon: 'appstore',
        access: 'productCategory',
        component: './product/category',
      },
      {
        path: '/product/series',
        name: 'series',
        icon: 'cluster',
        access: 'productSeries',
        component: './product/series',
      },
    ],
  },
  {
    path: '/partner',
    name: 'partner',
    icon: 'contacts',
    // 父路由也要挂 access，理由同 /tenant、/inquiry、/product；用 partnerMenu（两个子权限
    // 任一为真）而不是直接复用 partnerCustomer，避免只有供应商权限、没有客户权限的角色被
    // 父路由的 access 连带挡住，进不了 /partner/suppliers
    access: 'partnerMenu',
    routes: [
      { path: '/partner', redirect: '/partner/customers' },
      {
        path: '/partner/customers',
        name: 'customers',
        icon: 'solution',
        access: 'partnerCustomer',
        component: './partner/customer',
      },
      {
        path: '/partner/customers/new',
        hideInMenu: true,
        access: 'partnerCustomer',
        component: './partner/customer/form',
      },
      {
        path: '/partner/customers/:id/edit',
        hideInMenu: true,
        access: 'partnerCustomer',
        component: './partner/customer/form',
      },
      {
        path: '/partner/customers/:id',
        hideInMenu: true,
        access: 'partnerCustomer',
        component: './partner/customer/detail',
      },
      {
        path: '/partner/suppliers',
        name: 'suppliers',
        icon: 'shop',
        access: 'partnerSupplier',
        component: './partner/supplier',
      },
      {
        path: '/partner/suppliers/new',
        hideInMenu: true,
        access: 'partnerSupplier',
        component: './partner/supplier/form',
      },
      {
        path: '/partner/suppliers/:id/edit',
        hideInMenu: true,
        access: 'partnerSupplier',
        component: './partner/supplier/form',
      },
      {
        path: '/partner/suppliers/:id',
        hideInMenu: true,
        access: 'partnerSupplier',
        component: './partner/supplier/detail',
      },
    ],
  },
  {
    path: '/inquiry',
    name: 'inquiry',
    icon: 'mail',
    // 父路由也要挂 access（同 /tenant 的教训）：不挂的话子路由权限判断再准，
    // 侧边栏这个父分组本身照样会露出来
    access: 'inquiryMenu',
    routes: [
      { path: '/inquiry', redirect: '/inquiry/customer-inquiries' },
      {
        path: '/inquiry/customer-inquiries',
        name: 'customerInquiry',
        icon: 'inbox',
        access: 'inquiryCustomerInquiry',
        component: './inquiry/customer-inquiry',
      },
      {
        path: '/inquiry/customer-inquiries/:id',
        hideInMenu: true,
        access: 'inquiryCustomerInquiry',
        component: './inquiry/customer-inquiry/detail',
      },
      {
        path: '/inquiry/orders',
        name: 'order',
        icon: 'shoppingCart',
        access: 'inquiryOrder',
        component: './inquiry/order',
      },
      {
        path: '/inquiry/orders/:id',
        hideInMenu: true,
        access: 'inquiryOrder',
        component: './inquiry/order/detail',
      },
    ],
  },
  {
    path: '/system',
    name: 'system',
    icon: 'setting',
    // 父路由也要挂 access，理由同 /tenant、/inquiry
    access: 'systemUser',
    routes: [
      { path: '/system', redirect: '/system/user' },
      {
        path: '/system/user',
        name: 'user',
        icon: 'user',
        access: 'systemUser',
        component: './system/user',
      },
      {
        path: '/system/role',
        name: 'role',
        icon: 'team',
        access: 'systemRole',
        component: './system/role',
      },
      {
        path: '/system/menu',
        name: 'menu',
        icon: 'menu',
        access: 'systemMenu',
        component: './system/menu',
      },
      {
        path: '/system/dept',
        name: 'dept',
        icon: 'apartment',
        access: 'systemDept',
        component: './system/dept',
      },
      {
        path: '/system/position',
        name: 'position',
        icon: 'idcard',
        access: 'systemPosition',
        component: './system/position',
      },
      {
        path: '/system/dict',
        name: 'dict',
        icon: 'book',
        access: 'systemDict',
        component: './system/dict',
      },
      {
        path: '/system/config',
        name: 'config',
        icon: 'tool',
        access: 'systemConfig',
        component: './system/config',
      },
      {
        path: '/system/log/operate',
        name: 'operateLog',
        icon: 'fileText',
        access: 'systemLogOperate',
        component: './system/log/operate',
      },
      {
        path: '/system/log/login',
        name: 'loginLog',
        icon: 'login',
        access: 'systemLogLogin',
        component: './system/log/login',
      },
    ],
  },
  {
    path: '/tenant',
    name: 'tenant',
    icon: 'cluster',
    // 父路由也要挂 access：子路由各自的 access 只挡得住子页面本身，挡不住这个父分组
    // 在侧边栏里露出来（umi 的菜单渲染不会因为子路由全部无权限就自动隐藏父分组）
    access: 'tenantList',
    routes: [
      { path: '/tenant', redirect: '/tenant/list' },
      {
        path: '/tenant/list',
        name: 'list',
        icon: 'cluster',
        access: 'tenantList',
        component: './tenant/list',
      },
      {
        path: '/tenant/package',
        name: 'package',
        icon: 'appstore',
        access: 'tenantPackage',
        component: './tenant/package',
      },
    ],
  },
  { path: '/', redirect: '/dashboard' },
  { path: '/*', component: './exception/404' },
];
