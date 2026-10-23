-- 细分品类简介与系列重整（openspec seed-subcategory-desc-and-series）
-- 1. 140 个细分品类写入中英文简介，只填空的；
-- 2. 系列按定稿清单：品牌按名称识别，同品牌下按系列名识别（忽略大小写与首尾空格）；
--    已有的保留 ID 与启用状态、补空的简介，没有的新建（同名系列曾被删除的不恢复、不新建）；
--    ATV48 → ATS48、ATS01/ATV22 → ATS01/ATS22 在原 ID 上改名；台达下误挂的 S7-1200 无商品使用时软删除、有商品使用时停用；
--    清单之外的系列不改动。
-- 迁移前的简介与系列状态存入 bak_v139_* 两张备份表，供反向 SQL 恢复。可重复执行，结果一致。
-- 本文件由 openspec/changes/seed-subcategory-desc-and-series/data/gen_migration.py 生成，改清单后重新生成，不要手改。

CREATE TABLE IF NOT EXISTS `bak_v139_category_desc` (
  `id`             bigint       NOT NULL COMMENT '品类ID',
  `description`    varchar(500) NOT NULL DEFAULT '' COMMENT '迁移前的英文简介',
  `description_zh` varchar(500) NOT NULL DEFAULT '' COMMENT '迁移前的中文简介',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V1.3.9 细分品类简介迁移前备份（反向 SQL 用）';

CREATE TABLE IF NOT EXISTS `bak_v139_product_series` (
  `id`          bigint       NOT NULL COMMENT '系列ID',
  `series_name` varchar(64)  NOT NULL DEFAULT '' COMMENT '迁移前的系列名称',
  `description` varchar(500) NOT NULL DEFAULT '' COMMENT '迁移前的简介',
  `status`      tinyint      NOT NULL DEFAULT 1 COMMENT '迁移前的状态（0-禁用、1-启用）',
  `deleted_at`  datetime     NULL COMMENT '迁移前的软删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V1.3.9 系列迁移前备份（反向 SQL 用；不在表里的系列是本次新建的）';

INSERT IGNORE INTO `bak_v139_category_desc` (`id`, `description`, `description_zh`)
SELECT `id`, `description`, `description_zh` FROM `product_category` WHERE `tenant_id` = 0 AND `parent_id` IS NOT NULL;
INSERT IGNORE INTO `bak_v139_product_series` (`id`, `series_name`, `description`, `status`, `deleted_at`)
SELECT `id`, `series_name`, `description`, `status`, `deleted_at` FROM `product_series` WHERE `tenant_id` = 0;

-- ================================================================ 细分品类简介

UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'PLC 的中央处理单元及一体式小型 PLC（CPU 与 I/O 集成），负责程序运行和逻辑控制。', `description_zh`), `description` = IF(`description` = '', 'PLC central processing units and compact all-in-one PLCs with built-in I/O that run the control program.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'plc_cpu' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '装在 PLC 机架或主机旁的数字量、模拟量输入输出模块，连接现场传感器与执行器。', `description_zh`), `description` = IF(`description` = '', 'Digital and analog input/output modules mounted on the PLC rack or next to the CPU to connect field sensors and actuators.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'io_module' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '通过现场总线或工业以太网连接 PLC 的远程 I/O 站及其接口模块（耦合器、适配器）。', `description_zh`), `description` = IF(`description` = '', 'Remote I/O stations and their interface modules (couplers, adapters) connected to the PLC over fieldbus or industrial Ethernet.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'distributed_io' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'PLC 的以太网、PROFIBUS、串口、CC-Link 等通讯处理器与通讯模块。', `description_zh`), `description` = IF(`description` = '', 'PLC communication processors and modules for Ethernet, PROFIBUS, serial, CC-Link and other networks.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'comm_module' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '冗余 PLC 系统使用的同步模块、冗余电源与热备切换组件。', `description_zh`), `description` = IF(`description` = '', 'Synchronization modules, redundant power supplies and hot-standby components for redundant PLC systems.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'redundancy_module' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '高速计数、定位、温度控制、称重等专用功能模块。', `description_zh`), `description` = IF(`description` = '', 'Special function modules for high-speed counting, positioning, temperature control, weighing and similar tasks.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'function_module' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '给 PLC 机架和模块供电的专用电源模块。', `description_zh`), `description` = IF(`description` = '', 'Dedicated power supply modules that power the PLC rack and modules.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'plc_power' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'PLC 的底板、机架、基座和扩展底板。', `description_zh`), `description` = IF(`description` = '', 'PLC backplanes, racks, base units and expansion bases.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'plc_rack' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '插在小型 PLC 主机上的扩展板，增加通讯口、模拟量或少量 I/O。', `description_zh`), `description` = IF(`description` = '', 'Expansion boards that plug into compact PLCs to add communication ports, analog channels or a few I/O points.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'expansion_board' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '符合安全标准（如 SIL、PL）的安全控制器、安全 CPU 和安全 I/O 模块。', `description_zh`), `description` = IF(`description` = '', 'Safety controllers, safety CPUs and safety I/O modules certified to standards such as SIL and PL.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'safety_plc' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '多轴运动控制器与运动控制 CPU，用于定位、同步和插补控制。', `description_zh`), `description` = IF(`description` = '', 'Multi-axis motion controllers and motion CPUs for positioning, synchronization and interpolation.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'motion_controller' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '存储卡、电池、前连接器、端子排、编程适配器等 PLC 配件。', `description_zh`), `description` = IF(`description` = '', 'PLC accessories such as memory cards, batteries, front connectors, terminal blocks and programming adapters.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'plc_accessory' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '工业触摸屏人机界面，用于设备监控与参数设置。', `description_zh`), `description` = IF(`description` = '', 'Industrial touch-screen HMIs for machine monitoring and parameter setting.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'hmi_panel' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '文本显示器、按键式操作面板等小型人机界面。', `description_zh`), `description` = IF(`description` = '', 'Text displays, keypad operator panels and other small operator interfaces.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'text_display' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '带急停和使能开关的手持式操作终端。', `description_zh`), `description` = IF(`description` = '', 'Handheld operator terminals with emergency stop and enabling switches.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'handheld_hmi' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '带触摸显示屏的一体式工业平板电脑。', `description_zh`), `description` = IF(`description` = '', 'Industrial panel PCs with an integrated touch display.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'panel_pc' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '无屏的箱式、嵌入式和机架式工控机。', `description_zh`), `description` = IF(`description` = '', 'Box, embedded and rack-mount industrial PCs without a display.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'box_pc' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '工业级显示器与触摸显示器，配合工控机使用。', `description_zh`), `description` = IF(`description` = '', 'Industrial-grade monitors and touch monitors used with industrial PCs.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'industrial_monitor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '触摸屏和工控机的维修备件：触摸玻璃、LCD 屏、背光、面膜、主板等。', `description_zh`), `description` = IF(`description` = '', 'Repair parts for HMIs and panel PCs: touch glass, LCD screens, backlights, overlays and mainboards.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'hmi_repair_part' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '非网管与网管型工业以太网交换机，适应宽温和导轨安装。', `description_zh`), `description` = IF(`description` = '', 'Unmanaged and managed industrial Ethernet switches built for wide temperatures and DIN-rail mounting.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'ethernet_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '不同协议之间的网关与协议转换器，以及串口服务器。', `description_zh`), `description` = IF(`description` = '', 'Gateways and protocol converters between different networks, plus serial device servers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'gateway' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'PROFIBUS、DeviceNet、CANopen 等现场总线的中继器、终端电阻与网络组件。', `description_zh`), `description` = IF(`description` = '', 'Repeaters, terminators and network components for PROFIBUS, DeviceNet, CANopen and other fieldbuses.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'fieldbus' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '把以太网或串口信号转为光纤传输的工业光纤收发器。', `description_zh`), `description` = IF(`description` = '', 'Industrial media converters that carry Ethernet or serial signals over fiber.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'fiber_converter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '连接 IO-Link 传感器和执行器的主站模块与集线器。', `description_zh`), `description` = IF(`description` = '', 'IO-Link master modules and hubs that connect IO-Link sensors and actuators.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'io_link_master' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '工业无线 AP、无线客户端、4G/5G 工业路由器。', `description_zh`), `description` = IF(`description` = '', 'Industrial wireless access points, wireless clients and 4G/5G industrial routers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'industrial_wireless' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'SFP 光模块、网络电缆附件与机柜网络配件。', `description_zh`), `description` = IF(`description` = '', 'SFP transceivers, network cable accessories and cabinet networking parts.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'sfp_module' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '工业机器人控制柜、驱动单元与控制板卡。', `description_zh`), `description` = IF(`description` = '', 'Industrial robot controllers, drive units and control boards.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'robot_controller' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '工业机器人的示教器及其电缆。', `description_zh`), `description` = IF(`description` = '', 'Teach pendants for industrial robots and their cables.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'teach_pendant' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '机器人关节电机、减速机、编码器等维修备件。', `description_zh`), `description` = IF(`description` = '', 'Robot joint motors, reducers, encoders and other spare parts.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'robot_motor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '机器人夹爪、真空吸盘组件、快换装置等末端执行器。', `description_zh`), `description` = IF(`description` = '', 'Grippers, vacuum gripper assemblies, tool changers and other end effectors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'gripper' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '机器人本体与控制柜之间的动力、编码器和示教器电缆。', `description_zh`), `description` = IF(`description` = '', 'Power, encoder and teach-pendant cables between the robot arm and the controller.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'robot_cable' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '数控系统主机、操作面板、I/O 单元与相关板卡。', `description_zh`), `description` = IF(`description` = '', 'CNC controllers, operator panels, I/O units and related boards.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'cnc_system' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '机床主轴驱动器与主轴电机。', `description_zh`), `description` = IF(`description` = '', 'Machine tool spindle drives and spindle motors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'spindle' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '调节交流电机转速的通用变频器。', `description_zh`), `description` = IF(`description` = '', 'General purpose inverters (VFDs) that control AC motor speed.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'vfd' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '由整流、逆变等单元组合的模块化多传动系统。', `description_zh`), `description` = IF(`description` = '', 'Modular multi-drive systems built from rectifier, inverter and control units.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'modular_drive' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '用于直流电机调速的直流调速器。', `description_zh`), `description` = IF(`description` = '', 'DC drives for controlling DC motor speed.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'dc_drive' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '降低电机启动电流与冲击的软启动器。', `description_zh`), `description` = IF(`description` = '', 'Soft starters that reduce motor starting current and mechanical shock.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'soft_starter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '直接启动、正反转等电机启动器与启动器组合。', `description_zh`), `description` = IF(`description` = '', 'Direct-on-line, reversing and other motor starters and starter combinations.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'motor_starter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '变频器的操作面板、通讯卡、编码器卡、I/O 扩展卡等选件。', `description_zh`), `description` = IF(`description` = '', 'Inverter options such as operator panels, communication cards, encoder cards and I/O extension cards.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'drive_option' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '变频器和伺服的控制板、电源板、驱动板等维修备件。', `description_zh`), `description` = IF(`description` = '', 'Control, power and driver boards for inverters and servo drives, used as repair parts.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'drive_board' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '制动单元与制动电阻，吸收电机减速时的回馈能量。', `description_zh`), `description` = IF(`description` = '', 'Braking units and resistors that absorb regenerative energy during deceleration.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'braking' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '输入输出电抗器、正弦波滤波器与 EMC 滤波器。', `description_zh`), `description` = IF(`description` = '', 'Input and output reactors, sine-wave filters and EMC filters for drives.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'drive_filter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '驱动伺服电机实现精确位置、速度和转矩控制的伺服驱动器。', `description_zh`), `description` = IF(`description` = '', 'Servo drives that control servo motors for precise position, speed and torque.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'servo_drive' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '带编码器的交流伺服电机。', `description_zh`), `description` = IF(`description` = '', 'AC servo motors with encoders.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'servo_motor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '配套出售的伺服驱动器与伺服电机组合。', `description_zh`), `description` = IF(`description` = '', 'Matched servo drive and motor packages.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'servo_kit' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '步进驱动器、步进电机与闭环步进系统。', `description_zh`), `description` = IF(`description` = '', 'Stepper drives, stepper motors and closed-loop stepper systems.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'stepper' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '直线电机与直驱力矩电机。', `description_zh`), `description` = IF(`description` = '', 'Linear motors and direct drive torque motors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'linear_motor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '伺服电机的动力电缆、编码器电缆与制动电缆。', `description_zh`), `description` = IF(`description` = '', 'Power, encoder and brake cables for servo motors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'servo_cable' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '伺服系统的电池、再生电阻、连接器与安装附件。', `description_zh`), `description` = IF(`description` = '', 'Servo system batteries, regenerative resistors, connectors and mounting accessories.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'servo_accessory' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '三相与单相交流异步电机及高效电机。', `description_zh`), `description` = IF(`description` = '', 'Three-phase and single-phase AC induction motors, including high-efficiency motors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'ac_motor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电机与减速机一体的齿轮电机。', `description_zh`), `description` = IF(`description` = '', 'Gear motors with an integrated motor and gearbox.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'gear_motor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '行星、蜗轮、斜齿轮等减速机。', `description_zh`), `description` = IF(`description` = '', 'Planetary, worm, helical and other gear reducers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'reducer' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '直流电机与无刷直流电机。', `description_zh`), `description` = IF(`description` = '', 'DC motors and brushless DC motors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'dc_motor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电磁制动器、电磁离合器与制动离合器组合。', `description_zh`), `description` = IF(`description` = '', 'Electromagnetic brakes, clutches and clutch-brake units.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'brake_clutch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电动缸、电动滑台与阀门电动执行器。', `description_zh`), `description` = IF(`description` = '', 'Electric cylinders, electric slides and electric valve actuators.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'electric_actuator' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '连接电机与负载的联轴器。', `description_zh`), `description` = IF(`description` = '', 'Couplings that connect motors to their loads.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'coupling' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电机风扇、风罩、接线盒、编码器安装件等配件。', `description_zh`), `description` = IF(`description` = '', 'Motor accessories such as fans, fan covers, terminal boxes and encoder mounting kits.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'motor_accessory' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电感式、电容式接近开关，非接触检测金属或物体。', `description_zh`), `description` = IF(`description` = '', 'Inductive and capacitive proximity sensors for non-contact detection of metal and other objects.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'proximity_sensor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '对射、反射、漫反射等光电开关。', `description_zh`), `description` = IF(`description` = '', 'Through-beam, retro-reflective and diffuse photoelectric sensors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'photoelectric_sensor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '光纤放大器与配套光纤头。', `description_zh`), `description` = IF(`description` = '', 'Fiber optic amplifiers and fiber heads.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'fiber_sensor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '超声波测距与检测传感器，适合透明或液体目标。', `description_zh`), `description` = IF(`description` = '', 'Ultrasonic distance and presence sensors, suited to transparent and liquid targets.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'ultrasonic_sensor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '增量式、绝对值编码器与拉线编码器。', `description_zh`), `description` = IF(`description` = '', 'Incremental, absolute and draw-wire encoders.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'encoder' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '激光位移、线性位移与测距传感器。', `description_zh`), `description` = IF(`description` = '', 'Laser displacement, linear position and distance sensors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'displacement_sensor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '检测气缸活塞位置的磁性开关。', `description_zh`), `description` = IF(`description` = '', 'Magnetic switches that detect cylinder piston position.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'magnetic_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '机械式限位开关与微动开关。', `description_zh`), `description` = IF(`description` = '', 'Mechanical limit switches and micro switches.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'limit_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '热电阻、热电偶与温湿度传感器。', `description_zh`), `description` = IF(`description` = '', 'RTDs, thermocouples and temperature and humidity sensors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'temperature_sensor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '工业相机、视觉传感器与视觉系统。', `description_zh`), `description` = IF(`description` = '', 'Industrial cameras, vision sensors and machine vision systems.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'vision' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '颜色传感器与色标传感器。', `description_zh`), `description` = IF(`description` = '', 'Color sensors and contrast (registration mark) sensors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'color_sensor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '条码与二维码读码器、RFID 读写器与标签。', `description_zh`), `description` = IF(`description` = '', 'Barcode and 2D code readers, RFID readers and tags.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'code_reader' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '安全光幕、安全光栅与安全激光扫描仪。', `description_zh`), `description` = IF(`description` = '', 'Safety light curtains, light grids and safety laser scanners.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'safety_light_curtain' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '振动、倾角、电流等状态监测传感器。', `description_zh`), `description` = IF(`description` = '', 'Condition monitoring sensors for vibration, tilt and current.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'vibration_sensor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '传感器支架、反光板、连接线与安装附件。', `description_zh`), `description` = IF(`description` = '', 'Sensor brackets, reflectors, cables and mounting accessories.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'sensor_accessory' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '雷达、超声波、导波雷达等连续物位计。', `description_zh`), `description` = IF(`description` = '', 'Continuous level meters such as radar, ultrasonic and guided wave radar.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'level_meter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '音叉、浮球、电容等液位与料位开关。', `description_zh`), `description` = IF(`description` = '', 'Vibrating fork, float, capacitive and other point level switches.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'level_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '压力传感器、压力与差压变送器。', `description_zh`), `description` = IF(`description` = '', 'Pressure sensors and pressure and differential pressure transmitters.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'pressure_transmitter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '机械式与电子式压力开关。', `description_zh`), `description` = IF(`description` = '', 'Mechanical and electronic pressure switches.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'pressure_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电磁、涡街、质量等流量计与流量开关。', `description_zh`), `description` = IF(`description` = '', 'Electromagnetic, vortex, Coriolis and other flow meters and flow switches.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'flowmeter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '把热电阻、热电偶信号转为标准信号的温度变送器。', `description_zh`), `description` = IF(`description` = '', 'Temperature transmitters that convert RTD and thermocouple signals into standard outputs.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'temperature_transmitter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '称重传感器、称重变送器与称重显示器。', `description_zh`), `description` = IF(`description` = '', 'Load cells, weighing transmitters and weighing indicators.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'load_cell' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '指针式压力表、双金属温度计等现场显示仪表。', `description_zh`), `description` = IF(`description` = '', 'Dial pressure gauges, bimetal thermometers and other local indicators.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'pressure_gauge' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'pH、电导率、溶解氧、气体等分析仪表。', `description_zh`), `description` = IF(`description` = '', 'Analytical instruments for pH, conductivity, dissolved oxygen, gas and more.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'analyzer' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '无纸记录仪与数据记录仪。', `description_zh`), `description` = IF(`description` = '', 'Paperless recorders and data loggers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'recorder' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '温控器与 PID 调节器。', `description_zh`), `description` = IF(`description` = '', 'Temperature controllers and PID controllers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'temperature_controller' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '面板式计数器、计时器与转速表。', `description_zh`), `description` = IF(`description` = '', 'Panel-mount counters, timers and tachometers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'counter_timer' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '数显电压、电流等面板表与过程显示表。', `description_zh`), `description` = IF(`description` = '', 'Digital panel meters for voltage, current and process signals.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'panel_meter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电能表与多功能电力仪表。', `description_zh`), `description` = IF(`description` = '', 'Energy meters and multifunction power meters.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'energy_meter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '交流与直流接触器及辅助触点。', `description_zh`), `description` = IF(`description` = '', 'AC and DC contactors and auxiliary contacts.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'contactor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '保护电机过载的热过载继电器。', `description_zh`), `description` = IF(`description` = '', 'Thermal overload relays that protect motors from overload.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'thermal_overload' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '兼具短路与过载保护的电动机保护断路器。', `description_zh`), `description` = IF(`description` = '', 'Motor protection circuit breakers with short-circuit and overload protection.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'motor_protection_breaker' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电机管理系统与电子式电机保护器，带通讯与诊断。', `description_zh`), `description` = IF(`description` = '', 'Motor management systems and electronic motor protectors with communication and diagnostics.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'motor_management' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '微型断路器、塑壳断路器与框架断路器。', `description_zh`), `description` = IF(`description` = '', 'Miniature, molded case and air circuit breakers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'circuit_breaker' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '熔断器、熔断体与熔断器座。', `description_zh`), `description` = IF(`description` = '', 'Fuses, fuse links and fuse holders.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'fuse' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电源与信号线路的浪涌保护器。', `description_zh`), `description` = IF(`description` = '', 'Surge protective devices for power and signal lines.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'surge_protector' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '负荷隔离开关与熔断器式隔离开关。', `description_zh`), `description` = IF(`description` = '', 'Load break disconnect switches and fuse switch disconnectors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'disconnect_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '在两路电源之间自动切换的双电源转换开关。', `description_zh`), `description` = IF(`description` = '', 'Automatic transfer switches that switch between two power sources.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'transfer_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '低压电器的辅助触点、分励脱扣器、手柄、母排等附件。', `description_zh`), `description` = IF(`description` = '', 'Switchgear accessories such as auxiliary contacts, shunt trips, handles and busbars.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'switchgear_accessory' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '中间继电器、小型继电器及继电器底座。', `description_zh`), `description` = IF(`description` = '', 'General purpose and miniature relays and relay sockets.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'relay' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '无触点的固态继电器与固态接触器。', `description_zh`), `description` = IF(`description` = '', 'Contactless solid state relays and solid state contactors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'solid_state_relay' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '用于急停、安全门、光幕等回路的安全继电器。', `description_zh`), `description` = IF(`description` = '', 'Safety relays for emergency stop, safety door and light curtain circuits.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'safety_relay' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '通电延时、断电延时等时间继电器。', `description_zh`), `description` = IF(`description` = '', 'On-delay, off-delay and multifunction timer relays.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'timer_relay' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '相序、电压、电流、液位等监控继电器。', `description_zh`), `description` = IF(`description` = '', 'Monitoring relays for phase sequence, voltage, current, level and more.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'monitoring_relay' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电力系统用的综合保护继电器与保护装置。', `description_zh`), `description` = IF(`description` = '', 'Protection relays and protection devices for power distribution systems.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'protection_relay' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '信号隔离器、信号转换器与本安型安全栅。', `description_zh`), `description` = IF(`description` = '', 'Signal isolators, signal converters and intrinsic safety barriers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'signal_isolator' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '继电器输出、端子转接等 PLC 接口模块。', `description_zh`), `description` = IF(`description` = '', 'PLC interface modules such as relay output modules and terminal adapters.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'interface_module' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '导轨安装的开关电源。', `description_zh`), `description` = IF(`description` = '', 'DIN-rail mounted switching power supplies.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'din_power' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '直流 UPS、缓冲模块与冗余模块。', `description_zh`), `description` = IF(`description` = '', 'DC UPS, buffer modules and redundancy modules.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'ups' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '直流输入转换为另一电压的 DC/DC 转换器。', `description_zh`), `description` = IF(`description` = '', 'DC/DC converters that convert one DC voltage to another.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'dc_dc' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '控制变压器与隔离变压器。', `description_zh`), `description` = IF(`description` = '', 'Control transformers and isolation transformers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'transformer' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '调节电加热等负载功率的晶闸管电力调整器。', `description_zh`), `description` = IF(`description` = '', 'Thyristor power controllers that regulate electric heaters and similar loads.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'power_controller' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '抑制电源线干扰的电源滤波器。', `description_zh`), `description` = IF(`description` = '', 'Power line filters that suppress interference on supply lines.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'power_filter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '功率因数补偿电容器、补偿控制器与电容接触器。', `description_zh`), `description` = IF(`description` = '', 'Power factor correction capacitors, PFC controllers and capacitor contactors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'capacitor_compensation' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '按钮、急停按钮与选择开关。', `description_zh`), `description` = IF(`description` = '', 'Pushbuttons, emergency stop buttons and selector switches.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'pushbutton' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '凸轮开关与万能转换开关。', `description_zh`), `description` = IF(`description` = '', 'Cam switches and changeover switches.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'cam_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '指示灯与多层信号灯（三色灯）。', `description_zh`), `description` = IF(`description` = '', 'Indicator lights and stacked signal towers.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'indicator' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '安全门锁、安全门开关与拉绳开关。', `description_zh`), `description` = IF(`description` = '', 'Safety interlock switches, safety door switches and rope pull switches.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'safety_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '脚踏开关、操纵杆与按钮操作盒。', `description_zh`), `description` = IF(`description` = '', 'Foot switches, joysticks and pushbutton control stations.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'foot_switch' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'PLC、触摸屏、变频器的编程电缆与通讯电缆。', `description_zh`), `description` = IF(`description` = '', 'Programming and communication cables for PLCs, HMIs and drives.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'programming_cable' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '变频器、伺服与操作面板之间的连接电缆。', `description_zh`), `description` = IF(`description` = '', 'Connection cables between drives, servo systems and operator panels.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'drive_cable' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '工业光纤跳线与光纤连接器。', `description_zh`), `description` = IF(`description` = '', 'Industrial fiber patch cords and fiber connectors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'fiber_patch_cord' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'PROFIBUS 等总线接头与工业连接器。', `description_zh`), `description` = IF(`description` = '', 'PROFIBUS and other bus connectors and industrial connectors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'bus_connector' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '螺钉、弹簧、直插式接线端子及标记附件。', `description_zh`), `description` = IF(`description` = '', 'Screw, spring-clamp and push-in terminal blocks and marking accessories.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'terminal_block' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '气动与流体用电磁阀及阀岛。', `description_zh`), `description` = IF(`description` = '', 'Solenoid valves and valve terminals for pneumatics and fluids.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'solenoid_valve' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '球阀、蝶阀、角座阀等过程阀门。', `description_zh`), `description` = IF(`description` = '', 'Process valves such as ball, butterfly and angle seat valves.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'process_valve' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '无需外部能源、靠介质自身压力或温度动作的自力式调节阀。', `description_zh`), `description` = IF(`description` = '', 'Self-operated regulators driven by the process pressure or temperature without external power.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'self_operated_valve' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '阀门定位器与气动、电动执行机构。', `description_zh`), `description` = IF(`description` = '', 'Valve positioners and pneumatic or electric valve actuators.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'valve_positioner' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '气动和液压比例阀、伺服阀与电气比例调压阀。', `description_zh`), `description` = IF(`description` = '', 'Pneumatic and hydraulic proportional valves, servo valves and electro-pneumatic regulators.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'proportional_valve' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '标准、薄型、迷你等各类气缸。', `description_zh`), `description` = IF(`description` = '', 'Standard, compact, mini and other pneumatic cylinders.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'pneumatic_cylinder' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '过滤器、减压阀、油雾器等气源处理元件。', `description_zh`), `description` = IF(`description` = '', 'Filters, regulators, lubricators and other air preparation units.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'air_preparation' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '真空发生器、真空吸盘与真空开关。', `description_zh`), `description` = IF(`description` = '', 'Vacuum generators, vacuum cups and vacuum switches.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'vacuum' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '快插接头、气管与消声器等气动附件。', `description_zh`), `description` = IF(`description` = '', 'Push-in fittings, tubing, silencers and other pneumatic accessories.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'pneumatic_fitting' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '换向阀、溢流阀、叠加阀等液压阀。', `description_zh`), `description` = IF(`description` = '', 'Directional, relief, modular and other hydraulic valves.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'hydraulic_valve' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '柱塞泵、叶片泵、齿轮泵与液压马达。', `description_zh`), `description` = IF(`description` = '', 'Piston, vane and gear pumps and hydraulic motors.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'hydraulic_pump' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '各类液压缸（油缸）。', `description_zh`), `description` = IF(`description` = '', 'Hydraulic cylinders of various types.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'hydraulic_cylinder' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '液压系统的滤芯与过滤器。', `description_zh`), `description` = IF(`description` = '', 'Filter elements and filters for hydraulic systems.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'hydraulic_filter' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'IGBT 模块、整流桥、晶闸管等功率器件，多用于变频器维修。', `description_zh`), `description` = IF(`description` = '', 'IGBT modules, rectifier bridges, thyristors and other power devices, mainly for drive repair.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'igbt_module' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '设备控制板、主板与电路板备件。', `description_zh`), `description` = IF(`description` = '', 'Machine control boards, mainboards and circuit board spare parts.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'control_board' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '电解电容、薄膜电容等电子元件。', `description_zh`), `description` = IF(`description` = '', 'Electrolytic and film capacitors and other electronic components.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'capacitor' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '散热风扇、柜体风扇与空调等热管理产品。', `description_zh`), `description` = IF(`description` = '', 'Cooling fans, enclosure fans, cooling units and other thermal management products.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'cooling_fan' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', 'PLC、CNC、伺服用的备用锂电池。', `description_zh`), `description` = IF(`description` = '', 'Backup lithium batteries for PLCs, CNCs and servo systems.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'backup_battery' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');
UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', '工业 LCD 屏与显示模组。', `description_zh`), `description` = IF(`description` = '', 'Industrial LCD panels and display modules.', `description`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `category_code` = 'lcd_module' AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');

