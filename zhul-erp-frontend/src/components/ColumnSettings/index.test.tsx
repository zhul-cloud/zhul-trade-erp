import React from 'react';
import { HeadHelp } from '@/components/ListLayout';
import { arrange, colId, colLabel } from './index';

const cols = [
  { title: '单号', dataIndex: 'no', fixed: 'left' as const },
  { title: '客户', dataIndex: 'customer' },
  { title: '型号', key: 'qty' },
  { title: '品牌', key: 'brands' },
  { title: '创建', key: 'create' },
  { title: '更新', key: 'update' },
  { title: '操作', key: 'actions', fixed: 'right' as const },
];
const EMPTY = { order: [], shown: [], hidden: [] };
const shown = (cs: object[]) =>
  (cs as { hidden?: boolean }[])
    .filter((c) => !c.hidden)
    .map((c) => colId(c as never));

describe('列设置 arrange', () => {
  it('默认收起「创建」「更新」和页面指定的列，固定列始终显示', () => {
    const r = arrange(cols, EMPTY, ['create', 'update', 'brands']);
    expect(shown(r)).toEqual(['no', 'customer', 'qty', 'actions']);
    expect(r).toHaveLength(cols.length);
  });

  it('用户打开默认收起的列、收起默认显示的列', () => {
    const r = arrange(cols, { order: [], shown: ['update'], hidden: ['qty'] }, [
      'create',
      'update',
    ]);
    expect(shown(r)).toEqual(['no', 'customer', 'brands', 'update', 'actions']);
  });

  it('按保存的顺序排，固定列位置不变；新加的列按原位置插入', () => {
    const r = arrange(
      cols,
      {
        order: ['brands', 'customer', 'gone', 'create', 'update'],
        shown: [],
        hidden: [],
      },
      [],
    );
    expect(r.map((c) => colId(c))).toEqual([
      'no',
      'brands',
      'qty',
      'customer',
      'create',
      'update',
      'actions',
    ]);
  });

  it('列名取字符串标题或 HeadHelp 的 title', () => {
    expect(colLabel({ title: '客户' })).toBe('客户');
    expect(
      colLabel({ title: <HeadHelp title="毛利" help="说明" />, key: 'profit' }),
    ).toBe('毛利');
    expect(colLabel({ title: <span>x</span>, key: 'k' })).toBe('k');
  });
});
