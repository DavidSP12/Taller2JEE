package com.taller2jee.logic.grpc;

import com.taller2jee.common.model.EvaluationAnswer;
import com.taller2jee.common.model.EvaluationSubmission;
import com.taller2jee.logic.saga.DistributedTransactionError;
import com.taller2jee.logic.saga.EvaluationSagaOrchestrator;
import com.taller2jee.proto.Answer;
import com.taller2jee.proto.EvaluationServiceGrpc;
import com.taller2jee.proto.SubmitEvaluationRequest;
import com.taller2jee.proto.SubmitEvaluationResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.List;

public class EvaluationServiceImpl extends EvaluationServiceGrpc.EvaluationServiceImplBase {

    private final EvaluationSagaOrchestrator orchestrator;

    public EvaluationServiceImpl(EvaluationSagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void submitEvaluation(SubmitEvaluationRequest request, StreamObserver<SubmitEvaluationResponse> responseObserver) {
        List<EvaluationAnswer> answers = request.getAnswersList().stream()
                .map(this::toModel)
                .toList();

        EvaluationSubmission submission = new EvaluationSubmission(
                request.getEvaluationId(),
                request.getStudentId(),
                request.getStudentName(),
                request.getStudentEmail(),
                answers
        );

        try {
            var result = orchestrator.process(submission);
            SubmitEvaluationResponse response = SubmitEvaluationResponse.newBuilder()
                    .setEvaluationId(result.evaluationId())
                    .setStudentId(result.studentId())
                    .setScore(result.score())
                    .setStatus(result.status())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (DistributedTransactionError e) {
            responseObserver.onError(Status.ABORTED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
        }
    }

    private EvaluationAnswer toModel(Answer answer) {
        return new EvaluationAnswer(answer.getQuestionId(), answer.getAnswer());
    }
}
