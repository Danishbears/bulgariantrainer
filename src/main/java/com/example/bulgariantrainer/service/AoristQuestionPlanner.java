package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AoristDtos.Entry;
import com.example.bulgariantrainer.dto.AoristPracticeDtos.Option;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.random.RandomGenerator;

@Component
public class AoristQuestionPlanner {
    public record Candidate(long verbId,String person,int priority,Entry content) {}
    private final RandomGenerator random;
    @org.springframework.beans.factory.annotation.Autowired
    public AoristQuestionPlanner(@Value("${training.aorist.random-seed:}") String seed) {
        this(seed.isBlank()?new Random():new Random(Long.parseLong(seed)));
    }
    public AoristQuestionPlanner(RandomGenerator random) { this.random=random; }
    public synchronized List<Candidate> plan(List<Candidate> source,int size) {
        return shuffle(shuffle(source).stream().sorted(Comparator.comparingInt(Candidate::priority)).limit(size).toList());
    }
    public int optionCount(Entry entry,String person) { return Math.min(4,1+distractors(entry,person).size()); }
    public synchronized List<Option> choices(Entry entry,String person) {
        var distractors=shuffle(distractors(entry,person));
        if(distractors.isEmpty()) throw new IllegalArgumentException("At least one distinct distractor is required");
        List<Option> options=new ArrayList<>();
        options.add(new Option(UUID.randomUUID(),entry.forms().get(person).getFirst()));
        for(String text:distractors.stream().limit(3).toList()) options.add(new Option(UUID.randomUUID(),text));
        return shuffle(options);
    }
    private List<String> distractors(Entry entry,String person) {
        Set<String> accepted=new HashSet<>();
        for(String text:entry.forms().get(person)) accepted.add(AoristAnswerNormalizer.key(text));
        Map<String,String> unique=new LinkedHashMap<>();
        for(String other:AoristValidation.PERSONS) if(!other.equals(person)) for(String text:entry.forms().get(other)) {
            String key=AoristAnswerNormalizer.key(text);
            if(!accepted.contains(key)) unique.putIfAbsent(key,text);
        }
        return List.copyOf(unique.values());
    }
    private <T> List<T> shuffle(List<T> source) {
        List<T> result=new ArrayList<>(source);
        for(int i=result.size()-1;i>0;i--) Collections.swap(result,i,random.nextInt(i+1));
        return result;
    }
}