-- ================================================================ 系列

-- Siemens
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Siemens' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：LOGO! 逻辑模块，带显示与按键，适合简单控制。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LOGO!' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LOGO!', '小型 PLC：LOGO! 逻辑模块，带显示与按键，适合简单控制。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LOGO!') x);
UPDATE `product_series` SET `description` = '小型 PLC：S7-200 SMART 一体机与扩展模块，国内市场主力机型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-200 SMART' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S7-200 SMART', '小型 PLC：S7-200 SMART 一体机与扩展模块，国内市场主力机型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-200 SMART') x);
UPDATE `product_series` SET `description` = '小型 PLC：SIMATIC S7-1200 CPU、信号模块与通讯模块，TIA 博途编程。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-1200' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S7-1200', '小型 PLC：SIMATIC S7-1200 CPU、信号模块与通讯模块，TIA 博途编程。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-1200') x);
UPDATE `product_series` SET `description` = '中大型 PLC：SIMATIC S7-1500 CPU、I/O 与工艺模块，含 F 安全型与 T 运动型 CPU。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-1500' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S7-1500', '中大型 PLC：SIMATIC S7-1500 CPU、I/O 与工艺模块，含 F 安全型与 T 运动型 CPU。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-1500') x);
UPDATE `product_series` SET `description` = '中型 PLC：SIMATIC S7-300 CPU 与 SM/CP/FM 模块，存量设备维修需求大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-300' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S7-300', '中型 PLC：SIMATIC S7-300 CPU 与 SM/CP/FM 模块，存量设备维修需求大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-300') x);
UPDATE `product_series` SET `description` = '大型 PLC：SIMATIC S7-400 CPU、冗余 H 系统与机架模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-400' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S7-400', '大型 PLC：SIMATIC S7-400 CPU、冗余 H 系统与机架模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-400') x);
UPDATE `product_series` SET `description` = '老款小型 PLC：S7-200 CPU 22x 与 EM 扩展模块，已停产，多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-200' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S7-200', '老款小型 PLC：S7-200 CPU 22x 与 EM 扩展模块，已停产，多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-200') x);
UPDATE `product_series` SET `description` = '分布式 I/O：ET 200SP、ET 200MP、ET 200S、ET 200M 等接口模块与 I/O 模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ET-200' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ET-200', '分布式 I/O：ET 200SP、ET 200MP、ET 200S、ET 200M 等接口模块与 I/O 模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ET-200') x);
UPDATE `product_series` SET `description` = '触摸屏：SIMATIC KTP 精简面板，4–15 寸。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Basic Panel(KTP)' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Basic Panel(KTP)', '触摸屏：SIMATIC KTP 精简面板，4–15 寸。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Basic Panel(KTP)') x);
UPDATE `product_series` SET `description` = '触摸屏：SIMATIC TP/KP 精智面板，带按键或触摸，7–22 寸。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Comfort Panel' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Comfort Panel', '触摸屏：SIMATIC TP/KP 精智面板，带按键或触摸，7–22 寸。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Comfort Panel') x);
UPDATE `product_series` SET `description` = '老款面板：TP177、TP277、OP177、OP277 等触摸屏与操作面板，多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TP/OP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TP/OP', '老款面板：TP177、TP277、OP177、OP277 等触摸屏与操作面板，多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TP/OP') x);
UPDATE `product_series` SET `description` = '工控机：SIMATIC 平板式与箱式工业 PC。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIMATIC IPC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SIMATIC IPC', '工控机：SIMATIC 平板式与箱式工业 PC。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIMATIC IPC') x);
UPDATE `product_series` SET `description` = '工业以太网交换机：SCALANCE X 非网管与网管型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SCALANCE X' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SCALANCE X', '工业以太网交换机：SCALANCE X 非网管与网管型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SCALANCE X') x);
UPDATE `product_series` SET `description` = '变频器：SINAMICS V20 基本型，0.12–30 kW。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'V20' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'V20', '变频器：SINAMICS V20 基本型，0.12–30 kW。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'V20') x);
UPDATE `product_series` SET `description` = '变频器：SINAMICS G120 模块化（控制单元 CU + 功率模块 PM）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G120' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'G120', '变频器：SINAMICS G120 模块化（控制单元 CU + 功率模块 PM）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G120') x);
UPDATE `product_series` SET `description` = '变频器：SINAMICS G120C 紧凑型一体机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G120C' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'G120C', '变频器：SINAMICS G120C 紧凑型一体机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G120C') x);
UPDATE `product_series` SET `description` = '多轴驱动：SINAMICS S120 电机模块、电源模块与 CU320 控制单元。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S120' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S120', '多轴驱动：SINAMICS S120 电机模块、电源模块与 CU320 控制单元。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S120') x);
UPDATE `product_series` SET `description` = '老款变频器：MICROMASTER 420 通用型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro Master420' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Micro Master420', '老款变频器：MICROMASTER 420 通用型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro Master420') x);
UPDATE `product_series` SET `description` = '老款变频器：MICROMASTER 430 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro Master430' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Micro Master430', '老款变频器：MICROMASTER 430 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro Master430') x);
UPDATE `product_series` SET `description` = '老款变频器：MICROMASTER 440 矢量型，存量大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro Master440' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Micro Master440', '老款变频器：MICROMASTER 440 矢量型，存量大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro Master440') x);
UPDATE `product_series` SET `description` = '软启动器：SIRIUS 3RW30 简易型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '3RW30' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '3RW30', '软启动器：SIRIUS 3RW30 简易型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '3RW30') x);
UPDATE `product_series` SET `description` = '软启动器：SIRIUS 3RW40，带内置旁路与过载保护。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '3RW40' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '3RW40', '软启动器：SIRIUS 3RW40，带内置旁路与过载保护。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '3RW40') x);
UPDATE `product_series` SET `description` = '软启动器：SIRIUS 3RW44 高性能型，带通讯与显示。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '3RW44' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '3RW44', '软启动器：SIRIUS 3RW44 高性能型，带通讯与显示。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '3RW44') x);
UPDATE `product_series` SET `description` = '伺服：SINAMICS V90 驱动器与 SIMOTICS S-1FL6 电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SINAMICS V90' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SINAMICS V90', '伺服：SINAMICS V90 驱动器与 SIMOTICS S-1FL6 电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SINAMICS V90') x);
UPDATE `product_series` SET `description` = '伺服电机：1FK7 同步伺服电机，配 S120 使用。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIMOTICS S-1FK7' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SIMOTICS S-1FK7', '伺服电机：1FK7 同步伺服电机，配 S120 使用。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIMOTICS S-1FK7') x);
UPDATE `product_series` SET `description` = '接触器：SIRIUS 3RT2 / 3RT1 系列及辅助触点。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIRIUS 3RT' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SIRIUS 3RT', '接触器：SIRIUS 3RT2 / 3RT1 系列及辅助触点。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIRIUS 3RT') x);
UPDATE `product_series` SET `description` = '电动机保护断路器：SIRIUS 3RV2 / 3RV1。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIRIUS 3RV' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SIRIUS 3RV', '电动机保护断路器：SIRIUS 3RV2 / 3RV1。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIRIUS 3RV') x);
UPDATE `product_series` SET `description` = '电机管理：SIMOCODE pro 电机保护与控制系统。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIMOCODE pro' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SIMOCODE pro', '电机管理：SIMOCODE pro 电机保护与控制系统。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIMOCODE pro') x);
UPDATE `product_series` SET `description` = '导轨电源：SITOP PSU、smart、modular 及 UPS 模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SITOP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SITOP', '导轨电源：SITOP PSU、smart、modular 及 UPS 模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SITOP') x);
UPDATE `product_series` SET `description` = '压力变送器：SITRANS P 系列压力与差压变送器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SITRANS P' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SITRANS P', '压力变送器：SITRANS P 系列压力与差压变送器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SITRANS P') x);
UPDATE `product_series` SET `description` = '阀门定位器：SIPART PS2 智能电气阀门定位器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIPART PS2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SIPART PS2', '阀门定位器：SIPART PS2 智能电气阀门定位器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SIPART PS2') x);
UPDATE `product_series` SET `description` = '数控系统：SINUMERIK 808D、828D、840D 系统与面板。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SINUMERIK' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SINUMERIK', '数控系统：SINUMERIK 808D、828D、840D 系统与面板。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SINUMERIK') x);

