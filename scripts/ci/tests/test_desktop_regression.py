import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("regression", Path(__file__).parents[1] / "desktop-regression.py")
regression = importlib.util.module_from_spec(spec)
spec.loader.exec_module(regression)


class SelectionTest(unittest.TestCase):
    def test_exact_bounded_selection_includes_repository_guards(self):
        available = {"org.viptv.app.FlowTest", "org.viptv.app.ImageGuardTest", "org.viptv.app.CodeScanTest"}
        requested, filters = regression.select_classes("org.viptv.app.FlowTest,\norg.viptv.app.FlowTest", available)
        self.assertEqual(["org.viptv.app.FlowTest"], requested)
        self.assertEqual(available, set(filters))
        self.assertEqual(3, len(filters))

    def test_rejects_unbounded_unknown_and_shell_inputs(self):
        available = {f"org.viptv.app.Flow{i}Test" for i in range(9)}
        for raw in ("", "*", "org.viptv.app.*Test", "org.viptv.app.UnknownTest",
                    "$(touch /tmp/unwanted)", "org.viptv.app.Flow0Test;false",
                    "--tests=org.viptv.app.Flow0Test", "org.viptv.app.Flow0Test.method",
                    ",".join(sorted(available)), " " * 2001 + "org.viptv.app.Flow0Test"):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                regression.select_classes(raw, available)

    def test_indexes_declared_test_classes_not_helpers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = root / "composeApp/src/desktopTest/kotlin"
            sources.mkdir(parents=True)
            (sources / "FlowTest.kt").write_text("package org.viptv.app\nclass FlowTest { }\nclass Helper { }")
            (sources / "EngineRequests.kt").write_text("package org.viptv.app\nclass EngineRequests { }")
            self.assertEqual({"org.viptv.app.FlowTest"}, regression.classes_in(root))


class NativeIdentityTest(unittest.TestCase):
    def test_source_and_platform_changes_invalidate_native_outputs(self):
        inputs = {"native/core": "a" * 40, "tools/build_engine.py": "b" * 40}
        key = regression.native_key(inputs, "ubuntu24", "X64")
        self.assertEqual(key, regression.native_key(dict(reversed(list(inputs.items()))), "ubuntu24", "X64"))
        for changed, image, arch in (({**inputs, "native/core": "c" * 40}, "ubuntu24", "X64"),
                                     ({**inputs, "tools/build_engine.py": "c" * 40}, "ubuntu24", "X64"),
                                     (inputs, "ubuntu26", "X64"), (inputs, "ubuntu24", "ARM64")):
            self.assertNotEqual(key, regression.native_key(changed, image, arch))

    def test_requires_hosted_platform_identity(self):
        with self.assertRaises(ValueError):
            regression.native_key({}, None, "X64")

    def test_rejects_wrong_revision_and_modified_worker(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "evidence"
            output.mkdir()
            (output / "selection.json").write_text(json.dumps({"torrent_commit": "a" * 40,
                "native_inputs": {"native/core": "b" * 40}, "native_key": "fixture-key"}))
            core = root / "composeApp/build/nativeResources/linux-x86-64/libviptv_core.so"
            core.parent.mkdir(parents=True)
            core.write_bytes(b"fixture core")
            worker = root / "composeApp/build/torrentResources/torrent-runtime/linux-x86-64/torrent-worker"
            worker.parent.mkdir(parents=True)
            worker.write_bytes(b"fixture worker")
            worker.chmod(0o755)
            manifest = {"protocol": 2, "qualification_only": False, "revision": "a" * 40,
                        "sha256": hashlib.sha256(worker.read_bytes()).hexdigest()}
            manifest_path = worker.with_name("worker.json")
            manifest_path.write_text(json.dumps(manifest))
            regression.verify_natives(root, output)
            self.assertEqual("b" * 40, json.loads((output / "native.json").read_text())["core_commit"])
            for field, wrong in (("revision", "c" * 40), ("sha256", "tampered"), ("qualification_only", True)):
                manifest_path.write_text(json.dumps({**manifest, field: wrong}))
                with self.subTest(field=field), self.assertRaises(ValueError):
                    regression.verify_natives(root, output)


class ReportTest(unittest.TestCase):
    def report(self, cases):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "TEST-FlowTest.xml").write_text(f'<testsuite name="FlowTest[desktop]">{cases}</testsuite>')
            return regression.test_results(root, ["org.viptv.app.FlowTest"])

    def test_requires_actual_requested_cases(self):
        for cases in ("", '<testcase classname="org.viptv.app.FlowTest"><skipped/></testcase>',
                      '<testcase classname="org.viptv.app.OtherTest"/>'):
            self.assertFalse(self.report(cases)["passed"])

    def test_failure_and_error_are_not_green(self):
        for node in ("failure", "error"):
            result = self.report(f'<testcase classname="org.viptv.app.FlowTest"><{node}/></testcase>')
            self.assertFalse(result["passed"])
            self.assertEqual(1, result["classes"]["org.viptv.app.FlowTest"]["failed"])

    def test_reports_executed_successful_cases(self):
        result = self.report('<testcase classname="org.viptv.app.FlowTest" name="works[desktop]"/>')
        self.assertTrue(result["passed"])
        self.assertEqual(1, result["classes"]["org.viptv.app.FlowTest"]["tests"])


if __name__ == "__main__":
    unittest.main()
