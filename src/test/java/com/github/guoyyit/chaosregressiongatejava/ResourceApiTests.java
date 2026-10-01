package com.github.guoyyit.chaosregressiongatejava;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void versioningAndHistory() throws Exception {
        // create -> version 1
        String createBody = "{\"data\":{\"name\":\"foo\"}}";
        String createResp = mvc.perform(post("/api/resources").contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = mapper.readTree(createResp);
        String id = created.get("id").asText();
        assert created.get("version").asLong() == 1;
        assert "CREATED".equals(created.get("code").asText());

        // read -> version 1
        mvc.perform(get("/api/resources/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.code").value("OK"));

        // update with correct version -> version 2
        String updateBody = "{\"version\":1,\"data\":{\"name\":\"bar\"}}";
        mvc.perform(post("/api/resources/" + id + "/update").contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.code").value("OK"));

        // update with stale version -> STALE_VERSION
        mvc.perform(post("/api/resources/" + id + "/update").contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.version").value(2));

        // history -> 2 versions
        mvc.perform(get("/api/resources/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].version").value(1))
                .andExpect(jsonPath("$.versions[1].version").value(2));
    }

    @Test
    void notFound() throws Exception {
        mvc.perform(get("/api/resources/r-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/resources/r-999/update").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"data\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void idempotentCreate() throws Exception {
        String body = "{\"idempotencyKey\":\"k1\",\"data\":{\"name\":\"foo\"}}";
        String r1 = mvc.perform(post("/api/resources").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/api/resources").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode n1 = mapper.readTree(r1);
        JsonNode n2 = mapper.readTree(r2);
        assert n1.get("id").asText().equals(n2.get("id").asText());
        assert n2.get("replayed").asBoolean();
        assert n2.get("code").asText().equals("OK");
    }
}
