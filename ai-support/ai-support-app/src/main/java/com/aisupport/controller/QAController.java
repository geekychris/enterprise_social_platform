package com.aisupport.controller;

import com.aisupport.qa.AgenticQAService;
import com.aisupport.qa.QAService;
import com.aisupport.qa.QATraceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/qa")
public class QAController {

    private final QAService qaService;
    private final AgenticQAService agenticQAService;
    private final QATraceService traceService;

    public QAController(QAService qaService, AgenticQAService agenticQAService, QATraceService traceService) {
        this.qaService = qaService;
        this.agenticQAService = agenticQAService;
        this.traceService = traceService;
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, Object> body) {
        long ksId = Long.parseLong(body.get("knowledgeSetId").toString());
        String question = (String) body.get("question");
        Long socialPostId = body.get("socialPostId") != null ?
                Long.parseLong(body.get("socialPostId").toString()) : null;
        Long socialUserId = body.get("socialUserId") != null ?
                Long.parseLong(body.get("socialUserId").toString()) : null;

        var result = qaService.answer(ksId, question, socialPostId, socialUserId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("answer", result.answer());
        response.put("confidence", result.confidence());
        response.put("method", result.method());
        response.put("suggestHuman", result.suggestHuman());
        response.put("interactionId", result.interactionId());
        response.put("traceId", result.traceId());
        response.put("citations", result.citations().stream().map(c -> Map.of(
                "documentId", c.documentId(),
                "title", c.title(),
                "snippet", c.snippet()
        )).toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ask-agentic")
    public ResponseEntity<Map<String, Object>> askAgentic(@RequestBody Map<String, Object> body) {
        long ksId = Long.parseLong(body.get("knowledgeSetId").toString());
        String question = (String) body.get("question");
        Long socialPostId = body.get("socialPostId") != null ?
                Long.parseLong(body.get("socialPostId").toString()) : null;
        Long socialUserId = body.get("socialUserId") != null ?
                Long.parseLong(body.get("socialUserId").toString()) : null;

        var result = agenticQAService.answer(ksId, question, socialPostId, socialUserId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("answer", result.answer());
        response.put("confidence", result.confidence());
        response.put("method", "AGENTIC");
        response.put("suggestHuman", result.suggestHuman());
        response.put("interactionId", result.interactionId());
        response.put("traceId", result.traceId());
        response.put("steps", result.steps().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("iteration", s.iteration());
            m.put("thought", s.thought());
            m.put("tool", s.toolName());
            m.put("args", s.toolArgs());
            m.put("resultPreview", s.toolResult().length() > 500 ? s.toolResult().substring(0, 500) + "..." : s.toolResult());
            m.put("durationMs", s.durationMs());
            return m;
        }).toList());
        response.put("citations", result.citations());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/route")
    public ResponseEntity<List<Map<String, Object>>> routeQuestion(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        return ResponseEntity.ok(qaService.routeQuestion(question));
    }

    @GetMapping("/traces")
    public ResponseEntity<List<Map<String, Object>>> getTraces(
            @RequestParam(required = false) Long knowledgeSetId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(traceService.getRecentTraces(knowledgeSetId, limit));
    }

    @GetMapping("/traces/{id}")
    public ResponseEntity<Map<String, Object>> getTrace(@PathVariable long id) {
        var trace = traceService.getTrace(id);
        return trace != null ? ResponseEntity.ok(trace) : ResponseEntity.notFound().build();
    }

    @PostMapping("/feedback")
    public ResponseEntity<Map<String, String>> feedback(@RequestBody Map<String, Object> body) {
        // TODO: record feedback on interaction
        return ResponseEntity.ok(Map.of("status", "recorded"));
    }
}
