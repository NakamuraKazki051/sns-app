package com.snsapp.api.report;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** まだ対応していない通報(古い順。先に来たものから片付ける) */
    @EntityGraph(attributePaths = {"reporter", "targetUser", "post"})
    List<Report> findByResolvedAtIsNullOrderByIdAsc(Limit limit);

    /** 対応済みの通報(新しく対応した順) */
    @EntityGraph(attributePaths = {"reporter", "targetUser", "post"})
    List<Report> findByResolvedAtIsNotNullOrderByResolvedAtDesc(Limit limit);

    long countByResolvedAtIsNull();

    List<Report> findByPostIdAndResolvedAtIsNull(Long postId);

    /** 同じ人が同じ相手(投稿)をまだ対応前のまま通報しているか。二重に数えない */
    @Query("select count(r) > 0 from Report r left join r.post p"
            + " where r.reporter.id = :reporterId and r.targetUser.id = :targetId"
            + " and ((:postId is null and p is null) or p.id = :postId) and r.resolvedAt is null")
    boolean existsOpen(@Param("reporterId") String reporterId, @Param("targetId") String targetId,
                       @Param("postId") Long postId);

}
