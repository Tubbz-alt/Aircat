package com.talybin.aircat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static android.app.Activity.RESULT_OK;


public class JobDetailsFragment extends Fragment implements Job.StateListener {

    static final String KEY_JOB_ID = "job_id";

    private static final int FILE_SELECT_CODE = 0;

    private BottomSheetDialog bottomDialog;

    private Job job = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_job_details, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Retrieve the job
        String key = null;
        Bundle args = getArguments();
        if (args != null)
            key = args.getString(KEY_JOB_ID);
        if (key == null) {  // Should never happen
            Log.e(this.getClass().getName(), "Missing key argument");
            goBack();
            return;
        }

        setJob(JobManager.getInstance().get(key));

        // Alternatives for recovered password
        bottomDialog = new BottomSheetDialog(requireContext());
        bottomDialog.setContentView(R.layout.job_item_bottom_sheet);

        setupBottomDialog();
    }

    private void setJob(Job job) {

        if (job == null) {
            // Job not found or deleted
            goBack();
            return;
        }

        this.job = job;
        View view = requireView();

        View jobItem = view.findViewById(R.id.job_details_item);
        JobListAdapter.JobViewHolder viewHolder = new JobListAdapter.JobViewHolder(jobItem);
        viewHolder.bindData(job);

        // Redirect state listener to re-invalidate options menu
        Job.StateListener oldListener = job.getStateListener();
        job.setStateListener(j -> {
            if (j.getState() == Job.State.NOT_RUNNING) {
                // Activity may be null here if Delete button pressed and
                // we are about to leave this fragment
                if (getActivity() != null)
                    getActivity().invalidateOptionsMenu();
            }
            oldListener.onStateChange(j);
        });

        // Fill fields
        ((TextView)view.findViewById(R.id.job_details_mac_ap)).setText(job.getApMac());
        ((TextView)view.findViewById(R.id.job_details_mac_client)).setText(job.getClientMac());
        ((TextView)view.findViewById(R.id.job_details_pmkid)).setText(job.getPmkId());
        ((TextView)view.findViewById(R.id.job_details_wordlist)).setText(
                WordList.getFileName(job.getUri()));

        // Click listeners
        jobItem.setOnClickListener(v -> showPasswordDialog());
        view.findViewById(R.id.job_details_hash_info).setOnClickListener(this::onViewClick);
        view.findViewById(R.id.job_details_wordlist_info).setOnClickListener(v -> bottomDialog.show());

        requireActivity().invalidateOptionsMenu();
    }

    @Override
    public void onDestroyView() {
        job = null;
        bottomDialog = null;

        super.onDestroyView();
    }

    private void setupBottomDialog() {
        Context context = requireContext();

        final int[] jobActions = {
                R.id.job_action_browse,
                // TODO on long click make uri editable
        };
        for (int id : jobActions) {
            View v = Objects.requireNonNull(bottomDialog.findViewById(id));
            v.setOnClickListener(this::onViewClick);
        }

        ListView lastUsedList = bottomDialog.findViewById(R.id.last_used_list_view);

        if (lastUsedList != null) {
            Uri builtInUri = WordList.getDefault();

            // Create a list of last used word lists excluding the built-in ones
            List<Uri> wordLists = WordListManager.getInstance().getAll().stream()
                    .sorted((left, right) -> left.getLastUsed().compareTo(right.getLastUsed()))
                    .map(WordList::getUri)
                    .filter(uri -> !uri.equals(builtInUri))
                    .limit(5)
                    .collect(Collectors.toList());

            // Always add built-in at the end
            wordLists.add(builtInUri);

            if (!wordLists.isEmpty()) {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        context, R.layout.wordlist_choose_item, R.id.wordlist_choose_item_text,
                        wordLists.stream().map(WordList::getFileName).collect(Collectors.toList()));
                lastUsedList.setAdapter(adapter);
                lastUsedList.setOnItemClickListener((parent, view, position, id) -> {
                    bottomDialog.dismiss();
                    // Update current job and the view
                    job.setUri(wordLists.get(position));
                    setJob(job);
                });
            }
            else {
                lastUsedList.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.job_menu, menu);
        menu.findItem(R.id.action_select_all).setVisible(false);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        boolean loaded = job != null;
        boolean running = loaded && job.isProcessing();

        menu.findItem(R.id.action_stop).setVisible(loaded && running);
        menu.findItem(R.id.action_start).setVisible(loaded && !running);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_start:
                startJob();
                return true;
            case R.id.action_stop:
                stopJob();
                return true;
            case R.id.action_remove:
                removeJob();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void goBack() {
        NavHostFragment.findNavController(this).popBackStack();
    }

    private void startJob() {
        HashCat.getInstance().start(job);
        if (job.getState() == Job.State.NOT_RUNNING)
            Toast.makeText(getContext(), R.string.err_job_start, Toast.LENGTH_LONG).show();
        else
            requireActivity().invalidateOptionsMenu();
    }

    private void stopJob() {
        HashCat.getInstance().stop(job);
        requireActivity().invalidateOptionsMenu();
    }

    private void removeJob() {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.remove_job)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    JobManager.getInstance().remove(job);
                    goBack();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onStateChange(Job job) {
        requireActivity().invalidateOptionsMenu();
    }

    private void showPasswordDialog() {
        // This dialog is for actions on retrieved password
        if (job.getPassword() == null)
            return;

        Context context = requireContext();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(job.getSafeSSID())
                .setMessage(job.getPassword())
                // Connect
                .setPositiveButton(R.string.connect, (dlg, id) -> connect())
                // Copy to clipboard
                .setNegativeButton(android.R.string.copy, (dlg, id) -> {
                    if (copyToClipboard(getString(R.string.password), job.getPassword()))
                        Toast.makeText(context, R.string.password_clipped, Toast.LENGTH_SHORT).show();
                });

        builder.create().show();
    }

    private void onViewClick(View view) {
        bottomDialog.dismiss();
        Context ctx = requireContext();

        switch (view.getId()) {

            case R.id.job_action_browse:
                browseForWordlist();
                break;

            case R.id.job_details_hash_info:
                if (copyToClipboard("hashcat", job.getHash()))
                    Toast.makeText(ctx, R.string.hash_clipped, Toast.LENGTH_SHORT).show();
                break;

            default:
                Toast.makeText(getContext(), R.string.not_implemented_yet, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private boolean copyToClipboard(String name, String data) {
        Context ctx = requireContext();
        ClipboardManager clipboard =
                (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(name, data));
            return true;
        }
        else
            Toast.makeText(ctx, R.string.err_operation_failed, Toast.LENGTH_LONG).show();
        return false;
    }

    private void browseForWordlist() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(
                    intent, getString(R.string.choose_wordlist)), FILE_SELECT_CODE);
        }
        catch (Exception e) {
            Toast.makeText(getContext(),
                    getString(R.string.err_message, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void connect() {
        Context context = requireContext();

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Toast.makeText(context, R.string.err_unknown, Toast.LENGTH_SHORT).show();
            return;
        }

        WifiConfiguration conf = new WifiConfiguration();

        conf.hiddenSSID = job.getSsid() == null;
        conf.preSharedKey = "\"" + job.getPassword() + "\"";
        if (conf.hiddenSSID) {
            conf.SSID = job.getApMac();
            conf.BSSID = "\"" + job.getApMac()+ "\"";
        }
        else
            conf.SSID = "\"" + job.getSsid() + "\"";

        int networkId = wifiManager.addNetwork(conf);

        wifiManager.disconnect();
        wifiManager.enableNetwork(networkId, true);
        wifiManager.reconnect();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != FILE_SELECT_CODE)
            return;
        if (resultCode != RESULT_OK)
            return;

        Uri uri = data.getData();
        if (uri != null) {
            WordListManager.getInstance().add(new WordList(uri));
            // Update current job and the view
            job.setUri(uri);
            setJob(job);
        }
    }
}
