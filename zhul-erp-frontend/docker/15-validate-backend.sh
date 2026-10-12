#!/bin/sh
set -eu

# Only an origin is accepted. Disallow paths, credentials, and Nginx syntax.
case "${BACKEND_URL:-}" in
    *[[:space:]]*)
        echo 'BACKEND_URL must not contain whitespace.' >&2
        exit 1
        ;;
esac
if ! printf '%s\n' "${BACKEND_URL:-}" | grep -Eq '^https?://([[:alnum:]]([[:alnum:].-]*[[:alnum:]])?|\[[[:xdigit:]:]+\])(:[0-9]{1,5})?$'; then
    echo 'BACKEND_URL must be http(s)://hostname[:port], without a path or credentials.' >&2
    exit 1
fi
