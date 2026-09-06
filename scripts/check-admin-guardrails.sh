#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MAVEN=(mvn)
if [ -x "$ROOT_DIR/mvnw" ]; then
    MAVEN=("$ROOT_DIR/mvnw")
fi

FULL=0
if [ "${1:-}" = "--full" ]; then
    FULL=1
elif [ -n "${1:-}" ]; then
    echo "Usage: scripts/check-admin-guardrails.sh [--full]" >&2
    exit 2
fi

section() {
    printf '\n## %s\n' "$1"
}

scan() {
    local title="$1"
    shift
    section "$title"
    set +e
    rg -n "$@"
    local status=$?
    set -e
    if [ "$status" -eq 1 ]; then
        echo "No matches."
        return 0
    fi
    return "$status"
}

section "Admin guardrail scans"
echo "Review matches before changing code. Some matches can be comments, test data, SVG ids, or intentional reset styles."

scan "Frontend i18n access and fallback candidates" \
    'getRes\(\)\[|\bres\[|adminI18nAliases|getRes\(\)(\.[A-Za-z0-9_]+)+\s*\|\|\s*["'\'']' \
    src/main/frontend/src \
    --glob '*.ts' \
    --glob '*.tsx'

scan "Frontend suppression and debug candidates" \
    'eslint-disable|@ts-ignore|@ts-expect-error|console\.log|debugger' \
    src/main/frontend/src \
    --glob '*.ts' \
    --glob '*.tsx'

scan "Frontend visible Chinese outside i18n candidates" \
    '[\u4e00-\u9fff]' \
    src/main/frontend/src \
    --glob '*.ts' \
    --glob '*.tsx' \
    --glob '!src/main/frontend/src/i18n/**'

THEME_PATTERN='borderRadius:\s*[0-9]+|border-radius:\s*[0-9]+px|1px solid|border:\s*["'\'']|borderBottom:\s*["'\'']|borderTop:\s*["'\'']|borderLeft:\s*["'\'']|borderRight:\s*["'\'']|color:\s*["'\''](#1677ff|#1890ff|blue)["'\'']'
scan "Frontend theme hard-coded style candidates" \
    "$THEME_PATTERN" \
    src/main/frontend/src/common \
    src/main/frontend/src/components \
    src/main/frontend/src/layout \
    --glob '*.ts' \
    --glob '*.tsx'

scan "Admin DTO candidates for native image registration review" \
    'class .*Response|class .*Request|class .*VO' \
    src/main/java/com/zrlog/admin/business

scan "Native image registration anchors" \
    'gsonNativeAgentByClazz|getResources\(' \
    src/main/java/com/zrlog/admin/util/AdminNativeImageUtils.java

section "Admin native JSON registration completeness"
NATIVE_JSON_FILE="src/main/java/com/zrlog/admin/util/AdminNativeImageUtils.java"
NATIVE_JSON_STATUS=0
check_native_json_class() {
    local class_name="$1"
    if ! rg -q -F "${class_name}.class" "$NATIVE_JSON_FILE"; then
        printf '%s missing %s.class\n' "$NATIVE_JSON_FILE" "$class_name"
        NATIVE_JSON_STATUS=1
    fi
}
is_native_json_container() {
    case "$1" in
        SafeRequestUrl|AdminSsePayloads)
            # Validation/factory containers are never serialized. Serialized
            # AdminSsePayloads nested classes are checked separately below.
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}
for dto_dir in \
    src/main/java/com/zrlog/admin/business/rest/base \
    src/main/java/com/zrlog/admin/business/rest/request \
    src/main/java/com/zrlog/admin/business/rest/response; do
    for file in "$dto_dir"/*.java; do
        class_name="$(basename "$file" .java)"
        if is_native_json_container "$class_name"; then
            continue
        fi
        check_native_json_class "$class_name"
    done
done
for file in src/main/java/com/zrlog/admin/business/rest/response/*.java; do
    while IFS= read -r line; do
        class_name="$(printf '%s' "$line" | sed -E 's/.*class ([A-Za-z0-9_]+).*/\1/')"
        if ! rg -q -F ".${class_name}.class" "$NATIVE_JSON_FILE"; then
            printf '%s missing nested %s.class\n' "$NATIVE_JSON_FILE" "$class_name"
            NATIVE_JSON_STATUS=1
        fi
    done < <(rg 'public static (final )?class [A-Za-z0-9_]+' "$file")
done
for class_name in \
    PageData \
    LinkDTO \
    LogNavDTO \
    TypeDTO \
    CommentDTO \
    BaseTemplateVO \
    TemplateVO.TemplateConfigVO \
    AIModelCapability \
    AIModelEntry \
    AIProviderType \
    LockVO; do
    check_native_json_class "$class_name"
done
if [ "$NATIVE_JSON_STATUS" -eq 0 ]; then
    echo "OK"
else
    exit "$NATIVE_JSON_STATUS"
fi

section "Backend audit i18n completeness"
AUDIT_KEYS="$(rg -o '"admin\.audit\.action\.[^"]+"' src/main/java/com/zrlog/admin/business/type/AdminAuditAction.java | tr -d '"' | sort -u)"
AUDIT_I18N_STATUS=0
for locale in src/main/resources/i18n/admin_backend_*.properties; do
    missing=0
    while IFS= read -r key; do
        if [ -n "$key" ] && ! rg -q -F "${key}=" "$locale"; then
            printf '%s missing %s\n' "$locale" "$key"
            missing=1
            AUDIT_I18N_STATUS=1
        fi
    done <<< "$AUDIT_KEYS"
    if [ "$missing" -eq 0 ]; then
        printf '%s OK\n' "$locale"
    fi
done
if [ "$AUDIT_I18N_STATUS" -ne 0 ]; then
    exit "$AUDIT_I18N_STATUS"
fi

scan "Admin SQL portability candidates" \
    'DATE_FORMAT|UNIX_TIMESTAMP|FROM_UNIXTIME|strftime|information_schema|pg_stat_user_tables|OPTIMIZE TABLE|VACUUM|group by|GROUP BY|select count|SELECT count|count\(1\)' \
    src/main/java/com/zrlog/admin/business \
    src/main/java/com/zrlog/admin/web \
    --glob '*.java'

section "Build artifact diff candidates"
BUILD_ARTIFACT_DIFF="$(git diff --name-only -- src/main/resources/admin src/main/frontend/build static/changelog || true)"
if [ -n "$BUILD_ARTIFACT_DIFF" ]; then
    printf '%s\n' "$BUILD_ARTIFACT_DIFF"
else
    echo "No matches."
fi

if [ "$FULL" -eq 1 ]; then
    section "Backend compile"
    "${MAVEN[@]}" -q -DskipTests compile

    section "API documentation contract"
    (cd src/main/frontend && yarn api-docs:check)

    section "Frontend type check"
    (cd src/main/frontend && yarn type-check)

    section "Diff whitespace check"
    git diff --check
fi
