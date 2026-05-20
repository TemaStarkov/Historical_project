package org.historybot;

import io.github.cdimascio.dotenv.Dotenv;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.Connection;
import java.sql.DriverManager;

public class Main {
    public static void main(String[] args) {
        // Try to load .env file if it exists (local development)
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        
        // Prioritize System Environment variables (standard for Docker/Servers)
        String botUsername = getEnv("BOT_USERNAME", dotenv);
        String botToken = getEnv("BOT_TOKEN", dotenv);
        String dbUrl = getEnv("DB_URL", dotenv);
        String dbUser = getEnv("DB_USER", dotenv);
        String dbPass = getEnv("DB_PASSWORD", dotenv);

        System.out.println("Starting History Bot: " + botUsername);

        if (botToken == null || botToken.isEmpty()) {
            System.err.println("FATAL: BOT_TOKEN is missing!");
            return;
        }

        // 1. Run Liquibase Migrations
        if (dbUrl != null) {
            try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.xml", 
                        new ClassLoaderResourceAccessor(), database);
                liquibase.update("");
                System.out.println("Database migrations applied successfully!");
            } catch (Exception e) {
                System.err.println("Error during DB initialization: " + e.getMessage());
            }
        } else {
            System.err.println("WARNING: DB_URL is missing, skipping migrations.");
        }

        // 2. Register Telegram Bot
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new HistoryBot(botToken, botUsername, dbUrl, dbUser, dbPass));
            System.out.println("Bot is up and running!");
        } catch (Exception e) {
            System.err.println("Error during Bot registration: " + e.getMessage());
        }
    }

    private static String getEnv(String key, Dotenv dotenv) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            value = dotenv.get(key);
        }
        return value;
    }
}
