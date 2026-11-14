import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * 业务代码和 Umi 的 request 都用 axios，它会进入浏览器（audit DEP-003）。
 * 必须由本项目直接声明、只有一份，且不低于覆盖当前公告的版本：
 * 1.x 需 >= 1.20.0（GHSA-9fr6-4gfg-395g，同时覆盖 XSRF 与绝对 URL 绕过 baseURL 两个公告）。
 */
const ROOT = join(__dirname, '..');
const lock = JSON.parse(readFileSync(join(ROOT, 'package-lock.json'), 'utf8'));
const pkg = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8'));

const atLeast = (version: string, min: number[]) => {
  const v = version.split('.').map(Number);
  for (let i = 0; i < min.length; i++) {
    if (v[i] !== min[i]) return v[i] > min[i];
  }
  return true;
};

describe('axios 依赖（DEP-003）', () => {
  it('由项目直接声明为生产依赖，Umi 也用同一份', () => {
    expect(pkg.dependencies.axios).toBeDefined();
    expect(pkg.overrides?.axios).toBe('$axios');
  });

  it('锁文件里只有一份 axios，版本不低于 1.20.0，且不是开发依赖', () => {
    const copies = Object.entries(lock.packages as Record<string, any>).filter(
      ([path]) => path === 'node_modules/axios' || path.endsWith('/node_modules/axios'),
    );
    expect(copies.map(([path]) => path)).toEqual(['node_modules/axios']);
    const [, axios] = copies[0];
    expect(atLeast(axios.version, [1, 20, 0])).toBe(true);
    expect(axios.dev).toBeUndefined();
  });
});
