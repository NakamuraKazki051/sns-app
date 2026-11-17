package com.snsapp.api.report;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.Admins;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.common.TextLength;
import com.snsapp.api.post.Post;
import com.snsapp.api.post.PostController;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserDtos.PublicUser;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 通報(ユーザー・投稿を運営に知らせる)と、管理者が通報を見て対応する API */
@RestController
@RequestMapping("/api")
@Tag(name = OpenApiConfig.TAG_REPORT)
public class ReportController {

    static final int DETAIL_MAX = 500;
    // 見た目で500字でも、結合文字を重ねた巨大な文字列は受け付けない
    private static final int DETAIL_MAX_CHARS = 2000;
    static final int REPORTS_PER_HOUR = 20;
    private static final Duration REPORT_WINDOW = Duration.ofHours(1);
    static final int ADMIN_LIST_MAX = 100;

    public record ReportRequest(
            @Schema(description = "通報するユーザー名。投稿の通報なら省いてよい") String username,
            @Schema(description = "通報する投稿の ID(文字列)。ユーザーの通報なら省く") String postId,
            @Schema(description = "理由") Report.Reason reason,
            @Schema(description = "詳しい説明(任意、500字まで)") String detail) {
    }

    public record ReportedPost(@Schema(types = {"string", "null"}, description = "投稿 ID。投稿が消されていれば null") String id,
                              @Schema(description = "通報した時点の本文") String body) {
    }

    public record ReportResponse(@Schema(description = "通報 ID(文字列)") String id, Report.Reason reason,
                                 @Schema(types = {"string", "null"}) String detail, Instant createdAt,
                                 PublicUser reporter, PublicUser target,
                                 @Schema(types = {"object", "null"}, description = "投稿の通報なら入る") ReportedPost post,
                                 @Schema(types = {"string", "null"}, format = "date-time") Instant resolvedAt,
                                 @Schema(types = {"string", "null"}) Report.Resolution resolution) {
        static ReportResponse from(Report report) {
            ReportedPost post = report.getPostBody() == null && report.getPost() == null ? null
                    : new ReportedPost(report.getPost() == null ? null : report.getPost().getId().toString(),
                    report.getPostBody());
            return new ReportResponse(report.getId().toString(), report.getReason(), report.getDetail(),
                    report.getCreatedAt(), PublicUser.from(report.getReporter()), PublicUser.from(report.getTargetUser()),
                    post, report.getResolvedAt(), report.getResolution());
        }
    }

    public record AdminStatus(boolean admin, long openReports) {
    }

    public record ResolveRequest(@Schema(description = "DISMISSED(問題なし)か POST_DELETED(投稿を消す)") Report.Resolution resolution) {
    }

    private final ReportRepository reports;
    private final UserRepository users;
    private final PostController posts;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;
    private final Admins admins;

    public ReportController(ReportRepository reports, UserRepository users, PostController posts,
                            CurrentUser currentUser, RateLimiter rateLimiter, Admins admins) {
        this.reports = reports;
        this.users = users;
        this.posts = posts;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
        this.admins = admins;
    }

