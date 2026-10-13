package com.simomusic.player;

import android.app.Activity;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes one or many songs from the phone the way each Android version allows it: Android 11+ shows the system's
 * own "allow SimoMusic to delete these songs?" prompt once for the whole batch, Android 10 asks for the one-time
 * access per file that needs it, older versions delete directly (after the storage permission). It has no UI of
 * its own and works from any screen.
 */
public class DeleteActivity extends Activity {
    private static final int REQ_SYSTEM = 91, REQ_PERM = 92;
    private final List<Uri> uris = new ArrayList<>();
    private final List<Long> ids = new ArrayList<>();
    private final Set<Long> deleted = new HashSet<>();
    private int next;       // Android 10 / older: index of the file being handled

    public static void start(Context c, Track t) {
        start(c, Collections.singletonList(t));
    }

    public static void start(Context c, List<Track> list) {
        if (list == null || list.isEmpty()) return;
        long[] ids = new long[list.size()];
        ArrayList<Uri> us = new ArrayList<>(list.size());
        for (int i = 0; i < ids.length; i++) {
            ids[i] = list.get(i).id;
            us.add(list.get(i).uri);
        }
        Intent i = new Intent(c, DeleteActivity.class).putExtra("ids", ids).putParcelableArrayListExtra("uris", us);
        Ui.go(c, i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            long[] idArr = getIntent().getLongArrayExtra("ids");
            ArrayList<Uri> us = androidx.core.content.IntentCompat.getParcelableArrayListExtra(getIntent(), "uris", Uri.class);
            if (idArr == null || us == null || idArr.length == 0 || idArr.length != us.size()) {
                finish();
                return;
            }
            for (long id : idArr) ids.add(id);
            uris.addAll(us);
            if (b == null) begin();
        } catch (Throwable t) {
            CrashGuard.nonFatal("delete", t);
            fail();
        }
    }

    private void begin() throws Exception {
        if (Build.VERSION.SDK_INT >= 30) {
            IntentSender s = MediaStore.createDeleteRequest(getContentResolver(), uris).getIntentSender();
            startIntentSenderForResult(s, REQ_SYSTEM, null, 0, 0, 0);
        } else if (Build.VERSION.SDK_INT >= 29) {
            deleteQ();
        } else if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            deleteOld();
        } else {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
        }
    }

    /** Before Android 10: the storage permission is enough. */
    private void deleteOld() {
        ContentResolver cr = getContentResolver();
        for (int i = 0; i < uris.size(); i++) {
            try {
                if (cr.delete(uris.get(i), null, null) > 0) deleted.add(ids.get(i));
            } catch (Exception e) {
                CrashGuard.nonFatal("delete", e);
            }
        }
        finishUp();
    }

    /** Android 10: delete file by file; when the system says one needs the user's consent, ask once and go on. */
    private void deleteQ() {
        ContentResolver cr = getContentResolver();
        try {
            while (next < uris.size()) {
                try {
                    if (cr.delete(uris.get(next), null, null) > 0) deleted.add(ids.get(next));
                    next++;
                } catch (RecoverableSecurityException e) {
                    startIntentSenderForResult(e.getUserAction().getActionIntent().getIntentSender(), REQ_SYSTEM, null, 0, 0, 0);
                    return;
                }
            }
        } catch (Exception e) {
            CrashGuard.nonFatal("delete", e);
        }
        finishUp();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SYSTEM) return;
        if (Build.VERSION.SDK_INT >= 30) {
            if (res == RESULT_OK) {
                deleted.addAll(ids);             // the system already deleted them all
                finishUp();
            } else {
                finish();
            }
            return;
        }
        if (res != RESULT_OK) {                  // consent refused: keep what was already deleted, stop here
            finishUp();
            return;
        }
        deleteQ();                               // retry the same file, then carry on with the rest
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == REQ_PERM && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            deleteOld();
        } else {
            finish();
        }
    }

    private void finishUp() {
        if (deleted.isEmpty()) {
            fail();
            return;
        }
        Library.remove(deleted);
        Store.forget(this, deleted);
        Pb.removeIds(deleted);
        int n = deleted.size();
        if (ids.size() == 1) Ui.toast(this, R.string.song_deleted);
        else if (n == ids.size()) Ui.toast(this, getResources().getQuantityString(R.plurals.songs_deleted_n, n, n));
        else Ui.toast(this, getString(R.string.delete_partial, n, ids.size()));
        finish();
    }

    private void fail() {
        Ui.toast(this, R.string.delete_failed);
        finish();
    }
}