-- ABB
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'ABB' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = 'PLC：AC500 与 AC500-eCo CPU 及 S500 I/O 模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AC500' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AC500', 'PLC：AC500 与 AC500-eCo CPU 及 S500 I/O 模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AC500') x);
UPDATE `product_series` SET `description` = '变频器：ACS150 微型通用型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 150' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACS 150', '变频器：ACS150 微型通用型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 150') x);
UPDATE `product_series` SET `description` = '变频器：ACS310 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 310' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACS 310', '变频器：ACS310 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 310') x);
UPDATE `product_series` SET `description` = '变频器：ACS355 机械型，紧凑安装。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 355' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACS 355', '变频器：ACS355 机械型，紧凑安装。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 355') x);
UPDATE `product_series` SET `description` = '变频器：ACS380 机械型，替代 ACS355。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 380' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACS 380', '变频器：ACS380 机械型，替代 ACS355。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 380') x);
UPDATE `product_series` SET `description` = '变频器：ACS530 通用型（国内市场）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 530' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACS 530', '变频器：ACS530 通用型（国内市场）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 530') x);
UPDATE `product_series` SET `description` = '老款变频器：ACS550 通用型，存量大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 550' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACS 550', '老款变频器：ACS550 通用型，存量大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 550') x);
UPDATE `product_series` SET `description` = '变频器：ACS580 通用型，替代 ACS550。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 580' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACS 580', '变频器：ACS580 通用型，替代 ACS550。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 580') x);
UPDATE `product_series` SET `description` = '老款变频器：ACS800 工业型，单传动与多传动。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 800' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACS 800', '老款变频器：ACS800 工业型，单传动与多传动。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 800') x);
UPDATE `product_series` SET `description` = '变频器：ACS880 工业型，替代 ACS800。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 880' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACS 880', '变频器：ACS880 工业型，替代 ACS800。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACS 880') x);
UPDATE `product_series` SET `description` = '直流调速器：DCS800。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DCS800' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DCS800', '直流调速器：DCS800。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DCS800') x);
UPDATE `product_series` SET `description` = '软启动器：PSR 紧凑型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PSR', '软启动器：PSR 紧凑型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSR') x);
UPDATE `product_series` SET `description` = '软启动器：PSE 高效型，带显示。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSE' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PSE', '软启动器：PSE 高效型，带显示。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSE') x);
UPDATE `product_series` SET `description` = '软启动器：PSTX 高级型，带通讯与扭矩控制。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSTX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PSTX', '软启动器：PSTX 高级型，带通讯与扭矩控制。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSTX') x);
UPDATE `product_series` SET `description` = '接触器：AF 系列（宽电压线圈）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AF' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AF', '接触器：AF 系列（宽电压线圈）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AF') x);
UPDATE `product_series` SET `description` = '电动机保护断路器：MS116、MS132。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MS116/MS132' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MS116/MS132', '电动机保护断路器：MS116、MS132。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MS116/MS132') x);
UPDATE `product_series` SET `description` = '微型断路器：S200 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S200' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S200', '微型断路器：S200 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S200') x);
UPDATE `product_series` SET `description` = '塑壳断路器：Tmax T 与 Tmax XT 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Tmax' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Tmax', '塑壳断路器：Tmax T 与 Tmax XT 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Tmax') x);
UPDATE `product_series` SET `description` = '压力变送器：266 系列压力与差压变送器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '266' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '266', '压力变送器：266 系列压力与差压变送器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '266') x);
UPDATE `product_series` SET `description` = '机器人：IRC5 控制柜及 FlexPendant 示教器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IRC5' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IRC5', '机器人：IRC5 控制柜及 FlexPendant 示教器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IRC5') x);

-- Schneider Electric
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Schneider Electric' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：Modicon M100/M200 经济型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'M100/M200 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'M100/M200 Series', '小型 PLC：Modicon M100/M200 经济型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'M100/M200 Series') x);
UPDATE `product_series` SET `description` = '小型 PLC：Modicon M221、M241、M251 及 TM3 扩展模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TM221/241/251 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TM221/241/251 Series', '小型 PLC：Modicon M221、M241、M251 及 TM3 扩展模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TM221/241/251 Series') x);
UPDATE `product_series` SET `description` = '中大型 PLC：Modicon M340、M580（以太网 ePAC）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'M340/M580 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'M340/M580 Series', '中大型 PLC：Modicon M340、M580（以太网 ePAC）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'M340/M580 Series') x);
UPDATE `product_series` SET `description` = '老款 PLC：TSX Micro，多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TSX Micro' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TSX Micro', '老款 PLC：TSX Micro，多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TSX Micro') x);
UPDATE `product_series` SET `description` = '老款 PLC：Modicon Premium（TSX57），多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PREMIUM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PREMIUM', '老款 PLC：Modicon Premium（TSX57），多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PREMIUM') x);
UPDATE `product_series` SET `description` = '老款大型 PLC：Modicon Quantum（140 系列）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'QUANTUM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'QUANTUM', '老款大型 PLC：Modicon Quantum（140 系列）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'QUANTUM') x);
UPDATE `product_series` SET `description` = '智能继电器：Zelio Logic SR2/SR3 小型逻辑控制器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ZELIO Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ZELIO Series', '智能继电器：Zelio Logic SR2/SR3 小型逻辑控制器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ZELIO Series') x);
UPDATE `product_series` SET `description` = '触摸屏：Magelis / Harmony HMIGTO、HMISTO、HMIGXU 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Magelis HMI' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Magelis HMI', '触摸屏：Magelis / Harmony HMIGTO、HMISTO、HMIGXU 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Magelis HMI') x);
UPDATE `product_series` SET `description` = '变频器：Altivar 12 微型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV12' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATV12', '变频器：Altivar 12 微型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV12') x);
UPDATE `product_series` SET `description` = '变频器：Altivar 212 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV212' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATV212', '变频器：Altivar 212 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV212') x);
UPDATE `product_series` SET `description` = '变频器：Altivar 310 经济型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV310' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATV310', '变频器：Altivar 310 经济型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV310') x);
UPDATE `product_series` SET `description` = '变频器：Altivar 312 通用型，存量大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV312' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATV312', '变频器：Altivar 312 通用型，存量大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV312') x);
UPDATE `product_series` SET `description` = '变频器：Altivar Machine 320 机械型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV320' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATV320', '变频器：Altivar Machine 320 机械型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV320') x);
UPDATE `product_series` SET `description` = '老款变频器：Altivar 61 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV61' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATV61', '老款变频器：Altivar 61 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV61') x);
UPDATE `product_series` SET `description` = '老款变频器：Altivar 71 重载型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV71' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATV71', '老款变频器：Altivar 71 重载型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV71') x);
UPDATE `product_series` SET `description` = '变频器：Altivar 610 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV610' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATV610', '变频器：Altivar 610 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV610') x);
UPDATE `product_series` SET `description` = '变频器：Altivar Process 930 过程型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV930' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATV930', '变频器：Altivar Process 930 过程型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV930') x);
SET @taken := (SELECT COUNT(*) FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATS01/ATS22');
UPDATE `product_series` SET `series_name` = 'ATS01/ATS22', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATS01/ATV22' AND `deleted_at` IS NULL AND @taken = 0;
UPDATE `product_series` SET `description` = '软启动器：Altistart 01（ATS01）、Altistart 22（ATS22）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATS01/ATS22' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATS01/ATS22', '软启动器：Altistart 01（ATS01）、Altistart 22（ATS22）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATS01/ATS22') x);
SET @taken := (SELECT COUNT(*) FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATS48');
UPDATE `product_series` SET `series_name` = 'ATS48', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATV48' AND `deleted_at` IS NULL AND @taken = 0;
UPDATE `product_series` SET `description` = '软启动器：Altistart 48（ATS48）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATS48' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ATS48', '软启动器：Altistart 48（ATS48）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ATS48') x);
UPDATE `product_series` SET `description` = '伺服：Lexium 32 驱动器与 BMH/BSH 电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lexium 32' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Lexium 32', '伺服：Lexium 32 驱动器与 BMH/BSH 电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lexium 32') x);
UPDATE `product_series` SET `description` = '伺服：Lexium 28 经济型驱动器与 BCH2 电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lexium 28' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Lexium 28', '伺服：Lexium 28 经济型驱动器与 BCH2 电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lexium 28') x);
UPDATE `product_series` SET `description` = '伺服：Lexium 26 驱动器与 BCH 电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lexium 26' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Lexium 26', '伺服：Lexium 26 驱动器与 BCH 电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lexium 26') x);
UPDATE `product_series` SET `description` = '老款伺服：Lexium 16 系列，多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lexium 16D' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Lexium 16D', '老款伺服：Lexium 16 系列，多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lexium 16D') x);
UPDATE `product_series` SET `description` = '接触器：TeSys D（LC1D）及热继电器 LRD。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TeSys D' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TeSys D', '接触器：TeSys D（LC1D）及热继电器 LRD。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TeSys D') x);
UPDATE `product_series` SET `description` = '电动机保护断路器：TeSys GV2、GV3。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TeSys GV2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TeSys GV2', '电动机保护断路器：TeSys GV2、GV3。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TeSys GV2') x);
UPDATE `product_series` SET `description` = '微型断路器：Acti9 iC65 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Acti9' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Acti9', '微型断路器：Acti9 iC65 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Acti9') x);
UPDATE `product_series` SET `description` = '塑壳断路器：Compact NSX。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Compact NSX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Compact NSX', '塑壳断路器：Compact NSX。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Compact NSX') x);
UPDATE `product_series` SET `description` = '中间继电器：Zelio Relay RXM、RSB 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Zelio Relay' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Zelio Relay', '中间继电器：Zelio Relay RXM、RSB 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Zelio Relay') x);
UPDATE `product_series` SET `description` = '按钮指示灯：Harmony XB4、XB5、XB7。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Harmony XB5' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Harmony XB5', '按钮指示灯：Harmony XB4、XB5、XB7。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Harmony XB5') x);

