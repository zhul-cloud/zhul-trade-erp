-- 品类初始化（openspec seed-product-categories）：16 个一级、140 个二级，中英文
-- 按编码更新或插入（含已软删除的同编码行），已在用的 plc/hmi/inverter/servo 及其二级保留 ID 与编码；可重复执行

-- ---------------------------------------------------------------- 一级品类
-- A 控制与自动化
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'plc' AS code, 'PLC & Controllers' AS en, 'PLC 与控制器' AS zh, NULL AS pid, 10 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'hmi' AS code, 'HMI & Industrial PCs' AS en, 'HMI 与工控机' AS zh, NULL AS pid, 20 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'industrial_network' AS code, 'Industrial Networking' AS en, '工业网络' AS zh, NULL AS pid, 30 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'robotics_cnc' AS code, 'Robotics & CNC' AS en, '机器人与数控' AS zh, NULL AS pid, 40 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- B 驱动与运动
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'inverter' AS code, 'Inverters & Drives' AS en, '变频器与驱动' AS zh, NULL AS pid, 50 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'servo' AS code, 'Servo & Motion' AS en, '伺服与运动' AS zh, NULL AS pid, 60 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'motors' AS code, 'Motors & Power Transmission' AS en, '电机与传动' AS zh, NULL AS pid, 70 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- C 检测与仪表
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'sensors' AS code, 'Sensors' AS en, '传感器' AS zh, NULL AS pid, 80 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'instruments' AS code, 'Process & Control Instruments' AS en, '过程仪表与控制仪表' AS zh, NULL AS pid, 90 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- D 电气与配电
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'switchgear' AS code, 'Low Voltage Switchgear' AS en, '低压电器' AS zh, NULL AS pid, 100 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'relays' AS code, 'Relays & Signal Interfaces' AS en, '继电器与信号接口' AS zh, NULL AS pid, 110 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'power_supplies' AS code, 'Power Supplies & Transformers' AS en, '电源与变压器' AS zh, NULL AS pid, 120 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'pilot_devices' AS code, 'Pushbuttons, Switches & Indicators' AS en, '按钮开关与指示' AS zh, NULL AS pid, 130 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'cables' AS code, 'Cables & Connectivity' AS en, '线缆与连接' AS zh, NULL AS pid, 140 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- E 流体动力
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'fluid_power' AS code, 'Fluid Power' AS en, '流体动力' AS zh, NULL AS pid, 150 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- F 元器件与备件
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'electronic_components' AS code, 'Electronic Components' AS en, '电子元器件' AS zh, NULL AS pid, 160 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';

