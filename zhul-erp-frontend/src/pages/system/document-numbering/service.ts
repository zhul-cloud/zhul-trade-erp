import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

const API = '/api/v1/system/document-numbering/prefix';
const quiet = { skipErrorHandler: true } as const;

export const prefixApi = {
  get: () =>
    request<{ data: { prefix: string } }>(API, {
      method: 'GET',
      ...quiet,
    }).then((r) => r.data),
  save: (prefix: string) =>
    request<{ data: { prefix: string } }>(API, {
      method: 'PUT',
      data: { prefix },
      ...quiet,
    }).then((r) => r.data),
};
