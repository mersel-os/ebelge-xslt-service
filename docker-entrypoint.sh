#!/bin/sh
# ══════════════════════════════════════════════════════════════════════
# MERSEL.Services.XsltService — Docker Entrypoint
# ══════════════════════════════════════════════════════════════════════
# Root olarak başlar, volume mount izinlerini düzeltir ve
# su-exec ile non-root kullanıcıya (appuser) geçer.

set -e

ASSET_DIR="${XSLT_ASSETS_EXTERNAL_PATH:-/opt/xslt-assets}"

# Root olarak çalışıyorsa volume izinlerini düzelt
if [ "$(id -u)" = "0" ]; then
    # Asset dizini yoksa oluştur
    mkdir -p "$ASSET_DIR"

    # Sahipliği appuser'a ver (volume mount sonrası gerekli)
    chown -R appuser:appgroup "$ASSET_DIR" 2>/dev/null || {
        echo "UYARI: $ASSET_DIR sahipliği değiştirilemedi — GIB sync yazma hatası verebilir"
    }

    # Auto-generated alt dizinlerini oluştur
    mkdir -p "$ASSET_DIR/auto-generated/schematron" 2>/dev/null || true
    mkdir -p "$ASSET_DIR/auto-generated/schema" 2>/dev/null || true
    chown -R appuser:appgroup "$ASSET_DIR/auto-generated" 2>/dev/null || true

    # Non-root kullanıcıya geç ve uygulamayı başlat
    exec su-exec appuser java $JAVA_OPTS -jar app.jar "$@"
else
    # Zaten non-root ise doğrudan başlat
    exec java $JAVA_OPTS -jar app.jar "$@"
fi
