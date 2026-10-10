package com.example.bulgariantrainer.service;
import com.example.bulgariantrainer.entity.TranslationDirection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.random.RandomGenerator;
/** A seed is optional in production and configurable for reproducible tests. */
@Component
public class TypingQuestionPlanner {
    private final RandomGenerator random;
    @org.springframework.beans.factory.annotation.Autowired
    public TypingQuestionPlanner(@Value("${training.typing.random-seed:}") String seed) {
        this(seed.isBlank() ? new Random() : new Random(Long.parseLong(seed)));
    }
    public TypingQuestionPlanner(RandomGenerator random) { this.random = random; }
    public synchronized <T> List<T> shuffled(List<T> source) {
        List<T> values = new ArrayList<>(source);
        for (int i = values.size() - 1; i > 0; i--) Collections.swap(values, i, random.nextInt(i + 1));
        return values;
    }
    public synchronized List<TranslationDirection> directions(int size) {
        int bulgarian = size / 2 + (size % 2 == 1 && random.nextBoolean() ? 1 : 0);
        List<TranslationDirection> result = new ArrayList<>();
        for (int i = 0; i < size; i++) result.add(i < bulgarian ? TranslationDirection.BG_TO_RU : TranslationDirection.RU_TO_BG);
        return shuffled(result);
    }
}
