package com.csit321g1.bairus.lessonfy.conversion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.csit321g1.bairus.lessonfy.conversion.ExtractedDocument.Block;

/**
 * Extracts text from supported documents. This class only reads files; it
 * knows nothing about AI or semantic lesson generation.
 */
@Service
public class DocumentExtractionService {

    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_EXTRACTED_CHARS = 200_000;
    private static final int MAX_ZIP_ENTRIES = 3_000;
    private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 80L * 1024 * 1024;
    private static final long MAX_ZIP_ENTRY_BYTES = 60L * 1024 * 1024;
    private static final Set<String> SUPPORTED = Set.of("txt", "pdf", "docx", "pptx");
    private static final Logger log = LoggerFactory.getLogger(DocumentExtractionService.class);

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern NAMED_HEADING = Pattern.compile(
            "(?i)^(chapter|section|part|unit|lesson|topic)\\s+\\d+([.:\\-]\\s*.*)?$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^(\\d+(\\.\\d+){0,2})\\.?\\s+\\S.*$");
    private static final Pattern DOCX_HEADING_STYLE = Pattern.compile("(?i)(heading|header|title)\\s*([1-6])?");

    public ExtractedDocument extract(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String extension = extensionOf(fileName);

        if (!SUPPORTED.contains(extension)) {
            throw badRequest("Unsupported file type. Upload a PDF, Word (.docx), PowerPoint (.pptx), or text (.txt) file.");
        }
        if (file.isEmpty()) {
            throw badRequest("This file is empty. Please choose a file that has content.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw badRequest("This file is too large. The limit is 10 MB.");
        }

        try {
            byte[] bytes = file.getBytes();
            validateSignature(extension, bytes);
            List<Block> blocks = switch (extension) {
                case "txt" -> parsePlainText(decodeText(bytes));
                case "pdf" -> extractPdf(bytes);
                case "docx" -> extractDocx(bytes);
                case "pptx" -> extractPptx(bytes);
                default -> throw badRequest("Unsupported file type.");
            };

            blocks = sanitizeBlocks(blocks, extension);
            if (blocks.isEmpty()) {
                throw badRequest("No readable text was found in this file.");
            }

            String text = toPlainText(blocks);
            if (text.isBlank()) {
                throw badRequest("No readable text was found in this file.");
            }
            return new ExtractedDocument(text, blocks, suggestTitle(blocks, fileName));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Could not extract text from uploaded file: {}", ex.getMessage());
            throw badRequest("This " + extension.toUpperCase(Locale.ROOT)
                    + " file could not be read. Check that it is a valid, unlocked document and try again.");
        }
    }

    /**
     * Checks that the file really is what its extension says. This stops renamed
     * files and very large ZIP expansions (DOCX/PPTX are ZIP containers).
     */
    private static void validateSignature(String extension, byte[] bytes) {
        if (bytes.length == 0) {
            throw badRequest("This file is empty. Please choose a file that has content.");
        }
        switch (extension) {
            case "txt" -> {
                for (int i = 0; i < bytes.length; i++) {
                    if (bytes[i] == 0) {
                        throw badRequest("This text file could not be read. Save it as plain UTF-8 text and try again.");
                    }
                }
            }
            case "pdf" -> {
                if (!startsWith(bytes, "%PDF-")) {
                    throw badRequest("This is not a valid PDF file. Choose a real PDF and try again.");
                }
            }
            case "docx" -> {
                requireZip(bytes);
                validateZip(bytes, "word/document.xml", "Word (.docx)");
            }
            case "pptx" -> {
                requireZip(bytes);
                validateZip(bytes, "ppt/presentation.xml", "PowerPoint (.pptx)");
            }
            default -> throw badRequest("Unsupported file type.");
        }
    }

    private static void requireZip(byte[] bytes) {
        if (!startsWith(bytes, "PK\u0003\u0004")) {
            throw badRequest("This file could not be read as an Office document. Choose a valid, unlocked file.");
        }
    }

    private static boolean startsWith(byte[] bytes, String value) {
        byte[] prefix = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }

    private static void validateZip(byte[] bytes, String requiredEntry, String label) {
        boolean foundRequired = false;
        int entries = 0;
        long total = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw badRequest("This " + label + " file has too many internal parts to read safely.");
                }
                String name = entry.getName();
                if (name.startsWith("../") || name.contains("\\") || name.contains("\u0000")) {
                    throw badRequest("This " + label + " file has unsafe internal paths and cannot be read.");
                }
                if (requiredEntry.equals(name)) foundRequired = true;

                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    total += read;
                    if (entryBytes > MAX_ZIP_ENTRY_BYTES || total > MAX_ZIP_UNCOMPRESSED_BYTES) {
                        throw badRequest("This " + label + " file expands to an unsafe size and cannot be read.");
                    }
                }
            }
        } catch (IOException ex) {
            throw badRequest("This " + label + " file could not be read. Choose a valid, unlocked file.");
        }

        if (!foundRequired) {
            throw badRequest("This " + label + " file is missing its main content and cannot be read.");
        }
    }

    /** Removes hidden control characters and caps how much text reaches AI or semantic generation. */
    private static List<Block> sanitizeBlocks(List<Block> blocks, String extension) {
        List<Block> clean = new ArrayList<>();
        int total = 0;
        for (Block block : blocks) {
            String text = sanitizeText(block.text());
            if (text.isEmpty()) continue;
            int remaining = MAX_EXTRACTED_CHARS - total;
            if (remaining <= 0) break;
            if (text.length() > remaining) text = text.substring(0, remaining);
            clean.add(new Block(block.level(), text));
            total += text.length();
        }
        return clean;
    }

    private static String sanitizeText(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean hidden = ch == '\uFEFF'
                    || (ch >= '\u200B' && ch <= '\u200F')
                    || (ch >= '\u202A' && ch <= '\u202E')
                    || (ch >= '\u2060' && ch <= '\u206F');
            if (!hidden && (ch == '\n' || ch == '\r' || ch == '\t' || ch >= 0x20)) {
                result.append(ch);
            }
        }
        return collapseWhitespace(result.toString());
    }

    private List<Block> extractPdf(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes); PDDocument pdf = PDDocument.load(in)) {
            if (pdf.isEncrypted()) {
                throw badRequest("This PDF is password-protected. Unlock it and try again.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return parsePlainText(stripper.getText(pdf));
        } catch (InvalidPasswordException ex) {
            // PDFBox can reject encrypted PDFs while loading, before isEncrypted() runs.
            throw badRequest("This PDF is password-protected. Unlock it and try again.");
        }
    }

    private List<Block> extractDocx(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes); XWPFDocument doc = new XWPFDocument(in)) {
            List<Block> blocks = new ArrayList<>();
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                addParagraph(blocks, paragraph.getText(), headingLevelFromStyle(paragraph.getStyle()));
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            addParagraph(blocks, paragraph.getText(), 0);
                        }
                    }
                }
            }
            return blocks;
        }
    }

    private List<Block> extractPptx(byte[] bytes) throws IOException {
        List<Block> blocks = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(bytes); XMLSlideShow slides = new XMLSlideShow(in)) {
            for (XSLFSlide slide : slides.getSlides()) {
                boolean firstShapeOnSlide = true;
                for (XSLFShape shape : slide.getShapes()) {
                    if (!(shape instanceof XSLFTextShape textShape)) {
                        continue;
                    }
                    String value = textShape.getText();
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    // PowerPoint does not force a title placeholder, so treat
                    // the first text shape on each slide as the slide title.
                    addParagraph(blocks, value, firstShapeOnSlide ? 1 : 0);
                    firstShapeOnSlide = false;
                }
            }
        }
        return blocks;
    }

    private static int headingLevelFromStyle(String styleId) {
        if (styleId == null) {
            return 0;
        }
        Matcher matcher = DOCX_HEADING_STYLE.matcher(styleId);
        if (!matcher.find()) {
            return 0;
        }
        String number = matcher.group(2);
        return number == null ? 1 : Math.min(6, Integer.parseInt(number));
    }

    /** Parses plain text (TXT and PDF) into heading / paragraph blocks. */
    private static List<Block> parsePlainText(String rawText) {
        String text = rawText.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "");
        List<Block> blocks = new ArrayList<>();
        String[] rawBlocks = text.split("\\n\\s*\\n");

        for (String rawBlock : rawBlocks) {
            String[] lines = rawBlock.split("\\n");
            String firstLine = lines[0].trim();
            int level = detectHeadingLevel(firstLine);
            if (level > 0) {
                addBlock(blocks, level, stripHeadingMarker(firstLine));
                if (lines.length > 1) {
                    addParagraph(blocks, joinLines(lines, 1), 0);
                }
            } else {
                addParagraph(blocks, joinLines(lines, 0), 0);
            }
        }

        if (blocks.isEmpty()) {
            addParagraph(blocks, collapseWhitespace(text), 0);
        }
        return blocks;
    }

    private static int detectHeadingLevel(String line) {
        if (line.isEmpty()) {
            return 0;
        }
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) {
            return Math.min(6, markdown.group(1).length());
        }
        if (NAMED_HEADING.matcher(line).matches()) {
            return 1;
        }
        Matcher numbered = NUMBERED_HEADING.matcher(line);
        if (numbered.matches() && line.length() <= 120) {
            int dots = 1;
            for (int i = 0; i < numbered.group(1).length(); i++) {
                if (numbered.group(1).charAt(i) == '.') dots++;
            }
            return Math.min(3, dots);
        }
        boolean endsWithColon = line.endsWith(":");
        boolean shortAllCaps = line.length() <= 80 && line.equals(line.toUpperCase(Locale.ROOT))
                && line.chars().anyMatch(Character::isLetter);
        return (shortAllCaps || endsWithColon) && line.length() <= 120 ? 1 : 0;
    }

    private static String stripHeadingMarker(String line) {
        return line.replaceFirst("^#{1,6}\\s+", "").trim();
    }

    private static String joinLines(String[] lines, int from) {
        StringBuilder result = new StringBuilder();
        for (int i = from; i < lines.length; i++) {
            if (result.length() > 0) result.append(' ');
            result.append(lines[i].trim());
        }
        return collapseWhitespace(result.toString());
    }

    private static void addParagraph(List<Block> blocks, String value, int level) {
        String clean = collapseWhitespace(value);
        if (!clean.isEmpty()) {
            addBlock(blocks, level, clean);
        }
    }

    private static void addBlock(List<Block> blocks, int level, String value) {
        String clean = collapseWhitespace(value);
        if (!clean.isEmpty()) {
            blocks.add(new Block(level, clean));
        }
    }

    private static String collapseWhitespace(String value) {
        return value == null ? "" : value.replaceAll("[ \\t]+", " ").trim();
    }

    private static String decodeText(byte[] bytes) {
        String utf8 = decodeStrict(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        // Older Windows text files are often Windows-1252 rather than UTF-8.
        return new String(bytes, Charset.forName("windows-1252"));
    }

    private static String decodeStrict(byte[] bytes, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            return null;
        }
    }

    private static String toPlainText(List<Block> blocks) {
        StringBuilder text = new StringBuilder();
        for (Block block : blocks) {
            if (text.length() > 0) text.append("\n\n");
            if (block.level() > 0) {
                text.append("#".repeat(Math.min(block.level(), 6))).append(' ');
            }
            text.append(block.text());
        }
        return text.toString();
    }

    private static String suggestTitle(List<Block> blocks, String fileName) {
        for (Block block : blocks) {
            if (block.level() == 1) {
                String title = block.text();
                return title.length() > 200 ? title.substring(0, 200) : title;
            }
        }
        int dot = fileName.lastIndexOf('.');
        String fromFile = dot > 0 ? fileName.substring(0, dot) : fileName;
        fromFile = fromFile.trim();
        if (fromFile.isEmpty()) fromFile = "Untitled lesson";
        return fromFile.length() > 200 ? fromFile.substring(0, 200) : fromFile;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
