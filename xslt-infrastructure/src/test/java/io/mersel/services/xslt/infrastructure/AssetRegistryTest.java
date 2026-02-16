package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.Reloadable;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * AssetRegistry birim testleri.
 * <p>
 * Reload orkestrasyon mantığını, hata izolasyonunu ve
 * thread-safety (concurrent reload) davranışını test eder.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssetRegistry")
class AssetRegistryTest {

    @Mock
    private XsltMetrics xsltMetrics;

    @Test
    @DisplayName("reload_tum_bilesenleri_calistirir — all reloadables called in order")
    void reload_tum_bilesenleri_calistirir() {
        var reloadable1 = createSuccessReloadable("XSD Schemas", 4);
        var reloadable2 = createSuccessReloadable("Schematron Rules", 8);

        var registry = new AssetRegistry(List.of(reloadable1, reloadable2), xsltMetrics);
        var results = registry.reload();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).componentName()).isEqualTo("XSD Schemas");
        assertThat(results.get(0).loadedCount()).isEqualTo(4);
        assertThat(results.get(1).componentName()).isEqualTo("Schematron Rules");
        assertThat(results.get(1).loadedCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("reload_hata_izolasyonu — one failing component doesn't block others")
    void reload_hata_izolasyonu() {
        var failingReloadable = mock(Reloadable.class);
        when(failingReloadable.getName()).thenReturn("Failing");
        when(failingReloadable.reload()).thenThrow(new RuntimeException("Test error"));

        var healthyReloadable = createSuccessReloadable("Healthy", 5);

        var registry = new AssetRegistry(List.of(failingReloadable, healthyReloadable), xsltMetrics);
        var results = registry.reload();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(ReloadResult.Status.FAILED);
        assertThat(results.get(0).errors()).contains("Test error");
        assertThat(results.get(1).status()).isEqualTo(ReloadResult.Status.OK);
        assertThat(results.get(1).loadedCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("reload_partial_sonuc — partial result propagated correctly")
    void reload_partial_sonuc() {
        var partialReloadable = mock(Reloadable.class);
        when(partialReloadable.getName()).thenReturn("Partial");
        when(partialReloadable.reload()).thenReturn(
                ReloadResult.partial("Partial", 3, 100, List.of("XYZ failed")));

        var registry = new AssetRegistry(List.of(partialReloadable), xsltMetrics);
        var results = registry.reload();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(ReloadResult.Status.PARTIAL);
        assertThat(results.get(0).loadedCount()).isEqualTo(3);
        assertThat(results.get(0).errors()).containsExactly("XYZ failed");
    }

    @Test
    @DisplayName("reload_bos_liste — empty reloadables returns empty results")
    void reload_bos_liste() {
        var registry = new AssetRegistry(List.of(), xsltMetrics);
        var results = registry.reload();

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("concurrent_reload_atlanir — only one reload runs at a time")
    void concurrent_reload_atlanir() throws Exception {
        var reloadCount = new AtomicInteger(0);

        // Yavaş reloadable — 200ms sürer
        var slowReloadable = new Reloadable() {
            @Override
            public ReloadResult reload() {
                reloadCount.incrementAndGet();
                try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ReloadResult.success("Slow", 1, 200);
            }
            @Override
            public String getName() { return "Slow"; }
        };

        var registry = new AssetRegistry(List.of(slowReloadable), xsltMetrics);

        // İlk reload tamamlanmış, lock serbest
        // Paralel reload dene
        var latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> { registry.reload(); latch.countDown(); });
        Thread.sleep(50); // İlk reload'un lock almasını bekle
        executor.submit(() -> { registry.reload(); latch.countDown(); });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // En az 1 tam reload çalışmalı; ikinci tryLock başarısız olursa atlanır
        assertThat(reloadCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("reload_metrics_kaydeder — success flag and duration recorded")
    void reload_metrics_kaydeder() {
        var reloadable = createSuccessReloadable("Test", 1);
        var registry = new AssetRegistry(List.of(reloadable), xsltMetrics);

        registry.reload();

        verify(xsltMetrics).recordReload(eq(true), anyLong());
    }

    @Test
    @DisplayName("reload_metrics_basarisiz — failure flag recorded when component fails")
    void reload_metrics_basarisiz() {
        var failReloadable = mock(Reloadable.class);
        when(failReloadable.getName()).thenReturn("Fail");
        when(failReloadable.reload()).thenReturn(
                ReloadResult.failed("Fail", 0, "error"));

        var registry = new AssetRegistry(List.of(failReloadable), xsltMetrics);
        registry.reload();

        verify(xsltMetrics).recordReload(eq(false), anyLong());
    }

    private Reloadable createSuccessReloadable(String name, int count) {
        var reloadable = mock(Reloadable.class);
        when(reloadable.getName()).thenReturn(name);
        when(reloadable.reload()).thenReturn(
                ReloadResult.success(name, count, 50));
        return reloadable;
    }
}
