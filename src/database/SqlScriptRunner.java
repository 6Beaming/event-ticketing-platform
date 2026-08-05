package database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class SqlScriptRunner {
    public void run(Connection connection, Path scriptPath) throws IOException, SQLException {
        List<String> lines = Files.readAllLines(scriptPath, StandardCharsets.UTF_8);
        StringBuilder statementText = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.regionMatches(true, 0, "SOURCE ", 0, 7)) {
                if (statementText.length() != 0) {
                    throw new SQLException("SOURCE appeared inside an SQL statement");
                }
                String source = trimmed.substring(7).trim();
                if (source.endsWith(";")) {
                    source = source.substring(0, source.length() - 1).trim();
                }
                Path included = Paths.get(source).normalize();
                run(connection, included);
                continue;
            }

            statementText.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                execute(connection, statementText.toString());
                statementText.setLength(0);
            }
        }

        if (!statementText.toString().trim().isEmpty()) {
            throw new SQLException("SQL script ended before its final semicolon: " + scriptPath);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
