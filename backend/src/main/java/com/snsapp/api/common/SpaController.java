package com.snsapp.api.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 画面の URL(/@ユーザー名 や /posts/1 など)を直接開いたり再読み込みしたりしたときも、React の index.html を返す。
 * どの画面を出すかは React Router が URL を見て決める。/api/ と静的ファイル(/assets/...)はここに来ない
 */
@Controller
public class SpaController {

    @GetMapping({"/login", "/search", "/settings", "/posts/{id:\\d+}",
            "/{handle:@[A-Za-z0-9_]{1,15}}",
            "/{handle:@[A-Za-z0-9_]{1,15}}/following",
            "/{handle:@[A-Za-z0-9_]{1,15}}/followers"})
    public String index() {
        return "forward:/index.html";
    }
}
