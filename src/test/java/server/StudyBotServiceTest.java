package server;

import common.entities.Role;
import common.entities.User;
import common.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.bot.StudyBotApi;
import server.db.ExamSessionDAO;
import server.db.StudyBotDAO;
import server.db.UserDAO;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudyBotServiceTest {
    private StudyBotDAO dao; private UserDAO users; private ExamSessionDAO sessions; private StudyBotApi api; private StudyBotService service;
    private final User student=new User(10,"maya",Role.STUDENT,"Maya","123");
    private final User teacher=new User(2,"teacher",Role.TEACHER,"Teacher",null);
    @BeforeEach void setUp(){dao=mock(StudyBotDAO.class);users=mock(UserDAO.class);sessions=mock(ExamSessionDAO.class);api=mock(StudyBotApi.class);service=new StudyBotService(dao,users,sessions,api);}

    @Test void creationRequiresNameAndExistingAssignment(){when(dao.courseExists(1)).thenReturn(true);when(dao.teacherCanEdit(2,1)).thenReturn(true);when(dao.createBot(eq(1),eq("Math Helper"),eq(true),eq(2))).thenReturn(new StudyBotView(1,"Math Helper",false,true,2,LocalDateTime.now(),0));Message r=service.create(teacher,new CreateStudyBotRequest(1,"Math Helper",true));assertEquals(Message.Command.SUCCESS,r.getCommand());}
    @Test void studentMustBeEnrolled(){when(users.isEnrolled(10,1)).thenReturn(false);Message result=service.ask(student,new StudyBotQuestionRequest(1,"Explain trees"));assertEquals(Message.Command.ERROR,result.getCommand());verifyNoInteractions(api);}
    @Test void botBlockedOnlyForActiveExamInSameCourse(){when(users.isEnrolled(10,1)).thenReturn(true);when(sessions.hasActiveSessionForCourse(eq(10),eq(1),any())).thenReturn(true);Message result=service.ask(student,new StudyBotQuestionRequest(1,"Explain trees"));assertEquals(Message.Command.ERROR,result.getCommand());verifyNoInteractions(api);}
    @Test void differentCourseExamDoesNotBlockBot() throws Exception {when(users.isEnrolled(10,1)).thenReturn(true);when(sessions.hasActiveSessionForCourse(eq(10),eq(1),any())).thenReturn(false);when(dao.getBot(1)).thenReturn(new StudyBotView(1,"Bot",true,false,2,LocalDateTime.now(),1));when(dao.getSources(1)).thenReturn(List.of(new StudyBotSourceView(1,1,"Notes","Trees",2,LocalDateTime.now())));when(api.ask(anyString(),anyList())).thenReturn("A tree is...");when(dao.saveHistory(1,10,"Explain trees","A tree is...")).thenReturn(new StudyBotAnswer(5,1,"Explain trees","A tree is...",LocalDateTime.now()));Message result=service.ask(student,new StudyBotQuestionRequest(1,"Explain trees"));assertEquals(Message.Command.SUCCESS,result.getCommand());}
    @Test void questionBankIsAddedToContext() throws Exception {when(users.isEnrolled(10,1)).thenReturn(true);when(dao.getBot(1)).thenReturn(new StudyBotView(1,"Bot",true,true,2,LocalDateTime.now(),0));when(dao.getQuestionBankContext(1)).thenReturn(List.of("Question: AVL?"));when(api.ask(anyString(),argThat(x->x.contains("Question: AVL?")))).thenReturn("Balanced tree");when(dao.saveHistory(anyInt(),anyInt(),anyString(),anyString())).thenReturn(new StudyBotAnswer(1,1,"q","a",LocalDateTime.now()));assertEquals(Message.Command.SUCCESS,service.ask(student,new StudyBotQuestionRequest(1,"AVL?")).getCommand());}
    @Test void apiFailureIsSavedWithoutLeakingTechnicalMessage() throws Exception {when(users.isEnrolled(10,1)).thenReturn(true);when(dao.getBot(1)).thenReturn(new StudyBotView(1,"Bot",true,false,2,LocalDateTime.now(),1));when(dao.getSources(1)).thenReturn(List.of(new StudyBotSourceView(1,1,"N","C",2,LocalDateTime.now())));when(api.ask(anyString(),anyList())).thenThrow(new IllegalStateException("HTTP 401 secret"));Message r=service.ask(student,new StudyBotQuestionRequest(1,"q"));assertEquals(Message.Command.ERROR,r.getCommand());assertFalse(String.valueOf(r.getPayload()).contains("401"));verify(dao).saveHistory(eq(1),eq(10),eq("q"),anyString(),eq("NO_ANSWER"));}
    @Test void onlyAssignedTeacherCanReadAggregateUsage(){when(dao.teacherCanEdit(2,1)).thenReturn(false);Message result=service.aggregateHistory(teacher,1);assertEquals(Message.Command.ERROR,result.getCommand());verify(dao,never()).usage(anyInt());}
}
