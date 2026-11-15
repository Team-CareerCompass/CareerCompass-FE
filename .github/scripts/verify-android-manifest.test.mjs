import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

import {
  inspectDataExtractionRules,
  inspectManifest,
  inspectPrivacyDefaults,
} from './verify-android-manifest.mjs';

const disabledFirebaseAutoInit = `
    <meta-data android:name="firebase_analytics_collection_enabled" android:value="false" />
    <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />`;

const manifest = ({
  permissions = '',
  components = '',
  cleartext = 'false',
  allowBackup = 'false',
  backupAttributes = 'android:dataExtractionRules="@xml/data_extraction_rules"',
  metadata = disabledFirebaseAutoInit,
} = {}) => `
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  ${permissions}
  <application
      ${cleartext === null ? '' : `android:usesCleartextTraffic="${cleartext}"`}
      android:allowBackup="${allowBackup}"
      ${backupAttributes}>
    ${metadata}
    ${components}
  </application>
</manifest>`;

test('current allowed permissions and protected exported components pass', () => {
  const violations = inspectManifest(
    manifest({
      permissions: `
        <uses-permission android:name="android.permission.INTERNET" />
        <uses-permission android:name="com.example.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />`,
      components: `
        <activity android:name="com.careercompass.careercompass_fe.MainActivity" android:exported="true" />
        <service android:name="example.SystemService" android:exported="true"
                 android:permission="android.permission.BIND_JOB_SERVICE" />
        <provider android:name="example.PrivateProvider" android:exported="false" />`,
    }),
  );

  assert.deepEqual(violations, []);
});

test('new permissions fail closed until reviewed and allowlisted', () => {
  const violations = inspectManifest(
    manifest({
      permissions: '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />',
    }),
  );

  assert.deepEqual(violations, [
    'permission is not allowlisted: android.permission.ACCESS_FINE_LOCATION',
  ]);
});

test('cleartext traffic must stay explicitly disabled', () => {
  assert.deepEqual(inspectManifest(manifest({ cleartext: 'true' })), [
    'application must explicitly set android:usesCleartextTraffic="false"',
  ]);
  assert.deepEqual(inspectManifest(manifest({ cleartext: null })), [
    'application must explicitly set android:usesCleartextTraffic="false"',
  ]);
});

test('backup stays disabled and the extraction rules stay wired', () => {
  assert.deepEqual(
    inspectPrivacyDefaults(
      manifest({
        allowBackup: 'true',
        backupAttributes: 'android:fullBackupContent="@xml/backup_rules"',
      }),
    ),
    [
      'application must explicitly set android:allowBackup="false"',
      'application must declare android:dataExtractionRules="@xml/data_extraction_rules"',
      'application must not declare android:fullBackupContent when backup is disabled',
    ],
  );
});

const allDomainExcludes = [
  'root',
  'file',
  'database',
  'sharedpref',
  'external',
  'device_root',
  'device_file',
  'device_database',
  'device_sharedpref',
]
  .map((domain) => `<exclude domain="${domain}" path="." />`)
  .join('');

/**
 * allowBackup="false" 는 Android 12+ 에서 클라우드 백업만 끈다. 규칙 파일을 가리키지 않으면 기기 간 전송으로
 * 세션이 새 기기에 그대로 실려 간다. 매니페스트가 가리키기만 하고 파일 안이 비어 있어도 마찬가지다.
 *
 * root 하나만 빼는 것도 통과가 아니다. 백업 에이전트는 root 순회에서 files/·databases/·shared_prefs/ 를 빼고
 * 그 셋을 별도 도메인으로 다시 순회하므로, root 제외만으로는 세션 토큰이 file 도메인으로 그대로 나간다.
 */
test('data extraction rules exclude everything from both transfer paths', async () => {
  assert.deepEqual(inspectPrivacyDefaults(manifest({ backupAttributes: '' })), [
    'application must declare android:dataExtractionRules="@xml/data_extraction_rules"',
  ]);

  assert.deepEqual(
    inspectDataExtractionRules('<data-extraction-rules></data-extraction-rules>'),
    ['cloud-backup section is missing', 'device-transfer section is missing'],
  );
  assert.deepEqual(
    inspectDataExtractionRules(
      `<data-extraction-rules>
         <cloud-backup>${allDomainExcludes}</cloud-backup>
         <device-transfer>${allDomainExcludes}</device-transfer>
       </data-extraction-rules>`,
    ),
    [],
  );
  assert.deepEqual(
    inspectDataExtractionRules(
      `<data-extraction-rules>
         <cloud-backup>${allDomainExcludes}</cloud-backup>
         <device-transfer><exclude domain="root" path="." /></device-transfer>
       </data-extraction-rules>`,
    ),
    [
      'device-transfer must exclude path="." for every backup domain, missing: file, database,' +
        ' sharedpref, external, device_root, device_file, device_database, device_sharedpref',
    ],
  );
  assert.deepEqual(
    inspectDataExtractionRules(
      `<data-extraction-rules>
         <cloud-backup>${allDomainExcludes}</cloud-backup>
         <device-transfer>
           ${allDomainExcludes.replace('<exclude domain="file" path="." />', '<exclude domain="file" path="datastore" />')}
         </device-transfer>
       </data-extraction-rules>`,
    ),
    ['device-transfer must exclude path="." for every backup domain, missing: file'],
  );

  const rules = await readFile(
    new URL('../../app/src/main/res/xml/data_extraction_rules.xml', import.meta.url),
    'utf8',
  );
  assert.deepEqual(inspectDataExtractionRules(rules), []);
});

test('Firebase Analytics and Messaging auto-init stay explicitly disabled', () => {
  assert.deepEqual(inspectPrivacyDefaults(manifest({ metadata: '' })), [
    'firebase_analytics_collection_enabled must explicitly set android:value="false"',
    'firebase_messaging_auto_init_enabled must explicitly set android:value="false"',
  ]);
  assert.deepEqual(
    inspectPrivacyDefaults(
      manifest({
        metadata: `
          <meta-data android:name="firebase_analytics_collection_enabled" android:value="true" />
          <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />`,
      }),
    ),
    ['firebase_analytics_collection_enabled must explicitly set android:value="false"'],
  );
});

test('source manifest keeps privacy-sensitive defaults fail closed', async () => {
  const sourceManifest = await readFile(
    new URL('../../app/src/main/AndroidManifest.xml', import.meta.url),
    'utf8',
  );

  assert.deepEqual(inspectPrivacyDefaults(sourceManifest), []);
});

test('unprotected exports and every exported provider fail closed', () => {
  const violations = inspectManifest(
    manifest({
      components: `
        <receiver android:name="example.OpenReceiver" android:exported="true" />
        <provider android:name="example.OpenProvider" android:exported="true"
                  android:permission="example.PRIVATE" />`,
    }),
  );

  assert.deepEqual(violations, [
    'unprotected exported receiver is not allowlisted: example.OpenReceiver',
    'exported provider is forbidden: example.OpenProvider',
  ]);
});