-- Telemecanique
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Telemecanique' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '接近开关：OsiSense XS 电感式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XS', '接近开关：OsiSense XS 电感式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XS') x);
UPDATE `product_series` SET `description` = '光电开关：OsiSense XU。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XU' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XU', '光电开关：OsiSense XU。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XU') x);
UPDATE `product_series` SET `description` = '限位开关：OsiSense XC。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XC', '限位开关：OsiSense XC。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XC') x);
UPDATE `product_series` SET `description` = '压力开关：OsiSense XM（XMLP、XMLR 等）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XM', '压力开关：OsiSense XM（XMLP、XMLR 等）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XM') x);

-- Bosch Rexroth
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Bosch Rexroth' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '伺服驱动：IndraDrive Cs、C、M 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IndraDrive' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IndraDrive', '伺服驱动：IndraDrive Cs、C、M 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IndraDrive') x);
UPDATE `product_series` SET `description` = '伺服电机：MSK（老款）与 MS2N。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MSK/MS2N' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MSK/MS2N', '伺服电机：MSK（老款）与 MS2N。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MSK/MS2N') x);
UPDATE `product_series` SET `description` = '变频器：EFC 3610、EFC 5610。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EFC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EFC', '变频器：EFC 3610、EFC 5610。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EFC') x);
UPDATE `product_series` SET `description` = '液压阀：4WE 电磁换向阀。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '4WE' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '4WE', '液压阀：4WE 电磁换向阀。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '4WE') x);
UPDATE `product_series` SET `description` = '液压泵：A10VSO 变量柱塞泵。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A10VSO' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'A10VSO', '液压泵：A10VSO 变量柱塞泵。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A10VSO') x);

-- Lenze
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Lenze' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：i500 系列（i510、i550）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'i500' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'i500', '变频器：i500 系列（i510、i550）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'i500') x);
UPDATE `product_series` SET `description` = '变频器：8400 系列（BaseLine、StateLine、HighLine、TopLine）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '8400' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '8400', '变频器：8400 系列（BaseLine、StateLine、HighLine、TopLine）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '8400') x);
UPDATE `product_series` SET `description` = '伺服驱动：9400 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '9400' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '9400', '伺服驱动：9400 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '9400') x);
UPDATE `product_series` SET `description` = '齿轮电机：g500 系列斜齿轮、锥齿轮减速电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'g500' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'g500', '齿轮电机：g500 系列斜齿轮、锥齿轮减速电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'g500') x);

-- SEW-EURODRIVE
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'SEW-EURODRIVE' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：MOVITRAC B、LTE-B 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MOVITRAC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MOVITRAC', '变频器：MOVITRAC B、LTE-B 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MOVITRAC') x);
UPDATE `product_series` SET `description` = '驱动：MOVIDRIVE B、MOVIDRIVE modular 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MOVIDRIVE' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MOVIDRIVE', '驱动：MOVIDRIVE B、MOVIDRIVE modular 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MOVIDRIVE') x);
UPDATE `product_series` SET `description` = '分布式驱动：MOVIMOT 电机集成变频器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MOVIMOT' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MOVIMOT', '分布式驱动：MOVIMOT 电机集成变频器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MOVIMOT') x);
UPDATE `product_series` SET `description` = '齿轮电机：R 斜齿轮、F 平行轴、K 锥齿轮、S 蜗轮蜗杆减速电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'R/F/K/S' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'R/F/K/S', '齿轮电机：R 斜齿轮、F 平行轴、K 锥齿轮、S 蜗轮蜗杆减速电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'R/F/K/S') x);

-- Danfoss
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Danfoss' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：VLT Micro Drive FC 51。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC51' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC51', '变频器：VLT Micro Drive FC 51。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC51') x);
UPDATE `product_series` SET `description` = '变频器：VLT HVAC Basic Drive FC 101。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC101' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC101', '变频器：VLT HVAC Basic Drive FC 101。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC101') x);
UPDATE `product_series` SET `description` = '变频器：VLT HVAC Drive FC 102。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC102' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC102', '变频器：VLT HVAC Drive FC 102。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC102') x);
UPDATE `product_series` SET `description` = '变频器：VLT FC 111（主营产品表中的型号，定位待核实）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC111' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC111', '变频器：VLT FC 111（主营产品表中的型号，定位待核实）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC111') x);
UPDATE `product_series` SET `description` = '变频器：VLT AQUA Drive FC 202 水处理型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC202' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC202', '变频器：VLT AQUA Drive FC 202 水处理型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC202') x);
UPDATE `product_series` SET `description` = '变频器：VLT Midi Drive FC 280。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC280' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC280', '变频器：VLT Midi Drive FC 280。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC280') x);
UPDATE `product_series` SET `description` = '变频器：VLT AutomationDrive FC 301。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC301' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC301', '变频器：VLT AutomationDrive FC 301。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC301') x);
UPDATE `product_series` SET `description` = '变频器：VLT AutomationDrive FC 302 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC302' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC302', '变频器：VLT AutomationDrive FC 302 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC302') x);
UPDATE `product_series` SET `description` = '变频器：VLT AutomationDrive FC 360 经济型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC360' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC360', '变频器：VLT AutomationDrive FC 360 经济型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC360') x);
UPDATE `product_series` SET `description` = '变频器：VACON 10 微型（原伟肯）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON 10' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VACON 10', '变频器：VACON 10 微型（原伟肯）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON 10') x);
UPDATE `product_series` SET `description` = '变频器：VACON 20 紧凑型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON 20' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VACON 20', '变频器：VACON 20 紧凑型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON 20') x);
UPDATE `product_series` SET `description` = '变频器：VACON 20 冷板型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON 20Cold Plate' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VACON 20Cold Plate', '变频器：VACON 20 冷板型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON 20Cold Plate') x);
UPDATE `product_series` SET `description` = '变频器：VACON NXP / NXS 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON NXP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VACON NXP', '变频器：VACON NXP / NXS 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON NXP') x);
UPDATE `product_series` SET `description` = '变频器：VACON 100 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON 100' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VACON 100', '变频器：VACON 100 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VACON 100') x);
UPDATE `product_series` SET `description` = '软启动器：VLT Soft Starter MCD 201。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MCD201' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MCD201', '软启动器：VLT Soft Starter MCD 201。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MCD201') x);
UPDATE `product_series` SET `description` = '软启动器：VLT Soft Starter MCD 202，带电机保护。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MCD202' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MCD202', '软启动器：VLT Soft Starter MCD 202，带电机保护。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MCD202') x);
UPDATE `product_series` SET `description` = '软启动器：VLT Soft Starter MCD 500 高级型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MCD500' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MCD500', '软启动器：VLT Soft Starter MCD 500 高级型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MCD500') x);
UPDATE `product_series` SET `description` = '压力变送器：MBS 3000 等工业压力变送器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MBS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MBS', '压力变送器：MBS 3000 等工业压力变送器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MBS') x);
UPDATE `product_series` SET `description` = '压力开关：KP 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'KP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'KP', '压力开关：KP 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'KP') x);
UPDATE `product_series` SET `description` = '电磁阀：EV220B 等伺服式电磁阀。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EV220B' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EV220B', '电磁阀：EV220B 等伺服式电磁阀。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EV220B') x);

-- Beckhoff
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Beckhoff' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '嵌入式控制器：CX 系列嵌入式 PC（TwinCAT）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CX', '嵌入式控制器：CX 系列嵌入式 PC（TwinCAT）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CX') x);
UPDATE `product_series` SET `description` = 'I/O：EtherCAT 端子模块 EL 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EL' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EL', 'I/O：EtherCAT 端子模块 EL 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EL') x);
UPDATE `product_series` SET `description` = '总线耦合器：EK 系列 EtherCAT 耦合器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EK' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EK', '总线耦合器：EK 系列 EtherCAT 耦合器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EK') x);
UPDATE `product_series` SET `description` = '伺服驱动：AX5000 EtherCAT 伺服驱动器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AX5000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AX5000', '伺服驱动：AX5000 EtherCAT 伺服驱动器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AX5000') x);
UPDATE `product_series` SET `description` = '伺服电机：AM8000 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AM8000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AM8000', '伺服电机：AM8000 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AM8000') x);
UPDATE `product_series` SET `description` = '平板工控机与控制面板：CP 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CP', '平板工控机与控制面板：CP 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CP') x);

-- B&R
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'B&R' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '控制器与 I/O：X20 系统 CPU 与 I/O 模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'X20' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'X20', '控制器与 I/O：X20 系统 CPU 与 I/O 模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'X20') x);
UPDATE `product_series` SET `description` = '人机界面：Power Panel 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Power Panel' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Power Panel', '人机界面：Power Panel 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Power Panel') x);
UPDATE `product_series` SET `description` = '伺服驱动：ACOPOS、ACOPOSmulti、ACOPOS P3。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACOPOS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACOPOS', '伺服驱动：ACOPOS、ACOPOSmulti、ACOPOS P3。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACOPOS') x);
UPDATE `product_series` SET `description` = '工控机：Automation PC 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Automation PC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Automation PC', '工控机：Automation PC 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Automation PC') x);

-- Phoenix Contact
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Phoenix Contact' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '导轨电源：QUINT POWER 高端型，含 UPS 与冗余模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'QUINT' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'QUINT', '导轨电源：QUINT POWER 高端型，含 UPS 与冗余模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'QUINT') x);
UPDATE `product_series` SET `description` = '导轨电源：TRIO POWER 标准型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TRIO' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TRIO', '导轨电源：TRIO POWER 标准型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TRIO') x);
UPDATE `product_series` SET `description` = '继电器接口：PLC-INTERFACE 继电器模块（PLC-RSC 等）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PLC-INTERFACE' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PLC-INTERFACE', '继电器接口：PLC-INTERFACE 继电器模块（PLC-RSC 等）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PLC-INTERFACE') x);
UPDATE `product_series` SET `description` = '信号隔离：MINI Analog（MINI MCR）信号调理器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MINI Analog' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MINI Analog', '信号隔离：MINI Analog（MINI MCR）信号调理器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MINI Analog') x);
UPDATE `product_series` SET `description` = '接线端子：UT 螺钉式、PT 直插式等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UT/PT' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'UT/PT', '接线端子：UT 螺钉式、PT 直插式等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UT/PT') x);
UPDATE `product_series` SET `description` = '工业以太网交换机：FL SWITCH 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FL SWITCH' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FL SWITCH', '工业以太网交换机：FL SWITCH 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FL SWITCH') x);
UPDATE `product_series` SET `description` = '控制器：PLCnext Control（AXC F 系列）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PLCnext' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PLCnext', '控制器：PLCnext Control（AXC F 系列）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PLCnext') x);

-- Weidmüller
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Weidmüller' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '导轨电源：PROmax、PROtop、PROeco。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PRO' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PRO', '导轨电源：PROmax、PROtop、PROeco。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PRO') x);
UPDATE `product_series` SET `description` = '继电器：TERMSERIES 超薄继电器模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TERMSERIES' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TERMSERIES', '继电器：TERMSERIES 超薄继电器模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TERMSERIES') x);
UPDATE `product_series` SET `description` = '信号隔离：ACT20 系列信号调理器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACT20' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ACT20', '信号隔离：ACT20 系列信号调理器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ACT20') x);
UPDATE `product_series` SET `description` = '远程 I/O：u-remote 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'u-remote' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'u-remote', '远程 I/O：u-remote 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'u-remote') x);
UPDATE `product_series` SET `description` = '接线端子：W 螺钉式、A 直插式、Z 弹簧式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Klippon Connect' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Klippon Connect', '接线端子：W 螺钉式、A 直插式、Z 弹簧式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Klippon Connect') x);

-- WAGO
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'WAGO' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = 'I/O 系统：750 系列总线耦合器与 I/O 模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '750' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '750', 'I/O 系统：750 系列总线耦合器与 I/O 模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '750') x);
UPDATE `product_series` SET `description` = '控制器：PFC200 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PFC200' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PFC200', '控制器：PFC200 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PFC200') x);
UPDATE `product_series` SET `description` = '导轨电源：787 系列 Eco、Classic、Pro 电源。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '787' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '787', '导轨电源：787 系列 Eco、Classic、Pro 电源。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '787') x);
UPDATE `product_series` SET `description` = '信号隔离：857 JUMPFLEX 信号调理器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '857' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '857', '信号隔离：857 JUMPFLEX 信号调理器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '857') x);
UPDATE `product_series` SET `description` = '接线端子：TOPJOB S（2002 等）直插式端子。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TOPJOB S' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TOPJOB S', '接线端子：TOPJOB S（2002 等）直插式端子。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TOPJOB S') x);
UPDATE `product_series` SET `description` = '接线连接器：221 系列杠杆式连接器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '221' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '221', '接线连接器：221 系列杠杆式连接器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '221') x);

-- Pilz
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Pilz' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '安全继电器：PNOZ、PNOZ X、PNOZsigma、PNOZmulti。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PNOZ' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PNOZ', '安全继电器：PNOZ、PNOZ X、PNOZsigma、PNOZmulti。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PNOZ') x);
UPDATE `product_series` SET `description` = '安全控制系统：PSS 4000。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSS 4000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PSS 4000', '安全控制系统：PSS 4000。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSS 4000') x);
UPDATE `product_series` SET `description` = '安全传感器：PSEN 安全门开关、安全光幕等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSEN' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PSEN', '安全传感器：PSEN 安全门开关、安全光幕等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PSEN') x);

-- SICK
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'SICK' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '光电开关：W12、W16、W26 等 W 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'W12' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'W12', '光电开关：W12、W16、W26 等 W 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'W12') x);
UPDATE `product_series` SET `description` = '接近开关：IME 电感式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IME' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IME', '接近开关：IME 电感式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IME') x);
UPDATE `product_series` SET `description` = '编码器：DFS60 增量式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DFS60' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DFS60', '编码器：DFS60 增量式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DFS60') x);
UPDATE `product_series` SET `description` = '安全光幕：deTec4。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'deTec4' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'deTec4', '安全光幕：deTec4。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'deTec4') x);
UPDATE `product_series` SET `description` = '安全激光扫描仪：S300、microScan3。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S300' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S300', '安全激光扫描仪：S300、microScan3。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S300') x);
UPDATE `product_series` SET `description` = '读码器：Lector 图像式读码器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lector' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Lector', '读码器：Lector 图像式读码器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Lector') x);

-- Pepperl+Fuchs
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Pepperl+Fuchs' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '安全栅与隔离器：K 系统（KFD2 等）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'K-System' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'K-System', '安全栅与隔离器：K 系统（KFD2 等）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'K-System') x);
UPDATE `product_series` SET `description` = '接近开关：NBB、NBN 等电感式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NBB/NBN' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NBB/NBN', '接近开关：NBB、NBN 等电感式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NBB/NBN') x);
UPDATE `product_series` SET `description` = '防爆工作站：VisuNet 系列 HMI 与远程监视器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VisuNet' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VisuNet', '防爆工作站：VisuNet 系列 HMI 与远程监视器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VisuNet') x);

-- ifm
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'ifm' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '接近开关：IF、IG、IE 等电感式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IF/IG/IE' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IF/IG/IE', '接近开关：IF、IG、IE 等电感式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IF/IG/IE') x);
UPDATE `product_series` SET `description` = '光电开关：O5、O6 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'O5/O6' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'O5/O6', '光电开关：O5、O6 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'O5/O6') x);
UPDATE `product_series` SET `description` = '压力传感器：PN 系列电子式压力开关。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PN' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PN', '压力传感器：PN 系列电子式压力开关。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PN') x);
UPDATE `product_series` SET `description` = '流量开关：SI 系列流量监视器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SI' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SI', '流量开关：SI 系列流量监视器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SI') x);
UPDATE `product_series` SET `description` = '温度传感器：TA 系列与 TN 温度开关。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TA/TN' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TA/TN', '温度传感器：TA 系列与 TN 温度开关。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TA/TN') x);
UPDATE `product_series` SET `description` = 'IO-Link 主站：AL 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AL' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AL', 'IO-Link 主站：AL 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AL') x);

