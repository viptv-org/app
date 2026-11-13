import json
import os


def validate(jobs, event):
    expected = {
        'compile-android': 'skipped' if event == 'pull_request' else 'success',
        'submodule-guard': 'success' if event == 'pull_request' else 'skipped',
        **dict.fromkeys(('compile-wasm', 'compile-ios', 'compile-android-app', 'native-prep', 'desktop-test'), 'success'),
    }
    return set(jobs) == set(expected) and all(jobs[name].get('result') == result for name, result in expected.items())


if __name__ == '__main__':
    jobs = json.loads(os.environ['JOB_RESULTS'])
    print(json.dumps(jobs, indent=2))
    if not validate(jobs, os.environ['GITHUB_EVENT_NAME']):
        raise SystemExit('Every required app check must succeed; only event-specific jobs may skip.')
