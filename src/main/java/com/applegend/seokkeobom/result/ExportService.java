package com.applegend.seokkeobom.result;

import com.applegend.seokkeobom.common.ApiException;
import com.applegend.seokkeobom.common.GenerationStatus;
import com.applegend.seokkeobom.result.ResultDtos.ResultItem;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 결과물 내보내기: PDF·텍스트·Markdown. PDF는 서버에서 만든다. */
@Service
public class ExportService {
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));
    private static final String DRAFT_NOTICE = "이 내용은 AI가 만든 아이디어 초안입니다. 검증된 정답이 아니니 검토 후 발전시켜 사용하세요.";

    /** 한글 글리프가 있는 폰트 후보. (TTC는 ',0'으로 첫 글꼴 지정) */
    private static final List<String> FONT_CANDIDATES = List.of(
            "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
            "C:/Windows/Fonts/malgun.ttf",
            "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
            "/Library/Fonts/NanumGothic.ttf",
            "/usr/share/fonts/truetype/unfonts-core/UnDotum.ttf");

    private final ResultService resultService;
    private final String configuredFont;

    public ExportService(ResultService resultService, @Value("${app.export.pdf-font-path}") String configuredFont) {
        this.resultService = resultService;
        this.configuredFont = configuredFont;
    }

    public enum Format {
        PDF("pdf", "application/pdf"), TXT("txt", "text/plain; charset=UTF-8"), MD("md", "text/markdown; charset=UTF-8");

        final String ext;
        final String contentType;

        Format(String ext, String contentType) {
            this.ext = ext;
            this.contentType = contentType;
        }

        static Format parse(String raw) {
            if (raw == null) throw unsupported();
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "pdf" -> PDF;
                case "txt", "text" -> TXT;
                case "md", "markdown" -> MD;
                default -> throw unsupported();
            };
        }

        private static ApiException unsupported() {
            return ApiException.badRequest("UNSUPPORTED_FORMAT", "PDF, 텍스트(txt), Markdown(md) 형식만 내보낼 수 있습니다.");
        }
    }

    public record ExportFile(String filename, String contentType, byte[] bytes) {}

    public ExportFile export(Long resultId, Long userId, String rawFormat) {
        Format format = Format.parse(rawFormat);
        Result result = resultService.requireVisible(resultId, userId);
        if (result.getStatus() != GenerationStatus.SUCCESS) {
            throw ApiException.conflict("RESULT_NOT_READY", "생성이 끝난 결과물만 내보낼 수 있습니다.");
        }
        List<ResultItem> items = resultService.itemsOf(result);
        String base = safeName(result.getTitle());
        byte[] bytes = switch (format) {
            case TXT -> toText(result, items).getBytes(StandardCharsets.UTF_8);
            case MD -> toMarkdown(result, items).getBytes(StandardCharsets.UTF_8);
            case PDF -> toPdf(result, items);
        };
        return new ExportFile(base + "." + format.ext, format.contentType, bytes);
    }

    String toText(Result r, List<ResultItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getTitle()).append('\n');
        sb.append("카테고리: ").append(r.getCategoryName()).append('\n');
        sb.append("사용한 단어: ").append(String.join(", ", r.getWords())).append('\n');
        sb.append("생성 일시: ").append(DATE.format(r.getCreatedAt())).append("\n\n");
        int n = 1;
        for (ResultItem i : items) {
            sb.append(String.format("%02d. %s%n", n++, i.label()));
            sb.append(i.content()).append("\n\n");
        }
        sb.append("※ ").append(DRAFT_NOTICE).append('\n');
        return sb.toString();
    }

    String toMarkdown(Result r, List<ResultItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(r.getTitle()).append("\n\n");
        sb.append("- **카테고리**: ").append(r.getCategoryName()).append('\n');
        sb.append("- **사용한 단어**: ").append(String.join(" · ", r.getWords())).append('\n');
        sb.append("- **생성 일시**: ").append(DATE.format(r.getCreatedAt())).append("\n\n");
        for (ResultItem i : items) {
            sb.append("## ").append(i.label()).append("\n\n").append(i.content()).append("\n\n");
        }
        sb.append("> ").append(DRAFT_NOTICE).append('\n');
        return sb.toString();
    }

    byte[] toPdf(Result r, List<ResultItem> items) {
        BaseFont baseFont = loadKoreanFont();
        Font title = new Font(baseFont, 18);
        Font heading = new Font(baseFont, 13);
        Font body = new Font(baseFont, 10.5f);
        Font muted = new Font(baseFont, 9);
        muted.setColor(110, 110, 110);
        heading.setColor(47, 93, 70);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 54, 54, 54, 54);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(r.getTitle(), title));
            doc.add(new Paragraph(r.getCategoryName() + "  |  " + String.join(" · ", r.getWords())
                    + "  |  " + DATE.format(r.getCreatedAt()), muted));
            int n = 1;
            for (ResultItem i : items) {
                Paragraph h = new Paragraph(String.format("%02d  %s", n++, i.label()), heading);
                h.setSpacingBefore(14);
                h.setSpacingAfter(4);
                doc.add(h);
                Paragraph p = new Paragraph(i.content(), body);
                p.setLeading(16);
                doc.add(p);
            }
            Paragraph notice = new Paragraph(DRAFT_NOTICE, muted);
            notice.setSpacingBefore(20);
            doc.add(notice);
        } catch (Exception e) {
            log.error("PDF export failed", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF_FAILED", "PDF를 만들지 못했습니다. 텍스트나 Markdown으로 내보내 주세요.");
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    private BaseFont loadKoreanFont() {
        List<String> candidates = new ArrayList<>();
        if (configuredFont != null && !configuredFont.isBlank()) candidates.add(configuredFont.strip());
        candidates.addAll(FONT_CANDIDATES);
        for (String path : candidates) {
            String filePath = path.contains(",") ? path.substring(0, path.indexOf(',')) : path;
            if (!Files.isRegularFile(Path.of(filePath))) continue;
            try {
                return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                log.warn("Font {} could not be loaded: {}", path, e.getMessage());
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PDF_FONT_MISSING",
                "서버에 PDF용 한글 폰트가 없습니다. 텍스트나 Markdown으로 내보내거나 관리자에게 PDF_FONT_PATH 설정을 요청하세요.");
    }

    private static String safeName(String title) {
        String cleaned = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "").strip();
        if (cleaned.isEmpty()) cleaned = "seokkeobom-result";
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }
}
