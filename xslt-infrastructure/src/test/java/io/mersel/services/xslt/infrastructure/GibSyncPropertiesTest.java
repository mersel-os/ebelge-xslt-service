package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.infrastructure.config.GibSyncProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GibSyncProperties birim testleri.
 * <p>
 * @PostConstruct validate() metodunun negatif/sıfır timeout değerlerini
 * varsayılana geri döndürdüğünü test eder.
 */
@DisplayName("GibSyncProperties")
class GibSyncPropertiesTest {

    @Test
    @DisplayName("validate_gecerli_degerler — positive timeouts unchanged")
    void validate_gecerli_degerler() throws Exception {
        var props = new GibSyncProperties();
        props.setConnectTimeoutMs(5000);
        props.setReadTimeoutMs(30000);

        invokeValidate(props);

        assertThat(props.getConnectTimeoutMs()).isEqualTo(5000);
        assertThat(props.getReadTimeoutMs()).isEqualTo(30000);
    }

    @Test
    @DisplayName("validate_sifir_connect_timeout — zero resets to default 10000")
    void validate_sifir_connect_timeout() throws Exception {
        var props = new GibSyncProperties();
        props.setConnectTimeoutMs(0);
        props.setReadTimeoutMs(60000);

        invokeValidate(props);

        assertThat(props.getConnectTimeoutMs()).isEqualTo(10000);
        assertThat(props.getReadTimeoutMs()).isEqualTo(60000);
    }

    @Test
    @DisplayName("validate_negatif_read_timeout — negative resets to default 60000")
    void validate_negatif_read_timeout() throws Exception {
        var props = new GibSyncProperties();
        props.setConnectTimeoutMs(5000);
        props.setReadTimeoutMs(-1);

        invokeValidate(props);

        assertThat(props.getConnectTimeoutMs()).isEqualTo(5000);
        assertThat(props.getReadTimeoutMs()).isEqualTo(60000);
    }

    @Test
    @DisplayName("validate_her_ikisi_negatif — both reset to defaults")
    void validate_her_ikisi_negatif() throws Exception {
        var props = new GibSyncProperties();
        props.setConnectTimeoutMs(-100);
        props.setReadTimeoutMs(-200);

        invokeValidate(props);

        assertThat(props.getConnectTimeoutMs()).isEqualTo(10000);
        assertThat(props.getReadTimeoutMs()).isEqualTo(60000);
    }

    @Test
    @DisplayName("varsayilan_degerler — defaults are positive")
    void varsayilan_degerler() {
        var props = new GibSyncProperties();

        assertThat(props.getConnectTimeoutMs()).isGreaterThan(0);
        assertThat(props.getReadTimeoutMs()).isGreaterThan(0);
        assertThat(props.isEnabled()).isTrue();
    }

    private void invokeValidate(GibSyncProperties props) throws Exception {
        Method validate = GibSyncProperties.class.getDeclaredMethod("validate");
        validate.setAccessible(true);
        validate.invoke(props);
    }
}
