import { PictureOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import React, { useState } from 'react';
import MediaDrawer from './MediaDrawer';

/** 单据型号行的「查看素材」：有素材库菜单权限才显示，打开这个型号的素材抽屉 */
const ViewMediaLink: React.FC<{ brand?: string; model?: string }> = ({
  brand,
  model,
}) => {
  const access = useAccess() as Record<string, boolean>;
  const [open, setOpen] = useState(false);
  if (!access.operationsMedia || !model) return null;
  return (
    <>
      <a
        onClick={(e) => {
          e.stopPropagation();
          setOpen(true);
        }}
        style={{ fontSize: 12 }}
      >
        <PictureOutlined /> 查看素材
      </a>
      {open && (
        <MediaDrawer
          open
          brand={brand ?? ''}
          model={model}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
};

export default ViewMediaLink;
