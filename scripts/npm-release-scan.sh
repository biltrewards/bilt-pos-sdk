#!/usr/bin/env bash
# Scans @biltorg/* tarballs from the bilt Artifact Registry npm repo (lifecycle-script
# check, osv-scanner, npm audit) and stages the passing ones for the publish-npmjs
# job: <work-dir>/out/packages.txt ("<spec> <filename>" per line) plus the tarballs.
# Already-published versions are skipped, making re-runs idempotent. Artifact
# Registry does not scan standalone npm packages, so these checks are the gate.
#
# Usage: npm-release-scan.sh <work-dir>
# Required env: PACKAGES (comma-separated name@version specs).
set -euo pipefail

AR_REGISTRY=https://us-central1-npm.pkg.dev/single-scholar-280421/bilt/
NPMJS_REGISTRY=https://registry.npmjs.org/
OSV_VERSION=v2.7.0
OSV_SHA256=160c6253e2922e9e0a6a9771c5c4c40a4b3c6090b34a3cdd5e80c96cc2e4218b

WORK_DIR=$1
mkdir -p "$WORK_DIR"
WORK_DIR=$(realpath "$WORK_DIR")
umask 077

# --- validate input (trust boundary) ---
IFS=',' read -r -a RAW_SPECS <<<"$PACKAGES"
# dedupe preserving first-seen order
SPECS=()
declare -A SEEN
for spec in "${RAW_SPECS[@]}"; do
  if [ -n "${SEEN[$spec]:-}" ]; then
    echo "duplicate spec $spec, ignoring"
    continue
  fi
  SEEN[$spec]=1
  SPECS+=("$spec")
done
if [ "${#SPECS[@]}" -eq 0 ]; then
  echo "PACKAGES is empty"
  exit 1
fi
for spec in "${SPECS[@]}"; do
  if [[ ! "$spec" =~ ^@biltorg/[a-z0-9][a-z0-9._-]*@[0-9]+\.[0-9]+\.[0-9]+([-+][0-9A-Za-z.+-]+)?$ ]]; then
    echo "invalid package spec: $spec"
    exit 1
  fi
done
echo "scanning ${#SPECS[@]} package(s): ${SPECS[*]}"

# --- separate npm userconfigs: AR (authenticated) and npmjs (read-only) ---
AR_RC="$WORK_DIR/ar-npmrc"
NPMJS_PUBLIC_RC="$WORK_DIR/npmjs-public-npmrc"

AR_TOKEN=$(gcloud auth print-access-token)
cat >"$AR_RC" <<EOF
@biltorg:registry=${AR_REGISTRY}
//us-central1-npm.pkg.dev/single-scholar-280421/bilt/:_authToken=${AR_TOKEN}
EOF
# token-less config for read-only npmjs calls; the npm token lives in GitHub
# secrets and only the publish-npmjs job ever sees it.
cat >"$NPMJS_PUBLIC_RC" <<EOF
registry=${NPMJS_REGISTRY}
EOF

# --- install osv-scanner (pinned + checksum-verified) ---
OSV_SCANNER="$WORK_DIR/osv-scanner"
curl -fsSL -o "$OSV_SCANNER" \
  "https://github.com/google/osv-scanner/releases/download/${OSV_VERSION}/osv-scanner_linux_amd64"
echo "${OSV_SHA256}  $OSV_SCANNER" | sha256sum -c -
chmod +x "$OSV_SCANNER"

already_published() {
  npm view "$1" version --userconfig "$NPMJS_PUBLIC_RC" 2>/dev/null || true
}

