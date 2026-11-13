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
JAR_FILE="${APP_HOME}/MonitorSystemServer-1.0-SNAPSHOT.jar"
PID_FILE="${APP_HOME}/simple-monitor-system.pid"

if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet "${SERVICE_NAME}"; then
  if [ "$(id -u)" -ne 0 ]; then
    log_error "${SERVICE_NAME} is managed by systemd; run: sudo ./bin/stop.sh"
    exit 1
  fi

  log_info "stopping ${SERVICE_NAME}"
  systemctl stop "${SERVICE_NAME}"
  if systemctl is-active --quiet "${SERVICE_NAME}"; then
    log_error "failed to stop ${SERVICE_NAME}"
    exit 1
  fi

  log_info "${SERVICE_NAME} stopped successfully"
  exit 0
fi

if [ ! -f "${PID_FILE}" ]; then
  log_warn "pid file not found; Simple Monitor System is not running in application mode"
  exit 0
fi

SERVER_PID="$(tr -d '[:space:]' <"${PID_FILE}")"
if [ -z "${SERVER_PID}" ] || ! kill -0 "${SERVER_PID}" 2>/dev/null; then
  log_warn "stale pid file found; no running process"
  rm -f "${PID_FILE}"
  exit 0
fi

if [ -r "/proc/${SERVER_PID}/cmdline" ]; then
  PROCESS_COMMAND="$(tr '\0' ' ' <"/proc/${SERVER_PID}/cmdline")"
  case "${PROCESS_COMMAND}" in
  *"${JAR_FILE}"*) ;;
  *)
    log_error "pid ${SERVER_PID} does not belong to ${JAR_FILE}; refusing to stop it"
    exit 1
    ;;
  esac
fi

log_info "stopping Simple Monitor System with pid ${SERVER_PID}"
kill "${SERVER_PID}"

WAIT_SECONDS=0
while kill -0 "${SERVER_PID}" 2>/dev/null && [ "${WAIT_SECONDS}" -lt 30 ]; do
  sleep 1
  WAIT_SECONDS=$((WAIT_SECONDS + 1))
done

if kill -0 "${SERVER_PID}" 2>/dev/null; then
  log_error "process ${SERVER_PID} did not stop within 30 seconds"
  exit 1
fi

rm -f "${PID_FILE}"
log_info "Simple Monitor System stopped successfully"

