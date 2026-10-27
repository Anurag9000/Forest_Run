import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

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

    def test_source_root_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            root = parent / "checkout"
            root.mkdir()
            self._fixture(root)
            alias = parent / "checkout-alias"
            alias.symlink_to(root, target_is_directory=True)
            with self.assertRaisesRegex(
                inventory.DeclaredDependencyInventoryError, "symbolic link"
            ):
                inventory.build_inventory(alias, CANDIDATE)

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

    def test_requires_expected_plugin_identities_not_only_two_plugin_entries(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root)
            build_file = root / "build.gradle.kts"
            build_file.write_text(
                build_file.read_text(encoding="utf-8")
                .replace("com.android.application", "example.fake.android")
                .replace("org.jetbrains.kotlin.android", "example.fake.kotlin"),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                inventory.DeclaredDependencyInventoryError,
                "expected the Android application and Kotlin Android",
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

    def test_comments_and_non_code_strings_do_not_become_inventory_entries(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._fixture(root)
            app_build = root / "app" / "build.gradle.kts"
            app_build.write_text(
                app_build.read_text(encoding="utf-8")
                + '\n// implementation("phantom:line-comment:9.9")\n'
                + '/* androidTestImplementation("phantom:block-comment:8.8") */\n'
                + 'val commentText = "literal // marker; implementation(\\"phantom:string:7.7\\")"\n'
                + 'val sample = """\nimplementation("phantom:raw-string:6.6")\n"""\n',
                encoding="utf-8",
            )
            root_build = root / "build.gradle.kts"
            root_build.write_text(
                root_build.read_text(encoding="utf-8")
                + '\n// id("phantom.plugin") version "9.9"\n'
                + 'val pluginExample = """id("phantom.raw-plugin") version "8.8"""\n',
                encoding="utf-8",
            )

            payload = inventory.build_inventory(root, CANDIDATE)

        entries = payload["entries"]
        self.assertNotIn("phantom:line-comment", [entry.get("name") for entry in entries])
        self.assertNotIn("phantom:block-comment", [entry.get("name") for entry in entries])
        self.assertNotIn("phantom:string", [entry.get("name") for entry in entries])
        self.assertNotIn("phantom:raw-string", [entry.get("name") for entry in entries])
        self.assertNotIn("phantom.plugin", [entry.get("name") for entry in entries])
        self.assertNotIn("phantom.raw-plugin", [entry.get("name") for entry in entries])

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

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_dependency_source_parent_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "root"
            outside = Path(temporary) / "outside"
            root.mkdir()
            (outside / "app").mkdir(parents=True)
            source = outside / "app" / "build.gradle.kts"
            source.write_text("plugins {}\n", encoding="utf-8")
            (root / "app").symlink_to(outside / "app", target_is_directory=True)

            with self.assertRaisesRegex(
                inventory.DeclaredDependencyInventoryError, "symbolic link"
            ):
                inventory._read(root, Path("app/build.gradle.kts"))

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_dependency_source_replaced_by_symlink_after_inspection_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "build.gradle.kts"
            source.write_text("plugins {}\n", encoding="utf-8")
            target = root / "target.txt"
            target.write_text("attacker source\n", encoding="utf-8")
            displaced = root / "displaced.txt"
            original_open = inventory.os.open

            def swap_before_open(path: object, flags: int, *args: object, **kwargs: object) -> int:
                if path == source.name and kwargs.get("dir_fd") is not None:
                    source.replace(displaced)
                    source.symlink_to(target)
                return original_open(path, flags, *args, **kwargs)

            with mock.patch.object(inventory.os, "open", side_effect=swap_before_open):
                with self.assertRaisesRegex(
                    inventory.DeclaredDependencyInventoryError,
                    "symbolic link|changed while",
                ):
                    inventory._read(root, Path("build.gradle.kts"))

    @unittest.skipUnless(hasattr(os, "symlink"), "symlink support required")
    def test_dependency_parent_swap_to_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "root"
            app = root / "app"
            app.mkdir(parents=True)
            (app / "build.gradle.kts").write_text('implementation("a:b:1")\n', encoding="utf-8")
            outside = Path(temporary) / "outside"
            outside.mkdir()
            (outside / "build.gradle.kts").write_text('implementation("x:y:9")\n', encoding="utf-8")
            displaced = root / "app.saved"
            original_open = inventory.os.open
            swapped = False

            def swap_parent(path: object, flags: int, *args: object, **kwargs: object) -> int:
                nonlocal swapped
                if path == "app" and kwargs.get("dir_fd") is not None and not swapped:
                    app.rename(displaced)
                    app.symlink_to(outside, target_is_directory=True)
                    swapped = True
                return original_open(path, flags, *args, **kwargs)

            with mock.patch.object(inventory.os, "open", side_effect=swap_parent):
                with self.assertRaisesRegex(
                    inventory.DeclaredDependencyInventoryError,
                    "symbolic link|safely read",
                ):
                    inventory._read(root, Path("app/build.gradle.kts"))
            self.assertTrue(swapped)

    def test_dependency_source_size_is_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "large.gradle.kts"
            source.write_bytes(b"x" * (inventory.MAX_SOURCE_BYTES + 1))
            with self.assertRaisesRegex(
                inventory.DeclaredDependencyInventoryError,
                "safety limit exceeded",
            ):
                inventory._read(root, Path("large.gradle.kts"))


if __name__ == "__main__":
    unittest.main()
