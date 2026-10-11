import type { CategoryOption } from '../service';

/** 品类下拉：按一级品类分组，一级本身也可选（标「一级」），下面列出它的细分品类；value 为品类 ID */
export const groupedCategoryOptions = (list: CategoryOption[]) =>
  list
    .filter((c) => !c.parentId)
    .map((p) => {
      const name = p.categoryNameZh || p.categoryName;
      return {
        label: name,
        title: name,
        options: [
          { value: p.id, label: `${name}（一级）` },
          ...list
            .filter((c) => c.parentId === p.id)
            .map((c) => ({
              value: c.id,
              label: c.categoryNameZh || c.categoryName,
            })),
        ],
      };
    });
