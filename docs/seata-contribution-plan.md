# Seata rm-datasource 模块测试增强贡献计划

## Issue 信息

- **项目**: Apache Seata (incubating)
- **仓库**: https://github.com/apache/incubator-seata
- **Issue**: [#1714 - Test: increase rm-datasource module test coverage](https://github.com/apache/incubator-seata/issues/1714)
- **标签**: test, first-time-contributor-friendly
- **状态**: Open，持续欢迎贡献

## 模块介绍

rm-datasource 是 Seata AT（Automatic Transaction）模式的核心模块，负责：
- 数据源代理（DataSourceProxy）
- 连接管理与 XID 绑定（ConnectionProxy）
- SQL 拦截与执行（StatementProxy → Executor）
- 前后镜像记录与 undo_log 生成
- 分布式事务回滚支持（UndoLogManager）

**核心架构链路：**
```
DataSourceProxy → ConnectionProxy → StatementProxy → ExecuteTemplate → Executor → UndoLogManager
```

## 贡献策略（分阶段 PR）

### PR 1：ConnectionProxy & DataSourceProxy 测试补充（8-10h）
- XID 绑定与解绑
- 事务状态管理
- 资源 ID 生成
- 异常恢复处理
- 连接关闭与资源释放

### PR 2：Executor 系列测试（15-20h）
- InsertExecutor：单行/批量/自增主键/undo_log 正确性
- UpdateExecutor：版本快照/before_image/after_image/批量更新
- DeleteExecutor：数据记录/批量删除/数据恢复
- SelectForUpdateExecutor：FOR UPDATE 识别/全局锁

### PR 3：UndoLogManager 测试补充（12-15h）
- undo_log 插入逻辑
- 回滚操作正确性
- 多数据库适配

## 技术规范

- **测试框架**: JUnit 5 + Mockito 4.11+ + AssertJ 3.12+
- **测试数据库**: H2 内存数据库
- **覆盖率目标**: 60% → 75%+
- **规范遵循**: AIR 原则（Automatic、Independent、Repeatable）

## 操作步骤

### 1. Fork & Clone
```bash
git clone git@github.com:hqbhonker/incubator-seata.git
cd incubator-seata
git remote add upstream git@github.com:apache/incubator-seata.git
git remote set-url --push upstream no-pushing
```

### 2. 创建分支
```bash
git fetch upstream
git rebase upstream/develop
git checkout -b test/rm-datasource-connectionproxy
```

### 3. 本地构建验证
```bash
cd rm-datasource
mvn clean compile -DskipTests
mvn test  # 运行现有测试看基线
```

### 4. 编写测试后提交
```bash
git add .
git commit -m "test: add unit tests for ConnectionProxy and DataSourceProxy"
git push origin test/rm-datasource-connectionproxy
```

### 5. 创建 PR
- 标题：`test: add unit tests for rm-datasource ConnectionProxy`
- 描述清楚测试范围和覆盖率变化
- 关联 Issue #1714

## Issue 认领评论

```
Hi @apache/seata maintainers,

I would like to contribute to improve the test coverage of the rm-datasource module.

My Plan:
- Phase 1: ConnectionProxy & DataSourceProxy extended tests
- Phase 2: InsertExecutor, UpdateExecutor, DeleteExecutor unit tests
- Phase 3: UndoLogManager extended tests

Framework: JUnit 5 + Mockito + AssertJ
Target: Coverage improvement to 75%+
Timeline: 2-3 weeks

References: PR #7788, PR #7203

Looking forward to your guidance!
```

## Commit 规范

```
test: add unit tests for rm-datasource [模块名]

- 具体测试内容描述
- 覆盖的场景列表

Closes #1714
```

## 参考资源

- [Seata 开发者指南](https://seata.apache.org/zh-cn/docs/developers/guide_dev/)
- [Seata 测试规范](https://seata.apache.org/blog/how-to-write-unit-tests/)
- [参考 PR #7788](https://github.com/apache/incubator-seata/pull/7788)
- [参考 PR #7203](https://github.com/apache/incubator-seata/pull/7203)
- [rm-datasource 源码](https://github.com/apache/incubator-seata/tree/develop/rm-datasource)

## 时间规划

| 日期 | 任务 | 产出 |
|------|------|------|
| Day 1 | 环境搭建、Issue 认领 | 本地可编译运行 |
| Day 2-3 | ConnectionProxy 测试编写 | PR 1 提交 |
| Day 4-7 | Executor 系列测试编写 | PR 2 提交 |
| Day 8-9 | UndoLogManager 测试编写 | PR 3 提交 |
| Day 10 | 根据 Review 反馈修改 | PR 合并 |
