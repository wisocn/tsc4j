#!/usr/bin/env bash

PROJECT_NAME="tsc4j-aws"

die() {
  echo "FATAL: $@" 1>&2
  exit 1
}

dc_run() {
  docker-compose -p "${PROJECT_NAME}" "$@"
}

svc_start() {
  local ps_output=$(dc_run ps | tail -n +3)
  local non_running=$(echo "$ps_output" | grep " Up " -v | grep -v '^ ')

  if [ -z "$ps_output" -o ! -z "$non_running" ]; then
    dc_run up -d
    wait_for_services
  fi
}

svc_stop() {
  dc_run stop -t 2
}

svc_status() {
  dc_run ps
}

wait_for_services() {
  local ready="0"
  local attempts=0
  while [ "$ready" != "1" -a $attempts -le 50 ]; do
    attempts=$((attempts + 1))
    if dc_run logs | grep -qi "ready."; then
      echo "ready after $attempts attemps."
      ready=1
    fi
    sleep 0.1
  done
}

script_init() {
  test -z "$PROJECT_NAME" && die "Undefined project name."
}

do_run() {
  local action="$1"
  shift

  script_init

  case "$action" in
  start)
    svc_start "$@"
    ;;
  stop)
    svc_stop "$@"
    ;;
  status)
    svc_status "$@"
    ;;
  *)
    die "Usage: $0 {start|stop|status}"
    ;;
  esac
}

do_run "$@"

# vim:shiftwidth=2 softtabstop=2 expandtab
# EOF
