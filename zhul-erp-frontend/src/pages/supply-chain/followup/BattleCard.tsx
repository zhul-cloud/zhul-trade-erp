import { CompassOutlined } from '@ant-design/icons';
import { Checkbox, Skeleton } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { Card, Pill } from '../components';
import { type BattleCardData, COVER_NAMES, supplyApi } from '../service';

/** 阶段 → 作战地图节点（与后端 SupplyBoardService.STAGE_NODES 一致） */
export const STAGE_NODES: Record<string, string[]> = {
  unclassified: ['C4'],
  watching: ['C5'],
  review: ['C5'],
  paused: ['C5'],
  '1': ['V1'],
  '2': ['V3', 'V4'],
  '3': ['Q1', 'Q2', 'Q3'],
  '4': ['M4'],
};

export const STAGE_NAMES: Record<string, string> = {
  unclassified: '需求归一化',
  watching: '观察中',
  review: '待复核',
  paused: '暂不覆盖',
  ...COVER_NAMES,
};

let cache: Promise<Record<string, BattleCardData>> | null = null;
export const useCards = () => {
  const [cards, setCards] = useState<Record<string, BattleCardData>>();
  useEffect(() => {
    cache ??= supplyApi.cards().catch(() => {
      cache = null;
      return {};
    });
    cache.then(setCards);
  }, []);
  return cards;
};

const Title: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ margin: '16px 0 8px', fontSize: 13, color: palette.mute }}>
      {children}
    </div>
  );
};

/** 右侧作战卡：当前阶段的目标、该做的事、怎样算做好、进入下一阶段的条件 */
const BattleCard: React.FC<{ stage?: string }> = ({ stage }) => {
  const { palette } = useAppTheme();
  const cards = useCards();
  const [done, setDone] = useState<string[]>([]);
  const nodes = STAGE_NODES[stage ?? ''] ?? ['C5'];
  useEffect(() => setDone([]), [stage]);
  if (!cards)
    return (
      <Card style={{ padding: 20 }}>
        <Skeleton active paragraph={{ rows: 6 }} />
      </Card>
    );
  const card = cards[nodes[0]];
  if (!card) return null;
  const code =
    nodes.length > 1 ? `${nodes[0]}-${nodes[nodes.length - 1]}` : nodes[0];
  return (
    <Card style={{ padding: 20 }}>
      <b style={{ display: 'inline-flex', gap: 8, fontSize: 15 }}>
        <CompassOutlined style={{ color: palette.link }} /> 作战卡
      </b>
      <div
        style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 12 }}
      >
        <Pill tone="accent">{code}</Pill>
        <b>{STAGE_NAMES[stage ?? ''] ?? card.name}</b>
      </div>
      {card.goal && (
        <div
          style={{
            marginTop: 12,
            padding: 12,
            borderRadius: 10,
            background: palette.inset,
          }}
        >
          <div style={{ fontSize: 12, color: palette.mute }}>这一步的目标</div>
          <b style={{ fontSize: 14 }}>{card.goal}</b>
        </div>
      )}
      {card.checklist.length > 0 && (
        <>
          <Title>该做的事</Title>
          <div style={{ display: 'grid', gap: 6 }}>
            {card.checklist.map((c) => (
              <Checkbox
                key={c}
                checked={done.includes(c)}
                onChange={(e) =>
                  setDone((d) =>
                    e.target.checked ? [...d, c] : d.filter((x) => x !== c),
                  )
                }
              >
                {c}
              </Checkbox>
            ))}
          </div>
        </>
      )}
      {card.quality.length > 0 && (
        <>
          <Title>怎样算做好</Title>
          <ul style={{ margin: 0, paddingLeft: 18, display: 'grid', gap: 4 }}>
            {card.quality.map((q) => (
              <li key={q}>{q}</li>
            ))}
          </ul>
        </>
      )}
      {card.gate && (
        <>
          <Title>进入下一阶段的条件</Title>
          <ul style={{ margin: 0, paddingLeft: 18, display: 'grid', gap: 4 }}>
            {card.gate
              .split(/[；;]/)
              .filter(Boolean)
              .map((g) => (
                <li key={g}>{g.trim()}</li>
              ))}
          </ul>
        </>
      )}
      {card.autoHint && (
        <div style={{ marginTop: 12, fontSize: 12, color: palette.link }}>
          {card.autoHint}
        </div>
      )}
    </Card>
  );
};

export default BattleCard;
