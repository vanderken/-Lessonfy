package com.csit321g1.bairus.lessonfy.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class DocumentExtractionServiceTests {

    private final DocumentExtractionService service = new DocumentExtractionService();

    @Test
    void renamedPdfWithWrongBytesGetsFriendlyError() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf",
                "this is not a pdf".getBytes(StandardCharsets.UTF_8));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertTrue(error.getReason().contains("not a valid PDF"));
    }

    @Test
    void encryptedPdfGetsFriendlyError() throws Exception {
        PDDocument document = new PDDocument();
        document.addPage(new PDPage());
        document.protect(new StandardProtectionPolicy("owner", "user", new AccessPermission()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        document.close();

        MockMultipartFile file = new MockMultipartFile("file", "locked.pdf", "application/pdf", out.toByteArray());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertTrue(error.getReason().contains("password-protected"));
    }

    @Test
    void fakeDocxAndPptxGetFriendlyErrors() throws Exception {
        ResponseStatusException docxError = assertThrows(ResponseStatusException.class,
                () -> service.extract(zip("fake.docx", "word/document.xml", "not real xml")));
        assertTrue(docxError.getReason().contains("could not be read"));

        ResponseStatusException pptxError = assertThrows(ResponseStatusException.class,
                () -> service.extract(zip("fake.pptx", "ppt/presentation.xml", "not real xml")));
        assertTrue(pptxError.getReason().contains("could not be read"));
    }

    @Test
    void officeZipWithMissingMainPartIsRejected() throws Exception {
        MockMultipartFile file = zip("broken.docx", "word/other.xml", "<xml/>");

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertTrue(error.getReason().contains("missing its main content"));
    }

    @Test
    void officeZipWithUnsafePathIsRejected() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("../escape.txt", "bad path");
        entries.put("word/document.xml", "<xml/>");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.extract(zip("unsafe.docx", entries)));

        assertTrue(error.getReason().contains("unsafe internal paths"));
    }

    @Test
    void officeZipWithTooManyPartsIsRejected() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("word/document.xml", "<xml/>");
        for (int i = 0; i < 3001; i++) {
            entries.put("word/part-" + i + ".xml", "x");
        }

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.extract(zip("huge.docx", entries)));

        assertTrue(error.getReason().contains("too many internal parts"));
    }

    @Test
    void hiddenControlCharactersAreStrippedFromText() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "Hello\u200B\u0001 world".getBytes(StandardCharsets.UTF_8));

        ExtractedDocument document = assertDoesNotThrow(() -> service.extract(file));

        assertEquals("Hello world", document.text());
        assertFalse(document.text().contains("\u200B"));
        assertFalse(document.text().contains("\u0001"));
    }

    @Test
    void oversizedFileIsRejectedBeforeExtraction() {
        byte[] tooLarge = new byte[(int) DocumentExtractionService.MAX_FILE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "large.txt", "text/plain", tooLarge);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertTrue(error.getReason().contains("too large"));
    }

    private static MockMultipartFile zip(String name, String entry, String content) throws Exception {
        return zip(name, Map.of(entry, content));
    }

    private static MockMultipartFile zip(String name, Map<String, String> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", name, "application/octet-stream", out.toByteArray());
    }
}
