## Context

- 细分品类（140 个，二级）只有中英文名称，简介字段都为空；一级品类的简介已在 V1.2.50 写入。
- 系列表 `product_series` 只有品牌、名称、简介（`description`，单一字段）三项业务字段，没有品类；系列简介目前只在系统内显示，没有输出到独立站。
- 现有 183 个系列都来自《公司主营产品》表，分布在 20 个品牌；开发库只有 4 个商品引用了系列（含台达 S7-1200 这一条测试数据）。其他环境的品牌、系列 ID 与开发库不同，迁移只能按名称识别。
- 商品资料导入写系列时按「同品牌 + 系列名」匹配，比较时忽略大小写、空格和末尾的「Series」（`ContentWriter.seriesKey`）。

## Goals / Non-Goals

**Goals:** 细分品类都有中英文简介；每个常用品牌都有主力系列，系列都有简介；改正主营产品表里明显写错的系列。

**Non-Goals:**
- 不给系列加品类字段，也不加英文简介（系列不对外展示，需要时另提变更）。
- 不改系列的现有命名风格（如保留「FX Series」「ET-200」的写法），只改正确认写错的。
- 资料不足的品牌不编造系列：ASA、Barksdale、Broyce Control、Connectwell、FANOX、Italweber、Klemsan、Plastim、未识别品牌。

## Decisions

1. **清单放在 TSV 里，迁移由清单生成。** `data/subcategory-descriptions.tsv`（编码、中文简介、英文简介）与 `data/series.tsv`（品牌、系列、主要品类、处理、原名称、简介、待核实）是唯一来源，方便在表格软件里审阅；迁移 SQL 由脚本从 TSV 生成，避免手抄出错。`品类` 列只用于审阅分组，不写入数据库。
2. **系列简介只写中文。** 字段只有一个，系列目前只在系统内显示；按「系统内中文、对客户英文」的规则写中文。格式统一为「品类：要点」，如「变频器：SINAMICS G120 模块化（控制单元 CU + 功率模块 PM）」，一眼能看出是什么产品。
3. **只填空的，不覆盖。** 品类简介、系列简介都只在为空时写入，平台账号已手工填写的保留，与 V1.2.50 一致。
4. **改名在原 ID 上进行。** ATV48 → ATS48、ATS01/ATV22 → ATS01/ATS22：先找原名称，找到就改名；改名后的名称已存在（重复执行）就跳过。商品引用不受影响。
5. **删除挂错品牌的系列。** 台达 S7-1200：没有商品引用时软删除；有引用时停用并保留，由人工把商品改到正确系列。
6. **新增系列的选择标准。** 按品牌在公司 16 个一级品类里的主营产品线选：核心品牌（等级 2）覆盖 PLC、触摸屏、变频、伺服的现行与常见老款（老款维修需求大）；常做品牌（等级 1）选 3–7 个主力系列；普通品牌选 2–6 个。只收能确认存在的系列名，拿不准的在「待核实」列注明。
7. **清单之外的现有系列不动。** 其他环境里如有平台账号手工加的系列，迁移不删不改。

## 待核实（9 项）

审阅时重点看这几项，确认后再执行迁移：

| 品牌 / 系列 | 说明 |
|---|---|
| Schneider Electric / ATS48 | 原名称 ATV48，施耐德没有 ATV48，按软启动器 ATS48 改名 |
| Schneider Electric / ATS01/ATS22 | 原名称 ATS01/ATV22，按软启动器 ATS22 改名 |
| Delta / S7-1200 | 西门子系列误挂在台达下，删除（有商品时停用） |
| Danfoss / FC111 | 主营产品表里的型号，产品定位未能确认，简介写得较笼统 |
| Fuji Electric / AEC | 按 FRENIC-Ace 理解 |
| Pro-face / LM48 Series | 未能确认是哪个系列，简介写得较笼统 |
| MAC / 35 Series、45 Series | 系列名称按记忆整理 |
| Haiwell / C/T/H Series | 系列划分按记忆整理 |

## 系列清单（按品牌）

共 514 行：保留 180、改名 2、删除 1、新增 331，涉及 82 个品牌。每个系列的品类与简介见 `data/series.tsv`。

