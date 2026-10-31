package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.entity.AspectDirection;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AspectQuestionPlannerTests {
    @Test void mixedDirectionBalanceIsOptimalForAllSmallEligibilityPools(){
        var planner=new AspectQuestionPlanner(new Random(42));
        for(int forwardOnly=0;forwardOnly<=4;forwardOnly++)for(int reverseOnly=0;reverseOnly<=4;reverseOnly++)for(int both=0;both<=4;both++){
            List<AspectQuestionPlanner.Candidate> candidates=new ArrayList<>();long id=1;
            for(int i=0;i<forwardOnly;i++)candidates.add(new AspectQuestionPlanner.Candidate(id++,0,null));
            for(int i=0;i<reverseOnly;i++)candidates.add(new AspectQuestionPlanner.Candidate(id++,null,0));
            for(int i=0;i<both;i++)candidates.add(new AspectQuestionPlanner.Candidate(id++,0,0));
            for(int requested=1;requested<=15;requested++){
                var plan=planner.plan(candidates,requested,AspectDirection.MIXED);
                int size=Math.min(requested,candidates.size());
                assertThat(plan).hasSize(size);assertThat(plan.stream().map(AspectQuestionPlanner.Planned::pairId)).doesNotHaveDuplicates();
                int forward=(int)plan.stream().filter(p->p.direction()==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE).count();
                int best=size;
                for(int n=0;n<=size;n++)if(n<=forwardOnly+both && size-n<=reverseOnly+both)
                    best=Math.min(best,Math.abs(size-2*n));
                assertThat(Math.abs(size-2*forward)).isEqualTo(best);
                for(var p:plan){
                    var candidate=candidates.stream().filter(c->c.pairId()==p.pairId()).findFirst().orElseThrow();
                    assertThat(p.direction()==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE?candidate.forwardPriority():candidate.reversePriority()).isNotNull();
                }
            }
        }
    }
    @Test void mixedQueueChoosesDueDirectionsBeforeNewDirectionsWhenBothAreEligible(){
        var plan=new AspectQuestionPlanner(new Random(42)).plan(List.of(
                new AspectQuestionPlanner.Candidate(1,0,1),new AspectQuestionPlanner.Candidate(2,1,0),
                new AspectQuestionPlanner.Candidate(3,2,2),new AspectQuestionPlanner.Candidate(4,3,3)),2,AspectDirection.MIXED);
        assertThat(plan).containsExactlyInAnyOrder(
                new AspectQuestionPlanner.Planned(1,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE),
                new AspectQuestionPlanner.Planned(2,AspectDirection.PERFECTIVE_TO_IMPERFECTIVE));
    }
    @Test void oddSplitAndPairSelectionPreferDueDirectionsWithoutSacrificingBalance(){
        var planner=new AspectQuestionPlanner(new Random(42));
        for(int i=0;i<20;i++)assertThat(planner.plan(List.of(new AspectQuestionPlanner.Candidate(1,0,1)),1,AspectDirection.MIXED))
                .containsExactly(new AspectQuestionPlanner.Planned(1,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE));
        for(int i=0;i<20;i++){
            var plan=planner.plan(List.of(new AspectQuestionPlanner.Candidate(1,0,1),
                    new AspectQuestionPlanner.Candidate(2,0,1),new AspectQuestionPlanner.Candidate(3,1,0)),2,AspectDirection.MIXED);
            assertThat(plan).contains(new AspectQuestionPlanner.Planned(3,AspectDirection.PERFECTIVE_TO_IMPERFECTIVE));
            assertThat(plan.stream().filter(p->p.direction()==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE).count()).isEqualTo(1);
        }
    }
}
