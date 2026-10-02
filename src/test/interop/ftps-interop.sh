#!/usr/bin/env bash
#
# FTPS interoperability check (BJL-2): this project's FTP server against non-Java clients.
#
#   lftp - GnuTLS, the TLS library FileZilla uses
#   curl - OpenSSL (or the platform's TLS on macOS)
#
# For each client: explicit FTPS (AUTH TLS) and implicit FTPS, passive and active mode,
# TLS 1.3 and TLS 1.2: list, download (checked byte for byte), upload (checked), resumed
# download (REST), and no TLS alerts or errors reported by the client.
#
# Usage:  src/test/interop/ftps-interop.sh
# Needs:  mvn, java, and lftp and/or curl on the PATH (or LFTP=/path/to/lftp, CURL=...).
#         A missing client is skipped. Exit status 0 = every check passed.
#
set -u
cd "$(dirname "$0")/../../.."
MVN=${MVN:-mvn}
LFTP=${LFTP:-$(command -v lftp || true)}
CURL=${CURL:-$(command -v curl || true)}
EXPLICIT_PORT=${EXPLICIT_PORT:-8160}
IMPLICIT_PORT=${IMPLICIT_PORT:-8161}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/ftps-interop.XXXXXX")
ROOT="$WORK/root"
PASS=0; FAIL=0; FAILED=()

echo "Building test classes..."
$MVN -q -o test-compile dependency:build-classpath -Dmdep.outputFile="$WORK/cp.txt" -Dspotbugs.skip >/dev/null 2>&1 \
  || $MVN -q test-compile dependency:build-classpath -Dmdep.outputFile="$WORK/cp.txt" -Dspotbugs.skip || { echo "build failed"; exit 2; }
CP="target/classes:target/test-classes:$(cat "$WORK/cp.txt")"

mkdir -p "$ROOT/pub" "$WORK/local"
head -c 1000000 /dev/urandom > "$ROOT/pub/big.bin"
echo "hello interop" > "$ROOT/pub/hello.txt"
head -c 300000 /dev/urandom > "$WORK/local/upload.bin"

# Server: runs until its stdin (this fifo) is closed
mkfifo "$WORK/stop"
java -cp "$CP" us.bringardner.net.ftp.test.InteropServer "$ROOT" $EXPLICIT_PORT $IMPLICIT_PORT < "$WORK/stop" > "$WORK/server.log" 2>&1 &
SERVER=$!
exec 3>"$WORK/stop"
cleanup() { exec 3>&-; wait $SERVER 2>/dev/null; rm -rf "$WORK"; }
trap cleanup EXIT
for i in $(seq 1 60); do grep -q READY "$WORK/server.log" && break; sleep 0.5; done
grep -q READY "$WORK/server.log" || { echo "server did not start:"; cat "$WORK/server.log"; exit 2; }

sum() { cksum < "$1" | cut -d' ' -f1-2; }

check() { # name, command output file, condition result
	local name=$1 ok=$2 log=$3
	if [ "$ok" = 0 ]; then PASS=$((PASS+1)); printf '  PASS  %s\n' "$name"
	else FAIL=$((FAIL+1)); FAILED+=("$name"); printf '  FAIL  %s\n' "$name"; sed 's/^/        | /' "$log" | tail -8; fi
}

