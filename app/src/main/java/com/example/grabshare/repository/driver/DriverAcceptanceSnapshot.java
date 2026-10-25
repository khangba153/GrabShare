package com.example.grabshare.repository.driver;

import com.example.grabshare.contract.ContributionCalculator;
import com.example.grabshare.contract.DetourLimit;
import com.example.grabshare.contract.RequestRouteResolver;
import com.example.grabshare.model.Place;
import com.example.grabshare.model.Trip;
import com.example.grabshare.model.TripRequest;
import com.example.grabshare.repository.search.SearchTripPolicy;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;

/** Giữ dữ liệu máy chủ dùng đo tuyến để transaction không ghi theo kết quả của tuyến cũ. */
final class DriverAcceptanceSnapshot {
    final Trip trip;
    final TripRequest request;
    final Place dropoff;
    final boolean alreadyAccepted;
    private final Map<String, Object> requestFields;
    private final Map<String, Object> tripFields;

    /** Không sửa snapshot tiền, kể cả khi mức tham khảo toàn tuyến hiện tại đã thay đổi. */
    DriverAcceptanceSnapshot(DocumentSnapshot requestDocument, DocumentSnapshot tripDocument,
            String driverIdentifier) {
        request = requestDocument.toObject(TripRequest.class);
        trip = tripDocument.toObject(Trip.class);
        if (request == null || trip == null
                || !driverIdentifier.equals(request.getDriverId())
                || !driverIdentifier.equals(trip.getDriverId())
                || !tripDocument.getId().equals(request.getTripId())
                || !SearchTripPolicy.validId(request.getPassengerId())
                || driverIdentifier.equals(request.getPassengerId())
                || !requestDocument.getId().equals(request.getTripId() + "_"
                        + request.getPassengerId())) {
            throw new IllegalArgumentException("Yêu cầu hoặc chuyến không thuộc phiên chủ xe này.");
        }
        request.setId(requestDocument.getId());
        trip.setId(tripDocument.getId());
        alreadyAccepted = "ACCEPTED".equals(request.getStatus())
                && requestDocument.getId().equals(trip.getAcceptedRequestId())
                && ("FULL".equals(trip.getStatus()) || "COMPLETED".equals(trip.getStatus()));
        requestFields = requestDocument.getData();
        tripFields = tripDocument.getData();
        if (alreadyAccepted) {
            dropoff = null;
            return;
        }
        if (!"PENDING".equals(request.getStatus()) || !"OPEN".equals(trip.getStatus())
                || trip.getAvailableSeats() == null || trip.getAvailableSeats() != 1L
                || trip.getAcceptedRequestId() == null || !trip.getAcceptedRequestId().isEmpty()
                || trip.getDepartureAt() == null
                || trip.getDepartureAt().toDate().getTime() <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("Yêu cầu không còn chờ duyệt hoặc chuyến không còn nhận khách.");
        }
        if (!SearchTripPolicy.validPlace(trip.getOrigin())
                || !SearchTripPolicy.validPlace(trip.getDestination())
                || !SearchTripPolicy.validPlace(request.getPickup())
                || !DetourLimit.isValidMeters(trip.getMaxDetourMeters())) {
            throw new IllegalArgumentException("Chuyến thiếu tuyến hoặc giới hạn đi thêm hợp lệ.");
        }
        dropoff = RequestRouteResolver.resolveDropoff(request, trip);
        // Thiếu hẳn snapshot là yêu cầu cũ; snapshot đã có nhưng sai phải dừng nhận khách.
        ContributionCalculator.readConfirmed(request);
    }

    /** So cả mốc trạng thái, P/C, tiền và cấu hình chuyến trước khi ghi nguyên tử. */
    boolean matches(DocumentSnapshot requestDocument, DocumentSnapshot tripDocument) {
        return requestDocument.exists() && tripDocument.exists()
                && requestFields.equals(requestDocument.getData())
                && tripFields.equals(tripDocument.getData());
    }
}
