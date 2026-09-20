import {
  isSafeUrl,
  normalizeHsCode,
  normalizeMpn,
  precheckUpload,
  UPLOAD_TYPE_MESSAGE,
} from './constants';

// 这些规则与后端 MpnNormalizer / UrlRules / ProductMediaStorageServiceImpl 保持一致，
// 后端有对应的单元测试；改任何一边都要同步另一边。

describe('normalizeMpn（与后端 MpnNormalizer 一致）', () => {
  it('空格、连字符、大小写写法不同的同一型号归一化后相同', () => {
    const expected = '6es72141bd230xb0';
    expect(normalizeMpn('6ES7 214-1BD23-0XB0')).toBe(expected);
    expect(normalizeMpn('6ES7214-1BD23-0XB0')).toBe(expected);
    expect(normalizeMpn('  6es7214-1bd23-0xb0  ')).toBe(expected);
  });

  it('全角字符按 NFKC 折叠', () => {
    expect(normalizeMpn('６ＥＳ７２１４')).toBe('6es7214');
  });

  it('只含符号的型号归一化为空', () => {
    expect(normalizeMpn('---')).toBe('');
    expect(normalizeMpn('   ')).toBe('');
    expect(normalizeMpn('')).toBe('');
  });
});

describe('isSafeUrl（与后端 UrlRules 一致）', () => {
  it.each([
    'http://example.com/a.pdf',
    'https://example.com/s7-1200.pdf',
    'HTTPS://EXAMPLE.COM/A.PDF',
    '/datasheets/a.pdf',
    '/',
    'https://example.com/a.pdf?x=1&y=2#f',
  ])('接受 %s', (url) => expect(isSafeUrl(url)).toBe(true));

  it.each([
    'javascript:alert(1)',
    'data:text/html,x',
    '//evil.example/x.pdf',
    '/\\evil.example',
    'http://',
    'example.com/a.pdf',
    'https://exa mple.com',
    '',
  ])('拒绝 %s', (url) => expect(isSafeUrl(url)).toBe(false));
});

describe('precheckUpload（上传预检，服务端仍会再校验）', () => {
  const MB = 1024 * 1024;

  it('合规的图片和视频通过', () => {
    expect(precheckUpload({ name: 'a.png', size: 2 * MB }, 1)).toBeUndefined();
    expect(precheckUpload({ name: 'a.JPG', size: 5 * MB }, 1)).toBeUndefined();
    expect(
      precheckUpload({ name: 'a.webm', size: 100 * MB }, 2),
    ).toBeUndefined();
  });

  it('超过大小上限给出原因', () => {
    expect(precheckUpload({ name: 'a.png', size: 5 * MB + 1 }, 1)).toBe(
      '图片不能超过 5MB',
    );
    expect(precheckUpload({ name: 'a.mp4', size: 100 * MB + 1 }, 2)).toBe(
      '视频不能超过 100MB',
    );
  });

  it('类型不在白名单（含 SVG、无扩展名、图片视频互换）被拒绝', () => {
    for (const name of ['a.svg', 'a.gif', 'noext', 'a.exe']) {
      expect(precheckUpload({ name, size: 1 }, 1)).toBe(UPLOAD_TYPE_MESSAGE);
    }
    expect(precheckUpload({ name: 'a.mp4', size: 1 }, 1)).toBe(
      UPLOAD_TYPE_MESSAGE,
    );
    expect(precheckUpload({ name: 'a.png', size: 1 }, 2)).toBe(
      UPLOAD_TYPE_MESSAGE,
    );
  });
});

describe('normalizeHsCode', () => {
  it('去掉点和空格，只保留数字', () => {
    expect(normalizeHsCode('8537.10.90')).toBe('85371090');
    expect(normalizeHsCode(' 8537 10 90 ')).toBe('85371090');
  });
});
