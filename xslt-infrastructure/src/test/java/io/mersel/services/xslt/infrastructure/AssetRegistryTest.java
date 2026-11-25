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
    @DisplayName("reload_surerken_gelen_istek_kaybolmaz — süren reload bitince yeni reload çalışır")
    void reload_surerken_gelen_istek_kaybolmaz() throws Exception {
        // Senaryo: dosya izleyicinin başlattığı reload sürerken GİB sync son paketini yazıp
        // reload ister. Bu istek atlanırsa son paket bir sonraki değişikliğe kadar yüklenmez.
        var blocking = new BlockingReloadable();
        var registry = new AssetRegistry(List.of(blocking), xsltMetrics);

        var first = new ReloadCall(registry);
        first.start();
        assertThat(blocking.firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

        var second = new ReloadCall(registry);
        second.start();
        awaitParked(second);

        blocking.releaseFirst.countDown();
        first.join(5000);
        second.join(5000);

        assertThat(blocking.invocations.get()).as("ikinci istek ayrı bir reload çalıştırmalı").isEqualTo(2);
        assertThat(first.results).singleElement().extracting(ReloadResult::loadedCount).isEqualTo(1);
        assertThat(second.results).as("ikinci çağrı kendi isteğinden sonra başlayan reload'un sonucunu almalı")
                .singleElement().extracting(ReloadResult::loadedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("bekleyen_istekler_tek_reloadda_birlesir — aynı anda bekleyen çağrılar için tek ek reload")
    void bekleyen_istekler_tek_reloadda_birlesir() throws Exception {
        var blocking = new BlockingReloadable();
        var registry = new AssetRegistry(List.of(blocking), xsltMetrics);

        var first = new ReloadCall(registry);
        first.start();
        assertThat(blocking.firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

        var second = new ReloadCall(registry);
        var third = new ReloadCall(registry);
        second.start();
        third.start();
        awaitParked(second);
        awaitParked(third);

        blocking.releaseFirst.countDown();
        for (ReloadCall call : List.of(first, second, third)) {
            call.join(5000);
        }

        assertThat(blocking.invocations.get()).isEqualTo(2);
        assertThat(second.results).isEqualTo(third.results);
        assertThat(second.results).singleElement().extracting(ReloadResult::loadedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("ardisik_reloadlar_her_seferinde_calisir")
    void ardisik_reloadlar_her_seferinde_calisir() {
        var counter = new AtomicInteger();
        Reloadable counting = new Reloadable() {
            @Override
            public ReloadResult reload() {
                return ReloadResult.success("Counting", counter.incrementAndGet(), 0);
            }

            @Override
            public String getName() {
                return "Counting";
            }
        };
        var registry = new AssetRegistry(List.of(counting), xsltMetrics);

        registry.reload();
        List<ReloadResult> second = registry.reload();

        assertThat(counter.get()).isEqualTo(2);
        assertThat(second).singleElement().extracting(ReloadResult::loadedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("stack_overflow_veren_bilesen_failed_olur_digerleri_calisir")
    void stack_overflow_veren_bilesen_failed_olur_digerleri_calisir() {
        // Aşırı derin iç içe bir XSD derlenirken StackOverflowError oluşabilir.
        var overflowing = mock(Reloadable.class);
        when(overflowing.getName()).thenReturn("Overflowing");
        when(overflowing.reload()).thenThrow(new StackOverflowError());
        var healthy = createSuccessReloadable("Healthy", 3);

        var results = new AssetRegistry(List.of(overflowing, healthy), xsltMetrics).reload();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(ReloadResult.Status.FAILED);
        assertThat(results.get(0).errors()).singleElement().asString().contains("StackOverflowError");
        assertThat(results.get(1).status()).as("sonraki bileşen yine çalışmalı").isEqualTo(ReloadResult.Status.OK);
    }

    @Test
    @DisplayName("yarida_kalan_reloada_baglanan_cagri_eski_sonucu_almaz")
    void yarida_kalan_reloada_baglanan_cagri_eski_sonucu_almaz() throws Exception {
        // 1. çağrı başarılı bir reload'u bekletir; 2. ve 3. çağrı kilitte bekler. Kilidi ilk alan
        // ikisini birden kapsayan reload'u başlatır ve bu reload bir Error ile ölür. Diğeri bu reload'a
        // bağlandığı için kendi reload'unu çalıştırmaz: önceki başarılı sonucu değil FAILED almalı.
        var dying = new DyingReloadable();
        var registry = new AssetRegistry(List.of(dying), xsltMetrics);

        var first = new ReloadCall(registry);
        first.start();
        assertThat(dying.firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

        var second = new ReloadCall(registry);
        var third = new ReloadCall(registry);
        second.start();
        third.start();
        awaitParked(second);
        awaitParked(third);

        dying.releaseFirst.countDown();
        for (ReloadCall call : List.of(first, second, third)) {
            call.join(5000);
            assertThat(call.isAlive()).isFalse();
        }

        assertThat(first.results).singleElement().extracting(ReloadResult::status).isEqualTo(ReloadResult.Status.OK);
        assertThat(dying.invocations.get()).as("bekleyen iki çağrı tek reload'da birleşmeli").isEqualTo(2);

        List<ReloadCall> waiting = List.of(second, third);
        assertThat(waiting).filteredOn(call -> call.failure != null)
                .as("ölen reload'u çalıştıran çağrı hatayı almalı")
                .singleElement().extracting(call -> call.failure).isInstanceOf(DyingError.class);
        ReloadCall coalesced = waiting.stream().filter(call -> call.failure == null).findFirst().orElseThrow();
        assertThat(coalesced.results).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(ReloadResult.Status.FAILED);
            assertThat(result.componentName()).isEqualTo(AssetRegistry.INTERRUPTED_COMPONENT);
            assertThat(result.errors()).singleElement().asString().contains("yarıda kesildi");
        });
    }

    private static final class DyingError extends Error {
        DyingError() {
            super("reload sırasında beklenmeyen hata");
        }
    }

    /** İlk çağrı serbest bırakılana kadar bekleyip başarılı döner; ikinci çağrı {@link DyingError} fırlatır. */
    private static final class DyingReloadable implements Reloadable {
        final AtomicInteger invocations = new AtomicInteger();
        final CountDownLatch firstEntered = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);

        @Override
        public ReloadResult reload() {
            int call = invocations.incrementAndGet();
            if (call == 1) {
                firstEntered.countDown();
                try {
                    releaseFirst.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ReloadResult.success("Dying", 1, 0);
            }
            throw new DyingError();
        }

        @Override
        public String getName() {
            return "Dying";
        }
    }

    /** İlk çağrıda serbest bırakılana kadar bekleyen; loadedCount = çağrı sırası. */
    private static final class BlockingReloadable implements Reloadable {
        final AtomicInteger invocations = new AtomicInteger();
        final CountDownLatch firstEntered = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);

        @Override
        public ReloadResult reload() {
            int call = invocations.incrementAndGet();
            if (call == 1) {
                firstEntered.countDown();
                try {
                    releaseFirst.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return ReloadResult.success("Blocking", call, 0);
        }

        @Override
        public String getName() {
            return "Blocking";
        }
    }

    private static final class ReloadCall extends Thread {
        private final AssetRegistry registry;
        volatile List<ReloadResult> results;
        volatile Throwable failure;

        ReloadCall(AssetRegistry registry) {
            this.registry = registry;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                results = registry.reload();
            } catch (Throwable t) {
                failure = t;
            }
        }
    }

    /** Çağrının reload kilidinde beklemeye geçtiğini (park) doğrular. */
    private static void awaitParked(Thread thread) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (thread.getState() != Thread.State.WAITING && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertThat(thread.getState()).as("reload çağrısı kilitte beklemeli").isEqualTo(Thread.State.WAITING);
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
