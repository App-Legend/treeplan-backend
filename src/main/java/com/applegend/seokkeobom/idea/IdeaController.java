package com.applegend.seokkeobom.idea;

import com.applegend.seokkeobom.common.ApiException;
import com.applegend.seokkeobom.common.CurrentUser;
import com.applegend.seokkeobom.idea.IdeaDtos.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class IdeaController {
    private final IdeaService ideaService;

    public IdeaController(IdeaService ideaService) {
        this.ideaService = ideaService;
    }

    @PostMapping("/draws/{drawId}/idea")
    public IdeaResponse generate(@PathVariable Long drawId) {
        return ideaService.generate(drawId, CurrentUser.id());
    }

    @PutMapping("/ideas/{ideaId}/bookmark")
    public BookmarkResponse bookmark(@PathVariable Long ideaId, @RequestBody BookmarkRequest req) {
        if (req == null || req.bookmarked() == null) {
            throw ApiException.badRequest("BAD_REQUEST", "보관 여부(bookmarked)를 보내 주세요.");
        }
        return ideaService.bookmark(ideaId, CurrentUser.id(), req.bookmarked());
    }
}
