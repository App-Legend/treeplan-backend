package com.applegend.seokkeobom.auth;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게스트로 만든 작업(세션·추출·아이디어·결과물)을 로그인한 계정으로 옮긴다.
 * 로그인 권유 팝업("히스토리로 남기고 싶다면 로그인해 주세요")에서 로그인·가입하면 지금 결과가 히스토리에 남게 된다.
 */
@Service
public class GuestMergeService {
    private static final Logger log = LoggerFactory.getLogger(GuestMergeService.class);

    private final UserRepository users;
    private final EntityManager em;

    public GuestMergeService(UserRepository users, EntityManager em) {
        this.users = users;
        this.em = em;
    }

    @Transactional
    public void merge(Long guestId, Long userId) {
        if (guestId == null || guestId.equals(userId)) return;
        if (users.findById(guestId).filter(User::isGuest).isEmpty()) return;
        int sessions = update("update IdeaSession s set s.ownerId = :to where s.ownerId = :from", guestId, userId);
        // 같은 세션에 이미 참여 중이면(드묾) 게스트 쪽 참여 기록만 지운다
        em.createQuery("delete from SessionMember g where g.userId = :from and exists "
                        + "(select 1 from SessionMember m where m.sessionId = g.sessionId and m.userId = :to)")
                .setParameter("from", guestId).setParameter("to", userId).executeUpdate();
        update("update SessionMember m set m.userId = :to where m.userId = :from", guestId, userId);
        update("update Draw d set d.userId = :to where d.userId = :from", guestId, userId);
        update("update Idea i set i.userId = :to where i.userId = :from", guestId, userId);
        int results = update("update Result r set r.authorId = :to where r.authorId = :from", guestId, userId);
        log.info("게스트 {}의 작업을 사용자 {}에게 옮겼습니다. (세션 {}개, 결과물 {}개)", guestId, userId, sessions, results);
    }

    private int update(String jpql, Long from, Long to) {
        return em.createQuery(jpql).setParameter("from", from).setParameter("to", to).executeUpdate();
    }
}
