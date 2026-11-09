package com.snsapp.api.block;

import com.snsapp.api.user.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MuteRepository extends JpaRepository<Mute, Long> {

    boolean existsByMuterIdAndMutedId(String muterId, String mutedId);

    @Query("select m.mutedId from Mute m where m.muterId = :userId")
    List<String> findMutedIds(@Param("userId") String userId);

    /** ミュート中の人(新しくミュートした順)。設定画面の一覧 */
    @Query("select u from Mute m join User u on u.id = m.mutedId where m.muterId = :userId order by m.id desc")
    List<User> findMutedUsers(@Param("userId") String userId, Limit limit);

    @Transactional
    @Modifying
    @Query("delete from Mute m where m.muterId = :muterId and m.mutedId = :mutedId")
    int deleteByMuterIdAndMutedId(@Param("muterId") String muterId, @Param("mutedId") String mutedId);
}
