package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.entity.AspectDirection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.random.RandomGenerator;

@Component
public class AspectQuestionPlanner {
    /** Null priority means that direction is ineligible in the selected queue. */
    public record Candidate(long pairId,Integer forwardPriority,Integer reversePriority) {}
    public record Planned(long pairId,AspectDirection direction) {}
    private final RandomGenerator random;
    @org.springframework.beans.factory.annotation.Autowired
    public AspectQuestionPlanner(@Value("${training.aspect-pairs.random-seed:}") String seed){
        this(seed.isBlank()?new Random():new Random(Long.parseLong(seed)));
    }
    public AspectQuestionPlanner(RandomGenerator random){this.random=random;}

    public synchronized List<Planned> plan(List<Candidate> candidates,int requested,AspectDirection direction){
        List<Candidate> shuffled=shuffle(candidates);
        int size=Math.min(requested,shuffled.size());
        if(direction!=AspectDirection.MIXED){
            boolean forward=direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE;
            return shuffle(shuffled.stream().filter(c->(forward?c.forwardPriority():c.reversePriority())!=null)
                    .sorted(Comparator.comparingInt(c->forward?c.forwardPriority():c.reversePriority()))
                    .limit(size).map(c->new Planned(c.pairId(),direction)).toList());
        }
        int forwardEligible=(int)shuffled.stream().filter(c->c.forwardPriority()!=null).count();
        int reverseEligible=(int)shuffled.stream().filter(c->c.reversePriority()!=null).count();
        int lower=Math.max(0,size-reverseEligible),upper=Math.min(size,forwardEligible);
        int floor=Math.clamp(size/2,lower,upper),ceil=Math.clamp((size+1)/2,lower,upper);
        int maxForward=Math.max(floor,ceil),maxReverse=size-Math.min(floor,ceil);
        int infinity=Integer.MAX_VALUE/2;
        int[][] cost=new int[maxForward+1][maxReverse+1];
        Path[][] paths=new Path[maxForward+1][maxReverse+1];
        for(int[] row:cost)Arrays.fill(row,infinity);
        cost[0][0]=0;
        // Bounded assignment: each pair can contribute one direction or be skipped.
        // Descending counts prevent using the same pair twice. Among maximally
        // balanced plans, minimize the total scheduler priority of actual reviews.
        for(Candidate c:shuffled)for(int f=maxForward;f>=0;f--)for(int r=maxReverse;r>=0;r--){
            if(f>0&&c.forwardPriority()!=null&&cost[f-1][r]+c.forwardPriority()<cost[f][r]){
                cost[f][r]=cost[f-1][r]+c.forwardPriority();
                paths[f][r]=new Path(paths[f-1][r],new Planned(c.pairId(),AspectDirection.IMPERFECTIVE_TO_PERFECTIVE));
            }
            if(r>0&&c.reversePriority()!=null&&cost[f][r-1]+c.reversePriority()<cost[f][r]){
                cost[f][r]=cost[f][r-1]+c.reversePriority();
                paths[f][r]=new Path(paths[f][r-1],new Planned(c.pairId(),AspectDirection.PERFECTIVE_TO_IMPERFECTIVE));
            }
        }
        int floorCost=cost[floor][size-floor],ceilCost=cost[ceil][size-ceil];
        int forward=floorCost<ceilCost?floor:ceilCost<floorCost?ceil:random.nextBoolean()?floor:ceil;
        List<Planned> result=new ArrayList<>();
        for(Path path=paths[forward][size-forward];path!=null;path=path.previous())result.add(path.question());
        return shuffle(result);
    }
    private record Path(Path previous,Planned question) {}
    private <T> List<T> shuffle(List<T> source){
        List<T> copy=new ArrayList<>(source);
        for(int i=copy.size()-1;i>0;i--)Collections.swap(copy,i,random.nextInt(i+1));
        return copy;
    }
}