-- ---------------------------------------------------------------- 二级品类
-- PLC 与控制器
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'plc_cpu' AS code, 'PLC CPUs & Compact PLCs' AS en, 'PLC CPU/一体机' AS zh, 10 AS so
    UNION ALL SELECT 'io_module' AS code, 'I/O Modules' AS en, 'I/O 模块' AS zh, 20 AS so
    UNION ALL SELECT 'distributed_io' AS code, 'Distributed I/O & Interface Modules' AS en, '分布式 I/O 与接口模块' AS zh, 30 AS so
    UNION ALL SELECT 'comm_module' AS code, 'Communication Modules' AS en, '通讯模块' AS zh, 40 AS so
    UNION ALL SELECT 'redundancy_module' AS code, 'Redundancy & Synchronization Modules' AS en, '冗余与同步模块' AS zh, 50 AS so
    UNION ALL SELECT 'function_module' AS code, 'Function Modules (Positioning/Counting/Temperature)' AS en, '功能模块（定位/计数/温度）' AS zh, 60 AS so
    UNION ALL SELECT 'plc_power' AS code, 'PLC Power Supplies' AS en, 'PLC 电源' AS zh, 70 AS so
    UNION ALL SELECT 'plc_rack' AS code, 'Racks, Backplanes & Bases' AS en, '底板/机架/基座' AS zh, 80 AS so
    UNION ALL SELECT 'expansion_board' AS code, 'Expansion Boards' AS en, '扩展板' AS zh, 90 AS so
    UNION ALL SELECT 'safety_plc' AS code, 'Safety Controllers' AS en, '安全控制器' AS zh, 100 AS so
    UNION ALL SELECT 'motion_controller' AS code, 'Motion Controllers' AS en, '运动控制器' AS zh, 110 AS so
    UNION ALL SELECT 'plc_accessory' AS code, 'PLC Accessories' AS en, 'PLC 配件' AS zh, 120 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'plc'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- HMI 与工控机
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'hmi_panel' AS code, 'Touch Panels' AS en, '触摸屏' AS zh, 10 AS so
    UNION ALL SELECT 'text_display' AS code, 'Text Displays & Key Panels' AS en, '文本显示器/按键面板' AS zh, 20 AS so
    UNION ALL SELECT 'handheld_hmi' AS code, 'Handheld HMIs' AS en, '手持式 HMI' AS zh, 30 AS so
    UNION ALL SELECT 'panel_pc' AS code, 'Panel PCs' AS en, '平板工控机' AS zh, 40 AS so
    UNION ALL SELECT 'box_pc' AS code, 'Box PCs' AS en, '箱式工控机' AS zh, 50 AS so
    UNION ALL SELECT 'industrial_monitor' AS code, 'Industrial Monitors' AS en, '工业显示器' AS zh, 60 AS so
    UNION ALL SELECT 'hmi_repair_part' AS code, 'HMI Repair Parts (Touch Glass/LCD/Backlight)' AS en, 'HMI 维修备件（触摸玻璃/LCD/背光）' AS zh, 70 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'hmi'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 工业网络
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'ethernet_switch' AS code, 'Industrial Ethernet Switches' AS en, '以太网交换机' AS zh, 10 AS so
    UNION ALL SELECT 'gateway' AS code, 'Gateways & Protocol Converters' AS en, '网关/协议转换' AS zh, 20 AS so
    UNION ALL SELECT 'fieldbus' AS code, 'Fieldbus Components' AS en, '现场总线组件' AS zh, 30 AS so
    UNION ALL SELECT 'fiber_converter' AS code, 'Fiber Media Converters' AS en, '光纤收发器' AS zh, 40 AS so
    UNION ALL SELECT 'io_link_master' AS code, 'IO-Link Masters' AS en, 'IO-Link 主站' AS zh, 50 AS so
    UNION ALL SELECT 'industrial_wireless' AS code, 'Industrial Wireless & Routers' AS en, '工业无线/路由器' AS zh, 60 AS so
    UNION ALL SELECT 'sfp_module' AS code, 'SFP Modules & Network Accessories' AS en, 'SFP 光模块与网络附件' AS zh, 70 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'industrial_network'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 机器人与数控
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'robot_controller' AS code, 'Robot Controllers & Boards' AS en, '机器人控制柜与板卡' AS zh, 10 AS so
    UNION ALL SELECT 'teach_pendant' AS code, 'Teach Pendants' AS en, '示教器' AS zh, 20 AS so
    UNION ALL SELECT 'robot_motor' AS code, 'Robot Motors & Spare Parts' AS en, '机器人电机与备件' AS zh, 30 AS so
    UNION ALL SELECT 'gripper' AS code, 'Grippers & End Effectors' AS en, '夹爪/末端执行器' AS zh, 40 AS so
    UNION ALL SELECT 'robot_cable' AS code, 'Robot Cables' AS en, '机器人线缆' AS zh, 50 AS so
    UNION ALL SELECT 'cnc_system' AS code, 'CNC Systems & Panels' AS en, 'CNC 系统与面板' AS zh, 60 AS so
    UNION ALL SELECT 'spindle' AS code, 'Spindle Drives & Motors' AS en, '主轴驱动与电机' AS zh, 70 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'robotics_cnc'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 变频器与驱动
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'vfd' AS code, 'General Purpose Inverters (VFDs)' AS en, '通用变频器' AS zh, 10 AS so
    UNION ALL SELECT 'modular_drive' AS code, 'Modular Drives' AS en, '模块化驱动' AS zh, 20 AS so
    UNION ALL SELECT 'dc_drive' AS code, 'DC Drives' AS en, '直流调速器' AS zh, 30 AS so
    UNION ALL SELECT 'soft_starter' AS code, 'Soft Starters' AS en, '软启动器' AS zh, 40 AS so
    UNION ALL SELECT 'motor_starter' AS code, 'Motor Starters' AS en, '电机启动器' AS zh, 50 AS so
    UNION ALL SELECT 'drive_option' AS code, 'Operator Panels & Option Cards' AS en, '操作面板与选件卡' AS zh, 60 AS so
    UNION ALL SELECT 'drive_board' AS code, 'Drive Boards & Spare Parts' AS en, '驱动板卡备件' AS zh, 70 AS so
    UNION ALL SELECT 'braking' AS code, 'Braking Units & Resistors' AS en, '制动单元/电阻' AS zh, 80 AS so
    UNION ALL SELECT 'drive_filter' AS code, 'Reactors & EMC Filters' AS en, '电抗器/EMC 滤波器' AS zh, 90 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'inverter'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 伺服与运动
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'servo_drive' AS code, 'Servo Drives' AS en, '伺服驱动器' AS zh, 10 AS so
    UNION ALL SELECT 'servo_motor' AS code, 'Servo Motors' AS en, '伺服电机' AS zh, 20 AS so
    UNION ALL SELECT 'servo_kit' AS code, 'Servo Kits' AS en, '伺服套装' AS zh, 30 AS so
    UNION ALL SELECT 'stepper' AS code, 'Stepper Drives & Motors' AS en, '步进驱动/电机' AS zh, 40 AS so
    UNION ALL SELECT 'linear_motor' AS code, 'Linear & Direct Drive Motors' AS en, '直线/直驱电机' AS zh, 50 AS so
    UNION ALL SELECT 'servo_cable' AS code, 'Servo Cables' AS en, '伺服电缆' AS zh, 60 AS so
    UNION ALL SELECT 'servo_accessory' AS code, 'Servo Accessories' AS en, '伺服附件' AS zh, 70 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'servo'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 电机与传动
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'ac_motor' AS code, 'AC Motors' AS en, '交流电机' AS zh, 10 AS so
    UNION ALL SELECT 'gear_motor' AS code, 'Gear Motors' AS en, '齿轮电机' AS zh, 20 AS so
    UNION ALL SELECT 'reducer' AS code, 'Gear Reducers' AS en, '减速机' AS zh, 30 AS so
    UNION ALL SELECT 'dc_motor' AS code, 'DC Motors' AS en, '直流电机' AS zh, 40 AS so
    UNION ALL SELECT 'brake_clutch' AS code, 'Brakes & Clutches' AS en, '制动器/离合器' AS zh, 50 AS so
    UNION ALL SELECT 'electric_actuator' AS code, 'Electric Actuators' AS en, '电动执行器' AS zh, 60 AS so
    UNION ALL SELECT 'coupling' AS code, 'Couplings' AS en, '联轴器' AS zh, 70 AS so
    UNION ALL SELECT 'motor_accessory' AS code, 'Motor Accessories' AS en, '电机配件' AS zh, 80 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'motors'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 传感器
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'proximity_sensor' AS code, 'Proximity Sensors' AS en, '接近' AS zh, 10 AS so
    UNION ALL SELECT 'photoelectric_sensor' AS code, 'Photoelectric Sensors' AS en, '光电' AS zh, 20 AS so
    UNION ALL SELECT 'fiber_sensor' AS code, 'Fiber Optic Sensors' AS en, '光纤' AS zh, 30 AS so
    UNION ALL SELECT 'ultrasonic_sensor' AS code, 'Ultrasonic Sensors' AS en, '超声波' AS zh, 40 AS so
    UNION ALL SELECT 'encoder' AS code, 'Encoders' AS en, '编码器' AS zh, 50 AS so
    UNION ALL SELECT 'displacement_sensor' AS code, 'Displacement Sensors' AS en, '位移' AS zh, 60 AS so
    UNION ALL SELECT 'magnetic_switch' AS code, 'Magnetic Switches' AS en, '磁性开关' AS zh, 70 AS so
    UNION ALL SELECT 'limit_switch' AS code, 'Limit Switches' AS en, '限位开关' AS zh, 80 AS so
    UNION ALL SELECT 'temperature_sensor' AS code, 'Temperature & Humidity Sensors' AS en, '温度/温湿度' AS zh, 90 AS so
    UNION ALL SELECT 'vision' AS code, 'Vision Systems & Cameras' AS en, '视觉/相机' AS zh, 100 AS so
    UNION ALL SELECT 'color_sensor' AS code, 'Color & Contrast Sensors' AS en, '颜色/色标' AS zh, 110 AS so
    UNION ALL SELECT 'code_reader' AS code, 'Code Readers & RFID' AS en, '读码/RFID' AS zh, 120 AS so
    UNION ALL SELECT 'safety_light_curtain' AS code, 'Safety Light Curtains & Laser Scanners' AS en, '安全光幕/扫描仪' AS zh, 130 AS so
    UNION ALL SELECT 'vibration_sensor' AS code, 'Vibration, Tilt & Current Sensors' AS en, '振动/倾角/电流' AS zh, 140 AS so
    UNION ALL SELECT 'sensor_accessory' AS code, 'Sensor Accessories' AS en, '传感器配件' AS zh, 150 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'sensors'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 过程仪表与控制仪表
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'level_meter' AS code, 'Level Meters' AS en, '物位计' AS zh, 10 AS so
    UNION ALL SELECT 'level_switch' AS code, 'Level Switches' AS en, '液位开关' AS zh, 20 AS so
    UNION ALL SELECT 'pressure_transmitter' AS code, 'Pressure Sensors & Transmitters' AS en, '压力传感器/变送器' AS zh, 30 AS so
    UNION ALL SELECT 'pressure_switch' AS code, 'Pressure Switches' AS en, '压力开关' AS zh, 40 AS so
    UNION ALL SELECT 'flowmeter' AS code, 'Flow Meters & Flow Switches' AS en, '流量计/开关' AS zh, 50 AS so
    UNION ALL SELECT 'temperature_transmitter' AS code, 'Temperature Transmitters' AS en, '温度变送器' AS zh, 60 AS so
    UNION ALL SELECT 'load_cell' AS code, 'Load Cells & Weighing Indicators' AS en, '称重传感器与仪表' AS zh, 70 AS so
    UNION ALL SELECT 'pressure_gauge' AS code, 'Pressure Gauges & Thermometers' AS en, '压力表/温度计' AS zh, 80 AS so
    UNION ALL SELECT 'analyzer' AS code, 'Analytical Instruments' AS en, '分析仪表' AS zh, 90 AS so
    UNION ALL SELECT 'recorder' AS code, 'Recorders' AS en, '记录仪' AS zh, 100 AS so
    UNION ALL SELECT 'temperature_controller' AS code, 'Temperature & PID Controllers' AS en, '温控器/PID' AS zh, 110 AS so
    UNION ALL SELECT 'counter_timer' AS code, 'Counters & Timers' AS en, '计数器/计时器' AS zh, 120 AS so
    UNION ALL SELECT 'panel_meter' AS code, 'Digital Panel Meters' AS en, '数显面板表' AS zh, 130 AS so
    UNION ALL SELECT 'energy_meter' AS code, 'Energy Meters' AS en, '电能表' AS zh, 140 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'instruments'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 低压电器
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'contactor' AS code, 'Contactors' AS en, '接触器' AS zh, 10 AS so
    UNION ALL SELECT 'thermal_overload' AS code, 'Thermal Overload Relays' AS en, '热过载继电器' AS zh, 20 AS so
    UNION ALL SELECT 'motor_protection_breaker' AS code, 'Motor Protection Circuit Breakers' AS en, '电动机保护断路器' AS zh, 30 AS so
    UNION ALL SELECT 'motor_management' AS code, 'Motor Management & Protection' AS en, '电机管理/保护' AS zh, 40 AS so
    UNION ALL SELECT 'circuit_breaker' AS code, 'Circuit Breakers' AS en, '断路器' AS zh, 50 AS so
    UNION ALL SELECT 'fuse' AS code, 'Fuses' AS en, '熔断器' AS zh, 60 AS so
    UNION ALL SELECT 'surge_protector' AS code, 'Surge Protective Devices' AS en, '浪涌保护器' AS zh, 70 AS so
    UNION ALL SELECT 'disconnect_switch' AS code, 'Disconnect Switches' AS en, '隔离开关' AS zh, 80 AS so
    UNION ALL SELECT 'transfer_switch' AS code, 'Automatic Transfer Switches' AS en, '双电源转换开关' AS zh, 90 AS so
    UNION ALL SELECT 'switchgear_accessory' AS code, 'Switchgear Accessories' AS en, '附件' AS zh, 100 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'switchgear'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 继电器与信号接口
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'relay' AS code, 'General Purpose Relays' AS en, '中间继电器' AS zh, 10 AS so
    UNION ALL SELECT 'solid_state_relay' AS code, 'Solid State Relays' AS en, '固态继电器' AS zh, 20 AS so
    UNION ALL SELECT 'safety_relay' AS code, 'Safety Relays' AS en, '安全继电器' AS zh, 30 AS so
    UNION ALL SELECT 'timer_relay' AS code, 'Timer Relays' AS en, '时间继电器' AS zh, 40 AS so
    UNION ALL SELECT 'monitoring_relay' AS code, 'Monitoring Relays' AS en, '监控继电器' AS zh, 50 AS so
    UNION ALL SELECT 'protection_relay' AS code, 'Protection Relays' AS en, '保护继电器' AS zh, 60 AS so
    UNION ALL SELECT 'signal_isolator' AS code, 'Signal Isolators & Safety Barriers' AS en, '信号隔离器/安全栅' AS zh, 70 AS so
    UNION ALL SELECT 'interface_module' AS code, 'Interface Modules' AS en, '接口模块' AS zh, 80 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'relays'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 电源与变压器
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'din_power' AS code, 'DIN Rail Power Supplies' AS en, '导轨电源' AS zh, 10 AS so
    UNION ALL SELECT 'ups' AS code, 'UPS, Buffer & Redundancy Modules' AS en, 'UPS/缓冲/冗余' AS zh, 20 AS so
    UNION ALL SELECT 'dc_dc' AS code, 'DC/DC Converters' AS en, 'DC/DC' AS zh, 30 AS so
    UNION ALL SELECT 'transformer' AS code, 'Control & Isolation Transformers' AS en, '控制/隔离变压器' AS zh, 40 AS so
    UNION ALL SELECT 'power_controller' AS code, 'Power Controllers & Thyristor Units' AS en, '电力调整器/调功器' AS zh, 50 AS so
    UNION ALL SELECT 'power_filter' AS code, 'Power Line Filters' AS en, '电源滤波器' AS zh, 60 AS so
    UNION ALL SELECT 'capacitor_compensation' AS code, 'Capacitors & Power Factor Correction' AS en, '电容/补偿' AS zh, 70 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'power_supplies'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 按钮开关与指示
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'pushbutton' AS code, 'Pushbuttons, E-Stops & Selector Switches' AS en, '按钮/急停/选择开关' AS zh, 10 AS so
    UNION ALL SELECT 'cam_switch' AS code, 'Cam & Changeover Switches' AS en, '凸轮/万能转换开关' AS zh, 20 AS so
    UNION ALL SELECT 'indicator' AS code, 'Indicator Lights & Signal Towers' AS en, '指示灯/三色灯' AS zh, 30 AS so
    UNION ALL SELECT 'safety_switch' AS code, 'Safety Door Locks & Rope Pull Switches' AS en, '安全门锁/拉绳开关' AS zh, 40 AS so
    UNION ALL SELECT 'foot_switch' AS code, 'Foot Switches, Joysticks & Control Stations' AS en, '脚踏/操纵杆/操作盒' AS zh, 50 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'pilot_devices'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 线缆与连接
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'programming_cable' AS code, 'Programming & Communication Cables' AS en, '编程/通讯电缆' AS zh, 10 AS so
    UNION ALL SELECT 'drive_cable' AS code, 'Drive & Panel Connection Cables' AS en, '驱动/面板连接线' AS zh, 20 AS so
    UNION ALL SELECT 'fiber_patch_cord' AS code, 'Fiber Patch Cords' AS en, '光纤跳线' AS zh, 30 AS so
    UNION ALL SELECT 'bus_connector' AS code, 'Bus Connectors' AS en, '总线接头/连接器' AS zh, 40 AS so
    UNION ALL SELECT 'terminal_block' AS code, 'Terminal Blocks' AS en, '接线端子' AS zh, 50 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'cables'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 流体动力
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'solenoid_valve' AS code, 'Solenoid Valves' AS en, '电磁阀' AS zh, 10 AS so
    UNION ALL SELECT 'process_valve' AS code, 'Process Valves' AS en, '过程阀' AS zh, 20 AS so
    UNION ALL SELECT 'self_operated_valve' AS code, 'Self-Operated Regulators' AS en, '自力式调节阀' AS zh, 30 AS so
    UNION ALL SELECT 'valve_positioner' AS code, 'Valve Positioners & Actuators' AS en, '阀门定位器与执行机构' AS zh, 40 AS so
    UNION ALL SELECT 'proportional_valve' AS code, 'Proportional & Servo Valves' AS en, '比例阀/伺服阀' AS zh, 50 AS so
    UNION ALL SELECT 'pneumatic_cylinder' AS code, 'Pneumatic Cylinders' AS en, '气缸' AS zh, 60 AS so
    UNION ALL SELECT 'air_preparation' AS code, 'Air Preparation Units' AS en, '气源处理' AS zh, 70 AS so
    UNION ALL SELECT 'vacuum' AS code, 'Vacuum Components' AS en, '真空元件' AS zh, 80 AS so
    UNION ALL SELECT 'pneumatic_fitting' AS code, 'Pneumatic Fittings' AS en, '气动接头' AS zh, 90 AS so
    UNION ALL SELECT 'hydraulic_valve' AS code, 'Hydraulic Valves' AS en, '液压阀' AS zh, 100 AS so
    UNION ALL SELECT 'hydraulic_pump' AS code, 'Hydraulic Pumps & Motors' AS en, '液压泵/马达' AS zh, 110 AS so
    UNION ALL SELECT 'hydraulic_cylinder' AS code, 'Hydraulic Cylinders' AS en, '液压缸' AS zh, 120 AS so
    UNION ALL SELECT 'hydraulic_filter' AS code, 'Hydraulic Filter Elements' AS en, '液压滤芯' AS zh, 130 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'fluid_power'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
