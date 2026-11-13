## Purpose

This capability defines differentiated, map-aligned operating checklists for the five supply-chain SOP workbooks so each stage has its own business actions, quality evidence, and exit conditions.

## ADDED Requirements

### Requirement: Map-aligned stage dictionary
The SOP set SHALL use the lifecycle map's 25 stage nodes as the single source of truth: C1-C5, M1-M5, V1-V5, Q1-Q5, and G1-G5.

#### Scenario: Stage names are consistent
- **WHEN** a reviewer compares any workbook Stage sheet with the lifecycle map
- **THEN** the code, Chinese name, and stage order match exactly

### Requirement: Stage-specific BAU checklist
Each Stage sheet SHALL contain at least four actions that are specific to that stage's supply-chain decision, in addition to common ownership and evidence controls.

#### Scenario: Demand intelligence stage
- **WHEN** a user opens C2 搜索信号
- **THEN** the BAU actions cover platform keywords, independent-site keywords, frequency, trend, synonym normalization, and demand validation

#### Scenario: Supplier validation stage
- **WHEN** a user opens V3 货源验证
- **THEN** the BAU actions cover product state, authenticity evidence, stock evidence, model matching, and risk recording

#### Scenario: Quote and fulfillment stage
- **WHEN** a user opens Q3 交期确认
- **THEN** the BAU actions cover stock status, promised lead time, production time, logistics time, validity, and escalation risk

### Requirement: Stage-specific quality checklist
Each Stage sheet SHALL define quality checks that evaluate the correctness of that stage's supply-chain decision, rather than repeating generic record-keeping language.

#### Scenario: Coverage quality
- **WHEN** a reviewer checks M4 覆盖矩阵
- **THEN** the checklist distinguishes supplier count from effective coverage and requires current evidence for cost, lead time, source, and backup channels

#### Scenario: Supplier grading quality
- **WHEN** a reviewer checks V5 能力分级
- **THEN** the checklist requires grading by evidence and separates capability by brand or category where applicable

### Requirement: Stage-specific exit gate
Each Stage sheet SHALL define observable exit conditions that determine whether the object can advance to the next map node.

#### Scenario: Demand prioritization gate
- **WHEN** C5 优先级判断 is completed
- **THEN** the record has a traceable score, priority level, uncertainty note, and a decision to enter the supply map or remain under observation

#### Scenario: Supplier capability gate
- **WHEN** V4 条件验证 is completed
- **THEN** cost currency, validity, lead time, response commitment, and risk are confirmed before the channel can support quoting

#### Scenario: Exception gate
- **WHEN** Q5 异常处理 is active
- **THEN** the record cannot close until the impact, owner, resolution, and verification result are recorded

### Requirement: Workbook-wide content alignment
The supporting tabs SHALL use supply-chain terminology and SHALL not retain customer-sales stage content from the source templates.

#### Scenario: Residual content scan
- **WHEN** the five workbooks are scanned after generation
- **THEN** customer-sales stage names such as 首次触达, 商务谈判, 客户签收, 评价邀请, and 交叉销售 are absent
