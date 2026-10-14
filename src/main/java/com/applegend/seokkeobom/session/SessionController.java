package com.applegend.seokkeobom.session;

import com.applegend.seokkeobom.common.CurrentUser;
import com.applegend.seokkeobom.session.SessionDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sessions")
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSessionResponse create(@RequestBody(required = false) CreateSessionRequest req,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return sessionService.create(CurrentUser.id(), req == null ? null : req.name(), idempotencyKey);
    }

    @PostMapping("/join")
    public JoinResponse join(@Valid @RequestBody JoinRequest req) {
        return sessionService.join(CurrentUser.id(), req.teamCode());
    }

    @GetMapping("/{id}")
    public SessionInfo info(@PathVariable Long id) {
        return sessionService.info(id, CurrentUser.id());
    }

    @DeleteMapping("/{id}/members/me")
    public LeaveResponse leave(@PathVariable Long id) {
        return sessionService.leave(id, CurrentUser.id());
    }

    @PostMapping("/{id}/close")
    public CloseResponse close(@PathVariable Long id) {
        return sessionService.close(id, CurrentUser.id());
    }
}
