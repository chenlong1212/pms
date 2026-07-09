-- 修复已有重复数据并添加唯一约束
-- 用法: mysql -u root -p123456 pms_local < deploy/fix-duplicate.sql

-- 删除重复记录，保留 id 最小的那条
DELETE t1 FROM device_data_record t1
INNER JOIN device_data_record t2
    ON t1.device_id = t2.device_id
   AND t1.collect_time_str = t2.collect_time_str
   AND t1.id > t2.id;

-- 添加唯一约束（如果尚未添加）
ALTER TABLE device_data_record
    ADD UNIQUE KEY uk_device_collect_time (device_id, collect_time_str);
