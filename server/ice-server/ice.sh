#!/bin/sh

APP_NAME=ice-server
PID_FILE=run.pid

OPTS="
-port 8121
-storage-path ./ice-data
-client-timeout 60
-version-retention 1000
-recycle-cron '0 3 * * *'
-recycle-way hard
-recycle-protect-days 1
"

start() {
  if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
    echo "$APP_NAME is already running (pid=$(cat $PID_FILE))"
    return
  fi
  echo "starting $APP_NAME..."
  nohup ./$APP_NAME $OPTS > logs/ice-server.log 2>&1 &
  echo $! > $PID_FILE
  sleep 1
  if kill -0 $(cat "$PID_FILE") 2>/dev/null; then
    echo "$APP_NAME started (pid=$(cat $PID_FILE))"
  else
    echo "$APP_NAME failed to start, check logs/ice-server.log"
  fi
}

stop() {
  if [ ! -f "$PID_FILE" ]; then
    echo "$APP_NAME is not running"
    return
  fi
  pid=$(cat $PID_FILE)
  if ! kill -0 $pid 2>/dev/null; then
    echo "$APP_NAME is not running"
    rm -f $PID_FILE
    return
  fi
  echo "stopping $APP_NAME (pid=$pid)..."
  kill -15 $pid
  for i in 1 2 3 4 5; do
    sleep 1
    if ! kill -0 $pid 2>/dev/null; then
      echo "$APP_NAME stopped"
      rm -f $PID_FILE
      return
    fi
  done
  echo "force killing $APP_NAME"
  kill -9 $pid
  rm -f $PID_FILE
}

restart() {
  stop
  sleep 1
  start
}

status() {
  if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
    echo "$APP_NAME is running (pid=$(cat $PID_FILE))"
  else
    echo "$APP_NAME is not running"
  fi
}

usage() {
  echo "Usage: $0 {start|stop|restart|status}"
  exit 1
}

[ $# -ne 1 ] && usage

mkdir -p logs

case $1 in
  start)   start ;;
  stop)    stop ;;
  restart) restart ;;
  status)  status ;;
  *)       usage ;;
esac
