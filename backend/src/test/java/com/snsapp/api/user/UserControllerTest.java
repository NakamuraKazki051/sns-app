package com.snsapp.api.user;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.post.Post;
import com.snsapp.api.post.PostController;
import com.snsapp.api.post.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest extends ApiTestSupport {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void userPageShowsTheProfileAndPostCountWithoutTheEmail() throws Exception {
        String alice = newUsername();
        MockHttpSession session = registerAndLogin(alice);
        for (int i = 0; i < 3; i++) {
            createPost(session, "アリス " + i);
        }
        createPost(registerAndLogin(), "他の人の投稿");

        mockMvc.perform(get("/api/users/" + alice).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(alice))
                .andExpect(jsonPath("$.displayName").value("表示名 " + alice))
                .andExpect(jsonPath("$.postsCount").value(3))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void usernameLookupIgnoresCase() throws Exception {
        MockHttpSession session = registerAndLogin("CamelCase");
        mockMvc.perform(get("/api/users/camelcase").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("CamelCase"));
    }

    @Test
    void userPostsListOnlyThatUsersPostsNewestFirstPageByPage() throws Exception {
        String alice = newUsername();
        MockHttpSession session = registerAndLogin(alice);
        User user = userRepository.findByUsername(alice).orElseThrow();
        for (int i = 0; i < PostController.PAGE_SIZE + 3; i++) {
            postRepository.save(new Post(user, "投稿 " + i));
        }
        createPost(registerAndLogin(), "ボブ");

        List<String> expected = postRepository.findAll().stream()
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .map(Post::getId).sorted((a, b) -> Long.compare(b, a)).map(String::valueOf).toList();
        assertEquals(expected, allPages("/api/users/" + alice + "/posts", session));
    }

    @Test
    void unknownOrMalformedUsernamesAreNotFound() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(get("/api/users/nobody_here").session(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/nobody_here/posts").session(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/{username}", "ハイフン").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void userPagesNeedALogin() throws Exception {
        String username = newUsername();
        registerAndLogin(username);
        mockMvc.perform(get("/api/users/" + username)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/" + username + "/posts")).andExpect(status().isUnauthorized());
    }
}
