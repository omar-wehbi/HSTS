package server;

import common.entities.Role;
import common.entities.User;
import common.network.*;
import server.bot.StudyBotApi;
import server.bot.StudyBotDocumentExtractor;
import server.db.ExamSessionDAO;
import server.db.StudyBotDAO;
import server.db.UserDAO;

import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Scenarios 13-14: course bot management, guarded use and privacy-safe history. */
public class StudyBotService {
    private static final String NO_ANSWER="The study bot could not provide a suitable answer. Please try again later.";
    private final StudyBotDAO dao; private final UserDAO users; private final ExamSessionDAO sessions; private final StudyBotApi api;
    public StudyBotService(StudyBotDAO dao, UserDAO users, ExamSessionDAO sessions, StudyBotApi api){this.dao=dao;this.users=users;this.sessions=sessions;this.api=api;}

    public Message create(User caller,Object payload){
        try{Authorization.requireRole(caller,Role.TEACHER);if(!(payload instanceof CreateStudyBotRequest))return error("CREATE_STUDY_BOT requires CreateStudyBotRequest.");CreateStudyBotRequest r=(CreateStudyBotRequest)payload;int course=positive(r.getCourseId(),"Course ID");if(!dao.courseExists(course))return error("Course does not exist.");requireTeacher(caller,course);return ok(dao.createBot(course,text(r.getName(),"Bot name",120),r.isIncludeQuestionBank(),caller.getId()));}catch(Exception e){return fail(e);}
    }
    public Message getBot(User caller,Object payload){try{int course=id(payload,"GET_STUDY_BOT");if(caller==null)throw new AuthorizationException("Please log in first.");if(caller.getRole()==Role.TEACHER)requireTeacher(caller,course);else if(caller.getRole()==Role.STUDENT)Authorization.requireEnrollment(caller,course,users::isEnrolled);else throw new AuthorizationException("Only course teachers and enrolled students may view the study bot.");StudyBotView bot=dao.getBot(course);return bot==null?error("Study bot does not exist."):ok(bot);}catch(Exception e){return fail(e);}}
    public Message setAvailability(User caller,Object payload){try{Authorization.requireRole(caller,Role.TEACHER);if(!(payload instanceof SetStudyBotAvailabilityRequest))return error("SET_STUDY_BOT_AVAILABILITY requires SetStudyBotAvailabilityRequest.");SetStudyBotAvailabilityRequest r=(SetStudyBotAvailabilityRequest)payload;requireTeacher(caller,r.getCourseId());if(dao.getBot(r.getCourseId())==null)return error("Study bot does not exist.");return ok(dao.setAvailability(r.getCourseId(),r.isAvailable()));}catch(Exception e){return fail(e);}}
    public Message addSource(User caller,Object payload){try{Authorization.requireRole(caller,Role.TEACHER);if(!(payload instanceof StudyBotSourceRequest))return error("ADD_STUDY_BOT_SOURCE requires StudyBotSourceRequest.");StudyBotSourceRequest r=(StudyBotSourceRequest)payload;requireExistingBotAndTeacher(caller,r.getCourseId());return ok(dao.addSource(r.getCourseId(),text(r.getTitle(),"Source title",120),text(r.getContent(),"Source content",200000),StudyBotSourceType.TEXT,null,caller.getId()));}catch(Exception e){return fail(e);}}
    public Message addDocumentSource(User caller,Object payload){try{Authorization.requireRole(caller,Role.TEACHER);if(!(payload instanceof StudyBotDocumentSourceRequest))return error("ADD_STUDY_BOT_DOCUMENT_SOURCE requires StudyBotDocumentSourceRequest.");StudyBotDocumentSourceRequest r=(StudyBotDocumentSourceRequest)payload;requireExistingBotAndTeacher(caller,r.getCourseId());String file=text(r.getFileName(),"File name",255);String content=StudyBotDocumentExtractor.extract(file,r.getMimeType(),r.getFileData());StudyBotSourceType type=file.toLowerCase().endsWith(".pdf")?StudyBotSourceType.PDF:StudyBotSourceType.WORD;return ok(dao.addSource(r.getCourseId(),text(r.getTitle(),"Source title",120),content,type,file,caller.getId()));}catch(Exception e){return fail(e);}}
    public Message updateSource(User caller,Object payload){try{Authorization.requireRole(caller,Role.TEACHER);if(!(payload instanceof UpdateStudyBotSourceRequest))return error("UPDATE_STUDY_BOT_SOURCE requires UpdateStudyBotSourceRequest.");UpdateStudyBotSourceRequest r=(UpdateStudyBotSourceRequest)payload;StudyBotSourceView old=dao.getSource(r.getSourceId());if(old==null)return error("Study-bot source does not exist.");requireTeacher(caller,old.getCourseId());return ok(dao.updateSource(r.getSourceId(),text(r.getTitle(),"Source title",120),text(r.getContent(),"Source content",200000),caller.getId()));}catch(Exception e){return fail(e);}}
    public Message deleteSource(User caller,Object payload){try{Authorization.requireRole(caller,Role.TEACHER);int sourceId=id(payload,"DELETE_STUDY_BOT_SOURCE");StudyBotSourceView old=dao.getSource(sourceId);if(old==null)return error("Study-bot source does not exist.");requireTeacher(caller,old.getCourseId());return ok(dao.deleteSource(sourceId));}catch(Exception e){return fail(e);}}
    public Message getSources(User caller,Object payload){try{Authorization.requireRole(caller,Role.TEACHER);int course=id(payload,"GET_STUDY_BOT_SOURCES");requireTeacher(caller,course);return ok(dao.getSources(course));}catch(Exception e){return fail(e);}}
    public Message ask(User caller,Object payload){
        try{Authorization.requireRole(caller,Role.STUDENT);if(!(payload instanceof StudyBotQuestionRequest))return error("ASK_STUDY_BOT requires StudyBotQuestionRequest.");StudyBotQuestionRequest r=(StudyBotQuestionRequest)payload;int course=positive(r.getCourseId(),"Course ID");Authorization.requireEnrollment(caller,course,users::isEnrolled);LocalDateTime now=LocalDateTime.now();sessions.expireOverdueSessions(now);if(sessions.hasActiveSessionForCourse(caller.getId(),course,now))return error("The study bot is unavailable while you are taking an exam in this course.");StudyBotView bot=dao.getBot(course);if(bot==null||!bot.isAvailable())return error("The study bot is unavailable for this course.");String q=text(r.getQuestion(),"Question",2000);List<String> context=new ArrayList<>();for(StudyBotSourceView source:dao.getSources(course))context.add(source.getTitle()+"\n"+source.getContent());if(bot.isIncludeQuestionBank())context.addAll(dao.getQuestionBankContext(course));if(context.isEmpty())return error("The study bot has no information sources yet.");
            try{String answer=api.ask(q,context);return ok(dao.saveHistory(course,caller.getId(),q,answer));}
            catch(Exception apiError){System.err.println("[StudyBotService] external API failure: "+apiError);String message=apiError instanceof HttpTimeoutException?"The study bot did not respond in time. Please try again later.":NO_ANSWER;dao.saveHistory(course,caller.getId(),q,message,"NO_ANSWER");return error(message);}
        }catch(Exception e){return fail(e);}
    }
    public Message personalHistory(User caller){try{Authorization.requireRole(caller,Role.STUDENT);return ok(dao.personalHistory(caller.getId()));}catch(Exception e){return fail(e);}}
    public Message aggregateHistory(User caller,Object payload){try{Authorization.requireRole(caller,Role.TEACHER);int course=id(payload,"GET_STUDY_BOT_USAGE");requireTeacher(caller,course);return ok(dao.usage(course));}catch(Exception e){return fail(e);}}