| 品牌 | 保留 | 新增 | 系列（新增的加 **粗体**，改名的标 ←原名称） |
|---|---|---|---|
| Siemens | 18 | 14 | LOGO!、**S7-200 SMART**、S7-1200、**S7-1500**、S7-300、S7-400、**S7-200**、ET-200、Basic Panel(KTP)、Comfort Panel、TP/OP、**SIMATIC IPC**、**SCALANCE X**、V20、G120、G120C、**S120**、Micro Master420、Micro Master430、Micro Master440、3RW30、3RW40、3RW44、SINAMICS V90、**SIMOTICS S-1FK7**、**SIRIUS 3RT**、**SIRIUS 3RV**、**SIMOCODE pro**、**SITOP**、**SITRANS P**、**SIPART PS2**、**SINUMERIK** |
| ABB | 10 | 10 | **AC500**、ACS 150、ACS 310、ACS 355、ACS 380、ACS 530、**ACS 550**、ACS 580、**ACS 800**、ACS 880、**DCS800**、PSR、PSE、PSTX、**AF**、**MS116/MS132**、**S200**、**Tmax**、**266**、**IRC5** |
| Schneider Electric | 22 | 7 | M100/M200 Series、TM221/241/251 Series、M340/M580 Series、TSX Micro、PREMIUM、QUANTUM、ZELIO Series、**Magelis HMI**、ATV12、ATV212、ATV310、ATV312、ATV320、ATV61、ATV71、ATV610、ATV930、ATS01/ATS22 ←ATS01/ATV22、ATS48 ←ATV48、Lexium 32、Lexium 28、Lexium 26、Lexium 16D、**TeSys D**、**TeSys GV2**、**Acti9**、**Compact NSX**、**Zelio Relay**、**Harmony XB5** |
| Telemecanique | 0 | 4 | **XS**、**XU**、**XC**、**XM** |
| Bosch Rexroth | 0 | 5 | **IndraDrive**、**MSK/MS2N**、**EFC**、**4WE**、**A10VSO** |
| Lenze | 1 | 3 | i500、**8400**、**9400**、**g500** |
| SEW-EURODRIVE | 0 | 4 | **MOVITRAC**、**MOVIDRIVE**、**MOVIMOT**、**R/F/K/S** |
| Danfoss | 14 | 6 | FC51、FC101、FC102、FC111、FC202、**FC280**、FC301、FC302、FC360、VACON 10、VACON 20、VACON 20Cold Plate、**VACON NXP**、**VACON 100**、MCD201、MCD202、MCD500、**MBS**、**KP**、**EV220B** |
| Beckhoff | 0 | 6 | **CX**、**EL**、**EK**、**AX5000**、**AM8000**、**CP** |
| B&R | 0 | 4 | **X20**、**Power Panel**、**ACOPOS**、**Automation PC** |
| Phoenix Contact | 0 | 7 | **QUINT**、**TRIO**、**PLC-INTERFACE**、**MINI Analog**、**UT/PT**、**FL SWITCH**、**PLCnext** |
| Weidmüller | 0 | 5 | **PRO**、**TERMSERIES**、**ACT20**、**u-remote**、**Klippon Connect** |
| WAGO | 0 | 6 | **750**、**PFC200**、**787**、**857**、**TOPJOB S**、**221** |
| Pilz | 0 | 3 | **PNOZ**、**PSS 4000**、**PSEN** |
| SICK | 0 | 6 | **W12**、**IME**、**DFS60**、**deTec4**、**S300**、**Lector** |
| Pepperl+Fuchs | 0 | 3 | **K-System**、**NBB/NBN**、**VisuNet** |
| ifm | 0 | 6 | **IF/IG/IE**、**O5/O6**、**PN**、**SI**、**TA/TN**、**AL** |
| Balluff | 0 | 5 | **BES**、**BOS**、**BTL**、**BIS**、**BNI** |
| TURCK | 0 | 4 | **BI/NI**、**TBEN**、**BL20**、**IM** |
| Leuze | 0 | 3 | **BCL**、**MLC**、**ODS** |
| Festo | 0 | 5 | **DSBC**、**ADN**、**VUVG**、**MS**、**CPX** |
| Bürkert | 0 | 4 | **6013**、**6213**、**ELEMENT**、**8692** |
| Endress+Hauser | 0 | 5 | **Cerabar**、**Promag**、**Micropilot**、**Liquiphant**、**iTEMP** |
| VEGA | 0 | 4 | **VEGAPULS**、**VEGAFLEX**、**VEGASWING**、**VEGABAR** |
| KROHNE | 0 | 4 | **OPTIFLUX**、**OPTIMASS**、**OPTIWAVE**、**H250** |
| Heidenhain | 0 | 4 | **ERN**、**ECN**、**LS**、**TNC** |
| Kübler | 0 | 2 | **Sendix**、**Codix** |
| POSITAL | 0 | 3 | **IXARC**、**TILTIX**、**LINARIX** |
| Moeller | 0 | 3 | **DILM**、**PKZM0**、**easy** |
| Eaton | 0 | 4 | **easyE4**、**XV**、**PowerXL**、**Bussmann** |
| Rittal | 0 | 2 | **Blue e**、**TopTherm** |
| Murrelektronik | 0 | 3 | **Emparro**、**MICO**、**Cube67** |
| Control Techniques | 0 | 5 | **Unidrive M**、**Unidrive SP**、**Commander C**、**Digitax HD**、**Mentor MP** |
| Allen-Bradley | 23 | 10 | Micro800 Series、Micrologix Series、Compactlogix Series、Controllogix Series、**GuardLogix**、SLC-500 Series、PLC-5 Series、Flex I/O、**POINT I/O**、PanelView 800 Graphic Terminals、PanelView Compact Graphic Terminals、PanelView Plus 6 Compact Graphic Terminals、PanelView Plus 6 Graphic Terminals、PanelView Plus 7 Graphic Terminals、PanelView 5000 Graphic Terminals、**Stratix**、PowerFlex 4、PowerFlex 4M、PowerFlex 40、PowerFlex 40P、PowerFlex 400、PowerFlex 523、PowerFlex 525、PowerFlex 527、**PowerFlex 700**、PowerFlex 753、PowerFlex 755、**Kinetix 5500**、**Kinetix 5700**、**100-C**、**140M**、**1606**、**Guardmaster** |
| Honeywell | 0 | 3 | **MICRO SWITCH**、**SmartLine**、**UDC** |
| Emerson | 0 | 4 | **Rosemount 3051**、**Micro Motion**、**Fisher DVC6200**、**ASCO** |
| Parker | 0 | 5 | **Compax3**、**AC10**、**D1VW**、**PV**、**P1D** |
| Banner | 0 | 4 | **QS18**、**Q4X**、**EZ-SCREEN**、**TL50** |
| Cognex | 0 | 2 | **In-Sight**、**DataMan** |
| Red Lion | 0 | 4 | **Graphite**、**G3**、**PAX**、**N-Tron** |
| MAC | 0 | 2 | **35 Series**、**45 Series** |
| Mitsubishi Electric | 14 | 6 | FX Series、**iQ-F Series**、L Series、Q Series、**iQ-R Series**、CC-Link、CC-Link/LT、GOT1000、GOT2000、FR-D700、FR-E700、**FR-E800**、**FR-A700**、FR-A800、FR-F800、JE Series、J3 Series、J4 Series、**J5 Series**、**M800/M80** |
| OMRON | 11 | 10 | CP Series、CJ Series、CS Series、NJ Series、NX Series、Zen Series、Remote I/O DRT、NS Series、NB Series、**NA Series**、MX2、G5 Series、**1S Series**、**E2E**、**E3Z**、**E6B2/E6C2**、**E5CC**、**H3CR**、**MY**、**G3NA**、**S8VK** |
| Yaskawa | 7 | 5 | J1000、V1000、A1000、E1000、H1000、**GA500**、GA700、**Varispeed G7/F7**、**Sigma-5**、Sigma 7 Series、**MP3000**、**YRC1000/DX200** |
| Fanuc | 0 | 6 | **Series 0i**、**αi 伺服放大器**、**βi 伺服**、**αi 主轴**、**R-30iB**、**iPendant** |
| Panasonic | 9 | 2 | FPOR/FP Series、FP-X Series、FP7 Series、FP-2SH Series、GT 02、GT 05、GT 12、GT 32、**A5 Series**、A6 Series、**CX-400** |
| Keyence | 0 | 6 | **KV**、**FS-N**、**LV-N**、**IL**、**SR**、**CV-X** |
| Fuji Electric | 6 | 2 | MINI[C]、MULTI[E]、MEGA[G]、ECO[F]、HVAC[AR]、AEC、**ALPHA5**、**MICREX-SX** |
| Hitachi | 4 | 1 | WJ200、WJ200N、SJ700、**SJ-P1**、NJ600B |
| Toshiba | 4 | 0 | VFNC-3、VFS-15、AS-1、PS-1 |
| Pro-face | 5 | 2 | GP4000 Series、**GP3000 Series**、SP5000 Series、**ST6000 Series**、FP5000 Series、LT4000M Series、LM48 Series |
| Koyo | 0 | 2 | **DirectLOGIC**、**TRD** |
| SMC | 0 | 7 | **SY**、**CQ2**、**CP96**、**AC**、**ZP**、**KQ2**、**ITV** |
| CKD | 0 | 3 | **4GB**、**SCM**、**SSD2** |
| Azbil | 0 | 3 | **SDC**、**HPX**、**AVP** |
| Oriental Motor | 0 | 3 | **PKP**、**AZ**、**BLE2** |
| Nidec | 0 | 1 | **VRL/VRB** |
| IDEC | 0 | 4 | **FC6A**、**HG**、**RJ**、**HW** |
| Hokuyo | 0 | 2 | **UAM**、**UST** |
| LS Electric | 14 | 3 | MASTER-K/GLOFA(K10S1/K80S/K120S/GM7/GM7U)、MASTER-K/GLOFA(K200S/K300S/GM6/GM4)、XGB Series、XGT Series、XP Series、eXP Series、iXP Series、IE5/IC5、IG5A、IP5A、IS7、IV5、S100、H100、**L7**、**Metasol MC**、**Metasol ABN/ABS** |
| Autonics | 0 | 4 | **PR**、**BJ**、**E40/E50**、**TK** |
| Delta | 15 | 5 | ES2/EX2 Series、SS2/SA2/SX2、SV2 Series、EH3 Series、EC3 Series、**AS Series**、AH500 Series、DOP-100 Series、VFD-E、**VFD-EL**、VFD-B、MS300、MH300、C-2000、CP-2000、ASDA-A2、**ASDA-A3**、ASDA-B2、**ASDA-B3**、**ECMA**、~~S7-1200~~（删除） |
| Weintek | 4 | 1 | eMT Series、iE Series、iP Series、CMT Series、**MT8000i/MT6000i** |
| AirTAC | 0 | 4 | **SC**、**MAL**、**4V**、**GFC/GFR** |
| Advantech | 0 | 5 | **ADAM**、**IPC**、**UNO**、**PPC**、**EKI** |
| Moxa | 0 | 5 | **EDS**、**NPort**、**MGate**、**ioLogik**、**AWK** |
| HIWIN | 0 | 1 | **E1** |
| TECO | 0 | 2 | **E510**、**A510** |
| MEAN WELL | 0 | 4 | **NDR**、**SDR**、**HDR**、**DDR** |
| Inovance | 0 | 6 | **MD500**、**MD290**、**SV660**、**IS620**、**H3U/H5U**、**IT7000** |
| INVT | 0 | 5 | **GD20**、**GD200A**、**GD350**、**DA200**、**IVC** |
| Kinco | 0 | 4 | **GL**、**K**、**FD**、**2M/3M** |
| Leadshine | 0 | 2 | **DM**、**EL5/EL6** |
| WECON | 0 | 3 | **LX3V**、**LX5V**、**PI** |
| Haiwell | 0 | 1 | **C/T/H Series** |
| Xinje | 0 | 4 | **XD**、**XC**、**TG**、**DS5** |
| Estun | 0 | 2 | **ProNet**、**ED3L** |
| CHINT | 0 | 5 | **NXC**、**NR2**、**NXB**、**NM1**、**NVF2G** |
| Hollysys | 0 | 2 | **LK**、**LE** |
| SUPCON | 0 | 2 | **JX-300XP**、**ECS-700** |
| Carlo Gavazzi | 0 | 3 | **RM**、**EM24**、**DPA/DPB** |
| Hitech | 1 | 0 | PWS Series |

