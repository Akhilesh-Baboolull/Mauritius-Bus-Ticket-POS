package com.ayb.busticketpos;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StageViewModel extends AndroidViewModel {
    private final AppDatabase db;

    // Will emit exactly one ordered list (forward or reversed)
    private final MediatorLiveData<List<StageEntity>> stagesLive = new MediatorLiveData<>();
    // Shared “cursor” index
    private final MutableLiveData<Integer> indexLive = new MutableLiveData<>();

    public StageViewModel(@NonNull Application app) {
        super(app);
        db = AppDatabase.getInstance(app);

        // Seed our index from Prefs on startup
        int saved = Prefs.getCurrentStageID(app);
        indexLive.setValue(saved);
    }

    /** Fragments observe this to get the ordered list of stages */
    public LiveData<List<StageEntity>> getStages() {
        return stagesLive;
    }

    /** Fragments observe this for the shared cursor index */
    public LiveData<Integer> getCurrentIndex() {
        return indexLive;
    }

    /**
     * Call once per route.  We add Room’s LiveData as a source,
     * but only remove it when we see a *non-empty* list.
     */
    public void loadStagesForRoute(String routeId) {
        LiveData<List<StageEntity>> source =
                db.stageDao().getStagesForRouteLive(routeId);

        stagesLive.addSource(source, list -> {
            // 1) If the list is still empty, wait for the real data
            if (list == null || list.isEmpty()) {
                return;
            }

            // 2) Now that we have real data, un-hook the Room LiveData
            stagesLive.removeSource(source);

            // 3) Apply forward/reverse based on Prefs
            int dir = Prefs.getRouteDirection(getApplication());
            List<StageEntity> ordered = new ArrayList<>(list);
            if (dir < 0) {
                Collections.reverse(ordered);
            }

            // 4) Emit the ordered list
            stagesLive.setValue(ordered);

            // 5) Clamp and emit our saved index
            int saved = Prefs.getCurrentStageID(getApplication());
            if (saved < 0 || saved >= ordered.size()) {
                saved = 0;
            }
            indexLive.setValue(saved);
        });
    }

    /** Move forward one step (and persist it) */
    public void next() {
        Integer idx = indexLive.getValue();
        List<StageEntity> list = stagesLive.getValue();
        if (idx != null && list != null && idx < list.size() - 1) {
            int n = idx + 1;
            indexLive.setValue(n);
            Prefs.saveCurrentStageID(getApplication(), n);
        }
    }

    /** Move back one step (and persist it) */
    public void prev() {
        Integer idx = indexLive.getValue();
        if (idx != null && idx > 0) {
            int p = idx - 1;
            indexLive.setValue(p);
            Prefs.saveCurrentStageID(getApplication(), p);
        }
    }

    /** (Optional) force-set the index (and persist it) */
    public void setIndex(int i) {
        indexLive.setValue(i);
        Prefs.saveCurrentStageID(getApplication(), i);
    }
}
