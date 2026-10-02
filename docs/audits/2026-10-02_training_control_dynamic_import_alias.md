# Forest Run — Alias-aware dynamic ML import detection (2026-10-02)

## Gap

The Python no-training authority now masks comments and string literals so its own prose fixtures do not recursively poison the live scan, while separately inspecting executable dynamic imports. The first AST pass covered `__import__("...")` and `importlib.import_module("...")`, but not the equivalent executable form `from importlib import import_module as load_module; load_module("...")`. A future retained Python loader could therefore introduce an ML framework without invalidating the no-training certificate.

## Correction

Resolve names bound specifically by `from importlib import import_module`, including aliases, and treat calls through those names as dynamic import sinks. The framework argument must still be a literal string and is mapped through the same existing framework/category table. Arbitrary user functions named `import_module` are not promoted to import sinks, so this does not broaden detection through naming coincidence.

A regression creates an aliased importlib helper and a dynamically loaded PyTorch module using split source literals; the audit must fail with one `pytorch` finding. Existing prose masking, static-source regexes, dependency checks, model-artifact checks and symlink provenance remain unchanged.
