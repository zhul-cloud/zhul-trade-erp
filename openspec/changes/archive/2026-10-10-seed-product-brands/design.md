## Context

- `product_brand`：平台级，`brand_name` 唯一（唯一键包含已软删除的行，排序规则不区分大小写），有 `country`（国家清单英文名）、`description`（独立站用）、`is_genuine`、`status`、`deleted_at`。`product_brand_alias` 存别名，`alias_key` 为去首尾空格转小写，平台内唯一；别名与品牌名称的交叉唯一由应用层保证。
- 引用：`brand_id` 在 `product`、`product_series`、`product_candidate`、`supplier_product_scope`、`inquiry_item`、`product_brand_alias`；品牌键（已识别品牌为 `#品牌ID`）在 `inquiry_item`、`product_candidate`、`sourcing_task`、`sourcing_quote`、`quotation_item`。唯一键：`product(tenant_id, brand_id, mpn_normalized)`、`product_series(tenant_id, brand_id, series_name)`、`product_candidate(brand_key, mpn_normalized)`。
- 开发库现状：24 个品牌，21 个在名单里（写法不同），Vacon 有 3 个系列，Hitech 有 1 个系列，HIDER 无引用。其他环境的品牌 ID 与开发库不同，迁移只能按名称和别名识别，不能写死 ID。
- 品牌选项有 5 分钟缓存。

## Goals / Non-Goals

**Goals:** 83 个品牌按名单初始化（名称、别名、原产地、等级、中英文简介）；已有品牌保留 ID；Vacon 并入 Danfoss；名单外品牌按引用软删除或停用；等级在列表、编辑、下拉排序中体现；可重复执行。

**Non-Goals:** 系列数据调整（保留现有）；品牌 Logo 与主题色（不改）；按等级做权限或报价规则；名单外品牌的人工归并。

## Decisions

1. **字段**：`brand_level tinyint(2) NOT NULL DEFAULT 0`（0-普通、1-常做、2-核心，加索引）；`description_zh varchar(500) NOT NULL DEFAULT ''`。`description` 注释改为英文简介。
2. **识别已有品牌**：每个名单品牌的匹配键 = 正式名称 + 别名 + 少量旧写法（如 `ABB Group`、`Mitsubishi`、`Omron`、`Wecon`、`Fuji Electric`）。按顺序在未删除的品牌里找：名称等于任一匹配键，或别名的 `alias_key` 等于任一匹配键；找到则更新这一行（名称改正式写法、原产地、等级、两段简介、启用），找不到则按名称插入或恢复（同名的软删除行恢复）。用会话变量保存找到的 ID，脚本逐个品牌执行。
3. **别名**：名单里的别名与旧名称逐个 `INSERT IGNORE` 到该品牌（`alias_key` 已被其他品牌占用时跳过）；最后删除与任何品牌名称相同的别名（如 ABB 原有别名「ABB」在改名后与名称重复），维持「别名与名称交叉唯一」。
4. **Vacon 并入 Danfoss**：`brand_id` 从 Vacon 改为 Danfoss——`supplier_product_scope`、`inquiry_item` 直接改；`product`、`product_series`、`product_candidate` 只改不会与 Danfoss 已有行冲突的；品牌键 `#Vacon的ID` 在 `inquiry_item`、`sourcing_task`、`sourcing_quote`、`quotation_item` 改为 `#Danfoss的ID`，`product_candidate` 同样只改不冲突的。Vacon 的别名转给 Danfoss，并加上「Vacon」「伟肯」。之后 Vacon 按第 5 条处理（全部移走则软删除，留有冲突行则停用）。
5. **名单外品牌**：没有任何引用（上述 `brand_id` 与品牌键各表）的软删除；仍有引用的停用（`status = 0`）。
6. **等级在界面上**：品牌列表增加「等级」列（核心、常做标签，普通不显示）与等级筛选；编辑弹窗增加等级单选、中文简介、英文简介；`/brands/options` 按 `brand_level DESC, brand_name ASC` 排序，前端各处下拉不再自行排序。
7. **事务边界**：Flyway 单个脚本一个事务（DDL 单独一个脚本在前），失败整体回滚。

## Risks / Trade-offs

- [改名影响对外单据] → 对外单据的品牌取正式名称，ABB Group → ABB、Mitsubishi → Mitsubishi Electric 后，新生成的报价单、PI 用新写法；已生成的单据快照不受影响。
- [别名被占用时跳过] → 例如两个品牌都写了同一个中文名时，只保留先写入的；迁移测试检查名单内没有重复别名。
- [名单外品牌被停用] → 停用不删除，引用保留，可在品牌管理页人工处理。

