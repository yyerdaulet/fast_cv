package com.example.fast_cv.telegram.bot;

import com.example.fast_cv.telegram.ai_integration.model.CvData;
import com.example.fast_cv.telegram.ai_integration.service.CvParserService;
import com.example.fast_cv.telegram.service.SessionManager;
import com.example.fast_cv.telegram.model.UserSession;
import com.example.fast_cv.telegram.model.State;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String botUserName;

    @Value("${telegram.bot.token}")
    private String token;

    private final CvParserService cvParserService;

    @Override
    public String getBotUsername(){
        return botUserName;
    }

    @Override
    public String getBotToken(){
        return token;
    }

    private final SessionManager sessionManager = new SessionManager();

    private static final List<String[]> TEMPLATES = List.of(
            new String[]{"Classic","classic.png"},
            new String[]{"Modern","modern.png"},
            new String[]{"Professional","professional.png"},
            new String[]{"Creative","creative.png"}
    );


    @Override
    public void onUpdateReceived(Update update) {
        if(!update.hasMessage() || !update.getMessage().hasText()) return;

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        UserSession session = sessionManager.get(chatId);

        switch(session.getState()){

            case START -> {
                send(chatId,"Enter your full name");
                session.setState(State.WAITING_FULL_NAME);
            }

            case WAITING_FULL_NAME -> {
                session.setFullName(text);
                send(chatId,"enter your birthday");
                session.setState(State.WAITING_BIRTHDAY);
            }

            case WAITING_BIRTHDAY -> {
                session.setBirthday(text);
                send(chatId,"Enter your City");
                session.setState(State.WAITING_CITY);
            }

            case WAITING_CITY -> {
                session.setCity(text);
                send(chatId,"Enter your email");
                session.setState(State.WAITING_EMAIL);
            }

            case WAITING_EMAIL -> {
                session.setEmail(text);
                send(chatId,"Enter your Degree");
                session.setState(State.WAITING_EDUCATION);
            }

            case WAITING_EDUCATION -> {
                session.setEducation(text);
                send(chatId,"Enter your experience");
                session.setState(State.WAITING_EXPERIENCE);
            }

            case WAITING_EXPERIENCE -> {
                session.setExperience(text);
                send(chatId,"Enter your projects");
                session.setState(State.WAITING_PROJECTS);
            }

            case WAITING_PROJECTS -> {
                session.setProjects(text);
                send(chatId,"Enter your hard skills");
                session.setState(State.WAITING_HARD_SKILLS);
            }

            case WAITING_HARD_SKILLS -> {
                session.setHard_skills(text);
                send(chatId,"Enter your soft skills");
                session.setState(State.WAITING_SOFT_SKILLS);
            }

            case WAITING_SOFT_SKILLS-> {
                session.setSoft_skills(text);
                String rawData = collectRowData(session);
                CvData json = cvParserService.parse(rawData);
                send(chatId,"Add additional links");
                session.setState(State.WAITING_LINKS);
            }

            case WAITING_LINKS -> {
                session.setLinks(text);
                send(chatId,"Please choose template");
                sendTemplateMediaGroup(chatId);
                sendTemplateKeyBoard(chatId);
                session.setState(State.WAITING_TEMPLATE);
            }

            case WAITING_TEMPLATE -> {
                boolean valid = TEMPLATES.stream()
                        .anyMatch(t -> t[0].equalsIgnoreCase(text));

                if(!valid){
                    send(chatId,"Please choose a template using the buttons below.");
                    sendTemplateKeyBoard(chatId);
                    return;
                }

                session.setTemplate(text);
                send(chatId, "Great choice! Wait a minute");
                session.setState(State.DONE);
            }

            case DONE -> {
                send(chatId,"CV has been generated");
            }

        }


    }

    private static @NonNull String collectRowData(UserSession session) {
        return "education : " + session.getEducation() + "\n" +
                "projects : " + session.getProjects() + "\n" +
                "experience : " + session.getExperience();
    }

    private void sendTemplateKeyBoard(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText("Select a template");

        List<KeyboardRow> rows = new ArrayList<>();
        for (String[] tpl : TEMPLATES){
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(tpl[0]));
            rows.add(row);
        }

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);
        msg.setReplyMarkup(keyboard);

        try{
            execute(msg);
        }catch (TelegramApiException e){
            e.printStackTrace();
        }

    }

    private void sendTemplateMediaGroup(Long chatId) {
        List<InputMedia> media = new ArrayList<>();

        for (int i = 0; i < TEMPLATES.size(); i++) {
            String[] tpl    = TEMPLATES.get(i);
            String fileName = tpl[1];

            try {
                InputStream is = getClass().getClassLoader()
                        .getResourceAsStream("templates/" + fileName);

                if (is == null) {
                    System.out.println("NOT FOUND: templates/" + fileName);
                    continue;
                }


                byte[] bytes = is.readAllBytes();
                is.close();

                InputMediaPhoto photo = new InputMediaPhoto();

                photo.setMedia(new java.io.ByteArrayInputStream(bytes), fileName);

                if (i == 0) {
                    photo.setCaption("Choose a template 👇");
                }
                media.add(photo);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (media.isEmpty()) {
            send(chatId, "Template previews not available. Choose by name:");
            return;
        }

        SendMediaGroup mediaGroup = new SendMediaGroup();
        mediaGroup.setChatId(chatId);
        mediaGroup.setMedias(media);

        try {
            execute(mediaGroup);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }



    private void send(Long chatId,String text){
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);

        msg.setReplyMarkup(new ReplyKeyboardRemove(true));
        try {
            execute(msg);
        }catch(TelegramApiException e){
            e.printStackTrace();
        }
    }
}

