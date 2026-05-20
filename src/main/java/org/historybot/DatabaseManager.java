package org.historybot;

import io.github.cdimascio.dotenv.Dotenv;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void saveUserProgress(long userId, long scenarioId, int stepOrder, int score) {
        String sql = "INSERT INTO user_progress (user_id, scenario_id, current_step_id, score) " +
                     "VALUES (?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET " +
                     "scenario_id = EXCLUDED.scenario_id, current_step_id = EXCLUDED.current_step_id, score = EXCLUDED.score";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, scenarioId);
            pstmt.setLong(3, stepOrder);
            pstmt.setLong(4, score);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public UserProgress getUserProgress(long userId) {
        String sql = "SELECT * FROM user_progress WHERE user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new UserProgress(
                    rs.getLong("user_id"),
                    rs.getLong("scenario_id"),
                    rs.getInt("current_step_id"),
                    rs.getInt("score")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ScenarioStep getStep(long scenarioId, int stepOrder) {
        String sql = "SELECT * FROM steps WHERE scenario_id = ? AND step_order = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, scenarioId);
            pstmt.setInt(2, stepOrder);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                long stepId = rs.getLong("id");
                String content = rs.getString("content");
                String correctExplanation = rs.getString("correct_explanation");
                return new ScenarioStep(stepId, content, correctExplanation, getOptions(stepId));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<Option> getOptions(long stepId) {
        List<Option> options = new ArrayList<>();
        String sql = "SELECT * FROM options WHERE step_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, stepId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                options.add(new Option(
                    rs.getString("text"),
                    rs.getString("feedback"),
                    rs.getBoolean("is_correct")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return options;
    }

    public void clearMistakes(long userId, long scenarioId) {
        String sql = "DELETE FROM user_mistakes WHERE user_id = ? AND scenario_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, scenarioId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addMistake(long userId, long scenarioId, int stepOrder, String explanation) {
        String sql = "INSERT INTO user_mistakes (user_id, scenario_id, step_order, explanation) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, scenarioId);
            pstmt.setInt(3, stepOrder);
            pstmt.setString(4, explanation);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getMistakes(long userId, long scenarioId) {
        List<String> mistakes = new ArrayList<>();
        String sql = "SELECT explanation FROM user_mistakes WHERE user_id = ? AND scenario_id = ? ORDER BY step_order";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, scenarioId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                mistakes.add(rs.getString("explanation"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mistakes;
    }

    public String getHistoricalSummary(long scenarioId) {
        String sql = "SELECT historical_summary FROM scenarios WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, scenarioId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("historical_summary");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Информация о реальных событиях временно недоступна.";
    }

    public static class UserProgress {
        public long userId, scenarioId;
        public int currentStepOrder, score;
        public UserProgress(long userId, long scenarioId, int currentStepOrder, int score) {
            this.userId = userId; this.scenarioId = scenarioId;
            this.currentStepOrder = currentStepOrder; this.score = score;
        }
    }

    public static class ScenarioStep {
        public long id;
        public String content;
        public String correctExplanation;
        public List<Option> options;
        public ScenarioStep(long id, String content, String correctExplanation, List<Option> options) {
            this.id = id; this.content = content; 
            this.correctExplanation = correctExplanation;
            this.options = options;
        }
    }

    public static class Option {
        public String text, feedback;
        public boolean isCorrect;
        public Option(String text, String feedback, boolean isCorrect) {
            this.text = text; this.feedback = feedback; this.isCorrect = isCorrect;
        }
    }
}
