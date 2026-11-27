import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { HeadHelp } from '@/components/ListLayout';
import { arrange, autoFill, colId, colLabel, useColumnSettings } from './index';

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

describe('列设置 arrange 补充', () => {
  it('第一列没固定在左也始终显示、不参与排序', () => {
    const plain = [
      { title: '客户名称', dataIndex: 'name' },
      { title: '国家', dataIndex: 'country' },
      { title: '编码', dataIndex: 'code' },
    ];
    const r = arrange(
      plain,
      { order: ['code', 'name', 'country'], shown: [], hidden: ['name'] },
      [],
    );
    expect(r.map((c) => colId(c))).toEqual(['name', 'code', 'country']);
    expect(shown(r)).toContain('name');
  });

  it('保存的顺序有重复时不丢列', () => {
    const r = arrange(
      cols,
      { order: ['qty', 'qty', 'customer'], shown: [], hidden: [] },
      [],
    );
    expect(r).toHaveLength(cols.length);
    expect(new Set(r.map((c) => colId(c))).size).toBe(cols.length);
  });
});

describe('useColumnSettings', () => {
  afterEach(() => localStorage.clear());

  const Demo: React.FC = () => {
    const s = useColumnSettings('demo', cols, { hidden: ['品牌'] });
    return (
      <div>
        {s.settings}
        <ul>
          {s.columns
            .filter((c) => !(c as { hidden?: boolean }).hidden)
            .map((c) => (
              <li key={String(colId(c))}>{String(c.title)}</li>
            ))}
        </ul>
      </div>
    );
  };
  const visible = () =>
    screen.getAllByRole('listitem').map((li) => li.textContent);

  it('勾选后显示并记住，恢复默认后清掉', async () => {
    const { unmount } = render(<Demo />);
    expect(visible()).toEqual(['单号', '客户', '型号', '操作']);
    const btn = screen.getByRole('button', { name: /列设置/ });
    expect(btn.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(btn);
    expect(btn.getAttribute('aria-expanded')).toBe('true');
    fireEvent.click(await screen.findByRole('checkbox', { name: '品牌' }));
    expect(visible()).toContain('品牌');
    unmount();
    render(<Demo />);
    expect(visible()).toContain('品牌');
    fireEvent.click(screen.getByRole('button', { name: /列设置/ }));
    fireEvent.click(await screen.findByRole('button', { name: '恢复默认' }));
    expect(visible()).toEqual(['单号', '客户', '型号', '操作']);
    expect(
      Object.keys(localStorage).filter((k) => k.startsWith('zhul_cols')),
    ).toEqual([]);
  });

  it('只剩一列可设置的列时不能再收起', async () => {
    render(<Demo />);
    fireEvent.click(screen.getByRole('button', { name: /列设置/ }));
    fireEvent.click(await screen.findByRole('checkbox', { name: '客户' }));
    const qty = screen.getByRole('checkbox', { name: '型号' });
    expect((qty as HTMLInputElement).disabled).toBe(true);
  });
});

describe('宽屏自动补列 autoFill', () => {
  const wide = [
    { title: '单号', dataIndex: 'no', fixed: 'left' as const, width: 170 },
    { title: '客户', dataIndex: 'customer', width: 200 },
    { title: '型号', key: 'qty', width: 190 },
    { title: '品牌', key: 'brands', width: 160 },
    { title: '创建', key: 'create', width: 120 },
    { title: '更新', key: 'update', width: 120 },
    { title: '操作', key: 'actions', fixed: 'right' as const, width: 96 },
  ];
  const defaults = ['qty', 'brands', 'create', 'update'];
  const base = arrange(wide, EMPTY, defaults);

  it('放得下就按列顺序补，「创建」「更新」最后补，放不下的不补', () => {
    const r = autoFill(base, EMPTY, defaults, 300);
    expect([...r.auto]).toEqual(['qty']);
    expect(shown(r.columns)).toEqual(['no', 'customer', 'qty', 'actions']);
    expect([...autoFill(base, EMPTY, defaults, 700).auto]).toEqual([
      'qty',
      'brands',
      'create',
      'update',
    ]);
  });

  it('只补不减：没有空余时保持默认，用户手动关掉的不补', () => {
    expect(autoFill(base, EMPTY, defaults, 0).auto.size).toBe(0);
    const off = { order: [], shown: [], hidden: ['qty'] };
    expect([
      ...autoFill(arrange(wide, off, defaults), off, defaults, 200).auto,
    ]).toEqual(['brands']);
  });
});
