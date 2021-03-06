package eu.siacs.conversations.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityWelcomeBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.InstallReferrerUtils;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.XmppUri;
import rocks.xmpp.addr.Jid;

import static eu.siacs.conversations.utils.PermissionUtils.allGranted;
import static eu.siacs.conversations.utils.PermissionUtils.writeGranted;

public class WelcomeActivity extends XmppActivity {

    private static final int REQUEST_IMPORT_BACKUP = 0x63fb;

    private XmppUri inviteUri;

    public static void launch(AppCompatActivity activity) {
        Intent intent = new Intent(activity, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    public void onInstallReferrerDiscovered(final String referrer) {
        Log.d(Config.LOGTAG, "welcome activity: on install referrer discovered " + referrer);
        if (referrer != null) {
            final XmppUri xmppUri = new XmppUri(referrer);
            runOnUiThread(() -> processXmppUri(xmppUri));
        }
    }

    private boolean processXmppUri(final XmppUri xmppUri) {
        if (xmppUri.isValidJid()) {
            final String preauth = xmppUri.getParamater("preauth");
            final Jid jid = xmppUri.getJid();
            final Intent intent;
            if (xmppUri.isAction(XmppUri.ACTION_REGISTER)) {
                intent = SignupUtils.getTokenRegistrationIntent(this, jid, preauth);
            } else if (xmppUri.isAction(XmppUri.ACTION_ROSTER) && "y".equals(xmppUri.getParamater("ibr"))) {
                intent = SignupUtils.getTokenRegistrationIntent(this, Jid.ofDomain(jid.getDomain()), preauth);
                intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, xmppUri.toString());
            } else {
                intent = null;
            }
            if (intent != null) {
                startActivity(intent);
                finish();
                return true;
            }
            this.inviteUri = xmppUri;
        }
        return false;
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {

    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
        new InstallReferrerUtils(this);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent != null) {
            setIntent(intent);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        ActivityWelcomeBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_welcome);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar(), false);
        binding.learnMore.setOnClickListener(v -> {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://snikket.org/app/learn/"));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this,R.string.no_application_found_to_open_link, Toast.LENGTH_LONG).show();
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.welcome_menu, menu);
        final MenuItem scan = menu.findItem(R.id.action_scan_qr_code);
        scan.setVisible(getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_import_backup:
                if (hasStoragePermission(REQUEST_IMPORT_BACKUP)) {
                    startActivity(new Intent(this, ImportBackupActivity.class));
                }
                break;
            case R.id.action_scan_qr_code:
                UriHandlerActivity.scan(this);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        UriHandlerActivity.onRequestPermissionResult(this, requestCode, grantResults);
        if (grantResults.length > 0) {
            if (allGranted(grantResults)) {
                switch (requestCode) {
                    case REQUEST_IMPORT_BACKUP:
                        startActivity(new Intent(this, ImportBackupActivity.class));
                        break;
                }
            } else if (Arrays.asList(permissions).contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
            }
        }
        if (writeGranted(grantResults, permissions)) {
            if (xmppConnectionService != null) {
                xmppConnectionService.restartFileObserver();
            }
        }
    }

    public void addInviteUri(Intent to) {
        final Intent from = getIntent();
        if (from != null && from.hasExtra(StartConversationActivity.EXTRA_INVITE_URI)) {
            final String invite = from.getStringExtra(StartConversationActivity.EXTRA_INVITE_URI);
            to.putExtra(StartConversationActivity.EXTRA_INVITE_URI, invite);
        } else if (this.inviteUri != null) {
            Log.d(Config.LOGTAG, "injecting referrer uri into on-boarding flow");
            to.putExtra(StartConversationActivity.EXTRA_INVITE_URI, this.inviteUri.toString());
        }
    }

}