# --- scan ALL packages before staging ANY ---
declare -A SKIPPED TARBALL
for i in "${!SPECS[@]}"; do
  spec="${SPECS[$i]}"
  existing=$(already_published "$spec")
  if [ -n "$existing" ]; then
    echo "$spec already published ($existing), skipping"
    SKIPPED[$spec]=1
    continue
  fi

  pkg_dir="$WORK_DIR/pkg-$i"
  mkdir -p "$pkg_dir"

  tarball=$(npm pack "$spec" --userconfig "$AR_RC" --pack-destination "$pkg_dir" |
    tail -n 1 | tr -d '[:space:]')
  tarball="$pkg_dir/$tarball"
  echo "downloaded $tarball"

  # the tarball is untrusted: extract only the manifest, nothing else is needed
  mkdir -p "$pkg_dir/src"
  tar -xzf "$tarball" -C "$pkg_dir/src" --no-same-owner --no-same-permissions package/package.json
  if [ ! -f "$pkg_dir/src/package/package.json" ] || [ -L "$pkg_dir/src/package/package.json" ]; then
    echo "extracted manifest for $spec is missing or not a regular file"
    exit 1
  fi
  # shellcheck disable=SC2016
  node -e '
    const p = require(process.argv[1]);
    if (`${p.name}@${p.version}` !== process.argv[2]) {
      console.error(`package.json ${p.name}@${p.version} != requested ${process.argv[2]}`);
      process.exit(1);
    }
  ' "$pkg_dir/src/package/package.json" "$spec"

  # shellcheck disable=SC2016
  node -e '
    const s = require(process.argv[1]).scripts || {};
    const bad = ["preinstall", "install", "postinstall", "prepare"].filter(k => k in s);
    if (bad.length) {
      console.error(`${process.argv[2]} has forbidden lifecycle scripts: ${bad.join(", ")}`);
      process.exit(1);
    }
  ' "$pkg_dir/src/package/package.json" "$spec"

  # npm must never run inside the extracted package dir: a package .npmrc in
  # there could leak env vars. Scan a bare copy of package.json instead.
  mkdir -p "$pkg_dir/lock"
  cp "$pkg_dir/src/package/package.json" "$pkg_dir/lock/package.json"
  (
    cd "$pkg_dir/lock"
    npm pkg delete devDependencies
    npm install --package-lock-only --ignore-scripts --userconfig "$AR_RC"
  )

  set +e
  "$OSV_SCANNER" scan source --lockfile "$pkg_dir/lock/package-lock.json" \
    --format json --output-file "$pkg_dir/osv.json"
  osv_code=$?
  set -e
  case "$osv_code" in
    0 | 1 | 128) ;;
    *)
      echo "osv-scanner failed with exit code $osv_code"
      exit 1
      ;;
  esac
  # shellcheck disable=SC2016
  node -e '
    const data = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
    const criticals = [];
    for (const r of data.results || [])
      for (const p of r.packages || [])
        for (const g of p.groups || []) {
          const sev = parseFloat(g.max_severity);
          if (!isNaN(sev) && sev >= 9.0)
            criticals.push(`${p.package.name}@${p.package.version}: ${(g.ids || []).join(", ")} (severity ${g.max_severity})`);
        }
    if (criticals.length) {
      console.error(`critical vulnerabilities in ${process.argv[2]}:\n` + criticals.join("\n"));
      process.exit(1);
    }
  ' "$pkg_dir/osv.json" "$spec"

  (
    cd "$pkg_dir/lock"
    npm audit --omit=dev --audit-level=critical --userconfig "$NPMJS_PUBLIC_RC"
  )

  TARBALL[$spec]=$tarball
done

# --- stage the scanned tarballs for the publish job ---
mkdir -p "$WORK_DIR/out"
for spec in "${SPECS[@]}"; do
  if [ -n "${SKIPPED[$spec]:-}" ]; then
    continue
  fi
  tgz="${TARBALL[$spec]}"
  cp "$tgz" "$WORK_DIR/out/"
  echo "$spec $(basename "$tgz")" >>"$WORK_DIR/out/packages.txt"
done
touch "$WORK_DIR/out/packages.txt"

echo "scanned: $(wc -l <"$WORK_DIR/out/packages.txt") tarball(s) staged"
skipped=()
for spec in "${SPECS[@]}"; do
  [ -n "${SKIPPED[$spec]:-}" ] && skipped+=("$spec")
done
echo "skipped: ${skipped[*]:-none}"
