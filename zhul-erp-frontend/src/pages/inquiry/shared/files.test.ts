import {
  fileKind,
  imagePosition,
  movePlate,
  plateOwners,
  stepImage,
} from './files';

const img = (id: number) => ({
  id,
  fileName: `p${id}.jpg`,
  contentType: 'image/jpeg',
});
const pdf = { id: 9, fileName: '规格书.pdf', contentType: 'application/pdf' };
const xlsx = { id: 8, fileName: '询价清单.xlsx' };

describe('fileKind', () => {
  it('按文件类型或扩展名区分图片、PDF 与表格', () => {
    expect(fileKind(img(1))).toBe('image');
    expect(fileKind({ id: 2, fileName: 'A.PNG' })).toBe('image');
    expect(fileKind(pdf)).toBe('pdf');
    expect(fileKind(xlsx)).toBe('sheet');
  });
});

describe('stepImage', () => {
  const files = [img(1), pdf, img(2), xlsx, img(3)];
  it('左右切换跳过 PDF 与表格', () => {
    expect(stepImage(files, 0, 1)).toBe(2);
    expect(stepImage(files, 2, 1)).toBe(4);
    expect(stepImage(files, 4, -1)).toBe(2);
  });
  it('到头时不动', () => {
    expect(stepImage(files, 4, 1)).toBe(4);
    expect(stepImage(files, 0, -1)).toBe(0);
  });
  it('第 n / 共 m 张只数图片', () => {
    expect(imagePosition(files, 2)).toEqual({ n: 2, total: 3 });
  });
});

describe('铭牌对应', () => {
  const rows = [
    { key: 'a', nameplateIds: [1, 2] },
    { key: 'b', nameplateIds: [2, 3] },
    { key: 'c' },
  ];
  it('一张图只算给第一个引用它的行', () => {
    expect([...plateOwners(rows)]).toEqual([
      [1, 'a'],
      [2, 'a'],
      [3, 'b'],
    ]);
  });
  it('改到别的行时从原来的行去掉', () => {
    const next = movePlate(rows, 2, 'c');
    expect(next.map((r) => r.nameplateIds)).toEqual([[1], [3], [2]]);
  });
  it('改为未对应时所有行都去掉', () => {
    expect(movePlate(rows, 3).map((r) => r.nameplateIds)).toEqual([
      [1, 2],
      [2],
      [],
    ]);
  });
});
