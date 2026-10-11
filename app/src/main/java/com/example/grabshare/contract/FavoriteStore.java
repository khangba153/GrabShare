package com.example.grabshare.contract;

/** Hợp đồng FavoriteRepository của B cung cấp cho màn Detail của A. */
public interface FavoriteStore {
    /** Kiểm tra yêu thích theo document userId_tripId, trả Boolean khác null. */
    void isFavorite(String userId, String tripId, RepositoryCallback<Boolean> callback);

    /** Thêm yêu thích chính chủ; chỉ thành công sau khi Firestore xác nhận. */
    void addFavorite(String userId, String tripId, RepositoryCallback<Void> callback);

    /** Bỏ yêu thích chính chủ theo ID cố định. */
    void removeFavorite(String userId, String tripId, RepositoryCallback<Void> callback);
}
