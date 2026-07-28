#!/usr/bin/env python3
"""Correct stale identity, audio, Java, and signing checks in the Play release gate."""

from pathlib import Path

PATH = Path("scripts/prepare_play_release.py")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one source match, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    text = PATH.read_text(encoding="utf-8")

    text = replace_once(
        text,
        '''PLACEHOLDER_APPLICATION_IDS = {
    "com.anurag9000.forestrun",
    "com.example.forest_run",
    "com.example.forestrun",
}
''',
        '''PLACEHOLDER_APPLICATION_IDS = {
    "com.yourname.forest_run",
    "com.example.forest_run",
    "com.example.forestrun",
}
''',
        "final application ID classification",
    )

    text = replace_once(
        text,
        '''    "sfx_bloom",
    "sfx_bloom_ready",
    "sfx_bloom_convert",
    "sfx_bloom_fade",
    "sfx_mercy_miss",
''',
        '''    "sfx_bloom",
    "sfx_mercy_miss",
''',
        "optional Bloom fallback audio",
    )

    text = replace_once(
        text,
        '''def verify_release_signing(build_text: str, allow_unsigned: bool) -> None:
    release_block = re.search(r"release\\s*\\{(?P<body>.*?)\\n\\s*\\}", build_text, re.DOTALL)
    has_signing_reference = bool(
        release_block
        and re.search(r"\\bsigningConfig\\b", release_block.group("body"))
    )
    has_signing_declaration = "signingConfigs" in build_text
    if not (has_signing_reference and has_signing_declaration) and not allow_unsigned:
        fail(
            "No explicit release signing configuration was found. Configure an upload/release key "
            "outside source control, or pass --allow-unsigned for a non-upload dry run."
        )
''',
        '''def read_external_gradle_properties() -> dict[str, str]:
    values: dict[str, str] = {}
    for path in (ROOT / "gradle.properties", Path.home() / ".gradle" / "gradle.properties"):
        if not path.is_file():
            continue
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def verify_release_signing(build_text: str, allow_unsigned: bool) -> None:
    release_block = re.search(r"release\\s*\\{(?P<body>.*?)\\n\\s*\\}", build_text, re.DOTALL)
    has_signing_reference = bool(
        release_block
        and re.search(r"\\bsigningConfig\\b", release_block.group("body"))
    )
    has_signing_declaration = "signingConfigs" in build_text
    if not (has_signing_reference and has_signing_declaration):
        if allow_unsigned:
            return
        fail(
            "No explicit release signing configuration was found. Configure an upload/release key "
            "outside source control, or pass --allow-unsigned for a non-upload dry run."
        )

    properties = read_external_gradle_properties()
    required = (
        "FOREST_RUN_KEYSTORE",
        "FOREST_RUN_STORE_PASSWORD",
        "FOREST_RUN_KEY_ALIAS",
        "FOREST_RUN_KEY_PASSWORD",
    )
    missing = [name for name in required if not (os.environ.get(name) or properties.get(name))]
    if missing and not allow_unsigned:
        fail(
            "Release signing is configured, but credentials are missing:\\n- "
            + "\\n- ".join(missing)
            + "\\nProvide them as environment variables or external Gradle properties, "
            "or pass --allow-unsigned for a non-upload dry run."
        )
''',
        "real signing credentials",
    )

    text = replace_once(
        text,
        '''def run_gradle(tasks: list[str]) -> None:
    wrapper = require_file(ROOT / "gradlew", "Gradle wrapper")
    java = shutil.which("java")
    if java is None and not os.environ.get("JAVA_HOME"):
        fail("Java 17 is unavailable: set JAVA_HOME or add java to PATH")

    command = ["bash", str(wrapper), *tasks, "--no-daemon", "--stacktrace"]
''',
        '''def run_gradle(tasks: list[str]) -> None:
    wrapper = require_file(ROOT / "gradlew", "Gradle wrapper")
    java = shutil.which("java")
    if java is None:
        fail("Java 21 is unavailable: set JAVA_HOME or add a Java 21 runtime to PATH")

    version_result = subprocess.run(
        [java, "-version"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    version_text = version_result.stderr or version_result.stdout
    version_match = re.search(r'version "(?:1\\.)?(\\d+)', version_text)
    if not version_match or int(version_match.group(1)) < 21:
        fail(f"Java 21 or newer is required for the API 36 test gate; found: {version_text.splitlines()[0] if version_text else 'unknown'}")

    command = ["bash", str(wrapper), *tasks, "--no-daemon", "--stacktrace"]
''',
        "Java 21 release runtime",
    )

    PATH.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
