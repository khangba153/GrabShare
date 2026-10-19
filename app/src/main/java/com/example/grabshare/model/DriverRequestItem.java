package com.example.grabshare.model;

/** Một yêu cầu đi nhờ cùng dữ liệu chuyến và hồ sơ công khai dùng để hiển thị. */
public class DriverRequestItem {
    private final TripRequest request;
    private Trip trip;
    private PublicProfile passengerProfile;

    /** Tạo item từ yêu cầu đã đọc từ Firestore. */
    public DriverRequestItem(TripRequest request) {
        this.request = request;
    }

    /** Lấy yêu cầu đi nhờ. */
    public TripRequest getRequest() {
        return request;
    }

    /** Lấy chuyến liên quan. */
    public Trip getTrip() {
        return trip;
    }

    /** Gán chuyến liên quan sau khi tải dữ liệu. */
    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    /** Lấy hồ sơ công khai của hành khách. */
    public PublicProfile getPassengerProfile() {
        return passengerProfile;
    }

    /** Gán hồ sơ công khai của hành khách. */
    public void setPassengerProfile(PublicProfile passengerProfile) {
        this.passengerProfile = passengerProfile;
    }
}
