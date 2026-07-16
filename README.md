# Livess Sync

Livess Sync is the AliveGroup-maintained Android call tracking client used by
Livess. It is based on
[MarkoBL/AndroidCallLogSync](https://github.com/MarkoBL/AndroidCallLogSync) and
remains licensed under GPL-3.0.

Official builds use the Android application id `br.com.livess.callsync`. This
identity is intentionally different from the upstream application because the
upstream signing key is not controlled by AliveGroup.

With this android app, you can sync your call log with a remote endpoint. 

## Official release signing

Release APKs must be signed with the AliveGroup-managed key. The key and its
passwords are injected at build time and must never be committed.

Required environment variables:

```text
LIVESS_ANDROID_KEYSTORE_FILE
LIVESS_ANDROID_KEYSTORE_PASSWORD
LIVESS_ANDROID_KEY_ALIAS
LIVESS_ANDROID_KEY_PASSWORD
```

Build and verify:

```powershell
gradlew.bat -p . testDebugUnitTest lintDebug verifyReleaseSigning assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

The release certificate SHA-256 fingerprint is the stable public identifier
used by the release pipeline. Secret key material stays in Infisical and is
injected through the Quartzo operational boundary.

# Build your own Remote Endpoint

Building your own remote endpoint for Call Log Sync is straight forward. The app sends the call log as a `POST` request to the url that you specify in the settings. This `POST` request contains the following headers:

`Device-Name` 
The name of the device

`Device-Token` 
The device token

`Device-Number` 
The device number (optional)


`Test-Run`
Only sent, when testing a remote endpoint


The payload is a JSON array with an object for every call log entry:

```
[{
	"ID": 1,
	"NUMBER": "00495235",
	"TYPE": 1,
	"DATE": 1642421186000,
	"DURATION": 10
}, {
	"ID": 2,
	"NUMBER": "00415434",
	"TYPE": 2,
	"DATE": 1642821186000,
	"DURATION": 22
}]
```

`ID (int)` 
The android internal id of this call log entry.

`NUMBER (string)` 
The called/answered phone number.

`TYPE (int)` 
The internal type of the call (incoming, outgoing or missed). See https://developer.android.com/reference/android/provider/CallLog.Calls#TYPE for more information.

`DATE (long)` 
The date the call occured, in milliseconds since the epoch.

`DURATION (long)` 
The duration of the call in seconds.


# Privacy Policy

- The app only sends the call log to the specified remote endpoint
- The app does not collect any data at all
- The app does not send any data to third party services
