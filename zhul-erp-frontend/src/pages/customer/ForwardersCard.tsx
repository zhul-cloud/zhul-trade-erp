import { CarOutlined, PlusOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import React, { useCallback, useEffect, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { ForwarderDrawer } from '@/pages/logistics/forwarders';
import { type ForwarderRow, forwarderApi } from '@/pages/logistics/service';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 客户详情「客户指定货代」（spec logistics/forwarder「客户自有货代」）：归属这个客户的货代，
 * 发货通知时排在最前，只有一家时默认选中。可直接新增，归属客户自动带上。
 */
const ForwardersCard: React.FC<{
  customerId: number;
  customerName: string;
  canEdit: boolean;
}> = ({ customerId, customerName, canEdit }) => {
  const { palette } = useAppTheme();
  // 改已有货代走货代管理的编辑接口，要求货代编辑权限
  const canEditForwarder = !!(useAccess() as Record<string, boolean>)[
    'logistics:forwarder:edit'
  ];
  const [rows, setRows] = useState<ForwarderRow[]>();
  const [drawer, setDrawer] = useState<{ open: boolean; id?: number }>({
    open: false,
  });

  const load = useCallback(() => {
    forwarderApi
      .ofCustomer(customerId)
      .then(setRows)
      .catch(() => setRows([]));
  }, [customerId]);
  useEffect(load, [load]);

  if (!rows) return null;
  return (
    <section
      aria-label="客户指定货代"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        padding: 20,
        borderRadius: 16,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <CarOutlined style={{ color: palette.link }} />
        <h2
          style={{
            margin: 0,
            fontSize: 14,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          客户指定货代 {rows.length || ''}
        </h2>
        <span style={{ flex: 1 }} />
        {canEdit && (
          <button
            type="button"
            className="zhul-link"
            onClick={() => setDrawer({ open: true })}
          >
            <PlusOutlined /> 新增
          </button>
        )}
      </div>
      {rows.length === 0 ? (
        <div style={{ fontSize: 12, color: palette.mute }}>
          客户自己指定货代时在这里登记，发货通知会优先选它
        </div>
      ) : (
        rows.map((f) => (
          <div
            key={f.id}
            style={{
              padding: '10px 12px',
              borderRadius: 12,
              background: palette.inset,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <b
                title={f.name}
                style={{
                  color: palette.ink,
                  flex: 1,
                  minWidth: 0,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {f.name}
              </b>
              {f.handoverMode === 2 && (
                <Pill tone="violet">
                  {['上门取件', f.courierCarrier].filter(Boolean).join(' · ')}
                </Pill>
              )}
              {f.status === 1 ? (
                <Pill tone="green">启用</Pill>
              ) : (
                <Pill tone="gray">停用</Pill>
              )}
              {canEditForwarder && (
                <button
                  type="button"
                  className="zhul-link"
                  style={{ whiteSpace: 'nowrap' }}
                  onClick={() => setDrawer({ open: true, id: f.id })}
                >
                  编辑
                </button>
              )}
            </div>
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
              {[f.contactName, f.contactPhone, f.contactEmail]
                .filter(Boolean)
                .join(' · ') || '—'}
              {` · 体积系数 ${f.volumeDivisor ?? 5000}`}
            </div>
          </div>
        ))
      )}
      <ForwarderDrawer
        open={drawer.open}
        id={drawer.id}
        customerId={drawer.id ? undefined : customerId}
        customerName={customerName}
        onClose={() => setDrawer({ open: false })}
        onDone={load}
      />
    </section>
  );
};

export default ForwardersCard;
