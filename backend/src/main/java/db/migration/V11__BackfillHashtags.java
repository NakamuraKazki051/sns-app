package db.migration;

import com.snsapp.api.hashtag.Hashtags;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * ハッシュタグ(F-22)より前からある投稿の本文から、タグを取り出して post_hashtags に入れる。1回だけ流れる。
 * 本文の決まりは Hashtags と同じ(SQL では日本語の文字の判定が書けないので Java にした)
 */
public class V11__BackfillHashtags extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement();
             ResultSet posts = select.executeQuery("select id, body, created_at from posts");
             PreparedStatement insert = context.getConnection().prepareStatement(
                     "insert into post_hashtags (post_id, tag, created_at) values (?, ?, ?)")) {
            while (posts.next()) {
                long postId = posts.getLong(1);
                Timestamp createdAt = posts.getTimestamp(3);
                for (String tag : Hashtags.extract(posts.getString(2))) {
                    insert.setLong(1, postId);
                    insert.setString(2, tag);
                    insert.setTimestamp(3, createdAt);
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }
    }
}