    private void requireExistingBotAndTeacher(User u,int c){requireTeacher(u,c);if(dao.getBot(c)==null)throw new IllegalArgumentException("Study bot does not exist for this course.");}
    private void requireTeacher(User u,int c){if(!dao.teacherCanEdit(u.getId(),c))throw new AuthorizationException("You are not assigned to this course.");}
    private static int positive(int v,String n){if(v<=0)throw new IllegalArgumentException(n+" must be positive.");return v;}
    private static int id(Object p,String command){if(!(p instanceof Integer)||((Integer)p)<=0)throw new IllegalArgumentException(command+" requires a positive ID.");return(Integer)p;}
    private static String text(String s,String name,int max){if(s==null||s.trim().isEmpty())throw new IllegalArgumentException(name+" is required.");String v=s.trim();if(v.length()>max)throw new IllegalArgumentException(name+" is too long.");return v;}
    private static Message ok(Object p){return new Message(Message.Command.SUCCESS,p);}private static Message error(String s){return new Message(Message.Command.ERROR,s);}
    private static Message fail(Exception e){if(!(e instanceof IllegalArgumentException)&&!(e instanceof AuthorizationException))System.err.println("[StudyBotService] "+e);return error(e.getMessage()==null?"Study-bot operation failed.":e.getMessage());}
}
