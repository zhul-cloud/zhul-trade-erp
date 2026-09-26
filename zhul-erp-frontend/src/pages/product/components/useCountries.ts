import { useEffect, useMemo, useState } from 'react';
import { type Country, countryApi } from '../service';

// 清单是静态的，整个页面生命周期里只请求一次
let cache: Promise<Country[]> | null = null;
const loadCountries = () => {
  if (!cache) {
    cache = countryApi.list().catch((e) => {
      cache = null;
      throw e;
    });
  }
  return cache;
};

/** 国家/地区清单，以及把已保存的英文名显示成「中文名 English」、只显示中文名、换成 ISO 两位代码的方法 */
export function useCountries() {
  const [list, setList] = useState<Country[]>([]);
  useEffect(() => {
    let alive = true;
    loadCountries()
      .then((rows) => alive && setList(rows))
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, []);

  const options = useMemo(
    () =>
      list.map((c) => ({
        value: c.nameEn,
        label: `${c.nameZh} ${c.nameEn}`,
      })),
    [list],
  );
  const labelOf = useMemo(() => {
    const map = new Map(list.map((c) => [c.nameEn, `${c.nameZh} ${c.nameEn}`]));
    return (nameEn?: string) => (nameEn ? (map.get(nameEn) ?? nameEn) : '');
  }, [list]);

  const zhOf = useMemo(() => {
    const map = new Map(list.map((c) => [c.nameEn, c.nameZh]));
    return (nameEn?: string) => (nameEn ? (map.get(nameEn) ?? nameEn) : '');
  }, [list]);

  const codeOf = useMemo(() => {
    const map = new Map(list.map((c) => [c.nameEn, c.code]));
    return (nameEn?: string) => (nameEn ? map.get(nameEn) : undefined);
  }, [list]);

  return { options, labelOf, zhOf, codeOf };
}
