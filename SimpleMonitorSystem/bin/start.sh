#!/bin/bash

set -u

CURR_DIR="$(
  cd "$(dirname "$0")"
  pwd
)"
APP_HOME="$(
  cd "${CURR_DIR}/.."
  pwd
)"

source "${CURR_DIR}/print.sh"

SERVICE_NAME="simple-monitor-system.service"
SERVICE_TEMPLATE="${CURR_DIR}/${SERVICE_NAME}"
SERVICE_TARGET="/etc/systemd/system/${SERVICE_NAME}"
JAR_FILE="${APP_HOME}/MonitorSystemServer-1.0-SNAPSHOT.jar"
PID_FILE="${APP_HOME}/simple-monitor-system.pid"
CONSOLE_LOG="${APP_HOME}/logs/monitor-system-console.log"
TLS_CERT="${APP_HOME}/tls_certs/monitor-system.crt"
TLS_KEY="${APP_HOME}/tls_certs/monitor-system.key"
SYSTEM_SERVICE_MODE="off"

usage() {
  cat <<'EOF'
Usage:
  ./bin/start.sh       Start in background application mode
  sudo ./bin/start.sh -s
                       Install and start as a systemd service

Options:
  -s  Run in systemd service mode
  -h  Show this help

Environment:
  JAVA_OPTS  Extra JVM options used in background application mode
EOF
}

while getopts "hs" opt; do
  case "${opt}" in
  h)
    usage
    exit 0
    ;;
  s)
    SYSTEM_SERVICE_MODE="on"
    ;;
  *)
    usage
    exit 1
    ;;
  esac
done

JAVA_BIN="$(command -v java 2>/dev/null || true)"
if [ -z "${JAVA_BIN}" ]; then
  log_error "java is not installed or is not available in PATH"
  exit 1
fi

if [ ! -f "${JAR_FILE}" ]; then
  log_error "server jar not found: ${JAR_FILE}"
  exit 1
fi

if [ ! -f "${TLS_CERT}" ] || [ ! -f "${TLS_KEY}" ]; then
  log_error "TLS certificate or key is missing under ${APP_HOME}/tls_certs"
  log_error "run ${APP_HOME}/init_tls_certs.sh once before starting the service"
  exit 1
fi

mkdir -p "${APP_HOME}/logs"

if [ "${SYSTEM_SERVICE_MODE}" = "on" ]; then
  if [ "$(id -u)" -ne 0 ]; then
    log_error "systemd service mode requires root privileges; run: sudo ./bin/start.sh -s"
    exit 1
  fi
  if ! command -v systemctl >/dev/null 2>&1; then
    log_error "systemctl is not available on this host"
    exit 1
  fi

  ESCAPED_APP_HOME="$(printf '%s' "${APP_HOME}" | sed 's/[&|]/\\&/g')"
  ESCAPED_JAVA_BIN="$(printf '%s' "${JAVA_BIN}" | sed 's/[&|]/\\&/g')"
  ESCAPED_JAR_FILE="$(printf '%s' "${JAR_FILE}" | sed 's/[&|]/\\&/g')"
  RUN_USER="${SUDO_USER:-root}"
  if ! id "${RUN_USER}" >/dev/null 2>&1; then
    RUN_USER="root"
  fi
  RUN_GROUP="$(id -gn "${RUN_USER}")"

  sed \
    -e "s|@@RUN_USER@@|${RUN_USER}|g" \
    -e "s|@@RUN_GROUP@@|${RUN_GROUP}|g" \
    -e "s|@@WORKING_DIR@@|${ESCAPED_APP_HOME}|g" \
    -e "s|@@JAVA_BIN@@|${ESCAPED_JAVA_BIN}|g" \
    -e "s|@@JAR_FILE@@|${ESCAPED_JAR_FILE}|g" \
    "${SERVICE_TEMPLATE}" >"${SERVICE_TARGET}"

  systemctl daemon-reload
  systemctl enable "${SERVICE_NAME}" >/dev/null
  systemctl restart "${SERVICE_NAME}"

  if ! systemctl is-active --quiet "${SERVICE_NAME}"; then
    log_error "failed to start ${SERVICE_NAME}"
    systemctl status "${SERVICE_NAME}" --no-pager || true
    exit 1
  fi

  log_info "${SERVICE_NAME} started successfully"
  log_info "view logs with: journalctl -u ${SERVICE_NAME} -f"
  exit 0
fi

if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet "${SERVICE_NAME}"; then
  log_warn "${SERVICE_NAME} is already running under systemd"
  exit 0
fi

if [ -f "${PID_FILE}" ]; then
  EXISTING_PID="$(tr -d '[:space:]' <"${PID_FILE}")"
  if [ -n "${EXISTING_PID}" ] && kill -0 "${EXISTING_PID}" 2>/dev/null; then
    log_warn "Simple Monitor System is already running with pid ${EXISTING_PID}"
    exit 0
  fi
fi

log_info "starting Simple Monitor System in background application mode"

if [ -n "${JAVA_OPTS:-}" ]; then
  read -r -a JVM_ARGS <<<"${JAVA_OPTS}"
else
  JVM_ARGS=(-Xms128m -Xmx512m)
fi

nohup "${JAVA_BIN}" "${JVM_ARGS[@]}" -jar "${JAR_FILE}" \
  >>"${CONSOLE_LOG}" 2>&1 </dev/null &
SERVER_PID=$!
printf '%s\n' "${SERVER_PID}" >"${PID_FILE}"

sleep 1
if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
  log_error "Simple Monitor System exited during startup"
  log_error "check ${CONSOLE_LOG}"
  exit 1
fi

log_info "Simple Monitor System started successfully with pid ${SERVER_PID}"
log_info "application log: ${APP_HOME}/logs/monitor-system.log"
log_info "console log: ${CONSOLE_LOG}"
