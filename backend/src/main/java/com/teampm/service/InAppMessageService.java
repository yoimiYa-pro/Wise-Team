package com.teampm.service;

import com.teampm.domain.InAppMessage;
import com.teampm.dto.MessagePageResponse;
import com.teampm.mapper.InAppMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class InAppMessageService {

    private final InAppMessageMapper inAppMessageMapper;

    @Transactional
    public void send(Long userId, String title, String body, String msgType, String refType, Long refId) {
        InAppMessage m = new InAppMessage();
        m.setUserId(userId);
        m.setTitle(title);
        m.setBody(body);
        m.setMsgType(msgType);
        m.setReadFlag(0);
        m.setRefType(refType);
        m.setRefId(refId);
        inAppMessageMapper.insert(m);
    }

    @Transactional
    public void sendToMany(Collection<Long> userIds, String title, String body, String msgType, String refType, Long refId) {
        for (Long uid : userIds) {
            if (uid != null) {
                send(uid, title, body, msgType, refType, refId);
            }
        }
    }

    public MessagePageResponse pageList(Long userId, String keyword, int page, int pageSize) {
        int p = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 50);
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }
        long total = inAppMessageMapper.countPage(userId, kw);
        int offset = (p - 1) * size;
        var items = inAppMessageMapper.findPage(userId, kw, offset, size);
        return new MessagePageResponse(items, total);
    }

    public int unreadCount(Long userId) {
        return inAppMessageMapper.countUnread(userId);
    }

    @Transactional
    public void markRead(Long messageId, Long userId) {
        inAppMessageMapper.markRead(messageId, userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        inAppMessageMapper.markAllRead(userId);
    }

    public InAppMessage requireOwned(Long id, Long userId) {
        InAppMessage m = inAppMessageMapper.findById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            return null;
        }
        return m;
    }

    @Transactional
    public boolean deleteOwned(Long id, Long userId) {
        return inAppMessageMapper.deleteByIdAndUserId(id, userId) > 0;
    }
}
