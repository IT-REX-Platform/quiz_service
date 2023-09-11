package de.unistuttgart.iste.gits.quiz_service.matcher;

import de.unistuttgart.iste.gits.generated.dto.MultipleChoiceAnswerInput;
import de.unistuttgart.iste.gits.quiz_service.persistence.entity.MultipleChoiceAnswerEmbeddable;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static de.unistuttgart.iste.gits.quiz_service.matcher.ResourceMarkdownMatchers.markdownMatches;

/**
 * Matcher for comparing a {@link MultipleChoiceAnswerEmbeddable} to a {@link MultipleChoiceAnswerInput}.
 */
public class MultipleChoiceAnswerEntityToInputMatcher extends TypeSafeDiagnosingMatcher<MultipleChoiceAnswerEmbeddable> {

    private final MultipleChoiceAnswerInput expected;

    public MultipleChoiceAnswerEntityToInputMatcher(MultipleChoiceAnswerInput expected) {
        this.expected = expected;
    }

    public static MultipleChoiceAnswerEntityToInputMatcher matchesInput(MultipleChoiceAnswerInput expected) {
        return new MultipleChoiceAnswerEntityToInputMatcher(expected);
    }

    @Override
    protected boolean matchesSafely(MultipleChoiceAnswerEmbeddable item, Description mismatchDescription) {
        if (!markdownMatches(item.getAnswerText(), expected.getAnswerText())) {
            mismatchDescription.appendText("answer text was ").appendValue(item.getAnswerText());
            return false;
        }
        if (item.isCorrect() != expected.getCorrect()) {
            mismatchDescription.appendText("correct was ").appendValue(item.isCorrect());
            return false;
        }
        if (!markdownMatches(item.getFeedback(), expected.getFeedback())) {
            mismatchDescription.appendText("feedback was ").appendValue(item.getFeedback());
            return false;
        }

        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("matches MultipleChoiceAnswerInput");
        description.appendValue(expected);
    }
}
