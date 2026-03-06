package io.mersel.services.xslt.web.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TransformController.buildTransformCsp() birim testleri.
 */
@DisplayName("TransformController — Dinamik CSP")
class TransformControllerCspTest {

    @Test
    @DisplayName("Script yoksa CSP script-src 'none' olmalı")
    void shouldReturnScriptSrcNoneWhenNoScripts() {
        String csp = TransformController.buildTransformCsp(List.of());

        assertThat(csp).contains("script-src 'none'");
        assertThat(csp).contains("connect-src 'none'");
        assertThat(csp).contains("form-action 'none'");
        assertThat(csp).contains("img-src 'self' data: https:");
    }

    @Test
    @DisplayName("null hash listesi için CSP script-src 'none' olmalı")
    void shouldReturnScriptSrcNoneForNullHashes() {
        String csp = TransformController.buildTransformCsp(null);

        assertThat(csp).contains("script-src 'none'");
    }

    @Test
    @DisplayName("Tek script hash'i ile doğru CSP üretmeli")
    void shouldBuildCspWithSingleHash() {
        String csp = TransformController.buildTransformCsp(List.of("abc123def456"));

        assertThat(csp).contains("script-src 'sha256-abc123def456'");
        assertThat(csp).doesNotContain("script-src 'unsafe-inline'");
        assertThat(csp).contains("connect-src 'none'");
    }

    @Test
    @DisplayName("Birden fazla script hash'i ile doğru CSP üretmeli")
    void shouldBuildCspWithMultipleHashes() {
        String csp = TransformController.buildTransformCsp(List.of("hash1", "hash2", "hash3"));

        assertThat(csp).contains("'sha256-hash1'");
        assertThat(csp).contains("'sha256-hash2'");
        assertThat(csp).contains("'sha256-hash3'");
    }

    @Test
    @DisplayName("CSP her zaman connect-src 'none' ve form-action 'none' içermeli")
    void shouldAlwaysBlockConnectAndForm() {
        String cspWithScripts = TransformController.buildTransformCsp(List.of("hash1"));
        String cspWithoutScripts = TransformController.buildTransformCsp(List.of());

        for (String csp : List.of(cspWithScripts, cspWithoutScripts)) {
            assertThat(csp).contains("connect-src 'none'");
            assertThat(csp).contains("form-action 'none'");
            assertThat(csp).contains("frame-src 'none'");
            assertThat(csp).contains("object-src 'none'");
        }
    }
}
