import {
  BellOutlined,
  CloseOutlined,
  CodeSandboxOutlined,
  PlusOutlined,
  SearchOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { App, Button, Segmented, Select, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/warehouse/service';
import { type ReceiverPost, receiverApi } from '@/services/zhul/notification';
import { useAppTheme } from '@/theme/AppTheme';
import type { Palette } from '@/theme/palette';

type User = { id: number; name: string };

const postStyle = (post: string, p: Palette) => {
  if (post === 'RECEIVER') {
    return { icon: <CodeSandboxOutlined />, color: p.green, bg: p.greenSoft };
  }
  if (post === 'FINANCE') {
    return { icon: <WalletOutlined />, color: p.orange, bg: p.orangeSoft };
  }
  return { icon: <SearchOutlined />, color: p.link, bg: p.accentSoft };
};

/** 一个岗位：接收人（收货人可跟采购订单的采购员走）+ 它接收的通知 */
const PostCard: React.FC<{
  data: ReceiverPost;
  candidates: User[];
  canEdit: boolean;
  onSave: (poBuyer: boolean, userIds: number[]) => Promise<void>;
}> = ({ data, candidates, canEdit, onSave }) => {
  const { palette } = useAppTheme();
  const [adding, setAdding] = useState(false);
  const [specified, setSpecified] = useState(!data.poBuyer);
  const isReceiver = data.post === 'RECEIVER';
  const byBuyer = isReceiver && !specified;
  const ids = data.users.map((u) => u.id);
  const s = postStyle(data.post, palette);

  useEffect(() => setSpecified(!data.poBuyer), [data.poBuyer]);

  const add = async (id: number) => {
    setAdding(false);
    await onSave(false, [...ids, id]);
  };

  return (
    <Card style={{ padding: 20 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span
          style={{
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
        <span style={{ fontSize: 16, fontWeight: 600, color: palette.ink }}>
          {data.postName}
        </span>
      </div>

      {isReceiver && (
        <Segmented
          style={{ marginTop: 16 }}
          disabled={!canEdit}
          value={specified ? 'users' : 'buyer'}
          onChange={(v) => {
            if (v === 'buyer') {
              onSave(true, []);
            } else {
              setSpecified(true);
            }
          }}
          options={[
            { label: '采购订单的采购员', value: 'buyer' },
            { label: '指定的人', value: 'users' },
          ]}
        />
      )}

      {byBuyer ? (
        <div style={{ marginTop: 12, fontSize: 13, color: palette.mute }}>
          本人登记的发货，到预计到货日还没收货时提醒
        </div>
      ) : (
        <>
          {ids.length === 0 && (
            <div
              style={{
                marginTop: 16,
                padding: '8px 12px',
                borderRadius: 8,
                fontSize: 13,
                color: palette.orange,
                background: palette.orangeSoft,
              }}
            >
              <WarningOutlined /> 还没有接收人，这些通知不会发出
            </div>
          )}
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              gap: 8,
              marginTop: 16,
            }}
          >
            {data.users.map((u) => (
              <span
                key={u.id}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '3px 8px 3px 3px',
                  borderRadius: 999,
                  fontSize: 13,
                  color: palette.ink,
                  background: palette.accentSoft,
                }}
              >
                <span
                  style={{
                    width: 22,
                    height: 22,
                    borderRadius: 999,
                    display: 'grid',
                    placeItems: 'center',
                    fontSize: 11,
                    color: '#fff',
                    background: '#2563EB',
                  }}
                >
                  {u.name?.slice(0, 1)}
                </span>
                {u.name}
                {canEdit && (
                  <CloseOutlined
                    aria-label={`移除 ${u.name}`}
                    style={{ fontSize: 10, color: palette.mute }}
                    onClick={() =>
                      onSave(
                        false,
                        ids.filter((x) => x !== u.id),
                      )
                    }
                  />
                )}
              </span>
            ))}
            {canEdit &&
              (adding ? (
                <Select
                  autoFocus
                  open
                  size="small"
                  showSearch={{ optionFilterProp: 'label' }}
                  placeholder="搜索姓名"
                  style={{ width: 160 }}
                  onBlur={() => setAdding(false)}
                  onChange={add}
                  options={candidates
                    .filter((c) => !ids.includes(c.id))
                    .map((c) => ({ label: c.name, value: c.id }))}
                />
              ) : (
                <Button
                  size="small"
                  shape="round"
                  icon={<PlusOutlined />}
                  onClick={() => setAdding(true)}
                >
                  添加
                </Button>
              ))}
          </div>
        </>
      )}

      <div
        style={{
          marginTop: 16,
          paddingTop: 16,
          borderTop: `1px solid ${palette.hairline}`,
        }}
      >
        <div style={{ fontSize: 12, color: palette.mute, marginBottom: 8 }}>
          接收的通知
        </div>
        {data.nodes.map((n) => (
          <div
            key={n}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '4px 0',
              fontSize: 13,
              color: palette.sub,
            }}
          >
            <BellOutlined style={{ color: palette.mute }} />
            {n}
          </div>
        ))}
      </div>
    </Card>
  );
};

const NotificationReceivers: React.FC = () => {
  const { message } = App.useApp();
  const access = useAccess();
  const canEdit = !!access['system:notification-receiver:edit'];
  const [posts, setPosts] = useState<ReceiverPost[]>();
  const [candidates, setCandidates] = useState<User[]>([]);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const [p, c] = await Promise.all([
        receiverApi.list(),
        receiverApi.candidates(),
      ]);
      setPosts(p);
      setCandidates(c);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const save = async (post: string, poBuyer: boolean, userIds: number[]) => {
    try {
      setPosts(await receiverApi.save(post, { poBuyer, userIds }));
      message.success('已保存');
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  return (
    <div>
      <PageTitle />
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !posts ? (
        <Skeleton active />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
            gap: 16,
            alignItems: 'start',
          }}
        >
          {posts.map((p) => (
            <PostCard
              key={p.post}
              data={p}
              candidates={candidates}
              canEdit={canEdit}
              onSave={(poBuyer, ids) => save(p.post, poBuyer, ids)}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default NotificationReceivers;
