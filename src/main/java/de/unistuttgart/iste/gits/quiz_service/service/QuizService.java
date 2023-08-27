package de.unistuttgart.iste.gits.quiz_service.service;

import de.unistuttgart.iste.gits.common.event.*;
import de.unistuttgart.iste.gits.generated.dto.*;
import de.unistuttgart.iste.gits.quiz_service.dapr.TopicPublisher;
import de.unistuttgart.iste.gits.quiz_service.persistence.dao.*;
import de.unistuttgart.iste.gits.quiz_service.persistence.mapper.QuizMapper;
import de.unistuttgart.iste.gits.quiz_service.persistence.repository.QuizRepository;
import de.unistuttgart.iste.gits.quiz_service.validation.QuizValidator;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizMapper quizMapper;
    private final QuizValidator quizValidator;
    private final TopicPublisher topicPublisher;

    /**
     * Returns all quizzes for the given assessment ids.
     * If an assessment id does not exist, the corresponding quiz is null.
     *
     * @param assessmentIds the assessment ids
     * @return the quizzes
     */
    public List<Quiz> findQuizzesByAssessmentIds(List<UUID> assessmentIds) {
        return assessmentIds.stream()
                .map(quizRepository::findById)
                .map(optionalQuiz -> optionalQuiz.map(this::entityToDto))
                .map(optionalQuiz -> optionalQuiz.orElse(null))
                .toList();
    }

    public List<Quiz> getAllQuizzes() {
        return quizRepository.findAll()
                .stream()
                .map(this::entityToDto)
                .toList();
    }


    /**
     * Creates a new quiz.
     *
     * @param input the quiz to create
     * @return the created quiz
     * @throws ValidationException if the question numbers are not unique or any question does not validate
     */
    public Quiz createQuiz(UUID assessmentId, CreateQuizInput input) {
        quizValidator.validateCreateQuizInput(input);

        QuizEntity entity = quizMapper.createQuizInputToEntity(input);
        entity.setAssessmentId(assessmentId);

        QuizEntity savedEntity = quizRepository.save(entity);
        return entityToDto(savedEntity);
    }

    /**
     * Deletes a quiz.
     *
     * @param id the id of the quiz to delete
     * @return the id of the deleted quiz
     * @throws EntityNotFoundException if the quiz does not exist
     */
    public UUID deleteQuiz(UUID id) {
        requireQuizExists(id);

        quizRepository.deleteById(id);

        return id;
    }

    /**
     * Adds a multiple choice question to a quiz.
     *
     * @param quizId the id of the quiz
     * @param input  the question to add
     * @return the modified quiz
     * @throws EntityNotFoundException if the quiz does not exist
     * @throws ValidationException     if the question number is not unique or if there is no correct answer
     */
    public Quiz addMultipleChoiceQuestion(UUID quizId, CreateMultipleChoiceQuestionInput input) {
        quizValidator.validateCreateMultipleChoiceQuestionInput(input);

        return modifyQuiz(quizId, entity -> {
            int number = input.getNumber() == null ? assignNumber(entity) : input.getNumber();
            checkNumberIsUnique(entity, number);

            QuestionEntity questionEntity = quizMapper.multipleChoiceQuestionInputToEntity(input);
            questionEntity.setNumber(number);
            entity.getQuestionPool().add(questionEntity);
        });
    }

    /**
     * Updates a multiple choice question in a quiz.
     *
     * @param quizId the id of the quiz
     * @param input  the updated question
     * @return the modified quiz
     * @throws EntityNotFoundException if the quiz or the question does not exist
     * @throws ValidationException     if there is no correct answer
     */
    public Quiz updateMultipleChoiceQuestion(UUID quizId, UpdateMultipleChoiceQuestionInput input) {
        quizValidator.validateUpdateMultipleChoiceQuestionInput(input);

        return modifyQuiz(quizId, entity -> {
            QuestionEntity questionEntity = getQuestionInQuizById(entity, input.getId());

            int indexOfQuestion = entity.getQuestionPool().indexOf(questionEntity);
            entity.getQuestionPool().set(indexOfQuestion, quizMapper.multipleChoiceQuestionInputToEntity(input));
        });
    }

    /**
     * Adds a cloze question to a quiz.
     *
     * @param quizId the id of the quiz
     * @param input  the question to add
     * @return the modified quiz
     * @throws EntityNotFoundException if the quiz does not exist
     * @throws ValidationException     if input is invalid, see
     *                                 {@link QuizValidator#validateCreateClozeQuestionInput(CreateClozeQuestionInput)}
     */
    public Quiz addClozeQuestion(UUID quizId, CreateClozeQuestionInput input) {
        quizValidator.validateCreateClozeQuestionInput(input);

        return modifyQuiz(quizId, entity -> {
            int number = input.getNumber() == null ? assignNumber(entity) : input.getNumber();
            checkNumberIsUnique(entity, number);

            QuestionEntity questionEntity = quizMapper.clozeQuestionInputToEntity(input);
            questionEntity.setNumber(number);
            entity.getQuestionPool().add(questionEntity);
        });
    }

    /**
     * Updates a cloze question in a quiz.
     *
     * @param quizId the id of the quiz
     * @param input  the updated question
     * @return the modified quiz
     * @throws EntityNotFoundException if the quiz or the question does not exist
     * @throws ValidationException     if input is invalid, see
     */
    public Quiz updateClozeQuestion(UUID quizId, UpdateClozeQuestionInput input) {
        quizValidator.validateUpdateClozeQuestionInput(input);

        return modifyQuiz(quizId, entity -> {
            QuestionEntity questionEntity = getQuestionInQuizById(entity, input.getId());

            int indexOfQuestion = entity.getQuestionPool().indexOf(questionEntity);
            entity.getQuestionPool().set(indexOfQuestion, quizMapper.clozeQuestionInputToEntity(input));
        });
    }

    /**
     * Removes a question from a quiz.
     *
     * @param quizId the id of the quiz
     * @param number the number of the question to remove
     * @return the modified quiz
     * @throws EntityNotFoundException if the quiz does not exist
     */
    public Quiz removeQuestion(UUID quizId, int number) {
        return modifyQuiz(quizId, entity -> {
            QuestionEntity questionEntity = getQuestionInQuizByNumber(entity, number);
            entity.getQuestionPool().remove(questionEntity);

            // decrease the number of all questions with a higher number
            entity.getQuestionPool().stream()
                    .filter(q -> q.getNumber() > number)
                    .forEach(q -> q.setNumber(q.getNumber() - 1));
        });
    }

    /**
     * Switches the position of two questions in a quiz.
     *
     * @param quizId       the id of the quiz
     * @param firstNumber  the number of the first question
     * @param secondNumber the number of the second question
     * @return the modified quiz
     * @throws EntityNotFoundException if the quiz does not exist
     */
    public Quiz switchQuestions(UUID quizId, int firstNumber, int secondNumber) {
        return modifyQuiz(quizId, entity -> {
            QuestionEntity firstQuestionEntity = getQuestionInQuizByNumber(entity, firstNumber);
            QuestionEntity secondQuestionEntity = getQuestionInQuizByNumber(entity, secondNumber);

            firstQuestionEntity.setNumber(secondNumber);
            secondQuestionEntity.setNumber(firstNumber);

            int indexOfFirstQuestion = entity.getQuestionPool().indexOf(firstQuestionEntity);
            int indexOfSecondQuestion = entity.getQuestionPool().indexOf(secondQuestionEntity);
            Collections.swap(entity.getQuestionPool(), indexOfFirstQuestion, indexOfSecondQuestion);
        });
    }

    public Quiz setRequiredCorrectAnswers(UUID quizId, int requiredCorrectAnswers) {
        return modifyQuiz(quizId, entity -> entity.setRequiredCorrectAnswers(requiredCorrectAnswers));
    }

    public Quiz setQuestionPoolingMode(UUID quizId, QuestionPoolingMode questionPoolingMode) {
        return modifyQuiz(quizId, entity -> entity.setQuestionPoolingMode(questionPoolingMode));
    }

    public Quiz setNumberOfRandomlySelectedQuestions(UUID quizId, int numberOfRandomlySelectedQuestions) {
        return modifyQuiz(quizId, entity -> entity.setNumberOfRandomlySelectedQuestions(numberOfRandomlySelectedQuestions));
    }

    public QuizEntity requireQuizExists(UUID id) {
        return quizRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Quiz with id " + id + " not found"));
    }

    /**
     * Modifies a quiz by applying the given modifier to the quiz entity
     * and saves the modified entity to the database.
     *
     * @param quiz     the quiz to modify
     * @param modifier the modifier to apply to the quiz entity
     * @return the modified quiz as DTO
     * @throws EntityNotFoundException if the quiz does not exist
     */
    private Quiz modifyQuiz(UUID quiz, Consumer<QuizEntity> modifier) {
        QuizEntity entity = requireQuizExists(quiz);

        modifier.accept(entity);

        QuizEntity savedEntity = quizRepository.save(entity);
        return entityToDto(savedEntity);
    }

    private void checkNumberIsUnique(QuizEntity quizEntity, int questionNumber) {
        if (quizEntity.getQuestionPool().stream().anyMatch(q -> q.getNumber() == questionNumber)) {
            throw new ValidationException("Question number must be unique, but the number "
                                          + questionNumber
                                          + " is already used.");
        }
    }

    private int assignNumber(QuizEntity entity) {
        if (entity.getQuestionPool().isEmpty()) {
            return 1;
        }
        return entity.getQuestionPool().get(entity.getQuestionPool().size() - 1).getNumber() + 1;
    }

    private Quiz entityToDto(QuizEntity entity) {
        Quiz dto = quizMapper.entityToDto(entity);
        return selectQuestions(dto);
    }

    /**
     * Selects the questions for a quiz based on the question pooling mode.
     * <p>
     * If the question pooling mode is {@link QuestionPoolingMode#ORDERED}, all questions are selected
     * and the order is preserved.
     * If the question pooling mode is {@link QuestionPoolingMode#RANDOM}, the questions are shuffled
     * and the number of questions is limited to {@link Quiz#getNumberOfRandomlySelectedQuestions()}.
     *
     * @param quiz the quiz
     * @return the quiz with the selected questions
     */
    private Quiz selectQuestions(Quiz quiz) {
        if (quiz.getQuestionPoolingMode() == QuestionPoolingMode.ORDERED) {
            quiz.setSelectedQuestions(quiz.getQuestionPool());
            return quiz;
        }

        int limit = quiz.getQuestionPool().size();
        if (quiz.getNumberOfRandomlySelectedQuestions() != null) {
            limit = Math.min(limit, quiz.getNumberOfRandomlySelectedQuestions());
        }

        List<Question> pool = new ArrayList<>(quiz.getQuestionPool());
        Collections.shuffle(pool);
        quiz.setSelectedQuestions(pool.subList(0, limit));

        return quiz;
    }

    private QuestionEntity getQuestionInQuizById(QuizEntity quizEntity, UUID questionId) {
        return quizEntity.getQuestionPool().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() ->
                        new EntityNotFoundException(MessageFormat.format(
                                "Question with id {0} not found in quiz with id {1}.",
                                questionId, quizEntity.getAssessmentId())));
    }

    private QuestionEntity getQuestionInQuizByNumber(QuizEntity quizEntity, int number) {
        return quizEntity.getQuestionPool().stream()
                .filter(q -> q.getNumber() == number)
                .findFirst()
                .orElseThrow(() ->
                        new EntityNotFoundException(MessageFormat.format(
                                "Question with number {0} not found in quiz with id {1}.",
                                number, quizEntity.getAssessmentId())));
    }

    /**
     * removes all Quizzes when linked Content gets deleted
     *
     * @param dto event object containing changes to content
     */
    public void removeContentIds(ContentChangeEvent dto) {
        // validate event message
        try {
            checkCompletenessOfDto(dto);
        } catch (NullPointerException e) {
            log.error(e.getMessage());
            return;
        }
        // only consider DELETE Operations
        if (!dto.getOperation().equals(CrudOperation.DELETE) || dto.getContentIds().isEmpty()) {
            return;
        }

        // find all quizzes
        List<QuizEntity> quizEntities = quizRepository.findAllById(dto.getContentIds());

        // delete all found quizzes
        quizRepository.deleteAllInBatch(quizEntities);

    }

    /**
     * helper function to make sure received event message is complete
     *
     * @param dto event message under evaluation
     * @throws NullPointerException if any of the fields are null
     */
    private void checkCompletenessOfDto(ContentChangeEvent dto) throws NullPointerException {
        if (dto.getOperation() == null || dto.getContentIds() == null) {
            throw new NullPointerException("incomplete message received: all fields of a message must be non-null");
        }
    }


    /**
     * publishes user progress on a quiz to dapr topic
     *
     * @param input  user progress made
     * @param userId the user that made progress
     * @return quiz that was worked on
     */
    public Quiz publishProgress(QuizCompletedInput input, UUID userId) {
        QuizEntity quizEntity = quizRepository.getReferenceById(input.getQuizId());

        updateQuestionStatistics(input, userId, quizEntity);

        // count the number of questions that were answered correctly
        long numbCorrectAnswers = input.getCompletedQuestions().stream().filter(QuestionCompletedInput::getCorrect).count();

        boolean success = numbCorrectAnswers >= quizEntity.getRequiredCorrectAnswers();
        double correctness = calcCorrectness(numbCorrectAnswers, quizEntity);

        // create new user progress event message
        UserProgressLogEvent userProgressLogEvent = UserProgressLogEvent.builder()
                .userId(userId)
                .contentId(quizEntity.getAssessmentId())
                .hintsUsed((int) input.getCompletedQuestions().stream().filter(QuestionCompletedInput::getUsedHint).count())
                .success(success)
                .timeToComplete(null)
                .correctness(correctness)
                .build();

        // publish new user progress event message
        topicPublisher.notifyUserWorkedOnContent(userProgressLogEvent);
        return quizMapper.entityToDto(quizEntity);
    }

    /**
     * Method that updates Statistics for a question. Any changes are discarded if any of the received question IDs do not exist.
     *
     * @param input      Object containing the IDs of the answered questions and their correctness
     * @param userId     ID of user completing the quiz
     * @param quizEntity quiz, questions are a part of
     */
    private void updateQuestionStatistics(QuizCompletedInput input, UUID userId, QuizEntity quizEntity) {

        for (QuestionCompletedInput completedQuestion : input.getCompletedQuestions()) {

            // find question in quiz. throws an exception if question can not be found
            QuestionEntity questionEntity = getQuestionInQuizById(quizEntity, completedQuestion.getQuestionId());
            int index = quizEntity.getQuestionPool().indexOf(questionEntity);

            // create new Question Statistic
            QuestionStatisticEntity newQuestionStatistic = QuestionStatisticEntity.builder()
                    .questionId(questionEntity.getId())
                    .userId(userId)
                    .answeredCorrectly(completedQuestion.getCorrect())
                    .build();

            questionEntity.getQuestionStatistics().add(newQuestionStatistic);

            //overwrite outdated question
            quizEntity.getQuestionPool().set(index, questionEntity);
        }

        //persist changes
        quizRepository.save(quizEntity);

    }

    /**
     * Calculates the correctness value for a quiz
     *
     * @param correctAnswers number of correct answers
     * @param quizEntity     quizEntity source
     * @return calculated correctness value
     */
    protected double calcCorrectness(double correctAnswers, QuizEntity quizEntity) {
        if (correctAnswers == 0.0) {
            return correctAnswers;
        }

        if (quizEntity.getQuestionPoolingMode().equals(QuestionPoolingMode.RANDOM) && quizEntity.getNumberOfRandomlySelectedQuestions() != null) {

            if (quizEntity.getNumberOfRandomlySelectedQuestions() == 0) {
                return 1.0;
            }
            return correctAnswers / quizEntity.getNumberOfRandomlySelectedQuestions();

        } else if (quizEntity.getQuestionPool().isEmpty()) {
            return 1.0;
        } else {
            return correctAnswers / quizEntity.getQuestionPool().size();
        }

    }
}
