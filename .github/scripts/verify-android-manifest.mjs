#!/usr/bin/env node

import path from 'node:path';
import process from 'node:process';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

export const ALLOWED_PERMISSIONS = new Set([
  'android.permission.ACCESS_NETWORK_STATE',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.INTERNET',
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.RECEIVE_BOOT_COMPLETED',
  'android.permission.RECORD_AUDIO',
  'android.permission.USE_BIOMETRIC',
  'android.permission.USE_FINGERPRINT',
  'android.permission.WAKE_LOCK',
  'com.google.android.c2dm.permission.RECEIVE',
]);

export const ALLOWED_UNPROTECTED_EXPORTED_COMPONENTS = new Set([
  'androidx.activity.ComponentActivity',
  'androidx.compose.ui.tooling.PreviewActivity',
  'com.careercompass.careercompass_fe.MainActivity',
  'com.careercompass.careercompass_fe.debug.DebugSettingsActivity',
  'com.kakao.sdk.auth.AuthCodeHandlerActivity',
]);

export const DATA_EXTRACTION_RULES_RESOURCE = '@xml/data_extraction_rules';

export const BACKUP_SECTIONS = Object.freeze(['cloud-backup', 'device-transfer']);

// 백업 에이전트는 앱 데이터를 도메인별로 나눠 순회한다. root 순회는 files/·databases/·shared_prefs/ 를 먼저
// 빼고, 그 셋은 file·database·sharedpref 도메인으로 따로 순회한다(AOSP BackupAgent#onFullBackup). 제외 판정도
// 경로 문자열의 정확 일치라(BackupAgent#manifestExcludesContainFilePath) 상위를 뺐다고 그 아래가 따라 빠지지
// 않는다. 그래서 root 하나가 아니라 문서가 적어 둔 아홉 도메인을 모두 요구한다.
// https://developer.android.com/identity/data/autobackup
export const BACKUP_EXCLUDED_DOMAINS = Object.freeze([
  'root',
  'file',
  'database',
  'sharedpref',
  'external',
  'device_root',
  'device_file',
  'device_database',
  'device_sharedpref',
]);

export const DISABLED_FIREBASE_AUTO_INIT = new Set([
  'firebase_analytics_collection_enabled',
  'firebase_messaging_auto_init_enabled',
]);

function attributes(source) {
  return Object.fromEntries(
    [...source.matchAll(/android:([A-Za-z][A-Za-z0-9]*)\s*=\s*"([^"]*)"/g)].map((match) => [
      match[1],
      match[2],
    ]),
  );
}

export function inspectPrivacyDefaults(source) {
  const violations = [];
  const application = /<application\b([^>]*)>/s.exec(source);
  if (!application) {
    return ['application declaration is missing'];
  }

  const applicationAttributes = attributes(application[1]);
  if (applicationAttributes.allowBackup !== 'false') {
    violations.push('application must explicitly set android:allowBackup="false"');
  }
  // Android 12+ 에서 allowBackup="false" 는 클라우드 백업만 끈다. 일부 제조사 기기는 기기 간 전송(D2D)을
  // 그대로 허용하고, <device-transfer> 규칙이 없으면 no-backup·cache 를 뺀 전부가 새 기기로 넘어간다.
  // 세션 토큰이 그 경로로 따라가면 안 되므로 규칙 파일 선언을 강제한다.
  // https://developer.android.com/identity/data/autobackup
  if (applicationAttributes.dataExtractionRules !== DATA_EXTRACTION_RULES_RESOURCE) {
    violations.push(
      `application must declare android:dataExtractionRules="${DATA_EXTRACTION_RULES_RESOURCE}"`,
    );
  }
  // 반대로 fullBackupContent 는 API 30 이하용이고, 그 아래에서는 allowBackup="false" 가 전부를 끈다.
  if ('fullBackupContent' in applicationAttributes) {
    violations.push('application must not declare android:fullBackupContent when backup is disabled');
  }

  const metadataValues = new Map();
  for (const match of source.matchAll(/<meta-data\b([^>]*)\/?\s*>/gs)) {
    const metadata = attributes(match[1]);
    if (!metadata.name) continue;
    const values = metadataValues.get(metadata.name) ?? [];
    values.push(metadata.value);
    metadataValues.set(metadata.name, values);
  }
  for (const name of DISABLED_FIREBASE_AUTO_INIT) {
    const values = metadataValues.get(name) ?? [];
    if (values.length === 0 || values.some((value) => value !== 'false')) {
      violations.push(`${name} must explicitly set android:value="false"`);
    }
  }

  return violations;
}