-- 电子元器件
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (
    SELECT 0 AS t, s.code, s.en, s.zh, p.`id` AS pid, s.so, 1 AS st, 'sys' AS cb, 'sys' AS ub
    FROM (
              SELECT 'igbt_module' AS code, 'IGBT & Power Modules' AS en, 'IGBT/功率模块' AS zh, 10 AS so
    UNION ALL SELECT 'control_board' AS code, 'Control Boards' AS en, '控制板' AS zh, 20 AS so
    UNION ALL SELECT 'capacitor' AS code, 'Capacitors & Components' AS en, '电容/元件' AS zh, 30 AS so
    UNION ALL SELECT 'cooling_fan' AS code, 'Fans & Thermal Management' AS en, '风扇/热管理' AS zh, 40 AS so
    UNION ALL SELECT 'backup_battery' AS code, 'Backup Batteries' AS en, '备用电池' AS zh, 50 AS so
    UNION ALL SELECT 'lcd_module' AS code, 'LCD & Display Modules' AS en, 'LCD 与显示模组' AS zh, 60 AS so
    ) s
    JOIN `product_category` p ON p.`tenant_id` = 0 AND p.`category_code` = 'electronic_components'
) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = v.pid, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';

-- ---------------------------------------------------------------- 旧品类：引用迁到对应的新品类
-- controllers → plc
UPDATE `product` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'controllers'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'plc'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `product_candidate` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'controllers'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'plc'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `supplier_product_scope` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'controllers'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'plc'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
-- hydraulics → fluid_power
UPDATE `product` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'hydraulics'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'fluid_power'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `product_candidate` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'hydraulics'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'fluid_power'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `supplier_product_scope` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'hydraulics'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'fluid_power'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
-- pneumatics → fluid_power
UPDATE `product` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'pneumatics'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'fluid_power'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `product_candidate` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'pneumatics'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'fluid_power'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `supplier_product_scope` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'pneumatics'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'fluid_power'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
-- transmission → motors
UPDATE `product` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'transmission'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'motors'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `product_candidate` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'transmission'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'motors'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `supplier_product_scope` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'transmission'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'motors'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
-- spares → electronic_components
UPDATE `product` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'spares'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'electronic_components'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `product_candidate` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'spares'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'electronic_components'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `supplier_product_scope` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'spares'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'electronic_components'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
-- power_module → din_power
UPDATE `product` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'power_module'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'din_power'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `product_candidate` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'power_module'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'din_power'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';
UPDATE `supplier_product_scope` x
JOIN `product_category` o ON o.`id` = x.`category_id` AND o.`tenant_id` = 0 AND o.`category_code` = 'power_module'
JOIN `product_category` n ON n.`tenant_id` = 0 AND n.`category_code` = 'din_power'
SET x.`category_id` = n.`id`, x.`update_time` = NOW(), x.`update_by` = 'sys';

