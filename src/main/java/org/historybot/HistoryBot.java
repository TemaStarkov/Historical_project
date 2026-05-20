package org.historybot;

import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

public class HistoryBot extends TelegramLongPollingBot {

    private final String botToken;
    private final String botUsername;
    private final DatabaseManager dbManager;

    public HistoryBot(String botToken, String botUsername, String dbUrl, String dbUser, String dbPass) {
        this.botToken = botToken;
        this.botUsername = botUsername != null ? botUsername.replace("@", "") : "";
        this.dbManager = new DatabaseManager(dbUrl, dbUser, dbPass);
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        System.out.println("DEBUG: Received Update ID: " + update.getUpdateId());
        if (update.hasMessage() && update.getMessage().hasText()) {
            System.out.println("DEBUG: Processing Message from " + update.getMessage().getChatId() + ": " + update.getMessage().getText());
            handleMessage(update);
        } else if (update.hasCallbackQuery()) {
            System.out.println("DEBUG: Processing Callback from " + update.getCallbackQuery().getMessage().getChatId() + ": " + update.getCallbackQuery().getData());
            handleCallback(update);
        }
    }

    private void handleMessage(Update update) {
        String text = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        if (text.equals("/start")) {
            sendMainMenu(chatId);
        }
    }

    private void handleCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        long userId = update.getCallbackQuery().getFrom().getId();

        if (data.startsWith("scenario_")) {
            long scenarioId = Long.parseLong(data.split("_")[1]);
            startScenario(chatId, userId, scenarioId);
        } else if (data.startsWith("option_")) {
            int optionIndex = Integer.parseInt(data.split("_")[1]);
            processChoice(chatId, userId, optionIndex);
        } else if (data.equals("main_menu")) {
            sendMainMenu(chatId);
        }
    }

    private void sendMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Приветствую. Вы находитесь в архиве исторических симуляций.\n\n" +
                "Здесь собраны ключевые моменты истории — переломные точки, где одно решение меняло судьбы стран и миллионов людей. " +
                "Ваша задача — взять на себя роль участника этих событий (агента разведки, дипломата или аналитика) и провести мир через кризис.\n\n" +
                "📜 Как это работает:\n" +
                "1. Вы выбираете исторический сценарий.\n" +
                "2. Я описываю ситуацию и предлагаю варианты действий.\n" +
                "3. Ваш выбор имеет последствия. Вы можете следовать реальной истории или создать альтернативную ветку событий.\n\n" +
                "⚖️ Оценка:\n" +
                "За выбор, который соответствует реальным историческим фактам, вы получаете баллы. " +
                "Альтернативные решения баллов не приносят, но открывают иной взгляд на развитие событий. " +
                "В конце каждой миссии вы получите итоговый отчет.\n\n" +
                "Готовы начать? Выберите дело из списка ниже:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        rows.add(createRow("Сценарий 1: Карибский кризис (США)", "scenario_1"));
        rows.add(createRow("Сценарий 2: Берлинский кризис 1961 (СССР)", "scenario_2"));
        rows.add(createRow("Сценарий 3: Пражская весна 1968 (СССР)", "scenario_3"));
        
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        executeSafe(message);
    }

    private void startScenario(long chatId, long userId, long scenarioId) {
        dbManager.clearMistakes(userId, scenarioId);
        dbManager.saveUserProgress(userId, scenarioId, 0, 0);
        sendStep(chatId, scenarioId, 0);
    }

    private void sendStep(long chatId, long scenarioId, int stepOrder) {
        DatabaseManager.ScenarioStep step = dbManager.getStep(scenarioId, stepOrder);
        if (step == null) {
            DatabaseManager.UserProgress progress = dbManager.getUserProgress(chatId);
            finishGame(chatId, progress.score, scenarioId);
            return;
        }

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(step.content);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < step.options.size(); i++) {
            rows.add(createRow(step.options.get(i).text, "option_" + i));
        }
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        executeSafe(message);
    }

    private void processChoice(long chatId, long userId, int optionIndex) {
        DatabaseManager.UserProgress progress = dbManager.getUserProgress(userId);
        if (progress == null) return;

        DatabaseManager.ScenarioStep step = dbManager.getStep(progress.scenarioId, progress.currentStepOrder);
        DatabaseManager.Option selectedOption = step.options.get(optionIndex);

        if (!selectedOption.isCorrect && progress.currentStepOrder > 0) {
            dbManager.addMistake(userId, progress.scenarioId, progress.currentStepOrder, step.correctExplanation);
        }

        int newScore = progress.score + (selectedOption.isCorrect ? 1 : 0);
        int nextStep = progress.currentStepOrder + 1;

        // Показываем результат выбора
        SendMessage feedback = new SendMessage();
        feedback.setChatId(String.valueOf(chatId));
        feedback.setText("Результат: " + selectedOption.feedback);
        executeSafe(feedback);

        // Сохраняем прогресс и шлем следующий шаг
        dbManager.saveUserProgress(userId, progress.scenarioId, nextStep, newScore);
        sendStep(chatId, progress.scenarioId, nextStep);
    }

    private void finishGame(long chatId, int score, long scenarioId) {
        List<String> mistakes = dbManager.getMistakes(chatId, scenarioId);
        
        StringBuilder resultText = new StringBuilder();
        resultText.append("🏁 *ИГРА ОКОНЧЕНА*\n\n");
        resultText.append(String.format("📊 *Ваш итоговый балл:* %d из 5\n\n", score));

        if (mistakes.isEmpty()) {
            resultText.append("🌟 *Великолепно!* Вы безупречно справились с миссией, точно следуя историческому пути. Мир в безопасности благодаря вашей мудрости.\n\n");
        } else {
            resultText.append("📜 *Историческая справка по вашим ошибкам:*\n");
            for (String mistake : mistakes) {
                resultText.append("• ").append(mistake).append("\n");
            }
            resultText.append("\n");
        }

        resultText.append("Хотите попробовать другой сценарий или пройти этот заново?");

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(resultText.toString());
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createRow("🔙 В главное меню", "main_menu"));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        
        executeSafe(message);
    }

    private List<InlineKeyboardButton> createRow(String text, String callbackData) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        row.add(button);
        return row;
    }

    private void executeSafe(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
