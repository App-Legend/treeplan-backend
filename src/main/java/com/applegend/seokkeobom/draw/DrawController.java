package com.applegend.seokkeobom.draw;

import com.applegend.seokkeobom.common.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class DrawController {
    private final DrawService drawService;

    public DrawController(DrawService drawService) {
        this.drawService = drawService;
    }

    @PostMapping("/sessions/{sessionId}/draws")
    @ResponseStatus(HttpStatus.CREATED)
    public DrawService.DrawResponse draw(@PathVariable Long sessionId, @RequestBody DrawService.DrawRequest req) {
        return drawService.draw(sessionId, CurrentUser.id(), req);
    }
}