-- Balluff
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Balluff' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '接近开关：BES 电感式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BES' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BES', '接近开关：BES 电感式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BES') x);
UPDATE `product_series` SET `description` = '光电开关：BOS 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BOS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BOS', '光电开关：BOS 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BOS') x);
UPDATE `product_series` SET `description` = '位移传感器：BTL 磁致伸缩直线位移传感器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BTL' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BTL', '位移传感器：BTL 磁致伸缩直线位移传感器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BTL') x);
UPDATE `product_series` SET `description` = 'RFID：BIS 工业 RFID 系统。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BIS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BIS', 'RFID：BIS 工业 RFID 系统。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BIS') x);
UPDATE `product_series` SET `description` = 'IO-Link：BNI 主站与集线器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BNI' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BNI', 'IO-Link：BNI 主站与集线器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BNI') x);

-- TURCK
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'TURCK' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '接近开关：BI、NI 电感式（含 uprox）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BI/NI' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BI/NI', '接近开关：BI、NI 电感式（含 uprox）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BI/NI') x);
UPDATE `product_series` SET `description` = '块式 I/O：TBEN 系列 IP67 模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TBEN' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TBEN', '块式 I/O：TBEN 系列 IP67 模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TBEN') x);
UPDATE `product_series` SET `description` = '远程 I/O：BL20 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BL20' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BL20', '远程 I/O：BL20 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BL20') x);
UPDATE `product_series` SET `description` = '隔离与安全栅：IM 系列接口模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IM', '隔离与安全栅：IM 系列接口模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IM') x);

-- Leuze
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Leuze' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '条码读码器：BCL 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BCL' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BCL', '条码读码器：BCL 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BCL') x);
UPDATE `product_series` SET `description` = '安全光幕：MLC 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MLC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MLC', '安全光幕：MLC 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MLC') x);
UPDATE `product_series` SET `description` = '距离传感器：ODS 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ODS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ODS', '距离传感器：ODS 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ODS') x);

-- Festo
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Festo' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '气缸：DSBC 标准气缸（ISO 15552）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DSBC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DSBC', '气缸：DSBC 标准气缸（ISO 15552）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DSBC') x);
UPDATE `product_series` SET `description` = '气缸：ADN 紧凑型气缸。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ADN' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ADN', '气缸：ADN 紧凑型气缸。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ADN') x);
UPDATE `product_series` SET `description` = '电磁阀：VUVG 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VUVG' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VUVG', '电磁阀：VUVG 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VUVG') x);
UPDATE `product_series` SET `description` = '气源处理：MS 系列过滤减压阀等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MS', '气源处理：MS 系列过滤减压阀等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MS') x);
UPDATE `product_series` SET `description` = '阀岛：CPX 电气终端与 VTSA、MPA 阀岛。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CPX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CPX', '阀岛：CPX 电气终端与 VTSA、MPA 阀岛。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CPX') x);

-- Bürkert
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Bürkert' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '电磁阀：6013 直动式两位两通电磁阀。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '6013' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '6013', '电磁阀：6013 直动式两位两通电磁阀。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '6013') x);
UPDATE `product_series` SET `description` = '电磁阀：6213 伺服辅助式电磁阀。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '6213' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '6213', '电磁阀：6213 伺服辅助式电磁阀。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '6213') x);
UPDATE `product_series` SET `description` = '过程阀：ELEMENT 系列角座阀与调节阀。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ELEMENT' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ELEMENT', '过程阀：ELEMENT 系列角座阀与调节阀。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ELEMENT') x);
UPDATE `product_series` SET `description` = '定位器：8692、8693 等阀门定位器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '8692' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '8692', '定位器：8692、8693 等阀门定位器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '8692') x);

-- Endress+Hauser
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Endress+Hauser' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '压力变送器：Cerabar 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Cerabar' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Cerabar', '压力变送器：Cerabar 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Cerabar') x);
UPDATE `product_series` SET `description` = '流量计：Promag 电磁流量计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Promag' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Promag', '流量计：Promag 电磁流量计。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Promag') x);
UPDATE `product_series` SET `description` = '物位计：Micropilot 雷达物位计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micropilot' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Micropilot', '物位计：Micropilot 雷达物位计。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micropilot') x);
UPDATE `product_series` SET `description` = '液位开关：Liquiphant 音叉液位开关。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Liquiphant' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Liquiphant', '液位开关：Liquiphant 音叉液位开关。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Liquiphant') x);
UPDATE `product_series` SET `description` = '温度变送器：iTEMP 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iTEMP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'iTEMP', '温度变送器：iTEMP 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iTEMP') x);

-- VEGA
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'VEGA' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '物位计：VEGAPULS 雷达物位计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VEGAPULS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VEGAPULS', '物位计：VEGAPULS 雷达物位计。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VEGAPULS') x);
UPDATE `product_series` SET `description` = '物位计：VEGAFLEX 导波雷达。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VEGAFLEX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VEGAFLEX', '物位计：VEGAFLEX 导波雷达。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VEGAFLEX') x);
UPDATE `product_series` SET `description` = '液位开关：VEGASWING 音叉。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VEGASWING' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VEGASWING', '液位开关：VEGASWING 音叉。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VEGASWING') x);
UPDATE `product_series` SET `description` = '压力变送器：VEGABAR 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VEGABAR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VEGABAR', '压力变送器：VEGABAR 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VEGABAR') x);

-- KROHNE
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'KROHNE' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '流量计：OPTIFLUX 电磁流量计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'OPTIFLUX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'OPTIFLUX', '流量计：OPTIFLUX 电磁流量计。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'OPTIFLUX') x);
UPDATE `product_series` SET `description` = '流量计：OPTIMASS 质量流量计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'OPTIMASS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'OPTIMASS', '流量计：OPTIMASS 质量流量计。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'OPTIMASS') x);
UPDATE `product_series` SET `description` = '物位计：OPTIWAVE 雷达物位计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'OPTIWAVE' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'OPTIWAVE', '物位计：OPTIWAVE 雷达物位计。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'OPTIWAVE') x);
UPDATE `product_series` SET `description` = '流量计：H250 金属管浮子流量计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H250' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'H250', '流量计：H250 金属管浮子流量计。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H250') x);

-- Heidenhain
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Heidenhain' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '编码器：ERN 增量式旋转编码器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ERN' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ERN', '编码器：ERN 增量式旋转编码器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ERN') x);
UPDATE `product_series` SET `description` = '编码器：ECN、EQN 绝对式旋转编码器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ECN' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ECN', '编码器：ECN、EQN 绝对式旋转编码器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ECN') x);
UPDATE `product_series` SET `description` = '光栅尺：LS 系列封闭式直线光栅尺。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LS', '光栅尺：LS 系列封闭式直线光栅尺。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LS') x);
UPDATE `product_series` SET `description` = '数控系统：TNC 系列（TNC 640 等）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TNC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TNC', '数控系统：TNC 系列（TNC 640 等）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TNC') x);

-- Kübler
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Kübler' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '编码器：Sendix 增量式与绝对式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Sendix' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Sendix', '编码器：Sendix 增量式与绝对式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Sendix') x);
UPDATE `product_series` SET `description` = '计数器与显示器：Codix 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Codix' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Codix', '计数器与显示器：Codix 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Codix') x);

-- POSITAL
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'POSITAL' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '编码器：IXARC 绝对值编码器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IXARC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IXARC', '编码器：IXARC 绝对值编码器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IXARC') x);
UPDATE `product_series` SET `description` = '倾角传感器：TILTIX 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TILTIX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TILTIX', '倾角传感器：TILTIX 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TILTIX') x);
UPDATE `product_series` SET `description` = '拉线位移传感器：LINARIX 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LINARIX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LINARIX', '拉线位移传感器：LINARIX 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LINARIX') x);

-- Moeller
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Moeller' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '接触器：DILM 系列（现属 Eaton）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DILM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DILM', '接触器：DILM 系列（现属 Eaton）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DILM') x);
UPDATE `product_series` SET `description` = '电动机保护断路器：PKZM0、PKZM4（现属 Eaton）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PKZM0' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PKZM0', '电动机保护断路器：PKZM0、PKZM4（现属 Eaton）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PKZM0') x);
UPDATE `product_series` SET `description` = '控制继电器：easy500、easy700、easy800 等（现属 Eaton）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'easy' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'easy', '控制继电器：easy500、easy700、easy800 等（现属 Eaton）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'easy') x);

-- Eaton
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Eaton' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '控制继电器：easyE4 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'easyE4' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'easyE4', '控制继电器：easyE4 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'easyE4') x);
UPDATE `product_series` SET `description` = '触摸屏：XV100、XV300 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XV' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XV', '触摸屏：XV100、XV300 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XV') x);
UPDATE `product_series` SET `description` = '变频器：PowerXL DC1、DG1、DA1。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerXL' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerXL', '变频器：PowerXL DC1、DG1、DA1。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerXL') x);
UPDATE `product_series` SET `description` = '熔断器：Bussmann 系列熔断器与熔断器座。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Bussmann' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Bussmann', '熔断器：Bussmann 系列熔断器与熔断器座。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Bussmann') x);

-- Rittal
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Rittal' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '机柜空调：Blue e、Blue e+ 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Blue e' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Blue e', '机柜空调：Blue e、Blue e+ 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Blue e') x);
UPDATE `product_series` SET `description` = '机柜风扇：TopTherm 过滤风扇。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TopTherm' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TopTherm', '机柜风扇：TopTherm 过滤风扇。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TopTherm') x);

-- Murrelektronik
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Murrelektronik' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '导轨电源：Emparro 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Emparro' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Emparro', '导轨电源：Emparro 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Emparro') x);
UPDATE `product_series` SET `description` = '电源分配：MICO 智能电流分配模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MICO' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MICO', '电源分配：MICO 智能电流分配模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MICO') x);
UPDATE `product_series` SET `description` = '分布式 I/O：Cube67 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Cube67' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Cube67', '分布式 I/O：Cube67 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Cube67') x);

-- Control Techniques
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Control Techniques' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：Unidrive M 系列（M200–M700）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Unidrive M' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Unidrive M', '变频器：Unidrive M 系列（M200–M700）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Unidrive M') x);
UPDATE `product_series` SET `description` = '老款变频器：Unidrive SP，多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Unidrive SP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Unidrive SP', '老款变频器：Unidrive SP，多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Unidrive SP') x);
UPDATE `product_series` SET `description` = '变频器：Commander C200、C300。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Commander C' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Commander C', '变频器：Commander C200、C300。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Commander C') x);
UPDATE `product_series` SET `description` = '伺服驱动：Digitax HD 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Digitax HD' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Digitax HD', '伺服驱动：Digitax HD 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Digitax HD') x);
UPDATE `product_series` SET `description` = '直流调速器：Mentor MP。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Mentor MP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Mentor MP', '直流调速器：Mentor MP。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Mentor MP') x);

-- Allen-Bradley
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Allen-Bradley' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：Micro810、Micro820、Micro850、Micro870。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro800 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Micro800 Series', '小型 PLC：Micro810、Micro820、Micro850、Micro870。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro800 Series') x);
UPDATE `product_series` SET `description` = '小型 PLC：MicroLogix 1000、1100、1400、1500。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micrologix Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Micrologix Series', '小型 PLC：MicroLogix 1000、1100、1400、1500。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micrologix Series') x);
UPDATE `product_series` SET `description` = '中型 PLC：CompactLogix 5370、5380 与 1769 I/O。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Compactlogix Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Compactlogix Series', '中型 PLC：CompactLogix 5370、5380 与 1769 I/O。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Compactlogix Series') x);
UPDATE `product_series` SET `description` = '大型 PLC：ControlLogix 1756 控制器与 I/O。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Controllogix Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Controllogix Series', '大型 PLC：ControlLogix 1756 控制器与 I/O。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Controllogix Series') x);
UPDATE `product_series` SET `description` = '安全控制器：GuardLogix、Compact GuardLogix。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GuardLogix' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GuardLogix', '安全控制器：GuardLogix、Compact GuardLogix。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GuardLogix') x);
UPDATE `product_series` SET `description` = '老款 PLC：SLC 500（1747/1746），多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SLC-500 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SLC-500 Series', '老款 PLC：SLC 500（1747/1746），多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SLC-500 Series') x);
UPDATE `product_series` SET `description` = '老款大型 PLC：PLC-5（1771/1785）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PLC-5 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PLC-5 Series', '老款大型 PLC：PLC-5（1771/1785）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PLC-5 Series') x);
UPDATE `product_series` SET `description` = '分布式 I/O：FLEX I/O（1794）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Flex I/O' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Flex I/O', '分布式 I/O：FLEX I/O（1794）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Flex I/O') x);
UPDATE `product_series` SET `description` = '分布式 I/O：POINT I/O（1734）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'POINT I/O' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'POINT I/O', '分布式 I/O：POINT I/O（1734）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'POINT I/O') x);
UPDATE `product_series` SET `description` = '触摸屏：PanelView 800（2711R）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView 800 Graphic Terminals' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PanelView 800 Graphic Terminals', '触摸屏：PanelView 800（2711R）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView 800 Graphic Terminals') x);
UPDATE `product_series` SET `description` = '老款触摸屏：PanelView Component / Compact。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView Compact Graphic Terminals' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PanelView Compact Graphic Terminals', '老款触摸屏：PanelView Component / Compact。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView Compact Graphic Terminals') x);
UPDATE `product_series` SET `description` = '触摸屏：PanelView Plus 6 Compact（2711PC）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView Plus 6 Compact Graphic Terminals' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PanelView Plus 6 Compact Graphic Terminals', '触摸屏：PanelView Plus 6 Compact（2711PC）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView Plus 6 Compact Graphic Terminals') x);
UPDATE `product_series` SET `description` = '触摸屏：PanelView Plus 6（2711P），存量大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView Plus 6 Graphic Terminals' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PanelView Plus 6 Graphic Terminals', '触摸屏：PanelView Plus 6（2711P），存量大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView Plus 6 Graphic Terminals') x);
UPDATE `product_series` SET `description` = '触摸屏：PanelView Plus 7（2711P）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView Plus 7 Graphic Terminals' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PanelView Plus 7 Graphic Terminals', '触摸屏：PanelView Plus 7（2711P）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView Plus 7 Graphic Terminals') x);
UPDATE `product_series` SET `description` = '触摸屏：PanelView 5310、5510（2713P）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView 5000 Graphic Terminals' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PanelView 5000 Graphic Terminals', '触摸屏：PanelView 5310、5510（2713P）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PanelView 5000 Graphic Terminals') x);
UPDATE `product_series` SET `description` = '工业以太网交换机：Stratix 2000、5700 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Stratix' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Stratix', '工业以太网交换机：Stratix 2000、5700 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Stratix') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 4 微型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 4' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 4', '变频器：PowerFlex 4 微型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 4') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 4M 微型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 4M' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 4M', '变频器：PowerFlex 4M 微型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 4M') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 40 紧凑型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 40' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 40', '变频器：PowerFlex 40 紧凑型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 40') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 40P，带位置控制。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 40P' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 40P', '变频器：PowerFlex 40P，带位置控制。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 40P') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 400 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 400' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 400', '变频器：PowerFlex 400 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 400') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 523 紧凑型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 523' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 523', '变频器：PowerFlex 523 紧凑型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 523') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 525 紧凑型，带以太网。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 525' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 525', '变频器：PowerFlex 525 紧凑型，带以太网。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 525') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 527，配 Logix 运动控制。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 527' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 527', '变频器：PowerFlex 527，配 Logix 运动控制。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 527') x);
UPDATE `product_series` SET `description` = '老款变频器：PowerFlex 70 / 700，存量大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 700' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 700', '老款变频器：PowerFlex 70 / 700，存量大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 700') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 753 架构型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 753' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 753', '变频器：PowerFlex 753 架构型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 753') x);
UPDATE `product_series` SET `description` = '变频器：PowerFlex 755 架构型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 755' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PowerFlex 755', '变频器：PowerFlex 755 架构型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PowerFlex 755') x);
UPDATE `product_series` SET `description` = '伺服驱动：Kinetix 5500。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Kinetix 5500' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Kinetix 5500', '伺服驱动：Kinetix 5500。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Kinetix 5500') x);
UPDATE `product_series` SET `description` = '伺服驱动：Kinetix 5700 多轴。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Kinetix 5700' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Kinetix 5700', '伺服驱动：Kinetix 5700 多轴。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Kinetix 5700') x);
UPDATE `product_series` SET `description` = '接触器：Bulletin 100-C。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '100-C' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '100-C', '接触器：Bulletin 100-C。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '100-C') x);
UPDATE `product_series` SET `description` = '电动机保护断路器：Bulletin 140M。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '140M' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '140M', '电动机保护断路器：Bulletin 140M。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '140M') x);
UPDATE `product_series` SET `description` = '导轨电源：Bulletin 1606。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '1606' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '1606', '导轨电源：Bulletin 1606。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '1606') x);
UPDATE `product_series` SET `description` = '安全继电器：Guardmaster 440R、GSR 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Guardmaster' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Guardmaster', '安全继电器：Guardmaster 440R、GSR 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Guardmaster') x);

