import { request } from '@umijs/max';

/**
 * 私有文件（附件、水单、合同等）的临时访问链接：接口校验登录与单据权限后返回签名链接（默认 10 分钟有效），
 * 浏览器直接从存储取文件，图片、PDF、视频都可以直接用这个地址，视频边下边播。
 *
 * @param endpoint 附件接口地址（不含 /url），如 `/api/v1/attachments/12`
 * @param download true 时链接强制下载（使用原文件名），否则浏览器内预览
 */
export const fileUrl = async (endpoint: string, download = false) => {
  const res = await request<{ data: { url: string } }>(`${endpoint}/url`, {
    method: 'GET',
    params: { download },
    skipErrorHandler: true,
  });
  return res.data.url;
};

/** 在新窗口预览 */
export const openFile = async (endpoint: string) => {
  window.open(await fileUrl(endpoint), '_blank', 'noopener');
};

/** 下载：签名链接带「下载」响应头，直接导航即可保存为原文件名 */
export const downloadFile = async (endpoint: string) => {
  const a = document.createElement('a');
  a.href = await fileUrl(endpoint, true);
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
};
