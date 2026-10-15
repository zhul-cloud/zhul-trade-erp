## Why

很多付款是法人用个人账户转给对方个人账户（私对私），现在付款单只有公司对公与员工垫付，报销单登记已付只能选公司账户，这类付款没法如实登记。

## What Changes

- 付款方式新增「私对私」：财务登记，不选公司账户，必填付款个人账户；不进报销，核销与公司对公相同。
- 报销单登记已付可选公司对公或私对私。
- 付款单列表、筛选、顶部统计和各付款面板显示私对私与付款个人账户。费用单（公司对公生成的应付）、采购订单、运费账单都在应付单付款时可选私对私，整条付款流程都支持。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `finance/supplier-payment`：登记付款、付款单页。
- `finance/reimbursement`：登记报销已付。

## Impact

- **数据库**：`fin_payment` 加 `payer_account`，付款方式注释加 3-私对私；`fin_reimbursement` 加 `pay_method`、`pay_payer_account`。
- **后端**：付款登记与报销付款校验私对私；统计、列表、应付单付款明细带付款个人账户。
- **前端**：付款弹窗、报销付款弹窗、付款单页、应付单付款明细。
