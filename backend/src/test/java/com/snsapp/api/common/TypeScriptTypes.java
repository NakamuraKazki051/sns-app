package com.snsapp.api.common;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * API 仕様書(/v3/api-docs の JSON)の components.schemas から、画面の型(frontend/src/api-schema.ts)を作る。
 * 仕様書で使っている形だけを扱う(type・format・enum・items・$ref・anyOf と null)。
 * 知らない形が出てきたら止める(黙って unknown にすると、型が合っているように見えてしまう)
 */
final class TypeScriptTypes {

    static final String HEADER = """
            // このファイルは自動で作っている。直接書き換えない。
            // API を変えたら backend で mvn test -Dtest=OpenApiDocTest -Dopenapi.update=true を実行すると、
            // docs/openapi.yaml と一緒に作り直される(OpenApiDocTest が、コードと合っているかも確かめる)
            """;

    private TypeScriptTypes() {
    }

    static String generate(JsonNode openApi) {
        JsonNode schemas = openApi.get("components").get("schemas");
        // 名前の順に並べる(作り直したときに差分が出ないように)
        TreeMap<String, JsonNode> sorted = new TreeMap<>();
        schemas.properties().forEach(e -> sorted.put(e.getKey(), e.getValue()));
        StringBuilder out = new StringBuilder(HEADER);
        sorted.forEach((name, schema) -> {
            out.append('\n');
            comment(out, schema, "");
            if (schema.has("properties")) {
                out.append("export interface ").append(name).append(" {\n");
                Set<String> required = new HashSet<>();
                if (schema.has("required")) {
                    schema.get("required").forEach(r -> required.add(r.asString()));
                }
                TreeMap<String, JsonNode> properties = new TreeMap<>();
                schema.get("properties").properties().forEach(e -> properties.put(e.getKey(), e.getValue()));
                properties.forEach((property, value) -> {
                    comment(out, value, "  ");
                    out.append("  ").append(property).append(required.contains(property) ? ": " : "?: ")
                            .append(type(value, name + "." + property)).append(";\n");
                });
                out.append("}\n");
            } else {
                out.append("export type ").append(name).append(" = ").append(type(schema, name)).append(";\n");
            }
        });
        return out.toString();
    }

    private static void comment(StringBuilder out, JsonNode schema, String indent) {
        if (schema.has("description")) {
            out.append(indent).append("/** ").append(schema.get("description").asString().replace("*/", "* /"))
                    .append(" */\n");
        }
    }

    private static String type(JsonNode schema, String where) {
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asString();
            return ref.substring(ref.lastIndexOf('/') + 1);
        }
        if (schema.has("anyOf")) {
            List<String> parts = new ArrayList<>();
            schema.get("anyOf").forEach(s -> parts.add(type(s, where)));
            return String.join(" | ", parts);
        }
        List<String> types = new ArrayList<>();
        JsonNode type = schema.get("type");
        if (type == null) {
            throw new IllegalStateException(where + ": 型が書かれていません");
        }
        if (type.isArray()) {
            type.forEach(t -> types.add(t.asString()));
        } else {
            types.add(type.asString());
        }
        List<String> parts = new ArrayList<>();
        for (String t : types) {
            parts.add(switch (t) {
                case "string" -> schema.has("enum") ? literals(schema.get("enum")) : "string";
                case "integer", "number" -> "number";
                case "boolean" -> "boolean";
                case "null" -> "null";
                case "array" -> {
                    String item = type(schema.get("items"), where + "[]");
                    yield item.contains(" ") ? "(" + item + ")[]" : item + "[]";
                }
                case "object" -> schema.has("properties")
                        ? throwUnsupported(where, "入れ子の object")
                        : "Record<string, unknown>";
                default -> throwUnsupported(where, t);
            });
        }
        return String.join(" | ", parts);
    }

    private static String literals(JsonNode values) {
        List<String> parts = new ArrayList<>();
        values.forEach(v -> {
            if (!v.isNull()) {
                parts.add("\"" + v.asString() + "\"");
            }
        });
        return String.join(" | ", parts);
    }

    private static String throwUnsupported(String where, String what) {
        throw new IllegalStateException(where + ": 画面の型にできない形です(" + what + ")。TypeScriptTypes に足してください");
    }
}
