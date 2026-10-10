## Context

- `product_category`：两级结构（`parent_id` 为空是一级），`category_code` 平台内唯一（唯一键包含已软删除的行），`category_name` 是英文名（独立站用），`category_name_zh` 是中文名（业务界面优先显示）。
- 引用：`product.category_id`（只能是一级），`supplier_product_scope.category_id`（二级），`product_candidate.category_id`（候选建议，一级）。
- 开发库现状：在用 4 个一级（plc、hmi、inverter、servo）与其下 10 个二级；其余旧品类（controllers、sensors、switchgear、power_supplies、hydraulics、pneumatics、transmission、spares 及其二级）已软删除。其他环境可能仍在用这些旧品类，迁移不能假设它们已删除。

## Goals / Non-Goals

**Goals:** 16 个一级、140 个二级品类中英文初始化；已在用的品类 ID 与编码不变；旧品类引用不丢；任何环境可重复执行。

**Non-Goals:** 品类结构改三级（领域只是排序分组）；改动品类管理界面；独立站新品类页面；按新品类重新归类已有商品（只迁移旧品类上的引用）。

## Decisions

1. **按编码更新或插入**：每个品类按 `category_code` 查（含软删除行），有则更新名称、上级、排序，并把 `status` 置 1、`deleted_at` 置空；没有则插入。一级先写，二级按上级编码取父 ID。用 `INSERT … ON DUPLICATE KEY UPDATE`（唯一键 `tenant_id + category_code`），天然可重复执行。
2. **保留编码**：在用的 `plc`、`hmi`、`inverter`、`servo` 直接作为对应新一级品类的编码；二级 `plc_cpu`、`io_module`、`comm_module`、`safety_plc`、`hmi_panel`、`vfd`、`soft_starter`、`servo_drive`、`servo_motor` 作为对应新二级的编码；`industrial_network` 原为 PLC 下的二级，改为一级「工业网络」（`parent_id` 置空），它没有被引用，商品今后可以选它。旧的软删除编码与新体系同名的（`sensors`、`switchgear`、`power_supplies`、`contactor`、`circuit_breaker`、`relay`、`ups`、`reducer`、`coupling`、`flowmeter`、`level_meter`、`pressure_gauge`、`hydraulic_valve`、`hydraulic_pump`、`hydraulic_cylinder`、`pneumatic_cylinder`、`pneumatic_fitting`）直接复用。
3. **旧品类处理顺序**（同一迁移内，先写新品类再处理旧品类）：
   - 有对应关系的旧编码把引用改到新品类：`controllers` → `plc`，`hydraulics`、`pneumatics` → `fluid_power`，`transmission` → `motors`，`spares` → `electronic_components`，`power_module` → `din_power`。三张引用表都改，并更新 `update_time`。
   - 不在新体系里的旧品类：没有任何引用的软删除；仍被引用的（没有对应新品类，如旧二级 `bearing`、`seal`）改为停用并保留，迁移日志里列出，由平台账号在品类管理页处理。
   - 只处理平台级（`tenant_id = 0`）数据。
4. **排序**：一级按 1–16 的顺序 `sort_order` = 10、20…160；二级按定稿顺序 10、20…，在品类管理页与选择器里按定稿顺序展示。
5. **事务边界**：Flyway 单个迁移脚本在一个事务内执行（MySQL DDL 除外，本迁移只有 DML），失败整体回滚。

## Risks / Trade-offs

- [复用已软删除的旧编码会让它们「复活」] → 复活后名称、上级都改成新体系，旧含义（如 `sensors` 原为「传感与仪表」）不再保留；这些行当前没有引用，复用比新建一个近义编码更利于独立站网址稳定。
- [二级中文名较短（接近、光电、附件）] → 按定稿写入；界面在上级品类下显示，语义清楚。需要时可在品类管理页改中文名，不影响编码。
- [其他环境仍有引用但无对应的新品类的旧品类] → 停用不删除，引用不丢，人工归类。

## 品类清单（中英文对照）

### A 控制与自动化

