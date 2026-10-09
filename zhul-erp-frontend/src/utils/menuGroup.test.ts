import { menuGroupOf } from './menuGroup';

describe('menuGroupOf：页面路径对应的侧边栏分组', () => {
  it.each([
    ['/inquiry/customer-inquiries', '业务管理'],
    ['/inquiry/customer-inquiries/12/confirm', '业务管理'],
    ['/quotation/quotations/3', '业务管理'],
    ['/quotation/pricing', '业务设置'],
    ['/inquiry/sourcing-board/rules', '采购管理'],
    ['/inquiry/price-history', '采购管理'],
    ['/supplier/list/8', '采购管理'],
    ['/customer/list', '业务管理'],
    ['/finance/receipts', '财务管理'],
    ['/system/exchange-rate', '业务设置'],
    ['/system/document-numbering', '业务设置'],
    ['/system/user', '系统管理'],
    ['/product/brands', '商品资料'],
    ['/purchase/shipments', '采购管理'],
    ['/warehouse/shoots', '仓库管理'],
    ['/logistics/shipments', '单证物流'],
    ['/system/transit-times', '业务设置'],
  ])('%s → %s', (path, group) => {
    expect(menuGroupOf(path)).toBe(group);
  });
});
