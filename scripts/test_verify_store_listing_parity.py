import tempfile
import unittest
from pathlib import Path
from unittest import mock

import verify_store_listing_parity as listing_parity
from verify_store_listing_parity import (
    StoreListingParityError,
    extract_canonical_metadata,
    verify_listing_parity,
)

ROOT = Path(__file__).resolve().parent.parent


class StoreListingParityTest(unittest.TestCase):
    @staticmethod
    def listing_text(
        *,
        title: str = "Forest Run",
        short: str = "Run gently through a living forest that remembers your choices.",
        full: str | None = None,
    ) -> str:
        if full is None:
            full = (
                "Forest Run is a handcrafted endless runner where mercy changes the path.\n\n"
                "Collect Seeds, enter Bloom, meet memorable creatures, and return to a "
                "persistent Garden that remembers how you played. Open the Forest Journal "
                "to revisit discoveries and bonds from earlier journeys."
            )
        return (
            "# Store listing\n\n"
            "## Google Play title\n\n"
            "```text\n"
            f"{title}\n"
            "```\n\n"
            "## Short description\n\n"
            "```text\n"
            f"{short}\n"
            "```\n\n"
            "## Full description\n\n"
            "```text\n"
            f"{full}\n"
            "```\n"
        )

    @staticmethod
    def write_metadata(metadata: Path, values: dict[str, str]) -> None:
        metadata.mkdir(parents=True, exist_ok=True)
        for filename, text in values.items():
            (metadata / filename).write_text(text, encoding="utf-8")

    def test_checked_in_play_copy_is_exact_projection_of_canonical_listing(self) -> None:
        listing = ROOT / "docs" / "STORE_LISTING.md"
        metadata = ROOT / "release" / "google-play" / "metadata" / "en-US"
        extracted = verify_listing_parity(listing, metadata)

        self.assertEqual("Forest Run", extracted["title.txt"])
        self.assertEqual(
            "Run gently through a living forest that remembers your choices.",
            extracted["short-description.txt"],
        )
        self.assertIn("Forest Journal", extracted["full-description.txt"])

    def test_exact_projection_accepts_matching_metadata_and_rejects_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            listing = root / "STORE_LISTING.md"
            listing.write_text(self.listing_text(), encoding="utf-8")
            metadata = root / "metadata"
            values = extract_canonical_metadata(listing)
            self.write_metadata(metadata, values)

            verify_listing_parity(listing, metadata)
            (metadata / "short-description.txt").write_text(
                values["short-description.txt"] + " Changed.", encoding="utf-8"
            )
            with self.assertRaisesRegex(StoreListingParityError, "does not exactly match"):
                verify_listing_parity(listing, metadata)

    def test_trailing_newline_is_rejected_instead_of_silently_normalized(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            listing = root / "STORE_LISTING.md"
            listing.write_text(self.listing_text(), encoding="utf-8")
            metadata = root / "metadata"
            values = extract_canonical_metadata(listing)
            self.write_metadata(metadata, values)
            (metadata / "title.txt").write_text("Forest Run\n", encoding="utf-8")

            with self.assertRaisesRegex(StoreListingParityError, "structurally invalid"):
                verify_listing_parity(listing, metadata)

    def test_duplicate_heading_and_wrong_fence_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            duplicate = root / "duplicate.md"
            duplicate.write_text(
                self.listing_text() + "\n## Google Play title\n\n```text\nForest Run\n```\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(StoreListingParityError, "exactly one"):
                extract_canonical_metadata(duplicate)

            wrong_fence = root / "wrong-fence.md"
            wrong_fence.write_text(
                self.listing_text().replace("```text\nForest Run\n```", "```\nForest Run\n```", 1),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(StoreListingParityError, "fenced text block"):
                extract_canonical_metadata(wrong_fence)

    def test_listing_source_rejects_bom_invalid_utf8_crlf_and_non_nfc(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary, "listing.md")
            invalid_cases = (
                (b"\xef\xbb\xbf# listing", "BOM"),
                (b"\xff", "UTF-8"),
                (b"# listing\r\n", "LF line endings"),
                (self.listing_text(title="Cafe\u0301").encode("utf-8"), "NFC"),
            )
            for raw, message in invalid_cases:
                with self.subTest(message=message):
                    path.write_bytes(raw)
                    with self.assertRaisesRegex(StoreListingParityError, message):
                        extract_canonical_metadata(path)

    def test_oversized_listing_is_rejected_before_open(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary, "listing.md")
            path.write_bytes(b"x" * (listing_parity.MAX_LISTING_BYTES + 1))
            with mock.patch.object(
                listing_parity.os, "open", wraps=listing_parity.os.open
            ) as opened:
                with self.assertRaisesRegex(StoreListingParityError, "safety limit"):
                    extract_canonical_metadata(path)
            self.assertFalse(
                any(call.args[0] == "listing.md" for call in opened.call_args_list),
                "the oversized leaf must be rejected before it is opened",
            )

    def test_listing_parent_swap_to_external_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "root"
            root.mkdir()
            source = root / "listing.md"
            source.write_text(self.listing_text(), encoding="utf-8")
            saved_root = root.with_name(root.name + ".saved")
            external = root.with_name(root.name + ".external")
            external.mkdir()
            (external / "listing.md").write_text(
                self.listing_text(title="Another Run"), encoding="utf-8"
            )
            original_open = listing_parity.os.open
            swapped = False

            def swap_before_open(path: object, flags: int, *args: object, **kwargs: object) -> int:
                nonlocal swapped
                if path == root.name and kwargs.get("dir_fd") is not None and not swapped:
                    root.rename(saved_root)
                    root.symlink_to(external, target_is_directory=True)
                    swapped = True
                return original_open(path, flags, *args, **kwargs)

            with mock.patch.object(listing_parity.os, "open", side_effect=swap_before_open):
                with self.assertRaises(StoreListingParityError):
                    extract_canonical_metadata(source)
            self.assertTrue(swapped)
            self.assertTrue(root.is_symlink())

    def test_listing_source_parent_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            outside = root / "outside"
            outside.mkdir()
            source = outside / "STORE_LISTING.md"
            source.write_text(self.listing_text(), encoding="utf-8")
            alias = root / "alias"
            try:
                alias.symlink_to(outside, target_is_directory=True)
            except OSError as exc:
                self.skipTest(f"symbolic links unavailable: {exc}")
            with self.assertRaisesRegex(StoreListingParityError, "symbolic link"):
                extract_canonical_metadata(alias / "STORE_LISTING.md")

    def test_release_wrapper_checks_listing_parity_before_candidate_metadata(self) -> None:
        source = (ROOT / "scripts" / "prepare_main_release.sh").read_text(encoding="utf-8")
        parity_index = source.index("verify_store_listing_parity.py")
        metadata_index = source.index("verify_store_metadata.py")

        self.assertLess(parity_index, metadata_index)
        parity_command = source[parity_index:metadata_index]
        self.assertIn('--listing-source "${ROOT}/docs/STORE_LISTING.md"', parity_command)
        self.assertIn('--metadata-dir "${ROOT}/release/google-play/metadata/en-US"', parity_command)


if __name__ == "__main__":
    unittest.main()
