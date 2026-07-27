package common.network;

import common.entities.StudentAnswer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Saves the student's current selections without closing the exam. */
public class SaveAnswersRequest implements Serializable { private static final long serialVersionUID=1L; private int sessionId; private List<StudentAnswer> answers=new ArrayList<>();
 public SaveAnswersRequest(){} public SaveAnswersRequest(int id, List<StudentAnswer>a){sessionId=id;answers=a==null?new ArrayList<>():new ArrayList<>(a);} public int getSessionId(){return sessionId;} public void setSessionId(int v){sessionId=v;} public List<StudentAnswer> getAnswers(){return new ArrayList<>(answers);} public void setAnswers(List<StudentAnswer>v){answers=v==null?new ArrayList<>():new ArrayList<>(v);} }