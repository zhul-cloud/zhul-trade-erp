import React from 'react';
import { TaskList } from '@/pages/product/fact-tasks/TaskList';

/** 运营管理 → 营销任务：本公司各平台的营销内容，只收营销包 */
const OperationsProducts: React.FC = () => <TaskList entry="marketing" />;

export default OperationsProducts;
