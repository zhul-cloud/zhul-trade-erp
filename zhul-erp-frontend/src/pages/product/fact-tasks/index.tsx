import React from 'react';
import { TaskList } from './TaskList';

/** 运营管理 → 资料任务：只管资料阶段（共享），只收资料包与 llm-wiki 商品文件 */
const FactTasks: React.FC = () => <TaskList entry="facts" />;

export default FactTasks;
