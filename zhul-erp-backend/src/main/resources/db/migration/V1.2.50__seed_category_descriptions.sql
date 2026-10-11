-- 品类中英文简介（openspec add-category-description）：为 17 个一级品类写入中英文简介，已有的不覆盖。可重复执行

UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '可编程控制器 CPU、I/O 与通讯模块、分布式 I/O、安全控制器与运动控制器，以及电源、机架等配件。', `description_zh`),
    `description` = IF(`description` = '', 'Programmable logic controllers: CPUs, I/O and communication modules, distributed I/O, safety and motion controllers, plus power supplies, racks and accessories.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'plc';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '触摸屏、文本显示器、手持 HMI、平板与箱式工控机、工业显示器，以及触摸玻璃、LCD 等维修备件。', `description_zh`),
    `description` = IF(`description` = '', 'Touch panels, text displays, handheld HMIs, panel and box PCs, industrial monitors, and repair parts such as touch glass and LCDs.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'hmi';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '工业以太网交换机、网关与协议转换、现场总线组件、光纤收发器、IO-Link 主站、工业无线与 SFP 光模块。', `description_zh`),
    `description` = IF(`description` = '', 'Industrial Ethernet switches, gateways and protocol converters, fieldbus components, fiber media converters, IO-Link masters, industrial wireless and SFP modules.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'industrial_network';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '机器人控制柜与板卡、示教器、机器人电机与备件、夹爪与线缆，以及 CNC 系统、面板与主轴驱动。', `description_zh`),
    `description` = IF(`description` = '', 'Robot controllers and boards, teach pendants, robot motors and spare parts, grippers and cables, plus CNC systems, panels and spindle drives.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'robotics_cnc';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '通用与模块化变频器、直流调速器、软启动器与电机启动器，以及操作面板、选件卡、驱动板、制动单元和滤波器。', `description_zh`),
    `description` = IF(`description` = '', 'General purpose and modular inverters, DC drives, soft starters and motor starters, with operator panels, option cards, drive boards, braking units and filters.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'inverter';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '伺服驱动器、伺服电机与套装、步进驱动与电机、直线与直驱电机，以及伺服电缆和附件。', `description_zh`),
    `description` = IF(`description` = '', 'Servo drives, servo motors and kits, stepper drives and motors, linear and direct drive motors, plus servo cables and accessories.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'servo';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '交流、直流与齿轮电机，减速机、制动器与离合器、电动执行器、联轴器及电机配件。', `description_zh`),
    `description` = IF(`description` = '', 'AC, DC and gear motors, gear reducers, brakes and clutches, electric actuators, couplings and motor accessories.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'motors';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '接近、光电、光纤、超声波、位移、温湿度等传感器，编码器、限位与磁性开关，视觉、读码与 RFID，安全光幕和扫描仪。', `description_zh`),
    `description` = IF(`description` = '', 'Proximity, photoelectric, fiber optic, ultrasonic, displacement and temperature sensors, encoders, limit and magnetic switches, vision systems, code readers and RFID, safety light curtains and scanners.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'sensors';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '物位、压力、流量、温度等过程仪表与开关，称重、分析仪表与记录仪，以及温控器、计数器、面板表和电能表。', `description_zh`),
    `description` = IF(`description` = '', 'Level, pressure, flow and temperature instruments and switches, weighing and analytical instruments, recorders, plus temperature controllers, counters, panel meters and energy meters.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'instruments';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '接触器、热过载继电器、电动机保护与普通断路器、熔断器、浪涌保护器、隔离开关、双电源转换开关及附件。', `description_zh`),
    `description` = IF(`description` = '', 'Contactors, thermal overload relays, motor protection and general circuit breakers, fuses, surge protectors, disconnect and transfer switches, and accessories.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'switchgear';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '中间继电器、固态继电器，安全、时间、监控与保护继电器，以及信号隔离器、安全栅和接口模块。', `description_zh`),
    `description` = IF(`description` = '', 'General purpose and solid state relays, safety, timer, monitoring and protection relays, plus signal isolators, safety barriers and interface modules.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'relays';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '导轨电源、UPS 与冗余模块、DC/DC 转换器、控制与隔离变压器、电力调整器、电源滤波器及电容补偿。', `description_zh`),
    `description` = IF(`description` = '', 'DIN rail power supplies, UPS and redundancy modules, DC/DC converters, control and isolation transformers, power controllers, line filters and power factor correction.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'power_supplies';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '按钮、急停与选择开关，凸轮与万能转换开关，指示灯与三色灯，安全门锁与拉绳开关，脚踏开关与操作盒。', `description_zh`),
    `description` = IF(`description` = '', 'Pushbuttons, e-stops and selector switches, cam switches, indicator lights and signal towers, safety door locks and rope pull switches, foot switches and control stations.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'pilot_devices';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '编程与通讯电缆、驱动与面板连接线、光纤跳线、总线接头与连接器、接线端子。', `description_zh`),
    `description` = IF(`description` = '', 'Programming and communication cables, drive and panel connection cables, fiber patch cords, bus connectors and terminal blocks.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'cables';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '电磁阀、过程阀与调节阀、阀门定位器、比例阀，气缸、气源处理、真空元件与气动接头，以及液压阀、泵、缸和滤芯。', `description_zh`),
    `description` = IF(`description` = '', 'Solenoid, process and control valves, valve positioners, proportional valves, pneumatic cylinders, air preparation, vacuum components and fittings, plus hydraulic valves, pumps, cylinders and filters.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'fluid_power';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', 'IGBT 与功率模块、控制板、电容等元件、风扇与散热、备用电池以及 LCD 显示模组，多用于设备维修。', `description_zh`),
    `description` = IF(`description` = '', 'IGBT and power modules, control boards, capacitors and components, fans and thermal management, backup batteries and LCD modules, mostly used for equipment repair.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'electronic_components';
UPDATE `product_category` SET
    `description_zh` = IF(`description_zh` = '', '不属于以上品类的工业自动化产品；询盘里匹配不到品类的型号先归到这里，审核时可改到具体品类。', `description_zh`),
    `description` = IF(`description` = '', 'Industrial automation products that fall outside the categories above.', `description`),
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `category_code` = 'other';
