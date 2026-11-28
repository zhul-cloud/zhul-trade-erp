import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { Pill } from '../components';
import { MODULE_SHORT, type Profile } from './service';
import { Progress5 } from './ui';

/** 档案完整度（原型 C1 顶部）：n/5、五段进度、每个缺的模块缺什么 */
const CompletenessCard: React.FC<{ profile: Profile }> = ({ profile }) => {
  const { palette } = useAppTheme();
  const c = profile.completeness;
  return (
    <section
      className="zhul-card"
      aria-label="档案完整度"
      style={{
        padding: '16px 20px',
        marginBottom: 16,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <h2
          style={{
            margin: 0,
            fontSize: 16,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          档案完整度
        </h2>
        <span
          className="num"
          style={{
            // 完整度是程度：数字用正文色，进度条表示多少；状态由旁边的标签表达（UI-118）
            fontSize: 20,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          {c.done} / 5
        </span>
        <Pill tone={c.complete ? 'green' : 'orange'}>
          {c.complete ? '已建档' : '待完善'}
        </Pill>
        <Progress5 mask={c.mask} width={220} />
        <span style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}>
          核心项全部填好算完成；带「系统」的不用填
        </span>
      </div>
      {c.missing.length > 0 && (
        <div
          style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 12 }}
        >
          {c.missing.map((m) => (
            <span
              key={m.module}
              style={{
                // 缺项是待办清单，用卡内凹格浅灰，不整片铺警告色（UI-118）
                padding: '4px 10px',
                borderRadius: 6,
                fontSize: 12,
                background: palette.inset,
                color: palette.sub,
              }}
            >
              <span
                style={{
                  color: palette.ink,
                  fontWeight: 600,
                  marginRight: 6,
                }}
              >
                {m.module} {MODULE_SHORT[m.module - 1]}
              </span>
              缺 {m.labels.join('、')}
            </span>
          ))}
        </div>
      )}
    </section>
  );
};

export default CompletenessCard;
