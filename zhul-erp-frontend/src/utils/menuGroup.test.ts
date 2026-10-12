import { menuGroupOf } from './menuGroup';

describe('menuGroupOf：页面路径对应的侧边栏分组', () => {
  it.each([
    ['/inquiry/customer-inquiries', '销售管理'],
    ['/inquiry/customer-inquiries/12/confirm', '销售管理'],
    ['/quotation/quotations/3', '销售管理'],
    ['/quotation/pricing', '业务设置'],
    ['/inquiry/sourcing-board/rules', '采购管理'],
    ['/inquiry/price-history', '采购管理'],
    ['/supplier/list/8', '采购管理'],
    ['/customer/list', '销售管理'],
    ['/finance/receipts', '财务管理'],
    ['/system/exchange-rate', '业务设置'],
    ['/system/document-numbering', '业务设置'],
    ['/system/user', '系统管理'],
    ['/product/brands', '商品管理'],
    ['/product/fact-tasks', '商品管理'],
    ['/operations/products', '运营管理'],
    ['/operations/products/85', '运营管理'],
    ['/purchase/shipments', '采购管理'],
    ['/warehouse/shoots', '仓库管理'],
    ['/logistics/shipments', '物流管理'],
    ['/system/transit-times', '业务设置'],
  ])('%s → %s', (path, group) => {
    expect(menuGroupOf(path)).toBe(group);
  });
});