**1 PLC 与控制器** · PLC & Controllers · `plc`（12 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `plc_cpu` | PLC CPU/一体机 | PLC CPUs & Compact PLCs |
| `io_module` | I/O 模块 | I/O Modules |
| `distributed_io` | 分布式 I/O 与接口模块 | Distributed I/O & Interface Modules |
| `comm_module` | 通讯模块 | Communication Modules |
| `redundancy_module` | 冗余与同步模块 | Redundancy & Synchronization Modules |
| `function_module` | 功能模块（定位/计数/温度） | Function Modules (Positioning/Counting/Temperature) |
| `plc_power` | PLC 电源 | PLC Power Supplies |
| `plc_rack` | 底板/机架/基座 | Racks, Backplanes & Bases |
| `expansion_board` | 扩展板 | Expansion Boards |
| `safety_plc` | 安全控制器 | Safety Controllers |
| `motion_controller` | 运动控制器 | Motion Controllers |
| `plc_accessory` | PLC 配件 | PLC Accessories |

**2 HMI 与工控机** · HMI & Industrial PCs · `hmi`（7 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `hmi_panel` | 触摸屏 | Touch Panels |
| `text_display` | 文本显示器/按键面板 | Text Displays & Key Panels |
| `handheld_hmi` | 手持式 HMI | Handheld HMIs |
| `panel_pc` | 平板工控机 | Panel PCs |
| `box_pc` | 箱式工控机 | Box PCs |
| `industrial_monitor` | 工业显示器 | Industrial Monitors |
| `hmi_repair_part` | HMI 维修备件（触摸玻璃/LCD/背光） | HMI Repair Parts (Touch Glass/LCD/Backlight) |

**3 工业网络** · Industrial Networking · `industrial_network`（7 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `ethernet_switch` | 以太网交换机 | Industrial Ethernet Switches |
| `gateway` | 网关/协议转换 | Gateways & Protocol Converters |
| `fieldbus` | 现场总线组件 | Fieldbus Components |
| `fiber_converter` | 光纤收发器 | Fiber Media Converters |
| `io_link_master` | IO-Link 主站 | IO-Link Masters |
| `industrial_wireless` | 工业无线/路由器 | Industrial Wireless & Routers |
| `sfp_module` | SFP 光模块与网络附件 | SFP Modules & Network Accessories |

**4 机器人与数控** · Robotics & CNC · `robotics_cnc`（7 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `robot_controller` | 机器人控制柜与板卡 | Robot Controllers & Boards |
| `teach_pendant` | 示教器 | Teach Pendants |
| `robot_motor` | 机器人电机与备件 | Robot Motors & Spare Parts |
| `gripper` | 夹爪/末端执行器 | Grippers & End Effectors |
| `robot_cable` | 机器人线缆 | Robot Cables |
| `cnc_system` | CNC 系统与面板 | CNC Systems & Panels |
| `spindle` | 主轴驱动与电机 | Spindle Drives & Motors |


### B 驱动与运动

**5 变频器与驱动** · Inverters & Drives · `inverter`（9 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `vfd` | 通用变频器 | General Purpose Inverters (VFDs) |
| `modular_drive` | 模块化驱动 | Modular Drives |
| `dc_drive` | 直流调速器 | DC Drives |
| `soft_starter` | 软启动器 | Soft Starters |
| `motor_starter` | 电机启动器 | Motor Starters |
| `drive_option` | 操作面板与选件卡 | Operator Panels & Option Cards |
| `drive_board` | 驱动板卡备件 | Drive Boards & Spare Parts |
| `braking` | 制动单元/电阻 | Braking Units & Resistors |
| `drive_filter` | 电抗器/EMC 滤波器 | Reactors & EMC Filters |

**6 伺服与运动** · Servo & Motion · `servo`（7 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `servo_drive` | 伺服驱动器 | Servo Drives |
| `servo_motor` | 伺服电机 | Servo Motors |
| `servo_kit` | 伺服套装 | Servo Kits |
| `stepper` | 步进驱动/电机 | Stepper Drives & Motors |
| `linear_motor` | 直线/直驱电机 | Linear & Direct Drive Motors |
| `servo_cable` | 伺服电缆 | Servo Cables |
| `servo_accessory` | 伺服附件 | Servo Accessories |

