#!/bin/bash

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
  printf "${GREEN}[ INFO ]${NC} [ %s ] : %s\n" "$(date '+%Y-%m-%d %H:%M:%S')" "$1"
}

log_warn() {
  printf "${YELLOW}[ WARN ]${NC} [ %s ] : %s\n" "$(date '+%Y-%m-%d %H:%M:%S')" "$1"
}

log_error() {
  printf "${RED}[ ERROR ]${NC} [ %s ] : %s\n" "$(date '+%Y-%m-%d %H:%M:%S')" "$1"
}

