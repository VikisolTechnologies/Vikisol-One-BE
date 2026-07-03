package com.vikisol.one.common.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

// Renders our own branded XHTML (offer letters etc) into a real PDF - no external service, no
// extra account/API key needed. Keep markup XHTML-strict (self-closing tags, no bare & etc).
@Service
public class PdfService {

    public byte[] renderPdf(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Could not generate PDF: " + e.getMessage(), e);
        }
    }
}
