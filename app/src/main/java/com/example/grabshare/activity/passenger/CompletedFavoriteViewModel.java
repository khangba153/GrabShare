package com.example.grabshare.activity.passenger;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.grabshare.contract.RepositoryCallback;
import com.example.grabshare.repository.passenger.FavoriteRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/** Giữ một thao tác yêu thích qua xoay; repository kiểm lại quyền trước ghi. */
public class CompletedFavoriteViewModel extends ViewModel {
    public static final class State {
        public final Boolean favorite;
        public final boolean busy;
        public final String message;

        State(Boolean favorite, boolean busy, String message) {
            this.favorite = favorite;
            this.busy = busy;
            this.message = message;
        }
    }

    private final FavoriteRepository repository = new FavoriteRepository(FirebaseFirestore.getInstance());
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(null, false, ""));
    private String userIdentifier;
    private String tripIdentifier;
    private int generation;
    private boolean cleared;

    public LiveData<State> getState() {
        return state;
    }

    public void start(String userIdentifier, String tripIdentifier) {
        if (tripIdentifier.equals(this.tripIdentifier) && userIdentifier.equals(this.userIdentifier)) {
            return;
        }
        generation = generation + 1;
        this.userIdentifier = userIdentifier;
        this.tripIdentifier = tripIdentifier;
        read();
    }

    private boolean current(int expectedGeneration) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return !cleared && generation == expectedGeneration && user != null
                && user.getUid().equals(userIdentifier);
    }

    private void read() {
        final int expectedGeneration = generation;
        if (!current(expectedGeneration)) {
            return;
        }
        state.setValue(new State(null, true, "Đang đọc yêu thích…"));
        repository.isFavorite(userIdentifier, tripIdentifier, new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean favorite) {
                if (current(expectedGeneration)) {
                    state.setValue(new State(favorite, false, ""));
                }
            }

            @Override
            public void onError(Exception error) {
                if (current(expectedGeneration)) {
                    state.setValue(new State(null, false, "Chưa đọc được yêu thích. Bấm Thử lại."));
                }
            }
        });
    }

    public void toggle() {
        State value = state.getValue();
        if (value == null || value.busy || !current(generation)) {
            return;
        }
        if (value.favorite == null) {
            read();
            return;
        }
        final boolean previousFavorite = value.favorite;
        final int expectedGeneration = generation;
        state.setValue(new State(previousFavorite, true, "Đang cập nhật yêu thích…"));
        RepositoryCallback<Void> callback = new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                if (current(expectedGeneration)) {
                    state.setValue(new State(!previousFavorite, false, "Đã cập nhật yêu thích."));
                }
            }

            @Override
            public void onError(Exception error) {
                if (current(expectedGeneration)) {
                    // Đọc đối soát trước lần bấm tiếp theo; không đoán ghi đã thành công.
                    state.setValue(new State(null, false, "Chưa xác nhận được kết quả. Bấm Thử lại để đọc lại."));
                }
            }
        };
        if (previousFavorite) {
            repository.removeFavorite(userIdentifier, tripIdentifier, callback);
        } else {
            repository.addFavorite(userIdentifier, tripIdentifier, callback);
        }
    }

    @Override
    protected void onCleared() {
        cleared = true;
        generation = generation + 1;
    }
}
