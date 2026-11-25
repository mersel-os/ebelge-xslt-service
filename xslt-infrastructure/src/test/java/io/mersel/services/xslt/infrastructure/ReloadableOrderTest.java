package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.Reloadable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.OrderUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssetRegistry}'ye enjekte edilen {@link Reloadable} listesinin sırası.
 * <p>
 * Spring {@code List<Reloadable>} enjeksiyonunu {@link AnnotationAwareOrderComparator} ile sıralar.
 * Özel belge türü tanımları XSD derlemesinden önce yüklenmeli; aksi halde XSD bileşeni bir önceki
 * reload'un tanımlarını derler.
 */
@DisplayName("Reloadable sırası")
class ReloadableOrderTest {

    @Test
    @DisplayName("ozel_belge_turleri_ilk_sirada_yuklenir")
    void ozel_belge_turleri_ilk_sirada_yuklenir() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        List<Class<?>> reloadables = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("io.mersel.services.xslt")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (Reloadable.class.isAssignableFrom(type)) {
                reloadables.add(type);
            }
        }
        assertThat(reloadables).as("tarama bileşenleri bulmalı")
                .contains(CustomDocumentTypeRegistry.class, JaxpSchemaValidator.class)
                .hasSizeGreaterThan(2);
        assertThat(reloadables).as("sıralama örnek değil sınıf üzerinden yapılıyor; Ordered uygulayan olmamalı")
                .noneMatch(Ordered.class::isAssignableFrom);

        // Tarama sırası alfabetik olabilir ve tesadüfen doğru sonucu verir: sıralamayı tersinden başlat.
        reloadables.sort(Comparator.comparing(Class::getName, Comparator.reverseOrder()));
        AnnotationAwareOrderComparator.sort(reloadables);

        assertThat(reloadables.get(0)).isEqualTo(CustomDocumentTypeRegistry.class);
        assertThat(OrderUtils.getOrder(reloadables.get(1), Ordered.LOWEST_PRECEDENCE))
                .as("başka bir bileşen ilk sırayı paylaşmamalı")
                .isGreaterThan(Ordered.HIGHEST_PRECEDENCE);
    }
}
