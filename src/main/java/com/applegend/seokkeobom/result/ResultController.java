package com.applegend.seokkeobom.result;

import com.applegend.seokkeobom.common.CurrentUser;
import com.applegend.seokkeobom.result.ResultDtos.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
public class ResultController {
    private final ResultService resultService;
    private final ExportService exportService;

    public ResultController(ResultService resultService, ExportService exportService) {
        this.resultService = resultService;
        this.exportService = exportService;
    }

    @PostMapping("/ideas/{ideaId}/result")
    public ResultResponse create(@PathVariable Long ideaId) {
        return resultService.create(ideaId, CurrentUser.id());
    }

    @GetMapping("/results/{id}")
    public ResultResponse get(@PathVariable Long id) {
        return resultService.get(id, CurrentUser.id());
    }

    @PatchMapping("/results/{id}")
    public ResultResponse update(@PathVariable Long id, @RequestBody UpdateRequest req) {
        return resultService.update(id, CurrentUser.id(), req == null ? null : req.items());
    }

    @PostMapping("/results/{id}/regenerate")
    public ResultResponse regenerate(@PathVariable Long id, @RequestBody(required = false) RegenerateRequest req) {
        return resultService.regenerate(id, CurrentUser.id(), req == null ? null : req.feedback());
    }

    @GetMapping("/results/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam("format") String format) {
        ExportService.ExportFile file = exportService.export(id, CurrentUser.id(), format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }

    @DeleteMapping("/results/{id}")
    public DeleteResponse delete(@PathVariable Long id) {
        return resultService.delete(id, CurrentUser.id());
    }
}
