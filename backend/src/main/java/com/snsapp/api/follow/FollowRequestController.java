package com.snsapp.api.follow;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.block.Visibility;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.notification.Notification;
import com.snsapp.api.notification.NotificationService;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserDtos.UserSummary;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 鍵アカウント(F-26)に届いたフォローのリクエストを見て、承認・拒否する */
@RestController
@RequestMapping("/api/me/follow-requests")
@Tag(name = OpenApiConfig.TAG_USER)
public class FollowRequestController {

    static final int LIST_MAX = 200;

    public record RequestCount(long count) {
    }

    private final FollowRequestRepository requests;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final Visibility visibility;
    private final Approver approver;

    public FollowRequestController(FollowRequestRepository requests, UserRepository users, CurrentUser currentUser,
                                   Visibility visibility, Approver approver) {
        this.requests = requests;
        this.users = users;
        this.currentUser = currentUser;
        this.visibility = visibility;
        this.approver = approver;
    }

    @Operation(operationId = "listFollowRequests", summary = "届いているフォローのリクエスト",
            description = "新しい順に200人まで。ブロックしている・されている人のものは出さない")
    @GetMapping
    public List<UserSummary> list(HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        return requests.findByTargetIdAndRequesterIdNotInOrderByIdDesc(me, visibility.blockRelated(me), Limit.of(LIST_MAX))
                .stream().map(r -> UserSummary.from(r.getRequester())).toList();
    }

    @Operation(operationId = "countFollowRequests", summary = "届いているフォローのリクエストの数")
    @GetMapping("/count")
    public RequestCount count(HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        return new RequestCount(requests.countByTargetIdAndRequesterIdNotIn(me, visibility.blockRelated(me)));
    }

    @Operation(operationId = "approveFollowRequest", summary = "フォローのリクエストを承認する",
            description = "相手が自分をフォローした状態になり、自分の投稿が見えるようになる。リクエストが無ければ 404")
    @ApiErrors(404)
    @PostMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void approve(@PathVariable String username, HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        FollowRequest request = find(username, me);
        approver.approve(request);
    }

    @Operation(operationId = "rejectFollowRequest", summary = "フォローのリクエストを拒否する",
            description = "リクエストを消す。相手には知らせない(もう一度リクエストできる)。リクエストが無ければ 404")
    @ApiErrors(404)
    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void reject(@PathVariable String username, HttpServletRequest httpRequest) {
        String me = currentUser.require(httpRequest);
        FollowRequest request = find(username, me);
        approver.discard(request);
    }

    private FollowRequest find(String username, String me) {
        return (username.matches(User.USERNAME_PATTERN) ? users.findByUsername(username) : java.util.Optional.<User>empty())
                .flatMap(user -> requests.findByRequesterIdAndTargetId(user.getId(), me))
                .orElseThrow(() -> ApiException.notFound("リクエストが見つかりません: @" + username));
    }

    /** リクエストを承認する・捨てる。鍵を外したとき(MeController)にも、まとめて承認するので分けておく */
    @Service
    public static class Approver {

        private final FollowRequestRepository requests;
        private final FollowRepository follows;
        private final UserRepository users;
        private final NotificationService notifications;

        public Approver(FollowRequestRepository requests, FollowRepository follows, UserRepository users,
                        NotificationService notifications) {
            this.requests = requests;
            this.follows = follows;
            this.users = users;
            this.notifications = notifications;
        }

        @Transactional
        public void approve(FollowRequest request) {
            String requesterId = request.getRequester().getId();
            String targetId = request.getTargetId();
            discard(request);
            if (!follows.existsByFollowerIdAndFolloweeId(requesterId, targetId)) {
                try {
                    follows.saveAndFlush(new Follow(users.getReferenceById(requesterId), users.getReferenceById(targetId)));
                } catch (DataIntegrityViolationException e) {
                    // 同時に承認された。フォロー済みとして扱う
                }
            }
        }

        @Transactional
        public void discard(FollowRequest request) {
            requests.delete(request);
            notifications.retract(Notification.Type.FOLLOW_REQUEST, request.getRequester().getId(), request.getTargetId(), null);
        }

        /** 鍵を外したら、届いているリクエストはすべて承認する(待たせる理由が無くなるので) */
        @Transactional
        public void approveAll(String targetId) {
            requests.findByTargetId(targetId).forEach(this::approve);
        }
    }
}
