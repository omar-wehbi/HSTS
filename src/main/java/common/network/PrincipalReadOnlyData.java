package common.network;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** Read-only principal snapshot of questions, exams, releases, sessions and final results. */
public class PrincipalReadOnlyData implements Serializable { private static final long serialVersionUID=1L; private List<Map<String,Serializable>> questions,exams,releases,sessions,results;
 public PrincipalReadOnlyData(){this(List.of(),List.of(),List.of(),List.of(),List.of());} public PrincipalReadOnlyData(List<Map<String,Serializable>>q, List<Map<String,Serializable>>e, List<Map<String,Serializable>>r, List<Map<String,Serializable>>s, List<Map<String,Serializable>>g){questions=q;exams=e;releases=r;sessions=s;results=g;}
 public List<Map<String,Serializable>> getQuestions(){return questions;} public List<Map<String,Serializable>> getExams(){return exams;} public List<Map<String,Serializable>> getReleases(){return releases;} public List<Map<String,Serializable>> getSessions(){return sessions;} public List<Map<String,Serializable>> getResults(){return results;}
}