#!/usr/bin/env python3
"""Source contracts for debug-only recovery maintenance launch intents."""

from __future__ import annotations

import pathlib
import unittest

SOURCE = (
    pathlib.Path(__file__).resolve().parents[1]
    / "app/src/main/java/com/anurag9000/forestrun/MainActivity.kt"
)


def extract_braced_block(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    in_string = False
    escaped = False
    line_comment = False
    block_comment = False
    index = brace

    while index < len(source):
        char = source[index]
        nxt = source[index + 1] if index + 1 < len(source) else ""
        if line_comment:
            if char == "\n":
                line_comment = False
            index += 1
            continue
        if block_comment:
            if char == "*" and nxt == "/":
                block_comment = False
                index += 2
            else:
                index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == "/" and nxt == "/":
            line_comment = True
            index += 2
            continue
        if char == '"':
            in_string = True
            index += 1
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
        index += 1

    raise AssertionError(f"Unbalanced Kotlin block for {signature!r}")


class RecoveryMaintenanceLaunchContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_cold_start_runs_maintenance_after_save_repair_before_game_view(self) -> None:
        create = extract_braced_block(
            self.source,
            "override fun onCreate(savedInstanceState: Bundle?)",
        )
        repair = create.index("SaveIntegrityManager.repair(this)")
        maintenance = create.index(
            "handleRecoveryMaintenanceIntent(intent, allowMutation = true)"
        )
        feedback = create.index("FeedbackSettings.init(this)")
        game_view = create.index("gameView = GameView(this)")
        self.assertLess(repair, maintenance)
        self.assertLess(maintenance, feedback)
        self.assertLess(maintenance, game_view)

    def test_reused_activity_is_inspection_only(self) -> None:
        new_intent = extract_braced_block(
            self.source,
            "override fun onNewIntent(intent: Intent)",
        )
        self.assertIn(
            "handleRecoveryMaintenanceIntent(intent, allowMutation = false)",
            new_intent,
        )
        self.assertLess(
            new_intent.index("setIntent(intent)"),
            new_intent.index("handleRecoveryMaintenanceIntent("),
        )

    def test_debuggable_gate_precedes_every_maintenance_construction(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private fun handleRecoveryMaintenanceIntent(",
        )
        debug_gate = handler.index("if (!isDebuggableRuntime())")
        constructions = []
        start = 0
        needle = "AndroidRecoveryEvidenceMaintenance(this)"
        while True:
            found = handler.find(needle, start)
            if found < 0:
                break
            constructions.append(found)
            start = found + len(needle)
        self.assertGreaterEqual(len(constructions), 3)
        for construction in constructions:
            self.assertLess(debug_gate, construction)
        self.assertIn("reason=not_debuggable", handler)

        debug = extract_braced_block(
            self.source,
            "private fun isDebuggableRuntime()",
        )
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", debug)

    def test_recover_and_discard_commands_require_quiescent_cold_start(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private fun handleRecoveryMaintenanceIntent(",
        )
        recover_start = handler.index("RECOVERY_ACTION_RECOVER ->")
        discard_start = handler.index("RECOVERY_ACTION_DISCARD_CORRUPT,")
        recover_block = handler[recover_start:discard_start]
        discard_block = handler[discard_start:]

        self.assertIn("if (!allowMutation)", recover_block)
        self.assertIn("reason=active_session", recover_block)
        self.assertLess(
            recover_block.index("if (!allowMutation)"),
            recover_block.index("AndroidRecoveryEvidenceMaintenance(this)"),
        )

        self.assertIn("if (!allowMutation)", discard_block)
        self.assertIn("reason=active_session", discard_block)
        self.assertLess(
            discard_block.index("if (!allowMutation)"),
            discard_block.index("val domain = recoveryDomain("),
        )
        self.assertLess(
            discard_block.index("val domain = recoveryDomain("),
            discard_block.index("AndroidRecoveryEvidenceMaintenance(this)"),
        )
        self.assertIn("reason=invalid_domain", discard_block)

    def test_live_inspection_constructs_no_mutating_command_path(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private fun handleRecoveryMaintenanceIntent(",
        )
        inspect_start = handler.index("RECOVERY_ACTION_INSPECT ->")
        recover_start = handler.index("RECOVERY_ACTION_RECOVER ->")
        inspect_block = handler[inspect_start:recover_start]
        self.assertIn("maintenance.inspect().supportSummary()", inspect_block)
        self.assertNotIn("allowMutation", inspect_block)
        self.assertNotIn("recoverSafely", inspect_block)
        self.assertNotIn("discard", inspect_block)

    def test_commands_are_one_shot_across_activity_recreation(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private fun handleRecoveryMaintenanceIntent(",
        )
        self.assertIn("finally", handler)
        self.assertIn("launchIntent.removeExtra(EXTRA_RECOVERY_ACTION)", handler)
        self.assertIn("launchIntent.removeExtra(EXTRA_RECOVERY_DOMAIN)", handler)

    def test_logs_expose_statuses_not_run_or_frame_payloads(self) -> None:
        handler = extract_braced_block(
            self.source,
            "private fun handleRecoveryMaintenanceIntent(",
        )
        required = (
            "result.disposition.name",
            "result.before.state.name",
            "result.after.state.name",
            "supportSummary()",
        )
        for item in required:
            self.assertIn(item, handler)
        for forbidden in (
            "RunSummary",
            "GhostFrame",
            "restQuote",
            "fingerprint",
            "completedGhost",
        ):
            self.assertNotIn(forbidden, handler)

    def test_domain_parser_accepts_only_named_evidence_domains(self) -> None:
        parser = extract_braced_block(
            self.source,
            "private fun recoveryDomain(raw: String?)",
        )
        self.assertIn("raw?.trim()?.uppercase()", parser)
        self.assertIn("RecoveryEvidenceDomain.entries.firstOrNull", parser)
        self.assertIn("it.name == normalized", parser)

    def test_public_debug_constants_cover_all_supported_actions(self) -> None:
        for literal in (
            'EXTRA_RECOVERY_ACTION = "recovery_action"',
            'EXTRA_RECOVERY_DOMAIN = "recovery_domain"',
            'RECOVERY_ACTION_INSPECT = "inspect"',
            'RECOVERY_ACTION_RECOVER = "recover"',
            'RECOVERY_ACTION_DISCARD_CORRUPT = "discard_corrupt"',
            'RECOVERY_ACTION_DISCARD_PENDING = "discard_pending"',
        ):
            self.assertIn(literal, self.source)


if __name__ == "__main__":
    unittest.main()
