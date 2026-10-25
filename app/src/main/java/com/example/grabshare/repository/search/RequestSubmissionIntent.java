package com.example.grabshare.repository.search;

import com.example.grabshare.contract.RequestAttemptContract;

import java.util.Objects;

/** Một thao tác gửi có ID cố định và mốc lịch sử; retry không tự biến thành lần gửi khác. */
public final class RequestSubmissionIntent {
    public final String requestIdentifier;
    public final String tripIdentifier;
    public final String passengerIdentifier;
    public final String previousRequestIdentifier;
    public final Long previousAttemptNumber;

    /** Phục hồi giá trị đã lưu; không tạo ID mới khi xoay hoặc khởi động lại tiến trình. */
    public RequestSubmissionIntent(
            String requestIdentifier,
            String tripIdentifier,
            String passengerIdentifier,
            String previousRequestIdentifier,
            Long previousAttemptNumber) {
        if (!SearchTripPolicy.validId(tripIdentifier)
                || !SearchTripPolicy.validId(passengerIdentifier)
                || requestIdentifier == null
                || !requestIdentifier.matches(
                        "[0-9a-f]{32}_" + java.util.regex.Pattern.quote(passengerIdentifier))
                || (previousRequestIdentifier == null && previousAttemptNumber != null)
                || (previousRequestIdentifier != null
                        && !SearchTripPolicy.validId(previousRequestIdentifier))
                || (previousAttemptNumber != null && previousAttemptNumber < 1)) {
            throw new IllegalArgumentException("Ý định gửi yêu cầu không hợp lệ.");
        }
        this.requestIdentifier = requestIdentifier;
        this.tripIdentifier = tripIdentifier;
        this.passengerIdentifier = passengerIdentifier;
        this.previousRequestIdentifier = previousRequestIdentifier;
        this.previousAttemptNumber = previousAttemptNumber;
    }

    /** Chỉ gọi từ thao tác Gửi; lần trước phải đã hủy trên server nếu có. */
    public static RequestSubmissionIntent create(
            String tripIdentifier,
            String passengerIdentifier,
            RequestCreationRepository.Result previous) {
        String previousIdentifier = null;
        Long previousNumber = null;
        if (previous != null) {
            if (!"CANCELLED".equals(previous.status)) {
                throw new IllegalStateException("Yêu cầu trước chưa đủ điều kiện gửi lại.");
            }
            previousIdentifier = previous.requestId;
            previousNumber = previous.attemptNumber;
        }
        return new RequestSubmissionIntent(
                RequestAttemptContract.newRequestId(passengerIdentifier),
                tripIdentifier,
                passengerIdentifier,
                previousIdentifier,
                previousNumber);
    }

    /** Cố định lần hủy trước khi gửi; đối thủ thay guard không được tự tăng sang lần kế tiếp. */
    public boolean follows(RequestCreationRepository.Result previous) {
        if (previous == null) {
            return previousRequestIdentifier == null && previousAttemptNumber == null;
        }
        return "CANCELLED".equals(previous.status)
                && Objects.equals(previousRequestIdentifier, previous.requestId)
                && Objects.equals(previousAttemptNumber, previous.attemptNumber);
    }
}
