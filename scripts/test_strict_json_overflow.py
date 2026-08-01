import unittest

from strict_json import StrictJsonError, loads


class StrictJsonOverflowTest(unittest.TestCase):
    def test_finite_looking_numeric_overflow_is_rejected(self) -> None:
        for raw in ('{"value":1e400}', '{"value":-1e400}'):
            with self.subTest(raw=raw):
                with self.assertRaisesRegex(StrictJsonError, "overflowed"):
                    loads(raw)

    def test_oversized_positive_and_negative_integers_are_rejected_explicitly(self) -> None:
        for sign in ("", "-"):
            raw = '{"value":' + sign + "9" * 257 + "}"
            with self.subTest(sign=sign):
                with self.assertRaisesRegex(StrictJsonError, "256-digit safety limit"):
                    loads(raw)

    def test_integer_digit_bound_is_configurable_without_global_interpreter_state(self) -> None:
        self.assertEqual({"value": 1234}, loads('{"value":1234}', maximum_integer_digits=4))
        with self.assertRaisesRegex(StrictJsonError, "4-digit safety limit"):
            loads('{"value":12345}', maximum_integer_digits=4)
        with self.assertRaisesRegex(ValueError, "maximum_integer_digits"):
            loads('{"value":1}', maximum_integer_digits=0)

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
