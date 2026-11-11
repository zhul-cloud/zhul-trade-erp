## Why

商品候选的品牌、品类目前只能逐个打开审核时修改。运营在一批型号里看出它们同属一个品牌或品类时，只能一个一个改，效率低。批量 AI 解析品牌时，AI 给不出品牌或给出的品牌不在品牌库里，候选会一直停在「待确认品牌」，没办法批量通过建档。用户要求：AI 认不出的品牌统一记为品牌 Others，让这些型号能继续走审核。

## What Changes

- 商品候选「待审核」页签新增「批量修改」：勾选候选后一次改品牌和（或）品类，不填的那一项保持不变。每次最多 200 条，需要「商品候选审核」权限。
- 品牌库新增平台品牌 **Others**，属于所有行业，标为非原厂（下游不对它用正品类表述）。
- 批量 AI 解析品牌：AI 没给出品牌，或给出的名称在品牌库匹配不上，候选品牌都填为 Others。AI 给出的原名称仍保存，并在列表里显示为「AI 未识别：XXX」供参考，审核时可以改成新建品牌或已有品牌。**这会替代原来「匹配不上保持待确认品牌」的规则。**
- 跑完的提示改为「品类填了 X 个，品牌匹配 Y 个、记为 Others Z 个，跳过 N 个」。

不在本次范围：已有候选不回填 Others，下次跑 AI 解析时才处理。不批量修改系列、型号、生命周期。不改审核通过的规则，Others 视为已确认品牌，可以单个或批量通过。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `product/product-candidate`：新增「批量修改品牌与品类」「AI 解析不出的品牌记为 Others」两条要求。后一条取代未归档变更 `candidate-batch-ai-fill` 里「品牌匹配不上」的规则，该变更的规格同步改写。

## Impact

- 数据库：迁移 V1.5.26。新增品牌 Others，并关联字典 `industry` 的全部行业；不改表结构。V1.5.25 已被 develop 的 AI 任务进度占用。
- 后端：`ProductCandidateController` 新增 `POST /api/v1/product/candidates/batch-edit`；候选服务新增批量修改，写操作日志；`CandidateAiFill` 处理品牌时，没给出或匹配不上的都写 Others。
- 前端：`pages/product/candidates/index.tsx` 加「批量修改」按钮与弹窗，`service.ts` 加接口；AI 解析结果提示文字改为新口径。
- 影响面：Others 下会累积来源不明的商品。商品档案、独立站等按品牌展示的地方会出现 Others 品牌，`is_genuine = 0` 保证下游不出现正品类表述。
