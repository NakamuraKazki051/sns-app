package com.snsapp.api.block;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.follow.FollowRepository;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserDtos.UserSummary;
import com.snsapp.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ブロック・ミュート(F-20)。何度押しても結果は同じ */
@RestController
@RequestMapping("/api")
@Tag(name = OpenApiConfig.TAG_USER)
public class BlockController {

    /** 設定画面に出すブロック中・ミュート中の人の上限 */
    static final int LIST_MAX = 200;

    public record BlockResponse(boolean blocking) {
    }

    public record MuteResponse(boolean muting) {
    }

    private final BlockRepository blocks;
    private final MuteRepository mutes;
    private final FollowRepository follows;
    private final UserRepository users;
    private final CurrentUser currentUser;

    public BlockController(BlockRepository blocks, MuteRepository mutes, FollowRepository follows,
                           UserRepository users, CurrentUser currentUser) {
        this.blocks = blocks;
        this.mutes = mutes;
        this.follows = follows;
        this.users = users;
        this.currentUser = currentUser;
    }

    @Operation(operationId = "block", summary = "ブロックする",
            description = "お互いのフォローを外し、お互いの投稿・リプライ・プロフィールが見えなくなる。"
                    + "相手はフォロー・リプライ・いいね・リポストができない。自分はブロックできない(400)")
    @ApiErrors({400, 404})
    @PostMapping("/users/{username}/block")
    public BlockResponse block(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User target = other(username, viewerId, "ブロック");
        if (!blocks.existsByBlockerIdAndBlockedId(viewerId, target.getId())) {
            try {
                blocks.saveAndFlush(new Block(viewerId, target.getId()));
            } catch (DataIntegrityViolationException e) {
                // 連打で同じ組が先に入った。ブロック済みとして扱う
            }
        }
        follows.deleteByFollowerIdAndFolloweeId(viewerId, target.getId());
        follows.deleteByFollowerIdAndFolloweeId(target.getId(), viewerId);
        return new BlockResponse(true);
    }

    @Operation(operationId = "unblock", summary = "ブロックを解除する",
            description = "外れたフォローは戻らない")
    @ApiErrors(404)
    @DeleteMapping("/users/{username}/block")
    public BlockResponse unblock(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        blocks.deleteByBlockerIdAndBlockedId(viewerId, find(username).getId());
        return new BlockResponse(false);
    }

    @Operation(operationId = "mute", summary = "ミュートする",
            description = "自分のタイムライン・新着・リプライ一覧に出なくなる。相手には分からない。自分はミュートできない(400)")
    @ApiErrors({400, 404})
    @PostMapping("/users/{username}/mute")
    public MuteResponse mute(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        User target = other(username, viewerId, "ミュート");
        if (!mutes.existsByMuterIdAndMutedId(viewerId, target.getId())) {
            try {
                mutes.saveAndFlush(new Mute(viewerId, target.getId()));
            } catch (DataIntegrityViolationException e) {
                // 連打で同じ組が先に入った。ミュート済みとして扱う
            }
        }
        return new MuteResponse(true);
    }

    @Operation(operationId = "unmute", summary = "ミュートを解除する")
    @ApiErrors(404)
    @DeleteMapping("/users/{username}/mute")
    public MuteResponse unmute(@PathVariable String username, HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        mutes.deleteByMuterIdAndMutedId(viewerId, find(username).getId());
        return new MuteResponse(false);
    }

    @Operation(operationId = "listBlocks", summary = "ブロック中の人", description = "新しくブロックした順に200人まで")
    @GetMapping("/me/blocks")
    public List<UserSummary> listBlocks(HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        return blocks.findBlockedUsers(viewerId, Limit.of(LIST_MAX)).stream().map(UserSummary::from).toList();
    }

    @Operation(operationId = "listMutes", summary = "ミュート中の人", description = "新しくミュートした順に200人まで")
    @GetMapping("/me/mutes")
    public List<UserSummary> listMutes(HttpServletRequest httpRequest) {
        String viewerId = currentUser.require(httpRequest);
        return mutes.findMutedUsers(viewerId, Limit.of(LIST_MAX)).stream().map(UserSummary::from).toList();
    }

    private User other(String username, String viewerId, String action) {
        User user = find(username);
        if (user.getId().equals(viewerId)) {
            throw ApiException.badRequest("自分自身は" + action + "できません");
        }
        return user;
    }

    // ブロックされていても、ブロックし返したり解除したりはできるよう、ここではブロックで隠さない
    private User find(String username) {
        return (username.matches(User.USERNAME_PATTERN) ? users.findByUsername(username) : java.util.Optional.<User>empty())
                .filter(User::isEmailVerified)
                .orElseThrow(() -> ApiException.notFound("ユーザーが見つかりません: @" + username));
    }
}
