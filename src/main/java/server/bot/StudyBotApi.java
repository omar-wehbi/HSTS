package server.bot;

import java.util.List;

public interface StudyBotApi {
    String ask(String question, List<String> sources) throws Exception;
}
