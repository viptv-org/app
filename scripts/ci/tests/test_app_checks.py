import importlib.util
from pathlib import Path
import os
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[3]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gate = load('gate', 'scripts/ci/check-app-jobs.py')
regression = load('regression', 'scripts/ci/desktop-regression.py')


class AppGateTest(unittest.TestCase):
    def jobs(self, event):
        jobs = {name: {'result': 'success'} for name in (
            'compile-android', 'submodule-guard', 'compile-wasm', 'compile-ios',
            'compile-android-app', 'native-prep', 'desktop-test')}
        jobs['compile-android' if event == 'pull_request' else 'submodule-guard']['result'] = 'skipped'
        return jobs

    def test_event_specific_skips_are_allowed(self):
        for event in ('push', 'workflow_dispatch', 'pull_request'):
            self.assertTrue(gate.validate(self.jobs(event), event))

    def test_missing_failed_cancelled_or_skipped_required_job_fails(self):
        for event in ('push', 'pull_request'):
            for name, job in self.jobs(event).items():
                for result in ('failure', 'cancelled', 'skipped'):
                    if job['result'] == result:
                        continue
                    jobs = self.jobs(event)
                    jobs[name]['result'] = result
                    with self.subTest(event=event, name=name, result=result):
                        self.assertFalse(gate.validate(jobs, event))
                jobs = self.jobs(event)
                del jobs[name]
                self.assertFalse(gate.validate(jobs, event))


class ShardCoverageTest(unittest.TestCase):
    def shard(self, index, mode='list', total=12):
        return subprocess.run([str(ROOT / 'tools/desktop-test-shards.sh'), mode],
                              env=dict(os.environ, SHARDS=str(total), SHARD=str(index)),
                              text=True, capture_output=True)

    def test_all_desktop_classes_are_assigned_once(self):
        source = ROOT / "composeApp/src/desktopTest/kotlin"
        expected = {str(path.relative_to(source).with_suffix('')).replace('/', '.')
                    for path in source.rglob("*Test.kt")}
        assigned = []
        for index in range(12):
            result = self.shard(index)
            self.assertEqual(0, result.returncode, result.stderr)
            classes = result.stdout.splitlines()
            self.assertTrue(classes)
            assigned.extend(classes)
        self.assertEqual(expected, set(assigned))
        self.assertEqual(len(expected), len(assigned))
        self.assertEqual(0, self.shard(0, 'check').returncode)

    def test_invalid_shard_configuration_fails(self):
        for index, total in ((0, 0), (12, 12), (-1, 12), ('oops', 12)):
            self.assertNotEqual(0, self.shard(index, total=total).returncode)


if __name__ == '__main__':
    unittest.main()