**7 电机与传动** · Motors & Power Transmission · `motors`（8 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `ac_motor` | 交流电机 | AC Motors |
| `gear_motor` | 齿轮电机 | Gear Motors |
| `reducer` | 减速机 | Gear Reducers |
| `dc_motor` | 直流电机 | DC Motors |
| `brake_clutch` | 制动器/离合器 | Brakes & Clutches |
| `electric_actuator` | 电动执行器 | Electric Actuators |
| `coupling` | 联轴器 | Couplings |
| `motor_accessory` | 电机配件 | Motor Accessories |


### C 检测与仪表

**8 传感器** · Sensors · `sensors`（15 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `proximity_sensor` | 接近 | Proximity Sensors |
| `photoelectric_sensor` | 光电 | Photoelectric Sensors |
| `fiber_sensor` | 光纤 | Fiber Optic Sensors |
| `ultrasonic_sensor` | 超声波 | Ultrasonic Sensors |
| `encoder` | 编码器 | Encoders |
| `displacement_sensor` | 位移 | Displacement Sensors |
| `magnetic_switch` | 磁性开关 | Magnetic Switches |
| `limit_switch` | 限位开关 | Limit Switches |
| `temperature_sensor` | 温度/温湿度 | Temperature & Humidity Sensors |
| `vision` | 视觉/相机 | Vision Systems & Cameras |
| `color_sensor` | 颜色/色标 | Color & Contrast Sensors |
| `code_reader` | 读码/RFID | Code Readers & RFID |
| `safety_light_curtain` | 安全光幕/扫描仪 | Safety Light Curtains & Laser Scanners |
| `vibration_sensor` | 振动/倾角/电流 | Vibration, Tilt & Current Sensors |
| `sensor_accessory` | 传感器配件 | Sensor Accessories |

**9 过程仪表与控制仪表** · Process & Control Instruments · `instruments`（14 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `level_meter` | 物位计 | Level Meters |
| `level_switch` | 液位开关 | Level Switches |
| `pressure_transmitter` | 压力传感器/变送器 | Pressure Sensors & Transmitters |
| `pressure_switch` | 压力开关 | Pressure Switches |
| `flowmeter` | 流量计/开关 | Flow Meters & Flow Switches |
| `temperature_transmitter` | 温度变送器 | Temperature Transmitters |
| `load_cell` | 称重传感器与仪表 | Load Cells & Weighing Indicators |
| `pressure_gauge` | 压力表/温度计 | Pressure Gauges & Thermometers |
| `analyzer` | 分析仪表 | Analytical Instruments |
| `recorder` | 记录仪 | Recorders |
| `temperature_controller` | 温控器/PID | Temperature & PID Controllers |
| `counter_timer` | 计数器/计时器 | Counters & Timers |
| `panel_meter` | 数显面板表 | Digital Panel Meters |
| `energy_meter` | 电能表 | Energy Meters |


### D 电气与配电

**10 低压电器** · Low Voltage Switchgear · `switchgear`（10 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `contactor` | 接触器 | Contactors |
| `thermal_overload` | 热过载继电器 | Thermal Overload Relays |
| `motor_protection_breaker` | 电动机保护断路器 | Motor Protection Circuit Breakers |
| `motor_management` | 电机管理/保护 | Motor Management & Protection |
| `circuit_breaker` | 断路器 | Circuit Breakers |
| `fuse` | 熔断器 | Fuses |
| `surge_protector` | 浪涌保护器 | Surge Protective Devices |
| `disconnect_switch` | 隔离开关 | Disconnect Switches |
| `transfer_switch` | 双电源转换开关 | Automatic Transfer Switches |
| `switchgear_accessory` | 附件 | Switchgear Accessories |

