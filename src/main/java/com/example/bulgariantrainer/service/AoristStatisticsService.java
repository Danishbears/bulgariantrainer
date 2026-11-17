package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AoristStatisticsDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class AoristStatisticsService {
    private final AoristAttemptRepository attempts;
    private final AoristProgressRepository progress;
    private final AoristValidation validation;
    private final SpacedRepetitionScheduler scheduler;

    public AoristStatisticsService(AoristAttemptRepository attempts, AoristProgressRepository progress,
                                   AoristValidation validation, SpacedRepetitionScheduler scheduler) {
        this.attempts=attempts; this.progress=progress; this.validation=validation; this.scheduler=scheduler;
    }

    public Summary overview(String datasetId) {
        if(datasetId!=null) validation.datasetId(datasetId);
        var at=scheduler.now();
        var counts=attempts.countOutcomes(datasetId);
        var schedules=progress.findForDataset(datasetId);
        List<Exercise> exercises=new ArrayList<>();
        for(var type:AoristExerciseType.values()) {
            Set<Long> dueVerbs=new HashSet<>();
            Map<String,Long> due=new HashMap<>();
            for(var row:schedules) {
                if(row.getExerciseType()==type && !row.getVerb().isArchived() && scheduler.category(row,at)==LearningCategory.DUE) {
                    dueVerbs.add(row.getVerb().getId()); due.merge(row.getPerson(),1L,Long::sum);
                }
            }
            List<Person> persons=new ArrayList<>();
            for(String person:AoristValidation.PERSONS) {
                Map<AoristOutcome,Long> values=new EnumMap<>(AoristOutcome.class);
                counts.stream().filter(row->row.exerciseType()==type && row.person().equals(person))
                        .forEach(row->values.put(row.outcome(),row.count()));
                long correct=values.getOrDefault(AoristOutcome.CORRECT,0L), incorrect=values.getOrDefault(AoristOutcome.INCORRECT,0L);
                persons.add(new Person(person,correct+incorrect,correct,incorrect,values.getOrDefault(AoristOutcome.REVEALED,0L),due.getOrDefault(person,0L)));
            }
            exercises.add(new Exercise(type,persons.stream().mapToLong(Person::answered).sum(),persons.stream().mapToLong(Person::correct).sum(),
                    persons.stream().mapToLong(Person::incorrect).sum(),persons.stream().mapToLong(Person::revealed).sum(),
                    persons.stream().mapToLong(Person::dueForReview).sum(),dueVerbs.size(),List.copyOf(persons)));
        }
        return new Summary(at,List.copyOf(exercises));
    }
}
