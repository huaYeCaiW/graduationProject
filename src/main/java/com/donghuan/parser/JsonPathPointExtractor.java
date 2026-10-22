package com.donghuan.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonPathPointExtractor {

    //Spring自带json解析器
    private final ObjectMapper objectMapper;

    public Optional<Double> extract(String payload, String jsonPath){
        try{
            JsonNode node = objectMapper.readTree(payload);
            for(String field : split(jsonPath)) {
                node = node.path(field);
            }
            //抽到的不是数字返回空
            if(!node.isNumber()){
                return Optional.empty();
            }
            //转Double返回
            return Optional.of(node.asDouble());
        } catch (Exception e){
            log.debug("解析失败 payload={} jsonPath={}", payload, jsonPath, e);
            return Optional.empty();
        }
    }

    private String[] split(String jsonPath){
        String path = jsonPath == null ? "" : jsonPath.trim();
        if(path.startsWith("$.")){
            path = path.substring(2);
        }else if(path.startsWith("$")){
            path = path.substring(1);
        }
        return path.isEmpty() ? new String[0] : path.split("\\.");
    }
}