## 品牌清单

### 欧洲（34 个）

| 品牌 | 等级 | 别名 | 原产地 | 中文简介 | 英文简介 |
|---|---|---|---|---|---|
| Siemens | 核心 | 西门子 | Germany | 工控龙头；SIMATIC PLC/HMI、SINAMICS 驱动、SIRIUS 低压电器、SITOP 电源 | Automation leader from Munich: SIMATIC PLCs and HMIs, SINAMICS drives, SIRIUS low-voltage controls and SITOP power supplies. |
| ABB | 核心 | 阿西布朗勃法瑞、ABB Group | Switzerland | ACS 变频器、AC500 PLC、电机、机器人；B&R 是旗下品牌 | Zurich-based group known for ACS drives, AC500 PLCs, motors and robots; B&R is part of ABB. |
| Schneider Electric | 核心 | 施耐德电气、施耐德、Schneider | France | Modicon PLC、Altivar 变频器、Lexium 伺服、TeSys 低压电器；旗下有 Pro-face | Modicon PLCs, Altivar drives, Lexium servos and TeSys low-voltage controls; Pro-face belongs to Schneider. |
| Telemecanique | 常做 | 德美康 | France | 接近开关、限位开关；传感器业务 2023 年起归国巨 | Proximity and limit switches; the Telemecanique Sensors business has belonged to YAGEO since 2023. |
| Bosch Rexroth | 常做 | 博世力士乐、力士乐、Rexroth | Germany | 液压、IndraDrive 伺服、直线运动 | Hydraulics, IndraDrive servo systems and linear motion technology. |
| Lenze | 常做 | 伦茨 | Germany | 变频器、伺服、齿轮电机 | Inverters, servo drives and geared motors. |
| SEW-EURODRIVE |  | 赛威、SEW | Germany | 齿轮电机、减速机龙头 | Leading maker of geared motors and gear reducers. |
| Danfoss | 常做 | 丹佛斯、Vacon、伟肯 | Denmark | VLT 变频器、阀件、液压；2014 年收购 Vacon（伟肯，芬兰） | VLT drives, valves and hydraulics; acquired Finnish drive maker Vacon in 2014. |
| Beckhoff | 常做 | 倍福 | Germany | PC 控制、TwinCAT，EtherCAT 发明者 | PC-based control and TwinCAT software; the inventor of EtherCAT. |
| B&R |  | 贝加莱、B&R Automation | Austria | X20 系统、ACOPOS 伺服，2017 年起属 ABB | X20 control system and ACOPOS servo drives; part of ABB since 2017. |
| Phoenix Contact |  | 菲尼克斯 | Germany | 接线端子、接口继电器、电源 | Terminal blocks, interface relays and power supplies. |
| Weidmüller | 常做 | 魏德米勒、Weidmuller | Germany | 接线端子、接口继电器、电源 | Terminal blocks, interface relays and power supplies. |
| WAGO |  | 万可 | Germany | 接线端子、接口继电器、电源 | Terminal blocks, interface relays and power supplies. |
| Pilz |  | 皮尔磁 | Germany | 安全继电器、安全控制器 | Safety relays and safety controllers. |
| SICK |  | 西克 | Germany | 传感器第一梯队，以激光和安全传感器见长 | Top-tier sensor maker, strong in laser and safety sensors. |
| Pepperl+Fuchs |  | 倍加福、P+F | Germany | 传感器第一梯队，以防爆产品见长 | Top-tier sensor maker, strong in explosion-protection products. |
| ifm | 常做 | 易福门、ifm electronic | Germany | 传感器第一梯队 | Top-tier industrial sensor maker. |
| Balluff | 常做 | 巴鲁夫 | Germany | 传感器第一梯队，以位移传感器见长 | Top-tier sensor maker, strong in displacement sensors. |
| TURCK | 常做 | 图尔克 | Germany | 传感器第一梯队 | Top-tier industrial sensor maker. |
| Leuze |  | 劳易测 | Germany | 传感器第一梯队 | Top-tier industrial sensor maker. |
| Festo |  | 费斯托 | Germany | 气动元件 | Pneumatic components. |
| Bürkert | 常做 | 宝德、Burkert | Germany | 流体控制阀 | Fluid control valves. |
| Endress+Hauser |  | E+H、恩德斯豪斯 | Switzerland | 过程仪表 | Process instrumentation. |
| VEGA | 常做 | 威格 | Germany | 过程仪表，以雷达物位计见长 | Process instrumentation, best known for radar level meters. |
| KROHNE |  | 科隆 | Germany | 过程仪表，以流量计见长 | Process instrumentation, best known for flow meters. |
| Heidenhain |  | 海德汉 | Germany | 编码器、光栅尺、数控系统 | Encoders, linear scales and CNC controls. |
| Kübler |  | 库伯勒、Kubler | Germany | 编码器 | Encoders. |
| POSITAL | 常做 | 博思特 | Germany | 编码器 | Encoders. |
| Moeller |  | 金钟穆勒 | Germany | 2008 年起属伊顿，老款接触器、电动机保护断路器常见 | Part of Eaton since 2008; legacy contactors and motor protection circuit breakers are common. |
| Rittal |  | 威图 | Germany | 机柜和柜用温控 | Enclosures and enclosure climate control. |
| Murrelektronik |  | 穆尔 | Germany | 电源和分布式 I/O | Power supplies and distributed I/O. |
| Control Techniques |  | CT | United Kingdom | Unidrive 变频器，2017 年起属日本电产 | Unidrive inverters; part of Nidec since 2017. |
| FANOX | 常做 | — | Spain | 保护继电器 | Protection relays. |
| ASA | 常做 | — | Germany | 限位开关 | Limit switches. |

