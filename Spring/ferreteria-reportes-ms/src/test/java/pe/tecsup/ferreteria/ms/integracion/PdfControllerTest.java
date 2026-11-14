package pe.tecsup.ferreteria.ms.integracion;
import org.junit.jupiter.api.Test;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class PdfControllerTest extends IntegracionBase {
    private String exportar(String ruta) throws Exception {
        byte[] bytes=mvc.perform(get(ruta).header("Authorization",admin()))
            .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".pdf")))
            .andReturn().getResponse().getContentAsByteArray();
        PdfReader reader=new PdfReader(bytes);
        try {
            assertTrue(reader.getNumberOfPages()>0);
            StringBuilder texto=new StringBuilder();
            var extractor=new PdfTextExtractor(reader);
            for(int i=1;i<=reader.getNumberOfPages();i++) texto.append(extractor.getTextFromPage(i));
            return texto.toString();
        } finally { reader.close(); }
    }
    @Test void pdfVentasContieneDatosYRegistraExportacion() throws Exception {
        String texto=exportar("/api/reportes/ventas-dia/pdf?fecha=2026-10-27");
        assertTrue(texto.contains("27/10/2026")); assertTrue(texto.contains("177"));
        assertEquals(1,eventos());
        assertEquals("REPORTE_EXPORTADO",jdbc.queryForObject("select tipo from ms_evento_auditoria",String.class));
        assertEquals("MS",jdbc.queryForObject("select origen from ms_evento_auditoria",String.class));
    }
    @Test void pdfStockContieneProductosYNoModificaStock() throws Exception {
        var antes=jdbc.queryForList("select id,stock_actual from productos_producto order by id");
        String texto=exportar("/api/reportes/stock-critico/pdf");
        assertTrue(texto.contains("MAR-001")); assertTrue(texto.contains("CLA-001")); assertFalse(texto.contains("INA-001"));
        assertEquals(antes,jdbc.queryForList("select id,stock_actual from productos_producto order by id"));
        assertEquals(1,eventos());
    }
    @Test void exportacionDenegadaNoGeneraAuditoria() throws Exception {
        mvc.perform(get("/api/reportes/stock-critico/pdf").header("Authorization",vendedor()))
            .andExpect(status().isForbidden());
        assertEquals(0,eventos());
    }
}
