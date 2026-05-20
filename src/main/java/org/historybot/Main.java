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
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        
        String botUsername = System.getenv("BOT_USERNAME") != null ? System.getenv("BOT_USERNAME") : dotenv.get("BOT_USERNAME");
        String dbUrl = System.getenv("DB_URL") != null ? System.getenv("DB_URL") : dotenv.get("DB_URL");
        String dbUser = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : dotenv.get("DB_USER");
        String dbPass = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : dotenv.get("DB_PASSWORD");
        String botToken = System.getenv("BOT_TOKEN") != null ? System.getenv("BOT_TOKEN") : dotenv.get("BOT_TOKEN");

        System.out.println("Starting History Bot: " + botUsername);

        // 1. Run Liquibase Migrations
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

        // 2. Register Telegram Bot
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            // Pass all config to HistoryBot
            botsApi.registerBot(new HistoryBot(botToken, botUsername, dbUrl, dbUser, dbPass));
            System.out.println("Bot is up and running!");
        } catch (Exception e) {
            System.err.println("Error during Bot registration: " + e.getMessage());
        }
    }
}
