const { withAndroidManifest } = require('expo/config-plugins');

const PACKAGE = 'expo.modules.appblockerengine.blocker';
const SERVICE_NAME = `${PACKAGE}.service.BlockerService`;
const BOOT_RECEIVER_NAME = `${PACKAGE}.receiver.BootReceiver`;

// PACKAGE_USAGE_STATS is granted in Settings, not at install, hence the lint
// suppression the platform expects.
const PERMISSIONS = [
  { name: 'android.permission.PACKAGE_USAGE_STATS', ignore: 'ProtectedPermissions' },
  { name: 'android.permission.SYSTEM_ALERT_WINDOW' },
  { name: 'android.permission.FOREGROUND_SERVICE' },
  { name: 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE' },
  { name: 'android.permission.RECEIVE_BOOT_COMPLETED' },
  { name: 'android.permission.POST_NOTIFICATIONS' },
];

const requireSubtype = (props) => {
  const subtype = String(props?.foregroundServiceSubtype ?? '').trim();
  if (!subtype) {
    throw new Error(
      'expo-blocker: foregroundServiceSubtype is required. Google Play reviews it as the reason the specialUse foreground service exists.',
    );
  }

  return subtype;
};

const hasEntry = (entries, name) =>
  (entries ?? []).some((entry) => entry.$['android:name'] === name);

const addPermissions = (manifest) => {
  manifest['uses-permission'] = manifest['uses-permission'] ?? [];
  for (const { name, ignore } of PERMISSIONS) {
    if (hasEntry(manifest['uses-permission'], name)) continue;

    manifest['uses-permission'].push({
      $: { 'android:name': name, ...(ignore ? { 'tools:ignore': ignore } : {}) },
    });
  }
};

// MAIN/LAUNCHER lists launchable apps for the picker without QUERY_ALL_PACKAGES;
// SETTINGS and HOME let the monitor see the apps it must never block.
const QUERY_INTENTS = [
  { action: 'android.intent.action.MAIN', category: 'android.intent.category.LAUNCHER' },
  { action: 'android.settings.SETTINGS' },
  { action: 'android.intent.action.MAIN', category: 'android.intent.category.HOME' },
];

const matchesQueryIntent = (intent, { action, category }) =>
  hasEntry(intent.action, action) &&
  (category ? hasEntry(intent.category, category) : (intent.category ?? []).length === 0);

const addQueries = (manifest) => {
  manifest.queries = manifest.queries ?? [];
  for (const wanted of QUERY_INTENTS) {
    const isDeclared = manifest.queries.some((query) =>
      (query.intent ?? []).some((intent) => matchesQueryIntent(intent, wanted)),
    );
    if (isDeclared) continue;

    manifest.queries.push({
      intent: [
        {
          action: [{ $: { 'android:name': wanted.action } }],
          ...(wanted.category
            ? { category: [{ $: { 'android:name': wanted.category } }] }
            : {}),
        },
      ],
    });
  }
};

// Replaced rather than skipped when present, since a prebuild without --clean
// reapplies the plugin to a manifest that may carry an older entry.
const addComponents = (application, subtype) => {
  application.service = (application.service ?? []).filter(
    (service) => service.$['android:name'] !== SERVICE_NAME,
  );
  application.service.push({
    $: {
      'android:name': SERVICE_NAME,
      'android:enabled': 'true',
      'android:exported': 'false',
      'android:foregroundServiceType': 'specialUse',
    },
    property: [
      {
        $: {
          'android:name': 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE',
          'android:value': subtype,
        },
      },
    ],
  });

  application.receiver = (application.receiver ?? []).filter(
    (receiver) => receiver.$['android:name'] !== BOOT_RECEIVER_NAME,
  );
  application.receiver.push({
    $: {
      'android:name': BOOT_RECEIVER_NAME,
      'android:enabled': 'true',
      'android:exported': 'true',
    },
    'intent-filter': [
      {
        action: [
          { $: { 'android:name': 'android.intent.action.BOOT_COMPLETED' } },
          { $: { 'android:name': 'android.intent.action.MY_PACKAGE_REPLACED' } },
        ],
      },
    ],
  });
};

const withExpoBlocker = (config, props) => {
  const subtype = requireSubtype(props);

  return withAndroidManifest(config, (modConfig) => {
    const manifest = modConfig.modResults.manifest;
    manifest.$['xmlns:tools'] = manifest.$['xmlns:tools'] ?? 'http://schemas.android.com/tools';
    addPermissions(manifest);
    addQueries(manifest);
    addComponents(manifest.application[0], subtype);

    return modConfig;
  });
};

module.exports = withExpoBlocker;
