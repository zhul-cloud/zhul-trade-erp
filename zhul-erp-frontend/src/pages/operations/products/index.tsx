import React from 'react';
import { TaskList } from '@/pages/product/fact-tasks/TaskList';

/** 运营管理 → 商品运营：本公司各平台的营销内容，只收营销包 */
const OperationsProducts: React.FC = () => <TaskList entry="marketing" />;

export default OperationsProducts;
