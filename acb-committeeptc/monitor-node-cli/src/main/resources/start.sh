#!/bin/bash

bin=`dirname "${BASH_SOURCE-$0}"`
CLI_HOME=`cd "$bin"; pwd`

Help=$(
  cat <<-"HELP"

 start.sh - Start the AntChain Bridge Monitor Node Command Line Interface Tool

 Usage:
   start.sh <params>

 Examples:
  1. start with the default server address `localhost` and default port `10088`：
   start.sh
  2. start with specific server address and port:
   start.sh -H 0.0.0.0 -p 10088

 Options:
   -H         admin server host of monitor node.
   -p         admin server port of monitor node.
   -h         print help information.

HELP
)

CURR_DIR="$(
  cd $(dirname $0)
  pwd
)"

while getopts "hH:p:" opt; do
  case "$opt" in
  "h")
    echo "$Help"
    exit 0
    ;;
  "H")
    SERVER_HOST=$OPTARG
    ;;
  "p")
    SERVER_PORT=$OPTARG
    ;;
  "?")
    echo "invalid arguments. "
    exit 1
    ;;
  *)
    echo "Unknown error while processing options"
    exit 1
    ;;
  esac
done


which java > /dev/null
if [ $? -eq 1 ]; then
    echo "no java installed. "
    exit 1
fi

java -jar ${CLI_HOME}/../lib/monitor-node-cli.jar --port=${SERVER_PORT:-10088} --host=${SERVER_HOST:-"localhost"}