-- ---------------------------------------------------------------- 不在新体系里的旧品类
-- 先处理二级：没有引用的软删除，仍有引用的停用（保留，待平台账号在品类管理页处理）
UPDATE `product_category` c SET c.`deleted_at` = NOW(), c.`update_time` = NOW(), c.`update_by` = 'sys'
WHERE c.`tenant_id` = 0 AND c.`parent_id` IS NOT NULL AND c.`deleted_at` IS NULL
  AND c.`category_code` NOT IN ('plc', 'hmi', 'industrial_network', 'robotics_cnc', 'inverter', 'servo', 'motors', 'sensors', 'instruments', 'switchgear', 'relays', 'power_supplies', 'pilot_devices', 'cables', 'fluid_power', 'electronic_components', 'plc_cpu', 'io_module', 'distributed_io', 'comm_module', 'redundancy_module', 'function_module', 'plc_power', 'plc_rack', 'expansion_board', 'safety_plc', 'motion_controller', 'plc_accessory', 'hmi_panel', 'text_display', 'handheld_hmi', 'panel_pc', 'box_pc', 'industrial_monitor', 'hmi_repair_part', 'ethernet_switch', 'gateway', 'fieldbus', 'fiber_converter', 'io_link_master', 'industrial_wireless', 'sfp_module', 'robot_controller', 'teach_pendant', 'robot_motor', 'gripper', 'robot_cable', 'cnc_system', 'spindle', 'vfd', 'modular_drive', 'dc_drive', 'soft_starter', 'motor_starter', 'drive_option', 'drive_board', 'braking', 'drive_filter', 'servo_drive', 'servo_motor', 'servo_kit', 'stepper', 'linear_motor', 'servo_cable', 'servo_accessory', 'ac_motor', 'gear_motor', 'reducer', 'dc_motor', 'brake_clutch', 'electric_actuator', 'coupling', 'motor_accessory', 'proximity_sensor', 'photoelectric_sensor', 'fiber_sensor', 'ultrasonic_sensor', 'encoder', 'displacement_sensor', 'magnetic_switch', 'limit_switch', 'temperature_sensor', 'vision', 'color_sensor', 'code_reader', 'safety_light_curtain', 'vibration_sensor', 'sensor_accessory', 'level_meter', 'level_switch', 'pressure_transmitter', 'pressure_switch', 'flowmeter', 'temperature_transmitter', 'load_cell', 'pressure_gauge', 'analyzer', 'recorder', 'temperature_controller', 'counter_timer', 'panel_meter', 'energy_meter', 'contactor', 'thermal_overload', 'motor_protection_breaker', 'motor_management', 'circuit_breaker', 'fuse', 'surge_protector', 'disconnect_switch', 'transfer_switch', 'switchgear_accessory', 'relay', 'solid_state_relay', 'safety_relay', 'timer_relay', 'monitoring_relay', 'protection_relay', 'signal_isolator', 'interface_module', 'din_power', 'ups', 'dc_dc', 'transformer', 'power_controller', 'power_filter', 'capacitor_compensation', 'pushbutton', 'cam_switch', 'indicator', 'safety_switch', 'foot_switch', 'programming_cable', 'drive_cable', 'fiber_patch_cord', 'bus_connector', 'terminal_block', 'solenoid_valve', 'process_valve', 'self_operated_valve', 'valve_positioner', 'proportional_valve', 'pneumatic_cylinder', 'air_preparation', 'vacuum', 'pneumatic_fitting', 'hydraulic_valve', 'hydraulic_pump', 'hydraulic_cylinder', 'hydraulic_filter', 'igbt_module', 'control_board', 'capacitor', 'cooling_fan', 'backup_battery', 'lcd_module')
  AND NOT EXISTS (SELECT 1 FROM `product` p WHERE p.`category_id` = c.`id` AND p.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `product_candidate` pc WHERE pc.`category_id` = c.`id` AND pc.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `supplier_product_scope` s WHERE s.`category_id` = c.`id` AND s.`deleted_at` IS NULL);
