package com.searchable.api.controller.payload;

/** Per-document outcome carried inside a batch index response. */
public record BatchIndexResult(String id, String status, String error) {

    public static BatchIndexResult ok(final String id) {
        return new BatchIndexResult(id, "INDEXED", null);
    }

    public static BatchIndexResult failed(final String id, final String error) {
        return new BatchIndexResult(id, "FAILED", error);
    }
}
