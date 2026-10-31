package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AspectStatisticsDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class AspectStatisticsService {
    private final AspectAttemptRepository attempts;
    private final AspectProgressRepository progress;
    private final AspectPairValidation validation;
    private final SpacedRepetitionScheduler scheduler;

    public AspectStatisticsService(AspectAttemptRepository attempts, AspectProgressRepository progress,
                                   AspectPairValidation validation, SpacedRepetitionScheduler scheduler) {
        this.attempts=attempts; this.progress=progress; this.validation=validation; this.scheduler=scheduler;
    }

    public Summary overview(String namespace) {
        if(namespace!=null) validation.namespace(namespace);
        Instant at=scheduler.now();
        Map<AspectDirection,EnumMap<AspectOutcome,Long>> counts=new EnumMap<>(AspectDirection.class);
        for(var row:attempts.countOutcomes(namespace))
            counts.computeIfAbsent(row.direction(),ignored->new EnumMap<>(AspectOutcome.class)).put(row.outcome(),row.count());
        Set<Long> duePairs=new HashSet<>();
        Map<AspectDirection,Long> dueDirections=new EnumMap<>(AspectDirection.class);
        for(AspectProgress row:progress.findForNamespace(namespace)) {
            if(!row.getPair().isArchived() && scheduler.category(row,at)==LearningCategory.DUE) {
                duePairs.add(row.getPair().getId());
                dueDirections.merge(row.getDirection(),1L,Long::sum);
            }
        }
        List<Direction> directions=new ArrayList<>();
        for(AspectDirection direction:List.of(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,AspectDirection.PERFECTIVE_TO_IMPERFECTIVE)) {
            Map<AspectOutcome,Long> values=counts.getOrDefault(direction,new EnumMap<>(AspectOutcome.class));
            long correct=values.getOrDefault(AspectOutcome.CORRECT,0L);
            long incorrect=values.getOrDefault(AspectOutcome.INCORRECT,0L);
            directions.add(new Direction(direction,correct+incorrect,correct,incorrect,
                    values.getOrDefault(AspectOutcome.REVEALED,0L),dueDirections.getOrDefault(direction,0L)));
        }
        return new Summary(directions.stream().mapToLong(Direction::answered).sum(),
                directions.stream().mapToLong(Direction::correct).sum(),
                directions.stream().mapToLong(Direction::incorrect).sum(),
                directions.stream().mapToLong(Direction::revealed).sum(),duePairs.size(),
                directions.stream().mapToLong(Direction::dueForReview).sum(),at,List.copyOf(directions));
    }
}
