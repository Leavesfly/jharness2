#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper startup script for UN*X
# Adapted for JHarness2 project
# ----------------------------------------------------------------------------

set -e

# Resolve project base directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
MAVEN_WRAPPER_PROPERTIES="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"
MAVEN_WRAPPER_DIR="${HOME}/.m2/wrapper/dists"

# ─── Resolve JAVA_HOME ──────────────────────────────────────────────────────
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ]; then
        echo "ERROR: JAVA_HOME is set to \"$JAVA_HOME\" but no java executable found at $JAVACMD" >&2
        exit 1
    fi
else
    JAVACMD="$(command -v java)" || true
    if [ -z "$JAVACMD" ]; then
        echo "ERROR: java not found. Install JDK 17+ or set JAVA_HOME." >&2
        exit 1
    fi
fi

# Verify Java version >= 17
JAVA_VER=$("$JAVACMD" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
    echo "ERROR: JHarness2 requires Java 17+, found Java $JAVA_VER" >&2
    exit 1
fi

# ─── Read wrapper properties ────────────────────────────────────────────────
if [ ! -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
    echo "ERROR: $MAVEN_WRAPPER_PROPERTIES not found" >&2
    exit 1
fi

DISTRIBUTION_URL=$(grep '^distributionUrl=' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
if [ -z "$DISTRIBUTION_URL" ]; then
    echo "ERROR: distributionUrl not found in $MAVEN_WRAPPER_PROPERTIES" >&2
    exit 1
fi

# ─── Resolve Maven installation ─────────────────────────────────────────────
MAVEN_VERSION=$(echo "$DISTRIBUTION_URL" | sed -n 's|.*apache-maven-\([0-9.]*\)-bin.*|\1|p')
if [ -z "$MAVEN_VERSION" ]; then
    echo "ERROR: Cannot parse Maven version from URL: $DISTRIBUTION_URL" >&2
    exit 1
fi

MAVEN_HOME_DIR="$MAVEN_WRAPPER_DIR/apache-maven-$MAVEN_VERSION"
MVN_CMD="$MAVEN_HOME_DIR/bin/mvn"

# Download Maven if not present
if [ ! -x "$MVN_CMD" ]; then
    echo "Maven $MAVEN_VERSION not found, downloading..."
    mkdir -p "$MAVEN_WRAPPER_DIR"
    TEMP_ARCHIVE="$(mktemp)"

    # Download with curl or wget
    if command -v curl > /dev/null 2>&1; then
        HTTP_CODE=$(curl -fsSL -w "%{http_code}" -o "$TEMP_ARCHIVE" "$DISTRIBUTION_URL" 2>/dev/null) || true
        if [ "$HTTP_CODE" != "200" ]; then
            rm -f "$TEMP_ARCHIVE"
            echo "ERROR: Failed to download Maven (HTTP $HTTP_CODE) from $DISTRIBUTION_URL" >&2
            exit 1
        fi
    elif command -v wget > /dev/null 2>&1; then
        if ! wget -q -O "$TEMP_ARCHIVE" "$DISTRIBUTION_URL" 2>/dev/null; then
            rm -f "$TEMP_ARCHIVE"
            echo "ERROR: Failed to download Maven from $DISTRIBUTION_URL" >&2
            exit 1
        fi
    else
        echo "ERROR: Neither curl nor wget found. Cannot download Maven." >&2
        exit 1
    fi

    # Extract
    if command -v unzip > /dev/null 2>&1; then
        unzip -q "$TEMP_ARCHIVE" -d "$MAVEN_WRAPPER_DIR"
    elif command -v tar > /dev/null 2>&1 && echo "$DISTRIBUTION_URL" | grep -q '\.tar\.gz$'; then
        tar -xzf "$TEMP_ARCHIVE" -C "$MAVEN_WRAPPER_DIR"
    else
        rm -f "$TEMP_ARCHIVE"
        echo "ERROR: unzip is required to extract Maven distribution" >&2
        exit 1
    fi

    rm -f "$TEMP_ARCHIVE"
    chmod +x "$MVN_CMD"
    echo "Maven $MAVEN_VERSION installed to $MAVEN_HOME_DIR"
fi

# ─── Execute Maven ──────────────────────────────────────────────────────────
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx512m}"
exec "$MVN_CMD" "$@"
