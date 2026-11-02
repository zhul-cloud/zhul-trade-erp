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
      aria-label="档案完整度"
      style={{
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        borderRadius: 16,
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
            fontSize: 15,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          档案完整度
        </h2>
        <span
          className="num"
          style={{
            fontSize: 22,
            fontWeight: 700,
            color: c.complete ? palette.green : palette.orange,
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
                padding: '4px 10px',
                borderRadius: 10,
                fontSize: 12,
                background: palette.orangeSoft,
                color: palette.ink,
              }}
            >
              <span
                style={{
                  color: palette.orange,
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
