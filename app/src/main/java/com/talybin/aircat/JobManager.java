package com.talybin.aircat;

import android.util.Log;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class JobManager {

    public interface Listener {
        void onNewJob(Job job);
        //void onJobStart(HashCat handler, Job job);
    }

    private static JobManager instance = new JobManager();

    private Set<Listener> listeners;
    private List<Job> jobs;

    public static JobManager getInstance() {
        return instance;
    }

    private JobManager() {
        listeners = new HashSet<>();
        jobs = new LinkedList<>();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public void add(Job job) {
        jobs.add(job);

        Log.d("JobManager::add", "---> ap mac [" + job.getApMac() + "]");
        Log.d("JobManager::add", "---> client mac [" + job.getClientMac() + "]");
        Log.d("JobManager::add", "---> pmkid [" + job.getHash() + "]");
        Log.d("JobManager::add", "---> ssid [" + job.getSSID() + "]");

        // Notify others
        for (Listener listener : listeners)
            listener.onNewJob(job);
    }

    public void add(ApInfo apInfo) {
        add(new Job(apInfo));
    }

    public void remove(int pos) {
        jobs.remove(pos).stop();
    }

    public void remove(Job job) {
        job.stop();
        jobs.remove(job);
    }
}
