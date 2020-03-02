CREATE TABLE `idempotent_request` (
  `id` bigint(19) NOT NULL AUTO_INCREMENT,
  `prj_name` varchar(64) DEFAULT NULL,
  `interface_name` varchar(64) DEFAULT NULL,
  `request_param` varchar(2048) DEFAULT NULL,
  `response` varchar(2048) DEFAULT NULL,
  `biz_column_values` varchar(512) DEFAULT NULL,
  `sign` varchar(64) DEFAULT NULL,
  `status` tinyint(1) DEFAULT NULL,
  `valid_end_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sign` (`sign`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