## 细分品类简介

140 个细分品类的中英文简介见 `data/subcategory-descriptions.tsv`。写法与一级品类一致：中文一句话说明产品是什么、用在哪里；英文对应同一句意思，供独立站使用。示例：

| 编码 | 中文简介 | 英文简介 |
|---|---|---|
| `plc_cpu` | PLC 的中央处理单元及一体式小型 PLC（CPU 与 I/O 集成），负责程序运行和逻辑控制。 | PLC central processing units and compact all-in-one PLCs with built-in I/O that run the control program. |
| `soft_starter` | 降低电机启动电流与冲击的软启动器。 | Soft starters that reduce motor starting current and mechanical shock. |
| `hmi_repair_part` | 触摸屏和工控机的维修备件：触摸玻璃、LCD 屏、背光、面膜、主板等。 | Repair parts for HMIs and panel PCs: touch glass, LCD screens, backlights, overlays and mainboards. |

## Risks / Trade-offs

- [系列名凭经验整理，可能有个别不准] → 「待核实」列标出拿不准的项；系列可在系列管理里随时改名、停用，商品引用不受影响。
- [其他环境的系列名写法与开发库不同（如多了空格）] → 按忽略大小写和首尾空格匹配；仍匹配不上的会新建一条，可在系列管理里合并。
- [系列数量从 183 增加到约 510，下拉选项变长] → 系列选项本来就按品牌过滤，单个品牌最多 33 个（Allen-Bradley）。

## Migration Plan

- 迁移 V1.3.9：写入细分品类简介、系列（含改名与删除）；平台级数据，一次执行对所有租户生效；可重复执行。
- 反向 SQL：`sql/build/rollback/V1.3.9__seed_subcategory_desc_and_series_rollback.sql`，清空本次写入的简介（内容与清单一致的才清空）、软删除本次新建的系列（无商品引用的）、两个改名改回原名、台达 S7-1200 恢复。