-- Honeywell
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Honeywell' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '限位与微动开关：MICRO SWITCH 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MICRO SWITCH' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MICRO SWITCH', '限位与微动开关：MICRO SWITCH 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MICRO SWITCH') x);
UPDATE `product_series` SET `description` = '变送器：SmartLine STG、STD 压力与差压变送器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SmartLine' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SmartLine', '变送器：SmartLine STG、STD 压力与差压变送器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SmartLine') x);
UPDATE `product_series` SET `description` = '调节器：UDC2500、UDC3200 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UDC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'UDC', '调节器：UDC2500、UDC3200 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UDC') x);

-- Emerson
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Emerson' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '压力变送器：Rosemount 3051 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Rosemount 3051' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Rosemount 3051', '压力变送器：Rosemount 3051 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Rosemount 3051') x);
UPDATE `product_series` SET `description` = '流量计：Micro Motion 科里奥利质量流量计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro Motion' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Micro Motion', '流量计：Micro Motion 科里奥利质量流量计。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Micro Motion') x);
UPDATE `product_series` SET `description` = '阀门定位器：Fisher FIELDVUE DVC6200。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Fisher DVC6200' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Fisher DVC6200', '阀门定位器：Fisher FIELDVUE DVC6200。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Fisher DVC6200') x);
UPDATE `product_series` SET `description` = '电磁阀：ASCO 8210 等系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASCO' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ASCO', '电磁阀：ASCO 8210 等系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASCO') x);

-- Parker
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Parker' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '伺服驱动：Compax3 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Compax3' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Compax3', '伺服驱动：Compax3 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Compax3') x);
UPDATE `product_series` SET `description` = '变频器：AC10、AC890（原 SSD Drives）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AC10' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AC10', '变频器：AC10、AC890（原 SSD Drives）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AC10') x);
UPDATE `product_series` SET `description` = '液压阀：D1VW 电磁换向阀。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'D1VW' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'D1VW', '液压阀：D1VW 电磁换向阀。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'D1VW') x);
UPDATE `product_series` SET `description` = '液压泵：PV 变量柱塞泵。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PV' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PV', '液压泵：PV 变量柱塞泵。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PV') x);
UPDATE `product_series` SET `description` = '气缸：P1D 标准气缸。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'P1D' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'P1D', '气缸：P1D 标准气缸。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'P1D') x);

-- Banner
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Banner' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '光电开关：WORLD-BEAM QS18。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'QS18' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'QS18', '光电开关：WORLD-BEAM QS18。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'QS18') x);
UPDATE `product_series` SET `description` = '激光测距与检测：Q4X 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Q4X' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Q4X', '激光测距与检测：Q4X 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Q4X') x);
UPDATE `product_series` SET `description` = '安全光幕：EZ-SCREEN 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EZ-SCREEN' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EZ-SCREEN', '安全光幕：EZ-SCREEN 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EZ-SCREEN') x);
UPDATE `product_series` SET `description` = '信号灯：TL50 塔灯。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TL50' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TL50', '信号灯：TL50 塔灯。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TL50') x);

-- Cognex
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Cognex' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '视觉系统：In-Sight 智能相机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'In-Sight' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'In-Sight', '视觉系统：In-Sight 智能相机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'In-Sight') x);
UPDATE `product_series` SET `description` = '读码器：DataMan 固定式与手持式读码器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DataMan' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DataMan', '读码器：DataMan 固定式与手持式读码器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DataMan') x);

-- Red Lion
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Red Lion' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '触摸屏与控制器：Graphite HMI。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Graphite' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Graphite', '触摸屏与控制器：Graphite HMI。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Graphite') x);
UPDATE `product_series` SET `description` = '老款触摸屏：G3 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G3' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'G3', '老款触摸屏：G3 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G3') x);
UPDATE `product_series` SET `description` = '面板表：PAX 系列数显表。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PAX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PAX', '面板表：PAX 系列数显表。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PAX') x);
UPDATE `product_series` SET `description` = '工业以太网交换机：N-Tron 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'N-Tron' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'N-Tron', '工业以太网交换机：N-Tron 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'N-Tron') x);

-- MAC
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'MAC' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '电磁阀：35 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '35 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '35 Series', '电磁阀：35 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '35 Series') x);
UPDATE `product_series` SET `description` = '电磁阀：45 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '45 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '45 Series', '电磁阀：45 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '45 Series') x);

-- Mitsubishi Electric
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Mitsubishi Electric' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：MELSEC FX3U、FX3G、FX3S 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FX Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FX Series', '小型 PLC：MELSEC FX3U、FX3G、FX3S 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FX Series') x);
UPDATE `product_series` SET `description` = '小型 PLC：MELSEC iQ-F（FX5U、FX5UC 等）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iQ-F Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'iQ-F Series', '小型 PLC：MELSEC iQ-F（FX5U、FX5UC 等）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iQ-F Series') x);
UPDATE `product_series` SET `description` = '中型 PLC：MELSEC-L 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'L Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'L Series', '中型 PLC：MELSEC-L 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'L Series') x);
UPDATE `product_series` SET `description` = '中大型 PLC：MELSEC-Q 系列 CPU 与模块，存量大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Q Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Q Series', '中大型 PLC：MELSEC-Q 系列 CPU 与模块，存量大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Q Series') x);
UPDATE `product_series` SET `description` = '大型 PLC：MELSEC iQ-R 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iQ-R Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'iQ-R Series', '大型 PLC：MELSEC iQ-R 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iQ-R Series') x);
UPDATE `product_series` SET `description` = '现场网络：CC-Link 主站、远程 I/O 与网络模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CC-Link' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CC-Link', '现场网络：CC-Link 主站、远程 I/O 与网络模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CC-Link') x);
UPDATE `product_series` SET `description` = '现场网络：CC-Link/LT 省配线网络模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CC-Link/LT' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CC-Link/LT', '现场网络：CC-Link/LT 省配线网络模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CC-Link/LT') x);
UPDATE `product_series` SET `description` = '触摸屏：GOT1000（GT11、GT15、GT16）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GOT1000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GOT1000', '触摸屏：GOT1000（GT11、GT15、GT16）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GOT1000') x);
UPDATE `product_series` SET `description` = '触摸屏：GOT2000（GT21、GT25、GT27）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GOT2000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GOT2000', '触摸屏：GOT2000（GT21、GT25、GT27）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GOT2000') x);
UPDATE `product_series` SET `description` = '变频器：FR-D700 紧凑型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-D700' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FR-D700', '变频器：FR-D700 紧凑型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-D700') x);
UPDATE `product_series` SET `description` = '变频器：FR-E700 经济型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-E700' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FR-E700', '变频器：FR-E700 经济型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-E700') x);
UPDATE `product_series` SET `description` = '变频器：FR-E800，替代 FR-E700。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-E800' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FR-E800', '变频器：FR-E800，替代 FR-E700。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-E800') x);
UPDATE `product_series` SET `description` = '老款变频器：FR-A700 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-A700' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FR-A700', '老款变频器：FR-A700 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-A700') x);
UPDATE `product_series` SET `description` = '变频器：FR-A800 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-A800' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FR-A800', '变频器：FR-A800 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-A800') x);
UPDATE `product_series` SET `description` = '变频器：FR-F800 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-F800' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FR-F800', '变频器：FR-F800 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FR-F800') x);
UPDATE `product_series` SET `description` = '伺服：MELSERVO-JE 经济型驱动器与 HG-KN/SN 电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'JE Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'JE Series', '伺服：MELSERVO-JE 经济型驱动器与 HG-KN/SN 电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'JE Series') x);
UPDATE `product_series` SET `description` = '伺服：MELSERVO-J3 驱动器与 HF 电机，存量大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'J3 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'J3 Series', '伺服：MELSERVO-J3 驱动器与 HF 电机，存量大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'J3 Series') x);
UPDATE `product_series` SET `description` = '伺服：MELSERVO-J4 驱动器与 HG 电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'J4 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'J4 Series', '伺服：MELSERVO-J4 驱动器与 HG 电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'J4 Series') x);
UPDATE `product_series` SET `description` = '伺服：MELSERVO-J5 驱动器与 HK 电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'J5 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'J5 Series', '伺服：MELSERVO-J5 驱动器与 HK 电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'J5 Series') x);
UPDATE `product_series` SET `description` = '数控系统：M800、M80 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'M800/M80' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'M800/M80', '数控系统：M800、M80 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'M800/M80') x);

-- OMRON
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'OMRON' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：CP1E、CP1H、CP1L、CP2E。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CP Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CP Series', '小型 PLC：CP1E、CP1H、CP1L、CP2E。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CP Series') x);
UPDATE `product_series` SET `description` = '中型 PLC：CJ1、CJ2 CPU 与模块。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CJ Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CJ Series', '中型 PLC：CJ1、CJ2 CPU 与模块。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CJ Series') x);
UPDATE `product_series` SET `description` = '大型 PLC：CS1 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CS Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CS Series', '大型 PLC：CS1 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CS Series') x);
UPDATE `product_series` SET `description` = '机器自动化控制器：NJ 系列（Sysmac）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NJ Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NJ Series', '机器自动化控制器：NJ 系列（Sysmac）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NJ Series') x);
UPDATE `product_series` SET `description` = '机器自动化控制器与 I/O：NX1P、NX102 及 NX I/O。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NX Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NX Series', '机器自动化控制器与 I/O：NX1P、NX102 及 NX I/O。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NX Series') x);
UPDATE `product_series` SET `description` = '可编程继电器：ZEN 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Zen Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Zen Series', '可编程继电器：ZEN 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Zen Series') x);
UPDATE `product_series` SET `description` = '远程 I/O：DRT 系列 DeviceNet 远程终端。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Remote I/O DRT' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Remote I/O DRT', '远程 I/O：DRT 系列 DeviceNet 远程终端。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Remote I/O DRT') x);
UPDATE `product_series` SET `description` = '触摸屏：NS 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NS Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NS Series', '触摸屏：NS 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NS Series') x);
UPDATE `product_series` SET `description` = '触摸屏：NB 系列经济型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NB Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NB Series', '触摸屏：NB 系列经济型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NB Series') x);
UPDATE `product_series` SET `description` = '触摸屏：NA 系列（Sysmac）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NA Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NA Series', '触摸屏：NA 系列（Sysmac）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NA Series') x);
UPDATE `product_series` SET `description` = '变频器：3G3MX2。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MX2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MX2', '变频器：3G3MX2。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MX2') x);
UPDATE `product_series` SET `description` = '伺服：G5 系列（R88D-K 驱动器、R88M-K 电机）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G5 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'G5 Series', '伺服：G5 系列（R88D-K 驱动器、R88M-K 电机）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G5 Series') x);
UPDATE `product_series` SET `description` = '伺服：1S 系列（R88D-1S）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '1S Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '1S Series', '伺服：1S 系列（R88D-1S）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '1S Series') x);
UPDATE `product_series` SET `description` = '接近开关：E2E 电感式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E2E' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'E2E', '接近开关：E2E 电感式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E2E') x);
UPDATE `product_series` SET `description` = '光电开关：E3Z 小型光电。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E3Z' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'E3Z', '光电开关：E3Z 小型光电。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E3Z') x);
UPDATE `product_series` SET `description` = '编码器：E6B2、E6C2 增量式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E6B2/E6C2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'E6B2/E6C2', '编码器：E6B2、E6C2 增量式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E6B2/E6C2') x);
UPDATE `product_series` SET `description` = '温控器：E5CC、E5EC 等 E5 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E5CC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'E5CC', '温控器：E5CC、E5EC 等 E5 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E5CC') x);
UPDATE `product_series` SET `description` = '时间继电器：H3CR 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H3CR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'H3CR', '时间继电器：H3CR 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H3CR') x);
UPDATE `product_series` SET `description` = '中间继电器：MY 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MY' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MY', '中间继电器：MY 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MY') x);
UPDATE `product_series` SET `description` = '固态继电器：G3NA 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G3NA' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'G3NA', '固态继电器：G3NA 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'G3NA') x);
UPDATE `product_series` SET `description` = '导轨电源：S8VK 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S8VK' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S8VK', '导轨电源：S8VK 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S8VK') x);

-- Yaskawa
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Yaskawa' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：J1000 小型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'J1000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'J1000', '变频器：J1000 小型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'J1000') x);
UPDATE `product_series` SET `description` = '变频器：V1000 紧凑矢量型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'V1000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'V1000', '变频器：V1000 紧凑矢量型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'V1000') x);
UPDATE `product_series` SET `description` = '变频器：A1000 高性能矢量型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A1000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'A1000', '变频器：A1000 高性能矢量型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A1000') x);
UPDATE `product_series` SET `description` = '变频器：E1000 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E1000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'E1000', '变频器：E1000 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E1000') x);
UPDATE `product_series` SET `description` = '变频器：H1000 重载型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H1000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'H1000', '变频器：H1000 重载型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H1000') x);
UPDATE `product_series` SET `description` = '变频器：GA500 小型通用型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GA500' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GA500', '变频器：GA500 小型通用型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GA500') x);
UPDATE `product_series` SET `description` = '变频器：GA700，替代 A1000。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GA700' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GA700', '变频器：GA700，替代 A1000。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GA700') x);
UPDATE `product_series` SET `description` = '老款变频器：Varispeed G7、F7，多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Varispeed G7/F7' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Varispeed G7/F7', '老款变频器：Varispeed G7、F7，多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Varispeed G7/F7') x);
UPDATE `product_series` SET `description` = '伺服：Σ-V（SGDV 驱动器、SGMJV 等电机）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Sigma-5' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Sigma-5', '伺服：Σ-V（SGDV 驱动器、SGMJV 等电机）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Sigma-5') x);
UPDATE `product_series` SET `description` = '伺服：Σ-7（SGD7S 驱动器、SGM7 电机）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Sigma 7 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Sigma 7 Series', '伺服：Σ-7（SGD7S 驱动器、SGM7 电机）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Sigma 7 Series') x);
UPDATE `product_series` SET `description` = '运动控制器：MP3000 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MP3000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MP3000', '运动控制器：MP3000 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MP3000') x);
UPDATE `product_series` SET `description` = '机器人：MOTOMAN 控制柜 YRC1000、DX200、DX100 及示教器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'YRC1000/DX200' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'YRC1000/DX200', '机器人：MOTOMAN 控制柜 YRC1000、DX200、DX100 及示教器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'YRC1000/DX200') x);

