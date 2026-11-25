package io.mersel.services.xslt.web.controllers;

import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.interfaces.ICustomDocumentTypeRegistry;
import io.mersel.services.xslt.application.models.CustomDocumentType;
import io.mersel.services.xslt.web.config.AdminAuthInterceptor;
import io.mersel.services.xslt.web.config.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("CustomDocumentTypeController")
class CustomDocumentTypeControllerTest {

    private final ICustomDocumentTypeRegistry registry = mock(ICustomDocumentTypeRegistry.class);
    private final CustomDocumentTypeController controller = new CustomDocumentTypeController(registry);

    @Test
    @DisplayName("yuklu_turleri_ada_gore_sirali_doner")
    void yuklu_turleri_ada_gore_sirali_doner() throws Exception {
        when(registry.getAll()).thenReturn(List.of(
                new CustomDocumentType("SAMPLE_VOUCHER", "Örnek makbuz", "urn:example:sample:voucher-1", "Voucher",
                        "custom/sample/maindoc/Sample-Voucher-1.0.xsd", SchematronValidationType.UBLTR_MAIN),
                new CustomDocumentType("NOTE", null, "urn:example:note-1", "Note", "custom/note/Note.xsd", null)));

        standaloneSetup(controller).build()
                .perform(get("/v1/admin/custom-document-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types.length()").value(2))
                .andExpect(jsonPath("$.types[0].id").value("NOTE"))
                .andExpect(jsonPath("$.types[0].schematron").doesNotExist())
                .andExpect(jsonPath("$.types[1].id").value("SAMPLE_VOUCHER"))
                .andExpect(jsonPath("$.types[1].description").value("Örnek makbuz"))
                .andExpect(jsonPath("$.types[1].rootElement").value("Voucher"))
                .andExpect(jsonPath("$.types[1].xsd").value("custom/sample/maindoc/Sample-Voucher-1.0.xsd"))
                .andExpect(jsonPath("$.types[1].schematron").value("UBLTR_MAIN"));
    }

    @Test
    @DisplayName("tanim_yoksa_bos_liste_doner")
    void tanim_yoksa_bos_liste_doner() throws Exception {
        when(registry.getAll()).thenReturn(List.of());

        standaloneSetup(controller).build()
                .perform(get("/v1/admin/custom-document-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types.length()").value(0));
    }

    @Test
    @DisplayName("token_olmadan_401_doner")
    void token_olmadan_401_doner() throws Exception {
        standaloneSetup(controller)
                .addInterceptors(new AdminAuthInterceptor(mock(AuthService.class)))
                .build()
                .perform(get("/v1/admin/custom-document-types"))
                .andExpect(status().isUnauthorized());
    }
}
