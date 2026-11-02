import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { FileTile, useFilePreview } from './FilePreview';
import type { PreviewFile } from './files';
import {
  boardApi,
  type InquiryAttachment,
  myTaskApi,
  type TaskNameplates,
} from './service';

/**
 * 询价任务里的铭牌图片（spec inquiry/sourcing-quote「采购查看铭牌图片」、inquiry/sourcing-task「询价分配查看铭牌图片」）：
 * 每个型号下面显示对应的图，同一询盘单未对应型号的图在任务顶部提示。from 为 my（我的询价）或 board（询价分配）。
 */
export const useTaskNameplates = (
  taskId: number | undefined,
  from: 'my' | 'board',
  size: { width: number; height: number } = { width: 72, height: 54 },
) => {
  const { palette } = useAppTheme();
  const preview = useFilePreview();
  const [data, setData] = useState<TaskNameplates | null>(null);
  const api = from === 'my' ? myTaskApi : boardApi;

  useEffect(() => {
    setData(null);
    if (!taskId) return undefined;
    let alive = true;
    api
      .nameplates(taskId)
      .then((d) => alive && setData(d))
      .catch(() => alive && setData(null));
    return () => {
      alive = false;
    };
  }, [taskId, from]);

  const endpointOf = (f: PreviewFile) =>
    api.nameplateEndpoint(taskId as number, f.id);
  const open = (
    list: InquiryAttachment[],
    f: InquiryAttachment,
    caption: string,
  ) => preview.open(list, list.indexOf(f), endpointOf, () => caption);

  const strip = (
    list: InquiryAttachment[],
    caption: string,
    w = size.width,
    h = size.height,
  ) =>
    list.map((f) => (
      <FileTile
        key={f.id}
        file={f}
        endpoint={endpointOf(f)}
        width={w}
        height={h}
        showName={false}
        onOpen={() => open(list, f, caption)}
      />
    ));

  /** 型号描述下面的铭牌缩略图；没有时不显示 */
  const forItem = (itemId: number, model?: string) => {
    const files = data?.items.find((i) => i.itemId === itemId)?.files ?? [];
    if (files.length === 0) return null;
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap',
          marginTop: 8,
        }}
      >
        {strip(files, `铭牌图片${model ? ` · ${model}` : ''}`)}
        <span style={{ fontSize: 12, color: palette.link }}>
          {files.length} 张铭牌 · 点开预览
        </span>
      </div>
    );
  };

  /** 任务顶部：同一询盘单里未对应型号的铭牌图 */
  const unmatched =
    data && data.unmatched.length > 0 ? (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          padding: '10px 14px',
          borderRadius: 12,
          background: palette.orangeSoft,
        }}
      >
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {strip(data.unmatched, '未对应型号的铭牌图', 56, 42)}
        </div>
        <div style={{ display: 'grid', gap: 2 }}>
          <span style={{ fontWeight: 600, color: palette.orange }}>
            未对应型号的铭牌图 {data.unmatched.length} 张
          </span>
          <span style={{ fontSize: 12, color: palette.sub }}>
            业务员还没对上是哪个型号，找货时可以对照着看
          </span>
        </div>
      </div>
    ) : null;

  return { forItem, unmatched, node: preview.node };
};