-- Fanuc
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Fanuc' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '数控系统：Series 0i-F / 0i-D 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Series 0i' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Series 0i', '数控系统：Series 0i-F / 0i-D 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Series 0i') x);
UPDATE `product_series` SET `description` = '伺服：αi 系列伺服放大器（SVM）与电源模块（PSM）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'αi 伺服放大器' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'αi 伺服放大器', '伺服：αi 系列伺服放大器（SVM）与电源模块（PSM）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'αi 伺服放大器') x);
UPDATE `product_series` SET `description` = '伺服：βi 系列伺服放大器与电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'βi 伺服' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'βi 伺服', '伺服：βi 系列伺服放大器与电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'βi 伺服') x);
UPDATE `product_series` SET `description` = '主轴：αi 主轴放大器（SPM）与主轴电机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'αi 主轴' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'αi 主轴', '主轴：αi 主轴放大器（SPM）与主轴电机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'αi 主轴') x);
UPDATE `product_series` SET `description` = '机器人：R-30iA / R-30iB 控制柜。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'R-30iB' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'R-30iB', '机器人：R-30iA / R-30iB 控制柜。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'R-30iB') x);
UPDATE `product_series` SET `description` = '示教器：iPendant 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iPendant' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'iPendant', '示教器：iPendant 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iPendant') x);

-- Panasonic
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Panasonic' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：FP0R、FP0H 等 FP 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FPOR/FP Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FPOR/FP Series', '小型 PLC：FP0R、FP0H 等 FP 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FPOR/FP Series') x);
UPDATE `product_series` SET `description` = '小型 PLC：FP-X、FP-XH 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FP-X Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FP-X Series', '小型 PLC：FP-X、FP-XH 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FP-X Series') x);
UPDATE `product_series` SET `description` = '中型 PLC：FP7 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FP7 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FP7 Series', '中型 PLC：FP7 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FP7 Series') x);
UPDATE `product_series` SET `description` = '老款 PLC：FP2SH。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FP-2SH Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FP-2SH Series', '老款 PLC：FP2SH。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FP-2SH Series') x);
UPDATE `product_series` SET `description` = '触摸屏：GT02 小型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GT 02' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GT 02', '触摸屏：GT02 小型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GT 02') x);
UPDATE `product_series` SET `description` = '触摸屏：GT05。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GT 05' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GT 05', '触摸屏：GT05。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GT 05') x);
UPDATE `product_series` SET `description` = '触摸屏：GT12。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GT 12' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GT 12', '触摸屏：GT12。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GT 12') x);
UPDATE `product_series` SET `description` = '触摸屏：GT32。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GT 32' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GT 32', '触摸屏：GT32。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GT 32') x);
UPDATE `product_series` SET `description` = '伺服：MINAS A5 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A5 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'A5 Series', '伺服：MINAS A5 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A5 Series') x);
UPDATE `product_series` SET `description` = '伺服：MINAS A6 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A6 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'A6 Series', '伺服：MINAS A6 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A6 Series') x);
UPDATE `product_series` SET `description` = '光电开关：CX-400 系列（原 SUNX）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CX-400' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CX-400', '光电开关：CX-400 系列（原 SUNX）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CX-400') x);

-- Keyence
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Keyence' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = 'PLC：KV-8000、KV-5500、KV Nano 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'KV' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'KV', 'PLC：KV-8000、KV-5500、KV Nano 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'KV') x);
UPDATE `product_series` SET `description` = '光纤放大器：FS-N 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FS-N' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FS-N', '光纤放大器：FS-N 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FS-N') x);
UPDATE `product_series` SET `description` = '激光传感器：LV-N 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LV-N' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LV-N', '激光传感器：LV-N 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LV-N') x);
UPDATE `product_series` SET `description` = '激光位移传感器：IL 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IL' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IL', '激光位移传感器：IL 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IL') x);
UPDATE `product_series` SET `description` = '读码器：SR-1000、SR-X 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SR', '读码器：SR-1000、SR-X 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SR') x);
UPDATE `product_series` SET `description` = '视觉系统：CV-X、XG-X 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CV-X' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CV-X', '视觉系统：CV-X、XG-X 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CV-X') x);

-- Fuji Electric
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Fuji Electric' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：FRENIC-Mini（C 系列）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MINI[C]' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MINI[C]', '变频器：FRENIC-Mini（C 系列）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MINI[C]') x);
UPDATE `product_series` SET `description` = '变频器：FRENIC-Multi（E 系列）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MULTI[E]' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MULTI[E]', '变频器：FRENIC-Multi（E 系列）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MULTI[E]') x);
UPDATE `product_series` SET `description` = '变频器：FRENIC-MEGA（G 系列）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MEGA[G]' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MEGA[G]', '变频器：FRENIC-MEGA（G 系列）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MEGA[G]') x);
UPDATE `product_series` SET `description` = '变频器：FRENIC-Eco（F 系列）风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ECO[F]' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ECO[F]', '变频器：FRENIC-Eco（F 系列）风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ECO[F]') x);
UPDATE `product_series` SET `description` = '变频器：FRENIC-HVAC（AR 系列）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HVAC[AR]' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'HVAC[AR]', '变频器：FRENIC-HVAC（AR 系列）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HVAC[AR]') x);
UPDATE `product_series` SET `description` = '变频器：FRENIC-Ace（主营产品表写作 AEC）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AEC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AEC', '变频器：FRENIC-Ace（主营产品表写作 AEC）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AEC') x);
UPDATE `product_series` SET `description` = '伺服：ALPHA5 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ALPHA5' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ALPHA5', '伺服：ALPHA5 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ALPHA5') x);
UPDATE `product_series` SET `description` = 'PLC：MICREX-SX（SPH）系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MICREX-SX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MICREX-SX', 'PLC：MICREX-SX（SPH）系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MICREX-SX') x);

-- Hitachi
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Hitachi' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：WJ200 紧凑型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'WJ200' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'WJ200', '变频器：WJ200 紧凑型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'WJ200') x);
UPDATE `product_series` SET `description` = '变频器：WJ200N。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'WJ200N' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'WJ200N', '变频器：WJ200N。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'WJ200N') x);
UPDATE `product_series` SET `description` = '变频器：SJ700 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SJ700' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SJ700', '变频器：SJ700 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SJ700') x);
UPDATE `product_series` SET `description` = '变频器：SJ-P1，替代 SJ700。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SJ-P1' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SJ-P1', '变频器：SJ-P1，替代 SJ700。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SJ-P1') x);
UPDATE `product_series` SET `description` = '变频器：NJ600B 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NJ600B' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NJ600B', '变频器：NJ600B 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NJ600B') x);

-- Toshiba
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Toshiba' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：TOSVERT VF-nC3 小型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFNC-3' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VFNC-3', '变频器：TOSVERT VF-nC3 小型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFNC-3') x);
UPDATE `product_series` SET `description` = '变频器：TOSVERT VF-S15 通用型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFS-15' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VFS-15', '变频器：TOSVERT VF-S15 通用型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFS-15') x);
UPDATE `product_series` SET `description` = '变频器：TOSVERT VF-AS1 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AS-1' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AS-1', '变频器：TOSVERT VF-AS1 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AS-1') x);
UPDATE `product_series` SET `description` = '变频器：TOSVERT VF-PS1 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PS-1' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PS-1', '变频器：TOSVERT VF-PS1 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PS-1') x);

-- Pro-face
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Pro-face' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '触摸屏：GP4000 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GP4000 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GP4000 Series', '触摸屏：GP4000 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GP4000 Series') x);
UPDATE `product_series` SET `description` = '老款触摸屏：GP3000 系列，多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GP3000 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GP3000 Series', '老款触摸屏：GP3000 系列，多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GP3000 Series') x);
UPDATE `product_series` SET `description` = '触摸屏与工控机：SP5000 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SP5000 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SP5000 Series', '触摸屏与工控机：SP5000 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SP5000 Series') x);
UPDATE `product_series` SET `description` = '触摸屏：ST6000 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ST6000 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ST6000 Series', '触摸屏：ST6000 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ST6000 Series') x);
UPDATE `product_series` SET `description` = '工业显示器：FP5000 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FP5000 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FP5000 Series', '工业显示器：FP5000 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FP5000 Series') x);
UPDATE `product_series` SET `description` = '一体式触摸屏：LT4000M（HMI 带 I/O）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LT4000M Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LT4000M Series', '一体式触摸屏：LT4000M（HMI 带 I/O）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LT4000M Series') x);
UPDATE `product_series` SET `description` = '主营产品表中的 Pro-face 系列，具体型号范围待核实。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LM48 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LM48 Series', '主营产品表中的 Pro-face 系列，具体型号范围待核实。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LM48 Series') x);

-- Koyo
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Koyo' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：DirectLOGIC DL05、DL06 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DirectLOGIC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DirectLOGIC', '小型 PLC：DirectLOGIC DL05、DL06 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DirectLOGIC') x);
UPDATE `product_series` SET `description` = '编码器：TRD 系列增量式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TRD' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TRD', '编码器：TRD 系列增量式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TRD') x);

-- SMC
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'SMC' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '电磁阀：SY 系列五通电磁阀。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SY' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SY', '电磁阀：SY 系列五通电磁阀。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SY') x);
UPDATE `product_series` SET `description` = '气缸：CQ2 薄型气缸。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CQ2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CQ2', '气缸：CQ2 薄型气缸。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CQ2') x);
UPDATE `product_series` SET `description` = '气缸：CP96 标准气缸（ISO 15552）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CP96' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CP96', '气缸：CP96 标准气缸（ISO 15552）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CP96') x);
UPDATE `product_series` SET `description` = '气源处理：AC 系列三联件与 AW、AR 等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AC', '气源处理：AC 系列三联件与 AW、AR 等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AC') x);
UPDATE `product_series` SET `description` = '真空吸盘：ZP 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ZP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ZP', '真空吸盘：ZP 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ZP') x);
UPDATE `product_series` SET `description` = '快插接头：KQ2 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'KQ2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'KQ2', '快插接头：KQ2 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'KQ2') x);
UPDATE `product_series` SET `description` = '电气比例阀：ITV 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ITV' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ITV', '电气比例阀：ITV 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ITV') x);

-- CKD
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'CKD' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '电磁阀：4GB 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '4GB' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '4GB', '电磁阀：4GB 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '4GB') x);
UPDATE `product_series` SET `description` = '气缸：SCM 标准气缸。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SCM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SCM', '气缸：SCM 标准气缸。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SCM') x);
UPDATE `product_series` SET `description` = '气缸：SSD2 超薄气缸。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SSD2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SSD2', '气缸：SSD2 超薄气缸。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SSD2') x);

-- Azbil
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Azbil' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '调节器：SDC 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SDC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SDC', '调节器：SDC 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SDC') x);
UPDATE `product_series` SET `description` = '光电开关：HPX 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HPX' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'HPX', '光电开关：HPX 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HPX') x);
UPDATE `product_series` SET `description` = '阀门定位器：AVP 智能阀门定位器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AVP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AVP', '阀门定位器：AVP 智能阀门定位器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AVP') x);

-- Oriental Motor
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Oriental Motor' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '步进电机：PKP 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PKP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PKP', '步进电机：PKP 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PKP') x);
UPDATE `product_series` SET `description` = '步进：AZ 系列（αSTEP）闭环步进电机与驱动器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AZ' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AZ', '步进：AZ 系列（αSTEP）闭环步进电机与驱动器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AZ') x);
UPDATE `product_series` SET `description` = '无刷电机：BLE2 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BLE2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BLE2', '无刷电机：BLE2 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BLE2') x);

-- Nidec
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Nidec' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '减速机：VRL、VRB 行星减速机（Nidec-Shimpo）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VRL/VRB' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VRL/VRB', '减速机：VRL、VRB 行星减速机（Nidec-Shimpo）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VRL/VRB') x);

-- IDEC
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'IDEC' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：MicroSmart FC6A。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC6A' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FC6A', '小型 PLC：MicroSmart FC6A。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FC6A') x);
UPDATE `product_series` SET `description` = '触摸屏：HG 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HG' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'HG', '触摸屏：HG 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HG') x);
UPDATE `product_series` SET `description` = '继电器：RJ 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'RJ' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'RJ', '继电器：RJ 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'RJ') x);
UPDATE `product_series` SET `description` = '按钮指示灯：HW 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HW' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'HW', '按钮指示灯：HW 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HW') x);

-- Hokuyo
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Hokuyo' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '安全激光扫描仪：UAM 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UAM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'UAM', '安全激光扫描仪：UAM 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UAM') x);
UPDATE `product_series` SET `description` = '激光测距扫描仪：UST 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UST' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'UST', '激光测距扫描仪：UST 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UST') x);

-- LS Electric
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'LS Electric' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '老款小型 PLC：MASTER-K K10S1、K80S、K120S 与 GLOFA GM7、GM7U。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MASTER-K/GLOFA(K10S1/K80S/K120S/GM7/GM7U)' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MASTER-K/GLOFA(K10S1/K80S/K120S/GM7/GM7U)', '老款小型 PLC：MASTER-K K10S1、K80S、K120S 与 GLOFA GM7、GM7U。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MASTER-K/GLOFA(K10S1/K80S/K120S/GM7/GM7U)') x);
UPDATE `product_series` SET `description` = '老款中型 PLC：MASTER-K K200S、K300S 与 GLOFA GM6、GM4。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MASTER-K/GLOFA(K200S/K300S/GM6/GM4)' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MASTER-K/GLOFA(K200S/K300S/GM6/GM4)', '老款中型 PLC：MASTER-K K200S、K300S 与 GLOFA GM6、GM4。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MASTER-K/GLOFA(K200S/K300S/GM6/GM4)') x);
UPDATE `product_series` SET `description` = '小型 PLC：XGB 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XGB Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XGB Series', '小型 PLC：XGB 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XGB Series') x);
UPDATE `product_series` SET `description` = '中大型 PLC：XGT（XGK、XGI）系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XGT Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XGT Series', '中大型 PLC：XGT（XGK、XGI）系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XGT Series') x);
UPDATE `product_series` SET `description` = '触摸屏：XP 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XP Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XP Series', '触摸屏：XP 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XP Series') x);
UPDATE `product_series` SET `description` = '触摸屏：eXP 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'eXP Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'eXP Series', '触摸屏：eXP 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'eXP Series') x);
UPDATE `product_series` SET `description` = '触摸屏：iXP 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iXP Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'iXP Series', '触摸屏：iXP 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iXP Series') x);
UPDATE `product_series` SET `description` = '变频器：iE5、iC5 微型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IE5/IC5' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IE5/IC5', '变频器：iE5、iC5 微型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IE5/IC5') x);
UPDATE `product_series` SET `description` = '变频器：iG5A 通用型，存量大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IG5A' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IG5A', '变频器：iG5A 通用型，存量大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IG5A') x);
UPDATE `product_series` SET `description` = '变频器：iP5A 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IP5A' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IP5A', '变频器：iP5A 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IP5A') x);
UPDATE `product_series` SET `description` = '变频器：iS7 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IS7' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IS7', '变频器：iS7 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IS7') x);
UPDATE `product_series` SET `description` = '变频器：iV5 矢量型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IV5' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IV5', '变频器：iV5 矢量型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IV5') x);
UPDATE `product_series` SET `description` = '变频器：S100 系列，替代 iG5A。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S100' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'S100', '变频器：S100 系列，替代 iG5A。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S100') x);
UPDATE `product_series` SET `description` = '变频器：H100 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H100' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'H100', '变频器：H100 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H100') x);
UPDATE `product_series` SET `description` = '伺服：XDL-L7 系列驱动器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'L7' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'L7', '伺服：XDL-L7 系列驱动器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'L7') x);
UPDATE `product_series` SET `description` = '接触器：Metasol MC 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Metasol MC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Metasol MC', '接触器：Metasol MC 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Metasol MC') x);
UPDATE `product_series` SET `description` = '塑壳断路器：Metasol ABN、ABS。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Metasol ABN/ABS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'Metasol ABN/ABS', '塑壳断路器：Metasol ABN、ABS。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'Metasol ABN/ABS') x);

