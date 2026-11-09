package com.snsapp.api.block;

import com.snsapp.api.user.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerIdAndBlockedId(String blockerId, String blockedId);

    /** ブロックしているか、されているか(どちらでもお互いが見えなくなる) */
    @Query("select count(b) > 0 from Block b where (b.blockerId = :a and b.blockedId = :b)"
            + " or (b.blockerId = :b and b.blockedId = :a)")
    boolean existsBetween(@Param("a") String a, @Param("b") String b);

    /** この人がブロックしている人と、この人をブロックしている人の ID */
    @Query("select b.blockedId from Block b where b.blockerId = :userId"
            + " union select b.blockerId from Block b where b.blockedId = :userId")
    List<String> findRelatedIds(@Param("userId") String userId);

    /** ブロック中の人(新しくブロックした順)。設定画面の一覧 */
    @Query("select u from Block b join User u on u.id = b.blockedId where b.blockerId = :userId order by b.id desc")
    List<User> findBlockedUsers(@Param("userId") String userId, Limit limit);

    @Transactional
    @Modifying
    @Query("delete from Block b where b.blockerId = :blockerId and b.blockedId = :blockedId")
    int deleteByBlockerIdAndBlockedId(@Param("blockerId") String blockerId, @Param("blockedId") String blockedId);
}
