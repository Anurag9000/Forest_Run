from __future__ import annotations

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "app/src/main/AndroidManifest.xml"
APP_BUILD_PATH = ROOT / "app/build.gradle.kts"
PRIVACY_PATH = ROOT / "PRIVACY.md"
ANDROID_NAMESPACE = "{http://schemas.android.com/apk/res/android}"


class PrivacyContractTest(unittest.TestCase):
    def test_manifest_requests_only_vibration_and_disables_backup(self) -> None:
        manifest = ET.parse(MANIFEST_PATH).getroot()
        permissions = {
            element.attrib[f"{ANDROID_NAMESPACE}name"]
            for element in manifest.findall("uses-permission")
        }
        self.assertEqual({"android.permission.VIBRATE"}, permissions)

        application = manifest.find("application")
        self.assertIsNotNone(application)
        assert application is not None
        self.assertEqual("false", application.attrib.get(f"{ANDROID_NAMESPACE}allowBackup"))
        self.assertNotEqual(
            "true",
            application.attrib.get(f"{ANDROID_NAMESPACE}usesCleartextTraffic"),
        )

    def test_production_dependencies_exclude_remote_data_sdks(self) -> None:
        build = APP_BUILD_PATH.read_text(encoding="utf-8").lower()
        for forbidden in (
            "firebase",
            "play-services-ads",
            "billingclient",
            "okhttp",
            "retrofit",
            "amplitude",
            "appsflyer",
            "adjust",
            "sentry",
            "facebook-android-sdk",
            "segment-analytics",
        ):
            self.assertNotIn(forbidden, build)

    def test_production_source_has_no_network_or_remote_sdk_imports(self) -> None:
        source_root = ROOT / "app/src/main/java"
        forbidden_imports = (
            "import java.net.",
            "import javax.net.",
            "import android.webkit.",
            "import com.google.firebase.",
            "import okhttp3.",
            "import retrofit2.",
            "import com.android.billingclient.",
            "import com.google.android.gms.ads.",
            "import com.google.android.gms.auth.",
            "import com.segment.analytics.",
            "import com.amplitude.",
            "import com.appsflyer.",
            "import com.adjust.sdk.",
            "import io.sentry.",
        )
        for path in source_root.rglob("*.kt"):
            source = path.read_text(encoding="utf-8")
            for forbidden in forbidden_imports:
                self.assertNotIn(forbidden, source, str(path.relative_to(ROOT)))

    def test_policy_matches_the_enforced_source_model(self) -> None:
        policy = PRIVACY_PATH.read_text(encoding="utf-8")
        for required in (
            "com.anurag9000.forestrun",
            "offline, single-player game",
            "does not request Android's Internet permission",
            "android.permission.VIBRATE",
            'android:allowBackup="false"',
            "Clear storage",
            "uninstalled",
            "stable publicly accessible HTTPS URL",
            "Data Safety",
        ):
            self.assertIn(required, policy)
        self.assertIn("does not sell data", policy)
        self.assertIn("do not automatically transmit data", policy)


if __name__ == "__main__":
    unittest.main()
