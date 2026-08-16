import tempfile
import unittest
from pathlib import Path

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
