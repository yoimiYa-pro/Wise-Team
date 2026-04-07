package com.teampm.web;

import com.teampm.domain.InAppMessage;
import com.teampm.security.SecurityUtils;
import com.teampm.service.InAppMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class InAppMessageController {

    private final InAppMessageService inAppMessageService;

    @GetMapping
    public List<InAppMessage> list(@RequestParam(defaultValue = "80") int limit) {
        var p = SecurityUtils.requireUser();
        return inAppMessageService.list(p.getId(), Math.min(Math.max(limit, 1), 200));
    }

    @GetMapping("/unread-count")
    public Map<String, Integer> unreadCount() {
        int n = inAppMessageService.unreadCount(SecurityUtils.requireUser().getId());
        return Map.of("count", n);
    }

    @PatchMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        var p = SecurityUtils.requireUser();
        if (inAppMessageService.requireOwned(id, p.getId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        inAppMessageService.markRead(id, p.getId());
    }

    @PostMapping("/read-all")
    public void readAll() {
        inAppMessageService.markAllRead(SecurityUtils.requireUser().getId());
    }
}
