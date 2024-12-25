package com.example.csvdb.engine;

import java.util.Map;

public interface SQLParser {
    Map<SQLParserKey, Object> parse(String sql);
}
