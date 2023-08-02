package de.unistuttgart.iste.gits.quiz_service.controller;

import de.unistuttgart.iste.gits.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.gits.generated.dto.*;
import de.unistuttgart.iste.gits.quiz_service.service.QuizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @QueryMapping
    public List<Quiz> findQuizzesByAssessmentIds(@Argument List<UUID> assessmentIds) {
        return quizService.findQuizzesByAssessmentIds(assessmentIds);
    }

    @QueryMapping
    public List<Quiz> quizzes() {
        return quizService.getAllQuizzes();
    }

    @MutationMapping
    public Quiz createQuiz(@Argument UUID assessmentId, @Argument CreateQuizInput input) {
        return quizService.createQuiz(assessmentId, input);
    }

    @MutationMapping
    public QuizMutation mutateQuiz(@Argument UUID assessmentId) {
        // this is basically an empty object, only serving as a parent for the nested mutations
        return new QuizMutation(assessmentId);
    }

    @MutationMapping
    public UUID deleteQuiz(@Argument UUID assessmentId) {
        return quizService.deleteQuiz(assessmentId);
    }

    @SchemaMapping(typeName = "QuizMutation")
    public Quiz addMultipleChoiceQuestion(@Argument CreateMultipleChoiceQuestionInput input, QuizMutation quizMutation) {
        return quizService.addMultipleChoiceQuestion(quizMutation.getAssessmentId(), input);
    }

    @SchemaMapping(typeName = "QuizMutation")
    public Quiz updateMultipleChoiceQuestion(@Argument UpdateMultipleChoiceQuestionInput input, QuizMutation quizMutation) {
        return quizService.updateMultipleChoiceQuestion(quizMutation.getAssessmentId(), input);
    }

    @SchemaMapping(typeName = "QuizMutation")
    public Quiz removeQuestion(@Argument int number, QuizMutation quizMutation) {
        return quizService.removeQuestion(quizMutation.getAssessmentId(), number);
    }

    @SchemaMapping(typeName = "QuizMutation")
    public Quiz switchQuestions(@Argument int firstNumber, @Argument int secondNumber, QuizMutation quizMutation) {
        return quizService.switchQuestions(quizMutation.getAssessmentId(), firstNumber, secondNumber);
    }

    @SchemaMapping(typeName = "QuizMutation")
    public Quiz setRequiredCorrectAnswers(@Argument int requiredCorrectAnswers, QuizMutation quizMutation) {
        return quizService.setRequiredCorrectAnswers(quizMutation.getAssessmentId(), requiredCorrectAnswers);
    }

    @SchemaMapping(typeName = "QuizMutation")
    public Quiz setQuestionPoolingMode(@Argument QuestionPoolingMode questionPoolingMode, QuizMutation quizMutation) {
        return quizService.setQuestionPoolingMode(quizMutation.getAssessmentId(), questionPoolingMode);
    }

    @SchemaMapping(typeName = "QuizMutation")
    public Quiz setNumberOfRandomlySelectedQuestions(@Argument int numberOfRandomlySelectedQuestions, QuizMutation quizMutation) {
        return quizService.setNumberOfRandomlySelectedQuestions(quizMutation.getAssessmentId(), numberOfRandomlySelectedQuestions);
    }

    @MutationMapping
    public Quiz logQuizCompleted(@Argument QuizCompletedInput input, @ContextValue LoggedInUser currentUser) {
        return quizService.publishProgress(input, currentUser.getId());
    }

}