-- Autonics
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Autonics' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '接近开关：PR 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PR', '接近开关：PR 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PR') x);
UPDATE `product_series` SET `description` = '光电开关：BJ 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BJ' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'BJ', '光电开关：BJ 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'BJ') x);
UPDATE `product_series` SET `description` = '编码器：E40、E50 系列增量式。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E40/E50' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'E40/E50', '编码器：E40、E50 系列增量式。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E40/E50') x);
UPDATE `product_series` SET `description` = '温控器：TK 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TK' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TK', '温控器：TK 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TK') x);

-- Delta
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Delta' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：DVP-ES2、DVP-EX2 标准型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ES2/EX2 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ES2/EX2 Series', '小型 PLC：DVP-ES2、DVP-EX2 标准型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ES2/EX2 Series') x);
UPDATE `product_series` SET `description` = '小型 PLC：DVP-SS2、SA2、SX2 薄型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SS2/SA2/SX2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SS2/SA2/SX2', '小型 PLC：DVP-SS2、SA2、SX2 薄型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SS2/SA2/SX2') x);
UPDATE `product_series` SET `description` = '小型 PLC：DVP-SV2 高性能薄型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SV2 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SV2 Series', '小型 PLC：DVP-SV2 高性能薄型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SV2 Series') x);
UPDATE `product_series` SET `description` = '小型 PLC：DVP-EH3 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EH3 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EH3 Series', '小型 PLC：DVP-EH3 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EH3 Series') x);
UPDATE `product_series` SET `description` = '小型 PLC：DVP-EC3 经济型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EC3 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EC3 Series', '小型 PLC：DVP-EC3 经济型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EC3 Series') x);
UPDATE `product_series` SET `description` = '中型 PLC：AS300 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AS Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AS Series', '中型 PLC：AS300 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AS Series') x);
UPDATE `product_series` SET `description` = '中大型 PLC：AH500 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AH500 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AH500 Series', '中大型 PLC：AH500 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AH500 Series') x);
UPDATE `product_series` SET `description` = '触摸屏：DOP-100 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DOP-100 Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DOP-100 Series', '触摸屏：DOP-100 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DOP-100 Series') x);
UPDATE `product_series` SET `description` = '变频器：VFD-E 系列，存量大。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFD-E' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VFD-E', '变频器：VFD-E 系列，存量大。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFD-E') x);
UPDATE `product_series` SET `description` = '变频器：VFD-EL 简易型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFD-EL' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VFD-EL', '变频器：VFD-EL 简易型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFD-EL') x);
UPDATE `product_series` SET `description` = '老款变频器：VFD-B 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFD-B' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'VFD-B', '老款变频器：VFD-B 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'VFD-B') x);
UPDATE `product_series` SET `description` = '变频器：MS300 紧凑型，替代 VFD-E。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MS300' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MS300', '变频器：MS300 紧凑型，替代 VFD-E。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MS300') x);
UPDATE `product_series` SET `description` = '变频器：MH300 高性能紧凑型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MH300' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MH300', '变频器：MH300 高性能紧凑型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MH300') x);
UPDATE `product_series` SET `description` = '变频器：C2000 高性能矢量型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'C-2000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'C-2000', '变频器：C2000 高性能矢量型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'C-2000') x);
UPDATE `product_series` SET `description` = '变频器：CP2000 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CP-2000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CP-2000', '变频器：CP2000 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CP-2000') x);
UPDATE `product_series` SET `description` = '伺服：ASDA-A2 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASDA-A2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ASDA-A2', '伺服：ASDA-A2 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASDA-A2') x);
UPDATE `product_series` SET `description` = '伺服：ASDA-A3，替代 A2。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASDA-A3' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ASDA-A3', '伺服：ASDA-A3，替代 A2。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASDA-A3') x);
UPDATE `product_series` SET `description` = '伺服：ASDA-B2 标准型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASDA-B2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ASDA-B2', '伺服：ASDA-B2 标准型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASDA-B2') x);
UPDATE `product_series` SET `description` = '伺服：ASDA-B3，替代 B2。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASDA-B3' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ASDA-B3', '伺服：ASDA-B3，替代 B2。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ASDA-B3') x);
UPDATE `product_series` SET `description` = '伺服电机：ECMA 系列，配 ASDA-A2/B2。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ECMA' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ECMA', '伺服电机：ECMA 系列，配 ASDA-A2/B2。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ECMA') x);
UPDATE `product_series` s SET s.`deleted_at` = NOW(), s.`update_time` = NOW(), s.`update_by` = 'sys'
WHERE s.`tenant_id` = 0 AND s.`brand_id` = @b AND TRIM(s.`series_name`) = 'S7-1200' AND s.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `product` p WHERE p.`series_id` = s.`id` AND p.`deleted_at` IS NULL);
UPDATE `product_series` SET `status` = 0, `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'S7-1200' AND `deleted_at` IS NULL AND `status` = 1;

-- Weintek
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Weintek' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '触摸屏：eMT 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'eMT Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'eMT Series', '触摸屏：eMT 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'eMT Series') x);
UPDATE `product_series` SET `description` = '触摸屏：MT8000iE / MT6000iE 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iE Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'iE Series', '触摸屏：MT8000iE / MT6000iE 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iE Series') x);
UPDATE `product_series` SET `description` = '触摸屏：MT8000iP / MT6000iP 经济型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iP Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'iP Series', '触摸屏：MT8000iP / MT6000iP 经济型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'iP Series') x);
UPDATE `product_series` SET `description` = '触摸屏：cMT 系列（cMT X 等）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CMT Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'CMT Series', '触摸屏：cMT 系列（cMT X 等）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'CMT Series') x);
UPDATE `product_series` SET `description` = '老款触摸屏：MT8000i、MT6000i 系列，多为维修替换。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MT8000i/MT6000i' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MT8000i/MT6000i', '老款触摸屏：MT8000i、MT6000i 系列，多为维修替换。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MT8000i/MT6000i') x);

-- AirTAC
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'AirTAC' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '气缸：SC 标准气缸。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SC', '气缸：SC 标准气缸。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SC') x);
UPDATE `product_series` SET `description` = '气缸：MAL 铝合金迷你气缸。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MAL' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MAL', '气缸：MAL 铝合金迷你气缸。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MAL') x);
UPDATE `product_series` SET `description` = '电磁阀：4V100、4V200、4V300 等五通电磁阀。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '4V' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '4V', '电磁阀：4V100、4V200、4V300 等五通电磁阀。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '4V') x);
UPDATE `product_series` SET `description` = '气源处理：GFC 二联件、GFR 过滤减压阀等。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GFC/GFR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GFC/GFR', '气源处理：GFC 二联件、GFR 过滤减压阀等。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GFC/GFR') x);

-- Advantech
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Advantech' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '远程 I/O：ADAM-4000、ADAM-6000 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ADAM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ADAM', '远程 I/O：ADAM-4000、ADAM-6000 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ADAM') x);
UPDATE `product_series` SET `description` = '工控机：IPC-610 等机架式工控机。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IPC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IPC', '工控机：IPC-610 等机架式工控机。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IPC') x);
UPDATE `product_series` SET `description` = '嵌入式工控机：UNO 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UNO' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'UNO', '嵌入式工控机：UNO 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'UNO') x);
UPDATE `product_series` SET `description` = '平板工控机：PPC 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PPC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PPC', '平板工控机：PPC 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PPC') x);
UPDATE `product_series` SET `description` = '工业网络：EKI 交换机与串口服务器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EKI' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EKI', '工业网络：EKI 交换机与串口服务器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EKI') x);

-- Moxa
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Moxa' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '工业以太网交换机：EDS 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EDS' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EDS', '工业以太网交换机：EDS 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EDS') x);
UPDATE `product_series` SET `description` = '串口服务器：NPort 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NPort' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NPort', '串口服务器：NPort 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NPort') x);
UPDATE `product_series` SET `description` = '协议网关：MGate 系列（Modbus、PROFINET 等）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MGate' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MGate', '协议网关：MGate 系列（Modbus、PROFINET 等）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MGate') x);
UPDATE `product_series` SET `description` = '远程 I/O：ioLogik 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ioLogik' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ioLogik', '远程 I/O：ioLogik 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ioLogik') x);
UPDATE `product_series` SET `description` = '工业无线：AWK 系列 AP 与客户端。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AWK' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'AWK', '工业无线：AWK 系列 AP 与客户端。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'AWK') x);

-- HIWIN
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'HIWIN' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '伺服驱动：E1 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E1' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'E1', '伺服驱动：E1 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E1') x);

-- TECO
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'TECO' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：E510 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E510' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'E510', '变频器：E510 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'E510') x);
UPDATE `product_series` SET `description` = '变频器：A510 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A510' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'A510', '变频器：A510 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'A510') x);

-- MEAN WELL
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'MEAN WELL' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '导轨电源：NDR 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NDR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NDR', '导轨电源：NDR 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NDR') x);
UPDATE `product_series` SET `description` = '导轨电源：SDR 系列，带 PFC。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SDR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SDR', '导轨电源：SDR 系列，带 PFC。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SDR') x);
UPDATE `product_series` SET `description` = '导轨电源：HDR 超薄型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HDR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'HDR', '导轨电源：HDR 超薄型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'HDR') x);
UPDATE `product_series` SET `description` = '导轨 DC/DC 转换器：DDR 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DDR' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DDR', '导轨 DC/DC 转换器：DDR 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DDR') x);

-- Inovance
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Inovance' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：MD500 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MD500' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MD500', '变频器：MD500 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MD500') x);
UPDATE `product_series` SET `description` = '变频器：MD290 风机水泵型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MD290' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'MD290', '变频器：MD290 风机水泵型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'MD290') x);
UPDATE `product_series` SET `description` = '伺服：SV660 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SV660' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'SV660', '伺服：SV660 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'SV660') x);
UPDATE `product_series` SET `description` = '老款伺服：IS620 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IS620' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IS620', '老款伺服：IS620 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IS620') x);
UPDATE `product_series` SET `description` = '小型 PLC：H3U、H5U 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H3U/H5U' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'H3U/H5U', '小型 PLC：H3U、H5U 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'H3U/H5U') x);
UPDATE `product_series` SET `description` = '触摸屏：IT7000 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IT7000' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IT7000', '触摸屏：IT7000 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IT7000') x);

-- INVT
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'INVT' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '变频器：Goodrive20 通用型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GD20' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GD20', '变频器：Goodrive20 通用型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GD20') x);
UPDATE `product_series` SET `description` = '变频器：Goodrive200A 通用型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GD200A' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GD200A', '变频器：Goodrive200A 通用型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GD200A') x);
UPDATE `product_series` SET `description` = '变频器：Goodrive350 高性能型。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GD350' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GD350', '变频器：Goodrive350 高性能型。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GD350') x);
UPDATE `product_series` SET `description` = '伺服：DA200 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DA200' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DA200', '伺服：DA200 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DA200') x);
UPDATE `product_series` SET `description` = '小型 PLC：IVC1S、IVC3 等 IVC 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IVC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'IVC', '小型 PLC：IVC1S、IVC3 等 IVC 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'IVC') x);

-- Kinco
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Kinco' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '触摸屏：GL 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GL' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'GL', '触摸屏：GL 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'GL') x);
UPDATE `product_series` SET `description` = '小型 PLC：K2、K5 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'K' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'K', '小型 PLC：K2、K5 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'K') x);
UPDATE `product_series` SET `description` = '伺服驱动：FD 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FD' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'FD', '伺服驱动：FD 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'FD') x);
UPDATE `product_series` SET `description` = '步进驱动：2M、3M 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '2M/3M' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, '2M/3M', '步进驱动：2M、3M 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = '2M/3M') x);

-- Leadshine
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Leadshine' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '步进驱动：DM 系列数字式步进驱动器。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DM', '步进驱动：DM 系列数字式步进驱动器。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DM') x);
UPDATE `product_series` SET `description` = '伺服：EL5、EL6 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EL5/EL6' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EL5/EL6', '伺服：EL5、EL6 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EL5/EL6') x);

-- WECON
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'WECON' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：LX3V 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LX3V' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LX3V', '小型 PLC：LX3V 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LX3V') x);
UPDATE `product_series` SET `description` = '小型 PLC：LX5V 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LX5V' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LX5V', '小型 PLC：LX5V 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LX5V') x);
UPDATE `product_series` SET `description` = '触摸屏：PI3000、PI8000 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PI' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PI', '触摸屏：PI3000、PI8000 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PI') x);

-- Haiwell
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Haiwell' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：C、T、H 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'C/T/H Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'C/T/H Series', '小型 PLC：C、T、H 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'C/T/H Series') x);

-- Xinje
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Xinje' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '小型 PLC：XD、XL 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XD' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XD', '小型 PLC：XD、XL 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XD') x);
UPDATE `product_series` SET `description` = '小型 PLC：XC 系列（经典款）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'XC', '小型 PLC：XC 系列（经典款）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'XC') x);
UPDATE `product_series` SET `description` = '触摸屏：TG 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TG' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'TG', '触摸屏：TG 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'TG') x);
UPDATE `product_series` SET `description` = '伺服：DS5 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DS5' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DS5', '伺服：DS5 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DS5') x);

-- Estun
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Estun' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '伺服：ProNet 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ProNet' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ProNet', '伺服：ProNet 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ProNet') x);
UPDATE `product_series` SET `description` = '伺服：ED3L 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ED3L' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ED3L', '伺服：ED3L 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ED3L') x);

-- CHINT
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'CHINT' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '接触器：NXC 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NXC' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NXC', '接触器：NXC 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NXC') x);
UPDATE `product_series` SET `description` = '热过载继电器：NR2 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NR2' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NR2', '热过载继电器：NR2 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NR2') x);
UPDATE `product_series` SET `description` = '微型断路器：NXB 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NXB' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NXB', '微型断路器：NXB 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NXB') x);
UPDATE `product_series` SET `description` = '塑壳断路器：NM1 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NM1' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NM1', '塑壳断路器：NM1 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NM1') x);
UPDATE `product_series` SET `description` = '变频器：NVF2G 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NVF2G' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'NVF2G', '变频器：NVF2G 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'NVF2G') x);

-- Hollysys
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Hollysys' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '大型 PLC：LK 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LK' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LK', '大型 PLC：LK 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LK') x);
UPDATE `product_series` SET `description` = '小型 PLC：LE 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LE' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'LE', '小型 PLC：LE 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'LE') x);

-- SUPCON
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'SUPCON' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = 'DCS：JX-300XP 控制系统卡件。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'JX-300XP' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'JX-300XP', 'DCS：JX-300XP 控制系统卡件。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'JX-300XP') x);
UPDATE `product_series` SET `description` = 'DCS：ECS-700 控制系统。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ECS-700' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'ECS-700', 'DCS：ECS-700 控制系统。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'ECS-700') x);

-- Carlo Gavazzi
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Carlo Gavazzi' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '固态继电器：RM 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'RM' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'RM', '固态继电器：RM 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'RM') x);
UPDATE `product_series` SET `description` = '电能表：EM24 系列。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EM24' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'EM24', '电能表：EM24 系列。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'EM24') x);
UPDATE `product_series` SET `description` = '监控继电器：DPA、DPB 相序与电压监控。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DPA/DPB' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'DPA/DPB', '监控继电器：DPA、DPB 相序与电压监控。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'DPA/DPB') x);

-- Hitech
SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Hitech' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `product_series` SET `description` = '触摸屏：PWS 系列（海泰克，品牌已停用，保留供老型号查询）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PWS Series' AND `deleted_at` IS NULL AND `description` = '';
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)
SELECT 0, @b, 'PWS Series', '触摸屏：PWS 系列（海泰克，品牌已停用，保留供老型号查询）。', 1, 'sys', 'sys' FROM DUAL
WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = 'PWS Series') x);
