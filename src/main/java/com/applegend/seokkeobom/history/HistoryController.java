package com.applegend.seokkeobom.history;

import com.applegend.seokkeobom.common.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
public class HistoryController {
    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/history")
    public HistoryService.HistoryPage list(@RequestParam(value = "q", required = false) String q,
                                           @RequestParam(value = "categoryId", required = false) Long categoryId,
                                           @RequestParam(value = "cursor", required = false) String cursor) {
        return historyService.list(CurrentUser.id(), q, categoryId, cursor);
    }
}
