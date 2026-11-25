package com.csit321g1.bairus.lessonfy.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class LessonConversionServiceTests {

    private LessonConversionService service;

    @BeforeEach
    void setUp() {
        // Semantic tests must never touch AI. An empty chain proves that.
        AiProviderChain chain = new AiProviderChain(List.of(), "", Duration.ofSeconds(5));
        service = new LessonConversionService(new DocumentExtractionService(), new SemanticLessonService(), chain);
    }

    @Test
    void txtDocumentBecomesStructuredLessonWithoutAi() {
        String text = """
                # Introduction to Database Systems

                ## What is a Database?
                A database is an organized collection of information that can be stored and retrieved.

                ## Types of Databases
                Databases can be relational, document-based, hierarchical, and other types.
                """;

        GeneratedLesson lesson = service.convert(txt(text), LessonConversionService.METHOD_SEMANTIC);

        assertEquals("Introduction to Database Systems", lesson.title());
        assertEquals(2, lesson.sections().size());
        assertEquals("What is a Database?", lesson.sections().get(0).title());
        assertTrue(lesson.sections().get(0).content().contains("organized collection"));
        assertEquals("Types of Databases", lesson.sections().get(1).title());
    }

    @Test
    void headingsAreNotRequired() {
        String text = "Photosynthesis converts light energy into chemical energy. "
                + "Plants store this energy in glucose. Chlorophyll absorbs mostly red and blue light.";

        GeneratedLesson lesson = service.convert(txt(text), LessonConversionService.METHOD_SEMANTIC);

        assertFalse(lesson.title().isBlank());
        assertEquals(1, lesson.sections().size());
        assertTrue(lesson.sections().get(0).content().contains("Photosynthesis"));
    }

    @Test
    void docxTextIsExtracted() throws Exception {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph heading = document.createParagraph();
        heading.setStyle("Heading1");
        heading.createRun().setText("Study Methods");
        document.createParagraph().createRun().setText("Short review sessions help you remember more.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.write(out);
        document.close();

        GeneratedLesson lesson = service.convert(
                new MockMultipartFile("file", "notes.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        out.toByteArray()),
                LessonConversionService.METHOD_SEMANTIC);

        assertEquals("Study Methods", lesson.title());
        assertTrue(lesson.sections().get(0).content().contains("Short review sessions"));
    }

    @Test
    void pdfTextIsExtracted() throws Exception {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 12);
            stream.newLineAtOffset(60, 700);
            stream.showText("Cell Structure");
            stream.endText();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        document.close();

        GeneratedLesson lesson = service.convert(
                new MockMultipartFile("file", "biology.pdf", "application/pdf", out.toByteArray()),
                LessonConversionService.METHOD_SEMANTIC);

        assertTrue(lesson.sections().get(0).content().contains("Cell Structure"),
                "PDF text should be extracted. Title was: " + lesson.title()
                        + ", content: " + lesson.sections().get(0).content());
    }

    @Test
    void pptxTextIsExtracted() throws Exception {
        XMLSlideShow slides = new XMLSlideShow();
        XSLFSlide slide = slides.createSlide();
        XSLFTextBox title = slide.createTextBox();
        title.setText("Water Cycle");
        XSLFTextBox body = slide.createTextBox();
        body.setText("Evaporation turns water into vapour.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        slides.write(out);
        slides.close();

        GeneratedLesson lesson = service.convert(
                new MockMultipartFile("file", "science.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        out.toByteArray()),
                LessonConversionService.METHOD_SEMANTIC);

        assertEquals("Water Cycle", lesson.title());
        assertTrue(lesson.sections().get(0).content().contains("Evaporation"));
    }

    @Test
    void unsupportedAndEmptyFilesGetFriendlyErrors() {
        ResponseStatusException unsupported = assertThrows(ResponseStatusException.class,
                () -> service.convert(txtWithName("picture.png", "hello"), LessonConversionService.METHOD_SEMANTIC));
        assertEquals(HttpStatus.BAD_REQUEST, unsupported.getStatusCode());
        assertTrue(unsupported.getReason().contains("Unsupported file type"));

        ResponseStatusException empty = assertThrows(ResponseStatusException.class,
                () -> service.convert(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]),
                        LessonConversionService.METHOD_SEMANTIC));
        assertTrue(empty.getReason().contains("empty"));
    }

    @Test
    void invalidMethodIsRejected() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.convert(txt("# Title\n\nSome notes."), "unknown"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void aiResultIncludesTheGeneratorName() {
        AiLessonProvider provider = new AiLessonProvider() {
            @Override
            public String name() {
                return "fake";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public GeneratedLesson generate(String documentText, String titleHint) {
                return new GeneratedLesson("AI title",
                        List.of(new GeneratedLesson.GeneratedSection("AI section", "AI text.")));
            }
        };
        AiProviderChain chain = new AiProviderChain(List.of(provider), "fake", Duration.ofSeconds(5));
        LessonConversionService aiService = new LessonConversionService(
                new DocumentExtractionService(), new SemanticLessonService(), chain);

        LessonConversionService.ConversionResult result = aiService.convertDetailed(
                txt("A real document with useful content."), LessonConversionService.METHOD_AI);

        assertEquals("fake", result.generator());
        assertFalse(result.usedFallback());
        assertEquals("AI title", result.lesson().title());
    }

    private static MockMultipartFile txt(String text) {
        return txtWithName("notes.txt", text);
    }

    private static MockMultipartFile txtWithName(String name, String text) {
        return new MockMultipartFile("file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8));
    }
}
