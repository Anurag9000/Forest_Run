import unittest

from strict_json import StrictJsonError, loads


class StrictJsonOverflowTest(unittest.TestCase):
    def test_finite_looking_numeric_overflow_is_rejected(self) -> None:
        for raw in ('{"value":1e400}', '{"value":-1e400}'):
            with self.subTest(raw=raw):
                with self.assertRaisesRegex(StrictJsonError, "overflowed"):
                    loads(raw)

    def test_oversized_integer_failure_is_normalized(self) -> None:
        raw = '{"value":' + "9" * 10_000 + "}"
        with self.assertRaisesRegex(StrictJsonError, "invalid JSON"):
            loads(raw)

    def test_very_deep_input_is_rejected_before_recursive_parse(self) -> None:
        raw = "[" * 5_000 + "0" + "]" * 5_000
        with self.assertRaisesRegex(StrictJsonError, "nesting"):
            loads(raw, maximum_depth=64)

    def test_brackets_inside_strings_do_not_count_as_nesting(self) -> None:
        payload = '{"text":"[[[[{{{{ escaped \\\" ]]]]}}}}"}'
        parsed = loads(payload, maximum_depth=2, require_object=True)
        self.assertIn("[[[[", parsed["text"])


if __name__ == "__main__":
    unittest.main()
