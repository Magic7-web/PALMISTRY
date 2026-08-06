#!/bin/bash

echo "🚀 Palmistry 项目更新脚本"
cd /Users/wangliqun/Desktop/SoftWare/Mine/palmistry

echo "📦 正在备份服务器当前版本..."
ssh root@104.225.236.196 "mkdir -p /var/www/palmistry/backup && cp -r /var/www/palmistry/frontend /var/www/palmistry/backup/frontend_backup_\$(date +%Y%m%d_%H%M) 2>/dev/null || true"

echo "🧹 清空服务器旧前端文件..."
ssh root@104.225.236.196 "mkdir -p /var/www/palmistry/frontend && rm -rf /var/www/palmistry/frontend/* /var/www/palmistry/frontend/.[!.]*"

echo "📤 正在上传新打包文件..."
scp -r frontend/unpackage/dist/build/web/* root@104.225.236.196:/var/www/palmistry/frontend/

echo "♻️  重启 Nginx..."
ssh root@104.225.236.196 "nginx -t && systemctl reload nginx"

echo "✅ 更新完成！请用 Command + Shift + R 强制刷新浏览器查看效果"