UPDATE `product_category` c SET c.`status` = 0, c.`update_time` = NOW(), c.`update_by` = 'sys'
WHERE c.`tenant_id` = 0 AND c.`parent_id` IS NOT NULL AND c.`deleted_at` IS NULL AND c.`status` = 1
  AND c.`category_code` NOT IN ('plc', 'hmi', 'industrial_network', 'robotics_cnc', 'inverter', 'servo', 'motors', 'sensors', 'instruments', 'switchgear', 'relays', 'power_supplies', 'pilot_devices', 'cables', 'fluid_power', 'electronic_components', 'plc_cpu', 'io_module', 'distributed_io', 'comm_module', 'redundancy_module', 'function_module', 'plc_power', 'plc_rack', 'expansion_board', 'safety_plc', 'motion_controller', 'plc_accessory', 'hmi_panel', 'text_display', 'handheld_hmi', 'panel_pc', 'box_pc', 'industrial_monitor', 'hmi_repair_part', 'ethernet_switch', 'gateway', 'fieldbus', 'fiber_converter', 'io_link_master', 'industrial_wireless', 'sfp_module', 'robot_controller', 'teach_pendant', 'robot_motor', 'gripper', 'robot_cable', 'cnc_system', 'spindle', 'vfd', 'modular_drive', 'dc_drive', 'soft_starter', 'motor_starter', 'drive_option', 'drive_board', 'braking', 'drive_filter', 'servo_drive', 'servo_motor', 'servo_kit', 'stepper', 'linear_motor', 'servo_cable', 'servo_accessory', 'ac_motor', 'gear_motor', 'reducer', 'dc_motor', 'brake_clutch', 'electric_actuator', 'coupling', 'motor_accessory', 'proximity_sensor', 'photoelectric_sensor', 'fiber_sensor', 'ultrasonic_sensor', 'encoder', 'displacement_sensor', 'magnetic_switch', 'limit_switch', 'temperature_sensor', 'vision', 'color_sensor', 'code_reader', 'safety_light_curtain', 'vibration_sensor', 'sensor_accessory', 'level_meter', 'level_switch', 'pressure_transmitter', 'pressure_switch', 'flowmeter', 'temperature_transmitter', 'load_cell', 'pressure_gauge', 'analyzer', 'recorder', 'temperature_controller', 'counter_timer', 'panel_meter', 'energy_meter', 'contactor', 'thermal_overload', 'motor_protection_breaker', 'motor_management', 'circuit_breaker', 'fuse', 'surge_protector', 'disconnect_switch', 'transfer_switch', 'switchgear_accessory', 'relay', 'solid_state_relay', 'safety_relay', 'timer_relay', 'monitoring_relay', 'protection_relay', 'signal_isolator', 'interface_module', 'din_power', 'ups', 'dc_dc', 'transformer', 'power_controller', 'power_filter', 'capacitor_compensation', 'pushbutton', 'cam_switch', 'indicator', 'safety_switch', 'foot_switch', 'programming_cable', 'drive_cable', 'fiber_patch_cord', 'bus_connector', 'terminal_block', 'solenoid_valve', 'process_valve', 'self_operated_valve', 'valve_positioner', 'proportional_valve', 'pneumatic_cylinder', 'air_preparation', 'vacuum', 'pneumatic_fitting', 'hydraulic_valve', 'hydraulic_pump', 'hydraulic_cylinder', 'hydraulic_filter', 'igbt_module', 'control_board', 'capacitor', 'cooling_fan', 'backup_battery', 'lcd_module');

