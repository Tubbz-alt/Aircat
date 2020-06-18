package com.talybin.aircat;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;

// A Repository manages queries and allows you to use multiple backends.
// In the most common example, the Repository implements the logic for
// deciding whether to fetch data from a network or use results cached
// in a local database.
class AircatRepository {

    private WordListDao wordListDao;
    private LiveData<List<WordList>> allWordLists;

    // Note that in order to unit test the AircatRepository, you have to remove the Application
    // dependency. This adds complexity and much more code, and this sample is not about testing.
    // See the BasicSample in the android-architecture-components repository at
    // https://github.com/googlesamples
    AircatRepository(Application application) {
        AircatRoomDatabase db = AircatRoomDatabase.getDatabase(application);
        wordListDao = db.wordListDao();
        allWordLists = wordListDao.getWordLists();
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    LiveData<List<WordList>> getAllWordLists() {
        return allWordLists;
    }

    void insert(WordList wordList) {
        // You must call this on a non-UI thread or your app will throw an exception. Room ensures
        // that you're not doing any long running operations on the main thread, blocking the UI.
        AircatRoomDatabase.databaseWriteExecutor.execute(() -> {
            wordListDao.insert(wordList);
        });
    }
}