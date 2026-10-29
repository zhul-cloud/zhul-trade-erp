/**
 * @name 代理的配置
 * @see 在生产环境 代理是无法生效的，所以这里没有生产环境的配置
 * -------------------------------
 * The agent cannot take effect in the production environment
 * so there is no configuration of the production environment
 * For details, please see
 * https://pro.ant.design/docs/deploy
 *
 * @doc https://umijs.org/docs/guides/proxy
 */
export default {
  // 如果需要自定义本地开发服务器  请取消注释按需调整
  dev: {
    // 本地开发：前端 localhost:8000 → 后端 localhost:8080
    '/api/': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    // 配置上传的图片（如登录页 Logo / 背景）由后端静态资源提供
    '/uploads/': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
};