-- 再处理一级：没有引用、也没有未删除的下级时软删除，否则停用
UPDATE `product_category` c
LEFT JOIN (SELECT DISTINCT `parent_id` FROM `product_category` WHERE `tenant_id` = 0 AND `parent_id` IS NOT NULL AND `deleted_at` IS NULL) k
    ON k.`parent_id` = c.`id`
SET c.`deleted_at` = NOW(), c.`update_time` = NOW(), c.`update_by` = 'sys'
WHERE c.`tenant_id` = 0 AND c.`parent_id` IS NULL AND c.`deleted_at` IS NULL
  AND c.`category_code` NOT IN ('plc', 'hmi', 'industrial_network', 'robotics_cnc', 'inverter', 'servo', 'motors', 'sensors', 'instruments', 'switchgear', 'relays', 'power_supplies', 'pilot_devices', 'cables', 'fluid_power', 'electronic_components', 'plc_cpu', 'io_module', 'distributed_io', 'comm_module', 'redundancy_module', 'function_module', 'plc_power', 'plc_rack', 'expansion_board', 'safety_plc', 'motion_controller', 'plc_accessory', 'hmi_panel', 'text_display', 'handheld_hmi', 'panel_pc', 'box_pc', 'industrial_monitor', 'hmi_repair_part', 'ethernet_switch', 'gateway', 'fieldbus', 'fiber_converter', 'io_link_master', 'industrial_wireless', 'sfp_module', 'robot_controller', 'teach_pendant', 'robot_motor', 'gripper', 'robot_cable', 'cnc_system', 'spindle', 'vfd', 'modular_drive', 'dc_drive', 'soft_starter', 'motor_starter', 'drive_option', 'drive_board', 'braking', 'drive_filter', 'servo_drive', 'servo_motor', 'servo_kit', 'stepper', 'linear_motor', 'servo_cable', 'servo_accessory', 'ac_motor', 'gear_motor', 'reducer', 'dc_motor', 'brake_clutch', 'electric_actuator', 'coupling', 'motor_accessory', 'proximity_sensor', 'photoelectric_sensor', 'fiber_sensor', 'ultrasonic_sensor', 'encoder', 'displacement_sensor', 'magnetic_switch', 'limit_switch', 'temperature_sensor', 'vision', 'color_sensor', 'code_reader', 'safety_light_curtain', 'vibration_sensor', 'sensor_accessory', 'level_meter', 'level_switch', 'pressure_transmitter', 'pressure_switch', 'flowmeter', 'temperature_transmitter', 'load_cell', 'pressure_gauge', 'analyzer', 'recorder', 'temperature_controller', 'counter_timer', 'panel_meter', 'energy_meter', 'contactor', 'thermal_overload', 'motor_protection_breaker', 'motor_management', 'circuit_breaker', 'fuse', 'surge_protector', 'disconnect_switch', 'transfer_switch', 'switchgear_accessory', 'relay', 'solid_state_relay', 'safety_relay', 'timer_relay', 'monitoring_relay', 'protection_relay', 'signal_isolator', 'interface_module', 'din_power', 'ups', 'dc_dc', 'transformer', 'power_controller', 'power_filter', 'capacitor_compensation', 'pushbutton', 'cam_switch', 'indicator', 'safety_switch', 'foot_switch', 'programming_cable', 'drive_cable', 'fiber_patch_cord', 'bus_connector', 'terminal_block', 'solenoid_valve', 'process_valve', 'self_operated_valve', 'valve_positioner', 'proportional_valve', 'pneumatic_cylinder', 'air_preparation', 'vacuum', 'pneumatic_fitting', 'hydraulic_valve', 'hydraulic_pump', 'hydraulic_cylinder', 'hydraulic_filter', 'igbt_module', 'control_board', 'capacitor', 'cooling_fan', 'backup_battery', 'lcd_module')
  AND k.`parent_id` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `product` p WHERE p.`category_id` = c.`id` AND p.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `product_candidate` pc WHERE pc.`category_id` = c.`id` AND pc.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `supplier_product_scope` s WHERE s.`category_id` = c.`id` AND s.`deleted_at` IS NULL);
