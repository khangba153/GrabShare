package com.example.grabshare.model;

/** Địa điểm dùng chung cho điểm đi, điểm đến, điểm đón và điểm xuống. */
public class Place {
    private String name;
    private Double latitude;
    private Double longitude;

    /** Tạo đối tượng rỗng để Firestore ánh xạ dữ liệu. */
    public Place() {
    }

    /** Tạo đối tượng từ các field dữ liệu dùng chung. */
    public Place(String name, Double latitude, Double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** Lấy tên địa điểm. */
    public String getName() {
        return name;
    }

    /** Gán tên địa điểm. */
    public void setName(String name) {
        this.name = name;
    }

    /** Lấy vĩ độ. */
    public Double getLatitude() {
        return latitude;
    }

    /** Gán vĩ độ. */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    /** Lấy kinh độ. */
    public Double getLongitude() {
        return longitude;
    }

    /** Gán kinh độ. */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
