## 1. 无货附件

- [x] 1.1 后端：`ItemQuotesRequest` 增加 `noStockAttachmentIds`（最多 5 个），保存无货回价时挂附件；回显返回附件
- [x] 1.2 前端：无货时显示附件上传，提交带附件，已提交行显示附件数量；补测试

## 2. 批量付款

- [x] 2.1 后端：`BatchPaymentRequest`、`POST /finance/payments/batch`、`PaymentService.batchRegister`（分组校验、按到期日核销、整批事务、幂等）
- [x] 2.2 后端契约测试：跨收款方两组成功、金额改小按到期日核销、超过未付合计报错、一组失败整批回滚、同键重复提交
- [x] 2.3 前端：应付列表勾选（未付清可选、最多 50 张）、已选汇总条、批量付款弹窗

## 3. 型号查看分页

- [x] 3.1 后端：销售订单 `GET /sales/orders/{id}/models` 分页；询盘 `models` 增加可选分页
- [x] 3.2 前端：通用分页型号弹窗；销售订单列表「查看」；询盘列表改用分页弹窗；复制全部型号

## 4. 收尾

- [x] 4.1 后端相关测试、前端 lint / tsc / jest 通过，本地走查
