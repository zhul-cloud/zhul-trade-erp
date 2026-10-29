/**
 * 配置键命名规则（与后端 SaveConfigRequest 一致）：2 段及以上，段之间用点；
 * 每段小写字母开头，只含小写字母和数字，段内多个单词用短横线连接；整体 4~128 个字符。
 */
export const CONFIG_KEY_PATTERN =
  /^(?=.{4,128}$)[a-z][a-z0-9]*(-[a-z0-9]+)*(\.[a-z][a-z0-9]*(-[a-z0-9]+)*)+$/;

export const CONFIG_KEY_MESSAGE =
  '配置键由小写字母、数字和短横线组成的多段名称，段之间用点分隔，如 custom.order.auto-close';
