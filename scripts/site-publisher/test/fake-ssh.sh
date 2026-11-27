#!/bin/sh
# Test stand-in for ssh: skips options and the host, then runs the remote command locally,
# so rsync "deploys" into a local directory. FAKE_SSH_FAIL=1 simulates a rejected key.
if [ "$FAKE_SSH_FAIL" = "1" ]; then echo "user@host: Permission denied (publickey)." >&2; exit 255; fi
while [ $# -gt 0 ]; do
  case "$1" in
    -p|-i|-o|-l) shift 2 ;;
    -*) shift ;;
    *) shift; break ;;
  esac
done
exec sh -c "$*"
