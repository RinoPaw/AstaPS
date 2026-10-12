package emu.grasscutter.game.quest;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Isolates failures within each candidate for a quest content event.
 * One malformed subquest must not starve unrelated subquests triggered by the
 * same event. The caller reports each failure with its main/subquest IDs.
 */
final class QuestContentDispatch {
    private QuestContentDispatch() {}

    static <T> void forEachCandidate(
            Iterable<T> candidates, Consumer<T> action, BiConsumer<T, RuntimeException> onError) {
        for (T candidate : candidates) {
            try {
                action.accept(candidate);
            } catch (RuntimeException exception) {
                onError.accept(candidate, exception);
            }
        }
    }
}
