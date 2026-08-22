#!/usr/bin/env bash
# 备份：MySQL 库 + MinIO 数据卷 → 项目根 backups/ 目录（建议每周或每次大批量录入后执行）
#
# 用法：bash deploy/backup.sh
#
# 恢复步骤（新机器/灾难恢复）：
#   1. docker compose up -d mysql redis minio        # 起基础设施（空卷）
#   2. 恢复数据库：
#      gunzip < backups/ai_school_YYYYMMDD_HHMMSS.sql.gz | \
#        docker exec -i aischool-mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD ai_school
#   3. 恢复 MinIO（照片/PDF 报告）：
#      docker run --rm -v <minio卷名>:/data -v "$PWD/backups":/backup mysql:8.0 \
#        tar xzf /backup/minio-data_YYYYMMDD_HHMMSS.tgz -C /data
#   4. docker compose up -d --build                   # 起全栈
#   卷名用 `docker volume ls` 查（形如 ai_school_minio-data）
set -euo pipefail
cd "$(dirname "$0")/.."
# Git Bash(Windows) 下禁止把容器内路径 /data /backup 篡改成宿主机路径
export MSYS_NO_PATHCONV=1
STAMP=$(date +%Y%m%d_%H%M%S)
DIR=backups
mkdir -p "$DIR"

# 1) MySQL（--single-transaction 不锁表；密码经容器内环境变量，不落命令行参数）
docker exec aischool-mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines ai_school' \
  | gzip > "$DIR/ai_school_$STAMP.sql.gz"

# 2) MinIO 数据卷（复用本地已有 mysql:8.0 镜像打包，不新拉镜像）
MINIO_VOL=$(docker volume ls -q | grep -m1 'minio-data$')
if [ -n "$MINIO_VOL" ]; then
  docker run --rm -v "$MINIO_VOL":/data:ro -v "$PWD/$DIR":/backup mysql:8.0 \
    tar czf "/backup/minio-data_$STAMP.tgz" -C /data .
else
  echo "警告：未找到 minio-data 卷，跳过 MinIO 备份" >&2
fi

echo "备份完成："
ls -lh "$DIR"/*"$STAMP"* | awk '{print "  " $9 " (" $5 ")"}'