/**
 * 규칙 파일이 두 전송 경로 모두에서 앱 데이터 전체를 빼는지 본다.
 *
 * 매니페스트가 파일을 가리키기만 하고 안이 비어 있으면 D2D 는 그대로 열려 있다. 섹션이 없으면 그 모드가
 * 전부 허용이라는 것이 문서의 기본값이라, "없음" 을 통과로 읽지 않는다.
 *
 * 섹션 하나마다 [BACKUP_EXCLUDED_DOMAINS] 아홉 도메인의 path="." 제외를 모두 요구한다. root 만 빼면
 * files/·databases/·shared_prefs/ 는 별도 도메인으로 그대로 나간다.
 */
export function inspectDataExtractionRules(source) {
  const violations = [];
  for (const section of BACKUP_SECTIONS) {
    const block = new RegExp(`<${section}\\b[^>]*>(.*?)</${section}>`, 's').exec(source);
    if (!block) {
      violations.push(`${section} section is missing`);
      continue;
    }
    // 규칙 파일의 속성에는 android: 접두가 없다. 매니페스트용 attributes() 를 그대로 쓰면 전부 빈 객체가 된다.
    const rules = [...block[1].matchAll(/<exclude\b([^>]*)\/?\s*>/gs)].map((match) =>
      Object.fromEntries(
        [...match[1].matchAll(/([A-Za-z][A-Za-z0-9]*)\s*=\s*"([^"]*)"/g)].map((attribute) => [
          attribute[1],
          attribute[2],
        ]),
      ),
    );
    const missingDomains = BACKUP_EXCLUDED_DOMAINS.filter(
      (domain) => !rules.some((rule) => rule.domain === domain && rule.path === '.'),
    );
    if (missingDomains.length > 0) {
      violations.push(
        `${section} must exclude path="." for every backup domain, missing: ${missingDomains.join(', ')}`,
      );
    }
  }
  return violations;
}

export function inspectManifest(
  source,
  {
    allowedPermissions = ALLOWED_PERMISSIONS,
    allowedUnprotectedExportedComponents = ALLOWED_UNPROTECTED_EXPORTED_COMPONENTS,
  } = {},
) {
  const violations = [];
  const application = /<application\b([^>]*)>/s.exec(source);
  if (!application) {
    violations.push('application declaration is missing');
  } else {
    const applicationAttributes = attributes(application[1]);
    if (applicationAttributes.usesCleartextTraffic !== 'false') {
      violations.push('application must explicitly set android:usesCleartextTraffic="false"');
    }
    violations.push(...inspectPrivacyDefaults(source));
  }

  for (const match of source.matchAll(/<uses-permission\b([^>]*)\/?\s*>/gs)) {
    const permission = attributes(match[1]).name;
    if (!permission) {
      violations.push('uses-permission without android:name');
      continue;
    }
    const isAndroidxDynamicReceiverPermission = permission.endsWith('.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION');
    if (!allowedPermissions.has(permission) && !isAndroidxDynamicReceiverPermission) {
      violations.push(`permission is not allowlisted: ${permission}`);
    }
  }

  for (const match of source.matchAll(/<(activity|activity-alias|service|receiver|provider)\b([^>]*)\/?\s*>/gs)) {
    const [, kind, rawAttributes] = match;
    const component = attributes(rawAttributes);
    if (component.exported !== 'true') {
      continue;
    }
    const name = component.name ?? '(missing android:name)';
    if (kind === 'provider') {
      violations.push(`exported provider is forbidden: ${name}`);
      continue;
    }
    if (!component.permission && !allowedUnprotectedExportedComponents.has(name)) {
      violations.push(`unprotected exported ${kind} is not allowlisted: ${name}`);
    }
  }

  return violations;
}

async function main() {
  const manifestPath = process.argv[2];
  if (!manifestPath || process.argv.length !== 3) {
    throw new Error('Usage: verify-android-manifest.mjs <merged-AndroidManifest.xml>');
  }

  const source = await readFile(manifestPath, 'utf8');
  const violations = inspectManifest(source);
  if (violations.length > 0) {
    throw new Error(violations.map((violation) => `Manifest policy: ${violation}`).join('\n'));
  }
  console.log(`Manifest policy: passed (${manifestPath})`);
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : '';
if (import.meta.url === invokedPath) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