# ---------------------------------------------------------------- lftp (GnuTLS)
if [ -n "$LFTP" ]; then
	echo "lftp: $("$LFTP" --version | head -1)"
	for mode in explicit implicit; do for pasv in on off; do for tls in 1.3 1.2; do
		name="lftp $mode passive=$pasv TLS$tls"
		if [ $mode = explicit ]; then url="ftp://127.0.0.1:$EXPLICIT_PORT"; else url="ftps://127.0.0.1:$IMPLICIT_PORT"; fi
		prio="NORMAL"; [ $tls = 1.2 ] && prio="NORMAL:-VERS-TLS1.3"
		up="up-lftp-$mode-$pasv-$tls.bin"
		dl="$WORK/local/dl-$mode-$pasv-$tls.bin"
		rm -f "$dl"
		head -c 400000 "$ROOT/pub/big.bin" > "$dl.part"
		log="$WORK/lftp.log"
		timeout 60 "$LFTP" -c "set ssl:verify-certificate no; set ftp:ssl-force true; set ftp:ssl-protect-data true;
			set ftp:ssl-auth TLS; set ssl:priority $prio; set ftp:passive-mode $pasv; set net:max-retries 1; set net:timeout 10;
			open -u anonymous,x $url; cls -l pub; get pub/big.bin -o $dl; put $WORK/local/upload.bin -o pub/$up;
			get -c pub/big.bin -o $dl.part" > "$log" 2>&1
		rc=$?
		ok=1
		if [ $rc = 0 ] && ! grep -qi -E 'fatal|alert|error' "$log" \
			&& grep -q 'big.bin' "$log" \
			&& [ "$(sum "$dl")" = "$(sum "$ROOT/pub/big.bin")" ] \
			&& [ "$(sum "$ROOT/pub/$up")" = "$(sum "$WORK/local/upload.bin")" ] \
			&& [ "$(sum "$dl.part")" = "$(sum "$ROOT/pub/big.bin")" ]; then ok=0; fi
		check "$name" $ok "$log"
	done; done; done
else
	echo "lftp not found: skipped (install it, or set LFTP=...)"
fi

# ---------------------------------------------------------------- curl (OpenSSL)
if [ -n "$CURL" ]; then
	echo "curl: $("$CURL" --version | head -1)"
	for mode in explicit implicit; do for pasv in on off; do for tls in 1.3 1.2; do
		name="curl $mode passive=$pasv TLS$tls"
		if [ $mode = explicit ]; then base="ftp://127.0.0.1:$EXPLICIT_PORT"; opts=(--ssl-reqd); else base="ftps://127.0.0.1:$IMPLICIT_PORT"; opts=(); fi
		[ $pasv = off ] && opts+=(-P -)
		if [ $tls = 1.2 ]; then opts+=(--tlsv1.2 --tls-max 1.2); else opts+=(--tlsv1.3); fi
		common=(-sS -k -u anonymous:x --max-time 60 "${opts[@]}")
		up="up-curl-$mode-$pasv-$tls.bin"
		dl="$WORK/local/curl-$mode-$pasv-$tls.bin"
		head -c 400000 "$ROOT/pub/big.bin" > "$dl.part"
		log="$WORK/curl.log"
		{
			"$CURL" "${common[@]}" "$base/pub/" &&
			"$CURL" "${common[@]}" -o "$dl" "$base/pub/big.bin" &&
			"$CURL" "${common[@]}" -T "$WORK/local/upload.bin" "$base/pub/$up" &&
			"$CURL" "${common[@]}" -C - -o "$dl.part" "$base/pub/big.bin" &&
			"$CURL" "${common[@]}" -v -o /dev/null "$base/pub/hello.txt" 2>&1
		} > "$log" 2>&1
		rc=$?
		ok=1
		if [ $rc = 0 ] && grep -q 'big.bin' "$log" \
			&& [ "$(sum "$dl")" = "$(sum "$ROOT/pub/big.bin")" ] \
			&& [ "$(sum "$ROOT/pub/$up")" = "$(sum "$WORK/local/upload.bin")" ] \
			&& [ "$(sum "$dl.part")" = "$(sum "$ROOT/pub/big.bin")" ]; then ok=0; fi
		check "$name" $ok "$log"
		# OpenSSL reports when the data connection resumed the control connection's session
		if grep -qi 're-using\|reusing' "$log"; then reuse=0; else reuse=1; fi
		check "$name data connection resumed the TLS session" $reuse "$log"
	done; done; done
else
	echo "curl not found: skipped"
fi

echo
echo "$PASS passed, $FAIL failed"
for f in "${FAILED[@]+"${FAILED[@]}"}"; do echo "  failed: $f"; done
[ $FAIL = 0 ]