### 美洲（10 个）

| 品牌 | 等级 | 别名 | 原产地 | 中文简介 | 英文简介 |
|---|---|---|---|---|---|
| Allen-Bradley | 核心 | AB、罗克韦尔、Rockwell、Allen Bradley | United States | 北美龙头；Logix PLC、PanelView HMI、PowerFlex 变频器、Kinetix 伺服 | North American leader: Logix PLCs, PanelView HMIs, PowerFlex drives and Kinetix servos. |
| Honeywell | 常做 | 霍尼韦尔 | United States | DCS、MICRO SWITCH 开关、压力变送器 | DCS, MICRO SWITCH switches and pressure transmitters. |
| Emerson |  | 艾默生 | United States | Rosemount 变送器、Fisher 调节阀、ASCO 电磁阀；接手了原 GE 的 PLC 业务 | Rosemount transmitters, Fisher control valves and ASCO solenoid valves; took over the former GE PLC business. |
| Eaton | 常做 | 伊顿 | Ireland | 接触器、断路器、UPS；旗下有 Moeller | Contactors, circuit breakers and UPS; Moeller belongs to Eaton. |
| Parker |  | 派克汉尼汾、派克、Parker Hannifin | United States | 液压、气动、驱动 | Hydraulics, pneumatics and drives. |
| Banner |  | 邦纳 | United States | 光电和安全传感器 | Photoelectric and safety sensors. |
| Cognex |  | 康耐视 | United States | 机器视觉 | Machine vision. |
| Red Lion |  | 红狮 | United States | HMI、工业交换机、面板表 | HMIs, industrial Ethernet switches and panel meters. |
| MAC | 常做 | MAC Valves | United States | 电磁阀 | Solenoid valves. |
| Barksdale | 常做 | — | United States | 压力开关 | Pressure switches. |

### 日本（18 个）

