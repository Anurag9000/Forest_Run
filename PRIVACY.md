# Forest Run Privacy Policy

**Effective date:** August 6, 2026  
**Applies to:** the Forest Run Android application with application ID `com.anurag9000.forestrun`

## Summary

Forest Run is designed as an offline, single-player game. The application source does not include advertising, analytics, account, cloud-sync, billing, social-login, crash-reporting, or network-transport SDKs. The application does not request Android's Internet permission and cannot transmit gameplay or personal data through its own code.

Forest Run stores game state locally on the device so that progression can continue between sessions. The application does not sell data and does not share locally stored game state with advertisers or other third parties.

## Data stored locally

Forest Run may store the following application data in its private Android storage:

- scores, best distance, completed-run summaries, and route history;
- Seed currency, Garden plants, wardrobe selections, and unlock progress;
- encounter, mercy, relationship, forest-mood, return-moment, and story-fragment state;
- reduced-motion, audio, and haptic preferences;
- ghost-run frames and their local recovery receipts/manifests;
- recovery journals and integrity metadata needed to complete or diagnose interrupted local writes.

This data describes game progress and settings. The application does not ask for a name, email address, phone number, account identifier, contact list, precise location, photographs, microphone recordings, or advertising identifier.

## Permissions

The release source manifest requests only:

- `android.permission.VIBRATE`, used for optional gameplay haptic feedback.

Haptics can be disabled in the application's feedback settings. Forest Run does not request Internet, advertising-ID, billing, location, camera, microphone, contacts, calendar, call-log, SMS, or broad storage permissions.

## Network access and third-party SDKs

Forest Run does not request `android.permission.INTERNET`. Its production dependency declarations contain no advertising, analytics, remote-config, authentication, cloud-storage, billing, networking, or third-party crash-reporting SDK.

Google Play, Android, a device manufacturer, or an operating-system service may independently process store, installation, security, or diagnostic information under its own terms and user settings. Forest Run does not receive that platform data through an in-app account or server.

## Backup, retention, and deletion

Android application backup is disabled for Forest Run (`android:allowBackup="false"`). There is no Forest Run cloud account or server-side copy of game progress.

Local data remains until it is replaced by normal gameplay, cleared through Android's **Settings → Apps → Forest Run → Storage → Clear storage**, or removed when the application is uninstalled. Because no remote Forest Run account exists, there is no separate remote-deletion request process.

## Debug and release evidence

Debug builds contain developer-triggered deterministic scenarios, recovery-maintenance commands, and performance/evidence capture tools. These facilities do not automatically transmit data. Mutating recovery commands are rejected by non-debuggable release builds.

Authorized release testing may deliberately export performance reports, screenshots, scenario traces, artifact identities, and anonymized device-profile evidence from test devices. Those files are created and handled by release operators; ordinary players are not automatically enrolled in that evidence workflow.

## Children and target audience

The source does not collect personal information from children or adults. Final age targeting, content-rating, family-policy eligibility, and store declarations remain product-owner decisions that must be reviewed in the applicable store console before publication.

## Security and changes

Any future addition of networking, accounts, cloud synchronization, analytics, advertising, billing, crash reporting, new Android permissions, or remote support must be treated as a privacy-model change. The source contract, this policy, the store Data Safety declaration, and release evidence must all be updated before that feature is shipped.

## Publication status

This repository policy is the source-backed privacy statement for the current application. Before a public store release, the owner must review it for the intended jurisdictions, publish it at a stable publicly accessible HTTPS URL, and ensure the store listing and Data Safety answers match the exact signed candidate.