**11 继电器与信号接口** · Relays & Signal Interfaces · `relays`（8 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `relay` | 中间继电器 | General Purpose Relays |
| `solid_state_relay` | 固态继电器 | Solid State Relays |
| `safety_relay` | 安全继电器 | Safety Relays |
| `timer_relay` | 时间继电器 | Timer Relays |
| `monitoring_relay` | 监控继电器 | Monitoring Relays |
| `protection_relay` | 保护继电器 | Protection Relays |
| `signal_isolator` | 信号隔离器/安全栅 | Signal Isolators & Safety Barriers |
| `interface_module` | 接口模块 | Interface Modules |

**12 电源与变压器** · Power Supplies & Transformers · `power_supplies`（7 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `din_power` | 导轨电源 | DIN Rail Power Supplies |
| `ups` | UPS/缓冲/冗余 | UPS, Buffer & Redundancy Modules |
| `dc_dc` | DC/DC | DC/DC Converters |
| `transformer` | 控制/隔离变压器 | Control & Isolation Transformers |
| `power_controller` | 电力调整器/调功器 | Power Controllers & Thyristor Units |
| `power_filter` | 电源滤波器 | Power Line Filters |
| `capacitor_compensation` | 电容/补偿 | Capacitors & Power Factor Correction |

**13 按钮开关与指示** · Pushbuttons, Switches & Indicators · `pilot_devices`（5 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `pushbutton` | 按钮/急停/选择开关 | Pushbuttons, E-Stops & Selector Switches |
| `cam_switch` | 凸轮/万能转换开关 | Cam & Changeover Switches |
| `indicator` | 指示灯/三色灯 | Indicator Lights & Signal Towers |
| `safety_switch` | 安全门锁/拉绳开关 | Safety Door Locks & Rope Pull Switches |
| `foot_switch` | 脚踏/操纵杆/操作盒 | Foot Switches, Joysticks & Control Stations |

**14 线缆与连接** · Cables & Connectivity · `cables`（5 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `programming_cable` | 编程/通讯电缆 | Programming & Communication Cables |
| `drive_cable` | 驱动/面板连接线 | Drive & Panel Connection Cables |
| `fiber_patch_cord` | 光纤跳线 | Fiber Patch Cords |
| `bus_connector` | 总线接头/连接器 | Bus Connectors |
| `terminal_block` | 接线端子 | Terminal Blocks |


### E 流体动力

**15 流体动力** · Fluid Power · `fluid_power`（13 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `solenoid_valve` | 电磁阀 | Solenoid Valves |
| `process_valve` | 过程阀 | Process Valves |
| `self_operated_valve` | 自力式调节阀 | Self-Operated Regulators |
| `valve_positioner` | 阀门定位器与执行机构 | Valve Positioners & Actuators |
| `proportional_valve` | 比例阀/伺服阀 | Proportional & Servo Valves |
| `pneumatic_cylinder` | 气缸 | Pneumatic Cylinders |
| `air_preparation` | 气源处理 | Air Preparation Units |
| `vacuum` | 真空元件 | Vacuum Components |
| `pneumatic_fitting` | 气动接头 | Pneumatic Fittings |
| `hydraulic_valve` | 液压阀 | Hydraulic Valves |
| `hydraulic_pump` | 液压泵/马达 | Hydraulic Pumps & Motors |
| `hydraulic_cylinder` | 液压缸 | Hydraulic Cylinders |
| `hydraulic_filter` | 液压滤芯 | Hydraulic Filter Elements |


### F 元器件与备件

**16 电子元器件** · Electronic Components · `electronic_components`（6 个二级）

| 编码 | 中文名 | 英文名 |
|---|---|---|
| `igbt_module` | IGBT/功率模块 | IGBT & Power Modules |
| `control_board` | 控制板 | Control Boards |
| `capacitor` | 电容/元件 | Capacitors & Components |
| `cooling_fan` | 风扇/热管理 | Fans & Thermal Management |
| `backup_battery` | 备用电池 | Backup Batteries |
| `lcd_module` | LCD 与显示模组 | LCD & Display Modules |

