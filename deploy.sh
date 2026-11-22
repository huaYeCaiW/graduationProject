#!/usr/bin/env bash
# 服务器一键部署：首次执行会生成 .env 并构建镜像
# 用法：bash deploy.sh
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker，请先安装 Docker Engine" >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "未找到 docker compose，请安装 Docker Compose v2" >&2
  exit 1
fi

gen_password() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 12
  else
    head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n'
  fi
}

if [ ! -f .env ]; then
  umask 077
  cat > .env <<EOF
MYSQL_ROOT_PASSWORD=$(gen_password)
EMQX_DASHBOARD_PASSWORD=$(gen_password)
MQTT_USERNAME=donghuan
MQTT_PASSWORD=$(gen_password)
APP_PORT=8080
JAVA_OPTS=-Xms256m -Xmx768m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError
EOF
  echo "已生成 .env（随机密码）。如需修改，请编辑后重新执行。"
fi

# 兼容旧版 .env：补齐 MQTT 账号配置
if ! grep -q '^MQTT_USERNAME=' .env; then
  echo "MQTT_USERNAME=donghuan" >> .env
fi
if ! grep -q '^MQTT_PASSWORD=' .env; then
  echo "MQTT_PASSWORD=$(gen_password)" >> .env
fi

$DC config -q

# 先起依赖，配置好 EMQX 认证后再启动应用，避免应用以匿名方式连上
$DC up -d --build mysql emqx modbus
bash scripts/setup-emqx-auth.sh
$DC up -d --build app

echo "等待应用健康检查..."
for _ in $(seq 1 60); do
  status=$(docker inspect -f '{{.State.Health.Status}}' dh-app 2>/dev/null || true)
  if [ "$status" = "healthy" ]; then
    break
  fi
  if [ "$status" = "unhealthy" ]; then
    echo "应用健康检查失败，最近日志：" >&2
    $DC logs --tail=80 app >&2
    exit 1
  fi
  sleep 3
done

if [ "${status:-}" != "healthy" ]; then
  echo "应用在等待时间内未就绪，请执行：$DC logs -f app" >&2
  exit 1
fi

port=$(grep -E '^APP_PORT=' .env | cut -d= -f2)
ip=$(hostname -I 2>/dev/null | awk '{print $1}')
echo
echo "部署完成：http://${ip:-服务器IP}:${port:-8080}"
echo "查看日志：$DC logs -f app"
echo "停止服务：$DC down"