UPDATE `product_category` c SET c.`status` = 0, c.`update_time` = NOW(), c.`update_by` = 'sys'
WHERE c.`tenant_id` = 0 AND c.`parent_id` IS NULL AND c.`deleted_at` IS NULL AND c.`status` = 1
  AND c.`category_code` NOT IN ('plc', 'hmi', 'industrial_network', 'robotics_cnc', 'inverter', 'servo', 'motors', 'sensors', 'instruments', 'switchgear', 'relays', 'power_supplies', 'pilot_devices', 'cables', 'fluid_power', 'electronic_components', 'plc_cpu', 'io_module', 'distributed_io', 'comm_module', 'redundancy_module', 'function_module', 'plc_power', 'plc_rack', 'expansion_board', 'safety_plc', 'motion_controller', 'plc_accessory', 'hmi_panel', 'text_display', 'handheld_hmi', 'panel_pc', 'box_pc', 'industrial_monitor', 'hmi_repair_part', 'ethernet_switch', 'gateway', 'fieldbus', 'fiber_converter', 'io_link_master', 'industrial_wireless', 'sfp_module', 'robot_controller', 'teach_pendant', 'robot_motor', 'gripper', 'robot_cable', 'cnc_system', 'spindle', 'vfd', 'modular_drive', 'dc_drive', 'soft_starter', 'motor_starter', 'drive_option', 'drive_board', 'braking', 'drive_filter', 'servo_drive', 'servo_motor', 'servo_kit', 'stepper', 'linear_motor', 'servo_cable', 'servo_accessory', 'ac_motor', 'gear_motor', 'reducer', 'dc_motor', 'brake_clutch', 'electric_actuator', 'coupling', 'motor_accessory', 'proximity_sensor', 'photoelectric_sensor', 'fiber_sensor', 'ultrasonic_sensor', 'encoder', 'displacement_sensor', 'magnetic_switch', 'limit_switch', 'temperature_sensor', 'vision', 'color_sensor', 'code_reader', 'safety_light_curtain', 'vibration_sensor', 'sensor_accessory', 'level_meter', 'level_switch', 'pressure_transmitter', 'pressure_switch', 'flowmeter', 'temperature_transmitter', 'load_cell', 'pressure_gauge', 'analyzer', 'recorder', 'temperature_controller', 'counter_timer', 'panel_meter', 'energy_meter', 'contactor', 'thermal_overload', 'motor_protection_breaker', 'motor_management', 'circuit_breaker', 'fuse', 'surge_protector', 'disconnect_switch', 'transfer_switch', 'switchgear_accessory', 'relay', 'solid_state_relay', 'safety_relay', 'timer_relay', 'monitoring_relay', 'protection_relay', 'signal_isolator', 'interface_module', 'din_power', 'ups', 'dc_dc', 'transformer', 'power_controller', 'power_filter', 'capacitor_compensation', 'pushbutton', 'cam_switch', 'indicator', 'safety_switch', 'foot_switch', 'programming_cable', 'drive_cable', 'fiber_patch_cord', 'bus_connector', 'terminal_block', 'solenoid_valve', 'process_valve', 'self_operated_valve', 'valve_positioner', 'proportional_valve', 'pneumatic_cylinder', 'air_preparation', 'vacuum', 'pneumatic_fitting', 'hydraulic_valve', 'hydraulic_pump', 'hydraulic_cylinder', 'hydraulic_filter', 'igbt_module', 'control_board', 'capacitor', 'cooling_fan', 'backup_battery', 'lcd_module');
