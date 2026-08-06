import json
import os
import tempfile
import unittest
from pathlib import Path

import build_declared_dependency_inventory as inventory


CANDIDATE = "a" * 40


class DeclaredDependencyInventoryTest(unittest.TestCase):
    def _fixture(self, root: Path) -> None:
        (root / "app").mkdir(parents=True)
        (root / "gradle" / "wrapper").mkdir(parents=True)
        (root / "scripts").mkdir(parents=True)
        (root / "build.gradle.kts").write_text(
            '\n'.join(
                [
                    'plugins {',
                    '    id("com.android.application") version "8.13.2" apply false',
                    '    id("org.jetbrains.kotlin.android") version "1.9.22" apply false',
                    '}',
                ]
            ) + '\n',
            encoding="utf-8",
        )
        (root / "app" / "build.gradle.kts").write_text(
            '\n'.join(
                [
                    'dependencies {',
                    '    implementation("androidx.appcompat:appcompat:1.7.0")',
                    '    testImplementation("junit:junit:4.13.2")',
                    '    androidTestImplementation("junit:junit:4.13.2")',
                    '}',
                ]
            ) + '\n',
            encoding="utf-8",
        )
        (root / "gradle" / "wrapper" / "gradle-wrapper.properties").write_text(
            'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip\n',
            encoding="utf-8",
        )
        (root / "scripts" / "requirements-ci.txt").write_text(
            'Pillow==11.3.0\n',
            encoding="utf-8",
        )

    def test_inventory_is_candidate_bound_deterministic_and_explicitly_limited(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root)

            first = inventory.build_inventory(root, CANDIDATE)
            second = inventory.build_inventory(root, CANDIDATE)

            self.assertEqual(first, second)
            self.assertEqual(CANDIDATE, first["candidateSha"])
            self.assertEqual("declared-direct-dependencies-only", first["scope"])
            self.assertEqual(7, first["entryCount"])
            self.assertEqual(64, len(first["inventorySha256"]))
            self.assertEqual(4, len(first["sourceFiles"]))
            self.assertIn(
                "This is not a resolved transitive dependency graph.",
                first["limitations"],
            )
            entries = first["entries"]
            self.assertIn(
                {
                    "ecosystem": "gradle-wrapper",
                    "name": "gradle",
                    "version": "8.13",
                },
                entries,
            )
            self.assertIn(
                {
                    "ecosystem": "python",
                    "name": "Pillow",
                    "version": "11.3.0",
                },
                entries,
            )
            junit_entries = [entry for entry in entries if entry["name"] == "junit:junit"]
            self.assertEqual(2, len(junit_entries))
            self.assertEqual({"4.13.2"}, {entry["version"] for entry in junit_entries})

    def test_rejects_noncanonical_candidate_sha(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root)
            for invalid in ("A" * 40, "a" * 39, "not-a-sha"):
                with self.subTest(invalid=invalid):
                    with self.assertRaisesRegex(
                        inventory.DeclaredDependencyInventoryError,
                        "40 lowercase hexadecimal",
                    ):
                        inventory.build_inventory(root, invalid)

    def test_rejects_unpinned_python_requirement(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root)
            (root / "scripts" / "requirements-ci.txt").write_text(
                "Pillow>=11\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                inventory.DeclaredDependencyInventoryError,
                "exact == pin",
            ):
                inventory.build_inventory(root, CANDIDATE)

    def test_source_digests_change_when_declarations_change(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root)
            first = inventory.build_inventory(root, CANDIDATE)
            app_build = root / "app" / "build.gradle.kts"
            app_build.write_text(
                app_build.read_text(encoding="utf-8").replace("1.7.0", "1.7.1"),
                encoding="utf-8",
            )
            second = inventory.build_inventory(root, CANDIDATE)

            self.assertNotEqual(first["inventorySha256"], second["inventorySha256"])
            self.assertNotEqual(first["sourceFiles"], second["sourceFiles"])

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_publish_rejects_output_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root)
            payload = inventory.build_inventory(root, CANDIDATE)
            target = root / "target.json"
            target.write_text("{}\n", encoding="utf-8")
            output = root / "inventory.json"
            output.symlink_to(target)

            with self.assertRaisesRegex(
                inventory.DeclaredDependencyInventoryError,
                "must not be a symbolic link",
            ):
                inventory.publish(output, payload)
            self.assertEqual("{}\n", target.read_text(encoding="utf-8"))

    def test_publish_writes_canonical_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root)
            payload = inventory.build_inventory(root, CANDIDATE)
            output = root / "inventory.json"

            inventory.publish(output, payload)

            raw = output.read_bytes()
            expected = (
                json.dumps(
                    payload,
                    sort_keys=True,
                    separators=(",", ":"),
                    ensure_ascii=False,
                )
                + "\n"
            ).encode("utf-8")
            self.assertEqual(expected, raw)
            self.assertEqual(payload, json.loads(raw.decode("utf-8")))


if __name__ == "__main__":
    unittest.main()
