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
  // 工作台(1) → 商品管理(2) → 询盘管理(3) → 报价中心(4) → 系统管理(98) → 租户管理(99)
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
  // 客户管理、供应商管理各自一个一级菜单（原「客商管理」拆分），后续评分页面挂在各自目录下
  {
    path: '/customer',
    name: 'customer',
    icon: 'solution',
    // 父路由也要挂 access，理由同 /tenant、/inquiry、/product
    access: 'customerMenu',
    routes: [
      { path: '/customer', redirect: '/customer/list' },
      {
        path: '/customer/list',
        name: 'list',
        icon: 'unorderedList',
        access: 'customerList',
        component: './customer',
      },
      {
        path: '/customer/list/new',
        hideInMenu: true,
        access: 'customerList',
        component: './customer/form',
      },
      {
        path: '/customer/list/:id/edit',
        hideInMenu: true,
        access: 'customerList',
        component: './customer/form',
      },
      {
        path: '/customer/list/:id',
        hideInMenu: true,
        access: 'customerList',
        component: './customer/detail',
      },
    ],
  },
  {
    path: '/supplier',
    name: 'supplier',
    icon: 'shop',
    // 父路由也要挂 access，理由同 /tenant、/inquiry、/product
    access: 'supplierMenu',
    routes: [
      { path: '/supplier', redirect: '/supplier/list' },
      {
        path: '/supplier/list',
        name: 'list',
        icon: 'unorderedList',
        access: 'supplierList',
        component: './supplier',
      },
      {
        path: '/supplier/list/new',
        hideInMenu: true,
        access: 'supplierList',
        component: './supplier/form',
      },
      {
        path: '/supplier/list/:id/edit',
        hideInMenu: true,
        access: 'supplierList',
        component: './supplier/form',
      },
      {
        path: '/supplier/list/:id',
        hideInMenu: true,
        access: 'supplierList',
        component: './supplier/detail',
      },
    ],
  },
  {
    path: '/crm',
    name: 'crm',
    icon: 'userAdd',
    // 父路由也要挂 access，理由同 /inquiry
    access: 'crmMenu',
    routes: [
      { path: '/crm', redirect: '/crm/opportunities' },
      {
        path: '/crm/opportunities',
        name: 'opportunity',
        icon: 'unorderedList',
        access: 'crmOpportunity',
        component: './crm/opportunity',
      },
      {
        path: '/crm/opportunities/:id',
        hideInMenu: true,
        access: 'crmOpportunity',
        component: './crm/opportunity/detail',
      },
      {
        path: '/crm/opportunity-stats',
        name: 'opportunityStats',
        icon: 'barChart',
        access: 'crmOpportunityStats',
        component: './crm/opportunity/stats',
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
        path: '/inquiry/part-time-board',
        name: 'partTimeBoard',
        icon: 'dashboard',
        access: 'inquiryPartTimeBoard',
        component: './inquiry/part-time-board',
      },
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
        path: '/inquiry/customer-inquiries/:id/confirm',
        hideInMenu: true,
        access: 'inquiryCustomerInquiry',
        component: './inquiry/customer-inquiry/confirm',
      },
      {
        path: '/inquiry/sourcing-board',
        name: 'sourcingBoard',
        icon: 'apartment',
        access: 'inquirySourcingBoard',
        component: './inquiry/sourcing-board',
      },
      {
        path: '/inquiry/sourcing-board/rules',
        hideInMenu: true,
        access: 'inquirySourcingBoard',
        component: './inquiry/sourcing-board/rules',
      },
      {
        path: '/inquiry/my-tasks',
        name: 'myTasks',
        icon: 'solution',
        access: 'inquiryMyTasks',
        component: './inquiry/my-tasks',
      },
      {
        path: '/inquiry/price-history',
        name: 'priceHistory',
        icon: 'history',
        access: 'inquiryPriceHistory',
        component: './inquiry/price-history',
      },
    ],
  },
  {
    path: '/quotation',
    name: 'quotation',
    icon: 'fileText',
    // 父路由也要挂 access，理由同 /inquiry
    access: 'quotationMenu',
    routes: [
      { path: '/quotation', redirect: '/quotation/quotations' },
      {
        path: '/quotation/quotations',
        name: 'quotations',
        icon: 'fileText',
        access: 'quotationList',
        component: './quotation',
      },
      {
        path: '/quotation/quotations/:id',
        hideInMenu: true,
        access: 'quotationList',
        component: './quotation/detail',
      },
      {
        path: '/quotation/pricing',
        name: 'pricing',
        icon: 'percentage',
        access: 'quotationPricing',
        component: './quotation/pricing',
      },
    ],
  },
  {
    path: '/sales',
    name: 'sales',
    icon: 'accountBook',
    // 父路由也要挂 access，理由同 /inquiry
    access: 'salesMenu',
    routes: [
      { path: '/sales', redirect: '/sales/pi' },
      {
        path: '/sales/pi',
        name: 'pi',
        icon: 'fileDone',
        access: 'salesPi',
        component: './sales/pi',
      },
      {
        path: '/sales/pi/:id',
        hideInMenu: true,
        access: 'salesPi',
        component: './sales/pi/detail',
      },
      {
        path: '/sales/orders',
        name: 'orders',
        icon: 'container',
        access: 'salesOrders',
        component: './sales/orders',
      },
      {
        path: '/sales/orders/:id',
        hideInMenu: true,
        access: 'salesOrders',
        component: './sales/orders/detail',
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
        path: '/system/exchange-rate',
        name: 'exchangeRate',
        icon: 'transaction',
        access: 'systemExchangeRate',
        component: './system/exchange-rate',
      },
      {
        path: '/system/document-template',
        name: 'documentTemplate',
        icon: 'fileExcel',
        access: 'systemDocumentTemplate',
        component: './system/document-template',
      },
      {
        path: '/system/bank-account',
        name: 'bankAccount',
        icon: 'bank',
        access: 'systemBankAccount',
        component: './system/bank-account',
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
