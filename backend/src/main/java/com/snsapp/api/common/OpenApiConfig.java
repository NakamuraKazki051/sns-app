package com.snsapp.api.common;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API 仕様書の全体の情報と、コードから読み取れないログインの要・不要とエラーを足す。
 * 開き方と更新のしかたは README の「API 仕様書」
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "RaiseLine API", version = "0.1.0", description = """
                RaiseLine(旧Twitter風のテキストSNS)の API。

                - **ログイン：** `POST /api/auth/login` が返す Cookie(`SNS_TOKEN`、中身は JWT)で判定する
                - **エラー：** 本文は共通の `ApiError`。`message` は画面にそのまま出せる日本語
                - **他のサイトからの呼び出し：** 許可していないオリジンからの POST などは 403
                - **ページ送り：** 1ページ20件。応答の `nextCursor` を次のリクエストに渡す
                """),
        servers = @Server(url = "/"),
        security = @SecurityRequirement(name = OpenApiConfig.AUTH_COOKIE),
        tags = {
                @Tag(name = OpenApiConfig.TAG_AUTH, description = "登録・ログイン・ログアウト"),
                @Tag(name = OpenApiConfig.TAG_POST, description = "投稿・タイムライン・いいね"),
                @Tag(name = OpenApiConfig.TAG_REPLY, description = "投稿へのリプライ"),
                @Tag(name = OpenApiConfig.TAG_REPOST, description = "他の人の投稿のリポスト"),
                @Tag(name = OpenApiConfig.TAG_USER, description = "ユーザーページ・検索・フォロー・メールとパスワードの変更"),
                @Tag(name = OpenApiConfig.TAG_ME, description = "表示名・自己紹介・アイコンの変更"),
                @Tag(name = OpenApiConfig.TAG_NOTIFICATION, description = "いいね・リプライ・リポスト・フォローの通知"),
                @Tag(name = OpenApiConfig.TAG_DM, description = "1対1のメッセージ")
        })
@SecurityScheme(name = OpenApiConfig.AUTH_COOKIE, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.COOKIE,
        paramName = "SNS_TOKEN", description = "ログインすると付く Cookie(中身は JWT、有効期限は最後の操作から30分)。Swagger UI では、ログインの API を試すと自動で付く")
public class OpenApiConfig {

    static final String AUTH_COOKIE = "jwtCookie";
    public static final String TAG_AUTH = "認証";
    public static final String TAG_POST = "投稿";
    public static final String TAG_REPLY = "リプライ";
    public static final String TAG_REPOST = "リポスト";
    public static final String TAG_USER = "ユーザー";
    public static final String TAG_ME = "自分の設定";
    public static final String TAG_NOTIFICATION = "通知";
    public static final String TAG_DM = "DM";

    private static final Map<Integer, String> ERROR_DESCRIPTIONS = Map.of(
            400, "入力が正しくない",
            401, "ログインしていない(ログインの API では、メールアドレスかパスワードが違う)",
            403, "メールアドレスの確認が済んでいない",
            404, "見つからない(他の人の投稿やリプライを消そうとしたときも)",
            409, "メールアドレスかユーザー名が、既に使われている",
            413, "ファイルが大きすぎる",
            429, "回数が多すぎる。少し待ってからやり直す",
            503, "確認メールを送れなかった。少し待ってからやり直す");

    @Bean
    OperationCustomizer documentAuthAndErrors() {
        return (operation, handler) -> {
            boolean isPublic = handler.hasMethodAnnotation(PublicApi.class);
            if (isPublic) {
                // 空のリストで、全体の「ログインが要る」を打ち消す
                operation.setSecurity(List.of());
            } else {
                addError(operation, 401);
            }
            ApiErrors errors = handler.getMethodAnnotation(ApiErrors.class);
            if (errors != null) {
                for (int status : errors.value()) {
                    addError(operation, status);
                }
            }
            return operation;
        };
    }

    private static void addError(Operation operation, int status) {
        String description = ERROR_DESCRIPTIONS.get(status);
        if (description == null) {
            throw new IllegalArgumentException("説明の無いエラーです: " + status);
        }
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/ApiError");
        operation.getResponses().addApiResponse(String.valueOf(status), new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(ref))));
    }

    /** エラーの本文の形。どの API からも直接は返していないので、ライブラリは見つけられない */
    @Bean
    OpenApiCustomizer addApiErrorSchema() {
        return openApi -> ModelConverters.getInstance().read(ApiError.class)
                .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
    }

    /**
     * null になる項目のうち、別の型を指すもの(@Schema(types = {"object", "null"}))を anyOf に書き換える。
     * そのままだと「参照先の型であり、かつ null でもよい」となり、null が参照先の型に合わなくなる
     */
    @Bean
    OpenApiCustomizer allowNullReferences() {
        return openApi -> openApi.getComponents().getSchemas().values().forEach(model -> {
            Map<String, Schema> properties = model.getProperties();
            if (properties != null) {
                properties.replaceAll((name, property) -> isNullableReference(property) ? anyOfNull(property) : property);
            }
        });
    }

    private static boolean isNullableReference(Schema<?> property) {
        return property.get$ref() != null && property.getTypes() != null && property.getTypes().contains("null");
    }

    private static Schema<?> anyOfNull(Schema<?> property) {
        return new JsonSchema().description(property.getDescription()).anyOf(List.of(
                new JsonSchema().$ref(property.get$ref()),
                new JsonSchema().types(Set.of("null"))));
    }
}