    @Operation(operationId = "createReport", summary = "通報する",
            description = "ユーザー(username)か投稿(postId)を、理由を選んで運営に知らせる。相手には知らせない。"
                    + "同じ相手(投稿)への対応前の通報があれば、新しく作らず 201 を返す。自分は通報できない(400)。1人1時間に20件まで(429)")
    @ApiErrors({400, 404, 429})
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public void create(@RequestBody ReportRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        if (request.reason() == null) {
            throw ApiException.badRequest("通報の理由を選んでください");
        }
        String detail = request.detail() == null ? null : request.detail().strip();
        if (detail != null && (detail.length() > DETAIL_MAX_CHARS || TextLength.visible(detail) > DETAIL_MAX)) {
            throw ApiException.badRequest("詳しい説明は" + DETAIL_MAX + "文字以内で入力してください");
        }
        Post post = null;
        User target;
        if (request.postId() != null && !request.postId().isBlank()) {
            post = posts.findPost(parsePostId(request.postId()), userId);
            target = post.getUser();
        } else if (request.username() != null && request.username().matches(User.USERNAME_PATTERN)) {
            target = users.findByUsername(request.username()).filter(User::isEmailVerified)
                    .orElseThrow(() -> ApiException.notFound("ユーザーが見つかりません: @" + request.username()));
        } else {
            throw ApiException.badRequest("通報するユーザーか投稿を指定してください");
        }
        if (target.getId().equals(userId)) {
            throw ApiException.badRequest("自分自身は通報できません");
        }
        if (!rateLimiter.tryAcquire("report:" + userId, REPORTS_PER_HOUR, REPORT_WINDOW)) {
            throw ApiException.tooManyRequests("通報が多すぎます。1時間ほど待ってからもう一度お試しください");
        }
        if (reports.existsOpen(userId, target.getId(), post == null ? null : post.getId())) {
            return;
        }
        reports.save(new Report(users.getReferenceById(userId), target, post, request.reason(),
                detail == null || detail.isEmpty() ? null : detail));
    }

    @Operation(operationId = "getAdminStatus", summary = "管理者かどうか",
            description = "管理者なら、対応前の通報の数も返す。画面はこれを見て「通報の確認」を出す")
    @GetMapping("/admin/status")
    public AdminStatus adminStatus(HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        boolean admin = admins.isAdmin(userId);
        return new AdminStatus(admin, admin ? reports.countByResolvedAtIsNull() : 0);
    }

    @Operation(operationId = "listReports", summary = "通報の一覧(管理者だけ)",
            description = "status=open は対応前を古い順に、resolved は対応済みを新しい順に、100件まで。管理者でなければ 404")
    @ApiErrors(404)
    @GetMapping("/admin/reports")
    @Transactional(readOnly = true)
    public List<ReportResponse> list(@Parameter(description = "open(対応前、既定)か resolved(対応済み)")
                                     @RequestParam(defaultValue = "open") String status,
                                     HttpServletRequest httpRequest) {
        admins.require(currentUser.require(httpRequest));
        Limit limit = Limit.of(ADMIN_LIST_MAX);
        List<Report> found = "resolved".equals(status)
                ? reports.findByResolvedAtIsNotNullOrderByResolvedAtDesc(limit)
                : reports.findByResolvedAtIsNullOrderByIdAsc(limit);
        return found.stream().map(ReportResponse::from).toList();
    }

    @Operation(operationId = "resolveReport", summary = "通報に対応する(管理者だけ)",
            description = "DISMISSED は何もせず片付ける。POST_DELETED は通報された投稿を消し、その投稿への対応前の通報もまとめて片付ける。"
                    + "管理者でない・通報が無いときは 404。投稿の通報でないのに POST_DELETED なら 400")
    @ApiErrors({400, 404})
    @PostMapping("/admin/reports/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void resolve(@PathVariable Long id, @RequestBody ResolveRequest request, HttpServletRequest httpRequest) {
        admins.require(currentUser.require(httpRequest));
        Report report = reports.findById(id).orElseThrow(() -> ApiException.notFound("通報が見つかりません: " + id));
        if (request.resolution() == null) {
            throw ApiException.badRequest("対応を選んでください");
        }
        if (request.resolution() == Report.Resolution.POST_DELETED) {
            Post post = Optional.ofNullable(report.getPost())
                    .orElseThrow(() -> ApiException.badRequest("消す投稿がありません(もう消されています)"));
            Long postId = post.getId();
            // 同じ投稿への通報もまとめて片付けてから消す(消すと post_id が空になり、どの投稿か分からなくなる)
            reports.findByPostIdAndResolvedAtIsNull(postId).forEach(r -> r.resolve(Report.Resolution.POST_DELETED));
            reports.flush();
            posts.deleteAsModerator(postId);
            return;
        }
        if (report.getResolvedAt() == null) {
            report.resolve(request.resolution());
        }
    }

    private static Long parsePostId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw ApiException.notFound("投稿が見つかりません: " + value);
        }
    }
}
