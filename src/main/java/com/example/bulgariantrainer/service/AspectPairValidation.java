package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AspectPairDtos.*;
import com.example.bulgariantrainer.exception.AspectPairException;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AspectPairValidation {
    public static final Set<String> RELATIONS=Set.of("core","resultative","bounded");
    public String namespace(String value) {
        if(value==null || !value.matches("[a-z0-9][a-z0-9._-]{0,99}"))
            throw new AspectPairException(400,"INVALID_NAMESPACE","Namespace must use 1–100 lowercase ASCII letters, digits, dots, underscores or hyphens");
        return value;
    }
    public List<ValidationError> errors(Entry e,String path) {
        List<ValidationError> errors=new ArrayList<>();
        if(e==null){error(errors,path,"Entry is required");return errors;}
        text(e.id(),100,path+".id",errors); text(e.imperfective(),255,path+".imperfective",errors);
        text(e.perfective(),255,path+".perfective",errors);
        text(e.russianImperfective(),1000,path+".russianImperfective",errors);
        text(e.russianPerfective(),1000,path+".russianPerfective",errors);text(e.topic(),255,path+".topic",errors);
        if(e.id()!=null && !e.id().equals(e.id().strip()))error(errors,path+".id","External ID must not have surrounding whitespace");
        if(e.studyStage()==null || e.studyStage()<1 || e.studyStage()>3)error(errors,path+".studyStage","Expected an integer from 1 to 3");
        if(e.relationType()==null || !RELATIONS.contains(e.relationType()))error(errors,path+".relationType","Expected core, resultative or bounded");
        if(e.note()==null || e.note().length()>4000)error(errors,path+".note","Note must be a string of at most 4000 characters; empty is allowed");
        answers(e.acceptedImperfective(),e.imperfective(),path+".acceptedImperfective",errors);
        answers(e.acceptedPerfective(),e.perfective(),path+".acceptedPerfective",errors);
        Provenance p=e.provenance();
        if(p==null)error(errors,path+".provenance","Provenance is required");
        else if("presentation".equals(p.kind())){
            text(p.sourceId(),100,path+".provenance.sourceId",errors);
            if(p.tableRow()==null || p.tableRow()<1)error(errors,path+".provenance.tableRow","Expected a positive table row");
        } else if(!"added".equals(p.kind()) || p.sourceId()!=null || p.tableRow()!=null)
            error(errors,path+".provenance","Expected presentation with sourceId/tableRow or added without source fields");
        if(e.examples()!=null){
            example(e.examples().imperfective(),path+".examples.imperfective",errors);
            example(e.examples().perfective(),path+".examples.perfective",errors);
        }
        return List.copyOf(errors);
    }
    public void requireValid(Entry entry) {
        var errors=errors(entry,"entry");
        if(!errors.isEmpty())throw new AspectPairException(400,"INVALID_ASPECT_PAIR",errors.getFirst().path()+": "+errors.getFirst().message());
    }
    private void answers(List<String> answers,String primary,String path,List<ValidationError> errors){
        if(answers==null || answers.isEmpty() || answers.size()>50){error(errors,path,"Expected 1–50 answers");return;}
        Set<String> keys=new HashSet<>();
        for(int i=0;i<answers.size();i++){
            String answer=answers.get(i);text(answer,255,path+"["+i+"]",errors);
            if(answer!=null){
                String key=AspectAnswerNormalizer.key(answer);
                if(key.length()>255)error(errors,path+"["+i+"]","Normalized answer exceeds 255 characters");
                if(!keys.add(key))error(errors,path+"["+i+"]","Duplicate normalized answer");
            }
        }
        if(primary!=null && !keys.contains(AspectAnswerNormalizer.key(primary)))error(errors,path,"Primary form must appear in its accepted answers");
    }
    private void example(Example e,String path,List<ValidationError> errors){
        if(e==null){error(errors,path,"Both aspect examples are required when examples are present");return;}
        text(e.bg(),4000,path+".bg",errors);text(e.ru(),4000,path+".ru",errors);
    }
    private void text(String value,int max,String path,List<ValidationError> errors){
        if(value==null || AspectAnswerNormalizer.key(value).isEmpty() || value.length()>max
                || value.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))
            error(errors,path,"Expected nonblank text of at most "+max+" characters");
    }
    static void error(List<ValidationError> errors,String path,String message){
        errors.add(new ValidationError(path,"INVALID_FIELD",message));
    }
}
