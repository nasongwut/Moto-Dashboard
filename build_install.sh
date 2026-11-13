#!/usr/bin/env bash
# เดิม: ตอนนี้ใช้ ./build.sh install แทน (วิธีเดียวกับ Mobile Remote)
exec "$(dirname "$0")/build.sh" install "$@"
