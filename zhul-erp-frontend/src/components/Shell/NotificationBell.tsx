import {
  BellOutlined,
  CodeSandboxOutlined,
  FileTextOutlined,
  SearchOutlined,
  ShoppingCartOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  App,
  Badge,
  Button,
  Drawer,
  Empty,
  Segmented,
  Spin,
  Tooltip,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import {
  type NotificationItem,
  notificationApi,
} from '@/services/zhul/notification';
import { useAppTheme } from '@/theme/AppTheme';
import type { Palette } from '@/theme/palette';

const POLL_MS = 60_000;
const PAGE_SIZE = 20;

type Tab = 'todo' | 'all';

/** 节点图标与配色：询价蓝、采购紫、收货绿、差异与收款橙、销售灰 */
const nodeStyle = (node: string, p: Palette) => {
  if (node.startsWith('SOURCING') || node === 'INQUIRY_QUOTABLE') {
    return { icon: <SearchOutlined />, color: p.link, bg: p.accentSoft };
  }
  if (node === 'MODEL_DOUBT') {
    return { icon: <SearchOutlined />, color: p.orange, bg: p.orangeSoft };
  }
  if (node.startsWith('SO_') || node === 'PURCHASER_CHANGED') {
    return {
      icon: <ShoppingCartOutlined />,
      color: p.violet,
      bg: p.violetSoft,
    };
  }
  if (node.startsWith('RECEIPT')) {
    return { icon: <WalletOutlined />, color: p.orange, bg: p.orangeSoft };
  }
  if (node === 'DISCREPANCY') {
    return { icon: <WarningOutlined />, color: p.orange, bg: p.orangeSoft };
  }
  if (
    node === 'SUPPLIER_SHIPPED' ||
    node === 'ARRIVAL_DUE' ||
    node === 'GOODS_RECEIVED' ||
    node === 'ORDER_READY'
  ) {
    return { icon: <CodeSandboxOutlined />, color: p.green, bg: p.greenSoft };
  }
  return { icon: <FileTextOutlined />, color: p.mute, bg: p.inset };
};

const timeText = (t: string) => {
  const d = dayjs(t);
  const minutes = dayjs().diff(d, 'minute');
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  if (d.isSame(dayjs(), 'day')) return d.format('HH:mm');
  return d.format('MM-DD HH:mm');
};

const dayGroup = (t: string) => {
  const d = dayjs(t);
  if (d.isSame(dayjs(), 'day')) return '今天';
  if (d.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天';
  return '更早';
};

/** 一条通知：待办在处理前一直留在「待处理」，知会点开即已读 */
/** 通知内容按「 · 」分段；key 用段文字加出现次数，重复段也不冲突 */
const segments = (content: string) => {
  const seen = new Map<string, number>();
  return content.split(' · ').map((part, i) => {
    const n = (seen.get(part) ?? 0) + 1;
    seen.set(part, n);
    return { key: `${part}#${n}`, part, first: i === 0 };
  });
};

const Row: React.FC<{
  item: NotificationItem;
  palette: Palette;
  onOpen: (item: NotificationItem) => void;
}> = ({ item, palette, onOpen }) => {
  const s = nodeStyle(item.node, palette);
  const settled = item.kind === 'TODO' ? item.done : item.read;
  const chip = (text: string, color: string, bg: string) => (
    <span
      style={{
        padding: '1px 8px',
        borderRadius: 999,
        fontSize: 12,
        color,
        background: bg,
      }}
    >
      {text}
    </span>
  );
  return (
    <button
      type="button"
      onClick={() => onOpen(item)}
      style={{
        width: '100%',
        display: 'flex',
        gap: 12,
        padding: 14,
        marginBottom: 10,
        textAlign: 'left',
        border: `1px solid ${palette.hairline}`,
        borderRadius: 12,
        background: palette.card,
        cursor: item.link ? 'pointer' : 'default',
        opacity: settled ? 0.6 : 1,
      }}
    >
      <span
        style={{
          flex: 'none',
          width: 36,
          height: 36,
          borderRadius: 10,
          display: 'grid',
          placeItems: 'center',
          fontSize: 16,
          color: s.color,
          background: s.bg,
        }}
      >
        {s.icon}
      </span>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            fontSize: 12,
            color: palette.mute,
          }}
        >
          <span>{item.nodeName}</span>
          {item.kind === 'TODO' &&
            (item.done
              ? chip('已处理', palette.mute, palette.inset)
              : chip('待办', palette.link, palette.accentSoft))}
          {item.urgent &&
            !item.done &&
            chip('紧急', palette.red, palette.redSoft)}
          <span style={{ flex: 1 }} />
          <span>{timeText(item.time)}</span>
          {!settled && (
            <span
              style={{
                width: 6,
                height: 6,
                borderRadius: 999,
                background: palette.red,
              }}
            />
          )}
        </span>
        <span
          style={{
            display: 'block',
            marginTop: 4,
            fontSize: 14,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          {item.title}
        </span>
        {item.content && (
          <span
            style={{
              display: 'block',
              marginTop: 4,
              fontSize: 12,
              lineHeight: 1.5,
              color: palette.sub,
            }}
          >
            {/* 按「 · 」分段整段换行，避免日期、型号从中间断开；单段超过一行时才在段内折行 */}
            {segments(item.content).map(({ key, part, first }) => (
              <React.Fragment key={key}>
                {!first && ' · '}
                <span
                  style={{
                    display: 'inline-block',
                    maxWidth: '100%',
                    overflowWrap: 'anywhere',
                  }}
                >
                  {part}
                </span>
              </React.Fragment>
            ))}
          </span>
        )}
      </span>
    </button>
  );
};

/** 顶栏铃铛：角标 = 未处理的待办 + 未读的知会，每分钟刷新一次（页面在后台时暂停） */
export const NotificationBell: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState<Tab>('todo');
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  const refreshCount = useCallback(async () => {
    try {
      const c = await notificationApi.count();
      setCount(c.total);
    } catch {
      // 角标取不到时保持原样，不打扰
    }
  }, []);

  useEffect(() => {
    refreshCount();
    const timer = window.setInterval(() => {
      if (!document.hidden) refreshCount();
    }, POLL_MS);
    const onVisible = () => {
      if (!document.hidden) refreshCount();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [refreshCount]);

  const load = useCallback(
    async (t: Tab, p: number) => {
      setLoading(true);
      try {
        const r = await notificationApi.list(t, p, PAGE_SIZE);
        setItems((prev) => (p === 1 ? r.records : [...prev, ...r.records]));
        setTotal(r.total);
        setPage(p);
      } catch {
        message.error('通知加载失败，请稍后重试');
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    if (open) load(tab, 1);
  }, [open, tab, load]);

  const openItem = async (item: NotificationItem) => {
    if (!item.read) {
      notificationApi.read(item.id).catch(() => undefined);
    }
    if (item.link) {
      setOpen(false);
      history.push(item.link);
    } else {
      setItems((prev) =>
        prev.map((x) => (x.id === item.id ? { ...x, read: true } : x)),
      );
    }
    refreshCount();
  };

  const readAll = async () => {
    try {
      await notificationApi.readAll();
      await Promise.all([load(tab, 1), refreshCount()]);
    } catch {
      message.error('操作失败，请稍后重试');
    }
  };

  const groups: { label: string; rows: NotificationItem[] }[] = [];
  for (const item of items) {
    const label = dayGroup(item.time);
    const last = groups[groups.length - 1];
    if (last?.label === label) last.rows.push(item);
    else groups.push({ label, rows: [item] });
  }

  return (
    <>
      <Tooltip title="通知">
        <Badge count={count} size="small" offset={[-6, 6]}>
          <Button
            type="text"
            aria-label="通知"
            icon={<BellOutlined />}
            onClick={() => setOpen(true)}
          />
        </Badge>
      </Tooltip>
      <Drawer
        title="通知"
        open={open}
        size={440}
        onClose={() => setOpen(false)}
        extra={
          <Button type="link" size="small" onClick={readAll}>
            全部已读
          </Button>
        }
        styles={{ body: { paddingTop: 12 } }}
      >
        <Segmented<Tab>
          value={tab}
          onChange={setTab}
          options={[
            {
              label: count ? `待处理 ${count}` : '待处理',
              value: 'todo',
            },
            { label: '全部', value: 'all' },
          ]}
          style={{ marginBottom: 16 }}
        />
        <Spin spinning={loading && page === 1}>
          {items.length === 0 && !loading ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={tab === 'todo' ? '没有待处理的通知' : '还没有通知'}
            />
          ) : (
            groups.map((g) => (
              <div key={g.label}>
                <div
                  style={{
                    margin: '4px 0 10px',
                    fontSize: 12,
                    color: palette.mute,
                  }}
                >
                  {g.label}
                </div>
                {g.rows.map((item) => (
                  <Row
                    key={item.id}
                    item={item}
                    palette={palette}
                    onOpen={openItem}
                  />
                ))}
              </div>
            ))
          )}
          {items.length < total && (
            <Button
              block
              type="text"
              loading={loading}
              onClick={() => load(tab, page + 1)}
            >
              加载更多
            </Button>
          )}
        </Spin>
      </Drawer>
    </>
  );
};