| 品牌 | 等级 | 别名 | 原产地 | 中文简介 | 英文简介 |
|---|---|---|---|---|---|
| Mitsubishi Electric | 核心 | 三菱、三菱电机、Mitsubishi | Japan | MELSEC PLC、GOT HMI、FR 变频器、MR 伺服 | MELSEC PLCs, GOT HMIs, FR inverters and MR servos. |
| OMRON | 核心 | 欧姆龙 | Japan | PLC、传感器、温控器、继电器，控制元件很全 | PLCs, sensors, temperature controllers and relays; a very broad range of control components. |
| Yaskawa | 核心 | 安川 | Japan | Σ-7 伺服、A1000/GA700 变频器、MOTOMAN 机器人 | Sigma-7 servos, A1000/GA700 inverters and MOTOMAN robots. |
| Fanuc |  | 发那科 | Japan | 数控系统和机器人龙头，伺服备件需求大 | Leader in CNC systems and robots, with strong demand for servo spare parts. |
| Panasonic | 常做 | 松下、SUNX | Japan | MINAS 伺服、FP PLC、传感器（原 SUNX） | MINAS servos, FP PLCs and sensors (formerly SUNX). |
| Keyence |  | 基恩士 | Japan | 传感器和视觉，只做直销 | Sensors and machine vision, sold direct only. |
| Fuji Electric |  | 富士电机、富士 | Japan | 变频器为主 | Mainly inverters. |
| Hitachi |  | 日立 | Japan | 变频器为主 | Mainly inverters. |
| Toshiba |  | 东芝 | Japan | 变频器为主 | Mainly inverters. |
| Pro-face | 常做 | 普洛菲斯 | Japan | 触摸屏，2002 年起属施耐德 | Touch panels; part of Schneider Electric since 2002. |
| Koyo |  | 光洋 | Japan | PLC、编码器 | PLCs and encoders. |
| SMC |  | — | Japan | 气动元件，全球第一 | Pneumatic components; the world's largest pneumatics maker. |
| CKD |  | 喜开理 | Japan | 气动元件 | Pneumatic components. |
| Azbil |  | 阿自倍尔、山武、Yamatake | Japan | 温控器、调节阀 | Temperature controllers and control valves. |
| Oriental Motor |  | 东方马达 | Japan | 步进电机和小电机 | Stepper motors and small motors. |
| Nidec |  | 日本电产、尼得科 | Japan | 电机龙头 | Leading motor manufacturer. |
| IDEC |  | 和泉 | Japan | 按钮、继电器 | Pushbuttons and relays. |
| Hokuyo |  | 北阳 | Japan | 激光扫描仪 | Laser scanners. |

### 韩国、中国台湾（10 个）

| 品牌 | 等级 | 别名 | 原产地 | 中文简介 | 英文简介 |
|---|---|---|---|---|---|
| LS Electric | 常做 | LS 产电、LS产电、LG 产电、LS、LSIS | South Korea | XGT PLC、变频器、低压电器 | XGT PLCs, inverters and low-voltage switchgear. |
| Autonics |  | 奥托尼克斯 | South Korea | 传感器、温控器、计数器，性价比高 | Cost-effective sensors, temperature controllers and counters. |
| Delta | 核心 | 台达、Delta Electronics | Taiwan, China | 变频器、伺服、PLC、HMI、电源，产品线全 | Full line of inverters, servos, PLCs, HMIs and power supplies. |
| Weintek | 常做 | 威纶通、威纶、Weinview、WE!NTEK | Taiwan, China | 中小型触摸屏龙头 | Leading maker of small and mid-size touch panels. |
| AirTAC | 常做 | 亚德客 | Taiwan, China | 性价比高的气动品牌 | Cost-effective pneumatic components. |
| Advantech |  | 研华 | Taiwan, China | 工控机 | Industrial PCs. |
| Moxa |  | 摩莎 | Taiwan, China | 工业网络 | Industrial networking. |
| HIWIN |  | 上银 | Taiwan, China | 线性导轨 | Linear guideways. |
| TECO |  | 东元 | Taiwan, China | 电机、变频器 | Motors and inverters. |
| MEAN WELL |  | 明纬、Meanwell | Taiwan, China | 开关电源 | Switching power supplies. |

### 中国大陆（11 个）

| 品牌 | 等级 | 别名 | 原产地 | 中文简介 | 英文简介 |
|---|---|---|---|---|---|
| Inovance |  | 汇川 | China | 国产工控龙头，变频器、伺服、PLC | Leading Chinese automation brand: inverters, servos and PLCs. |
| INVT | 常做 | 英威腾 | China | 变频器 | Inverters. |
| Kinco | 常做 | 步科 | China | HMI 和伺服 | HMIs and servo systems. |
| Leadshine |  | 雷赛 | China | 步进和伺服 | Stepper and servo systems. |
| WECON | 常做 | 维控、Wecon | China | PLC 兼容三菱 FX 指令，另有 HMI | PLCs compatible with the Mitsubishi FX instruction set, plus HMIs. |
| Haiwell | 常做 | 海为 | China | 小型 PLC 和 HMI | Compact PLCs and HMIs. |
| Xinje |  | 信捷、Xinjie | China | 小型 PLC 和 HMI | Compact PLCs and HMIs. |
| Estun |  | 埃斯顿 | China | 伺服、机器人 | Servo systems and robots. |
| CHINT |  | 正泰 | China | 低压电器龙头 | Leading low-voltage switchgear brand. |
| Hollysys |  | 和利时 | China | DCS 系统 | DCS systems. |
| SUPCON |  | 中控 | China | DCS 系统 | DCS systems. |
