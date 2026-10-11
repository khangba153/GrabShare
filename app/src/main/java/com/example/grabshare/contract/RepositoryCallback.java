package com.example.grabshare.contract;

/** Kết quả bất đồng bộ dùng chung; bên triển khai phải gọi lại trên luồng UI. */
public interface RepositoryCallback<T> {
    /** Nhận dữ liệu khi hoàn tất; thao tác Void trả null sau khi đã thành công. */
    void onSuccess(T data);

    /** Nhận lỗi thực tế; không báo thành công khi thao tác bị từ chối. */
    void onError(Exception error);
}
