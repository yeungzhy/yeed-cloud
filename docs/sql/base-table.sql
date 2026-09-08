-- 新建业务表 DDL 模板
-- 公共字段与 com.yeungzhy.yeed.common.data.model.BaseEntity 一一对应，改任一处都要同步另一处
--
-- 使用约定：
-- 1. 表名即契约：yeed_<业务域>[_<子域>]，全小写 + 下划线；表名决定实体名、包名、接口路径
--    （去 yeed_ 前缀、下划线转斜杠），定名前先想清楚，事后改名要连坐四处。
-- 2. 下面 9 个公共字段整段拷贝，不改列名 / 类型 / 默认值；
--    业务字段一律追加在`id`字段之后 `create_by`字段之前。
-- 3. delete_time 是纳秒级时间戳（不是 1/0 标记），取值高度离散，不要给它建索引。
-- 4. extra 只放渲染元数据一类弱语义扩展；需要查询、约束、关联的字段必须独立建列。
-- 5. 索引按真实查询建，覆盖高频 WHERE + ORDER BY 组合即可，别给审计字段逐个建。

CREATE TABLE `yeed_业务域` (
  `id`          bigint   NOT NULL     COMMENT '雪花ID主键',
  -- 在此插入业务字段
  `create_by`   bigint   DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by`   bigint   DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `delete_by`   bigint   DEFAULT NULL COMMENT '删除人',
  `delete_time` bigint   NOT NULL DEFAULT '0' COMMENT '逻辑删除,0-未删,时间戳-已删',
  `version`     int      NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `extra`       json     DEFAULT NULL COMMENT '扩展信息',
  PRIMARY KEY (`id`),
  KEY `idx_create_by` (`create_by`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务域表';
